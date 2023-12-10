package net.minecraft.world.level.block;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ScaffoldingBlock extends Block implements SimpleWaterloggedBlock {

    private static final int TICK_DELAY = 1;
    private static final VoxelShape STABLE_SHAPE;
    private static final VoxelShape UNSTABLE_SHAPE;
    private static final VoxelShape UNSTABLE_SHAPE_BOTTOM = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape BELOW_BLOCK = Shapes.block().move(0.0D, -1.0D, 0.0D);
    public static final int STABILITY_MAX_DISTANCE = 7;
    public static final IntegerProperty DISTANCE = BlockStateProperties.STABILITY_DISTANCE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;

    protected ScaffoldingBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ScaffoldingBlock.DISTANCE, 7)).setValue(ScaffoldingBlock.WATERLOGGED, false)).setValue(ScaffoldingBlock.BOTTOM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ScaffoldingBlock.DISTANCE, ScaffoldingBlock.WATERLOGGED, ScaffoldingBlock.BOTTOM);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return !context.isHoldingItem(state.getBlock().asItem()) ? ((Boolean) state.getValue(ScaffoldingBlock.BOTTOM) ? ScaffoldingBlock.UNSTABLE_SHAPE : ScaffoldingBlock.STABLE_SHAPE) : Shapes.block();
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(this.asItem());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos blockposition = ctx.getClickedPos();
        Level world = ctx.getLevel();
        int i = ScaffoldingBlock.getDistance(world, blockposition);

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(ScaffoldingBlock.WATERLOGGED, world.getFluidState(blockposition).getType() == Fluids.WATER)).setValue(ScaffoldingBlock.DISTANCE, i)).setValue(ScaffoldingBlock.BOTTOM, this.isBottom(world, blockposition, i));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClientSide) {
            world.scheduleTick(pos, (Block) this, 1);
        }

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(ScaffoldingBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        if (!world.isClientSide()) {
            world.scheduleTick(pos, (Block) this, 1);
        }

        return state;
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        int i = ScaffoldingBlock.getDistance(world, pos);
        BlockState iblockdata1 = (BlockState) ((BlockState) state.setValue(ScaffoldingBlock.DISTANCE, i)).setValue(ScaffoldingBlock.BOTTOM, this.isBottom(world, pos, i));

        if ((Integer) iblockdata1.getValue(ScaffoldingBlock.DISTANCE) == 7 && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, iblockdata1.getFluidState().createLegacyBlock()).isCancelled()) { // CraftBukkit - BlockFadeEvent // Paper - fix wrong block state
            if ((Integer) state.getValue(ScaffoldingBlock.DISTANCE) == 7) {
                FallingBlockEntity.fall(world, pos, iblockdata1);
            } else {
                world.destroyBlock(pos, true);
            }
        } else if (state != iblockdata1) {
            world.setBlock(pos, iblockdata1, 3);
        }

    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return ScaffoldingBlock.getDistance(world, pos) < 7;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? ScaffoldingBlock.STABLE_SHAPE : ((Integer) state.getValue(ScaffoldingBlock.DISTANCE) != 0 && (Boolean) state.getValue(ScaffoldingBlock.BOTTOM) && context.isAbove(ScaffoldingBlock.BELOW_BLOCK, pos, true) ? ScaffoldingBlock.UNSTABLE_SHAPE_BOTTOM : Shapes.empty());
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(ScaffoldingBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private boolean isBottom(BlockGetter world, BlockPos pos, int distance) {
        return distance > 0 && !world.getBlockState(pos.below()).is((Block) this);
    }

    public static int getDistance(BlockGetter world, BlockPos pos) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable().move(Direction.DOWN);
        BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition);
        int i = 7;

        if (iblockdata.is(Blocks.SCAFFOLDING)) {
            i = (Integer) iblockdata.getValue(ScaffoldingBlock.DISTANCE);
        } else if (iblockdata.isFaceSturdy(world, blockposition_mutableblockposition, Direction.UP)) {
            return 0;
        }

        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockState iblockdata1 = world.getBlockState(blockposition_mutableblockposition.setWithOffset(pos, enumdirection));

            if (iblockdata1.is(Blocks.SCAFFOLDING)) {
                i = Math.min(i, (Integer) iblockdata1.getValue(ScaffoldingBlock.DISTANCE) + 1);
                if (i == 1) {
                    break;
                }
            }
        }

        return i;
    }

    static {
        VoxelShape voxelshape = Block.box(0.0D, 14.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        VoxelShape voxelshape1 = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 2.0D);
        VoxelShape voxelshape2 = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D);
        VoxelShape voxelshape3 = Block.box(0.0D, 0.0D, 14.0D, 2.0D, 16.0D, 16.0D);
        VoxelShape voxelshape4 = Block.box(14.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D);

        STABLE_SHAPE = Shapes.or(voxelshape, voxelshape1, voxelshape2, voxelshape3, voxelshape4);
        VoxelShape voxelshape5 = Block.box(0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 16.0D);
        VoxelShape voxelshape6 = Block.box(14.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
        VoxelShape voxelshape7 = Block.box(0.0D, 0.0D, 14.0D, 16.0D, 2.0D, 16.0D);
        VoxelShape voxelshape8 = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 2.0D);

        UNSTABLE_SHAPE = Shapes.or(ScaffoldingBlock.UNSTABLE_SHAPE_BOTTOM, ScaffoldingBlock.STABLE_SHAPE, voxelshape6, voxelshape5, voxelshape8, voxelshape7);
    }
}
