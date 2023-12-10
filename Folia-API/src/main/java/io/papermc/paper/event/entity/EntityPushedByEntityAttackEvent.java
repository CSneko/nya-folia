package io.papermc.paper.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an entity is pushed by another entity's attack. The acceleration vector can be
 * modified. If this event is cancelled, the entity will not get pushed.
 * <p>
 * Note: Some entities might trigger this multiple times on the same entity
 * as multiple acceleration calculations are done.
 */
public class EntityPushedByEntityAttackEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final @NotNull Entity pushedBy;
    private final @NotNull Vector acceleration;
    private boolean cancelled = false;

    public EntityPushedByEntityAttackEvent(@NotNull Entity entity, @NotNull Entity pushedBy, @NotNull Vector acceleration) {
        super(entity);
        this.pushedBy = pushedBy;
        this.acceleration = acceleration;
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    /**
     * Gets the entity which pushed the affected entity.
     *
     * @return the pushing entity
     */
    @NotNull
    public Entity getPushedBy() {
        return pushedBy;
    }

    /**
     * Gets the acceleration that will be applied to the affected entity.
     *
     * @return the acceleration vector
     */
    @NotNull
    public Vector getAcceleration() {
        return acceleration;
    }
}
