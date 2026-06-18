package brc;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
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
}