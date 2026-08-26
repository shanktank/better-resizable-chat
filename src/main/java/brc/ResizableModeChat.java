package brc;

import brc.internal.ChatGeometry;
import brc.internal.ChatRebuild;
import brc.internal.RawScripts;
import brc.internal.SizeClamps;
import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import java.awt.Dimension;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ResizableModeChat {
    private final Client client;
    private final ChatResizerConfig config;
    private final SecondarySize swapSize;
    private final ChatBackgroundSprite bgGraphic;
    private final PrivateMessageSplit pmSplit;
    private final ChatDialogModals dialogModals;
    private final TopLevelModals mainModals;
    private final RuneLiteHudAnchors hudAnchors;
    private final RuneLiteMovedChat movedChat;

    private int lastOpenTab; // Tab open before the chat was hidden, reopened on unhide
    private boolean lastCollapsed; // Collapse state the last apply() sized the slot to
    private boolean relayoutNeeded; // Collapse changed the band above the chat
    private boolean enablePending; // onEnable ran before the layout swap handed the chatbox over

    @Inject
    ResizableModeChat(
            Client client, ChatResizerConfig config, SecondarySize swapSize,
            ChatBackgroundSprite bgGraphic, PrivateMessageSplit pmSplit,
            ChatDialogModals dialogModals, TopLevelModals mainModals,
            RuneLiteHudAnchors hudAnchors, RuneLiteMovedChat movedChat
    ) {
        this.client = client;
        this.config = config;
        this.swapSize = swapSize;
        this.bgGraphic = bgGraphic;
        this.pmSplit = pmSplit;
        this.dialogModals = dialogModals;
        this.mainModals = mainModals;
        this.hudAnchors = hudAnchors;
        this.movedChat = movedChat;
    }

    // Adopt the chat size, then re-fit and re-wrap; rebuilding mid-swap would blacken a transparent chat
    void onEnable() {
        if (apply(swapSize.effectiveHeightChange() == 0 && swapSize.effectiveWidthChange() == 0) == null) {
            enablePending = true; // Mid-swap; retry on a later frame, once the slot is ours
        } else {
            ChatRebuild.now(client, RawScripts.RESIZES_CHAT); // Re-fits an already-open dialog group too, not just the text
            mainModals.relayout();
        }
    }

    // True once when an onEnable bailed mid-swap and still owes its rebuild
    boolean consumeEnablePending() {
        boolean pending = enablePending;
        enablePending = false;
        return pending;
    }

    // Apply resizes in resizable layout; returns the applied slot size, or null if widgets missing/mid-swap
    Dimension apply(boolean force) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return null;
        Widget slot = universe.getParent(); // Should be InterfaceID.ToplevelPreEoc.CHAT_CONTAINER
        if (slot == null || slot.getId() == InterfaceID.Toplevel.CHAT_CONTAINER) return null; // Not loaded or mid-swap

        boolean dialogOpen = dialogModals.isDialogOpen();
        int widthChange = SizeClamps.clamp(swapSize.effectiveWidthChange(), false, false, dialogOpen, config);
        int heightChange = SizeClamps.clamp(effectiveHeightChange(dialogOpen), false, mainModals.isModalOpen(), dialogOpen, config);

        int openTab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        boolean collapsed = openTab == RawScripts.COLLAPSED_TAB;
        if (!collapsed) lastOpenTab = openTab; // Tracked live, so an unhide reopens the tab whichever way the hide happened
        if (collapsed != lastCollapsed) {
            lastCollapsed = collapsed;
            relayoutNeeded = true; // Everything reserving against the chat slot has to re-fit to the new band
        }

        int slotW = ChatGeometry.CHATBOX_SPRITE_W + widthChange;
        // Bottomed at 0: the height floor runs the slot out entirely, background first, then the tab bar
        int slotH = Math.max(0, ChatGeometry.CHATBOX_SLOT_H + heightChange);
        int backgroundH = Math.max(0, ChatGeometry.CHATBOX_SPRITE_H + heightChange);

        hudAnchors.sync(heightChange); // Vertically shift RuneLite's HUD anchors
        movedChat.sync(slot, slotW, slotH); // Hold a RuneLite-moved chat's bottom edge still through the resize

        if (!force &&
            slot.getWidth() == slotW && slot.getHeight() == slotH &&
            universe.getWidth() == slotW && universe.getHeight() == slotH &&
            chatArea.getWidth() == slotW &&
            bgGraphic.tabBarMatches(widthChange)
        ) { // Short-circuit but still make some assurances
            bgGraphic.resizeTabBar(widthChange);
            bgGraphic.syncBackground(slotW, backgroundH);
            if (dialogOpen) dialogModals.centerDialogs();
            bgGraphic.syncBorder(chatArea, false); // Recreate if dropped, else re-sync visibility
            pmSplit.resizePmBox(slotW);
            sizeHpBarBand(slotH);
            return new Dimension(slotW, slotH);
        }

        // Resize the toplevel chat slot, propagates to message layer and CHATAREA's height
        slot.setSize(slotW, slotH);
        slot.revalidate();

        // Universe's minus fill resolves against client root, pin as absolute
        universe.setSize(slotW, slotH, WidgetSizeMode.ABSOLUTE, WidgetSizeMode.ABSOLUTE);
        universe.setForcedPosition(0, 0);
        universe.revalidate();

        chatArea.setOriginalWidth(slotW); // Chat area's width is absolute, does not follow universe

        bgGraphic.resizeTabBar(widthChange); // Must resize before cascading revalidate
        Widgets.revalidateChildren(universe);
        bgGraphic.syncBorder(chatArea, true);
        bgGraphic.syncBackground(slotW, backgroundH);
        if (dialogOpen) dialogModals.centerDialogs(); // Mounted dialog groups need placing by hand
        pmSplit.resizePmBox(slotW);
        sizeHpBarBand(slotH);

        return new Dimension(slotW, slotH);
    }

    // Revert the resizes; the collapse itself is engine-owned state, so leave it alone and just drop our tracking
    void restore() {
        lastCollapsed = false;
        relayoutNeeded = false;
        enablePending = false;

        movedChat.restore(); // Before the slot goes back to stock height, which is what the point is handed back for

        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return;

        // Don't touch the fixed slot from the resizable path
        Widget slot = universe.getParent();
        if (slot != null && slot.getId() != InterfaceID.Toplevel.CHAT_CONTAINER) {
            slot.setSize(ChatGeometry.CHATBOX_SPRITE_W, ChatGeometry.CHATBOX_SLOT_H);
            slot.setForcedPosition(-1, -1);
            slot.revalidate();
        }

        universe.setSize(0, 0, WidgetSizeMode.MINUS, WidgetSizeMode.MINUS);
        universe.setForcedPosition(-1, -1);
        universe.revalidate();

        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea != null) chatArea.setOriginalWidth(ChatGeometry.CHATBOX_SPRITE_W);

        bgGraphic.resizeTabBar(0);
        bgGraphic.destroyBorder();
        pmSplit.restorePmBox();
        Widgets.revalidateChildren(universe);
        bgGraphic.revertBackground(); // After the cascade, so the container resolves against a settled chat area
        dialogModals.resetDialogPositions();
        hudAnchors.restore();

        // Only the literal height was overridden, so a plain revalidate recomputes the stock MINUS reserve
        Widget dodger = client.getWidget(InterfaceID.HpbarHud.HPDODGER);
        if (dodger != null) dodger.revalidate();
    }

    // The chat rebuild re-cuts a transparent chat's gradient off the height it reads going in, so re-assert after it
    void restoreBackground() {
        bgGraphic.revertBackground();
    }

    // Hide/unhide via the native tab collapse. Clicking the active tab is what collapses, so hide by clicking whichever
    // tab is up and unhide by clicking the remembered one; a stale index on a showing chat would just switch tabs.
    void toggleHidden() {
        int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        client.runScript(RawScripts.CHAT_TAB_CLICKED, 1, tab == RawScripts.COLLAPSED_TAB ? lastOpenTab : tab);
    }

    // RuneLite clamps the native boss health bar to this widget, whose bottom edge is otherwise the stock chat top
    private void sizeHpBarBand(int slotH) {
        Widget dodger = client.getWidget(InterfaceID.HpbarHud.HPDODGER);
        if (dodger == null) return; // No health bar on screen, group isn't mounted
        Widget root = dodger.getParent();
        if (root == null) return;

        int h = Math.max(0, root.getHeight() - slotH - dodger.getRelativeY()); // Bottom edge lands on the chat top
        if (dodger.getHeight() != h) Widgets.setHeight(dodger, h);
    }

    // True once when collapsing/uncollapsing has changed the band above the chat
    boolean consumeRelayoutNeeded() {
        boolean needed = relayoutNeeded;
        relayoutNeeded = false;
        return needed;
    }

    // True while the engine's native tab collapse has the chat hidden
    boolean isCollapsed() {
        return client.getVarcIntValue(VarClientID.CHAT_VIEW) == RawScripts.COLLAPSED_TAB;
    }

    // Height change before dialog adjustments: the min keeps a collapse a shrink, and a dialog drops
    // that shrink alone while the collapse itself stands (FixedModeChat has the twin of this)
    int effectiveHeightChange(boolean dialogOpen) {
        int configured = swapSize.effectiveHeightChange();
        return isCollapsed() && !dialogOpen ? Math.min(-ChatGeometry.CHATBOX_SPRITE_H, configured) : configured;
    }
}