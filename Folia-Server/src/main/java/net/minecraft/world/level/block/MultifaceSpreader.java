package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class MultifaceSpreader {

    public static final MultifaceSpreader.SpreadType[] DEFAULT_SPREAD_ORDER = new MultifaceSpreader.SpreadType[]{MultifaceSpreader.SpreadType.SAME_POSITION, MultifaceSpreader.SpreadType.SAME_PLANE, MultifaceSpreader.SpreadType.WRAP_AROUND};
    private final MultifaceSpreader.SpreadConfig config;

    public MultifaceSpreader(MultifaceBlock lichen) {
        this((MultifaceSpreader.SpreadConfig) (new MultifaceSpreader.DefaultSpreaderConfig(lichen)));
    }

    public MultifaceSpreader(MultifaceSpreader.SpreadConfig growChecker) {
        this.config = growChecker;
    }

    public boolean canSpreadInAnyDirection(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return Direction.stream().anyMatch((enumdirection1) -> {
            MultifaceSpreader.SpreadConfig multifacespreader_b = this.config;

            Objects.requireNonNull(this.config);
            return this.getSpreadFromFaceTowardDirection(state, world, pos, direction, enumdirection1, multifacespreader_b::canSpreadInto).isPresent();
        });
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromRandomFaceTowardRandomDirection(BlockState state, LevelAccessor world, BlockPos pos, RandomSource random) {
        return (Optional) Direction.allShuffled(random).stream().filter((enumdirection) -> {
            return this.config.canSpreadFrom(state, enumdirection);
        }).map((enumdirection) -> {
            return this.spreadFromFaceTowardRandomDirection(state, world, pos, enumdirection, random, false);
        }).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    public long spreadAll(BlockState state, LevelAccessor world, BlockPos pos, boolean markForPostProcessing) {
        return (Long) Direction.stream().filter((enumdirection) -> {
            return this.config.canSpreadFrom(state, enumdirection);
        }).map((enumdirection) -> {
            return this.spreadFromFaceTowardAllDirections(state, world, pos, enumdirection, markForPostProcessing);
        }).reduce(0L, Long::sum);
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardRandomDirection(BlockState state, LevelAccessor world, BlockPos pos, Direction direction, RandomSource random, boolean markForPostProcessing) {
        return (Optional) Direction.allShuffled(random).stream().map((enumdirection1) -> {
            return this.spreadFromFaceTowardDirection(state, world, pos, direction, enumdirection1, markForPostProcessing);
        }).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    private long spreadFromFaceTowardAllDirections(BlockState state, LevelAccessor world, BlockPos pos, Direction direction, boolean markForPostProcessing) {
        return Direction.stream().map((enumdirection1) -> {
            return this.spreadFromFaceTowardDirection(state, world, pos, direction, enumdirection1, markForPostProcessing);
        }).filter(Optional::isPresent).count();
    }

    @VisibleForTesting
    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(BlockState state, LevelAccessor world, BlockPos pos, Direction oldDirection, Direction newDirection, boolean markForPostProcessing) {
        MultifaceSpreader.SpreadConfig multifacespreader_b = this.config;

        Objects.requireNonNull(this.config);
        return this.getSpreadFromFaceTowardDirection(state, world, pos, oldDirection, newDirection, multifacespreader_b::canSpreadInto).flatMap((multifacespreader_c) -> {
            return this.spreadToFace(world, multifacespreader_c, markForPostProcessing);
        });
    }

    public Optional<MultifaceSpreader.SpreadPos> getSpreadFromFaceTowardDirection(BlockState state, BlockGetter world, BlockPos pos, Direction oldDirection, Direction newDirection, MultifaceSpreader.SpreadPredicate predicate) {
        if (newDirection.getAxis() == oldDirection.getAxis()) {
            return Optional.empty();
        } else if (!this.config.isOtherBlockValidAsSource(state) && (!this.config.hasFace(state, oldDirection) || this.config.hasFace(state, newDirection))) {
            return Optional.empty();
        } else {
            MultifaceSpreader.SpreadType[] amultifacespreader_e = this.config.getSpreadTypes();
            int i = amultifacespreader_e.length;

            for (int j = 0; j < i; ++j) {
                MultifaceSpreader.SpreadType multifacespreader_e = amultifacespreader_e[j];
                MultifaceSpreader.SpreadPos multifacespreader_c = multifacespreader_e.getSpreadPos(pos, newDirection, oldDirection);

                if (predicate.test(world, pos, multifacespreader_c)) {
                    return Optional.of(multifacespreader_c);
                }
            }

            return Optional.empty();
        }
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadToFace(LevelAccessor world, MultifaceSpreader.SpreadPos pos, boolean markForPostProcessing) {
        BlockState iblockdata = world.getBlockState(pos.pos());

        return this.config.placeBlock(world, pos, iblockdata, markForPostProcessing) ? Optional.of(pos) : Optional.empty();
    }

    public static class DefaultSpreaderConfig implements MultifaceSpreader.SpreadConfig {

        protected MultifaceBlock block;

        public DefaultSpreaderConfig(MultifaceBlock lichen) {
            this.block = lichen;
        }

        @Nullable
        @Override
        public BlockState getStateForPlacement(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
            return this.block.getStateForPlacement(state, world, pos, direction);
        }

        protected boolean stateCanBeReplaced(BlockGetter world, BlockPos pos, BlockPos growPos, Direction direction, BlockState state) {
            return state.isAir() || state.is((Block) this.block) || state.is(Blocks.WATER) && state.getFluidState().isSource();
        }

        @Override
        public boolean canSpreadInto(BlockGetter world, BlockPos pos, MultifaceSpreader.SpreadPos growPos) {
            BlockState iblockdata = world.getBlockState(growPos.pos());

            return this.stateCanBeReplaced(world, pos, growPos.pos(), growPos.face(), iblockdata) && this.block.isValidStateForPlacement(world, iblockdata, growPos.pos(), growPos.face());
        }
    }

    public interface SpreadConfig {

        @Nullable
        BlockState getStateForPlacement(BlockState state, BlockGetter world, BlockPos pos, Direction direction);

        boolean canSpreadInto(BlockGetter world, BlockPos pos, MultifaceSpreader.SpreadPos growPos);

        default MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return MultifaceSpreader.DEFAULT_SPREAD_ORDER;
        }

        default boolean hasFace(BlockState state, Direction direction) {
            return MultifaceBlock.hasFace(state, direction);
        }

        default boolean isOtherBlockValidAsSource(BlockState state) {
            return false;
        }

        default boolean canSpreadFrom(BlockState state, Direction direction) {
            return this.isOtherBlockValidAsSource(state) || this.hasFace(state, direction);
        }

        default boolean placeBlock(LevelAccessor world, MultifaceSpreader.SpreadPos growPos, BlockState state, boolean markForPostProcessing) {
            BlockState iblockdata1 = this.getStateForPlacement(state, world, growPos.pos(), growPos.face());

            if (iblockdata1 != null) {
                if (markForPostProcessing) {
                    world.getChunk(growPos.pos()).markPosForPostprocessing(growPos.pos());
                }

                return org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, growPos.source(), growPos.pos(), iblockdata1, 2); // CraftBukkit
            } else {
                return false;
            }
        }
    }

    @FunctionalInterface
    public interface SpreadPredicate {

        boolean test(BlockGetter world, BlockPos pos, MultifaceSpreader.SpreadPos growPos);
    }

    public static enum SpreadType {

        SAME_POSITION {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction newDirection, Direction oldDirection) {
                return new MultifaceSpreader.SpreadPos(pos, newDirection, pos); // CraftBukkit
            }
        },
        SAME_PLANE {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction newDirection, Direction oldDirection) {
                return new MultifaceSpreader.SpreadPos(pos.relative(newDirection), oldDirection, pos); // CraftBukkit
            }
        },
        WRAP_AROUND {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction newDirection, Direction oldDirection) {
                return new MultifaceSpreader.SpreadPos(pos.relative(newDirection).relative(oldDirection), newDirection.getOpposite(), pos); // CraftBukkit
            }
        };

        SpreadType() {}

        public abstract MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction newDirection, Direction oldDirection);
    }

    public static record SpreadPos(BlockPos pos, Direction face, BlockPos source) { // CraftBukkit

    }
}
