package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;

public class CraftEvokerFangs extends CraftEntity implements EvokerFangs {

    public CraftEvokerFangs(CraftServer server, net.minecraft.world.entity.projectile.EvokerFangs entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.EvokerFangs getHandleRaw() {
        return (net.minecraft.world.entity.projectile.EvokerFangs)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.EvokerFangs getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.EvokerFangs) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftEvokerFangs";
    }

    @Override
    public LivingEntity getOwner() {
        net.minecraft.world.entity.LivingEntity owner = this.getHandle().getOwner();

        return (owner == null) ? null : (LivingEntity) owner.getBukkitEntity();
    }

    @Override
    public void setOwner(LivingEntity owner) {
        this.getHandle().setOwner(owner == null ? null : ((CraftLivingEntity) owner).getHandle());
    }

    @Override
    public int getAttackDelay() {
        return this.getHandle().warmupDelayTicks;
    }

    @Override
    public void setAttackDelay(int delay) {
        Preconditions.checkArgument(delay >= 0, "Delay must be positive");

        this.getHandle().warmupDelayTicks = delay;
    }
}
