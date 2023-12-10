package io.papermc.paper.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BlockPreDispenseEvent extends BlockEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final ItemStack itemStack;
    private final int slot;

    public BlockPreDispenseEvent(@NotNull Block block, @NotNull ItemStack itemStack, int slot) {
        super(block);
        this.itemStack = itemStack;
        this.slot = slot;
    }

    /**
     * Gets the {@link ItemStack} to be dispensed.
     *
     * @return The item to be dispensed
     */
    @NotNull
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Gets the inventory slot of the dispenser to dispense from.
     *
     * @return The inventory slot
     */
    public int getSlot() {
        return slot;
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
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
