package net.minecraft.world.level.levelgen.carver;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public class CarvingContext extends WorldGenerationContext {
    private final RegistryAccess registryAccess;
    private final NoiseChunk noiseChunk;
    private final RandomState randomState;
    private final SurfaceRules.RuleSource surfaceRule;

    public CarvingContext(NoiseBasedChunkGenerator noiseChunkGenerator, RegistryAccess registryManager, LevelHeightAccessor heightLimitView, NoiseChunk chunkNoiseSampler, RandomState noiseConfig, SurfaceRules.RuleSource materialRule, @javax.annotation.Nullable net.minecraft.world.level.Level level) { // Paper
        super(noiseChunkGenerator, heightLimitView, level); // Paper
        this.registryAccess = registryManager;
        this.noiseChunk = chunkNoiseSampler;
        this.randomState = noiseConfig;
        this.surfaceRule = materialRule;
    }

    /** @deprecated */
    @Deprecated
    public Optional<BlockState> topMaterial(Function<BlockPos, Holder<Biome>> posToBiome, ChunkAccess chunk, BlockPos pos, boolean hasFluid) {
        return this.randomState.surfaceSystem().topMaterial(this.surfaceRule, this, posToBiome, chunk, this.noiseChunk, pos, hasFluid);
    }

    /** @deprecated */
    @Deprecated
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public RandomState randomState() {
        return this.randomState;
    }
}
