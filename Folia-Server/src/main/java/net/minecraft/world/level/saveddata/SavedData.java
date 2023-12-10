package net.minecraft.world.level.saveddata;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import org.slf4j.Logger;

public abstract class SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile boolean dirty; // Folia - make map data thread-safe

    public abstract CompoundTag save(CompoundTag nbt);

    public void setDirty() {
        this.setDirty(true);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void save(File file) {
        if (this.isDirty()) {
            this.setDirty(false); // Folia - make map data thread-safe - move before save, so that any changes after are not lost
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("data", this.save(new CompoundTag()));
            NbtUtils.addCurrentDataVersion(compoundTag);

            try {
                NbtIo.writeCompressed(compoundTag, file);
            } catch (IOException var4) {
                LOGGER.error("Could not save data {}", this, var4);
            }

            // Folia - make map data thread-safe - move before save, so that any changes after are not lost
        }
    }

    public static record Factory<T extends SavedData>(Supplier<T> constructor, Function<CompoundTag, T> deserializer, DataFixTypes type) {
    }
}
