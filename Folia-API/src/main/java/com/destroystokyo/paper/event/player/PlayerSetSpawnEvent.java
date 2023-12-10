package com.destroystokyo.paper.event.player;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a player's spawn is set, either by themselves or otherwise.<br>
 * Cancelling this event will prevent the spawn from being set.
 */
public class PlayerSetSpawnEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Cause cause;
    private Location location;
    private boolean forced;
    private boolean notifyPlayer;
    private Component notification;

    private boolean cancelled;

    public PlayerSetSpawnEvent(@NotNull Player who, @NotNull Cause cause, @Nullable Location location, boolean forced, boolean notifyPlayer, @Nullable Component notification) {
        super(who);
        this.cause = cause;
        this.location = location;
        this.forced = forced;
        this.notifyPlayer = notifyPlayer;
        this.notification = notification;
    }

    /**
     * Gets the cause of this event.
     *
     * @return the cause
     */
    @NotNull
    public Cause getCause() {
        return cause;
    }

    /**
     * Gets the location that the spawn is set to. The yaw
     * of this location is the spawn angle. Mutating this location
     * will change the resulting spawn point of the player. Use
     * {@link Location#clone()} to get a copy of this location.
     *
     * @return the spawn location, or null if removing the location
     */
    @Nullable
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the location to be set as the spawn location. The yaw
     * of this location is the spawn angle.
     *
     * @param location the spawn location, or null to remove the spawn location
     */
    public void setLocation(@Nullable Location location) {
        this.location = location;
    }

    /**
     * Gets if this is a force spawn location
     *
     * @return true if forced
     */
    public boolean isForced() {
        return forced;
    }

    /**
     * Sets if this is a forced spawn location
     *
     * @param forced true to force
     */
    public void setForced(boolean forced) {
        this.forced = forced;
    }

    /**
     * Gets if this action will notify the player their spawn
     * has been set.
     *
     * @return true to notify
     */
    public boolean willNotifyPlayer() {
        return notifyPlayer;
    }

    /**
     * Sets if this action will notify the player that their spawn
     * has been set.
     *
     * @param notifyPlayer true to notify
     */
    public void setNotifyPlayer(boolean notifyPlayer) {
        this.notifyPlayer = notifyPlayer;
    }

    /**
     * Gets the notification message that will be sent to the player
     * if {@link #willNotifyPlayer()} returns true.
     *
     * @return null if no notification
     */
    @Nullable
    public Component getNotification() {
        return notification;
    }

    /**
     * Sets the notification message that will be sent to the player.
     *
     * @param notification null to send no message
     */
    public void setNotification(@Nullable Component notification) {
        this.notification = notification;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    public enum Cause {
        /**
         * When a player interacts successfully with a bed.
         */
        BED,
        /**
         * When a player interacts successfully with a respawn anchor.
         */
        RESPAWN_ANCHOR,
        /**
         * When a player respawns.
         */
        PLAYER_RESPAWN,
        /**
         * When the {@code /spawnpoint} command is used on a player.
         */
        COMMAND,
        /**
         * When a plugin uses {@link Player#setBedSpawnLocation(Location)} or
         * {@link Player#setBedSpawnLocation(Location, boolean)}.
         */
        PLUGIN,
        /**
         * Fallback cause.
         */
        UNKNOWN,
    }
}
