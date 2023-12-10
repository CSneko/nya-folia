package net.minecraft.server.level;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;

public class TicketType<T> {
    public static final TicketType<Long> FUTURE_AWAIT = create("future_await", Long::compareTo); // Paper
    public static final TicketType<Long> ASYNC_LOAD = create("async_load", Long::compareTo); // Paper

    private final String name;
    private final Comparator<T> comparator;
    public long timeout;
    public static final TicketType<Unit> START = TicketType.create("start", (unit, unit1) -> {
        return 0;
    });
    public static final TicketType<Unit> DRAGON = TicketType.create("dragon", (unit, unit1) -> {
        return 0;
    });
    public static final TicketType<ChunkPos> PLAYER = TicketType.create("player", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<ChunkPos> FORCED = TicketType.create("forced", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<ChunkPos> LIGHT = TicketType.create("light", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<BlockPos> PORTAL = TicketType.create("portal", Vec3i::compareTo, 300);
    public static final TicketType<Integer> POST_TELEPORT = TicketType.create("post_teleport", Integer::compareTo, 5);
    public static final TicketType<ChunkPos> UNKNOWN = TicketType.create("unknown", Comparator.comparingLong(ChunkPos::toLong), 1);
    public static final TicketType<Unit> PLUGIN = TicketType.create("plugin", (a, b) -> 0); // CraftBukkit
    public static final TicketType<org.bukkit.plugin.Plugin> PLUGIN_TICKET = TicketType.create("plugin_ticket", (plugin1, plugin2) -> plugin1.getClass().getName().compareTo(plugin2.getClass().getName())); // CraftBukkit
    public static final TicketType<Long> CHUNK_RELIGHT = create("light_update", Long::compareTo); // Paper - ensure chunks stay loaded for lighting
    // Paper start - rewrite chunk system
    public static final TicketType<Long> CHUNK_LOAD = create("chunk_load", Long::compareTo);
    public static final TicketType<Long> STATUS_UPGRADE = create("status_upgrade", Long::compareTo);
    public static final TicketType<Long> ENTITY_LOAD = create("entity_load", Long::compareTo);
    public static final TicketType<Long> POI_LOAD = create("poi_load", Long::compareTo);
    public static final TicketType<Unit> UNLOAD_COOLDOWN = create("unload_cooldown", (u1, u2) -> 0, 5 * 20);
    public static final TicketType<Long> NON_FULL_SYNC_LOAD = create("non_full_sync_load", Long::compareTo);
    public static final TicketType<ChunkPos> DELAY_UNLOAD = create("delay_unload", Comparator.comparingLong(ChunkPos::toLong), 1);
    // Paper end - rewrite chunk system
    // Folia start - region threading
    public static final TicketType<Unit> LOGIN = create("login", (u1, u2) -> 0, 20);
    public static final TicketType<Unit> DELAYED = create("delay", (u1, u2) -> 0, 5);
    public static final TicketType<Long> END_GATEWAY_EXIT_SEARCH = create("end_gateway_exit_search", Long::compareTo);
    public static final TicketType<Long> NETHER_PORTAL_DOUBLE_CHECK = create("nether_portal_double_check", Long::compareTo);
    public static final TicketType<Long> TELEPORT_HOLD_TICKET = create("teleport_hold_ticket", Long::compareTo);
    public static final TicketType<Unit> REGION_SCHEDULER_API_HOLD = create("region_scheduler_api_hold", (a, b) -> 0);
    // Folia end - region threading

    public static <T> TicketType<T> create(String name, Comparator<T> argumentComparator) {
        return new TicketType<>(name, argumentComparator, 0L);
    }

    public static <T> TicketType<T> create(String name, Comparator<T> argumentComparator, int expiryTicks) {
        return new TicketType<>(name, argumentComparator, (long) expiryTicks);
    }

    protected TicketType(String name, Comparator<T> argumentComparator, long expiryTicks) {
        this.name = name;
        this.comparator = argumentComparator;
        this.timeout = expiryTicks;
    }

    public String toString() {
        return this.name;
    }

    public Comparator<T> getComparator() {
        return this.comparator;
    }

    public long timeout() {
        return this.timeout;
    }
}
