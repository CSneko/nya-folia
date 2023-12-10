package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.SmallFireball;

public class CraftSmallFireball extends CraftSizedFireball implements SmallFireball {
    public CraftSmallFireball(CraftServer server, net.minecraft.world.entity.projectile.SmallFireball entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.SmallFireball getHandleRaw() {
        return (net.minecraft.world.entity.projectile.SmallFireball)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.SmallFireball getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.SmallFireball) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSmallFireball";
    }
}
