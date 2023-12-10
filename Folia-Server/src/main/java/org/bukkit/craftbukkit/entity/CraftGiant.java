package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Giant;

public class CraftGiant extends CraftMonster implements Giant {

    public CraftGiant(CraftServer server, net.minecraft.world.entity.monster.Giant entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Giant getHandleRaw() {
        return (net.minecraft.world.entity.monster.Giant)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Giant getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Giant) this.entity;
    }

    @Override
    public String toString() {
        return "CraftGiant";
    }
}
