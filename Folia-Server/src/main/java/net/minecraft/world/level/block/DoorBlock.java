package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class DoorBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final float AABB_DOOR_THICKNESS = 3.0F;
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D);
    private final BlockSetType type;

    protected DoorBlock(BlockBehaviour.Properties settings, BlockSetType blockSetType) {
        super(settings.sound(blockSetType.soundType()));
        this.type = blockSetType;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DoorBlock.FACING, Direction.NORTH)).setValue(DoorBlock.OPEN, false)).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)).setValue(DoorBlock.POWERED, false)).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    }

    public BlockSetType type() {
        return this.type;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction enumdirection = (Direction) state.getValue(DoorBlock.FACING);
        boolean flag = !(Boolean) state.getValue(DoorBlock.OPEN);
        boolean flag1 = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;

        switch (enumdirection) {
            case EAST:
            default:
                return flag ? DoorBlock.EAST_AABB : (flag1 ? DoorBlock.NORTH_AABB : DoorBlock.SOUTH_AABB);
            case SOUTH:
                return flag ? DoorBlock.SOUTH_AABB : (flag1 ? DoorBlock.EAST_AABB : DoorBlock.WEST_AABB);
            case WEST:
                return flag ? DoorBlock.WEST_AABB : (flag1 ? DoorBlock.SOUTH_AABB : DoorBlock.NORTH_AABB);
            case NORTH:
                return flag ? DoorBlock.NORTH_AABB : (flag1 ? DoorBlock.WEST_AABB : DoorBlock.EAST_AABB);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf blockpropertydoubleblockhalf = (DoubleBlockHalf) state.getValue(DoorBlock.HALF);

        return direction.getAxis() == Direction.Axis.Y && blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER == (direction == Direction.UP) ? (neighborState.is((Block) this) && neighborState.getValue(DoorBlock.HALF) != blockpropertydoubleblockhalf ? (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(DoorBlock.FACING, (Direction) neighborState.getValue(DoorBlock.FACING))).setValue(DoorBlock.OPEN, (Boolean) neighborState.getValue(DoorBlock.OPEN))).setValue(DoorBlock.HINGE, (DoorHingeSide) neighborState.getValue(DoorBlock.HINGE))).setValue(DoorBlock.POWERED, (Boolean) neighborState.getValue(DoorBlock.POWERED)) : Blocks.AIR.defaultBlockState()) : (blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos));
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && player.isCreative()) {
            DoublePlantBlock.preventCreativeDropFromBottomPart(world, pos, state, player);
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        switch (type) {
            case LAND:
                return (Boolean) state.getValue(DoorBlock.OPEN);
            case WATER:
                return false;
            case AIR:
                return (Boolean) state.getValue(DoorBlock.OPEN);
            default:
                return false;
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos blockposition = ctx.getClickedPos();
        Level world = ctx.getLevel();

        if (blockposition.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(blockposition.above()).canBeReplaced(ctx)) {
            boolean flag = world.hasNeighborSignal(blockposition) || world.hasNeighborSignal(blockposition.above());

            return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(DoorBlock.FACING, ctx.getHorizontalDirection())).setValue(DoorBlock.HINGE, this.getHinge(ctx))).setValue(DoorBlock.POWERED, flag)).setValue(DoorBlock.OPEN, flag)).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        } else {
            return null;
        }
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        world.setBlock(pos.above(), (BlockState) state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private DoorHingeSide getHinge(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        Direction enumdirection = ctx.getHorizontalDirection();
        BlockPos blockposition1 = blockposition.above();
        Direction enumdirection1 = enumdirection.getCounterClockWise();
        BlockPos blockposition2 = blockposition.relative(enumdirection1);
        BlockState iblockdata = world.getBlockState(blockposition2);
        BlockPos blockposition3 = blockposition1.relative(enumdirection1);
        BlockState iblockdata1 = world.getBlockState(blockposition3);
        Direction enumdirection2 = enumdirection.getClockWise();
        BlockPos blockposition4 = blockposition.relative(enumdirection2);
        BlockState iblockdata2 = world.getBlockState(blockposition4);
        BlockPos blockposition5 = blockposition1.relative(enumdirection2);
        BlockState iblockdata3 = world.getBlockState(blockposition5);
        int i = (iblockdata.isCollisionShapeFullBlock(world, blockposition2) ? -1 : 0) + (iblockdata1.isCollisionShapeFullBlock(world, blockposition3) ? -1 : 0) + (iblockdata2.isCollisionShapeFullBlock(world, blockposition4) ? 1 : 0) + (iblockdata3.isCollisionShapeFullBlock(world, blockposition5) ? 1 : 0);
        boolean flag = iblockdata.is((Block) this) && iblockdata.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        boolean flag1 = iblockdata2.is((Block) this) && iblockdata2.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;

        if ((!flag || flag1) && i <= 0) {
            if ((!flag1 || flag) && i >= 0) {
                int j = enumdirection.getStepX();
                int k = enumdirection.getStepZ();
                Vec3 vec3d = ctx.getClickLocation();
                double d0 = vec3d.x - (double) blockposition.getX();
                double d1 = vec3d.z - (double) blockposition.getZ();

                return (j >= 0 || d1 >= 0.5D) && (j <= 0 || d1 <= 0.5D) && (k >= 0 || d0 <= 0.5D) && (k <= 0 || d0 >= 0.5D) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
            } else {
                return DoorHingeSide.LEFT;
            }
        } else {
            return DoorHingeSide.RIGHT;
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!this.type.canOpenByHand()) {
            return InteractionResult.PASS;
        } else {
            state = (BlockState) state.cycle(DoorBlock.OPEN);
            world.setBlock(pos, state, 10);
            this.playSound(player, world, pos, (Boolean) state.getValue(DoorBlock.OPEN));
            world.gameEvent((Entity) player, this.isOpen(state) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    public boolean isOpen(BlockState state) {
        return (Boolean) state.getValue(DoorBlock.OPEN);
    }

    public void setOpen(@Nullable Entity entity, Level world, BlockState state, BlockPos pos, boolean open) {
        if (state.is((Block) this) && (Boolean) state.getValue(DoorBlock.OPEN) != open) {
            world.setBlock(pos, (BlockState) state.setValue(DoorBlock.OPEN, open), 10);
            this.playSound(entity, world, pos, open);
            world.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        // CraftBukkit start
        BlockPos otherHalf = pos.relative(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);

        org.bukkit.World bworld = world.getWorld();
        org.bukkit.block.Block bukkitBlock = bworld.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
        org.bukkit.block.Block blockTop = bworld.getBlockAt(otherHalf.getX(), otherHalf.getY(), otherHalf.getZ());

        int power = bukkitBlock.getBlockPower();
        int powerTop = blockTop.getBlockPower();
        if (powerTop > power) power = powerTop;
        int oldPower = (Boolean) state.getValue(DoorBlock.POWERED) ? 15 : 0;

        if (oldPower == 0 ^ power == 0) {
            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bukkitBlock, oldPower, power);
            world.getCraftServer().getPluginManager().callEvent(eventRedstone);

            boolean flag1 = eventRedstone.getNewCurrent() > 0;
            // CraftBukkit end
            if (flag1 != (Boolean) state.getValue(DoorBlock.OPEN)) {
                this.playSound((Entity) null, world, pos, flag1);
                world.gameEvent((Entity) null, flag1 ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            }

            world.setBlock(pos, (BlockState) ((BlockState) state.setValue(DoorBlock.POWERED, flag1)).setValue(DoorBlock.OPEN, flag1), 2);
        }

    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? iblockdata1.isFaceSturdy(world, blockposition1, Direction.UP) : iblockdata1.is((Block) this);
    }

    private void playSound(@Nullable Entity entity, Level world, BlockPos pos, boolean open) {
        world.playSound(entity, pos, open ? this.type.doorOpen() : this.type.doorClose(), SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(DoorBlock.FACING, rotation.rotate((Direction) state.getValue(DoorBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : (BlockState) state.rotate(mirror.getRotation((Direction) state.getValue(DoorBlock.FACING))).cycle(DoorBlock.HINGE);
    }

    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos.getX(), pos.below(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 0 : 1).getY(), pos.getZ());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DoorBlock.HALF, DoorBlock.FACING, DoorBlock.OPEN, DoorBlock.HINGE, DoorBlock.POWERED);
    }

    public static boolean isWoodenDoor(Level world, BlockPos pos) {
        return DoorBlock.isWoodenDoor(world.getBlockState(pos));
    }

    public static boolean isWoodenDoor(BlockState state) {
        Block block = state.getBlock();
        boolean flag;

        if (block instanceof DoorBlock) {
            DoorBlock blockdoor = (DoorBlock) block;

            if (blockdoor.type().canOpenByHand()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }
}
