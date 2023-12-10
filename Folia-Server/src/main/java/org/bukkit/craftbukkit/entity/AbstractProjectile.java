package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.Entity;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Projectile;

public abstract class AbstractProjectile extends CraftEntity implements Projectile {

    public AbstractProjectile(CraftServer server, net.minecraft.world.entity.Entity entity) {
        super(server, entity);
    }

    @Override
    public boolean doesBounce() {
        return false;
    }

    @Override
    public void setBounce(boolean doesBounce) {}

    // Paper start
    @Override
    public boolean hasLeftShooter() {
        return this.getHandle().leftOwner;
    }

    @Override
    public void setHasLeftShooter(boolean leftShooter) {
        this.getHandle().leftOwner = leftShooter;
    }

    @Override
    public boolean hasBeenShot() {
        return this.getHandle().hasBeenShot;
    }

    @Override
    public void setHasBeenShot(boolean beenShot) {
        this.getHandle().hasBeenShot = beenShot;
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.Projectile getHandleRaw() {
        return (net.minecraft.world.entity.projectile.Projectile)this.entity;
    }
    // Folia end - region threading

    @Override
    public boolean canHitEntity(org.bukkit.entity.Entity entity) {
        return this.getHandle().canHitEntity(((CraftEntity) entity).getHandle());
    }

    @Override
    public void hitEntity(org.bukkit.entity.Entity entity) {
        this.getHandle().preOnHit(new net.minecraft.world.phys.EntityHitResult(((CraftEntity) entity).getHandle()));
    }

    @Override
    public void hitEntity(org.bukkit.entity.Entity entity, org.bukkit.util.Vector vector) {
        this.getHandle().preOnHit(new net.minecraft.world.phys.EntityHitResult(((CraftEntity) entity).getHandle(), new net.minecraft.world.phys.Vec3(vector.getX(), vector.getY(), vector.getZ())));
    }

    @Override
    public net.minecraft.world.entity.projectile.Projectile getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.Projectile) entity;
    }

    @Override
    public final org.bukkit.projectiles.ProjectileSource getShooter() {
        this.getHandle().refreshProjectileSource(true); // Paper
        return this.getHandle().projectileSource;
    }

    @Override
    public final void setShooter(org.bukkit.projectiles.ProjectileSource shooter) {
        if (shooter instanceof CraftEntity craftEntity) {
            this.getHandle().setOwner(craftEntity.getHandle());
        } else {
            this.getHandle().setOwner(null);
        }
        this.getHandle().projectileSource = shooter;
    }

    @Override
    public java.util.UUID getOwnerUniqueId() {
        return this.getHandle().ownerUUID;
    }
    // Paper end
}
