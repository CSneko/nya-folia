package net.minecraft.world.level;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerInternalException;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import io.papermc.paper.util.MCUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;

// CraftBukkit start
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.world.GenericGameEvent;
// CraftBukkit end

public abstract class Level implements LevelAccessor, AutoCloseable {

    public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("the_end"));
    public static final int MAX_LEVEL_SIZE = 30000000;
    public static final int LONG_PARTICLE_CLIP_RANGE = 512;
    public static final int SHORT_PARTICLE_CLIP_RANGE = 32;
    public static final int MAX_BRIGHTNESS = 15;
    public static final int TICKS_PER_DAY = 24000;
    public static final int MAX_ENTITY_SPAWN_Y = 20000000;
    public static final int MIN_ENTITY_SPAWN_Y = -20000000;
    //protected final List<TickingBlockEntity> blockEntityTickers = Lists.newArrayList(); public final int getTotalTileEntityTickers() { return this.blockEntityTickers.size(); } // Paper // Folia - region threading
    public final int neighbourUpdateMax; //protected final NeighborUpdater neighborUpdater;
    //private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList(); // Folia - region threading
    //private boolean tickingBlockEntities; // Folia - region threading
    public final Thread thread;
    private final boolean isDebug;
    private int skyDarken;
    protected int randValue = RandomSource.create().nextInt();
    protected final int addend = 1013904223;
    protected float oRainLevel;
    public float rainLevel;
    protected float oThunderLevel;
    public float thunderLevel;
    public final RandomSource random = new Entity.RandomRandomSource(); // Folia - region threading
    /** @deprecated */
    @Deprecated
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
    private final ResourceKey<DimensionType> dimensionTypeId;
    private final Holder<DimensionType> dimensionTypeRegistration;
    public final WritableLevelData levelData;
    private final Supplier<ProfilerFiller> profiler;
    public final boolean isClientSide;
    private final WorldBorder worldBorder;
    private final BiomeManager biomeManager;
    private final ResourceKey<Level> dimension;
    private final RegistryAccess registryAccess;
    private final DamageSources damageSources;
    private final java.util.concurrent.atomic.AtomicLong subTickCount = new java.util.concurrent.atomic.AtomicLong(); //private long subTickCount; // Folia - region threading

    // CraftBukkit start Added the following
    private final CraftWorld world;
    public boolean pvpMode;
    public boolean keepSpawnInMemory = true;
    public org.bukkit.generator.ChunkGenerator generator;
    public static final boolean DEBUG_ENTITIES = Boolean.getBoolean("debug.entities"); // Paper

    // Folia - region threading - moved to regionised data
    public final it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<SpawnCategory> ticksPerSpawnCategory = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>();
    // Folia - region threading - moved to regionised data
    // Folia - region threading
    public final org.spigotmc.SpigotWorldConfig spigotConfig; // Spigot
    // Paper start
    private final io.papermc.paper.configuration.WorldConfiguration paperConfig;
    public io.papermc.paper.configuration.WorldConfiguration paperConfig() {
        return this.paperConfig;
    }
    // Paper end

    public final com.destroystokyo.paper.antixray.ChunkPacketBlockController chunkPacketBlockController; // Paper - Anti-Xray
    public final co.aikar.timings.WorldTimingsHandler timings; // Paper
    public static BlockPos lastPhysicsProblem; // Spigot
    private org.spigotmc.TickLimiter entityLimiter;
    private org.spigotmc.TickLimiter tileLimiter;
    //private int tileTickPosition; // Folia - region threading
    //public final Map<Explosion.CacheKey, Float> explosionDensityCache = new HashMap<>(); // Paper - Optimize explosions // Folia - region threading
    //public java.util.ArrayDeque<net.minecraft.world.level.block.RedstoneTorchBlock.Toggle> redstoneUpdateInfos; // Paper - Move from Map in BlockRedstoneTorch to here // Folia - region threading

    // Paper start - fix and optimise world upgrading
    // copied from below
    public static ResourceKey<DimensionType> getDimensionKey(DimensionType manager) {
        return ((org.bukkit.craftbukkit.CraftServer)org.bukkit.Bukkit.getServer()).getHandle().getServer().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).getResourceKey(manager).orElseThrow(() -> {
            return new IllegalStateException("Unregistered dimension type: " + manager);
        });
    }
    // Paper end - fix and optimise world upgrading

    public CraftWorld getWorld() {
        return this.world;
    }

    public CraftServer getCraftServer() {
        return (CraftServer) Bukkit.getServer();
    }

    // Paper start
    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkIfLoaded(chunkX, chunkZ) != null;
    }
    // Paper end

    public abstract ResourceKey<LevelStem> getTypeKey();

    // Folia start - region ticking
    public final io.papermc.paper.threadedregions.RegionizedData<io.papermc.paper.threadedregions.RegionizedWorldData> worldRegionData
        = new io.papermc.paper.threadedregions.RegionizedData<>(
        (ServerLevel)this, () -> new io.papermc.paper.threadedregions.RegionizedWorldData((ServerLevel)Level.this),
        io.papermc.paper.threadedregions.RegionizedWorldData.REGION_CALLBACK
    );
    public volatile io.papermc.paper.threadedregions.RegionizedServer.WorldLevelData tickData;
    public final java.util.concurrent.ConcurrentHashMap.KeySetView<net.minecraft.server.level.ChunkHolder, Boolean> needsChangeBroadcasting = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public io.papermc.paper.threadedregions.RegionizedWorldData getCurrentWorldData() {
        final io.papermc.paper.threadedregions.RegionizedWorldData ret = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();
        if (ret == null) {
            return ret;
        }
        Level world = ret.world;
        if (world != this) {
            throw new IllegalStateException("World mismatch: expected " + this.getWorld().getName() + " but got " + world.getWorld().getName());
        }
        return ret;
    }

    @Override
    public List<net.minecraft.server.level.ServerPlayer> getLocalPlayers() {
        return this.getCurrentWorldData().getLocalPlayers();
    }
    // Folia end - region ticking
    // Folia start - profiler
    public final int tickTimerId;
    // Folia end - profiler

    protected Level(WritableLevelData worlddatamutable, ResourceKey<Level> resourcekey, RegistryAccess iregistrycustom, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean flag, boolean flag1, long i, int j, org.bukkit.generator.ChunkGenerator gen, org.bukkit.generator.BiomeProvider biomeProvider, org.bukkit.World.Environment env, java.util.function.Function<org.spigotmc.SpigotWorldConfig, io.papermc.paper.configuration.WorldConfiguration> paperWorldConfigCreator, java.util.concurrent.Executor executor) { // Paper - Async-Anti-Xray - Pass executor
        this.spigotConfig = new org.spigotmc.SpigotWorldConfig(((net.minecraft.world.level.storage.PrimaryLevelData) worlddatamutable).getLevelName()); // Spigot
        this.paperConfig = paperWorldConfigCreator.apply(this.spigotConfig); // Paper
        this.generator = gen;
        this.world = new CraftWorld((ServerLevel) this, gen, biomeProvider, env);

        // CraftBukkit Ticks things
        for (SpawnCategory spawnCategory : SpawnCategory.values()) {
            if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                this.ticksPerSpawnCategory.put(spawnCategory, (long) this.getCraftServer().getTicksPerSpawns(spawnCategory));
            }
        }

        // CraftBukkit end
        this.profiler = supplier;
        this.levelData = worlddatamutable;
        this.dimensionTypeRegistration = holder;
        this.dimensionTypeId = (ResourceKey) holder.unwrapKey().orElseThrow(() -> {
            return new IllegalArgumentException("Dimension must be registered, got " + holder);
        });
        final DimensionType dimensionmanager = (DimensionType) holder.value();

        this.dimension = resourcekey;
        this.isClientSide = flag;
        if (dimensionmanager.coordinateScale() != 1.0D) {
            this.worldBorder = new WorldBorder() {
                @Override
                public double getCenterX() {
                    return super.getCenterX(); // CraftBukkit
                }

                @Override
                public double getCenterZ() {
                    return super.getCenterZ(); // CraftBukkit
                }
            };
        } else {
            this.worldBorder = new WorldBorder();
        }

        this.thread = Thread.currentThread();
        this.biomeManager = new BiomeManager(this, i);
        this.isDebug = flag1;
        this.neighbourUpdateMax = j; // Folia - region threading
        this.registryAccess = iregistrycustom;
        this.damageSources = new DamageSources(iregistrycustom);
        // CraftBukkit start
        this.getWorldBorder().world = (ServerLevel) this;
        // From PlayerList.setPlayerFileData
        this.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder border, double size) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderSizePacket(border), border.world);
            }

            @Override
            public void onBorderSizeLerping(WorldBorder border, double fromSize, double toSize, long time) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderLerpSizePacket(border), border.world);
            }

            @Override
            public void onBorderCenterSet(WorldBorder border, double centerX, double centerZ) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderCenterPacket(border), border.world);
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), border.world);
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder border, int warningBlockDistance) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), border.world);
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {}

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZoneRadius) {}
        });
        // CraftBukkit end
        this.timings = new co.aikar.timings.WorldTimingsHandler(this); // Paper - code below can generate new world and access timings
        this.keepSpawnInMemory = this.paperConfig().spawn.keepSpawnLoaded; // Paper
        this.entityLimiter = new org.spigotmc.TickLimiter(this.spigotConfig.entityMaxTickTime);
        this.tileLimiter = new org.spigotmc.TickLimiter(this.spigotConfig.tileMaxTickTime);
        this.chunkPacketBlockController = this.paperConfig().anticheat.antiXray.enabled ? new com.destroystokyo.paper.antixray.ChunkPacketBlockControllerAntiXray(this, executor) : com.destroystokyo.paper.antixray.ChunkPacketBlockController.NO_OPERATION_INSTANCE; // Paper - Anti-Xray
        // Paper start - optimise collisions
        this.minSection = io.papermc.paper.util.WorldUtil.getMinSection(this);
        this.maxSection = io.papermc.paper.util.WorldUtil.getMaxSection(this);
        // Paper end - optimise collisions
        // Folia start - profiler
        this.tickTimerId = ca.spottedleaf.leafprofiler.LProfilerRegistry.GLOBAL_REGISTRY.getOrCreateTimer(" Tick World: " + resourcekey.location().toString());
        // Folia end - profiler
    }

    // Paper start
    // ret true if no collision
    public final boolean checkEntityCollision(BlockState data, Entity source, net.minecraft.world.phys.shapes.CollisionContext voxelshapedcollision,
                                              BlockPos position, boolean checkCanSee) {
        // Copied from IWorldReader#a(IBlockData, BlockPosition, VoxelShapeCollision) & EntityAccess#a(Entity, VoxelShape)
        net.minecraft.world.phys.shapes.VoxelShape voxelshape = data.getCollisionShape(this, position, voxelshapedcollision);
        if (voxelshape.isEmpty()) {
            return true;
        }

        voxelshape = voxelshape.move((double) position.getX(), (double) position.getY(), (double) position.getZ());
        if (voxelshape.isEmpty()) {
            return true;
        }

        List<Entity> entities = this.getEntities(null, voxelshape.bounds());
        for (int i = 0, len = entities.size(); i < len; ++i) {
            Entity entity = entities.get(i);

            if (checkCanSee && source instanceof net.minecraft.server.level.ServerPlayer && entity instanceof net.minecraft.server.level.ServerPlayer
                && !((net.minecraft.server.level.ServerPlayer) source).getBukkitEntity().canSee(((net.minecraft.server.level.ServerPlayer) entity).getBukkitEntity())) {
                continue;
            }

            // !entity1.dead && entity1.i && (entity == null || !entity1.x(entity));
            // elide the last check since vanilla calls with entity = null
            // only we care about the source for the canSee check
            if (entity.isRemoved() || !entity.blocksBuilding) {
                continue;
            }

            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(voxelshape, net.minecraft.world.phys.shapes.Shapes.create(entity.getBoundingBox()), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                return false;
            }
        }

        return true;
    }
    // Paper end
    // Paper start - optimise collisions
    public final int minSection;
    public final int maxSection;

    @Override
    public final boolean isUnobstructed(final Entity entity) {
        final AABB boundingBox = entity.getBoundingBox();
        if (io.papermc.paper.util.CollisionUtil.isEmpty(boundingBox)) {
            return false;
        }

        final List<Entity> entities = this.getEntities(
                entity,
                boundingBox.inflate(-io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON, -io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON, -io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON),
                null
        );

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator() || otherEntity.isRemoved() || !otherEntity.blocksBuilding || otherEntity.isPassengerOfSameVehicle(entity)) {
                continue;
            }

            return false;
        }

        return true;
    }

    private static net.minecraft.world.phys.BlockHitResult miss(final ClipContext clipContext) {
        final Vec3 to = clipContext.getTo();
        final Vec3 from = clipContext.getFrom();

        return net.minecraft.world.phys.BlockHitResult.miss(to, Direction.getNearest(from.x - to.x, from.y - to.y, from.z - to.z), BlockPos.containing(to.x, to.y, to.z));
    }

    private static final FluidState AIR_FLUIDSTATE = Fluids.EMPTY.defaultFluidState();

    private static net.minecraft.world.phys.BlockHitResult fastClip(final Vec3 from, final Vec3 to, final Level level,
                                                                    final ClipContext clipContext) {
        final double adjX = io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON * (from.x - to.x);
        final double adjY = io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON * (from.y - to.y);
        final double adjZ = io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON * (from.z - to.z);

        if (adjX == 0.0 && adjY == 0.0 && adjZ == 0.0) {
            return miss(clipContext);
        }

        final double toXAdj = to.x - adjX;
        final double toYAdj = to.y - adjY;
        final double toZAdj = to.z - adjZ;
        final double fromXAdj = from.x + adjX;
        final double fromYAdj = from.y + adjY;
        final double fromZAdj = from.z + adjZ;

        int currX = Mth.floor(fromXAdj);
        int currY = Mth.floor(fromYAdj);
        int currZ = Mth.floor(fromZAdj);

        final BlockPos.MutableBlockPos currPos = new BlockPos.MutableBlockPos();

        final double diffX = toXAdj - fromXAdj;
        final double diffY = toYAdj - fromYAdj;
        final double diffZ = toZAdj - fromZAdj;

        final double dxDouble = Math.signum(diffX);
        final double dyDouble = Math.signum(diffY);
        final double dzDouble = Math.signum(diffZ);

        final int dx = (int)dxDouble;
        final int dy = (int)dyDouble;
        final int dz = (int)dzDouble;

        final double normalizedDiffX = diffX == 0.0 ? Double.MAX_VALUE : dxDouble / diffX;
        final double normalizedDiffY = diffY == 0.0 ? Double.MAX_VALUE : dyDouble / diffY;
        final double normalizedDiffZ = diffZ == 0.0 ? Double.MAX_VALUE : dzDouble / diffZ;

        double normalizedCurrX = normalizedDiffX * (diffX > 0.0 ? (1.0 - Mth.frac(fromXAdj)) : Mth.frac(fromXAdj));
        double normalizedCurrY = normalizedDiffY * (diffY > 0.0 ? (1.0 - Mth.frac(fromYAdj)) : Mth.frac(fromYAdj));
        double normalizedCurrZ = normalizedDiffZ * (diffZ > 0.0 ? (1.0 - Mth.frac(fromZAdj)) : Mth.frac(fromZAdj));

        net.minecraft.world.level.chunk.LevelChunkSection[] lastChunk = null;
        net.minecraft.world.level.chunk.PalettedContainer<BlockState> lastSection = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkY = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final int minSection = level.minSection;
        final net.minecraft.server.level.ServerChunkCache chunkProvider = (net.minecraft.server.level.ServerChunkCache)level.getChunkSource();

        for (;;) {
            currPos.set(currX, currY, currZ);

            final int newChunkX = currX >> 4;
            final int newChunkY = currY >> 4;
            final int newChunkZ = currZ >> 4;

            final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));
            final int chunkYDiff = newChunkY ^ lastChunkY;

            if ((chunkDiff | chunkYDiff) != 0) {
                if (chunkDiff != 0) {
                    LevelChunk chunk = chunkProvider.getChunkAtIfLoadedImmediately(newChunkX, newChunkZ);
                    lastChunk = chunk == null ? null : chunk.getSections(); // diff: don't load chunks for this
                }
                final int sectionY = newChunkY - minSection;
                lastSection = lastChunk != null && sectionY >= 0 && sectionY < lastChunk.length ? lastChunk[sectionY].states : null;

                lastChunkX = newChunkX;
                lastChunkY = newChunkY;
                lastChunkZ = newChunkZ;
            }

            final BlockState blockState;
            if (lastSection != null && !(blockState = lastSection.get((currX & 15) | ((currZ & 15) << 4) | ((currY & 15) << (4+4)))).isAir()) {
                final net.minecraft.world.phys.shapes.VoxelShape blockCollision = clipContext.getBlockShape(blockState, level, currPos);

                final net.minecraft.world.phys.BlockHitResult blockHit = blockCollision.isEmpty() ? null : level.clipWithInteractionOverride(from, to, currPos, blockCollision, blockState);

                final net.minecraft.world.phys.shapes.VoxelShape fluidCollision;
                final FluidState fluidState;
                if (clipContext.fluid != ClipContext.Fluid.NONE && (fluidState = blockState.getFluidState()) != AIR_FLUIDSTATE) {
                    fluidCollision = clipContext.getFluidShape(fluidState, level, currPos);

                    final net.minecraft.world.phys.BlockHitResult fluidHit = fluidCollision.clip(from, to, currPos);

                    if (fluidHit != null) {
                        if (blockHit == null) {
                            return fluidHit;
                        }

                        return from.distanceToSqr(blockHit.getLocation()) <= from.distanceToSqr(fluidHit.getLocation()) ? blockHit : fluidHit;
                    }
                }

                if (blockHit != null) {
                    return blockHit;
                }
            } // else: usually fall here

            if (normalizedCurrX > 1.0 && normalizedCurrY > 1.0 && normalizedCurrZ > 1.0) {
                return miss(clipContext);
            }

            // inc the smallest normalized coordinate

            if (normalizedCurrX < normalizedCurrY) {
                if (normalizedCurrX < normalizedCurrZ) {
                    currX += dx;
                    normalizedCurrX += normalizedDiffX;
                } else {
                    // x < y && x >= z <--> z < y && z <= x
                    currZ += dz;
                    normalizedCurrZ += normalizedDiffZ;
                }
            } else if (normalizedCurrY < normalizedCurrZ) {
                // y <= x && y < z
                currY += dy;
                normalizedCurrY += normalizedDiffY;
            } else {
                // y <= x && z <= y <--> z <= y && z <= x
                currZ += dz;
                normalizedCurrZ += normalizedDiffZ;
            }
        }
    }

    /**
     * @reason Route to optimized call
     * @author Spottedleaf
     */
    @Override
    public final net.minecraft.world.phys.BlockHitResult clip(final ClipContext clipContext) {
        // can only do this in this class, as not everything that implements BlockGetter can retrieve chunks
        return fastClip(clipContext.getFrom(), clipContext.getTo(), this, clipContext);
    }

    @Override
    public final boolean noCollision(final Entity entity, final AABB box, final boolean loadChunks) {
        int flags = io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_ONLY;
        if (entity != null) {
            flags |= io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_BORDER;
        }
        if (loadChunks) {
            flags |= io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_LOAD_CHUNKS;
        }
        if (io.papermc.paper.util.CollisionUtil.getCollisionsForBlocksOrWorldBorder(this, entity, box, null, null, flags, null)) {
            return false;
        }

        return !io.papermc.paper.util.CollisionUtil.getEntityHardCollisions(this, entity, box, null, flags, null);
    }

    @Override
    public final boolean collidesWithSuffocatingBlock(final Entity entity, final AABB box) {
        return io.papermc.paper.util.CollisionUtil.getCollisionsForBlocksOrWorldBorder(this, entity, box, null, null,
            io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_ONLY,
            (final BlockState state, final BlockPos pos) -> {
                return state.isSuffocating(Level.this, pos);
            }
        );
    }

    private static net.minecraft.world.phys.shapes.VoxelShape inflateAABBToVoxel(final AABB aabb, final double x, final double y, final double z) {
        return net.minecraft.world.phys.shapes.Shapes.create(
                aabb.minX - x,
                aabb.minY - y,
                aabb.minZ - z,

                aabb.maxX + x,
                aabb.maxY + y,
                aabb.maxZ + z
        );
    }

    @Override
    public final java.util.Optional<Vec3> findFreePosition(final Entity entity, final net.minecraft.world.phys.shapes.VoxelShape boundsShape, final Vec3 fromPosition,
                                                           final double rangeX, final double rangeY, final double rangeZ) {
        if (boundsShape.isEmpty()) {
            return java.util.Optional.empty();
        }

        final double expandByX = rangeX * 0.5;
        final double expandByY = rangeY * 0.5;
        final double expandByZ = rangeZ * 0.5;

        // note: it is useless to look at shapes outside of range / 2.0
        final AABB collectionVolume = boundsShape.bounds().inflate(expandByX, expandByY, expandByZ);

        final List<AABB> aabbs = new java.util.ArrayList<>();
        final List<net.minecraft.world.phys.shapes.VoxelShape> voxels = new java.util.ArrayList<>();

        io.papermc.paper.util.CollisionUtil.getCollisionsForBlocksOrWorldBorder(
                this, entity, collectionVolume, voxels, aabbs,
                io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null
        );

        // push voxels into aabbs
        for (int i = 0, len = voxels.size(); i < len; ++i) {
            aabbs.addAll(voxels.get(i).toAabbs());
        }

        // expand AABBs
        final net.minecraft.world.phys.shapes.VoxelShape first = aabbs.isEmpty() ? net.minecraft.world.phys.shapes.Shapes.empty() : inflateAABBToVoxel(aabbs.get(0), expandByX, expandByY, expandByZ);
        final net.minecraft.world.phys.shapes.VoxelShape[] rest = new net.minecraft.world.phys.shapes.VoxelShape[Math.max(0, aabbs.size() - 1)];

        for (int i = 1, len = aabbs.size(); i < len; ++i) {
            rest[i - 1] = inflateAABBToVoxel(aabbs.get(i), expandByX, expandByY, expandByZ);
        }

        // use optimized implementation of ORing the shapes together
        final net.minecraft.world.phys.shapes.VoxelShape joined = net.minecraft.world.phys.shapes.Shapes.or(first, rest);

        // find free space
        // can use unoptimized join here (instead of join()), as closestPointTo uses toAabbs()
        final net.minecraft.world.phys.shapes.VoxelShape freeSpace = net.minecraft.world.phys.shapes.Shapes.joinUnoptimized(
            boundsShape, joined, net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST
        );

        return freeSpace.closestPointTo(fromPosition);
    }

    @Override
    public final java.util.Optional<BlockPos> findSupportingBlock(final Entity entity, final AABB aabb) {
        final int minBlockX = Mth.floor(aabb.minX - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockX = Mth.floor(aabb.maxX + io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockY = Mth.floor(aabb.minY - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockY = Mth.floor(aabb.maxY + io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockZ = Mth.floor(aabb.minZ - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockZ = Mth.floor(aabb.maxZ + io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) + 1;

        io.papermc.paper.util.CollisionUtil.LazyEntityCollisionContext collisionContext = null;

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos selected = null;
        double selectedDistance = Double.MAX_VALUE;

        final Vec3 entityPos = entity.position();

        LevelChunk lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final net.minecraft.server.level.ServerChunkCache chunkProvider = (net.minecraft.server.level.ServerChunkCache)this.getChunkSource();

        for (int currZ = minBlockZ; currZ <= maxBlockZ; ++currZ) {
            pos.setZ(currZ);
            for (int currX = minBlockX; currX <= maxBlockX; ++currX) {
                pos.setX(currX);

                final int newChunkX = currX >> 4;
                final int newChunkZ = currZ >> 4;

                final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

                if (chunkDiff != 0) {
                    lastChunk = chunkProvider.getChunkAtIfLoadedImmediately(newChunkX, newChunkZ);
                }

                if (lastChunk == null) {
                    continue;
                }
                for (int currY = minBlockY; currY <= maxBlockY; ++currY) {
                    int edgeCount = ((currX == minBlockX || currX == maxBlockX) ? 1 : 0) +
                            ((currY == minBlockY || currY == maxBlockY) ? 1 : 0) +
                            ((currZ == minBlockZ || currZ == maxBlockZ) ? 1 : 0);
                    if (edgeCount == 3) {
                        continue;
                    }

                    pos.setY(currY);

                    final double distance = pos.distToCenterSqr(entityPos);
                    if (distance > selectedDistance || (distance == selectedDistance && selected.compareTo(pos) >= 0)) {
                        continue;
                    }

                    final BlockState state = lastChunk.getBlockState(currX, currY, currZ);
                    if (state.emptyCollisionShape()) {
                        continue;
                    }

                    if ((edgeCount != 1 || state.hasLargeCollisionShape()) && (edgeCount != 2 || state.getBlock() == Blocks.MOVING_PISTON)) {
                        if (collisionContext == null) {
                            collisionContext = new io.papermc.paper.util.CollisionUtil.LazyEntityCollisionContext(entity);
                        }
                        final net.minecraft.world.phys.shapes.VoxelShape blockCollision = state.getCollisionShape(lastChunk, pos, collisionContext);
                        if (blockCollision.isEmpty()) {
                            continue;
                        }

                        // avoid VoxelShape#move by shifting the entity collision shape instead
                        final AABB shiftedAABB = aabb.move(-(double)currX, -(double)currY, -(double)currZ);

                        final AABB singleAABB = blockCollision.getSingleAABBRepresentation();
                        if (singleAABB != null) {
                            if (!io.papermc.paper.util.CollisionUtil.voxelShapeIntersect(singleAABB, shiftedAABB)) {
                                continue;
                            }

                            selected = pos.immutable();
                            selectedDistance = distance;
                            continue;
                        }

                        if (!io.papermc.paper.util.CollisionUtil.voxelShapeIntersectNoEmpty(blockCollision, shiftedAABB)) {
                            continue;
                        }

                        selected = pos.immutable();
                        selectedDistance = distance;
                        continue;
                    }
                }
            }
        }

        return java.util.Optional.ofNullable(selected);
    }
    // Paper end - optimise collisions
    @Override
    public boolean isClientSide() {
        return this.isClientSide;
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return null;
    }

    // Paper start - Broken down method of raytracing for EntityLiving#hasLineOfSight, replaces BlockGetter#clip(CollisionContext)
    public net.minecraft.world.phys.BlockHitResult.Type clipDirect(Vec3 start, Vec3 end, net.minecraft.world.phys.shapes.CollisionContext collisionContext) {
        // most of this code comes from BlockGetter#clip(CollisionContext, BiFunction, Function), but removes the needless functions
        if (start.equals(end)) {
            return net.minecraft.world.phys.BlockHitResult.Type.MISS;
        }

        final double endX = Mth.lerp(-1.0E-7D, end.x, start.x);
        final double endY = Mth.lerp(-1.0E-7D, end.y, start.y);
        final double endZ = Mth.lerp(-1.0E-7D, end.z, start.z);

        final double startX = Mth.lerp(-1.0E-7D, start.x, end.x);
        final double startY = Mth.lerp(-1.0E-7D, start.y, end.y);
        final double startZ = Mth.lerp(-1.0E-7D, start.z, end.z);

        int currentX = Mth.floor(startX);
        int currentY = Mth.floor(startY);
        int currentZ = Mth.floor(startZ);

        final BlockPos.MutableBlockPos currentBlock = new BlockPos.MutableBlockPos(currentX, currentY, currentZ);

        LevelChunk chunk = this.getChunkIfLoaded(currentBlock);
        if (chunk == null) {
            return net.minecraft.world.phys.BlockHitResult.Type.MISS;
        }

        final net.minecraft.world.phys.BlockHitResult.Type initialCheck = this.clipDirect(start, end, currentBlock, chunk.getBlockState(currentBlock), collisionContext);
        if (initialCheck != null) {
            return initialCheck;
        }

        final double diffX = endX - startX;
        final double diffY = endY - startY;
        final double diffZ = endZ - startZ;

        final int xDirection = Mth.sign(diffX);
        final int yDirection = Mth.sign(diffY);
        final int zDirection = Mth.sign(diffZ);

        final double normalizedX = xDirection == 0 ? Double.MAX_VALUE : (double) xDirection / diffX;
        final double normalizedY = yDirection == 0 ? Double.MAX_VALUE : (double) yDirection / diffY;
        final double normalizedZ = zDirection == 0 ? Double.MAX_VALUE : (double) zDirection / diffZ;

        double normalizedXDirection = normalizedX * (xDirection > 0 ? 1.0D - Mth.frac(startX) : Mth.frac(startX));
        double normalizedYDirection = normalizedY * (yDirection > 0 ? 1.0D - Mth.frac(startY) : Mth.frac(startY));
        double normalizedZDirection = normalizedZ * (zDirection > 0 ? 1.0D - Mth.frac(startZ) : Mth.frac(startZ));

        net.minecraft.world.phys.BlockHitResult.Type result;

        do {
            if (normalizedXDirection > 1.0D && normalizedYDirection > 1.0D && normalizedZDirection > 1.0D) {
                return net.minecraft.world.phys.BlockHitResult.Type.MISS;
            }

            if (normalizedXDirection < normalizedYDirection) {
                if (normalizedXDirection < normalizedZDirection) {
                    currentX += xDirection;
                    normalizedXDirection += normalizedX;
                } else {
                    currentZ += zDirection;
                    normalizedZDirection += normalizedZ;
                }
            } else if (normalizedYDirection < normalizedZDirection) {
                currentY += yDirection;
                normalizedYDirection += normalizedY;
            } else {
                currentZ += zDirection;
                normalizedZDirection += normalizedZ;
            }

            currentBlock.set(currentX, currentY, currentZ);
            if (chunk.getPos().x != currentBlock.getX() >> 4 || chunk.getPos().z != currentBlock.getZ() >> 4) {
                chunk = this.getChunkIfLoaded(currentBlock);
                if (chunk == null) {
                    return net.minecraft.world.phys.BlockHitResult.Type.MISS;
                }
            }
            result = this.clipDirect(start, end, currentBlock, chunk.getBlockState(currentBlock), collisionContext);
        } while (result == null);

        return result;
    }
    // Paper end

    public boolean isInWorldBounds(BlockPos pos) {
        return pos.isInsideBuildHeightAndWorldBoundsHorizontal(this); // Paper - use better/optimized check
    }

    public static boolean isInSpawnableBounds(BlockPos pos) {
        return !Level.isOutsideSpawnableHeight(pos.getY()) && Level.isInWorldBoundsHorizontal(pos);
    }

    private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000; // Dif on change
    }

    private static boolean isOutsideSpawnableHeight(int y) {
        return y < -20000000 || y >= 20000000;
    }

    public final LevelChunk getChunkAt(BlockPos pos) { // Paper - help inline
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public final LevelChunk getChunk(int chunkX, int chunkZ) { // Paper - final to help inline
        // Paper start - make sure loaded chunks get the inlined variant of this function
        net.minecraft.server.level.ServerChunkCache cps = ((ServerLevel)this).getChunkSource();
        if (cps.mainThread == Thread.currentThread()) {
            LevelChunk ifLoaded = cps.getChunkAtIfLoadedMainThread(chunkX, chunkZ);
            if (ifLoaded != null) {
                return ifLoaded;
            }
        }
        // Paper end - make sure loaded chunks get the inlined variant of this function
        return (LevelChunk) this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true); // Paper - avoid a method jump
    }

    // Paper start - if loaded
    @Nullable
    @Override
    public final ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        return ((ServerLevel)this).chunkSource.getChunkAtIfLoadedImmediately(x, z);
    }

    @Override
    @Nullable
    public final BlockState getBlockStateIfLoaded(BlockPos pos) {
        // CraftBukkit start - tree generation
        if (this.getCurrentWorldData().captureTreeGeneration) { // Folia - region threading
            CraftBlockState previous = this.getCurrentWorldData().capturedBlockStates.get(pos); // Folia - region threading
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            ChunkAccess chunk = this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);

            return chunk == null ? null : chunk.getBlockState(pos);
        }
    }

    @Override
    public final FluidState getFluidIfLoaded(BlockPos blockposition) {
        ChunkAccess chunk = this.getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);

        return chunk == null ? null : chunk.getFluidState(blockposition);
    }

    @Override
    public final boolean hasChunkAt(BlockPos pos) {
        return getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4) != null; // Paper
    }

    public final boolean isLoadedAndInBounds(BlockPos blockposition) { // Paper - final for inline
        return getWorldBorder().isWithinBounds(blockposition) && getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4) != null;
    }

    public @Nullable LevelChunk getChunkIfLoaded(int x, int z) { // Overridden in WorldServer for ABI compat which has final
        return ((ServerLevel) this).getChunkSource().getChunkAtIfLoadedImmediately(x, z);
    }
    public final @Nullable LevelChunk getChunkIfLoaded(BlockPos blockposition) {
        return ((ServerLevel) this).getChunkSource().getChunkAtIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);
    }

    //  reduces need to do isLoaded before getType
    public final @Nullable BlockState getBlockStateIfLoadedAndInBounds(BlockPos blockposition) {
        return getWorldBorder().isWithinBounds(blockposition) ? getBlockStateIfLoaded(blockposition) : null;
    }

    @Override
    public final ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) { // Paper - final for inline
        // Paper end
        ChunkAccess ichunkaccess = this.getChunkSource().getChunk(chunkX, chunkZ, leastStatus, create);

        if (ichunkaccess == null && create) {
            throw new IllegalStateException("Should always be able to create a chunk!");
        } else {
            return ichunkaccess;
        }
    }

    @Override
    public final boolean setBlock(BlockPos pos, BlockState state, int flags) { // Paper - final for inline
        return this.setBlock(pos, state, flags, 512);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        io.papermc.paper.util.TickThread.ensureTickThread((ServerLevel)this, pos, "Updating block asynchronously"); // Folia - region threading
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.getCurrentWorldData(); // Folia - region threading
        // CraftBukkit start - tree generation
        if (worldData.captureTreeGeneration) { // Folia - region threading
            // Paper start
            BlockState type = getBlockState(pos);
            if (!type.isDestroyable()) return false;
            // Paper end
            CraftBlockState blockstate = worldData.capturedBlockStates.get(pos); // Folia - region threading
            if (blockstate == null) {
                blockstate = CapturedBlockState.getTreeBlockState(this, pos, flags);
                worldData.capturedBlockStates.put(pos.immutable(), blockstate); // Folia - region threading
            }
            blockstate.setFlag(flags); // Paper - update the flag also
            blockstate.setData(state);
            return true;
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        } else if (!this.isClientSide && this.isDebug()) {
            return false;
        } else {
            LevelChunk chunk = this.getChunkAt(pos);
            Block block = state.getBlock();

            // CraftBukkit start - capture blockstates
            boolean captured = false;
            if (worldData.captureBlockStates && !worldData.capturedBlockStates.containsKey(pos)) { // Folia - region threading
                CraftBlockState blockstate = (CraftBlockState) world.getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getState(); // Paper - use CB getState to get a suitable snapshot
                blockstate.setFlag(flags); // Paper - set flag
                worldData.capturedBlockStates.put(pos.immutable(), blockstate); // Folia - region threading
                captured = true;
            }
            // CraftBukkit end

            BlockState iblockdata1 = chunk.setBlockState(pos, state, (flags & 64) != 0, (flags & 1024) == 0); // CraftBukkit custom NO_PLACE flag
            this.chunkPacketBlockController.onBlockChange(this, pos, state, iblockdata1, flags, maxUpdateDepth); // Paper - Anti-Xray

            if (iblockdata1 == null) {
                // CraftBukkit start - remove blockstate if failed (or the same)
                if (worldData.captureBlockStates && captured) { // Folia - region threading
                    worldData.capturedBlockStates.remove(pos); // Folia - region threading
                }
                // CraftBukkit end
                return false;
            } else {
                BlockState iblockdata2 = this.getBlockState(pos);

                /*
                if (iblockdata2 == iblockdata) {
                    if (iblockdata1 != iblockdata2) {
                        this.setBlocksDirty(blockposition, iblockdata1, iblockdata2);
                    }

                    if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
                        this.sendBlockUpdated(blockposition, iblockdata1, iblockdata, i);
                    }

                    if ((i & 1) != 0) {
                        this.blockUpdated(blockposition, iblockdata1.getBlock());
                        if (!this.isClientSide && iblockdata.hasAnalogOutputSignal()) {
                            this.updateNeighbourForOutputSignal(blockposition, block);
                        }
                    }

                    if ((i & 16) == 0 && j > 0) {
                        int k = i & -34;

                        iblockdata1.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                        iblockdata.updateNeighbourShapes(this, blockposition, k, j - 1);
                        iblockdata.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                    }

                    this.onBlockStateChange(blockposition, iblockdata1, iblockdata2);
                }
                */

                // CraftBukkit start
                if (!worldData.captureBlockStates) { // Don't notify clients or update physics while capturing blockstates // Folia - region threading
                    // Modularize client and physic updates
                    // Spigot start
                    try {
                        this.notifyAndUpdatePhysics(pos, chunk, iblockdata1, state, iblockdata2, flags, maxUpdateDepth);
                    } catch (StackOverflowError ex) {
                        Level.lastPhysicsProblem = new BlockPos(pos);
                    }
                    // Spigot end
                }
                // CraftBukkit end

                return true;
            }
        }
    }

    // CraftBukkit start - Split off from above in order to directly send client and physic updates
    public void notifyAndUpdatePhysics(BlockPos blockposition, LevelChunk chunk, BlockState oldBlock, BlockState newBlock, BlockState actualBlock, int i, int j) {
        BlockState iblockdata = newBlock;
        BlockState iblockdata1 = oldBlock;
        BlockState iblockdata2 = actualBlock;
        if (iblockdata2 == iblockdata) {
            if (iblockdata1 != iblockdata2) {
                this.setBlocksDirty(blockposition, iblockdata1, iblockdata2);
            }

            if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk == null || (chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)))) { // allow chunk to be null here as chunk.isReady() is false when we send our notification during block placement
                this.sendBlockUpdated(blockposition, iblockdata1, iblockdata, i);
                // Paper start - per player view distance - allow block updates for non-ticking chunks in player view distance
                // if copied from above
            } else if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0)) { // Paper - replace old player chunk management
                ((ServerLevel)this).getChunkSource().blockChanged(blockposition);
                // Paper end - per player view distance
            }

            if ((i & 1) != 0) {
                this.blockUpdated(blockposition, iblockdata1.getBlock());
                if (!this.isClientSide && iblockdata.hasAnalogOutputSignal()) {
                    this.updateNeighbourForOutputSignal(blockposition, newBlock.getBlock());
                }
            }

            if ((i & 16) == 0 && j > 0) {
                int k = i & -34;

                // CraftBukkit start
                iblockdata1.updateIndirectNeighbourShapes(this, blockposition, k, j - 1); // Don't call an event for the old block to limit event spam
                CraftWorld world = ((ServerLevel) this).getWorld();
                boolean cancelledUpdates = false; // Paper
                if (world != null && ((ServerLevel)this).getCurrentWorldData().hasPhysicsEvent) { // Paper // Folia - region threading
                    BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), CraftBlockData.fromData(iblockdata));
                    this.getCraftServer().getPluginManager().callEvent(event);

                    cancelledUpdates = event.isCancelled(); // Paper
                }
                // CraftBukkit end
                if (!cancelledUpdates) { // Paper
                iblockdata.updateNeighbourShapes(this, blockposition, k, j - 1);
                iblockdata.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                } // Paper
            }

            // CraftBukkit start - SPIGOT-5710
            if (!this.getCurrentWorldData().preventPoiUpdated) { // Folia - region threading
                this.onBlockStateChange(blockposition, iblockdata1, iblockdata2);
            }
            // CraftBukkit end
        }
    }
    // CraftBukkit end

    public void onBlockStateChange(BlockPos pos, BlockState oldBlock, BlockState newBlock) {}

    @Override
    public boolean removeBlock(BlockPos pos, boolean move) {
        FluidState fluid = this.getFluidState(pos);

        return this.setBlock(pos, fluid.createLegacyBlock(), 3 | (move ? 64 : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth) {
        BlockState iblockdata = this.getBlockState(pos);

        if (iblockdata.isAir()) {
            return false;
        } else {
            FluidState fluid = this.getFluidState(pos);
            // Paper start - while the above setAir method is named same and looks very similar
            // they are NOT used with same intent and the above should not fire this event. The above method is more of a BlockSetToAirEvent,
            // it doesn't imply destruction of a block that plays a sound effect / drops an item.
            boolean playEffect = true;
            if (com.destroystokyo.paper.event.block.BlockDestroyEvent.getHandlerList().getRegisteredListeners().length > 0) {
                com.destroystokyo.paper.event.block.BlockDestroyEvent event = new com.destroystokyo.paper.event.block.BlockDestroyEvent(MCUtil.toBukkitBlock(this, pos), fluid.createLegacyBlock().createCraftBlockData(), drop);
                if (!event.callEvent()) {
                    return false;
                }
                playEffect = event.playEffect();
                drop = event.willDrop();
            }
            // Paper end

            if (playEffect && !(iblockdata.getBlock() instanceof BaseFireBlock)) { // Paper
                this.levelEvent(2001, pos, Block.getId(iblockdata));
            }

            if (drop) {
                BlockEntity tileentity = iblockdata.hasBlockEntity() ? this.getBlockEntity(pos) : null;

                Block.dropResources(iblockdata, this, pos, tileentity, breakingEntity, ItemStack.EMPTY);
            }

            boolean flag1 = this.setBlock(pos, fluid.createLegacyBlock(), 3, maxUpdateDepth);

            if (flag1) {
                this.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(breakingEntity, iblockdata));
            }

            return flag1;
        }
    }

    public void addDestroyBlockEffect(BlockPos pos, BlockState state) {}

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
        return this.setBlock(pos, state, 3);
    }

    public abstract void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags);

    public void setBlocksDirty(BlockPos pos, BlockState old, BlockState updated) {}

    public void updateNeighborsAt(BlockPos pos, Block sourceBlock) {}

    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block sourceBlock, Direction direction) {}

    public void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {}

    public void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {}

    @Override
    public void neighborShapeChanged(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        this.getCurrentWorldData().neighborUpdater.shapeUpdate(direction, neighborState, pos, neighborPos, flags, maxUpdateDepth); // Folia - region threading
    }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        int k;

        if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
            if (this.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                k = this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(heightmap, x & 15, z & 15) + 1;
            } else {
                k = this.getMinBuildHeight();
            }
        } else {
            k = this.getSeaLevel() + 1;
        }

        return k;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.getChunkSource().getLightEngine();
    }

    // Folia start - region threading
    @Nullable
    public BlockState getBlockStateFromEmptyChunkIfLoaded(BlockPos pos) {
        net.minecraft.server.level.ServerChunkCache chunkProvider = (net.minecraft.server.level.ServerChunkCache)this.getChunkSource();
        ChunkAccess chunk = chunkProvider.getChunkAtImmediately(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            return chunk.getBlockState(pos);
        }
        return null;
    }

    @Nullable
    public BlockState getBlockStateFromEmptyChunk(BlockPos pos) {
        net.minecraft.server.level.ServerChunkCache chunkProvider = (net.minecraft.server.level.ServerChunkCache)this.getChunkSource();
        ChunkAccess chunk = chunkProvider.getChunkAtImmediately(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            return chunk.getBlockState(pos);
        }
        chunk = chunkProvider.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.EMPTY, true);
        return chunk.getBlockState(pos);
    }
    // Folia end - region threading

    @Override
    public BlockState getBlockState(BlockPos pos) {
        // CraftBukkit start - tree generation
        if (this.getCurrentWorldData().captureTreeGeneration) { // Folia - region threading
            CraftBlockState previous = this.getCurrentWorldData().capturedBlockStates.get(pos); // Paper // Folia - region threading
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            ChunkAccess chunk = this.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true); // Paper - manually inline to reduce hops and avoid unnecessary null check to reduce total byte code size, this should never return null and if it does we will see it the next line but the real stack trace will matter in the chunk engine

            return chunk.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunk chunk = this.getChunkAt(pos);

            return chunk.getFluidState(pos);
        }
    }

    public boolean isDay() {
        return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
    }

    public boolean isNight() {
        return !this.dimensionType().hasFixedTime() && !this.isDay();
    }

    public void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
        Player entityhuman;

        if (except instanceof Player) {
            Player entityhuman1 = (Player) except;

            entityhuman = entityhuman1;
        } else {
            entityhuman = null;
        }

        this.playSound(entityhuman, pos, sound, category, volume, pitch);
    }

    @Override
    public void playSound(@Nullable Player except, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSound(except, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, sound, category, volume, pitch);
    }

    public abstract void playSeededSound(@Nullable Player except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed);

    public void playSeededSound(@Nullable Player except, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, long seed) {
        this.playSeededSound(except, x, y, z, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch, seed);
    }

    public abstract void playSeededSound(@Nullable Player except, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed);

    public void playSound(@Nullable Player except, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(except, x, y, z, sound, category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Player except, Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(except, entity, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playLocalSound(BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch, boolean useDistance) {
        this.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, sound, category, volume, pitch, useDistance);
    }

    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, boolean useDistance) {}

    @Override
    public void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public void addParticle(ParticleOptions parameters, boolean alwaysSpawn, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public void addAlwaysVisibleParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public void addAlwaysVisibleParticle(ParticleOptions parameters, boolean alwaysSpawn, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public float getSunAngle(float tickDelta) {
        float f1 = this.getTimeOfDay(tickDelta);

        return f1 * 6.2831855F;
    }

    public void addBlockEntityTicker(TickingBlockEntity ticker) {
        ((ServerLevel)this).getCurrentWorldData().addBlockEntityTicker(ticker); // Folia - regionised ticking
    }

    protected void tickBlockEntities() {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        gameprofilerfiller.push("blockEntities");
        this.timings.tileEntityPending.startTiming(); // Spigot
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - regionised ticking
        regionizedWorldData.seTtickingBlockEntities(true); // Folia - regionised ticking
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY_PENDING); try { // Folia - profiler
        regionizedWorldData.pushPendingTickingBlockEntities(); // Folia - regionised ticking
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY_PENDING); } // Folia - profiler
        List<TickingBlockEntity> blockEntityTickers = regionizedWorldData.getBlockEntityTickers(); // Folia - regionised ticking
        this.timings.tileEntityPending.stopTiming(); // Spigot

        this.timings.tileEntityTick.startTiming(); // Spigot
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY_TICK); try { // Folia - profiler
        // Spigot start
        // Iterator iterator = this.blockEntityTickers.iterator();
        int tilesThisCycle = 0;
        var toRemove = new it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet<TickingBlockEntity>(net.minecraft.Util.identityStrategy()); // Paper - use removeAll
        toRemove.add(null);
        for (int i = 0; i < blockEntityTickers.size(); i++) { // Paper - Disable tick limiters // Folia - regionised ticking
            TickingBlockEntity tickingblockentity = (TickingBlockEntity) blockEntityTickers.get(i); // Folia - regionised ticking
            // Spigot start
            if (tickingblockentity == null) {
                this.getCraftServer().getLogger().severe("Spigot has detected a null entity and has removed it, preventing a crash");
                tilesThisCycle--;
                continue;
            }
            // Spigot end

            if (tickingblockentity.isRemoved()) {
                // Spigot start
                tilesThisCycle--;
                toRemove.add(tickingblockentity); // Paper - use removeAll
                // Spigot end
            } else if (this.shouldTickBlocksAt(tickingblockentity.getPos())) {
                tickingblockentity.tick();
                // Paper start - execute chunk tasks during tick
                if ((i & 7) == 0) { // Folia - regionised ticking
                    MinecraftServer.getServer().executeMidTickTasks();
                }
                // Paper end - execute chunk tasks during tick
            }
        }
        blockEntityTickers.removeAll(toRemove); // Folia - regionised ticking
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY_TICK); } // Folia - profiler

        this.timings.tileEntityTick.stopTiming(); // Spigot
        regionizedWorldData.seTtickingBlockEntities(false); // Folia - regionised ticking
        //co.aikar.timings.TimingHistory.tileEntityTicks += this.blockEntityTickers.size(); // Paper // Folia - region threading
        gameprofilerfiller.pop();
        regionizedWorldData.currentPrimedTnt = 0; // Spigot // Folia - region threading
    }

    public <T extends Entity> void guardEntityTick(Consumer<T> tickConsumer, T entity) {
        try {
            tickConsumer.accept(entity);
            MinecraftServer.getServer().executeMidTickTasks(); // Paper - execute chunk tasks mid tick
        } catch (Throwable throwable) {
            if (throwable instanceof ThreadDeath) throw throwable; // Paper
            // Paper start - Prevent tile entity and entity crashes
            final String msg = String.format("Entity threw exception at %s:%s,%s,%s", entity.level().getWorld().getName(), entity.getX(), entity.getY(), entity.getZ());
            MinecraftServer.LOGGER.error(msg, throwable);
            getCraftServer().getPluginManager().callEvent(new ServerExceptionEvent(new ServerInternalException(msg, throwable)));
            if (!(entity instanceof net.minecraft.server.level.ServerPlayer)) entity.discard(); // Folia - properly disconnect players
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) player.connection.disconnect(net.minecraft.network.chat.Component.translatable("multiplayer.disconnect.generic"), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); // Folia - properly disconnect players
            // Paper end
        }
    }
    // Paper start - Prevent armor stands from doing entity lookups
    @Override
    public boolean noCollision(@Nullable Entity entity, AABB box) {
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand && !entity.level().paperConfig().entities.armorStands.doCollisionEntityLookups) return false;
        // Paper start - optimise collisions
        int flags = io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_ONLY;
        if (entity != null) {
            flags |= io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_BORDER;
        }
        if (io.papermc.paper.util.CollisionUtil.getCollisionsForBlocksOrWorldBorder(this, entity, box, null, null, flags, null)) {
            return false;
        }

        return !io.papermc.paper.util.CollisionUtil.getEntityHardCollisions(this, entity, box, null, flags, null);
        // Paper end - optimise collisions
    }
    // Paper end

    public boolean shouldTickDeath(Entity entity) {
        return true;
    }

    public boolean shouldTickBlocksAt(long chunkPos) {
        return true;
    }

    public boolean shouldTickBlocksAt(BlockPos pos) {
        return this.shouldTickBlocksAt(ChunkPos.asLong(pos));
    }

    public Explosion explode(@Nullable Entity entity, double x, double y, double z, float power, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, (DamageSource) null, (ExplosionDamageCalculator) null, x, y, z, power, false, explosionSourceType);
    }

    public Explosion explode(@Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, (DamageSource) null, (ExplosionDamageCalculator) null, x, y, z, power, createFire, explosionSourceType);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, Vec3 pos, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, damageSource, behavior, pos.x(), pos.y(), pos.z(), power, createFire, explosionSourceType);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, true);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType, boolean particles) {
        Explosion.BlockInteraction explosion_effect;

        switch (explosionSourceType) {
            case NONE:
                explosion_effect = Explosion.BlockInteraction.KEEP;
                break;
            case BLOCK:
                explosion_effect = this.getDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
                break;
            case MOB:
                explosion_effect = this.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? this.getDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY) : Explosion.BlockInteraction.KEEP;
                break;
            case TNT:
                explosion_effect = this.getDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
                break;
            default:
                throw new IncompatibleClassChangeError();
        }

        Explosion.BlockInteraction explosion_effect1 = explosion_effect;
        Explosion explosion = new Explosion(this, entity, damageSource, behavior, x, y, z, power, createFire, explosion_effect1);

        explosion.explode();
        explosion.finalizeExplosion(particles);
        return explosion;
    }

    private Explosion.BlockInteraction getDestroyType(GameRules.Key<GameRules.BooleanValue> gameRuleKey) {
        return this.getGameRules().getBoolean(gameRuleKey) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    public abstract String gatherChunkSourceStats();

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // CraftBukkit start
        return this.getBlockEntity(pos, true);
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos blockposition, boolean validate) {
        // Folia start - region threading
        if (!io.papermc.paper.util.TickThread.isTickThread()) {
            return null;
        }
        // Folia end - region threading
        // Paper start - Optimize capturedTileEntities lookup
        net.minecraft.world.level.block.entity.BlockEntity blockEntity;
        if (!this.getCurrentWorldData().capturedTileEntities.isEmpty() && (blockEntity = this.getCurrentWorldData().capturedTileEntities.get(blockposition)) != null) { // Folia - region threading
            return blockEntity;
        }
        // Paper end
        // CraftBukkit end
        return this.isOutsideBuildHeight(blockposition) ? null : (!this.isClientSide && !io.papermc.paper.util.TickThread.isTickThread() ? null : this.getChunkAt(blockposition).getBlockEntity(blockposition, LevelChunk.EntityCreationType.IMMEDIATE)); // Paper - rewrite chunk system
    }

    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockposition = blockEntity.getBlockPos();

        if (!this.isOutsideBuildHeight(blockposition)) {
            // CraftBukkit start
            if (this.getCurrentWorldData().captureBlockStates) { // Folia - region threading
                this.getCurrentWorldData().capturedTileEntities.put(blockposition.immutable(), blockEntity); // Folia - region threading
                return;
            }
            // CraftBukkit end
            this.getChunkAt(blockposition).addAndRegisterBlockEntity(blockEntity);
        }
    }

    public void removeBlockEntity(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            this.getChunkAt(pos).removeBlockEntity(pos);
        }
    }

    public boolean isLoaded(BlockPos pos) {
        return this.isOutsideBuildHeight(pos) ? false : this.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public boolean loadedAndEntityCanStandOnFace(BlockPos pos, Entity entity, Direction direction) {
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        } else {
            ChunkAccess ichunkaccess = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);

            return ichunkaccess == null ? false : ichunkaccess.getBlockState(pos).entityCanStandOnFace(this, pos, entity, direction);
        }
    }

    public boolean loadedAndEntityCanStandOn(BlockPos pos, Entity entity) {
        return this.loadedAndEntityCanStandOnFace(pos, entity, Direction.UP);
    }

    public void updateSkyBrightness() {
        double d0 = 1.0D - (double) (this.getRainLevel(1.0F) * 5.0F) / 16.0D;
        double d1 = 1.0D - (double) (this.getThunderLevel(1.0F) * 5.0F) / 16.0D;
        double d2 = 0.5D + 2.0D * Mth.clamp((double) Mth.cos(this.getTimeOfDay(1.0F) * 6.2831855F), -0.25D, 0.25D);

        this.skyDarken = (int) ((1.0D - d2 * d0 * d1) * 11.0D);
    }

    public void setSpawnSettings(boolean spawnMonsters, boolean spawnAnimals) {
        this.getChunkSource().setSpawnSettings(spawnMonsters, spawnAnimals);
    }

    public BlockPos getSharedSpawnPos() {
        BlockPos blockposition = new BlockPos(this.levelData.getXSpawn(), this.levelData.getYSpawn(), this.levelData.getZSpawn());

        if (!this.getWorldBorder().isWithinBounds(blockposition)) {
            blockposition = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(this.getWorldBorder().getCenterX(), 0.0D, this.getWorldBorder().getCenterZ()));
        }

        return blockposition;
    }

    public float getSharedSpawnAngle() {
        return this.levelData.getSpawnAngle();
    }

    protected void prepareWeather() {
        if (this.levelData.isRaining()) {
            this.rainLevel = 1.0F;
            if (this.levelData.isThundering()) {
                this.thunderLevel = 1.0F;
            }
        }

    }

    public void close() throws IOException {
        this.getChunkSource().close();
    }

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate) {
        io.papermc.paper.util.TickThread.ensureTickThread((ServerLevel)this, box, "Cannot getEntities asynchronously"); // Folia - region threading
        this.getProfiler().incrementCounter("getEntities");
        List<Entity> list = Lists.newArrayList();
        ((ServerLevel)this).getEntityLookup().getEntities(except, box, list, predicate); // Paper - optimise this call
        return list;
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();

        this.getEntities(filter, box, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate, List<? super T> result) {
        this.getEntities(filter, box, predicate, result, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate, List<? super T> result, int limit) {
        io.papermc.paper.util.TickThread.ensureTickThread((ServerLevel)this, box, "Cannot getEntities asynchronously"); // Folia - region threading
        this.getProfiler().incrementCounter("getEntities");
        // Paper start - optimise this call
        //TODO use limit
        if (filter instanceof net.minecraft.world.entity.EntityType entityTypeTest) {
            ((ServerLevel) this).getEntityLookup().getEntities(entityTypeTest, box, result, predicate);
        } else {
            Predicate<? super T> test = (obj) -> {
                return filter.tryCast(obj) != null;
            };
            predicate = predicate == null ? test : test.and((Predicate) predicate);
            Class base;
            if (filter == null || (base = filter.getBaseClass()) == null || base == Entity.class) {
                ((ServerLevel) this).getEntityLookup().getEntities((Entity) null, box, (List) result, (Predicate)predicate);
            } else {
                ((ServerLevel) this).getEntityLookup().getEntities(base, null, box, (List) result, (Predicate)predicate); // Paper - optimise this call
            }
        }
        // Paper end - optimise this call
    }

    @Nullable
    public abstract Entity getEntity(int id);

    public void blockEntityChanged(BlockPos pos) {
        if (this.hasChunkAt(pos)) {
            this.getChunkAt(pos).setUnsaved(true);
        }

    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    public void disconnect() {}

    @Override // Folia - region threading
    public long getGameTime() {
        // Dumb world gen thread calls this for some reason. So, check for null.
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.getCurrentWorldData();
        return worldData == null ? this.getLevelData().getGameTime() : worldData.getTickData().nonRedstoneGameTime();
    }

    public long getDayTime() {
        // Dumb world gen thread calls this for some reason. So, check for null.
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.getCurrentWorldData();
        return worldData == null ? this.getLevelData().getDayTime() : worldData.getTickData().dayTime();
    }

    // Folia start - region threading
    @Override
    public long dayTime() {
        return this.getDayTime();
    }

    @Override
    public long getRedstoneGameTime() {
        return this.getCurrentWorldData().getRedstoneGameTime();
    }
    // Folia end - region threading

    public boolean mayInteract(Player player, BlockPos pos) {
        return true;
    }

    public void broadcastEntityEvent(Entity entity, byte status) {}

    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {}

    public void blockEvent(BlockPos pos, Block block, int type, int data) {
        this.getBlockState(pos).triggerEvent(this, pos, type, data);
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    public GameRules getGameRules() {
        return this.levelData.getGameRules();
    }

    public float getThunderLevel(float delta) {
        return Mth.lerp(delta, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(delta);
    }

    public void setThunderLevel(float thunderGradient) {
        float f1 = Mth.clamp(thunderGradient, 0.0F, 1.0F);

        this.oThunderLevel = f1;
        this.thunderLevel = f1;
    }

    public float getRainLevel(float delta) {
        return Mth.lerp(delta, this.oRainLevel, this.rainLevel);
    }

    public void setRainLevel(float rainGradient) {
        float f1 = Mth.clamp(rainGradient, 0.0F, 1.0F);

        this.oRainLevel = f1;
        this.rainLevel = f1;
    }

    public boolean isThundering() {
        return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling() ? (double) this.getThunderLevel(1.0F) > 0.9D : false;
    }

    public boolean isRaining() {
        return (double) this.getRainLevel(1.0F) > 0.2D;
    }

    public boolean isRainingAt(BlockPos pos) {
        if (!this.isRaining()) {
            return false;
        } else if (!this.canSeeSky(pos)) {
            return false;
        } else if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            return false;
        } else {
            Biome biomebase = (Biome) this.getBiome(pos).value();

            return biomebase.getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
        }
    }

    @Nullable
    public abstract MapItemSavedData getMapData(String id);

    public abstract void setMapData(String id, MapItemSavedData state);

    public abstract int getFreeMapId();

    public void globalLevelEvent(int eventId, BlockPos pos, int data) {}

    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashreportsystemdetails = report.addCategory("Affected level", 1);

        crashreportsystemdetails.setDetail("All players", () -> {
            int i = this.players().size();

            return i + " total; " + this.players();
        });
        ChunkSource ichunkprovider = this.getChunkSource();

        Objects.requireNonNull(ichunkprovider);
        crashreportsystemdetails.setDetail("Chunk stats", ichunkprovider::gatherStats);
        crashreportsystemdetails.setDetail("Level dimension", () -> {
            return this.dimension().location().toString();
        });

        try {
            this.levelData.fillCrashReportCategory(crashreportsystemdetails, this);
        } catch (Throwable throwable) {
            crashreportsystemdetails.setDetailError("Level Data Unobtainable", throwable);
        }

        return crashreportsystemdetails;
    }

    public abstract void destroyBlockProgress(int entityId, BlockPos pos, int progress);

    public void createFireworks(double x, double y, double z, double velocityX, double velocityY, double velocityZ, @Nullable CompoundTag nbt) {}

    public abstract Scoreboard getScoreboard();

    public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection);

            if (io.papermc.paper.util.TickThread.isTickThreadFor((ServerLevel)this, blockposition1) && this.hasChunkAt(blockposition1)) { // Folia - block updates in unloaded chunks
                BlockState iblockdata = this.getBlockState(blockposition1);

                if (iblockdata.is(Blocks.COMPARATOR)) {
                    this.neighborChanged(iblockdata, blockposition1, block, pos, false);
                } else if (iblockdata.isRedstoneConductor(this, blockposition1)) {
                    blockposition1 = blockposition1.relative(enumdirection);
                    iblockdata = this.getBlockState(blockposition1);
                    if (iblockdata.is(Blocks.COMPARATOR)) {
                        this.neighborChanged(iblockdata, blockposition1, block, pos, false);
                    }
                }
            }
        }

    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        long i = 0L;
        float f = 0.0F;

        if (this.hasChunkAt(pos)) {
            f = this.getMoonBrightness();
            i = this.getChunkAt(pos).getInhabitedTime();
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), i, f);
    }

    @Override
    public int getSkyDarken() {
        return this.skyDarken;
    }

    public void setSkyFlashTime(int lightningTicksLeft) {}

    @Override
    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    public void sendPacketToServer(Packet<?> packet) {
        throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
    }

    @Override
    public DimensionType dimensionType() {
        return (DimensionType) this.dimensionTypeRegistration.value();
    }

    public ResourceKey<DimensionType> dimensionTypeId() {
        return this.dimensionTypeId;
    }

    public Holder<DimensionType> dimensionTypeRegistration() {
        return this.dimensionTypeRegistration;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) {
        return state.test(this.getFluidState(pos));
    }

    public abstract RecipeManager getRecipeManager();

    public BlockPos getBlockRandomPos(int x, int y, int z, int l) {
        // Paper start - allow use of mutable pos
        BlockPos.MutableBlockPos ret = new BlockPos.MutableBlockPos();
        this.getRandomBlockPosition(x, y, z, l, ret);
        return ret.immutable();
    }
    public final BlockPos.MutableBlockPos getRandomBlockPosition(int x, int y, int z, int l, BlockPos.MutableBlockPos out) {
        // Paper end
        int i1 = this.random.nextInt() >> 2; // Folia - region threading

        out.set(x + (i1 & 15), y + (i1 >> 16 & l), z + (i1 >> 8 & 15)); // Paper - change to setValues call
        return out; // Paper
    }

    public boolean noSave() {
        return false;
    }

    public ProfilerFiller getProfiler() {
        return (ProfilerFiller) this.profiler.get();
    }

    public Supplier<ProfilerFiller> getProfilerSupplier() {
        return this.profiler;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    public final boolean isDebug() {
        return this.isDebug;
    }

    public abstract LevelEntityGetter<Entity> getEntities();

    @Override
    public long nextSubTickCount() {
        return this.subTickCount.getAndIncrement(); // Folia - region threading
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public DamageSources damageSources() {
        return this.damageSources;
    }

    public static enum ExplosionInteraction {

        NONE, BLOCK, MOB, TNT;

        private ExplosionInteraction() {}
    }
    // Paper start
    //protected final io.papermc.paper.world.EntitySliceManager entitySliceManager; // Paper - rewrite chunk system

    public org.bukkit.entity.Entity[] getChunkEntities(int chunkX, int chunkZ) {
        io.papermc.paper.world.ChunkEntitySlices slices = ((ServerLevel)this).getEntityLookup().getChunk(chunkX, chunkZ);
        if (slices == null) {
            return new org.bukkit.entity.Entity[0];
        }
        return slices.getChunkEntities();
    }

    @Override
    public List<Entity> getHardCollidingEntities(Entity except, AABB box, Predicate<? super Entity> predicate) {
        List<Entity> ret = new java.util.ArrayList<>();
        ((ServerLevel)this).getEntityLookup().getHardCollidingEntities(except, box, ret, predicate);
        return ret;
    }

    @Override
    public void getEntities(Entity except, AABB box, Predicate<? super Entity> predicate, List<Entity> into) {
        ((ServerLevel)this).getEntityLookup().getEntities(except, box, into, predicate);
    }

    @Override
    public void getHardCollidingEntities(Entity except, AABB box, Predicate<? super Entity> predicate, List<Entity> into) {
        ((ServerLevel)this).getEntityLookup().getHardCollidingEntities(except, box, into, predicate);
    }

    @Override
    public <T> void getEntitiesByClass(Class<? extends T> clazz, Entity except, final AABB box, List<? super T> into,
                                       Predicate<? super T> predicate) {
        ((ServerLevel)this).getEntityLookup().getEntities((Class)clazz, except, box, (List)into, (Predicate)predicate);
    }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB box, Predicate<? super T> predicate) {
        List<T> ret = new java.util.ArrayList<>();
        ((ServerLevel)this).getEntityLookup().getEntities(entityClass, null, box, ret, predicate);
        return ret;
    }
    // Paper end
    // Paper start - optimize redstone (Alternate Current)
    public alternate.current.wire.WireHandler getWireHandler() {
        // This method is overridden in ServerLevel.
        // Since Paper is a server platform there is no risk
        // of this implementation being called. It is here
        // only so this method can be called without casting
        // an instance of Level to ServerLevel.
        return null;
    }
    // Paper end - optimize redstone (Alternate Current)
    // Paper start - notify observers even if grow failed
    public void checkCapturedTreeStateForObserverNotify(final BlockPos pos, final CraftBlockState craftBlockState) {
        // notify observers if the block state is the same and the Y level equals the original y level (for mega trees)
        // blocks at the same Y level with the same state can be assumed to be saplings which trigger observers regardless of if the
        // tree grew or not
        if (craftBlockState.getPosition().getY() == pos.getY() && this.getBlockState(craftBlockState.getPosition()) == craftBlockState.getHandle()) {
            this.notifyAndUpdatePhysics(craftBlockState.getPosition(), null, craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getFlag(), 512);
        }
    }
    // Paper end - notify observers even if grow failed
}
