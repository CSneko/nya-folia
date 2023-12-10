package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Silverfish;

public class CraftSilverfish extends CraftMonster implements Silverfish {
    public CraftSilverfish(CraftServer server, net.minecraft.world.entity.monster.Silverfish entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Silverfish getHandleRaw() {
        return (net.minecraft.world.entity.monster.Silverfish)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Silverfish getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Silverfish) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSilverfish";
    }
}
