package brc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ChatResizerPluginTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(ChatResizerPlugin.class);
        RuneLite.main(args);
    }
}