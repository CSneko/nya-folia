package org.bukkit.craftbukkit.block;

import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import org.bukkit.World;
import org.bukkit.block.Jigsaw;

public class CraftJigsaw extends CraftBlockEntityState<JigsawBlockEntity> implements Jigsaw {

    public CraftJigsaw(World world, JigsawBlockEntity tileEntity) {
        super(world, tileEntity);
    }

    protected CraftJigsaw(CraftJigsaw state) {
        super(state);
    }

    @Override
    public CraftJigsaw copy() {
        return new CraftJigsaw(this);
    }
}
