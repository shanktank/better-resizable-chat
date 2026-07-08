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

    // Resizable layout settings

    String HEIGHT_CHANGE = "heightChange";
    String WIDTH_CHANGE = "widthChange";
    String REWRAP_PRIVATE_CHAT = "rewrapPrivateChat";

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

    // Fixed layout settings

    String FIXED_HEIGHT_CHANGE = "fixedHeightChange";

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

    // Either layout settings

    String UNGROW_FOR_DIALOGS = "ungrowForDialogs";
    String ADJUST_HUD_ANCHORS = "adjustHudAnchors";

    @ConfigSection(name = "Both layouts", description = "Settings common to both layouts.", position = 200)
    String bothLayoutsSection = "bothLayouts";

    @ConfigItem(
        position = 201,
        keyName = UNGROW_FOR_DIALOGS,
        name = "Revert size during dialogs",
        description = "Return the chat box to its default size while an NPC dialog, dialog options, etc., is open.",
        section = bothLayoutsSection
    )
    default boolean ungrowForDialogs() {
        return true;
    }

    @ConfigItem(
        position = 202,
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
        keyName = INDICATOR_COLOR,
        name = "Indicator color",
        description = "Color of the draggable border highlight.",
        section = dragResizeSection
    )
    default Color indicatorColor() {
        return Color.GREEN;
    }

    @ConfigItem(
        position = 302,
        keyName = DRAG_MODIFIER,
        name = "Drag key",
        description = "Hold to drag-resize the chat box. Unset to disable.",
        section = dragResizeSection
    )
    default Keybind dragModifier() {
        return Keybind.CTRL;
    }

    @ConfigItem(
        position = 303,
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