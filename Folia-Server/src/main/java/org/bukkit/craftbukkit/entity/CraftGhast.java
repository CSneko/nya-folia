package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Ghast;

public class CraftGhast extends CraftFlying implements Ghast, CraftEnemy {

    public CraftGhast(CraftServer server, net.minecraft.world.entity.monster.Ghast entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Ghast getHandleRaw() {
        return (net.minecraft.world.entity.monster.Ghast)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Ghast getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Ghast) this.entity;
    }

    @Override
    public String toString() {
        return "CraftGhast";
    }

    @Override
    public boolean isCharging() {
        return this.getHandle().isCharging();
    }

    @Override
    public void setCharging(boolean flag) {
        this.getHandle().setCharging(flag);
    }

    // Paper start
    @Override
    public int getExplosionPower() {
        return this.getHandle().getExplosionPower();
    }

    @Override
    public void setExplosionPower(int explosionPower) {
        com.google.common.base.Preconditions.checkArgument(explosionPower >= 0 && explosionPower <= 127, "The explosion power has to be between 0 and 127");
        this.getHandle().setExplosionPower(explosionPower);
    }
    // Paper end
}
