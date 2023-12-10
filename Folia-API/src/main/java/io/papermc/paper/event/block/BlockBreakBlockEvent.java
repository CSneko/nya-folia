package io.papermc.paper.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Called when a block forces another block to break and drop items.
 * <p>
 * Currently called for piston's and liquid flows.
 */
public class BlockBreakBlockEvent extends BlockEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final List<ItemStack> drops;
    private final Block source;

    public BlockBreakBlockEvent(@NotNull Block block, @NotNull Block source, @NotNull List<ItemStack> drops) {
        super(block);
        this.source = source;
        this.drops = drops;
    }

    /**
     * Get the drops of this event
     *
     * @return the drops
     */
    @NotNull
    public List<ItemStack> getDrops() {
        return drops;
    }

    /**
     * Gets the block that cause this (e.g. a piston, or adjacent liquid)
     *
     * @return the source
     */
    @NotNull
    public Block getSource() {
        return source;
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
