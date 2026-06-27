package brc;

import brc.drag.DragModifier;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import java.awt.Color;

@ConfigGroup(BetterResizableChatConfig.GROUP)
public interface BetterResizableChatConfig extends Config {
    String GROUP = "betterresizablechat";

    // Sections

    @ConfigSection(
        name = "Drag-resizing",
        description = "Resize the chat by holding a key and dragging its top or right border.",
        position = 100
    )
    String dragResizeSection = "Drag-resizing";

    // General settings

    String HEIGHT_CHANGE_KEY = "heightChange";
    String WIDTH_CHANGE_KEY = "widthChange";

    @Units(Units.PIXELS)
    @Range(min = -142)
    @ConfigItem(
        position = 0,
        keyName = HEIGHT_CHANGE_KEY,
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
        keyName = WIDTH_CHANGE_KEY,
        name = "Width change",
        description = "Add or subtract width to chat box."
    )
    default int widthChange() {
        return 80;
    }

    @ConfigItem(
        position = 2,
        keyName = "rewrapPrivateChat",
        name = "Adjust private split width",
        description = "Adjust width of private messages above the chat box to match adjusted chat."
    )
    default boolean rewrapPrivateChat() {
        return true;
    }

    
    @ConfigItem(
        position = 4,
        keyName = "adjustHudAnchors",
        name = "Adjust HUD snap anchors",
        description = "Move RuneLite's above-chat overlay snap anchors up to track the adjusted chat box.<br>"
                    + "EXPERIMENTAL: May cause minor issues with center modals (bank, settings, etc)."
    )
    default boolean adjustHudAnchors() {
        return false;
    }
    
    @ConfigItem(
        position = 3,
        keyName = "ungrowForDialogs",
        name = "Revert size during dialogs",
        description = "Return the chat box to its default size while an NPC dialog, dialog options, etc., is open."
    )
    default boolean ungrowForDialogs() {
        return true;
    }

    // Drag-resize settings

    @ConfigItem(
        position = 101,
        keyName = "indicatorColor",
        name = "Indicator color",
        description = "Color of the draggable border highlight.",
        section = dragResizeSection
    )
    default Color indicatorColor() {
        return Color.GREEN;
    }

    @ConfigItem(
        position = 102,
        keyName = "dragModifier",
        name = "Drag modifier key",
        description = "Hold key to drag-resize chat box.",
        section = dragResizeSection
    )
    default DragModifier.ModifierKey dragModifier() {
        return DragModifier.ModifierKey.CTRL;
    }

    @ConfigItem(
        position = 103,
        keyName = "liveRewrap",
        name = "Live re-wrap",
        description = "Re-wrap chat text continuously while drag-resizing instead of only on release.<br>"
                    + "May hurt FPS during drag-resizing on slower machines, disable to improve performance.",
        section = dragResizeSection
    )
    default boolean liveRewrap() {
        return true;
    }
}