package io.papermc.paper.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a slot contents change in a player's inventory.
 */
public class PlayerInventorySlotChangeEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final int rawSlot;
    private final int slot;
    private final ItemStack oldItemStack;
    private final ItemStack newItemStack;
    private boolean triggerAdvancements = true;

    public PlayerInventorySlotChangeEvent(@NotNull Player player, int rawSlot, @NotNull ItemStack oldItemStack, @NotNull ItemStack newItemStack) {
        super(player);
        this.rawSlot = rawSlot;
        this.slot = player.getOpenInventory().convertSlot(rawSlot);
        this.oldItemStack = oldItemStack;
        this.newItemStack = newItemStack;
    }

    /**
     * The raw slot number that was changed.
     *
     * @return The raw slot number.
     */
    public int getRawSlot() {
        return rawSlot;
    }

    /**
     * The slot number that was changed, ready for passing to
     * {@link Inventory#getItem(int)}. Note that there may be two slots with
     * the same slot number, since a view links two different inventories.
     * <p>
     * If no inventory is opened, internal crafting view is used for conversion.
     *
     * @return The slot number.
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Clone of ItemStack that was in the slot before the change.
     *
     * @return The old ItemStack in the slot.
     */
    @NotNull
    public ItemStack getOldItemStack() {
        return oldItemStack;
    }

    /**
     * Clone of ItemStack that is in the slot after the change.
     *
     * @return The new ItemStack in the slot.
     */
    @NotNull
    public ItemStack getNewItemStack() {
        return newItemStack;
    }

    /**
     * Gets whether the slot change advancements will be triggered.
     *
     * @return Whether the slot change advancements will be triggered.
     */
    public boolean shouldTriggerAdvancements() {
        return triggerAdvancements;
    }

    /**
     * Sets whether the slot change advancements will be triggered.
     *
     * @param triggerAdvancements Whether the slot change advancements will be triggered.
     */
    public void setShouldTriggerAdvancements(boolean triggerAdvancements) {
        this.triggerAdvancements = triggerAdvancements;
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
