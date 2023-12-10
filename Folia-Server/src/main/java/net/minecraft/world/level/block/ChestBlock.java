package net.minecraft.world.level.block;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChestBlock extends AbstractChestBlock<ChestBlockEntity> implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int EVENT_SET_OPEN_COUNT = 1;
    protected static final int AABB_OFFSET = 1;
    protected static final int AABB_HEIGHT = 14;
    protected static final VoxelShape NORTH_AABB = Block.box(1.0D, 0.0D, 0.0D, 15.0D, 14.0D, 15.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(0.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    protected static final VoxelShape EAST_AABB = Block.box(1.0D, 0.0D, 1.0D, 16.0D, 14.0D, 15.0D);
    protected static final VoxelShape AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>> CHEST_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>>() {
        public Optional<Container> acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
            return Optional.of(new CompoundContainer(first, second));
        }

        public Optional<Container> acceptSingle(ChestBlockEntity single) {
            return Optional.of(single);
        }

        @Override
        public Optional<Container> acceptNone() {
            return Optional.empty();
        }
    };
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDER_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>>() {
        public Optional<MenuProvider> acceptDouble(final ChestBlockEntity first, final ChestBlockEntity second) {
            final CompoundContainer inventorylargechest = new CompoundContainer(first, second);

            return Optional.of(new DoubleInventory(first, second, inventorylargechest)); // CraftBukkit
        }

        public Optional<MenuProvider> acceptSingle(ChestBlockEntity single) {
            return Optional.of(single);
        }

        @Override
        public Optional<MenuProvider> acceptNone() {
            return Optional.empty();
        }
    };

    // CraftBukkit start
    public static class DoubleInventory implements MenuProvider {

        private final ChestBlockEntity tileentitychest;
        private final ChestBlockEntity tileentitychest1;
        public final CompoundContainer inventorylargechest;

        public DoubleInventory(ChestBlockEntity tileentitychest, ChestBlockEntity tileentitychest1, CompoundContainer inventorylargechest) {
            this.tileentitychest = tileentitychest;
            this.tileentitychest1 = tileentitychest1;
            this.inventorylargechest = inventorylargechest;
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
            if (this.tileentitychest.canOpen(player) && this.tileentitychest1.canOpen(player)) {
                this.tileentitychest.unpackLootTable(playerInventory.player);
                this.tileentitychest1.unpackLootTable(playerInventory.player);
                return ChestMenu.sixRows(syncId, playerInventory, this.inventorylargechest);
            } else {
                return null;
            }
        }

        @Override
        public Component getDisplayName() {
            return (Component) (this.tileentitychest.hasCustomName() ? this.tileentitychest.getDisplayName() : (this.tileentitychest1.hasCustomName() ? this.tileentitychest1.getDisplayName() : Component.translatable("container.chestDouble")));
        }
    };
    // CraftBukkit end

    protected ChestBlock(BlockBehaviour.Properties settings, Supplier<BlockEntityType<? extends ChestBlockEntity>> entityTypeSupplier) {
        super(settings, entityTypeSupplier);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ChestBlock.FACING, Direction.NORTH)).setValue(ChestBlock.TYPE, ChestType.SINGLE)).setValue(ChestBlock.WATERLOGGED, false));
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        ChestType blockpropertychesttype = (ChestType) state.getValue(ChestBlock.TYPE);

        return blockpropertychesttype == ChestType.SINGLE ? DoubleBlockCombiner.BlockType.SINGLE : (blockpropertychesttype == ChestType.RIGHT ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(ChestBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        if (neighborState.is((Block) this) && direction.getAxis().isHorizontal()) {
            ChestType blockpropertychesttype = (ChestType) neighborState.getValue(ChestBlock.TYPE);

            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE && blockpropertychesttype != ChestType.SINGLE && state.getValue(ChestBlock.FACING) == neighborState.getValue(ChestBlock.FACING) && ChestBlock.getConnectedDirection(neighborState) == direction.getOpposite()) {
                return (BlockState) state.setValue(ChestBlock.TYPE, blockpropertychesttype.getOpposite());
            }
        } else if (ChestBlock.getConnectedDirection(state) == direction) {
            return (BlockState) state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return ChestBlock.AABB;
        } else {
            switch (ChestBlock.getConnectedDirection(state)) {
                case NORTH:
                default:
                    return ChestBlock.NORTH_AABB;
                case SOUTH:
                    return ChestBlock.SOUTH_AABB;
                case WEST:
                    return ChestBlock.WEST_AABB;
                case EAST:
                    return ChestBlock.EAST_AABB;
            }
        }
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction enumdirection = (Direction) state.getValue(ChestBlock.FACING);

        return state.getValue(ChestBlock.TYPE) == ChestType.LEFT ? enumdirection.getClockWise() : enumdirection.getCounterClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        ChestType blockpropertychesttype = ChestType.SINGLE;
        Direction enumdirection = ctx.getHorizontalDirection().getOpposite();
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        boolean flag = ctx.isSecondaryUseActive();
        Direction enumdirection1 = ctx.getClickedFace();

        if (enumdirection1.getAxis().isHorizontal() && flag) {
            Direction enumdirection2 = this.candidatePartnerFacing(ctx, enumdirection1.getOpposite());

            if (enumdirection2 != null && enumdirection2.getAxis() != enumdirection1.getAxis()) {
                enumdirection = enumdirection2;
                blockpropertychesttype = enumdirection2.getCounterClockWise() == enumdirection1.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }

        if (blockpropertychesttype == ChestType.SINGLE && !flag) {
            if (enumdirection == this.candidatePartnerFacing(ctx, enumdirection.getClockWise())) {
                blockpropertychesttype = ChestType.LEFT;
            } else if (enumdirection == this.candidatePartnerFacing(ctx, enumdirection.getCounterClockWise())) {
                blockpropertychesttype = ChestType.RIGHT;
            }
        }

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(ChestBlock.FACING, enumdirection)).setValue(ChestBlock.TYPE, blockpropertychesttype)).setValue(ChestBlock.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(ChestBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    private Direction candidatePartnerFacing(BlockPlaceContext ctx, Direction dir) {
        BlockState iblockdata = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(dir));

        return iblockdata.is((Block) this) && iblockdata.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? (Direction) iblockdata.getValue(ChestBlock.FACING) : null;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (itemStack.hasCustomHoverName()) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof ChestBlockEntity) {
                ((ChestBlockEntity) tileentity).setCustomName(itemStack.getHoverName());
            }
        }

    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof Container) {
                Containers.dropContents(world, pos, (Container) tileentity);
                world.updateNeighbourForOutputSignal(pos, this);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            MenuProvider itileinventory = this.getMenuProvider(state, world, pos);

            if (itileinventory != null) {
                player.openMenu(itileinventory);
                player.awardStat(this.getOpenChestStat());
                PiglinAi.angerNearbyPiglins(player, true);
            }

            return InteractionResult.CONSUME;
        }
    }

    protected Stat<ResourceLocation> getOpenChestStat() {
        return Stats.CUSTOM.get(Stats.OPEN_CHEST);
    }

    public BlockEntityType<? extends ChestBlockEntity> blockEntityType() {
        return (BlockEntityType) this.blockEntityType.get();
    }

    @Nullable
    public static Container getContainer(ChestBlock block, BlockState state, Level world, BlockPos pos, boolean ignoreBlocked) {
        return (Container) ((Optional) block.combine(state, world, pos, ignoreBlocked).apply(ChestBlock.CHEST_COMBINER)).orElse((Object) null);
    }

    @Override
    public DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combine(BlockState state, Level world, BlockPos pos, boolean ignoreBlocked) {
        BiPredicate<LevelAccessor, BlockPos> bipredicate; // CraftBukkit - decompile error

        if (ignoreBlocked) {
            bipredicate = (generatoraccess, blockposition1) -> {
                return false;
            };
        } else {
            bipredicate = ChestBlock::isChestBlockedAt;
        }

        return DoubleBlockCombiner.combineWithNeigbour((BlockEntityType) this.blockEntityType.get(), ChestBlock::getBlockType, ChestBlock::getConnectedDirection, ChestBlock.FACING, state, world, pos, bipredicate);
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        return this.getMenuProvider(state, world, pos, false);
    }

    @Nullable
    public MenuProvider getMenuProvider(BlockState iblockdata, Level world, BlockPos blockposition, boolean ignoreObstructions) {
        return (MenuProvider) ((Optional) this.combine(iblockdata, world, blockposition, ignoreObstructions).apply(ChestBlock.MENU_PROVIDER_COMBINER)).orElse((Object) null);
        // CraftBukkit end
    }

    public static DoubleBlockCombiner.Combiner<ChestBlockEntity, Float2FloatFunction> opennessCombiner(final LidBlockEntity progress) {
        return new DoubleBlockCombiner.Combiner<ChestBlockEntity, Float2FloatFunction>() {
            public Float2FloatFunction acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
                return (f) -> {
                    return Math.max(first.getOpenNess(f), second.getOpenNess(f));
                };
            }

            public Float2FloatFunction acceptSingle(ChestBlockEntity single) {
                Objects.requireNonNull(single);
                return single::getOpenNess;
            }

            @Override
            public Float2FloatFunction acceptNone() {
                LidBlockEntity lidblockentity1 = progress;

                Objects.requireNonNull(progress);
                return lidblockentity1::getOpenNess;
            }
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? createTickerHelper(type, this.blockEntityType(), ChestBlockEntity::lidAnimateTick) : null;
    }

    public static boolean isChestBlockedAt(LevelAccessor world, BlockPos pos) {
        return ChestBlock.isBlockedChestByBlock(world, pos) || ChestBlock.isCatSittingOnChest(world, pos);
    }

    private static boolean isBlockedChestByBlock(BlockGetter world, BlockPos pos) {
        BlockPos blockposition1 = pos.above();

        return world.getBlockState(blockposition1).isRedstoneConductor(world, blockposition1);
    }

    private static boolean isCatSittingOnChest(LevelAccessor world, BlockPos pos) {
        // Paper start - Option to disable chest cat detection
        if (world.getMinecraftWorld().paperConfig().entities.behavior.disableChestCatDetection) {
            return false;
        }
        // Paper end
        List<Cat> list = world.getEntitiesOfClass(Cat.class, new AABB((double) pos.getX(), (double) (pos.getY() + 1), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 2), (double) (pos.getZ() + 1)));

        if (!list.isEmpty()) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Cat entitycat = (Cat) iterator.next();

                if (entitycat.isInSittingPose()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(ChestBlock.getContainer(this, state, world, pos, false));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ChestBlock.FACING, rotation.rotate((Direction) state.getValue(ChestBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ChestBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ChestBlock.FACING, ChestBlock.TYPE, ChestBlock.WATERLOGGED);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof ChestBlockEntity) {
            ((ChestBlockEntity) tileentity).recheckOpen();
        }

    }
}
