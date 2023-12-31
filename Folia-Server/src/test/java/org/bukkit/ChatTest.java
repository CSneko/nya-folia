package org.bukkit;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.junit.jupiter.api.Test;

public class ChatTest {

    @Test
    public void testColors() {
        for (ChatColor color : ChatColor.values()) {
            assertNotNull(CraftChatMessage.getColor(color));
            assertEquals(color, CraftChatMessage.getColor(CraftChatMessage.getColor(color)));
        }

        for (ChatFormatting format : ChatFormatting.values()) {
            assertNotNull(CraftChatMessage.getColor(format));
            assertEquals(format, CraftChatMessage.getColor(CraftChatMessage.getColor(format)));
        }
    }

    @Test
    public void testURLJsonConversion() {
        Component[] components;
        components = CraftChatMessage.fromString("https://spigotmc.org/test Test Message");
        assertEquals("{\"extra\":[{\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://spigotmc.org/test\"},\"text\":\"https://spigotmc.org/test\"},{\"text\":\" Test Message\"}],\"text\":\"\"}",
                CraftChatMessage.toJSON(components[0]));

        components = CraftChatMessage.fromString("123 " + ChatColor.GOLD + "https://spigotmc.org " + ChatColor.BOLD + "test");
        assertEquals("{\"extra\":[{\"text\":\"123 \"},{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"gold\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://spigotmc.org\"},\"text\":\"https://spigotmc.org\"},{\"bold\":false,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"gold\",\"text\":\" \"},{\"bold\":true,\"italic\":false,\"underlined\":false,\"strikethrough\":false,\"obfuscated\":false,\"color\":\"gold\",\"text\":\"test\"}],\"text\":\"\"}",
                CraftChatMessage.toJSON(components[0]));

        components = CraftChatMessage.fromString("multiCase http://SpigotMC.ORg/SpOngeBobMeEMeGoESHeRE");
        assertEquals("{\"extra\":[{\"text\":\"multiCase \"},{\"clickEvent\":{\"action\":\"open_url\",\"value\":\"http://SpigotMC.ORg/SpOngeBobMeEMeGoESHeRE\"},\"text\":\"http://SpigotMC.ORg/SpOngeBobMeEMeGoESHeRE\"}],\"text\":\"\"}",
                CraftChatMessage.toJSON(components[0]));
    }
}
