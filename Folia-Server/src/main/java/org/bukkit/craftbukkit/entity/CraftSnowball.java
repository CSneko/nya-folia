package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Snowball;

public class CraftSnowball extends CraftThrowableProjectile implements Snowball {
    public CraftSnowball(CraftServer server, net.minecraft.world.entity.projectile.Snowball entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.Snowball getHandleRaw() {
        return (net.minecraft.world.entity.projectile.Snowball)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.Snowball getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.Snowball) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSnowball";
    }
}
