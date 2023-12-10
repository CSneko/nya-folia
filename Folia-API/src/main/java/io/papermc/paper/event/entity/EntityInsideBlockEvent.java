package io.papermc.paper.event.entity;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an entity enters the hitbox of a block.
 * Only called for blocks that react when an entity is inside.
 * If cancelled, any action that would have resulted from that entity
 * being in the block will not happen (such as extinguishing an entity in a cauldron).
 * <p>
 * Blocks this is currently called for:
 * <ul>
 *     <li>Big dripleaf</li>
 *     <li>Bubble column</li>
 *     <li>Buttons</li>
 *     <li>Cactus</li>
 *     <li>Campfire</li>
 *     <li>Cauldron</li>
 *     <li>Crops</li>
 *     <li>Ender Portal</li>
 *     <li>Fires</li>
 *     <li>Frogspawn</li>
 *     <li>Honey</li>
 *     <li>Hopper</li>
 *     <li>Detector rails</li>
 *     <li>Nether portals</li>
 *     <li>Pitcher crop</li>
 *     <li>Powdered snow</li>
 *     <li>Pressure plates</li>
 *     <li>Sweet berry bush</li>
 *     <li>Tripwire</li>
 *     <li>Waterlily</li>
 *     <li>Web</li>
 *     <li>Wither rose</li>
 * </ul>
 */
public class EntityInsideBlockEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Block block;
    private boolean cancelled;

    public EntityInsideBlockEvent(@NotNull Entity entity, @NotNull Block block) {
        super(entity);
        this.block = block;
    }

    /**
     * Gets the block.
     *
     * @return the block
     */
    @NotNull
    public Block getBlock() {
        return block;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
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
