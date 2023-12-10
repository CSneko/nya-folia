package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public interface LevelWriter {

    boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth);

    default boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return this.setBlock(pos, state, flags, 512);
    }

    boolean removeBlock(BlockPos pos, boolean move);

    default boolean destroyBlock(BlockPos pos, boolean drop) {
        return this.destroyBlock(pos, drop, (Entity) null);
    }

    default boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity) {
        return this.destroyBlock(pos, drop, breakingEntity, 512);
    }

    boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth);

    default boolean addFreshEntity(Entity entity) {
        return false;
    }

    // CraftBukkit start
    default boolean addFreshEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        return false;
    }
    // CraftBukkit end
}
