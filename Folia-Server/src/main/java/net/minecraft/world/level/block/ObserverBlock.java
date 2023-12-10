package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ObserverBlock extends DirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ObserverBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ObserverBlock.FACING, Direction.SOUTH)).setValue(ObserverBlock.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ObserverBlock.FACING, ObserverBlock.POWERED);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ObserverBlock.FACING, rotation.rotate((Direction) state.getValue(ObserverBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ObserverBlock.FACING)));
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(ObserverBlock.POWERED)) {
            // CraftBukkit start
            if (CraftEventFactory.callRedstoneChange(world, pos, 15, 0).getNewCurrent() != 0) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.setValue(ObserverBlock.POWERED, false), 2);
        } else {
            // CraftBukkit start
            if (CraftEventFactory.callRedstoneChange(world, pos, 0, 15).getNewCurrent() != 15) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.setValue(ObserverBlock.POWERED, true), 2);
            world.scheduleTick(pos, (Block) this, 2);
        }

        this.updateNeighborsInFront(world, pos, state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(ObserverBlock.FACING) == direction && !(Boolean) state.getValue(ObserverBlock.POWERED)) {
            this.startSignal(world, pos);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    private void startSignal(LevelAccessor world, BlockPos pos) {
        if (!world.isClientSide() && !world.getBlockTicks().hasScheduledTick(pos, this)) {
            world.scheduleTick(pos, (Block) this, 2);
        }

    }

    protected void updateNeighborsInFront(Level world, BlockPos pos, BlockState state) {
        Direction enumdirection = (Direction) state.getValue(ObserverBlock.FACING);
        BlockPos blockposition1 = pos.relative(enumdirection.getOpposite());

        world.neighborChanged(blockposition1, this, pos);
        world.updateNeighborsAtExceptFromFacing(blockposition1, this, enumdirection);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getSignal(world, pos, direction);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(ObserverBlock.POWERED) && state.getValue(ObserverBlock.FACING) == direction ? 15 : 0;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!state.is(oldState.getBlock())) {
            if (!world.isClientSide() && (Boolean) state.getValue(ObserverBlock.POWERED) && !world.getBlockTicks().hasScheduledTick(pos, this)) {
                BlockState iblockdata2 = (BlockState) state.setValue(ObserverBlock.POWERED, false);

                world.setBlock(pos, iblockdata2, 18);
                this.updateNeighborsInFront(world, pos, iblockdata2);
            }

        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!world.isClientSide && (Boolean) state.getValue(ObserverBlock.POWERED) && world.getBlockTicks().hasScheduledTick(pos, this)) {
                this.updateNeighborsInFront(world, pos, (BlockState) state.setValue(ObserverBlock.POWERED, false));
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(ObserverBlock.FACING, ctx.getNearestLookingDirection().getOpposite().getOpposite());
    }
}
