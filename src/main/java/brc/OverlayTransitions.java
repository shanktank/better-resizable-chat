package brc;

import javax.inject.Inject;
import javax.inject.Singleton;

// Overlay edge detectors for chat dialog and toplevel modal, all consumers poll through here to ensure simultaneity
@Singleton
public class OverlayTransitions {
    private final ChatDialogBoxes dialogBoxes;
    private final TopLevelModals mainModals;

    @Inject
    OverlayTransitions(ChatDialogBoxes dialogBoxes, TopLevelModals mainModals) {
        this.dialogBoxes = dialogBoxes;
        this.mainModals = mainModals;
    }

    // Re-prime both caches on enable; the singletons survive disable -> enable
    void reset() {
        dialogBoxes.reset();
        mainModals.reset();
    }

    // Cycle-end route (PostClientTick): advance and report both edges in one call, so neither can defer the other
    Edges poll() {
        boolean dialogChanged = dialogBoxes.dialogOpenStateChanged();
        boolean modalChanged = mainModals.topLevelModalOpenStateChanged();
        return new Edges(dialogChanged, modalChanged);
    }

    // Fixed pre-apply route: consume only the modal edge, so the cycle-end poll won't double-fire on it.
    // The dialog cache is deliberately left for the next poll() rather than dropped.
    boolean pollModalEdge() {
        return mainModals.topLevelModalOpenStateChanged();
    }

    static final class Edges {
        final boolean dialogChanged;
        final boolean modalChanged;

        Edges(boolean dialogChanged, boolean modalChanged) {
            this.dialogChanged = dialogChanged;
            this.modalChanged = modalChanged;
        }
    }
}