package ca.spottedleaf.leafprofiler;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public final class LProfilerRegistry {

    // volatile required to ensure correct publishing when resizing
    private volatile ProfilerEntry[] typesById = new ProfilerEntry[16];
    private int totalEntries;
    private final ConcurrentHashMap<String, ProfilerEntry> nameToEntry = new ConcurrentHashMap<>();

    public LProfilerRegistry() {}

    public ProfilerEntry getById(final int id) {
        final ProfilerEntry[] entries = this.typesById;

        return id < 0 || id >= entries.length ? null : entries[id];
    }

    public ProfilerEntry getByName(final String name) {
        return this.nameToEntry.get(name);
    }

    public int getOrCreateType(final ProfileType type, final String name) {
        ProfilerEntry entry = this.nameToEntry.get(name);
        if (entry != null) {
            return entry.id;
        }
        synchronized (this) {
            entry = this.nameToEntry.get(name);
            if (entry != null) {
                return entry.id;
            }
            return this.createType(type, name);
        }
    }

    public int getOrCreateTimer(final String name) {
        return this.getOrCreateType(ProfileType.TIMER, name);
    }

    public int getOrCreateCounter(final String name) {
        return this.getOrCreateType(ProfileType.COUNTER, name);
    }

    public int createType(final ProfileType type, final String name) {
        synchronized (this) {
            final int id = this.totalEntries;

            final ProfilerEntry ret = new ProfilerEntry(type, name, id);

            final ProfilerEntry prev = this.nameToEntry.putIfAbsent(name, ret);

            if (prev != null) {
                throw new IllegalStateException("Entry already exists: " + prev);
            }

            ++this.totalEntries;

            ProfilerEntry[] entries = this.typesById;

            if (id >= entries.length) {
                this.typesById = entries = Arrays.copyOf(entries, entries.length * 2);
            }

            // should be opaque, but I don't think that matters here.
            entries[id] = ret;

            return id;
        }
    }

    public static enum ProfileType {
        COUNTER, TIMER;
    }

    public static record ProfilerEntry(ProfileType type, String name, int id) {}

    public static final LProfilerRegistry GLOBAL_REGISTRY = new LProfilerRegistry();
    public static final int TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Full Tick");
    public static final int IN_BETWEEN_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "In Between Tick");
    public static final int INTERNAL_TICK_TASKS = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Internal Tick Tasks");
    public static final int PLUGIN_TICK_TASKS = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Plugin Tick Tasks");
    public static final int ENTITY_SCHEDULER_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Entity Scheduler Tick");
    public static final int ENTITY_SCHEDULERS_TICKED = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Entity Schedulers Ticked");
    public static final int CONNECTION_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Connection Tick");
    public static final int AUTOSAVE = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Autosave");
    public static final int PLAYER_SAVE = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Player Save");
    public static final int CHUNK_SAVE = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Chunk Save");
    public static final int BLOCK_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Block Tick");
    public static final int FLUID_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Fluid Tick");
    public static final int BLOCK_OR_FLUID_TICK_COUNT = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Block/Fluid Tick Count");
    public static final int RAIDS_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Raids Tick");
    public static final int CHUNK_PROVIDER_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Chunk Source Tick");
    public static final int CHUNK_HOLDER_MANAGER_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Chunk Holder Manager Tick");
    public static final int TICKET_LEVEL_UPDATE_PROCESSING = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Ticket Level Update Processing");
    public static final int PLAYER_CHUNK_LOADER_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Player Chunk Loader Tick");
    public static final int CHUNK_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Chunk Ticks");
    public static final int MOB_SPAWN_ENTITY_COUNT = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Mob Spawn Entity Count");
    public static final int SPAWN_AND_RANDOM_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Spawn Entities And Random Tick");
    public static final int SPAWN_CHUNK_COUNT = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Entity Spawn Chunk Count");
    public static final int RANDOM_CHUNK_TICK_COUNT = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Random Chunk Tick Count");
    public static final int MISC_MOB_SPAWN_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Misc Mob Spawn Tick");
    public static final int BROADCAST_BLOCK_CHANGES = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Broadcast Block Changes");
    public static final int ENTITY_TRACKER_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Entity Tracker Tick");
    public static final int TRACKED_UNLOADED_ENTITY_COUNTS = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Total Untracked Unloaded Entities");
    public static final int TRACKED_ENTITY_COUNTS = GLOBAL_REGISTRY.createType(ProfileType.COUNTER, "Total Tracked Entities");
    public static final int POI_MANAGER_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "POI Manager Tick");
    public static final int PROCESS_UNLOADS = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Process Unloads");
    public static final int BLOCK_EVENT_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Block Event Tick");
    public static final int ENTITY_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Entity Tick");
    public static final int DRAGON_FIGHT_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Dragon Fight Tick");
    public static final int TILE_ENTITY = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Tile Entities");
    public static final int TILE_ENTITY_PENDING = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Tile Entity Handle Pending");
    public static final int TILE_ENTITY_TICK = GLOBAL_REGISTRY.createType(ProfileType.TIMER, "Tile Entity Tick");
}
