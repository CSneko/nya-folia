package net.minecraft.world.level.material;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
// CraftBukkit end

public abstract class FlowingFluid extends Fluid {

    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_FLOWING;
    private static final int CACHE_SIZE = 200;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(200) {
            protected void rehash(int i) {}
        };

        object2bytelinkedopenhashmap.defaultReturnValue((byte) 127);
        return object2bytelinkedopenhashmap;
    });
    private final Map<FluidState, VoxelShape> shapes = Maps.newIdentityHashMap();

    public FlowingFluid() {}

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        builder.add(FlowingFluid.FALLING);
    }

    @Override
    public Vec3 getFlow(BlockGetter world, BlockPos pos, FluidState state) {
        double d0 = 0.0D;
        double d1 = 0.0D;
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();

            blockposition_mutableblockposition.setWithOffset(pos, enumdirection);
            FluidState fluid1 = world.getFluidState(blockposition_mutableblockposition);

            if (this.affectsFlow(fluid1)) {
                float f = fluid1.getOwnHeight();
                float f1 = 0.0F;

                if (f == 0.0F) {
                    if (!world.getBlockState(blockposition_mutableblockposition).blocksMotion()) {
                        BlockPos blockposition1 = blockposition_mutableblockposition.below();
                        FluidState fluid2 = world.getFluidState(blockposition1);

                        if (this.affectsFlow(fluid2)) {
                            f = fluid2.getOwnHeight();
                            if (f > 0.0F) {
                                f1 = state.getOwnHeight() - (f - 0.8888889F);
                            }
                        }
                    }
                } else if (f > 0.0F) {
                    f1 = state.getOwnHeight() - f;
                }

                if (f1 != 0.0F) {
                    d0 += (double) ((float) enumdirection.getStepX() * f1);
                    d1 += (double) ((float) enumdirection.getStepZ() * f1);
                }
            }
        }

        Vec3 vec3d = new Vec3(d0, 0.0D, d1);

        if ((Boolean) state.getValue(FlowingFluid.FALLING)) {
            Iterator iterator1 = Direction.Plane.HORIZONTAL.iterator();

            while (iterator1.hasNext()) {
                Direction enumdirection1 = (Direction) iterator1.next();

                blockposition_mutableblockposition.setWithOffset(pos, enumdirection1);
                if (this.isSolidFace(world, blockposition_mutableblockposition, enumdirection1) || this.isSolidFace(world, blockposition_mutableblockposition.above(), enumdirection1)) {
                    vec3d = vec3d.normalize().add(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }

        return vec3d.normalize();
    }

    private boolean affectsFlow(FluidState state) {
        return state.isEmpty() || state.getType().isSame(this);
    }

    protected boolean isSolidFace(BlockGetter world, BlockPos pos, Direction direction) {
        BlockState iblockdata = world.getBlockState(pos);
        FluidState fluid = world.getFluidState(pos);

        return fluid.getType().isSame(this) ? false : (direction == Direction.UP ? true : (iblockdata.getBlock() instanceof IceBlock ? false : iblockdata.isFaceSturdy(world, pos, direction)));
    }

    protected void spread(Level world, BlockPos fluidPos, FluidState state) {
        if (!state.isEmpty()) {
            BlockState iblockdata = world.getBlockState(fluidPos);
            BlockPos blockposition1 = fluidPos.below();
            BlockState iblockdata1 = world.getBlockState(blockposition1);
            FluidState fluid1 = this.getNewLiquid(world, blockposition1, iblockdata1);

            if (this.canSpreadTo(world, fluidPos, iblockdata, Direction.DOWN, blockposition1, iblockdata1, world.getFluidState(blockposition1), fluid1.getType())) {
                // CraftBukkit start
                org.bukkit.block.Block source = CraftBlock.at(world, fluidPos);
                BlockFromToEvent event = new BlockFromToEvent(source, BlockFace.DOWN);
                world.getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.spreadTo(world, blockposition1, iblockdata1, Direction.DOWN, fluid1);
                if (this.sourceNeighborCount(world, fluidPos) >= 3) {
                    this.spreadToSides(world, fluidPos, state, iblockdata);
                }
            } else if (state.isSource() || !this.isWaterHole(world, fluid1.getType(), fluidPos, iblockdata, blockposition1, iblockdata1)) {
                this.spreadToSides(world, fluidPos, state, iblockdata);
            }

        }
    }

    private void spreadToSides(Level world, BlockPos pos, FluidState fluidState, BlockState blockState) {
        int i = fluidState.getAmount() - this.getDropOff(world);

        if ((Boolean) fluidState.getValue(FlowingFluid.FALLING)) {
            i = 7;
        }

        if (i > 0) {
            Map<Direction, FluidState> map = this.getSpread(world, pos, blockState);
            Iterator iterator = map.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Direction, FluidState> entry = (Entry) iterator.next();
                Direction enumdirection = (Direction) entry.getKey();
                FluidState fluid1 = (FluidState) entry.getValue();
                BlockPos blockposition1 = pos.relative(enumdirection);
                BlockState iblockdata1 = world.getBlockStateIfLoaded(blockposition1); // Paper
                if (iblockdata1 == null) continue; // Paper

                if (this.canSpreadTo(world, pos, blockState, enumdirection, blockposition1, iblockdata1, world.getFluidState(blockposition1), fluid1.getType())) {
                    // CraftBukkit start
                    org.bukkit.block.Block source = CraftBlock.at(world, pos);
                    BlockFromToEvent event = new BlockFromToEvent(source, org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(enumdirection));
                    world.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        continue;
                    }
                    // CraftBukkit end
                    this.spreadTo(world, blockposition1, iblockdata1, enumdirection, fluid1);
                }
            }

        }
    }

    protected FluidState getNewLiquid(Level world, BlockPos pos, BlockState state) {
        int i = 0;
        int j = 0;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection);

            BlockState iblockdata1 = world.getBlockStateIfLoaded(blockposition1); // Paper
            if (iblockdata1 == null) continue; // Paper
            FluidState fluid = iblockdata1.getFluidState();

            if (fluid.getType().isSame(this) && this.canPassThroughWall(enumdirection, world, pos, state, blockposition1, iblockdata1)) {
                if (fluid.isSource()) {
                    ++j;
                }

                i = Math.max(i, fluid.getAmount());
            }
        }

        if (this.canConvertToSource(world) && j >= 2) {
            BlockState iblockdata2 = world.getBlockState(pos.below());
            FluidState fluid1 = iblockdata2.getFluidState();

            if (iblockdata2.isSolid() || this.isSourceBlockOfThisType(fluid1)) {
                return this.getSource(false);
            }
        }

        BlockPos blockposition2 = pos.above();
        BlockState iblockdata3 = world.getBlockState(blockposition2);
        FluidState fluid2 = iblockdata3.getFluidState();

        if (!fluid2.isEmpty() && fluid2.getType().isSame(this) && this.canPassThroughWall(Direction.UP, world, pos, state, blockposition2, iblockdata3)) {
            return this.getFlowing(8, true);
        } else {
            int k = i - this.getDropOff(world);

            return k <= 0 ? Fluids.EMPTY.defaultFluidState() : this.getFlowing(k, false);
        }
    }

    private boolean canPassThroughWall(Direction face, BlockGetter world, BlockPos pos, BlockState state, BlockPos fromPos, BlockState fromState) {
        // Paper start - optimise collisions
        if (state.emptyCollisionShape() & fromState.emptyCollisionShape()) {
            // don't even try to cache simple cases
            return true;
        }

        if (state.occludesFullBlock() | fromState.occludesFullBlock()) {
            // don't even try to cache simple cases
            return false;
        }
        // Paper end - optimise collisions
        Object2ByteLinkedOpenHashMap object2bytelinkedopenhashmap;

        if (!state.getBlock().hasDynamicShape() && !fromState.getBlock().hasDynamicShape()) {
            object2bytelinkedopenhashmap = (Object2ByteLinkedOpenHashMap) FlowingFluid.OCCLUSION_CACHE.get();
        } else {
            object2bytelinkedopenhashmap = null;
        }

        Block.BlockStatePairKey block_a;

        if (object2bytelinkedopenhashmap != null) {
            block_a = new Block.BlockStatePairKey(state, fromState, face);
            byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(block_a);

            if (b0 != 127) {
                return b0 != 0;
            }
        } else {
            block_a = null;
        }

        VoxelShape voxelshape = state.getCollisionShape(world, pos);
        VoxelShape voxelshape1 = fromState.getCollisionShape(world, fromPos);
        boolean flag = !Shapes.mergedFaceOccludes(voxelshape, voxelshape1, face);

        if (object2bytelinkedopenhashmap != null) {
            if (object2bytelinkedopenhashmap.size() == 200) {
                object2bytelinkedopenhashmap.removeLastByte();
            }

            object2bytelinkedopenhashmap.putAndMoveToFirst(block_a, (byte) (flag ? 1 : 0));
        }

        return flag;
    }

    public abstract Fluid getFlowing();

    public FluidState getFlowing(int level, boolean falling) {
        return (FluidState) ((FluidState) this.getFlowing().defaultFluidState().setValue(FlowingFluid.LEVEL, level)).setValue(FlowingFluid.FALLING, falling);
    }

    public abstract Fluid getSource();

    public FluidState getSource(boolean falling) {
        return (FluidState) this.getSource().defaultFluidState().setValue(FlowingFluid.FALLING, falling);
    }

    protected abstract boolean canConvertToSource(Level world);

    protected void spreadTo(LevelAccessor world, BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        if (state.getBlock() instanceof LiquidBlockContainer) {
            ((LiquidBlockContainer) state.getBlock()).placeLiquid(world, pos, state, fluidState);
        } else {
            if (!state.isAir()) {
                this.beforeDestroyingBlock(world, pos, state, pos.relative(direction.getOpposite())); // Paper
            }

            world.setBlock(pos, fluidState.createLegacyBlock(), 3);
        }

    }

    protected void beforeDestroyingBlock(LevelAccessor world, BlockPos pos, BlockState state, BlockPos source) { beforeDestroyingBlock(world, pos, state); } // Paper - add source parameter
    protected abstract void beforeDestroyingBlock(LevelAccessor world, BlockPos pos, BlockState state);

    private static short getCacheKey(BlockPos from, BlockPos to) {
        int i = to.getX() - from.getX();
        int j = to.getZ() - from.getZ();

        return (short) ((i + 128 & 255) << 8 | j + 128 & 255);
    }

    protected int getSlopeDistance(LevelReader world, BlockPos pos, int i, Direction direction, BlockState state, BlockPos fromPos, Short2ObjectMap<Pair<BlockState, FluidState>> stateCache, Short2BooleanMap flowDownCache) {
        int j = 1000;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection1 = (Direction) iterator.next();

            if (enumdirection1 != direction) {
                BlockPos blockposition2 = pos.relative(enumdirection1);
                short short0 = FlowingFluid.getCacheKey(fromPos, blockposition2);
                // Paper start - avoid loading chunks
                Pair<BlockState, FluidState> pair = stateCache.get(short0);
                if (pair == null) {
                    BlockState iblockdatax = world.getBlockStateIfLoaded(blockposition2);
                    if (iblockdatax == null) {
                        continue;
                    }

                    pair = Pair.of(iblockdatax, iblockdatax.getFluidState());
                    stateCache.put(short0, pair);
                }
                // Paper end
                BlockState iblockdata1 = (BlockState) pair.getFirst();
                FluidState fluid = (FluidState) pair.getSecond();

                if (this.canPassThrough(world, this.getFlowing(), pos, state, enumdirection1, blockposition2, iblockdata1, fluid)) {
                    boolean flag = flowDownCache.computeIfAbsent(short0, (short1) -> {
                        BlockPos blockposition3 = blockposition2.below();
                        BlockState iblockdata2 = world.getBlockState(blockposition3);

                        return this.isWaterHole(world, this.getFlowing(), blockposition2, iblockdata1, blockposition3, iblockdata2);
                    });

                    if (flag) {
                        return i;
                    }

                    if (i < this.getSlopeFindDistance(world)) {
                        int k = this.getSlopeDistance(world, blockposition2, i + 1, enumdirection1.getOpposite(), iblockdata1, fromPos, stateCache, flowDownCache);

                        if (k < j) {
                            j = k;
                        }
                    }
                }
            }
        }

        return j;
    }

    private boolean isWaterHole(BlockGetter world, Fluid fluid, BlockPos pos, BlockState state, BlockPos fromPos, BlockState fromState) {
        return !this.canPassThroughWall(Direction.DOWN, world, pos, state, fromPos, fromState) ? false : (fromState.getFluidState().getType().isSame(this) ? true : this.canHoldFluid(world, fromPos, fromState, fluid));
    }

    private boolean canPassThrough(BlockGetter world, Fluid fluid, BlockPos pos, BlockState state, Direction face, BlockPos fromPos, BlockState fromState, FluidState fluidState) {
        return !this.isSourceBlockOfThisType(fluidState) && this.canPassThroughWall(face, world, pos, state, fromPos, fromState) && this.canHoldFluid(world, fromPos, fromState, fluid);
    }

    private boolean isSourceBlockOfThisType(FluidState state) {
        return state.getType().isSame(this) && state.isSource();
    }

    protected abstract int getSlopeFindDistance(LevelReader world);

    private int sourceNeighborCount(LevelReader world, BlockPos pos) {
        int i = 0;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection);
            FluidState fluid = world.getFluidState(blockposition1);

            if (this.isSourceBlockOfThisType(fluid)) {
                ++i;
            }
        }

        return i;
    }

    protected Map<Direction, FluidState> getSpread(Level world, BlockPos pos, BlockState state) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        Short2ObjectMap<Pair<BlockState, FluidState>> short2objectmap = new Short2ObjectOpenHashMap();
        Short2BooleanOpenHashMap short2booleanopenhashmap = new Short2BooleanOpenHashMap();
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection);
            short short0 = FlowingFluid.getCacheKey(pos, blockposition1);
            // Paper start
            Pair pair = (Pair) short2objectmap.get(short0);
            if (pair == null) {
                BlockState iblockdatax = world.getBlockStateIfLoaded(blockposition1);
                if (iblockdatax == null) continue;

                pair = Pair.of(iblockdatax, iblockdatax.getFluidState());
                short2objectmap.put(short0, pair);
            }
            // Paper end
            BlockState iblockdata1 = (BlockState) pair.getFirst();
            FluidState fluid = (FluidState) pair.getSecond();
            FluidState fluid1 = this.getNewLiquid(world, blockposition1, iblockdata1);

            if (this.canPassThrough(world, fluid1.getType(), pos, state, enumdirection, blockposition1, iblockdata1, fluid)) {
                BlockPos blockposition2 = blockposition1.below();
                boolean flag = short2booleanopenhashmap.computeIfAbsent(short0, (short1) -> {
                    BlockState iblockdata2 = world.getBlockState(blockposition2);

                    return this.isWaterHole(world, this.getFlowing(), blockposition1, iblockdata1, blockposition2, iblockdata2);
                });
                int j;

                if (flag) {
                    j = 0;
                } else {
                    j = this.getSlopeDistance(world, blockposition1, 1, enumdirection.getOpposite(), iblockdata1, pos, short2objectmap, short2booleanopenhashmap);
                }

                if (j < i) {
                    map.clear();
                }

                if (j <= i) {
                    map.put(enumdirection, fluid1);
                    i = j;
                }
            }
        }

        return map;
    }

    private boolean canHoldFluid(BlockGetter world, BlockPos pos, BlockState state, Fluid fluid) {
        Block block = state.getBlock();

        if (block instanceof LiquidBlockContainer) {
            LiquidBlockContainer ifluidcontainer = (LiquidBlockContainer) block;

            return ifluidcontainer.canPlaceLiquid((Player) null, world, pos, state, fluid);
        } else {
            return !(block instanceof DoorBlock) && !state.is(BlockTags.SIGNS) && !state.is(Blocks.LADDER) && !state.is(Blocks.SUGAR_CANE) && !state.is(Blocks.BUBBLE_COLUMN) ? (!state.is(Blocks.NETHER_PORTAL) && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_GATEWAY) && !state.is(Blocks.STRUCTURE_VOID) ? !state.blocksMotion() : false) : false;
        }
    }

    protected boolean canSpreadTo(BlockGetter world, BlockPos fluidPos, BlockState fluidBlockState, Direction flowDirection, BlockPos flowTo, BlockState flowToBlockState, FluidState fluidState, Fluid fluid) {
        return fluidState.canBeReplacedWith(world, flowTo, fluid, flowDirection) && this.canPassThroughWall(flowDirection, world, fluidPos, fluidBlockState, flowTo, flowToBlockState) && this.canHoldFluid(world, flowTo, flowToBlockState, fluid);
    }

    protected abstract int getDropOff(LevelReader world);

    protected int getSpreadDelay(Level world, BlockPos pos, FluidState oldState, FluidState newState) {
        return this.getTickDelay(world);
    }

    @Override
    public void tick(Level world, BlockPos pos, FluidState state) {
        if (!state.isSource()) {
            FluidState fluid1 = this.getNewLiquid(world, pos, world.getBlockState(pos));
            int i = this.getSpreadDelay(world, pos, state, fluid1);

            if (fluid1.isEmpty()) {
                state = fluid1;
                // CraftBukkit start
                FluidLevelChangeEvent event = CraftEventFactory.callFluidLevelChangeEvent(world, pos, Blocks.AIR.defaultBlockState());
                if (event.isCancelled()) {
                    return;
                }
                world.setBlock(pos, ((CraftBlockData) event.getNewData()).getState(), 3);
                // CraftBukkit end
            } else if (!fluid1.equals(state)) {
                state = fluid1;
                BlockState iblockdata = fluid1.createLegacyBlock();
                // CraftBukkit start
                FluidLevelChangeEvent event = CraftEventFactory.callFluidLevelChangeEvent(world, pos, iblockdata);
                if (event.isCancelled()) {
                    return;
                }
                world.setBlock(pos, ((CraftBlockData) event.getNewData()).getState(), 2);
                // CraftBukkit end
                world.scheduleTick(pos, fluid1.getType(), i);
                world.updateNeighborsAt(pos, iblockdata.getBlock());
            }
        }

        this.spread(world, pos, state);
    }

    protected static int getLegacyLevel(FluidState state) {
        return state.isSource() ? 0 : 8 - Math.min(state.getAmount(), 8) + ((Boolean) state.getValue(FlowingFluid.FALLING) ? 8 : 0);
    }

    private static boolean hasSameAbove(FluidState state, BlockGetter world, BlockPos pos) {
        return state.getType().isSame(world.getFluidState(pos.above()).getType());
    }

    @Override
    public float getHeight(FluidState state, BlockGetter world, BlockPos pos) {
        return FlowingFluid.hasSameAbove(state, world, pos) ? 1.0F : state.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return (float) state.getAmount() / 9.0F;
    }

    @Override
    public abstract int getAmount(FluidState state);

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter world, BlockPos pos) {
        return state.getAmount() == 9 && FlowingFluid.hasSameAbove(state, world, pos) ? Shapes.block() : (VoxelShape) this.shapes.computeIfAbsent(state, (fluid1) -> {
            return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, (double) fluid1.getHeight(world, pos), 1.0D);
        });
    }
}
