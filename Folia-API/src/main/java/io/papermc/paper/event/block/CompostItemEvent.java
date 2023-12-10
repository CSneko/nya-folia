package io.papermc.paper.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an item is about to be composted by a hopper.
 * To prevent hoppers from moving items into composters, cancel the {@link InventoryMoveItemEvent}.
 */
public class CompostItemEvent extends BlockEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ItemStack item;
    private boolean willRaiseLevel;

    public CompostItemEvent(@NotNull Block composter, @NotNull ItemStack item, boolean willRaiseLevel) {
        super(composter);
        this.item = item;
        this.willRaiseLevel = willRaiseLevel;
    }

    /**
     * Gets the item that was used on the composter.
     *
     * @return the item
     */
    @NotNull
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Gets whether the composter will rise a level.
     *
     * @return true if successful
     */
    public boolean willRaiseLevel() {
        return this.willRaiseLevel;
    }

    /**
     * Sets whether the composter will rise a level.
     *
     * @param willRaiseLevel true if the composter should rise a level
     */
    public void setWillRaiseLevel(boolean willRaiseLevel) {
        this.willRaiseLevel = willRaiseLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
