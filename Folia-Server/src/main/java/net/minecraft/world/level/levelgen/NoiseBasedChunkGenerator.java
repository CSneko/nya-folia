// keep
package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.apache.commons.lang3.mutable.MutableObject;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {

    public static final Codec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter((chunkgeneratorabstract) -> {
            return chunkgeneratorabstract.biomeSource;
        }), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter((chunkgeneratorabstract) -> {
            return chunkgeneratorabstract.settings;
        })).apply(instance, instance.stable(NoiseBasedChunkGenerator::new));
    });
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public final Holder<NoiseGeneratorSettings> settings;
    private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

    public NoiseBasedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;
        this.globalFluidPicker = Suppliers.memoize(() -> {
            return NoiseBasedChunkGenerator.createFluidPicker((NoiseGeneratorSettings) settings.value());
        });
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus aquifer_b = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = settings.seaLevel();
        Aquifer.FluidStatus aquifer_b1 = new Aquifer.FluidStatus(i, settings.defaultFluid());
        Aquifer.FluidStatus aquifer_b2 = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());

        return (j, k, l) -> {
            return k < Math.min(-54, i) ? aquifer_b : aquifer_b1;
        };
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState noiseConfig, Blender blender, StructureManager structureAccessor, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            this.doCreateBiomes(blender, noiseConfig, structureAccessor, chunk);
            return chunk;
        }), executor); // Paper - run with supplied executor
    }

    private void doCreateBiomes(Blender blender, RandomState noiseConfig, StructureManager structureAccessor, ChunkAccess chunk) {
        NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk((ichunkaccess1) -> {
            return this.createNoiseChunk(ichunkaccess1, structureAccessor, blender, noiseConfig);
        });
        BiomeResolver biomeresolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunk);

        chunk.fillBiomesFromNoise(biomeresolver, noisechunk.cachedClimateSampler(noiseConfig.router(), ((NoiseGeneratorSettings) this.settings.value()).spawnTarget()));
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager world, Blender blender, RandomState noiseConfig) {
        return NoiseChunk.forChunk(chunk, noiseConfig, Beardifier.forStructuresInChunk(world, chunk.getPos()), (NoiseGeneratorSettings) this.settings.value(), (Aquifer.FluidPicker) this.globalFluidPicker.get(), blender);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return NoiseBasedChunkGenerator.CODEC;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    public boolean stable(ResourceKey<NoiseGeneratorSettings> settings) {
        return this.settings.is(settings);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig) {
        return this.iterateNoiseColumn(world, noiseConfig, x, z, (MutableObject) null, heightmap.isOpaque()).orElse(world.getMinBuildHeight());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig) {
        MutableObject<NoiseColumn> mutableobject = new MutableObject();

        this.iterateNoiseColumn(world, noiseConfig, x, z, mutableobject, (Predicate) null);
        return (NoiseColumn) mutableobject.getValue();
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        NoiseRouter noiserouter = noiseConfig.router();
        DensityFunction.SinglePointContext densityfunction_e = new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
        double d0 = noiserouter.ridges().compute(densityfunction_e);
        String s = decimalformat.format(noiserouter.temperature().compute(densityfunction_e));

        text.add("NoiseRouter T: " + s + " V: " + decimalformat.format(noiserouter.vegetation().compute(densityfunction_e)) + " C: " + decimalformat.format(noiserouter.continents().compute(densityfunction_e)) + " E: " + decimalformat.format(noiserouter.erosion().compute(densityfunction_e)) + " D: " + decimalformat.format(noiserouter.depth().compute(densityfunction_e)) + " W: " + decimalformat.format(d0) + " PV: " + decimalformat.format((double) NoiseRouterData.peaksAndValleys((float) d0)) + " AS: " + decimalformat.format(noiserouter.initialDensityWithoutJaggedness().compute(densityfunction_e)) + " N: " + decimalformat.format(noiserouter.finalDensity().compute(densityfunction_e)));
    }

    private OptionalInt iterateNoiseColumn(LevelHeightAccessor world, RandomState noiseConfig, int x, int z, @Nullable MutableObject<NoiseColumn> columnSample, @Nullable Predicate<BlockState> stopPredicate) {
        NoiseSettings noisesettings = ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().clampToHeightAccessor(world);
        int k = noisesettings.getCellHeight();
        int l = noisesettings.minY();
        int i1 = Mth.floorDiv(l, k);
        int j1 = Mth.floorDiv(noisesettings.height(), k);

        if (j1 <= 0) {
            return OptionalInt.empty();
        } else {
            BlockState[] aiblockdata;

            if (columnSample == null) {
                aiblockdata = null;
            } else {
                aiblockdata = new BlockState[noisesettings.height()];
                columnSample.setValue(new NoiseColumn(l, aiblockdata));
            }

            int k1 = noisesettings.getCellWidth();
            int l1 = Math.floorDiv(x, k1);
            int i2 = Math.floorDiv(z, k1);
            int j2 = Math.floorMod(x, k1);
            int k2 = Math.floorMod(z, k1);
            int l2 = l1 * k1;
            int i3 = i2 * k1;
            double d0 = (double) j2 / (double) k1;
            double d1 = (double) k2 / (double) k1;
            NoiseChunk noisechunk = new NoiseChunk(1, noiseConfig, l2, i3, noisesettings, DensityFunctions.BeardifierMarker.INSTANCE, (NoiseGeneratorSettings) this.settings.value(), (Aquifer.FluidPicker) this.globalFluidPicker.get(), Blender.empty());

            noisechunk.initializeForFirstCellX();
            noisechunk.advanceCellX(0);

            for (int j3 = j1 - 1; j3 >= 0; --j3) {
                noisechunk.selectCellYZ(j3, 0);

                for (int k3 = k - 1; k3 >= 0; --k3) {
                    int l3 = (i1 + j3) * k + k3;
                    double d2 = (double) k3 / (double) k;

                    noisechunk.updateForY(l3, d2);
                    noisechunk.updateForX(x, d0);
                    noisechunk.updateForZ(z, d1);
                    BlockState iblockdata = noisechunk.getInterpolatedState();
                    BlockState iblockdata1 = iblockdata == null ? ((NoiseGeneratorSettings) this.settings.value()).defaultBlock() : iblockdata;

                    if (aiblockdata != null) {
                        int i4 = j3 * k + k3;

                        aiblockdata[i4] = iblockdata1;
                    }

                    if (stopPredicate != null && stopPredicate.test(iblockdata1)) {
                        noisechunk.stopInterpolation();
                        return OptionalInt.of(l3 + 1);
                    }
                }
            }

            noisechunk.stopInterpolation();
            return OptionalInt.empty();
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState noiseConfig, ChunkAccess chunk) {
        if (!SharedConstants.debugVoidTerrain(chunk.getPos())) {
            WorldGenerationContext worldgenerationcontext = new WorldGenerationContext(this, region, region.getMinecraftWorld()); // Paper

            this.buildSurface(chunk, worldgenerationcontext, noiseConfig, structures, region.getBiomeManager(), region.registryAccess().registryOrThrow(Registries.BIOME), Blender.of(region));
        }
    }

    @VisibleForTesting
    public void buildSurface(ChunkAccess chunk, WorldGenerationContext heightContext, RandomState noiseConfig, StructureManager structureAccessor, BiomeManager biomeAccess, Registry<Biome> biomeRegistry, Blender blender) {
        NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk((ichunkaccess1) -> {
            return this.createNoiseChunk(ichunkaccess1, structureAccessor, blender, noiseConfig);
        });
        NoiseGeneratorSettings generatorsettingbase = (NoiseGeneratorSettings) this.settings.value();

        noiseConfig.surfaceSystem().buildSurface(noiseConfig, biomeAccess, biomeRegistry, generatorsettingbase.useLegacyRandomSource(), heightContext, chunk, noisechunk, generatorsettingbase.surfaceRule());
    }

    @Override
    public void applyCarvers(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig, BiomeManager biomeAccess, StructureManager structureAccessor, ChunkAccess chunk, GenerationStep.Carving carverStep) {
        BiomeManager biomemanager1 = biomeAccess.withDifferentSource((j, k, l) -> {
            return this.biomeSource.getNoiseBiome(j, k, l, noiseConfig.sampler());
        });
        WorldgenRandom seededrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        boolean flag = true;
        ChunkPos chunkcoordintpair = chunk.getPos();
        NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk((ichunkaccess1) -> {
            return this.createNoiseChunk(ichunkaccess1, structureAccessor, Blender.of(chunkRegion), noiseConfig);
        });
        Aquifer aquifer = noisechunk.aquifer();
        CarvingContext carvingcontext = new CarvingContext(this, chunkRegion.registryAccess(), chunk.getHeightAccessorForGeneration(), noisechunk, noiseConfig, ((NoiseGeneratorSettings) this.settings.value()).surfaceRule(), chunkRegion.getMinecraftWorld()); // Paper
        CarvingMask carvingmask = ((ProtoChunk) chunk).getOrCreateCarvingMask(carverStep);

        for (int j = -8; j <= 8; ++j) {
            for (int k = -8; k <= 8; ++k) {
                ChunkPos chunkcoordintpair1 = new ChunkPos(chunkcoordintpair.x + j, chunkcoordintpair.z + k);
                ChunkAccess ichunkaccess1 = chunkRegion.getChunk(chunkcoordintpair1.x, chunkcoordintpair1.z);
                BiomeGenerationSettings biomesettingsgeneration = ichunkaccess1.carverBiome(() -> {
                    return this.getBiomeGenerationSettings(this.biomeSource.getNoiseBiome(QuartPos.fromBlock(chunkcoordintpair1.getMinBlockX()), 0, QuartPos.fromBlock(chunkcoordintpair1.getMinBlockZ()), noiseConfig.sampler()));
                });
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomesettingsgeneration.getCarvers(carverStep);
                int l = 0;

                for (Iterator iterator = iterable.iterator(); iterator.hasNext(); ++l) {
                    Holder<ConfiguredWorldCarver<?>> holder = (Holder) iterator.next();
                    ConfiguredWorldCarver<?> worldgencarverwrapper = (ConfiguredWorldCarver) holder.value();

                    seededrandom.setLargeFeatureSeed(seed + (long) l, chunkcoordintpair1.x, chunkcoordintpair1.z);
                    if (worldgencarverwrapper.isStartChunk(seededrandom)) {
                        Objects.requireNonNull(biomemanager1);
                        worldgencarverwrapper.carve(carvingcontext, chunk, biomemanager1::getBiome, seededrandom, aquifer, chunkcoordintpair1, carvingmask);
                    }
                }
            }
        }

    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState noiseConfig, StructureManager structureAccessor, ChunkAccess chunk) {
        NoiseSettings noisesettings = ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int i = noisesettings.minY();
        int j = Mth.floorDiv(i, noisesettings.getCellHeight());
        int k = Mth.floorDiv(noisesettings.height(), noisesettings.getCellHeight());

        if (k <= 0) {
            return CompletableFuture.completedFuture(chunk);
        } else {
            int l = chunk.getSectionIndex(k * noisesettings.getCellHeight() - 1 + i);
            int i1 = chunk.getSectionIndex(i);
            Set<LevelChunkSection> set = Sets.newHashSet();

            for (int j1 = l; j1 >= i1; --j1) {
                LevelChunkSection chunksection = chunk.getSection(j1);

                chunksection.acquire();
                set.add(chunksection);
            }

            return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("wgen_fill_noise", () -> {
                return this.doFill(blender, structureAccessor, noiseConfig, chunk, j, k);
            }), executor).whenCompleteAsync((ichunkaccess1, throwable) -> { // Paper - run with supplied executor
                Iterator iterator = set.iterator();

                while (iterator.hasNext()) {
                    LevelChunkSection chunksection1 = (LevelChunkSection) iterator.next();

                    chunksection1.release();
                }

            }, executor);
        }
    }

    private ChunkAccess doFill(Blender blender, StructureManager structureAccessor, RandomState noiseConfig, ChunkAccess chunk, int minimumCellY, int cellHeight) {
        NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk((ichunkaccess1) -> {
            return this.createNoiseChunk(ichunkaccess1, structureAccessor, blender, noiseConfig);
        });
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkcoordintpair = chunk.getPos();
        int k = chunkcoordintpair.getMinBlockX();
        int l = chunkcoordintpair.getMinBlockZ();
        Aquifer aquifer = noisechunk.aquifer();

        noisechunk.initializeForFirstCellX();
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        int i1 = noisechunk.cellWidth();
        int j1 = noisechunk.cellHeight();
        int k1 = 16 / i1;
        int l1 = 16 / i1;

        for (int i2 = 0; i2 < k1; ++i2) {
            noisechunk.advanceCellX(i2);

            for (int j2 = 0; j2 < l1; ++j2) {
                int k2 = chunk.getSectionsCount() - 1;
                LevelChunkSection chunksection = chunk.getSection(k2);

                for (int l2 = cellHeight - 1; l2 >= 0; --l2) {
                    noisechunk.selectCellYZ(l2, j2);

                    for (int i3 = j1 - 1; i3 >= 0; --i3) {
                        int j3 = (minimumCellY + l2) * j1 + i3;
                        int k3 = j3 & 15;
                        int l3 = chunk.getSectionIndex(j3);

                        if (k2 != l3) {
                            k2 = l3;
                            chunksection = chunk.getSection(l3);
                        }

                        double d0 = (double) i3 / (double) j1;

                        noisechunk.updateForY(j3, d0);

                        for (int i4 = 0; i4 < i1; ++i4) {
                            int j4 = k + i2 * i1 + i4;
                            int k4 = j4 & 15;
                            double d1 = (double) i4 / (double) i1;

                            noisechunk.updateForX(j4, d1);

                            for (int l4 = 0; l4 < i1; ++l4) {
                                int i5 = l + j2 * i1 + l4;
                                int j5 = i5 & 15;
                                double d2 = (double) l4 / (double) i1;

                                noisechunk.updateForZ(i5, d2);
                                BlockState iblockdata = noisechunk.getInterpolatedState();

                                if (iblockdata == null) {
                                    iblockdata = ((NoiseGeneratorSettings) this.settings.value()).defaultBlock();
                                }

                                iblockdata = this.debugPreliminarySurfaceLevel(noisechunk, j4, j3, i5, iblockdata);
                                if (iblockdata != NoiseBasedChunkGenerator.AIR && !SharedConstants.debugVoidTerrain(chunk.getPos())) {
                                    chunksection.setBlockState(k4, k3, j5, iblockdata, false);
                                    heightmap.update(k4, j3, j5, iblockdata);
                                    heightmap1.update(k4, j3, j5, iblockdata);
                                    if (aquifer.shouldScheduleFluidUpdate() && !iblockdata.getFluidState().isEmpty()) {
                                        blockposition_mutableblockposition.set(j4, j3, i5);
                                        chunk.markPosForPostprocessing(blockposition_mutableblockposition);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noisechunk.swapSlices();
        }

        noisechunk.stopInterpolation();
        return chunk;
    }

    private BlockState debugPreliminarySurfaceLevel(NoiseChunk chunkNoiseSampler, int x, int y, int z, BlockState state) {
        return state;
    }

    @Override
    public int getGenDepth() {
        return ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return ((NoiseGeneratorSettings) this.settings.value()).seaLevel();
    }

    @Override
    public int getMinY() {
        return ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().minY();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (!((NoiseGeneratorSettings) this.settings.value()).disableMobGeneration()) {
            ChunkPos chunkcoordintpair = region.getCenter();
            Holder<Biome> holder = region.getBiome(chunkcoordintpair.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
            WorldgenRandom seededrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));

            seededrandom.setDecorationSeed(region.getSeed(), chunkcoordintpair.getMinBlockX(), chunkcoordintpair.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(region, holder, chunkcoordintpair, seededrandom);
        }
    }
}
