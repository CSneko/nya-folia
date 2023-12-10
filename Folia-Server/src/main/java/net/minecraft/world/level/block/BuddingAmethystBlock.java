package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class BuddingAmethystBlock extends AmethystBlock {

    public static final int GROWTH_CHANCE = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    public BuddingAmethystBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            Direction enumdirection = BuddingAmethystBlock.DIRECTIONS[random.nextInt(BuddingAmethystBlock.DIRECTIONS.length)];
            BlockPos blockposition1 = pos.relative(enumdirection);
            BlockState iblockdata1 = world.getBlockState(blockposition1);
            Block block = null;

            if (BuddingAmethystBlock.canClusterGrowAtState(iblockdata1)) {
                block = Blocks.SMALL_AMETHYST_BUD;
            } else if (iblockdata1.is(Blocks.SMALL_AMETHYST_BUD) && iblockdata1.getValue(AmethystClusterBlock.FACING) == enumdirection) {
                block = Blocks.MEDIUM_AMETHYST_BUD;
            } else if (iblockdata1.is(Blocks.MEDIUM_AMETHYST_BUD) && iblockdata1.getValue(AmethystClusterBlock.FACING) == enumdirection) {
                block = Blocks.LARGE_AMETHYST_BUD;
            } else if (iblockdata1.is(Blocks.LARGE_AMETHYST_BUD) && iblockdata1.getValue(AmethystClusterBlock.FACING) == enumdirection) {
                block = Blocks.AMETHYST_CLUSTER;
            }

            if (block != null) {
                BlockState iblockdata2 = (BlockState) ((BlockState) block.defaultBlockState().setValue(AmethystClusterBlock.FACING, enumdirection)).setValue(AmethystClusterBlock.WATERLOGGED, iblockdata1.getFluidState().getType() == Fluids.WATER);

                // Paper Start - Have Amethyst throw both spread and grow events
                if (block == Blocks.SMALL_AMETHYST_BUD) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition1, iblockdata2); // CraftBukkit
                } else {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(world, blockposition1, iblockdata2);
                }
                // Paper end
            }

        }
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
