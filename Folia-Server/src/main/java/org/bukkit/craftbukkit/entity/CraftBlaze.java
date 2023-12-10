package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Blaze;

public class CraftBlaze extends CraftMonster implements Blaze {
    public CraftBlaze(CraftServer server, net.minecraft.world.entity.monster.Blaze entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Blaze getHandleRaw() {
        return (net.minecraft.world.entity.monster.Blaze)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Blaze getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Blaze) this.entity;
    }

    @Override
    public String toString() {
        return "CraftBlaze";
    }
}
