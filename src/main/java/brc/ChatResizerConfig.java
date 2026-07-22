package brc;

import brc.internal.SizeClamps;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import java.awt.Color;

@ConfigGroup(ChatResizerConfig.GROUP)
public interface ChatResizerConfig extends Config {
    String GROUP = "betterresizablechat";

    // Resizable layout settings

    String HEIGHT_CHANGE = "heightChange";
    String WIDTH_CHANGE = "widthChange";
    String REWRAP_PRIVATE_CHAT = "rewrapPrivateChat";
    String RESIZE_TAB_BUTTONS = "resizeTabButtons";

    @ConfigSection(name = "Resizable layout", description = "Settings for resizable layout.", position = 0)
    String resizableLayoutSection = "resizableLayout";

    @Range(min = SizeClamps.MIN_HEIGHT_CHANGE, max = SizeClamps.MAX_DIMENSION_CHANGE)
    @Units(Units.PIXELS)
    @ConfigItem(
        position = 1,
        keyName = HEIGHT_CHANGE,
        name = "Height change",
        description = "Add or subtract height to chat box in resizable layout.",
        section = resizableLayoutSection
    )
    default int heightChange() {
        return 28;
    }

    @Range(min = SizeClamps.MIN_WIDTH_CHANGE, max = SizeClamps.MAX_DIMENSION_CHANGE)
    @Units(Units.PIXELS)
    @ConfigItem(
        position = 2,
        keyName = WIDTH_CHANGE,
        name = "Width change",
        description = "Add or subtract width to chat box in resizable layout.",
        section = resizableLayoutSection
    )
    default int widthChange() {
        return 80;
    }

    @ConfigItem(
        position = 3,
        keyName = REWRAP_PRIVATE_CHAT,
        name = "Adjust private split width",
        description = "Adjust width of private messages above the chat box to match adjusted chat.",
        section = resizableLayoutSection
    )
    default boolean rewrapPrivateChat() {
        return true;
    }

    @ConfigItem(
        position = 4,
        keyName = RESIZE_TAB_BUTTONS,
        name = "Resize chat tab buttons",
        description = "Stretch the chat tab buttons relative to the adjusted chat width.",
        section = resizableLayoutSection
    )
    default boolean resizeTabButtons() {
        return false;
    }

    // Fixed layout settings

    String FIXED_HEIGHT_CHANGE = "fixedHeightChange";
    String FIXED_TAB_COLLAPSE = "fixedTabCollapse";
    String FIXED_ADJUST_VIEWPORT = "fixedAdjustViewport";

    @ConfigSection(name = "Fixed layout", description = "Settings for fixed layout.", position = 100)
    String fixedLayoutSection = "fixedLayout";

    @Range(min = SizeClamps.MIN_HEIGHT_CHANGE, max = SizeClamps.MAX_DIMENSION_CHANGE)
    @Units(Units.PIXELS)
    @ConfigItem(
        position = 101,
        keyName = FIXED_HEIGHT_CHANGE,
        name = "Height change",
        description = "Add or subtract height to chat box in fixed layout.",
        section = fixedLayoutSection
    )
    default int fixedHeightChange() {
        return 0;
    }

    @ConfigItem(
        position = 102,
        keyName = FIXED_TAB_COLLAPSE,
        name = "Hideable chat",
        description = "Click the open chat tab to hide chat, like in resizable layout.",
        section = fixedLayoutSection
    )
    default boolean fixedTabCollapse() {
        return true;
    }

    @ConfigItem(
        position = 103,
        keyName = FIXED_ADJUST_VIEWPORT,
        name = "Adjust camera on grow",
        description = "Keep the player centered in the viewport for positive height change values.",
        section = fixedLayoutSection
    )
    default boolean fixedAdjustViewport() {
        return false;
    }

    // Either layout settings

    enum Revert {
        UNGROW, UNSHRINK, BOTH, NEITHER;
        public boolean ungrows() { return this == UNGROW || this == BOTH; }
        public boolean unshrinks() { return this == UNSHRINK || this == BOTH; }
    }

    String REVERT_FOR_DIALOGS = "revertForDialogs";
    String REVERT_FOR_MODALS = "revertForModals";
    String ADJUST_HUD_ANCHORS = "adjustSnapAnchors";
    String TOGGLE_SHOW_CHAT = "toggleShowChat";

    @ConfigSection(name = "Both layouts", description = "Settings common to both layouts.", position = 200)
    String bothLayoutsSection = "bothLayouts";

    @ConfigItem(
        position = 201,
        keyName = REVERT_FOR_DIALOGS,
        name = "Revert for dialogs",
        description = "Temporarily revert adjusted dimensions to stock while an NPC dialog, dialog options, etc, is open."
                    + "<br>&nbsp;&nbsp; - Ungrow: revert grown dimensions (positive height/width change values)"
                    + "<br>&nbsp;&nbsp; - Unshrink: revert shrunk dimensions (negative height/width change values)"
                    + "<br>&nbsp;&nbsp; Recommended selection: BOTH",
        section = bothLayoutsSection
    )
    default Revert revertForDialogs() {
        return Revert.BOTH;
    }

    @ConfigItem(
        position = 202,
        keyName = REVERT_FOR_MODALS,
        name = "Revert for interfaces",
        description = "Temporarily revert adjusted height to stock size while an interface overlay (bank, settings, etc.) is open."
                    + "<br>&nbsp;&nbsp; - Ungrow: revert grown height (positive height change values)"
                    + "<br>&nbsp;&nbsp; - Unshrink: revert shrunk height (negative height change values)"
                    + "<br>&nbsp;&nbsp; Recommended selection: UNGROW"
                    + "<br>Fixed layout always ungrows; its modals are a fixed size that cannot be re-fit above a taller chat.",
        section = bothLayoutsSection
    )
    default Revert revertForModals() {
        return Revert.UNGROW;
    }

    @ConfigItem(
        position = 203,
        keyName = ADJUST_HUD_ANCHORS,
        name = "Adjust snap anchors",
        description = "Move RuneLite's above-chat overlay HUD snap anchors up/down to track the adjusted chat box height.<br>"
                    + "Recommended, but still somewhat experimental; may cause minor issues with center modals (bank, settings, etc).",
        section = bothLayoutsSection
    )
    default boolean adjustHudAnchors() {
        return true;
    }

    @ConfigItem(
        position = 204,
        keyName = TOGGLE_SHOW_CHAT,
        name = "Collapse chat box",
        description = "Press to hide or unhide the chat box.<br>In fixed layout, this requires 'Hideable chat' to be enabled.",
        section = bothLayoutsSection
    )
    default Keybind toggleShowChat() {
        return Keybind.NOT_SET;
    }

    // Drag-resize settings

    String INDICATOR_COLOR = "indicatorColor";
    String DRAG_MODIFIER = "dragModifier";
    String LIVE_REWRAP = "liveRewrap";

    @ConfigSection(name = "Drag-resizing", description = "Hold a key and drag a border to resize chat.", position = 300)
    String dragResizeSection = "Drag-resizing";

    @ConfigItem(
        position = 301,
        keyName = DRAG_MODIFIER,
        name = "Drag-resize",
        description = "Hold to resize chat by dragging its borders with the cursor. Unset to disable.",
        section = dragResizeSection
    )
    default Keybind dragModifier() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        position = 302,
        keyName = LIVE_REWRAP,
        name = "Live re-wrap",
        description = "Re-wrap chat text continuously while drag-resizing width instead of only on release.<br>"
                    + "May hurt FPS during drag-resizing on slower machines, disable to improve performance.",
        section = dragResizeSection
    )
    default boolean liveRewrap() {
        return true;
    }

    @Alpha
    @ConfigItem(
        position = 303,
        keyName = INDICATOR_COLOR,
        name = "Indicator color",
        description = "Color of the draggable border highlight. The opacity is used as a base value.",
        section = dragResizeSection
    )
    default Color indicatorColor() {
        return Color.GREEN;
    }

    // Secondary size settings

    enum Mode { HOLD, TOGGLE }

    String SWAP_HEIGHT_CHANGE = "swapHeightChange";
    String SWAP_WIDTH_CHANGE = "swapWidthChange";
    String SWAP_SIZE_KEYBIND = "swapSizeKeybind";
    String SWAP_SIZE_MODE = "swapSizeMode";

    @ConfigSection(name = "Secondary size", description = "Switch chat to a different size using a keybind.", position = 400, closedByDefault = true)
    String secondarySizeSection = "secondarySize";

    @Range(min = SizeClamps.MIN_HEIGHT_CHANGE, max = SizeClamps.MAX_DIMENSION_CHANGE)
    @Units(Units.PIXELS)
    @ConfigItem(
        position = 401,
        keyName = SWAP_HEIGHT_CHANGE,
        name = "Height change",
        description = "Height change while the secondary size is active. Applies in both layouts.<br>"
                    + "Like the primary height change value, this is applied relative to stock chat height.",
        section = secondarySizeSection
    )
    default int secondaryHeightChange() {
        return 0;
    }

    @Range(min = SizeClamps.MIN_WIDTH_CHANGE, max = SizeClamps.MAX_DIMENSION_CHANGE)
    @Units(Units.PIXELS)
    @ConfigItem(
        position = 402,
        keyName = SWAP_WIDTH_CHANGE,
        name = "Width change",
        description = "Width change while the secondary size is active (ignored in fixed layout).<br>"
                    + "Like the primary width change value, this is applied relative to stock chat width.",
        section = secondarySizeSection
    )
    default int secondaryWidthChange() {
        return 0;
    }

    @ConfigItem(
        position = 403,
        keyName = SWAP_SIZE_MODE,
        name = "Mode",
        description = "Secondary size keybind press behavior."
                    + "<br>&nbsp;&nbsp; - Hold: use the secondary size only while the key is held"
                    + "<br>&nbsp;&nbsp; - Toggle: switch between the primary and secondary sizes on each press",
        section = secondarySizeSection
    )
    default Mode secondaryMode() {
        return Mode.HOLD;
    }

    @ConfigItem(
        position = 404,
        keyName = SWAP_SIZE_KEYBIND,
        name = "Swap",
        description = "Switches the chat to (or from) the secondary size. Unset to disable.",
        section = secondarySizeSection
    )
    default Keybind secondaryKeybind() {
        return Keybind.NOT_SET;
    }
}