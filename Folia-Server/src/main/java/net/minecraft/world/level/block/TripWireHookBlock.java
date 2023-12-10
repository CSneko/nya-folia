package net.minecraft.world.level.block;

import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class TripWireHookBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    protected static final int WIRE_DIST_MIN = 1;
    protected static final int WIRE_DIST_MAX = 42;
    private static final int RECHECK_PERIOD = 10;
    protected static final int AABB_OFFSET = 3;
    protected static final VoxelShape NORTH_AABB = Block.box(5.0D, 0.0D, 10.0D, 11.0D, 10.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0D, 0.0D, 0.0D, 11.0D, 10.0D, 6.0D);
    protected static final VoxelShape WEST_AABB = Block.box(10.0D, 0.0D, 5.0D, 16.0D, 10.0D, 11.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 5.0D, 6.0D, 10.0D, 11.0D);

    public TripWireHookBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TripWireHookBlock.FACING, Direction.NORTH)).setValue(TripWireHookBlock.POWERED, false)).setValue(TripWireHookBlock.ATTACHED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        switch ((Direction) state.getValue(TripWireHookBlock.FACING)) {
            case EAST:
            default:
                return TripWireHookBlock.EAST_AABB;
            case WEST:
                return TripWireHookBlock.WEST_AABB;
            case SOUTH:
                return TripWireHookBlock.SOUTH_AABB;
            case NORTH:
                return TripWireHookBlock.NORTH_AABB;
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction enumdirection = (Direction) state.getValue(TripWireHookBlock.FACING);
        BlockPos blockposition1 = pos.relative(enumdirection.getOpposite());
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        return enumdirection.getAxis().isHorizontal() && iblockdata1.isFaceSturdy(world, blockposition1, enumdirection);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction.getOpposite() == state.getValue(TripWireHookBlock.FACING) && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState iblockdata = (BlockState) ((BlockState) this.defaultBlockState().setValue(TripWireHookBlock.POWERED, false)).setValue(TripWireHookBlock.ATTACHED, false);
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        Direction[] aenumdirection = ctx.getNearestLookingDirections();
        Direction[] aenumdirection1 = aenumdirection;
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection1[j];

            if (enumdirection.getAxis().isHorizontal()) {
                Direction enumdirection1 = enumdirection.getOpposite();

                iblockdata = (BlockState) iblockdata.setValue(TripWireHookBlock.FACING, enumdirection1);
                if (iblockdata.canSurvive(world, blockposition)) {
                    return iblockdata;
                }
            }
        }

        return null;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        this.calculateState(world, pos, state, false, false, -1, (BlockState) null);
    }

    public void calculateState(Level world, BlockPos pos, BlockState state, boolean beingRemoved, boolean flag1, int i, @Nullable BlockState iblockdata1) {
        // Paper start - fix tripwire inconsistency
        this.calculateState(world, pos, state, beingRemoved, flag1, i, iblockdata1, false);
    }

    public void calculateState(Level world, BlockPos pos, BlockState state, boolean beingRemoved, boolean flag1, int i, @Nullable BlockState iblockdata1, boolean tripWireBeingRemoved) {
        // Paper end
        Direction enumdirection = (Direction) state.getValue(TripWireHookBlock.FACING);
        boolean flag2 = (Boolean) state.getValue(TripWireHookBlock.ATTACHED);
        boolean flag3 = (Boolean) state.getValue(TripWireHookBlock.POWERED);
        boolean flag4 = !beingRemoved;
        boolean flag5 = false;
        int j = 0;
        BlockState[] aiblockdata = new BlockState[42];

        BlockPos blockposition1;

        for (int k = 1; k < 42; ++k) {
            blockposition1 = pos.relative(enumdirection, k);
            BlockState iblockdata2 = world.getBlockState(blockposition1);

            if (iblockdata2.is(Blocks.TRIPWIRE_HOOK)) {
                if (iblockdata2.getValue(TripWireHookBlock.FACING) == enumdirection.getOpposite()) {
                    j = k;
                }
                break;
            }

            if (!iblockdata2.is(Blocks.TRIPWIRE) && k != i) {
                aiblockdata[k] = null;
                flag4 = false;
            } else {
                if (k == i) {
                    iblockdata2 = (BlockState) MoreObjects.firstNonNull(iblockdata1, iblockdata2);
                }

                boolean flag6 = !(Boolean) iblockdata2.getValue(TripWireBlock.DISARMED);
                boolean flag7 = (Boolean) iblockdata2.getValue(TripWireBlock.POWERED);

                flag5 |= flag6 && flag7;
                if (k != i || !tripWireBeingRemoved || !flag6) // Paper - don't update the tripwire again if being removed and not disarmed
                aiblockdata[k] = iblockdata2;
                if (k == i) {
                    world.scheduleTick(pos, (Block) this, 10);
                    flag4 &= flag6;
                }
            }
        }

        flag4 &= j > 1;
        flag5 &= flag4;
        BlockState iblockdata3 = (BlockState) ((BlockState) this.defaultBlockState().setValue(TripWireHookBlock.ATTACHED, flag4)).setValue(TripWireHookBlock.POWERED, flag5);

        if (j > 0) {
            blockposition1 = pos.relative(enumdirection, j);
            Direction enumdirection1 = enumdirection.getOpposite();

            world.setBlock(blockposition1, (BlockState) iblockdata3.setValue(TripWireHookBlock.FACING, enumdirection1), 3);
            this.notifyNeighbors(world, blockposition1, enumdirection1);
            this.emitState(world, blockposition1, flag4, flag5, flag2, flag3);
        }

        // CraftBukkit start
        org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());

        BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, 15, 0);
        world.getCraftServer().getPluginManager().callEvent(eventRedstone);

        if (eventRedstone.getNewCurrent() > 0) {
            return;
        }
        // CraftBukkit end

        this.emitState(world, pos, flag4, flag5, flag2, flag3);
        if (!beingRemoved) {
            if (world.getBlockState(pos).getBlock() == Blocks.TRIPWIRE_HOOK) // Paper - validate
            world.setBlock(pos, (BlockState) iblockdata3.setValue(TripWireHookBlock.FACING, enumdirection), 3);
            if (flag1) {
                this.notifyNeighbors(world, pos, enumdirection);
            }
        }

        if (flag2 != flag4) {
            for (int l = 1; l < j; ++l) {
                BlockPos blockposition2 = pos.relative(enumdirection, l);
                BlockState iblockdata4 = aiblockdata[l];

                if (iblockdata4 != null) {
                    world.setBlock(blockposition2, (BlockState) iblockdata4.setValue(TripWireHookBlock.ATTACHED, flag4), 3);
                    if (!world.getBlockState(blockposition2).isAir()) {
                        ;
                    }
                }
            }
        }

    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.calculateState(world, pos, state, false, true, -1, (BlockState) null);
    }

    private void emitState(Level world, BlockPos pos, boolean attached, boolean on, boolean detached, boolean off) {
        if (on && !off) {
            world.playSound((Player) null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 0.6F);
            world.gameEvent((Entity) null, GameEvent.BLOCK_ACTIVATE, pos);
        } else if (!on && off) {
            world.playSound((Player) null, pos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.5F);
            world.gameEvent((Entity) null, GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (attached && !detached) {
            world.playSound((Player) null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4F, 0.7F);
            world.gameEvent((Entity) null, GameEvent.BLOCK_ATTACH, pos);
        } else if (!attached && detached) {
            world.playSound((Player) null, pos, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 0.4F, 1.2F / (world.random.nextFloat() * 0.2F + 0.9F));
            world.gameEvent((Entity) null, GameEvent.BLOCK_DETACH, pos);
        }

    }

    private void notifyNeighbors(Level world, BlockPos pos, Direction direction) {
        world.updateNeighborsAt(pos, this);
        world.updateNeighborsAt(pos.relative(direction.getOpposite()), this);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock())) {
            boolean flag1 = (Boolean) state.getValue(TripWireHookBlock.ATTACHED);
            boolean flag2 = (Boolean) state.getValue(TripWireHookBlock.POWERED);

            if (flag1 || flag2) {
                this.calculateState(world, pos, state, true, false, -1, (BlockState) null);
            }

            if (flag2) {
                world.updateNeighborsAt(pos, this);
                world.updateNeighborsAt(pos.relative(((Direction) state.getValue(TripWireHookBlock.FACING)).getOpposite()), this);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(TripWireHookBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return !(Boolean) state.getValue(TripWireHookBlock.POWERED) ? 0 : (state.getValue(TripWireHookBlock.FACING) == direction ? 15 : 0);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(TripWireHookBlock.FACING, rotation.rotate((Direction) state.getValue(TripWireHookBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(TripWireHookBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TripWireHookBlock.FACING, TripWireHookBlock.POWERED, TripWireHookBlock.ATTACHED);
    }
}
