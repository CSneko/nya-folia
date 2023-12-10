package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ChangeOverTimeBlock<T extends Enum<T>> {

    int SCAN_DISTANCE = 4;

    Optional<BlockState> getNext(BlockState state);

    float getChanceModifier();

    default void onRandomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        float f = 0.05688889F;

        if (random.nextFloat() < 0.05688889F) {
            this.applyChangeOverTime(state, world, pos, random);
        }

    }

    T getAge();

    default void applyChangeOverTime(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        int i = this.getAge().ordinal();
        int j = 0;
        int k = 0;
        Iterator iterator = BlockPos.withinManhattan(pos, 4, 4, 4).iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();
            int l = blockposition1.distManhattan(pos);

            if (l > 4) {
                break;
            }

            if (!blockposition1.equals(pos)) {
                BlockState iblockdata1 = world.getBlockState(blockposition1);
                Block block = iblockdata1.getBlock();

                if (block instanceof ChangeOverTimeBlock) {
                    Enum<?> oenum = ((ChangeOverTimeBlock) block).getAge();

                    if (this.getAge().getClass() == oenum.getClass()) {
                        int i1 = oenum.ordinal();

                        if (i1 < i) {
                            return;
                        }

                        if (i1 > i) {
                            ++k;
                        } else {
                            ++j;
                        }
                    }
                }
            }
        }

        float f = (float) (k + 1) / (float) (k + j + 1);
        float f1 = f * f * this.getChanceModifier();

        if (random.nextFloat() < f1) {
            this.getNext(state).ifPresent((iblockdata2) -> {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, pos, iblockdata2); // CraftBukkit
            });
        }

    }
}
