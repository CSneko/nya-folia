package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public abstract class StructurePlacement {
    public static final Codec<StructurePlacement> CODEC = BuiltInRegistries.STRUCTURE_PLACEMENT.byNameCodec().dispatch(StructurePlacement::type, StructurePlacementType::codec);
    private static final int HIGHLY_ARBITRARY_RANDOM_SALT = 10387320;
    public final Vec3i locateOffset;
    public final StructurePlacement.FrequencyReductionMethod frequencyReductionMethod;
    public final float frequency;
    public final int salt;
    public final Optional<StructurePlacement.ExclusionZone> exclusionZone;

    protected static <S extends StructurePlacement> Products.P5<RecordCodecBuilder.Mu<S>, Vec3i, StructurePlacement.FrequencyReductionMethod, Float, Integer, Optional<StructurePlacement.ExclusionZone>> placementCodec(RecordCodecBuilder.Instance<S> instance) {
        return instance.group(Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(StructurePlacement::locateOffset), StructurePlacement.FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method", StructurePlacement.FrequencyReductionMethod.DEFAULT).forGetter(StructurePlacement::frequencyReductionMethod), Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(StructurePlacement::frequency), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(StructurePlacement::salt), StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(StructurePlacement::exclusionZone));
    }

    protected StructurePlacement(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, Optional<StructurePlacement.ExclusionZone> exclusionZone) {
        this.locateOffset = locateOffset;
        this.frequencyReductionMethod = frequencyReductionMethod;
        this.frequency = frequency;
        this.salt = salt;
        this.exclusionZone = exclusionZone;
    }

    protected Vec3i locateOffset() {
        return this.locateOffset;
    }

    protected StructurePlacement.FrequencyReductionMethod frequencyReductionMethod() {
        return this.frequencyReductionMethod;
    }

    protected float frequency() {
        return this.frequency;
    }

    protected int salt() {
        return this.salt;
    }

    protected Optional<StructurePlacement.ExclusionZone> exclusionZone() {
        return this.exclusionZone;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public boolean isStructureChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ) {
        // Paper start - add missing structure set configs
        return this.isStructureChunk(calculator, chunkX, chunkZ, null);
    }
    public boolean isStructureChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ, @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceKey<StructureSet> structureSetKey) {
        Integer saltOverride = null;
        if (structureSetKey != null) {
            if (structureSetKey == net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.MINESHAFTS) {
                saltOverride = calculator.conf.mineshaftSeed;
            } else if (structureSetKey == net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.BURIED_TREASURES) {
                saltOverride = calculator.conf.buriedTreasureSeed;
            }
        }
        // Paper end
        if (!this.isPlacementChunk(calculator, chunkX, chunkZ)) {
            return false;
        } else if (this.frequency < 1.0F && !this.frequencyReductionMethod.shouldGenerate(calculator.getLevelSeed(), this.salt, chunkX, chunkZ, this.frequency, saltOverride)) { // Paper
            return false;
        } else {
            return !this.exclusionZone.isPresent() || !this.exclusionZone.get().isPlacementForbidden(calculator, chunkX, chunkZ);
        }
    }

    protected abstract boolean isPlacementChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ);

    public BlockPos getLocatePos(ChunkPos chunkPos) {
        return (new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ())).offset(this.locateOffset());
    }

    public abstract StructurePlacementType<?> type();

    private static boolean probabilityReducer(long seed, int salt, int chunkX, int chunkZ, float frequency, @org.jetbrains.annotations.Nullable Integer saltOverride) { // Paper - ignore here
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureWithSalt(seed, salt, chunkX, chunkZ);
        return worldgenRandom.nextFloat() < frequency;
    }

    private static boolean legacyProbabilityReducerWithDouble(long seed, int salt, int chunkX, int chunkZ, float frequency, @org.jetbrains.annotations.Nullable Integer saltOverride) { // Paper
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        if (saltOverride == null) { // Paper
        worldgenRandom.setLargeFeatureSeed(seed, chunkX, chunkZ);
        // Paper start
        } else {
            worldgenRandom.setLargeFeatureWithSalt(seed, chunkX, chunkZ, saltOverride);
        }
        // Paper end
        return worldgenRandom.nextDouble() < (double)frequency;
    }

    private static boolean legacyArbitrarySaltProbabilityReducer(long seed, int salt, int chunkX, int chunkZ, float frequency, @org.jetbrains.annotations.Nullable Integer saltOverride) { // Paper
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureWithSalt(seed, chunkX, chunkZ, saltOverride != null ? saltOverride : HIGHLY_ARBITRARY_RANDOM_SALT); // Paper
        return worldgenRandom.nextFloat() < frequency;
    }

    private static boolean legacyPillagerOutpostReducer(long seed, int salt, int chunkX, int chunkZ, float frequency, @org.jetbrains.annotations.Nullable Integer saltOverride) { // Paper - ignore here
        int i = chunkX >> 4;
        int j = chunkZ >> 4;
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setSeed((long)(i ^ j << 4) ^ seed);
        worldgenRandom.nextInt();
        return worldgenRandom.nextInt((int)(1.0F / frequency)) == 0;
    }

    /** @deprecated */
    @Deprecated
    public static record ExclusionZone(Holder<StructureSet> otherSet, int chunkCount) {
        public static final Codec<StructurePlacement.ExclusionZone> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(RegistryFileCodec.create(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, false).fieldOf("other_set").forGetter(StructurePlacement.ExclusionZone::otherSet), Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(StructurePlacement.ExclusionZone::chunkCount)).apply(instance, StructurePlacement.ExclusionZone::new);
        });

        boolean isPlacementForbidden(ChunkGeneratorStructureState calculator, int centerChunkX, int centerChunkZ) {
            return calculator.hasStructureChunkInRange(this.otherSet, centerChunkX, centerChunkZ, this.chunkCount);
        }
    }

    @FunctionalInterface
    public interface FrequencyReducer {
        boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float chance, @org.jetbrains.annotations.Nullable Integer saltOverride); // Paper
    }

    public static enum FrequencyReductionMethod implements StringRepresentable {
        DEFAULT("default", StructurePlacement::probabilityReducer),
        LEGACY_TYPE_1("legacy_type_1", StructurePlacement::legacyPillagerOutpostReducer),
        LEGACY_TYPE_2("legacy_type_2", StructurePlacement::legacyArbitrarySaltProbabilityReducer),
        LEGACY_TYPE_3("legacy_type_3", StructurePlacement::legacyProbabilityReducerWithDouble);

        public static final Codec<StructurePlacement.FrequencyReductionMethod> CODEC = StringRepresentable.fromEnum(StructurePlacement.FrequencyReductionMethod::values);
        private final String name;
        private final StructurePlacement.FrequencyReducer reducer;

        private FrequencyReductionMethod(String name, StructurePlacement.FrequencyReducer generationPredicate) {
            this.name = name;
            this.reducer = generationPredicate;
        }

        public boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float chance, @org.jetbrains.annotations.Nullable Integer saltOverride) { // Paper
            return this.reducer.shouldGenerate(seed, salt, chunkX, chunkZ, chance, saltOverride); // Paper
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
