package brc;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;

public class PrivateMessageSplit {
    private final Client client;
    private final BetterResizableChatConfig config;

    private boolean pmBoxResized;

    PrivateMessageSplit(Client client, BetterResizableChatConfig config) {
        this.client = client;
        this.config = config;
    }

    // Fixed-layout vertical tracking: the split-PM lines are bottom-anchored within PmChat.CONTAINER
    // (163:0), so setting its height puts its bottom at the chat top. Like resizePmBox's literal width,
    // a literal height is used (it is a mounted interface root, so its MINUS-fill resolves against the
    // client root, not its container) and only the children are revalidated.
    void resizePmBoxFixed(int height) {
        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return; // Split private chat off, or box not built yet
        if (pmChat.getHeight() == height) return; // Already positioned
        BetterResizableChatPlugin.setHeight(pmChat, height);
        BetterResizableChatPlugin.revalidateChildren(pmChat);
    }

    void resizePmBox(Integer slotW) {
        if (slotW == null || !config.rewrapPrivateChat()) { // Revert or disable
            if (!pmBoxResized) return;
            pmBoxResized = false;
            slotW = BetterResizableChatPlugin.CHATBOX_SPRITE_W;
        } else { // Enable or update
            pmBoxResized = true;
        }

        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return; // Split private chat off or box not built yet
        Widget pmContainer = pmChat.getParent();
        if (pmContainer == null) return;

        if (pmContainer.getWidthMode() != WidgetSizeMode.ABSOLUTE) return; // Fixed layout uses an absolute container

        if (pmContainer.getOriginalWidth() != slotW) {
            pmContainer.setOriginalWidth(slotW);
            pmContainer.revalidate();
        }

        BetterResizableChatPlugin.setWidth(pmChat, slotW); // setOriginalWidth leads to width spanning full viewport
    }
}