package io.papermc.paper.event.entity;

import org.bukkit.DyeColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when an entity is dyed. Currently, this is called for {@link org.bukkit.entity.Sheep}
 * being dyed, and {@link org.bukkit.entity.Wolf}/{@link org.bukkit.entity.Cat} collars being dyed.
 */
public class EntityDyeEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private DyeColor dyeColor;
    private final Player player;
    private boolean cancel;

    public EntityDyeEvent(@NotNull Entity entity, @NotNull DyeColor dyeColor, @Nullable Player player) {
        super(entity);
        this.dyeColor = dyeColor;
        this.player = player;
    }

    /**
     * Gets the DyeColor the entity is being dyed
     *
     * @return the DyeColor the entity is being dyed
     */
    public @NotNull DyeColor getColor() {
        return this.dyeColor;
    }

    /**
     * Sets the DyeColor the entity is being dyed
     *
     * @param dyeColor the DyeColor the entity will be dyed
     */
    public void setColor(@NotNull DyeColor dyeColor) {
        this.dyeColor = dyeColor;
    }

    /**
     * Returns the player dyeing the entity, if available.
     *
     * @return player or null
     */
    public @Nullable Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
