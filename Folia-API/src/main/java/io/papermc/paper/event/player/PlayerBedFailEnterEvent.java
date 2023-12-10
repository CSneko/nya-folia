package io.papermc.paper.event.player;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerBedFailEnterEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final FailReason failReason;
    private final Block bed;
    private boolean willExplode;
    private Component message;
    private boolean cancelled;

    public PlayerBedFailEnterEvent(@NotNull Player player, @NotNull FailReason failReason, @NotNull Block bed, boolean willExplode, @Nullable Component message) {
        super(player);
        this.failReason = failReason;
        this.bed = bed;
        this.willExplode = willExplode;
        this.message = message;
    }

    @NotNull
    public FailReason getFailReason() {
        return failReason;
    }

    @NotNull
    public Block getBed() {
        return bed;
    }

    public boolean getWillExplode() {
        return willExplode;
    }

    public void setWillExplode(boolean willExplode) {
        this.willExplode = willExplode;
    }

    @Nullable
    public Component getMessage() {
        return message;
    }

    public void setMessage(@Nullable Component message) {
        this.message = message;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancel this event.
     * <p>
     * <b>NOTE: This does not cancel the player getting in the bed, but any messages/explosions
     * that may occur because of the interaction.</b>
     * @param cancel true if you wish to cancel this event
     */
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

    public static enum FailReason {
        /**
         * The world doesn't allow sleeping (ex. Nether or The End). Entering
         * the bed is prevented and the bed explodes.
         */
        NOT_POSSIBLE_HERE,
        /**
         * Entering the bed is prevented due to it not being night nor
         * thundering currently.
         * <p>
         * If the event is forcefully allowed during daytime, the player will
         * enter the bed (and set its bed location), but might get immediately
         * thrown out again.
         */
        NOT_POSSIBLE_NOW,
        /**
         * Entering the bed is prevented due to the player being too far away.
         */
        TOO_FAR_AWAY,
        /**
         * Bed is obstructed.
         */
        OBSTRUCTED,
        /**
         * Entering the bed is prevented due to there being some other problem.
         */
        OTHER_PROBLEM,
        /**
         * Entering the bed is prevented due to there being monsters nearby.
         */
        NOT_SAFE;

        public static final FailReason[] VALUES = values();
    }
}
