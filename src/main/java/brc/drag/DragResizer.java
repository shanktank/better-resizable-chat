package brc.drag;

import brc.BetterResizableChatConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import lombok.Getter;
import lombok.Setter;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

// Let the player resize chat by holding the configured drag key and dragging top or right border of chat box
public final class DragResizer extends MouseAdapter {
    static final int BORDER_GRAB = 6;
    static final int RIGHT_BAND_SHIFT = 1;

    private final BetterResizableChatConfig config;
    private final ConfigManager configManager;

    @Getter private final KeyListener keyListener; // Tracks the configured drag key's held state via AWT key events

    @Getter private volatile Rectangle bounds; // Current chat rectangle, published from update on the client thread
    @Getter private volatile Point pointer; // Last known cursor position in canvas space, published from mouse callbacks
    private volatile boolean modifierHeld; // Written from the hotkey callbacks (AWT thread), read by the overlay (client thread)

    @Getter @Setter private Dimension lastDragSize; // Chat size from the previous drag frame; null while not dragging
    @Getter private volatile boolean dragging; // True while a border drag is in progress

    private boolean dragTop, dragRight;
    private int startX, startY, startExtraW, startExtraH;

    public DragResizer(BetterResizableChatConfig config, ConfigManager configManager) {
        this.config = config;
        this.configManager = configManager;
        this.keyListener = new KeyListener() { // Track the drag key's held state without consuming the event
            @Override public void keyTyped(KeyEvent e) {}
            @Override public void keyPressed(KeyEvent e) { if (config.dragModifier().matches(e)) modifierHeld = true; }
            @Override public void keyReleased(KeyEvent e) { if (config.dragModifier().matches(e)) modifierHeld = false; }
            @Override public void focusLost() { modifierHeld = false; } // Window blur (alt-tab while held) would otherwise stick it on
        };
    }

    // Publishes the current chat rectangle for the mouse callbacks and overlay to read
    public void update(Rectangle bounds) {
        this.bounds = bounds;
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
        if (dragging || !modifierActive()) return e; // An arbitrary drag key isn't carried in the mouse event, so gate on the tracked held-state

        Rectangle b = bounds;
        if (b == null) return e;

        boolean top = nearTop(b, e);
        boolean right = nearRight(b, e);
        if (!top && !right) return e;

        dragging = true;
        dragTop = top;
        dragRight = right;
        startX = e.getX();
        startY = e.getY();
        // Take live config as baseline so ungrabbed/unchanged axis is treated as no-op
        startExtraW = config.widthChange();
        startExtraH = config.heightChange();

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

    // Writes offsets from cursor's initial state as new values
    private void applyDrag(MouseEvent e) {
        if (dragRight) updateConfig(BetterResizableChatConfig.WIDTH_CHANGE, startExtraW + (e.getX() - startX));
        if (dragTop) updateConfig(BetterResizableChatConfig.HEIGHT_CHANGE, startExtraH + (startY - e.getY()));
    }

    private void updateConfig(String key, int value) {
        int current = configManager.getConfiguration(BetterResizableChatConfig.GROUP, key, int.class);
        if (current != value) configManager.setConfiguration(BetterResizableChatConfig.GROUP, key, value);
    }

    // Cursor within boundaries to alter height change on click-drag
    private static boolean nearTop(Rectangle b, MouseEvent e) {
        return e.getY() >= b.y && e.getY() <= b.y + BORDER_GRAB && e.getX() >= b.x - BORDER_GRAB && e.getX() <= b.x + b.width - RIGHT_BAND_SHIFT;
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

    // Whether the size readout should show: a drag is in progress, or the drag key is held with the cursor over a band
    boolean isSizeReadoutActive() {
        if (dragging) return true;
        if (!modifierActive()) return false;
        return bounds != null && pointer != null && (topBand(bounds, BORDER_GRAB).contains(pointer) || rightBand(bounds, BORDER_GRAB).contains(pointer));
    }

    static Rectangle topBand(Rectangle b, int grab) {
        return new Rectangle(b.x - grab, b.y, b.width + grab + 1 - RIGHT_BAND_SHIFT, grab);
    }

    static Rectangle rightBand(Rectangle b, int grab) {
        return new Rectangle(b.x + b.width - grab - RIGHT_BAND_SHIFT, b.y, grab + 1, b.height + grab);
    }
}