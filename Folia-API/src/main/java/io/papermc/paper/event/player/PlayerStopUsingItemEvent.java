package io.papermc.paper.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the server detects a player stopping using an item.
 * Examples of this are letting go of the interact button when holding a bow, an edible item, or a spyglass.
 */
public class PlayerStopUsingItemEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    @NotNull private final ItemStack item;
    private final int ticksHeldFor;

    public PlayerStopUsingItemEvent(@NotNull final Player player, @NotNull final ItemStack item, final int ticksHeldFor) {
        super(player);
        this.item = item;
        this.ticksHeldFor = ticksHeldFor;
    }

    /**
     * Gets the exact item the player is releasing
     *
     * @return ItemStack the exact item the player released
     */
    @NotNull
    public ItemStack getItem() {
        return item;
    }

    /**
     * Gets the number of ticks the item was held for
     *
     * @return int the number of ticks the item was held for
     */
    public int getTicksHeldFor() {
        return ticksHeldFor;
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
