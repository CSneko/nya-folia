package net.minecraft.world.level.block;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class DoubleBlockCombiner {
    public static <S extends BlockEntity> DoubleBlockCombiner.NeighborCombineResult<S> combineWithNeigbour(BlockEntityType<S> blockEntityType, Function<BlockState, DoubleBlockCombiner.BlockType> typeMapper, Function<BlockState, Direction> function, DirectionProperty directionProperty, BlockState state, LevelAccessor world, BlockPos pos, BiPredicate<LevelAccessor, BlockPos> fallbackTester) {
        S blockEntity = blockEntityType.getBlockEntity(world, pos);
        if (blockEntity == null) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else if (fallbackTester.test(world, pos)) {
            return DoubleBlockCombiner.Combiner::acceptNone;
        } else {
            DoubleBlockCombiner.BlockType blockType = typeMapper.apply(state);
            boolean bl = blockType == DoubleBlockCombiner.BlockType.SINGLE;
            boolean bl2 = blockType == DoubleBlockCombiner.BlockType.FIRST;
            if (bl) {
                return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
            } else {
                BlockPos blockPos = pos.relative(function.apply(state));
                // Paper start
                BlockState blockState = world.getBlockStateIfLoaded(blockPos);
                if (blockState == null) {
                    return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
                }
                // Paper end
                if (blockState.is(state.getBlock())) {
                    DoubleBlockCombiner.BlockType blockType2 = typeMapper.apply(blockState);
                    if (blockType2 != DoubleBlockCombiner.BlockType.SINGLE && blockType != blockType2 && blockState.getValue(directionProperty) == state.getValue(directionProperty)) {
                        if (fallbackTester.test(world, blockPos)) {
                            return DoubleBlockCombiner.Combiner::acceptNone;
                        }

                        S blockEntity2 = blockEntityType.getBlockEntity(world, blockPos);
                        if (blockEntity2 != null) {
                            S blockEntity3 = bl2 ? blockEntity : blockEntity2;
                            S blockEntity4 = bl2 ? blockEntity2 : blockEntity;
                            return new DoubleBlockCombiner.NeighborCombineResult.Double<>(blockEntity3, blockEntity4);
                        }
                    }
                }

                return new DoubleBlockCombiner.NeighborCombineResult.Single<>(blockEntity);
            }
        }
    }

    public static enum BlockType {
        SINGLE,
        FIRST,
        SECOND;
    }

    public interface Combiner<S, T> {
        T acceptDouble(S first, S second);

        T acceptSingle(S single);

        T acceptNone();
    }

    public interface NeighborCombineResult<S> {
        <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> retriever);

        public static final class Double<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S first;
            private final S second;

            public Double(S first, S second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> retriever) {
                return retriever.acceptDouble(this.first, this.second);
            }
        }

        public static final class Single<S> implements DoubleBlockCombiner.NeighborCombineResult<S> {
            private final S single;

            public Single(S single) {
                this.single = single;
            }

            @Override
            public <T> T apply(DoubleBlockCombiner.Combiner<? super S, T> retriever) {
                return retriever.acceptSingle(this.single);
            }
        }
    }
}
