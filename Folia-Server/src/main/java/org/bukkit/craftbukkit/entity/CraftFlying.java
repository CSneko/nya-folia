package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.FlyingMob;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Flying;

public class CraftFlying extends CraftMob implements Flying {

    public CraftFlying(CraftServer server, FlyingMob entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public FlyingMob getHandleRaw() {
        return (FlyingMob)this.entity;
    }
    // Folia end - region threading

    @Override
    public FlyingMob getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (FlyingMob) this.entity;
    }

    @Override
    public String toString() {
        return "CraftFlying";
    }
}
