package brc;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import java.awt.Dimension;

/**
 * Fixed (non-resizable) layout chat height resize.
 *
 * <p>In fixed layout the chatbox interface (group 162) mounts into {@code Toplevel.CHAT_CONTAINER}
 * (548:11) — an ABSOLUTE-sized slot pinned to the bottom of the fixed game frame, with stock
 * geometry {@code orig=(x=0, y=338, w=519, h=165)} so its bottom edge sits at the frame bottom
 * (503).
 *
 * <p>Growing the chat keeps the bottom edge pinned and moves the top edge up (over the bottom of the
 * 3D viewport); shrinking moves the top edge down. Only the slot's original Y/height change.
 *
 * <p>{@code Chatbox.UNIVERSE} (162:0) is the mounted interface root and is natively MINUS-fill. But,
 * like the resizable layout, its fill resolves against the client root (765x503) rather than the
 * slot, so {@code revalidate()} would blow it up to the whole frame. So we pin it ABSOLUTE to the
 * slot size; the rest of the chatbox subtree (chat area, tab bar, scroll area) then reflows
 * correctly via a revalidate cascade. Width is never touched, so no re-wrap or tab spreading is
 * needed. The background sprite and stone border are handled exactly as in resizable layout
 * ({@link ChatBackgroundGraphic#zoomBakedSprite}/{@link ChatBackgroundGraphic#drawBorder}).
 */
public class FixedModeChat {
    private static final int STOCK_Y = 338; // Stock top of CHAT_CONTAINER (548:11) in fixed layout
    private static final int STOCK_W = BetterResizableChatPlugin.CHATBOX_SPRITE_W; // 519
    private static final int STOCK_H = BetterResizableChatPlugin.CHATBOX_SLOT_H;   // 165 (chat box + tab bar)
    private static final int TAB_BAR_H = STOCK_H - BetterResizableChatPlugin.CHATBOX_SPRITE_H; // 23
    private static final int STOCK_BOTTOM = STOCK_Y + STOCK_H; // 503 — kept pinned as the chat grows/shrinks

    // The 3D viewport container Toplevel.MAIN (548:10), stock orig=(4,4,512,334). VIEWPORT (548:26)
    // MINUS-fills it. When the chat shrinks below stock we grow MAIN down to the chat's new top so the
    // freed strip renders (and is clickable) game instead of a black band.
    private static final int MAIN_TOP = 4;
    private static final int STOCK_MAIN_H = 334;

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

        // Clamp height to [tab bar, full frame]; bottom edge stays pinned at 503, top grows up toward 0
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
            pmSplit.resizePmBoxFixed(pmH); // Re-assert split-PM position (engine resets it on rebuilds)
            return new Dimension(STOCK_W, targetH);
        }

        sizeChat(slot, universe, targetY, targetH);
        sizeViewport(main, mainH);
        bgGraphic.drawBorder(chatArea);
        bgGraphic.zoomBakedSprite(STOCK_W, backgroundH);
        pmSplit.resizePmBoxFixed(pmH);
        return new Dimension(STOCK_W, targetH);
    }

    // Revert to stock. Pins UNIVERSE absolute at stock size (visually identical to native MINUS-fill;
    // the next engine chatbox rebuild restores the native mode). Only ever writes known stock values,
    // so it is safe to call mid-layout-swap (548:11 persists across logout).
    void restore() {
        Widget slot = client.getWidget(InterfaceID.Toplevel.CHAT_CONTAINER);
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (slot != null) {
            boolean stock = slot.getOriginalY() == STOCK_Y && slot.getOriginalHeight() == STOCK_H
                && (universe == null || (universe.getWidth() == STOCK_W && universe.getHeight() == STOCK_H));
            if (!stock) sizeChat(slot, universe, STOCK_Y, STOCK_H);
        }
        sizeViewport(client.getWidget(InterfaceID.Toplevel.MAIN), STOCK_MAIN_H);
        pmSplit.resizePmBoxFixed(STOCK_Y - MAIN_TOP); // Stock split-PM position (bottom at stock chat top 338)
        bgGraphic.revertBakedSprite();
        bgGraphic.destroyBorder();
    }

    private static void sizeChat(Widget slot, Widget universe, int y, int h) {
        slot.setOriginalY(y);
        slot.setOriginalHeight(h);
        slot.revalidate();

        if (universe == null) return;
        // Pin absolute: its native MINUS-fill resolves against the client root, not the slot
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
}
