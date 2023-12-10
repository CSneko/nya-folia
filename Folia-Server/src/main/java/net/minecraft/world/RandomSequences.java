package net.minecraft.world;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class RandomSequences extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final long worldSeed;
    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<ResourceLocation, RandomSequence> sequences = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - region threading

    public static SavedData.Factory<RandomSequences> factory(long seed) {
        return new SavedData.Factory<>(() -> {
            return new RandomSequences(seed);
        }, (nbt) -> {
            return load(seed, nbt);
        }, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
    }

    public RandomSequences(long seed) {
        this.worldSeed = seed;
    }

    public RandomSource get(ResourceLocation id) {
        RandomSource randomSource = this.sequences.computeIfAbsent(id, this::createSequence).random();
        return new RandomSequences.DirtyMarkingRandomSource(randomSource);
    }

    private RandomSequence createSequence(ResourceLocation id) {
        return this.createSequence(id, this.salt, this.includeWorldSeed, this.includeSequenceId);
    }

    private RandomSequence createSequence(ResourceLocation id, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        long l = (includeWorldSeed ? this.worldSeed : 0L) ^ (long)salt;
        return new RandomSequence(l, includeSequenceId ? Optional.of(id) : Optional.empty());
    }

    public void forAllSequences(BiConsumer<ResourceLocation, RandomSequence> consumer) {
        this.sequences.forEach(consumer);
    }

    public void setSeedDefaults(int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.salt = salt;
        this.includeWorldSeed = includeWorldSeed;
        this.includeSequenceId = includeSequenceId;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("salt", this.salt);
        nbt.putBoolean("include_world_seed", this.includeWorldSeed);
        nbt.putBoolean("include_sequence_id", this.includeSequenceId);
        CompoundTag compoundTag = new CompoundTag();
        this.sequences.forEach((id, sequence) -> {
            compoundTag.put(id.toString(), RandomSequence.CODEC.encodeStart(NbtOps.INSTANCE, sequence).result().orElseThrow());
        });
        nbt.put("sequences", compoundTag);
        return nbt;
    }

    private static boolean getBooleanWithDefault(CompoundTag nbt, String key, boolean fallback) {
        return nbt.contains(key, 1) ? nbt.getBoolean(key) : fallback;
    }

    public static RandomSequences load(long seed, CompoundTag nbt) {
        RandomSequences randomSequences = new RandomSequences(seed);
        randomSequences.setSeedDefaults(nbt.getInt("salt"), getBooleanWithDefault(nbt, "include_world_seed", true), getBooleanWithDefault(nbt, "include_sequence_id", true));
        CompoundTag compoundTag = nbt.getCompound("sequences");

        for(String string : compoundTag.getAllKeys()) {
            try {
                RandomSequence randomSequence = RandomSequence.CODEC.decode(NbtOps.INSTANCE, compoundTag.get(string)).result().get().getFirst();
                randomSequences.sequences.put(new ResourceLocation(string), randomSequence);
            } catch (Exception var9) {
                LOGGER.error("Failed to load random sequence {}", string, var9);
            }
        }

        return randomSequences;
    }

    public int clear() {
        int i = this.sequences.size();
        this.sequences.clear();
        return i;
    }

    public void reset(ResourceLocation id) {
        this.sequences.put(id, this.createSequence(id));
    }

    public void reset(ResourceLocation id, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.sequences.put(id, this.createSequence(id, salt, includeWorldSeed, includeSequenceId));
    }

    class DirtyMarkingRandomSource implements RandomSource {
        private final RandomSource random;

        DirtyMarkingRandomSource(RandomSource random) {
            this.random = random;
        }

        @Override
        public RandomSource fork() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.fork(); } // Folia - region threading
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.forkPositional(); } // Folia - region threading
        }

        @Override
        public void setSeed(long seed) {
            RandomSequences.this.setDirty();
            synchronized (this.random) { this.random.setSeed(seed); } // Folia - region threading
        }

        @Override
        public int nextInt() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextInt(); } // Folia - region threading
        }

        @Override
        public int nextInt(int bound) {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextInt(bound); } // Folia - region threading
        }

        @Override
        public long nextLong() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextLong(); } // Folia - region threading
        }

        @Override
        public boolean nextBoolean() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextBoolean(); } // Folia - region threading
        }

        @Override
        public float nextFloat() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextFloat(); } // Folia - region threading
        }

        @Override
        public double nextDouble() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextDouble(); } // Folia - region threading
        }

        @Override
        public double nextGaussian() {
            RandomSequences.this.setDirty();
            synchronized (this.random) { return this.random.nextGaussian(); } // Folia - region threading
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object instanceof RandomSequences.DirtyMarkingRandomSource) {
                RandomSequences.DirtyMarkingRandomSource dirtyMarkingRandomSource = (RandomSequences.DirtyMarkingRandomSource)object;
                return this.random.equals(dirtyMarkingRandomSource.random);
            } else {
                return false;
            }
        }
    }
}
