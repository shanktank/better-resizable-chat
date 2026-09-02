package brc.internal;

// Stock chatbox geometry shared by both layouts' apply/restore paths
public final class ChatGeometry {
    private ChatGeometry() {}

    public static final int CHATBOX_SPRITE_W = 519; // Stock chat width (locked in fixed layout)
    public static final int CHATBOX_SPRITE_H = 142; // Stock background sprite height
    public static final int CHATBOX_SLOT_H = 165; // Chat box plus tabs bar

    public static final int FIXED_CHAT_Y = 338; // Stock top of the chat slot in fixed layout

    public static final int NORMAL_LINE_PITCH = 14; // Height the chat builder gives each wrapped line of a message
    public static final int SMALL_LINE_PITCH = 12; // Default line pitch for the small font (Plain 11)
    public static final int MIN_LINE_PITCH = 10; // Floor for the small font's adjustable line pitch
}