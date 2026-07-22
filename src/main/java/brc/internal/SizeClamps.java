package brc.internal;

import brc.ChatResizerConfig;
import brc.ChatResizerConfig.Revert;

// The single encoding of the size clamp policy: the per-axis overlay clamp both layouts' apply paths call,
// plus the range a size delta may be written in at all, which drag-resize has to enforce for itself
public final class SizeClamps {
    private SizeClamps() {}

    // Max height/width change (for config panel @Range only)
    public static final int MAX_DIMENSION_CHANGE = 10000;

    // Floors: clamp drag-resizer and config @Range mins
    public static final int MIN_HEIGHT_CHANGE = -ChatGeometry.CHATBOX_SLOT_H;
    public static final int MIN_WIDTH_CHANGE = -ChatGeometry.CHATBOX_SPRITE_W;

    // Ceilings: the bottom edge is pinned, so the chat may grow until its top reaches the top of the space it lives in
    public static int maxHeightChange(boolean fixedLayout, int canvasH) {
        return fixedLayout ? ChatGeometry.FIXED_CHAT_Y : Math.max(0, canvasH - ChatGeometry.CHATBOX_SLOT_H);
    }

    // Width is locked in fixed layout, so only the resizable ceiling exists
    public static int maxWidthChange(int canvasW) {
        return Math.max(0, canvasW - ChatGeometry.CHATBOX_SPRITE_W);
    }

    // Clamp a configured size delta while overlays are open, per that overlay's revert gate. The gates are read here
    // rather than passed in so every caller shares one encoding. Modal clamp is height-only: width passes false.
    public static int clamp(int delta, boolean fixedLayout, boolean modalClamps, boolean dialogOpen, ChatResizerConfig config) {
        if (modalClamps) {
            Revert revert = config.revertForModals();
            // Fixed layout ungrows whatever the gate says: its modal slot is a hard size that can't
            // re-fit above a grown chat, so leaving the chat grown would only cover the modal up
            if (delta > 0 && (fixedLayout || revert.ungrows())) delta = 0; // Ungrow so the modal gets the band
            if (delta < 0 && revert.unshrinks()) delta = 0;
        }

        if (dialogOpen) {
            Revert revert = config.revertForDialogs();
            if (delta > 0 && revert.ungrows()) delta = 0;
            if (delta < 0 && revert.unshrinks()) delta = 0; // Unshrink so the dialog gets the full stock box
        }

        return delta;
    }
}