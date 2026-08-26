package brc;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import javax.inject.Inject;
import javax.inject.Singleton;

// Keeps the chat's visible text where it was across chat resizes: re-anchors the built lines to the
// bottom of a box that has grown past them, and holds the scroll location the viewer left it at
@Singleton
public final class ChatScrollRetainer {
    private final Client client;
    private final ChatDialogModals dialogModals;
    private final DragResizeActuator dragResize;

    private Integer lastViewport; // Null until the chat scroll area is first seen
    private int lastWidth;
    private int lastContentH; // Total content height last seen; lets noteRewrap() tell a width re-wrap from a height-only one
    private int distFromBottom; // Content pixels below the viewport bottom; -1 while the engine rests at its 1px scroll floor

    // Line anchor: the message row at the viewport top, held only while scrolled up. A width change re-wraps every
    // message, which distance-from-bottom cannot survive; repinning to a remembered row bounds the error to that row.
    private int anchorId = -1;  // Component id of the anchored row (LINE0 + slot), or -1 when none is held
    private int anchorOffset;   // Pixels from that row's top down to the viewport top
    private int anchorScrollY;  // Scroll + content the anchor was captured against; recapture only once either moves,
    private int anchorContentH; // so the per-row scan stays off the resting-frame fast path

    @Inject
    ChatScrollRetainer(Client client, ChatDialogModals dialogModals, DragResizeActuator dragResize) {
        this.client = client;
        this.dialogModals = dialogModals;
        this.dragResize = dragResize;
    }

    // Drop stale viewport tracking on enable so the first sync re-establishes instead of mis-repinning
    void reset() {
        lastViewport = null;
        lastWidth = 0;
        lastContentH = 0;
        distFromBottom = 0;
        clearAnchor();
    }

    void sync() {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getHeight() <= 0 || scrollArea.getScrollHeight() <= 0) {
            lastViewport = null; // Chat not live (hop/relog)
            clearAnchor();
            return;
        }

        int viewport = scrollArea.getHeight();
        int width = scrollArea.getWidth();
        int contentH = scrollArea.getScrollHeight();
        int scrollY = scrollArea.getScrollY();

        // Put the lines back on the bottom before anything below reads the geometry
        boolean reanchored = reanchor(scrollArea, viewport - contentH);
        if (reanchored) contentH = viewport;

        if (reanchored || (lastViewport != null && (viewport != lastViewport || width != lastWidth))) {
            // Resized: a width change re-wrapped the lines, so prefer the line anchor; a pure height change didn't, so
            // the remembered distance from the bottom is exact (the engine floors max scroll at 1, hence the max(1, ...))
            Integer anchored = width != lastWidth ? anchorTarget(viewport, contentH) : null;
            if (anchored == null) anchored = Math.max(0, Math.min(Math.max(1, contentH - viewport), contentH - viewport - distFromBottom));
            client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, anchored);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, anchored);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE, contentH);
        } else if (!dialogModals.isDialogOpen() && !dragResize.isDragging()) {
            // Remember where the viewer sits; skipped under a dialog (it hijacks the message layer) and
            // for the whole of a drag, whose intermediate frames are not the viewer settling somewhere new
            distFromBottom = contentH - scrollY - viewport;
            // Scrolled up: also remember the top line for a width change to repin to; at the bottom it isn't needed
            if (distFromBottom > 0) {
                if (scrollY != anchorScrollY || contentH != anchorContentH) captureAnchor(scrollArea, scrollY, contentH);
            } else {
                clearAnchor();
            }
        }

        lastViewport = viewport;
        lastWidth = width;
        lastContentH = contentH;
    }

    // Drag release, for the one width change width != lastWidth can't see: with live re-wrap off the wrap is deferred
    // to release, by which point the per-frame tracking has caught the width up. A moved content height means it was a
    // width drag, so repin to the line anchor; a height-only drag keeps its distance-from-bottom pin.
    void noteRewrap() {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getScrollHeight() == lastContentH) return;
        lastWidth = Integer.MIN_VALUE; // Never a real width, so sync() takes the width-change (line-anchor) branch
        sync();
    }

    // Re-anchor the built lines to the bottom of a box that grew past them, redoing the chat builder's own final pass
    // rather than waiting a tick for a rebuild that may never come. Self-clearing, so a resting frame costs one subtraction.
    private static boolean reanchor(Widget scrollArea, int shift) {
        if (shift <= 0) return false; // Content still reaches the bottom; nothing hangs from a stale anchor
        shiftDown(scrollArea.getStaticChildren(), shift); // The row containers, LINE0..LINE499
        shiftDown(scrollArea.getDynamicChildren(), shift); // Their text/graphic parts, laid out at the row's own Y
        scrollArea.setScrollHeight(scrollArea.getHeight()); // Adopt the anchor the rows now hang from
        return true;
    }

    private static void shiftDown(Widget[] children, int shift) {
        if (children == null) return;
        for (Widget child : children) {
            if (child == null || child.getHeight() <= 0) continue; // Unused row slots, which the builder collapses to 0
            child.setOriginalY(child.getOriginalY() + shift); // Rows are absolute from the top, so this is their Y
            child.revalidate();
        }
    }

    // Remember the message row straddling the viewport top, keyed by its (stable) component id plus the sub-row offset
    private void captureAnchor(Widget scrollArea, int scrollY, int contentH) {
        clearAnchor();
        anchorScrollY = scrollY; // Recorded even when no row is found, so a fruitless scan doesn't repeat every frame
        anchorContentH = contentH;
        Widget[] rows = scrollArea.getStaticChildren();
        if (rows == null) return;
        Widget top = null; // The used row whose top is highest without passing the viewport top
        for (Widget row : rows) {
            // Rows are the static children from LINE0 up (older higher, newer lower); unused slots have 0 height
            if (row == null || row.getHeight() <= 0 || row.getId() < InterfaceID.Chatbox.LINE0) continue;
            int y = row.getRelativeY();
            if (y <= scrollY && (top == null || y > top.getRelativeY())) top = row;
        }
        if (top == null) return; // No row starts at/above the fold: leave distance-from-bottom to cover it
        anchorId = top.getId();
        anchorOffset = scrollY - top.getRelativeY();
    }

    // Scroll offset that puts the anchored row back under the viewport top after a re-wrap, or null if it's gone
    private Integer anchorTarget(int viewport, int contentH) {
        if (anchorId == -1) return null;
        Widget row = client.getWidget(anchorId);
        if (row == null || row.getHeight() <= 0) return null; // Row recycled or collapsed away since capture
        int offset = Math.min(anchorOffset, row.getHeight()); // The row may re-wrap shorter than the offset into it
        int target = row.getRelativeY() + offset;
        return Math.max(0, Math.min(Math.max(1, contentH - viewport), target));
    }

    private void clearAnchor() {
        anchorId = -1;
        anchorOffset = 0;
        anchorScrollY = -1; // Never a real scrollY, so the next scrolled-up frame always re-captures
        anchorContentH = -1;
    }
}