package brc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BetterResizableChatPluginTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(BetterResizableChatPlugin.class);
        RuneLite.main(args);
    }
}