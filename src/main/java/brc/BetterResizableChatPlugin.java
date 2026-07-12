package brc;

import brc.drag.DragPreview;
import brc.drag.DragResizer;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.HotkeyListener;
import com.google.inject.Provides;
import java.awt.Dimension;
import javax.inject.Inject;

@PluginDescriptor(
    name = "Chat Resizer",
    description = "Resize chat box in resizable or fixed layout.",
    tags = {"chat", "chatbox", "private", "message", "extend", "resize", "resizable", "resizeable",
            "scale", "stretch", "width", "height", "ui", "better", "fixed", "hide", "toggle"},
    conflicts = {"Resizable Chat", "Fixed Mode Hide Chat"}
)
public class BetterResizableChatPlugin extends Plugin {
    private static final int TOPLEVEL_RELAYOUT_SCRIPT = 1972;
    private static final int RESIZES_CHAT_SCRIPT = 924;
    private static final int REWRAPS_CHAT_SCRIPT = 663;
    static final int CHAT_TAB_CLICKED_SCRIPT = 175; // Handles clicks on the chat tab buttons

    static final int CHATBOX_SPRITE_W = 519;
    static final int CHATBOX_SPRITE_H = 142;
    static final int CHATBOX_SLOT_H = 165; // Chat box plus tabs bar

    static final int COLLAPSED_TAB = 1337; // ID of sentinel "tab" when chat is collapsed

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private BetterResizableChatConfig config;
    @Inject private ConfigManager configManager;
    @Inject private KeyManager keyManager;
    @Inject private MouseManager mouseManager;
    @Inject private OverlayManager overlayManager;
    @Inject private TooltipManager tooltipManager;

    private RuneLiteHudAnchors hudAnchors;
    private TopLevelModals mainModals;
    private ChatBackgroundGraphic bgGraphic;
    private ChatDialogBoxes dialogBoxes;
    private PrivateMessageSplit privateSplit;
    private FixedModeChat fixedChat;
    private ChatScrollRetainer scrollKeep;
    private DragResizer dragResizer;
    private DragPreview dragPreview;

    private boolean wasDragging;
    private int lastOpenTab;

    // The eventbus registers on the EDT right after startUp() returns, but the enable pass is queued for the next
    // client tick; a BeforeRender in that gap would resize the chat without a rebuild and draw text off by the
    // height change. Handlers stay inert until the enable pass has run. Volatile: written on EDT in shutDown.
    private volatile boolean active;

    private final HotkeyListener hideChatHotkey = new HotkeyListener(() -> config.toggleShowChat()) {
        @Override public void hotkeyPressed() { clientThread.invoke(BetterResizableChatPlugin.this::toggleChatHidden); }
    };

    @Provides
    private BetterResizableChatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BetterResizableChatConfig.class);
    }

    private void onEnableResizable() {
        scrollKeep.sync();
        apply(config.heightChange() == 0 && config.widthChange() == 0);
        rebuildChatNow(REWRAPS_CHAT_SCRIPT);
        mainModals.relayout();
        scrollKeep.sync();
        active = true;
    }

    private void onEnableFixed() {
        scrollKeep.sync();
        apply(true);
        if (fixedChat.consumeRebuildNeeded()) rebuildChatNow(REWRAPS_CHAT_SCRIPT); // Deferring to refreshChat would draw stale line anchors for a frame
        scrollKeep.sync();
        active = true;
    }

    // Clear last rebuild stamp so rebuild happens now
    private void rebuildChatNow(int rebuildScript) {
        if (client.getWidget(InterfaceID.Chatbox.SCROLLAREA) == null) return; // Chatbox not live (login/hop)
        client.setVarcIntValue(VarClientID.CHAT_LASTREBUILD, client.getGameCycle() - 1);
        client.runScript(rebuildScript);
    }

    @Override
    protected void startUp() {
        hudAnchors = new RuneLiteHudAnchors(client, config);
        mainModals = new TopLevelModals(client);
        bgGraphic = new ChatBackgroundGraphic(client, config);
        dialogBoxes = new ChatDialogBoxes(client);
        privateSplit = new PrivateMessageSplit(client, config);
        fixedChat = new FixedModeChat(client, config, bgGraphic, privateSplit, dialogBoxes, mainModals);
        scrollKeep = new ChatScrollRetainer(client, dialogBoxes);
        dragResizer = new DragResizer(config, configManager);
        dragPreview = new DragPreview(client, dragResizer, config, tooltipManager);

        keyManager.registerKeyListener(hideChatHotkey);
        dragResizer.migrateDragModifier();
        keyManager.registerKeyListener(dragResizer.getKeyListener());
        mouseManager.registerMouseListener(dragResizer);
        overlayManager.add(dragPreview);

        clientThread.invoke(client.isResized() ? this::onEnableResizable : this::onEnableFixed); // Would invokeAtTickEnd obviate the active flag?
    }

    @Override
    protected void shutDown() {
        active = false; // Handlers are unregistered after this returns; go inert before the queued restore runs
        keyManager.unregisterKeyListener(hideChatHotkey);
        keyManager.unregisterKeyListener(dragResizer.getKeyListener());
        mouseManager.unregisterMouseListener(dragResizer);
        overlayManager.remove(dragPreview);
        dragResizer.reset();

        clientThread.invoke(() -> {
            scrollKeep.sync();
            if (client.isResized()) {
                restore();
                rebuildChatNow(RESIZES_CHAT_SCRIPT); // Clean up sprite + re-wrap at stock width
                mainModals.relayout();
            } else {
                fixedChat.restore();
                rebuildChatNow(REWRAPS_CHAT_SCRIPT); // Re-anchor lines to the restored stock height
                if (mainModals.isTopLevelModalOpen()) mainModals.relayout(); // Re-fit an open modal back to the stock band
            }
            scrollKeep.sync();
        });
    }

    @Subscribe
    void onCommandExecuted(CommandExecuted event) {
        if ("testpm".equals(event.getCommand())) {
            String message = "ABCDEFGHIJKLMNO PQRSTUVWXYZ ABCDEFG HIJKLMNOP QRSTU VWX YZ AB CD EF G H I J K L M N O P Q R S T U V W X Y Z";
            if (event.getArguments().length != 0) message = String.join(" ", event.getArguments());
            client.addChatMessage(ChatMessageType.MODPRIVATECHAT, "Test", message, null);
            client.addChatMessage(ChatMessageType.PUBLICCHAT, "Test", message, null);
        } else if ("clearpm".equals(event.getCommand())) {
            ChatLineBuffer buffer = client.getChatLineMap().get(ChatMessageType.MODPRIVATECHAT.getType());
            for (MessageNode node : buffer.getLines().clone()) buffer.removeMessageNode(node); // removeMessageNode mutates the backing array, so clone
            clientThread.invokeLater(() -> client.runScript(ScriptID.SPLITPM_CHANGED)); // Rebuilds both the chat box and the PM box
        }
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!active || !BetterResizableChatConfig.GROUP.equals(event.getGroup()) || dragResizer.isDragging()) return;
        clientThread.invoke(() -> {
            scrollKeep.sync();
            if (!config.fixedTabCollapse()) fixedChat.setCollapsed(false); // Don't leave chat stuck collapsed when disabled
            apply(true);
            if (client.isResized()) {
                rebuildChatNow(REWRAPS_CHAT_SCRIPT);
                String key = event.getKey();
                if (key.equals(BetterResizableChatConfig.WIDTH_CHANGE) || (key.equals(BetterResizableChatConfig.HEIGHT_CHANGE) && mainModals.isModalOpen())) {
                    mainModals.relayout(); // Re-fit bank, restack modern layout's inventory tabs
                }
            } else if (fixedChat.consumeRebuildNeeded()) {
                rebuildChatNow(REWRAPS_CHAT_SCRIPT); // Deferring to refreshChat would draw stale line anchors for a frame
            }
            scrollKeep.sync();
        });
    }

    // Show or hide chat in fixed layout when active chat tab button is clicked
    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        if (active) fixedChat.onMenuOptionClicked(event);
    }

    @Subscribe
    private void onVarbitChanged(VarbitChanged event) {
        if (active && event.getVarbitId() == VarbitID.CHATBOX_TRANSPARENCY && event.getValue() == 1) bgGraphic.destroyBorder();
    }

    @Subscribe
    private void onResizeableChanged(ResizeableChanged event) {
        if (!active) return; // The queued enable pass reads client.isResized() when it runs, so it lands in the right mode
        active = false; // Go inert until the swapped mode's enable pass has run

        if (event.isResized()) { // Can flip before toplevel swap completes, defer swap a cycle to undo current edits before applying new ones
            fixedChat.restore(); // Leaving fixed, reset CHAT_CONTAINER (persists across logout)
            clientThread.invokeLater(this::onEnableResizable);
        } else {
            restore(); // Leaving resizable
            clientThread.invokeLater(this::onEnableFixed);
        }
    }

    @Subscribe
    private void onScriptPreFired(ScriptPreFired event) {
        if (!active) return;

        if (config.adjustHudAnchors() && config.heightChange() > 0 && !mainModals.isModalOpen() && mainModals.isTopLevelModalOpen()) {
            hudAnchors.forceStockRendered(); // Top-level modal is open, pretend anchors haven't been moved so it draws itself with full size
        }

        // Fixed mode: a modal mounted this tick and its onLoad hook is about to fire, temp-shrink if necessary
        if (!client.isResized() && !mainModals.isModalOpen() && fixedChat.effectiveHeightChange() > 0 && mainModals.topLevelModalOpenStateChanged()) {
            apply(false);
            clientThread.invokeAtTickEnd(this::applyOverlayTransition); // Relayout + realign once the open salvo settles
        }

        int id = event.getScriptId();
        if (id == ScriptID.BUILD_CHATBOX || id == ScriptID.SPLITPM_CHANGED || id == TOPLEVEL_RELAYOUT_SCRIPT) apply(false);
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (!active) return;

        int id = event.getScriptId();
        if (id == ScriptID.TOPLEVEL_REDRAW) apply(false); // Fires when switching tabs on Character Summary tab, resets background
        if (id == ScriptID.MESSAGE_LAYER_OPEN) apply(false); // Re-center text cursor when opening RuneLite input prompts (e.g. quest search)
    }

    @Subscribe
    private void onBeforeRender(BeforeRender event) {
        if (!active) return;

        boolean dragging = dragResizer.isDragging();
        if (dragging && !wasDragging) fixedChat.setCollapsed(false); // Drag writes config height, which collapse would override

        if (overlayTransitioned()) {
            clientThread.invokeLater(this::applyOverlayTransition); // Fallback for overlays that open without a widget event
        } else if (dragging) {
            Dimension size = apply(false);
            // Fixed mode re-wraps within apply() (width is locked); the rewrap script + relayout are resizable-only
            if (client.isResized() && config.liveRewrap() && size != null && !size.equals(dragResizer.getLastDragSize())) client.runScript(RESIZES_CHAT_SCRIPT);
            dragResizer.setLastDragSize(size);
        } else {
            apply(false); // Drift-correct: re-stretch the tab bar/border after a rebuild (e.g. world hop) reverts it
            if (wasDragging && client.isResized()) {
                rebuildChatNow(RESIZES_CHAT_SCRIPT); // Single expensive re-wrap on drag-resize release
                mainModals.relayout(); // Re-fit bank/overlays to the new chat size on release
            }
            dragResizer.setLastDragSize(null);
        }
        wasDragging = dragging;

        // Fixed layout: mirror resizable's collapsed CHAT_VIEW sentinel so incoming messages blink their tab
        if (!client.isResized() && client.getGameState() == GameState.LOGGED_IN) fixedChat.syncCollapsedTab();
        // Fixed layout: the viewport band changed under an open modal
        if (!dragging && fixedChat.consumeRelayoutNeeded()) mainModals.relayout();
        // Fixed layout: rebuild the chatbox after a height change so lines re-anchor to the bottom
        if (!client.isResized() && (!dragging || config.liveRewrap()) && fixedChat.consumeRebuildNeeded()) client.refreshChat();

        scrollKeep.sync(); // Single preservation here

        Widget slot = chatSlot(client);
        dragResizer.update(slot == null ? null : slot.getBounds(), !client.isResized());
    }

    @Subscribe
    private void onWidgetLoaded(WidgetLoaded event) {
        clientThread.invokeAtTickEnd(this::handleOverlayTransition);
    }

    @Subscribe
    private void onWidgetClosed(WidgetClosed event) {
        clientThread.invokeAtTickEnd(this::handleOverlayTransition);
    }

    private void handleOverlayTransition() {
        if (active && overlayTransitioned()) applyOverlayTransition();
    }

    // Chat overlay or toplevel modal just opened or closed; consumes both edge detectors
    private boolean overlayTransitioned() {
        return (dialogBoxes.dialogOpenStateChanged() && dialogAdjustsSize()) || mainModals.topLevelModalOpenStateChanged();
    }

    // Adjust the chat and re-fit modals after an overlay transition
    private void applyOverlayTransition() {
        apply(false);
        if (client.isResized()) { // Resizable-only re-fit + re-wrap; fixed mode is fully re-asserted within apply()
            mainModals.relayout();
            rebuildChatNow(RESIZES_CHAT_SCRIPT);
        } else if (!dragResizer.isDragging() && fixedChat.consumeRelayoutNeeded()) {
            mainModals.relayout(); // Re-fit the open modal to the changed band in the same tick
        }
        scrollKeep.sync();
    }

    // Apply resizes for the current layout
    private Dimension apply(boolean force) {
        return client.isResized() ? applyResizable(force) : fixedChat.apply(force);
    }

    // Apply resizes in resizable layout
    private Dimension applyResizable(boolean force) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return null;
        Widget slot = universe.getParent();
        if (slot == null || slot.getId() == InterfaceID.Toplevel.CHAT_CONTAINER) return null; // Not loaded or mid-swap

        int widthChange = config.widthChange();
        int heightChange = config.heightChange();
        if (mainModals.isModalOpen() && heightChange > 0) heightChange = 0; // Should shrink

        // Unshrink or ungrow for dialog interfaces
        if (dialogBoxes.isDialogOpen()) {
            if (widthChange < 0) widthChange = 0;
            if (heightChange < 0) heightChange = 0;
            if (config.ungrowForDialogs()) {
                if (widthChange > 0) widthChange = 0;
                if (heightChange > 0) heightChange = 0;
            }
        }

        int slotW = CHATBOX_SPRITE_W + widthChange;
        int slotH = CHATBOX_SLOT_H + heightChange;

        hudAnchors.sync(heightChange); // Vertically shift RuneLite's HUD anchors

        if (!force &&
            slot.getWidth() == slotW && slot.getHeight() == slotH &&
            universe.getWidth() == slotW && universe.getHeight() == slotH &&
            chatArea.getWidth() == slotW &&
            bgGraphic.tabBarMatches(widthChange)
        ) {
            bgGraphic.resizeTabBar(widthChange);
            bgGraphic.zoomBakedSprite(slotW, CHATBOX_SPRITE_H + heightChange);
            dialogBoxes.centerDialogs();
            if (!bgGraphic.borderPresent(chatArea)) bgGraphic.drawBorder(chatArea); // In case of hop/enable with 0/0 change
            privateSplit.resizePmBox(slotW);
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
        revalidateChildren(universe);
        bgGraphic.drawBorder(chatArea);
        bgGraphic.zoomBakedSprite(slotW, CHATBOX_SPRITE_H + heightChange);
        dialogBoxes.centerDialogs(); // Some dialog interfaces need manual centering
        privateSplit.resizePmBox(slotW);

        return new Dimension(slotW, slotH);
    }

    // Revert the resizes
    private void restore() {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return;

        Widget slot = universe.getParent();
        if (slot != null && slot.getId() != InterfaceID.Toplevel.CHAT_CONTAINER) { // Don't touch the fixed slot from the resizable path
            slot.setSize(CHATBOX_SPRITE_W, CHATBOX_SLOT_H);
            slot.setForcedPosition(-1, -1);
            slot.revalidate();
        }

        universe.setSize(0, 0, WidgetSizeMode.MINUS, WidgetSizeMode.MINUS);
        universe.setForcedPosition(-1, -1);
        universe.revalidate();

        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea != null) chatArea.setOriginalWidth(CHATBOX_SPRITE_W);

        bgGraphic.resizeTabBar(0);
        bgGraphic.destroyBorder();
        bgGraphic.revertBakedSprite();
        privateSplit.resizePmBox(null);
        revalidateChildren(universe);
        dialogBoxes.resetDialogPositions();
        hudAnchors.restore();
    }

    // Hide or unhide chat on keybind
    private void toggleChatHidden() {
        if (!active || client.getGameState() != GameState.LOGGED_IN) return;

        if (client.isResized()) {
            int tab = client.getVarcIntValue(VarClientID.CHAT_VIEW);
            if (tab != COLLAPSED_TAB) lastOpenTab = tab; // Save/restore the tab that was open before hiding with keybind
            client.runScript(CHAT_TAB_CLICKED_SCRIPT, 1, lastOpenTab);
        } else { // Fixed layout
            if (config.fixedTabCollapse()) fixedChat.setCollapsed(!fixedChat.isCollapsed());
        }
    }

    // True if a dialog opening/closing would temporarily unshrink or ungrow the chat in the current layout
    private boolean dialogAdjustsSize() {
        if (!client.isResized()) {
            int h = fixedChat.effectiveHeightChange();
            return h < 0 || (config.ungrowForDialogs() && h > 0);
        } else {
            int w = config.widthChange(), h = config.heightChange();
            return w < 0 || h < 0 || (config.ungrowForDialogs() && (w > 0 || h > 0));
        }
    }

    // The toplevel slot the chatbox occupies; fixed: CHAT_CONTAINER, resizable: the layout's chat slot
    public static Widget chatSlot(Client client) {
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        return universe == null ? null : universe.getParent();
    }

    static void revalidateChildren(Widget widget) {
        revalidateAll(widget.getStaticChildren());
        revalidateAll(widget.getDynamicChildren());
    }

    static void revalidateAll(Widget[] children) {
        if (children == null) return;
        for (Widget child : children) {
            if (child == null) continue;
            child.revalidate();
            if (child.getId() == InterfaceID.Chatbox.SCROLLAREA) continue; // Gets handled for us
            revalidateChildren(child);
        }
    }

    @SuppressWarnings("deprecation")
    public static void setHeight(Widget widget, int height) {
        widget.setHeight(height);
    }

    @SuppressWarnings("deprecation")
    public static void setWidth(Widget widget, int width) {
        widget.setWidth(width);
    }
}