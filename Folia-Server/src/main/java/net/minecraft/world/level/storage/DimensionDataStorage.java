package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class DimensionDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    public final Map<String, SavedData> cache = Maps.newHashMap();
    private final DataFixer fixerUpper;
    private final File dataFolder;

    public DimensionDataStorage(File directory, DataFixer dataFixer) {
        this.fixerUpper = dataFixer;
        this.dataFolder = directory;
    }

    private File getDataFile(String id) {
        return new File(this.dataFolder, id + ".dat");
    }

    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> type, String id) {
        synchronized (this.cache) { // Folia - make map data thread-safe
        T savedData = this.get(type, id);
        if (savedData != null) {
            return savedData;
        } else {
            T savedData2 = type.constructor().get();
            this.set(id, savedData2);
            return savedData2;
        }
        } // Folia - make map data thread-safe
    }

    @Nullable
    public <T extends SavedData> T get(SavedData.Factory type, String id) {
        synchronized (this.cache) { // Folia - make map data thread-safe
        SavedData savedData = this.cache.get(id);
        if (savedData == null && !this.cache.containsKey(id)) {
            savedData = this.readSavedData(type.deserializer(), type.type(), id);
            this.cache.put(id, savedData);
        }

        return (T)savedData;
        } // Folia - make map data thread-safe
    }

    @Nullable
    public <T extends SavedData> T readSavedData(Function<CompoundTag, T> readFunction, DataFixTypes dataFixTypes, String id) { // Paper
        try {
            File file = this.getDataFile(id);
            if (file.exists()) {
                CompoundTag compoundTag = this.readTagFromDisk(id, dataFixTypes, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                return readFunction.apply(compoundTag.getCompound("data"));
            }
        } catch (Exception var6) {
            LOGGER.error("Error loading saved data: {}", id, var6);
        }

        return (T)null;
    }

    public void set(String id, SavedData state) {
        synchronized (this.cache) { // Folia - make map data thread-safe
        this.cache.put(id, state);
        } // Folia - make map data thread-safe
    }

    public CompoundTag readTagFromDisk(String id, DataFixTypes dataFixTypes, int currentSaveVersion) throws IOException {
        File file = this.getDataFile(id);

        CompoundTag var9;
        try (
            FileInputStream fileInputStream = new FileInputStream(file);
            PushbackInputStream pushbackInputStream = new PushbackInputStream(fileInputStream, 2);
        ) {
            CompoundTag compoundTag;
            if (this.isGzip(pushbackInputStream)) {
                compoundTag = NbtIo.readCompressed(pushbackInputStream);
            } else {
                try (DataInputStream dataInputStream = new DataInputStream(pushbackInputStream)) {
                    compoundTag = NbtIo.read(dataInputStream);
                }
            }

            int i = NbtUtils.getDataVersion(compoundTag, 1343);
            var9 = dataFixTypes.update(this.fixerUpper, compoundTag, i, currentSaveVersion);
        }

        return var9;
    }

    private boolean isGzip(PushbackInputStream stream) throws IOException {
        byte[] bs = new byte[2];
        boolean bl = false;
        int i = stream.read(bs, 0, 2);
        if (i == 2) {
            int j = (bs[1] & 255) << 8 | bs[0] & 255;
            if (j == 35615) {
                bl = true;
            }
        }

        if (i != 0) {
            stream.unread(bs, 0, i);
        }

        return bl;
    }

    public void save() {
        this.cache.forEach((id, state) -> {
            if (state != null) {
                state.save(this.getDataFile(id));
            }

        });
    }
}
