package org.bukkit.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a non-player entity is about to teleport because it is in
 * contact with a portal.
 * <p>
 * For players see {@link org.bukkit.event.player.PlayerPortalEvent}
 */
public class EntityPortalEvent extends EntityTeleportEvent {
    private static final HandlerList handlers = new HandlerList();
    private int searchRadius = 128;
    private final org.bukkit.PortalType type; // Paper

    public EntityPortalEvent(@NotNull final Entity entity, @NotNull final Location from, @Nullable final Location to) {
        this(entity, from, to, 128); // Paper
    }

    public EntityPortalEvent(@NotNull Entity entity, @NotNull Location from, @Nullable Location to, int searchRadius) {
        super(entity, from, to);
        this.searchRadius = searchRadius;
        this.type = org.bukkit.PortalType.CUSTOM; // Paper
    }

    // Paper start
    public EntityPortalEvent(@NotNull Entity entity, @NotNull Location from, @Nullable Location to, int searchRadius, final @NotNull org.bukkit.PortalType portalType) {
        super(entity, from, to);
        this.searchRadius = searchRadius;
        this.type = portalType;
    }

    /**
     * Get the portal type relating to this event.
     *
     * @return the portal type
     */
    public @NotNull org.bukkit.PortalType getPortalType() {
        return this.type;
    }
    /**
     * For {@link org.bukkit.PortalType#NETHER}, this is initially just the starting point
     * for the search for a portal to teleport to. It will initially just be the {@link #getFrom()}
     * scaled for dimension scaling and clamped to be inside the world border.
     * <p>
     * For {@link org.bukkit.PortalType#ENDER}, this will initially be the exact destination
     * either, the world spawn for <i>end->any world</i> or end spawn for <i>any world->end</i>.
     *
     * @return starting point for search or exact destination
     */
    @Override
    public @Nullable Location getTo() {
        return super.getTo();
    }

    /**
     * See the description of {@link #getTo()}.
     * @param to starting point for search or exact destination
     *           or null to cancel
     */
    @Override
    public void setTo(@Nullable final Location to) {
        super.setTo(to);
    }
    // Paper end

    /**
     * Set the Block radius to search in for available portals.
     *
     * @param searchRadius the radius in which to search for a portal from the
     * location
     */
    public void setSearchRadius(int searchRadius) {
        this.searchRadius = searchRadius;
    }

    /**
     * Gets the search radius value for finding an available portal.
     *
     * @return the currently set search radius
     */
    public int getSearchRadius() {
        return searchRadius;
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
