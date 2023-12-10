package net.minecraft.world.level;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PathNavigationRegion implements BlockGetter, CollisionGetter {
    protected final int centerX;
    protected final int centerZ;
    protected final ChunkAccess[][] chunks;
    protected boolean allEmpty;
    protected final Level level;
    private final Supplier<Holder<Biome>> plains;

    public PathNavigationRegion(Level world, BlockPos minPos, BlockPos maxPos) {
        this.level = world;
        this.plains = Suppliers.memoize(() -> {
            return world.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        });
        this.centerX = SectionPos.blockToSectionCoord(minPos.getX());
        this.centerZ = SectionPos.blockToSectionCoord(minPos.getZ());
        int i = SectionPos.blockToSectionCoord(maxPos.getX());
        int j = SectionPos.blockToSectionCoord(maxPos.getZ());
        this.chunks = new ChunkAccess[i - this.centerX + 1][j - this.centerZ + 1];
        ChunkSource chunkSource = world.getChunkSource();
        this.allEmpty = true;

        for(int k = this.centerX; k <= i; ++k) {
            for(int l = this.centerZ; l <= j; ++l) {
                this.chunks[k - this.centerX][l - this.centerZ] = chunkSource.getChunkNow(k, l);
            }
        }

        for(int m = SectionPos.blockToSectionCoord(minPos.getX()); m <= SectionPos.blockToSectionCoord(maxPos.getX()); ++m) {
            for(int n = SectionPos.blockToSectionCoord(minPos.getZ()); n <= SectionPos.blockToSectionCoord(maxPos.getZ()); ++n) {
                ChunkAccess chunkAccess = this.chunks[m - this.centerX][n - this.centerZ];
                if (chunkAccess != null && !chunkAccess.isYSpaceEmpty(minPos.getY(), maxPos.getY())) {
                    this.allEmpty = false;
                    return;
                }
            }
        }

    }

    private ChunkAccess getChunk(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkAccess getChunk(int chunkX, int chunkZ) {
        int i = chunkX - this.centerX;
        int j = chunkZ - this.centerZ;
        if (i >= 0 && i < this.chunks.length && j >= 0 && j < this.chunks[i].length) { // Paper - if this changes, update getChunkIfLoaded below
            ChunkAccess chunkAccess = this.chunks[i][j];
            return (ChunkAccess)(chunkAccess != null ? chunkAccess : new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), this.plains.get()));
        } else {
            return new EmptyLevelChunk(this.level, new ChunkPos(chunkX, chunkZ), this.plains.get());
        }
    }

    // Paper start - if loaded util
    private @Nullable ChunkAccess getChunkIfLoaded(int x, int z) {
        // Based on getChunk(int, int)
        int xx = x - this.centerX;
        int zz = z - this.centerZ;

        if (xx >= 0 && xx < this.chunks.length && zz >= 0 && zz < this.chunks[xx].length) {
            return this.chunks[xx][zz];
        }
        return null;
    }
    @Override
    public final FluidState getFluidIfLoaded(BlockPos blockposition) {
        ChunkAccess chunk = getChunkIfLoaded(blockposition.getX() >> 4, blockposition.getZ() >> 4);
        return chunk == null ? null : chunk.getFluidState(blockposition);
    }

    @Override
    public final BlockState getBlockStateIfLoaded(BlockPos blockposition) {
        ChunkAccess chunk = getChunkIfLoaded(blockposition.getX() >> 4, blockposition.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(blockposition);
    }
    // Paper end

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
        return List.of();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess chunkAccess = this.getChunk(pos);
        return chunkAccess.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ChunkAccess chunkAccess = this.getChunk(pos);
            return chunkAccess.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            ChunkAccess chunkAccess = this.getChunk(pos);
            return chunkAccess.getFluidState(pos);
        }
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    public ProfilerFiller getProfiler() {
        return this.level.getProfiler();
    }
}
