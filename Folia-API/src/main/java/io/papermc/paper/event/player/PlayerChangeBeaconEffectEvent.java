package io.papermc.paper.event.player;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a player sets the effect for a beacon
 */
public class PlayerChangeBeaconEffectEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private PotionEffectType primary;
    private PotionEffectType secondary;
    private final Block beacon;
    private boolean consumeItem = true;

    private boolean isCancelled;

    @ApiStatus.Internal
    public PlayerChangeBeaconEffectEvent(@NotNull Player player, @Nullable PotionEffectType primary, @Nullable PotionEffectType secondary, @NotNull Block beacon) {
        super(player);
        this.primary = primary;
        this.secondary = secondary;
        this.isCancelled = false;
        this.beacon = beacon;
    }

    /**
     * @return the primary effect
     */
    @Nullable public PotionEffectType getPrimary() {
        return primary;
    }

    /**
     * Sets the primary effect
     * <p>
     * NOTE: The primary effect still has to be one of the valid effects for a beacon.
     *
     * @param primary the primary effect
     */
    public void setPrimary(@Nullable PotionEffectType primary) {
        this.primary = primary;
    }

    /**
     * @return the secondary effect
     */
    @Nullable public PotionEffectType getSecondary() {
        return secondary;
    }

    /**
     * Sets the secondary effect
     * <p>
     * This only has an effect when the beacon is able to accept a secondary effect.
     * NOTE: The secondary effect still has to be a valid effect for a beacon.
     *
     * @param secondary the secondary effect
     */
    public void setSecondary(@Nullable PotionEffectType secondary) {
        this.secondary = secondary;
    }

    /**
     * @return the beacon block associated with this event
     */
    @NotNull
    public Block getBeacon() {
        return beacon;
    }

    /**
     * Gets if the item used to change the beacon will be consumed.
     * <p>
     * Independent of {@link #isCancelled()}. If the event is cancelled
     * the item will <b>NOT</b> be consumed.
     *
     * @return true if item will be consumed
     */
    public boolean willConsumeItem() {
        return consumeItem;
    }

    /**
     * Sets if the item used to change the beacon should be consumed.
     * <p>
     * Independent of {@link #isCancelled()}. If the event is cancelled
     * the item will <b>NOT</b> be consumed.
     *
     * @param consumeItem true if item should be consumed
     */
    public void setConsumeItem(boolean consumeItem) {
        this.consumeItem = consumeItem;
    }

    /**
     * Gets the cancellation state of this event. A cancelled event will not
     * be executed in the server, but will still pass to other plugins
     * <p>
     * If a {@link PlayerChangeBeaconEffectEvent} is cancelled, the changes will
     * not take effect
     *
     * @return true if this event is cancelled
     */
    @Override
    public boolean isCancelled() {
        return this.isCancelled;
    }

    /**
     * Sets the cancellation state of this event. A cancelled event will not
     * be executed in the server, but will still pass to other plugins
     * <p>
     * If cancelled, the item will <b>NOT</b> be consumed regardless of what {@link #willConsumeItem()} says
     * <p>
     * If a {@link PlayerChangeBeaconEffectEvent} is cancelled, the changes will not be applied
     * or saved.
     *
     * @param cancel true if you wish to cancel this event
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.isCancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
