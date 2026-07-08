package brc;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;

public class ChatBackgroundGraphic {
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

    // Chat tab bar and buttons below chat box
    private static final int[] DEFAULT_TAB_X = {458, 396, 334, 272, 210, 148, 86};
    private static final int[] CHAT_TAB_BUTTONS = {
        InterfaceID.Chatbox.CHAT_ALL,
        InterfaceID.Chatbox.CHAT_GAME,
        InterfaceID.Chatbox.CHAT_PUBLIC,
        InterfaceID.Chatbox.CHAT_PRIVATE,
        InterfaceID.Chatbox.CHAT_FRIENDSCHAT,
        InterfaceID.Chatbox.CHAT_CLAN,
        InterfaceID.Chatbox.CHAT_TRADE,
    };

    private static final int BACKGROUND_STRETCH_X = 40;
    private static final int BACKGROUND_STRETCH_Y = 75;

    private static final int CORNER_BLEED_TRIM = 1;

    private final Client client;

    private Widget[] borderPieces;

    ChatBackgroundGraphic(Client client) {
        this.client = client;
    }

    private Widget getBackgroundBody(Widget background) {
        Widget[] dynamic = background.getDynamicChildren();
        if (dynamic == null || dynamic.length == 0) return null;
        return dynamic[0];
    }

    // Stretch the background sprite so the baked-in edges get clipped, args are absolute target dims
    void zoomBakedSprite(int widthChange, int heightChange) {
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (background == null) return;
        Widget body = getBackgroundBody(background);
        if (body == null) return;

        // Must use setWidth/setHeight here
        BetterResizableChatPlugin.setWidth(body, widthChange + BACKGROUND_STRETCH_X * 2);
        BetterResizableChatPlugin.setHeight(body, heightChange + BACKGROUND_STRETCH_Y * 2);
        body.setForcedPosition(body.getOriginalX() - BACKGROUND_STRETCH_X, body.getOriginalY() - BACKGROUND_STRETCH_Y);
        body.setSpriteTiling(false);

        // Trim the transparent corner pixels off the background parchment
        background.setSize(CORNER_BLEED_TRIM, CORNER_BLEED_TRIM);
        background.setForcedPosition(0, CORNER_BLEED_TRIM);
        background.revalidate();
    }

    void revertBakedSprite() {
        Widget background = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
        if (background != null) {
            // Revert corner bleed adjustment
            background.setSize(0, 0);
            background.setForcedPosition(-1, -1);
            background.revalidate();

            Widget body = getBackgroundBody(background);
            if (body != null) {
                // Revert background sprite zoom
                body.setSpriteTiling(true);
                body.setForcedPosition(-1, -1);
                body.revalidate();
            }
        }
    }

    // Add border pieces and position them as children of CHATAREA since CHAT_BACKGROUND is finicky
    void drawBorder(Widget chatArea) {
        if (client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1) return;

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
                if (idx >= 0 && idx < children.length) {
                    children[idx] = null;
                }
            }
        }

        borderPieces = null;
    }

    // True when all border pieces are live under the latest widget
    boolean borderPresent(Widget chatArea) {
        if (borderPieces == null) return false;

        for (Widget borderPiece : borderPieces) {
            if (borderPiece == null) return false;
            if (chatArea.getChild(borderPiece.getIndex()) != borderPiece) return false;
        }

        return true;
    }

    // Passing 0 resets behavior to stock
    void resizeTabBar(int dw) {
        Widget controls = client.getWidget(InterfaceID.Chatbox.CONTROLS);
        Widget graphic = client.getWidget(InterfaceID.Chatbox.CONTROLS_BACKGROUND_GRAPHIC);
        if (controls == null || graphic == null) return;

        Widget[] tabs = getTabs();
        if (tabs == null) return;

        // Update background graphic width, gaps between tab buttons
        controls.setOriginalWidth(BetterResizableChatPlugin.CHATBOX_SPRITE_W + dw);
        graphic.setOriginalWidth(BetterResizableChatPlugin.CHATBOX_SPRITE_W + dw);
        for (int i = 0; i < tabs.length; i++) tabs[i].setOriginalX(tabTargetX(i, dw, tabs.length));
    }

    // Check if tab bar is already laid out for this width delta
    boolean tabBarMatches(int dw) {
        Widget controls = client.getWidget(InterfaceID.Chatbox.CONTROLS);
        if (controls == null) return true;
        Widget[] tabs = getTabs();
        if (tabs == null) return true;

        if (controls.getWidth() != BetterResizableChatPlugin.CHATBOX_SPRITE_W + dw) return false;
        for (int i = 0; i < tabs.length; i++) if (tabs[i].getOriginalX() != tabTargetX(i, dw, tabs.length)) return false;
        return true;
    }

    private Widget[] getTabs() {
        Widget[] tabs = new Widget[CHAT_TAB_BUTTONS.length];
        for (int i = 0; i < CHAT_TAB_BUTTONS.length; i++) {
            tabs[i] = client.getWidget(CHAT_TAB_BUTTONS[i]);
            if (tabs[i] == null) return null; // Not all tabs present yet
            if (tabs[i].getXPositionMode() != WidgetPositionMode.ABSOLUTE_RIGHT) return null; // ABSOLUTE_LEFT during construction
        }
        return tabs;
    }

    private static int tabTargetX(int i, int dw, int n) {
        return DEFAULT_TAB_X[i] + dw * (n - i) / n;
    }

    // Tab index of a chat tab button's component id (equals its varc CHAT_VIEW value), or -1 if not a chat tab
    static int tabIndexOf(int componentId) {
        for (int i = 0; i < CHAT_TAB_BUTTONS.length; i++) if (CHAT_TAB_BUTTONS[i] == componentId) return i;
        return -1;
    }
}