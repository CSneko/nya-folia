package com.destroystokyo.paper.event.inventory;

import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when an item is put in an inventory containing a result slot
 */
public class PrepareResultEvent extends PrepareInventoryResultEvent {

    // HandlerList on PrepareInventoryResultEvent to ensure api compat
    public PrepareResultEvent(@NotNull InventoryView inventory, @Nullable ItemStack result) {
        super(inventory, result);
    }

    /**
     * Get result item, may be null.
     *
     * @return result item
     */
    @Nullable
    public ItemStack getResult() {
        return super.getResult();
    }

    /**
     * Set result item, may be null.
     *
     * @param result result item
     */
    public void setResult(@Nullable ItemStack result) {
        super.setResult(result);
    }
}
