package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.TraderLlama;

public class CraftTraderLlama extends CraftLlama implements TraderLlama {

    public CraftTraderLlama(CraftServer server, net.minecraft.world.entity.animal.horse.TraderLlama entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.horse.TraderLlama getHandleRaw() {
        return (net.minecraft.world.entity.animal.horse.TraderLlama)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.horse.TraderLlama getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.horse.TraderLlama) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftTraderLlama";
    }
}
