package io.papermc.paper.event.entity;

import com.google.common.base.Preconditions;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Holds information for living entity movement events
 * <p>
 * Does not fire for players; use {@link PlayerMoveEvent} for player movement.
 */
public class EntityMoveEvent extends EntityEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean canceled;
    private Location from;
    private Location to;

    public EntityMoveEvent(@NotNull LivingEntity entity, @NotNull Location from, @NotNull Location to) {
        super(entity);
        this.from = from;
        this.to = to;
    }

    @Override
    @NotNull
    public LivingEntity getEntity() {
        return (LivingEntity) entity;
    }

    public boolean isCancelled() {
        return canceled;
    }

    public void setCancelled(boolean cancel) {
        canceled = cancel;
    }

    /**
     * Gets the location this entity moved from
     *
     * @return Location the entity moved from
     */
    @NotNull
    public Location getFrom() {
        return from;
    }

    /**
     * Sets the location to mark as where the entity moved from
     *
     * @param from New location to mark as the entity's previous location
     */
    public void setFrom(@NotNull Location from) {
        validateLocation(from);
        this.from = from;
    }

    /**
     * Gets the location this entity moved to
     *
     * @return Location the entity moved to
     */
    @NotNull
    public Location getTo() {
        return to;
    }

    /**
     * Sets the location that this entity will move to
     *
     * @param to New Location this entity will move to
     */
    public void setTo(@NotNull Location to) {
        validateLocation(to);
        this.to = to;
    }

    /**
     * Check if the entity has changed position (even within the same block) in the event
     *
     * @return whether the entity has changed position or not
     */
    public boolean hasChangedPosition() {
        return hasExplicitlyChangedPosition() || !from.getWorld().equals(to.getWorld());
    }

    /**
     * Check if the entity has changed position (even within the same block) in the event, disregarding a possible world change
     *
     * @return whether the entity has changed position or not
     */
    public boolean hasExplicitlyChangedPosition() {
        return from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    /**
     * Check if the entity has moved to a new block in the event
     *
     * @return whether the entity has moved to a new block or not
     */
    public boolean hasChangedBlock() {
        return hasExplicitlyChangedBlock() || !from.getWorld().equals(to.getWorld());
    }

    /**
     * Check if the entity has moved to a new block in the event, disregarding a possible world change
     *
     * @return whether the entity has moved to a new block or not
     */
    public boolean hasExplicitlyChangedBlock() {
        return from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
    }

    /**
     * Check if the entity has changed orientation in the event
     *
     * @return whether the entity has changed orientation or not
     */
    public boolean hasChangedOrientation() {
        return from.getPitch() != to.getPitch() || from.getYaw() != to.getYaw();
    }

    private void validateLocation(@NotNull Location loc) {
        Preconditions.checkArgument(loc != null, "Cannot use null location!");
        Preconditions.checkArgument(loc.getWorld() != null, "Cannot use null location with null world!");
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
