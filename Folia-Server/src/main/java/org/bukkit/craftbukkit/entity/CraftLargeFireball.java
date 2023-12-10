package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.LargeFireball;

public class CraftLargeFireball extends CraftSizedFireball implements LargeFireball {
    public CraftLargeFireball(CraftServer server, net.minecraft.world.entity.projectile.LargeFireball entity) {
        super(server, entity);
    }

    @Override
    public void setYield(float yield) {
        super.setYield(yield);
        this.getHandle().explosionPower = (int) yield;
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.LargeFireball getHandleRaw() {
        return (net.minecraft.world.entity.projectile.LargeFireball)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.LargeFireball getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.LargeFireball) this.entity;
    }

    @Override
    public String toString() {
        return "CraftLargeFireball";
    }
}
