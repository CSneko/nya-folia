package io.papermc.paper.inventory;

import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ItemRarityTest {

    @Test
    public void testConvertFromNmsToBukkit() {
        for (Rarity nmsRarity : Rarity.values()) {
            assertEquals(ItemRarity.values()[nmsRarity.ordinal()].name(), nmsRarity.name(), "rarity names are mis-matched");
        }
    }

    @Test
    public void testRarityFormatting() {
        for (Rarity nmsRarity : Rarity.values()) {
            assertEquals(nmsRarity.color, PaperAdventure.asVanilla(ItemRarity.values()[nmsRarity.ordinal()].color), "rarity formatting is mis-matched");
        }
    }
}
