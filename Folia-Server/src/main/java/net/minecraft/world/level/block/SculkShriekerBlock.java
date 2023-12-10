package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SculkShriekerBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty SHRIEKING = BlockStateProperties.SHRIEKING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty CAN_SUMMON = BlockStateProperties.CAN_SUMMON;
    protected static final VoxelShape COLLIDER = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    public static final double TOP_Y = SculkShriekerBlock.COLLIDER.max(Direction.Axis.Y);

    public SculkShriekerBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(SculkShriekerBlock.SHRIEKING, false)).setValue(SculkShriekerBlock.WATERLOGGED, false)).setValue(SculkShriekerBlock.CAN_SUMMON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SculkShriekerBlock.SHRIEKING);
        builder.add(SculkShriekerBlock.WATERLOGGED);
        builder.add(SculkShriekerBlock.CAN_SUMMON);
    }

    @Override
    public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (world instanceof ServerLevel) {
            ServerLevel worldserver = (ServerLevel) world;
            ServerPlayer entityplayer = SculkShriekerBlockEntity.tryGetPlayer(entity);

            if (entityplayer != null) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(entityplayer, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null).isCancelled()) return; // CraftBukkit
                worldserver.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent((sculkshriekerblockentity) -> {
                    sculkshriekerblockentity.tryShriek(worldserver, entityplayer);
                });
            }
        }

        super.stepOn(world, pos, state, entity);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (world instanceof ServerLevel) {
            ServerLevel worldserver = (ServerLevel) world;

            if ((Boolean) state.getValue(SculkShriekerBlock.SHRIEKING) && !state.is(newState.getBlock())) {
                worldserver.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent((sculkshriekerblockentity) -> {
                    sculkshriekerblockentity.tryRespond(worldserver);
                });
            }
        }

        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(SculkShriekerBlock.SHRIEKING)) {
            world.setBlock(pos, (BlockState) state.setValue(SculkShriekerBlock.SHRIEKING, false), 3);
            world.getBlockEntity(pos, BlockEntityType.SCULK_SHRIEKER).ifPresent((sculkshriekerblockentity) -> {
                sculkshriekerblockentity.tryRespond(world);
            });
        }

    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SculkShriekerBlock.COLLIDER;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return SculkShriekerBlock.COLLIDER;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SculkShriekerBlockEntity(pos, state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(SculkShriekerBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(SculkShriekerBlock.WATERLOGGED, ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SculkShriekerBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, world, pos, tool, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, ItemStack itemstack, boolean flag) {
        if (flag) {
            return this.tryDropExperience(worldserver, blockposition, itemstack, ConstantInt.of(5));
        }

        return 0;
        // CraftBukkit end
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return !world.isClientSide ? BaseEntityBlock.createTickerHelper(type, BlockEntityType.SCULK_SHRIEKER, (world1, blockposition, iblockdata1, sculkshriekerblockentity) -> {
            VibrationSystem.Ticker.tick(world1, sculkshriekerblockentity.getVibrationData(), sculkshriekerblockentity.getVibrationUser());
        }) : null;
    }
}
