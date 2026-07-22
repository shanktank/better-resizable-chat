package brc.internal;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import java.awt.Rectangle;

// Widget lookup and mutation helpers shared by the plugin and its subsystems
public final class Widgets {
    private Widgets() {}

    // The toplevel slot the chatbox occupies; fixed: CHAT_CONTAINER, resizable: the layout's chat slot
    public static Widget chatSlot(Client client) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        return universe == null ? null : universe.getParent();
    }

    // Root layer of a mounted interface group. Climb rather than trust file 0: the Collection Log's file 0
    // is a frame inside its movable window, and realigning that to a toplevel slot mangles the window.
    public static Widget mountedRoot(Client client, int group) {
        Widget root = client.getWidget(group, 0);
        if (root == null) return null;
        for (Widget up = root.getParent(); up != null && WidgetUtil.componentToInterface(up.getId()) == group; up = up.getParent()) root = up;
        return root;
    }

    // Canvas bounds from the live layout fields, since getBounds()'s x/y are recorded during the draw pass and so lag
    // a widget moved this frame. Covers setForcedPosition, which writes the relative x/y alongside the forced pair.
    public static Rectangle liveBounds(Widget widget) {
        int x = widget.getRelativeX();
        int y = widget.getRelativeY();
        // Climbing ends at the toplevel's own components, which draw at canvas (0,0), so no origin term is needed
        for (Widget up = widget.getParent(); up != null; up = up.getParent()) {
            x += up.getRelativeX();
            y += up.getRelativeY();
        }
        return new Rectangle(x, y, widget.getWidth(), widget.getHeight());
    }

    public static void revalidateChildren(Widget widget) {
        revalidateAll(widget.getStaticChildren());
        revalidateAll(widget.getDynamicChildren());
    }

    static void revalidateAll(Widget[] children) {
        if (children == null) return;
        for (Widget child : children) {
            if (child == null) continue;
            child.revalidate();
            if (child.getId() == InterfaceID.Chatbox.SCROLLAREA) continue; // Engine computes scroll area's inner layout
            revalidateChildren(child);
        }
    }

    // Deliberate: for MINUS/fill children and mounted sub-interface roots, setOriginal* resolves to the wrong value
    @SuppressWarnings("deprecation") public static void setWidth(Widget widget, int width) { widget.setWidth(width); }
    @SuppressWarnings("deprecation") public static void setHeight(Widget widget, int height) { widget.setHeight(height); }
}