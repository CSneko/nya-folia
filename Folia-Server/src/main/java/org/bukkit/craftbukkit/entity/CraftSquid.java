package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Squid;

public class CraftSquid extends CraftWaterMob implements Squid {

    public CraftSquid(CraftServer server, net.minecraft.world.entity.animal.Squid entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.Squid getHandleRaw() {
        return (net.minecraft.world.entity.animal.Squid)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.Squid getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.Squid) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSquid";
    }
}
