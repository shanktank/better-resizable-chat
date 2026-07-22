package brc;

import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;

// Highlight draggable areas on top and right chat box borders
@Singleton
public final class DragResizePreview extends Overlay {
    private final Client client;
    private final ChatResizerConfig config;
    private final DragResizeActuator drag;
    private final TooltipManager tooltipManager;
    private final SecondarySize swapSize;

    private static final Color VALUE = Color.CYAN;
    private static final Color PRIMARY = Color.GREEN;
    private static final Color SECONDARY = new Color(0xFF77FF);

    private Color base, fill, edge, fillHover, edgeHover;

    @Inject
    public DragResizePreview(
        Client client, ChatResizerConfig config,
        DragResizeActuator drag, SecondarySize swapSize,
        TooltipManager tooltipManager
    ) {
        this.client = client;
        this.config = config;
        this.drag = drag;
        this.swapSize = swapSize;
        this.tooltipManager = tooltipManager;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    // Update derived draw colors when configured indicator is changed
    private void ensureColors() {
        Color c = config.indicatorColor();
        if (c.equals(base)) return;
        base = c;
        int a = c.getAlpha();
        fill = ColorUtil.colorWithAlpha(c, a / 5); // 20% of alpha value for band when not hovered
        edge = ColorUtil.colorWithAlpha(c, a * 4 / 5); // 80% of alpha value for edges when not hovered
        fillHover = ColorUtil.colorWithAlpha(c, a * 3 / 5); // 60% of alpha value for band when hovered
        edgeHover = ColorUtil.colorWithAlpha(c, a); // Full alpha value for edges when hovered
    }

    @Override
    public Dimension render(Graphics2D g) {
        boolean bands = drag.isHighlightActive();
        boolean readout = drag.isSizeReadoutActive();
        if (!bands && !readout) return null;

        boolean fixed = drag.isFixedMode();

        if (readout) {
            String xy = fixed
                ? "Y: " + signed(swapSize.effectiveFixedHeightChange()) // Fixed layout resizes height only
                : "X: " + signed(swapSize.effectiveWidthChange()) + "   Y: " + signed(swapSize.effectiveHeightChange());
            String set = swapSize.isActive()
                ? "(" + ColorUtil.wrapWithColorTag("Secondary", SECONDARY) + ")"
                : "(" + ColorUtil.wrapWithColorTag("Primary", PRIMARY) + ")";
            tooltipManager.add(new Tooltip(xy + "   " + set));
        }

        // Read the slot live to ensure drawn indicators don't lag
        Widget slot = Widgets.chatSlot(client);
        if (slot == null) return null;
        Rectangle b = Widgets.liveBounds(slot);

        ensureColors();
        Point p = drag.getPointer();

        if (bands) {
            int grab = DragResizeActuator.BORDER_GRAB;
            Rectangle top = DragResizeActuator.topBand(b, grab, client.getCanvasHeight());
            boolean hoverTop = drag.isDraggingTop() || (p != null && top.contains(p)); // Brighten if hovered/dragging

            g.setColor(hoverTop ? fillHover : fill);
            g.fill(top);

            int rightX = b.x + b.width - DragResizeActuator.RIGHT_BAND_SHIFT; // Stone border
            g.setColor(hoverTop ? edgeHover : edge);
            g.drawLine(b.x - grab, top.y, rightX, top.y); // Top edge, off the band so it follows a band held on screen

            if (!fixed) {
                Rectangle right = DragResizeActuator.rightBand(b, grab);
                boolean hoverRight = drag.isDraggingRight() || (p != null && right.contains(p));
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
        return ColorUtil.wrapWithColorTag(v > 0 ? "+" + v : Integer.toString(v), VALUE);
    }
}