package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Drowned;

public class CraftDrowned extends CraftZombie implements Drowned, com.destroystokyo.paper.entity.CraftRangedEntity<net.minecraft.world.entity.monster.Drowned> { // Paper

    public CraftDrowned(CraftServer server, net.minecraft.world.entity.monster.Drowned entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Drowned getHandleRaw() {
        return (net.minecraft.world.entity.monster.Drowned)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Drowned getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Drowned) this.entity;
    }

    @Override
    public String toString() {
        return "CraftDrowned";
    }
}
