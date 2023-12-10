package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantHeadBlock extends GrowingPlantBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    public static final int MAX_AGE = 25;
    private final double growPerTickProbability;

    protected GrowingPlantHeadBlock(BlockBehaviour.Properties settings, Direction growthDirection, VoxelShape outlineShape, boolean tickWater, double growthChance) {
        super(settings, growthDirection, outlineShape, tickWater);
        this.growPerTickProbability = growthChance;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(GrowingPlantHeadBlock.AGE, 0));
    }

    @Override
    public BlockState getStateForPlacement(LevelAccessor world) {
        return (BlockState) this.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, world.getRandom().nextInt(25));
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(GrowingPlantHeadBlock.AGE) < 25;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        // Spigot start
        int modifier;
        if (this == Blocks.KELP) {
            modifier = world.spigotConfig.kelpModifier;
        } else if (this == Blocks.TWISTING_VINES) {
            modifier = world.spigotConfig.twistingVinesModifier;
        } else if (this == Blocks.WEEPING_VINES) {
            modifier = world.spigotConfig.weepingVinesModifier;
        } else {
            modifier = world.spigotConfig.caveVinesModifier;
        }
        if ((Integer) state.getValue(GrowingPlantHeadBlock.AGE) < 25 && random.nextDouble() < ((modifier / 100.0D) * this.growPerTickProbability)) { // Spigot - SPIGOT-7159: Better modifier resolution
            // Spigot end
            BlockPos blockposition1 = pos.relative(this.growthDirection);

            if (this.canGrowInto(world.getBlockState(blockposition1))) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition1, this.getGrowIntoState(state, world.random, world)); // CraftBukkit // Paper
            }
        }

    }

    // Paper start
    protected BlockState getGrowIntoState(BlockState state, RandomSource random, @javax.annotation.Nullable Level level) {
        return this.getGrowIntoState(state, random);
    }
    // Paper end

    protected BlockState getGrowIntoState(BlockState state, RandomSource random) {
        return (BlockState) state.cycle(GrowingPlantHeadBlock.AGE);
    }

    public BlockState getMaxAgeState(BlockState state) {
        return (BlockState) state.setValue(GrowingPlantHeadBlock.AGE, 25);
    }

    public boolean isMaxAge(BlockState state) {
        return (Integer) state.getValue(GrowingPlantHeadBlock.AGE) == 25;
    }

    protected BlockState updateBodyAfterConvertedFromHead(BlockState from, BlockState to) {
        return to;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == this.growthDirection.getOpposite() && !state.canSurvive(world, pos)) {
            world.scheduleTick(pos, (Block) this, 1);
        }

        if (direction == this.growthDirection && (neighborState.is((Block) this) || neighborState.is(this.getBodyBlock()))) {
            return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
        } else {
            if (this.scheduleFluidTicks) {
                world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
            }

            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GrowingPlantHeadBlock.AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return this.canGrowInto(world.getBlockState(pos.relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockposition1 = pos.relative(this.growthDirection);
        int i = Math.min((Integer) state.getValue(GrowingPlantHeadBlock.AGE) + 1, 25);
        int j = this.getBlocksToGrowWhenBonemealed(random);

        for (int k = 0; k < j && this.canGrowInto(world.getBlockState(blockposition1)); ++k) {
            world.setBlockAndUpdate(blockposition1, (BlockState) state.setValue(GrowingPlantHeadBlock.AGE, i));
            blockposition1 = blockposition1.relative(this.growthDirection);
            i = Math.min(i + 1, 25);
        }

    }

    protected abstract int getBlocksToGrowWhenBonemealed(RandomSource random);

    protected abstract boolean canGrowInto(BlockState state);

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return this;
    }
}
