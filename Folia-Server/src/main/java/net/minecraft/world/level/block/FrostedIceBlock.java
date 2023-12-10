package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class FrostedIceBlock extends IceBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final int NEIGHBORS_TO_AGE = 4;
    private static final int NEIGHBORS_TO_MELT = 2;

    public FrostedIceBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.tick(state, world, pos, random);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!world.paperConfig().environment.frostedIce.enabled) return; // Paper - add ability to disable frosted ice
        if ((random.nextInt(3) == 0 || this.fewerNeigboursThan(world, pos, 4)) && world.getMaxLocalRawBrightness(pos) > 11 - state.getValue(AGE) - state.getLightBlock(world, pos) && this.slightlyMelt(state, world, pos)) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for(Direction direction : Direction.values()) {
                mutableBlockPos.setWithOffset(pos, direction);
                BlockState blockState = world.getBlockStateIfLoaded(mutableBlockPos); // Paper
                if (blockState == null) { continue; } // Paper
                if (blockState.is(this) && !this.slightlyMelt(blockState, world, mutableBlockPos)) {
                    world.scheduleTick(mutableBlockPos, this, Mth.nextInt(random, world.paperConfig().environment.frostedIce.delay.min, world.paperConfig().environment.frostedIce.delay.max)); // Paper - use configurable min/max delay
                }
            }

        } else {
            world.scheduleTick(pos, this, Mth.nextInt(random, world.paperConfig().environment.frostedIce.delay.min, world.paperConfig().environment.frostedIce.delay.max)); // Paper - use configurable min/max delay
        }
    }

    private boolean slightlyMelt(BlockState state, Level world, BlockPos pos) {
        int i = state.getValue(AGE);
        if (i < 3) {
            world.setBlock(pos, state.setValue(AGE, Integer.valueOf(i + 1)), 2);
            return false;
        } else {
            this.melt(state, world, pos);
            return true;
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (sourceBlock.defaultBlockState().is(this) && this.fewerNeigboursThan(world, pos, 2)) {
            this.melt(state, world, pos);
        }

        super.neighborChanged(state, world, pos, sourceBlock, sourcePos, notify);
    }

    private boolean fewerNeigboursThan(BlockGetter world, BlockPos pos, int maxNeighbors) {
        int i = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for(Direction direction : Direction.values()) {
            mutableBlockPos.setWithOffset(pos, direction);
            // Paper start
            BlockState blockState = world.getBlockStateIfLoaded(mutableBlockPos);
            if (blockState != null && blockState.is(this)) {
                // Paper end
                ++i;
                if (i >= maxNeighbors) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
