package brc;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import lombok.extern.slf4j.Slf4j;

/**
 * DEV-ONLY probe. Dumps the live fixed-mode chat widget tree to the log so the
 * fixed-layout resize mechanics can be reverse-engineered (geometry + size/position
 * modes are not available statically). Trigger in-game with the {@code ::brcfixed}
 * chat command while in fixed (non-resizable) layout.
 *
 * Reflection-free; uses only the public Widget API. Remove before any hub release.
 */
@Slf4j
class FixedModeProbe {
    private final Client client;

    FixedModeProbe(Client client) {
        this.client = client;
    }

    void dump() {
        StringBuilder sb = new StringBuilder("\n==== brcfixed probe ====\n");
        sb.append("isResized=").append(client.isResized())
            .append(" stoneArrangement=").append(client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT))
            .append(" state=").append(client.getGameState())
            .append('\n');

        sb.append("\n-- ancestor chain of Chatbox.UNIVERSE (leaf -> root) --\n");
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) {
            sb.append("  UNIVERSE null (chatbox not built / not logged in?)\n");
        } else {
            int depth = 0;
            for (Widget cur = universe; cur != null && depth < 16; cur = cur.getParent(), depth++) {
                sb.append("  [").append(depth).append("] ").append(line(cur)).append('\n');
            }
        }

        sb.append("\n-- toplevel (fixed group 548) --\n");
        appendNamed(sb, "CONTROL", InterfaceID.Toplevel.CONTROL);
        appendNamed(sb, "GAMEFRAME", InterfaceID.Toplevel.GAMEFRAME);
        appendNamed(sb, "VIEWPORT", InterfaceID.Toplevel.VIEWPORT);
        appendNamed(sb, "CHAT_CONTAINER", InterfaceID.Toplevel.CHAT_CONTAINER);
        appendNamed(sb, "PM_CONTAINER", InterfaceID.Toplevel.PM_CONTAINER);
        appendNamed(sb, "PmChat.CONTAINER", InterfaceID.PmChat.CONTAINER);

        sb.append("\n-- chatbox (group 162) --\n");
        appendNamed(sb, "UNIVERSE", InterfaceID.Chatbox.UNIVERSE);
        appendNamed(sb, "CHATAREA", InterfaceID.Chatbox.CHATAREA);
        appendNamed(sb, "CHAT_BACKGROUND", InterfaceID.Chatbox.CHAT_BACKGROUND);
        appendNamed(sb, "SCROLLAREA", InterfaceID.Chatbox.SCROLLAREA);
        appendNamed(sb, "CONTROLS", InterfaceID.Chatbox.CONTROLS);
        appendNamed(sb, "CONTROLS_BG_GRAPHIC", InterfaceID.Chatbox.CONTROLS_BACKGROUND_GRAPHIC);

        // Root child order = draw order (lower index drawn first/behind). Tells us if the chat (under
        // GAMEFRAME 548:2) draws over the 3D scene (under MAIN 548:10) once grown into the viewport band.
        appendChildren(sb, "ROOT children (z-order: GAMEFRAME 548:2 vs MAIN 548:10)", InterfaceID.Toplevel.UNIVERSE);
        appendChildren(sb, "GAMEFRAME children (the fixed frame sprites; find the chat/viewport divider)", InterfaceID.Toplevel.GAMEFRAME);
        appendChildren(sb, "MAIN children (548:10 viewport container; what reflows when we extend it down)", InterfaceID.Toplevel.MAIN);
        appendChildren(sb, "PM_CONTAINER children (548:36 -> PmChat mount; how the split-PM box is anchored)", InterfaceID.Toplevel.PM_CONTAINER);
        appendChildren(sb, "PmChat.CONTAINER children (163:0 -> the PM message lines)", InterfaceID.PmChat.CONTAINER);
        appendChildren(sb, "CHAT_CONTAINER children", InterfaceID.Toplevel.CHAT_CONTAINER);
        appendChildren(sb, "CHAT_BACKGROUND children", InterfaceID.Chatbox.CHAT_BACKGROUND);
        appendChildren(sb, "CHATAREA children", InterfaceID.Chatbox.CHATAREA);

        sb.append("==== end brcfixed ====");
        log.info(sb.toString());
    }

    private void appendNamed(StringBuilder sb, String label, int id) {
        Widget w = client.getWidget(id);
        sb.append("  ").append(label).append(": ").append(w == null ? "null" : line(w)).append('\n');
    }

    private void appendChildren(StringBuilder sb, String label, int id) {
        sb.append("\n-- ").append(label).append(" --\n");
        Widget w = client.getWidget(id);
        if (w == null) {
            sb.append("  (parent null)\n");
            return;
        }
        appendArray(sb, "static", w.getStaticChildren());
        appendArray(sb, "dynamic", w.getDynamicChildren());
        appendArray(sb, "nested", w.getNestedChildren());
    }

    private void appendArray(StringBuilder sb, String kind, Widget[] arr) {
        if (arr == null) return;
        for (int i = 0; i < arr.length; i++) {
            sb.append("    ").append(kind).append('[').append(i).append("] ")
                .append(arr[i] == null ? "null" : line(arr[i])).append('\n');
        }
    }

    private static String line(Widget w) {
        return String.format(
            "%s rel=(%d,%d) size=%dx%d orig=(%d,%d,%d,%d) mode[x=%s y=%s w=%s h=%s] type=%d hidden=%b sprite=%d parent=%s kids[s=%d d=%d n=%d]",
            hid(w.getId()),
            w.getRelativeX(), w.getRelativeY(),
            w.getWidth(), w.getHeight(),
            w.getOriginalX(), w.getOriginalY(), w.getOriginalWidth(), w.getOriginalHeight(),
            xpos(w.getXPositionMode()), ypos(w.getYPositionMode()),
            sizeMode(w.getWidthMode()), sizeMode(w.getHeightMode()),
            w.getType(), w.isHidden(), w.getSpriteId(), hid(w.getParentId()),
            count(w.getStaticChildren()), count(w.getDynamicChildren()), count(w.getNestedChildren())
        );
    }

    private static int count(Widget[] a) {
        return a == null ? 0 : a.length;
    }

    private static String hid(int id) {
        return id < 0 ? String.valueOf(id) : (id >>> 16) + ":" + (id & 0xFFFF);
    }

    private static String sizeMode(int m) {
        return m == WidgetSizeMode.ABSOLUTE ? "ABS" : m == WidgetSizeMode.MINUS ? "MINUS" : "FRAC";
    }

    // WidgetPositionMode: x -> 0 LEFT / 1 CENTER / 2 RIGHT; y -> 0 TOP / 1 CENTER / 2 BOTTOM
    private static String xpos(int m) {
        return m == 0 ? "LEFT" : m == 1 ? "CENTER" : m == 2 ? "RIGHT" : String.valueOf(m);
    }

    private static String ypos(int m) {
        return m == 0 ? "TOP" : m == 1 ? "CENTER" : m == 2 ? "BOTTOM" : String.valueOf(m);
    }
}
