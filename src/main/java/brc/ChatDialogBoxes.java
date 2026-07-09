package brc;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;

public class ChatDialogBoxes {
    private static final int CHATBOX_GROUP = WidgetUtil.componentToInterface(InterfaceID.Chatbox.UNIVERSE);
    private static final int[] DIALOGS_TO_CENTER = {
        InterfaceID.MembershipBenefitsPrompt.UNIVERSE,
        InterfaceID.Chatmenu.OPTIONS,
    };

    private final Client client;

    private boolean dialogOpen;

    ChatDialogBoxes(Client client) {
        this.client = client;
    }

    boolean isDialogOpen() {
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return false;

        // Message-layer text inputs, built into Chatbox.MES_LAYER rather than opening a sub-interface
        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != InputType.NONE.getType()) return true;

        // Detect RuneLite text input prompts (e.g. quest search) since they don't fire VarClientIntChanged on creation
        Widget mesLayer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
        if (mesLayer != null && !mesLayer.isHidden()) return true;

        // Any sub-interface opened into a chatbox-group component, keyed on mount slot
        for (WidgetNode node : client.getComponentTable())
            if (WidgetUtil.componentToInterface((int) node.getHash()) == CHATBOX_GROUP) return true;

        return false;
    }

    void centerDialogs() {
        for (int id : DIALOGS_TO_CENTER) {
            Widget dialog = client.getWidget(id);
            if (dialog == null) continue;
            Widget parent = dialog.getParent();
            if (parent == null) continue;

            int x = (parent.getWidth() - dialog.getWidth()) / 2;
            int y = (parent.getHeight() - dialog.getHeight()) / 2;
            dialog.setForcedPosition(Math.max(0, x), Math.max(0, y));
        }
    }

    void resetDialogPositions() {
        for (int id : DIALOGS_TO_CENTER) {
            Widget dialog = client.getWidget(id);
            if (dialog != null) dialog.setForcedPosition(-1, -1);
        }
    }

    // I don't know any other way to tell if an input prompt (e.g. typing a private message) was just opened or closed
    boolean dialogOpenStateChanged() {
        boolean open = isDialogOpen();
        boolean changed = open != dialogOpen;
        dialogOpen = open;
        return changed;
    }
}