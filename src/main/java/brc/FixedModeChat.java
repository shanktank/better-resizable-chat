package brc;

import brc.internal.ChatGeometry;
import brc.internal.ChatRebuild;
import brc.internal.RawScripts;
import brc.internal.SizeClamps;
import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.WidgetNode;
import net.runelite.api.annotations.Component;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.eventbus.Subscribe;
import lombok.Getter;
import lombok.Setter;
import java.awt.Dimension;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FixedModeChat {
    private static final int STOCK_Y = ChatGeometry.FIXED_CHAT_Y; // Stock top of CHAT_CONTAINER in fixed layout
    private static final int STOCK_W = ChatGeometry.CHATBOX_SPRITE_W;
    private static final int STOCK_H = ChatGeometry.CHATBOX_SLOT_H;
    private static final int TAB_BAR_H = STOCK_H - ChatGeometry.CHATBOX_SPRITE_H;
    private static final int STOCK_BOTTOM = STOCK_Y + STOCK_H;

    // Viewport container, used to adjust viewport as chat height shrinks
    private static final int MAIN_TOP = 4;
    private static final int STOCK_MAIN_H = 334;

    // Viewport's side border sprites, must be extended as chat height shrinks
    private static final int RIGHT_BORDER_TOP = 205;
    private static final int RIGHT_BORDER_H = 133;

    // Value of menu option when a chat tab is left-clicked, use as marker for hiding/unhiding chat
    private static final String SWITCH_TAB = "Switch tab";

    private final Client client;
    private final ChatResizerConfig config;
    private final SecondarySize swapSize;
    private final ChatBackgroundGraphic bgGraphic;
    private final PrivateMessageSplit pmSplit;
    private final ChatDialogBoxes dialogBoxes;
    private final TopLevelModals mainModals;

    private int lastTargetY = STOCK_Y;
    private boolean relayoutNeeded; // The viewport band changed under an open modal
    private boolean rebuildNeeded; // Chat height changed; lines keep stale positions until a chatbox rebuild

    @Getter @Setter private boolean collapsed; // Chat collapsed to just the tab bar via clicking the open chat tab
    private int savedTab = -1; // Open tab saved while collapse parks CHAT_VIEW on the sentinel

    @Inject
    FixedModeChat(
        Client client, ChatResizerConfig config, SecondarySize swapSize,
        ChatBackgroundGraphic bgGraphic, PrivateMessageSplit pmSplit,
        ChatDialogBoxes dialogBoxes, TopLevelModals mainModals
    ) {
        this.client = client;
        this.config = config;
        this.swapSize = swapSize;
        this.bgGraphic = bgGraphic;
        this.pmSplit = pmSplit;
        this.dialogBoxes = dialogBoxes;
        this.mainModals = mainModals;
    }

    void onEnable() {
        apply(true);
        if (consumeRebuildNeeded()) ChatRebuild.now(client, RawScripts.REWRAPS_CHAT); // Ensure no stale positioning
    }

    // Returns the applied slot size, or null if not in fixed layout / widgets missing
    Dimension apply(boolean force) {
        Widget slot = client.getWidget(InterfaceID.Toplevel.CHAT_CONTAINER);
        if (slot == null) return null; // Not fixed layout, or frame not built yet
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return null;

        boolean modalOpen = mainModals.isModalOpen();
        boolean dialogOpen = dialogBoxes.isDialogOpen();
        int rawHeightChange = effectiveHeightChange(dialogOpen);
        int heightChange = SizeClamps.clamp(rawHeightChange, true, modalOpen, dialogOpen, config);
        // Viewport keeps its pre-dialog size to avoid a camera jerk, so this is the height with no dialog
        int heldHeightChange = dialogOpen && !modalOpen ? effectiveHeightChange(false) : heightChange;

        // Clamp height to [gone, full frame]; bottom edge stays pinned, top grows up toward 0
        int targetH = Math.min(STOCK_BOTTOM, Math.max(0, STOCK_H + heightChange));
        int targetY = STOCK_BOTTOM - targetH;
        int heldH = Math.min(STOCK_BOTTOM, Math.max(0, STOCK_H + heldHeightChange));
        int heldY = STOCK_BOTTOM - heldH; // Chat top the viewport is held to while a dialog is open

        if (targetY != lastTargetY) {
            lastTargetY = targetY;
            rebuildNeeded = true;
            if (mainModals.isTopLevelModalOpen()) relayoutNeeded = true; // Modal must re-fit to the changed band
        }

        int backgroundH = Math.max(0, targetH - TAB_BAR_H); // Excludes the tab bar, and is gone once shrink reaches it
        int minViewH = config.fixedAdjustViewport() ? 0 : STOCK_MAIN_H; // Handle chat height above stock
        int chatBandH = Math.max(minViewH, targetY - MAIN_TOP); // Band above the chat the viewport must fill
        int mainH = Math.max(chatBandH, heldY - MAIN_TOP); // Viewport keeps held band but must always reach chat top
        int viewportBottom = MAIN_TOP + mainH;
        int pmH = targetY - MAIN_TOP; // Split-PM box height so its bottom lands at the chat top

        Widget main = client.getWidget(InterfaceID.Toplevel.MAIN);

        if (!force &&
            slot.getHeight() == targetH && slot.getRelativeY() == targetY &&
            universe.getWidth() == STOCK_W && universe.getHeight() == targetH &&
            (main == null || main.getHeight() == mainH)
        ) {
            bgGraphic.zoomBakedSprite(STOCK_W, backgroundH);
            if (!bgGraphic.borderPresent(chatArea)) bgGraphic.drawBorder(chatArea); // In case of hop/rebuild
            bgGraphic.syncBorderVisibility();
            extendViewportBorders(viewportBottom); // Re-assert side borders (engine resets them on rebuilds)
            pmSplit.resizePmBoxFixed(pmH); // Re-assert split-PM position (engine resets it on rebuilds)
            if (dialogOpen) dialogBoxes.centerDialogs();
            return new Dimension(STOCK_W, targetH);
        }

        sizeChat(slot, universe, targetY, targetH);
        sizeViewport(main, mainH);
        extendViewportBorders(viewportBottom);
        bgGraphic.drawBorder(chatArea);
        bgGraphic.zoomBakedSprite(STOCK_W, backgroundH);
        pmSplit.resizePmBoxFixed(pmH);
        if (dialogOpen) dialogBoxes.centerDialogs(); // Mounted dialog groups need placing by hand, as in resizable
        return new Dimension(STOCK_W, targetH);
    }

    // True once when the viewport band has changed under an open modal
    boolean consumeRelayoutNeeded() {
        boolean needed = relayoutNeeded;
        relayoutNeeded = false;
        return needed;
    }

    // True once when the chat height has changed and lines need re-laying out
    boolean consumeRebuildNeeded() {
        boolean needed = rebuildNeeded;
        rebuildNeeded = false;
        return needed;
    }

    // Revert to stock using absolute universe, next engine chatbox rebuild fully restore native mode
    void restore() {
        collapsed = false;
        if (savedTab != -1 && client.getVarcIntValue(VarClientID.CHAT_VIEW) == RawScripts.COLLAPSED_TAB)
            client.setVarcIntValue(VarClientID.CHAT_VIEW, savedTab); // Must unpark with raw write
        savedTab = -1;
        lastTargetY = STOCK_Y;
        relayoutNeeded = false;
        rebuildNeeded = false;
        Widget slot = client.getWidget(InterfaceID.Toplevel.CHAT_CONTAINER);
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (slot != null && !isStock(slot, universe)) sizeChat(slot, universe, STOCK_Y, STOCK_H);
        sizeViewport(client.getWidget(InterfaceID.Toplevel.MAIN), STOCK_MAIN_H);
        extendViewportBorders(STOCK_Y); // Reset side borders to stock
        pmSplit.resizePmBoxFixed(STOCK_Y - MAIN_TOP);
        dialogBoxes.resetDialogPositions();
        bgGraphic.revertBakedSprite();
        bgGraphic.destroyBorder();
    }

    // Park chat view on the collapsed sentinel so messages blink their tab's stone, reopen the saved tab on uncollapse
    void syncCollapsedTab() {
        int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        if (collapsed) {
            if (tab != RawScripts.COLLAPSED_TAB) {
                if (savedTab == -1) savedTab = tab; // Avoid clearing parked tab on dialog open
                client.setVarcIntValue(VarClientID.CHAT_VIEW, RawScripts.COLLAPSED_TAB);
                client.runScript(RawScripts.REDRAW_CHAT_BUTTONS); // Repaint tab as unselected
            }
        } else if (tab == RawScripts.COLLAPSED_TAB) {
            if (savedTab != -1) { // Uncollapsed by hotkey/config/drag; reopen and clear blink
                client.runScript(RawScripts.CHAT_TAB_CLICKED, 1, savedTab);
                savedTab = -1;
            }
        } else {
            savedTab = -1; // Uncollapsed by clicking a tab; the click script already opened it
        }
    }

    // Show or hide chat in fixed layout when active chat tab button is clicked
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (client.isResized() || !config.fixedTabCollapse() || !event.getMenuOption().equals(SWITCH_TAB)) return;
        int tab = ChatBackgroundGraphic.tabIndexOf(event.getParam1()); // Param1 is the clicked widget's component ID
        if (tab != -1) setCollapsed(!isCollapsed() && tab == client.getVarcIntValue(VarClientID.CHAT_VIEW));
    }

    // Height change before dialog adjustments: swap-aware config value, or full shrink while tab-collapsed. The min
    // keeps a collapse a shrink. A dialog drops the collapse's shrink alone, ungated, while the collapse itself
    // stands; the gate still governs the configured height underneath (ResizableModeChat has the twin of this).
    int effectiveHeightChange(boolean dialogOpen) {
        int configured = swapSize.effectiveFixedHeightChange();
        return collapsed && !dialogOpen ? Math.min(-ChatGeometry.CHATBOX_SPRITE_H, configured) : configured;
    }

    private static boolean isStock(Widget slot, Widget universe) {
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
        Widgets.revalidateChildren(universe); // Reflow chat area, tabs, scroll, background
    }

    // Grow/shrink the 3D viewport container so a shrunk chat exposes game instead of a black band
    private void sizeViewport(Widget main, int h) {
        if (main == null || main.getOriginalHeight() == h) return;
        main.setOriginalHeight(h);
        main.revalidate();
        Widgets.revalidateChildren(main); // Reflow the viewport + in-scene overlays
        sizeAtmosphere();
    }

    // Resize atmosphere overlays to cover newly added viewport area
    private void sizeAtmosphere() {
        Widget slot = client.getWidget(InterfaceID.Toplevel.OVERLAY_ATMOSPHERE);
        if (slot == null) return;
        WidgetNode mounted = client.getComponentTable().get(InterfaceID.Toplevel.OVERLAY_ATMOSPHERE);
        if (mounted == null) return; // Current area has no tint
        Widget root = Widgets.mountedRoot(client, mounted.getId());
        if (root == null) return;
        Widgets.setWidth(root, slot.getWidth());
        Widgets.setHeight(root, slot.getHeight());
        Widgets.revalidateChildren(root); // Nested tint layers follow the root
    }

    // Extend the viewport's left/right border sprites down to the viewport bottom
    private void extendViewportBorders(int vpB) {
        resizeBorder(InterfaceID.Toplevel.CONTROL, MAIN_TOP, STOCK_MAIN_H, vpB, false);
        resizeBorder(InterfaceID.Toplevel.GAMEFRAME_GRAPHIC5, RIGHT_BORDER_TOP, RIGHT_BORDER_H, vpB, true);
    }

    private void resizeBorder(@Component int widgetId, int top, int stockH, int bottom, boolean tile) {
        Widget border = client.getWidget(widgetId);
        if (border == null) return;
        int h = Math.max(stockH, bottom - top); // Stay stock when the viewport shrinks; follow it down when grown
        if (border.getOriginalHeight() == h) return;
        if (tile) border.setSpriteTiling(true); // Repeat the texture instead of stretching its detail
        border.setOriginalHeight(h);
        border.revalidate();
    }
}