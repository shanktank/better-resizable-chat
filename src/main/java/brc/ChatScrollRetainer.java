package brc;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;
import javax.inject.Singleton;

// Keeps the chat's visible text where it was across chat resizes: re-anchors the built lines to the
// bottom of a box that has grown past them, and holds the scroll location the viewer left it at
@Slf4j
@Singleton
public final class ChatScrollRetainer {
    private final Client client;
    private final ChatDialogModals dialogModals;
    private final DragResizeActuator dragResize;

    private Integer lastViewport; // Null until the chat scroll area is first seen
    private int lastWidth;
    private int lastContentH; // Total content height last seen; lets noteRewrap() tell a width re-wrap from a height-only one
    private int distFromBottom; // Content pixels below the viewport bottom; -1 while the engine rests at its 1px scroll floor
    private int capturedWidth; // Wrap width distFromBottom was captured at; the pin compares against this, not lastWidth (see sync)
    private boolean reanchorInflated; // True while reanchor has the rows dropped past their content (box bigger than content)

    // Line anchor: the message row at the viewport top, held only while scrolled up. A width change re-wraps every
    // message, which distance-from-bottom cannot survive; repinning to a remembered row bounds the error to that row.
    private int anchorId = -1;  // Component id of the anchored row (LINE0 + slot), or -1 when none is held
    private int anchorOffset;   // Pixels from that row's top down to the viewport top
    private int anchorScrollY;  // Scroll + content the anchor was captured against; recapture only once either moves,
    private int anchorContentH; // so the per-row scan stays off the resting-frame fast path
    private int anchorViewport;  // Viewport height at capture; the target holds the row this far from the bottom, not the top

    boolean trace; // TEMP(scrolltrace): dev drag-flicker tracing, toggled by ::scrolltrace
    private int lastApplied = -1; // TEMP(scrolltrace): scroll the previous sync pushed, to see whether it stuck to the next frame
    private int dbgRy, dbgOff, dbgPre, dbgCeil; // TEMP(scrolltrace): exactly what anchorTarget read/computed this frame

    @Inject
    ChatScrollRetainer(Client client, ChatDialogModals dialogModals, DragResizeActuator dragResize) {
        this.client = client;
        this.dialogModals = dialogModals;
        this.dragResize = dragResize;
    }

    // TEMP(scrolltrace): dev toggle for the drag-flicker trace
    boolean toggleTrace() {
        trace = !trace;
        return trace;
    }

    // Drop stale viewport tracking on enable so the first sync re-establishes instead of mis-repinning
    void reset() {
        lastViewport = null;
        lastWidth = 0;
        lastContentH = 0;
        distFromBottom = 0;
        capturedWidth = 0;
        reanchorInflated = false;
        lastApplied = -1; // TEMP(scrolltrace)
        clearAnchor();
    }

    void sync() {
        Widget scrollArea = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (scrollArea == null || scrollArea.getHeight() <= 0 || scrollArea.getScrollHeight() <= 0) {
            lastViewport = null; // Chat not live (hop/relog)
            reanchorInflated = false;
            clearAnchor();
            return;
        }

        int viewport = scrollArea.getHeight();
        int width = scrollArea.getWidth();
        int contentH = scrollArea.getScrollHeight();
        int scrollY = scrollArea.getScrollY();
        int rawContentH = contentH; // TEMP(scrolltrace): scrollHeight as read, before reanchor may overwrite it

        // Keep the lines dropped to the bottom of a box grown past its content (below); it returns the effective content
        // height when it acts, so contentH reflects the inflated (or undone) drop rather than a stale raw scrollHeight
        int reanchoredH = reanchor(scrollArea, viewport);
        boolean reanchored = reanchoredH >= 0;
        if (reanchored) contentH = reanchoredH;
        boolean tracing = trace && dragResize.isDragging(); // TEMP(scrolltrace)

        // Re-pin on any change to the box (viewport/width) OR to the content height. The contentH term catches a
        // deferred re-wrap landing a frame or two after apply() changed the width: the engine's coalesced chatbox
        // rebuild re-runs its own distance-from-bottom scroll math when it finally fires, dropping our pinned scroll by
        // the contentH delta on a frame where width no longer differs from last. Without this term that lands in the
        // measure branch and the engine's scroll sticks until the next width change snaps it back.
        if (reanchored || (lastViewport != null && (viewport != lastViewport || width != lastWidth || contentH != lastContentH))) {
            // Repin to the line anchor once the width differs from what distFromBottom was captured at, since the
            // re-wrap makes that remembered distance stale. Compared against capturedWidth, NOT lastWidth: during a
            // corner drag the width pauses for the odd frame while the viewport keeps moving, and a per-frame lastWidth
            // test would flip back to the distance pin, which has diverged from the anchor by the viewport delta (snap).
            boolean widthChanged = width != capturedWidth;
            dbgRy = dbgOff = dbgPre = dbgCeil = -1; // TEMP(scrolltrace)
            Integer anchored = widthChanged ? anchorTarget(viewport, contentH) : null;
            boolean anchorHit = anchored != null;
            if (anchored == null) anchored = Math.max(0, Math.min(Math.max(1, contentH - viewport), contentH - viewport - distFromBottom));
            client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR, InterfaceID.Chatbox.SCROLLAREA, anchored);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLPOS, anchored);
            client.setVarcIntValue(VarClientID.CHAT_LASTSCROLLSIZE, contentH);
            if (tracing) traceDrag(scrollArea, viewport, width, rawContentH, contentH, scrollY, reanchored, widthChanged, anchorHit, anchored);
            lastApplied = anchored; // TEMP(scrolltrace)
        } else {
            if (tracing) traceDrag(scrollArea, viewport, width, rawContentH, contentH, scrollY, reanchored, false, false, null);
            if (!dialogModals.isDialogOpen() && !dragResize.isDragging()) {
                // Remember where the viewer sits; skipped under a dialog (it hijacks the message layer) and
                // for the whole of a drag, whose intermediate frames are not the viewer settling somewhere new
                distFromBottom = contentH - scrollY - viewport;
                capturedWidth = width; // The wrap this distance is valid against; a later resize repins by anchor once width leaves it
                // Scrolled up: also remember the top line for a width change to repin to; at the bottom it isn't needed
                if (distFromBottom > 0) {
                    if (scrollY != anchorScrollY || contentH != anchorContentH) captureAnchor(scrollArea, scrollY, contentH);
                } else {
                    clearAnchor();
                }
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
        lastWidth = Integer.MIN_VALUE; // Force the resize branch to run; the moved contentH means width left capturedWidth, so it repins by anchor
        sync();
    }

    // Keep the built rows dropped by max(realContentH, viewport) so a box grown past its own content draws its text at
    // the bottom instead of hanging too high, redoing the builder's final pass without waiting a tick for a rebuild that
    // may not come mid-drag. Bidirectional and self-undoing (unlike the old grow-only form): the drop is re-applied as
    // the box shrinks and fully removed once the content fills the box again, so the inflated scrollHeight can never
    // linger and poison the distance-from-bottom pin on a later shrink frame. Returns the effective content height when
    // it acts, or -1 on the fast path (not inflated and the content still fills the box: one comparison, no row scan).
    private int reanchor(Widget scrollArea, int viewport) {
        int scrollHeight = scrollArea.getScrollHeight();
        if (!reanchorInflated && viewport <= scrollHeight) return -1; // Content fills the box; nothing hangs
        int realC = usedContentHeight(scrollArea); // Sum of row heights (shift-invariant), cheap here: few rows fit
        if (realC <= 0) { reanchorInflated = false; return -1; }
        int desired = Math.max(realC, viewport); // Height the rows should hang from: viewport while grown past, else realC
        reanchorInflated = viewport > realC; // Still bigger than the content: revisit next frame to shrink or undo
        int shift = desired - scrollHeight; // >0 growing past content, <0 shrinking back; both applied
        if (shift == 0) return -1; // Rows already hang correctly; contentH reads the (already correct) scrollHeight
        shiftRows(scrollArea.getStaticChildren(), shift); // The row containers, LINE0..LINE499
        shiftRows(scrollArea.getDynamicChildren(), shift); // Their text/graphic parts, laid out at the row's own Y
        scrollArea.setScrollHeight(desired); // Adopt the height the rows now hang from
        return desired;
    }

    // Total built content height: the row containers are sized to their full wrapped height and stack, so summing them
    // gives the real content extent independent of any reanchor shift (which moves originalY, not height). +2 per builder.
    private static int usedContentHeight(Widget scrollArea) {
        Widget[] rows = scrollArea.getStaticChildren();
        if (rows == null) return 0;
        int sum = 0;
        for (Widget row : rows) {
            if (row == null || row.getHeight() <= 0 || row.getId() < InterfaceID.Chatbox.LINE0) continue;
            sum += row.getHeight();
        }
        return sum == 0 ? 0 : sum + 2;
    }

    private static void shiftRows(Widget[] children, int shift) {
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
        anchorViewport = scrollArea.getHeight(); // The height the row's distance-from-bottom is measured against
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

    // Scroll offset that holds the anchored row at its captured distance from the viewport BOTTOM after a re-wrap, or
    // null if it's gone. Bottom, not top: the chat box is bottom-pinned on screen, so holding the row a fixed distance
    // from the bottom keeps it visually put as height changes. The (anchorViewport - viewport) term is that correction;
    // with no re-wrap it makes this exactly equal the distance-from-bottom pin, so a corner drag holds like height-only.
    private Integer anchorTarget(int viewport, int contentH) {
        if (anchorId == -1) return null;
        Widget row = client.getWidget(anchorId);
        if (row == null || row.getHeight() <= 0) return null; // Row recycled or collapsed away since capture
        int offset = Math.min(anchorOffset, row.getHeight()); // The row may re-wrap shorter than the offset into it
        int target = row.getRelativeY() + offset + (anchorViewport - viewport);
        dbgRy = row.getRelativeY(); dbgOff = offset; dbgPre = target; dbgCeil = Math.max(1, contentH - viewport); // TEMP(scrolltrace)
        return Math.max(0, Math.min(Math.max(1, contentH - viewport), target));
    }

    private void clearAnchor() {
        anchorId = -1;
        anchorOffset = 0;
        anchorScrollY = -1; // Never a real scrollY, so the next scrolled-up frame always re-captures
        anchorContentH = -1;
        anchorViewport = 0;
    }

    // TEMP(scrolltrace): one line per drag frame. gc vs r1112 shows whether 84 coalesced (equal = wrap is stale this
    // frame); rows changing = messages added/removed (slot shift); scrollTo vs rY+off shows the bottom clamp biting.
    private void traceDrag(
        Widget scrollArea, int viewport, int width, int rawContentH, int contentH,
        int scrollY, boolean reanchored, boolean widthChanged, boolean anchorHit, Integer applied
    ) {
        // TEMP(scrolltrace): only emit anomalies so a corner drag doesn't flood the console. An anomaly is a reanchor, the
        // engine moving the scroll out from under our last pin (sYin != lastApp), or our own applied scroll jumping.
        boolean interesting = reanchored
                || (lastApplied >= 0 && Math.abs(scrollY - lastApplied) > 3)
                || (applied != null && lastApplied >= 0 && Math.abs(applied - lastApplied) > 3);
        if (!interesting) return;
        Widget row = anchorId == -1 ? null : client.getWidget(anchorId);
        int rYnow = row == null ? -1 : row.getRelativeY(); // Re-read at log time: differs from at[ry] => the row moved after anchorTarget read it
        String branch = applied == null ? "measure" : widthChanged ? (anchorHit ? "anchor" : "anchorMiss>dist") : "dist";
        int usedRows = 0; // TEMP(scrolltrace): count of live message rows; a change across a drag means a slot shift broke the anchor id
        Widget[] rows = scrollArea.getStaticChildren();
        if (rows != null) for (Widget r : rows) if (r != null && r.getHeight() > 0 && r.getId() >= InterfaceID.Chatbox.LINE0) usedRows++;
        log.debug("scrolltrace[{}{}] gc={} r1112={} reanch={} {} | vp {}->{} w {}->{}(cap {}) cH {}->{}(last {}) sYin={}" +
                  "lastApp={} dfb={} | aId={} rYnow={} rH={} anchOff={} | at[ry={} off={} pre={} ceil={}] scrollTo={} rows={}",
            dragResize.isDraggingTop() ? "T" : "-", dragResize.isDraggingRight() ? "R" : "-",
            client.getGameCycle(), client.getVarcIntValue(VarClientID.CHAT_LASTREBUILD),
            reanchored, branch,
            lastViewport, viewport, lastWidth, width, capturedWidth, rawContentH, contentH, lastContentH, scrollY, lastApplied, distFromBottom,
            anchorId, rYnow, row == null ? -1 : row.getHeight(), anchorOffset,
            dbgRy, dbgOff, dbgPre, dbgCeil, applied, usedRows);
    }
}