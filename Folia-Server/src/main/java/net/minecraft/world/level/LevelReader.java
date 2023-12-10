package net.minecraft.world.level;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public interface LevelReader extends BlockAndTintGetter, CollisionGetter, SignalGetter, BiomeManager.NoiseBiomeSource {
    @Nullable
    ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create);

    // Paper start - rewrite chunk system
    default ChunkAccess syncLoadNonFull(int chunkX, int chunkZ, ChunkStatus status) {
        if (status == null || status.isOrAfter(ChunkStatus.FULL)) {
            throw new IllegalArgumentException("Status: " + status.toString());
        }
        return this.getChunk(chunkX, chunkZ, status, true);
    }
    // Paper end - rewrite chunk system

    @Nullable ChunkAccess getChunkIfLoadedImmediately(int x, int z); // Paper - ifLoaded api (we need this since current impl blocks if the chunk is loading)
    @Nullable default ChunkAccess getChunkIfLoadedImmediately(BlockPos pos) { return this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);}

    /** @deprecated */
    @Deprecated
    boolean hasChunk(int chunkX, int chunkZ);

    int getHeight(Heightmap.Types heightmap, int x, int z);

    int getSkyDarken();

    BiomeManager getBiomeManager();

    default Holder<Biome> getBiome(BlockPos pos) {
        return this.getBiomeManager().getBiome(pos);
    }

    default Stream<BlockState> getBlockStatesIfLoaded(AABB box) {
        int i = Mth.floor(box.minX);
        int j = Mth.floor(box.maxX);
        int k = Mth.floor(box.minY);
        int l = Mth.floor(box.maxY);
        int m = Mth.floor(box.minZ);
        int n = Mth.floor(box.maxZ);
        return this.hasChunksAt(i, k, m, j, l, n) ? this.getBlockStates(box) : Stream.empty();
    }

    @Override
    default int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return colorResolver.getColor(this.getBiome(pos).value(), (double)pos.getX(), (double)pos.getZ());
    }

    @Override
    default Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        ChunkAccess chunkAccess = this.getChunk(QuartPos.toSection(biomeX), QuartPos.toSection(biomeZ), ChunkStatus.BIOMES, false);
        return chunkAccess != null ? chunkAccess.getNoiseBiome(biomeX, biomeY, biomeZ) : this.getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
    }

    Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ);

    boolean isClientSide();

    /** @deprecated */
    @Deprecated
    int getSeaLevel();

    DimensionType dimensionType();

    @Override
    default int getMinBuildHeight() {
        return this.dimensionType().minY();
    }

    @Override
    default int getHeight() {
        return this.dimensionType().height();
    }

    default BlockPos getHeightmapPos(Heightmap.Types heightmap, BlockPos pos) {
        return new BlockPos(pos.getX(), this.getHeight(heightmap, pos.getX(), pos.getZ()), pos.getZ());
    }

    default boolean isEmptyBlock(BlockPos pos) {
        return this.getBlockState(pos).isAir();
    }

    default boolean canSeeSkyFromBelowWater(BlockPos pos) {
        if (pos.getY() >= this.getSeaLevel()) {
            return this.canSeeSky(pos);
        } else {
            BlockPos blockPos = new BlockPos(pos.getX(), this.getSeaLevel(), pos.getZ());
            if (!this.canSeeSky(blockPos)) {
                return false;
            } else {
                for(BlockPos var4 = blockPos.below(); var4.getY() > pos.getY(); var4 = var4.below()) {
                    BlockState blockState = this.getBlockState(var4);
                    if (blockState.getLightBlock(this, var4) > 0 && !blockState.liquid()) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    default float getPathfindingCostFromLightLevels(BlockPos pos) {
        return this.getLightLevelDependentMagicValue(pos) - 0.5F;
    }

    /** @deprecated */
    @Deprecated
    default float getLightLevelDependentMagicValue(BlockPos pos) {
        float f = (float)this.getMaxLocalRawBrightness(pos) / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        return Mth.lerp(this.dimensionType().ambientLight(), g, 1.0F);
    }

    default ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
    }

    default ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status) {
        return this.getChunk(chunkX, chunkZ, status, true);
    }

    @Nullable
    @Override
    default BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    default boolean isWaterAt(BlockPos pos) {
        return this.getFluidState(pos).is(FluidTags.WATER);
    }

    default boolean containsAnyLiquid(AABB box) {
        int i = Mth.floor(box.minX);
        int j = Mth.ceil(box.maxX);
        int k = Mth.floor(box.minY);
        int l = Mth.ceil(box.maxY);
        int m = Mth.floor(box.minZ);
        int n = Mth.ceil(box.maxZ);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for(int o = i; o < j; ++o) {
            for(int p = k; p < l; ++p) {
                for(int q = m; q < n; ++q) {
                    BlockState blockState = this.getBlockState(mutableBlockPos.set(o, p, q));
                    if (!blockState.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    default int getMaxLocalRawBrightness(BlockPos pos) {
        return this.getMaxLocalRawBrightness(pos, this.getSkyDarken());
    }

    default int getMaxLocalRawBrightness(BlockPos pos, int ambientDarkness) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000 ? this.getRawBrightness(pos, ambientDarkness) : 15;
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunkAt(int x, int z) {
        return this.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunkAt(BlockPos pos) {
        return this.hasChunkAt(pos.getX(), pos.getZ());
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(BlockPos min, BlockPos max) {
        return this.hasChunksAt(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return maxY >= this.getMinBuildHeight() && minY < this.getMaxBuildHeight() ? this.hasChunksAt(minX, minZ, maxX, maxZ) : false;
    }

    // Folia start - region threading
    default boolean hasAndOwnsChunksAt(int minX, int minZ, int maxX, int maxZ) {
        int i = SectionPos.blockToSectionCoord(minX);
        int j = SectionPos.blockToSectionCoord(maxX);
        int k = SectionPos.blockToSectionCoord(minZ);
        int l = SectionPos.blockToSectionCoord(maxZ);

        for(int m = i; m <= j; ++m) {
            for(int n = k; n <= l; ++n) {
                if (!this.hasChunk(m, n) || (this instanceof net.minecraft.server.level.ServerLevel world && !io.papermc.paper.util.TickThread.isTickThreadFor(world, m, n))) {
                    return false;
                }
            }
        }

        return true;
    }
    // Folia end - region threading

    /** @deprecated */
    @Deprecated
    default boolean hasChunksAt(int minX, int minZ, int maxX, int maxZ) {
        int i = SectionPos.blockToSectionCoord(minX);
        int j = SectionPos.blockToSectionCoord(maxX);
        int k = SectionPos.blockToSectionCoord(minZ);
        int l = SectionPos.blockToSectionCoord(maxZ);

        for(int m = i; m <= j; ++m) {
            for(int n = k; n <= l; ++n) {
                if (!this.hasChunk(m, n)) {
                    return false;
                }
            }
        }

        return true;
    }

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default <T> HolderLookup<T> holderLookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
        Registry<T> registry = this.registryAccess().registryOrThrow(registryRef);
        return registry.asLookup().filterFeatures(this.enabledFeatures());
    }
}
