package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.projectile.ThrownEgg;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Egg;

public class CraftEgg extends CraftThrowableProjectile implements Egg {
    public CraftEgg(CraftServer server, ThrownEgg entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public ThrownEgg getHandleRaw() {
        return (ThrownEgg)this.entity;
    }
    // Folia end - region threading

    @Override
    public ThrownEgg getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (ThrownEgg) this.entity;
    }

    @Override
    public String toString() {
        return "CraftEgg";
    }
}
