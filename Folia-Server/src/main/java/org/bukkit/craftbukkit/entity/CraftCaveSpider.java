package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.CaveSpider;

public class CraftCaveSpider extends CraftSpider implements CaveSpider {
    public CraftCaveSpider(CraftServer server, net.minecraft.world.entity.monster.CaveSpider entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.CaveSpider getHandleRaw() {
        return (net.minecraft.world.entity.monster.CaveSpider)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.CaveSpider getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.CaveSpider) this.entity;
    }

    @Override
    public String toString() {
        return "CraftCaveSpider";
    }
}
