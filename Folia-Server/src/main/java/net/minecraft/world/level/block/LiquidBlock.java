package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LiquidBlock extends Block implements BucketPickup {

    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
    protected final FlowingFluid fluid;
    private final List<FluidState> stateCache;
    public static final VoxelShape STABLE_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    public static final ImmutableList<Direction> POSSIBLE_FLOW_DIRECTIONS = ImmutableList.of(Direction.DOWN, Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST);

    protected LiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties settings) {
        super(settings);
        this.fluid = fluid;
        this.stateCache = Lists.newArrayList();
        this.stateCache.add(fluid.getSource(false));

        for (int i = 1; i < 8; ++i) {
            this.stateCache.add(fluid.getFlowing(8 - i, false));
        }

        this.stateCache.add(fluid.getFlowing(8, true));
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LiquidBlock.LEVEL, 0));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return context.isAbove(LiquidBlock.STABLE_SHAPE, pos, true) && (Integer) state.getValue(LiquidBlock.LEVEL) == 0 && context.canStandOnFluid(world.getFluidState(pos.above()), state.getFluidState()) ? LiquidBlock.STABLE_SHAPE : Shapes.empty();
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getFluidState().isRandomlyTicking();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        state.getFluidState().randomTick(world, pos, random);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return !this.fluid.is(FluidTags.LAVA);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        int i = (Integer) state.getValue(LiquidBlock.LEVEL);

        return (FluidState) this.stateCache.get(Math.min(i, 8));
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState stateFrom, Direction direction) {
        return stateFrom.getFluidState().getType().isSame(this.fluid);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.emptyList();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (this.shouldSpreadLiquid(world, pos, state)) {
            world.scheduleTick(pos, state.getFluidState().getType(), this.getFlowSpeed(world, pos)); // Paper
        }

    }

    // Paper start - Get flow speed. Throttle if its water and flowing adjacent to lava
    public int getFlowSpeed(Level world, BlockPos blockposition) {
        if (net.minecraft.core.registries.BuiltInRegistries.FLUID.wrapAsHolder(this.fluid).is(FluidTags.WATER)) {
            if (
                isLava(world, blockposition.north(1)) ||
                isLava(world, blockposition.south(1)) ||
                isLava(world, blockposition.west(1)) ||
                isLava(world, blockposition.east(1))
            ) {
                return world.paperConfig().environment.waterOverLavaFlowSpeed;
            }
        }
        return this.fluid.getTickDelay(world);
    }
    private static boolean isLava(Level world, BlockPos blockPos) {
        final FluidState fluidState = world.getFluidIfLoaded(blockPos);
        return fluidState != null && fluidState.is(FluidTags.LAVA);
    }
    // Paper end

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (state.getFluidState().isSource() || neighborState.getFluidState().isSource()) {
            world.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (this.shouldSpreadLiquid(world, pos, state)) {
            world.scheduleTick(pos, state.getFluidState().getType(), this.getFlowSpeed(world, pos)); // Paper
        }

    }

    private boolean shouldSpreadLiquid(Level world, BlockPos pos, BlockState state) {
        if (this.fluid.is(FluidTags.LAVA)) {
            boolean flag = world.getBlockState(pos.below()).is(Blocks.SOUL_SOIL);
            UnmodifiableIterator unmodifiableiterator = LiquidBlock.POSSIBLE_FLOW_DIRECTIONS.iterator();

            while (unmodifiableiterator.hasNext()) {
                Direction enumdirection = (Direction) unmodifiableiterator.next();
                BlockPos blockposition1 = pos.relative(enumdirection.getOpposite());

                if (world.getFluidState(blockposition1).is(FluidTags.WATER)) {
                    Block block = world.getFluidState(pos).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;

                    // CraftBukkit start
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, pos, block.defaultBlockState())) {
                        this.fizz(world, pos);
                    }
                    // CraftBukkit end
                    return false;
                }

                if (flag && world.getBlockState(blockposition1).is(Blocks.BLUE_ICE)) {
                    // CraftBukkit start
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, pos, Blocks.BASALT.defaultBlockState())) {
                        this.fizz(world, pos);
                    }
                    // CraftBukkit end
                    return false;
                }
            }
        }

        return true;
    }

    private void fizz(LevelAccessor world, BlockPos pos) {
        world.levelEvent(1501, pos, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LiquidBlock.LEVEL);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor world, BlockPos pos, BlockState state) {
        if ((Integer) state.getValue(LiquidBlock.LEVEL) == 0) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            return new ItemStack(this.fluid.getBucket());
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return this.fluid.getPickupSound();
    }
}
