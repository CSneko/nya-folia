package io.papermc.paper.event.entity;

import io.papermc.paper.event.block.CompostItemEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an item is about to be composted by an entity.
 */
public class EntityCompostItemEvent extends CompostItemEvent implements Cancellable {

    private final Entity who;
    private boolean cancelled;

    public EntityCompostItemEvent(@NotNull Entity who, @NotNull Block composter, @NotNull ItemStack item, boolean willRaiseLevel) {
        super(composter, item, willRaiseLevel);
        this.who = who;
    }

    /**
     * Gets the entity that interacted with the composter.
     *
     * @return the entity that composted an item.
     */
    @NotNull
    public Entity getEntity() {
        return this.who;
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
