package com.destroystokyo.paper.event.entity;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

import java.util.Collection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a DragonFireball collides with a block/entity and spawns an AreaEffectCloud
 */
public class EnderDragonFireballHitEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    @NotNull private final Collection<LivingEntity> targets;
    @NotNull private final AreaEffectCloud areaEffectCloud;
    private boolean cancelled = false;

    @ApiStatus.Internal
    public EnderDragonFireballHitEvent(@NotNull DragonFireball fireball, @NotNull Collection<LivingEntity> targets, @NotNull AreaEffectCloud areaEffectCloud) {
        super(fireball);
        this.targets = targets;
        this.areaEffectCloud = areaEffectCloud;
    }

    /**
     * The fireball involved in this event
     */
    @NotNull
    @Override
    public DragonFireball getEntity() {
        return (DragonFireball) super.getEntity();
    }

    /**
     * The living entities hit by fireball
     *
     * @return the targets
     */
    @NotNull
    public Collection<LivingEntity> getTargets() {
        return targets;
    }

    /**
     * @return The area effect cloud spawned in this collision
     */
    @NotNull
    public AreaEffectCloud getAreaEffectCloud() {
        return areaEffectCloud;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @NotNull
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
