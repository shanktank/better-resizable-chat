package brc;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import lombok.Getter;
import java.awt.Rectangle;

public class TopLevelModals {
    // Top-level interface slots, per layout, into which most overlays, such as the bank screen, are loaded
    private static final int[] MODAL_SLOTS = {
        InterfaceID.Toplevel.MAINMODAL, InterfaceID.Toplevel.FLOATER,
        InterfaceID.ToplevelOsrsStretch.MAINMODAL, InterfaceID.ToplevelOsrsStretch.FLOATER,
        InterfaceID.ToplevelDisplay.MAINMODAL, InterfaceID.ToplevelDisplay.FLOATER,
        InterfaceID.ToplevelOsm.MAINMODAL, InterfaceID.ToplevelOsm.FLOATER,
        InterfaceID.ToplevelPreEoc.MAINMODAL, InterfaceID.ToplevelPreEoc.FLOATER,
        InterfaceID.ToplevelSpectator.MAINMODAL, InterfaceID.ToplevelSpectator.FLOATER,
    };

    private static final int RESIZE_SCRIPT = 904; // onResize handler of the toplevel CONTROL widget
    private static final int[][] CONTROL_ARG = { // Toplevel variants and the layout enum the script expects
        { InterfaceID.Toplevel.CONTROL,            1129 }, // 548, fixed
        { InterfaceID.ToplevelOsrsStretch.CONTROL, 1130 }, // 161
        { InterfaceID.ToplevelPreEoc.CONTROL,      1131 }, // 164
    };

    private final Client client;

    @Getter private boolean modalOpen;
    @Getter private boolean ungrowNeeded; // An open modal requires the chat to give up its extra height

    TopLevelModals(Client client) {
        this.client = client;
    }

    boolean isTopLevelModalOpen() {
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return false;

        HashTable<WidgetNode> componentTable = client.getComponentTable();
        for (int slot : MODAL_SLOTS) if (componentTable.get(slot) != null) return true;
        return false;
    }

    boolean modalStateChanged(Rectangle grownChat, boolean overlapOnly) {
        boolean open = isTopLevelModalOpen();
        boolean ungrow = open && (!overlapOnly || anyModalObstructs(grownChat));
        boolean changed = open != modalOpen || ungrow != ungrowNeeded;
        modalOpen = open;
        ungrowNeeded = ungrow;
        return changed;
    }

    // True if any mounted modal wants the full band or would overlap the given chat rect
    private boolean anyModalObstructs(Rectangle chat) {
        if (chat == null) return true;
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        for (int slotId : MODAL_SLOTS) {
            if (componentTable.get(slotId) == null) continue;
            Widget slot = client.getWidget(slotId);
            if (slot == null) return true;
            if (slot.isHidden()) continue;
            // The open script sizes the slot itself: band/fill modals (bank, settings; varc 173 < 0)
            // get MINUS height, hard modals (quest journal, skill guide; varc 173 >= 0) get ABSOLUTE
            // 512x334 centered in the band — so the slot's bounds are the modal's real boundary
            if (slot.getHeightMode() != WidgetSizeMode.ABSOLUTE) return true;
            if (slot.getBounds().intersects(chat)) return true;
        }
        return false;
    }

    // Re-fit the UI to current available space
    void relayout() {
        for (int[] ca : CONTROL_ARG) {
            Widget control = client.getWidget(ca[0]);
            if (control != null && !control.isHidden()) {
                client.runScript(RESIZE_SCRIPT, control.getId(), ca[1]);
                realignMounted();
                return;
            }
        }
    }

    // Re-align each mounted modal's root to its slot
    private void realignMounted() {
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        for (int slotId : MODAL_SLOTS) {
            WidgetNode mounted = componentTable.get(slotId);
            if (mounted == null) continue;
            Widget slot = client.getWidget(slotId);
            if (slot == null) continue;
            Widget root = client.getWidget(mounted.getId(), 0);
            if (root == null) continue;
            // Match engine alignment: only MINUS axes track the slot; absolute roots size themselves
            int w = root.getWidthMode() == WidgetSizeMode.MINUS ? slot.getWidth() - root.getOriginalWidth() : root.getWidth();
            int h = root.getHeightMode() == WidgetSizeMode.MINUS ? slot.getHeight() - root.getOriginalHeight() : root.getHeight();
            if (w == root.getWidth() && h == root.getHeight()) continue;
            BetterResizableChatPlugin.setWidth(root, w);
            BetterResizableChatPlugin.setHeight(root, h);
            BetterResizableChatPlugin.revalidateChildren(root);
        }
    }
}