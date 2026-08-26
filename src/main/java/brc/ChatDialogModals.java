package brc;

import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.annotations.Component;
import net.runelite.api.annotations.Interface;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetUtil;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatDialogModals {
    @Interface private static final int CHATBOX_GROUP = WidgetUtil.componentToInterface(InterfaceID.Chatbox.UNIVERSE);
    @Component private static final int[] DIALOGS_TO_CENTER = {
        InterfaceID.MembershipBenefitsPrompt.UNIVERSE,
        InterfaceID.Chatmenu.OPTIONS,
    };

    private final Client client;

    private boolean dialogOpen;

    @Inject
    ChatDialogModals(Client client) {
        this.client = client;
    }

    // Re-prime the cached open-state on enable; the singleton survives disable -> enable
    void reset() {
        dialogOpen = isDialogOpen();
    }

    boolean isDialogOpen() {
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return false;

        // Message-layer text inputs, built into Chatbox.MES_LAYER rather than opening a sub-interface
        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != InputType.NONE.getType()) return true;

        // RuneLite's own prompts (e.g. quest search) set no varc, and this is the only signal the 677
        // post-fire apply has: MESLAYERMODE above isn't written until after that script returns
        Widget mesLayer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
        if (mesLayer != null && !mesLayer.isHidden()) return true;

        // Any sub-interface opened into a chatbox-group component, keyed on mount slot
        for (WidgetNode node : client.getComponentTable()) {
            if (WidgetUtil.componentToInterface((int) node.getHash()) == CHATBOX_GROUP) return true;
        }

        return false;
    }

    // Re-place the dialog groups mounted into the chatbox after a resize; each is its own widget group, so the chat's
    // revalidate cascade never reaches it. The DIALOGS_TO_CENTER roots sit ABSOLUTE at a stock-width offset, so they
    // are centered by hand instead. Callers gate this on a dialog being open, keeping the walk off the idle path.
    void centerDialogs() {
        alignMounted();
        for (int id : DIALOGS_TO_CENTER) {
            Widget dialog = client.getWidget(id);
            Widget parent = dialog == null ? null : dialog.getParent();
            if (parent == null) continue;
            place(dialog, Math.max(0, (parent.getWidth() - dialog.getWidth()) / 2),
                          Math.max(0, (parent.getHeight() - dialog.getHeight()) / 2));
        }
    }

    // Hand the dialogs back to the engine's placement at the restored stock size. Deliberately a re-place rather than
    // a clear: setForcedPosition(-1, -1) is a position too, and nothing realigns a mounted root in fixed layout.
    void resetDialogPositions() {
        alignMounted();
        for (int id : DIALOGS_TO_CENTER) {
            Widget dialog = client.getWidget(id);
            Widget parent = dialog == null ? null : dialog.getParent();
            if (parent != null) align(dialog, parent);
        }
    }

    // Re-run the engine's alignment for every group mounted into the chatbox, against the slot it hangs in
    private void alignMounted() {
        for (WidgetNode node : client.getComponentTable()) {
            if (WidgetUtil.componentToInterface((int) node.getHash()) != CHATBOX_GROUP) continue;
            Widget slot = client.getWidget((int) node.getHash());
            Widget root = Widgets.mountedRoot(client, node.getId());
            if (slot != null && root != null) align(root, slot);
        }
    }

    private static void align(Widget widget, Widget slot) {
        int x = aligned(widget.getXPositionMode(), slot.getWidth(), widget.getWidth(), widget.getOriginalX());
        int y = aligned(widget.getYPositionMode(), slot.getHeight(), widget.getHeight(), widget.getOriginalY());
        if (x >= 0 && y >= 0) place(widget, x, y); // Otherwise a mode we don't reproduce: leave the placement alone
    }

    // Where the engine's alignment would put a widget on one axis, or -1 for the fractional modes no dialog group uses
    private static int aligned(int mode, int slotSize, int size, int original) {
        if (mode == WidgetPositionMode.ABSOLUTE_LEFT) return original;
        if (mode == WidgetPositionMode.ABSOLUTE_CENTER) return Math.max(0, (slotSize - size) / 2 + original);
        if (mode == WidgetPositionMode.ABSOLUTE_RIGHT) return Math.max(0, slotSize - size - original);
        return -1;
    }

    private static void place(Widget widget, int x, int y) {
        if (x != widget.getRelativeX() || y != widget.getRelativeY()) widget.setForcedPosition(x, y);
    }

    // Polled edge detection; input prompts (e.g. typing a private message) announce themselves no other way
    boolean dialogOpenStateChanged() {
        boolean open = isDialogOpen();
        boolean changed = open != dialogOpen;
        dialogOpen = open;
        return changed;
    }
}