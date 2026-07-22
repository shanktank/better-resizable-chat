package brc;

import net.runelite.api.widgets.Widget;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import javax.inject.Inject;
import javax.inject.Singleton;

// RuneLite's own chatbox prompts (quest search, bank tag name, item search) bake their value and caret into
// ABSOLUTE children centered by hand at build time, so a later width change leaves them off to one side of the
// box. Rather than chase every resize path, watch the prompt container and re-bake whenever its width moves.
@Singleton
public final class RuneLiteChatInput {
    private final ChatboxPanelManager panels;

    // The input the width below was measured for; identity also stops a reused input
    // singleton (ChatboxItemSearch) from carrying a previous session's width into a fresh one
    private ChatboxInput lastInput;
    private int lastWidth;

    @Inject
    RuneLiteChatInput(ChatboxPanelManager panels) {
        this.panels = panels;
    }

    // Client thread. Idle cost is a field read; only a live text prompt gets as far as the widget lookup.
    void refit() {
        ChatboxInput input = panels.getCurrentInput();
        if (!(input instanceof ChatboxTextInput)) { // ChatboxTextMenuInput self-centers, so it needs nothing
            lastInput = null;
            return;
        }

        ChatboxTextInput text = (ChatboxTextInput) input;
        if (!text.isBuilt()) return; // Opened but not drawn yet; its own build will use the current width

        Widget container = panels.getContainerWidget();
        if (container == null) return; // Chatbox gone (hop, relog, layout swap): keep the tracking for when it's back

        int width = container.getWidth();
        boolean moved = input == lastInput && width != lastWidth;
        lastInput = input;
        lastWidth = width;

        // Handing cursorAt() back the caret it already holds makes it a pure rebuild at the current width
        if (moved) text.cursorAt(text.getCursorStart(), text.getCursorEnd());
    }
}