package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Skeleton;

public abstract class CraftAbstractSkeleton extends CraftMonster implements AbstractSkeleton, com.destroystokyo.paper.entity.CraftRangedEntity<net.minecraft.world.entity.monster.AbstractSkeleton> { // Paper

    public CraftAbstractSkeleton(CraftServer server, net.minecraft.world.entity.monster.AbstractSkeleton entity) {
        super(server, entity);
    }

    @Override
    public void setSkeletonType(Skeleton.SkeletonType type) {
        throw new UnsupportedOperationException("Not supported.");
    }
	// Paper start

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.AbstractSkeleton getHandleRaw() {
        return (net.minecraft.world.entity.monster.AbstractSkeleton)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.AbstractSkeleton getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.AbstractSkeleton) super.getHandle();
    }
    // Paper end
    // Paper start
    @Override
    public boolean shouldBurnInDay() {
        return getHandle().shouldBurnInDay();
    }

    @Override
    public void setShouldBurnInDay(boolean shouldBurnInDay) {
        getHandle().setShouldBurnInDay(shouldBurnInDay);
    }
    // Paper end
}
