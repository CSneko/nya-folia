package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BellBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BellAttachType> ATTACHMENT = BlockStateProperties.BELL_ATTACHMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape NORTH_SOUTH_FLOOR_SHAPE = Block.box(0.0D, 0.0D, 4.0D, 16.0D, 16.0D, 12.0D);
    private static final VoxelShape EAST_WEST_FLOOR_SHAPE = Block.box(4.0D, 0.0D, 0.0D, 12.0D, 16.0D, 16.0D);
    private static final VoxelShape BELL_TOP_SHAPE = Block.box(5.0D, 6.0D, 5.0D, 11.0D, 13.0D, 11.0D);
    private static final VoxelShape BELL_BOTTOM_SHAPE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 6.0D, 12.0D);
    private static final VoxelShape BELL_SHAPE = Shapes.or(BellBlock.BELL_BOTTOM_SHAPE, BellBlock.BELL_TOP_SHAPE);
    private static final VoxelShape NORTH_SOUTH_BETWEEN = Shapes.or(BellBlock.BELL_SHAPE, Block.box(7.0D, 13.0D, 0.0D, 9.0D, 15.0D, 16.0D));
    private static final VoxelShape EAST_WEST_BETWEEN = Shapes.or(BellBlock.BELL_SHAPE, Block.box(0.0D, 13.0D, 7.0D, 16.0D, 15.0D, 9.0D));
    private static final VoxelShape TO_WEST = Shapes.or(BellBlock.BELL_SHAPE, Block.box(0.0D, 13.0D, 7.0D, 13.0D, 15.0D, 9.0D));
    private static final VoxelShape TO_EAST = Shapes.or(BellBlock.BELL_SHAPE, Block.box(3.0D, 13.0D, 7.0D, 16.0D, 15.0D, 9.0D));
    private static final VoxelShape TO_NORTH = Shapes.or(BellBlock.BELL_SHAPE, Block.box(7.0D, 13.0D, 0.0D, 9.0D, 15.0D, 13.0D));
    private static final VoxelShape TO_SOUTH = Shapes.or(BellBlock.BELL_SHAPE, Block.box(7.0D, 13.0D, 3.0D, 9.0D, 15.0D, 16.0D));
    private static final VoxelShape CEILING_SHAPE = Shapes.or(BellBlock.BELL_SHAPE, Block.box(7.0D, 13.0D, 7.0D, 9.0D, 16.0D, 9.0D));
    public static final int EVENT_BELL_RING = 1;

    public BellBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BellBlock.FACING, Direction.NORTH)).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR)).setValue(BellBlock.POWERED, false));
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        boolean flag1 = world.hasNeighborSignal(pos);

        if (flag1 != (Boolean) state.getValue(BellBlock.POWERED)) {
            if (flag1) {
                this.attemptToRing(world, pos, (Direction) null);
            }

            world.setBlock(pos, (BlockState) state.setValue(BellBlock.POWERED, flag1), 3);
        }

    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        Entity entity = projectile.getOwner();
        Player entityhuman = entity instanceof Player ? (Player) entity : null;

        this.onHit(world, state, hit, entityhuman, true);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return this.onHit(world, state, hit, player, true) ? InteractionResult.sidedSuccess(world.isClientSide) : InteractionResult.PASS;
    }

    public boolean onHit(Level world, BlockState state, BlockHitResult hitResult, @Nullable Player player, boolean checkHitPos) {
        Direction enumdirection = hitResult.getDirection();
        BlockPos blockposition = hitResult.getBlockPos();
        boolean flag1 = !checkHitPos || this.isProperHit(state, enumdirection, hitResult.getLocation().y - (double) blockposition.getY());

        if (flag1) {
            boolean flag2 = this.attemptToRing(player, world, blockposition, enumdirection);

            if (flag2 && player != null) {
                player.awardStat(Stats.BELL_RING);
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isProperHit(BlockState state, Direction side, double y) {
        if (side.getAxis() != Direction.Axis.Y && y <= 0.8123999834060669D) {
            Direction enumdirection1 = (Direction) state.getValue(BellBlock.FACING);
            BellAttachType blockpropertybellattach = (BellAttachType) state.getValue(BellBlock.ATTACHMENT);

            switch (blockpropertybellattach) {
                case FLOOR:
                    return enumdirection1.getAxis() == side.getAxis();
                case SINGLE_WALL:
                case DOUBLE_WALL:
                    return enumdirection1.getAxis() != side.getAxis();
                case CEILING:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public boolean attemptToRing(Level world, BlockPos pos, @Nullable Direction direction) {
        return this.attemptToRing((Entity) null, world, pos, direction);
    }

    public boolean attemptToRing(@Nullable Entity entity, Level world, BlockPos pos, @Nullable Direction direction) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (!world.isClientSide && tileentity instanceof BellBlockEntity) {
            if (direction == null) {
                direction = (Direction) world.getBlockState(pos).getValue(BellBlock.FACING);
            }
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBellRingEvent(world, pos, direction, entity)) {
                return false;
            }
            // CraftBukkit end

            ((BellBlockEntity) tileentity).onHit(direction);
            world.playSound((Player) null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
            world.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
            return true;
        } else {
            return false;
        }
    }

    private VoxelShape getVoxelShape(BlockState state) {
        Direction enumdirection = (Direction) state.getValue(BellBlock.FACING);
        BellAttachType blockpropertybellattach = (BellAttachType) state.getValue(BellBlock.ATTACHMENT);

        return blockpropertybellattach == BellAttachType.FLOOR ? (enumdirection != Direction.NORTH && enumdirection != Direction.SOUTH ? BellBlock.EAST_WEST_FLOOR_SHAPE : BellBlock.NORTH_SOUTH_FLOOR_SHAPE) : (blockpropertybellattach == BellAttachType.CEILING ? BellBlock.CEILING_SHAPE : (blockpropertybellattach == BellAttachType.DOUBLE_WALL ? (enumdirection != Direction.NORTH && enumdirection != Direction.SOUTH ? BellBlock.EAST_WEST_BETWEEN : BellBlock.NORTH_SOUTH_BETWEEN) : (enumdirection == Direction.NORTH ? BellBlock.TO_NORTH : (enumdirection == Direction.SOUTH ? BellBlock.TO_SOUTH : (enumdirection == Direction.EAST ? BellBlock.TO_EAST : BellBlock.TO_WEST)))));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction enumdirection = ctx.getClickedFace();
        BlockPos blockposition = ctx.getClickedPos();
        Level world = ctx.getLevel();
        Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
        BlockState iblockdata;

        if (enumdirection_enumaxis == Direction.Axis.Y) {
            iblockdata = (BlockState) ((BlockState) this.defaultBlockState().setValue(BellBlock.ATTACHMENT, enumdirection == Direction.DOWN ? BellAttachType.CEILING : BellAttachType.FLOOR)).setValue(BellBlock.FACING, ctx.getHorizontalDirection());
            if (iblockdata.canSurvive(ctx.getLevel(), blockposition)) {
                return iblockdata;
            }
        } else {
            boolean flag = enumdirection_enumaxis == Direction.Axis.X && world.getBlockState(blockposition.west()).isFaceSturdy(world, blockposition.west(), Direction.EAST) && world.getBlockState(blockposition.east()).isFaceSturdy(world, blockposition.east(), Direction.WEST) || enumdirection_enumaxis == Direction.Axis.Z && world.getBlockState(blockposition.north()).isFaceSturdy(world, blockposition.north(), Direction.SOUTH) && world.getBlockState(blockposition.south()).isFaceSturdy(world, blockposition.south(), Direction.NORTH);

            iblockdata = (BlockState) ((BlockState) this.defaultBlockState().setValue(BellBlock.FACING, enumdirection.getOpposite())).setValue(BellBlock.ATTACHMENT, flag ? BellAttachType.DOUBLE_WALL : BellAttachType.SINGLE_WALL);
            if (iblockdata.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return iblockdata;
            }

            boolean flag1 = world.getBlockState(blockposition.below()).isFaceSturdy(world, blockposition.below(), Direction.UP);

            iblockdata = (BlockState) iblockdata.setValue(BellBlock.ATTACHMENT, flag1 ? BellAttachType.FLOOR : BellAttachType.CEILING);
            if (iblockdata.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return iblockdata;
            }
        }

        return null;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        BellAttachType blockpropertybellattach = (BellAttachType) state.getValue(BellBlock.ATTACHMENT);
        Direction enumdirection1 = BellBlock.getConnectedDirection(state).getOpposite();

        if (enumdirection1 == direction && !state.canSurvive(world, pos) && blockpropertybellattach != BellAttachType.DOUBLE_WALL) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if (direction.getAxis() == ((Direction) state.getValue(BellBlock.FACING)).getAxis()) {
                if (blockpropertybellattach == BellAttachType.DOUBLE_WALL && !neighborState.isFaceSturdy(world, neighborPos, direction)) {
                    return (BlockState) ((BlockState) state.setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL)).setValue(BellBlock.FACING, direction.getOpposite());
                }

                if (blockpropertybellattach == BellAttachType.SINGLE_WALL && enumdirection1.getOpposite() == direction && neighborState.isFaceSturdy(world, neighborPos, (Direction) state.getValue(BellBlock.FACING))) {
                    return (BlockState) state.setValue(BellBlock.ATTACHMENT, BellAttachType.DOUBLE_WALL);
                }
            }

            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction enumdirection = BellBlock.getConnectedDirection(state).getOpposite();

        return enumdirection == Direction.UP ? Block.canSupportCenter(world, pos.above(), Direction.DOWN) : FaceAttachedHorizontalDirectionalBlock.canAttach(world, pos, enumdirection);
    }

    private static Direction getConnectedDirection(BlockState state) {
        switch ((BellAttachType) state.getValue(BellBlock.ATTACHMENT)) {
            case FLOOR:
                return Direction.UP;
            case CEILING:
                return Direction.DOWN;
            default:
                return ((Direction) state.getValue(BellBlock.FACING)).getOpposite();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BellBlock.FACING, BellBlock.ATTACHMENT, BellBlock.POWERED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.BELL, world.isClientSide ? BellBlockEntity::clientTick : BellBlockEntity::serverTick);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    // CraftBukkit start - fix MC-253819
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BellBlock.FACING, rotation.rotate(state.getValue(BellBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BellBlock.FACING)));
    }
    // CraftBukkit end
}
