package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class StructureCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final Registry<Biome> biomes;
    private final Registry<Structure> structureConfigs;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<net.minecraft.world.level.dimension.LevelStem> dimension; // Paper - fix missing CB diff
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    // Paper start - rewrite chunk system - synchronise this class
    // additionally, make sure to purge entries from the maps so it does not leak memory
    private static final int CHUNK_TOTAL_LIMIT = 50 * (2 * 100 + 1) * (2 * 100 + 1); // cache 50 structure lookups
    private static final int PER_FEATURE_CHECK_LIMIT = 50 * (2 * 100 + 1) * (2 * 100 + 1); // cache 50 structure lookups

    private final SynchronisedLong2ObjectMap<Object2IntMap<Structure>> loadedChunksSafe = new SynchronisedLong2ObjectMap<>(CHUNK_TOTAL_LIMIT);
    private final java.util.concurrent.ConcurrentHashMap<Structure, SynchronisedLong2BooleanMap> featureChecksSafe = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class SynchronisedLong2ObjectMap<V> {
        private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<V> map = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
        private final int limit;

        public SynchronisedLong2ObjectMap(final int limit) {
            this.limit = limit;
        }

        // must hold lock on map
        private void purgeEntries() {
            while (this.map.size() > this.limit) {
                this.map.removeLast();
            }
        }

        public V get(final long key) {
            synchronized (this.map) {
                return this.map.getAndMoveToFirst(key);
            }
        }

        public V put(final long key, final V value) {
            synchronized (this.map) {
                final V ret = this.map.putAndMoveToFirst(key, value);
                this.purgeEntries();
                return ret;
            }
        }

        public V compute(final long key, final java.util.function.BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
            synchronized (this.map) {
                // first, compute the value - if one is added, it will be at the last entry
                this.map.compute(key, remappingFunction);
                // move the entry to first, just in case it was added at last
                final V ret = this.map.getAndMoveToFirst(key);
                // now purge the last entries
                this.purgeEntries();

                return ret;
            }
        }
    }

    private static final class SynchronisedLong2BooleanMap {
        private final it.unimi.dsi.fastutil.longs.Long2BooleanLinkedOpenHashMap map = new it.unimi.dsi.fastutil.longs.Long2BooleanLinkedOpenHashMap();
        private final int limit;

        public SynchronisedLong2BooleanMap(final int limit) {
            this.limit = limit;
        }

        // must hold lock on map
        private void purgeEntries() {
            while (this.map.size() > this.limit) {
                this.map.removeLastBoolean();
            }
        }

        public boolean remove(final long key) {
            synchronized (this.map) {
                return this.map.remove(key);
            }
        }

        // note:
        public boolean getOrCompute(final long key, final it.unimi.dsi.fastutil.longs.Long2BooleanFunction ifAbsent) {
            synchronized (this.map) {
                if (this.map.containsKey(key)) {
                    return this.map.getAndMoveToFirst(key);
                }
            }

            final boolean put = ifAbsent.get(key);

            synchronized (this.map) {
                if (this.map.containsKey(key)) {
                    return this.map.getAndMoveToFirst(key);
                }
                this.map.putAndMoveToFirst(key, put);

                this.purgeEntries();

                return put;
            }
        }
    }
    // Paper end - rewrite chunk system - synchronise this class

    public StructureCheck(ChunkScanAccess chunkIoWorker, RegistryAccess registryManager, StructureTemplateManager structureTemplateManager, ResourceKey<net.minecraft.world.level.dimension.LevelStem> worldKey, ChunkGenerator chunkGenerator, RandomState noiseConfig, LevelHeightAccessor world, BiomeSource biomeSource, long seed, DataFixer dataFixer) { // Paper - fix missing CB diff
        this.storageAccess = chunkIoWorker;
        this.registryAccess = registryManager;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = worldKey;
        this.chunkGenerator = chunkGenerator;
        this.randomState = noiseConfig;
        this.heightAccessor = world;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = dataFixer;
        this.biomes = registryManager.registryOrThrow(Registries.BIOME);
        this.structureConfigs = registryManager.registryOrThrow(Registries.STRUCTURE);
    }

    public StructureCheckResult checkStart(ChunkPos pos, Structure type, boolean skipReferencedStructures) {
        long l = pos.toLong();
        Object2IntMap<Structure> object2IntMap = this.loadedChunksSafe.get(l); // Paper - rewrite chunk system - synchronise this class
        if (object2IntMap != null) {
            return this.checkStructureInfo(object2IntMap, type, skipReferencedStructures);
        } else {
            StructureCheckResult structureCheckResult = this.tryLoadFromStorage(pos, type, skipReferencedStructures, l);
            if (structureCheckResult != null) {
                return structureCheckResult;
            } else {
                boolean bl = this.featureChecksSafe.computeIfAbsent(type, (structure2) -> { // Paper - rewrite chunk system - synchronise this class
                    return new SynchronisedLong2BooleanMap(PER_FEATURE_CHECK_LIMIT); // Paper - rewrite chunk system - synchronise this class
                }).getOrCompute(l, (chunkPos) -> { // Paper - rewrite chunk system - synchronise this class
                    return this.canCreateStructure(pos, type);
                });
                return !bl ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
            }
        }
    }

    private boolean canCreateStructure(ChunkPos pos, Structure structure) {
        return structure.findValidGenerationPoint(new Structure.GenerationContext(this.registryAccess, this.chunkGenerator, this.biomeSource, this.randomState, this.structureTemplateManager, this.seed, pos, this.heightAccessor, structure.biomes()::contains)).isPresent();
    }

    @Nullable
    private StructureCheckResult tryLoadFromStorage(ChunkPos pos, Structure structure, boolean skipReferencedStructures, long posLong) {
        CollectFields collectFields = new CollectFields(new FieldSelector(IntTag.TYPE, "DataVersion"), new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"), new FieldSelector("structures", CompoundTag.TYPE, "starts"));

        try {
            this.storageAccess.scanChunk(pos, collectFields).join();
        } catch (Exception var13) {
            LOGGER.warn("Failed to read chunk {}", pos, var13);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        Tag tag = collectFields.getResult();
        if (!(tag instanceof CompoundTag compoundTag)) {
            return null;
        } else {
            int i = ChunkStorage.getVersion(compoundTag);
            if (i <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            } else {
                ChunkStorage.injectDatafixingContext(compoundTag, this.dimension, this.chunkGenerator.getTypeNameForDataFixer());

                CompoundTag compoundTag2;
                try {
                    compoundTag2 = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, compoundTag, i, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()); // Paper - replace chunk converter
                } catch (Exception var12) {
                    LOGGER.warn("Failed to partially datafix chunk {}", pos, var12);
                    return StructureCheckResult.CHUNK_LOAD_NEEDED;
                }

                Object2IntMap<Structure> object2IntMap = this.loadStructures(compoundTag2);
                if (object2IntMap == null) {
                    return null;
                } else {
                    this.storeFullResults(posLong, object2IntMap);
                    return this.checkStructureInfo(object2IntMap, structure, skipReferencedStructures);
                }
            }
        }
    }

    @Nullable
    private Object2IntMap<Structure> loadStructures(CompoundTag nbt) {
        if (!nbt.contains("structures", 10)) {
            return null;
        } else {
            CompoundTag compoundTag = nbt.getCompound("structures");
            if (!compoundTag.contains("starts", 10)) {
                return null;
            } else {
                CompoundTag compoundTag2 = compoundTag.getCompound("starts");
                if (compoundTag2.isEmpty()) {
                    return Object2IntMaps.emptyMap();
                } else {
                    Object2IntMap<Structure> object2IntMap = new Object2IntOpenHashMap<>();
                    Registry<Structure> registry = this.registryAccess.registryOrThrow(Registries.STRUCTURE);

                    for(String string : compoundTag2.getAllKeys()) {
                        ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
                        if (resourceLocation != null) {
                            Structure structure = registry.get(resourceLocation);
                            if (structure != null) {
                                CompoundTag compoundTag3 = compoundTag2.getCompound(string);
                                if (!compoundTag3.isEmpty()) {
                                    String string2 = compoundTag3.getString("id");
                                    if (!"INVALID".equals(string2)) {
                                        int i = compoundTag3.getInt("references");
                                        object2IntMap.put(structure, i);
                                    }
                                }
                            }
                        }
                    }

                    return object2IntMap;
                }
            }
        }
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> referencesByStructure, Structure structure, boolean skipReferencedStructures) {
        int i = referencesByStructure.getOrDefault(structure, -1);
        return i == -1 || skipReferencedStructures && i != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos pos, Map<Structure, StructureStart> structureStarts) {
        long l = pos.toLong();
        Object2IntMap<Structure> object2IntMap = new Object2IntOpenHashMap<>();
        structureStarts.forEach((start, structureStart) -> {
            if (structureStart.isValid()) {
                object2IntMap.put(start, structureStart.getReferences());
            }

        });
        this.storeFullResults(l, object2IntMap);
    }

    private void storeFullResults(long pos, Object2IntMap<Structure> referencesByStructure) {
        // Paper start - rewrite chunk system - synchronise this class
        this.loadedChunksSafe.put(pos, deduplicateEmptyMap(referencesByStructure));
        // once we insert into loadedChunks, we don't really need to be very careful about removing everything
        // from this map, as everything that checks this map uses loadedChunks first
        // so, one way or another it's a race condition that doesn't matter
        for (SynchronisedLong2BooleanMap value : this.featureChecksSafe.values()) {
            value.remove(pos);
        }
        // Paper end - rewrite chunk system - synchronise this class
    }

    public void incrementReference(ChunkPos pos, Structure structure) {
        this.loadedChunksSafe.compute(pos.toLong(), (posx, referencesByStructure) -> { // Paper start - rewrite chunk system - synchronise this class
            // make this COW so that we do not mutate state that may be currently in use
            if (referencesByStructure == null) {
                referencesByStructure = new Object2IntOpenHashMap<>();
            } else {
                referencesByStructure = referencesByStructure instanceof Object2IntOpenHashMap<Structure> fastClone ? fastClone.clone() : new Object2IntOpenHashMap<>(referencesByStructure);
            }
            // Paper end - rewrite chunk system - synchronise this class

            referencesByStructure.computeInt(structure, (feature, references) -> {
                return references == null ? 1 : references + 1;
            });
            return referencesByStructure;
        });
    }
}
