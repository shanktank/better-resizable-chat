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
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue(), a = c.getAlpha();
        fill = new Color(r, g, b, a / 5); // 20% of alpha value for band when not hovered
        edge = new Color(r, g, b, a * 4 / 5); // 80% of alpha value for edges when not hovered
        fillHover = new Color(r, g, b, a * 3 / 5); // 60% of alpha value for band when hovered
        edgeHover = new Color(r, g, b, a); // Full alpha value for edges when hovered
    }

    @Override
    public Dimension render(Graphics2D g) {
        boolean bands = drag.isHighlightActive();
        boolean readout = drag.isSizeReadoutActive();
        if (!bands && !readout) return null;

        boolean fixed = drag.isFixedMode();
        if (readout) tooltipManager.add(new Tooltip(fixed
            ? "Y: " + signed(config.fixedHeightChange()) // Fixed layout resizes height only
            : "X: " + signed(config.widthChange()) + "   Y: " + signed(config.heightChange())));

        Rectangle b = drag.getBounds();
        if (b == null) return null; // Suspended between the active-check and here

        ensureColors();
        Point p = drag.getPointer();

        if (bands) {
            int grab = DragResizer.BORDER_GRAB;
            Rectangle top = DragResizer.topBand(b, grab);
            boolean hoverTop = p != null && top.contains(p); // Brighten hovered band

            g.setColor(hoverTop ? fillHover : fill);
            g.fill(top);

            int rightX = b.x + b.width - DragResizer.RIGHT_BAND_SHIFT; // Stone border
            g.setColor(hoverTop ? edgeHover : edge);
            g.drawLine(b.x - grab, b.y, rightX, b.y); // Top edge

            if (!fixed) {
                Rectangle right = DragResizer.rightBand(b, grab);
                boolean hoverRight = p != null && right.contains(p);
                g.setColor(hoverRight ? fillHover : fill);
                g.fill(right);
                g.setColor(hoverRight ? edgeHover : edge);
                g.drawLine(rightX, b.y, rightX, b.y + b.height + grab); // Right edge
            }
        }

        return null;
    }

    // Tooltip signed value text display
    private static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }
}