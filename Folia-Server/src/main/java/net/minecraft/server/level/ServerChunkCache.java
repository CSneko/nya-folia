package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;

public class ServerChunkCache extends ChunkSource {

    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger(); // Paper
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private final DistanceManager distanceManager;
    final ServerLevel level;
    public final Thread mainThread;
    final ThreadedLevelLightEngine lightEngine;
    public final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    //private long lastInhabitedUpdate; // Folia - region threading
    public boolean spawnEnemies = true;
    public boolean spawnFriendlies = true;
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final ChunkAccess[] lastChunk = new ChunkAccess[4];
    // Folia - moved to regionised world data
    // Paper start
    // Folia - region threading
    final ca.spottedleaf.concurrentutil.map.SWMRLong2ObjectHashTable<LevelChunk> loadedChunkMap = new ca.spottedleaf.concurrentutil.map.SWMRLong2ObjectHashTable<>(8192, 0.5f); // Folia - region threading

    // Folia - region threading

    private static int getChunkCacheKey(int x, int z) {
        return x & 3 | ((z & 3) << 2);
    }

    public void addLoadedChunk(LevelChunk chunk) {
        synchronized (this.loadedChunkMap) { // Folia - region threading
            this.loadedChunkMap.put(chunk.coordinateKey, chunk);
        } // Folia - region threading

        // Folia - region threading
    }

    public void removeLoadedChunk(LevelChunk chunk) {
        synchronized (this.loadedChunkMap) { // Folia - region threading
            this.loadedChunkMap.remove(chunk.coordinateKey);
        } // Folia - region threading

        // Folia - region threading
    }

    public final LevelChunk getChunkAtIfLoadedMainThread(int x, int z) {
        return this.loadedChunkMap.get(ChunkPos.asLong(x, z)); // Folia - region threading
    }

    public final LevelChunk getChunkAtIfLoadedMainThreadNoCache(int x, int z) {
        return this.loadedChunkMap.get(ChunkPos.asLong(x, z));
    }

    public final LevelChunk getChunkAtMainThread(int x, int z) {
        LevelChunk ret = this.getChunkAtIfLoadedMainThread(x, z);
        if (ret != null) {
            return ret;
        }
        return (LevelChunk)this.getChunk(x, z, ChunkStatus.FULL, true);
    }

    final java.util.concurrent.atomic.AtomicLong chunkFutureAwaitCounter = new java.util.concurrent.atomic.AtomicLong(); // Paper - private -> package private
    // Paper end

    // Paper start
    @Nullable
    public ChunkAccess getChunkAtImmediately(int x, int z) {
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) {
            return null;
        }

        return holder.getLastAvailable();
    }

    public <T> void addTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkPos, int ticketLevel, T identifier) {
        this.distanceManager.addTicket(ticketType, chunkPos, ticketLevel, identifier);
    }

    public <T> void removeTicketAtLevel(TicketType<T> ticketType, ChunkPos chunkPos, int ticketLevel, T identifier) {
        this.distanceManager.removeTicket(ticketType, chunkPos, ticketLevel, identifier);
    }

    // Folia - region threading
    // Paper end

    public ServerChunkCache(ServerLevel world, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor workerExecutor, ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, ChunkProgressListener worldGenerationProgressListener, ChunkStatusUpdateListener chunkStatusChangeListener, Supplier<DimensionDataStorage> persistentStateManagerFactory) {
        this.level = world;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(world);
        this.mainThread = Thread.currentThread();
        File file = session.getDimensionPath(world.dimension()).resolve("data").toFile();

        file.mkdirs();
        this.dataStorage = new DimensionDataStorage(file, dataFixer);
        this.chunkMap = new ChunkMap(world, session, dataFixer, structureTemplateManager, workerExecutor, this.mainThreadProcessor, this, chunkGenerator, worldGenerationProgressListener, chunkStatusChangeListener, persistentStateManagerFactory, viewDistance, dsync);
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(simulationDistance);
        this.clearCache();
    }

    // CraftBukkit start - properly implement isChunkLoaded
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        ChunkHolder chunk = this.chunkMap.getUpdatingChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        return chunk.getFullChunkNow() != null;
    }
    // CraftBukkit end

    @Override
    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private ChunkHolder getVisibleChunkIfPresent(long pos) {
        return this.chunkMap.getVisibleChunkIfPresent(pos);
    }

    public int getTickingGenerated() {
        return this.chunkMap.getTickingGenerated();
    }

    private void storeInCache(long pos, ChunkAccess chunk, ChunkStatus status) {
        for (int j = 3; j > 0; --j) {
            this.lastChunkPos[j] = this.lastChunkPos[j - 1];
            this.lastChunkStatus[j] = this.lastChunkStatus[j - 1];
            this.lastChunk[j] = this.lastChunk[j - 1];
        }

        this.lastChunkPos[0] = pos;
        this.lastChunkStatus[0] = status;
        this.lastChunk[0] = chunk;
    }

    // Paper start - "real" get chunk if loaded
    // Note: Partially copied from the getChunkAt method below
    @Nullable
    public LevelChunk getChunkAtIfCachedImmediately(int x, int z) {
        long k = ChunkPos.asLong(x, z);

        // Note: Bypass cache since we need to check ticket level, and to make this MT-Safe

        ChunkHolder playerChunk = this.getVisibleChunkIfPresent(k);
        if (playerChunk == null) {
            return null;
        }

        return playerChunk.getFullChunkNowUnchecked();
    }

    @Nullable
    public LevelChunk getChunkAtIfLoadedImmediately(int x, int z) {
        long k = ChunkPos.asLong(x, z);

        return this.loadedChunkMap.get(k); // Folia - region threading
    }
    // Paper end

    @Nullable
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus leastStatus, boolean create) {
        final int x1 = x; final int z1 = z; // Paper - conflict on variable change
        if (!io.papermc.paper.util.TickThread.isTickThread()) { // Paper - rewrite chunk system
            return (ChunkAccess) CompletableFuture.supplyAsync(() -> {
                return this.getChunk(x, z, leastStatus, create);
            }, this.mainThreadProcessor).join();
        } else {
            // Paper start - optimise for loaded chunks
            LevelChunk ifLoaded = this.getChunkAtIfLoadedMainThread(x, z);
            if (ifLoaded != null) {
                return ifLoaded;
            }
            // Paper end
            ProfilerFiller gameprofilerfiller = this.level.getProfiler();

            gameprofilerfiller.incrementCounter("getChunk");
            long k = ChunkPos.asLong(x, z);

            ChunkAccess ichunkaccess;

            // Paper - rewrite chunk system - there are no correct callbacks to remove items from cache in the new chunk system

            gameprofilerfiller.incrementCounter("getChunkCacheMiss");
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = this.getChunkFutureMainThread(x, z, leastStatus, create, true); // Paper
            ServerChunkCache.MainThreadExecutor chunkproviderserver_b = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            if (!completablefuture.isDone()) { // Paper
                // Paper start - async chunk io/loading
                io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler.pushChunkWait(this.level, x1, z1); // Paper - rewrite chunk system
                // Paper end
                com.destroystokyo.paper.io.SyncLoadFinder.logSyncLoad(this.level, x1, z1); // Paper - sync load info
                this.level.timings.syncChunkLoad.startTiming(); // Paper
            chunkproviderserver_b.managedBlock(completablefuture::isDone);
                io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler.popChunkWait(); // Paper - async chunk debug  // Paper - rewrite chunk system
                this.level.timings.syncChunkLoad.stopTiming(); // Paper
            } // Paper
            ichunkaccess = (ChunkAccess) ((Either) completablefuture.join()).map((ichunkaccess1) -> {
                return ichunkaccess1;
            }, (playerchunk_failure) -> {
                if (create) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + playerchunk_failure));
                } else {
                    return null;
                }
            });
            this.storeInCache(k, ichunkaccess, leastStatus);
            return ichunkaccess;
        }
    }

    @Nullable
    @Override
    public LevelChunk getChunkNow(int chunkX, int chunkZ) {
        if (!io.papermc.paper.util.TickThread.isTickThread()) { // Paper - rewrite chunk system
            return null;
        } else {
            return this.getChunkAtIfLoadedMainThread(chunkX, chunkZ); // Paper - optimise for loaded chunks
        }
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, (Object) null);
        Arrays.fill(this.lastChunk, (Object) null);
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getChunkFuture(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        boolean flag1 = io.papermc.paper.util.TickThread.isTickThread(); // Paper - rewrite chunk system
        CompletableFuture completablefuture;

        if (flag1) {
            completablefuture = this.getChunkFutureMainThread(chunkX, chunkZ, leastStatus, create);
            ServerChunkCache.MainThreadExecutor chunkproviderserver_b = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            chunkproviderserver_b.managedBlock(completablefuture::isDone);
        } else {
            completablefuture = CompletableFuture.supplyAsync(() -> {
                return this.getChunkFutureMainThread(chunkX, chunkZ, leastStatus, create);
            }, this.mainThreadProcessor).thenCompose((completablefuture1) -> {
                return completablefuture1;
            });
        }

        return completablefuture;
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getChunkFutureMainThread(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        // Paper start - add isUrgent - old sig left in place for dirty nms plugins
        return getChunkFutureMainThread(chunkX, chunkZ, leastStatus, create, false);
    }
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getChunkFutureMainThread(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create, boolean isUrgent) {
        // Paper start - rewrite chunk system
        io.papermc.paper.util.TickThread.ensureTickThread(this.level, chunkX, chunkZ, "Scheduling chunk load off-main");
        int minLevel = ChunkLevel.byStatus(leastStatus);
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.level.chunkTaskScheduler.chunkHolderManager.getChunkHolder(chunkX, chunkZ);

        boolean needsFullScheduling = leastStatus == ChunkStatus.FULL && (chunkHolder == null || !chunkHolder.getChunkStatus().isOrAfter(FullChunkStatus.FULL));

        if ((chunkHolder == null || chunkHolder.getTicketLevel() > minLevel || needsFullScheduling) && !create) {
            return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        }

        io.papermc.paper.chunk.system.scheduling.NewChunkHolder.ChunkCompletion chunkCompletion = chunkHolder == null ? null : chunkHolder.getLastChunkCompletion();
        if (needsFullScheduling || chunkCompletion == null || !chunkCompletion.genStatus().isOrAfter(leastStatus)) {
            // schedule
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> ret = new CompletableFuture<>();
            Consumer<ChunkAccess> complete = (ChunkAccess chunk) -> {
                if (chunk == null) {
                    ret.complete(Either.right(ChunkHolder.ChunkLoadingFailure.UNLOADED));
                } else {
                    ret.complete(Either.left(chunk));
                }
            };

            this.level.chunkTaskScheduler.scheduleChunkLoad(
                chunkX, chunkZ, leastStatus, true,
                isUrgent ? ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.BLOCKING : ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.NORMAL,
                complete
            );

            return ret;
        } else {
            // can return now
            return CompletableFuture.completedFuture(Either.left(chunkCompletion.chunk()));
        }
        // Paper end - rewrite chunk system
    }

    // Paper - rewrite chunk system

    @Override
    public boolean hasChunk(int x, int z) {
        return this.getChunkAtIfLoadedImmediately(x, z) != null; // Paper - rewrite chunk system
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        long k = ChunkPos.asLong(chunkX, chunkZ);
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(k);

        if (playerchunk == null) {
            return null;
        } else {
            // Paper start - rewrite chunk system
            ChunkStatus status = playerchunk.getChunkHolderStatus();
            if (status != null && !status.isOrAfter(ChunkStatus.LIGHT.getParent())) {
                return null;
            }
            return playerchunk.getAvailableChunkNow();
            // Paper end - rewrite chunk system
        }
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    boolean runDistanceManagerUpdates() {
        return this.level.chunkTaskScheduler.chunkHolderManager.processTicketUpdates(); // Paper - rewrite chunk system
    }

    // Paper start
    public boolean isPositionTicking(Entity entity) {
        return this.isPositionTicking(ChunkPos.asLong(net.minecraft.util.Mth.floor(entity.getX()) >> 4, net.minecraft.util.Mth.floor(entity.getZ()) >> 4));
    }
    // Paper end

    public boolean isPositionTicking(long pos) {
        // Paper start - replace player chunk loader system
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(pos);
        return holder != null && holder.isTickingReady();
        // Paper end - replace player chunk loader system
    }

    public void save(boolean flush) {
        this.runDistanceManagerUpdates();
        try (co.aikar.timings.Timing timed = level.timings.chunkSaveData.startTiming()) { // Paper - Timings
        this.chunkMap.saveAllChunks(flush);
        } // Paper - Timings
    }

    // Paper start - duplicate save, but call incremental
    public void saveIncrementally() {
        this.runDistanceManagerUpdates();
        try (co.aikar.timings.Timing timed = level.timings.chunkSaveData.startTiming()) { // Paper - Timings
            this.chunkMap.saveIncrementally();
        } // Paper - Timings
    }
    // Paper end

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        this.close(true);
    }

    public void close(boolean save) { // Paper - rewrite chunk system
        this.level.chunkTaskScheduler.chunkHolderManager.close(save, true); // Paper - rewrite chunk system
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        if (true) return; // Paper - tickets will be removed later, this behavior isn't really well accounted for by the chunk system
        this.level.getProfiler().push("purge");
        this.distanceManager.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(() -> true);
        this.level.getProfiler().pop();
        this.clearCache();
    }
    // CraftBukkit end

    @Override
    public void tick(BooleanSupplier shouldKeepTicking, boolean tickChunks) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        this.level.getProfiler().push("purge");
        this.level.timings.doChunkMap.startTiming(); // Spigot
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_HOLDER_MANAGER_TICK); try { // Folia - profiler
        this.distanceManager.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_HOLDER_MANAGER_TICK); } // Folia - profiler
        this.level.timings.doChunkMap.stopTiming(); // Spigot
        this.level.getProfiler().popPush("chunks");
        if (tickChunks) {
            this.level.timings.chunks.startTiming(); // Paper - timings
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_CHUNK_LOADER_TICK); try { // Folia - profiler
            this.chunkMap.level.playerChunkLoader.tick(); // Paper - replace player chunk loader - this is mostly required to account for view distance changes
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_CHUNK_LOADER_TICK); } // Folia - profiler
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_TICK); try { // Folia - profiler
            this.tickChunks();
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_TICK); } // Folia - profiler
            this.level.timings.chunks.stopTiming(); // Paper - timings
        }

        this.level.timings.doChunkUnload.startTiming(); // Spigot
        this.level.getProfiler().popPush("unload");
        this.chunkMap.tick(shouldKeepTicking);
        this.level.timings.doChunkUnload.stopTiming(); // Spigot
        this.level.getProfiler().pop();
        this.clearCache();
    }

    private void tickChunks() {
        io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.level.getCurrentWorldData(); // Folia - region threading
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        long i = this.level.getGameTime();
        long j = 1; // Folia - region threading

        //this.lastInhabitedUpdate = i; // Folia - region threading
        boolean flag = this.level.isDebug();

        if (flag) {
            this.chunkMap.tick();
        } else {
            LevelData worlddata = this.level.getLevelData();
            ProfilerFiller gameprofilerfiller = this.level.getProfiler();

            gameprofilerfiller.push("pollingChunks");
            int k = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            boolean flag1 = this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) != 0L && this.level.getRedstoneGameTime() % this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) == 0L; // CraftBukkit // Folia - region threading

            gameprofilerfiller.push("naturalSpawnCount");
            this.level.timings.countNaturalMobs.startTiming(); // Paper - timings
            int l = this.distanceManager.getNaturalSpawnChunkCount();
            // Paper start - per player mob spawning
            NaturalSpawner.SpawnState spawnercreature_d; // moved down
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MOB_SPAWN_ENTITY_COUNT); try { // Folia - profiler
            if ((this.spawnFriendlies || this.spawnEnemies) && this.level.paperConfig().entities.spawning.perPlayerMobSpawns) { // don't count mobs when animals and monsters are disabled
                // re-set mob counts
                for (ServerPlayer player : regionizedWorldData.getLocalPlayers()) { // Folia - region threading
                    // Paper start - per player mob spawning backoff
                    for (int ii = 0; ii < ServerPlayer.MOBCATEGORY_TOTAL_ENUMS; ii++) {
                        player.mobCounts[ii] = 0;

                        int newBackoff = player.mobBackoffCounts[ii] - 1; // TODO make configurable bleed // TODO use nonlinear algorithm?
                        if (newBackoff < 0) {
                            newBackoff = 0;
                        }
                        player.mobBackoffCounts[ii] = newBackoff;
                    }
                    // Paper end - per player mob spawning backoff
                }
                spawnercreature_d = NaturalSpawner.createState(l, regionizedWorldData.getLoadedEntities(), this::getFullChunk, null, true); // Folia - region threading
            } else {
                spawnercreature_d = NaturalSpawner.createState(l, regionizedWorldData.getLoadedEntities(), this::getFullChunk, !this.level.paperConfig().entities.spawning.perPlayerMobSpawns ? new LocalMobCapCalculator(this.chunkMap) : null, false); // Folia - region threading
            }
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MOB_SPAWN_ENTITY_COUNT); } // Folia - profiler
            // Paper end
            this.level.timings.countNaturalMobs.stopTiming(); // Paper - timings

            regionizedWorldData.lastSpawnState = spawnercreature_d; // Folia - region threading
            gameprofilerfiller.popPush("filteringLoadedChunks");
            // Paper - optimise chunk tick iteration
            // Paper - optimise chunk tick iteration
            this.level.timings.chunkTicks.startTiming(); // Paper

            // Paper - optimise chunk tick iteration

            gameprofilerfiller.popPush("spawnAndTick");
            boolean flag2 = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && !regionizedWorldData.getLocalPlayers().isEmpty(); // CraftBukkit // Folia - region threading

            // Paper start - optimise chunk tick iteration
            ChunkMap playerChunkMap = this.chunkMap;
            for (ServerPlayer player : this.level.getLocalPlayers()) { // Folia - region threading
                if (!player.affectsSpawning || player.isSpectator()) {
                    regionizedWorldData.mobSpawnMap.remove(player); // Folia - region threading
                    player.playerNaturallySpawnedEvent = null;
                    player.lastEntitySpawnRadiusSquared = -1.0;
                    continue;
                }

                int viewDistance = io.papermc.paper.chunk.system.ChunkSystem.getTickViewDistance(player);

                // copied and modified from isOutisdeRange
                int chunkRange = (int)level.spigotConfig.mobSpawnRange;
                chunkRange = (chunkRange > viewDistance) ? viewDistance : chunkRange;
                chunkRange = (chunkRange > DistanceManager.MOB_SPAWN_RANGE) ? DistanceManager.MOB_SPAWN_RANGE : chunkRange;

                com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent event = new com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent(player.getBukkitEntity(), (byte)chunkRange);
                event.callEvent();
                if (event.isCancelled() || event.getSpawnRadius() < 0) {
                    regionizedWorldData.mobSpawnMap.remove(player); // Folia - region threading
                    player.playerNaturallySpawnedEvent = null;
                    player.lastEntitySpawnRadiusSquared = -1.0;
                    continue;
                }

                int range = Math.min(event.getSpawnRadius(), DistanceManager.MOB_SPAWN_RANGE); // limit to max spawn range
                int chunkX = io.papermc.paper.util.CoordinateUtils.getChunkCoordinate(player.getX());
                int chunkZ = io.papermc.paper.util.CoordinateUtils.getChunkCoordinate(player.getZ());

                regionizedWorldData.mobSpawnMap.addOrUpdate(player, chunkX, chunkZ, range); // Folia - region threading
                player.lastEntitySpawnRadiusSquared = (double)((range << 4) * (range << 4)); // used in anyPlayerCloseEnoughForSpawning
                player.playerNaturallySpawnedEvent = event;
            }
            // Paper end - optimise chunk tick iteration

            int chunksTicked = 0; // Paper
            // Paper start - optimise chunk tick iteration
            io.papermc.paper.util.player.NearbyPlayers nearbyPlayers = this.chunkMap.getNearbyPlayers(); // Paper - optimise chunk tick iteration
            Iterator<LevelChunk> iterator1;
            if (this.level.paperConfig().entities.spawning.perPlayerMobSpawns) {
                iterator1 = regionizedWorldData.getTickingChunks().iterator(); // Folia - region threading
            } else {
                iterator1 = regionizedWorldData.getTickingChunks().unsafeIterator(); // Folia - region threading
                List<LevelChunk> shuffled = Lists.newArrayListWithCapacity(regionizedWorldData.getTickingChunks().size()); // Folia - region threading
                while (iterator1.hasNext()) {
                    shuffled.add(iterator1.next());
                }
                Collections.shuffle(shuffled);
                iterator1 = shuffled.iterator();
            }
            try {
            // Paper end - optimise chunk tick iteration
            long spawnChunkCount = 0L; // Folia - profiler
            long randomChunkCount = 0L; // Folia - profiler
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.SPAWN_AND_RANDOM_TICK); try { // Folia - profiler
            while (iterator1.hasNext()) {
                LevelChunk chunk1 = iterator1.next(); // Paper - optimise chunk tick iteration
                ChunkPos chunkcoordintpair = chunk1.getPos();

                // Paper start - optimise chunk tick iteration
                com.destroystokyo.paper.util.maplist.ReferenceList<ServerPlayer> playersNearby
                    = nearbyPlayers.getPlayers(chunkcoordintpair, io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.SPAWN_RANGE);
                if (playersNearby == null) {
                    continue;
                }
                Object[] rawData = playersNearby.getRawData();
                boolean spawn = false;
                boolean tick = false;
                for (int itr = 0, len = playersNearby.size(); itr < len; ++itr) {
                    ServerPlayer player = (ServerPlayer)rawData[itr];
                    if (player.isSpectator()) {
                        continue;
                    }

                    double distance = ChunkMap.euclideanDistanceSquared(chunkcoordintpair, player);
                    spawn |= player.lastEntitySpawnRadiusSquared >= distance;
                    tick |= ((double)io.papermc.paper.util.player.NearbyPlayers.SPAWN_RANGE_VIEW_DISTANCE_BLOCKS) * ((double)io.papermc.paper.util.player.NearbyPlayers.SPAWN_RANGE_VIEW_DISTANCE_BLOCKS) >= distance;
                    if (spawn & tick) {
                        break;
                    }
                }
                // Paper end - optimise chunk tick iteration
                if (tick && chunk1.chunkStatus.isOrAfter(net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING)) { // Paper - optimise chunk tick iteration
                    chunk1.incrementInhabitedTime(j);
                    if (spawn && flag2 && (this.spawnEnemies || this.spawnFriendlies) && this.level.getWorldBorder().isWithinBounds(chunkcoordintpair)) { // Spigot // Paper - optimise chunk tick iteration
                        ++spawnChunkCount; // Folia - profiler
                        NaturalSpawner.spawnForChunk(this.level, chunk1, spawnercreature_d, this.spawnFriendlies, this.spawnEnemies, flag1);
                    }

                    if (true || this.level.shouldTickBlocksAt(chunkcoordintpair.toLong())) { // Paper - optimise chunk tick iteration
                        ++randomChunkCount; // Folia - profiler
                        this.level.tickChunk(chunk1, k);
                        if ((chunksTicked++ & 1) == 0) net.minecraft.server.MinecraftServer.getServer().executeMidTickTasks(); // Paper
                    }
                }
            }
            profiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.SPAWN_CHUNK_COUNT, spawnChunkCount); // Folia - profiler
            profiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.RANDOM_CHUNK_TICK_COUNT, randomChunkCount); // Folia - profiler
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.SPAWN_AND_RANDOM_TICK); } // Folia - profiler
            // Paper start - optimise chunk tick iteration
            } finally {
                if (iterator1 instanceof io.papermc.paper.util.maplist.IteratorSafeOrderedReferenceSet.Iterator safeIterator) {
                    safeIterator.finishedIterating();
                }
            }
            // Paper end - optimise chunk tick iteration
            this.level.timings.chunkTicks.stopTiming(); // Paper
            gameprofilerfiller.popPush("customSpawners");
            if (flag2) {
                profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MISC_MOB_SPAWN_TICK); try { // Folia - profiler
                try (co.aikar.timings.Timing ignored = this.level.timings.miscMobSpawning.startTiming()) { // Paper - timings
                this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                } // Paper - timings
                } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.MISC_MOB_SPAWN_TICK); } // Folia - profiler
            }

            gameprofilerfiller.popPush("broadcast");
            // Paper - optimise chunk tick iteration
                this.level.timings.broadcastChunkUpdates.startTiming(); // Paper - timing
            // Paper start - optimise chunk tick iteration
            // Folia start - region threading
            if (!this.level.needsChangeBroadcasting.isEmpty()) {
                profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BROADCAST_BLOCK_CHANGES); try { // Folia - profiler
                for (Iterator<ChunkHolder> iterator = this.level.needsChangeBroadcasting.iterator(); iterator.hasNext();) {
                    ChunkHolder holder = iterator.next();
                    if (!io.papermc.paper.util.TickThread.isTickThreadFor(holder.newChunkHolder.world, holder.pos)) {
                        continue;
                    }
                    // don't need to worry about chunk holder remove, as that can only be done by this tick thread
                    holder.broadcastChanges(holder.getFullChunkNowUnchecked());
                    if (!holder.needsBroadcastChanges()) {
                        iterator.remove();
                    }
                }
                } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BROADCAST_BLOCK_CHANGES); } // Folia - profiler
            }
            // Folia end - region threading
            // Paper end - optimise chunk tick iteration
                this.level.timings.broadcastChunkUpdates.stopTiming(); // Paper - timing
            // Paper - optimise chunk tick iteration
            gameprofilerfiller.pop();
            gameprofilerfiller.pop();
            this.chunkMap.tick();
        }
    }

    private void getFullChunk(long pos, Consumer<LevelChunk> chunkConsumer) {
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos);

        if (playerchunk != null) {
            // Paper start - rewrite chunk system
            LevelChunk chunk = playerchunk.getFullChunk();
            if (chunk != null) {
                chunkConsumer.accept(chunk);
            }
            // Paper end - rewrite chunk system
        }

    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkHolder playerchunk = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));

        if (playerchunk != null) {
            playerchunk.blockChanged(pos);
        }

    }

    @Override
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        Runnable run = () -> { // Folia - region threading
            ChunkHolder playerchunk = this.getVisibleChunkIfPresent(pos.chunk().toLong());

            if (playerchunk != null) {
                playerchunk.sectionLightChanged(type, pos.y());
            }

        }; // Folia - region threading
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueChunkTask(
            this.level, pos.getX(), pos.getZ(), run
        );
        // Folia end - region threading
    }

    public <T> void addRegionTicket(TicketType<T> ticketType, ChunkPos pos, int radius, T argument) {
        this.distanceManager.addRegionTicket(ticketType, pos, radius, argument);
    }

    public <T> void removeRegionTicket(TicketType<T> ticketType, ChunkPos pos, int radius, T argument) {
        this.distanceManager.removeRegionTicket(ticketType, pos, radius, argument);
    }

    @Override
    public void updateChunkForced(ChunkPos pos, boolean forced) {
        this.distanceManager.updateChunkForced(pos, forced);
    }

    public void move(ServerPlayer player) {
        if (!player.isRemoved()) {
            this.chunkMap.move(player);
        }

    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void broadcastAndSend(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcastAndSend(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int watchDistance) {
        this.chunkMap.setServerViewDistance(watchDistance);
    }

    public void setSimulationDistance(int simulationDistance) {
        this.distanceManager.updateSimulationDistance(simulationDistance);
    }

    @Override
    public void setSpawnSettings(boolean spawnMonsters, boolean spawnAnimals) {
        this.spawnEnemies = spawnMonsters;
        this.spawnFriendlies = spawnAnimals;
    }

    public String getChunkDebugData(ChunkPos pos) {
        return this.chunkMap.getChunkDebugData(pos);
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @Nullable
    @VisibleForDebug
    public NaturalSpawner.SpawnState getLastSpawnState() {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.level.getCurrentWorldData(); // Folia - region threading
        return worldData == null ? null : worldData.lastSpawnState; // Folia - region threading
    }

    public void removeTicketsOnClosing() {
        this.distanceManager.removeTicketsOnClosing();
    }

    public final class MainThreadExecutor extends BlockableEventLoop<Runnable> {

        MainThreadExecutor(Level world) {
            super("Chunk source main thread executor for " + world.dimension().location());
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable task) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        // Folia start - region threading
        @Override
        public void tell(Runnable runnable) {
            if (true) {
                throw new UnsupportedOperationException();
            }
            super.tell(runnable);
        }

        @Override
        public void executeBlocking(Runnable runnable) {
            if (true) {
                throw new UnsupportedOperationException();
            }
            super.executeBlocking(runnable);
        }

        @Override
        public void execute(Runnable runnable) {
            if (true) {
                throw new UnsupportedOperationException();
            }
            super.execute(runnable);
        }

        @Override
        public void executeIfPossible(Runnable runnable) {
            if (true) {
                throw new UnsupportedOperationException();
            }
            super.executeIfPossible(runnable);
        }
        // Folia end - region threading

        @Override
        protected void doRunTask(Runnable task) {
            if (true) throw new UnsupportedOperationException(); // Folia - region threading
            ServerChunkCache.this.level.getProfiler().incrementCounter("runTask");
            super.doRunTask(task);
        }

        @Override
        // CraftBukkit start - process pending Chunk loadCallback() and unloadCallback() after each run task
        public boolean pollTask() {
            // Folia start - region threading
            if (ServerChunkCache.this.level != io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData().world) {
                throw new IllegalStateException("Polling tasks from non-owned region");
            }
            // Folia end - region threading
            if (ServerChunkCache.this.runDistanceManagerUpdates()) {
                return true;
            }
            return io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion().getData().getTaskQueueData().executeChunkTask(); // Paper - rewrite chunk system // Folia - region threading
        }
    }

    private static record ChunkAndHolder(LevelChunk chunk, ChunkHolder holder) {

    }
}
