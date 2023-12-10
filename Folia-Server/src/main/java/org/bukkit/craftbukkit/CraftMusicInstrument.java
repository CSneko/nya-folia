package org.bukkit.craftbukkit;

import com.google.common.base.Preconditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Instrument;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.jetbrains.annotations.NotNull;

public class CraftMusicInstrument extends MusicInstrument {

    public static MusicInstrument minecraftToBukkit(Instrument minecraft) {
        Preconditions.checkArgument(minecraft != null);

        net.minecraft.core.Registry<Instrument> registry = CraftRegistry.getMinecraftRegistry(Registries.INSTRUMENT);
        MusicInstrument bukkit = Registry.INSTRUMENT.get(CraftNamespacedKey.fromMinecraft(registry.getKey(minecraft)));

        Preconditions.checkArgument(bukkit != null);

        return bukkit;
    }

    public static Instrument bukkitToMinecraft(MusicInstrument bukkit) {
        Preconditions.checkArgument(bukkit != null);

        return ((CraftMusicInstrument) bukkit).getHandle();
    }

    private final NamespacedKey key;
    private final Instrument handle;

    public CraftMusicInstrument(NamespacedKey key, Instrument handle) {
        this.key = key;
        this.handle = handle;
    }

    public Instrument getHandle() {
        return this.handle;
    }

    @NotNull
    @Override
    public NamespacedKey getKey() {
        return this.key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof CraftMusicInstrument)) {
            return false;
        }

        return this.getKey().equals(((MusicInstrument) other).getKey());
    }

    @Override
    public int hashCode() {
        return this.getKey().hashCode();
    }

    @Override
    public String toString() {
        return "CraftMusicInstrument{key=" + this.key + "}";
    }
}
