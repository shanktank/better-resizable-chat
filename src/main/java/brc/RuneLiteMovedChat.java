package brc;

import brc.internal.ChatGeometry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

// Resizable-only. RuneLite lets the player alt-drag the chat slot out of its bottom-anchored home and then
// pins it by the top-left, so our height changes run the tab bar up the screen instead of opening space
// above it. Shift the stored point against each change, which keeps the bottom edge still like stock does.
@Singleton
public final class RuneLiteMovedChat {
    // RuneLite's names for the two resizable chat slots' widget overlays; also its config keys, so they are stable
    private static final String CLASSIC_CHAT = "RESIZABLE_VIEWPORT_CHATBOX_PARENT";
    private static final String MODERN_CHAT = "RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT";

    private final OverlayManager overlayManager;
    private final Map<String, Overlay> overlays = new HashMap<>(); // Resolved once each; RuneLite builds them at startup

    private Overlay tracked; // The live layout's chat overlay, null while there is none to track
    private int lastHeight; // Slot height the tracked overlay's stored point is aligned to

    @Inject
    RuneLiteMovedChat(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
    }

    // Keep a moved chat's bottom edge still across our resizes; no-op while the chat sits in its stock anchor
    void sync(Widget slot, int slotW, int slotH) {
        Overlay overlay = chatOverlay(slot.getId());
        if (overlay != tracked) {
            restore(); // Hand back what the outgoing layout's point is holding, e.g. across a classic/modern swap
            tracked = overlay;
            lastHeight = slot.getHeight(); // Its point is aligned to the box as it stands right now
        }
        if (tracked == null) return;

        shift(slotH - lastHeight);
        lastHeight = slotH;
        // RuneLite clamps the box into the canvas against these, and only adopts our size after the draw
        tracked.getBounds().setSize(slotW, slotH);
    }

    // Hand back the height we took from the stored point, and untrack: the next sync re-primes against the live box
    void restore() {
        if (tracked != null) shift(ChatGeometry.CHATBOX_SLOT_H - lastHeight);
        tracked = null;
    }

    // Move the stored top edge up by a grow, down by a shrink
    private void shift(int delta) {
        if (delta == 0) return;
        Point stored = tracked.getPreferredLocation();
        // Null while unmoved, and while parked in a snap corner, which bottom-aligns the box on its own
        if (stored != null) tracked.setPreferredLocation(new Point(stored.x, stored.y - delta));
    }

    private Overlay chatOverlay(int slotId) {
        String name;
        if (slotId == InterfaceID.ToplevelPreEoc.CHAT_CONTAINER) name = MODERN_CHAT;
        else if (slotId == InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER) name = CLASSIC_CHAT;
        else return null; // Fixed layout's slot: not movable, RuneLite has no overlay on it

        Overlay cached = overlays.get(name);
        if (cached != null) return cached;
        Overlay found = findOverlay(name);
        if (found != null) overlays.put(name, found);
        return found;
    }

    // RuneLite keeps its overlay list package-private; anyMatch is the public read path over it
    private Overlay findOverlay(String name) {
        Overlay[] found = new Overlay[1];
        overlayManager.anyMatch(overlay -> {
            if (!name.equals(overlay.getName())) return false;
            found[0] = overlay;
            return true;
        });
        return found[0];
    }
}