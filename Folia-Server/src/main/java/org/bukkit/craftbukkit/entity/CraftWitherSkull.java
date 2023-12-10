package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.WitherSkull;

public class CraftWitherSkull extends CraftFireball implements WitherSkull {
    public CraftWitherSkull(CraftServer server, net.minecraft.world.entity.projectile.WitherSkull entity) {
        super(server, entity);
    }

    @Override
    public void setCharged(boolean charged) {
        this.getHandle().setDangerous(charged);
    }

    @Override
    public boolean isCharged() {
        return this.getHandle().isDangerous();
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.WitherSkull getHandleRaw() {
        return (net.minecraft.world.entity.projectile.WitherSkull)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.WitherSkull getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.WitherSkull) this.entity;
    }

    @Override
    public String toString() {
        return "CraftWitherSkull";
    }
}
