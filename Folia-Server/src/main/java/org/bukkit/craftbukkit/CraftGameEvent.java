package org.bukkit.craftbukkit;

import com.google.common.base.Preconditions;
import net.minecraft.core.registries.Registries;
import org.bukkit.GameEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.jetbrains.annotations.NotNull;

public class CraftGameEvent extends GameEvent {

    public static GameEvent minecraftToBukkit(net.minecraft.world.level.gameevent.GameEvent minecraft) {
        Preconditions.checkArgument(minecraft != null);

        net.minecraft.core.Registry<net.minecraft.world.level.gameevent.GameEvent> registry = CraftRegistry.getMinecraftRegistry(Registries.GAME_EVENT);
        GameEvent bukkit = Registry.GAME_EVENT.get(CraftNamespacedKey.fromMinecraft(registry.getKey(minecraft)));

        Preconditions.checkArgument(bukkit != null);

        return bukkit;
    }

    public static net.minecraft.world.level.gameevent.GameEvent bukkitToMinecraft(GameEvent bukkit) {
        Preconditions.checkArgument(bukkit != null);

        return ((CraftGameEvent) bukkit).getHandle();
    }

    private final NamespacedKey key;
    private final net.minecraft.world.level.gameevent.GameEvent handle;

    public CraftGameEvent(NamespacedKey key, net.minecraft.world.level.gameevent.GameEvent handle) {
        this.key = key;
        this.handle = handle;
    }

    public net.minecraft.world.level.gameevent.GameEvent getHandle() {
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

        if (!(other instanceof CraftGameEvent)) {
            return false;
        }

        return this.getKey().equals(((GameEvent) other).getKey());
    }

    @Override
    public int hashCode() {
        return this.getKey().hashCode();
    }

    @Override
    public String toString() {
        return "CraftGameEvent{key=" + this.key + "}";
    }
}
