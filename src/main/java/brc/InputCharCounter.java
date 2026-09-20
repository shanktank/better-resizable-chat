package brc;

import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import javax.inject.Inject;
import javax.inject.Singleton;

// Typed character count parked on the right edge of the chat input line. It hangs off the input line's own parent as
// a dynamic child, so the chat's hiding, moving and resizing all carry it along without any path being told about it,
// and both layouts are covered by the one attachment. Font and colour are copied from the line rather than hardcoded,
// which keeps the count drawn the way the chat currently is (opaque, transparent, dialog-forced opaque, sprite packs).
@Singleton
public class InputCharCounter {
    private static final int MAX_CHARS = 80; // The engine's own cap on a typed chat line
    private static final int RIGHT_PAD = 2; // Gap left between the count and the input line's right edge
    private static final int SHADOW_SLACK = 2; // Room past the glyphs for the text shadow, inside the box
    private static final int FALLBACK_W = 32; // Stand-in text width for the frames before the font resolves

    private final Client client;
    private final ChatResizerConfig config;

    private Widget counter; // Our text widget, null while torn down
    private Widget host; // Input line's parent, the widget the counter hangs from; held for the teardown
    private int lastCount = -1; // Character count the text was built for; never a real count, so the first pass writes
    private int textW; // Width the current text measured, which the placement right-aligns against

    @Inject
    InputCharCounter(Client client, ChatResizerConfig config) {
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
            counter = null;
            host = null;
            return;
        }

        // Create widget for counter if it's not live
        if (counter == null || parent != host || parent.getChild(counter.getIndex()) != counter) {
            // Create
            counter = parent.createChild(-1, WidgetType.TEXT);
            counter.setXTextAlignment(WidgetTextAlignment.RIGHT); // Count grows leftward, holding its right edge
            host = parent;
            lastCount = -1; // Fresh widget holds no text
        }

        // Ahead of the count, whose measurement is taken in the font this sets
        // Draw in the input line's own font and colour, so a transparency swap or a sprite pack carries over for free
        if (counter.getFontId() != input.getFontId()) {
            counter.setFontId(input.getFontId());
            lastCount = -1; // Re-measure: the count's width is a different number of pixels in the new font
        }
        if (counter.getTextColor() != input.getTextColor()) counter.setTextColor(input.getTextColor());
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
            FontTypeFace font = counter.getFont();
            textW = font == null ? FALLBACK_W : font.getTextWidth(text);
            if (font == null) lastCount = -1; // Measure again next frame, once the font is there to measure in
        }

        // Sit the count on the input line's right edge, in the line's own coordinate space since we share its parent
        int w = textW + SHADOW_SLACK;
        int h = input.getHeight();
        int x = Math.max(0, input.getRelativeX() + input.getWidth() - RIGHT_PAD - w); // Shrunk past fitting: pin to the left
        int y = input.getRelativeY(); // Matching the line's box and its vertical alignment lands us on its baseline
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