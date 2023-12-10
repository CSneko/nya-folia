package net.minecraft.world.level;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface BlockGetter extends LevelHeightAccessor {

    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        BlockEntity tileentity = this.getBlockEntity(pos);

        return tileentity != null && tileentity.getType() == type ? (Optional<T>) Optional.of(tileentity) : Optional.empty(); // CraftBukkit - decompile error
    }

    BlockState getBlockState(BlockPos pos);
    // Paper start - if loaded util
    @Nullable BlockState getBlockStateIfLoaded(BlockPos blockposition);

    default @Nullable Block getBlockIfLoaded(BlockPos blockposition) {
        BlockState type = this.getBlockStateIfLoaded(blockposition);
        return type == null ? null : type.getBlock();
    }
    @Nullable FluidState getFluidIfLoaded(BlockPos blockposition);
    // Paper end

    FluidState getFluidState(BlockPos pos);

    default int getLightEmission(BlockPos pos) {
        return this.getBlockState(pos).getLightEmission();
    }

    default int getMaxLightLevel() {
        return 15;
    }

    default Stream<BlockState> getBlockStates(AABB box) {
        return BlockPos.betweenClosedStream(box).map(this::getBlockState);
    }

    default BlockHitResult isBlockInLine(ClipBlockStateContext context) {
        return (BlockHitResult) BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (clipblockstatecontext1, blockposition) -> {
            BlockState iblockdata = this.getBlockState(blockposition);
            Vec3 vec3d = clipblockstatecontext1.getFrom().subtract(clipblockstatecontext1.getTo());

            return clipblockstatecontext1.isTargetBlock().test(iblockdata) ? new BlockHitResult(clipblockstatecontext1.getTo(), Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos.containing(clipblockstatecontext1.getTo()), false) : null;
        }, (clipblockstatecontext1) -> {
            Vec3 vec3d = clipblockstatecontext1.getFrom().subtract(clipblockstatecontext1.getTo());

            return BlockHitResult.miss(clipblockstatecontext1.getTo(), Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos.containing(clipblockstatecontext1.getTo()));
        });
    }

    // Paper start - Broken down variant of the method below, used by Level#clipDirect
    @Nullable
    default BlockHitResult.Type clipDirect(Vec3 start, Vec3 end, BlockPos pos, BlockState state, net.minecraft.world.phys.shapes.CollisionContext collisionContext) {
        if (state.isAir()) {
            return null;
        }

        final VoxelShape voxelshape = ClipContext.Block.COLLIDER.get(state, this, pos, collisionContext);
        final BlockHitResult hitResult = this.clipWithInteractionOverride(start, end, pos, voxelshape, state);
        return hitResult == null ? null : hitResult.getType();
    }
    // Paper end

    // CraftBukkit start - moved block handling into separate method for use by Block#rayTrace
    default BlockHitResult clip(ClipContext raytrace1, BlockPos blockposition) {
        // Paper start
        return clip(raytrace1, blockposition, null);
    }

    default BlockHitResult clip(ClipContext raytrace1, BlockPos blockposition, java.util.function.Predicate<org.bukkit.block.Block> canCollide) {
            // Paper end
            // Paper start - Prevent raytrace from loading chunks
            BlockState iblockdata = this.getBlockStateIfLoaded(blockposition);
            if (iblockdata == null) {
                // copied the last function parameter (listed below)
                Vec3 vec3d = raytrace1.getFrom().subtract(raytrace1.getTo());

                return BlockHitResult.miss(raytrace1.getTo(), Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos.containing(raytrace1.getTo()));
            }
            // Paper end
            if (iblockdata.isAir() || (canCollide != null && this instanceof LevelAccessor levelAccessor && !canCollide.test(org.bukkit.craftbukkit.block.CraftBlock.at(levelAccessor, blockposition)))) return null; // Paper - optimise air cases and check canCollide predicate
            FluidState fluid = iblockdata.getFluidState(); // Paper - don't need to go to world state again
            Vec3 vec3d = raytrace1.getFrom();
            Vec3 vec3d1 = raytrace1.getTo();
            VoxelShape voxelshape = raytrace1.getBlockShape(iblockdata, this, blockposition);
            BlockHitResult movingobjectpositionblock = this.clipWithInteractionOverride(vec3d, vec3d1, blockposition, voxelshape, iblockdata);
            VoxelShape voxelshape1 = raytrace1.getFluidShape(fluid, this, blockposition);
            BlockHitResult movingobjectpositionblock1 = voxelshape1.clip(vec3d, vec3d1, blockposition);
            double d0 = movingobjectpositionblock == null ? Double.MAX_VALUE : raytrace1.getFrom().distanceToSqr(movingobjectpositionblock.getLocation());
            double d1 = movingobjectpositionblock1 == null ? Double.MAX_VALUE : raytrace1.getFrom().distanceToSqr(movingobjectpositionblock1.getLocation());

            return d0 <= d1 ? movingobjectpositionblock : movingobjectpositionblock1;
    }
    // CraftBukkit end

    default BlockHitResult clip(ClipContext context) {
        // Paper start
        return clip(context, (java.util.function.Predicate<org.bukkit.block.Block>) null);
    }

    default BlockHitResult clip(ClipContext context, java.util.function.Predicate<org.bukkit.block.Block> canCollide) {
        // Paper end
        return (BlockHitResult) BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (raytrace1, blockposition) -> {
            return this.clip(raytrace1, blockposition, canCollide); // CraftBukkit - moved into separate method // Paper - use method with canCollide predicate
        }, (raytrace1) -> {
            Vec3 vec3d = raytrace1.getFrom().subtract(raytrace1.getTo());

            return BlockHitResult.miss(raytrace1.getTo(), Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos.containing(raytrace1.getTo()));
        });
    }

    @Nullable
    default BlockHitResult clipWithInteractionOverride(Vec3 start, Vec3 end, BlockPos pos, VoxelShape shape, BlockState state) {
        BlockHitResult movingobjectpositionblock = shape.clip(start, end, pos);

        if (movingobjectpositionblock != null) {
            BlockHitResult movingobjectpositionblock1 = state.getInteractionShape(this, pos).clip(start, end, pos);

            if (movingobjectpositionblock1 != null && movingobjectpositionblock1.getLocation().subtract(start).lengthSqr() < movingobjectpositionblock.getLocation().subtract(start).lengthSqr()) {
                return movingobjectpositionblock.withDirection(movingobjectpositionblock1.getDirection());
            }
        }

        return movingobjectpositionblock;
    }

    default double getBlockFloorHeight(VoxelShape blockCollisionShape, Supplier<VoxelShape> belowBlockCollisionShapeGetter) {
        if (!blockCollisionShape.isEmpty()) {
            return blockCollisionShape.max(Direction.Axis.Y);
        } else {
            double d0 = ((VoxelShape) belowBlockCollisionShapeGetter.get()).max(Direction.Axis.Y);

            return d0 >= 1.0D ? d0 - 1.0D : Double.NEGATIVE_INFINITY;
        }
    }

    default double getBlockFloorHeight(BlockPos pos) {
        return this.getBlockFloorHeight(this.getBlockState(pos).getCollisionShape(this, pos), () -> {
            BlockPos blockposition1 = pos.below();

            return this.getBlockState(blockposition1).getCollisionShape(this, blockposition1);
        });
    }

    static <T, C> T traverseBlocks(Vec3 start, Vec3 end, C context, BiFunction<C, BlockPos, T> blockHitFactory, Function<C, T> missFactory) {
        if (start.equals(end)) {
            return missFactory.apply(context);
        } else {
            double d0 = Mth.lerp(-1.0E-7D, end.x, start.x);
            double d1 = Mth.lerp(-1.0E-7D, end.y, start.y);
            double d2 = Mth.lerp(-1.0E-7D, end.z, start.z);
            double d3 = Mth.lerp(-1.0E-7D, start.x, end.x);
            double d4 = Mth.lerp(-1.0E-7D, start.y, end.y);
            double d5 = Mth.lerp(-1.0E-7D, start.z, end.z);
            int i = Mth.floor(d3);
            int j = Mth.floor(d4);
            int k = Mth.floor(d5);
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos(i, j, k);
            T t0 = blockHitFactory.apply(context, blockposition_mutableblockposition);

            if (t0 != null) {
                return t0;
            } else {
                double d6 = d0 - d3;
                double d7 = d1 - d4;
                double d8 = d2 - d5;
                int l = Mth.sign(d6);
                int i1 = Mth.sign(d7);
                int j1 = Mth.sign(d8);
                double d9 = l == 0 ? Double.MAX_VALUE : (double) l / d6;
                double d10 = i1 == 0 ? Double.MAX_VALUE : (double) i1 / d7;
                double d11 = j1 == 0 ? Double.MAX_VALUE : (double) j1 / d8;
                double d12 = d9 * (l > 0 ? 1.0D - Mth.frac(d3) : Mth.frac(d3));
                double d13 = d10 * (i1 > 0 ? 1.0D - Mth.frac(d4) : Mth.frac(d4));
                double d14 = d11 * (j1 > 0 ? 1.0D - Mth.frac(d5) : Mth.frac(d5));

                T object; // CraftBukkit - decompile error

                do {
                    if (d12 > 1.0D && d13 > 1.0D && d14 > 1.0D) {
                        return missFactory.apply(context);
                    }

                    if (d12 < d13) {
                        if (d12 < d14) {
                            i += l;
                            d12 += d9;
                        } else {
                            k += j1;
                            d14 += d11;
                        }
                    } else if (d13 < d14) {
                        j += i1;
                        d13 += d10;
                    } else {
                        k += j1;
                        d14 += d11;
                    }

                    object = blockHitFactory.apply(context, blockposition_mutableblockposition.set(i, j, k));
                } while (object == null);

                return object;
            }
        }
    }
}
