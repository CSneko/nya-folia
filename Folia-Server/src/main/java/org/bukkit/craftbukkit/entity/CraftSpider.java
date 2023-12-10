package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Spider;

public class CraftSpider extends CraftMonster implements Spider {

    public CraftSpider(CraftServer server, net.minecraft.world.entity.monster.Spider entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Spider getHandleRaw() {
        return (net.minecraft.world.entity.monster.Spider)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Spider getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Spider) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSpider";
    }
}
