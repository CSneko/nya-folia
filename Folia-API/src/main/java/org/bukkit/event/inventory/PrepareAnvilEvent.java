package org.bukkit.event.inventory;

import org.bukkit.event.HandlerList;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when an item is put in a slot for repair by an anvil.
 */
public class PrepareAnvilEvent extends com.destroystokyo.paper.event.inventory.PrepareResultEvent {

    // Paper - move HandlerList to PrepareInventoryResultEvent

    public PrepareAnvilEvent(@NotNull InventoryView inventory, @Nullable ItemStack result) {
        super(inventory, result);
    }

    @NotNull
    @Override
    public AnvilInventory getInventory() {
        return (AnvilInventory) super.getInventory();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Note: by default custom recipes in anvil are disabled
     * you should define a repair cost on the anvil inventory
     * greater or equals to zero in order to allow that.
     *
     * @param result result item
     */
    public void setResult(@Nullable ItemStack result) {
        super.setResult(result);
    }

    // Paper - move HandlerList to PrepareInventoryResultEvent
}
