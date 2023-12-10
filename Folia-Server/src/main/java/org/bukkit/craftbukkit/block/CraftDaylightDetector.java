package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import org.bukkit.World;
import org.bukkit.block.DaylightDetector;

public class CraftDaylightDetector extends CraftBlockEntityState<DaylightDetectorBlockEntity> implements DaylightDetector {

    public CraftDaylightDetector(World world, DaylightDetectorBlockEntity tileEntity) {
        super(world, tileEntity);
    }

    protected CraftDaylightDetector(CraftDaylightDetector state) {
        super(state);
    }

    @Override
    public CraftDaylightDetector copy() {
        return new CraftDaylightDetector(this);
    }
}
