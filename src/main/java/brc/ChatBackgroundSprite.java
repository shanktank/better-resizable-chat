package brc;

import brc.internal.ChatGeometry;
import brc.internal.Widgets;
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatBackgroundSprite {
    // Manually-cobbled chat box border
    private static final int CORNER_SIZE = 32;
    private static final int BORDER_OFFSET = 12;
    private static final int[] BORDER_SPRITES = {
        SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_TOP_LEFT,
        SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_TOP_RIGHT,
        SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_BOTTOM_LEFT,
        SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_BOTTOM_RIGHT,
        SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_TOP,
        SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_LEFT,
        SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_BOTTOM,
        SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_RIGHT,
    };

    // Chat tab bar, buttons, and stock layout values
    private static final int TAB_STOCK_W = 56;
    private static final int TAB_STOCK_GAP = 6;
    private static final int TAB_STOCK_MARGIN = 5;
    private static final int[] TAB_STOCK_X = { 458, 396, 334, 272, 210, 148, 86 };
    @Component private static final int[] CHAT_TAB_BUTTONS = {
        InterfaceID.Chatbox.CHAT_ALL,
        InterfaceID.Chatbox.CHAT_GAME,
        InterfaceID.Chatbox.CHAT_PUBLIC,
        InterfaceID.Chatbox.CHAT_PRIVATE,
        InterfaceID.Chatbox.CHAT_FRIENDSCHAT,
        InterfaceID.Chatbox.CHAT_CLAN,
        InterfaceID.Chatbox.CHAT_TRADE,
    };
    @Component private static final int[] CHAT_TAB_GRAPHICS = {
        InterfaceID.Chatbox.CHAT_ALL_GRAPHIC,
        InterfaceID.Chatbox.CHAT_GAME_GRAPHIC,
        InterfaceID.Chatbox.CHAT_PUBLIC_GRAPHIC,
        InterfaceID.Chatbox.CHAT_PRIVATE_GRAPHIC,
        InterfaceID.Chatbox.CHAT_FRIENDSCHAT_GRAPHIC,
        InterfaceID.Chatbox.CHAT_CLAN_GRAPHIC,
        InterfaceID.Chatbox.CHAT_TRADE_GRAPHIC,
    };

    // Tab bar button shifting and label alignment
    private static final int ALIGN_SHIFT = 1;
    private static final int MINIMUM_GAP = 10;

    // Chat background trimming and border cropping
    private static final int BAKED_BORDER = 8;
    private static final int CORNER_BLEED_TRIM = 1;

    private final Client client;
    private final ChatResizerConfig config;

    private Widget[] borderPieces;
    private boolean trimmed;
    private boolean zoomed;

    @Inject
    ChatBackgroundSprite(Client client, ChatResizerConfig config) {
        this.client = client;
        this.config = config;
    }

    // Fit the background to the box: zoom an opaque chat's parchment, re-stack a transparent one's gradient
    void syncBackground(int targetW, int targetH) {
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (background == null) return;
        Widget body = getBackgroundBody(background);
        if (!isParchment(body)) { // Nothing to clip, so the zoom comes off entirely
            untrimBackground(background);
            stackGradientBands(background, targetH);
            return;
        }

        if (config.noBackgroundZoom()) {
            unzoomBakedSprite(body);
        } else {
            zoomBakedSprite(body, targetW, targetH);
        }

        // Only worth shaving the corners off a zoomed sprite whose border is redrawn over them
        if (config.noBackgroundZoom() || config.noBorders()) {
            untrimBackground(background);
        } else {
            trimBackground(background);
        }
    }

    void revertBackground() {
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (background == null) return;
        untrimBackground(background);
        Widget body = getBackgroundBody(background);
        if (isParchment(body)) {
            unzoomBakedSprite(body);
        } else {
            stackGradientBands(background, ChatGeometry.CHATBOX_SPRITE_H);
        }
    }

    // Overscan the sprite so exactly the baked bevel spills past the box edges and clips
    private void zoomBakedSprite(Widget body, int targetW, int targetH) {
        int ohX = bevelOverscan(targetW, ChatGeometry.CHATBOX_SPRITE_W);
        int ohY = bevelOverscan(targetH, ChatGeometry.CHATBOX_SPRITE_H);
        Widgets.setWidth(body, targetW + ohX * 2);
        Widgets.setHeight(body, targetH + ohY * 2);
        body.setForcedPosition(body.getOriginalX() - ohX, body.getOriginalY() - ohY);
        body.setSpriteTiling(false);
        zoomed = true;
    }

    // Screen overscan per side that clips exactly BAKED_BORDER source px once the sprite stretches to target
    private static int bevelOverscan(int target, int nativeSize) {
        int denom = nativeSize - 2 * BAKED_BORDER;
        if (denom <= 0) return BAKED_BORDER;
        return (BAKED_BORDER * target + denom - 1) / denom;
    }

    // Trim the transparent corner pixels off the background parchment; re-asserted each pass, the engine resets the size
    private void trimBackground(Widget background) {
        background.setSize(CORNER_BLEED_TRIM, CORNER_BLEED_TRIM);
        background.setForcedPosition(0, CORNER_BLEED_TRIM);
        background.revalidate();
        trimmed = true;
    }

    // Revert the sprite zoom; the revalidate resolves the literal size back off the widget's own layout fields
    private void unzoomBakedSprite(Widget body) {
        if (!zoomed) return;
        zoomed = false;
        body.setSpriteTiling(true);
        body.setForcedPosition(-1, -1);
        body.revalidate();
    }

    // Revert the corner bleed adjustment; the engine restores the size on its own rebuilds but never the position
    private void untrimBackground(Widget background) {
        if (!trimmed) return;
        trimmed = false;
        background.setSize(0, 0);
        background.setForcedPosition(-1, -1);
        background.revalidate();
    }

    // A transparent chat draws as a stack of bands the engine cuts to the height it read on its last rebuild, so
    // they hold that height through a resize. Re-cut them; the last band takes the remainder the division dropped.
    private static void stackGradientBands(Widget background, int targetH) {
        Widget[] bands = background.getDynamicChildren();
        if (bands == null || bands.length == 0) return;
        if (background.getHeight() != targetH) return; // Box isn't at our size, e.g. drawing small mid layout swap
        if (targetH < bands.length) return; // Too short to divide: every band but the last would collapse to nothing

        int n = bands.length, bandH = targetH / n;
        for (int i = 0; i < n; i++) {
            Widget band = bands[i];
            if (band == null || band.getHeightMode() != WidgetSizeMode.ABSOLUTE) continue; // Fills on its own
            int y = bandH * i, h = i == n - 1 ? targetH - y : bandH;
            if (band.getOriginalY() == y && band.getOriginalHeight() == h) continue;
            band.setOriginalY(y);
            band.setOriginalHeight(h);
            band.revalidate();
        }
    }

    // Add border pieces and position them as children of CHATAREA since CHAT_BACKGROUND is finicky
    private void drawBorder(Widget chatArea, Widget parchment) {
        if (!borderPresent(chatArea)) {
            borderPieces = new Widget[BORDER_SPRITES.length];
            for (int i = 0; i < BORDER_SPRITES.length; i++) {
                Widget w = chatArea.createChild(-1, WidgetType.GRAPHIC);
                w.setSpriteId(BORDER_SPRITES[i]);
                w.setSpriteTiling(true);
                borderPieces[i] = w;
            }
        }

        int w = chatArea.getWidth();
        int h = chatArea.getHeight();
        int innerW = Math.max(0, w - 2 * CORNER_SIZE);
        int innerH = Math.max(0, h - 2 * CORNER_SIZE);
        int[][] rects = {
            //x                                y                                width        height
            { 0,                               0,                               CORNER_SIZE, CORNER_SIZE }, // tl
            { w - CORNER_SIZE,                 0,                               CORNER_SIZE, CORNER_SIZE }, // tr
            { 0,                               h - CORNER_SIZE,                 CORNER_SIZE, CORNER_SIZE }, // bl
            { w - CORNER_SIZE,                 h - CORNER_SIZE,                 CORNER_SIZE, CORNER_SIZE }, // br
            { CORNER_SIZE,                    -BORDER_OFFSET - 1,               innerW,      CORNER_SIZE }, // top
            {-BORDER_OFFSET - 1,               CORNER_SIZE,                     CORNER_SIZE, innerH      }, // left
            { CORNER_SIZE,                     h - CORNER_SIZE + BORDER_OFFSET, innerW,  CORNER_SIZE     }, // bottom
            { w - CORNER_SIZE + BORDER_OFFSET, CORNER_SIZE,                     CORNER_SIZE, innerH      }, // right
        };

        for (int i = 0; i < rects.length; i++) {
            borderPieces[i].setOriginalX(rects[i][0]);
            borderPieces[i].setOriginalY(rects[i][1]);
            borderPieces[i].setSize(rects[i][2], rects[i][3]);
            borderPieces[i].revalidate();
        }

        syncBorderVisibility(parchment);
    }

    void destroyBorder() {
        if (borderPieces == null) return;

        Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        if (chatArea == null) return;
        Widget[] children = chatArea.getChildren();
        if (children == null) return;

        for (Widget borderPiece : borderPieces) {
            if (borderPiece != null) {
                int idx = borderPiece.getIndex();
                if (idx >= 0 && idx < children.length) children[idx] = null;
            }
        }

        borderPieces = null;
    }

    // True when all border pieces are live under the latest widget
    private boolean borderPresent(Widget chatArea) {
        if (borderPieces == null) return false;

        for (Widget borderPiece : borderPieces) {
            if (borderPiece == null) return false;
            if (chatArea.getChild(borderPiece.getIndex()) != borderPiece) return false;
        }

        return true;
    }

    // Draw, refresh, or tear down chat border, keyed on how the engine has built the background this frame
    void syncBorder(Widget chatArea, boolean reposition) {
        if (config.noBorders()) {
            destroyBorder();
            return;
        }

        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        Widget body = background == null ? null : getBackgroundBody(background);

        if (body == null) {
            syncBorderVisibility(null); // Box blanked (e.g. a cutscene): keep the pieces parked and hidden
        } else if (!isParchment(body)) {
            destroyBorder(); // Transparent chat draws gradient bands instead of parchment, so there's nothing to frame
        } else if (reposition || !borderPresent(chatArea)) { // Not present or we need to reposition
            drawBorder(chatArea, body);
        } else {
            syncBorderVisibility(body);
        }
    }

    // Match the border to the parchment it frames, which the game hides or drops out from under us
    private void syncBorderVisibility(Widget parchment) {
        if (borderPieces == null) return;

        boolean hidden = parchment == null || parchment.isHidden();
        for (Widget piece : borderPieces) if (piece != null && piece.isSelfHidden() != hidden) piece.setHidden(hidden);
    }

    // Passing 0 resets behavior to stock
    void resizeTabBar(int dw) {
        Widget controls = client.getWidget(InterfaceID.Chatbox.CONTROLS);
        Widget graphic = client.getWidget(InterfaceID.Chatbox.CONTROLS_BACKGROUND_GRAPHIC);
        if (controls == null || graphic == null) return;

        Widget[] tabs = getTabs();
        if (tabs == null) return;

        // Update background graphic width, gaps between tab buttons
        controls.setOriginalWidth(ChatGeometry.CHATBOX_SPRITE_W + dw);
        graphic.setOriginalWidth(ChatGeometry.CHATBOX_SPRITE_W + dw);

        // Adjust chat tab button widths, graphics, and labels
        boolean resize = config.resizeTabButtons();
        boolean measure = resize && dw != 0; // Stock and revert paths stay centered
        int n = tabs.length, stonesTW = stonesTotal(dw, n), gapsTW = gapsTotal(dw, n);
        for (int i = 0; i < n; i++) {
            Widget[] tabSC = tabs[i].getStaticChildren();
            int w = resize ? tabTargetW(i, stonesTW, n) : TAB_STOCK_W;
            tabs[i].setOriginalWidth(w);
            tabs[i].setOriginalX(resize ? tabStretchX(i, dw, stonesTW, gapsTW, n) : tabSpreadX(i, dw, n));
            if (tabSC.length > 0 && tabSC[0] != null) tabSC[0].setOriginalWidth(w); // Resize tab button graphic
            if (tabSC.length > 1 && tabSC[1] != null) alignLabel(tabSC[1], measure, w); // Tab button label text
            if (tabSC.length > 2 && tabSC[2] != null) alignLabel(tabSC[2], measure, w); // Tab button config text
        }
    }

    // Check if tab bar is already laid out for this width delta
    boolean tabBarMatches(int dw) {
        Widget controls = client.getWidget(InterfaceID.Chatbox.CONTROLS);
        if (controls == null) return true;
        Widget[] tabs = getTabs();
        if (tabs == null) return true;

        if (controls.getWidth() != ChatGeometry.CHATBOX_SPRITE_W + dw) return false;

        boolean resize = config.resizeTabButtons();
        int n = tabs.length, stonesTW = stonesTotal(dw, n), gapsTW = gapsTotal(dw, n);
        for (int i = 0; i < n; i++) {
            int w = resize ? tabTargetW(i, stonesTW, n) : TAB_STOCK_W;
            if (tabs[i].getOriginalWidth() != w) return false;
            int x = resize ? tabStretchX(i, dw, stonesTW, gapsTW, n) : tabSpreadX(i, dw, n);
            if (tabs[i].getOriginalX() != x) return false;
            Widget stone = client.getWidget(CHAT_TAB_GRAPHICS[i]);
            if (stone != null && stone.getOriginalWidth() != w) return false;
        }

        return true;
    }

    // Tab index of a chat tab button's component ID (equals its varc CHAT_VIEW value), or -1 if not a chat tab
    static int tabIndexOf(@Component int componentId) {
        for (int i = 0; i < CHAT_TAB_BUTTONS.length; i++) if (CHAT_TAB_BUTTONS[i] == componentId) return i;
        return -1;
    }

    // The engine rebuilds these to match how the chat actually draws: one parchment sprite when opaque (set opaque,
    // or a dialog forcing it), gradient rectangles when transparent, and none at all while the box is blanked
    private Widget getBackgroundBody(Widget background) {
        Widget[] dynamic = background.getDynamicChildren();
        if (dynamic == null || dynamic.length == 0) return null;
        return dynamic[0];
    }

    private static boolean isParchment(Widget body) {
        return body != null && body.getType() == WidgetType.GRAPHIC;
    }

    private Widget[] getTabs() {
        Widget[] tabs = new Widget[CHAT_TAB_BUTTONS.length];
        for (int i = 0; i < CHAT_TAB_BUTTONS.length; i++) {
            tabs[i] = client.getWidget(CHAT_TAB_BUTTONS[i]);
            if (tabs[i] == null) return null; // Not all tabs present yet
            if (tabs[i].getXPositionMode() != WidgetPositionMode.ABSOLUTE_RIGHT) return null; // Still in construction
        }
        return tabs;
    }

    // Width available for the stones and gaps, between the left margin and the report button
    private static int tabSpace(int dw, int n) {
        return n * TAB_STOCK_W + (n - 1) * TAB_STOCK_GAP + dw;
    }

    // Combined gap width: stock-constant while growing; absorbs shrink first, until the stones touch
    private static int gapsTotal(int dw, int n) {
        return Math.max(MINIMUM_GAP, Math.min((n - 1) * TAB_STOCK_GAP, tabSpace(dw, n) - n * TAB_STOCK_W));
    }

    // Combined stone width: absorbs growth beyond stock, and shrink once the gaps are gone
    private static int stonesTotal(int dw, int n) {
        return Math.max(n, tabSpace(dw, n) - gapsTotal(dw, n));
    }

    // Stone widths distributed evenly
    private static int tabTargetW(int i, int stones, int n) {
        return stones * (i + 1) / n - stones * i / n;
    }

    // Offset from the bar's right edge when stones stay stock-size: spread them proportionally instead
    private static int tabSpreadX(int i, int dw, int n) {
        return TAB_STOCK_X[i] + dw * (n - i) / n;
    }

    // Offset from the bar's right edge
    private static int tabStretchX(int i, int dw, int stones, int gaps, int n) {
        int x = TAB_STOCK_MARGIN + stones * i / n + gaps * i / (n - 1);
        return ChatGeometry.CHATBOX_SPRITE_W + dw - x - tabTargetW(i, stones, n);
    }

    // Left-align a label when its text is too wide to draw centered in its stone
    private static void alignLabel(Widget label, boolean measure, int stoneW) {
        int alignment = measure && labelOverflows(label, stoneW) ? WidgetTextAlignment.LEFT : WidgetTextAlignment.CENTER;
        int originalX = alignment == WidgetTextAlignment.LEFT ? ALIGN_SHIFT : 0; // Default originalX is 0
        if (label.getXTextAlignment() == alignment && label.getOriginalX() == originalX) return;
        label.setXTextAlignment(alignment);
        label.setOriginalX(originalX);
        label.revalidate(); // In case main apply short-circuits
    }

    // Measure the label's text in its own font (tags like <col> are skipped by getTextWidth)
    private static boolean labelOverflows(Widget label, int stoneW) {
        FontTypeFace font = label.getFont();
        String text = label.getText();
        return font != null && text != null && font.getTextWidth(text) > stoneW;
    }
}