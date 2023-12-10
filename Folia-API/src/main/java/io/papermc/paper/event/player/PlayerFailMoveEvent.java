package io.papermc.paper.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Runs when a player attempts to move, but is prevented from doing so by the server
 */
public class PlayerFailMoveEvent extends PlayerEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final FailReason failReason;
    private boolean allowed;
    private boolean logWarning;
    private final Location from;
    private final Location to;

    public PlayerFailMoveEvent(@NotNull Player who, @NotNull FailReason failReason, boolean allowed,
                               boolean logWarning, @NotNull Location from, @NotNull Location to) {
        super(who);
        this.failReason = failReason;
        this.allowed = allowed;
        this.logWarning = logWarning;
        this.from = from;
        this.to = to;
    }

    /**
     * Gets the reason this movement was prevented by the server
     * @return The reason the movement was prevented
     */
    @NotNull
    public FailReason getFailReason() {
        return failReason;
    }

    /**
     * Gets the location this player moved from
     * @return Location the player moved from
     */
    @NotNull
    public Location getFrom() {
        return from.clone();
    }

    /**
     * Gets the location this player tried to move to
     * @return Location the player tried to move to
     */
    @NotNull
    public Location getTo() {
        return to.clone();
    }

    /**
     * Gets if the check should be bypassed, allowing the movement
     * @return whether to bypass the check
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Set if the check should be bypassed and the movement should be allowed
     * @param allowed whether to bypass the check
     */
    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    /**
     * Gets if warnings will be printed to console. eg. "Player123 moved too quickly!"
     * @return whether to log warnings
     */
    public boolean getLogWarning() {
        return logWarning;
    }

    /**
     * Set if a warning is printed to console. eg. "Player123 moved too quickly!"
     * @param logWarning whether to log warnings
     */
    public void setLogWarning(boolean logWarning) {
        this.logWarning = logWarning;
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

    public enum FailReason {
        MOVED_INTO_UNLOADED_CHUNK, // Only fired if the world setting prevent-moving-into-unloaded-chunks is true
        MOVED_TOO_QUICKLY,
        MOVED_WRONGLY,
        CLIPPED_INTO_BLOCK
    }

}
