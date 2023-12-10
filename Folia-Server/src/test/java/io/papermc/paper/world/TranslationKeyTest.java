package io.papermc.paper.world;

import com.destroystokyo.paper.ClientOption;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.MusicInstrument;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TranslationKeyTest extends AbstractTestingBase {

    @Test
    public void testChatVisibilityKeys() {
        for (ClientOption.ChatVisibility chatVisibility : ClientOption.ChatVisibility.values()) {
            if (chatVisibility == ClientOption.ChatVisibility.UNKNOWN) continue;
            Assertions.assertEquals(ChatVisiblity.valueOf(chatVisibility.name()).getKey(), chatVisibility.translationKey(), chatVisibility + "'s translation key doesn't match");
        }
    }

    @Test
    public void testDifficultyKeys() {
        for (Difficulty bukkitDifficulty : Difficulty.values()) {
            Assertions.assertEquals(((TranslatableContents) net.minecraft.world.Difficulty.byId(bukkitDifficulty.ordinal()).getDisplayName().getContents()).getKey(), bukkitDifficulty.translationKey(), bukkitDifficulty + "'s translation key doesn't match");
        }
    }

    @Test
    public void testGameruleKeys() {
        for (GameRule<?> rule : GameRule.values()) {
            Assertions.assertEquals(org.bukkit.craftbukkit.CraftWorld.getGameRulesNMS().get(rule.getName()).getDescriptionId(), rule.translationKey(), rule.getName() + "'s translation doesn't match");
        }
    }

    @Test
    public void testAttributeKeys() {
        for (Attribute attribute : Attribute.values()) {
            Assertions.assertEquals(org.bukkit.craftbukkit.attribute.CraftAttribute.bukkitToMinecraft(attribute).getDescriptionId(), attribute.translationKey(), "translation key mismatch for " + attribute);
        }
    }

    @Test
    public void testFireworkEffectType() {
        for (FireworkEffect.Type type : FireworkEffect.Type.values()) {
            Assertions.assertEquals(net.minecraft.world.item.FireworkRocketItem.Shape.byId(org.bukkit.craftbukkit.inventory.CraftMetaFirework.getNBT(type)).getName(), org.bukkit.FireworkEffect.Type.NAMES.key(type), "translation key mismatch for " + type);
        }
    }

    @Test
    @Disabled // TODO fix
    public void testCreativeCategory() {
        // for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
        //     CreativeCategory category = Objects.requireNonNull(CraftCreativeCategory.fromNMS(tab));
        //     Assertions.assertEquals("translation key mismatch for " + category, ((TranslatableContents) tab.getDisplayName().getContents()).getKey(), category.translationKey());
        // }
    }

    @Test
    public void testGameMode() {
        for (GameType nms : GameType.values()) {
            GameMode bukkit = GameMode.getByValue(nms.getId());
            Assertions.assertNotNull(bukkit);
            Assertions.assertEquals(((TranslatableContents) nms.getLongDisplayName().getContents()).getKey(), bukkit.translationKey(), "translation key mismatch for " + bukkit);
        }
    }

    @Test
    public void testBiome() {
        for (Map.Entry<ResourceKey<Biome>, Biome> nms : AbstractTestingBase.BIOMES.entrySet()) {
            org.bukkit.block.Biome bukkit = org.bukkit.block.Biome.valueOf(nms.getKey().location().getPath().toUpperCase());
            Assertions.assertEquals(nms.getKey().location().toLanguageKey("biome"), bukkit.translationKey(), "translation key mismatch for " + bukkit);
        }
    }

    @Test
    public void testMusicInstrument() {
        for (final ResourceLocation nms : BuiltInRegistries.INSTRUMENT.keySet()) {
            final MusicInstrument bukkit = MusicInstrument.getByKey(CraftNamespacedKey.fromMinecraft(nms));
            Assertions.assertNotNull(bukkit, "Missing bukkit instrument for " + nms);
            Assertions.assertEquals(nms.toLanguageKey("instrument"), bukkit.translationKey(), "translation key mismatch for " + bukkit);
        }
    }
}
