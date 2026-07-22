package brc;

import brc.internal.SizeClamps;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.HotkeyListener;
import lombok.Getter;
import lombok.Setter;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;

// Let the player resize chat by holding the configured drag key and dragging top or right border of chat box
@Singleton
public final class DragResizeActuator extends MouseAdapter {
    static final int BORDER_GRAB = 6;
    static final int RIGHT_BAND_SHIFT = 1;

    private final ChatResizerConfig config;
    private final ConfigManager configManager;
    private final SecondarySize swapSize;

    @Getter private final KeyListener keyListener; // Tracks the configured drag key's held state via AWT key events

    @Getter private volatile Point pointer; // Last known cursor position in canvas space, published from mouse callbacks
    private volatile Rectangle bounds; // Chat rectangle for mouse hit-testing, published from update on the client thread
    private volatile int maxWidthChange, maxHeightChange; // Size ceilings for the live window, published alongside bounds
    private volatile int canvasH; // Window height, published alongside bounds; keeps the top band reachable (see bandY)
    private volatile boolean modifierHeld; // Written from the hotkey callbacks (AWT thread), read by the overlay (client thread)

    @Getter @Setter private Dimension lastDragSize; // Chat size from the previous drag frame; null while not dragging
    @Getter private volatile boolean dragging; // True while a border drag is in progress
    @Getter private volatile boolean fixedMode; // Fixed layout: only the top border (height) is draggable

    private volatile boolean dragTop, dragRight; // Read by the overlay (client thread) to keep the dragged band lit
    private boolean dragFixed; // Dragging in fixed layout
    private boolean dragSwap; // Dragging the secondary size's values
    private int startX, startY, startExtraW, startExtraH;

    @Inject
    public DragResizeActuator(ChatResizerConfig config, ConfigManager configManager, SecondarySize swapSize) {
        this.config = config;
        this.configManager = configManager;
        this.swapSize = swapSize;
        this.keyListener = new HotkeyListener(config::dragModifier) {
            @Override public void hotkeyPressed() { modifierHeld = true; }
            @Override public void hotkeyReleased() { modifierHeld = false; }
            @Override public void focusLost() { super.focusLost(); dragging = false; }
        };
    }

    // Publishes the chat rectangle, layout and size ceilings for the AWT-thread mouse callbacks and the overlay to read
    public void update(Rectangle bounds, boolean fixedMode, int canvasW, int canvasH) {
        this.bounds = bounds;
        this.fixedMode = fixedMode;
        this.maxWidthChange = SizeClamps.maxWidthChange(canvasW);
        this.maxHeightChange = SizeClamps.maxHeightChange(fixedMode, canvasH);
        this.canvasH = canvasH;
    }

    // Clears all state, including any in-progress drag
    public void reset() {
        bounds = null;
        pointer = null;
        modifierHeld = false;
        dragging = false;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent e) {
        if (dragging || !modifierActive()) return e; // Mouse event can't carry an arbitrary key, so use tracked state

        Rectangle b = bounds;
        if (b == null) return e;

        boolean top = nearTop(b, e);
        boolean right = !fixedMode && nearRight(b, e); // Fixed layout resizes height only, so ignore the right border
        if (!top && !right) return e;

        dragging = true;
        dragTop = top;
        dragRight = right;
        dragFixed = fixedMode;
        dragSwap = swapSize.isActive(); // Captured, so one drag edits one set even if the hotkey flips mid-drag
        startX = e.getX();
        startY = e.getY();
        // Take live config as baseline so ungrabbed/unchanged axis is treated as no-op
        startExtraW = swapSize.effectiveWidthChange();
        startExtraH = dragFixed ? swapSize.effectiveFixedHeightChange() : swapSize.effectiveHeightChange();

        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e) {
        pointer = e.getPoint();
        if (!dragging) return e;
        applyDrag(e);
        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e) {
        if (!dragging) return e;
        applyDrag(e);
        dragging = false;
        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e) {
        pointer = e.getPoint();
        return e;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent e) {
        pointer = null; // Cursor left the canvas; drop the hover so the bands dim and the readout hides
        return e;
    }

    // Writes offsets from the cursor's initial state as new values; edits the secondary set while it's active
    private void applyDrag(MouseEvent e) {
        if (dragRight) updateConfig(draggedWidthKey(), clamp(startExtraW + (e.getX() - startX), SizeClamps.MIN_WIDTH_CHANGE, maxWidthChange));
        if (dragTop) updateConfig(draggedHeightKey(), clamp(startExtraH + (startY - e.getY()), SizeClamps.MIN_HEIGHT_CHANGE, maxHeightChange));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String draggedWidthKey() {
        return dragSwap ? ChatResizerConfig.SWAP_WIDTH_CHANGE : ChatResizerConfig.WIDTH_CHANGE;
    }

    private String draggedHeightKey() {
        if (dragSwap) return ChatResizerConfig.SWAP_HEIGHT_CHANGE; // Both layouts share the secondary height
        return dragFixed ? ChatResizerConfig.FIXED_HEIGHT_CHANGE : ChatResizerConfig.HEIGHT_CHANGE;
    }

    private void updateConfig(String key, int value) {
        Integer current = configManager.getConfiguration(ChatResizerConfig.GROUP, key, Integer.class);
        if (current == null || current != value) configManager.setConfiguration(ChatResizerConfig.GROUP, key, value);
    }

    // Cursor within boundaries to alter height change on click-drag
    private boolean nearTop(Rectangle b, MouseEvent e) {
        int y = bandY(b, BORDER_GRAB, canvasH);
        return e.getY() >= y && e.getY() <= y + BORDER_GRAB && e.getX() >= b.x - BORDER_GRAB && e.getX() <= b.x + b.width - RIGHT_BAND_SHIFT;
    }

    // Cursor within boundaries to alter width change on click-drag
    private static boolean nearRight(Rectangle b, MouseEvent e) {
        int outerX = b.x + b.width - RIGHT_BAND_SHIFT;
        return e.getX() >= outerX - BORDER_GRAB && e.getX() <= outerX && e.getY() >= b.y && e.getY() <= b.y + b.height + BORDER_GRAB;
    }

    // Drag key enabled and held
    private boolean modifierActive() {
        return modifierHeld && !Keybind.NOT_SET.equals(config.dragModifier());
    }

    // Whether the draggable border bands should be drawn this frame: a drag is in progress, or the drag key is active
    boolean isHighlightActive() {
        return dragging || modifierActive();
    }

    boolean isDraggingTop() {
        return dragging && dragTop;
    }

    boolean isDraggingRight() {
        return dragging && dragRight;
    }

    static Rectangle rightBand(Rectangle b, int grab) {
        return new Rectangle(b.x + b.width - grab - RIGHT_BAND_SHIFT, b.y, grab + 1, b.height + grab);
    }

    static Rectangle topBand(Rectangle b, int grab, int canvasH) {
        return new Rectangle(b.x - grab, bandY(b, grab, canvasH), b.width + grab + 1 - RIGHT_BAND_SHIFT, grab);
    }

    // Hold a grab width inside canvas bottom so chat fully shrunk at canvas bottom can still be grabbed
    static int bandY(Rectangle b, int grab, int canvasH) {
        return Math.min(b.y, canvasH - grab);
    }

    // Whether the size readout should show: a drag is in progress, or the drag key is held with the cursor over a band
    boolean isSizeReadoutActive() {
        if (dragging) return true;
        if (!modifierActive()) return false;
        Rectangle b = bounds; // Both are published from other threads; read once so a clear can't null them mid-test
        Point p = pointer;
        if (b == null || p == null) return false;
        if (topBand(b, BORDER_GRAB, canvasH).contains(p)) return true;
        return !fixedMode && rightBand(b, BORDER_GRAB).contains(p); // No right band in fixed layout
    }
}