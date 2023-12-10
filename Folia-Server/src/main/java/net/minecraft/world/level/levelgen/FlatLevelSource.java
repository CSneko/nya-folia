package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class FlatLevelSource extends ChunkGenerator {

    public static final Codec<FlatLevelSource> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings)).apply(instance, instance.stable(FlatLevelSource::new));
    });
    private final FlatLevelGeneratorSettings settings;

    public FlatLevelSource(FlatLevelGeneratorSettings config) {
        // CraftBukkit start
        // WorldChunkManagerHell worldchunkmanagerhell = new WorldChunkManagerHell(generatorsettingsflat.getBiome());

        // Objects.requireNonNull(generatorsettingsflat);
        this(config, new FixedBiomeSource(config.getBiome()));
    }

    public FlatLevelSource(FlatLevelGeneratorSettings generatorsettingsflat, net.minecraft.world.level.biome.BiomeSource worldchunkmanager) {
        super(worldchunkmanager, Util.memoize(generatorsettingsflat::adjustGenerationSettings));
        // CraftBukkit end
        this.settings = generatorsettingsflat;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderlookup, RandomState randomstate, long i, org.spigotmc.SpigotWorldConfig conf) { // Spigot
        Stream<Holder<StructureSet>> stream = (Stream) this.settings.structureOverrides().map(HolderSet::stream).orElseGet(() -> {
            return holderlookup.listElements().map((holder_c) -> {
                return holder_c;
            });
        });

        return ChunkGeneratorStructureState.createForFlat(randomstate, i, this.biomeSource, stream, conf); // Spigot
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return FlatLevelSource.CODEC;
    }

    public FlatLevelGeneratorSettings settings() {
        return this.settings;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState noiseConfig, ChunkAccess chunk) {}

    @Override
    public int getSpawnHeight(LevelHeightAccessor world) {
        return world.getMinBuildHeight() + Math.min(world.getHeight(), this.settings.getLayers().size());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState noiseConfig, StructureManager structureAccessor, ChunkAccess chunk) {
        List<BlockState> list = this.settings.getLayers();
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int i = 0; i < Math.min(chunk.getHeight(), list.size()); ++i) {
            BlockState iblockdata = (BlockState) list.get(i);

            if (iblockdata != null) {
                int j = chunk.getMinBuildHeight() + i;

                for (int k = 0; k < 16; ++k) {
                    for (int l = 0; l < 16; ++l) {
                        chunk.setBlockState(blockposition_mutableblockposition.set(k, j, l), iblockdata, false);
                        heightmap.update(k, j, l, iblockdata);
                        heightmap1.update(k, j, l, iblockdata);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig) {
        List<BlockState> list = this.settings.getLayers();

        for (int k = Math.min(list.size(), world.getMaxBuildHeight()) - 1; k >= 0; --k) {
            BlockState iblockdata = (BlockState) list.get(k);

            if (iblockdata != null && heightmap.isOpaque().test(iblockdata)) {
                return world.getMinBuildHeight() + k + 1;
            }
        }

        return world.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig) {
        return new NoiseColumn(world.getMinBuildHeight(), (BlockState[]) this.settings.getLayers().stream().limit((long) world.getHeight()).map((iblockdata) -> {
            return iblockdata == null ? Blocks.AIR.defaultBlockState() : iblockdata;
        }).toArray((k) -> {
            return new BlockState[k];
        }));
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos) {}

    @Override
    public void applyCarvers(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig, BiomeManager biomeAccess, StructureManager structureAccessor, ChunkAccess chunk, GenerationStep.Carving carverStep) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }
}
