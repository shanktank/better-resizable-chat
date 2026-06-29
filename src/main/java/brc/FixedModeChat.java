package brc;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import java.awt.Dimension;

public class FixedModeChat {
    private static final int STOCK_Y = 338; // Stock top of CHAT_CONTAINER in fixed layout
    private static final int STOCK_W = BetterResizableChatPlugin.CHATBOX_SPRITE_W;
    private static final int STOCK_H = BetterResizableChatPlugin.CHATBOX_SLOT_H;
    private static final int TAB_BAR_H = STOCK_H - BetterResizableChatPlugin.CHATBOX_SPRITE_H;
    private static final int STOCK_BOTTOM = STOCK_Y + STOCK_H;

    // Veiwport container, used to adjust viewport as chat height shrinks
    private static final int MAIN_TOP = 4;
    private static final int STOCK_MAIN_H = 334;

    // Viewport's side border sprites, must be extended as chat height shrinks
    private static final int RIGHT_BORDER_TOP = 205;
    private static final int RIGHT_BORDER_H = 133;

    private final Client client;
    private final BetterResizableChatConfig config;
    private final ChatBackgroundGraphic bgGraphic;
    private final PrivateMessageSplit pmSplit;

    FixedModeChat(Client client, BetterResizableChatConfig config, ChatBackgroundGraphic bgGraphic, PrivateMessageSplit pmSplit) {
        this.client = client;
        this.config = config;
        this.bgGraphic = bgGraphic;
        this.pmSplit = pmSplit;
    }

    // Returns the applied slot size, or null if not in fixed layout / widgets missing
    Dimension apply(boolean force) {
        Widget slot = client.getWidget(InterfaceID.Toplevel.CHAT_CONTAINER);
        if (slot == null) return null; // Not fixed layout, or frame not built yet
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return null;

        // Clamp height to [tab bar, full frame]; bottom edge stays pinned, top grows up toward 0
        int targetH = Math.min(STOCK_BOTTOM, Math.max(TAB_BAR_H, STOCK_H + config.fixedHeightChange()));
        int targetY = STOCK_BOTTOM - targetH;
        int backgroundH = targetH - TAB_BAR_H; // Background/chat-area height (excludes the tab bar)
        int mainH = Math.max(STOCK_MAIN_H, targetY - MAIN_TOP); // Extend the viewport down to the chat when shrunk
        int pmH = targetY - MAIN_TOP; // Split-PM box height so its bottom lands at the chat top

        Widget main = client.getWidget(InterfaceID.Toplevel.MAIN);

        if (!force &&
            slot.getHeight() == targetH && slot.getRelativeY() == targetY &&
            universe.getWidth() == STOCK_W && universe.getHeight() == targetH &&
            (main == null || main.getHeight() == mainH)
        ) {
            bgGraphic.zoomBakedSprite(STOCK_W, backgroundH);
            if (!bgGraphic.borderPresent(chatArea)) bgGraphic.drawBorder(chatArea); // In case of hop/rebuild
            extendViewportBorders(targetY); // Re-assert side borders (engine resets them on rebuilds)
            pmSplit.resizePmBoxFixed(pmH); // Re-assert split-PM position (engine resets it on rebuilds)
            return new Dimension(STOCK_W, targetH);
        }

        sizeChat(slot, universe, targetY, targetH);
        sizeViewport(main, mainH);
        extendViewportBorders(targetY);
        bgGraphic.drawBorder(chatArea);
        bgGraphic.zoomBakedSprite(STOCK_W, backgroundH);
        pmSplit.resizePmBoxFixed(pmH);
        return new Dimension(STOCK_W, targetH);
    }

    // Revert to stock using absolute universe, next engine chatbox rebuild fully restore native mode
    void restore() {
        Widget slot = client.getWidget(InterfaceID.Toplevel.CHAT_CONTAINER);
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (slot != null && !isStock(slot, universe)) sizeChat(slot, universe, STOCK_Y, STOCK_H);
        sizeViewport(client.getWidget(InterfaceID.Toplevel.MAIN), STOCK_MAIN_H);
        extendViewportBorders(STOCK_Y); // Reset side borders to stock
        pmSplit.resizePmBoxFixed(STOCK_Y - MAIN_TOP);
        bgGraphic.revertBakedSprite();
        bgGraphic.destroyBorder();
    }

    boolean isStock(Widget slot, Widget universe) {
        return slot.getOriginalY() == STOCK_Y && slot.getOriginalHeight() == STOCK_H
            && (universe == null || (universe.getWidth() == STOCK_W && universe.getHeight() == STOCK_H));
    }

    private static void sizeChat(Widget slot, Widget universe, int y, int h) {
        slot.setOriginalY(y);
        slot.setOriginalHeight(h);
        slot.revalidate();

        if (universe == null) return;
        universe.setSize(STOCK_W, h, WidgetSizeMode.ABSOLUTE, WidgetSizeMode.ABSOLUTE);
        universe.revalidate();
        BetterResizableChatPlugin.revalidateChildren(universe); // Reflow chat area, tabs, scroll, background
    }

    // Grow/shrink the 3D viewport container so a shrunk chat exposes game instead of a black band
    private static void sizeViewport(Widget main, int h) {
        if (main == null || main.getOriginalHeight() == h) return;
        main.setOriginalHeight(h);
        main.revalidate();
        BetterResizableChatPlugin.revalidateChildren(main); // Reflow the viewport + in-scene overlays
    }

    // Extend the viewport's left/right border sprites down to the chat top
    private void extendViewportBorders(int targetY) {
        resizeBorder(InterfaceID.Toplevel.CONTROL, MAIN_TOP, STOCK_MAIN_H, targetY, false);
        resizeBorder(InterfaceID.Toplevel.GAMEFRAME_GRAPHIC5, RIGHT_BORDER_TOP, RIGHT_BORDER_H, targetY, true);
    }

    private void resizeBorder(int widgetId, int top, int stockH, int targetY, boolean tile) {
        Widget border = client.getWidget(widgetId);
        if (border == null) return;
        int h = Math.max(stockH, targetY - top); // Stay stock when growing; reach the chat top when shrunk
        if (border.getOriginalHeight() == h) return;
        if (tile) border.setSpriteTiling(true); // Repeat the texture instead of stretching its detail
        border.setOriginalHeight(h);
        border.revalidate();
    }
}