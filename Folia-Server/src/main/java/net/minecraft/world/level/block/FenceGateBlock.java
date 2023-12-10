package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FenceGateBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
    protected static final VoxelShape Z_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape X_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);
    protected static final VoxelShape Z_SHAPE_LOW = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 13.0D, 10.0D);
    protected static final VoxelShape X_SHAPE_LOW = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 13.0D, 16.0D);
    protected static final VoxelShape Z_COLLISION_SHAPE = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 24.0D, 10.0D);
    protected static final VoxelShape X_COLLISION_SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 24.0D, 16.0D);
    protected static final VoxelShape Z_SUPPORT_SHAPE = Block.box(0.0D, 5.0D, 6.0D, 16.0D, 24.0D, 10.0D);
    protected static final VoxelShape X_SUPPORT_SHAPE = Block.box(6.0D, 5.0D, 0.0D, 10.0D, 24.0D, 16.0D);
    protected static final VoxelShape Z_OCCLUSION_SHAPE = Shapes.or(Block.box(0.0D, 5.0D, 7.0D, 2.0D, 16.0D, 9.0D), Block.box(14.0D, 5.0D, 7.0D, 16.0D, 16.0D, 9.0D));
    protected static final VoxelShape X_OCCLUSION_SHAPE = Shapes.or(Block.box(7.0D, 5.0D, 0.0D, 9.0D, 16.0D, 2.0D), Block.box(7.0D, 5.0D, 14.0D, 9.0D, 16.0D, 16.0D));
    protected static final VoxelShape Z_OCCLUSION_SHAPE_LOW = Shapes.or(Block.box(0.0D, 2.0D, 7.0D, 2.0D, 13.0D, 9.0D), Block.box(14.0D, 2.0D, 7.0D, 16.0D, 13.0D, 9.0D));
    protected static final VoxelShape X_OCCLUSION_SHAPE_LOW = Shapes.or(Block.box(7.0D, 2.0D, 0.0D, 9.0D, 13.0D, 2.0D), Block.box(7.0D, 2.0D, 14.0D, 9.0D, 13.0D, 16.0D));
    private final WoodType type;

    public FenceGateBlock(BlockBehaviour.Properties settings, WoodType type) {
        super(settings.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(FenceGateBlock.OPEN, false)).setValue(FenceGateBlock.POWERED, false)).setValue(FenceGateBlock.IN_WALL, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(FenceGateBlock.IN_WALL) ? (((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_SHAPE_LOW : FenceGateBlock.Z_SHAPE_LOW) : (((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_SHAPE : FenceGateBlock.Z_SHAPE);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis enumdirection_enumaxis = direction.getAxis();

        if (((Direction) state.getValue(FenceGateBlock.FACING)).getClockWise().getAxis() != enumdirection_enumaxis) {
            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        } else {
            boolean flag = this.isWall(neighborState) || this.isWall(world.getBlockState(pos.relative(direction.getOpposite())));

            return (BlockState) state.setValue(FenceGateBlock.IN_WALL, flag);
        }
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return (Boolean) state.getValue(FenceGateBlock.OPEN) ? Shapes.empty() : (((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.Z ? FenceGateBlock.Z_SUPPORT_SHAPE : FenceGateBlock.X_SUPPORT_SHAPE);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(FenceGateBlock.OPEN) ? Shapes.empty() : (((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.Z ? FenceGateBlock.Z_COLLISION_SHAPE : FenceGateBlock.X_COLLISION_SHAPE);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return (Boolean) state.getValue(FenceGateBlock.IN_WALL) ? (((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_OCCLUSION_SHAPE_LOW : FenceGateBlock.Z_OCCLUSION_SHAPE_LOW) : (((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == Direction.Axis.X ? FenceGateBlock.X_OCCLUSION_SHAPE : FenceGateBlock.Z_OCCLUSION_SHAPE);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        switch (type) {
            case LAND:
                return (Boolean) state.getValue(FenceGateBlock.OPEN);
            case WATER:
                return false;
            case AIR:
                return (Boolean) state.getValue(FenceGateBlock.OPEN);
            default:
                return false;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        boolean flag = world.hasNeighborSignal(blockposition);
        Direction enumdirection = ctx.getHorizontalDirection();
        Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
        boolean flag1 = enumdirection_enumaxis == Direction.Axis.Z && (this.isWall(world.getBlockState(blockposition.west())) || this.isWall(world.getBlockState(blockposition.east()))) || enumdirection_enumaxis == Direction.Axis.X && (this.isWall(world.getBlockState(blockposition.north())) || this.isWall(world.getBlockState(blockposition.south())));

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(FenceGateBlock.FACING, enumdirection)).setValue(FenceGateBlock.OPEN, flag)).setValue(FenceGateBlock.POWERED, flag)).setValue(FenceGateBlock.IN_WALL, flag1);
    }

    private boolean isWall(BlockState state) {
        return state.is(BlockTags.WALLS);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if ((Boolean) state.getValue(FenceGateBlock.OPEN)) {
            state = (BlockState) state.setValue(FenceGateBlock.OPEN, false);
            world.setBlock(pos, state, 10);
        } else {
            Direction enumdirection = player.getDirection();

            if (state.getValue(FenceGateBlock.FACING) == enumdirection.getOpposite()) {
                state = (BlockState) state.setValue(FenceGateBlock.FACING, enumdirection);
            }

            state = (BlockState) state.setValue(FenceGateBlock.OPEN, true);
            world.setBlock(pos, state, 10);
        }

        boolean flag = (Boolean) state.getValue(FenceGateBlock.OPEN);

        world.playSound(player, pos, flag ? this.type.fenceGateOpen() : this.type.fenceGateClose(), SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
        world.gameEvent((Entity) player, flag ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide) {
            boolean flag1 = world.hasNeighborSignal(pos);
            // CraftBukkit start
            boolean oldPowered = state.getValue(FenceGateBlock.POWERED);
            if (oldPowered != flag1) {
                int newPower = flag1 ? 15 : 0;
                int oldPower = oldPowered ? 15 : 0;
                org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(world, pos);
                org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(bukkitBlock, oldPower, newPower);
                world.getCraftServer().getPluginManager().callEvent(eventRedstone);
                flag1 = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end

            if ((Boolean) state.getValue(FenceGateBlock.POWERED) != flag1) {
                world.setBlock(pos, (BlockState) ((BlockState) state.setValue(FenceGateBlock.POWERED, flag1)).setValue(FenceGateBlock.OPEN, flag1), 2);
                if ((Boolean) state.getValue(FenceGateBlock.OPEN) != flag1) {
                    world.playSound((Player) null, pos, flag1 ? this.type.fenceGateOpen() : this.type.fenceGateClose(), SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
                    world.gameEvent((Entity) null, flag1 ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
                }
            }

        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FenceGateBlock.FACING, FenceGateBlock.OPEN, FenceGateBlock.POWERED, FenceGateBlock.IN_WALL);
    }

    public static boolean connectsToDirection(BlockState state, Direction side) {
        return ((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == side.getClockWise().getAxis();
    }
}
