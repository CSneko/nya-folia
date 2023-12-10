package org.bukkit.map;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.world.level.material.MapColor;
import org.bukkit.craftbukkit.map.CraftMapColorCache;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MapTest {

    private static final Logger logger = Logger.getLogger("MapTest");

    private static final int[] modifiers = {180, 220, 255, 135};

    @Test
    public void testColors() {
        MapColor[] nmsColors = MapColor.MATERIAL_COLORS;
        Color[] bukkitColors = MapPalette.colors;

        boolean fail = false;
        for (int i = 0; i < nmsColors.length; i++) {
            if (nmsColors[i] == null) {
                break;
            }
            int rgb = nmsColors[i].col;

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (i + 1 > bukkitColors.length / 4) {
                for (int modi : MapTest.modifiers) {
                    int mr = (r * modi) / 255;
                    int mg = (g * modi) / 255;
                    int mb = (b * modi) / 255;
                    MapTest.logger.log(Level.WARNING, "Missing color (check CraftMapView#render and update md5 hash in CraftMapColorCache): c({0}, {1}, {2})", new Object[]{mr, mg, mb});
                }
                fail = true;
            } else {
                for (int j = 0; j < MapTest.modifiers.length; j++) {
                    int modi = MapTest.modifiers[j];
                    Color bukkit = bukkitColors[i * 4 + j];
                    int mr = (r * modi) / 255;
                    int mg = (g * modi) / 255;
                    int mb = (b * modi) / 255;

                    if (bukkit.getRed() != mr || bukkit.getGreen() != mg || bukkit.getBlue() != mb) {
                        MapTest.logger.log(Level.WARNING, "Incorrect color: {6} {7} c({0}, {1}, {2}) != c({3}, {4}, {5})", new Object[]{
                            bukkit.getRed(), bukkit.getGreen(), bukkit.getBlue(),
                            mr, mg, mb,
                            i, j
                        });
                        fail = true;
                    }
                }
            }
        }
        assertFalse(fail);
    }

    @Disabled("Test takes around 25 seconds, should be run by changes to the map color conversion")
    @Test
    public void testMapColorCacheBuilding() throws ExecutionException, InterruptedException {
        CraftMapColorCache craftMapColorCache = new CraftMapColorCache(MapTest.logger);
        craftMapColorCache.initCache().get();

        for (int r = 0; r < 256; r++) {
            for (int g = 0; g < 256; g++) {
                for (int b = 0; b < 256; b++) {
                    Color color = new Color(r, g, b);
                    assertEquals(MapPalette.matchColor(color), craftMapColorCache.matchColor(color), String.format("Incorrect matched color c(%s, %s, %s)", color.getRed(), color.getGreen(), color.getBlue()));
                }
            }
        }
    }
}
