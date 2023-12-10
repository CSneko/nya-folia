package net.minecraft.world.entity.vehicle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.util.Pair;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.util.Vector;
// CraftBukkit end

public abstract class AbstractMinecart extends Entity {

    private static final float LOWERED_PASSENGER_ATTACHMENT_Y = 0.0F;
    private static final float PASSENGER_ATTACHMENT_Y = 0.1875F;
    private static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_BLOCK = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_OFFSET = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ID_CUSTOM_DISPLAY = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.BOOLEAN);
    private static final ImmutableMap<Pose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(Pose.STANDING, ImmutableList.of(0, 1, -1), Pose.CROUCHING, ImmutableList.of(0, 1, -1), Pose.SWIMMING, ImmutableList.of(0, 1));
    protected static final float WATER_SLOWDOWN_FACTOR = 0.95F;
    private boolean flipped;
    private boolean onRails;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private Vec3 targetDeltaMovement;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> EXITS = (Map) Util.make(Maps.newEnumMap(RailShape.class), (enummap) -> {
        Vec3i baseblockposition = Direction.WEST.getNormal();
        Vec3i baseblockposition1 = Direction.EAST.getNormal();
        Vec3i baseblockposition2 = Direction.NORTH.getNormal();
        Vec3i baseblockposition3 = Direction.SOUTH.getNormal();
        Vec3i baseblockposition4 = baseblockposition.below();
        Vec3i baseblockposition5 = baseblockposition1.below();
        Vec3i baseblockposition6 = baseblockposition2.below();
        Vec3i baseblockposition7 = baseblockposition3.below();

        enummap.put(RailShape.NORTH_SOUTH, Pair.of(baseblockposition2, baseblockposition3));
        enummap.put(RailShape.EAST_WEST, Pair.of(baseblockposition, baseblockposition1));
        enummap.put(RailShape.ASCENDING_EAST, Pair.of(baseblockposition4, baseblockposition1));
        enummap.put(RailShape.ASCENDING_WEST, Pair.of(baseblockposition, baseblockposition5));
        enummap.put(RailShape.ASCENDING_NORTH, Pair.of(baseblockposition2, baseblockposition7));
        enummap.put(RailShape.ASCENDING_SOUTH, Pair.of(baseblockposition6, baseblockposition3));
        enummap.put(RailShape.SOUTH_EAST, Pair.of(baseblockposition3, baseblockposition1));
        enummap.put(RailShape.SOUTH_WEST, Pair.of(baseblockposition3, baseblockposition));
        enummap.put(RailShape.NORTH_WEST, Pair.of(baseblockposition2, baseblockposition));
        enummap.put(RailShape.NORTH_EAST, Pair.of(baseblockposition2, baseblockposition1));
    });

    // CraftBukkit start
    public boolean slowWhenEmpty = true;
    private double derailedX = 0.5;
    private double derailedY = 0.5;
    private double derailedZ = 0.5;
    private double flyingX = 0.949999988079071D; // Paper - restore vanilla precision
    private double flyingY = 0.949999988079071D; // Paper - restore vanilla precision
    private double flyingZ = 0.949999988079071D; // Paper - restore vanilla precision
    public double maxSpeed = 0.4D;
    // CraftBukkit end

    protected AbstractMinecart(EntityType<?> type, Level world) {
        super(type, world);
        this.targetDeltaMovement = Vec3.ZERO;
        this.blocksBuilding = true;
    }

    protected AbstractMinecart(EntityType<?> type, Level world, double x, double y, double z) {
        this(type, world);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public static AbstractMinecart createMinecart(Level world, double x, double y, double z, AbstractMinecart.Type type) {
        return (AbstractMinecart) (type == AbstractMinecart.Type.CHEST ? new MinecartChest(world, x, y, z) : (type == AbstractMinecart.Type.FURNACE ? new MinecartFurnace(world, x, y, z) : (type == AbstractMinecart.Type.TNT ? new MinecartTNT(world, x, y, z) : (type == AbstractMinecart.Type.SPAWNER ? new MinecartSpawner(world, x, y, z) : (type == AbstractMinecart.Type.HOPPER ? new MinecartHopper(world, x, y, z) : (type == AbstractMinecart.Type.COMMAND_BLOCK ? new MinecartCommandBlock(world, x, y, z) : new Minecart(world, x, y, z)))))));
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(AbstractMinecart.DATA_ID_HURT, 0);
        this.entityData.define(AbstractMinecart.DATA_ID_HURTDIR, 1);
        this.entityData.define(AbstractMinecart.DATA_ID_DAMAGE, 0.0F);
        this.entityData.define(AbstractMinecart.DATA_ID_DISPLAY_BLOCK, Block.getId(Blocks.AIR.defaultBlockState()));
        this.entityData.define(AbstractMinecart.DATA_ID_DISPLAY_OFFSET, 6);
        this.entityData.define(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY, false);
    }

    @Override
    public boolean canCollideWith(Entity other) {
        // Paper start - fixed VehicleEntityCollisionEvent not called when colliding with player
        boolean collides = Boat.canVehicleCollide(this, other);
        if (!collides) {
            return false;
        }
        org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent((org.bukkit.entity.Vehicle) getBukkitEntity(), other.getBukkitEntity());

        return collisionEvent.callEvent();
        // Paper end
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper
        return true;
    }

    @Override
    protected Vec3 getRelativePortalPosition(Direction.Axis portalAxis, BlockUtil.FoundRectangle portalRect) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(portalAxis, portalRect));
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        boolean flag = passenger instanceof Villager || passenger instanceof WanderingTrader;

        return new Vector3f(0.0F, flag ? 0.0F : 0.1875F, 0.0F);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction enumdirection = this.getMotionDirection();

        if (enumdirection.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(passenger);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(enumdirection);
            BlockPos blockposition = this.blockPosition();
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
            ImmutableList<Pose> immutablelist = passenger.getDismountPoses();
            UnmodifiableIterator unmodifiableiterator = immutablelist.iterator();

            while (unmodifiableiterator.hasNext()) {
                Pose entitypose = (Pose) unmodifiableiterator.next();
                EntityDimensions entitysize = passenger.getDimensions(entitypose);
                float f = Math.min(entitysize.width, 1.0F) / 2.0F;
                UnmodifiableIterator unmodifiableiterator1 = ((ImmutableList) AbstractMinecart.POSE_DISMOUNT_HEIGHTS.get(entitypose)).iterator();

                while (unmodifiableiterator1.hasNext()) {
                    int i = (Integer) unmodifiableiterator1.next();
                    int[][] aint1 = aint;
                    int j = aint.length;

                    for (int k = 0; k < j; ++k) {
                        int[] aint2 = aint1[k];

                        blockposition_mutableblockposition.set(blockposition.getX() + aint2[0], blockposition.getY() + i, blockposition.getZ() + aint2[1]);
                        double d0 = this.level().getBlockFloorHeight(DismountHelper.nonClimbableShape(this.level(), blockposition_mutableblockposition), () -> {
                            return DismountHelper.nonClimbableShape(this.level(), blockposition_mutableblockposition.below());
                        });

                        if (DismountHelper.isBlockFloorValid(d0)) {
                            AABB axisalignedbb = new AABB((double) (-f), 0.0D, (double) (-f), (double) f, (double) entitysize.height, (double) f);
                            Vec3 vec3d = Vec3.upFromBottomCenterOf(blockposition_mutableblockposition, d0);

                            if (DismountHelper.canDismountTo(this.level(), passenger, axisalignedbb.move(vec3d))) {
                                passenger.setPose(entitypose);
                                return vec3d;
                            }
                        }
                    }
                }
            }

            double d1 = this.getBoundingBox().maxY;

            blockposition_mutableblockposition.set((double) blockposition.getX(), d1, (double) blockposition.getZ());
            UnmodifiableIterator unmodifiableiterator2 = immutablelist.iterator();

            while (unmodifiableiterator2.hasNext()) {
                Pose entitypose1 = (Pose) unmodifiableiterator2.next();
                double d2 = (double) passenger.getDimensions(entitypose1).height;
                int l = Mth.ceil(d1 - (double) blockposition_mutableblockposition.getY() + d2);
                double d3 = DismountHelper.findCeilingFrom(blockposition_mutableblockposition, l, (blockposition1) -> {
                    return this.level().getBlockState(blockposition1).getCollisionShape(this.level(), blockposition1);
                });

                if (d1 + d2 <= d3) {
                    passenger.setPose(entitypose1);
                    break;
                }
            }

            return super.getDismountLocationForPassenger(passenger);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (this.isInvulnerableTo(source)) {
                return false;
            } else {
                // CraftBukkit start - fire VehicleDamageEvent
                Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                org.bukkit.entity.Entity passenger = (source.getEntity() == null) ? null : source.getEntity().getBukkitEntity();

                VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, amount);
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return false;
                }

                amount = (float) event.getDamage();
                // CraftBukkit end
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.markHurt();
                this.setDamage(this.getDamage() + amount * 10.0F);
                this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
                boolean flag = source.getEntity() instanceof Player && ((Player) source.getEntity()).getAbilities().instabuild;

                if (flag || this.getDamage() > 40.0F) {
                    // CraftBukkit start
                    VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
                    this.level().getCraftServer().getPluginManager().callEvent(destroyEvent);

                    if (destroyEvent.isCancelled()) {
                        this.setDamage(40); // Maximize damage so this doesn't get triggered again right away
                        return true;
                    }
                    // CraftBukkit end
                    this.ejectPassengers();
                    if (flag && !this.hasCustomName()) {
                        this.discard();
                    } else {
                        this.destroy(source);
                    }
                }

                return true;
            }
        } else {
            return true;
        }
    }

    @Override
    protected float getBlockSpeedFactor() {
        BlockState iblockdata = this.level().getBlockState(this.blockPosition());

        return iblockdata.is(BlockTags.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    public void destroy(DamageSource damageSource) {
        this.kill();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack itemstack = new ItemStack(this.getDropItem());

            if (this.hasCustomName()) {
                itemstack.setHoverName(this.getCustomName());
            }

            this.spawnAtLocation(itemstack);
        }

    }

    abstract Item getDropItem();

    @Override
    public void animateHurt(float yaw) {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    private static Pair<Vec3i, Vec3i> exits(RailShape shape) {
        return (Pair) AbstractMinecart.EXITS.get(shape);
    }

    @Override
    public Direction getMotionDirection() {
        return this.flipped ? this.getDirection().getOpposite().getClockWise() : this.getDirection().getClockWise();
    }

    @Override
    public void tick() {
        // CraftBukkit start
        double prevX = this.getX();
        double prevY = this.getY();
        double prevZ = this.getZ();
        float prevYaw = this.getYRot();
        float prevPitch = this.getXRot();
        // CraftBukkit end

        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        // this.handleNetherPortal(); // CraftBukkit - handled in postTick
        if (this.level().isClientSide) {
            if (this.lerpSteps > 0) {
                this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
                --this.lerpSteps;
            } else {
                this.reapplyPosition();
                this.setRot(this.getYRot(), this.getXRot());
            }

        } else {
            if (!this.isNoGravity()) {
                double d0 = this.isInWater() ? -0.005D : -0.04D;

                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d0, 0.0D));
            }

            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY());
            int k = Mth.floor(this.getZ());

            if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
                --j;
            }

            BlockPos blockposition = new BlockPos(i, j, k);
            BlockState iblockdata = this.level().getBlockState(blockposition);

            this.onRails = BaseRailBlock.isRail(iblockdata);
            if (this.onRails) {
                this.moveAlongTrack(blockposition, iblockdata);
                if (iblockdata.is(Blocks.ACTIVATOR_RAIL)) {
                    this.activateMinecart(i, j, k, (Boolean) iblockdata.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.comeOffTrack();
            }

            this.checkInsideBlocks();
            this.setXRot(0.0F);
            double d1 = this.xo - this.getX();
            double d2 = this.zo - this.getZ();

            if (d1 * d1 + d2 * d2 > 0.001D) {
                this.setYRot((float) (Mth.atan2(d2, d1) * 180.0D / 3.141592653589793D));
                if (this.flipped) {
                    this.setYRot(this.getYRot() + 180.0F);
                }
            }

            double d3 = (double) Mth.wrapDegrees(this.getYRot() - this.yRotO);

            if (d3 < -170.0D || d3 >= 170.0D) {
                this.setYRot(this.getYRot() + 180.0F);
                this.flipped = !this.flipped;
            }

            this.setRot(this.getYRot(), this.getXRot());
            // CraftBukkit start
            org.bukkit.World bworld = this.level().getWorld();
            Location from = new Location(bworld, prevX, prevY, prevZ, prevYaw, prevPitch);
            Location to = CraftLocation.toBukkit(this.position(), bworld, this.getYRot(), this.getXRot());
            Vehicle vehicle = (Vehicle) this.getBukkitEntity();

            this.level().getCraftServer().getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleUpdateEvent(vehicle));

            if (!from.equals(to)) {
                this.level().getCraftServer().getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleMoveEvent(vehicle, from, to));
            }
            // CraftBukkit end
            if (this.getMinecartType() == AbstractMinecart.Type.RIDEABLE && this.getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
                List<Entity> list = this.level().getEntities((Entity) this, this.getBoundingBox().inflate(0.20000000298023224D, 0.0D, 0.20000000298023224D), EntitySelector.pushableBy(this));

                if (!list.isEmpty()) {
                    Iterator iterator = list.iterator();

                    while (iterator.hasNext()) {
                        Entity entity = (Entity) iterator.next();

                        if (!(entity instanceof Player) && !(entity instanceof IronGolem) && !(entity instanceof AbstractMinecart) && !this.isVehicle() && !entity.isPassenger()) {
                            // CraftBukkit start
                            VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(vehicle, entity.getBukkitEntity());
                            this.level().getCraftServer().getPluginManager().callEvent(collisionEvent);

                            if (collisionEvent.isCancelled()) {
                                continue;
                            }
                            // CraftBukkit end
                            entity.startRiding(this);
                        } else {
                            // CraftBukkit start
                            if (!this.isPassengerOfSameVehicle(entity)) {
                                VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(vehicle, entity.getBukkitEntity());
                                this.level().getCraftServer().getPluginManager().callEvent(collisionEvent);

                                if (collisionEvent.isCancelled()) {
                                    continue;
                                }
                            }
                            // CraftBukkit end
                            entity.push(this);
                        }
                    }
                }
            } else {
                Iterator iterator1 = this.level().getEntities(this, this.getBoundingBox().inflate(0.20000000298023224D, 0.0D, 0.20000000298023224D)).iterator();

                while (iterator1.hasNext()) {
                    Entity entity1 = (Entity) iterator1.next();

                    if (!this.hasPassenger(entity1) && entity1.isPushable() && entity1 instanceof AbstractMinecart) {
                        // CraftBukkit start
                        VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent(vehicle, entity1.getBukkitEntity());
                        this.level().getCraftServer().getPluginManager().callEvent(collisionEvent);

                        if (collisionEvent.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end
                        entity1.push(this);
                    }
                }
            }

            this.updateInWaterStateAndDoFluidPushing();
            if (this.isInLava()) {
                this.lavaHurt();
                this.fallDistance *= 0.5F;
            }

            this.firstTick = false;
        }
    }

    protected double getMaxSpeed() {
        return (this.isInWater() ? this.maxSpeed / 2.0D: this.maxSpeed); // CraftBukkit
    }

    public void activateMinecart(int x, int y, int z, boolean powered) {}

    protected void comeOffTrack() {
        double d0 = this.getMaxSpeed();
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(Mth.clamp(vec3d.x, -d0, d0), vec3d.y, Mth.clamp(vec3d.z, -d0, d0));
        if (this.onGround()) {
            // CraftBukkit start - replace magic numbers with our variables
            this.setDeltaMovement(new Vec3(this.getDeltaMovement().x * this.derailedX, this.getDeltaMovement().y * this.derailedY, this.getDeltaMovement().z * this.derailedZ));
            // CraftBukkit end
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround()) {
            // CraftBukkit start - replace magic numbers with our variables
            this.setDeltaMovement(new Vec3(this.getDeltaMovement().x * this.flyingX, this.getDeltaMovement().y * this.flyingY, this.getDeltaMovement().z * this.flyingZ));
            // CraftBukkit end
        }

    }

    protected void moveAlongTrack(BlockPos pos, BlockState state) {
        this.resetFallDistance();
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        Vec3 vec3d = this.getPos(d0, d1, d2);

        d1 = (double) pos.getY();
        boolean flag = false;
        boolean flag1 = false;

        if (state.is(Blocks.POWERED_RAIL)) {
            flag = (Boolean) state.getValue(PoweredRailBlock.POWERED);
            flag1 = !flag;
        }

        double d3 = 0.0078125D;

        if (this.isInWater()) {
            d3 *= 0.2D;
        }

        Vec3 vec3d1 = this.getDeltaMovement();
        RailShape blockpropertytrackposition = (RailShape) state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty());

        switch (blockpropertytrackposition) {
            case ASCENDING_EAST:
                this.setDeltaMovement(vec3d1.add(-d3, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_WEST:
                this.setDeltaMovement(vec3d1.add(d3, 0.0D, 0.0D));
                ++d1;
                break;
            case ASCENDING_NORTH:
                this.setDeltaMovement(vec3d1.add(0.0D, 0.0D, d3));
                ++d1;
                break;
            case ASCENDING_SOUTH:
                this.setDeltaMovement(vec3d1.add(0.0D, 0.0D, -d3));
                ++d1;
        }

        vec3d1 = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(blockpropertytrackposition);
        Vec3i baseblockposition = (Vec3i) pair.getFirst();
        Vec3i baseblockposition1 = (Vec3i) pair.getSecond();
        double d4 = (double) (baseblockposition1.getX() - baseblockposition.getX());
        double d5 = (double) (baseblockposition1.getZ() - baseblockposition.getZ());
        double d6 = Math.sqrt(d4 * d4 + d5 * d5);
        double d7 = vec3d1.x * d4 + vec3d1.z * d5;

        if (d7 < 0.0D) {
            d4 = -d4;
            d5 = -d5;
        }

        double d8 = Math.min(2.0D, vec3d1.horizontalDistance());

        vec3d1 = new Vec3(d8 * d4 / d6, vec3d1.y, d8 * d5 / d6);
        this.setDeltaMovement(vec3d1);
        Entity entity = this.getFirstPassenger();

        if (entity instanceof Player) {
            Vec3 vec3d2 = entity.getDeltaMovement();
            double d9 = vec3d2.horizontalDistanceSqr();
            double d10 = this.getDeltaMovement().horizontalDistanceSqr();

            if (d9 > 1.0E-4D && d10 < 0.01D) {
                this.setDeltaMovement(this.getDeltaMovement().add(vec3d2.x * 0.1D, 0.0D, vec3d2.z * 0.1D));
                flag1 = false;
            }
        }

        double d11;

        if (flag1) {
            d11 = this.getDeltaMovement().horizontalDistance();
            if (d11 < 0.03D) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, 0.0D, 0.5D));
            }
        }

        d11 = (double) pos.getX() + 0.5D + (double) baseblockposition.getX() * 0.5D;
        double d12 = (double) pos.getZ() + 0.5D + (double) baseblockposition.getZ() * 0.5D;
        double d13 = (double) pos.getX() + 0.5D + (double) baseblockposition1.getX() * 0.5D;
        double d14 = (double) pos.getZ() + 0.5D + (double) baseblockposition1.getZ() * 0.5D;

        d4 = d13 - d11;
        d5 = d14 - d12;
        double d15;
        double d16;
        double d17;

        if (d4 == 0.0D) {
            d15 = d2 - (double) pos.getZ();
        } else if (d5 == 0.0D) {
            d15 = d0 - (double) pos.getX();
        } else {
            d16 = d0 - d11;
            d17 = d2 - d12;
            d15 = (d16 * d4 + d17 * d5) * 2.0D;
        }

        d0 = d11 + d4 * d15;
        d2 = d12 + d5 * d15;
        this.setPos(d0, d1, d2);
        d16 = this.isVehicle() ? 0.75D : 1.0D;
        d17 = this.getMaxSpeed();
        vec3d1 = this.getDeltaMovement();
        this.move(MoverType.SELF, new Vec3(Mth.clamp(d16 * vec3d1.x, -d17, d17), 0.0D, Mth.clamp(d16 * vec3d1.z, -d17, d17)));
        if (baseblockposition.getY() != 0 && Mth.floor(this.getX()) - pos.getX() == baseblockposition.getX() && Mth.floor(this.getZ()) - pos.getZ() == baseblockposition.getZ()) {
            this.setPos(this.getX(), this.getY() + (double) baseblockposition.getY(), this.getZ());
        } else if (baseblockposition1.getY() != 0 && Mth.floor(this.getX()) - pos.getX() == baseblockposition1.getX() && Mth.floor(this.getZ()) - pos.getZ() == baseblockposition1.getZ()) {
            this.setPos(this.getX(), this.getY() + (double) baseblockposition1.getY(), this.getZ());
        }

        this.applyNaturalSlowdown();
        Vec3 vec3d3 = this.getPos(this.getX(), this.getY(), this.getZ());
        Vec3 vec3d4;
        double d18;

        if (vec3d3 != null && vec3d != null) {
            double d19 = (vec3d.y - vec3d3.y) * 0.05D;

            vec3d4 = this.getDeltaMovement();
            d18 = vec3d4.horizontalDistance();
            if (d18 > 0.0D) {
                this.setDeltaMovement(vec3d4.multiply((d18 + d19) / d18, 1.0D, (d18 + d19) / d18));
            }

            this.setPos(this.getX(), vec3d3.y, this.getZ());
        }

        int i = Mth.floor(this.getX());
        int j = Mth.floor(this.getZ());

        if (i != pos.getX() || j != pos.getZ()) {
            vec3d4 = this.getDeltaMovement();
            d18 = vec3d4.horizontalDistance();
            this.setDeltaMovement(d18 * (double) (i - pos.getX()), vec3d4.y, d18 * (double) (j - pos.getZ()));
        }

        if (flag) {
            vec3d4 = this.getDeltaMovement();
            d18 = vec3d4.horizontalDistance();
            if (d18 > 0.01D) {
                double d20 = 0.06D;

                this.setDeltaMovement(vec3d4.add(vec3d4.x / d18 * 0.06D, 0.0D, vec3d4.z / d18 * 0.06D));
            } else {
                Vec3 vec3d5 = this.getDeltaMovement();
                double d21 = vec3d5.x;
                double d22 = vec3d5.z;

                if (blockpropertytrackposition == RailShape.EAST_WEST) {
                    if (this.isRedstoneConductor(pos.west())) {
                        d21 = 0.02D;
                    } else if (this.isRedstoneConductor(pos.east())) {
                        d21 = -0.02D;
                    }
                } else {
                    if (blockpropertytrackposition != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.isRedstoneConductor(pos.north())) {
                        d22 = 0.02D;
                    } else if (this.isRedstoneConductor(pos.south())) {
                        d22 = -0.02D;
                    }
                }

                this.setDeltaMovement(d21, vec3d5.y, d22);
            }
        }

    }

    @Override
    public boolean isOnRails() {
        return this.onRails;
    }

    private boolean isRedstoneConductor(BlockPos pos) {
        return this.level().getBlockState(pos).isRedstoneConductor(this.level(), pos);
    }

    protected void applyNaturalSlowdown() {
        double d0 = this.isVehicle() || !this.slowWhenEmpty ? 0.997D : 0.96D; // CraftBukkit - add !this.slowWhenEmpty
        Vec3 vec3d = this.getDeltaMovement();

        vec3d = vec3d.multiply(d0, 0.0D, d0);
        if (this.isInWater()) {
            vec3d = vec3d.scale(0.949999988079071D);
        }

        this.setDeltaMovement(vec3d);
    }

    @Nullable
    public Vec3 getPosOffs(double x, double y, double z, double offset) {
        int i = Mth.floor(x);
        int j = Mth.floor(y);
        int k = Mth.floor(z);

        if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            --j;
        }

        BlockState iblockdata = this.level().getBlockState(new BlockPos(i, j, k));

        if (BaseRailBlock.isRail(iblockdata)) {
            RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty());

            y = (double) j;
            if (blockpropertytrackposition.isAscending()) {
                y = (double) (j + 1);
            }

            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(blockpropertytrackposition);
            Vec3i baseblockposition = (Vec3i) pair.getFirst();
            Vec3i baseblockposition1 = (Vec3i) pair.getSecond();
            double d4 = (double) (baseblockposition1.getX() - baseblockposition.getX());
            double d5 = (double) (baseblockposition1.getZ() - baseblockposition.getZ());
            double d6 = Math.sqrt(d4 * d4 + d5 * d5);

            d4 /= d6;
            d5 /= d6;
            x += d4 * offset;
            z += d5 * offset;
            if (baseblockposition.getY() != 0 && Mth.floor(x) - i == baseblockposition.getX() && Mth.floor(z) - k == baseblockposition.getZ()) {
                y += (double) baseblockposition.getY();
            } else if (baseblockposition1.getY() != 0 && Mth.floor(x) - i == baseblockposition1.getX() && Mth.floor(z) - k == baseblockposition1.getZ()) {
                y += (double) baseblockposition1.getY();
            }

            return this.getPos(x, y, z);
        } else {
            return null;
        }
    }

    @Nullable
    public Vec3 getPos(double x, double y, double z) {
        int i = Mth.floor(x);
        int j = Mth.floor(y);
        int k = Mth.floor(z);

        if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            --j;
        }

        BlockState iblockdata = this.level().getBlockState(new BlockPos(i, j, k));

        if (BaseRailBlock.isRail(iblockdata)) {
            RailShape blockpropertytrackposition = (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(blockpropertytrackposition);
            Vec3i baseblockposition = (Vec3i) pair.getFirst();
            Vec3i baseblockposition1 = (Vec3i) pair.getSecond();
            double d3 = (double) i + 0.5D + (double) baseblockposition.getX() * 0.5D;
            double d4 = (double) j + 0.0625D + (double) baseblockposition.getY() * 0.5D;
            double d5 = (double) k + 0.5D + (double) baseblockposition.getZ() * 0.5D;
            double d6 = (double) i + 0.5D + (double) baseblockposition1.getX() * 0.5D;
            double d7 = (double) j + 0.0625D + (double) baseblockposition1.getY() * 0.5D;
            double d8 = (double) k + 0.5D + (double) baseblockposition1.getZ() * 0.5D;
            double d9 = d6 - d3;
            double d10 = (d7 - d4) * 2.0D;
            double d11 = d8 - d5;
            double d12;

            if (d9 == 0.0D) {
                d12 = z - (double) k;
            } else if (d11 == 0.0D) {
                d12 = x - (double) i;
            } else {
                double d13 = x - d3;
                double d14 = z - d5;

                d12 = (d13 * d9 + d14 * d11) * 2.0D;
            }

            x = d3 + d9 * d12;
            y = d4 + d10 * d12;
            z = d5 + d11 * d12;
            if (d10 < 0.0D) {
                ++y;
            } else if (d10 > 0.0D) {
                y += 0.5D;
            }

            return new Vec3(x, y, z);
        } else {
            return null;
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB axisalignedbb = this.getBoundingBox();

        return this.hasCustomDisplay() ? axisalignedbb.inflate((double) Math.abs(this.getDisplayOffset()) / 16.0D) : axisalignedbb;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.getBoolean("CustomDisplayTile")) {
            this.setDisplayBlockState(NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), nbt.getCompound("DisplayState")));
            this.setDisplayOffset(nbt.getInt("DisplayOffset"));
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (this.hasCustomDisplay()) {
            nbt.putBoolean("CustomDisplayTile", true);
            nbt.put("DisplayState", NbtUtils.writeBlockState(this.getDisplayBlockState()));
            nbt.putInt("DisplayOffset", this.getDisplayOffset());
        }

    }

    @Override
    public void push(Entity entity) {
        if (!this.level().isClientSide) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (!this.level().paperConfig().collisions.allowVehicleCollisions && this.level().paperConfig().collisions.onlyPlayersCollide && !(entity instanceof Player)) return; // Paper
                if (!this.hasPassenger(entity)) {
                    // CraftBukkit start
                    VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent((Vehicle) this.getBukkitEntity(), entity.getBukkitEntity());
                    this.level().getCraftServer().getPluginManager().callEvent(collisionEvent);

                    if (collisionEvent.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    double d0 = entity.getX() - this.getX();
                    double d1 = entity.getZ() - this.getZ();
                    double d2 = d0 * d0 + d1 * d1;

                    if (d2 >= 9.999999747378752E-5D) {
                        d2 = Math.sqrt(d2);
                        d0 /= d2;
                        d1 /= d2;
                        double d3 = 1.0D / d2;

                        if (d3 > 1.0D) {
                            d3 = 1.0D;
                        }

                        d0 *= d3;
                        d1 *= d3;
                        d0 *= 0.10000000149011612D;
                        d1 *= 0.10000000149011612D;
                        d0 *= 0.5D;
                        d1 *= 0.5D;
                        if (entity instanceof AbstractMinecart) {
                            double d4 = entity.getX() - this.getX();
                            double d5 = entity.getZ() - this.getZ();
                            Vec3 vec3d = (new Vec3(d4, 0.0D, d5)).normalize();
                            Vec3 vec3d1 = (new Vec3((double) Mth.cos(this.getYRot() * 0.017453292F), 0.0D, (double) Mth.sin(this.getYRot() * 0.017453292F))).normalize();
                            double d6 = Math.abs(vec3d.dot(vec3d1));

                            if (d6 < 0.800000011920929D) {
                                return;
                            }

                            Vec3 vec3d2 = this.getDeltaMovement();
                            Vec3 vec3d3 = entity.getDeltaMovement();

                            if (((AbstractMinecart) entity).getMinecartType() == AbstractMinecart.Type.FURNACE && this.getMinecartType() != AbstractMinecart.Type.FURNACE) {
                                this.setDeltaMovement(vec3d2.multiply(0.2D, 1.0D, 0.2D));
                                this.push(vec3d3.x - d0, 0.0D, vec3d3.z - d1);
                                entity.setDeltaMovement(vec3d3.multiply(0.95D, 1.0D, 0.95D));
                            } else if (((AbstractMinecart) entity).getMinecartType() != AbstractMinecart.Type.FURNACE && this.getMinecartType() == AbstractMinecart.Type.FURNACE) {
                                entity.setDeltaMovement(vec3d3.multiply(0.2D, 1.0D, 0.2D));
                                entity.push(vec3d2.x + d0, 0.0D, vec3d2.z + d1);
                                this.setDeltaMovement(vec3d2.multiply(0.95D, 1.0D, 0.95D));
                            } else {
                                double d7 = (vec3d3.x + vec3d2.x) / 2.0D;
                                double d8 = (vec3d3.z + vec3d2.z) / 2.0D;

                                this.setDeltaMovement(vec3d2.multiply(0.2D, 1.0D, 0.2D));
                                this.push(d7 - d0, 0.0D, d8 - d1);
                                entity.setDeltaMovement(vec3d3.multiply(0.2D, 1.0D, 0.2D));
                                entity.push(d7 + d0, 0.0D, d8 + d1);
                            }
                        } else {
                            this.push(-d0, 0.0D, -d1);
                            entity.push(d0 / 4.0D, 0.0D, d1 / 4.0D);
                        }
                    }

                }
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = (double) yaw;
        this.lerpXRot = (double) pitch;
        this.lerpSteps = interpolationSteps + 2;
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float) this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot();
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.targetDeltaMovement = new Vec3(x, y, z);
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    public void setDamage(float damageWobbleStrength) {
        this.entityData.set(AbstractMinecart.DATA_ID_DAMAGE, damageWobbleStrength);
    }

    public float getDamage() {
        return (Float) this.entityData.get(AbstractMinecart.DATA_ID_DAMAGE);
    }

    public void setHurtTime(int wobbleTicks) {
        this.entityData.set(AbstractMinecart.DATA_ID_HURT, wobbleTicks);
    }

    public int getHurtTime() {
        return (Integer) this.entityData.get(AbstractMinecart.DATA_ID_HURT);
    }

    public void setHurtDir(int wobbleSide) {
        this.entityData.set(AbstractMinecart.DATA_ID_HURTDIR, wobbleSide);
    }

    public int getHurtDir() {
        return (Integer) this.entityData.get(AbstractMinecart.DATA_ID_HURTDIR);
    }

    public abstract AbstractMinecart.Type getMinecartType();

    public BlockState getDisplayBlockState() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayBlockState() : Block.stateById((Integer) this.getEntityData().get(AbstractMinecart.DATA_ID_DISPLAY_BLOCK));
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    public int getDisplayOffset() {
        return !this.hasCustomDisplay() ? this.getDefaultDisplayOffset() : (Integer) this.getEntityData().get(AbstractMinecart.DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setDisplayBlockState(BlockState state) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_DISPLAY_BLOCK, Block.getId(state));
        this.setCustomDisplay(true);
    }

    public void setDisplayOffset(int offset) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_DISPLAY_OFFSET, offset);
        this.setCustomDisplay(true);
    }

    public boolean hasCustomDisplay() {
        return (Boolean) this.getEntityData().get(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY);
    }

    public void setCustomDisplay(boolean present) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY, present);
    }

    @Override
    public ItemStack getPickResult() {
        Item item;

        switch (this.getMinecartType()) {
            case FURNACE:
                item = Items.FURNACE_MINECART;
                break;
            case CHEST:
                item = Items.CHEST_MINECART;
                break;
            case TNT:
                item = Items.TNT_MINECART;
                break;
            case HOPPER:
                item = Items.HOPPER_MINECART;
                break;
            case COMMAND_BLOCK:
                item = Items.COMMAND_BLOCK_MINECART;
                break;
            default:
                item = Items.MINECART;
        }

        return new ItemStack(item);
    }

    public static enum Type {

        RIDEABLE, CHEST, FURNACE, TNT, SPAWNER, HOPPER, COMMAND_BLOCK;

        private Type() {}
    }

    // CraftBukkit start - Methods for getting and setting flying and derailed velocity modifiers
    public Vector getFlyingVelocityMod() {
        return new Vector(this.flyingX, this.flyingY, this.flyingZ);
    }

    public void setFlyingVelocityMod(Vector flying) {
        this.flyingX = flying.getX();
        this.flyingY = flying.getY();
        this.flyingZ = flying.getZ();
    }

    public Vector getDerailedVelocityMod() {
        return new Vector(this.derailedX, this.derailedY, this.derailedZ);
    }

    public void setDerailedVelocityMod(Vector derailed) {
        this.derailedX = derailed.getX();
        this.derailedY = derailed.getY();
        this.derailedZ = derailed.getZ();
    }
    // CraftBukkit end
}
