package org.bukkit;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

public class BiomeTest extends AbstractTestingBase {

    @Test
    public void testBukkitToMinecraft() {
        for (Biome biome : Biome.values()) {
            if (biome == Biome.CUSTOM) {
                continue;
            }

            assertNotNull(CraftBiome.bukkitToMinecraftHolder(biome), "No NMS mapping for " + biome);
        }
    }

    @Test
    public void testMinecraftToBukkit() {
        for (net.minecraft.world.level.biome.Biome biomeBase : BIOMES) {
            Biome biome = CraftBiome.minecraftToBukkit(biomeBase);
            assertTrue(biome != null && biome != Biome.CUSTOM, "No Bukkit mapping for " + biomeBase);
        }
    }
}
