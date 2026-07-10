package brc;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import java.awt.Color;

@ConfigGroup(BetterResizableChatConfig.GROUP)
public interface BetterResizableChatConfig extends Config {
    String GROUP = "betterresizablechat";

    // Resizable layout settings

    String HEIGHT_CHANGE = "heightChange";
    String WIDTH_CHANGE = "widthChange";
    String REWRAP_PRIVATE_CHAT = "rewrapPrivateChat";
    String RESIZE_TAB_BUTTONS = "resizeTabButtons";

    @ConfigSection(name = "Resizable layout", description = "Settings for resizable layout.", position = 0)
    String resizableLayoutSection = "resizableLayout";

    @Units(Units.PIXELS)
    @Range(min = -142)
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

    @Units(Units.PIXELS)
    @Range(min = -519)
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

    @Units(Units.PIXELS)
    @Range(min = -142)
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

    String UNGROW_FOR_DIALOGS = "ungrowForDialogs";
    String ADJUST_HUD_ANCHORS = "adjustHudAnchors";
    String TOGGLE_SHOW_CHAT = "toggleShowChat";

    @ConfigSection(name = "Both layouts", description = "Settings common to both layouts.", position = 200)
    String bothLayoutsSection = "bothLayouts";

    @ConfigItem(
        position = 201,
        keyName = TOGGLE_SHOW_CHAT,
        name = "Toggle chat",
        description = "Press to hide or unhide the chat box.<br>"
                    + "In fixed layout, this requires 'Hideable chat' to be enabled.",
        section = bothLayoutsSection
    )
    default Keybind toggleShowChat() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        position = 202,
        keyName = UNGROW_FOR_DIALOGS,
        name = "Revert size during dialogs",
        description = "Return the chat box to its default size while an NPC dialog, dialog options, etc., is open.",
        section = bothLayoutsSection
    )
    default boolean ungrowForDialogs() {
        return true;
    }

    @ConfigItem(
        position = 203,
        keyName = ADJUST_HUD_ANCHORS,
        name = "Adjust HUD snap anchors",
        description = "Move RuneLite's above-chat overlay snap anchors up to track the adjusted chat box.<br>"
                    + "EXPERIMENTAL: MAY CAUSE MINOR ISSUES WITH CENTER MODALS (bank, settings, etc).",
        section = bothLayoutsSection
    )
    default boolean adjustHudAnchors() {
        return false;
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
        name = "Keybind",
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
        description = "Re-wrap chat text continuously while drag-resizing instead of only on release.<br>"
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
}