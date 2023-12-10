package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class DoublePlantBlock extends BushBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public DoublePlantBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf blockpropertydoubleblockhalf = (DoubleBlockHalf) state.getValue(DoublePlantBlock.HALF);

        return direction.getAxis() == Direction.Axis.Y && blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER == (direction == Direction.UP) && (!neighborState.is((Block) this) || neighborState.getValue(DoublePlantBlock.HALF) == blockpropertydoubleblockhalf) ? Blocks.AIR.defaultBlockState() : (blockpropertydoubleblockhalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos blockposition = ctx.getClickedPos();
        Level world = ctx.getLevel();

        return blockposition.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(blockposition.above()).canBeReplaced(ctx) ? super.getStateForPlacement(ctx) : null;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        BlockPos blockposition1 = pos.above();

        world.setBlock(blockposition1, DoublePlantBlock.copyWaterloggedFrom(world, blockposition1, (BlockState) this.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        if (state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.UPPER) {
            return super.canSurvive(state, world, pos);
        } else {
            BlockState iblockdata1 = world.getBlockState(pos.below());

            return iblockdata1.is((Block) this) && iblockdata1.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
        }
    }

    public static void placeAt(LevelAccessor world, BlockState state, BlockPos pos, int flags) {
        BlockPos blockposition1 = pos.above();

        world.setBlock(pos, DoublePlantBlock.copyWaterloggedFrom(world, pos, (BlockState) state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER)), flags);
        world.setBlock(blockposition1, DoublePlantBlock.copyWaterloggedFrom(world, blockposition1, (BlockState) state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)), flags);
    }

    public static BlockState copyWaterloggedFrom(LevelReader world, BlockPos pos, BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState) state.setValue(BlockStateProperties.WATERLOGGED, world.isWaterAt(pos)) : state;
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide) {
            if (player.isCreative()) {
                DoublePlantBlock.preventCreativeDropFromBottomPart(world, pos, state, player);
            } else {
                dropResources(state, world, pos, (BlockEntity) null, player, player.getMainHandItem());
            }
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool, boolean includeDrops) { // Paper
        super.playerDestroy(world, player, pos, Blocks.AIR.defaultBlockState(), blockEntity, tool, includeDrops); // Paper
    }

    protected static void preventCreativeDropFromBottomPart(Level world, BlockPos pos, BlockState state, Player player) {
        // CraftBukkit start
        if (((net.minecraft.server.level.ServerLevel)world).getCurrentWorldData().hasPhysicsEvent && org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPhysicsEvent(world, pos).isCancelled()) { // Paper // Folia - region threading
            return;
        }
        // CraftBukkit end
        DoubleBlockHalf blockpropertydoubleblockhalf = (DoubleBlockHalf) state.getValue(DoublePlantBlock.HALF);

        if (blockpropertydoubleblockhalf == DoubleBlockHalf.UPPER) {
            BlockPos blockposition1 = pos.below();
            BlockState iblockdata1 = world.getBlockState(blockposition1);

            if (iblockdata1.is(state.getBlock()) && iblockdata1.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                BlockState iblockdata2 = iblockdata1.getFluidState().is((Fluid) Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();

                world.setBlock(blockposition1, iblockdata2, 35);
                world.levelEvent(player, 2001, blockposition1, Block.getId(iblockdata1));
            }
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DoublePlantBlock.HALF);
    }

    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos.getX(), pos.below(state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? 0 : 1).getY(), pos.getZ());
    }
}
