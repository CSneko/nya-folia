package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.ThrownExpBottle;

public class CraftThrownExpBottle extends CraftThrowableProjectile implements ThrownExpBottle {
    public CraftThrownExpBottle(CraftServer server, ThrownExperienceBottle entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public ThrownExperienceBottle getHandleRaw() {
        return (ThrownExperienceBottle)this.entity;
    }
    // Folia end - region threading

    @Override
    public ThrownExperienceBottle getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (ThrownExperienceBottle) this.entity;
    }

    @Override
    public String toString() {
        return "EntityThrownExpBottle";
    }
}
