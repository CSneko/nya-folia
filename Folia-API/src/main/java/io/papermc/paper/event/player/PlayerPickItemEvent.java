package io.papermc.paper.event.player;

import org.apache.commons.lang3.Validate;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

/**
 * Event that is fired when a player uses the pick item functionality (middle-clicking a block to get the appropriate
 * item). However, note that this event will only trigger if an item has to be moved from the inventory to the hotbar.
 * After the handling of this event, the contents of the source and the target slot will be swapped and the currently
 * selected hotbar slot of the player will be set to the target slot.
 * <p>
 * Note: This event will not be fired for players in creative mode.
 */
public class PlayerPickItemEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private int targetSlot;
    private int sourceSlot;
    private boolean cancelled;

    public PlayerPickItemEvent(@NotNull Player player, int targetSlot, int sourceSlot) {
        super(player);
        this.targetSlot = targetSlot;
        this.sourceSlot = sourceSlot;
    }

    /**
     * Returns the slot the item that is being picked goes into.
     *
     * @return hotbar slot (0-8 inclusive)
     */
    @Range(from = 0, to = 8)
    public int getTargetSlot() {
        return this.targetSlot;
    }

    /**
     * Changes the slot the item that is being picked goes into.
     *
     * @param targetSlot hotbar slot (0-8 inclusive)
     */
    public void setTargetSlot(@Range(from = 0, to = 8) int targetSlot) {
        Validate.isTrue(targetSlot >= 0 && targetSlot <= 8, "Target slot must be in range 0 - 8 (inclusive)");
        this.targetSlot = targetSlot;
    }

    /**
     * Returns the slot in which the item that will be put into the players hotbar is located.
     *
     * @return player inventory slot (0-35 inclusive)
     */
    @Range(from = 0, to = 35)
    public int getSourceSlot() {
        return this.sourceSlot;
    }

    /**
     * Change the source slot from which the item that will be put in the players hotbar will be taken.
     *
     * @param sourceSlot player inventory slot (0-35 inclusive)
     */
    public void setSourceSlot(@Range(from = 0, to = 35) int sourceSlot) {
        Validate.isTrue(sourceSlot >= 0 && sourceSlot <= 35, "Source slot must be in range of the players inventorys slot ids");
        this.sourceSlot = sourceSlot;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
