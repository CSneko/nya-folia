package net.minecraft.world.level.saveddata.maps;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class MapIndex extends SavedData {
    public static final String FILE_NAME = "idcounts";
    private final Object2IntMap<String> usedAuxIds = new Object2IntOpenHashMap<>();

    public static SavedData.Factory<MapIndex> factory() {
        return new SavedData.Factory<>(MapIndex::new, MapIndex::load, DataFixTypes.SAVED_DATA_MAP_INDEX);
    }

    public MapIndex() {
        this.usedAuxIds.defaultReturnValue(-1);
    }

    public static MapIndex load(CompoundTag nbt) {
        MapIndex mapIndex = new MapIndex();

        for(String string : nbt.getAllKeys()) {
            if (nbt.contains(string, 99)) {
                mapIndex.usedAuxIds.put(string, nbt.getInt(string));
            }
        }

        return mapIndex;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        synchronized (this.usedAuxIds) { // Folia - make map data thread-safe
        for(Object2IntMap.Entry<String> entry : this.usedAuxIds.object2IntEntrySet()) {
            nbt.putInt(entry.getKey(), entry.getIntValue());
        }
        } // Folia - make map data thread-safe

        return nbt;
    }

    public int getFreeAuxValueForMap() {
        synchronized (this.usedAuxIds) { // Folia - make map data thread-safe
        int i = this.usedAuxIds.getInt("map") + 1;
        this.usedAuxIds.put("map", i);
        this.setDirty();
        return i;
        } // Folia - make map data thread-safe
    }
}
