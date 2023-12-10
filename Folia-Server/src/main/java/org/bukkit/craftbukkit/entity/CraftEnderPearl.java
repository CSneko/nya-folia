package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EnderPearl;

public class CraftEnderPearl extends CraftThrowableProjectile implements EnderPearl {
    public CraftEnderPearl(CraftServer server, ThrownEnderpearl entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public ThrownEnderpearl getHandleRaw() {
        return (ThrownEnderpearl)this.entity;
    }
    // Folia end - region threading

    @Override
    public ThrownEnderpearl getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (ThrownEnderpearl) this.entity;
    }

    @Override
    public String toString() {
        return "CraftEnderPearl";
    }
}
