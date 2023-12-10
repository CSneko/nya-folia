package org.bukkit.event.block;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a block explodes interacting with blocks. The
 * event isn't called if the {@link org.bukkit.GameRule#MOB_GRIEFING}
 * is disabled as no block interaction will occur.
 * <p>
 * The {@link Block} returned by this event is not necessarily
 * the block that caused the explosion, just the block at the location where
 * the explosion originated. See {@link #getExplodedBlockState()}
 */
public class BlockExplodeEvent extends BlockEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancel;
    private final List<Block> blocks;
    private float yield;
    private final org.bukkit.block.BlockState explodedBlockState; // Paper

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public BlockExplodeEvent(@NotNull final Block what, @NotNull final List<Block> blocks, final float yield) {
        // Paper start
        this(what, blocks, yield, null);
    }
    @org.jetbrains.annotations.ApiStatus.Internal
    public BlockExplodeEvent(@NotNull final Block what, @NotNull final List<Block> blocks, final float yield, @org.jetbrains.annotations.Nullable org.bukkit.block.BlockState explodedBlockState) {
        // Paper end
        super(what);
        this.blocks = blocks;
        this.yield = yield;
        this.cancel = false;
        this.explodedBlockState = explodedBlockState; // Paper
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    // Paper start
    /**
     * Get a capture of the block that directly caused
     * the explosion, like a bed or respawn anchor. This
     * block state is not placed so {@link org.bukkit.block.BlockState#isPlaced}
     * will be false.
     * <p>
     * Can be null if no block directly caused the explosion.
     *
     * @return the exploded block state or null if not applicable
     */
    public @org.jetbrains.annotations.Nullable org.bukkit.block.BlockState getExplodedBlockState() {
        return this.explodedBlockState;
    }
    // Paper end

    /**
     * Returns the list of blocks that would have been removed or were removed
     * from the explosion event.
     *
     * @return All blown-up blocks
     */
    @NotNull
    public List<Block> blockList() {
        return blocks;
    }

    /**
     * Returns the percentage of blocks to drop from this explosion
     *
     * @return The yield.
     */
    public float getYield() {
        return yield;
    }

    /**
     * Sets the percentage of blocks to drop from this explosion
     *
     * @param yield The new yield percentage
     */
    public void setYield(float yield) {
        this.yield = yield;
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
