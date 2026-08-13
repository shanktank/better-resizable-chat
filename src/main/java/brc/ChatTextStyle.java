package brc;

import brc.internal.ChatGeometry;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

// Restyles the built chat rows. The chat builder hardcodes Plain 12 and a 14px line pitch, so a
// different font or spacing has to be re-stamped and re-stacked over its output after every rebuild.
@Singleton
public final class ChatTextStyle {
    private static final int STOCK_PITCH = ChatGeometry.LINE_PITCH;
    private static final int STOCK_FONT = FontID.PLAIN_12;
    private static final int SMALL_FONT = FontID.PLAIN_11;
    private static final int PARTS_PER_ROW = 4;
    private static final int PART_NAME = 0; // Sender name, or the whole prefix on a two-part row
    private static final int PART_BODY = 1; // Message text
    private static final int PART_CHANNEL = 2; // Clan/channel prefix, drawn leftmost
    private static final int PART_RANK = 3; // Clan rank icon
    private static final int HEADER_PAD = 3; // Builder's gap before the body, and between prefix and name
    private static final int RANK_PAD = 1; // Its tighter gap either side of the rank icon
    private static final int RANK_LIFT = 2; // Pixels the rank icon rides up out of the row beneath it
    private static final int CONTENT_SLACK = 2; // Builder's scroll height is the stacked rows plus this

    private final Client client;
    private final ChatResizerConfig config;
    private final ChatIconStyle iconStyle;

    private int lastRebuild = -1; // varc CHAT_LASTREBUILD as of the last look
    private int appliedPitch = STOCK_PITCH; // Pitch the current row heights are laid out at

    @Inject
    ChatTextStyle(Client client, ChatResizerConfig config, ChatIconStyle iconStyle) {
        this.client = client;
        this.config = config;
        this.iconStyle = iconStyle;
    }

    void reset() {
        lastRebuild = -1;
        appliedPitch = STOCK_PITCH;
    }

    // Per frame: the builder stamps the cycle it ran on, so a changed value is an exact "rows are fresh" signal
    void sync() {
        boolean fresh = noteRebuild();
        // An icon copy registered on an earlier pass may only now be usable, and those rows drew the stock one
        boolean landed = iconStyle.settle();
        if ((fresh || landed) && isActive()) restyle(font(), pitch(), config.chatIcons(), fresh);
    }

    // A setting changed, or sizes were re-applied over a rebuild
    void reapply() {
        restyle(font(), pitch(), config.chatIcons(), noteRebuild());
    }

    // Hand the rows back to the game's own font, spacing and icons; no rebuild needed to undo
    void restore() {
        restyle(STOCK_FONT, STOCK_PITCH, ChatResizerConfig.ChatIcons.NORMAL, noteRebuild());
        reset();
    }

    private boolean isActive() {
        return font() != STOCK_FONT || pitch() != STOCK_PITCH || config.chatIcons() != ChatResizerConfig.ChatIcons.NORMAL;
    }

    private int font() {
        return config.chatFont() == ChatResizerConfig.ChatFont.SMALL ? SMALL_FONT : STOCK_FONT;
    }

    // Clamped rather than trusted: the range is a config-panel hint, and a zero would divide by zero below
    private int pitch() {
        return Math.min(STOCK_PITCH, Math.max(1, config.chatLineSpacing()));
    }

    // True when the builder has re-laid the rows since the last look, which resets the pitch they sit at
    private boolean noteRebuild() {
        int rebuild = client.getVarcIntValue(VarClientID.CHAT_LASTREBUILD);
        if (rebuild == lastRebuild) return false;
        lastRebuild = rebuild;
        appliedPitch = STOCK_PITCH;
        return true;
    }

    // Re-stamp and re-stack every filled row. Re-runnable without a rebuild: a row's height is always a
    // whole number of lines at appliedPitch, so its line count survives any number of passes.
    private void restyle(int font, int pitch, ChatResizerConfig.ChatIcons icons, boolean fresh) {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getStaticChildren() == null) return;

        List<Widget> rows = new ArrayList<>();
        for (Widget row : scrollArea.getStaticChildren())
            if (row != null && row.getHeight() > 0 && row.getId() >= InterfaceID.Chatbox.LINE0) rows.add(row);
        rows.sort(Comparator.comparingInt(Widget::getRelativeY)); // Slots are filled newest-first, so sort into view order

        int y = 0;
        // Stock spacing puts the rank icon where the game intends it, so only a restyled row lifts it
        int lift = font == STOCK_FONT && pitch == STOCK_PITCH ? 0 : RANK_LIFT;
        Widget[] parts = scrollArea.getDynamicChildren();
        for (Widget row : rows) {
            int was = row.getHeight();
            int slot = (row.getId() - InterfaceID.Chatbox.LINE0) * PARTS_PER_ROW;
            int first = Math.min(slot + PARTS_PER_ROW, parts == null ? 0 : parts.length);
            for (int i = slot; i < first; i++) {
                Widget part = parts == null ? null : parts[i];
                if (part == null || part.getHeight() <= 0) continue;
                part.setFontId(font); // Stamped before the measuring below, which reads the font back off the part
                part.setLineHeight(pitch); // The parts are created with an explicit 14, so a message's own wrap ignores the font
                String text = part.getText();
                String retagged = text == null ? null : iconStyle.retag(text, icons);
                if (retagged != null && !retagged.equals(text)) part.setText(retagged); // Its width counts toward the packing
            }

            repack(parts, slot, row.getOriginalX()); // Ahead of the count: it settles the body's wrap width
            int height = rowLines(parts, slot, Math.max(1, was / appliedPitch), font, fresh) * pitch;
            for (int i = slot; i < first; i++) {
                Widget part = parts == null ? null : parts[i];
                if (part == null || part.getHeight() <= 0) continue;
                // The rank icon is taller than a tightened row, so raise it out of the one below rather than
                // scale it: the engine's scaler is nearest neighbour and makes a mess of a 13px icon
                int top = i == slot + PART_RANK ? Math.max(0, y - lift) : y;
                // Only a part spanning the whole row follows its height; a sender name or rank icon keeps its own
                place(part, top, part.getHeight() == was ? height : part.getHeight());
            }
            place(row, y, height);
            y += height;
        }
        appliedPitch = pitch;
        repin(scrollArea, y + CONTENT_SLACK);
    }

    // How many lines the row's message actually draws as. The builder sized it by wrapping in Plain 12, but the
    // text re-wraps in whatever font renders it, so a smaller font can leave a trailing line of empty box.
    private int rowLines(Widget[] parts, int slot, int built, int font, boolean fresh) {
        if (fresh && font == STOCK_FONT) return built; // Builder-fresh rows are already measured in the font drawing them

        Widget body = shown(parts, slot + PART_BODY);
        if (body == null) body = shown(parts, slot + PART_NAME); // One-part row: the whole line is here
        if (body == null) return built;

        int lines = wrappedLines(body.getFont(), body.getText(), body.getOriginalWidth(), built);
        // Every glyph but one is narrower in Plain 11 than in Plain 12, so the builder's count is a true ceiling;
        // going back the other way it is a floor. Clamping either way can only ever leave a row too tall.
        return font == STOCK_FONT ? Math.max(built, lines) : Math.min(built, lines);
    }

    // Greedy wrap mirroring the engine's, minus its soft-hyphen breaks: having fewer places to break can only
    // round a count up, so this never claims fewer lines than are drawn. Anything it cannot model defers to built.
    private static int wrappedLines(FontTypeFace font, String text, int width, int built) {
        if (font == null || text == null || text.isEmpty() || width <= 0) return built;
        if (text.indexOf('<') >= 0 && (text.contains("<br>") || text.contains("<n>"))) {
            return built; // Forced break tag: splitting it is the engine's own job
        }

        int space = font.getTextWidth(" ");
        int lines = 1;
        int x = 0;
        boolean start = true;
        for (int i = 0; ; ) {
            int end = text.indexOf(' ', i);
            int word = font.getTextWidth(text.substring(i, end < 0 ? text.length() : end));
            if (word > width) return built; // A word the engine would break mid-way through; let its count stand
            if (start) {
                x = word;
            } else if (x + space + word <= width) {
                x += space + word;
            } else {
                lines++;
                x = word;
            }
            start = false;
            if (end < 0) return lines;
            i = end + 1;
        }
    }

    // The builder lays a header out at widths it measured in Plain 12 whatever font renders them, so a smaller
    // font leaves dead space before the message. Re-place the parts left to right at the live font's widths.
    private void repack(Widget[] parts, int slot, int base) {
        Widget body = shown(parts, slot + PART_BODY);
        if (body == null) return; // One-part row: the whole line is a single string, so there is no gap to close

        Widget name = shown(parts, slot + PART_NAME);
        Widget rank = shown(parts, slot + PART_RANK);
        int channelW = textWidth(shown(parts, slot + PART_CHANNEL)); // Sits at the base itself, so it never moves
        int nameW = textWidth(name);

        int x = 0;
        if (channelW > 0) x = channelW + (rank != null ? RANK_PAD : nameW > 0 ? HEADER_PAD : 0);
        if (rank != null) {
            move(rank, base + x);
            x += rank.getOriginalWidth() + (nameW > 0 ? RANK_PAD : 0);
        }
        if (nameW > 0) {
            if (name != null) move(name, base + x);
            x += nameW;
        }
        if (x > 0) x += HEADER_PAD;
        widen(body, base + x);
    }

    // Shifting the body left frees pixels off the row's right edge; give them to its width so the message still
    // wraps against the full chat. Safe only because the row height is measured from the text, not inherited.
    private static void widen(Widget body, int x) {
        int freed = body.getOriginalX() - x;
        if (freed == 0) return;
        body.setOriginalX(x);
        body.setOriginalWidth(body.getOriginalWidth() + freed);
        body.revalidate();
    }

    private static Widget shown(Widget[] parts, int index) {
        Widget part = parts == null || index >= parts.length ? null : parts[index];
        return part == null || part.isSelfHidden() ? null : part;
    }

    private static int textWidth(Widget part) {
        if (part == null || part.getText() == null || part.getText().isEmpty()) return 0;
        FontTypeFace font = part.getFont(); // Resolved live off the font id stamped above
        return font == null ? 0 : font.getTextWidth(part.getText());
    }

    private static void move(Widget part, int x) {
        if (part.getOriginalX() == x) return; // At the stock font this re-derives the builder's own numbers
        part.setOriginalX(x);
        part.revalidate();
    }

    private static void place(Widget widget, int y, int height) {
        widget.setOriginalY(y);
        widget.setOriginalHeight(height);
        widget.revalidate();
    }

    // The chat builder's own scroll tail, which preserves the viewer's distance from the bottom
    private void repin(Widget scrollArea, int contentH) {
        scrollArea.setScrollHeight(contentH);
        int scroll = client.getVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS) + contentH - client.getVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE);
        client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, scroll);
        client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE, contentH);
        client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, scrollArea.getScrollY());
    }
}