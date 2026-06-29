package brc;

import brc.drag.DragPreview;
import brc.drag.DragResizer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
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
import com.google.inject.Provides;
import java.awt.Dimension;
import javax.inject.Inject;

@PluginDescriptor(
    name = "Chat Resizer",
    description = "Resize chat box in resizable or fixed layout.",
    tags = {"chat", "chatbox", "private", "message", "extend", "resize", "resizable", "resizeable", "scale", "stretch", "width", "height", "ui", "better", "fixed"},
    conflicts = {"Resizable Chat"}
)
public class BetterResizableChatPlugin extends Plugin {
    private static final int TOPLEVEL_RELAYOUT_SCRIPT = 1972;
    private static final int RESIZES_CHAT_SCRIPT = 924;
    private static final int REWRAPS_CHAT_SCRIPT = 663;

    public static final int CHATBOX_SPRITE_W = 519;
    public static final int CHATBOX_SPRITE_H = 142;
    public static final int CHATBOX_SLOT_H = 165; // Chat box plus tabs bar

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
    private PrivateMessageSplit pmSplit;
    private FixedModeChat fixedChat;
    private ChatScrollRetainer scrollKeep;
    private DragResizer dragResizer;
    private DragPreview dragPreview;
    private boolean wasDragging;

    @Provides
    private BetterResizableChatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BetterResizableChatConfig.class);
    }

    private void onEnableResizable() {
        scrollKeep.sync();
        apply(config.heightChange() == 0 && config.widthChange() == 0);
        client.runScript(REWRAPS_CHAT_SCRIPT);
        mainModals.relayout();
        scrollKeep.sync();
    }

    private void onEnableFixed() {
        scrollKeep.sync();
        apply(true);
        scrollKeep.sync();
    }

    @Override
    protected void startUp() {
        hudAnchors = new RuneLiteHudAnchors(client, config);
        mainModals = new TopLevelModals(client);
        bgGraphic = new ChatBackgroundGraphic(client);
        dialogBoxes = new ChatDialogBoxes(client);
        pmSplit = new PrivateMessageSplit(client, config);
        fixedChat = new FixedModeChat(client, config, bgGraphic, pmSplit, dialogBoxes);
        scrollKeep = new ChatScrollRetainer(client, dialogBoxes);
        dragResizer = new DragResizer(config, configManager);
        dragPreview = new DragPreview(dragResizer, config, tooltipManager);

        dragResizer.migrateDragModifier();
        keyManager.registerKeyListener(dragResizer.getKeyListener());
        mouseManager.registerMouseListener(dragResizer);
        overlayManager.add(dragPreview);

        clientThread.invoke(client.isResized() ? this::onEnableResizable : this::onEnableFixed);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(dragResizer.getKeyListener());
        mouseManager.unregisterMouseListener(dragResizer);
        overlayManager.remove(dragPreview);

        dragResizer.reset();

        clientThread.invoke(() -> {
            scrollKeep.sync();
            if (client.isResized()) {
                restore();
                client.runScript(RESIZES_CHAT_SCRIPT); // Clean up sprite + re-wrap at stock width
                mainModals.relayout();
            } else {
                fixedChat.restore();
            }
            scrollKeep.sync();
        });
    }

    @Subscribe
    void onCommandExecuted(CommandExecuted event) {
        if ("testpm".equals(event.getCommand())) { // Test add a private message
            String message = "ABCDEFGHIJKLMNO PQRSTUVWXYZ ABCDEFG HIJKLMNOP QRSTU VWX YZ AB CD EF G H I J K L M N O P Q R S T U V W X Y Z";
            if (event.getArguments().length != 0) message = String.join(" ", event.getArguments());
            client.addChatMessage(ChatMessageType.MODPRIVATECHAT, "Test", message, null);
            client.addChatMessage(ChatMessageType.PUBLICCHAT, "Test", message, null);
        }
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!BetterResizableChatConfig.GROUP.equals(event.getGroup()) || dragResizer.isDragging()) return;
        clientThread.invoke(() -> {
            scrollKeep.sync();
            apply(true);
            if (client.isResized()) {
                client.runScript(REWRAPS_CHAT_SCRIPT);
                if (event.getKey().equals(BetterResizableChatConfig.HEIGHT_CHANGE) && mainModals.isModalOpen()) mainModals.relayout(); // Re-fit bank
            }
            scrollKeep.sync();
        });
    }

    @Subscribe
    private void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.CHATBOX_TRANSPARENCY && event.getValue() == 1) bgGraphic.destroyBorder();
    }

    @Subscribe
    private void onResizeableChanged(ResizeableChanged event) {
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
        if (config.adjustHudAnchors() && config.heightChange() > 0 && !mainModals.isModalOpen() && mainModals.isTopLevelModalOpen())
            hudAnchors.forceStockRendered(); // Top-level modal is open, pretend anchors haven't been moved so it draws itself with full size

        int id = event.getScriptId();
        if (id == ScriptID.BUILD_CHATBOX || id == ScriptID.SPLITPM_CHANGED || id == TOPLEVEL_RELAYOUT_SCRIPT) apply(false);
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.TOPLEVEL_REDRAW) apply(false); // Fires when switching tabs on Character Summary tab, resets background
    }

    @Subscribe
    private void onBeforeRender(BeforeRender event) {
        boolean dragging = dragResizer.isDragging();
        if ((dialogBoxes.dialogOpenStateChanged() && dialogAdjustsSize()) || mainModals.topLevelModalOpenStateChanged()) {
            // Chat overlay or toplevel modal just opened or closed
            clientThread.invokeLater(() -> { // Redraw is smooth when done in client thread
                apply(false);
                if (client.isResized()) { // Resizable-only re-fit + re-wrap; fixed mode is fully re-asserted within apply()
                    mainModals.relayout();
                    client.runScript(RESIZES_CHAT_SCRIPT);
                }
                scrollKeep.sync();
            });
        } else if (dragging) {
            Dimension size = apply(false);
            // Fixed mode re-wraps within apply() (width is locked); the rewrap script + relayout are resizable-only
            if (client.isResized() && config.liveRewrap() && size != null && !size.equals(dragResizer.getLastDragSize())) client.runScript(RESIZES_CHAT_SCRIPT);
            dragResizer.setLastDragSize(size);
        } else {
            apply(false); // Drift-correct: re-stretch the tab bar/border after a rebuild (e.g. world hop) reverts it
            if (wasDragging && client.isResized()) {
                client.runScript(RESIZES_CHAT_SCRIPT); // Single expensive re-wrap on drag-resize release
                mainModals.relayout(); // Re-fit bank/overlays to the new chat size on release
            }
            dragResizer.setLastDragSize(null);
        }
        wasDragging = dragging;

        scrollKeep.sync(); // Single preservation here

        // Publish the current chat rectangle and layout for resize band management
        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        Widget slot = universe == null ? null : universe.getParent(); // Fixed: CHAT_CONTAINER; resizable: the chat slot
        dragResizer.update(slot == null ? null : slot.getBounds(), !client.isResized());
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
        pmSplit.resizePmBox(slotW);

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
        pmSplit.resizePmBox(null);
        revalidateChildren(universe);
        dialogBoxes.resetDialogPositions();
        hudAnchors.restore();
    }

    private static void revalidateAll(Widget[] children) {
        if (children == null) return;
        for (Widget child : children) {
            if (child == null) continue;
            child.revalidate();
            if (child.getId() == InterfaceID.Chatbox.SCROLLAREA) continue; // Gets handled for us
            revalidateChildren(child);
        }
    }

    static void revalidateChildren(Widget widget) {
        revalidateAll(widget.getStaticChildren());
        revalidateAll(widget.getDynamicChildren());
    }

    // True if a dialog opening/closing would temporarily unshrink or ungrow the chat in the current layout
    private boolean dialogAdjustsSize() {
        if (!client.isResized()) {
            int h = config.fixedHeightChange();
            return h < 0 || (config.ungrowForDialogs() && h > 0);
        } else {
            int w = config.widthChange(), h = config.heightChange();
            return w < 0 || h < 0 || (config.ungrowForDialogs() && (w > 0 || h > 0));
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