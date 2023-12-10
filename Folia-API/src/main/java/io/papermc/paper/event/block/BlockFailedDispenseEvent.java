package io.papermc.paper.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a block tries to dispense an item, but its inventory is empty.
 */
public class BlockFailedDispenseEvent extends BlockEvent {
    private static final HandlerList handlers = new HandlerList();

    private boolean shouldPlayEffect = true;

    public BlockFailedDispenseEvent(@NotNull Block theBlock) {
        super(theBlock);
    }

    /**
     * @return if the effect should be played
     */
    public boolean shouldPlayEffect() {
        return this.shouldPlayEffect;
    }

    /**
     * Sets if the effect for empty dispensers should be played
     *
     * @param playEffect if the effect should be played
     */
    public void shouldPlayEffect(boolean playEffect) {
        this.shouldPlayEffect = playEffect;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link #shouldPlayEffect()}
     */
    @Override
    public boolean callEvent() {
        super.callEvent();
        return this.shouldPlayEffect();
    }

    @Override
    public @NotNull
    HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull
    HandlerList getHandlerList() {
        return handlers;
    }
}
