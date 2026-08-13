package brc;

import brc.internal.ChatRebuild;
import brc.internal.RawScripts;
import brc.internal.SizeClamps;
import brc.internal.Widgets;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CanvasSizeChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import com.google.inject.Provides;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;

@PluginDescriptor(
    name = "Chat Resizer",
    description = "Resize chat box in resizable or fixed layout.",
    tags = {"chat", "chatbox", "private", "message", "extend", "resize", "resizable", "resizeable",
            "scale", "stretch", "width", "height", "ui", "better", "fixed", "hide", "toggle"},
    conflicts = {"Resizable Chat", "Fixed Mode Hide Chat"}
)
public class ChatResizerPlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ChatResizerConfig config;
    @Inject private KeyManager keyManager;
    @Inject private MouseManager mouseManager;
    @Inject private OverlayManager overlayManager;
    @Inject private EventBus eventBus;

    @Inject private RuneLiteHudAnchors hudAnchors;
    @Inject private TopLevelModals mainModals;
    @Inject private OverlayTransitions transitions;
    @Inject private ChatDialogBoxes dialogBoxes;
    @Inject private FixedModeChat fixedChat;
    @Inject private ResizableModeChat resizable;
    @Inject private ChatScrollRetainer scrollKeep;
    @Inject private ChatTextStyle textStyle;
    @Inject private RuneLiteChatInput rlInput;
    @Inject private SecondarySize swapSize;
    @Inject private DragResizeActuator dragResizeActuator;
    @Inject private DragResizePreview dragResizePreview;

    // Non-null exactly while the handlers are registered; this reference is the arming state, and a
    // fresh instance per enable resets the per-cycle latches. Only the AWT-thread hops null-check it.
    private volatile Events events;

    @Provides
    private ChatResizerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatResizerConfig.class);
    }

    @Override
    protected void startUp() {
        keyManager.registerKeyListener(hideChatHotkey);
        keyManager.registerKeyListener(swapSizeHotkey);
        keyManager.registerKeyListener(dragResizeActuator.getKeyListener());
        keyManager.registerKeyListener(hudAnchors.dragHotkeyListener); // Track RuneLite's overlay-management mode
        mouseManager.registerMouseListener(dragResizeActuator);
        overlayManager.add(dragResizePreview);
        overlayManager.add(hudAnchors.indicatorPresent); // Bracket RuneLite's snap-corner indicator draw
        overlayManager.add(hudAnchors.indicatorRestore);

        clientThread.invoke(() -> {
            // Injected singletons persist across disable -> enable; re-prime cached state before the enable path
            transitions.reset();
            scrollKeep.reset();
            textStyle.reset();
            swapSize.reset();
            enable();
        });
    }

    @Override
    protected void shutDown() {
        unregisterHandlers(); // Disarm first, so no event lands between here (EDT) and the queued restore
        keyManager.unregisterKeyListener(hideChatHotkey);
        keyManager.unregisterKeyListener(swapSizeHotkey);
        keyManager.unregisterKeyListener(dragResizeActuator.getKeyListener());
        keyManager.unregisterKeyListener(hudAnchors.dragHotkeyListener);
        mouseManager.unregisterMouseListener(dragResizeActuator);
        overlayManager.remove(dragResizePreview);
        overlayManager.remove(hudAnchors.indicatorPresent);
        overlayManager.remove(hudAnchors.indicatorRestore);
        dragResizeActuator.reset();

        clientThread.invoke(() -> {
            unregisterHandlers(); // Once more: a layout-swap enable() queued before shutdown would have re-armed
            textStyle.restore(); // Ahead of the rebuilds below, so they re-wrap against stock line heights
            scrollKeep.sync();
            if (client.isResized()) {
                resizable.restore();
                ChatRebuild.now(client, RawScripts.RESIZES_CHAT); // Clean up sprite + re-wrap at stock width
                resizable.restoreBackground();
                mainModals.relayout();
            } else {
                fixedChat.restore();
                ChatRebuild.now(client, RawScripts.REWRAPS_CHAT); // Re-anchor lines to restored stock height this frame
                if (mainModals.isTopLevelModalOpen()) mainModals.relayout(); // Re-fit an open modal back to stock band
            }
            if (dialogBoxes.isDialogOpen()) dialogBoxes.resetDialogPositions(); // Must reset position of open dialog
            rlInput.refit(); // Frame loop is off by now, so re-center an open input prompt on the restored width here
            scrollKeep.sync();
        });
    }

    // Arm the handlers, then enable the live layout, in one client-thread pass.
    // Registration must come first: this pass's own relayout depends on the registered pre-fire hooks.
    private void enable() {
        registerHandlers();
        scrollKeep.sync();
        if (client.isResized()) resizable.onEnable(); else fixedChat.onEnable();
        scrollKeep.sync();
    }

    // Client thread only, and only while unregistered: a stale registration would orphan
    // the old Events on the bus as a zombie subscriber that keeps firing after disable
    private void registerHandlers() {
        assert events == null : "registerHandlers called while already armed"; // Dev-client tripwire
        unregisterHandlers(); // Release-build self-heal
        events = new Events();
        eventBus.register(events);
        eventBus.register(fixedChat); // Fixed-mode tab collapse consumes its own menu clicks
        eventBus.register(hudAnchors.beforeDrawRestore); // Restores the interface band height after snap-corner read
    }

    // Safe to repeat while already unregistered; shutDown() relies on that
    private void unregisterHandlers() {
        Events e = events;
        events = null; // Disarm first, before the unsubscribes
        if (e != null) eventBus.unregister(e);
        eventBus.unregister(fixedChat);
        eventBus.unregister(hudAnchors.beforeDrawRestore);
    }

    // Editing these while the secondary size is showing would change nothing on screen; drop back so edit is visible
    private boolean primarySizeEdited(String key) {
        return client.isResized()
            ? key.equals(ChatResizerConfig.WIDTH_CHANGE) || key.equals(ChatResizerConfig.HEIGHT_CHANGE)
            : key.equals(ChatResizerConfig.FIXED_HEIGHT_CHANGE);
    }

    // The secondary size can't stay active without its keybind, or when the mode falls back to hold (key isn't held)
    private boolean swapUnusableAfter(String key) {
        return (key.equals(ChatResizerConfig.SWAP_SIZE_KEYBIND) && config.secondaryKeybind().equals(Keybind.NOT_SET))
            || (key.equals(ChatResizerConfig.SWAP_SIZE_MODE) && config.secondaryMode() == ChatResizerConfig.Mode.HOLD);
    }

    private boolean widthChanged(String key) {
        return key.equals(ChatResizerConfig.WIDTH_CHANGE) || key.equals(ChatResizerConfig.SWAP_WIDTH_CHANGE)
            || clampGateChanged(key); // A dialog gate moves width too; a modal gate rides along for the same re-fit
    }

    // Refitting interfaces counts as a band change: it resizes the container an open modal is
    // fitted into, so toggling it must re-fit that modal exactly like a height change does
    private boolean heightChanged(String key) {
        return key.equals(ChatResizerConfig.HEIGHT_CHANGE) || key.equals(ChatResizerConfig.SWAP_HEIGHT_CHANGE)
            || key.equals(ChatResizerConfig.GROW_INTERFACES) || clampGateChanged(key);
    }

    // Either overlay's revert gate: with that overlay open, changing it re-clamps and moves the chat on the spot
    private boolean clampGateChanged(String key) {
        return key.equals(ChatResizerConfig.REVERT_FOR_DIALOGS) || key.equals(ChatResizerConfig.REVERT_FOR_MODALS);
    }

    // The swap hotkey turned the secondary size on or off. Client thread only: the flip has to share a
    // pass with the rebuild, or one frame draws the new width with the lines still wrapped for the old one.
    private void setSwapActive(boolean active) {
        if (!swapSize.setActive(active)) return;
        if (dragResizeActuator.isDragging()) return; // A drag re-applies each frame and rebuilds on release
        reapplySizes(true, true);
    }

    // Config value or the live size set changed: re-apply, re-wrap, and re-fit. bandChanged covers anything that
    // moves the band a top-level modal is sized against: the chat's own height, and the interface-refit toggle.
    private void reapplySizes(boolean widthChanged, boolean bandChanged) {
        if (events == null) return; // Reached through an AWT-thread hop, whose queue outlives registration
        scrollKeep.sync();
        if (!config.fixedTabCollapse()) fixedChat.setCollapsed(false); // Don't leave chat stuck collapsed when disabled
        apply(true);
        if (client.isResized()) {
            // The resize script, not the plain re-wrap: it also re-fits an open dialog's mounted group
            ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
            if (widthChanged || (bandChanged && mainModals.isModalOpen())) mainModals.relayout(); // Re-fit bank, restack inv tabs
        } else if (fixedChat.consumeRebuildNeeded()) {
            ChatRebuild.now(client, RawScripts.REWRAPS_CHAT); // Re-anchor lines this frame to avoid drawing stale anchors
        }
        textStyle.reapply(); // After the rebuild, whose rows come back at the game's own font and pitch
        scrollKeep.sync();
    }

    // Chat overlay or toplevel modal just opened or closed; poll() consumes both edge detectors together
    private boolean overlayTransitioned() {
        OverlayTransitions.Edges edges = transitions.poll();
        if (edges.modalChanged) return true;
        if (edges.dialogChanged) {
            // A dialog edge only forces a re-apply if it shifts the effective size, per axis
            if (!client.isResized()) {
                return dialogShifts(fixedChat.effectiveHeightChange(false), fixedChat.effectiveHeightChange(true));
            } else {
                int w = swapSize.effectiveWidthChange(); // No collapse term on width, so both sides are the same
                return dialogShifts(w, w) || dialogShifts(resizable.effectiveHeightChange(false), resizable.effectiveHeightChange(true));
            }
        }
        return false;
    }

    // Per axis: does a dialog move the applied size? Derived from the apply path's own clamp, so the two can't drift
    private boolean dialogShifts(int closed, int open) {
        return closed != SizeClamps.clamp(open, !client.isResized(), false, true, config);
    }

    // Would a modal opening now move the chat? Either gate, either direction; asked by the fixed pre-apply
    private boolean modalClampShifts(int delta) {
        return delta != SizeClamps.clamp(delta, true, true, false, config);
    }

    // Adjust the chat and re-fit modals after an overlay transition
    private void applyOverlayTransition() {
        apply(false);
        if (client.isResized()) { // Resizable-only re-fit + re-wrap; fixed mode is fully re-asserted within apply()
            mainModals.relayout();
            ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
        } else if (!dragResizeActuator.isDragging() && fixedChat.consumeRelayoutNeeded()) {
            mainModals.relayout(); // Re-fit the open modal to the changed band in the same tick
        }
        scrollKeep.sync();
    }

    // Apply resizes for the current layout
    private Dimension apply(boolean force) {
        return client.isResized() ? resizable.apply(force) : fixedChat.apply(force);
    }

    // Hide or unhide chat on keybind
    private final HotkeyListener hideChatHotkey = new HotkeyListener(() -> config.toggleShowChat()) {
        @Override public void hotkeyPressed() {
            clientThread.invoke(() -> {
                if (events == null || client.getGameState() != GameState.LOGGED_IN) return;
                if (client.isResized()) {
                    resizable.toggleHidden();
                } else if (config.fixedTabCollapse()) {
                    fixedChat.setCollapsed(!fixedChat.isCollapsed());
                }
            });
        }
    };

    // Swap to the secondary size while its keybind is held, or toggled with it, per the configured mode
    private final HotkeyListener swapSizeHotkey = new HotkeyListener(() -> config.secondaryKeybind()) {
        @Override public void hotkeyPressed() {
            boolean toggle = config.secondaryMode() == ChatResizerConfig.Mode.TOGGLE;
            clientThread.invoke(() -> setSwapActive(!toggle || !swapSize.isActive())); // Read flag where it's written
        }
        @Override public void hotkeyReleased() {
            if (config.secondaryMode() == ChatResizerConfig.Mode.HOLD) clientThread.invoke(() -> setSwapActive(false));
        }
    };

    // The plugin's @Subscribe handlers live off the plugin class so that registration timing is ours: PluginManager
    // would otherwise register them on the EDT before the enable pass runs, and a frame in that gap flickers
    private final class Events {
        private boolean wasDragging;

        // Deferred to this cycle's onPostClientTick drain rather than a ClientThread
        // continuation, so disarming drops the pending work with it. Client thread only.
        private boolean transitionPending; // Fixed pre-apply ran; finish its relayout + realign at cycle end
        private boolean refitPending; // Canvas resized; re-fit the mounted modal windows once the cascade settles

        // Edited config keys, latched off-thread and adopted next frame; a queued hop moves the chat before its band
        private final Queue<String> configEdits = new ConcurrentLinkedQueue<>();

        @Subscribe
        private void onConfigChanged(ConfigChanged event) {
            if (!ChatResizerConfig.GROUP.equals(event.getGroup()) || dragResizeActuator.isDragging()) return;
            configEdits.add(event.getKey());
        }

        // The engine's own relayout leaves mounted modal roots measuring the mid-cascade band; re-fit once it settles
        @Subscribe
        private void onCanvasSizeChanged(CanvasSizeChanged event) {
            refitPending = true;
        }

        @Subscribe
        private void onResizeableChanged(ResizeableChanged event) {
            // The flag flips before the toplevel swap completes: undo the outgoing layout's edits now,
            // go inert so nothing re-applies mid-swap, and let the deferred enable() re-arm a cycle later
            unregisterHandlers();
            if (event.isResized()) {
                fixedChat.restore(); // Leaving fixed, reset CHAT_CONTAINER (persists across logout)
            } else {
                resizable.restore(); // Leaving resizable
            }
            clientThread.invokeLater(ChatResizerPlugin.this::enable);
        }

        @Subscribe
        private void onScriptPreFired(ScriptPreFired event) {
            // A grown chat is about to ungrow for an opening modal: present the container's stock
            // height now, so the modal lays itself out against the full space it is about to get
            if (config.growInterfaces() && config.revertForModals().ungrows() &&
                resizable.effectiveHeightChange(false) > 0 &&
                !mainModals.isModalOpen() && mainModals.isTopLevelModalOpen()
            ) {
                hudAnchors.forceStockRendered();
            }

            // Fixed: a modal mounted this tick and its onLoad is about to fire; move the chat first,
            // if the modal clamp moves it at all, so the modal measures the band it will actually get
            if (!client.isResized() && !mainModals.isModalOpen() &&
                modalClampShifts(fixedChat.effectiveHeightChange(false)) &&
                transitions.pollModalEdge()
            ) {
                apply(false);
                transitionPending = true; // Relayout + realign at cycle end, once the open salvo settles
            }

            int id = event.getScriptId();
            mainModals.cacheRefit(id, event.getScriptEvent()); // Capture self-refitting overlays' size polls
            if (id == ScriptID.BUILD_CHATBOX || id == ScriptID.SPLITPM_CHANGED || id == RawScripts.TOPLEVEL_RELAYOUT) apply(false);
        }

        @Subscribe
        private void onScriptPostFired(ScriptPostFired event) {
            int id = event.getScriptId();
            if (id == ScriptID.TOPLEVEL_REDRAW) apply(false); // Fires on Character Summary tab switches; resets background
            // RuneLite runs 677 before building its own input prompts, so this is what gets the chat to its final width
            // in time for the prompt to bake its text and caret against it (RuneLiteChatInput covers the reverse)
            if (id == ScriptID.MESSAGE_LAYER_OPEN) apply(false);
        }

        // Single per-cycle drain: the overlay-transition poll plus whatever the cycle's handlers latched. Fires
        // after the tick-end drain, so the cycle's widget and varc state is final and the frame hasn't drawn yet.
        @Subscribe
        private void onPostClientTick(PostClientTick event) {
            boolean preApplied = transitionPending;
            transitionPending = false;
            boolean transitioned = overlayTransitioned(); // Poll unconditionally; it advances both edge detectors
            if (transitioned || preApplied) applyOverlayTransition();

            // After the transition, so the windows measure the settled chat band rather than the old one
            if (refitPending) {
                refitPending = false;
                mainModals.relayout();
            }
        }

        @Subscribe
        private void onBeforeRender(BeforeRender event) {
            boolean dragging = dragResizeActuator.isDragging();

            if (dragging) {
                if (!wasDragging) fixedChat.setCollapsed(false); // Drag writes config height, which collapse would override
                Dimension size = apply(false), last = dragResizeActuator.getLastDragSize();
                if (client.isResized() && config.liveRewrap() && size != null && !size.equals(last))
                    client.runScript(RawScripts.RESIZES_CHAT); // Re-wrap text and move PM split
                dragResizeActuator.setLastDragSize(size);
            } else {
                adoptConfigEdits(); // Ahead of this frame's apply, or chat adopts the edit a frame before interfaces re-fit to it
                apply(false); // Drift-correct: re-stretch the tab bar/border after a rebuild (e.g. world hop) reverts it
                if (wasDragging && client.isResized()) {
                    ChatRebuild.now(client, RawScripts.RESIZES_CHAT); // Single expensive re-wrap on drag-resize release
                    mainModals.relayout(); // Re-fit bank/overlays to the new chat size on release
                    scrollKeep.noteRewrap(); // Re-anchor scroll if that re-wrap was the drag's deferred width change
                }
                dragResizeActuator.setLastDragSize(null);
            }
            wasDragging = dragging;

            // Resizable layout: an enable that landed mid layout swap owes its rebuild; retries until the slot is ours
            if (client.isResized() && resizable.consumeEnablePending()) resizable.onEnable();
            // Fixed layout: mirror resizable's collapsed CHAT_VIEW sentinel so incoming messages blink their tab
            if (!client.isResized() && client.getGameState() == GameState.LOGGED_IN) fixedChat.syncCollapsedTab();
            // Fixed layout: the viewport band changed under an open modal
            if (!dragging && fixedChat.consumeRelayoutNeeded()) mainModals.relayout();
            // Fixed layout: re-anchor lines to the bottom after a height change; never mid-drag (see the drag branch)
            if (!client.isResized() && !dragging && fixedChat.consumeRebuildNeeded()) client.refreshChat();
            // Resizable layout: chat was collapsed or uncollapsed, re-fit the band and re-wrap at the new height
            if (client.isResized() && resizable.consumeRelayoutNeeded()) {
                ChatRebuild.now(client, RawScripts.RESIZES_CHAT);
                mainModals.relayout();
            }

            rlInput.refit(); // Re-center an open RuneLite input prompt if this frame moved the width out from under it
            textStyle.sync(); // Before the scroll pin: re-stacking the rows moves the content height it reads
            scrollKeep.sync(); // Single preservation here

            // Publish the current chat rectangle, layout and window-derived size ceilings for resize band management
            Widget slot = Widgets.chatSlot(client);
            Rectangle bounds = slot == null ? null : Widgets.liveBounds(slot);
            dragResizeActuator.update(bounds, !client.isResized(), client.getCanvasWidth(), client.getCanvasHeight());

            hudAnchors.presentAnchorHeight();
        }

        @Subscribe
        private void onCommandExecuted(CommandExecuted event) {
            if (event.getCommand().equals("testpm")) {
                String message = "ABCDEFGHIJKLMNO PQRSTUVWXYZ ABCDEFG HIJKLMNOP QRSTU VWX YZ AB CD EF G H I J K L M N O P Q R S T U V W X Y Z";
                if (event.getArguments().length != 0) message = String.join(" ", event.getArguments());
                client.addChatMessage(ChatMessageType.MODPRIVATECHAT, "Test", message, null);
                //client.addChatMessage(ChatMessageType.PUBLICCHAT, "Test", message, null);
            } else if (event.getCommand().equals("clearpm")) {
                ChatLineBuffer buffer = client.getChatLineMap().get(ChatMessageType.MODPRIVATECHAT.getType());
                for (MessageNode node : buffer.getLines().clone()) buffer.removeMessageNode(node);
                // Dev-only one-shot with no layout state, so it can stay on invokeAtTickEnd rather than a latch
                clientThread.invokeAtTickEnd(() -> ChatRebuild.now(client, ScriptID.SPLITPM_CHANGED));
            } else if (event.getCommand().equals("lorem")) {
                String lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et "
                    + "dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo "
                    + "consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. "
                    + "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
                java.util.Random rand = new java.util.Random();
                for (int n = 0; n < (event.getArguments().length != 0 ? Integer.parseInt(event.getArguments()[0]) : 1); n++) {
                    for (int i = 0, j = 0; i < lorem.length(); i = j) {
                        String name = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(rand.nextInt(6) + 4);
                        j = Math.min(j + rand.nextInt(90) + 40, lorem.length());
                        client.addChatMessage(ChatMessageType.MODCHAT, name, lorem.substring(i, j).trim(), null);
                    }
                }
            }
        }

        // Adopt every edit latched since the last frame in one pass
        private void adoptConfigEdits() {
            if (configEdits.isEmpty()) return;

            boolean width = false, band = false, unusable = false;
            for (String key = configEdits.poll(); key != null; key = configEdits.poll()) {
                width = width || widthChanged(key);
                band = band || heightChanged(key);
                unusable = unusable || swapUnusableAfter(key) || primarySizeEdited(key);
            }
            boolean deactivated = unusable && swapSize.setActive(false); // Secondary size was deactivated

            reapplySizes(deactivated || width, deactivated || band);
        }
    }
}