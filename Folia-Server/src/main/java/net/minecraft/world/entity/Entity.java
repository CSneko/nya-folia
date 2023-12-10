package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import io.papermc.paper.util.MCUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
// CraftBukkit end

public abstract class Entity implements Nameable, EntityAccess, CommandSource {

    // CraftBukkit start
    private static final int CURRENT_LEVEL = 2;
    public boolean preserveMotion = true; // Paper - keep initial motion on first setPositionRotation
    static boolean isLevelAtLeast(CompoundTag tag, int level) {
        return tag.contains("Bukkit.updateLevel") && tag.getInt("Bukkit.updateLevel") >= level;
    }

    // Paper start
    public static RandomSource SHARED_RANDOM = new RandomRandomSource();
    public static final class RandomRandomSource extends java.util.Random implements net.minecraft.world.level.levelgen.BitRandomSource { // Folia - region threading
        private boolean locked = false;

        @Override
        public synchronized void setSeed(long seed) {
            if (locked) {
                LOGGER.error("Ignoring setSeed on Entity.SHARED_RANDOM", new Throwable());
            } else {
                super.setSeed(seed);
                locked = true;
            }
        }

        @Override
        public RandomSource fork() {
            return new net.minecraft.world.level.levelgen.LegacyRandomSource(this.nextLong());
        }

        @Override
        public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            return new net.minecraft.world.level.levelgen.LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
        }

        // these below are added to fix reobf issues that I don't wanna deal with right now
        @Override
        public int next(int bits) {
            return super.next(bits);
        }

        @Override
        public int nextInt(int origin, int bound) {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextInt(origin, bound);
        }

        @Override
        public long nextLong() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextLong();
        }

        @Override
        public int nextInt() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextInt(bound);
        }

        @Override
        public boolean nextBoolean() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextFloat();
        }

        @Override
        public double nextDouble() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextDouble();
        }

        @Override
        public double nextGaussian() {
            return super.nextGaussian();
        }
    }
    // Paper end
    public org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason; // Paper

    public com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData; // Paper
    public boolean collisionLoadChunks = false; // Paper
    private volatile CraftEntity bukkitEntity; // Folia - region threading

    public @org.jetbrains.annotations.Nullable net.minecraft.server.level.ChunkMap.TrackedEntity tracker; // Paper
    public @Nullable Throwable addedToWorldStack; // Paper - entity debug
    public CraftEntity getBukkitEntity() {
        if (this.bukkitEntity == null) {
            // Paper start - Folia schedulers
            synchronized (this) {
                if (this.bukkitEntity == null) {
                    return this.bukkitEntity = CraftEntity.getEntity(this.level.getCraftServer(), this);
                }
            }
            // Paper end - Folia schedulers
        }
        return this.bukkitEntity;
    }

    // Paper start
    public CraftEntity getBukkitEntityRaw() {
        return this.bukkitEntity;
    }
    // Paper end

    @Override
    public CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return this.getBukkitEntity();
    }

    // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    public int getDefaultMaxAirSupply() {
        return Entity.TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID_TAG = "id";
    public static final String PASSENGERS_TAG = "Passengers";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    private static final List<ItemStack> EMPTY_LIST = Collections.emptyList();
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001D;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999D;
    public static final float BREATHING_DISTANCE_BELOW_EYES = 0.11111111F;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    private static final AABB INITIAL_AABB = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static final double WATER_FLOW_SCALE = 0.014D;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007D;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335D;
    public static final String UUID_TAG = "UUID";
    private static double viewScale = 1.0D;
    private final EntityType<?> type;
    private int id;
    public boolean blocksBuilding;
    public ImmutableList<Entity> passengers;
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    private Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    private BlockPos blockPosition;
    private ChunkPos chunkPosition;
    private Vec3 deltaMovement;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier;
    @Nullable
    private Entity.RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float walkDistO;
    public float walkDist;
    public float moveDist;
    public float flyDist;
    public float fallDistance;
    private float nextStep;
    public double xOld;
    public double yOld;
    public double zOld;
    private float maxUpStep;
    public boolean noPhysics;
    public final RandomSource random;
    public int tickCount;
    private int remainingFireTicks;
    public boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    protected boolean wasEyeInWater;
    private final Set<TagKey<Fluid>> fluidOnEyes;
    public int invulnerableTime;
    protected boolean firstTick;
    protected final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    private static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<net.minecraft.world.entity.Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
    private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private EntityInLevelCallback levelCallback;
    private final VecDeltaCodec packetPositionCodec;
    public boolean noCulling;
    public boolean hasImpulse;
    public int portalCooldown;
    public boolean isInsidePortal;
    protected int portalTime;
    protected BlockPos portalEntrancePos;
    private boolean invulnerable;
    protected UUID uuid;
    protected String stringUUID;
    private boolean hasGlowingTag;
    private final Set<String> tags;
    private final double[] pistonDeltas;
    private long pistonDeltasGameTime;
    private EntityDimensions dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public boolean wasOnFire;
    public Optional<BlockPos> mainSupportingBlockPos;
    private boolean onGroundNoBlocks;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    public boolean hasVisualFire;
    @Nullable
    private BlockState feetBlockState;
    // CraftBukkit start
    public boolean persist = true;
    public boolean visibleByDefault = true;
    public boolean valid;
    public boolean generation;
    public int maxAirTicks = this.getDefaultMaxAirSupply(); // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Nullable // Paper
    public org.bukkit.projectiles.ProjectileSource projectileSource; // For projectiles only
    public boolean lastDamageCancelled; // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Keep track if the event was canceled
    public boolean persistentInvisibility = false;
    public BlockPos lastLavaContact;
    // Spigot start
    public final org.spigotmc.ActivationRange.ActivationType activationType = org.spigotmc.ActivationRange.initializeEntityActivationType(this);
    public final boolean defaultActivationState;
    public long activatedTick = Integer.MIN_VALUE;
    public void inactiveTick() { }
    // Spigot end
    // Paper start
    public long activatedImmunityTick = Integer.MIN_VALUE; // Paper
    public boolean isTemporarilyActive = false; // Paper
    public boolean fromNetherPortal; // Paper
    protected int numCollisions = 0; // Paper
    public boolean spawnedViaMobSpawner; // Paper - Yes this name is similar to above, upstream took the better one
    @javax.annotation.Nullable
    private org.bukkit.util.Vector origin;
    @javax.annotation.Nullable
    private UUID originWorld;
    public boolean freezeLocked = false; // Paper - Freeze Tick Lock API
    public boolean collidingWithWorldBorder; // Paper
    public boolean fixedPose = false; // Paper

    public void setOrigin(@javax.annotation.Nonnull Location location) {
        this.origin = location.toVector();
        this.originWorld = location.getWorld().getUID();
    }

    @javax.annotation.Nullable
    public org.bukkit.util.Vector getOriginVector() {
        return this.origin != null ? this.origin.clone() : null;
    }

    @javax.annotation.Nullable
    public UUID getOriginWorld() {
        return this.originWorld;
    }
    // Paper end
    public float getBukkitYaw() {
        return this.yRot;
    }

    public boolean isChunkLoaded() {
        return this.level.hasChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4);
    }
    // CraftBukkit end
    // Paper start
    public final AABB getBoundingBoxAt(double x, double y, double z) {
        return this.dimensions.makeBoundingBox(x, y, z);
    }
    // Paper end

    // Paper start
    /**
     * Overriding this field will cause memory leaks.
     */
    private final boolean hardCollides;

    private static final java.util.Map<Class<? extends Entity>, Boolean> cachedOverrides = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    {
        /* // Goodbye, broken on reobf...
        Boolean hardCollides = cachedOverrides.get(this.getClass());
        if (hardCollides == null) {
            try {
                java.lang.reflect.Method getHardCollisionBoxEntityMethod = Entity.class.getMethod("canCollideWith", Entity.class);
                java.lang.reflect.Method hasHardCollisionBoxMethod = Entity.class.getMethod("canBeCollidedWith");
                if (!this.getClass().getMethod(hasHardCollisionBoxMethod.getName(), hasHardCollisionBoxMethod.getParameterTypes()).equals(hasHardCollisionBoxMethod)
                        || !this.getClass().getMethod(getHardCollisionBoxEntityMethod.getName(), getHardCollisionBoxEntityMethod.getParameterTypes()).equals(getHardCollisionBoxEntityMethod)) {
                    hardCollides = Boolean.TRUE;
                } else {
                    hardCollides = Boolean.FALSE;
                }
                cachedOverrides.put(this.getClass(), hardCollides);
            }
            catch (ThreadDeath thr) { throw thr; }
            catch (Throwable thr) {
                // shouldn't happen, just explode
                throw new RuntimeException(thr);
            }
        } */
        this.hardCollides = this instanceof Boat
            || this instanceof net.minecraft.world.entity.monster.Shulker
            || this instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
            || this.shouldHardCollide();
    }

    // plugins can override
    protected boolean shouldHardCollide() {
        return false;
    }

    public final boolean hardCollides() {
        return this.hardCollides;
    }

    public net.minecraft.server.level.FullChunkStatus chunkStatus;

    public int sectionX = Integer.MIN_VALUE;
    public int sectionY = Integer.MIN_VALUE;
    public int sectionZ = Integer.MIN_VALUE;

    public boolean updatingSectionStatus = false;
    // Paper end
    // Paper start - optimise entity tracking
    final org.spigotmc.TrackingRange.TrackingRangeType trackingRangeType = org.spigotmc.TrackingRange.getTrackingRangeType(this);
    // Paper start - make end portalling safe
    public BlockPos portalBlock;
    public ServerLevel portalWorld;
    public void tickEndPortal() {
        BlockPos pos = this.portalBlock;
        ServerLevel world = this.portalWorld;
        this.portalBlock = null;
        this.portalWorld = null;

        if (pos == null || world == null || world != this.level) {
            return;
        }

        if (this.isPassenger() || this.isVehicle() || !this.canChangeDimensions() || this.isRemoved() || !this.valid || !this.isAlive()) {
            return;
        }

        ResourceKey<Level> resourcekey = world.getTypeKey() == LevelStem.END ? Level.OVERWORLD : Level.END; // CraftBukkit - SPIGOT-6152: send back to main overworld in custom ends
        ServerLevel worldserver = world.getServer().getLevel(resourcekey);

        org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(this.getBukkitEntity(), new org.bukkit.Location(world.getWorld(), pos.getX(), pos.getY(), pos.getZ()));
        event.callEvent();

        if (this instanceof ServerPlayer) {
            ((ServerPlayer)this).changeDimension(worldserver, PlayerTeleportEvent.TeleportCause.END_PORTAL);
            return;
        }
        this.teleportTo(worldserver, null);
    }
    // Paper end - make end portalling safe
    // Folia start
    private static final java.util.concurrent.ConcurrentHashMap<Class<? extends Entity>, Integer> CLASS_ID_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicInteger CLASS_ID_GENERATOR = new AtomicInteger();
    public final int classId = CLASS_ID_MAP.computeIfAbsent(this.getClass(), (Class<? extends Entity> c) -> {
        return CLASS_ID_GENERATOR.getAndIncrement();
    });
    private static final java.util.concurrent.atomic.AtomicLong REFERENCE_ID_GENERATOR = new java.util.concurrent.atomic.AtomicLong();
    public final long referenceId = REFERENCE_ID_GENERATOR.getAndIncrement();
    // Folia end
    // Folia start - region ticking
    public void updateTicks(long fromTickOffset, long fromRedstoneTimeOffset) {
        if (this.activatedTick != Integer.MIN_VALUE) {
            this.activatedTick += fromTickOffset;
        }
        if (this.activatedImmunityTick != Integer.MIN_VALUE) {
            this.activatedImmunityTick += fromTickOffset;
        }
    }
    // Folia end - region ticking

    public boolean isLegacyTrackingEntity = false;

    public final void setLegacyTrackingEntity(final boolean isLegacyTrackingEntity) {
        this.isLegacyTrackingEntity = isLegacyTrackingEntity;
    }

    // Folia - region ticking
    // Paper end - optimise entity tracking

    public Entity(EntityType<?> type, Level world) {
        this.id = Entity.ENTITY_COUNTER.incrementAndGet();
        this.passengers = ImmutableList.of();
        this.deltaMovement = Vec3.ZERO;
        this.bb = Entity.INITIAL_AABB;
        this.stuckSpeedMultiplier = Vec3.ZERO;
        this.nextStep = 1.0F;
        this.random = SHARED_RANDOM; // Paper
        this.remainingFireTicks = -this.getFireImmuneTicks();
        this.fluidHeight = new Object2DoubleArrayMap(2);
        this.fluidOnEyes = new HashSet();
        this.firstTick = true;
        this.levelCallback = EntityInLevelCallback.NULL;
        this.packetPositionCodec = new VecDeltaCodec();
        this.uuid = Mth.createInsecureUUID(this.random);
        this.stringUUID = this.uuid.toString();
        this.tags = Sets.newHashSet();
        this.pistonDeltas = new double[]{0.0D, 0.0D, 0.0D};
        this.mainSupportingBlockPos = Optional.empty();
        this.onGroundNoBlocks = false;
        this.feetBlockState = null;
        this.type = type;
        this.level = world;
        this.dimensions = type.getDimensions();
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.chunkPosition = ChunkPos.ZERO;
        // Spigot start
        if (world != null) {
            this.defaultActivationState = org.spigotmc.ActivationRange.initializeEntityActivationState(this, world.spigotConfig);
        } else {
            this.defaultActivationState = false;
        }
        // Spigot end
        this.entityData = new SynchedEntityData(this);
        this.entityData.define(Entity.DATA_SHARED_FLAGS_ID, (byte) 0);
        this.entityData.define(Entity.DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        this.entityData.define(Entity.DATA_CUSTOM_NAME_VISIBLE, false);
        this.entityData.define(Entity.DATA_CUSTOM_NAME, Optional.empty());
        this.entityData.define(Entity.DATA_SILENT, false);
        this.entityData.define(Entity.DATA_NO_GRAVITY, false);
        this.entityData.define(Entity.DATA_POSE, net.minecraft.world.entity.Pose.STANDING);
        this.entityData.define(Entity.DATA_TICKS_FROZEN, 0);
        this.defineSynchedData();
        this.getEntityData().registrationLocked = true; // Spigot
        this.setPos(0.0D, 0.0D, 0.0D);
        this.eyeHeight = this.getEyeHeight(net.minecraft.world.entity.Pose.STANDING, this.dimensions);
    }

    public boolean isColliding(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getCollisionShape(this.level(), pos, CollisionContext.of(this));
        VoxelShape voxelshape1 = voxelshape.move((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());

        return Shapes.joinIsNotEmpty(voxelshape1, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
    }

    public int getTeamColor() {
        Team scoreboardteambase = this.getTeam();

        return scoreboardteambase != null && scoreboardteambase.getColor().getColor() != null ? scoreboardteambase.getColor().getColor() : 16777215;
    }

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

    }

    public void syncPacketPositionCodec(double x, double y, double z) {
        this.packetPositionCodec.setBase(new Vec3(x, y, z));
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String tag) {
        return this.tags.size() >= 1024 ? false : this.tags.add(tag);
    }

    public boolean removeTag(String tag) {
        return this.tags.remove(tag);
    }

    public void kill() {
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        this.remove(Entity.RemovalReason.DISCARDED);
    }

    protected abstract void defineSynchedData();

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    public boolean equals(Object object) {
        return object instanceof Entity ? ((Entity) object).id == this.id : false;
    }

    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason reason) {
        this.setRemoved(reason);
    }

    public void onClientRemoval() {}

    public void setPose(net.minecraft.world.entity.Pose pose) {
        if (this.fixedPose) return; // Paper
        // CraftBukkit start
        if (pose == this.getPose()) {
            return;
        }
        this.level.getCraftServer().getPluginManager().callEvent(new EntityPoseChangeEvent(this.getBukkitEntity(), Pose.values()[pose.ordinal()]));
        // CraftBukkit end
        this.entityData.set(Entity.DATA_POSE, pose);
    }

    public net.minecraft.world.entity.Pose getPose() {
        return (net.minecraft.world.entity.Pose) this.entityData.get(Entity.DATA_POSE);
    }

    public boolean hasPose(net.minecraft.world.entity.Pose pose) {
        return this.getPose() == pose;
    }

    public boolean closerThan(Entity entity, double radius) {
        return this.position().closerThan(entity.position(), radius);
    }

    public boolean closerThan(Entity entity, double horizontalRadius, double verticalRadius) {
        double d2 = entity.getX() - this.getX();
        double d3 = entity.getY() - this.getY();
        double d4 = entity.getZ() - this.getZ();

        return Mth.lengthSquared(d2, d4) < Mth.square(horizontalRadius) && Mth.square(d3) < Mth.square(verticalRadius);
    }

    public void setRot(float yaw, float pitch) {
        // CraftBukkit start - yaw was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(yaw)) {
            yaw = 0;
        }

        if (yaw == Float.POSITIVE_INFINITY || yaw == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid yaw");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite yaw (Hacking?)");
            }
            yaw = 0;
        }

        // pitch was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(pitch)) {
            pitch = 0;
        }

        if (pitch == Float.POSITIVE_INFINITY || pitch == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid pitch");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite pitch (Hacking?)");
            }
            pitch = 0;
        }
        // CraftBukkit end

        this.setYRot(yaw % 360.0F);
        this.setXRot(pitch % 360.0F);
    }

    public final void setPos(Vec3 pos) {
        this.setPos(pos.x(), pos.y(), pos.z());
    }

    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z, true); // Paper - force bounding box update
        // this.setBoundingBox(this.makeBoundingBox()); // Paper - move into setPositionRaw
    }

    protected AABB makeBoundingBox() {
        return this.dimensions.makeBoundingBox(this.position);
    }

    protected void reapplyPosition() {
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void turn(double cursorDeltaX, double cursorDeltaY) {
        float f = (float) cursorDeltaY * 0.15F;
        float f1 = (float) cursorDeltaX * 0.15F;

        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + f1);
        this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += f1;
        this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }

    }

    public void tick() {
        this.baseTick();
    }

    // CraftBukkit start
    public void postTick() {
        // Folia start - region threading
        // moved to doPortalLogic
        if (true) {
            return;
        }
        // Folia end - region threading
        // No clean way to break out of ticking once the entity has been copied to a new world, so instead we move the portalling later in the tick cycle
        if (!(this instanceof ServerPlayer) && this.isAlive()) { // Paper - don't attempt to teleport dead entities
            this.handleNetherPortal();
        }
    }
    // CraftBukkit end

    public void baseTick() {
        this.level().getProfiler().push("entityBaseTick");
        if (firstTick && this instanceof net.minecraft.world.entity.NeutralMob neutralMob) neutralMob.tickInitialPersistentAnger(level); // Paper - Update last hurt when ticking
        this.feetBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            --this.boardingCooldown;
        }

        this.walkDistO = this.walkDist;
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        //if (this instanceof ServerPlayer) this.handleNetherPortal(); // CraftBukkit - // Moved up to postTick // Folia - region threading - ONLY allow in postTick()
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        if (this.level().isClientSide) {
            this.clearFire();
        } else if (this.remainingFireTicks > 0) {
            if (this.fireImmune()) {
                this.setRemainingFireTicks(this.remainingFireTicks - 4);
                if (this.remainingFireTicks < 0) {
                    this.clearFire();
                }
            } else {
                if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                    this.hurt(this.damageSources().onFire(), 1.0F);
                }

                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            }

            if (this.getTicksFrozen() > 0 && !freezeLocked) { // Paper - Freeze Tick Lock API
                this.setTicksFrozen(0);
                this.level().levelEvent((Player) null, 1009, this.blockPosition, 1);
            }
        }

        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
            // CraftBukkit start
        } else {
            this.lastLavaContact = null;
            // CraftBukkit end
        }

        this.checkBelowWorld();
        if (!this.level().isClientSide) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        this.level().getProfiler().pop();
    }

    public void setSharedFlagOnFire(boolean onFire) {
        this.setSharedFlag(0, onFire || this.hasVisualFire);
    }

    public void checkBelowWorld() {
        // Paper start - Configurable nether ceiling damage
        if (this.getY() < (double) (this.level.getMinBuildHeight() - 64) || (this.level.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER
            && this.level.paperConfig().environment.netherCeilingVoidDamageHeight.test(v -> this.getY() >= v)
            && (!(this instanceof Player player) || !player.getAbilities().invulnerable))) {
            // Paper end
            this.onBelowWorld();
        }

    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            --this.portalCooldown;
        }

    }

    public int getPortalWaitTime() {
        return 0;
    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            // CraftBukkit start - Fallen in lava TODO: this event spams!
            if (this instanceof net.minecraft.world.entity.LivingEntity && this.remainingFireTicks <= 0) {
                // not on fire yet
                org.bukkit.block.Block damager = (this.lastLavaContact == null) ? null : org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.lastLavaContact);
                org.bukkit.entity.Entity damagee = this.getBukkitEntity();
                EntityCombustEvent combustEvent = new org.bukkit.event.entity.EntityCombustByBlockEvent(damager, damagee, 15);
                this.level.getCraftServer().getPluginManager().callEvent(combustEvent);

                if (!combustEvent.isCancelled()) {
                    this.setSecondsOnFire(combustEvent.getDuration(), false);
                }
            } else {
                // This will be called every single tick the entity is in lava, so don't throw an event
                this.setSecondsOnFire(15, false);
            }
            CraftEventFactory.blockDamageRT.set((this.lastLavaContact) == null ? null : org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.lastLavaContact)); // Folia - region threading
            if (this.hurt(this.damageSources().lava(), 4.0F)) {
                this.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
            }
            CraftEventFactory.blockDamageRT.set(null); // Folia - region threading
            // CraftBukkit end - we also don't throw an event unless the object in lava is living, to save on some event calls

        }
    }

    public void setSecondsOnFire(int seconds) {
        // CraftBukkit start
        this.setSecondsOnFire(seconds, true);
    }

    public void setSecondsOnFire(int i, boolean callEvent) {
        if (callEvent) {
            EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity(), i);
            this.level.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            i = event.getDuration();
        }
        // CraftBukkit end
        int j = i * 20;

        if (this instanceof net.minecraft.world.entity.LivingEntity) {
            j = ProtectionEnchantment.getFireAfterDampener((net.minecraft.world.entity.LivingEntity) this, j);
        }

        if (this.remainingFireTicks < j) {
            this.setRemainingFireTicks(j);
        }

    }

    public void setRemainingFireTicks(int fireTicks) {
        this.remainingFireTicks = fireTicks;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void onBelowWorld() {
        this.discard();
    }

    public boolean isFree(double offsetX, double offsetY, double offsetZ) {
        return this.isFree(this.getBoundingBox().move(offsetX, offsetY, offsetZ));
    }

    private boolean isFree(AABB box) {
        return this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box);
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, (Vec3) null);
    }

    public void setOnGroundWithKnownMovement(boolean onGround, Vec3 movement) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, movement);
    }

    public boolean isSupportedBy(BlockPos pos) {
        return this.mainSupportingBlockPos.isPresent() && ((BlockPos) this.mainSupportingBlockPos.get()).equals(pos);
    }

    protected void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement) {
        if (onGround) {
            AABB axisalignedbb = this.getBoundingBox();
            AABB axisalignedbb1 = new AABB(axisalignedbb.minX, axisalignedbb.minY - 1.0E-6D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
            Optional<BlockPos> optional = this.level.findSupportingBlock(this, axisalignedbb1);

            if (!optional.isPresent() && !this.onGroundNoBlocks) {
                if (movement != null) {
                    AABB axisalignedbb2 = axisalignedbb1.move(-movement.x, 0.0D, -movement.z);

                    optional = this.level.findSupportingBlock(this, axisalignedbb2);
                    this.mainSupportingBlockPos = optional;
                }
            } else {
                this.mainSupportingBlockPos = optional;
            }

            this.onGroundNoBlocks = optional.isEmpty();
        } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
                this.mainSupportingBlockPos = Optional.empty();
            }
        }

    }

    public boolean onGround() {
        return this.onGround;
    }

    // Paper start - detailed watchdog information
    public final Object posLock = new Object(); // Paper - log detailed entity tick information

    private Vec3 moveVector;
    private double moveStartX;
    private double moveStartY;
    private double moveStartZ;

    public final Vec3 getMoveVector() {
        return this.moveVector;
    }

    public final double getMoveStartX() {
        return this.moveStartX;
    }

    public final double getMoveStartY() {
        return this.moveStartY;
    }

    public final double getMoveStartZ() {
        return this.moveStartZ;
    }
    // Paper end - detailed watchdog information

    public void move(MoverType movementType, Vec3 movement) {
        // Paper start - detailed watchdog information
        io.papermc.paper.util.TickThread.ensureTickThread("Cannot move an entity off-main");
        synchronized (this.posLock) {
            this.moveStartX = this.getX();
            this.moveStartY = this.getY();
            this.moveStartZ = this.getZ();
            this.moveVector = movement;
        }
        try {
        // Paper end - detailed watchdog information
        if (this.noPhysics) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
        } else {
            this.wasOnFire = this.isOnFire();
            if (movementType == MoverType.PISTON) {
                this.activatedTick = Math.max(this.activatedTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 20); // Paper
                this.activatedImmunityTick = Math.max(this.activatedImmunityTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 20);   // Paper
                movement = this.limitPistonMovement(movement);
                if (movement.equals(Vec3.ZERO)) {
                    return;
                }
            }

            this.level().getProfiler().push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
                movement = movement.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }
            // Paper start - ignore movement changes while inactive.
            if (isTemporarilyActive && !(this instanceof ItemEntity || this instanceof net.minecraft.world.entity.vehicle.AbstractMinecart) && movement == getDeltaMovement() && movementType == MoverType.SELF) {
                setDeltaMovement(Vec3.ZERO);
                this.level.getProfiler().pop();
                return;
            }
            // Paper end

            movement = this.maybeBackOffFromEdge(movement, movementType);
            Vec3 vec3d1 = this.collide(movement);
            double d0 = vec3d1.lengthSqr();

            if (d0 > 1.0E-7D) {
                if (this.fallDistance != 0.0F && d0 >= 1.0D) {
                    BlockHitResult movingobjectpositionblock = this.level().clip(new ClipContext(this.position(), this.position().add(vec3d1), ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this));

                    if (movingobjectpositionblock.getType() != HitResult.Type.MISS) {
                        this.resetFallDistance();
                    }
                }

                this.setPos(this.getX() + vec3d1.x, this.getY() + vec3d1.y, this.getZ() + vec3d1.z);
            }

            this.level().getProfiler().pop();
            this.level().getProfiler().push("rest");
            boolean flag = !Mth.equal(movement.x, vec3d1.x);
            boolean flag1 = !Mth.equal(movement.z, vec3d1.z);

            this.horizontalCollision = flag || flag1;
            this.verticalCollision = movement.y != vec3d1.y;
            this.verticalCollisionBelow = this.verticalCollision && movement.y < 0.0D;
            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec3d1);
            } else {
                this.minorHorizontalCollision = false;
            }

            this.setOnGroundWithKnownMovement(this.verticalCollisionBelow, vec3d1);
            BlockPos blockposition = this.getOnPosLegacy();
            BlockState iblockdata = this.level().getBlockState(blockposition);

            this.checkFallDamage(vec3d1.y, this.onGround(), iblockdata, blockposition);
            if (this.isRemoved()) {
                this.level().getProfiler().pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3 vec3d2 = this.getDeltaMovement();

                    this.setDeltaMovement(flag ? 0.0D : vec3d2.x, vec3d2.y, flag1 ? 0.0D : vec3d2.z);
                }

                Block block = iblockdata.getBlock();

                if (movement.y != vec3d1.y) {
                    block.updateEntityAfterFallOn(this.level(), this);
                }

                // CraftBukkit start
                if (this.horizontalCollision && this.getBukkitEntity() instanceof Vehicle) {
                    Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                    org.bukkit.block.Block bl = this.level.getWorld().getBlockAt(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()));

                    if (movement.x > vec3d1.x) {
                        bl = bl.getRelative(BlockFace.EAST);
                    } else if (movement.x < vec3d1.x) {
                        bl = bl.getRelative(BlockFace.WEST);
                    } else if (movement.z > vec3d1.z) {
                        bl = bl.getRelative(BlockFace.SOUTH);
                    } else if (movement.z < vec3d1.z) {
                        bl = bl.getRelative(BlockFace.NORTH);
                    }

                    if (!bl.getType().isAir()) {
                        VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, bl, org.bukkit.craftbukkit.util.CraftVector.toBukkit(moveVector)); // Paper - Expose pre-collision velocity
                        this.level.getCraftServer().getPluginManager().callEvent(event);
                    }
                }
                // CraftBukkit end

                if (this.onGround()) {
                    block.stepOn(this.level(), blockposition, iblockdata, this);
                }

                Entity.MovementEmission entity_movementemission = this.getMovementEmission();

                if (entity_movementemission.emitsAnything() && !this.isPassenger()) {
                    double d1 = vec3d1.x;
                    double d2 = vec3d1.y;
                    double d3 = vec3d1.z;

                    this.flyDist += (float) (vec3d1.length() * 0.6D);
                    BlockPos blockposition1 = this.getOnPos();
                    BlockState iblockdata1 = this.level().getBlockState(blockposition1);
                    boolean flag2 = this.isStateClimbable(iblockdata1);

                    if (!flag2) {
                        d2 = 0.0D;
                    }

                    this.walkDist += (float) vec3d1.horizontalDistance() * 0.6F;
                    this.moveDist += (float) Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3) * 0.6F;
                    if (this.moveDist > this.nextStep && !iblockdata1.isAir()) {
                        boolean flag3 = blockposition1.equals(blockposition);
                        boolean flag4 = this.vibrationAndSoundEffectsFromBlock(blockposition, iblockdata, entity_movementemission.emitsSounds(), flag3, movement);

                        if (!flag3) {
                            flag4 |= this.vibrationAndSoundEffectsFromBlock(blockposition1, iblockdata1, false, entity_movementemission.emitsEvents(), movement);
                        }

                        if (flag4) {
                            this.nextStep = this.nextStep();
                        } else if (this.isInWater()) {
                            this.nextStep = this.nextStep();
                            if (entity_movementemission.emitsSounds()) {
                                this.waterSwimSound();
                            }

                            if (entity_movementemission.emitsEvents()) {
                                this.gameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (iblockdata1.isAir()) {
                        this.processFlappingMovement();
                    }
                }

                this.tryCheckInsideBlocks();
                float f = this.getBlockSpeedFactor();

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 1.0D, (double) f));
                // Paper start - remove expensive streams from here
                boolean noneMatch = true;
                AABB fireSearchBox = this.getBoundingBox().deflate(1.0E-6D);
                {
                    int minX = Mth.floor(fireSearchBox.minX);
                    int minY = Mth.floor(fireSearchBox.minY);
                    int minZ = Mth.floor(fireSearchBox.minZ);
                    int maxX = Mth.floor(fireSearchBox.maxX);
                    int maxY = Mth.floor(fireSearchBox.maxY);
                    int maxZ = Mth.floor(fireSearchBox.maxZ);
                    fire_search_loop:
                    for (int fz = minZ; fz <= maxZ; ++fz) {
                        for (int fx = minX; fx <= maxX; ++fx) {
                            for (int fy = minY; fy <= maxY; ++fy) {
                                net.minecraft.world.level.chunk.LevelChunk chunk = (net.minecraft.world.level.chunk.LevelChunk)this.level.getChunkIfLoadedImmediately(fx >> 4, fz >> 4);
                                if (chunk == null) {
                                    // Vanilla rets an empty stream if all the chunks are not loaded, so noneMatch will be true
                                    // even if we're in lava/fire
                                    noneMatch = true;
                                    break fire_search_loop;
                                }
                                if (!noneMatch) {
                                    // don't do get type, we already know we're in fire - we just need to check the chunks
                                    // loaded state
                                    continue;
                                }

                                BlockState type = chunk.getBlockStateFinal(fx, fy, fz);
                                if (type.is(BlockTags.FIRE) || type.is(Blocks.LAVA)) {
                                    noneMatch = false;
                                    // can't break, we need to retain vanilla behavior by ensuring ALL chunks are loaded
                                }
                            }
                        }
                    }
                }
                if (noneMatch) {
                    // Paper end - remove expensive streams from here
                    if (this.remainingFireTicks <= 0) {
                        this.setRemainingFireTicks(-this.getFireImmuneTicks());
                    }

                    if (this.wasOnFire && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                        this.playEntityOnFireExtinguishedSound();
                    }
                }

                if (this.isOnFire() && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                    this.setRemainingFireTicks(-this.getFireImmuneTicks());
                }

                this.level().getProfiler().pop();
            }
        }
        // Paper start - detailed watchdog information
        } finally {
            synchronized (this.posLock) { // Paper
                this.moveVector = null;
            } // Paper
        }
        // Paper end - detailed watchdog information
    }

    private boolean isStateClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE) || state.is(Blocks.POWDER_SNOW);
    }

    private boolean vibrationAndSoundEffectsFromBlock(BlockPos pos, BlockState state, boolean playSound, boolean emitEvent, Vec3 movement) {
        if (state.isAir()) {
            return false;
        } else {
            boolean flag2 = this.isStateClimbable(state);

            if ((this.onGround() || flag2 || this.isCrouching() && movement.y == 0.0D || this.isOnRails()) && !this.isSwimming()) {
                if (playSound) {
                    this.walkingStepSound(pos, state);
                }

                if (emitEvent) {
                    this.level().gameEvent(GameEvent.STEP, this.position(), GameEvent.Context.of(this, state));
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3 adjustedMovement) {
        return false;
    }

    protected void tryCheckInsideBlocks() {
        try {
            this.checkInsideBlocks();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Checking entity block collision");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being checked for collision");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    protected void playEntityOnFireExtinguishedSound() {
        this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    public void extinguishFire() {
        if (!this.level().isClientSide && this.wasOnFire) {
            this.playEntityOnFireExtinguishedSound();
        }

        this.clearFire();
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }

    }

    /** @deprecated */
    @Deprecated
    public BlockPos getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.500001F);
    }

    public BlockPos getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    protected BlockPos getOnPos(float offset) {
        if (this.mainSupportingBlockPos.isPresent() && this.level().getChunkIfLoadedImmediately(this.mainSupportingBlockPos.get()) != null) { // Paper - ensure no loads
            BlockPos blockposition = (BlockPos) this.mainSupportingBlockPos.get();

            if (offset <= 1.0E-5F) {
                return blockposition;
            } else {
                BlockState iblockdata = this.level().getBlockState(blockposition);

                return ((double) offset > 0.5D || !iblockdata.is(BlockTags.FENCES)) && !iblockdata.is(BlockTags.WALLS) && !(iblockdata.getBlock() instanceof FenceGateBlock) ? blockposition.atY(Mth.floor(this.position.y - (double) offset)) : blockposition;
            }
        } else {
            int i = Mth.floor(this.position.x);
            int j = Mth.floor(this.position.y - (double) offset);
            int k = Mth.floor(this.position.z);

            return new BlockPos(i, j, k);
        }
    }

    protected float getBlockJumpFactor() {
        float f = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float f1 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();

        return (double) f == 1.0D ? f1 : f;
    }

    protected float getBlockSpeedFactor() {
        BlockState iblockdata = this.level().getBlockState(this.blockPosition());
        float f = iblockdata.getBlock().getSpeedFactor();

        return !iblockdata.is(Blocks.WATER) && !iblockdata.is(Blocks.BUBBLE_COLUMN) ? ((double) f == 1.0D ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f) : f;
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 movement, MoverType type) {
        return movement;
    }

    protected Vec3 limitPistonMovement(Vec3 movement) {
        if (movement.lengthSqr() <= 1.0E-7D) {
            return movement;
        } else {
            long i = this.level().getGameTime();

            if (i != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0D);
                this.pistonDeltasGameTime = i;
            }

            double d0;

            if (movement.x != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.X, movement.x);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(d0, 0.0D, 0.0D);
            } else if (movement.y != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.Y, movement.y);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(0.0D, d0, 0.0D);
            } else if (movement.z != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.Z, movement.z);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(0.0D, 0.0D, d0);
            } else {
                return Vec3.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis axis, double offsetFactor) {
        int i = axis.ordinal();
        double d1 = Mth.clamp(offsetFactor + this.pistonDeltas[i], -0.51D, 0.51D);

        offsetFactor = d1 - this.pistonDeltas[i];
        this.pistonDeltas[i] = d1;
        return offsetFactor;
    }

    private Vec3 collide(Vec3 movement) {
        // Paper start - optimise collisions
        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        if (xZero & yZero & zZero) {
            return movement;
        }

        final Level world = this.level;
        final AABB currBoundingBox = this.getBoundingBox();

        if (io.papermc.paper.util.CollisionUtil.isEmpty(currBoundingBox)) {
            return movement;
        }

        final List<AABB> potentialCollisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> potentialCollisionsVoxel = new java.util.ArrayList<>();
        final double stepHeight = (double)this.maxUpStep();
        final AABB collisionBox;
        final boolean onGround = this.onGround;

        if (xZero & zZero) {
            if (movement.y > 0.0) {
                collisionBox = io.papermc.paper.util.CollisionUtil.cutUpwards(currBoundingBox, movement.y);
            } else {
                collisionBox = io.papermc.paper.util.CollisionUtil.cutDownwards(currBoundingBox, movement.y);
            }
        } else {
            // note: xZero == false or zZero == false
            if (stepHeight > 0.0 && (onGround || (movement.y < 0.0))) {
                // don't bother getting the collisions if we don't need them.
                if (movement.y <= 0.0) {
                    collisionBox = io.papermc.paper.util.CollisionUtil.expandUpwards(currBoundingBox.expandTowards(movement.x, movement.y, movement.z), stepHeight);
                } else {
                    collisionBox = currBoundingBox.expandTowards(movement.x, Math.max(stepHeight, movement.y), movement.z);
                }
            } else {
                collisionBox = currBoundingBox.expandTowards(movement.x, movement.y, movement.z);
            }
        }

        io.papermc.paper.util.CollisionUtil.getCollisions(
                world, this, collisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
                (0),
                null, null
        );

        if (collidingWithWorldBorder = io.papermc.paper.util.CollisionUtil.isCollidingWithBorderEdge(world.getWorldBorder(), collisionBox)) { // Paper - this line *is* correct, ignore the IDE warning about assignments being used as a condition
            potentialCollisionsVoxel.add(world.getWorldBorder().getCollisionShape());
        }

        if (potentialCollisionsVoxel.isEmpty() && potentialCollisionsBB.isEmpty()) {
            return movement;
        }

        final Vec3 limitedMoveVector = io.papermc.paper.util.CollisionUtil.performCollisions(movement, currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);

        if (stepHeight > 0.0
                && (onGround || (limitedMoveVector.y != movement.y && movement.y < 0.0))
                && (limitedMoveVector.x != movement.x || limitedMoveVector.z != movement.z)) {
            Vec3 vec3d2 = io.papermc.paper.util.CollisionUtil.performCollisions(new Vec3(movement.x, stepHeight, movement.z), currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);
            final Vec3 vec3d3 = io.papermc.paper.util.CollisionUtil.performCollisions(new Vec3(0.0, stepHeight, 0.0), currBoundingBox.expandTowards(movement.x, 0.0, movement.z), potentialCollisionsVoxel, potentialCollisionsBB);

            if (vec3d3.y < stepHeight) {
                final Vec3 vec3d4 = io.papermc.paper.util.CollisionUtil.performCollisions(new Vec3(movement.x, 0.0D, movement.z), currBoundingBox.move(vec3d3), potentialCollisionsVoxel, potentialCollisionsBB).add(vec3d3);

                if (vec3d4.horizontalDistanceSqr() > vec3d2.horizontalDistanceSqr()) {
                    vec3d2 = vec3d4;
                }
            }

            if (vec3d2.horizontalDistanceSqr() > limitedMoveVector.horizontalDistanceSqr()) {
                return vec3d2.add(io.papermc.paper.util.CollisionUtil.performCollisions(new Vec3(0.0D, -vec3d2.y + movement.y, 0.0D), currBoundingBox.move(vec3d2), potentialCollisionsVoxel, potentialCollisionsBB));
            }

            return limitedMoveVector;
        } else {
            return limitedMoveVector;
        }
        // Paper end - optimise collisions
    }

    public static Vec3 collideBoundingBox(@Nullable Entity entity, Vec3 movement, AABB entityBoundingBox, Level world, List<VoxelShape> collisions) {
        Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(collisions.size() + 1);

        if (!collisions.isEmpty()) {
            builder.addAll(collisions);
        }

        WorldBorder worldborder = world.getWorldBorder();
        boolean flag = entity != null && worldborder.isInsideCloseToBorder(entity, entityBoundingBox.expandTowards(movement));

        if (flag) {
            builder.add(worldborder.getCollisionShape());
        }

        builder.addAll(world.getBlockCollisions(entity, entityBoundingBox.expandTowards(movement)));
        return Entity.collideWithShapes(movement, entityBoundingBox, builder.build());
    }

    private static Vec3 collideWithShapes(Vec3 movement, AABB entityBoundingBox, List<VoxelShape> collisions) {
        if (collisions.isEmpty()) {
            return movement;
        } else {
            double d0 = movement.x;
            double d1 = movement.y;
            double d2 = movement.z;

            if (d1 != 0.0D) {
                d1 = Shapes.collide(Direction.Axis.Y, entityBoundingBox, collisions, d1);
                if (d1 != 0.0D) {
                    entityBoundingBox = entityBoundingBox.move(0.0D, d1, 0.0D);
                }
            }

            boolean flag = Math.abs(d0) < Math.abs(d2);

            if (flag && d2 != 0.0D) {
                d2 = Shapes.collide(Direction.Axis.Z, entityBoundingBox, collisions, d2);
                if (d2 != 0.0D) {
                    entityBoundingBox = entityBoundingBox.move(0.0D, 0.0D, d2);
                }
            }

            if (d0 != 0.0D) {
                d0 = Shapes.collide(Direction.Axis.X, entityBoundingBox, collisions, d0);
                if (!flag && d0 != 0.0D) {
                    entityBoundingBox = entityBoundingBox.move(d0, 0.0D, 0.0D);
                }
            }

            if (!flag && d2 != 0.0D) {
                d2 = Shapes.collide(Direction.Axis.Z, entityBoundingBox, collisions, d2);
            }

            return new Vec3(d0, d1, d2);
        }
    }

    protected float nextStep() {
        return (float) ((int) this.moveDist + 1);
    }

    protected SoundEvent getSwimSound() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    // CraftBukkit start - Add delegate methods
    public SoundEvent getSwimSound0() {
        return this.getSwimSound();
    }

    public SoundEvent getSwimSplashSound0() {
        return this.getSwimSplashSound();
    }

    public SoundEvent getSwimHighSpeedSplashSound0() {
        return this.getSwimHighSpeedSplashSound();
    }
    // CraftBukkit end

    protected void checkInsideBlocks() {
        AABB axisalignedbb = this.getBoundingBox();
        BlockPos blockposition = BlockPos.containing(axisalignedbb.minX + 1.0E-7D, axisalignedbb.minY + 1.0E-7D, axisalignedbb.minZ + 1.0E-7D);
        BlockPos blockposition1 = BlockPos.containing(axisalignedbb.maxX - 1.0E-7D, axisalignedbb.maxY - 1.0E-7D, axisalignedbb.maxZ - 1.0E-7D);

        if (this.level().hasChunksAt(blockposition, blockposition1)) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int i = blockposition.getX(); i <= blockposition1.getX(); ++i) {
                for (int j = blockposition.getY(); j <= blockposition1.getY(); ++j) {
                    for (int k = blockposition.getZ(); k <= blockposition1.getZ(); ++k) {
                        if (!this.isAlive()) {
                            return;
                        }

                        blockposition_mutableblockposition.set(i, j, k);
                        BlockState iblockdata = this.level().getBlockState(blockposition_mutableblockposition);

                        try {
                            iblockdata.entityInside(this.level(), blockposition_mutableblockposition, this);
                            this.onInsideBlock(iblockdata);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being collided with");

                            CrashReportCategory.populateBlockDetails(crashreportsystemdetails, this.level(), blockposition_mutableblockposition, iblockdata);
                            throw new ReportedException(crashreport);
                        }
                    }
                }
            }
        }

    }

    protected void onInsideBlock(BlockState state) {}

    public void gameEvent(GameEvent event, @Nullable Entity entity) {
        this.level().gameEvent(entity, event, this.position);
    }

    public void gameEvent(GameEvent event) {
        this.gameEvent(event, this);
    }

    private void walkingStepSound(BlockPos pos, BlockState state) {
        this.playStepSound(pos, state);
        if (this.shouldPlayAmethystStepSound(state)) {
            this.playAmethystStepSound();
        }

    }

    protected void waterSwimSound() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.35F : 0.4F;
        Vec3 vec3d = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f);

        this.playSwimSound(f1);
    }

    protected BlockPos getPrimaryStepSoundBlockPos(BlockPos pos) {
        BlockPos blockposition1 = pos.above();
        BlockState iblockdata = this.level().getBlockState(blockposition1);

        return !iblockdata.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) && !iblockdata.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) ? pos : blockposition1;
    }

    protected void playCombinationStepSounds(BlockState primaryState, BlockState secondaryState) {
        SoundType soundeffecttype = primaryState.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
        this.playMuffledStepSound(secondaryState);
    }

    protected void playMuffledStepSound(BlockState state) {
        SoundType soundeffecttype = state.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.05F, soundeffecttype.getPitch() * 0.8F);
    }

    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundType soundeffecttype = state.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
    }

    private boolean shouldPlayAmethystStepSound(BlockState state) {
        return state.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
    }

    private void playAmethystStepSound() {
        this.crystalSoundIntensity *= (float) Math.pow(0.997D, (double) (this.tickCount - this.lastCrystalSoundPlayTick));
        this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
        float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
        float f1 = 0.1F + this.crystalSoundIntensity * 1.2F;

        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, f1, f);
        this.lastCrystalSoundPlayTick = this.tickCount;
    }

    protected void playSwimSound(float volume) {
        this.playSound(this.getSwimSound(), volume, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {}

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) {
            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
        }

    }

    public void playSound(SoundEvent event) {
        if (!this.isSilent()) {
            this.playSound(event, 1.0F, 1.0F);
        }

    }

    public boolean isSilent() {
        return (Boolean) this.entityData.get(Entity.DATA_SILENT);
    }

    public void setSilent(boolean silent) {
        this.entityData.set(Entity.DATA_SILENT, silent);
    }

    public boolean isNoGravity() {
        return (Boolean) this.entityData.get(Entity.DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean noGravity) {
        this.entityData.set(Entity.DATA_NO_GRAVITY, noGravity);
    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
        if (onGround) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().fallOn(this.level(), state, landedPosition, this, this.fallDistance);
                this.level().gameEvent(GameEvent.HIT_GROUND, this.position, GameEvent.Context.of(this, (BlockState) this.mainSupportingBlockPos.map((blockposition1) -> {
                    return this.level().getBlockState(blockposition1);
                }).orElse(state)));
            }

            this.resetFallDistance();
        } else if (heightDifference < 0.0D) {
            this.fallDistance -= (float) heightDifference;
        }

    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (this.type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        } else {
            if (this.isVehicle()) {
                Iterator iterator = this.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();

                    entity.causeFallDamage(fallDistance, damageMultiplier, damageSource);
                }
            }

            return false;
        }
    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    public boolean isInRain() {
        BlockPos blockposition = this.blockPosition();

        return this.level().isRainingAt(blockposition) || this.level().isRainingAt(BlockPos.containing((double) blockposition.getX(), this.getBoundingBox().maxY, (double) blockposition.getZ()));
    }

    public boolean isInBubbleColumn() {
        return this.level().getBlockState(this.blockPosition()).is(Blocks.BUBBLE_COLUMN);
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInWaterRainOrBubble() {
        return this.isInWater() || this.isInRain() || this.isInBubbleColumn();
    }

    public boolean isInWaterOrBubble() {
        return this.isInWater() || this.isInBubbleColumn();
    }

    public boolean isInLiquid() {
        return this.isInWaterOrBubble() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(FluidTags.WATER));
        }

    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d0 = this.level().dimensionType().ultraWarm() ? 0.007D : 0.0023333333333333335D;
        boolean flag = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d0);

        return this.isInWater() || flag;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        Entity entity = this.getVehicle();

        if (entity instanceof Boat) {
            Boat entityboat = (Boat) entity;

            if (!entityboat.isUnderWater()) {
                this.wasTouchingWater = false;
                return;
            }
        }

        if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014D)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
            this.clearFire();
        } else {
            this.wasTouchingWater = false;
        }

    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.fluidOnEyes.clear();
        double d0 = this.getEyeY() - 0.1111111119389534D;
        Entity entity = this.getVehicle();

        if (entity instanceof Boat) {
            Boat entityboat = (Boat) entity;

            if (!entityboat.isUnderWater() && entityboat.getBoundingBox().maxY >= d0 && entityboat.getBoundingBox().minY <= d0) {
                return;
            }
        }

        BlockPos blockposition = BlockPos.containing(this.getX(), d0, this.getZ());
        FluidState fluid = this.level().getFluidState(blockposition);
        double d1 = (double) ((float) blockposition.getY() + fluid.getHeight(this.level(), blockposition));

        if (d1 > d0) {
            Stream stream = fluid.getTags();
            Set set = this.fluidOnEyes;

            Objects.requireNonNull(this.fluidOnEyes);
            stream.forEach(set::add);
        }

    }

    protected void doWaterSplashEffect() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 vec3d = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f);

        if (f1 < 0.25F) {
            this.playSound(this.getSwimSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f2 = (float) Mth.floor(this.getY());

        double d0;
        double d1;
        int i;

        for (i = 0; (float) i < 1.0F + this.dimensions.width * 20.0F; ++i) {
            d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y - this.random.nextDouble() * 0.20000000298023224D, vec3d.z);
        }

        for (i = 0; (float) i < 1.0F + this.dimensions.width * 20.0F; ++i) {
            d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width;
            this.level().addParticle(ParticleTypes.SPLASH, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y, vec3d.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    /** @deprecated */
    @Deprecated
    protected BlockState getBlockStateOnLegacy() {
        return this.level().getBlockState(this.getOnPosLegacy());
    }

    public BlockState getBlockStateOn() {
        return this.level().getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        BlockPos blockposition = this.getOnPosLegacy();
        BlockState iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 vec3d = this.getDeltaMovement();
            BlockPos blockposition1 = this.blockPosition();
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width;
            double d1 = this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width;

            if (blockposition1.getX() != blockposition.getX()) {
                d0 = Mth.clamp(d0, (double) blockposition.getX(), (double) blockposition.getX() + 1.0D);
            }

            if (blockposition1.getZ() != blockposition.getZ()) {
                d1 = Mth.clamp(d1, (double) blockposition.getZ(), (double) blockposition.getZ() + 1.0D);
            }

            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), d0, this.getY() + 0.1D, d1, vec3d.x * -4.0D, 1.5D, vec3d.z * -4.0D);
        }

    }

    public boolean isEyeInFluid(TagKey<Fluid> fluidTag) {
        return this.fluidOnEyes.contains(fluidTag);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    public void moveRelative(float speed, Vec3 movementInput) {
        Vec3 vec3d1 = Entity.getInputVector(movementInput, speed, this.getYRot());

        this.setDeltaMovement(this.getDeltaMovement().add(vec3d1));
    }

    private static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
        double d0 = movementInput.lengthSqr();

        if (d0 < 1.0E-7D) {
            return Vec3.ZERO;
        } else {
            Vec3 vec3d1 = (d0 > 1.0D ? movementInput.normalize() : movementInput).scale((double) speed);
            float f2 = Mth.sin(yaw * 0.017453292F);
            float f3 = Mth.cos(yaw * 0.017453292F);

            return new Vec3(vec3d1.x * (double) f3 - vec3d1.z * (double) f2, vec3d1.y, vec3d1.z * (double) f3 + vec3d1.x * (double) f2);
        }
    }

    /** @deprecated */
    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ()) ? this.level().getLightLevelDependentMagicValue(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())) : 0.0F;
    }

    public void absMoveTo(double x, double y, double z, float yaw, float pitch) {
        this.absMoveTo(x, y, z);
        this.setYRot(yaw % 360.0F);
        this.setXRot(Mth.clamp(pitch, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setYHeadRot(yaw); // Paper - Update head rotation
    }

    public void absMoveTo(double x, double y, double z) {
        double d3 = Mth.clamp(x, -3.0E7D, 3.0E7D);
        double d4 = Mth.clamp(z, -3.0E7D, 3.0E7D);

        this.xo = d3;
        this.yo = y;
        this.zo = d4;
        this.setPos(d3, y, d4);
        if (this.valid) this.level.getChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4); // CraftBukkit
    }

    public void moveTo(Vec3 pos) {
        this.moveTo(pos.x, pos.y, pos.z);
    }

    public void moveTo(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
    }

    public void moveTo(BlockPos pos, float yaw, float pitch) {
        this.moveTo((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, yaw, pitch);
    }

    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        // Paper - cancel entity velocity if teleported
        if (!preserveMotion) {
            this.deltaMovement = Vec3.ZERO;
        } else {
            this.preserveMotion = false;
        }
        // Paper end
        this.setPosRaw(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setOldPosAndRot();
        this.reapplyPosition();
        this.setYHeadRot(yaw); // Paper - Update head rotation
    }

    public final void setOldPosAndRot() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
        this.xOld = d0;
        this.yOld = d1;
        this.zOld = d2;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public float distanceTo(Entity entity) {
        float f = (float) (this.getX() - entity.getX());
        float f1 = (float) (this.getY() - entity.getY());
        float f2 = (float) (this.getZ() - entity.getZ());

        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double distanceToSqr(double x, double y, double z) {
        double d3 = this.getX() - x;
        double d4 = this.getY() - y;
        double d5 = this.getZ() - z;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3 vector) {
        double d0 = this.getX() - vector.x;
        double d1 = this.getY() - vector.y;
        double d2 = this.getZ() - vector.z;

        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public void playerTouch(Player player) {}

    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (this.level.paperConfig().collisions.onlyPlayersCollide && !(entity instanceof ServerPlayer || this instanceof ServerPlayer)) return; // Paper
                double d0 = entity.getX() - this.getX();
                double d1 = entity.getZ() - this.getZ();
                double d2 = Mth.absMax(d0, d1);

                if (d2 >= 0.009999999776482582D) {
                    d2 = Math.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;

                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= 0.05000000074505806D;
                    d1 *= 0.05000000074505806D;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d0, 0.0D, -d1);
                    }

                    if (!entity.isVehicle() && entity.isPushable()) {
                        entity.push(d0, 0.0D, d1);
                    }
                }

            }
        }
    }

    public void push(double deltaX, double deltaY, double deltaZ) {
        // Paper start - call EntityPushedByEntityEvent
        this.push(deltaX, deltaY, deltaZ, null);
    }

    public void push(double deltaX, double deltaY, double deltaZ, @org.jetbrains.annotations.Nullable Entity pushingEntity) {
        org.bukkit.util.Vector delta = new org.bukkit.util.Vector(deltaX, deltaY, deltaZ);
        if (pushingEntity == null || new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(getBukkitEntity(), pushingEntity.getBukkitEntity(), delta).callEvent()) {
            this.setDeltaMovement(this.getDeltaMovement().add(delta.getX(), delta.getY(), delta.getZ()));
            this.hasImpulse = true;
        }
        // Paper end - call EntityPushedByEntityEvent
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            this.markHurt();
            return false;
        }
    }

    public final Vec3 getViewVector(float tickDelta) {
        return this.calculateViewVector(this.getViewXRot(tickDelta), this.getViewYRot(tickDelta));
    }

    public float getViewXRot(float tickDelta) {
        return tickDelta == 1.0F ? this.getXRot() : Mth.lerp(tickDelta, this.xRotO, this.getXRot());
    }

    public float getViewYRot(float tickDelta) {
        return tickDelta == 1.0F ? this.getYRot() : Mth.lerp(tickDelta, this.yRotO, this.getYRot());
    }

    protected final Vec3 calculateViewVector(float pitch, float yaw) {
        float f2 = pitch * 0.017453292F;
        float f3 = -yaw * 0.017453292F;
        float f4 = Mth.cos(f3);
        float f5 = Mth.sin(f3);
        float f6 = Mth.cos(f2);
        float f7 = Mth.sin(f2);

        return new Vec3((double) (f5 * f6), (double) (-f7), (double) (f4 * f6));
    }

    public final Vec3 getUpVector(float tickDelta) {
        return this.calculateUpVector(this.getViewXRot(tickDelta), this.getViewYRot(tickDelta));
    }

    protected final Vec3 calculateUpVector(float pitch, float yaw) {
        return this.calculateViewVector(pitch - 90.0F, yaw);
    }

    public final Vec3 getEyePosition() {
        return new Vec3(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3 getEyePosition(float tickDelta) {
        double d0 = Mth.lerp((double) tickDelta, this.xo, this.getX());
        double d1 = Mth.lerp((double) tickDelta, this.yo, this.getY()) + (double) this.getEyeHeight();
        double d2 = Mth.lerp((double) tickDelta, this.zo, this.getZ());

        return new Vec3(d0, d1, d2);
    }

    public Vec3 getLightProbePosition(float tickDelta) {
        return this.getEyePosition(tickDelta);
    }

    public final Vec3 getPosition(float delta) {
        double d0 = Mth.lerp((double) delta, this.xo, this.getX());
        double d1 = Mth.lerp((double) delta, this.yo, this.getY());
        double d2 = Mth.lerp((double) delta, this.zo, this.getZ());

        return new Vec3(d0, d1, d2);
    }

    public HitResult pick(double maxDistance, float tickDelta, boolean includeFluids) {
        Vec3 vec3d = this.getEyePosition(tickDelta);
        Vec3 vec3d1 = this.getViewVector(tickDelta);
        Vec3 vec3d2 = vec3d.add(vec3d1.x * maxDistance, vec3d1.y * maxDistance, vec3d1.z * maxDistance);

        return this.level().clip(new ClipContext(vec3d, vec3d2, ClipContext.Block.OUTLINE, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
    }

    public boolean canBeHitByProjectile() {
        return this.isAlive() && this.isPickable();
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        // Paper start
        return isCollidable(false);
    }

    public boolean isCollidable(boolean ignoreClimbing) {
        // Paper end
        return false;
    }

    // CraftBukkit start - collidable API
    public boolean canCollideWithBukkit(Entity entity) {
        return this.isPushable();
    }
    // CraftBukkit end

    public void awardKillScore(Entity entityKilled, int score, DamageSource damageSource) {
        if (entityKilled instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer) entityKilled, this, damageSource);
        }

    }

    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        double d3 = this.getX() - cameraX;
        double d4 = this.getY() - cameraY;
        double d5 = this.getZ() - cameraZ;
        double d6 = d3 * d3 + d4 * d4 + d5 * d5;

        return this.shouldRenderAtSqrDistance(d6);
    }

    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize();

        if (Double.isNaN(d1)) {
            d1 = 1.0D;
        }

        d1 *= 64.0D * Entity.viewScale;
        return distance < d1 * d1;
    }

    public boolean saveAsPassenger(CompoundTag nbt) {
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else {
            String s = this.getEncodeId();

            if (!this.persist || s == null) { // CraftBukkit - persist flag
                return false;
            } else {
                nbt.putString("id", s);
                this.saveWithoutId(nbt);
                return true;
            }
        }
    }

    // Paper start - Entity serialization api
    public boolean serializeEntity(CompoundTag compound) {
        List<Entity> pass = new java.util.ArrayList<>(this.getPassengers());
        this.passengers = ImmutableList.of();
        boolean result = save(compound);
        this.passengers = ImmutableList.copyOf(pass);
        return result;
    }
    // Paper end
    public boolean save(CompoundTag nbt) {
        return this.isPassenger() ? false : this.saveAsPassenger(nbt);
    }

    public CompoundTag saveWithoutId(CompoundTag nbt) {
        try {
            if (this.vehicle != null) {
                nbt.put("Pos", this.newDoubleList(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
            } else {
                nbt.put("Pos", this.newDoubleList(this.getX(), this.getY(), this.getZ()));
            }

            Vec3 vec3d = this.getDeltaMovement();

            nbt.put("Motion", this.newDoubleList(vec3d.x, vec3d.y, vec3d.z));

            // CraftBukkit start - Checking for NaN pitch/yaw and resetting to zero
            // TODO: make sure this is the best way to address this.
            if (Float.isNaN(this.yRot)) {
                this.yRot = 0;
            }

            if (Float.isNaN(this.xRot)) {
                this.xRot = 0;
            }
            // CraftBukkit end

            nbt.put("Rotation", this.newFloatList(this.getYRot(), this.getXRot()));
            nbt.putFloat("FallDistance", this.fallDistance);
            nbt.putShort("Fire", (short) this.remainingFireTicks);
            nbt.putShort("Air", (short) this.getAirSupply());
            nbt.putBoolean("OnGround", this.onGround());
            nbt.putBoolean("Invulnerable", this.invulnerable);
            nbt.putInt("PortalCooldown", this.portalCooldown);
            nbt.putUUID("UUID", this.getUUID());
            // CraftBukkit start
            // PAIL: Check above UUID reads 1.8 properly, ie: UUIDMost / UUIDLeast
            nbt.putLong("WorldUUIDLeast", ((ServerLevel) this.level).getWorld().getUID().getLeastSignificantBits());
            nbt.putLong("WorldUUIDMost", ((ServerLevel) this.level).getWorld().getUID().getMostSignificantBits());
            nbt.putInt("Bukkit.updateLevel", Entity.CURRENT_LEVEL);
            if (!this.persist) {
                nbt.putBoolean("Bukkit.persist", this.persist);
            }
            if (!this.visibleByDefault) {
                nbt.putBoolean("Bukkit.visibleByDefault", this.visibleByDefault);
            }
            if (this.persistentInvisibility) {
                nbt.putBoolean("Bukkit.invisible", this.persistentInvisibility);
            }
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (this.maxAirTicks != this.getDefaultMaxAirSupply()) {
                nbt.putInt("Bukkit.MaxAirSupply", this.getMaxAirSupply());
            }
            nbt.putInt("Spigot.ticksLived", this.tickCount);
            // CraftBukkit end
            Component ichatbasecomponent = this.getCustomName();

            if (ichatbasecomponent != null) {
                nbt.putString("CustomName", Component.Serializer.toJson(ichatbasecomponent));
            }

            if (this.isCustomNameVisible()) {
                nbt.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                nbt.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                nbt.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                nbt.putBoolean("Glowing", true);
            }

            int i = this.getTicksFrozen();

            if (i > 0) {
                nbt.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                nbt.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            ListTag nbttaglist;
            Iterator iterator;

            if (!this.tags.isEmpty()) {
                nbttaglist = new ListTag();
                iterator = this.tags.iterator();

                while (iterator.hasNext()) {
                    String s = (String) iterator.next();

                    nbttaglist.add(StringTag.valueOf(s));
                }

                nbt.put("Tags", nbttaglist);
            }

            this.addAdditionalSaveData(nbt);
            if (this.isVehicle()) {
                nbttaglist = new ListTag();
                iterator = this.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();
                    CompoundTag nbttagcompound1 = new CompoundTag();

                    if (entity.saveAsPassenger(nbttagcompound1)) {
                        nbttaglist.add(nbttagcompound1);
                    }
                }

                if (!nbttaglist.isEmpty()) {
                    nbt.put("Passengers", nbttaglist);
                }
            }

            // CraftBukkit start - stores eventually existing bukkit values
            if (this.bukkitEntity != null) {
                this.bukkitEntity.storeBukkitValues(nbt);
            }
            // CraftBukkit end
            // Paper start - Save the entity's origin location
            if (this.origin != null) {
                UUID originWorld = this.originWorld != null ? this.originWorld : this.level != null ? this.level.getWorld().getUID() : null;
                if (originWorld != null) {
                    nbt.putUUID("Paper.OriginWorld", originWorld);
                }
                nbt.put("Paper.Origin", this.newDoubleList(origin.getX(), origin.getY(), origin.getZ()));
            }
            if (spawnReason != null) {
                nbt.putString("Paper.SpawnReason", spawnReason.name());
            }
            // Save entity's from mob spawner status
            if (spawnedViaMobSpawner) {
                nbt.putBoolean("Paper.FromMobSpawner", true);
            }
            if (fromNetherPortal) {
                nbt.putBoolean("Paper.FromNetherPortal", true);
            }
            if (freezeLocked) {
                nbt.putBoolean("Paper.FreezeLock", true);
            }
            // Paper end
            return nbt;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being saved");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    public void load(CompoundTag nbt) {
        try {
            ListTag nbttaglist = nbt.getList("Pos", 6);
            ListTag nbttaglist1 = nbt.getList("Motion", 6);
            ListTag nbttaglist2 = nbt.getList("Rotation", 5);
            double d0 = nbttaglist1.getDouble(0);
            double d1 = nbttaglist1.getDouble(1);
            double d2 = nbttaglist1.getDouble(2);

            this.setDeltaMovement(Math.abs(d0) > 10.0D ? 0.0D : d0, Math.abs(d1) > 10.0D ? 0.0D : d1, Math.abs(d2) > 10.0D ? 0.0D : d2);
            double d3 = 3.0000512E7D;

            this.setPosRaw(Mth.clamp(nbttaglist.getDouble(0), -3.0000512E7D, 3.0000512E7D), Mth.clamp(nbttaglist.getDouble(1), -2.0E7D, 2.0E7D), Mth.clamp(nbttaglist.getDouble(2), -3.0000512E7D, 3.0000512E7D));
            this.setYRot(nbttaglist2.getFloat(0));
            this.setXRot(nbttaglist2.getFloat(1));
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = nbt.getFloat("FallDistance");
            this.remainingFireTicks = nbt.getShort("Fire");
            if (nbt.contains("Air")) {
                this.setAirSupply(nbt.getShort("Air"));
            }

            this.onGround = nbt.getBoolean("OnGround");
            this.invulnerable = nbt.getBoolean("Invulnerable");
            this.portalCooldown = nbt.getInt("PortalCooldown");
            if (nbt.hasUUID("UUID")) {
                this.uuid = nbt.getUUID("UUID");
                this.stringUUID = this.uuid.toString();
            }

            if (Double.isFinite(this.getX()) && Double.isFinite(this.getY()) && Double.isFinite(this.getZ())) {
                if (Double.isFinite((double) this.getYRot()) && Double.isFinite((double) this.getXRot())) {
                    this.reapplyPosition();
                    this.setRot(this.getYRot(), this.getXRot());
                    if (nbt.contains("CustomName", 8)) {
                        String s = nbt.getString("CustomName");

                        try {
                            this.setCustomName(Component.Serializer.fromJson(s));
                        } catch (Exception exception) {
                            Entity.LOGGER.warn("Failed to parse entity custom name {}", s, exception);
                        }
                    }

                    this.setCustomNameVisible(nbt.getBoolean("CustomNameVisible"));
                    this.setSilent(nbt.getBoolean("Silent"));
                    this.setNoGravity(nbt.getBoolean("NoGravity"));
                    this.setGlowingTag(nbt.getBoolean("Glowing"));
                    this.setTicksFrozen(nbt.getInt("TicksFrozen"));
                    this.hasVisualFire = nbt.getBoolean("HasVisualFire");
                    if (nbt.contains("Tags", 9)) {
                        this.tags.clear();
                        ListTag nbttaglist3 = nbt.getList("Tags", 8);
                        int i = Math.min(nbttaglist3.size(), 1024);

                        for (int j = 0; j < i; ++j) {
                            this.tags.add(nbttaglist3.getString(j));
                        }
                    }

                    this.readAdditionalSaveData(nbt);
                    if (this.repositionEntityAfterLoad()) {
                        this.reapplyPosition();
                    }

                } else {
                    throw new IllegalStateException("Entity has invalid rotation");
                }
            } else {
                throw new IllegalStateException("Entity has invalid position");
            }

            // CraftBukkit start
            // Spigot start
            if (this instanceof net.minecraft.world.entity.LivingEntity) {
                this.tickCount = nbt.getInt("Spigot.ticksLived");
            }
            // Spigot end
            this.persist = !nbt.contains("Bukkit.persist") || nbt.getBoolean("Bukkit.persist");
            this.visibleByDefault = !nbt.contains("Bukkit.visibleByDefault") || nbt.getBoolean("Bukkit.visibleByDefault");
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (nbt.contains("Bukkit.MaxAirSupply")) {
                this.maxAirTicks = nbt.getInt("Bukkit.MaxAirSupply");
            }
            // CraftBukkit end

            // CraftBukkit start
            // Paper - move world parsing/loading to PlayerList#placeNewPlayer
            this.getBukkitEntity().readBukkitValues(nbt);
            if (nbt.contains("Bukkit.invisible")) {
                boolean bukkitInvisible = nbt.getBoolean("Bukkit.invisible");
                this.setInvisible(bukkitInvisible);
                this.persistentInvisibility = bukkitInvisible;
            }
            // CraftBukkit end

            // Paper start - Restore the entity's origin location
            ListTag originTag = nbt.getList("Paper.Origin", 6);
            if (!originTag.isEmpty()) {
                UUID originWorld = null;
                if (nbt.contains("Paper.OriginWorld")) {
                    originWorld = nbt.getUUID("Paper.OriginWorld");
                } else if (this.level != null) {
                    originWorld = this.level.getWorld().getUID();
                }
                this.originWorld = originWorld;
                origin = new org.bukkit.util.Vector(originTag.getDouble(0), originTag.getDouble(1), originTag.getDouble(2));
            }

            spawnedViaMobSpawner = nbt.getBoolean("Paper.FromMobSpawner"); // Restore entity's from mob spawner status
            fromNetherPortal = nbt.getBoolean("Paper.FromNetherPortal");
            if (nbt.contains("Paper.SpawnReason")) {
                String spawnReasonName = nbt.getString("Paper.SpawnReason");
                try {
                    spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(spawnReasonName);
                } catch (Exception ignored) {
                    LOGGER.error("Unknown SpawnReason " + spawnReasonName + " for " + this);
                }
            }
            if (spawnReason == null) {
                if (spawnedViaMobSpawner) {
                    spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
                } else if (this instanceof Mob && (this instanceof net.minecraft.world.entity.animal.Animal || this instanceof net.minecraft.world.entity.animal.AbstractFish) && !((Mob) this).removeWhenFarAway(0.0)) {
                    if (!nbt.getBoolean("PersistenceRequired")) {
                        spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL;
                    }
                }
            }
            if (spawnReason == null) {
                spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
            }
            if (nbt.contains("Paper.FreezeLock")) {
                freezeLocked = nbt.getBoolean("Paper.FreezeLock");
            }
            // Paper end

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being loaded");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    public final String getEncodeId() {
        EntityType<?> entitytypes = this.getType();
        ResourceLocation minecraftkey = EntityType.getKey(entitytypes);

        return entitytypes.canSerialize() && minecraftkey != null ? minecraftkey.toString() : null;
    }

    protected abstract void readAdditionalSaveData(CompoundTag nbt);

    protected abstract void addAdditionalSaveData(CompoundTag nbt);

    protected ListTag newDoubleList(double... values) {
        ListTag nbttaglist = new ListTag();
        double[] adouble1 = values;
        int i = values.length;

        for (int j = 0; j < i; ++j) {
            double d0 = adouble1[j];

            nbttaglist.add(DoubleTag.valueOf(d0));
        }

        return nbttaglist;
    }

    protected ListTag newFloatList(float... values) {
        ListTag nbttaglist = new ListTag();
        float[] afloat1 = values;
        int i = values.length;

        for (int j = 0; j < i; ++j) {
            float f = afloat1[j];

            nbttaglist.add(FloatTag.valueOf(f));
        }

        return nbttaglist;
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike item) {
        return this.spawnAtLocation(item, 0);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike item, int yOffset) {
        return this.spawnAtLocation(new ItemStack(item), (float) yOffset);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack stack) {
        return this.spawnAtLocation(stack, 0.0F);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        if (stack.isEmpty()) {
            return null;
        } else if (this.level().isClientSide) {
            return null;
        } else {
            // CraftBukkit start - Capture drops for death event
            if (this instanceof net.minecraft.world.entity.LivingEntity && !((net.minecraft.world.entity.LivingEntity) this).forceDrops) {
                ((net.minecraft.world.entity.LivingEntity) this).drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack)); // Paper - mirror so we can destroy it later
                return null;
            }
            // CraftBukkit end
            ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), this.getY() + (double) yOffset, this.getZ(), stack.copy()); // Paper - copy so we can destroy original
            stack.setCount(0); // Paper - destroy this item - if this ever leaks due to game bugs, ensure it doesn't dupe

            entityitem.setDefaultPickUpDelay();
            // Paper start
            return this.spawnAtLocation(entityitem);
        }
    }
    @Nullable
    public ItemEntity spawnAtLocation(ItemEntity entityitem) {
        {
            // Paper end
            // CraftBukkit start
            EntityDropItemEvent event = new EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }
            // CraftBukkit end
            this.level().addFreshEntity(entityitem);
            return entityitem;
        }
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        } else {
            float f = this.dimensions.width * 0.8F;
            AABB axisalignedbb = AABB.ofSize(this.getEyePosition(), (double) f, 1.0E-6D, (double) f);

            // Paper start - optimise collisions
            if (io.papermc.paper.util.CollisionUtil.isEmpty(axisalignedbb)) {
                return false;
            }

            final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

            final int minX = Mth.floor(axisalignedbb.minX);
            final int minY = Mth.floor(axisalignedbb.minY);
            final int minZ = Mth.floor(axisalignedbb.minZ);
            final int maxX = Mth.floor(axisalignedbb.maxX);
            final int maxY = Mth.floor(axisalignedbb.maxY);
            final int maxZ = Mth.floor(axisalignedbb.maxZ);

            final net.minecraft.server.level.ServerChunkCache chunkProvider = (net.minecraft.server.level.ServerChunkCache)this.level.getChunkSource();

            long lastChunkKey = ChunkPos.INVALID_CHUNK_POS;
            net.minecraft.world.level.chunk.LevelChunk lastChunk = null;
            for (int fz = minZ; fz <= maxZ; ++fz) {
                tempPos.setZ(fz);
                for (int fx = minX; fx <= maxX; ++fx) {
                    final int newChunkX = fx >> 4;
                    final int newChunkZ = fz >> 4;
                    final net.minecraft.world.level.chunk.LevelChunk chunk = lastChunkKey == (lastChunkKey = io.papermc.paper.util.CoordinateUtils.getChunkKey(newChunkX, newChunkZ)) ?
                        lastChunk : (lastChunk = chunkProvider.getChunkAtIfLoadedImmediately(newChunkX, newChunkZ));
                    tempPos.setX(fx);
                    if (chunk == null) {
                        continue;
                    }
                    for (int fy = minY; fy <= maxY; ++fy) {
                        tempPos.setY(fy);

                        final BlockState state = chunk.getBlockState(tempPos);

                        if (state.emptyCollisionShape() || !state.isSuffocating(this.level, tempPos)) {
                            continue;
                        }

                        // Yes, it does not use the Entity context stuff.
                        final VoxelShape collisionShape = state.getCollisionShape(this.level, tempPos);

                        if (collisionShape.isEmpty()) {
                            continue;
                        }

                        final AABB toCollide = axisalignedbb.move(-(double)fx, -(double)fy, -(double)fz);

                        final AABB singleAABB = collisionShape.getSingleAABBRepresentation();
                        if (singleAABB != null) {
                            if (io.papermc.paper.util.CollisionUtil.voxelShapeIntersect(singleAABB, toCollide)) {
                                return true;
                            }
                            continue;
                        }

                        if (io.papermc.paper.util.CollisionUtil.voxelShapeIntersectNoEmpty(collisionShape, toCollide)) {
                            return true;
                        }
                        continue;
                    }
                }
            }
            // Paper end - optimise collisions
            return false;
        }
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean canCollideWith(Entity other) { // Paper - diff on change, hard colliding entities override this - TODO CHECK ON UPDATE - AbstractMinecart/Boat override
        return other.canBeCollidedWith() && !this.isPassengerOfSameVehicle(other);
    }

    public boolean canBeCollidedWith() { // Paper - diff on change, hard colliding entities override this TODO CHECK ON UPDATE - Boat/Shulker override
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public final void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            this.positionRider(passenger, Entity::setPos);
        }
    }

    protected void positionRider(Entity passenger, Entity.MoveFunction positionUpdater) {
        Vec3 vec3d = this.getPassengerRidingPosition(passenger);

        positionUpdater.accept(passenger, vec3d.x, vec3d.y + (double) passenger.getMyRidingOffset(this), vec3d.z);
    }

    public void onPassengerTurned(Entity passenger) {}

    public float getMyRidingOffset(Entity vehicle) {
        return this.ridingOffset(vehicle);
    }

    protected float ridingOffset(Entity vehicle) {
        return 0.0F;
    }

    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return (new Vec3(this.getPassengerAttachmentPoint(passenger, this.dimensions, 1.0F).rotateY(-this.yRot * 0.017453292F))).add(this.position());
    }

    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height, 0.0F);
    }

    public boolean startRiding(Entity entity) {
        return this.startRiding(entity, false);
    }

    public boolean showVehicleHealth() {
        return this instanceof net.minecraft.world.entity.LivingEntity;
    }

    public boolean startRiding(Entity entity, boolean force) {
        if (entity == this.vehicle || entity.level != this.level) { // Paper - check level
            return false;
        } else if (!entity.couldAcceptPassenger()) {
            return false;
        } else {
            for (Entity entity1 = entity; entity1.vehicle != null; entity1 = entity1.vehicle) {
                if (entity1.vehicle == this) {
                    return false;
                }
            }

            if (!force && (!this.canRide(entity) || !entity.canAddPassenger(this))) {
                return false;
            } else {
                if (this.valid) { // Folia - region threading - suppress entire event logic during worldgen
                // CraftBukkit start
                if (entity.getBukkitEntity() instanceof Vehicle && this.getBukkitEntity() instanceof LivingEntity) {
                    VehicleEnterEvent event = new VehicleEnterEvent((Vehicle) entity.getBukkitEntity(), this.getBukkitEntity());
                    // Suppress during worldgen
                    if (this.valid) {
                        Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event.isCancelled()) {
                        return false;
                    }
                }
                // CraftBukkit end
                // Spigot start
                org.spigotmc.event.entity.EntityMountEvent event = new org.spigotmc.event.entity.EntityMountEvent(this.getBukkitEntity(), entity.getBukkitEntity());
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                if (event.isCancelled()) {
                    return false;
                }
                } // Folia - region threading - suppress entire event logic during worldgen
                // Spigot end
                if (this.isPassenger()) {
                    this.stopRiding();
                }

                this.setPose(net.minecraft.world.entity.Pose.STANDING);
                this.vehicle = entity;
                this.vehicle.addPassenger(this);
                entity.getIndirectPassengersStream().filter((entity2) -> {
                    return entity2 instanceof ServerPlayer;
                }).forEach((entity2) -> {
                    CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer) entity2);
                });
                return true;
            }
        }
    }

    protected boolean canRide(Entity entity) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; --i) {
            ((Entity) this.passengers.get(i)).stopRiding();
        }

    }

    public void removeVehicle() {
        // Paper start
        stopRiding(false);
    }
    public void stopRiding(boolean suppressCancellation) {
        // Paper end
        if (this.vehicle != null) {
            Entity entity = this.vehicle;

            this.vehicle = null;
            if (!entity.removePassenger(this, suppressCancellation)) this.vehicle = entity; // CraftBukkit // Paper
        }

    }

    public void stopRiding() {
        this.removeVehicle();
    }

    protected void addPassenger(Entity passenger) {
        if (passenger.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(passenger);
            } else {
                List<Entity> list = Lists.newArrayList(this.passengers);

                if (!this.level().isClientSide && passenger instanceof Player && !(this.getFirstPassenger() instanceof Player)) {
                    list.add(0, passenger);
                } else {
                    list.add(passenger);
                }

                this.passengers = ImmutableList.copyOf(list);
            }

            if (!passenger.hasNullCallback()) this.gameEvent(GameEvent.ENTITY_MOUNT, passenger); // Folia - region threading - do not fire game events for entities not added
        }
    }

    // Paper start
    protected boolean removePassenger(Entity entity) { return removePassenger(entity, false);}
    protected boolean removePassenger(Entity entity, boolean suppressCancellation) { // CraftBukkit
        // Paper end
        if (entity.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            // CraftBukkit start
            if (this.valid) { // Folia - region threading - suppress entire event logic during worldgen
            CraftEntity craft = (CraftEntity) entity.getBukkitEntity().getVehicle();
            Entity orig = craft == null ? null : craft.getHandle();
            if (this.getBukkitEntity() instanceof Vehicle && entity.getBukkitEntity() instanceof LivingEntity) {
                VehicleExitEvent event = new VehicleExitEvent(
                        (Vehicle) this.getBukkitEntity(),
                        (LivingEntity) entity.getBukkitEntity(), !suppressCancellation // Paper
                );
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                CraftEntity craftn = (CraftEntity) entity.getBukkitEntity().getVehicle();
                Entity n = craftn == null ? null : craftn.getHandle();
                if (event.isCancelled() || n != orig) {
                    return false;
                }
            }
            // CraftBukkit end
            // Spigot start
            org.spigotmc.event.entity.EntityDismountEvent event = new org.spigotmc.event.entity.EntityDismountEvent(entity.getBukkitEntity(), this.getBukkitEntity(), !suppressCancellation); // Paper
            // Suppress during worldgen
            if (this.valid) {
                Bukkit.getPluginManager().callEvent(event);
            }
            if (event.isCancelled()) {
                return false;
            }
            } // Folia - region threading - suppress entire event logic during worldgen
            // Spigot end
            if (this.passengers.size() == 1 && this.passengers.get(0) == entity) {
                this.passengers = ImmutableList.of();
            } else {
                this.passengers = (ImmutableList) this.passengers.stream().filter((entity1) -> {
                    return entity1 != entity;
                }).collect(ImmutableList.toImmutableList());
            }

            entity.boardingCooldown = 60;
            if (!entity.hasNullCallback()) this.gameEvent(GameEvent.ENTITY_DISMOUNT, entity); // Folia - region threading - do not fire game events for entities not added
        }
        return true; // CraftBukkit
    }

    protected boolean canAddPassenger(Entity passenger) {
        return this.passengers.isEmpty();
    }

    protected boolean couldAcceptPassenger() {
        return true;
    }

    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    public double lerpTargetX() {
        return this.getX();
    }

    public double lerpTargetY() {
        return this.getY();
    }

    public double lerpTargetZ() {
        return this.getZ();
    }

    public float lerpTargetXRot() {
        return this.getXRot();
    }

    public float lerpTargetYRot() {
        return this.getYRot();
    }

    public void lerpHeadTo(float yaw, int interpolationSteps) {
        this.setYHeadRot(yaw);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3 getHandHoldingItemAngle(Item item) {
        if (!(this instanceof Player)) {
            return Vec3.ZERO;
        } else {
            Player entityhuman = (Player) this;
            boolean flag = entityhuman.getOffhandItem().is(item) && !entityhuman.getMainHandItem().is(item);
            HumanoidArm enummainhand = flag ? entityhuman.getMainArm().getOpposite() : entityhuman.getMainArm();

            return this.calculateViewVector(0.0F, this.getYRot() + (float) (enummainhand == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5D);
        }
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.getXRot(), this.getYRot());
    }

    public Vec3 getForward() {
        return Vec3.directionFromRotation(this.getRotationVector());
    }

    public void handleInsidePortal(BlockPos pos) {
        if (this.isOnPortalCooldown()) {
            this.setPortalCooldown();
        } else {
            if (!this.level().isClientSide && !pos.equals(this.portalEntrancePos)) {
                this.portalEntrancePos = pos.immutable();
            }

            this.isInsidePortal = true;
        }
    }

    protected void handleNetherPortal() {
        if (this.level() instanceof ServerLevel) {
            int i = this.getPortalWaitTime();
            ServerLevel worldserver = (ServerLevel) this.level();

            if (this.isInsidePortal) {
                MinecraftServer minecraftserver = worldserver.getServer();
                ResourceKey<Level> resourcekey = this.level().getTypeKey() == LevelStem.NETHER ? Level.OVERWORLD : Level.NETHER; // CraftBukkit
                ServerLevel worldserver1 = minecraftserver.getLevel(resourcekey);

                if (true && !this.isPassenger() && this.portalTime++ >= i) { // CraftBukkit
                    this.level().getProfiler().push("portal");
                    this.portalTime = i;
                    // Paper start
                    io.papermc.paper.event.entity.EntityPortalReadyEvent event = new io.papermc.paper.event.entity.EntityPortalReadyEvent(this.getBukkitEntity(), worldserver1 == null ? null : worldserver1.getWorld(), org.bukkit.PortalType.NETHER);
                    if (!event.callEvent()) {
                        this.portalTime = 0;
                    } else {
                        worldserver1 = event.getTargetWorld() == null ? null : ((CraftWorld) event.getTargetWorld()).getHandle();
                    // Paper end
                    this.setPortalCooldown();
                    // CraftBukkit start
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).changeDimension(worldserver1, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
                    } else {
                        this.changeDimension(worldserver1);
                    }
                    } // Paper
                    // CraftBukkit end
                    this.level().getProfiler().pop();
                }

                this.isInsidePortal = false;
            } else {
                if (this.portalTime > 0) {
                    this.portalTime -= 4;
                }

                if (this.portalTime < 0) {
                    this.portalTime = 0;
                }
            }

            this.processPortalCooldown();
            this.tickEndPortal(); // Paper - make end portalling safe
        }
    }

    public int getDimensionChangingDelay() {
        return 300;
    }

    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    public void handleDamageEvent(DamageSource damageSource) {}

    public void handleEntityEvent(byte status) {
        switch (status) {
            case 53:
                HoneyBlock.showSlideParticles(this);
            default:
        }
    }

    public void animateHurt(float yaw) {}

    public Iterable<ItemStack> getHandSlots() {
        return Entity.EMPTY_LIST;
    }

    public Iterable<ItemStack> getArmorSlots() {
        return Entity.EMPTY_LIST;
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorSlots());
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    public boolean isOnFire() {
        boolean flag = this.level() != null && this.level().isClientSide;

        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean dismountsUnderwater() {
        return this.getType().is(EntityTypeTags.DISMOUNTS_UNDERWATER);
    }

    public boolean canControlVehicle() {
        return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER);
    }

    public void setShiftKeyDown(boolean sneaking) {
        this.setSharedFlag(1, sneaking);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(net.minecraft.world.entity.Pose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean sprinting) {
        this.setSharedFlag(3, sprinting);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(net.minecraft.world.entity.Pose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean swimming) {
        // CraftBukkit start
        if (this.valid && this.isSwimming() != swimming && this instanceof net.minecraft.world.entity.LivingEntity) {
            if (CraftEventFactory.callToggleSwimEvent((net.minecraft.world.entity.LivingEntity) this, swimming).isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.setSharedFlag(4, swimming);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean glowing) {
        this.hasGlowingTag = glowing;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    public boolean isInvisibleTo(Player player) {
        if (player.isSpectator()) {
            return false;
        } else {
            Team scoreboardteambase = this.getTeam();

            return scoreboardteambase != null && player != null && player.getTeam() == scoreboardteambase && scoreboardteambase.canSeeFriendlyInvisibles() ? false : this.isInvisible();
        }
    }

    public boolean isOnRails() {
        return false;
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> callback) {}

    @Nullable
    public Team getTeam() {
        // Folia start - region threading
        if (true) {
            return null;
        }
        // Folia end - region threading
        if (!this.level().paperConfig().scoreboards.allowNonPlayerEntitiesOnScoreboards && !(this instanceof Player)) { return null; } // Paper
        return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public boolean isAlliedTo(Entity other) {
        return this.isAlliedTo(other.getTeam());
    }

    public boolean isAlliedTo(Team team) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(team) : false;
    }

    // CraftBukkit - start
    public void setInvisible(boolean invisible) {
        if (!this.persistentInvisibility) { // Prevent Minecraft from removing our invisibility flag
            this.setSharedFlag(5, invisible);
        }
        // CraftBukkit - end
    }

    public boolean getSharedFlag(int index) {
        return ((Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) & 1 << index) != 0;
    }

    public void setSharedFlag(int index, boolean value) {
        byte b0 = (Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID);

        if (value) {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 | 1 << index));
        } else {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 & ~(1 << index)));
        }

    }

    public int getMaxAirSupply() {
        return this.maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    public int getAirSupply() {
        return (Integer) this.entityData.get(Entity.DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int air) {
        // CraftBukkit start
        EntityAirChangeEvent event = new EntityAirChangeEvent(this.getBukkitEntity(), air);
        // Suppress during worldgen
        if (this.valid) {
            event.getEntity().getServer().getPluginManager().callEvent(event);
        }
        if (event.isCancelled() && this.getAirSupply() != air) {
            this.entityData.markDirty(Entity.DATA_AIR_SUPPLY_ID);
            return;
        }
        this.entityData.set(Entity.DATA_AIR_SUPPLY_ID, event.getAmount());
        // CraftBukkit end
    }

    public int getTicksFrozen() {
        return (Integer) this.entityData.get(Entity.DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int frozenTicks) {
        this.entityData.set(Entity.DATA_TICKS_FROZEN, frozenTicks);
    }

    public float getPercentFrozen() {
        int i = this.getTicksRequiredToFreeze();

        return (float) Math.min(this.getTicksFrozen(), i) / (float) i;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(ServerLevel world, LightningBolt lightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        // CraftBukkit start
        final org.bukkit.entity.Entity thisBukkitEntity = this.getBukkitEntity();
        final org.bukkit.entity.Entity stormBukkitEntity = lightning.getBukkitEntity();
        final PluginManager pluginManager = Bukkit.getPluginManager();
        // CraftBukkit end

        if (this.remainingFireTicks == 0) {
            // CraftBukkit start - Call a combust event when lightning strikes
            EntityCombustByEntityEvent entityCombustEvent = new EntityCombustByEntityEvent(stormBukkitEntity, thisBukkitEntity, 8);
            pluginManager.callEvent(entityCombustEvent);
            if (!entityCombustEvent.isCancelled()) {
                this.setSecondsOnFire(entityCombustEvent.getDuration(), false);
            // Paper start - fix EntityCombustEvent cancellation.
            } else {
                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            // Paper end
            }
            // CraftBukkit end
        }

        // CraftBukkit start
        if (thisBukkitEntity instanceof Hanging) {
            HangingBreakByEntityEvent hangingEvent = new HangingBreakByEntityEvent((Hanging) thisBukkitEntity, stormBukkitEntity);
            pluginManager.callEvent(hangingEvent);

            if (hangingEvent.isCancelled()) {
                return;
            }
        }

        if (this.fireImmune()) {
            return;
        }
        CraftEventFactory.entityDamageRT.set(lightning); // Folia - region threading
        if (!this.hurt(this.damageSources().lightningBolt(), 5.0F)) {
            CraftEventFactory.entityDamageRT.set(null); // Folia - region threading
            return;
        }
        // CraftBukkit end
    }

    public void onAboveBubbleCol(boolean drag) {
        Vec3 vec3d = this.getDeltaMovement();
        double d0;

        if (drag) {
            d0 = Math.max(-0.9D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(1.8D, vec3d.y + 0.1D);
        }

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
    }

    public void onInsideBubbleColumn(boolean drag) {
        Vec3 vec3d = this.getDeltaMovement();
        double d0;

        if (drag) {
            d0 = Math.max(-0.3D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(0.7D, vec3d.y + 0.06D);
        }

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
        this.resetFallDistance();
    }

    public boolean killedEntity(ServerLevel world, net.minecraft.world.entity.LivingEntity other) {
        return true;
    }

    public void checkSlowFallDistance() {
        if (this.getDeltaMovement().y() > -0.5D && this.fallDistance > 1.0F) {
            this.fallDistance = 1.0F;
        }

    }

    public void resetFallDistance() {
        this.fallDistance = 0.0F;
    }

    protected void moveTowardsClosestSpace(double x, double y, double z) {
        BlockPos blockposition = BlockPos.containing(x, y, z);
        Vec3 vec3d = new Vec3(x - (double) blockposition.getX(), y - (double) blockposition.getY(), z - (double) blockposition.getZ());
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        Direction enumdirection = Direction.UP;
        double d3 = Double.MAX_VALUE;
        Direction[] aenumdirection = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection1 = aenumdirection[j];

            blockposition_mutableblockposition.setWithOffset(blockposition, enumdirection1);
            if (!this.level().getBlockState(blockposition_mutableblockposition).isCollisionShapeFullBlock(this.level(), blockposition_mutableblockposition)) {
                double d4 = vec3d.get(enumdirection1.getAxis());
                double d5 = enumdirection1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - d4 : d4;

                if (d5 < d3) {
                    d3 = d5;
                    enumdirection = enumdirection1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = (float) enumdirection.getAxisDirection().getStep();
        Vec3 vec3d1 = this.getDeltaMovement().scale(0.75D);

        if (enumdirection.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement((double) (f1 * f), vec3d1.y, vec3d1.z);
        } else if (enumdirection.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec3d1.x, (double) (f1 * f), vec3d1.z);
        } else if (enumdirection.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec3d1.x, vec3d1.y, (double) (f1 * f));
        }

    }

    public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = multiplier;
    }

    private static Component removeAction(Component textComponent) {
        MutableComponent ichatmutablecomponent = textComponent.plainCopy().setStyle(textComponent.getStyle().withClickEvent((ClickEvent) null));
        Iterator iterator = textComponent.getSiblings().iterator();

        while (iterator.hasNext()) {
            Component ichatbasecomponent1 = (Component) iterator.next();

            ichatmutablecomponent.append(Entity.removeAction(ichatbasecomponent1));
        }

        return ichatmutablecomponent;
    }

    @Override
    public Component getName() {
        Component ichatbasecomponent = this.getCustomName();

        return ichatbasecomponent != null ? Entity.removeAction(ichatbasecomponent) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float headYaw) {}

    public void setYBodyRot(float bodyYaw) {}

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity attacker) {
        return false;
    }

    public String toString() {
        String s = this.level() == null ? "~NULL~" : this.level().toString();

        return this.removalReason != null ? String.format(Locale.ROOT, "%s['%s'/%d, uuid='%s', l='%s', x=%.2f, y=%.2f, z=%.2f, cpos=%s, tl=%d, v=%b, removed=%s]", this.getClass().getSimpleName(), this.getName().getString(), this.id, this.uuid, s, this.getX(), this.getY(), this.getZ(), this.chunkPosition(), this.tickCount, this.valid, this.removalReason) : String.format(Locale.ROOT, "%s['%s'/%d, uuid='%s', l='%s', x=%.2f, y=%.2f, z=%.2f, cpos=%s, tl=%d, v=%b]", this.getClass().getSimpleName(), this.getName().getString(), this.id, this.uuid, s, this.getX(), this.getY(), this.getZ(), this.chunkPosition(), this.tickCount, this.valid);
    }

    public boolean isInvulnerableTo(DamageSource damageSource) {
        return this.isRemoved() || this.invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !damageSource.isCreativePlayer() || damageSource.is(DamageTypeTags.IS_FIRE) && this.fireImmune() || damageSource.is(DamageTypeTags.IS_FALL) && this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public void copyPosition(Entity entity) {
        this.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    public void restoreFrom(Entity original) {
        // Paper start
        CraftEntity bukkitEntity = original.bukkitEntity;
        if (bukkitEntity != null) {
            bukkitEntity.setHandle(this);
            this.bukkitEntity = bukkitEntity;
        }
        // Paper end
        CompoundTag nbttagcompound = original.saveWithoutId(new CompoundTag());

        nbttagcompound.remove("Dimension");
        this.load(nbttagcompound);
        this.portalCooldown = original.portalCooldown;
        this.portalEntrancePos = original.portalEntrancePos;
    }

    // Folia start - region threading
    public static class EntityTreeNode {
        @Nullable
        public EntityTreeNode parent;
        public Entity root;
        @Nullable
        public EntityTreeNode[] passengers;

        public EntityTreeNode(EntityTreeNode parent, Entity root) {
            this.parent = parent;
            this.root = root;
        }

        public EntityTreeNode(EntityTreeNode parent, Entity root, EntityTreeNode[] passengers) {
            this.parent = parent;
            this.root = root;
            this.passengers = passengers;
        }

        public List<EntityTreeNode> getFullTree() {
            List<EntityTreeNode> ret = new java.util.ArrayList<>();
            ret.add(this);

            // this is just a BFS except we don't remove from head, we just advance down the list
            for (int i = 0; i < ret.size(); ++i) {
                EntityTreeNode node = ret.get(i);

                EntityTreeNode[] passengers = node.passengers;
                if (passengers == null) {
                    continue;
                }
                for (EntityTreeNode passenger : passengers) {
                    ret.add(passenger);
                }
            }

            return ret;
        }

        public void restore() {
            java.util.ArrayDeque<EntityTreeNode> queue = new java.util.ArrayDeque<>();
            queue.add(this);

            EntityTreeNode curr;
            while ((curr = queue.pollFirst()) != null) {
                EntityTreeNode[] passengers = curr.passengers;
                if (passengers == null) {
                    continue;
                }

                List<Entity> newPassengers = new java.util.ArrayList<>();
                for (EntityTreeNode passenger : passengers) {
                    newPassengers.add(passenger.root);
                    passenger.root.vehicle = curr.root;
                }

                curr.root.passengers = ImmutableList.copyOf(newPassengers);
            }
        }

        public void addTracker() {
            for (final EntityTreeNode node : this.getFullTree()) {
                if (node.root.tracker != null) {
                    for (final ServerPlayer player : node.root.level.getLocalPlayers()) {
                        node.root.tracker.updatePlayer(player);
                    }
                }
            }
        }

        public void clearTracker() {
            for (final EntityTreeNode node : this.getFullTree()) {
                if (node.root.tracker != null) {
                    node.root.tracker.removeNonTickThreadPlayers();
                    for (final ServerPlayer player : node.root.level.getLocalPlayers()) {
                        node.root.tracker.removePlayer(player);
                    }
                }
            }
        }

        public void adjustRiders(boolean teleport) {
            java.util.ArrayDeque<EntityTreeNode> queue = new java.util.ArrayDeque<>();
            queue.add(this);

            EntityTreeNode curr;
            while ((curr = queue.pollFirst()) != null) {
                EntityTreeNode[] passengers = curr.passengers;
                if (passengers == null) {
                    continue;
                }

                for (EntityTreeNode passenger : passengers) {
                    curr.root.positionRider(passenger.root, teleport ? Entity::moveTo : Entity::setPos);
                }
            }
        }
    }

    public void repositionAllPassengers(boolean teleport) {
        this.makePassengerTree().adjustRiders(teleport);
    }

    protected EntityTreeNode makePassengerTree() {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot read passengers off of the main thread");

        EntityTreeNode root = new EntityTreeNode(null, this);
        java.util.ArrayDeque<EntityTreeNode> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        EntityTreeNode curr;
        while ((curr = queue.pollFirst()) != null) {
            Entity vehicle = curr.root;
            List<Entity> passengers = vehicle.passengers;
            if (passengers.isEmpty()) {
                continue;
            }

            EntityTreeNode[] treePassengers = new EntityTreeNode[passengers.size()];
            curr.passengers = treePassengers;

            for (int i = 0; i < passengers.size(); ++i) {
                Entity passenger = passengers.get(i);
                queue.addLast(treePassengers[i] = new EntityTreeNode(curr, passenger));
            }
        }

        return root;
    }

    protected EntityTreeNode detachPassengers() {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot adjust passengers/vehicle off of the main thread");

        EntityTreeNode root = new EntityTreeNode(null, this);
        java.util.ArrayDeque<EntityTreeNode> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        EntityTreeNode curr;
        while ((curr = queue.pollFirst()) != null) {
            Entity vehicle = curr.root;
            List<Entity> passengers = vehicle.passengers;
            if (passengers.isEmpty()) {
                continue;
            }

            vehicle.passengers = ImmutableList.of();

            EntityTreeNode[] treePassengers = new EntityTreeNode[passengers.size()];
            curr.passengers = treePassengers;

            for (int i = 0; i < passengers.size(); ++i) {
                Entity passenger = passengers.get(i);
                passenger.vehicle = null;
                queue.addLast(treePassengers[i] = new EntityTreeNode(curr, passenger));
            }
        }

        return root;
    }

    /**
     * This flag will perform an async load on the chunks determined by
     * the entity's bounding box before teleporting the entity.
     */
    public static final long TELEPORT_FLAG_LOAD_CHUNK = 1L << 0;
    /**
     * This flag requires the entity being teleported to be a root vehicle.
     * Thus, if you want to teleport a non-root vehicle, you must dismount
     * the target entity before calling teleport, otherwise the
     * teleport will be refused.
     */
    public static final long TELEPORT_FLAG_TELEPORT_PASSENGERS = 1L << 1;
    /**
     * The flag will dismount any passengers and dismout from the current vehicle
     * to teleport if and only if dismounting would result in the teleport being allowed.
     */
    public static final long TELEPORT_FLAG_UNMOUNT = 1L << 2;

    protected void placeSingleSync(ServerLevel originWorld, ServerLevel destination, EntityTreeNode treeNode, long teleportFlags) {
        destination.addDuringTeleport(this);
    }

    protected final void placeInAsync(ServerLevel originWorld, ServerLevel destination, long teleportFlags,
                                      EntityTreeNode passengerTree, java.util.function.Consumer<Entity> teleportComplete) {
        Vec3 pos = this.position();
        ChunkPos posChunk = new ChunkPos(
            io.papermc.paper.util.CoordinateUtils.getChunkX(pos),
            io.papermc.paper.util.CoordinateUtils.getChunkZ(pos)
        );

        // ensure the region is always ticking in case of a shutdown
        // otherwise, the shutdown will not be able to complete the shutdown as it requires a ticking region
        Long teleportHoldId = Long.valueOf(TELEPORT_HOLD_TICKET_GEN.getAndIncrement());
        originWorld.chunkSource.addTicketAtLevel(
            TicketType.TELEPORT_HOLD_TICKET, posChunk,
            io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
            teleportHoldId
        );
        final ServerLevel.PendingTeleport pendingTeleport = new ServerLevel.PendingTeleport(passengerTree, pos);
        destination.pushPendingTeleport(pendingTeleport);

        Runnable scheduleEntityJoin = () -> {
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                destination,
                io.papermc.paper.util.CoordinateUtils.getChunkX(pos), io.papermc.paper.util.CoordinateUtils.getChunkZ(pos),
                () -> {
                    if (!destination.removePendingTeleport(pendingTeleport)) {
                        // shutdown logic placed the entity already, and we are shutting down - do nothing to ensure
                        // we do not produce any errors here
                        return;
                    }
                    originWorld.chunkSource.removeTicketAtLevel(
                        TicketType.TELEPORT_HOLD_TICKET, posChunk,
                        io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                        teleportHoldId
                    );
                    List<EntityTreeNode> fullTree = passengerTree.getFullTree();
                    for (EntityTreeNode node : fullTree) {
                        node.root.placeSingleSync(originWorld, destination, node, teleportFlags);
                    }

                    // restore passenger tree
                    passengerTree.restore();
                    passengerTree.adjustRiders(true);

                    // invoke post dimension change now
                    for (EntityTreeNode node : fullTree) {
                        node.root.postChangeDimension();
                    }

                    if (teleportComplete != null) {
                        teleportComplete.accept(Entity.this);
                    }
                }
            );
        };

        if ((teleportFlags & TELEPORT_FLAG_LOAD_CHUNK) != 0L) {
            destination.loadChunksForMoveAsync(
                Entity.this.getBoundingBox(), ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER,
                (chunkList) -> {
                    for (net.minecraft.world.level.chunk.ChunkAccess chunk : chunkList) {
                        destination.chunkSource.addTicketAtLevel(
                            TicketType.POST_TELEPORT, chunk.getPos(),
                            io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.FULL_LOADED_TICKET_LEVEL,
                            Integer.valueOf(Entity.this.getId())
                        );
                    }
                    scheduleEntityJoin.run();
                }
            );
        } else {
            scheduleEntityJoin.run();
        }
    }

    protected boolean canTeleportAsync() {
        return !this.hasNullCallback() && !this.isRemoved() && this.isAlive() && (!(this instanceof net.minecraft.world.entity.LivingEntity livingEntity) || !livingEntity.isSleeping());
    }

    // Mojang for whatever reason has started storing positions to cache certain physics properties that entities collide with
    // As usual though, they don't properly do anything to prevent serious desync with respect to the current entity position
    // We add additional logic to reset these before teleporting to prevent issues with them possibly tripping thread checks.
    protected void resetStoredPositions() {
        this.mainSupportingBlockPos = Optional.empty();
    }

    protected void teleportSyncSameRegion(Vec3 pos, Float yaw, Float pitch, Vec3 speedDirectionUpdate) {
        if (yaw != null) {
            this.setYRot(yaw.floatValue());
            this.setYHeadRot(yaw.floatValue());
        }
        if (pitch != null) {
            this.setXRot(pitch.floatValue());
        }
        if (speedDirectionUpdate != null) {
            this.setDeltaMovement(speedDirectionUpdate.normalize().scale(this.getDeltaMovement().length()));
        }
        this.moveTo(pos.x, pos.y, pos.z);
        this.resetStoredPositions();
    }

    protected void transform(Vec3 pos, Float yaw, Float pitch, Vec3 speedDirectionUpdate) {
        if (yaw != null) {
            this.setYRot(yaw.floatValue());
            this.setYHeadRot(yaw.floatValue());
        }
        if (pitch != null) {
            this.setXRot(pitch.floatValue());
        }
        if (speedDirectionUpdate != null) {
            this.setDeltaMovement(speedDirectionUpdate);
        }
        if (pos != null) {
            this.setPosRaw(pos.x, pos.y, pos.z);
        }
    }

    protected Entity transformForAsyncTeleport(ServerLevel destination, Vec3 pos, Float yaw, Float pitch, Vec3 speedDirectionUpdate) {
        this.removeAfterChangingDimensions(); // remove before so that any CBEntity#getHandle call affects this entity before copying

        Entity copy = this.getType().create(destination);
        copy.restoreFrom(this);
        copy.transform(pos, yaw, pitch, speedDirectionUpdate);
        // vanilla code used to call remove _after_ copying, and some stuff is required to be after copy - so add hook here
        // for example, clearing of inventory after switching dimensions
        this.postRemoveAfterChangingDimensions();

        return copy;
    }

    public final boolean teleportAsync(ServerLevel destination, Vec3 pos, Float yaw, Float pitch, Vec3 speedDirectionUpdate,
                                       org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause, long teleportFlags,
                                       java.util.function.Consumer<Entity> teleportComplete) {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot teleport entity async");

        if (!ServerLevel.isInSpawnableBounds(new BlockPos(io.papermc.paper.util.CoordinateUtils.getBlockX(pos), io.papermc.paper.util.CoordinateUtils.getBlockY(pos), io.papermc.paper.util.CoordinateUtils.getBlockZ(pos)))) {
            return false;
        }

        if (!this.canTeleportAsync()) {
            return false;
        }
        this.getBukkitEntity(); // force bukkit entity to be created before TPing
        if ((teleportFlags & TELEPORT_FLAG_UNMOUNT) == 0L) {
            for (Entity entity : this.getIndirectPassengers()) {
                if (!entity.canTeleportAsync()) {
                    return false;
                }
                entity.getBukkitEntity(); // force bukkit entity to be created before TPing
            }
        } else {
            this.unRide();
        }

        if ((teleportFlags & TELEPORT_FLAG_TELEPORT_PASSENGERS) != 0L) {
            if (this.isPassenger()) {
                return false;
            }
        } else {
            if (this.isVehicle() || this.isPassenger()) {
                return false;
            }
        }

        // TODO any events that can modify go HERE

        // check for same region
        if (destination == this.level()) {
            Vec3 currPos = this.position();
            if (
                destination.regioniser.getRegionAtUnsynchronised(
                    io.papermc.paper.util.CoordinateUtils.getChunkX(currPos), io.papermc.paper.util.CoordinateUtils.getChunkZ(currPos)
                ) == destination.regioniser.getRegionAtUnsynchronised(
                    io.papermc.paper.util.CoordinateUtils.getChunkX(pos), io.papermc.paper.util.CoordinateUtils.getChunkZ(pos)
                )
            ) {
                EntityTreeNode passengerTree = this.detachPassengers();
                // Note: The client does not accept position updates for controlled entities. So, we must
                // perform a lot of tracker updates here to make it all work out.

                // first, clear the tracker
                passengerTree.clearTracker();
                for (EntityTreeNode entity : passengerTree.getFullTree()) {
                    entity.root.teleportSyncSameRegion(pos, yaw, pitch, speedDirectionUpdate);
                }

                passengerTree.restore();
                // re-add to the tracker once the tree is restored
                passengerTree.addTracker();

                // adjust entities to final position
                passengerTree.adjustRiders(true);

                // the tracker clear/add logic is only used in the same region, as the other logic
                // performs add/remove from world logic which will also perform add/remove tracker logic

                if (teleportComplete != null) {
                    teleportComplete.accept(this);
                }
                return true;
            }
        }

        EntityTreeNode passengerTree = this.detachPassengers();
        List<EntityTreeNode> fullPassengerTree = passengerTree.getFullTree();
        ServerLevel originWorld = (ServerLevel)this.level;

        for (EntityTreeNode node : fullPassengerTree) {
            node.root.preChangeDimension();
        }

        for (EntityTreeNode node : fullPassengerTree) {
            node.root = node.root.transformForAsyncTeleport(destination, pos, yaw, pitch, speedDirectionUpdate);
        }

        passengerTree.root.placeInAsync(originWorld, destination, teleportFlags, passengerTree, teleportComplete);

        return true;
    }

    public void preChangeDimension() {

    }

    public void postChangeDimension() {
        this.resetStoredPositions();
    }

    protected static enum PortalType {
        NETHER, END;
    }

    public boolean doPortalLogic() {
        if (this.tryNetherPortal()) {
            return true;
        }
        if (this.tryEndPortal()) {
            return true;
        }
        return false;
    }

    protected boolean tryEndPortal() {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot portal entity async");
        BlockPos pos = this.portalBlock;
        ServerLevel world = this.portalWorld;
        this.portalBlock = null;
        this.portalWorld = null;

        if (pos == null || world == null || world != this.level) {
            return false;
        }

        if (this.isPassenger() || this.isVehicle() || !this.canChangeDimensions() || this.isRemoved() || !this.valid || !this.isAlive()) {
            return false;
        }

        return this.endPortalLogicAsync();
    }

    protected boolean tryNetherPortal() {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot portal entity async");

        int portalWaitTime = this.getPortalWaitTime();

        if (this.isInsidePortal) {
            // if we are in a nether portal still, this flag will be set next tick.
            this.isInsidePortal = false;
            if (this.portalTime++ >= portalWaitTime) {
                this.portalTime = portalWaitTime;
                if (this.netherPortalLogicAsync()) {
                    return true;
                }
            }
        } else {
            // rapidly decrease portal time
            this.portalTime = Math.max(0, this.portalTime - 4);
        }

        this.processPortalCooldown();
        return false;
    }

    public boolean endPortalLogicAsync() {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot portal entity async");

        ServerLevel destination = this.getServer().getLevel(this.level().getTypeKey() == LevelStem.END ? Level.OVERWORLD : Level.END);
        if (destination == null) {
            // wat
            return false;
        }

        return this.portalToAsync(destination, false, PortalType.END, null);
    }

    public boolean netherPortalLogicAsync() {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot portal entity async");

        ServerLevel destination = this.getServer().getLevel(this.level().getTypeKey() == LevelStem.NETHER ? Level.OVERWORLD : Level.NETHER);
        if (destination == null) {
            // wat
            return false;
        }

        return this.portalToAsync(destination, false, PortalType.NETHER, null);
    }

    private static final java.util.concurrent.atomic.AtomicLong CREATE_PORTAL_DOUBLE_CHECK = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong TELEPORT_HOLD_TICKET_GEN = new java.util.concurrent.atomic.AtomicLong();

    // To simplify portal logic, in region threading both players
    // and non-player entities will create portals. By guaranteeing
    // that the teleportation can take place, we can simply
    // remove the entity, find/create the portal, and place async.
    // If we have to worry about whether the entity may not teleport,
    // we need to first search, then report back, ...
    protected void findOrCreatePortalAsync(ServerLevel origin, ServerLevel destination, PortalType type,
                                           ca.spottedleaf.concurrentutil.completable.Completable<PortalInfo> portalInfoCompletable) {
        switch (type) {
            // end portal logic is quite simple, the spawn in the end is fixed and when returning to the overworld
            // we just select the spawn position
            case END: {
                if (destination.getTypeKey() == LevelStem.END) {
                    BlockPos targetPos = ServerLevel.END_SPAWN_POINT;
                    // need to load chunks so we can create the platform
                    destination.loadChunksAsync(
                        targetPos, 16, // load 16 blocks to be safe from block physics
                        ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGH,
                        (chunks) -> {
                            ServerLevel.makeObsidianPlatform(destination, null, targetPos);

                            // the portal obsidian is placed at targetPos.y - 2, so if we want to place the entity
                            // on the obsidian, we need to spawn at targetPos.y - 1
                            portalInfoCompletable.complete(
                                new PortalInfo(Vec3.atBottomCenterOf(targetPos.below()), Vec3.ZERO, 90.0f, 0.0f, destination, null)
                            );
                        }
                    );
                } else {
                    BlockPos spawnPos = destination.getSharedSpawnPos();
                    // need to load chunk for heightmap
                    destination.loadChunksAsync(
                        spawnPos, 0,
                        ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGH,
                        (chunks) -> {
                            BlockPos adjustedSpawn = destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos);

                            // done
                            portalInfoCompletable.complete(
                                new PortalInfo(Vec3.atBottomCenterOf(adjustedSpawn), Vec3.ZERO, 90.0f, 0.0f, destination, null)
                            );
                        }
                    );
                }

                break;
            }
            // for the nether logic, we need to first load the chunks in radius to empty (so that POI is created)
            // then we can search for an existing portal using the POI routines
            // if we don't find a portal, then we bring the chunks in the create radius to full and
            // create it
            case NETHER: {
                // hoisted from the create fallback, so that we can avoid the sync load later if we need it
                BlockState originalPortalBlock = this.portalEntrancePos == null ? null : origin.getBlockStateIfLoaded(this.portalEntrancePos);
                Direction.Axis originalPortalDirection = originalPortalBlock == null ? Direction.Axis.X :
                    originalPortalBlock.getOptionalValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
                BlockUtil.FoundRectangle originalPortalRectangle =
                    originalPortalBlock == null || !originalPortalBlock.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                        ? null
                        : BlockUtil.getLargestRectangleAround(
                            this.portalEntrancePos, originalPortalDirection, 21, Direction.Axis.Y, 21,
                            (blockpos) -> {
                                return origin.getBlockStateFromEmptyChunkIfLoaded(blockpos) == originalPortalBlock;
                            }
                        );

                boolean destinationIsNether = destination.getTypeKey() == LevelStem.NETHER;

                int portalSearchRadius = origin.paperConfig().environment.portalSearchVanillaDimensionScaling && destinationIsNether ?
                    (int)(destination.paperConfig().environment.portalSearchRadius / destination.dimensionType().coordinateScale()) :
                    destination.paperConfig().environment.portalSearchRadius;
                int portalCreateRadius = destination.paperConfig().environment.portalCreateRadius;

                WorldBorder destinationBorder = destination.getWorldBorder();
                double dimensionScale = DimensionType.getTeleportationScale(origin.dimensionType(), destination.dimensionType());
                BlockPos targetPos = destination.getWorldBorder().clampToBounds(this.getX() * dimensionScale, this.getY(), this.getZ() * dimensionScale);

                ca.spottedleaf.concurrentutil.completable.Completable<BlockUtil.FoundRectangle> portalFound
                    = new ca.spottedleaf.concurrentutil.completable.Completable<>();

                // post portal find/create logic
                portalFound.addWaiter(
                    (BlockUtil.FoundRectangle portal, Throwable thr) -> {
                        // no portal could be created
                        if (portal == null) {
                            portalInfoCompletable.complete(
                                new PortalInfo(Vec3.atCenterOf(targetPos), Vec3.ZERO, 90.0f, 0.0f, destination, null)
                            );
                            return;
                        }

                        Vec3 relativePos = originalPortalRectangle == null ?
                            new Vec3(0.5, 0.0, 0.0) :
                            Entity.this.getRelativePortalPosition(originalPortalDirection, originalPortalRectangle);

                        portalInfoCompletable.complete(
                            PortalShape.createPortalInfo(
                                destination, portal, originalPortalDirection, relativePos,
                                Entity.this, Entity.this.getDeltaMovement(), Entity.this.getYRot(), Entity.this.getXRot(), null
                            )
                        );
                    }
                );

                // kick off search for existing portal or creation
                destination.loadChunksAsync(
                    // add 32 so that the final search for a portal frame doesn't load any chunks
                    targetPos, portalSearchRadius + 32,
                    net.minecraft.world.level.chunk.ChunkStatus.EMPTY,
                    ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGH,
                    (chunks) -> {
                        BlockUtil.FoundRectangle portal =
                            destination.getPortalForcer().findPortalAround(targetPos, destinationBorder, portalSearchRadius).orElse(null);
                        if (portal != null) {
                            portalFound.complete(portal);
                            return;
                        }

                        // add tickets so that we can re-search for a portal once the chunks are loaded
                        Long ticketId = Long.valueOf(CREATE_PORTAL_DOUBLE_CHECK.getAndIncrement());
                        for (net.minecraft.world.level.chunk.ChunkAccess chunk : chunks) {
                            destination.chunkSource.addTicketAtLevel(
                                TicketType.NETHER_PORTAL_DOUBLE_CHECK, chunk.getPos(),
                                io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                                ticketId
                            );
                        }

                        // no portal found - create one
                        destination.loadChunksAsync(
                            targetPos, portalCreateRadius + 32,
                            ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGH,
                            (chunks2) -> {
                                // don't need the tickets anymore
                                // note: we expect removeTicketsAtLevel to add an unknown ticket for us automatically
                                // if the ticket level were to decrease
                                for (net.minecraft.world.level.chunk.ChunkAccess chunk : chunks) {
                                    destination.chunkSource.removeTicketAtLevel(
                                        TicketType.NETHER_PORTAL_DOUBLE_CHECK, chunk.getPos(),
                                        io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                                        ticketId
                                    );
                                }

                                // when two entities portal at the same time, it is possible that both entities reach this
                                // part of the code - and create a double portal
                                // to fix this, we just issue another search to try and see if another entity created
                                // a portal nearby
                                BlockUtil.FoundRectangle existingTryAgain =
                                    destination.getPortalForcer().findPortalAround(targetPos, destinationBorder, portalSearchRadius).orElse(null);
                                if (existingTryAgain != null) {
                                    portalFound.complete(existingTryAgain);
                                    return;
                                }

                                // we do not have the correct entity reference here
                                BlockUtil.FoundRectangle createdPortal =
                                    destination.getPortalForcer().createPortal(targetPos, originalPortalDirection, null, portalCreateRadius).orElse(null);
                                // if it wasn't created, passing null is expected here
                                portalFound.complete(createdPortal);
                            }
                        );
                    }
                );
                break;
            }
            default: {
                throw new IllegalStateException("Unknown portal type " + type);
            }
        }
    }

    public boolean canPortalAsync(boolean considerPassengers) {
        return this.canPortalAsync(considerPassengers, false);
    }

    protected boolean canPortalAsync(boolean considerPassengers, boolean skipPassengerCheck) {
        if (considerPassengers) {
            if (!skipPassengerCheck && this.isPassenger()) {
                return false;
            }
        } else {
            if (this.isVehicle() || (!skipPassengerCheck && this.isPassenger())) {
                return false;
            }
        }
        this.getBukkitEntity(); // force bukkit entity to be created before TPing
        if (!this.canTeleportAsync() || !this.canChangeDimensions() || this.isOnPortalCooldown()) {
            return false;
        }
        if (considerPassengers) {
            for (Entity entity : this.passengers) {
                if (!entity.canPortalAsync(true, true)) {
                    return false;
                }
            }
        }

        return true;
    }

    protected void prePortalLogic(ServerLevel origin, ServerLevel destination, PortalType type) {

    }

    protected boolean portalToAsync(ServerLevel destination, boolean takePassengers,
                                    PortalType type, java.util.function.Consumer<Entity> teleportComplete) {
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot portal entity async");
        if (!this.canPortalAsync(takePassengers)) {
            return false;
        }

        Vec3 initialPosition = this.position();
        ChunkPos initialPositionChunk = new ChunkPos(
            io.papermc.paper.util.CoordinateUtils.getChunkX(initialPosition),
            io.papermc.paper.util.CoordinateUtils.getChunkZ(initialPosition)
        );

        // first, remove entity/passengers from world
        EntityTreeNode passengerTree = this.detachPassengers();
        List<EntityTreeNode> fullPassengerTree = passengerTree.getFullTree();
        ServerLevel originWorld = (ServerLevel)this.level;

        for (EntityTreeNode node : fullPassengerTree) {
            node.root.preChangeDimension();
            node.root.prePortalLogic(originWorld, destination, type);
        }

        for (EntityTreeNode node : fullPassengerTree) {
            // we will update pos/rot/speed later
            node.root = node.root.transformForAsyncTeleport(destination, null, null, null, null);
            // set portal cooldown
            node.root.setPortalCooldown();
        }

        // ensure the region is always ticking in case of a shutdown
        // otherwise, the shutdown will not be able to complete the shutdown as it requires a ticking region
        Long teleportHoldId = Long.valueOf(TELEPORT_HOLD_TICKET_GEN.getAndIncrement());
        originWorld.chunkSource.addTicketAtLevel(
            TicketType.TELEPORT_HOLD_TICKET, initialPositionChunk,
            io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
            teleportHoldId
        );

        ServerLevel.PendingTeleport beforeFindDestination = new ServerLevel.PendingTeleport(passengerTree, initialPosition);
        originWorld.pushPendingTeleport(beforeFindDestination);

        ca.spottedleaf.concurrentutil.completable.Completable<PortalInfo> portalInfoCompletable
            = new ca.spottedleaf.concurrentutil.completable.Completable<>();

        portalInfoCompletable.addWaiter((PortalInfo info, Throwable throwable) -> {
            if (!originWorld.removePendingTeleport(beforeFindDestination)) {
                // the shutdown thread has placed us back into the origin world at the original position
                // we just have to abandon this teleport to prevent duplication
                return;
            }
            originWorld.chunkSource.removeTicketAtLevel(
                TicketType.TELEPORT_HOLD_TICKET, initialPositionChunk,
                io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                teleportHoldId
            );
            // adjust passenger tree to final pos
            for (EntityTreeNode node : fullPassengerTree) {
                node.root.transform(info.pos, Float.valueOf(info.yRot), Float.valueOf(info.xRot), info.speed);
            }

            // place
            passengerTree.root.placeInAsync(
                originWorld, destination, Entity.TELEPORT_FLAG_LOAD_CHUNK | (takePassengers ? Entity.TELEPORT_FLAG_TELEPORT_PASSENGERS : 0L),
                passengerTree, teleportComplete
            );
        });


        passengerTree.root.findOrCreatePortalAsync(originWorld, destination, type, portalInfoCompletable);

        return true;
    }
    // Folia end - region threading

    @Nullable
    public Entity changeDimension(ServerLevel destination) {
        // CraftBukkit start
        return this.teleportTo(destination, null);
    }

    @Nullable
    public Entity teleportTo(ServerLevel worldserver, Vec3 location) {
        // Folia start - region threading
        if (true) {
            throw new UnsupportedOperationException("Must use teleportAsync while in region threading");
        }
        // Folia end - region threading
        // CraftBukkit end
        // Paper start - fix bad state entities causing dupes
        if (!this.isAlive() || !this.valid) {
            LOGGER.warn("Illegal Entity Teleport " + this + " to " + worldserver + ":" + location, new Throwable());
            return null;
        }
        // Paper end
        if (this.level() instanceof ServerLevel && !this.isRemoved()) {
            this.level().getProfiler().push("changeDimension");
            // CraftBukkit start
            // this.unRide();
            if (worldserver == null) {
                return null;
            }
            // CraftBukkit end
            this.level().getProfiler().push("reposition");
            PortalInfo shapedetectorshape = (location == null) ? this.findDimensionEntryPoint(worldserver) : new PortalInfo(new Vec3(location.x(), location.y(), location.z()), Vec3.ZERO, this.yRot, this.xRot, worldserver, null); // CraftBukkit

            if (shapedetectorshape == null) {
                return null;
            } else {
                // CraftBukkit start
                worldserver = shapedetectorshape.world;
                // Paper start - Call EntityPortalExitEvent
                CraftEntity bukkitEntity = this.getBukkitEntity();
                Vec3 position = shapedetectorshape.pos;
                float yaw = shapedetectorshape.yRot;
                float pitch = bukkitEntity.getLocation().getPitch(); // Keep entity pitch as per moveTo line below
                Vec3 velocity = shapedetectorshape.speed;
                org.bukkit.event.entity.EntityPortalExitEvent event = new org.bukkit.event.entity.EntityPortalExitEvent(bukkitEntity,
                    bukkitEntity.getLocation(), new Location(worldserver.getWorld(), position.x, position.y, position.z, yaw, pitch),
                    bukkitEntity.getVelocity(), org.bukkit.craftbukkit.util.CraftVector.toBukkit(shapedetectorshape.speed));
                event.callEvent();
                if (this.isRemoved()) {
                    return null;
                }

                if (!event.isCancelled() && event.getTo() != null) {
                    worldserver = ((CraftWorld) event.getTo().getWorld()).getHandle();
                    position = new Vec3(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
                    yaw = event.getTo().getYaw();
                    pitch = event.getTo().getPitch();
                    velocity = org.bukkit.craftbukkit.util.CraftVector.toNMS(event.getAfter());
                }
                // Paper end
                if (worldserver == this.level) {
                    // SPIGOT-6782: Just move the entity if a plugin changed the world to the one the entity is already in
                    this.moveTo(shapedetectorshape.pos.x, shapedetectorshape.pos.y, shapedetectorshape.pos.z, shapedetectorshape.yRot, shapedetectorshape.xRot);
                    this.setDeltaMovement(shapedetectorshape.speed);
                    return this;
                }
                this.unRide();
                // CraftBukkit end

                this.level().getProfiler().popPush("reloading");
                // Paper start - Change lead drop timing to prevent dupe
                if (this instanceof Mob) {
                    ((Mob) this).dropLeash(true, true); // Paper drop lead
                }
                // Paper end
                Entity entity = this.getType().create(worldserver);

                if (entity != null) {
                    entity.restoreFrom(this);
                    entity.moveTo(position.x, position.y, position.z, yaw, pitch); // Paper - use EntityPortalExitEvent values
                    entity.setDeltaMovement(velocity); // Paper - use EntityPortalExitEvent values
                    worldserver.addDuringTeleport(entity);
                    if (worldserver.getTypeKey() == LevelStem.END) { // CraftBukkit
                        ServerLevel.makeObsidianPlatform(worldserver, this); // CraftBukkit
                    }
                    // // CraftBukkit start - Forward the CraftEntity to the new entity // Paper - moved to Entity#restoreFrom
                    // this.getBukkitEntity().setHandle(entity);
                    // entity.bukkitEntity = this.getBukkitEntity();
                    // // CraftBukkit end
                }

                this.removeAfterChangingDimensions();
                this.level().getProfiler().pop();
                ((ServerLevel) this.level()).resetEmptyTime();
                worldserver.resetEmptyTime();
                this.level().getProfiler().pop();
                return entity;
            }
        } else {
            return null;
        }
    }

    // Folia start - region threading - move inventory clearing until after the dimension change
    protected void postRemoveAfterChangingDimensions() {

    }
    // Folia end - region threading - move inventory clearing until after the dimension change

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
    }

    @Nullable
    protected PortalInfo findDimensionEntryPoint(ServerLevel destination) {
        // CraftBukkit start
        if (destination == null) {
            return null;
        }
        boolean flag = this.level().getTypeKey() == LevelStem.END && destination.getTypeKey() == LevelStem.OVERWORLD; // fromEndToOverworld
        boolean flag1 = destination.getTypeKey() == LevelStem.END; // targetIsEnd
        // CraftBukkit end

        if (!flag && !flag1) {
            boolean flag2 = destination.getTypeKey() == LevelStem.NETHER; // CraftBukkit

            if (this.level().getTypeKey() != LevelStem.NETHER && !flag2) { // CraftBukkit
                return null;
            } else {
                WorldBorder worldborder = destination.getWorldBorder();
                double d0 = DimensionType.getTeleportationScale(this.level().dimensionType(), destination.dimensionType());
                BlockPos blockposition = worldborder.clampToBounds(this.getX() * d0, this.getY(), this.getZ() * d0);
                // CraftBukkit start
                // Paper start
                int portalSearchRadius = destination.paperConfig().environment.portalSearchRadius;
                if (level.paperConfig().environment.portalSearchVanillaDimensionScaling && flag2) { // == THE_NETHER
                    portalSearchRadius = (int) (portalSearchRadius / destination.dimensionType().coordinateScale());
                }
                // Paper end
                CraftPortalEvent event = this.callPortalEvent(this, destination, new Vec3(blockposition.getX(), blockposition.getY(), blockposition.getZ()), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL, portalSearchRadius, destination.paperConfig().environment.portalCreateRadius); // Paper start - configurable portal radius
                if (event == null) {
                    return null;
                }
                final ServerLevel worldserverFinal = destination = ((CraftWorld) event.getTo().getWorld()).getHandle();
                worldborder = worldserverFinal.getWorldBorder();
                blockposition = worldborder.clampToBounds(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());

                return (PortalInfo) this.getExitPortal(destination, blockposition, flag2, worldborder, event.getSearchRadius(), event.getCanCreatePortal(), event.getCreationRadius()).map((blockutil_rectangle) -> {
                    // CraftBukkit end
                    BlockState iblockdata = this.level().getBlockState(this.portalEntrancePos);
                    Direction.Axis enumdirection_enumaxis;
                    Vec3 vec3d;

                    if (iblockdata.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                        enumdirection_enumaxis = (Direction.Axis) iblockdata.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                        BlockUtil.FoundRectangle blockutil_rectangle1 = BlockUtil.getLargestRectangleAround(this.portalEntrancePos, enumdirection_enumaxis, 21, Direction.Axis.Y, 21, (blockposition1) -> {
                            return this.level().getBlockState(blockposition1) == iblockdata;
                        });

                        vec3d = this.getRelativePortalPosition(enumdirection_enumaxis, blockutil_rectangle1);
                    } else {
                        enumdirection_enumaxis = Direction.Axis.X;
                        vec3d = new Vec3(0.5D, 0.0D, 0.0D);
                    }

                    return PortalShape.createPortalInfo(worldserverFinal, blockutil_rectangle, enumdirection_enumaxis, vec3d, this, this.getDeltaMovement(), this.getYRot(), this.getXRot(), event); // CraftBukkit
                }).orElse(null); // CraftBukkit - decompile error
            }
        } else {
            BlockPos blockposition1;

            if (flag1) {
                blockposition1 = ServerLevel.END_SPAWN_POINT;
            } else {
                // Paper start - Ensure spawn chunk is always loaded before calculating Y coordinate
                destination.getChunkAt(destination.getSharedSpawnPos());
                // Paper end
                blockposition1 = destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destination.getSharedSpawnPos());
            }
            // CraftBukkit start
            CraftPortalEvent event = this.callPortalEvent(this, destination, new Vec3(blockposition1.getX() + 0.5D, blockposition1.getY(), blockposition1.getZ() + 0.5D), PlayerTeleportEvent.TeleportCause.END_PORTAL, 0, 0);
            if (event == null) {
                return null;
            }

            return new PortalInfo(new Vec3(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ()), this.getDeltaMovement(), this.getYRot(), this.getXRot(), ((CraftWorld) event.getTo().getWorld()).getHandle(), event);
            // CraftBukkit end
        }
    }

    protected Vec3 getRelativePortalPosition(Direction.Axis portalAxis, BlockUtil.FoundRectangle portalRect) {
        return PortalShape.getRelativePosition(portalRect, portalAxis, this.position(), this.getDimensions(this.getPose()));
    }

    // CraftBukkit start
    protected CraftPortalEvent callPortalEvent(Entity entity, ServerLevel exitWorldServer, Vec3 exitPosition, PlayerTeleportEvent.TeleportCause cause, int searchRadius, int creationRadius) {
        org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
        Location enter = bukkitEntity.getLocation();
        Location exit = CraftLocation.toBukkit(exitPosition, exitWorldServer.getWorld());

        // Paper start
        final org.bukkit.PortalType portalType = switch (cause) {
            case END_PORTAL -> org.bukkit.PortalType.ENDER;
            case NETHER_PORTAL -> org.bukkit.PortalType.NETHER;
            default -> org.bukkit.PortalType.CUSTOM;
        };
        EntityPortalEvent event = new EntityPortalEvent(bukkitEntity, enter, exit, searchRadius, portalType);
        // Paper end
        event.getEntity().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null || !entity.isAlive()) {
            return null;
        }
        return new CraftPortalEvent(event);
    }

    protected Optional<BlockUtil.FoundRectangle> getExitPortal(ServerLevel worldserver, BlockPos blockposition, boolean flag, WorldBorder worldborder, int searchRadius, boolean canCreatePortal, int createRadius) {
        return worldserver.getPortalForcer().findPortalAround(blockposition, worldborder, searchRadius);
        // CraftBukkit end
    }

    public boolean canChangeDimensions() {
        return !this.isPassenger() && !this.isVehicle() && isAlive() && valid; // Paper
    }

    public float getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState, float max) {
        return max;
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float explosionPower) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory section) {
        section.setDetail("Entity Type", () -> {
            ResourceLocation minecraftkey = EntityType.getKey(this.getType());

            return minecraftkey + " (" + this.getClass().getCanonicalName() + ")";
        });
        section.setDetail("Entity ID", (Object) this.id);
        section.setDetail("Entity Name", () -> {
            return this.getName().getString();
        });
        section.setDetail("Entity's Exact location", (Object) String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        section.setDetail("Entity's Block location", (Object) CrashReportCategory.formatLocation(this.level(), Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ())));
        Vec3 vec3d = this.getDeltaMovement();

        section.setDetail("Entity's Momentum", (Object) String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3d.x, vec3d.y, vec3d.z));
        section.setDetail("Entity's Passengers", () -> {
            return this.getPassengers().toString();
        });
        section.setDetail("Entity's Vehicle", () -> {
            return String.valueOf(this.getVehicle());
        });
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
        this.stringUUID = this.uuid.toString();
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return Entity.viewScale;
    }

    public static void setViewScale(double value) {
        Entity.viewScale = value;
    }

    @Override
    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName()).withStyle((chatmodifier) -> {
            return chatmodifier.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID());
        });
    }

    public void setCustomName(@Nullable Component name) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME, Optional.ofNullable(name));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return (Component) ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).orElse((Object) null);
    }

    @Override
    public boolean hasCustomName() {
        return ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).isPresent();
    }

    public void setCustomNameVisible(boolean visible) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME_VISIBLE, visible);
    }

    public boolean isCustomNameVisible() {
        return (Boolean) this.entityData.get(Entity.DATA_CUSTOM_NAME_VISIBLE);
    }

    public final void teleportToWithTicket(double destX, double destY, double destZ) {
        if (this.level() instanceof ServerLevel) {
            ChunkPos chunkcoordintpair = new ChunkPos(BlockPos.containing(destX, destY, destZ));

            ((ServerLevel) this.level()).getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkcoordintpair, 0, this.getId());
            this.level().getChunk(chunkcoordintpair.x, chunkcoordintpair.z);
            this.teleportTo(destX, destY, destZ);
        }
    }

    // CraftBukkit start
    public boolean teleportTo(ServerLevel worldserver, double d0, double d1, double d2, Set<RelativeMovement> set, float f, float f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return this.teleportTo(worldserver, d0, d1, d2, set, f, f1);
    }
    // CraftBukkit end

    public boolean teleportTo(ServerLevel world, double destX, double destY, double destZ, Set<RelativeMovement> flags, float yaw, float pitch) {
        float f2 = Mth.clamp(pitch, -90.0F, 90.0F);

        if (world == this.level()) {
            this.moveTo(destX, destY, destZ, yaw, f2);
            this.teleportPassengers();
            this.setYHeadRot(yaw);
        } else {
            this.unRide();
            Entity entity = this.getType().create(world);

            if (entity == null) {
                return false;
            }

            entity.restoreFrom(this);
            entity.moveTo(destX, destY, destZ, yaw, f2);
            entity.setYHeadRot(yaw);
            this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
            world.addDuringTeleport(entity);
        }

        return true;
    }

    public void dismountTo(double destX, double destY, double destZ) {
        this.teleportTo(destX, destY, destZ);
    }

    public void teleportTo(double destX, double destY, double destZ) {
        if (this.level() instanceof ServerLevel) {
            this.moveTo(destX, destY, destZ, this.getYRot(), this.getXRot());
            this.teleportPassengers();
        }
    }

    private void teleportPassengers() {
        this.getSelfAndPassengers().forEach((entity) -> {
            UnmodifiableIterator unmodifiableiterator = entity.passengers.iterator();

            while (unmodifiableiterator.hasNext()) {
                Entity entity1 = (Entity) unmodifiableiterator.next();

                entity.positionRider(entity1, Entity::moveTo);
            }

        });
    }

    public void teleportRelative(double offsetX, double offsetY, double offsetZ) {
        this.teleportTo(this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ);
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> dataEntries) {}

    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Entity.DATA_POSE.equals(data)) {
            this.refreshDimensions();
        }

    }

    /** @deprecated */
    @Deprecated
    protected void fixupDimensions() {
        net.minecraft.world.entity.Pose entitypose = this.getPose();
        EntityDimensions entitysize = this.getDimensions(entitypose);

        this.dimensions = entitysize;
        this.eyeHeight = this.getEyeHeight(entitypose, entitysize);
    }

    public void refreshDimensions() {
        EntityDimensions entitysize = this.dimensions;
        net.minecraft.world.entity.Pose entitypose = this.getPose();
        EntityDimensions entitysize1 = this.getDimensions(entitypose);

        this.dimensions = entitysize1;
        this.eyeHeight = this.getEyeHeight(entitypose, entitysize1);
        this.reapplyPosition();
        boolean flag = (double) entitysize1.width <= 4.0D && (double) entitysize1.height <= 4.0D;

        if (!this.level().isClientSide && !this.firstTick && !this.noPhysics && flag && (entitysize1.width > entitysize.width || entitysize1.height > entitysize.height) && !(this instanceof Player)) {
            Vec3 vec3d = this.position().add(0.0D, (double) entitysize.height / 2.0D, 0.0D);
            double d0 = (double) Math.max(0.0F, entitysize1.width - entitysize.width) + 1.0E-6D;
            double d1 = (double) Math.max(0.0F, entitysize1.height - entitysize.height) + 1.0E-6D;
            VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec3d, d0, d1, d0));

            this.level().findFreePosition(this, voxelshape, vec3d, (double) entitysize1.width, (double) entitysize1.height, (double) entitysize1.width).ifPresent((vec3d1) -> {
                this.setPos(vec3d1.add(0.0D, (double) (-entitysize1.height) / 2.0D, 0.0D));
            });
        }

    }

    public Direction getDirection() {
        return Direction.fromYRot((double) this.getYRot());
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer spectator) {
        return true;
    }

    @Override
    public final AABB getBoundingBox() {
        return this.bb;
    }

    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox();
    }

    public final void setBoundingBox(AABB boundingBox) {
        // CraftBukkit start - block invalid bounding boxes
        double minX = boundingBox.minX,
                minY = boundingBox.minY,
                minZ = boundingBox.minZ,
                maxX = boundingBox.maxX,
                maxY = boundingBox.maxY,
                maxZ = boundingBox.maxZ;
        double len = boundingBox.maxX - boundingBox.minX;
        if (len < 0) maxX = minX;
        if (len > 64) maxX = minX + 64.0;

        len = boundingBox.maxY - boundingBox.minY;
        if (len < 0) maxY = minY;
        if (len > 64) maxY = minY + 64.0;

        len = boundingBox.maxZ - boundingBox.minZ;
        if (len < 0) maxZ = minZ;
        if (len > 64) maxZ = minZ + 64.0;
        this.bb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        // CraftBukkit end
    }

    protected float getEyeHeight(net.minecraft.world.entity.Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.85F;
    }

    public float getEyeHeight(net.minecraft.world.entity.Pose pose) {
        return this.getEyeHeight(pose, this.getDimensions(pose));
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public Vec3 getLeashOffset(float tickDelta) {
        return this.getLeashOffset();
    }

    protected Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) this.getEyeHeight(), (double) (this.getBbWidth() * 0.4F));
    }

    public SlotAccess getSlot(int mappedIndex) {
        return SlotAccess.NULL;
    }

    @Override
    public void sendSystemMessage(Component message) {}

    public Level getCommandSenderWorld() {
        return this.level();
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level().getServer();
    }

    public InteractionResult interactAt(Player player, Vec3 hitPos, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion() {
        return false;
    }

    public void doEnchantDamageEffects(net.minecraft.world.entity.LivingEntity attacker, Entity target) {
        if (target instanceof net.minecraft.world.entity.LivingEntity) {
            EnchantmentHelper.doPostHurtEffects((net.minecraft.world.entity.LivingEntity) target, attacker);
        }

        EnchantmentHelper.doPostDamageEffects(attacker, target);
    }

    public void startSeenByPlayer(ServerPlayer player) {}

    public void stopSeenByPlayer(ServerPlayer player) {}

    public float rotate(Rotation rotation) {
        float f = Mth.wrapDegrees(this.getYRot());

        switch (rotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    public float mirror(Mirror mirror) {
        float f = Mth.wrapDegrees(this.getYRot());

        switch (mirror) {
            case FRONT_BACK:
                return -f;
            case LEFT_RIGHT:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    @Nullable
    public Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : (Entity) this.passengers.get(0);
    }

    public boolean hasPassenger(Entity passenger) {
        return this.passengers.contains(passenger);
    }

    public boolean hasPassenger(Predicate<Entity> predicate) {
        UnmodifiableIterator unmodifiableiterator = this.passengers.iterator();

        Entity entity;

        do {
            if (!unmodifiableiterator.hasNext()) {
                return false;
            }

            entity = (Entity) unmodifiableiterator.next();
        } while (!predicate.test(entity));

        return true;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        if (this.passengers.isEmpty()) { return Stream.of(); } // Paper
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    @Override
    public Stream<Entity> getSelfAndPassengers() {
        if (this.passengers.isEmpty()) { return Stream.of(this); } // Paper
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    @Override
    public Stream<Entity> getPassengersAndSelf() {
        if (this.passengers.isEmpty()) { return Stream.of(this); } // Paper
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        // Paper start - rewrite this method
        if (this.passengers.isEmpty()) { return ImmutableList.of(); }
        ImmutableList.Builder<Entity> indirectPassengers = ImmutableList.builder();
        for (Entity passenger : this.passengers) {
            indirectPassengers.add(passenger);
            indirectPassengers.addAll(passenger.getIndirectPassengers());
        }
        return indirectPassengers.build();
    }
    private Iterable<Entity> getIndirectPassengers_old() {
        // Paper end
        return () -> {
            return this.getIndirectPassengersStream().iterator();
        };
    }

    // Paper start - rewrite chunk system
    public boolean hasAnyPlayerPassengers() {
        // copied from below
        if (this.passengers.isEmpty()) { return false; }
        return this.getIndirectPassengersStream().anyMatch((entity) -> {
            return entity instanceof Player;
        });
    }
    // Paper end - rewrite chunk system

    public boolean hasExactlyOnePlayerPassenger() {
        if (this.passengers.isEmpty()) { return false; } // Paper
        return this.getIndirectPassengersStream().filter((entity) -> {
            return entity instanceof Player;
        }).count() == 1L;
    }

    public Entity getRootVehicle() {
        Entity entity;

        for (entity = this; entity.isPassenger(); entity = entity.getVehicle()) {
            ;
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity entity) {
        return this.getRootVehicle() == entity.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity passenger) {
        if (!passenger.isPassenger()) {
            return false;
        } else {
            Entity entity1 = passenger.getVehicle();

            return entity1 == this ? true : this.hasIndirectPassenger(entity1);
        }
    }

    public boolean isControlledByLocalInstance() {
        net.minecraft.world.entity.LivingEntity entityliving = this.getControllingPassenger();

        if (entityliving instanceof Player) {
            Player entityhuman = (Player) entityliving;

            return entityhuman.isLocalPlayer();
        } else {
            return this.isEffectiveAi();
        }
    }

    public boolean isEffectiveAi() {
        return !this.level().isClientSide;
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double vehicleWidth, double passengerWidth, float passengerYaw) {
        double d2 = (vehicleWidth + passengerWidth + 9.999999747378752E-6D) / 2.0D;
        float f1 = -Mth.sin(passengerYaw * 0.017453292F);
        float f2 = Mth.cos(passengerYaw * 0.017453292F);
        float f3 = Math.max(Math.abs(f1), Math.abs(f2));

        return new Vec3((double) f1 * d2 / (double) f3, 0.0D, (double) f2 * d2 / (double) f3);
    }

    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    @Nullable
    public Entity getControlledVehicle() {
        return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    public int getFireImmuneTicks() {
        return 1;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(this, this.position(), this.getRotationVector(), this.level() instanceof ServerLevel ? (ServerLevel) this.level() : null, this.getPermissionLevel(), this.getName().getString(), this.getDisplayName(), this.level().getServer(), this);
    }

    protected int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int permissionLevel) {
        return this.getPermissionLevel() >= permissionLevel;
    }

    @Override
    public boolean acceptsSuccess() {
        return this.level().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }

    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Vec3 target) {
        Vec3 vec3d1 = anchorPoint.apply(this);
        double d0 = target.x - vec3d1.x;
        double d1 = target.y - vec3d1.y;
        double d2 = target.z - vec3d1.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        this.setXRot(Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 57.2957763671875D))));
        this.setYRot(Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> tag, double speed) {
        if (this.touchingUnloadedChunk()) {
            return false;
        } else {
            AABB axisalignedbb = this.getBoundingBox().deflate(0.001D);
            int i = Mth.floor(axisalignedbb.minX);
            int j = Mth.ceil(axisalignedbb.maxX);
            int k = Mth.floor(axisalignedbb.minY);
            int l = Mth.ceil(axisalignedbb.maxY);
            int i1 = Mth.floor(axisalignedbb.minZ);
            int j1 = Mth.ceil(axisalignedbb.maxZ);
            double d1 = 0.0D;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3 vec3d = Vec3.ZERO;
            int k1 = 0;
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        blockposition_mutableblockposition.set(l1, i2, j2);
                        FluidState fluid = this.level().getFluidState(blockposition_mutableblockposition);

                        if (fluid.is(tag)) {
                            double d2 = (double) ((float) i2 + fluid.getHeight(this.level(), blockposition_mutableblockposition));

                            if (d2 >= axisalignedbb.minY) {
                                flag1 = true;
                                d1 = Math.max(d2 - axisalignedbb.minY, d1);
                                if (flag) {
                                    Vec3 vec3d1 = fluid.getFlow(this.level(), blockposition_mutableblockposition);

                                    if (d1 < 0.4D) {
                                        vec3d1 = vec3d1.scale(d1);
                                    }

                                    vec3d = vec3d.add(vec3d1);
                                    ++k1;
                                }
                                // CraftBukkit start - store last lava contact location
                                if (tag == FluidTags.LAVA) {
                                    this.lastLavaContact = blockposition_mutableblockposition.immutable();
                                }
                                // CraftBukkit end
                            }
                        }
                    }
                }
            }

            if (vec3d.length() > 0.0D) {
                if (k1 > 0) {
                    vec3d = vec3d.scale(1.0D / (double) k1);
                }

                if (!(this instanceof Player)) {
                    vec3d = vec3d.normalize();
                }

                Vec3 vec3d2 = this.getDeltaMovement();

                vec3d = vec3d.scale(speed * 1.0D);
                double d3 = 0.003D;

                if (Math.abs(vec3d2.x) < 0.003D && Math.abs(vec3d2.z) < 0.003D && vec3d.length() < 0.0045000000000000005D) {
                    vec3d = vec3d.normalize().scale(0.0045000000000000005D);
                }

                this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
            }

            this.fluidHeight.put(tag, d1);
            return flag1;
        }
    }

    public boolean touchingUnloadedChunk() {
        AABB axisalignedbb = this.getBoundingBox().inflate(1.0D);
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.minZ);
        int l = Mth.ceil(axisalignedbb.maxZ);

        return !this.level().hasChunksAt(i, k, j, l);
    }

    public double getFluidHeight(TagKey<Fluid> fluid) {
        return this.fluidHeight.getDouble(fluid);
    }

    public double getFluidJumpThreshold() {
        return (double) this.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
    }

    public final float getBbWidth() {
        return this.dimensions.width;
    }

    public final float getBbHeight() {
        return this.dimensions.height;
    }

    public float getNameTagOffsetY() {
        return this.getBbHeight() + 0.5F;
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return this.type.getDimensions();
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 trackingPosition() {
        return this.position();
    }

    @Override
    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    public BlockState getFeetBlockState() {
        if (this.feetBlockState == null) {
            this.feetBlockState = this.level().getBlockState(this.blockPosition());
        }

        return this.feetBlockState;
    }

    public ChunkPos chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 velocity) {
        synchronized (this.posLock) { // Paper
        this.deltaMovement = velocity;
        } // Paper
    }

    public void addDeltaMovement(Vec3 velocity) {
        this.setDeltaMovement(this.getDeltaMovement().add(velocity));
    }

    public void setDeltaMovement(double x, double y, double z) {
        this.setDeltaMovement(new Vec3(x, y, z));
    }

    public final int getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double widthScale) {
        return this.position.x + (double) this.getBbWidth() * widthScale;
    }

    public double getRandomX(double widthScale) {
        return this.getX((2.0D * this.random.nextDouble() - 1.0D) * widthScale);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double heightScale) {
        return this.position.y + (double) this.getBbHeight() * heightScale;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + (double) this.eyeHeight;
    }

    public final int getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double widthScale) {
        return this.position.z + (double) this.getBbWidth() * widthScale;
    }

    public double getRandomZ(double widthScale) {
        return this.getZ((2.0D * this.random.nextDouble() - 1.0D) * widthScale);
    }

    // Paper start - block invalid positions
    public static boolean checkPosition(Entity entity, double newX, double newY, double newZ) {
        if (Double.isFinite(newX) && Double.isFinite(newY) && Double.isFinite(newZ)) {
            return true;
        }

        String entityInfo = null;
        try {
            entityInfo = entity.toString();
        } catch (Exception ex) {
            entityInfo = "[Entity info unavailable] ";
        }
        LOGGER.error("New entity position is invalid! Tried to set invalid position (" + newX + "," + newY + "," + newZ + ") for entity " + entity.getClass().getName() + " located at " + entity.position + ", entity info: " + entityInfo, new Throwable());
        return false;
    }
    // Paper end - block invalid positions

    public final void setPosRaw(double x, double y, double z) {
        // Paper start
        this.setPosRaw(x, y, z, false);
    }
    public final void setPosRaw(double x, double y, double z, boolean forceBoundingBoxUpdate) {
        // Paper start - block invalid positions
        if (!checkPosition(this, x, y, z)) {
            return;
        }
        // Paper end - block invalid positions
        // Paper end
        // Paper start - rewrite chunk system
        if (this.updatingSectionStatus) {
            LOGGER.error("Refusing to update position for entity " + this + " to position " + new Vec3(x, y, z) + " since it is processing a section status update", new Throwable());
            return;
        }
        // Paper end - rewrite chunk system
        // Paper start - fix MC-4
        if (this instanceof ItemEntity) {
            if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.fixEntityPositionDesync) {
                // encode/decode from ClientboundMoveEntityPacket
                x = Mth.lfloor(x * 4096.0D) * (1 / 4096.0D);
                y = Mth.lfloor(y * 4096.0D) * (1 / 4096.0D);
                z = Mth.lfloor(z * 4096.0D) * (1 / 4096.0D);
            }
        }
        // Paper end - fix MC-4
        boolean posChanged = this.position.x != x || this.position.y != y || this.position.z != z; // Folia - region threading
        if (posChanged) { // Folia - region threading
            synchronized (this.posLock) { // Paper
            this.position = new Vec3(x, y, z);
            } // Paper
            int i = Mth.floor(x);
            int j = Mth.floor(y);
            int k = Mth.floor(z);

            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(i, j, k);
                this.feetBlockState = null;
                if (SectionPos.blockToSectionCoord(i) != this.chunkPosition.x || SectionPos.blockToSectionCoord(k) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkPos(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
        }

        // Paper start - never allow AABB to become desynced from position
        // hanging has its own special logic
        if (!(this instanceof net.minecraft.world.entity.decoration.HangingEntity) && (forceBoundingBoxUpdate || posChanged)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
        // Paper end
    }

    public void checkDespawn() {}

    public Vec3 getRopeHoldPosition(float delta) {
        return this.getPosition(delta).add(0.0D, (double) this.eyeHeight * 0.7D, 0.0D);
    }

    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        int i = packet.getId();
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.moveTo(d0, d1, d2);
        this.setXRot(packet.getXRot());
        this.setYRot(packet.getYRot());
        this.setId(i);
        this.setUUID(packet.getUUID());
    }

    @Nullable
    public ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean inPowderSnow) {
        this.isInPowderSnow = inPowderSnow;
    }

    public boolean canFreeze() {
        return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return (this.isInPowderSnow || this.wasInPowderSnow) && this.canFreeze();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    public void setYRot(float yaw) {
        if (!Float.isFinite(yaw)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yaw + ", discarding.");
        } else {
            this.yRot = yaw;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(float pitch) {
        if (!Float.isFinite(pitch)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + pitch + ", discarding.");
        } else {
            this.xRot = pitch;
        }
    }

    public boolean canSprint() {
        return false;
    }

    public float maxUpStep() {
        return this.maxUpStep;
    }

    public void setMaxUpStep(float stepHeight) {
        this.maxUpStep = stepHeight;
    }

    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    // Folia start - region threading
    public final boolean hasNullCallback() {
        return this.levelCallback == EntityInLevelCallback.NULL;
    }
    // Folia end - region threading

    @Nullable
    public Entity.RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    @Override
    public final void setRemoved(Entity.RemovalReason reason) {
        // Paper start - rewrite chunk system
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot remove entity off-main");
        if (!((ServerLevel)this.level).getEntityLookup().canRemoveEntity(this)) {
            LOGGER.warn("Entity " + this + " is currently prevented from being removed from the world since it is processing section status updates", new Throwable());
            return;
        }
        // Paper end - rewrite chunk system
        final boolean alreadyRemoved = this.removalReason != null;
        // Folia start - region threading
        this.preRemove(reason);
        // Folia end - region threading
        if (this.removalReason == null) {
            this.removalReason = reason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        if (reason != RemovalReason.UNLOADED_TO_CHUNK) this.getPassengers().forEach(Entity::stopRiding); // Paper - chunk system - don't adjust passenger state when unloading, it's just not safe (and messes with our logic in entity chunk unload)
        this.levelCallback.onRemove(reason);
        // Paper start - Folia schedulers
        if (!(this instanceof ServerPlayer) && reason != RemovalReason.CHANGED_DIMENSION && !alreadyRemoved) {
            // Players need to be special cased, because they are regularly removed from the world
            this.retireScheduler();
        }
        // Paper end - Folia schedulers
    }

    public void unsetRemoved() {
        this.removalReason = null;
    }

    // Folia start - region threading
    protected void preRemove(Entity.RemovalReason reason) {}
    // Folia end - region threading

    // Paper start - Folia schedulers
    /**
     * Invoked only when the entity is truly removed from the server, never to be added to any world.
     */
    public final void retireScheduler() {
        // we need to force create the bukkit entity so that the scheduler can be retired...
        this.getBukkitEntity().taskScheduler.retire();
    }
    // Paper end - Folia schedulers

    @Override
    public void setLevelCallback(EntityInLevelCallback changeListener) {
        this.levelCallback = changeListener;
    }

    @Override
    public boolean shouldBeSaved() {
        return this.removalReason != null && !this.removalReason.shouldSave() ? false : (this.isPassenger() ? false : !this.isVehicle() || !this.hasAnyPlayerPassengers()); // Paper - rewrite chunk system - it should check if the entity has ANY player passengers
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(Level world, BlockPos pos) {
        return true;
    }

    public Level level() {
        return this.level;
    }

    public void setLevel(Level world) {
        this.level = world;
    }

    public DamageSources damageSources() {
        return this.level().damageSources();
    }

    protected void lerpPositionAndRotationStep(int step, double x, double y, double z, double yaw, double pitch) {
        double d5 = 1.0D / (double) step;
        double d6 = Mth.lerp(d5, this.getX(), x);
        double d7 = Mth.lerp(d5, this.getY(), y);
        double d8 = Mth.lerp(d5, this.getZ(), z);
        float f = (float) Mth.rotLerp(d5, (double) this.getYRot(), yaw);
        float f1 = (float) Mth.lerp(d5, (double) this.getXRot(), pitch);

        this.setPos(d6, d7, d8);
        this.setRot(f, f1);
    }

    public static enum RemovalReason {

        KILLED(true, false), DISCARDED(true, false), UNLOADED_TO_CHUNK(false, true), UNLOADED_WITH_PLAYER(false, false), CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        private RemovalReason(boolean flag, boolean flag1) {
            this.destroy = flag;
            this.save = flag1;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }

    public static enum MovementEmission {

        NONE(false, false), SOUNDS(true, false), EVENTS(false, true), ALL(true, true);

        final boolean sounds;
        final boolean events;

        private MovementEmission(boolean flag, boolean flag1) {
            this.sounds = flag;
            this.events = flag1;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    @FunctionalInterface
    public interface MoveFunction {

        void accept(Entity entity, double x, double y, double z);
    }

    // Paper start
    public static int nextEntityId() {
        return ENTITY_COUNTER.incrementAndGet();
    }

    public boolean isTicking() {
        return ((net.minecraft.server.level.ServerChunkCache) level.getChunkSource()).isPositionTicking(this);
    }
    // Paper end
}
