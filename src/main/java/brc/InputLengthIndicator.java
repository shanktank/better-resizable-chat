package brc;

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

// Typed character count parked on the right edge of the chat input line. It hangs off the input line's own parent as
// a dynamic child, so the chat's hiding, moving and resizing all carry it along without any path being told about it,
// and both layouts are covered by the one attachment. It draws a size below the line it sits on, in the chat's own
// small font, and takes its shadow from that line so a transparency swap or a sprite pack carries over for free.
@Singleton
public class InputLengthIndicator {
    private static final int MAX_CHARS = 80;
    private static final int FONT = FontID.PLAIN_11;
    private static final int COLOR = Color.DARK_GRAY.getRGB();
    private static final int RIGHT_PAD = 2; // Gap left between the count and the input line's right edge
    private static final int SHADOW_SLACK = 2; // Room past the glyphs for the text shadow, inside the box
    private static final int PLACEMENT_LIFT = 1; // Nudge up one pixel

    private final Client client;
    private final ChatResizerConfig config;

    private Widget counter; // Our text widget, null while torn down
    private Widget host; // Input line's parent, the widget the counter hangs from; held for the teardown
    private int lastCount = -1; // Character count the text was built for; never a real count, so the first pass writes
    private int textW; // Width the current text measured, which the placement right-aligns against

    @Inject
    InputLengthIndicator(Client client, ChatResizerConfig config) {
        this.client = client;
        this.config = config;
    }

    // Client thread, once a frame. Idle cost with the setting off is a config read.
    void sync() {
        if (!config.inputLenIndicator()) {
            destroy();
            return;
        }

        Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
        Widget parent = input == null ? null : input.getParent();
        if (parent == null) { // Chatbox not live (login, hop, layout swap): our child went with it, so drop the tracking
            counter = host = null;
            return;
        }

        // Create widget for counter if it's not live
        if (counter == null || parent != host || parent.getChild(counter.getIndex()) != counter) {
            counter = parent.createChild(-1, WidgetType.TEXT);
            counter.setXTextAlignment(WidgetTextAlignment.RIGHT); // Count grows leftward, holding its right edge
            counter.setFontId(FONT); // Ahead of the count below, whose measurement is taken in this font
            counter.setTextColor(COLOR);
            host = parent;
            lastCount = -1;
        }

        // Shadowing still rides the line: the chat drops it on the opaque background and takes it up on the transparent
        if (counter.getTextShadowed() != input.getTextShadowed()) counter.setTextShadowed(input.getTextShadowed());
        if (counter.getYTextAlignment() != input.getYTextAlignment()) counter.setYTextAlignment(input.getYTextAlignment());

        // What the player has typed but not yet sent; the same varc the engine draws into the input line
        String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
        int count = typed == null ? 0 : typed.length();

        // Rebuild and re-measure the text only when the count moves, keeping a resting frame to a pair of comparisons
        if (count != lastCount) {
            lastCount = count;
            String text = count + "/" + MAX_CHARS;
            counter.setText(text);
            textW = counter.getFont().getTextWidth(text);
        }

        // Sit the count on the input line's right edge, in the line's own coordinate space since we share its parent
        int w = textW + SHADOW_SLACK;
        int h = input.getHeight();
        int x = Math.max(0, input.getRelativeX() + input.getWidth() - RIGHT_PAD - w); // Shrunk past fitting: pin to the left
        int y = input.getRelativeY() - PLACEMENT_LIFT; // Otherwise the line's own box and vertical alignment, so we share its baseline
        if (counter.getOriginalX() != x || counter.getOriginalY() != y || counter.getOriginalWidth() != w || counter.getOriginalHeight() != h) {
            counter.setPos(x, y);
            counter.setSize(w, h);
            counter.revalidate();
        }

        // The engine hides the line for anything that takes the chatbox over (dialogs, prompts); follow it
        if (counter.isSelfHidden() != input.isHidden()) counter.setHidden(input.isHidden());
    }

    // Take the counter back out of the chatbox, leaving the empty slot behind as the border teardown does
    void destroy() {
        Widget widget = counter, parent = host;
        counter = host = null;
        lastCount = -1;

        if (widget == null || parent == null) return;

        Widget[] children = parent.getChildren();
        int idx = widget.getIndex();
        if (children != null && idx >= 0 && idx < children.length && children[idx] == widget) children[idx] = null;
    }
}