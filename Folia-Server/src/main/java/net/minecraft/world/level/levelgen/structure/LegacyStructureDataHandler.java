package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class LegacyStructureDataHandler {

    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("Village", "Village");
        hashmap.put("Mineshaft", "Mineshaft");
        hashmap.put("Mansion", "Mansion");
        hashmap.put("Igloo", "Temple");
        hashmap.put("Desert_Pyramid", "Temple");
        hashmap.put("Jungle_Pyramid", "Temple");
        hashmap.put("Swamp_Hut", "Temple");
        hashmap.put("Stronghold", "Stronghold");
        hashmap.put("Monument", "Monument");
        hashmap.put("Fortress", "Fortress");
        hashmap.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("Iglu", "Igloo");
        hashmap.put("TeDP", "Desert_Pyramid");
        hashmap.put("TeJP", "Jungle_Pyramid");
        hashmap.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of("pillager_outpost", "mineshaft", "mansion", "jungle_pyramid", "desert_pyramid", "igloo", "ruined_portal", "shipwreck", "swamp_hut", "stronghold", "monument", "ocean_ruin", "fortress", "endcity", "buried_treasure", "village", "nether_fossil", "bastion_remnant");
    private final boolean hasLegacyData;
    private final Map<String, Long2ObjectMap<CompoundTag>> dataMap = Maps.newHashMap();
    private final Map<String, StructureFeatureIndexSavedData> indexMap = Maps.newHashMap();
    private final List<String> legacyKeys;
    private final List<String> currentKeys;

    public LegacyStructureDataHandler(@Nullable DimensionDataStorage persistentStateManager, List<String> oldNames, List<String> newNames) {
        this.legacyKeys = oldNames;
        this.currentKeys = newNames;
        this.populateCaches(persistentStateManager);
        boolean flag = false;

        String s;

        for (Iterator iterator = this.currentKeys.iterator(); iterator.hasNext(); flag |= this.dataMap.get(s) != null) {
            s = (String) iterator.next();
        }

        this.hasLegacyData = flag;
    }

    public void removeIndex(long chunkPos) {
        Iterator iterator = this.legacyKeys.iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            StructureFeatureIndexSavedData persistentindexed = (StructureFeatureIndexSavedData) this.indexMap.get(s);

            if (persistentindexed != null && persistentindexed.hasUnhandledIndex(chunkPos)) {
                persistentindexed.removeIndex(chunkPos);
                persistentindexed.setDirty();
            }
        }

    }

    public CompoundTag updateFromLegacy(CompoundTag nbt) {
        CompoundTag nbttagcompound1 = nbt.getCompound("Level");
        ChunkPos chunkcoordintpair = new ChunkPos(nbttagcompound1.getInt("xPos"), nbttagcompound1.getInt("zPos"));

        if (this.isUnhandledStructureStart(chunkcoordintpair.x, chunkcoordintpair.z)) {
            nbt = this.updateStructureStart(nbt, chunkcoordintpair);
        }

        CompoundTag nbttagcompound2 = nbttagcompound1.getCompound("Structures");
        CompoundTag nbttagcompound3 = nbttagcompound2.getCompound("References");
        Iterator iterator = this.currentKeys.iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            boolean flag = LegacyStructureDataHandler.OLD_STRUCTURE_REGISTRY_KEYS.contains(s.toLowerCase(Locale.ROOT));

            if (!nbttagcompound3.contains(s, 12) && flag) {
                boolean flag1 = true;
                LongArrayList longarraylist = new LongArrayList();

                for (int i = chunkcoordintpair.x - 8; i <= chunkcoordintpair.x + 8; ++i) {
                    for (int j = chunkcoordintpair.z - 8; j <= chunkcoordintpair.z + 8; ++j) {
                        if (this.hasLegacyStart(i, j, s)) {
                            longarraylist.add(ChunkPos.asLong(i, j));
                        }
                    }
                }

                nbttagcompound3.putLongArray(s, (List) longarraylist);
            }
        }

        nbttagcompound2.put("References", nbttagcompound3);
        nbttagcompound1.put("Structures", nbttagcompound2);
        nbt.put("Level", nbttagcompound1);
        return nbt;
    }

    private boolean hasLegacyStart(int chunkX, int chunkZ, String id) {
        return !this.hasLegacyData ? false : this.dataMap.get(id) != null && ((StructureFeatureIndexSavedData) this.indexMap.get(LegacyStructureDataHandler.CURRENT_TO_LEGACY_MAP.get(id))).hasStartIndex(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean isUnhandledStructureStart(int chunkX, int chunkZ) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            Iterator iterator = this.currentKeys.iterator();

            String s;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                s = (String) iterator.next();
            } while (this.dataMap.get(s) == null || !((StructureFeatureIndexSavedData) this.indexMap.get(LegacyStructureDataHandler.CURRENT_TO_LEGACY_MAP.get(s))).hasUnhandledIndex(ChunkPos.asLong(chunkX, chunkZ)));

            return true;
        }
    }

    private CompoundTag updateStructureStart(CompoundTag nbt, ChunkPos pos) {
        CompoundTag nbttagcompound1 = nbt.getCompound("Level");
        CompoundTag nbttagcompound2 = nbttagcompound1.getCompound("Structures");
        CompoundTag nbttagcompound3 = nbttagcompound2.getCompound("Starts");
        Iterator iterator = this.currentKeys.iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            Long2ObjectMap<CompoundTag> long2objectmap = (Long2ObjectMap) this.dataMap.get(s);

            if (long2objectmap != null) {
                long i = pos.toLong();

                if (((StructureFeatureIndexSavedData) this.indexMap.get(LegacyStructureDataHandler.CURRENT_TO_LEGACY_MAP.get(s))).hasUnhandledIndex(i)) {
                    CompoundTag nbttagcompound4 = (CompoundTag) long2objectmap.get(i);

                    if (nbttagcompound4 != null) {
                        nbttagcompound3.put(s, nbttagcompound4);
                    }
                }
            }
        }

        nbttagcompound2.put("Starts", nbttagcompound3);
        nbttagcompound1.put("Structures", nbttagcompound2);
        nbt.put("Level", nbttagcompound1);
        return nbt;
    }

    private void populateCaches(@Nullable DimensionDataStorage persistentStateManager) {
        if (persistentStateManager != null) {
            Iterator iterator = this.legacyKeys.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                CompoundTag nbttagcompound = new CompoundTag();

                try {
                    nbttagcompound = persistentStateManager.readTagFromDisk(s, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493).getCompound("data").getCompound("Features");
                    if (nbttagcompound.isEmpty()) {
                        continue;
                    }
                } catch (IOException ioexception) {
                    ;
                }

                Iterator iterator1 = nbttagcompound.getAllKeys().iterator();

                while (iterator1.hasNext()) {
                    String s1 = (String) iterator1.next();
                    CompoundTag nbttagcompound1 = nbttagcompound.getCompound(s1);
                    long i = ChunkPos.asLong(nbttagcompound1.getInt("ChunkX"), nbttagcompound1.getInt("ChunkZ"));
                    ListTag nbttaglist = nbttagcompound1.getList("Children", 10);
                    String s2;

                    if (!nbttaglist.isEmpty()) {
                        s2 = nbttaglist.getCompound(0).getString("id");
                        String s3 = (String) LegacyStructureDataHandler.LEGACY_TO_CURRENT_MAP.get(s2);

                        if (s3 != null) {
                            nbttagcompound1.putString("id", s3);
                        }
                    }

                    s2 = nbttagcompound1.getString("id");
                    ((Long2ObjectMap) this.dataMap.computeIfAbsent(s2, (s4) -> {
                        return new Long2ObjectOpenHashMap();
                    })).put(i, nbttagcompound1);
                }

                String s4 = s + "_index";
                StructureFeatureIndexSavedData persistentindexed = (StructureFeatureIndexSavedData) persistentStateManager.computeIfAbsent(StructureFeatureIndexSavedData.factory(), s4);

                if (!persistentindexed.getAll().isEmpty()) {
                    this.indexMap.put(s, persistentindexed);
                } else {
                    StructureFeatureIndexSavedData persistentindexed1 = new StructureFeatureIndexSavedData();

                    this.indexMap.put(s, persistentindexed1);
                    Iterator iterator2 = nbttagcompound.getAllKeys().iterator();

                    while (iterator2.hasNext()) {
                        String s5 = (String) iterator2.next();
                        CompoundTag nbttagcompound2 = nbttagcompound.getCompound(s5);

                        persistentindexed1.addIndex(ChunkPos.asLong(nbttagcompound2.getInt("ChunkX"), nbttagcompound2.getInt("ChunkZ")));
                    }

                    persistentindexed1.setDirty();
                }
            }

        }
    }

    public static LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<LevelStem> world, @Nullable DimensionDataStorage persistentStateManager) { // CraftBukkit
        if (world == LevelStem.OVERWORLD) { // CraftBukkit
            return new LegacyStructureDataHandler(persistentStateManager, ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"), ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument"));
        } else {
            ImmutableList immutablelist;

            if (world == LevelStem.NETHER) { // CraftBukkit
                immutablelist = ImmutableList.of("Fortress");
                return new LegacyStructureDataHandler(persistentStateManager, immutablelist, immutablelist);
            } else if (world == LevelStem.END) { // CraftBukkit
                immutablelist = ImmutableList.of("EndCity");
                return new LegacyStructureDataHandler(persistentStateManager, immutablelist, immutablelist);
            } else {
                throw new RuntimeException(String.format(Locale.ROOT, "Unknown dimension type : %s", world));
            }
        }
    }
}
