package brc;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

public final class ChatScrollRetainer {
    private final Client client;

    // y used as flag denoting presence of capture, all fields must be set or nulled together
    private Integer y;
    private Integer height;
    private Integer viewport;

    ChatScrollRetainer(Client client) {
        this.client = client;
    }

    // Snapshot scroll details or silently no-op if chatbox isn't loaded; always safe to call, always clears snapshot
    void capture() {
        y = null;
        height = null;
        viewport = null;
        Widget scrollArea = liveScrollArea();
        if (scrollArea == null) return;
        y = scrollArea.getScrollY();
        height = scrollArea.getScrollHeight();
        viewport = scrollArea.getHeight();
    }

    // Restore scroll position or silently no-op if chatbox isn't loaded; always safe to call, always clears snapshot
    void restore() {
        if (y == null) return;
        int oldY = y;
        int oldH = height;
        int oldVH = viewport;
        y = null;
        height = null;
        viewport = null;
        Widget scrollArea = liveScrollArea();
        if (scrollArea == null) return;
        int newH = scrollArea.getScrollHeight();
        int newVH = scrollArea.getHeight();
        int newMax = Math.max(0, newH - newVH);
        int distFromBottom = Math.max(0, oldH - oldY - oldVH);
        int newScrollY = Math.max(0, Math.min(newMax, newMax - distFromBottom));
        // Set scroll position, update client trackers so subsequent BUILD_CHATBOXes don't yank position
        client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, newScrollY);
        client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, newScrollY);
        client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE, newH);
    }

    // Usable metrics guarded against mid-construction values, otherwise null
    private Widget liveScrollArea() {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        return scrollArea == null || scrollArea.getHeight() <= 0 || scrollArea.getScrollHeight() <= 0 ? null : scrollArea;
    }

    // Save scroll position, run something that might cause drift, restore saved position
    void withScrollPreserved(Runnable body) {
        capture();
        body.run();
        restore();
    }
}