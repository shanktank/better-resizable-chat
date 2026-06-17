package brc;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import lombok.Getter;

public class TopLevelModals {
    // Top-level interface slots into which most overlays, such as the bank screen, are loaded
    final int MAINMODAL = InterfaceID.ToplevelPreEoc.MAINMODAL;
    final int FLOATER = InterfaceID.ToplevelPreEoc.FLOATER;

    private final Client client;

    @Getter private boolean modalOpen;

    TopLevelModals(Client client) {
        this.client = client;
    }

    boolean isTopLevelModalOpen() {
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return false;

        HashTable<WidgetNode> componentTable = client.getComponentTable();
        return componentTable.get(MAINMODAL) != null || componentTable.get(FLOATER) != null;
    }

    boolean topLevelModalOpenStateChanged() {
        boolean open = isTopLevelModalOpen();
        boolean changed = open != modalOpen;
        modalOpen = open;
        return changed;
    }
}