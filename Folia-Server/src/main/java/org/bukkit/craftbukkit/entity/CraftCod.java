package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Cod;

public class CraftCod extends io.papermc.paper.entity.PaperSchoolableFish implements Cod { // Paper - School Fish API

    public CraftCod(CraftServer server, net.minecraft.world.entity.animal.Cod entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.Cod getHandleRaw() {
        return (net.minecraft.world.entity.animal.Cod)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.Cod getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.Cod) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftCod";
    }
}
