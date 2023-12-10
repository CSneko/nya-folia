package io.papermc.paper.event.player;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when the player is attempting to rename a mob
 */
public class PlayerNameEntityEvent extends PlayerEvent implements Cancellable {

    private LivingEntity entity;
    private Component name;
    private boolean persistent;
    private boolean cancelled;

    public PlayerNameEntityEvent(@NotNull Player player, @NotNull LivingEntity entity, @NotNull Component name, boolean persistent) {
        super(player);
        this.entity = entity;
        this.name = name;
        this.persistent = persistent;
    }

    /**
     * Gets the name to be given to the entity.
     * @return the name
     */
    @Nullable
    public Component getName() {
        return name;
    }

    /**
     * Sets the name to be given to the entity.
     *
     * @param name the name
     */
    public void setName(@Nullable Component name) {
        this.name = name;
    }

    /**
     * Gets the entity involved in this event.
     *
     * @return the entity
     */
    @NotNull
    public LivingEntity getEntity() {
        return entity;
    }

    /**
     * Sets the entity involved in this event.
     *
     * @param entity the entity
     */
    public void setEntity(@NotNull LivingEntity entity) {
        this.entity = entity;
    }

    /**
     * Gets whether this will set the mob to be persistent.
     *
     * @return persistent
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Sets whether this will set the mob to be persistent.
     *
     * @param persistent persistent
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Gets the cancellation state of this event. A cancelled event will not
     * be executed in the server, but will still pass to other plugins
     *
     * @return true if this event is cancelled
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the cancellation state of this event. A cancelled event will not
     * be executed in the server, but will still pass to other plugins.
     *
     * @param cancel true if you wish to cancel this event
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    private static final HandlerList HANDLER_LIST = new HandlerList();

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
