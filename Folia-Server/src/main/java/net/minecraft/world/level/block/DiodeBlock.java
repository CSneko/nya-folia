package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public abstract class DiodeBlock extends HorizontalDirectionalBlock {

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected DiodeBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return DiodeBlock.SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();

        return this.canSurviveOn(world, blockposition1, world.getBlockState(blockposition1));
    }

    protected boolean canSurviveOn(LevelReader world, BlockPos pos, BlockState state) {
        return state.isFaceSturdy(world, pos, Direction.UP, SupportType.RIGID);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!this.isLocked(world, pos, state)) {
            boolean flag = (Boolean) state.getValue(DiodeBlock.POWERED);
            boolean flag1 = this.shouldTurnOn(world, pos, state);

            if (flag && !flag1) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(world, pos, 15, 0).getNewCurrent() != 0) {
                    return;
                }
                // CraftBukkit end
                world.setBlock(pos, (BlockState) state.setValue(DiodeBlock.POWERED, false), 2);
            } else if (!flag) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(world, pos, 0, 15).getNewCurrent() != 15) {
                    return;
                }
                // CraftBukkit end
                world.setBlock(pos, (BlockState) state.setValue(DiodeBlock.POWERED, true), 2);
                if (!flag1) {
                    world.scheduleTick(pos, (Block) this, this.getDelay(state), TickPriority.VERY_HIGH);
                }
            }

        }
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getSignal(world, pos, direction);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return !(Boolean) state.getValue(DiodeBlock.POWERED) ? 0 : (state.getValue(DiodeBlock.FACING) == direction ? this.getOutputSignal(world, pos, state) : 0);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (state.canSurvive(world, pos)) {
            this.checkTickOnNeighbor(world, pos, state);
        } else {
            BlockEntity tileentity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;

            dropResources(state, world, pos, tileentity);
            world.removeBlock(pos, false);
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                world.updateNeighborsAt(pos.relative(enumdirection), this);
            }

        }
    }

    protected void checkTickOnNeighbor(Level world, BlockPos pos, BlockState state) {
        if (!this.isLocked(world, pos, state)) {
            boolean flag = (Boolean) state.getValue(DiodeBlock.POWERED);
            boolean flag1 = this.shouldTurnOn(world, pos, state);

            if (flag != flag1 && !world.getBlockTicks().willTickThisTick(pos, this)) {
                TickPriority ticklistpriority = TickPriority.HIGH;

                if (this.shouldPrioritize(world, pos, state)) {
                    ticklistpriority = TickPriority.EXTREMELY_HIGH;
                } else if (flag) {
                    ticklistpriority = TickPriority.VERY_HIGH;
                }

                world.scheduleTick(pos, (Block) this, this.getDelay(state), ticklistpriority);
            }

        }
    }

    public boolean isLocked(LevelReader world, BlockPos pos, BlockState state) {
        return false;
    }

    protected boolean shouldTurnOn(Level world, BlockPos pos, BlockState state) {
        return this.getInputSignal(world, pos, state) > 0;
    }

    protected int getInputSignal(Level world, BlockPos pos, BlockState state) {
        Direction enumdirection = (Direction) state.getValue(DiodeBlock.FACING);
        BlockPos blockposition1 = pos.relative(enumdirection);
        int i = world.getSignal(blockposition1, enumdirection);

        if (i >= 15) {
            return i;
        } else {
            BlockState iblockdata1 = world.getBlockState(blockposition1);

            return Math.max(i, iblockdata1.is(Blocks.REDSTONE_WIRE) ? (Integer) iblockdata1.getValue(RedStoneWireBlock.POWER) : 0);
        }
    }

    protected int getAlternateSignal(SignalGetter world, BlockPos pos, BlockState state) {
        Direction enumdirection = (Direction) state.getValue(DiodeBlock.FACING);
        Direction enumdirection1 = enumdirection.getClockWise();
        Direction enumdirection2 = enumdirection.getCounterClockWise();
        boolean flag = this.sideInputDiodesOnly();

        return Math.max(world.getControlInputSignal(pos.relative(enumdirection1), enumdirection1, flag), world.getControlInputSignal(pos.relative(enumdirection2), enumdirection2, flag));
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(DiodeBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (this.shouldTurnOn(world, pos, state)) {
            world.scheduleTick(pos, (Block) this, 1);
        }

    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        this.updateNeighborsInFront(world, pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock())) {
            super.onRemove(state, world, pos, newState, moved);
            this.updateNeighborsInFront(world, pos, state);
        }
    }

    protected void updateNeighborsInFront(Level world, BlockPos pos, BlockState state) {
        Direction enumdirection = (Direction) state.getValue(DiodeBlock.FACING);
        BlockPos blockposition1 = pos.relative(enumdirection.getOpposite());

        world.neighborChanged(blockposition1, this, pos);
        world.updateNeighborsAtExceptFromFacing(blockposition1, this, enumdirection);
    }

    protected boolean sideInputDiodesOnly() {
        return false;
    }

    protected int getOutputSignal(BlockGetter world, BlockPos pos, BlockState state) {
        return 15;
    }

    public static boolean isDiode(BlockState state) {
        return state.getBlock() instanceof DiodeBlock;
    }

    public boolean shouldPrioritize(BlockGetter world, BlockPos pos, BlockState state) {
        Direction enumdirection = ((Direction) state.getValue(DiodeBlock.FACING)).getOpposite();
        BlockState iblockdata1 = world.getBlockState(pos.relative(enumdirection));

        return DiodeBlock.isDiode(iblockdata1) && iblockdata1.getValue(DiodeBlock.FACING) != enumdirection;
    }

    protected abstract int getDelay(BlockState state);
}
