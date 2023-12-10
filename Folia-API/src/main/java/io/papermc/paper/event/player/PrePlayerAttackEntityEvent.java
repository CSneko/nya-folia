package io.papermc.paper.event.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the player tries to attack an entity.
 *
 * This occurs before any of the damage logic, so cancelling this event
 * will prevent any sort of sounds from being played when attacking.
 *
 * This event will fire as cancelled for certain entities, with {@link PrePlayerAttackEntityEvent#willAttack()} being false
 * to indicate that this entity will not actually be attacked.
 * <p>
 * Note: there may be other factors (invulnerability, etc) that will prevent this entity from being attacked that this event will not cover
 */
public class PrePlayerAttackEntityEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    @NotNull
    private final Entity attacked;
    private boolean cancelled;
    private final boolean willAttack;

    public PrePlayerAttackEntityEvent(@NotNull Player who, @NotNull Entity attacked, boolean willAttack) {
        super(who);
        this.attacked = attacked;
        this.willAttack = willAttack;
        this.cancelled = !willAttack;
    }

    /**
     * Gets the entity that was attacked in this event.
     * @return entity that was attacked
     */
    @NotNull
    public Entity getAttacked() {
        return this.attacked;
    }

    /**
     * Gets if this entity will be attacked normally.
     * Entities like falling sand will return false because
     * their entity type does not allow them to be attacked.
     * <p>
     * Note: there may be other factors (invulnerability, etc) that will prevent this entity from being attacked that this event will not cover
     * @return if the entity will actually be attacked
     */
    public boolean willAttack() {
        return this.willAttack;
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

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Sets if this attack should be cancelled, note if {@link PrePlayerAttackEntityEvent#willAttack()} returns false
     * this event will always be cancelled.
     * @param cancel true if you wish to cancel this event
     */
    @Override
    public void setCancelled(boolean cancel) {
        if (!this.willAttack) {
            return;
        }

        this.cancelled = cancel;
    }
}
