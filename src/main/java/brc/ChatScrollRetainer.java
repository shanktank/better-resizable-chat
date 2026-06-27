package brc;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

// Keeps the chat's visual scroll location fixed across chat resizes
public final class ChatScrollRetainer {
    private final Client client;
    private final ChatDialogBoxes dialogBoxes;

    private Integer lastViewport; // Null until the chat scroll area is first seen
    private int lastWidth;
    private int distFromBottom; // Content pixels below the viewport bottom

    ChatScrollRetainer(Client client, ChatDialogBoxes dialogBoxes) {
        this.client = client;
        this.dialogBoxes = dialogBoxes;
    }

    void sync() {
        Widget scrollArea = liveScrollArea();
        if (scrollArea == null) {
            lastViewport = null; // Chat not live (hop/relog)
            return;
        }

        int viewport = scrollArea.getHeight();
        int width = scrollArea.getWidth();
        int contentH = scrollArea.getScrollHeight();
        int scrollY = scrollArea.getScrollY();

        if (lastViewport != null && (viewport != lastViewport || width != lastWidth)) {
            // Chat was resized, repin to the remembered distance from the bottom
            int target = Math.max(0, Math.min(Math.max(0, contentH - viewport), contentH - viewport - distFromBottom));
            client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, target);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, target);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE, contentH);
        } else if (!dialogBoxes.isDialogOpen()) {
            // Remember where viewer is sitting, skipped while chat dialog is open
            distFromBottom = Math.max(0, contentH - scrollY - viewport);
        }

        lastViewport = viewport;
        lastWidth = width;
    }

    // Usable metrics guarded against mid-construction values, otherwise null
    private Widget liveScrollArea() {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        return scrollArea == null || scrollArea.getHeight() <= 0 || scrollArea.getScrollHeight() <= 0 ? null : scrollArea;
    }
}