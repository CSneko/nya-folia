package io.papermc.paper.event.player;

import com.google.common.base.Preconditions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player receives an item cooldown.
 */
public class PlayerItemCooldownEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    @NotNull
    private final Material type;
    private boolean cancelled;
    private int cooldown;

    public PlayerItemCooldownEvent(@NotNull Player player, @NotNull Material type, int cooldown) {
        super(player);
        this.type = type;
        this.cooldown = cooldown;
    }

    /**
     * Get the material affected by the cooldown.
     *
     * @return material affected by the cooldown
     */
    @NotNull
    public Material getType() {
        return type;
    }

    /**
     * Gets the cooldown in ticks.
     *
     * @return cooldown in ticks
     */
    public int getCooldown() {
        return cooldown;
    }

    /**
     * Sets the cooldown of the material in ticks.
     * Setting the cooldown to 0 results in removing an already existing cooldown for the material.
     *
     * @param cooldown cooldown in ticks, has to be a positive number
     */
    public void setCooldown(int cooldown) {
        Preconditions.checkArgument(cooldown >= 0, "The cooldown has to be equal to or greater than 0!");
        this.cooldown = cooldown;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
