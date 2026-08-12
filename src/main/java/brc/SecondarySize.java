package brc;

import net.runelite.client.config.Keybind;
import lombok.Getter;
import javax.inject.Inject;
import javax.inject.Singleton;

// Which size set is live: the primary config values, or the secondary set while the swap keybind holds/toggles it on
@Singleton
public class SecondarySize {
    private final ChatResizerConfig config;

    // Written on the client thread only, since the flip must share a pass with the chat rebuild; see setSwapActive
    @Getter private volatile boolean active; // Volatile for the AWT-side readers (drag capture)

    @Inject
    SecondarySize(ChatResizerConfig config) {
        this.config = config;
    }

    // Returns whether the state changed, so the caller can refresh the chat exactly once per flip
    boolean setActive(boolean value) {
        if (value && config.secondaryKeybind().equals(Keybind.NOT_SET)) return false; // Unlikely, but just in case
        if (active == value) return false;
        active = value;
        return true;
    }

    void reset() {
        active = false;
    }

    // Size changes for the live set; the secondary height is shared by both layouts
    int effectiveWidthChange() { return active ? config.secondaryWidthChange() : config.widthChange(); }
    int effectiveHeightChange() { return active ? config.secondaryHeightChange() : config.heightChange(); }
    int effectiveFixedHeightChange() { return active ? config.secondaryHeightChange() : config.fixedHeightChange(); }
}