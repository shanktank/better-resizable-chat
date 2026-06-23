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
    description = "Resize chat box. Requires resizable layout.",
    tags = {"chat", "chatbox", "private", "message", "extend", "resize", "resizable", "resizeable", "scale", "stretch", "width", "height", "ui", "better"},
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
    private ChatScrollRetainer scrollKeep;
    private DragResizer dragResizer;
    private DragPreview dragPreview;
    private boolean wasDragging;

    @Provides
    private BetterResizableChatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BetterResizableChatConfig.class);
    }

    private void onEnable() {
        scrollKeep.withScrollPreserved(() -> {
            apply(config.heightChange() == 0 && config.widthChange() == 0);
            client.runScript(REWRAPS_CHAT_SCRIPT);
        });
    }

    private void onDisable() {
        restore();
    }

    @Override
    protected void startUp() {
        hudAnchors = new RuneLiteHudAnchors(client, config);
        mainModals = new TopLevelModals(client);
        bgGraphic = new ChatBackgroundGraphic(client);
        dialogBoxes = new ChatDialogBoxes(client);
        pmSplit = new PrivateMessageSplit(client, config);
        scrollKeep = new ChatScrollRetainer(client);
        dragResizer = new DragResizer(configManager, config);
        dragPreview = new DragPreview(dragResizer, config, tooltipManager);

        keyManager.registerKeyListener(dragResizer);
        mouseManager.registerMouseListener(dragResizer);
        overlayManager.add(dragPreview);

        if (client.isResized()) clientThread.invoke(this::onEnable);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(dragResizer);
        mouseManager.unregisterMouseListener(dragResizer);
        overlayManager.remove(dragPreview);

        dragResizer.reset();

        if (client.isResized()) {
            clientThread.invoke(() -> scrollKeep.withScrollPreserved(() -> {
                onDisable();
                client.runScript(RESIZES_CHAT_SCRIPT); // Clean up sprite + re-wrap at stock width
            }));
        }
    }

    @Subscribe
    void onCommandExecuted(CommandExecuted event) {
        if ("testpm".equals(event.getCommand())) { // Test add a private message
            String message = "ABCDEFGHIJKLMNO PQRSTUVWXYZ ABCDEFG HIJKLMNOP QRSTU VWX YZ AB CD EF G H I J K L M N O P Q R S T U V W X Y Z";
            if (event.getArguments().length != 0) message = String.join(" ", event.getArguments());
            client.addChatMessage(ChatMessageType.PRIVATECHAT, "Test", message, null);
            client.addChatMessage(ChatMessageType.PUBLICCHAT, "Test", message, null);
        }
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!BetterResizableChatConfig.GROUP.equals(event.getGroup()) || dragResizer.isDragging()) return;
        if (event.getKey().equals("rewrapPrivateChat") || event.getKey().equals("adjustHudAnchors")) clientThread.invoke(() -> apply(true));
        clientThread.invoke(() -> scrollKeep.withScrollPreserved(() -> client.runScript(REWRAPS_CHAT_SCRIPT)));
    }

    @Subscribe
    private void onVarbitChanged(VarbitChanged event) {
        if (client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1) bgGraphic.destroyBorder();
    }

    @Subscribe
    private void onResizeableChanged(ResizeableChanged event) {
        if (event.isResized()) {
            clientThread.invokeLater(this::onEnable);
        } else {
            onDisable();
        }
    }

    @Subscribe
    private void onScriptPreFired(ScriptPreFired event) {
        // Top-level modal is open, pretend anchors haven't been moved so it draws itself with full size
        if (config.adjustHudAnchors() && config.heightChange() > 0 && !mainModals.isModalOpen() && mainModals.isTopLevelModalOpen()) hudAnchors.forceStockRendered();

        int id = event.getScriptId();
        if (id == ScriptID.BUILD_CHATBOX || id == ScriptID.SPLITPM_CHANGED || id == TOPLEVEL_RELAYOUT_SCRIPT) apply(false);
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.TOPLEVEL_REDRAW) apply(false); // Fires when switching tabs on Character Summary tab, resets background
    }

    @Subscribe
    private void onBeforeRender(BeforeRender event) {
        // Input prompt was just closed or center modal was just either opened or closed
        if ((config.ungrowForDialogs() && dialogBoxes.dialogJustClosed()) || mainModals.topLevelModalOpenStateChanged()) {
            scrollKeep.withScrollPreserved(() -> { // Resize when a modal was just opened or closed
                apply(false);
                client.runScript(RESIZES_CHAT_SCRIPT);
            });
            return;
        }

        // Chat box is being drag-resized
        boolean dragging = dragResizer.isDragging();
        if (dragging) {
            if (!wasDragging) scrollKeep.capture();
            Dimension size = apply(false);
            if (config.liveRewrap() && size != null && !size.equals(dragResizer.getLastDragSize())) client.runScript(RESIZES_CHAT_SCRIPT);
            dragResizer.setLastDragSize(size);
        } else {
            if (wasDragging) {
                apply(false);
                client.runScript(RESIZES_CHAT_SCRIPT); // Single expensive re-wrap on drag-resize release
                scrollKeep.restore();
            } else {
                apply(false); // Drift-correct: re-stretch the tab bar/border after a rebuild (e.g. world hop) reverts it
            }
            dragResizer.setLastDragSize(null);
        }
        wasDragging = dragging;

        // Publish the current chat rectangle for resize band management
        if (client.isResized()) {
            Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
            Widget slot = universe == null ? null : universe.getParent();
            dragResizer.update(slot == null ? null : slot.getBounds());
        } else {
            dragResizer.update(null);
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

    // Apply resizes
    private Dimension apply(boolean force) {
        if (!client.isResized()) return null;

        Widget universe = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (universe == null) return null;
        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return null;
        Widget slot = universe.getParent();
        if (slot == null) return null;

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
        slot.setOriginalWidth(slotW);
        slot.setOriginalHeight(slotH);
        slot.revalidate();

        // Universe's minus fill resolves against client root, pin as absolute
        universe.setWidthMode(WidgetSizeMode.ABSOLUTE);
        universe.setHeightMode(WidgetSizeMode.ABSOLUTE);
        universe.setOriginalWidth(slotW);
        universe.setOriginalHeight(slotH);
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
        if (slot != null) {
            slot.setOriginalWidth(CHATBOX_SPRITE_W);
            slot.setOriginalHeight(CHATBOX_SLOT_H);
            slot.setForcedPosition(-1, -1);
            slot.revalidate();
        }

        universe.setWidthMode(WidgetSizeMode.MINUS);
        universe.setHeightMode(WidgetSizeMode.MINUS);
        universe.setOriginalWidth(0);
        universe.setOriginalHeight(0);
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

    private void revalidateAll(Widget[] children) {
        if (children == null) return;

        for (Widget child : children) {
            if (child == null) continue;
            child.revalidate();
            if (child.getId() == InterfaceID.Chatbox.SCROLLAREA) continue; // Gets handled for us
            revalidateChildren(child);
        }
    }

    private void revalidateChildren(Widget widget) {
        revalidateAll(widget.getStaticChildren());
        revalidateAll(widget.getDynamicChildren());
    }
}