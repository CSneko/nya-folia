package com.destroystokyo.paper.event.entity;

import com.google.common.base.Preconditions;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;

/**
 * WARNING: This event only fires for a limited number of cases, and not for every case that CreatureSpawnEvent does.
 *
 * You should still listen to CreatureSpawnEvent as a backup, and only use this event as an "enhancement".
 * The intent of this event is to improve server performance, so it fires even if the spawning might fail later, for
 * example when the entity would be unable to spawn due to limited space or lighting.
 * 
 * Currently: NATURAL and SPAWNER based reasons. Please submit a Pull Request for future additions.
 * Also, Plugins that replace Entity Registrations with their own custom entities might not fire this event.
 */
public class PreCreatureSpawnEvent extends Event implements Cancellable {
    @NotNull private final Location location;
    @NotNull private final EntityType type;
    @NotNull private final CreatureSpawnEvent.SpawnReason reason;
    private boolean shouldAbortSpawn;

    public PreCreatureSpawnEvent(@NotNull Location location, @NotNull EntityType type, @NotNull CreatureSpawnEvent.SpawnReason reason) {
        this.location = Preconditions.checkNotNull(location, "Location may not be null");
        this.type = Preconditions.checkNotNull(type, "Type may not be null");
        this.reason = Preconditions.checkNotNull(reason, "Reason may not be null");
    }

    /**
     * @return The location this creature is being spawned at
     */
    @NotNull
    public Location getSpawnLocation() {
        return location;
    }

    /**
     * @return The type of creature being spawned
     */
    @NotNull
    public EntityType getType() {
        return type;
    }

    /**
     * @return Reason this creature is spawning (ie, NATURAL vs SPAWNER)
     */
    @NotNull
    public CreatureSpawnEvent.SpawnReason getReason() {
        return reason;
    }

    /**
     * @return If the spawn process should be aborted vs trying more attempts
     */
    public boolean shouldAbortSpawn() {
        return shouldAbortSpawn;
    }

    /**
     * Set this if you are more blanket blocking all types of these spawns, and wish to abort the spawn process from
     * trying more attempts after this cancellation.
     *
     * @param shouldAbortSpawn Set if the spawn process should be aborted vs trying more attempts
     */
    public void setShouldAbortSpawn(boolean shouldAbortSpawn) {
        this.shouldAbortSpawn = shouldAbortSpawn;
    }

    private static final HandlerList handlers = new HandlerList();

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    private boolean cancelled = false;

    /**
     * @return If the spawn of this creature is cancelled or not
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancelling this event is more efficient than cancelling CreatureSpawnEvent
     * @param cancel true if you wish to cancel this event, and abort the spawn of this creature
     */
    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
