package brc;

import brc.ChatResizerConfig.ChatIcons;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.game.ChatIconManager;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

// Resizes the account type badges in chat. They are inline <img=n> tags drawn from the shared mod icon array,
// not widgets, so the only lever is registering a resized copy of the sprite and re-pointing the tag at it.
@Singleton
public final class ChatIconStyle {
    private static final String TAG = "<img=";
    private static final int SCALED_HEIGHT = 11; // Small font's cap height
    private static final int CROPPED_ROWS = 2;
    private static final int OPAQUE = 0xFF000000;

    private final Client client;
    private final ChatIconManager iconManager;

    private final Map<ChatIcons, Map<Integer, Integer>> made = new EnumMap<>(ChatIcons.class);
    private final Map<ChatIcons, Map<Integer, Integer>> filed = new EnumMap<>(ChatIcons.class);
    private final Map<Integer, Integer> origins = new HashMap<>(); // A copy back to the icon it was made from
    private int pending; // Copies registered whose array append has not landed yet

    @Inject
    ChatIconStyle(Client client, ChatIconManager iconManager) {
        this.client = client;
        this.iconManager = iconManager;
    }

    // Point every <img=n> at this mode's copy of that icon. Idempotent: a tag already pointing at a copy is
    // read back to its origin first, so repeated passes never stack.
    String retag(String text, ChatIcons mode) {
        int at = text == null ? -1 : text.indexOf(TAG);
        if (at < 0) return text;

        int from = 0;
        StringBuilder out = new StringBuilder(text.length());
        while (at >= 0) {
            int close = text.indexOf('>', at);
            int icon = close < 0 ? -1 : number(text, at + TAG.length(), close);
            if (icon < 0) break; // Not a tag we can read; leave this one and the rest of the line alone
            out.append(text, from, at).append(TAG).append(target(icon, mode)).append('>');
            from = close + 1;
            at = text.indexOf(TAG, from);
        }
        return out.append(text, from, text.length()).toString();
    }

    // Adopt any copy whose array append has landed. True when one just became usable, which is the cue to
    // restyle: the rows that asked for it were handed the stock icon and nothing else would revisit them.
    boolean settle() {
        if (pending == 0) return false;

        boolean landed = false;
        for (Map.Entry<ChatIcons, Map<Integer, Integer>> mode : filed.entrySet()) {
            Map<Integer, Integer> ready = made.computeIfAbsent(mode.getKey(), m -> new HashMap<>());
            for (Map.Entry<Integer, Integer> copy : mode.getValue().entrySet()) {
                if (ready.containsKey(copy.getKey())) continue;
                int index = iconManager.chatIconIndex(copy.getValue());
                if (index < 0) continue;
                ready.put(copy.getKey(), index);
                origins.put(index, copy.getKey());
                pending--;
                landed = true;
            }
        }
        return landed;
    }

    private int target(int icon, ChatIcons mode) {
        int origin = origins.getOrDefault(icon, icon);
        if (mode == ChatIcons.NORMAL) return origin;

        Integer ready = made.computeIfAbsent(mode, m -> new HashMap<>()).get(origin);
        if (ready != null) return ready;

        // Registering hands back a reservation, not an icon number; settle() adopts it once the append lands
        filed.computeIfAbsent(mode, m -> new HashMap<>()).computeIfAbsent(origin, o -> register(o, mode));

        return origin; // Stock icon for now, so the row stays readable rather than blank
    }

    private Integer register(int origin, ChatIcons mode) {
        IndexedSprite[] icons = client.getModIcons();
        if (icons == null || origin < 0 || origin >= icons.length || icons[origin] == null) return null;

        BufferedImage image = toImage(icons[origin]);
        if (image == null) return null;

        pending++;

        return iconManager.registerChatIcon(mode == ChatIcons.CROPPED ? cropped(image) : scaled(image));
    }

    private static int number(String text, int start, int end) {
        if (end <= start) return -1;

        int value = 0;
        for (int i = start; i < end; i++) {
            char digit = text.charAt(i);
            if (digit < '0' || digit > '9') return -1;
            value = value * 10 + (digit - '0');
        }
        return value;
    }

    // Drawn onto its full canvas, not just its pixel block: the renderer advances by the sprite's *original*
    // width and anchors by its original height, so the blank padding is the gap before the name. Keep it.
    private static BufferedImage toImage(IndexedSprite sprite) {
        int w = sprite.getWidth();
        int h = sprite.getHeight();
        int atX = Math.max(0, sprite.getOffsetX());
        int atY = Math.max(0, sprite.getOffsetY());
        int canvasW = Math.max(w + atX, sprite.getOriginalWidth());
        int canvasH = Math.max(h + atY, sprite.getOriginalHeight());
        byte[] pixels = sprite.getPixels();
        int[] palette = sprite.getPalette();
        if (w <= 0 || h <= 0 || pixels == null || palette == null || pixels.length < w * h) return null;

        BufferedImage image = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = pixels[y * w + x] & 0xFF; // Palette index 0 is the sprite's transparent colour
                if (index != 0 && index < palette.length) image.setRGB(x + atX, y + atY, OPAQUE | palette[index]);
            }
        }
        return image;
    }

    // Nearest neighbour deliberately: an indexed sprite carries no alpha channel, so blended edges would come
    // back as opaque fringe pixels instead of fading out.
    private static BufferedImage scaled(BufferedImage source) {
        int h = Math.min(SCALED_HEIGHT, source.getHeight());
        int ink = Math.max(1, source.getWidth() * h / source.getHeight());
        // Nearest neighbour can sample straight past the blank trailing column that
        // spaces the badge off the name, so keep one rather than let the rounding decide
        boolean eaten = blankColumn(source, source.getWidth() - 1) && (ink - 1) * source.getWidth() / ink != source.getWidth() - 1;

        BufferedImage image = new BufferedImage(eaten ? ink + 1 : ink, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < ink; x++) {
                image.setRGB(x, y, source.getRGB(x * source.getWidth() / ink, y * source.getHeight() / h));
            }
        }
        return image;
    }

    private static boolean blankColumn(BufferedImage image, int x) {
        for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(x, y) >>> 24) != 0) return false;
        return true;
    }

    // The renderer sits an icon's bottom edge on the text baseline, so shaving rows off the top leaves every
    // remaining pixel exactly where it was drawn before.
    private static BufferedImage cropped(BufferedImage source) {
        int h = source.getHeight() - CROPPED_ROWS;
        if (h < 1) return source;

        BufferedImage image = new BufferedImage(source.getWidth(), h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                image.setRGB(x, y, source.getRGB(x, y + CROPPED_ROWS));
            }
        }
        return image;
    }
}