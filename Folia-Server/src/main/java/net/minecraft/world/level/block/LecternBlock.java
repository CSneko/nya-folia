package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LecternBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty HAS_BOOK = BlockStateProperties.HAS_BOOK;
    public static final VoxelShape SHAPE_BASE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    public static final VoxelShape SHAPE_POST = Block.box(4.0D, 2.0D, 4.0D, 12.0D, 14.0D, 12.0D);
    public static final VoxelShape SHAPE_COMMON = Shapes.or(LecternBlock.SHAPE_BASE, LecternBlock.SHAPE_POST);
    public static final VoxelShape SHAPE_TOP_PLATE = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 15.0D, 16.0D);
    public static final VoxelShape SHAPE_COLLISION = Shapes.or(LecternBlock.SHAPE_COMMON, LecternBlock.SHAPE_TOP_PLATE);
    public static final VoxelShape SHAPE_WEST = Shapes.or(Block.box(1.0D, 10.0D, 0.0D, 5.333333D, 14.0D, 16.0D), Block.box(5.333333D, 12.0D, 0.0D, 9.666667D, 16.0D, 16.0D), Block.box(9.666667D, 14.0D, 0.0D, 14.0D, 18.0D, 16.0D), LecternBlock.SHAPE_COMMON);
    public static final VoxelShape SHAPE_NORTH = Shapes.or(Block.box(0.0D, 10.0D, 1.0D, 16.0D, 14.0D, 5.333333D), Block.box(0.0D, 12.0D, 5.333333D, 16.0D, 16.0D, 9.666667D), Block.box(0.0D, 14.0D, 9.666667D, 16.0D, 18.0D, 14.0D), LecternBlock.SHAPE_COMMON);
    public static final VoxelShape SHAPE_EAST = Shapes.or(Block.box(10.666667D, 10.0D, 0.0D, 15.0D, 14.0D, 16.0D), Block.box(6.333333D, 12.0D, 0.0D, 10.666667D, 16.0D, 16.0D), Block.box(2.0D, 14.0D, 0.0D, 6.333333D, 18.0D, 16.0D), LecternBlock.SHAPE_COMMON);
    public static final VoxelShape SHAPE_SOUTH = Shapes.or(Block.box(0.0D, 10.0D, 10.666667D, 16.0D, 14.0D, 15.0D), Block.box(0.0D, 12.0D, 6.333333D, 16.0D, 16.0D, 10.666667D), Block.box(0.0D, 14.0D, 2.0D, 16.0D, 18.0D, 6.333333D), LecternBlock.SHAPE_COMMON);
    private static final int PAGE_CHANGE_IMPULSE_TICKS = 2;

    protected LecternBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LecternBlock.FACING, Direction.NORTH)).setValue(LecternBlock.POWERED, false)).setValue(LecternBlock.HAS_BOOK, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return LecternBlock.SHAPE_COMMON;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        ItemStack itemstack = ctx.getItemInHand();
        Player entityhuman = ctx.getPlayer();
        boolean flag = false;

        if (!world.isClientSide && entityhuman != null && entityhuman.canUseGameMasterBlocks()) {
            CompoundTag nbttagcompound = BlockItem.getBlockEntityData(itemstack);

            if (nbttagcompound != null && nbttagcompound.contains("Book")) {
                flag = true;
            }
        }

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(LecternBlock.FACING, ctx.getHorizontalDirection().getOpposite())).setValue(LecternBlock.HAS_BOOK, flag);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return LecternBlock.SHAPE_COLLISION;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        switch ((Direction) state.getValue(LecternBlock.FACING)) {
            case NORTH:
                return LecternBlock.SHAPE_NORTH;
            case SOUTH:
                return LecternBlock.SHAPE_SOUTH;
            case EAST:
                return LecternBlock.SHAPE_EAST;
            case WEST:
                return LecternBlock.SHAPE_WEST;
            default:
                return LecternBlock.SHAPE_COMMON;
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(LecternBlock.FACING, rotation.rotate((Direction) state.getValue(LecternBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(LecternBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LecternBlock.FACING, LecternBlock.POWERED, LecternBlock.HAS_BOOK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LecternBlockEntity(pos, state);
    }

    public static boolean tryPlaceBook(@Nullable Entity user, Level world, BlockPos pos, BlockState state, ItemStack stack) {
        if (!(Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
            if (!world.isClientSide) {
                LecternBlock.placeBook(user, world, pos, state, stack);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void placeBook(@Nullable Entity user, Level world, BlockPos pos, BlockState state, ItemStack stack) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof LecternBlockEntity) {
            LecternBlockEntity tileentitylectern = (LecternBlockEntity) tileentity;

            tileentitylectern.setBook(stack.split(1));
            LecternBlock.resetBookState(user, world, pos, state, true);
            world.playSound((Player) null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    public static void resetBookState(@Nullable Entity user, Level world, BlockPos pos, BlockState state, boolean hasBook) {
        BlockState iblockdata1 = (BlockState) ((BlockState) state.setValue(LecternBlock.POWERED, false)).setValue(LecternBlock.HAS_BOOK, hasBook);

        world.setBlock(pos, iblockdata1, 3);
        world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(user, iblockdata1));
        LecternBlock.updateBelow(world, pos, state);
    }

    public static void signalPageChange(Level world, BlockPos pos, BlockState state) {
        LecternBlock.changePowered(world, pos, state, true);
        world.scheduleTick(pos, state.getBlock(), 2);
        world.levelEvent(1043, pos, 0);
    }

    private static void changePowered(Level world, BlockPos pos, BlockState state, boolean powered) {
        // Paper start - call BlockRedstoneEvents for lecterns
        final int currentRedstoneLevel = state.getValue(LecternBlock.POWERED) ? 15 : 0, targetRedstoneLevel = powered ? 15 : 0;
        if (currentRedstoneLevel != targetRedstoneLevel) {
            final org.bukkit.event.block.BlockRedstoneEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, pos, currentRedstoneLevel, targetRedstoneLevel);

            if (event.getNewCurrent() != targetRedstoneLevel) {
                return;
            }
        }
        // Paper end
        world.setBlock(pos, (BlockState) state.setValue(LecternBlock.POWERED, powered), 3);
        LecternBlock.updateBelow(world, pos, state);
    }

    private static void updateBelow(Level world, BlockPos pos, BlockState state) {
        world.updateNeighborsAt(pos.below(), state.getBlock());
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        LecternBlock.changePowered(world, pos, state, false);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if ((Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
                this.popBook(state, world, pos);
            }

            if ((Boolean) state.getValue(LecternBlock.POWERED)) {
                world.updateNeighborsAt(pos.below(), this);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    private void popBook(BlockState state, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos, false); // CraftBukkit - don't validate, type may be changed already

        if (tileentity instanceof LecternBlockEntity) {
            LecternBlockEntity tileentitylectern = (LecternBlockEntity) tileentity;
            Direction enumdirection = (Direction) state.getValue(LecternBlock.FACING);
            ItemStack itemstack = tileentitylectern.getBook().copy();
            if (itemstack.isEmpty()) return; // CraftBukkit - SPIGOT-5500
            float f = 0.25F * (float) enumdirection.getStepX();
            float f1 = 0.25F * (float) enumdirection.getStepZ();
            ItemEntity entityitem = new ItemEntity(world, (double) pos.getX() + 0.5D + (double) f, (double) (pos.getY() + 1), (double) pos.getZ() + 0.5D + (double) f1, itemstack);

            entityitem.setDefaultPickUpDelay();
            world.addFreshEntity(entityitem);
            tileentitylectern.clearContent();
        }

    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LecternBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return direction == Direction.UP && (Boolean) state.getValue(LecternBlock.POWERED) ? 15 : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        if ((Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof LecternBlockEntity) {
                return ((LecternBlockEntity) tileentity).getRedstoneSignal();
            }
        }

        return 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if ((Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
            if (!world.isClientSide) {
                this.openScreen(world, pos, player);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            ItemStack itemstack = player.getItemInHand(hand);

            return !itemstack.isEmpty() && !itemstack.is(ItemTags.LECTERN_BOOKS) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        return !(Boolean) state.getValue(LecternBlock.HAS_BOOK) ? null : super.getMenuProvider(state, world, pos);
    }

    private void openScreen(Level world, BlockPos pos, Player player) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof LecternBlockEntity) {
            player.openMenu((LecternBlockEntity) tileentity);
            player.awardStat(Stats.INTERACT_WITH_LECTERN);
        }

    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }
}
