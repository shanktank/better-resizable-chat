package brc;

import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class RuneLiteHudAnchors {
    private final Client client;
    private final ChatResizerConfig config;

    // Native MINUS reserve of each HUD container, captured once before we first override it
    private final Map<Integer, Integer> stockReserve = new HashMap<>();

    private boolean applied;

    @Inject
    RuneLiteHudAnchors(Client client, ChatResizerConfig config) {
        this.client = client;
        this.config = config;
    }

    // Safe and self-healing vertical shifting of anchors
    void sync(int chatHeightDelta) {
        if (!config.adjustHudAnchors()) {
            if (applied) restore(); // Setting just turned off: revert override once, then leave the HUD alone
            return;
        }

        int id = activeFrontId();
        Widget hud = client.getWidget(id);
        if (hud == null) return;

        int base = stockReserve.computeIfAbsent(id, k -> hud.getOriginalHeight());
        int target = Math.max(0, base + chatHeightDelta);
        if (hud.getOriginalHeight() != target) {
            hud.setOriginalHeight(target);
            hud.revalidate();
        }

        applied = true;
    }

    // Pretend we didn't move the anchors so an opening modal makes full use of available space
    void forceStockRendered() {
        int id = activeFrontId();
        Widget hud = client.getWidget(id);
        if (hud == null) return;
        Integer base = stockReserve.get(id);
        if (base == null || hud.getOriginalHeight() <= base) return; // Never overrode or not shrunk; already native
        Widget parent = hud.getParent();
        if (parent == null) return;
        int nativeRendered = parent.getHeight() - base; // MINUS height resolves as parentHeight minus reserve
        if (hud.getHeight() != nativeRendered) Widgets.setHeight(hud, nativeRendered);
    }

    void restore() {
        applied = false;
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