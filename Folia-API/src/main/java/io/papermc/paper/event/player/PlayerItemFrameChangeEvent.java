package io.papermc.paper.event.player;

import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when an {@link ItemFrame} is having an item rotated, added, or removed from it.
 */
public class PlayerItemFrameChangeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final ItemFrame itemFrame;
    private ItemStack itemStack;
    private final ItemFrameChangeAction action;

    public PlayerItemFrameChangeEvent(@NotNull Player player, @NotNull ItemFrame itemFrame,
                                      @NotNull ItemStack itemStack, @NotNull ItemFrameChangeAction action) {
        super(player);
        this.itemFrame = itemFrame;
        this.itemStack = itemStack;
        this.action = action;
    }

    /**
     * Gets the {@link ItemFrame} involved in this event.
     * @return the {@link ItemFrame}
     */
    @NotNull
    public ItemFrame getItemFrame() {
        return itemFrame;
    }

    /**
     * Gets the {@link ItemStack} involved in this event.
     * This is the item being added, rotated, or removed from the {@link ItemFrame}.
     * <p>If this method returns air, then the resulting item in the ItemFrame will be empty.</p>
     * @return the {@link ItemStack} being added, rotated, or removed
     */
    @NotNull
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Sets the {@link ItemStack} that this {@link ItemFrame} holds.
     * If null is provided, the ItemStack will become air and the result in the ItemFrame will be empty.
     * @param itemStack {@link ItemFrame} item
     */
    public void setItemStack(@Nullable ItemStack itemStack) {
        this.itemStack = itemStack == null ? new ItemStack(Material.AIR) : itemStack;
    }

    /**
     * Gets the action that was performed on this {@link ItemFrame}.
     * @see ItemFrameChangeAction
     * @return action performed on the item frame in this event
     */
    @NotNull
    public ItemFrameChangeAction getAction() {
        return action;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum ItemFrameChangeAction {
        PLACE,
        REMOVE,
        ROTATE
    }
}
