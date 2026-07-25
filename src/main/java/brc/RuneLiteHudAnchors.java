package brc;

import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.HotkeyListener;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

// Resizable-only. Resizes RuneLite's HUD container so its child interface band (bank, settings, etc.)
// tracks the adjusted chat, while freezing the bottom-right snap corner that reads the same container.
@Singleton
public final class RuneLiteHudAnchors {
    private final Client client;
    private final ChatResizerConfig config;
    private final ConfigManager configManager;

    // Native MINUS reserve of each HUD container, captured once before we first override it
    private final Map<Integer, Integer> stockReserve = new HashMap<>();

    private boolean applied;
    private boolean swapped; // The snap-corner read is currently seeing a fake stock height, to be restored before draw

    // Fix visual location of bottom-right HUD anchor when drag hotkey is held
    private boolean dragHotkeyHeld; // RuneLite's hotkey for moving HUD items and HUD snap anchors
    private RuneLiteConfig runeLiteConfig; // RuneLite core config holding that hotkey
    final HotkeyListener dragHotkeyListener = new HotkeyListener(this::runeLiteDragHotkey) {
        @Override public void hotkeyPressed() { dragHotkeyHeld = true; }
        @Override public void hotkeyReleased() { dragHotkeyHeld = false; }
    };

    final BeforeDrawRestore beforeDrawRestore = new BeforeDrawRestore();

    // Restores the real band height after the snap-corner read, before the draw. A separate object
    // because EventBus wants every BeforeRender handler named onBeforeRender (one per class).
    final class BeforeDrawRestore {
        // After OverlayRenderer's default-priority snap-corner read, before the draw
        @Subscribe(priority = -1) void onBeforeRender(BeforeRender event) { restoreLayoutHeight(); }
    }

    @Inject
    RuneLiteHudAnchors(Client client, ChatResizerConfig config, ConfigManager configManager) {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
    }

    // RuneLite's overlay drag hotkey, whatever the user has it set to (its config is fetched once and cached)
    private Keybind runeLiteDragHotkey() {
        if (runeLiteConfig == null) runeLiteConfig = configManager.getConfig(RuneLiteConfig.class);
        return runeLiteConfig.dragHotkey();
    }

    // Resize the HUD container so its child interface band tracks the chat. Safe and self-healing.
    void sync(int chatHeightDelta) {
        // Grow off but a grown chat won't ungrow for interfaces: still shrink the band, or it overlaps them
        if (!config.growInterfaces() && !(chatHeightDelta > 0 && !config.revertForModals().ungrows())) {
            if (applied) restore(); // Off with nothing forced: revert override once, then leave the HUD alone
            return;
        }

        // Grow off: keep only the shrink half of the delta, so a shorter chat never grows the band
        int delta = config.growInterfaces() ? chatHeightDelta : Math.max(0, chatHeightDelta);

        int id = activeFrontId();
        Widget hud = client.getWidget(id);
        if (hud == null) return;

        int base = stockReserve.computeIfAbsent(id, k -> hud.getOriginalHeight());
        int target = Math.max(0, base + delta);
        if (hud.getOriginalHeight() != target) {
            hud.setOriginalHeight(target);
            hud.revalidate();
        }

        applied = true;
    }

    // Freeze the bottom-right snap corner: present the stock rendered height for OverlayRenderer's
    // snap-corner read, at frame-loop end before that (default-priority) read. Resizable only.
    void presentAnchorHeight() {
        // Gate on whether the band is actually overridden, not the toggle: a forced shrink (grow off) resizes it too
        swapped = applied && client.isResized() && presentStockRendered();
    }

    // Present stock height for a modal's opening salvo, so it lays out against full space before the ungrow
    void forceStockRendered() {
        presentStockRendered();
    }

    // Write the stock rendered height directly, bypassing the reserve. Returns whether it changed anything.
    private boolean presentStockRendered() {
        int id = activeFrontId();
        Widget hud = client.getWidget(id);
        if (hud == null) return false;
        Integer base = stockReserve.get(id);
        if (base == null || hud.getOriginalHeight() == base) return false; // Never overrode, or already stock
        Widget parent = hud.getParent();
        if (parent == null) return false;
        int stockRendered = parent.getHeight() - base; // MINUS height resolves as parentHeight minus reserve
        if (hud.getHeight() != stockRendered) Widgets.setHeight(hud, stockRendered);
        return true;
    }

    // Undo presentAnchorHeight before the draw, so the band isn't clipped to stock.
    // Held through the draw while the overlay drag hotkey is down: RuneLite draws the
    // snap-corner indicator from the live container, so it would track the chat otherwise.
    private void restoreLayoutHeight() {
        if (!swapped || dragHotkeyHeld) return;
        swapped = false;
        Widget hud = client.getWidget(activeFrontId());
        if (hud != null) hud.revalidate(); // Recompute the rendered height from the (band) reserve
    }

    void restore() {
        applied = false;
        swapped = false;
        restore(InterfaceID.ToplevelPreEoc.HUD_CONTAINER_FRONT);
        restore(InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT);
    }

    private void restore(int id) {
        Integer base = stockReserve.get(id);
        if (base == null) return; // Never overrode this container
        Widget w = client.getWidget(id);
        if (w != null && w.getOriginalHeight() != base) {
            w.setOriginalHeight(base);
            w.revalidate();
        }
    }

    private int activeFrontId() {
        return client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
            ? InterfaceID.ToplevelPreEoc.HUD_CONTAINER_FRONT
            : InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT;
    }
}