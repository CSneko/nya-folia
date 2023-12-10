package io.papermc.paper.event.block;

import com.google.common.base.Preconditions;
import io.papermc.paper.block.LockableTileState;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Called when the server tries to check the lock on a lockable tile entity.
 * @see #setResult(Result) to change behavior
 */
public class BlockLockCheckEvent extends BlockEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final LockableTileState state;
    private final Player player;
    private ItemStack itemStack;
    private Result result = Result.DEFAULT;
    private Component lockedMessage;
    private Sound lockedSound;

    public BlockLockCheckEvent(final @NotNull Block block, final @NotNull LockableTileState state, final @NotNull Player player, final @NotNull Component lockedMessage, final @NotNull Sound lockedSound) {
        super(block);
        this.state = state;
        this.player = player;
        this.lockedMessage = lockedMessage;
        this.lockedSound = lockedSound;
    }

    /**
     * Gets the snapshot {@link LockableTileState} of the tile entity
     * whose lock is being checked.
     *
     * @return the snapshot block state.
     */
    public @NotNull LockableTileState getBlockState() {
        return this.state;
    }

    /**
     * Get the player involved this lock check.
     *
     * @return the player
     */
    public @NotNull Player getPlayer() {
        return this.player;
    }

    /**
     * Gets the itemstack that will be used as the key itemstack. Initially
     * this will be the item in the player's main hand but an override can be set
     * with {@link #setKeyItem(ItemStack)}. Use {@link #isUsingCustomKeyItemStack()}
     * to check if a custom key stack has been set.
     *
     * @return the item being used as the key item
     * @see #isUsingCustomKeyItemStack()
     */
    public @NotNull ItemStack getKeyItem() {
        return Objects.requireNonNullElseGet(this.itemStack, this.player.getInventory()::getItemInMainHand);
    }

    /**
     * Sets the itemstack that will be used as the key item.
     *
     * @param stack the stack to use as a key (or null to fall back to the player's main hand item)
     * @see #resetKeyItem() to clear a custom key item
     */
    public void setKeyItem(@NotNull ItemStack stack) {
        Preconditions.checkNotNull(stack, "stack is null");
        this.itemStack = stack;
    }

    /**
     * Reset the key stack to the default (the player's main hand).
     */
    public void resetKeyItem() {
        this.itemStack = null;
    }

    /**
     * Checks if a custom key stack has been set.
     *
     * @return true if a custom key itemstack has been set
     */
    public boolean isUsingCustomKeyItemStack() {
        return this.itemStack != null;
    }

    /**
     * Gets the result of this event.
     *
     * @return the result
     * @see #setResult(Result)
     */
    public @NotNull Result getResult() {
        return this.result;
    }

    /**
     * Gets the result of this event. {@link org.bukkit.event.Event.Result#DEFAULT} is the default
     * allowing the vanilla logic to check the lock of this block. Set to {@link org.bukkit.event.Event.Result#ALLOW}
     * or {@link org.bukkit.event.Event.Result#DENY} to override that behavior.
     * <p>
     * Setting this to {@link org.bukkit.event.Event.Result#ALLOW} bypasses the spectator check.
     *
     * @param result the result of this event
     */
    public void setResult(@NotNull Result result) {
        this.result = result;
    }

    /**
     * Shorthand method to set the {@link #getResult()} to {@link org.bukkit.event.Event.Result#DENY},
     * the locked message and locked sound.
     *
     * @param lockedMessage the message to show if locked (or null for none)
     * @param lockedSound the sound to play if locked (or null for none)
     */
    public void denyWithMessageAndSound(@Nullable Component lockedMessage, @Nullable Sound lockedSound) {
        this.result = Result.DENY;
        this.lockedMessage = lockedMessage;
        this.lockedSound = lockedSound;
    }

    /**
     * Gets the locked message that will be sent if the
     * player cannot open the block.
     *
     * @return the locked message (or null if none)
     */
    public @Nullable Component getLockedMessage() {
        return this.lockedMessage;
    }

    /**
     * Sets the locked message that will be sent if the
     * player cannot open the block.
     *
     * @param lockedMessage the locked message (or null for none)
     */
    public void setLockedMessage(@Nullable Component lockedMessage) {
        this.lockedMessage = lockedMessage;
    }

    /**
     * Gets the locked sound that will play if the
     * player cannot open the block.
     *
     * @return the locked sound (or null if none)
     */
    public @Nullable Sound getLockedSound() {
        return this.lockedSound;
    }

    /**
     * Sets the locked sound that will play if the
     * player cannot open the block.
     *
     * @param lockedSound the locked sound (or null for none)
     */
    public void setLockedSound(@Nullable Sound lockedSound) {
        this.lockedSound = lockedSound;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
