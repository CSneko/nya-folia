package io.papermc.paper.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Warden;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a Warden's anger level has changed due to another entity.
 * <p>
 * If the event is cancelled, the warden's anger level will not change.
 */
public class WardenAngerChangeEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private boolean cancelled;
    private final Entity target;
    private final int oldAnger;
    private int newAnger;

    @ApiStatus.Internal
    public WardenAngerChangeEvent(@NotNull final Warden warden, @NotNull final Entity target, final int oldAnger, final int newAnger) {
        super(warden);
        this.target = target;
        this.oldAnger = oldAnger;
        this.newAnger = newAnger;
    }

    /**
     * Gets the entity which triggered this anger update.
     *
     * @return triggering entity
     */
    @NotNull
    public Entity getTarget() {
        return target;
    }

    /**
     * Gets the old anger level.
     *
     * @return old anger level
     * @see Warden#getAnger(Entity)
     */
    public int getOldAnger() {
        return oldAnger;
    }

    /**
     * Gets the new anger level resulting from this event.
     *
     * @return new anger level
     * @see Warden#getAnger(Entity)
     */
    public int getNewAnger() {
        return newAnger;
    }

    /**
     * Sets the new anger level resulting from this event.
     * <p>
     * The anger of a warden is capped at 150.
     *
     * @param newAnger the new anger level, max 150
     * @see Warden#setAnger(Entity, int)
     * @throws IllegalArgumentException if newAnger is greater than 150
     */
    public void setNewAnger(int newAnger) {
        if (newAnger > 150)
            throw new IllegalArgumentException("newAnger must not be greater than 150");

        this.newAnger = newAnger;
    }

    @NotNull
    @Override
    public Warden getEntity() {
        return (Warden) entity;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
