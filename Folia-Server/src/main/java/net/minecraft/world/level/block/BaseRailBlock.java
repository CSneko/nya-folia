package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseRailBlock extends Block implements SimpleWaterloggedBlock {
    protected static final VoxelShape FLAT_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    protected static final VoxelShape HALF_BLOCK_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final boolean isStraight;

    public static boolean isRail(Level world, BlockPos pos) {
        return isRail(world.getBlockState(pos));
    }

    public static boolean isRail(BlockState state) {
        return state.is(BlockTags.RAILS) && state.getBlock() instanceof BaseRailBlock;
    }

    protected BaseRailBlock(boolean forbidCurves, BlockBehaviour.Properties settings) {
        super(settings);
        this.isStraight = forbidCurves;
    }

    public boolean isStraight() {
        return this.isStraight;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        RailShape railShape = state.is(this) ? state.getValue(this.getShapeProperty()) : null;
        return railShape != null && railShape.isAscending() ? HALF_BLOCK_AABB : FLAT_AABB;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return canSupportRigidBlock(world, pos.below());
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            this.updateState(state, world, pos, notify);
        }
    }

    protected BlockState updateState(BlockState state, Level world, BlockPos pos, boolean notify) {
        state = this.updateDir(world, pos, state, true);
        if (this.isStraight) {
            world.neighborChanged(state, pos, this, pos, notify);
            state = world.getBlockState(pos); // Paper - don't desync, update again
        }

        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide && world.getBlockState(pos).is(this)) {
            RailShape railShape = state.getValue(this.getShapeProperty());
            if (shouldBeRemoved(pos, world, railShape)) {
                dropResources(state, world, pos);
                world.removeBlock(pos, notify);
            } else {
                this.updateState(state, world, pos, sourceBlock);
            }

        }
    }

    private static boolean shouldBeRemoved(BlockPos pos, Level world, RailShape shape) {
        if (!canSupportRigidBlock(world, pos.below())) {
            return true;
        } else {
            switch (shape) {
                case ASCENDING_EAST:
                    return !canSupportRigidBlock(world, pos.east());
                case ASCENDING_WEST:
                    return !canSupportRigidBlock(world, pos.west());
                case ASCENDING_NORTH:
                    return !canSupportRigidBlock(world, pos.north());
                case ASCENDING_SOUTH:
                    return !canSupportRigidBlock(world, pos.south());
                default:
                    return false;
            }
        }
    }

    protected void updateState(BlockState state, Level world, BlockPos pos, Block neighbor) {
    }

    protected BlockState updateDir(Level world, BlockPos pos, BlockState state, boolean forceUpdate) {
        if (world.isClientSide) {
            return state;
        } else {
            RailShape railShape = state.getValue(this.getShapeProperty());
            return (new RailState(world, pos, state)).place(world.hasNeighborSignal(pos), forceUpdate, railShape).getState();
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved) {
            super.onRemove(state, world, pos, newState, moved);
            if (state.getValue(this.getShapeProperty()).isAscending()) {
                world.updateNeighborsAt(pos.above(), this);
            }

            if (this.isStraight) {
                world.updateNeighborsAt(pos, this);
                world.updateNeighborsAt(pos.below(), this);
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        boolean bl = fluidState.getType() == Fluids.WATER;
        BlockState blockState = super.defaultBlockState();
        Direction direction = ctx.getHorizontalDirection();
        boolean bl2 = direction == Direction.EAST || direction == Direction.WEST;
        return blockState.setValue(this.getShapeProperty(), bl2 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH).setValue(WATERLOGGED, Boolean.valueOf(bl));
    }

    public abstract Property<RailShape> getShapeProperty();

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
