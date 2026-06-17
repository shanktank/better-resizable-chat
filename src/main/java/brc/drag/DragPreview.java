package brc.drag;

import brc.BetterResizableChatConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

// Highlight draggable areas on top and right chat box borders
public final class DragPreview extends Overlay {
    private static final int FILL_ALPHA = 50;
    private static final int EDGE_ALPHA = 200;
    private static final int FILL_HOVER_ALPHA = 150;
    private static final int EDGE_HOVER_ALPHA = 255;

    private final DragResizer drag;
    private final TooltipManager tooltipManager; // RuneLite's built-in tooltip manager
    private final BetterResizableChatConfig config;

    private Color base, fill, edge, fillHover, edgeHover;

    public DragPreview(DragResizer drag, BetterResizableChatConfig config, TooltipManager tooltipManager) {
        this.drag = drag;
        this.config = config;
        this.tooltipManager = tooltipManager;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    // Update derived draw colors when configured indicator is changed
    private void ensureColors() {
        Color c = config.indicatorColor();
        if (c.equals(base)) return;
        base = c;
        int r = c.getRed();
        int gn = c.getGreen();
        int bl = c.getBlue();
        fill = new Color(r, gn, bl, FILL_ALPHA);
        edge = new Color(r, gn, bl, EDGE_ALPHA);
        fillHover = new Color(r, gn, bl, FILL_HOVER_ALPHA);
        edgeHover = new Color(r, gn, bl, EDGE_HOVER_ALPHA);
    }

    @Override
    public Dimension render(Graphics2D g) {
        boolean bands = drag.isHighlightActive();
        boolean readout = drag.isSizeReadoutActive();
        if (!bands && !readout) return null;

        if (readout) tooltipManager.add(new Tooltip("X: " + signed(config.widthChange()) + "   Y: " + signed(config.heightChange())));

        Rectangle b = drag.getBounds();
        if (b == null) return null; // Suspended between the active-check and here

        ensureColors();
        Point p = drag.getPointer();

        if (bands) {
            int grab = DragResizer.BORDER_GRAB;
            Rectangle top = DragResizer.topBand(b, grab);
            Rectangle right = DragResizer.rightBand(b, grab);

            // Brighten hovered band(s)
            boolean hoverTop = p != null && top.contains(p);
            boolean hoverRight = p != null && right.contains(p);

            g.setColor(hoverTop ? fillHover : fill);
            g.fill(top);
            g.setColor(hoverRight ? fillHover : fill);
            g.fill(right);

            int rightX = b.x + b.width - DragResizer.RIGHT_BAND_SHIFT; // Stone border
            g.setColor(hoverTop ? edgeHover : edge);
            g.drawLine(b.x - grab, b.y, rightX, b.y); // Top edge
            g.setColor(hoverRight ? edgeHover : edge);
            g.drawLine(rightX, b.y, rightX, b.y + b.height + grab); // Right edge
        }

        return null;
    }

    // Tooltip signed value text display
    private static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }
}