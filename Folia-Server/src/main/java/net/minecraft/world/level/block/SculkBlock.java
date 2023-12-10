package net.minecraft.world.level.block;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class SculkBlock extends DropExperienceBlock implements SculkBehaviour {

    public SculkBlock(BlockBehaviour.Properties settings) {
        super(settings, ConstantInt.of(1));
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor world, BlockPos catalystPos, RandomSource random, SculkSpreader spreadManager, boolean shouldConvertToBlock) {
        int i = cursor.getCharge();

        if (i != 0 && random.nextInt(spreadManager.chargeDecayRate()) == 0) {
            BlockPos blockposition1 = cursor.getPos();
            boolean flag1 = blockposition1.closerThan(catalystPos, (double) spreadManager.noGrowthRadius());

            if (!flag1 && SculkBlock.canPlaceGrowth(world, blockposition1)) {
                int j = spreadManager.growthSpawnCost();

                if (random.nextInt(j) < i) {
                    BlockPos blockposition2 = blockposition1.above();
                    BlockState iblockdata = this.getRandomGrowthState(world, blockposition2, random, spreadManager.isWorldGeneration());

                    // CraftBukkit start - Call BlockSpreadEvent
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, catalystPos, blockposition2, iblockdata, 3)) {
                        world.playSound((Player) null, blockposition1, iblockdata.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    // CraftBukkit end
                }

                return Math.max(0, i - j);
            } else {
                return random.nextInt(spreadManager.additionalDecayRate()) != 0 ? i : i - (flag1 ? 1 : SculkBlock.getDecayPenalty(spreadManager, blockposition1, catalystPos, i));
            }
        } else {
            return i;
        }
    }

    private static int getDecayPenalty(SculkSpreader spreadManager, BlockPos cursorPos, BlockPos catalystPos, int charge) {
        int j = spreadManager.noGrowthRadius();
        float f = Mth.square((float) Math.sqrt(cursorPos.distSqr(catalystPos)) - (float) j);
        int k = Mth.square(24 - j);
        float f1 = Math.min(1.0F, f / (float) k);

        return Math.max(1, (int) ((float) charge * f1 * 0.5F));
    }

    private BlockState getRandomGrowthState(LevelAccessor world, BlockPos pos, RandomSource random, boolean allowShrieker) {
        BlockState iblockdata;

        if (random.nextInt(11) == 0) {
            iblockdata = (BlockState) Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, allowShrieker);
        } else {
            iblockdata = Blocks.SCULK_SENSOR.defaultBlockState();
        }

        return iblockdata.hasProperty(BlockStateProperties.WATERLOGGED) && !world.getFluidState(pos).isEmpty() ? (BlockState) iblockdata.setValue(BlockStateProperties.WATERLOGGED, true) : iblockdata;
    }

    private static boolean canPlaceGrowth(LevelAccessor world, BlockPos pos) {
        BlockState iblockdata = world.getBlockState(pos.above());

        if (!iblockdata.isAir() && (!iblockdata.is(Blocks.WATER) || !iblockdata.getFluidState().is((Fluid) Fluids.WATER))) {
            return false;
        } else {
            int i = 0;
            Iterator iterator = BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4)).iterator();

            do {
                if (!iterator.hasNext()) {
                    return true;
                }

                BlockPos blockposition1 = (BlockPos) iterator.next();
                BlockState iblockdata1 = world.getBlockState(blockposition1);

                if (iblockdata1.is(Blocks.SCULK_SENSOR) || iblockdata1.is(Blocks.SCULK_SHRIEKER)) {
                    ++i;
                }
            } while (i <= 2);

            return false;
        }
    }

    @Override
    public boolean canChangeBlockStateOnSpread() {
        return false;
    }
}
