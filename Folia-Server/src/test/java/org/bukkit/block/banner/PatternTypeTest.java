package org.bukkit.block.banner;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

public class PatternTypeTest extends AbstractTestingBase {

    @Test
    public void testToBukkit() {
        for (BannerPattern nms : BuiltInRegistries.BANNER_PATTERN) {
            PatternType bukkit = PatternType.getByIdentifier(nms.getHashname());

            assertNotNull(bukkit, "No Bukkit banner for " + nms + " " + nms.getHashname());
        }
    }

    @Test
    public void testToNMS() {
        for (PatternType bukkit : PatternType.values()) {
            BannerPattern found = null;
            for (BannerPattern nms : BuiltInRegistries.BANNER_PATTERN) {
                if (bukkit.getIdentifier().equals(nms.getHashname())) {
                    found = nms;
                    break;
                }
            }

            assertNotNull(found, "No NMS banner for " + bukkit + " " + bukkit.getIdentifier());
        }
    }
}
