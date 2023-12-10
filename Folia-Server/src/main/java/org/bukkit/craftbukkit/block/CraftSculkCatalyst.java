package org.bukkit.craftbukkit.block;

import com.google.common.base.Preconditions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.SculkCatalyst;

public class CraftSculkCatalyst extends CraftBlockEntityState<SculkCatalystBlockEntity> implements SculkCatalyst {

    public CraftSculkCatalyst(World world, SculkCatalystBlockEntity tileEntity) {
        super(world, tileEntity);
    }

    protected CraftSculkCatalyst(CraftSculkCatalyst state) {
        super(state);
    }

    @Override
    public void bloom(Block block, int charge) {
        Preconditions.checkArgument(block != null, "block cannot be null");
        Preconditions.checkArgument(charge > 0, "charge must be positive");
        this.requirePlaced();

        // bloom() is for visual blooming effect, cursors are what changes the blocks.
        this.getTileEntity().getListener().bloom(this.world.getHandle(), this.getPosition(), this.getHandle(), this.world.getHandle().getRandom());
        this.getTileEntity().getListener().getSculkSpreader().addCursors(new BlockPos(block.getX(), block.getY(), block.getZ()), charge);
    }

    @Override
    public CraftSculkCatalyst copy() {
        return new CraftSculkCatalyst(this);
    }

    // Paper start - SculkCatalyst bloom API
    @Override
    public void bloom(@org.jetbrains.annotations.NotNull io.papermc.paper.math.Position position, int charge) {
        com.google.common.base.Preconditions.checkNotNull(position);
        requirePlaced();

        getTileEntity().getListener().bloom(
            world.getHandle(),
            getTileEntity().getBlockPos(),
            getTileEntity().getBlockState(),
            world.getHandle().getRandom()
        );
        getTileEntity().getListener().getSculkSpreader().addCursors(io.papermc.paper.util.MCUtil.toBlockPos(position), charge);
    }
    // Paper end
}
