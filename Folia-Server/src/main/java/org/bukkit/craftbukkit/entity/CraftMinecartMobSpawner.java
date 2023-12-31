package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.vehicle.MinecartSpawner;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.minecart.SpawnerMinecart;

final class CraftMinecartMobSpawner extends CraftMinecart implements SpawnerMinecart {
    CraftMinecartMobSpawner(CraftServer server, MinecartSpawner entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "CraftMinecartMobSpawner";
    }
}
