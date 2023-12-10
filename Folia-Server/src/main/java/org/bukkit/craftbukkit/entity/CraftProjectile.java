package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.LivingEntity;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

public abstract class CraftProjectile extends AbstractProjectile implements Projectile {
    public CraftProjectile(CraftServer server, net.minecraft.world.entity.projectile.Projectile entity) {
        super(server, entity);
    }

    // Paper - moved to AbstractProjectile

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.Projectile getHandleRaw() {
        return (net.minecraft.world.entity.projectile.Projectile)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.Projectile getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.Projectile) this.entity;
    }

    @Override
    public String toString() {
        return "CraftProjectile";
    }
}
