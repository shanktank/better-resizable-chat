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

        // setOriginalWidth leads to width spanning full viewport; guarded so the per-frame re-assert is free when in sync
        if (pmChat.getWidth() != slotW) BetterResizableChatPlugin.setWidth(pmChat, slotW);
    }

    // Bottom-anchored within PmChat.CONTAINER but resolves against client root
    void resizePmBoxFixed(int height) {
        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return; // Split private chat off or box not built yet
        if (pmChat.getHeight() == height) return; // Already positioned
        BetterResizableChatPlugin.setHeight(pmChat, height); // Must use literal width
        BetterResizableChatPlugin.revalidateChildren(pmChat);
    }
}