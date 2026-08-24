package brc.internal;

// Raw client script IDs, args, sentinels, etc, that lack an associated RuneLite constant
public final class RawScripts {
    private RawScripts() {}

    // cs2 [proc,on_mobile]: a helper the layout scripts call mid-refit, so its pre-fire is a relayout tripwire
    public static final int TOPLEVEL_RELAYOUT = 1972;

    // Re-wraps chat lines and cleans up the background sprite at the current width, resizable layout only
    public static final int RESIZES_CHAT = 924;

    // Re-wraps chat text at the current width
    public static final int REWRAPS_CHAT = 663;

    // onResize handler of a toplevel CONTROL widget: full in-engine UI re-fit without a real canvas resize
    public static final int TOPLEVEL_ONRESIZE = 904;

    // Component-layout enums (cs2 toplevel_getcomponents) TOPLEVEL_ONRESIZE expects per toplevel
    public static final int LAYOUT_ENUM_FIXED = 1129; // Toplevel (548)
    public static final int LAYOUT_ENUM_OSRS_STRETCH = 1130; // ToplevelOsrsStretch (161)
    public static final int LAYOUT_ENUM_PRE_EOC = 1131; // ToplevelPreEoc (164)

    // Ontimer wrappers of self-refitting windows' size polls
    public static final int BANKMAIN_SIZE_CHECK_TIMER = 839; // cs2 bankmain_size_check
    public static final int SETTINGS_SIZE_CHECK_TIMER = 3831; // cs2 settings_window_resize
    public static final int WINDOW_SIZE_CHECK_TIMER = 2599; // Shared movable-window poll (Collection Log, others)
    public static final int SEED_VAULT_SIZE_CHECK_TIMER = 742; // Ontimer wrapper of cs2 2852

    // cs2 chat_button_onop, the tab stones' click handler; (1, tabIdx) switches tab or collapses chat if already active
    public static final int CHAT_TAB_CLICKED = 175;

    // Repaints the tab stones from varc CHAT_VIEW; run after parking the collapse sentinel so open tab draws unselected
    public static final int REDRAW_CHAT_BUTTONS = 178;

    // Not a script: varc CHAT_VIEW sentinel the engine parks on while resizable chat is collapsed
    public static final int COLLAPSED_TAB = 1337;
}