package io.papermc.paper.event.player;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player begins editing a sign's text.
 * <p>
 * Cancelling this event stops the sign editing menu from opening.
 */
public class PlayerOpenSignEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private boolean cancel = false;
    private final Sign sign;
    private final Side side;
    private final Cause cause;

    @ApiStatus.Internal
    public PlayerOpenSignEvent(final @NotNull Player editor, final @NotNull Sign sign, final @NotNull Side side, final @NotNull Cause cause) {
        super(editor);
        this.sign = sign;
        this.side = side;
        this.cause = cause;
    }

    /**
     * Gets the sign that was clicked.
     *
     * @return {@link Sign} that was clicked
     */
    @NotNull
    public Sign getSign() {
        return sign;
    }

    /**
     * Gets which side of the sign was clicked.
     *
     * @return {@link Side} that was clicked
     * @see Sign#getSide(Side)
     */
    @NotNull
    public Side getSide() {
        return side;
    }

    /**
     * The cause of this sign open.
     *
     * @return the cause
     */
    public @NotNull Cause getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
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

    /**
     * The cause of the {@link PlayerOpenSignEvent}.
     */
    public enum Cause {
        /**
         * The event was triggered by the placement of a sign.
         */
        PLACE,
        /**
         * The event was triggered by an interaction with a sign.
         */
        INTERACT,
        /**
         * The event was triggered via a plugin with {@link org.bukkit.entity.HumanEntity#openSign(Sign, Side)}
         */
        PLUGIN,
        /**
         * Fallback cause for any unknown cause.
         */
        UNKNOWN,
    }
}
