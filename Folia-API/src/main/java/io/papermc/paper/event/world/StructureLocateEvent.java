package io.papermc.paper.event.world;

import org.bukkit.Location;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called <b>before</b> a structure/feature is located.
 * This happens when:
 * <ul>
 *     <li>The /locate command is used.<br></li>
 *     <li>An Eye of Ender is used.</li>
 *     <li>An Explorer/Treasure Map is activated.</li>
 *     <li>{@link World#locateNearestStructure(Location, StructureType, int, boolean)} is invoked.</li>
 * </ul>
 * @deprecated no longer used, see {@link StructuresLocateEvent}
 */
@Deprecated(forRemoval = true) @ApiStatus.ScheduledForRemoval(inVersion = "1.21")
public class StructureLocateEvent extends WorldEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Location origin;
    private Location result = null;
    private StructureType type;
    private int radius;
    private boolean findUnexplored;
    private boolean cancelled = false;

    public StructureLocateEvent(@NotNull World world, @NotNull Location origin, @NotNull StructureType structureType, int radius, boolean findUnexplored) {
        super(world);
        this.origin = origin;
        this.type = structureType;
        this.radius = radius;
        this.findUnexplored = findUnexplored;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Gets the location set as the structure location, if it was defined.
     * <p>
     * Returns {@code null} if it has not been set by {@link StructureLocateEvent#setResult(Location)}.
     * Since this event fires <i>before</i> the search is done, the actual location is unknown at this point.
     *
     * @return The result location, if it has been set. null if it has not.
     * @see World#locateNearestStructure(Location, StructureType, int, boolean)
     */
    @Nullable
    public Location getResult() {
        return result;
    }

    /**
     * Sets the result {@link Location}. This causes the search to be skipped, and the location passed here to be used as the result.
     *
     * @param result the {@link Location} of the structure.
     */
    public void setResult(@Nullable Location result) {
        this.result = result;
    }

    /**
     * Gets the {@link StructureType} that is to be located.
     *
     * @return the structure type.
     */
    @NotNull
    public StructureType getType() {
        return type;
    }

    /**
     * Sets the {@link StructureType} that is to be located.
     *
     * @param type the structure type.
     */
    public void setType(@NotNull StructureType type) {
        this.type = type;
    }

    /**
     * Gets the {@link Location} from which the search is to be conducted.
     *
     * @return {@link Location} where search begins
     */
    @NotNull
    public Location getOrigin() {
        return origin;
    }

    /**
     * Gets the search radius in which to attempt locating the structure.
     * <p>
     * This radius may not always be obeyed during the structure search!
     *
     * @return the search radius.
     */
    public int getRadius() {
        return radius;
    }

    /**
     * Sets the search radius in which to attempt locating the structure.
     * <p>
     * This radius may not always be obeyed during the structure search!
     *
     * @param radius the search radius.
     */
    public void setRadius(int radius) {
        this.radius = radius;
    }

    /**
     * Gets whether to search exclusively for unexplored structures.
     * <p>
     * As with the search radius, this value is not always obeyed.
     *
     * @return Whether to search for only unexplored structures.
     */
    public boolean shouldFindUnexplored() {
        return findUnexplored;
    }

    /**
     * Sets whether to search exclusively for unexplored structures.
     * <p>
     * As with the search radius, this value is not always obeyed.
     *
     * @param findUnexplored Whether to search for only unexplored structures.
     */
    public void setFindUnexplored(boolean findUnexplored) {
        this.findUnexplored = findUnexplored;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
