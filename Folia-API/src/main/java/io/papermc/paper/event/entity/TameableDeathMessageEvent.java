package io.papermc.paper.event.entity;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a {@link Tameable} dies and sends a death message.
 */
public class TameableDeathMessageEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private Component deathMessage;

    public TameableDeathMessageEvent(@NotNull Tameable what, @NotNull Component deathMessage) {
        super(what);
        this.deathMessage = deathMessage;
    }

    /**
     * Set the death message that appears to the owner of the tameable.
     *
     * @param deathMessage Death message to appear
     */
    public void deathMessage(@NotNull Component deathMessage) {
        this.deathMessage = deathMessage;
    }

    /**
     * Get the death message that appears to the owner of the tameable.
     *
     * @return Death message to appear
     */
    @NotNull
    public Component deathMessage() {
        return deathMessage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public Tameable getEntity() {
        return (Tameable) super.getEntity();
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
