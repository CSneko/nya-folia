package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.ComplexLivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

public class CraftComplexPart extends CraftEntity implements ComplexEntityPart {
    public CraftComplexPart(CraftServer server, EnderDragonPart entity) {
        super(server, entity);
    }

    @Override
    public ComplexLivingEntity getParent() {
        return (ComplexLivingEntity) ((EnderDragon) this.getHandle().parentMob).getBukkitEntity();
    }

    @Override
    public void setLastDamageCause(EntityDamageEvent cause) {
        this.getParent().setLastDamageCause(cause);
    }

    @Override
    public EntityDamageEvent getLastDamageCause() {
        return this.getParent().getLastDamageCause();
    }

    @Override
    public boolean isValid() {
        return this.getParent().isValid();
    }

    // Folia start - region threading
    @Override
    public EnderDragonPart getHandleRaw() {
        return (EnderDragonPart)this.entity;
    }
    // Folia end - region threading

    @Override
    public EnderDragonPart getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (EnderDragonPart) this.entity;
    }

    @Override
    public String toString() {
        return "CraftComplexPart";
    }
}
