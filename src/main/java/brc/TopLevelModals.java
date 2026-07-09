package brc;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import lombok.Getter;

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

    boolean topLevelModalOpenStateChanged() {
        boolean open = isTopLevelModalOpen();
        boolean changed = open != modalOpen;
        modalOpen = open;
        return changed;
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

    // Re-align each mounted modal's root to its slot; revalidates and the resize script don't cross
    // the nested-group boundary, so modals that size themselves off their own root (All Settings
    // polls it on a timer, unlike the bank which polls the slot) keep their mount-time size
    private void realignMounted() {
        HashTable<WidgetNode> componentTable = client.getComponentTable();
        for (int slotId : MODAL_SLOTS) {
            WidgetNode mounted = componentTable.get(slotId);
            if (mounted == null) continue;
            Widget slot = client.getWidget(slotId);
            if (slot == null) continue;
            Widget root = client.getWidget(mounted.getId(), 0);
            if (root == null || (root.getWidth() == slot.getWidth() && root.getHeight() == slot.getHeight())) continue;
            BetterResizableChatPlugin.setWidth(root, slot.getWidth());
            BetterResizableChatPlugin.setHeight(root, slot.getHeight());
            BetterResizableChatPlugin.revalidateChildren(root);
        }
    }
    // Makes the flicker from temp-shrinking chat height when opening bank in resizable layout go away
    // *Introduces* the abovementioned flicker when temp-shrinking chat height when opening bank in fixed layout
    // All Settings modal now refits itself when chat height is temp-shrunk in fixed layout, but introduces same flicker as above
    // All Settings modal still does not use extra available space when chat height is below stock in resizable layout
}