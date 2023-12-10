package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.ExperienceOrb;

public class CraftExperienceOrb extends CraftEntity implements ExperienceOrb {
    public CraftExperienceOrb(CraftServer server, net.minecraft.world.entity.ExperienceOrb entity) {
        super(server, entity);
    }

    @Override
    public int getExperience() {
        return this.getHandle().value;
    }

    @Override
    public void setExperience(int value) {
        this.getHandle().value = value;
    }

    // Paper start
    public java.util.UUID getTriggerEntityId() {
        return getHandle().triggerEntityId;
    }
    public java.util.UUID getSourceEntityId() {
        return getHandle().sourceEntityId;
    }
    public SpawnReason getSpawnReason() {
        return getHandle().spawnReason;
    }
    // Paper end

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.ExperienceOrb getHandleRaw() {
        return (net.minecraft.world.entity.ExperienceOrb)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.ExperienceOrb getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.ExperienceOrb) this.entity;
    }

    @Override
    public String toString() {
        return "CraftExperienceOrb";
    }
}
