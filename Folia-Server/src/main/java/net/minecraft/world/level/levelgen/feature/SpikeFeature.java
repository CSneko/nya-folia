package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.phys.AABB;

public class SpikeFeature extends Feature<SpikeConfiguration> {
    public static final int NUMBER_OF_SPIKES = 10;
    private static final int SPIKE_DISTANCE = 42;
    private static final LoadingCache<Long, List<SpikeFeature.EndSpike>> SPIKE_CACHE = CacheBuilder.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build(new SpikeFeature.SpikeCacheLoader());

    public SpikeFeature(Codec<SpikeConfiguration> configCodec) {
        super(configCodec);
    }

    public static List<SpikeFeature.EndSpike> getSpikesForLevel(WorldGenLevel world) {
        RandomSource randomSource = RandomSource.create(world.getSeed());
        long l = randomSource.nextLong() & 65535L;
        return SPIKE_CACHE.getUnchecked(l);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpikeConfiguration> context) {
        SpikeConfiguration spikeConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        List<SpikeFeature.EndSpike> list = spikeConfiguration.getSpikes();
        if (list.isEmpty()) {
            list = getSpikesForLevel(worldGenLevel);
        }

        for(SpikeFeature.EndSpike endSpike : list) {
            if (endSpike.isCenterWithinChunk(blockPos)) {
                this.placeSpike(worldGenLevel, randomSource, spikeConfiguration, endSpike);
            }
        }

        return true;
    }

    private void placeSpike(ServerLevelAccessor world, RandomSource random, SpikeConfiguration config, SpikeFeature.EndSpike spike) {
        int i = spike.getRadius();

        for(BlockPos blockPos : BlockPos.betweenClosed(new BlockPos(spike.getCenterX() - i, world.getMinBuildHeight(), spike.getCenterZ() - i), new BlockPos(spike.getCenterX() + i, spike.getHeight() + 10, spike.getCenterZ() + i))) {
            if (blockPos.distToLowCornerSqr((double)spike.getCenterX(), (double)blockPos.getY(), (double)spike.getCenterZ()) <= (double)(i * i + 1) && blockPos.getY() < spike.getHeight()) {
                this.setBlock(world, blockPos, Blocks.OBSIDIAN.defaultBlockState());
            } else if (blockPos.getY() > 65) {
                this.setBlock(world, blockPos, Blocks.AIR.defaultBlockState());
            }
        }

        if (spike.isGuarded()) {
            int j = -2;
            int k = 2;
            int l = 3;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for(int m = -2; m <= 2; ++m) {
                for(int n = -2; n <= 2; ++n) {
                    for(int o = 0; o <= 3; ++o) {
                        boolean bl = Mth.abs(m) == 2;
                        boolean bl2 = Mth.abs(n) == 2;
                        boolean bl3 = o == 3;
                        if (bl || bl2 || bl3) {
                            boolean bl4 = m == -2 || m == 2 || bl3;
                            boolean bl5 = n == -2 || n == 2 || bl3;
                            BlockState blockState = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, Boolean.valueOf(bl4 && n != -2)).setValue(IronBarsBlock.SOUTH, Boolean.valueOf(bl4 && n != 2)).setValue(IronBarsBlock.WEST, Boolean.valueOf(bl5 && m != -2)).setValue(IronBarsBlock.EAST, Boolean.valueOf(bl5 && m != 2));
                            this.setBlock(world, mutableBlockPos.set(spike.getCenterX() + m, spike.getHeight() + o, spike.getCenterZ() + n), blockState);
                        }
                    }
                }
            }
        }

        EndCrystal endCrystal = EntityType.END_CRYSTAL.create(world.getLevel());
        if (endCrystal != null) {
            endCrystal.setBeamTarget(config.getCrystalBeamTarget());
            endCrystal.setInvulnerable(config.isCrystalInvulnerable());
            endCrystal.moveTo((double)spike.getCenterX() + 0.5D, (double)(spike.getHeight() + 1), (double)spike.getCenterZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        endCrystal.generatedByDragonFight = true; // Paper
            world.addFreshEntity(endCrystal);
            BlockPos blockPos2 = endCrystal.blockPosition();
            this.setBlock(world, blockPos2.below(), Blocks.BEDROCK.defaultBlockState());
            this.setBlock(world, blockPos2, FireBlock.getState(world, blockPos2));
        }

    }

    public static class EndSpike {
        public static final Codec<SpikeFeature.EndSpike> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("centerX").orElse(0).forGetter((spike) -> {
                return spike.centerX;
            }), Codec.INT.fieldOf("centerZ").orElse(0).forGetter((spike) -> {
                return spike.centerZ;
            }), Codec.INT.fieldOf("radius").orElse(0).forGetter((spike) -> {
                return spike.radius;
            }), Codec.INT.fieldOf("height").orElse(0).forGetter((spike) -> {
                return spike.height;
            }), Codec.BOOL.fieldOf("guarded").orElse(false).forGetter((spike) -> {
                return spike.guarded;
            })).apply(instance, SpikeFeature.EndSpike::new);
        });
        private final int centerX;
        private final int centerZ;
        private final int radius;
        private final int height;
        private final boolean guarded;
        private final AABB topBoundingBox;

        public EndSpike(int centerX, int centerZ, int radius, int height, boolean guarded) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.height = height;
            this.guarded = guarded;
            this.topBoundingBox = new AABB((double)(centerX - radius), (double)DimensionType.MIN_Y, (double)(centerZ - radius), (double)(centerX + radius), (double)DimensionType.MAX_Y, (double)(centerZ + radius));
        }

        public boolean isCenterWithinChunk(BlockPos pos) {
            return SectionPos.blockToSectionCoord(pos.getX()) == SectionPos.blockToSectionCoord(this.centerX) && SectionPos.blockToSectionCoord(pos.getZ()) == SectionPos.blockToSectionCoord(this.centerZ);
        }

        public int getCenterX() {
            return this.centerX;
        }

        public int getCenterZ() {
            return this.centerZ;
        }

        public int getRadius() {
            return this.radius;
        }

        public int getHeight() {
            return this.height;
        }

        public boolean isGuarded() {
            return this.guarded;
        }

        public AABB getTopBoundingBox() {
            return this.topBoundingBox;
        }
    }

    static class SpikeCacheLoader extends CacheLoader<Long, List<SpikeFeature.EndSpike>> {
        @Override
        public List<SpikeFeature.EndSpike> load(Long long_) {
            IntArrayList intArrayList = Util.toShuffledList(IntStream.range(0, 10), RandomSource.create(long_));
            List<SpikeFeature.EndSpike> list = Lists.newArrayList();

            for(int i = 0; i < 10; ++i) {
                int j = Mth.floor(42.0D * Math.cos(2.0D * (-Math.PI + (Math.PI / 10D) * (double)i)));
                int k = Mth.floor(42.0D * Math.sin(2.0D * (-Math.PI + (Math.PI / 10D) * (double)i)));
                int l = intArrayList.get(i);
                int m = 2 + l / 3;
                int n = 76 + l * 3;
                boolean bl = l == 1 || l == 2;
                list.add(new SpikeFeature.EndSpike(j, k, m, n, bl));
            }

            return list;
        }
    }
}
