package brc;

import brc.internal.ChatGeometry;
import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PrivateMessageSplit {
    private final Client client;
    private final ChatResizerConfig config;

    private boolean pmBoxResized;

    @Inject
    PrivateMessageSplit(Client client, ChatResizerConfig config) {
        this.client = client;
        this.config = config;
    }

    void resizePmBox(int slotW) {
        if (!config.rewrapPrivateChat()) { // Setting disabled: revert once if resized earlier
            restorePmBox();
        } else {
            pmBoxResized = true;
            setPmBoxWidth(slotW);
        }
    }

    void restorePmBox() {
        if (!pmBoxResized) return;

        pmBoxResized = false;
        setPmBoxWidth(ChatGeometry.CHATBOX_SPRITE_W);
    }

    // Fixed only: its lines sit a flat offset above the container's bottom, so the container places them.
    // Resizable bakes the chat height into that offset instead, and there the rebuild owns the placement.
    void setPmBoxHeight(int height) {
        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return; // Split private chat off or box not built yet
        if (pmChat.getHeight() == height) return; // Already positioned

        Widgets.setHeight(pmChat, height); // Must use literal height
        Widgets.revalidateChildren(pmChat);
    }

    private void setPmBoxWidth(int slotW) {
        Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
        if (pmChat == null) return; // Split private chat off or box not built yet
        Widget pmContainer = pmChat.getParent();
        if (pmContainer == null) return;

        // Don't touch the fixed layout's MINUS-width container
        if (pmContainer.getWidthMode() != WidgetSizeMode.ABSOLUTE) return;

        if (pmContainer.getOriginalWidth() != slotW) {
            pmContainer.setOriginalWidth(slotW);
            pmContainer.revalidate();
        }

        if (pmChat.getWidth() != slotW) Widgets.setWidth(pmChat, slotW);
    }
}