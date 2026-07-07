package brc;

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

    // General settings

    String HEIGHT_CHANGE = "heightChange";
    String WIDTH_CHANGE = "widthChange";
    String REWRAP_PRIVATE_CHAT = "rewrapPrivateChat";
    String UNGROW_FOR_DIALOGS = "ungrowForDialogs";
    String ADJUST_HUD_ANCHORS = "adjustHudAnchors";

    @Units(Units.PIXELS)
    @Range(min = -142)
    @ConfigItem(
        position = 0,
        keyName = HEIGHT_CHANGE,
        name = "Height change",
        description = "Add or subtract height to chat box."
    )
    default int heightChange() {
        return 28;
    }

    @Units(Units.PIXELS)
    @Range(min = -519)
    @ConfigItem(
        position = 1,
        keyName = WIDTH_CHANGE,
        name = "Width change",
        description = "Add or subtract width to chat box."
    )
    default int widthChange() {
        return 80;
    }

    @ConfigItem(
        position = 2,
        keyName = REWRAP_PRIVATE_CHAT,
        name = "Adjust private split width",
        description = "Adjust width of private messages above the chat box to match adjusted chat."
    )
    default boolean rewrapPrivateChat() {
        return true;
    }

    @ConfigItem(
        position = 3,
        keyName = UNGROW_FOR_DIALOGS,
        name = "Revert size during dialogs",
        description = "Return the chat box to its default size while an NPC dialog, dialog options, etc., is open."
    )
    default boolean ungrowForDialogs() {
        return true;
    }
    
    @ConfigItem(
        position = 4,
        keyName = ADJUST_HUD_ANCHORS,
        name = "Adjust HUD snap anchors",
        description = "Move RuneLite's above-chat overlay snap anchors up to track the adjusted chat box.<br>"
                    + "EXPERIMENTAL: MAY CAUSE MINOR ISSUES WITH CENTER MODALS (bank, settings, etc)."
    )
    default boolean adjustHudAnchors() {
        return false;
    }

    // Fixed mode settings

    String FIXED_HEIGHT_CHANGE = "fixedHeightChange";

    @ConfigSection(
        name = "Fixed mode",
        description = "Resize the chat box while in fixed (non-resizable) layout.",
        position = 100
    )
    String fixedModeSection = "fixedMode";

    @Units(Units.PIXELS)
    @Range(min = -142)
    @ConfigItem(
        position = 101,
        keyName = FIXED_HEIGHT_CHANGE,
        name = "Height change",
        description = "Grow or shrink the chat box height in fixed layout.",
        section = fixedModeSection
    )
    default int fixedHeightChange() {
        return 0;
    }

    // Drag-resize settings

    String INDICATOR_COLOR = "indicatorColor";
    String DRAG_MODIFIER = "dragModifier";
    String LIVE_REWRAP = "liveRewrap";

    @ConfigSection(
        name = "Drag-resizing",
        description = "Resize the chat by holding a key and dragging its top or right border.",
        position = 200
    )
    String dragResizeSection = "Drag-resizing";

    @ConfigItem(
        position = 201,
        keyName = INDICATOR_COLOR,
        name = "Indicator color",
        description = "Color of the draggable border highlight.",
        section = dragResizeSection
    )
    default Color indicatorColor() {
        return Color.GREEN;
    }

    @ConfigItem(
        position = 202,
        keyName = DRAG_MODIFIER,
        name = "Drag key",
        description = "Hold to drag-resize the chat box. Unset to disable.",
        section = dragResizeSection
    )
    default Keybind dragModifier() {
        return Keybind.CTRL;
    }

    @ConfigItem(
        position = 203,
        keyName = LIVE_REWRAP,
        name = "Live re-wrap",
        description = "Re-wrap chat text continuously while drag-resizing instead of only on release.<br>"
                    + "May hurt FPS during drag-resizing on slower machines, disable to improve performance.",
        section = dragResizeSection
    )
    default boolean liveRewrap() {
        return true;
    }
}