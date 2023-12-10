package com.destroystokyo.paper.event.entity;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an Entity is knocked back by the hit of another Entity. The acceleration
 * vector can be modified. If this event is cancelled, the entity is not knocked back.
 *
 */
public class EntityKnockbackByEntityEvent extends EntityPushedByEntityAttackEvent {
    private final float knockbackStrength;

    public EntityKnockbackByEntityEvent(@NotNull LivingEntity entity, @NotNull Entity hitBy, float knockbackStrength, @NotNull Vector acceleration) {
        super(entity, hitBy, acceleration);
        this.knockbackStrength = knockbackStrength;
    }

    /**
     * @return the entity which was knocked back
     */
    @NotNull
    @Override
    public LivingEntity getEntity() {
        return (LivingEntity) super.getEntity();
    }

    /**
     * @return the original knockback strength.
     */
    public float getKnockbackStrength() {
        return knockbackStrength;
    }

    /**
     * @return the Entity which hit
     */
    @NotNull
    public Entity getHitBy() {
        return super.getPushedBy();
    }

}
