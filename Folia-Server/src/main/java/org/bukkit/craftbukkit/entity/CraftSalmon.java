package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Salmon;

public class CraftSalmon extends io.papermc.paper.entity.PaperSchoolableFish implements Salmon { // Paper - Schooling Fish API

    public CraftSalmon(CraftServer server, net.minecraft.world.entity.animal.Salmon entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.Salmon getHandleRaw() {
        return (net.minecraft.world.entity.animal.Salmon)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.Salmon getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.Salmon) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftSalmon";
    }
}
