package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Fireball;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class CraftFireball extends AbstractProjectile implements Fireball {
    public CraftFireball(CraftServer server, AbstractHurtingProjectile entity) {
        super(server, entity);
    }

    @Override
    public float getYield() {
        return this.getHandle().bukkitYield;
    }

    @Override
    public boolean isIncendiary() {
        return this.getHandle().isIncendiary;
    }

    @Override
    public void setIsIncendiary(boolean isIncendiary) {
        this.getHandle().isIncendiary = isIncendiary;
    }

    @Override
    public void setYield(float yield) {
        this.getHandle().bukkitYield = yield;
    }

    // Paper - moved to AbstractProjectile

    @Override
    public Vector getDirection() {
        return new Vector(this.getHandle().xPower, this.getHandle().yPower, this.getHandle().zPower);
    }

    @Override
    public void setDirection(Vector direction) {
        Preconditions.checkArgument(direction != null, "Vector direction cannot be null");
        this.getHandle().setDirection(direction.getX(), direction.getY(), direction.getZ());
        this.update(); // SPIGOT-6579
    }

    // Paper start - set direction without normalizing
    @Override
    public void setVelocity(Vector velocity) {
        Preconditions.checkArgument(velocity != null, "Vector velocity cannot be null");
        velocity.checkFinite();
        this.getHandle().xPower = velocity.getX();
        this.getHandle().yPower = velocity.getY();
        this.getHandle().zPower = velocity.getZ();
        update();
    }
    // Paper end

    // Folia start - region threading
    @Override
    public AbstractHurtingProjectile getHandleRaw() {
        return (AbstractHurtingProjectile)this.entity;
    }
    // Folia end - region threading

    @Override
    public AbstractHurtingProjectile getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (AbstractHurtingProjectile) this.entity;
    }

    @Override
    public String toString() {
        return "CraftFireball";
    }
}
