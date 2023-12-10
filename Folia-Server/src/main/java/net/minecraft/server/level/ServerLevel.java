package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import co.aikar.timings.TimingHistory; // Paper
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import org.slf4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.generator.CustomWorldChunkManager;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.util.WorldUUID;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.TimeSkipEvent;
// CraftBukkit end
import it.unimi.dsi.fastutil.ints.IntArrayList; // Paper

public class ServerLevel extends Level implements WorldGenLevel {

    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<ServerPlayer> players;
    public final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    public final PrimaryLevelData serverLevelData; // CraftBukkit - type
    //final EntityTickList entityTickList; // Folia - region threading
    //public final PersistentEntitySectionManager<Entity> entityManager; // Paper - rewrite chunk system
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    //private final LevelTicks<Block> blockTicks; // Folia - region threading
    //private final LevelTicks<Fluid> fluidTicks; // Folia - region threading
    final Set<Mob> navigatingMobs;
    volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    //private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents; // Folia - region threading
    //private final List<BlockEventData> blockEventsToReschedule; // Folia - region threading
    //private boolean handlingTick; // Folia - region threading
    private final List<CustomSpawner> customSpawners;
    @Nullable
    private EndDragonFight dragonFight;
    final Int2ObjectMap<EnderDragonPart> dragonParts;
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    public final boolean tickTime; // Folia - region threading
    private final RandomSequences randomSequences;
    // Folia - region threading

    // CraftBukkit start
    public final LevelStorageSource.LevelStorageAccess convertable;
    public final UUID uuid;
    // Folia - region threading
    private final alternate.current.wire.WireHandler wireHandler = new alternate.current.wire.WireHandler(this); // Paper - optimize redstone (Alternate Current)
    public static Throwable getAddToWorldStackTrace(Entity entity) {
        final Throwable thr = new Throwable(entity + " Added to world at " + new java.util.Date());
        io.papermc.paper.util.StacktraceDeobfuscator.INSTANCE.deobfuscateThrowable(thr);
        return thr;
    }

    @Override public LevelChunk getChunkIfLoaded(int x, int z) { // Paper - this was added in world too but keeping here for NMS ABI
        return this.chunkSource.getChunkAtIfLoadedImmediately(x, z); // Paper
    }

    @Override
    public ResourceKey<LevelStem> getTypeKey() {
        return this.convertable.dimensionType;
    }

    // Paper start
    public final boolean areChunksLoadedForMove(AABB axisalignedbb) {
        // copied code from collision methods, so that we can guarantee that they wont load chunks (we don't override
        // ICollisionAccess methods for VoxelShapes)
        // be more strict too, add a block (dumb plugins in move events?)
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        ServerChunkCache chunkProvider = this.getChunkSource();

        // Folia start - region threading
        // don't let players move into regions not owned
        if (!io.papermc.paper.util.TickThread.isTickThreadFor(this, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
            return false;
        }

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (chunkProvider.getChunkAtIfLoadedImmediately(cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public final boolean isAreaLoaded(final BlockPos center, final int radius) {
        int minX = (center.getX() - radius) >> 4;
        int minZ = (center.getZ() - radius) >> 4;
        int maxX = (center.getX() + radius) >> 4;
        int maxZ = (center.getZ() + radius) >> 4;

        return this.isAreaLoaded(minX, minZ, maxX, maxZ);
    }

    public final boolean isAreaLoaded(final int minChunkX, final int minChunkZ, final int maxChunkX, final int maxChunkZ) {
        // Folia end - region threading
        ServerChunkCache chunkProvider = this.getChunkSource();

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (chunkProvider.getChunkAtIfLoadedImmediately(cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public final void loadChunksAsync(BlockPos pos, int radiusBlocks,
                                      ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                      java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            priority, onLoad
        );
    }

    public final void loadChunksAsync(BlockPos pos, int radiusBlocks,
                                      net.minecraft.world.level.chunk.ChunkStatus chunkStatus,
                                      ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                      java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            chunkStatus, priority, onLoad
        );
    }

    public final void loadChunksAsync(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                      ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                      java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, net.minecraft.world.level.chunk.ChunkStatus.FULL, priority, onLoad);
    }

    public final void loadChunksAsync(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                      net.minecraft.world.level.chunk.ChunkStatus chunkStatus,
                                      ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                      java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        List<net.minecraft.world.level.chunk.ChunkAccess> ret = new java.util.ArrayList<>();

        ServerChunkCache chunkProvider = this.getChunkSource();

        int requiredChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        java.util.concurrent.atomic.AtomicInteger loadedChunks = new java.util.concurrent.atomic.AtomicInteger();

        Long holderIdentifier = Long.valueOf(chunkProvider.chunkFutureAwaitCounter.getAndIncrement());

        int ticketLevel = 33 + net.minecraft.world.level.chunk.ChunkStatus.getDistance(chunkStatus);

        java.util.function.Consumer<net.minecraft.world.level.chunk.ChunkAccess> consumer = (net.minecraft.world.level.chunk.ChunkAccess chunk) -> {
            if (chunk != null) {
                synchronized (ret) { // Folia - region threading - make callback thread-safe TODO rebase
                ret.add(chunk);
                } // Folia - region threading - make callback thread-safe TODO rebase
                chunkProvider.addTicketAtLevel(TicketType.FUTURE_AWAIT, chunk.getPos(), ticketLevel, holderIdentifier);
            }
            if (loadedChunks.incrementAndGet() == requiredChunks) {
                try {
                    onLoad.accept(java.util.Collections.unmodifiableList(ret));
                } finally {
                    for (int i = 0, len = ret.size(); i < len; ++i) {
                        ChunkPos chunkPos = ret.get(i).getPos();

                        chunkProvider.addTicketAtLevel(TicketType.UNKNOWN, chunkPos, ticketLevel, chunkPos);
                        chunkProvider.removeTicketAtLevel(TicketType.FUTURE_AWAIT, chunkPos, ticketLevel, holderIdentifier);
                    }
                }
            }
        };

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                io.papermc.paper.chunk.system.ChunkSystem.scheduleChunkLoad(
                    this, cx, cz, chunkStatus, true, priority, consumer
                );
            }
        }
    }

    public final void loadChunksForMoveAsync(AABB axisalignedbb, ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                             java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {

        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        this.loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, priority, onLoad);
    }

    // Paper start - rewrite chunk system
    public final io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler chunkTaskScheduler;
    public final io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController chunkDataControllerNew
        = new io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController(io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.CHUNK_DATA) {

        @Override
        public net.minecraft.world.level.chunk.storage.RegionFileStorage getCache() {
            return ServerLevel.this.getChunkSource().chunkMap.regionFileCache;
        }

        @Override
        public void writeData(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
            ServerLevel.this.getChunkSource().chunkMap.write(new ChunkPos(chunkX, chunkZ), compound);
        }

        @Override
        public net.minecraft.nbt.CompoundTag readData(int chunkX, int chunkZ) throws IOException {
            return ServerLevel.this.getChunkSource().chunkMap.readSync(new ChunkPos(chunkX, chunkZ));
        }
    };
    public final io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController poiDataControllerNew
        = new io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController(io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.POI_DATA) {

        @Override
        public net.minecraft.world.level.chunk.storage.RegionFileStorage getCache() {
            return ServerLevel.this.getChunkSource().chunkMap.getPoiManager();
        }

        @Override
        public void writeData(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
            ServerLevel.this.getChunkSource().chunkMap.getPoiManager().write(new ChunkPos(chunkX, chunkZ), compound);
        }

        @Override
        public net.minecraft.nbt.CompoundTag readData(int chunkX, int chunkZ) throws IOException {
            return ServerLevel.this.getChunkSource().chunkMap.getPoiManager().read(new ChunkPos(chunkX, chunkZ));
        }
    };
    public final io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController entityDataControllerNew
        = new io.papermc.paper.chunk.system.io.RegionFileIOThread.ChunkDataController(io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.ENTITY_DATA) {

        @Override
        public net.minecraft.world.level.chunk.storage.RegionFileStorage getCache() {
            return ServerLevel.this.entityStorage;
        }

        @Override
        public void writeData(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
            ServerLevel.this.writeEntityChunk(chunkX, chunkZ, compound);
        }

        @Override
        public net.minecraft.nbt.CompoundTag readData(int chunkX, int chunkZ) throws IOException {
            return ServerLevel.this.readEntityChunk(chunkX, chunkZ);
        }
    };
    private final EntityRegionFileStorage entityStorage;

    private static final class EntityRegionFileStorage extends net.minecraft.world.level.chunk.storage.RegionFileStorage {

        public EntityRegionFileStorage(Path directory, boolean dsync) {
            super(directory, dsync);
        }

        protected void write(ChunkPos pos, net.minecraft.nbt.CompoundTag nbt) throws IOException {
            ChunkPos nbtPos = nbt == null ? null : EntityStorage.readChunkPos(nbt);
            if (nbtPos != null && !pos.equals(nbtPos)) {
                throw new IllegalArgumentException(
                    "Entity chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + pos.toString()
                        + " but compound says coordinate is " + nbtPos + " for world: " + this
                );
            }
            super.write(pos, nbt);
        }
    }

    private void writeEntityChunk(int chunkX, int chunkZ, net.minecraft.nbt.CompoundTag compound) throws IOException {
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            io.papermc.paper.chunk.system.io.RegionFileIOThread.scheduleSave(
                this, chunkX, chunkZ, compound,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.ENTITY_DATA);
            return;
        }
        this.entityStorage.write(new ChunkPos(chunkX, chunkZ), compound);
    }

    private net.minecraft.nbt.CompoundTag readEntityChunk(int chunkX, int chunkZ) throws IOException {
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            return io.papermc.paper.chunk.system.io.RegionFileIOThread.loadData(
                this, chunkX, chunkZ, io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.ENTITY_DATA,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
            );
        }
        return this.entityStorage.read(new ChunkPos(chunkX, chunkZ));
    }

    private final io.papermc.paper.chunk.system.entity.EntityLookup entityLookup;
    public final io.papermc.paper.chunk.system.entity.EntityLookup getEntityLookup() {
        return this.entityLookup;
    }

    private final java.util.concurrent.atomic.AtomicLong nonFullSyncLoadIdGenerator = new java.util.concurrent.atomic.AtomicLong();

    private ChunkAccess getIfAboveStatus(int chunkX, int chunkZ, net.minecraft.world.level.chunk.ChunkStatus status) {
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder loaded =
            this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder.ChunkCompletion loadedCompletion;
        if (loaded != null && (loadedCompletion = loaded.getLastChunkCompletion()) != null && loadedCompletion.genStatus().isOrAfter(status)) {
            return loadedCompletion.chunk();
        }

        return null;
    }

    @Override
    public ChunkAccess syncLoadNonFull(int chunkX, int chunkZ, net.minecraft.world.level.chunk.ChunkStatus status) {
        if (status == null || status.isOrAfter(net.minecraft.world.level.chunk.ChunkStatus.FULL)) {
            throw new IllegalArgumentException("Status: " + status.toString());
        }
        ChunkAccess loaded = this.getIfAboveStatus(chunkX, chunkZ, status);
        if (loaded != null) {
            return loaded;
        }

        Long ticketId = Long.valueOf(this.nonFullSyncLoadIdGenerator.getAndIncrement());
        int ticketLevel = 33 + net.minecraft.world.level.chunk.ChunkStatus.getDistance(status);
        this.chunkTaskScheduler.chunkHolderManager.addTicketAtLevel(
            TicketType.NON_FULL_SYNC_LOAD, chunkX, chunkZ, ticketLevel, ticketId
        );
        this.chunkTaskScheduler.chunkHolderManager.processTicketUpdates();

        this.chunkTaskScheduler.beginChunkLoadForNonFullSync(chunkX, chunkZ, status, ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.BLOCKING);

        // we could do a simple spinwait here, since we do not need to process tasks while performing this load
        // but we process tasks only because it's a better use of the time spent
        this.chunkSource.mainThreadProcessor.managedBlock(() -> {
            return ServerLevel.this.getIfAboveStatus(chunkX, chunkZ, status) != null;
        });

        loaded = ServerLevel.this.getIfAboveStatus(chunkX, chunkZ, status);
        if (loaded == null) {
            throw new IllegalStateException("Expected chunk to be loaded for status " + status);
        }

        this.chunkTaskScheduler.chunkHolderManager.removeTicketAtLevel(
            TicketType.NON_FULL_SYNC_LOAD, chunkX, chunkZ, ticketLevel, ticketId
        );

        return loaded;
    }
    // Paper end - rewrite chunk system

    public final io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader playerChunkLoader = new io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader(this);
    private final java.util.concurrent.atomic.AtomicReference<io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.ViewDistances> viewDistances = new java.util.concurrent.atomic.AtomicReference<>(new io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.ViewDistances(-1, -1, -1));

    public io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.ViewDistances getViewDistances() {
        return this.viewDistances.get();
    }

    private void updateViewDistance(final java.util.function.Function<io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.ViewDistances, io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.ViewDistances> update) {
        for (io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.ViewDistances curr = this.viewDistances.get();;) {
            if (this.viewDistances.compareAndSet(curr, update.apply(curr))) {
                return;
            }
        }
    }

    public void setTickViewDistance(final int distance) {
        if ((distance < io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MIN_VIEW_DISTANCE || distance > io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MAX_VIEW_DISTANCE)) {
            throw new IllegalArgumentException("Tick view distance must be a number between " + io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MIN_VIEW_DISTANCE + " and " + (io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MAX_VIEW_DISTANCE) + ", got: " + distance);
        }
        this.updateViewDistance((input) -> {
            return input.setTickViewDistance(distance);
        });
    }

    public void setLoadViewDistance(final int distance) {
        if (distance != -1 && (distance < io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MIN_VIEW_DISTANCE || distance > io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MAX_VIEW_DISTANCE + 1)) {
            throw new IllegalArgumentException("Load view distance must be a number between " + io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MIN_VIEW_DISTANCE + " and " + (io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MAX_VIEW_DISTANCE + 1) + " or -1, got: " + distance);
        }
        this.updateViewDistance((input) -> {
            return input.setLoadViewDistance(distance);
        });
    }

    public void setSendViewDistance(final int distance) {
        if (distance != -1 && (distance < io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MIN_VIEW_DISTANCE || distance > io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MAX_VIEW_DISTANCE + 1)) {
            throw new IllegalArgumentException("Send view distance must be a number between " + io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MIN_VIEW_DISTANCE + " and " + (io.papermc.paper.chunk.system.RegionizedPlayerChunkLoader.MAX_VIEW_DISTANCE + 1) + " or -1, got: " + distance);
        }
        this.updateViewDistance((input) -> {
            return input.setSendViewDistance(distance);
        });
    }

    // Paper start - optimise getPlayerByUUID
    @Nullable
    @Override
    public Player getPlayerByUUID(UUID uuid) {
        final Player player = this.getServer().getPlayerList().getPlayer(uuid);
        return player != null && player.level() == this ? player : null;
    }
    // Paper end
    // Paper start - lag compensation
    // Folia - region threading

    public long getLagCompensationTick() {
        return this.getCurrentWorldData().getLagCompensationTick(); // Folia - region threading
    }

    public void updateLagCompensationTick() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }
    // Paper end - lag compensation
    // Paper start - optimise nearby player retrieval
    @Override
    public java.util.List<net.minecraft.world.entity.player.Player> getNearbyPlayers(net.minecraft.world.entity.ai.targeting.TargetingConditions targetPredicate,
                                                                                     net.minecraft.world.entity.LivingEntity entity,
                                                                                     net.minecraft.world.phys.AABB box) {
        return this.getNearbyEntities(Player.class, targetPredicate, entity, box);
    }

    @Override
    public Player getNearestPlayer(double x, double y, double z, double maxDistance, @Nullable Predicate<Entity> targetPredicate) {
        if (maxDistance > 0.0D) {
            io.papermc.paper.util.player.NearbyPlayers players = this.chunkSource.chunkMap.getNearbyPlayers();

            com.destroystokyo.paper.util.maplist.ReferenceList<ServerPlayer> nearby = players.getPlayersByBlock(
                io.papermc.paper.util.CoordinateUtils.getBlockCoordinate(x),
                io.papermc.paper.util.CoordinateUtils.getBlockCoordinate(z),
                io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.GENERAL
            );

            if (nearby == null) {
                return null;
            }

            ServerPlayer nearest = null;
            double nearestDist = maxDistance * maxDistance;
            Object[] rawData = nearby.getRawData();
            for (int i = 0, len = nearby.size(); i < len; ++i) {
                ServerPlayer player = (ServerPlayer)rawData[i];
                double dist = player.distanceToSqr(x, y, z);
                if (dist >= nearestDist) {
                    continue;
                }

                if (targetPredicate == null || targetPredicate.test(player)) {
                    nearest = player;
                    nearestDist = dist;
                }
            }

            return nearest;
        } else {
            ServerPlayer nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (ServerPlayer player : this.getLocalPlayers()) { // Folia - region threading
                double dist = player.distanceToSqr(x, y, z);
                if (dist >= nearestDist) {
                    continue;
                }

                if (targetPredicate == null || targetPredicate.test(player)) {
                    nearest = player;
                    nearestDist = dist;
                }
            }

            return nearest;
        }
    }

    @Override
    public Player getNearestPlayer(net.minecraft.world.entity.ai.targeting.TargetingConditions targetPredicate, LivingEntity entity) {
        return this.getNearestPlayer(targetPredicate, entity, entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public Player getNearestPlayer(net.minecraft.world.entity.ai.targeting.TargetingConditions targetPredicate, LivingEntity entity,
                                   double x, double y, double z) {
        double range = targetPredicate.range;
        if (range > 0.0D) {
            io.papermc.paper.util.player.NearbyPlayers players = this.chunkSource.chunkMap.getNearbyPlayers();

            com.destroystokyo.paper.util.maplist.ReferenceList<ServerPlayer> nearby = players.getPlayersByBlock(
                io.papermc.paper.util.CoordinateUtils.getBlockCoordinate(x),
                io.papermc.paper.util.CoordinateUtils.getBlockCoordinate(z),
                io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.GENERAL
            );

            if (nearby == null) {
                return null;
            }

            ServerPlayer nearest = null;
            double nearestDist = Double.MAX_VALUE;
            Object[] rawData = nearby.getRawData();
            for (int i = 0, len = nearby.size(); i < len; ++i) {
                ServerPlayer player = (ServerPlayer)rawData[i];
                double dist = player.distanceToSqr(x, y, z);
                if (dist >= nearestDist) {
                    continue;
                }

                if (targetPredicate.test(entity, player)) {
                    nearest = player;
                    nearestDist = dist;
                }
            }

            return nearest;
        } else {
            return this.getNearestEntity(this.getLocalPlayers(), targetPredicate, entity, x, y, z); // Folia - region threading
        }
    }

    @Nullable
    public Player getNearestPlayer(net.minecraft.world.entity.ai.targeting.TargetingConditions targetPredicate, double x, double y, double z) {
        return this.getNearestPlayer(targetPredicate, null, x, y, z);
    }
    // Paper end - optimise nearby player retrieval
    // Folia start - region threading
    public final io.papermc.paper.threadedregions.TickRegions tickRegions = new io.papermc.paper.threadedregions.TickRegions();
    public final io.papermc.paper.threadedregions.ThreadedRegionizer<io.papermc.paper.threadedregions.TickRegions.TickRegionData, io.papermc.paper.threadedregions.TickRegions.TickRegionSectionData> regioniser;
    {
        this.regioniser = new io.papermc.paper.threadedregions.ThreadedRegionizer<>(
            6,
            (1.0 / 6.0),
            1,
            1,
            io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift(),
            this,
            this.tickRegions
        );
    }
    public final io.papermc.paper.threadedregions.RegionizedTaskQueue.WorldRegionTaskData taskQueueRegionData = new io.papermc.paper.threadedregions.RegionizedTaskQueue.WorldRegionTaskData(this);
    public static final int WORLD_INIT_NOT_CHECKED = 0;
    public static final int WORLD_INIT_CHECKING = 1;
    public static final int WORLD_INIT_CHECKED = 2;
    public final java.util.concurrent.atomic.AtomicInteger checkInitialised = new java.util.concurrent.atomic.AtomicInteger(WORLD_INIT_NOT_CHECKED);
    public ChunkPos randomSpawnSelection;

    public static final record PendingTeleport(Entity.EntityTreeNode rootVehicle, Vec3 to) {}
    private final it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<PendingTeleport> pendingTeleports = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();

    public void pushPendingTeleport(final PendingTeleport teleport) {
        synchronized (this.pendingTeleports) {
            this.pendingTeleports.add(teleport);
        }
    }

    public boolean removePendingTeleport(final PendingTeleport teleport) {
        synchronized (this.pendingTeleports) {
            return this.pendingTeleports.remove(teleport);
        }
    }

    public List<PendingTeleport> removeAllRegionTeleports() {
        final List<PendingTeleport> ret = new ArrayList<>();

        synchronized (this.pendingTeleports) {
            for (final Iterator<PendingTeleport> iterator = this.pendingTeleports.iterator(); iterator.hasNext(); ) {
                final PendingTeleport pendingTeleport = iterator.next();
                if (io.papermc.paper.util.TickThread.isTickThreadFor(this, pendingTeleport.to())) {
                    ret.add(pendingTeleport);
                    iterator.remove();
                }
            }
        }

        return ret;
    }
    // Folia end - region threading

    // Add env and gen to constructor, IWorldDataServer -> WorldDataServer
    public ServerLevel(MinecraftServer minecraftserver, Executor executor, LevelStorageSource.LevelStorageAccess convertable_conversionsession, PrimaryLevelData iworlddataserver, ResourceKey<Level> resourcekey, LevelStem worlddimension, ChunkProgressListener worldloadlistener, boolean flag, long i, List<CustomSpawner> list, boolean flag1, @Nullable RandomSequences randomsequences, org.bukkit.World.Environment env, org.bukkit.generator.ChunkGenerator gen, org.bukkit.generator.BiomeProvider biomeProvider) {
        // IRegistryCustom.Dimension iregistrycustom_dimension = minecraftserver.registryAccess(); // CraftBukkit - decompile error
        // Holder holder = worlddimension.type(); // CraftBukkit - decompile error

        // Objects.requireNonNull(minecraftserver); // CraftBukkit - decompile error
        super(iworlddataserver, resourcekey, minecraftserver.registryAccess(), worlddimension.type(), minecraftserver::getProfiler, false, flag, i, minecraftserver.getMaxChainedNeighborUpdates(), gen, biomeProvider, env, spigotConfig -> minecraftserver.paperConfigurations.createWorldConfig(io.papermc.paper.configuration.PaperConfigurations.createWorldContextMap(convertable_conversionsession.levelDirectory.path(), iworlddataserver.getLevelName(), resourcekey.location(), spigotConfig)), executor); // Paper - Async-Anti-Xray - Pass executor
        this.pvpMode = minecraftserver.isPvpAllowed();
        this.convertable = convertable_conversionsession;
        this.uuid = WorldUUID.getUUID(convertable_conversionsession.levelDirectory.path().toFile());
        // CraftBukkit end
        this.players = new java.util.concurrent.CopyOnWriteArrayList<>(); // Folia - region threading
        //this.entityTickList = new EntityTickList(); // Folia - region threading
        //this.blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier()); // Folia - moved to RegioniedWorldData
        //this.fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier()); // Folia - moved to RegioniedWorldData
        this.navigatingMobs = new ObjectOpenHashSet();
        //this.blockEvents = new ObjectLinkedOpenHashSet(); // Folia - moved to RegioniedWorldData
        //this.blockEventsToReschedule = new ArrayList(64); // Folia - moved to RegioniedWorldData
        this.dragonParts = new Int2ObjectOpenHashMap();
        this.tickTime = flag1;
        this.server = minecraftserver;
        this.customSpawners = list;
        this.serverLevelData = iworlddataserver;
        ChunkGenerator chunkgenerator = worlddimension.generator();
        // CraftBukkit start
        this.serverLevelData.setWorld(this);

        if (biomeProvider != null) {
            BiomeSource worldChunkManager = new CustomWorldChunkManager(this.getWorld(), biomeProvider, this.server.registryAccess().registryOrThrow(Registries.BIOME));
            if (chunkgenerator instanceof NoiseBasedChunkGenerator cga) {
                chunkgenerator = new NoiseBasedChunkGenerator(worldChunkManager, cga.settings);
            } else if (chunkgenerator instanceof FlatLevelSource cpf) {
                chunkgenerator = new FlatLevelSource(cpf.settings(), worldChunkManager);
            }
        }

        if (gen != null) {
            chunkgenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(this, chunkgenerator, gen);
        }
        // CraftBukkit end
        boolean flag2 = minecraftserver.forceSynchronousWrites();
        DataFixer datafixer = minecraftserver.getFixerUpper();
        this.entityStorage = new EntityRegionFileStorage(convertable_conversionsession.getDimensionPath(resourcekey).resolve("entities"), flag2); // Paper - rewrite chunk system  //EntityPersistentStorage<Entity> entitypersistentstorage = new EntityStorage(this, convertable_conversionsession.getDimensionPath(resourcekey).resolve("entities"), datafixer, flag2, minecraftserver);

        // this.entityManager = new PersistentEntitySectionManager<>(Entity.class, new ServerLevel.EntityCallbacks(), entitypersistentstorage, this.entitySliceManager); // Paper // Paper - rewrite chunk system
        StructureTemplateManager structuretemplatemanager = minecraftserver.getStructureManager();
        int j = this.spigotConfig.viewDistance; // Spigot
        int k = this.spigotConfig.simulationDistance; // Spigot
        //PersistentEntitySectionManager persistententitysectionmanager = this.entityManager; // Paper - rewrite chunk system

        //Objects.requireNonNull(this.entityManager); // Paper - rewrite chunk system
        this.chunkSource = new ServerChunkCache(this, convertable_conversionsession, datafixer, structuretemplatemanager, executor, chunkgenerator, j, k, flag2, worldloadlistener, null, () -> { // Paper - rewrite chunk system
            return minecraftserver.overworld().getDataStorage();
        });
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        //this.updateSkyBrightness(); // Folia - region threading - delay until first tick
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(minecraftserver.getAbsoluteMaxWorldSize());
        this.raids = (Raids) this.getDataStorage().computeIfAbsent(Raids.factory(this), Raids.getFileId(this.dimensionTypeRegistration()));
        if (!minecraftserver.isSingleplayer()) {
            iworlddataserver.setGameType(minecraftserver.getDefaultGameType());
        }

        long l = minecraftserver.getWorldData().worldGenOptions().seed();

        this.structureCheck = new StructureCheck(this.chunkSource.chunkScanner(), this.registryAccess(), minecraftserver.getStructureManager(), this.getTypeKey(), chunkgenerator, this.chunkSource.randomState(), this, chunkgenerator.getBiomeSource(), l, datafixer); // Paper - Fix missing CB diff
        this.structureManager = new StructureManager(this, this.serverLevelData.worldGenOptions(), this.structureCheck); // CraftBukkit
        if ((this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) || env == org.bukkit.World.Environment.THE_END) { // CraftBukkit - Allow to create EnderDragonBattle in default and custom END
            this.dragonFight = new EndDragonFight(this, this.serverLevelData.worldGenOptions().seed(), this.serverLevelData.endDragonFightData()); // CraftBukkit
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = (RandomSequences) Objects.requireNonNullElseGet(randomsequences, () -> {
            return (RandomSequences) this.getDataStorage().computeIfAbsent(RandomSequences.factory(l), "random_sequences");
        });
        this.getCraftServer().addWorld(this.getWorld()); // CraftBukkit

        this.chunkTaskScheduler = new io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler(this, io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler.workerThreads); // Paper - rewrite chunk system
        this.entityLookup = new io.papermc.paper.chunk.system.entity.EntityLookup(this, new EntityCallbacks()); // Paper - rewrite chunk system
        this.updateTickData(); // Folia - region threading - make sure it is initialised before ticked
    }

    // Folia start - region threading
    public void updateTickData() {
        this.tickData = new io.papermc.paper.threadedregions.RegionizedServer.WorldLevelData(this, this.serverLevelData.getGameTime(), this.serverLevelData.getDayTime());
    }
    // Folia end - region threading

    // Paper start
    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkSource().getChunkAtIfLoadedImmediately(chunkX, chunkZ) != null;
    }
    // Paper end

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EndDragonFight enderDragonFight) {
        this.dragonFight = enderDragonFight;
    }

    public void setWeatherParameters(int clearDuration, int rainDuration, boolean raining, boolean thundering) {
        this.serverLevelData.setClearWeatherTime(clearDuration);
        this.serverLevelData.setRainTime(rainDuration);
        this.serverLevelData.setThunderTime(rainDuration);
        this.serverLevelData.setRaining(raining, org.bukkit.event.weather.WeatherChangeEvent.Cause.COMMAND); // Paper
        this.serverLevelData.setThundering(thundering, org.bukkit.event.weather.ThunderChangeEvent.Cause.COMMAND); // Paper
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(biomeX, biomeY, biomeZ, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    public void tick(BooleanSupplier shouldKeepTicking, io.papermc.paper.threadedregions.TickRegions.TickRegionData region) { // Folia - regionised ticking
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - regionised ticking
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        regionizedWorldData.setHandlingTick(true); // Folia - regionised ticking
        gameprofilerfiller.push("world border");
        if (region == null) this.getWorldBorder().tick(); // Folia - regionised ticking - moved into global tick
        gameprofilerfiller.popPush("weather");
        if (region == null) this.advanceWeatherCycle();
        //int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE); // Folia - region threading - move intotickSleep
        long j;

        if (region == null) this.tickSleep(); // Folia - region threading

        if (region == null) this.updateSkyBrightness(); // Folia - region threading
        this.tickTime();
        gameprofilerfiller.popPush("tickPending");
        this.timings.scheduledBlocks.startTiming(); // Paper
        if (!this.isDebug()) {
            j = regionizedWorldData.getRedstoneGameTime(); // Folia - region threading
            gameprofilerfiller.push("blockTicks");
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_TICK); try { // Folia - profiler
            regionizedWorldData.getBlockLevelTicks().tick(j, 65536, this::tickBlock); // Folia - region ticking
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_TICK); } // Folia - profiler
            gameprofilerfiller.popPush("fluidTicks");
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.FLUID_TICK); try { // Folia - profiler
            regionizedWorldData.getFluidLevelTicks().tick(j, 65536, this::tickFluid); // Folia - region ticking
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.FLUID_TICK); } // Folia - profiler
            gameprofilerfiller.pop();
        }
        this.timings.scheduledBlocks.stopTiming(); // Paper

        gameprofilerfiller.popPush("raid");
        this.timings.raids.startTiming(); // Paper - timings
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.RAIDS_TICK); try { // Folia - profiler
        this.raids.tick();
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.RAIDS_TICK); } // Folia - profiler
        this.timings.raids.stopTiming(); // Paper - timings
        gameprofilerfiller.popPush("chunkSource");
        this.timings.chunkProviderTick.startTiming(); // Paper - timings
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_PROVIDER_TICK); try { // Folia - profiler
        this.getChunkSource().tick(shouldKeepTicking, true);
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_PROVIDER_TICK); } // Folia - profiler
        this.timings.chunkProviderTick.stopTiming(); // Paper - timings
        gameprofilerfiller.popPush("blockEvents");
        this.timings.doSounds.startTiming(); // Spigot
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_EVENT_TICK); try { // Folia - profiler
        this.runBlockEvents();
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_EVENT_TICK); } // Folia - profiler
        this.timings.doSounds.stopTiming(); // Spigot
        regionizedWorldData.setHandlingTick(false); // Folia - regionised ticking
        gameprofilerfiller.pop();
        boolean flag = true || !this.players.isEmpty() || !this.getForcedChunks().isEmpty(); // CraftBukkit - this prevents entity cleanup, other issues on servers with no players

        if (flag) {
            this.resetEmptyTime();
        }

        if (flag || this.emptyTime++ < 300) {
            gameprofilerfiller.push("entities");
            this.timings.tickEntities.startTiming(); // Spigot
            if (this.dragonFight != null) {
                profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.DRAGON_FIGHT_TICK); try { // Folia - profiler
                if (io.papermc.paper.util.TickThread.isTickThreadFor(this, this.dragonFight.origin)) { // Folia - region threading
                gameprofilerfiller.push("dragonFight");
                this.dragonFight.tick();
                gameprofilerfiller.pop();
                } else {  // Folia start - region threading
                    // try to load dragon fight
                    ChunkPos fightCenter = new ChunkPos(0, 0);
                    this.chunkSource.addTicketAtLevel(
                        TicketType.UNKNOWN, fightCenter, io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                        fightCenter
                    );
                } // Folia end - region threading
                } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.DRAGON_FIGHT_TICK); } // Folia - profiler
            }

            org.spigotmc.ActivationRange.activateEntities(this); // Spigot
            this.timings.entityTick.startTiming(); // Spigot
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_TICK); try { // Folia - profiler
            regionizedWorldData.forEachTickingEntity((entity) -> { // Folia - regionised ticking
                if (!entity.isRemoved()) {
                    if (false && this.shouldDiscardEntity(entity)) { // CraftBukkit - We prevent spawning in general, so this butchering is not needed
                        entity.discard();
                    } else {
                        gameprofilerfiller.push("checkDespawn");
                        entity.checkDespawn();
                        if (entity.isRemoved()) return; // Folia - region threading - if we despawned, DON'T TICK IT!
                        gameprofilerfiller.pop();
                        if (true || this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) { // Paper - now always true if in the ticking list
                            Entity entity1 = entity.getVehicle();

                            if (entity1 != null) {
                                if (!entity1.isRemoved() && entity1.hasPassenger(entity)) {
                                    return;
                                }

                                entity.stopRiding();
                            }

                            gameprofilerfiller.push("tick");
                            this.guardEntityTick(this::tickNonPassenger, entity);
                            gameprofilerfiller.pop();
                        }
                    }
                }
            });
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_TICK); } // Folia - profiler
            this.timings.entityTick.stopTiming(); // Spigot
            this.timings.tickEntities.stopTiming(); // Spigot
            gameprofilerfiller.pop();
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY); try { // Folia - profiler
            this.tickBlockEntities();
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY); } // Folia - profiler
        }

        gameprofilerfiller.push("entityManagement");
        //this.entityManager.tick(); // Paper - rewrite chunk system
        gameprofilerfiller.pop();
    }

    // Folia start - region threading
    public void tickSleep() {
        int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE); long j; // Folia moved from tick loop
        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            // CraftBukkit start
            j = this.levelData.getDayTime() + 24000L;
            TimeSkipEvent event = new TimeSkipEvent(this.getWorld(), TimeSkipEvent.SkipReason.NIGHT_SKIP, (j - j % 24000L) - this.getDayTime());
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.getCraftServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }
            }

            if (!event.isCancelled()) {
                this.wakeUpAllPlayers();
            }
            // CraftBukkit end
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }
    }
    // Folia end - region threading

    @Override
    public boolean shouldTickBlocksAt(long chunkPos) {
        // Paper start - replace player chunk loader system
        ChunkHolder holder = this.chunkSource.chunkMap.getVisibleChunkIfPresent(chunkPos);
        return holder != null && holder.isTickingReady();
        // Paper end - replace player chunk loader system
    }

    protected void tickTime() {
        if (this.tickTime) {
            io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - region threading
            long i = regionizedWorldData.getRedstoneGameTime() + 1L; // Folia - region threading

            regionizedWorldData.setRedstoneGameTime(i); // Folia - region threading
            if (false) this.serverLevelData.getScheduledEvents().tick(this.server, i); // Folia - region threading - TODO any way to bring this in?
            if (false && this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) { // Folia - region threading
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }

        }
    }

    public void setDayTime(long timeOfDay) {
        this.serverLevelData.setDayTime(timeOfDay);
    }

    public void tickCustomSpawners(boolean spawnMonsters, boolean spawnAnimals) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        Iterator iterator = this.customSpawners.iterator();

        while (iterator.hasNext()) {
            CustomSpawner mobspawner = (CustomSpawner) iterator.next();

            final int customSpawnerTimer = profiler.getOrCreateTimerAndStart(() -> "Misc Spawner: ".concat(io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(mobspawner.getClass().getName()))); try { // Folia - profiler
            mobspawner.tick(this, spawnMonsters, spawnAnimals);
            } finally { profiler.stopTimer(customSpawnerTimer); } // Folia - profiler
        }

    }

    private boolean shouldDiscardEntity(Entity entity) {
        return !this.server.isSpawningAnimals() && (entity instanceof Animal || entity instanceof WaterAnimal) ? true : !this.server.areNpcsEnabled() && entity instanceof Npc;
    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        (this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList())).forEach((entityplayer) -> { // CraftBukkit - decompile error
            // Folia start - region threading
            entityplayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                if (player.level() != ServerLevel.this || !player.isSleeping()) {
                    return;
                }
                player.stopSleepInBed(false, false);
            }, null, 1L);
            // Folia end - region threading
        });
    }
    // Paper start - optimise random block ticking
    private final ThreadLocal<BlockPos.MutableBlockPos> chunkTickMutablePosition = ThreadLocal.withInitial(() -> new BlockPos.MutableBlockPos()); // Folia - region threading
    private final ThreadLocal<io.papermc.paper.util.math.ThreadUnsafeRandom> randomTickRandom = ThreadLocal.withInitial(() -> new io.papermc.paper.util.math.ThreadUnsafeRandom(this.random.nextLong())); // Folia - region threading
    // Paper end

    public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        io.papermc.paper.util.math.ThreadUnsafeRandom randomTickRandom = this.randomTickRandom.get(); // Folia - region threading
        ChunkPos chunkcoordintpair = chunk.getPos();
        boolean flag = this.isRaining();
        int j = chunkcoordintpair.getMinBlockX();
        int k = chunkcoordintpair.getMinBlockZ();
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        gameprofilerfiller.push("thunder");
        final BlockPos.MutableBlockPos blockposition = this.chunkTickMutablePosition.get(); // Paper - use mutable to reduce allocation rate, final to force compile fail on change // Folia - region threading

        if (!this.paperConfig().environment.disableThunder && flag && this.isThundering() && this.spigotConfig.thunderChance > 0 && this.random.nextInt(this.spigotConfig.thunderChance) == 0) { // Spigot // Paper - disable thunder
            blockposition.set(this.findLightningTargetAround(this.getBlockRandomPos(j, 0, k, 15))); // Paper

            if (this.isRainingAt(blockposition)) {
                DifficultyInstance difficultydamagescaler = this.getCurrentDifficultyAt(blockposition);
                boolean flag1 = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && this.random.nextDouble() < (double) difficultydamagescaler.getEffectiveDifficulty() * this.paperConfig().entities.spawning.skeletonHorseThunderSpawnChance.or(0.01D) && !this.getBlockState(blockposition.below()).is(Blocks.LIGHTNING_ROD); // Paper

                if (flag1) {
                    SkeletonHorse entityhorseskeleton = (SkeletonHorse) EntityType.SKELETON_HORSE.create(this);

                    if (entityhorseskeleton != null) {
                        entityhorseskeleton.setTrap(true);
                        entityhorseskeleton.setAge(0);
                        entityhorseskeleton.setPos((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                        this.addFreshEntity(entityhorseskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
                    }
                }

                LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(this);

                if (entitylightning != null) {
                    entitylightning.moveTo(Vec3.atBottomCenterOf(blockposition));
                    entitylightning.setVisualOnly(flag1);
                    this.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER); // CraftBukkit
                }
            }
        }

        gameprofilerfiller.popPush("iceandsnow");

        if (!this.paperConfig().environment.disableIceAndSnow) { // Paper
        for (int l = 0; l < randomTickSpeed; ++l) {
            if (this.random.nextInt(48) == 0) {
                this.getRandomBlockPosition(j, 0, k, 15, blockposition);
                this.tickIceAndSnow(flag, blockposition, chunk);
            }
        }
        } // Paper

        // Paper start - optimise random block ticking
        gameprofilerfiller.popPush("tickBlocks");
        timings.chunkTicksBlocks.startTiming(); // Paper
        if (randomTickSpeed > 0) {
            LevelChunkSection[] sections = chunk.getSections();
            final int minSection = io.papermc.paper.util.WorldUtil.getMinSection(this);
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.tickingList.size() == 0) continue;

                int yPos = (sectionIndex + minSection) << 4;
                for (int a = 0; a < randomTickSpeed; ++a) {
                    int tickingBlocks = section.tickingList.size();
                    int index = randomTickRandom.nextInt(16 * 16 * 16); // Folia - region threading
                    if (index >= tickingBlocks) {
                        continue;
                    }

                    long raw = section.tickingList.getRaw(index);
                    int location = com.destroystokyo.paper.util.maplist.IBlockDataList.getLocationFromRaw(raw);
                    int randomX = location & 15;
                    int randomY = ((location >>> (4 + 4)) & 255) | yPos;
                    int randomZ = (location >>> 4) & 15;

                    BlockPos blockposition2 = blockposition.set(j + randomX, randomY, k + randomZ);
                    BlockState iblockdata = com.destroystokyo.paper.util.maplist.IBlockDataList.getBlockDataFromRaw(raw);

                    iblockdata.randomTick(this, blockposition2, randomTickRandom); // Folia - region threading
                    // We drop the fluid tick since LAVA is ALREADY TICKED by the above method (See LiquidBlock).
                    // TODO CHECK ON UPDATE (ping the Canadian)
                }
            }
        }
        // Paper end - optimise random block ticking

        timings.chunkTicksBlocks.stopTiming(); // Paper
        gameprofilerfiller.pop();
    }

    private void tickIceAndSnow(boolean raining, BlockPos.MutableBlockPos blockposition1, final LevelChunk chunk) { // Paper - optimise chunk ticking
        // Paper start - optimise chunk ticking
        int normalY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockposition1.getX() & 15, blockposition1.getZ() & 15) + 1;
        int downY = normalY - 1;
        blockposition1.setY(normalY);
        Biome biomebase = (Biome) this.getBiome(blockposition1).value();

        blockposition1.setY(downY);
        if (biomebase.shouldFreeze(this, blockposition1)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, Blocks.ICE.defaultBlockState(), null); // CraftBukkit
        }
        // Paper end - optimise chunk ticking

        if (raining) {
            int i = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);

            blockposition1.setY(normalY); // Paper - optimise chunk ticking
            if (i > 0 && biomebase.shouldSnow(this, blockposition1)) {
                BlockState iblockdata = this.getBlockState(blockposition1);

                if (iblockdata.is(Blocks.SNOW)) {
                    int j = (Integer) iblockdata.getValue(SnowLayerBlock.LAYERS);

                    if (j < Math.min(i, 8)) {
                        BlockState iblockdata1 = (BlockState) iblockdata.setValue(SnowLayerBlock.LAYERS, j + 1);

                        Block.pushEntitiesUp(iblockdata, iblockdata1, this, blockposition1);
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, iblockdata1, null); // CraftBukkit
                    }
                } else {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, Blocks.SNOW.defaultBlockState(), null); // CraftBukkit
                }
            }

            blockposition1.setY(downY); // Paper - optimise chunk ticking
            Biome.Precipitation biomebase_precipitation = biomebase.getPrecipitationAt(blockposition1); // Paper - optimise chunk ticking

            if (biomebase_precipitation != Biome.Precipitation.NONE) {
                BlockState iblockdata2 = this.getBlockState(blockposition1); // Paper - optimise chunk ticking

                iblockdata2.getBlock().handlePrecipitation(iblockdata2, this, blockposition1, biomebase_precipitation); // Paper - optimise chunk ticking
            }
        }

    }

    public Optional<BlockPos> findLightningRod(BlockPos pos) {
        Optional<BlockPos> optional = this.getPoiManager().findClosest((holder) -> {
            return holder.is(PoiTypes.LIGHTNING_ROD);
        }, (blockposition1) -> {
            return blockposition1.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockposition1.getX(), blockposition1.getZ()) - 1;
        }, pos, 128, PoiManager.Occupancy.ANY);

        return optional.map((blockposition1) -> {
            return blockposition1.above(1);
        });
    }

    protected BlockPos findLightningTargetAround(BlockPos pos) {
        // Paper start
        return this.findLightningTargetAround(pos, false);
    }
    public BlockPos findLightningTargetAround(BlockPos pos, boolean returnNullWhenNoTarget) {
        // Paper end
        BlockPos blockposition1 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        Optional<BlockPos> optional = this.findLightningRod(blockposition1);

        if (optional.isPresent()) {
            return (BlockPos) optional.get();
        } else {
            AABB axisalignedbb = (new AABB(blockposition1, new BlockPos(blockposition1.getX(), this.getMaxBuildHeight(), blockposition1.getZ()))).inflate(3.0D);
            List<LivingEntity> list = this.getEntitiesOfClass(LivingEntity.class, axisalignedbb, (entityliving) -> {
                return entityliving != null && entityliving.isAlive() && this.canSeeSky(entityliving.blockPosition()) && !entityliving.isSpectator(); // Paper - Fix lightning being able to hit spectators (MC-262422)
            });

            if (!list.isEmpty()) {
                return ((LivingEntity) list.get(this.random.nextInt(list.size()))).blockPosition();
            } else {
                if (returnNullWhenNoTarget) return null; // Paper
                if (blockposition1.getY() == this.getMinBuildHeight() - 1) {
                    blockposition1 = blockposition1.above(2);
                }

                return blockposition1;
            }
        }
    }

    public boolean isHandlingTick() {
        return this.getCurrentWorldData().isHandlingTick(); // Folia - regionised ticking
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
                MutableComponent ichatmutablecomponent;

                if (this.sleepStatus.areEnoughSleeping(i)) {
                    ichatmutablecomponent = Component.translatable("sleep.skipping_night");
                } else {
                    ichatmutablecomponent = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                Iterator iterator = this.players.iterator();

                while (iterator.hasNext()) {
                    ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                    entityplayer.displayClientMessage(ichatmutablecomponent, true);
                }

            }
        }
    }

    public void updateSleepingPlayerList() {
        // Folia start - region threading
        if (!io.papermc.paper.threadedregions.RegionizedServer.isGlobalTickThread()) {
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
                ServerLevel.this.updateSleepingPlayerList();
            });
            return;
        }
        // Folia end - region threading
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }

    }

    @Override
    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    public void advanceWeatherCycle() { // Folia - region threading - public
        boolean flag = this.isRaining();

        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int i = this.serverLevelData.getClearWeatherTime();
                int j = this.serverLevelData.getThunderTime();
                int k = this.serverLevelData.getRainTime();
                boolean flag1 = this.levelData.isThundering();
                boolean flag2 = this.levelData.isRaining();

                if (i > 0) {
                    --i;
                    j = flag1 ? 0 : 1;
                    k = flag2 ? 0 : 1;
                    flag1 = false;
                    flag2 = false;
                } else {
                    if (j > 0) {
                        --j;
                        if (j == 0) {
                            flag1 = !flag1;
                        }
                    } else if (flag1) {
                        j = ServerLevel.THUNDER_DURATION.sample(this.random);
                    } else {
                        j = ServerLevel.THUNDER_DELAY.sample(this.random);
                    }

                    if (k > 0) {
                        --k;
                        if (k == 0) {
                            flag2 = !flag2;
                        }
                    } else if (flag2) {
                        k = ServerLevel.RAIN_DURATION.sample(this.random);
                    } else {
                        k = ServerLevel.RAIN_DELAY.sample(this.random);
                    }
                }

                this.serverLevelData.setThunderTime(j);
                this.serverLevelData.setRainTime(k);
                this.serverLevelData.setClearWeatherTime(i);
                this.serverLevelData.setThundering(flag1, org.bukkit.event.weather.ThunderChangeEvent.Cause.NATURAL); // Paper
                this.serverLevelData.setRaining(flag2, org.bukkit.event.weather.WeatherChangeEvent.Cause.NATURAL); // Paper
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        /* CraftBukkit start
        if (this.oRainLevel != this.rainLevel) {
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }
        // */
        ServerPlayer[] players = this.players.toArray(new ServerPlayer[0]); // Folia - region threading
        for (ServerPlayer player : players) { // Folia - region threading
            if (player.level() == this) { // Folia - region threading
                player.tickWeather(); // Folia - region threading
            }
        }

        if (flag != this.isRaining()) {
            // Only send weather packets to those affected
            for (ServerPlayer player : players) { // Folia - region threading
                if (player.level() == this) { // Folia - region threading
                    player.setPlayerWeather((!flag ? WeatherType.DOWNFALL : WeatherType.CLEAR), false); // Folia - region threading
                }
            }
        }
        for (ServerPlayer player : players) { // Folia - region threading
            if (player.level() == this) { // Folia - region threading
                player.updateWeather(this.oRainLevel, this.rainLevel, this.oThunderLevel, this.thunderLevel); // Folia - region threading
            }
        }
        // CraftBukkit end

    }

    private void resetWeatherCycle() {
        // CraftBukkit start
        this.serverLevelData.setRaining(false, org.bukkit.event.weather.WeatherChangeEvent.Cause.SLEEP); // Paper - when passing the night
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isRaining()) {
            this.serverLevelData.setRainTime(0);
        }
        // CraftBukkit end
        this.serverLevelData.setThundering(false, org.bukkit.event.weather.ThunderChangeEvent.Cause.SLEEP); // Paper - when passing the night
        // CraftBukkit start
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isThundering()) {
            this.serverLevelData.setThunderTime(0);
        }
        // CraftBukkit end
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos pos, Fluid fluid) {
        FluidState fluid1 = this.getFluidState(pos);

        if (fluid1.is(fluid)) {
            fluid1.tick(this, pos);
        }
        MinecraftServer.getServer().executeMidTickTasks(); // Paper - exec chunk tasks during world tick

    }

    private void tickBlock(BlockPos pos, Block block) {
        BlockState iblockdata = this.getBlockState(pos);

        if (iblockdata.is(block)) {
            iblockdata.tick(this, pos, this.random);
        }
        MinecraftServer.getServer().executeMidTickTasks(); // Paper - exec chunk tasks during world tick

    }

    // Paper start - log detailed entity tick information
    // TODO replace with varhandle
    static final java.util.concurrent.atomic.AtomicReference<Entity> currentlyTickingEntity = new java.util.concurrent.atomic.AtomicReference<>();

    public static List<Entity> getCurrentlyTickingEntities() {
        Entity ticking = currentlyTickingEntity.get();
        List<Entity> ret = java.util.Arrays.asList(ticking == null ? new Entity[0] : new Entity[] { ticking });

        return ret;
    }
    // Paper end - log detailed entity tick information

    public void tickNonPassenger(Entity entity) {
        // Paper start - log detailed entity tick information
        io.papermc.paper.util.TickThread.ensureTickThread(entity, "Cannot tick an entity off-main"); // Folia - region threading
        try {
            if (currentlyTickingEntity.get() == null) {
                currentlyTickingEntity.lazySet(entity);
            }
            // Paper end - log detailed entity tick information
        ++TimingHistory.entityTicks; // Paper - timings
        // Spigot start
        co.aikar.timings.Timing timer; // Paper
        /*if (!org.spigotmc.ActivationRange.checkIfActive(entity)) { // Paper - comment out - EAR 2, reimplement below
            entity.tickCount++;
            timer = entity.getType().inactiveTickTimer.startTiming(); try { // Paper - timings
            entity.inactiveTick();
            } finally { timer.stopTiming(); } // Paper
            return;
        }*/ // Paper - comment out EAR 2
        // Spigot end
        // Paper start- timings
        final boolean isActive = org.spigotmc.ActivationRange.checkIfActive(entity);
        timer = isActive ? entity.getType().tickTimer.startTiming() : entity.getType().inactiveTickTimer.startTiming(); // Paper
        // Folia start - timer
        final int timerId = isActive ? entity.getType().tickTimerId : entity.getType().inactiveTickTimerId;
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler();
        profiler.startTimer(timerId);
        // Folia end - timer
        try {
        // Paper end - timings
        entity.setOldPosAndRot();
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        ++entity.tickCount;
        this.getProfiler().push(() -> {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        });
        gameprofilerfiller.incrementCounter("tickNonPassenger");
        if (isActive) { // Paper - EAR 2
            TimingHistory.activatedEntityTicks++;
        entity.tick();
        // Folia start - region threading
        if (!io.papermc.paper.util.TickThread.isTickThreadFor(entity)) {
            // removed from region while ticking
            return;
        }
        if (entity.doPortalLogic()) {
            // portalled
            return;
        }
        // Folia end - region threading
        } else { entity.inactiveTick(); } // Paper - EAR 2
        this.getProfiler().pop();
        } finally { timer.stopTiming(); profiler.stopTimer(timerId); } // Paper - timings // Folia - timer
        Iterator iterator = entity.getPassengers().iterator();

        while (iterator.hasNext()) {
            Entity entity1 = (Entity) iterator.next();

            this.tickPassenger(entity, entity1);
        }
        // } finally { timer.stopTiming(); } // Paper - timings - move up
        // Paper start - log detailed entity tick information
        } finally {
            if (currentlyTickingEntity.get() == entity) {
                currentlyTickingEntity.lazySet(null);
            }
        }
        // Paper end - log detailed entity tick information
    }

    private void tickPassenger(Entity vehicle, Entity passenger) {
        if (!passenger.isRemoved() && passenger.getVehicle() == vehicle) {
            if (passenger instanceof Player || this.getCurrentWorldData().hasEntityTickingEntity(passenger)) { // Folia - region threading
                // Paper - EAR 2
                final boolean isActive = org.spigotmc.ActivationRange.checkIfActive(passenger);
                co.aikar.timings.Timing timer = isActive ? passenger.getType().passengerTickTimer.startTiming() : passenger.getType().passengerInactiveTickTimer.startTiming(); // Paper
                // Folia start - timer
                final int timerId = isActive ? passenger.getType().tickTimerId : passenger.getType().inactiveTickTimerId;
                final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler();
                profiler.startTimer(timerId);
                // Folia end - timer
                try {
                // Paper end
                passenger.setOldPosAndRot();
                ++passenger.tickCount;
                ProfilerFiller gameprofilerfiller = this.getProfiler();

                gameprofilerfiller.push(() -> {
                    return BuiltInRegistries.ENTITY_TYPE.getKey(passenger.getType()).toString();
                });
                gameprofilerfiller.incrementCounter("tickPassenger");
                // Paper start - EAR 2
                if (isActive) {
                passenger.rideTick();
                // Folia start - region threading
                if (!io.papermc.paper.util.TickThread.isTickThreadFor(passenger)) {
                    // removed from region while ticking
                    return;
                }
                if (passenger.doPortalLogic()) {
                    // portalled
                    return;
                }
                // Folia end - region threading
                } else {
                    passenger.setDeltaMovement(Vec3.ZERO);
                    passenger.inactiveTick();
                    // copied from inside of if (isPassenger()) of passengerTick, but that ifPassenger is unnecessary
                    vehicle.positionRider(passenger);
                }
                // Paper end - EAR 2
                gameprofilerfiller.pop();
                Iterator iterator = passenger.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity2 = (Entity) iterator.next();

                    this.tickPassenger(passenger, entity2);
                }

            } finally { timer.stopTiming(); profiler.stopTimer(timerId); }// Paper - EAR2 timings // Folia - timer
            }
        } else {
            passenger.stopRiding();
        }
    }

    @Override
    public boolean mayInteract(Player player, BlockPos pos) {
        return !this.server.isUnderSpawnProtection(this, pos, player) && this.getWorldBorder().isWithinBounds(pos);
    }

    // Paper start - derived from below
    public void saveIncrementally(boolean doFull) {
        ServerChunkCache chunkproviderserver = this.getChunkSource();

        if (doFull) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(getWorld()));
        }

        try (co.aikar.timings.Timing ignored = this.timings.worldSave.startTiming()) {
            if (doFull) {
                this.saveLevelData();
            }

            this.timings.worldSaveChunks.startTiming(); // Paper
            if (!this.noSave()) chunkproviderserver.saveIncrementally();
            this.timings.worldSaveChunks.stopTiming(); // Paper

            // Copied from save()
            // CraftBukkit start - moved from MinecraftServer.saveChunks
            if (doFull) { // Paper
                ServerLevel worldserver1 = this;

                this.serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
                this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save());
                this.convertable.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
            }
            // CraftBukkit end
        }
    }
    // Paper end

    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled) {
        // Paper start - rewrite chunk system - add close param
        this.save(progressListener, flush, savingDisabled, false);
    }
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
        // Paper end - rewrite chunk system - add close param
        ServerChunkCache chunkproviderserver = this.getChunkSource();

        if (!savingDisabled) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(this.getWorld())); // CraftBukkit
            try (co.aikar.timings.Timing ignored = timings.worldSave.startTiming()) { // Paper
            if (progressListener != null) {
                progressListener.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData();
            if (progressListener != null) {
                progressListener.progressStage(Component.translatable("menu.savingChunks"));
            }

                timings.worldSaveChunks.startTiming(); // Paper
            if (!close) chunkproviderserver.save(flush); // Paper - rewrite chunk system
            if (close) chunkproviderserver.close(true); // Paper - rewrite chunk system
                timings.worldSaveChunks.stopTiming(); // Paper
            }// Paper
            // Paper - rewrite chunk system - entity saving moved into ChunkHolder

        } else if (close) { chunkproviderserver.close(false); } // Paper - rewrite chunk system
        // Folia - move into saveLevelData()
    }

    public void saveLevelData() { // Folia - region threading
        if (this.dragonFight != null) {
            this.serverLevelData.setEndDragonFightData(this.dragonFight.saveData()); // CraftBukkit
        }
        // Folia start - region threading
        // moved from save
        // CraftBukkit start - moved from MinecraftServer.saveChunks
        ServerLevel worldserver1 = this;

        this.serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save());
        this.convertable.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        // CraftBukkit end
        // Folia end - region threading

        this.getChunkSource().getDataStorage().save();
    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();

        this.getEntities(filter, predicate, (List) list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result) {
        this.getEntities(filter, predicate, result, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result, int limit) {
        this.getEntities().get(filter, (entity) -> {
            if (predicate.test(entity)) {
                result.add(entity);
                if (result.size() >= limit) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities((EntityTypeTest) EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
        return this.getPlayers(predicate, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int limit) {
        List<ServerPlayer> list = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (predicate.test(entityplayer)) {
                list.add(entityplayer);
                if (list.size() >= limit) {
                    return list;
                }
            }
        }

        return list;
    }

    // Folia start - region threading
    @Nullable
    public ServerPlayer getRandomLocalPlayer() {
        List<ServerPlayer> list = this.getLocalPlayers();
        list = new java.util.ArrayList<>(list);
        list.removeIf((ServerPlayer player) -> {
            return !player.isAlive();
        });

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }
    // Folia end - region threading

    @Nullable
    public ServerPlayer getRandomPlayer() {
        List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public boolean addWithUUID(Entity entity) {
        // CraftBukkit start
        return this.addWithUUID(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean addWithUUID(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringTeleport(Entity entity) {
        // CraftBukkit start
        // SPIGOT-6415: Don't call spawn event for entities which travel trough worlds,
        // since it is only an implementation detail, that a new entity is created when
        // they are traveling between worlds.
        this.addDuringTeleport(entity, null);
    }

    public void addDuringTeleport(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringCommandTeleport(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addDuringPortalTeleport(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addNewPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addRespawnedPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    private void addPlayer(ServerPlayer player) {
        Entity entity = (Entity) this.getEntities().get(player.getUUID());

        if (entity != null) {
            ServerLevel.LOGGER.warn("Force-added player with duplicate UUID {}", player.getUUID());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer) entity, Entity.RemovalReason.DISCARDED);
        }

        this.entityLookup.addNewEntity(player); // Paper - rewite chunk system
    }

    // CraftBukkit start
    private boolean addEntity(Entity entity, CreatureSpawnEvent.SpawnReason spawnReason) {
        org.spigotmc.AsyncCatcher.catchOp("entity add"); // Spigot
        // Paper start
        if (entity.valid) {
            MinecraftServer.LOGGER.error("Attempted Double World add on " + entity, new Throwable());

            if (DEBUG_ENTITIES) {
                Throwable thr = entity.addedToWorldStack;
                if (thr == null) {
                    MinecraftServer.LOGGER.error("Double add entity has no add stacktrace");
                } else {
                    MinecraftServer.LOGGER.error("Double add stacktrace: ", thr);
                }
            }
            return true;
        }
        // Paper end
        if (entity.spawnReason == null) entity.spawnReason = spawnReason; // Paper
        if (entity.isRemoved()) {
            // Paper start
            if (DEBUG_ENTITIES) {
                io.papermc.paper.util.TraceUtil.dumpTraceForThread("Tried to add entity " + entity + " but it was marked as removed already"); // CraftBukkit
                getAddToWorldStackTrace(entity).printStackTrace();
            }
            // Paper end
            // WorldServer.LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityTypes.getKey(entity.getType())); // CraftBukkit
            return false;
        } else {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity && itemEntity.getItem().isEmpty()) return false; // Paper - Prevent empty items from being added
            // Paper start - capture all item additions to the world
            if (this.getCurrentWorldData().captureDrops != null && entity instanceof net.minecraft.world.entity.item.ItemEntity) { // Folia - region threading
                this.getCurrentWorldData().captureDrops.add((net.minecraft.world.entity.item.ItemEntity) entity); // Folia - region threading
                return true;
            }
            // Paper end
            // SPIGOT-6415: Don't call spawn event when reason is null. For example when an entity teleports to a new world.
            if (spawnReason != null && !CraftEventFactory.doEntityAddEventCalling(this, entity, spawnReason)) {
                return false;
            }
            // CraftBukkit end

            return this.entityLookup.addNewEntity(entity); // Paper - rewrite chunk system
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        // CraftBukkit start
        return this.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        Stream<UUID> stream = entity.getSelfAndPassengers().map(Entity::getUUID); // CraftBukkit - decompile error
        //PersistentEntitySectionManager persistententitysectionmanager = this.entityManager; // Paper - rewrite chunk system

        //Objects.requireNonNull(this.entityManager); // Paper - rewrite chunk system
        if (stream.anyMatch(this.entityLookup::hasEntity)) { // Paper - rewrite chunk system
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity, reason); // CraftBukkit
            return true;
        }
    }

    public void unload(LevelChunk chunk) {
        // Spigot Start
        for (net.minecraft.world.level.block.entity.BlockEntity tileentity : chunk.getBlockEntities().values()) {
            if (tileentity instanceof net.minecraft.world.Container) {
                // Paper start - this area looks like it can load chunks, change the behavior
                // chests for example can apply physics to the world
                // so instead we just change the active container and call the event
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((net.minecraft.world.Container) tileentity).getViewers())) {
                    ((org.bukkit.craftbukkit.entity.CraftHumanEntity)h).getHandle().closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper
                }
                // Paper end
            }
        }
        // Spigot End
        chunk.clearAllBlockEntities();
        chunk.unregisterTickContainerFromLevel(this);
    }

    public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        player.remove(reason);
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, LightningStrikeEvent.Cause cause) {
        LightningStrikeEvent lightning = CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);

        if (lightning.isCancelled()) {
            return false;
        }

        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int entityId, BlockPos pos, int progress) {
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        // CraftBukkit start
        Player entityhuman = null;
        Entity entity = this.getEntity(entityId);
        if (entity instanceof Player) entityhuman = (Player) entity;
        // CraftBukkit end

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer != null && entityplayer.level() == this && entityplayer.getId() != entityId) {
                double d0 = (double) pos.getX() - entityplayer.getX();
                double d1 = (double) pos.getY() - entityplayer.getY();
                double d2 = (double) pos.getZ() - entityplayer.getZ();

                // CraftBukkit start
                if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end

                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0D) {
                    entityplayer.connection.send(new ClientboundBlockDestructionPacket(entityId, pos, progress));
                }
            }
        }

    }

    @Override
    public void playSeededSound(@Nullable Player except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.server.getPlayerList().broadcast(except, x, y, z, (double) ((SoundEvent) sound.value()).getRange(volume), this.dimension(), new ClientboundSoundPacket(sound, category, x, y, z, volume, pitch, seed));
    }

    @Override
    public void playSeededSound(@Nullable Player except, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.server.getPlayerList().broadcast(except, entity.getX(), entity.getY(), entity.getZ(), (double) ((SoundEvent) sound.value()).getRange(volume), this.dimension(), new ClientboundSoundEntityPacket(sound, category, entity, volume, pitch, seed));
    }

    @Override
    public void globalLevelEvent(int eventId, BlockPos pos, int data) {
        if (this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().broadcastAll(new ClientboundLevelEventPacket(eventId, pos, data, true));
        } else {
            this.levelEvent((Player) null, eventId, pos, data);
        }

    }

    @Override
    public void levelEvent(@Nullable Player player, int eventId, BlockPos pos, int data) {
        this.server.getPlayerList().broadcast(player, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), 64.0D, this.dimension(), new ClientboundLevelEventPacket(eventId, pos, data, false));
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(GameEvent event, Vec3 emitterPos, GameEvent.Context emitter) {
        // Paper start
        if (this.getChunkIfLoadedImmediately((Mth.floor(emitterPos.x) >> 4), (Mth.floor(emitterPos.z) >> 4)) == null) {
            return;
        }
        // Paper end
        this.gameEventDispatcher.post(event, emitterPos, emitter);
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        if (false && this.isUpdatingNavigations) { // Folia - region threading
            String s = "recursive call to sendBlockUpdated";

            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        if(this.paperConfig().misc.updatePathfindingOnBlockUpdate) { // Paper - option to disable pathfinding updates
        VoxelShape voxelshape = oldState.getCollisionShape(this, pos);
        VoxelShape voxelshape1 = newState.getCollisionShape(this, pos);

        if (Shapes.joinIsNotEmpty(voxelshape, voxelshape1, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList();
            Iterator iterator = this.getCurrentWorldData().getNavigatingMobs(); // Folia - region threading

            while (iterator.hasNext()) {
                // CraftBukkit start - fix SPIGOT-6362
                Mob entityinsentient;
                try {
                    entityinsentient = (Mob) iterator.next();
                } catch (java.util.ConcurrentModificationException ex) {
                    // This can happen because the pathfinder update below may trigger a chunk load, which in turn may cause more navigators to register
                    // In this case we just run the update again across all the iterators as the chunk will then be loaded
                    // As this is a relative edge case it is much faster than copying navigators (on either read or write)
                    this.sendBlockUpdated(pos, oldState, newState, flags);
                    return;
                }
                // CraftBukkit end
                PathNavigation navigationabstract = entityinsentient.getNavigation();

                if (navigationabstract.shouldRecomputePath(pos)) {
                    list.add(navigationabstract);
                }
            }

            try {
                //this.isUpdatingNavigations = true; // Folia - region threading
                iterator = list.iterator();

                while (iterator.hasNext()) {
                    PathNavigation navigationabstract1 = (PathNavigation) iterator.next();

                    navigationabstract1.recomputePath();
                }
            } finally {
                //this.isUpdatingNavigations = false; // Folia - region threading
            }

        }
        } // Paper
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block sourceBlock) {
        if (this.getCurrentWorldData().captureBlockStates) { return; } // Paper - Cancel all physics during placement // Folia - region threading
        this.getCurrentWorldData().neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, (Direction) null); // Folia - region threading
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block sourceBlock, Direction direction) {
        this.getCurrentWorldData().neighborUpdater.updateNeighborsAtExceptFromFacing(pos, sourceBlock, direction); // Folia - region threading
    }

    @Override
    public void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {
        this.getCurrentWorldData().neighborUpdater.neighborChanged(pos, sourceBlock, sourcePos); // Folia - region threading
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        this.getCurrentWorldData().neighborUpdater.neighborChanged(state, pos, sourceBlock, sourcePos, notify); // Folia - region threading
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte status) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundEntityEventPacket(entity, status));
    }

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundDamageEventPacket(entity, damageSource));
    }

    @Override
    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        Explosion explosion = this.explode(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, false);
        // CraftBukkit start
        if (explosion.wasCanceled) {
            return explosion;
        }
        // CraftBukkit end

        if (!explosion.interactsWithBlocks()) {
            explosion.clearToBlow();
        }

        Iterator iterator = this.getLocalPlayers().iterator(); // Folia - region thraeding

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.distanceToSqr(x, y, z) < 4096.0D) {
                entityplayer.connection.send(new ClientboundExplodePacket(x, y, z, power, explosion.getToBlow(), (Vec3) explosion.getHitPlayers().get(entityplayer)));
            }
        }

        return explosion;
    }

    @Override
    public void blockEvent(BlockPos pos, Block block, int type, int data) {
        this.getCurrentWorldData().pushBlockEvent(new BlockEventData(pos, block, type, data)); // Folia - regionised ticking
    }

    private void runBlockEvents() {
        List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64); // Folia - regionised ticking

        // Folia start - regionised ticking
        io.papermc.paper.threadedregions.RegionizedWorldData worldRegionData = this.getCurrentWorldData();
        BlockEventData blockactiondata;
        while ((blockactiondata = worldRegionData.removeFirstBlockEvent()) != null) {
            // Folia end - regionised ticking

            if (this.shouldTickBlocksAt(blockactiondata.pos())) {
                if (this.doBlockEvent(blockactiondata)) {
                    this.server.getPlayerList().broadcast((Player) null, (double) blockactiondata.pos().getX(), (double) blockactiondata.pos().getY(), (double) blockactiondata.pos().getZ(), 64.0D, this.dimension(), new ClientboundBlockEventPacket(blockactiondata.pos(), blockactiondata.block(), blockactiondata.paramA(), blockactiondata.paramB()));
                }
            } else {
                blockEventsToReschedule.add(blockactiondata); // Folia - regionised ticking
            }
        }

        worldRegionData.pushBlockEvents(blockEventsToReschedule); // Folia - regionised ticking
    }

    private boolean doBlockEvent(BlockEventData event) {
        BlockState iblockdata = this.getBlockState(event.pos());

        return iblockdata.is(event.block()) ? iblockdata.triggerEvent(this, event.pos(), event.paramA(), event.paramB()) : false;
    }

    @Override
    public LevelTicks<Block> getBlockTicks() {
        return this.getCurrentWorldData().getBlockLevelTicks(); // Folia - region ticking
    }

    @Override
    public LevelTicks<Fluid> getFluidTicks() {
        return this.getCurrentWorldData().getFluidLevelTicks(); // Folia - region ticking
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed) {
        // CraftBukkit - visibility api support
        return this.sendParticles(null, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed, false);
    }

    public <T extends ParticleOptions> int sendParticles(ServerPlayer sender, T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6, boolean force) {
        // Paper start - Particle API Expansion
        return sendParticles(this.getLocalPlayers(), sender, t0, d0, d1, d2, i, d3, d4, d5, d6, force); // Folia - region threading
    }
    public <T extends ParticleOptions> int sendParticles(List<ServerPlayer> receivers, @Nullable ServerPlayer sender, T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6, boolean force) {
        // Paper end
        ClientboundLevelParticlesPacket packetplayoutworldparticles = new ClientboundLevelParticlesPacket(t0, force, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);
        // CraftBukkit end
        int j = 0;

        for (Player entityhuman : receivers) { // Paper - Particle API Expansion
            ServerPlayer entityplayer = (ServerPlayer) entityhuman; // Paper - Particle API Expansion
            if (sender != null && !entityplayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit

            if (this.sendParticles(entityplayer, force, d0, d1, d2, packetplayoutworldparticles)) { // CraftBukkit
                ++j;
            }
        }

        return j;
    }

    public <T extends ParticleOptions> boolean sendParticles(ServerPlayer viewer, T particle, boolean force, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(particle, force, x, y, z, (float) deltaX, (float) deltaY, (float) deltaZ, (float) speed, count);

        return this.sendParticles(viewer, force, x, y, z, packet);
    }

    private boolean sendParticles(ServerPlayer player, boolean force, double x, double y, double z, Packet<?> packet) {
        if (player.level() != this) {
            return false;
        } else {
            BlockPos blockposition = player.blockPosition();

            if (blockposition.closerToCenterThan(new Vec3(x, y, z), force ? 512.0D : 32.0D)) {
                player.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntity(int id) {
        return (Entity) this.getEntities().get(id);
    }

    /** @deprecated */
    @Deprecated
    @Nullable
    public Entity getEntityOrPart(int id) {
        Entity entity = (Entity) this.getEntities().get(id);

        // Folia start - region threading
        if (entity !=  null) {
            return entity;
        }
        synchronized (this.dragonParts) {
            return this.dragonParts.get(id);
        }
        // Folia end - region threading
    }

    @Nullable
    public Entity getEntity(UUID uuid) {
        return (Entity) this.getEntities().get(uuid);
    }

    @Nullable
    public BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipReferencedStructures) {
        if (!this.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(structureTag);

            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource().getGenerator().findNearestMapStructure(this, (HolderSet) optional.get(), pos, radius, skipReferencedStructures);

                return pair != null ? (BlockPos) pair.getFirst() : null;
            }
        }
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(Predicate<Holder<Biome>> predicate, BlockPos pos, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval) {
        return this.getChunkSource().getGenerator().getBiomeSource().findClosestBiome3d(pos, radius, horizontalBlockCheckInterval, verticalBlockCheckInterval, predicate, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.server.getRecipeManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(String id) {
        // Paper start - Call missing map initialize event & set id
        final DimensionDataStorage storage = this.getServer().overworld().getDataStorage();

        final net.minecraft.world.level.saveddata.SavedData existing = storage.cache.get(id);
        if (existing == null && !storage.cache.containsKey(id)) {
            final net.minecraft.world.level.saveddata.SavedData.Factory<MapItemSavedData> factory = MapItemSavedData.factory();
            final MapItemSavedData map = storage.readSavedData(factory.deserializer(), factory.type(), id);
            storage.cache.put(id, map);
            if (map != null) {
                map.id = id;
                new MapInitializeEvent(map.mapView).callEvent();
                return map;
            }
        }

        return existing instanceof MapItemSavedData data ? data : null;
        // Paper end
    }

    @Override
    public void setMapData(String id, MapItemSavedData state) {
        state.id = id; // CraftBukkit
        this.getServer().overworld().getDataStorage().set(id, state);
    }

    @Override
    public int getFreeMapId() {
        return ((MapIndex) this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.factory(), "idcounts")).getFreeAuxValueForMap();
    }

    // Paper start - helper function for configurable spawn radius
    public void addTicketsForSpawn(int radiusInBlocks, BlockPos spawn) {
        // In order to respect vanilla behavior, which is ensuring everything but the spawn border can tick, we add tickets
        // with level 31 for the non-border spawn chunks
        ServerChunkCache chunkproviderserver = this.getChunkSource();
        int tickRadius = radiusInBlocks - 16;

        // add ticking chunks
        for (int x = -tickRadius; x <= tickRadius; x += 16) {
            for (int z = -tickRadius; z <= tickRadius; z += 16) {
                // radius of 2 will have the current chunk be level 31
                chunkproviderserver.addRegionTicket(TicketType.START, new ChunkPos(spawn.offset(x, 0, z)), 2, Unit.INSTANCE);
            }
        }

        // add border chunks

        // add border along x axis (including corner chunks)
        for (int x = -radiusInBlocks; x <= radiusInBlocks; x += 16) {
            // top
            chunkproviderserver.addRegionTicket(TicketType.START, new ChunkPos(spawn.offset(x, 0, radiusInBlocks)), 1, Unit.INSTANCE); // level 32
            // bottom
            chunkproviderserver.addRegionTicket(TicketType.START, new ChunkPos(spawn.offset(x, 0, -radiusInBlocks)), 1, Unit.INSTANCE); // level 32
        }

        // add border along z axis (excluding corner chunks)
        for (int z = -radiusInBlocks + 16; z < radiusInBlocks; z += 16) {
            // right
            chunkproviderserver.addRegionTicket(TicketType.START, new ChunkPos(spawn.offset(radiusInBlocks, 0, z)), 1, Unit.INSTANCE); // level 32
            // left
            chunkproviderserver.addRegionTicket(TicketType.START, new ChunkPos(spawn.offset(-radiusInBlocks, 0, z)), 1, Unit.INSTANCE); // level 32
        }
    }
    public void removeTicketsForSpawn(int radiusInBlocks, BlockPos spawn) {
        // In order to respect vanilla behavior, which is ensuring everything but the spawn border can tick, we added tickets
        // with level 31 for the non-border spawn chunks
        ServerChunkCache chunkproviderserver = this.getChunkSource();
        int tickRadius = radiusInBlocks - 16;

        // remove ticking chunks
        for (int x = -tickRadius; x <= tickRadius; x += 16) {
            for (int z = -tickRadius; z <= tickRadius; z += 16) {
                // radius of 2 will have the current chunk be level 31
                chunkproviderserver.removeRegionTicket(TicketType.START, new ChunkPos(spawn.offset(x, 0, z)), 2, Unit.INSTANCE);
            }
        }

        // remove border chunks

        // remove border along x axis (including corner chunks)
        for (int x = -radiusInBlocks; x <= radiusInBlocks; x += 16) {
            // top
            chunkproviderserver.removeRegionTicket(TicketType.START, new ChunkPos(spawn.offset(x, 0, radiusInBlocks)), 1, Unit.INSTANCE); // level 32
            // bottom
            chunkproviderserver.removeRegionTicket(TicketType.START, new ChunkPos(spawn.offset(x, 0, -radiusInBlocks)), 1, Unit.INSTANCE); // level 32
        }

        // remove border along z axis (excluding corner chunks)
        for (int z = -radiusInBlocks + 16; z < radiusInBlocks; z += 16) {
            // right
            chunkproviderserver.removeRegionTicket(TicketType.START, new ChunkPos(spawn.offset(radiusInBlocks, 0, z)), 1, Unit.INSTANCE); // level 32
            // left
            chunkproviderserver.removeRegionTicket(TicketType.START, new ChunkPos(spawn.offset(-radiusInBlocks, 0, z)), 1, Unit.INSTANCE); // level 32
        }
    }
    // Paper end

    public void setDefaultSpawnPos(BlockPos pos, float angle) {
        // Paper - configurable spawn radius
        BlockPos prevSpawn = this.getSharedSpawnPos();
        Location prevSpawnLoc = this.getWorld().getSpawnLocation(); // Paper
        //ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(new BlockPosition(this.worldData.a(), 0, this.worldData.c()));

        this.levelData.setSpawn(pos, angle);
        new org.bukkit.event.world.SpawnChangeEvent(this.getWorld(), prevSpawnLoc).callEvent(); // Paper
        if (this.keepSpawnInMemory) {
            // if this keepSpawnInMemory is false a plugin has already removed our tickets, do not re-add
            this.removeTicketsForSpawn(this.paperConfig().spawn.keepSpawnLoadedRange * 16, prevSpawn);
            this.addTicketsForSpawn(this.paperConfig().spawn.keepSpawnLoadedRange * 16, pos);
        }
        this.getServer().getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(pos, angle));
    }

    public LongSet getForcedChunks() {
        ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) this.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");

        return (LongSet) (forcedchunk != null ? LongSets.unmodifiable(forcedchunk.getChunks()) : LongSets.EMPTY_SET);
    }

    public boolean setChunkForced(int x, int z, boolean forced) {
        io.papermc.paper.threadedregions.RegionizedServer.ensureGlobalTickThread("Cannot modify force loaded chunks off of the global region"); // Folia - region threading
        ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) this.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
        ChunkPos chunkcoordintpair = new ChunkPos(x, z);
        long k = chunkcoordintpair.toLong();
        boolean flag1;

        if (forced) {
            flag1 = forcedchunk.getChunks().add(k);
            if (flag1) {
                //this.getChunk(x, z); // Folia - region threading - we must let the chunk load asynchronously
            }
        } else {
            flag1 = forcedchunk.getChunks().remove(k);
        }

        forcedchunk.setDirty(flag1);
        if (flag1) {
            this.getChunkSource().updateChunkForced(chunkcoordintpair, forced);
        }

        return flag1;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void onBlockStateChange(BlockPos pos, BlockState oldBlock, BlockState newBlock) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(oldBlock);
        Optional<Holder<PoiType>> optional1 = PoiTypes.forState(newBlock);

        if (!Objects.equals(optional, optional1)) {
            BlockPos blockposition1 = pos.immutable();

            optional.ifPresent((holder) -> {
                Runnable run = () -> { // Folia - region threading
                    this.getPoiManager().remove(blockposition1);
                    DebugPackets.sendPoiRemovedPacket(this, blockposition1);
                }; // Folia - region threading
                // Folia start - region threading
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueChunkTask(
                    this, blockposition1.getX() >> 4, blockposition1.getZ() >> 4, run
                );
                // Folia end - region threading
            });
            optional1.ifPresent((holder) -> {
                Runnable run = () -> { // Folia - region threading
                    // Paper start
                    if (optional.isEmpty() && this.getPoiManager().exists(blockposition1, poiType -> true)) {
                        this.getPoiManager().remove(blockposition1);
                    }
                    // Paper end
                    this.getPoiManager().add(blockposition1, holder);
                    DebugPackets.sendPoiAddedPacket(this, blockposition1);
                }; // Folia - region threading
                // Folia start - region threading
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueChunkTask(
                    this, blockposition1.getX() >> 4, blockposition1.getZ() >> 4, run
                );
                // Folia end - region threading
            });
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos pos) {
        return this.isCloseToVillage(pos, 1);
    }

    public boolean isVillage(SectionPos sectionPos) {
        return this.isVillage(sectionPos.center());
    }

    public boolean isCloseToVillage(BlockPos pos, int maxDistance) {
        return maxDistance > 6 ? false : this.sectionsToVillage(SectionPos.of(pos)) <= maxDistance;
    }

    public int sectionsToVillage(SectionPos pos) {
        return this.getPoiManager().sectionsToVillage(pos);
    }

    public Raids getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPos pos) {
        return this.raids.getNearbyRaid(pos, 9216);
    }

    public boolean isRaided(BlockPos pos) {
        return this.getRaidAt(pos) != null;
    }

    public void onReputationEvent(ReputationEventType interaction, Entity entity, ReputationEventHandler observer) {
        observer.onReputationEventFrom(interaction, entity);
    }

    public void saveDebugReport(Path path) throws IOException {
        ChunkMap playerchunkmap = this.getChunkSource().chunkMap;
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path.resolve("stats.txt"));

        try {
            //bufferedwriter.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", playerchunkmap.getDistanceManager().getNaturalSpawnChunkCount())); // Folia - region threading
            NaturalSpawner.SpawnState spawnercreature_d = this.getChunkSource().getLastSpawnState();

            if (spawnercreature_d != null) {
                ObjectIterator objectiterator = spawnercreature_d.getMobCategoryCounts().object2IntEntrySet().iterator();

                while (objectiterator.hasNext()) {
                    Entry<MobCategory> entry = (Entry) objectiterator.next();

                    bufferedwriter.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", ((MobCategory) entry.getKey()).getName(), entry.getIntValue()));
                }
            }

            bufferedwriter.write(String.format(Locale.ROOT, "entities: %s\n", this.entityLookup.getDebugInfo())); // Paper - rewrite chunk system
            //bufferedwriter.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size())); // Folia - region threading
            bufferedwriter.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            bufferedwriter.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            bufferedwriter.write("distance_manager: " + playerchunkmap.getDistanceManager().getDebugStatus() + "\n");
            bufferedwriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

        CrashReport crashreport = new CrashReport("Level dump", new Exception("dummy"));

        this.fillReportDetails(crashreport);
        BufferedWriter bufferedwriter1 = Files.newBufferedWriter(path.resolve("example_crash.txt"));

        try {
            bufferedwriter1.write(crashreport.getFriendlyReport());
        } catch (Throwable throwable2) {
            if (bufferedwriter1 != null) {
                try {
                    bufferedwriter1.close();
                } catch (Throwable throwable3) {
                    throwable2.addSuppressed(throwable3);
                }
            }

            throw throwable2;
        }

        if (bufferedwriter1 != null) {
            bufferedwriter1.close();
        }

        Path path1 = path.resolve("chunks.csv");
        BufferedWriter bufferedwriter2 = Files.newBufferedWriter(path1);

        try {
            //playerchunkmap.dumpChunks(bufferedwriter2); // Paper - rewrite chunk system
        } catch (Throwable throwable4) {
            if (bufferedwriter2 != null) {
                try {
                    bufferedwriter2.close();
                } catch (Throwable throwable5) {
                    throwable4.addSuppressed(throwable5);
                }
            }

            throw throwable4;
        }

        if (bufferedwriter2 != null) {
            bufferedwriter2.close();
        }

        Path path2 = path.resolve("entity_chunks.csv");
        BufferedWriter bufferedwriter3 = Files.newBufferedWriter(path2);

        try {
            //this.entityManager.dumpSections(bufferedwriter3); // Paper - rewrite chunk system
        } catch (Throwable throwable6) {
            if (bufferedwriter3 != null) {
                try {
                    bufferedwriter3.close();
                } catch (Throwable throwable7) {
                    throwable6.addSuppressed(throwable7);
                }
            }

            throw throwable6;
        }

        if (bufferedwriter3 != null) {
            bufferedwriter3.close();
        }

        Path path3 = path.resolve("entities.csv");
        BufferedWriter bufferedwriter4 = Files.newBufferedWriter(path3);

        try {
            ServerLevel.dumpEntities(bufferedwriter4, this.getEntities().getAll());
        } catch (Throwable throwable8) {
            if (bufferedwriter4 != null) {
                try {
                    bufferedwriter4.close();
                } catch (Throwable throwable9) {
                    throwable8.addSuppressed(throwable9);
                }
            }

            throw throwable8;
        }

        if (bufferedwriter4 != null) {
            bufferedwriter4.close();
        }

        Path path4 = path.resolve("block_entities.csv");
        BufferedWriter bufferedwriter5 = Files.newBufferedWriter(path4);

        try {
            this.dumpBlockEntityTickers(bufferedwriter5);
        } catch (Throwable throwable10) {
            if (bufferedwriter5 != null) {
                try {
                    bufferedwriter5.close();
                } catch (Throwable throwable11) {
                    throwable10.addSuppressed(throwable11);
                }
            }

            throw throwable10;
        }

        if (bufferedwriter5 != null) {
            bufferedwriter5.close();
        }

    }

    private static void dumpEntities(Writer writer, Iterable<Entity> entities) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("uuid").addColumn("type").addColumn("alive").addColumn("display_name").addColumn("custom_name").build(writer);
        Iterator iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();
            Component ichatbasecomponent = entity.getCustomName();
            Component ichatbasecomponent1 = entity.getDisplayName();

            csvwriter.writeRow(entity.getX(), entity.getY(), entity.getZ(), entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.isAlive(), ichatbasecomponent1.getString(), ichatbasecomponent != null ? ichatbasecomponent.getString() : null);
        }

    }

    private void dumpBlockEntityTickers(Writer writer) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(writer);
        Iterator iterator = null; // Folia - region threading

        while (iterator.hasNext()) {
            TickingBlockEntity tickingblockentity = (TickingBlockEntity) iterator.next();
            BlockPos blockposition = tickingblockentity.getPos();

            csvwriter.writeRow(blockposition.getX(), blockposition.getY(), blockposition.getZ(), tickingblockentity.getType());
        }

    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox box) {
        this.getCurrentWorldData().removeIfBlockEvents((blockactiondata) -> { // Folia - regionised ticking
            return box.isInside(blockactiondata.pos());
        });
    }

    @Override
    public void blockUpdated(BlockPos pos, Block block) {
        if (!this.isDebug()) {
            // CraftBukkit start
            if (this.getCurrentWorldData().populating) { // Folia - region threading
                return;
            }
            // CraftBukkit end
            this.updateNeighborsAt(pos, block);
        }

    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.serverLevelData.isFlatWorld(); // CraftBukkit
    }

    @Override
    public long getSeed() {
        return this.serverLevelData.worldGenOptions().seed(); // CraftBukkit
    }

    @Nullable
    public EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return "region threading"; // Folia - region threading
    }

    private static <T> String getTypeCount(Iterable<T> items, Function<T, String> classifier) {
        try {
            Object2IntOpenHashMap<String> object2intopenhashmap = new Object2IntOpenHashMap();
            Iterator<T> iterator = items.iterator(); // CraftBukkit - decompile error

            while (iterator.hasNext()) {
                T t0 = iterator.next();
                String s = (String) classifier.apply(t0);

                object2intopenhashmap.addTo(s, 1);
            }

            return (String) object2intopenhashmap.object2IntEntrySet().stream().sorted(Comparator.comparing(Entry<String>::getIntValue).reversed()).limit(5L).map((entry) -> { // CraftBukkit - decompile error
                String s1 = (String) entry.getKey();

                return s1 + ":" + entry.getIntValue();
            }).collect(Collectors.joining(","));
        } catch (Exception exception) {
            return "";
        }
    }

    public static void makeObsidianPlatform(ServerLevel world) {
        // CraftBukkit start
        ServerLevel.makeObsidianPlatform(world, null);
    }

    public static void makeObsidianPlatform(ServerLevel worldserver, Entity entity) {
        // CraftBukkit end
        BlockPos blockposition = ServerLevel.END_SPAWN_POINT;
        // Folia start - region threading
        makeObsidianPlatform(worldserver, entity, blockposition);
    }

    public static void makeObsidianPlatform(ServerLevel worldserver, Entity entity, BlockPos blockposition) {
        // Folia end - region threading
        int i = blockposition.getX();
        int j = blockposition.getY() - 2;
        int k = blockposition.getZ();

        // CraftBukkit start
        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(worldserver);
        BlockPos.betweenClosed(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2).forEach((blockposition1) -> {
            blockList.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 3);
        });
        BlockPos.betweenClosed(i - 2, j, k - 2, i + 2, j, k + 2).forEach((blockposition1) -> {
            blockList.setBlock(blockposition1, Blocks.OBSIDIAN.defaultBlockState(), 3);
        });
        if (true) { // Folia - region threading
            blockList.updateList();
        }
        // CraftBukkit end
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        org.spigotmc.AsyncCatcher.catchOp("Chunk getEntities call"); // Spigot
        return this.entityLookup; // Paper - rewrite chunk system
    }

    public void addLegacyChunkEntities(Stream<Entity> entities, ChunkPos forChunk) { // Paper - rewrite chunk system
        this.entityLookup.addLegacyChunkEntities(entities.toList(), forChunk); // Paper - rewrite chunk system
    }

    public void addWorldGenChunkEntities(Stream<Entity> entities, ChunkPos forChunk) { // Paper - rewrite chunk system
        this.entityLookup.addWorldGenChunkEntities(entities.toList(), forChunk); // Paper - rewrite chunk system
    }

    public void startTickingChunk(LevelChunk chunk) {
        chunk.unpackTicks(this.getRedstoneGameTime()); // Folia - region threading
    }

    public void onStructureStartsAvailable(ChunkAccess chunk) {
        // Folia start - region threading
        // no longer needs to be on main
        this.structureCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        // Folia end - region threading
    }

    @Override
    public void close() throws IOException {
        super.close();
        //this.entityManager.close(); // Paper - rewrite chunk system
    }

    @Override
    public String gatherChunkSourceStats() {
        String s = this.chunkSource.gatherStats();

        return "Chunks[S] W: " + s + " E: " + this.entityLookup.getDebugInfo(); // Paper - rewrite chunk system
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        // Paper start - rewrite chunk system
        return this.getChunkIfLoadedImmediately(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos)) != null;
        // Paper end - rewrite chunk system
    }

    public boolean isPositionTickingWithEntitiesLoaded(long chunkPos) { // Folia - region threaded - make public
        // Paper start - optimize is ticking ready type functions
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(chunkPos);
        // isTicking implies the chunk is loaded, and the chunk is loaded now implies the entities are loaded
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(io.papermc.paper.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isNaturalSpawningAllowed(BlockPos pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(io.papermc.paper.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isNaturalSpawningAllowed(ChunkPos pos) {
        // Paper start - rewrite chunk system
        io.papermc.paper.chunk.system.scheduling.NewChunkHolder chunkHolder = this.chunkTaskScheduler.chunkHolderManager.getChunkHolder(io.papermc.paper.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    public RandomSource getRandomSequence(ResourceLocation id) {
        return this.randomSequences.get(id);
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    // Paper start - optimize redstone (Alternate Current)
    @Override
    public alternate.current.wire.WireHandler getWireHandler() {
        return wireHandler;
    }
    // Paper end

    private final class EntityCallbacks implements LevelCallback<Entity> {

        EntityCallbacks() {}

        public void onCreated(Entity entity) {}

        public void onDestroyed(Entity entity) {
            // ServerLevel.this.getScoreboard().entityRemoved(entity); // Folia - region threading
        }

        public void onTickingStart(Entity entity) {
            if (entity instanceof net.minecraft.world.entity.Marker && !paperConfig().entities.markers.tick) return; // Paper - Configurable marker ticking
            ServerLevel.this.getCurrentWorldData().addEntityTickingEntity(entity); // Folia - region threading
        }

        public void onTickingEnd(Entity entity) {
            ServerLevel.this.getCurrentWorldData().removeEntityTickingEntity(entity); // Folia - region threading
            // Paper start - Reset pearls when they stop being ticked
            if (paperConfig().fixes.disableUnloadedChunkEnderpearlExploit && entity instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl) {
                pearl.cachedOwner = null;
                pearl.ownerUUID = null;
            }
            // Paper end
        }

        public void onTrackingStart(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity register"); // Spigot
            ServerLevel.this.getCurrentWorldData().addLoadedEntity(entity); // Folia - region threading
            // ServerLevel.this.getChunkSource().addEntity(entity); // Paper - moved down below valid=true
            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                ServerLevel.this.players.add(entityplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                if (false && ServerLevel.this.isUpdatingNavigations) { // Paper
                    String s = "onTrackingStart called during navigation iteration";

                    Util.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                ServerLevel.this.getCurrentWorldData().addNavigatingMob(entityinsentient); // Folia - region threading
            }

            if (entity instanceof EnderDragon) {
                EnderDragon entityenderdragon = (EnderDragon) entity;
                EnderDragonPart[] aentitycomplexpart = entityenderdragon.getSubEntities();
                int i = aentitycomplexpart.length;

                for (int j = 0; j < i; ++j) {
                    EnderDragonPart entitycomplexpart = aentitycomplexpart[j];

                    synchronized (ServerLevel.this.dragonParts) { // Folia - region threading
                    ServerLevel.this.dragonParts.put(entitycomplexpart.getId(), entitycomplexpart);
                    } // Folia - region threading
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
            entity.valid = true; // CraftBukkit
            ServerLevel.this.getChunkSource().addEntity(entity);
            // Paper start - Set origin location when the entity is being added to the world
            if (entity.getOriginVector() == null) {
                entity.setOrigin(entity.getBukkitEntity().getLocation());
            }
            // Default to current world if unknown, gross assumption but entities rarely change world
            if (entity.getOriginWorld() == null) {
                entity.setOrigin(entity.getOriginVector().toLocation(getWorld()));
            }
            // Paper end
            new com.destroystokyo.paper.event.entity.EntityAddToWorldEvent(entity.getBukkitEntity()).callEvent(); // Paper - fire while valid
        }

        public void onTrackingEnd(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity unregister"); // Spigot
            ServerLevel.this.getCurrentWorldData().removeLoadedEntity(entity);
            // Spigot start
            if ( entity instanceof Player )
            {
                com.google.common.collect.Streams.stream( ServerLevel.this.getServer().getAllLevels() ).map( ServerLevel::getDataStorage ).forEach( (worldData) ->
                {
                    // Folia start - make map data thread-safe
                    List<Object> worldDataCache;
                    synchronized (worldData.cache) {
                        worldDataCache = new java.util.ArrayList<>(worldData.cache.values());
                    }
                    for (Object o : worldDataCache )
                    // Folia end - make map data thread-safe
                    {
                        if ( o instanceof MapItemSavedData )
                        {
                            MapItemSavedData map = (MapItemSavedData) o;
                            synchronized (map) { // Folia - make map data thread-safe
                            map.carriedByPlayers.remove( (Player) entity );
                            for ( Iterator<MapItemSavedData.HoldingPlayer> iter = (Iterator<MapItemSavedData.HoldingPlayer>) map.carriedBy.iterator(); iter.hasNext(); )
                            {
                                if ( iter.next().player == entity )
                                {
                                    map.decorations.remove(entity.getName().getString()); // Paper
                                    iter.remove();
                                }
                            }
                            } // Folia - make map data thread-safe
                        }
                    }
                } );
            }
            // Spigot end
            // Spigot Start
            if (entity.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder && (!(entity instanceof ServerPlayer) || entity.getRemovalReason() != Entity.RemovalReason.KILLED)) { // SPIGOT-6876: closeInventory clears death message
                // Paper start
                if (entity.getBukkitEntity() instanceof org.bukkit.inventory.Merchant merchant && merchant.getTrader() != null) {
                    merchant.getTrader().closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED);
                }
                // Paper end
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((org.bukkit.inventory.InventoryHolder) entity.getBukkitEntity()).getInventory().getViewers())) {
                    h.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper
                }
            }
            // Spigot End
            ServerLevel.this.getChunkSource().removeEntity(entity);
            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                ServerLevel.this.players.remove(entityplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                if (false && ServerLevel.this.isUpdatingNavigations) { // Paper
                    String s = "onTrackingStart called during navigation iteration";

                    Util.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                ServerLevel.this.getCurrentWorldData().removeNavigatingMob(entityinsentient); // Folia - region threading
            }

            if (entity instanceof EnderDragon) {
                EnderDragon entityenderdragon = (EnderDragon) entity;
                EnderDragonPart[] aentitycomplexpart = entityenderdragon.getSubEntities();
                int i = aentitycomplexpart.length;

                for (int j = 0; j < i; ++j) {
                    EnderDragonPart entitycomplexpart = aentitycomplexpart[j];

                    synchronized (ServerLevel.this.dragonParts) { // Folia - region threading
                    ServerLevel.this.dragonParts.remove(entitycomplexpart.getId());
                    } // Folia - region threading
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
            // CraftBukkit start
            entity.valid = false;
            // Folia - region threading - TODO THIS SHIT
            if (!(entity instanceof ServerPlayer)) {
                for (ServerPlayer player : ServerLevel.this.players) {
                    player.getBukkitEntity().onEntityRemove(entity);
                }
            }
            // CraftBukkit end
            new com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent(entity.getBukkitEntity()).callEvent(); // Paper - fire while valid
        }

        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }

    // Paper start
    @Override
    @Nullable
    public Player getGlobalPlayerByUUID(UUID uuid) {
        return this.server.getPlayerList().getPlayer(uuid);
    }
    // Paper end
}
