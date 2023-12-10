package io.papermc.paper.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an item on or used by an entity takes durability damage as a result of being hit/used.
 * <p>
 * NOTE: default vanilla behaviour dictates that armor/tools picked up by
 * mobs do not take damage (except via Thorns).
 */
public class EntityDamageItemEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final ItemStack item;
    private int damage;
    private boolean cancelled;

    public EntityDamageItemEvent(@NotNull Entity entity, @NotNull ItemStack item, int damage) {
        super(entity);
        this.item = item;
        this.damage = damage;
    }

    /**
     * Gets the item being damaged.
     *
     * @return the item
     */
    @NotNull
    public ItemStack getItem() {
        return item;
    }

    /**
     * Gets the amount of durability damage this item will be taking.
     *
     * @return durability change
     */
    public int getDamage() {
        return damage;
    }

    /**
     * Sets the amount of durability damage this item will be taking.
     *
     * @param damage the damage amount to cause
     */
    public void setDamage(int damage) {
        this.damage = damage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

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
