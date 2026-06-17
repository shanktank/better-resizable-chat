package brc.drag;

import java.awt.event.InputEvent;

public class DragModifier {
    public enum ModifierKey {
        ALT("Alt"),
        CTRL("Ctrl"),
        SHIFT("Shift"),
        DISABLE("Disable");

        private final String label;

        ModifierKey(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        public boolean matches(InputEvent e) {
            switch (this) {
                case ALT: return e.isAltDown();
                case CTRL: return e.isControlDown();
                case SHIFT: return e.isShiftDown();
                default: return false;
            }
        }
    }
}