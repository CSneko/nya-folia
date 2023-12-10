package net.minecraft.world.entity.projectile;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.entity.FishHook;
import org.bukkit.event.player.PlayerFishEvent;
// CraftBukkit end

public class FishingHook extends Projectile {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final RandomSource syncronizedRandom;
    private boolean biting;
    public int outOfWaterTime;
    private static final int MAX_OUT_OF_WATER_TIME = 10;
    public static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BITING = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.BOOLEAN);
    private int life;
    private int nibble;
    public int timeUntilLured;
    private int timeUntilHooked;
    private float fishAngle;
    private boolean openWater;
    @Nullable
    public Entity hookedIn;
    public FishingHook.FishHookState currentState;
    private final int luck;
    private final int lureSpeed;

    // CraftBukkit start - Extra variables to enable modification of fishing wait time, values are minecraft defaults
    public int minWaitTime = 100;
    public int maxWaitTime = 600;
    public int minLureTime = 20;
    public int maxLureTime = 80;
    public float minLureAngle = 0.0F;
    public float maxLureAngle = 360.0F;
    public boolean applyLure = true;
    public boolean rainInfluenced = true;
    public boolean skyInfluenced = true;
    // CraftBukkit end

    private FishingHook(EntityType<? extends FishingHook> type, Level world, int luckOfTheSeaLevel, int lureLevel) {
        super(type, world);
        this.syncronizedRandom = RandomSource.create();
        this.openWater = true;
        this.currentState = FishingHook.FishHookState.FLYING;
        this.noCulling = true;
        this.luck = Math.max(0, luckOfTheSeaLevel);
        this.lureSpeed = Math.max(0, lureLevel);
        // Paper start
        minWaitTime = world.paperConfig().fishingTimeRange.minimum;
        maxWaitTime = world.paperConfig().fishingTimeRange.maximum;
        // Paper end
    }

    public FishingHook(EntityType<? extends FishingHook> type, Level world) {
        this(type, world, 0, 0);
    }

    public FishingHook(net.minecraft.world.entity.player.Player thrower, Level world, int luckOfTheSeaLevel, int lureLevel) {
        this(EntityType.FISHING_BOBBER, world, luckOfTheSeaLevel, lureLevel);
        //this.setOwner(thrower); // Folia - region threading - move this down after position so that thread-checks do not fail
        float f = thrower.getXRot();
        float f1 = thrower.getYRot();
        float f2 = Mth.cos(-f1 * 0.017453292F - 3.1415927F);
        float f3 = Mth.sin(-f1 * 0.017453292F - 3.1415927F);
        float f4 = -Mth.cos(-f * 0.017453292F);
        float f5 = Mth.sin(-f * 0.017453292F);
        double d0 = thrower.getX() - (double) f3 * 0.3D;
        double d1 = thrower.getEyeY();
        double d2 = thrower.getZ() - (double) f2 * 0.3D;

        this.moveTo(d0, d1, d2, f1, f);
        this.setOwner(thrower); // Folia - region threading - move this down after position so that thread-checks do not fail
        Vec3 vec3d = new Vec3((double) (-f3), (double) Mth.clamp(-(f5 / f4), -5.0F, 5.0F), (double) (-f2));
        double d3 = vec3d.length();

        vec3d = vec3d.multiply(0.6D / d3 + this.random.triangle(0.5D, 0.0103365D), 0.6D / d3 + this.random.triangle(0.5D, 0.0103365D), 0.6D / d3 + this.random.triangle(0.5D, 0.0103365D));
        this.setDeltaMovement(vec3d);
        this.setYRot((float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D));
        this.setXRot((float) (Mth.atan2(vec3d.y, vec3d.horizontalDistance()) * 57.2957763671875D));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(FishingHook.DATA_HOOKED_ENTITY, 0);
        this.getEntityData().define(FishingHook.DATA_BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (FishingHook.DATA_HOOKED_ENTITY.equals(data)) {
            int i = (Integer) this.getEntityData().get(FishingHook.DATA_HOOKED_ENTITY);

            this.hookedIn = i > 0 ? this.level().getEntity(i - 1) : null;
        }

        if (FishingHook.DATA_BITING.equals(data)) {
            this.biting = (Boolean) this.getEntityData().get(FishingHook.DATA_BITING);
            if (this.biting) {
                this.setDeltaMovement(this.getDeltaMovement().x, (double) (-0.4F * Mth.nextFloat(this.syncronizedRandom, 0.6F, 1.0F)), this.getDeltaMovement().z);
            }
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = 64.0D;

        return distance < 4096.0D;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {}

    @Override
    public void tick() {
        this.syncronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level().getGameTime());
        super.tick();
        net.minecraft.world.entity.player.Player entityhuman = this.getPlayerOwner();

        if (entityhuman == null) {
            this.discard();
        } else if (this.level().isClientSide || !this.shouldStopFishing(entityhuman)) {
            if (this.onGround()) {
                ++this.life;
                if (this.life >= 1200) {
                    this.discard();
                    return;
                }
            } else {
                this.life = 0;
            }

            float f = 0.0F;
            BlockPos blockposition = this.blockPosition();
            FluidState fluid = this.level().getFluidState(blockposition);

            if (fluid.is(FluidTags.WATER)) {
                f = fluid.getHeight(this.level(), blockposition);
            }

            boolean flag = f > 0.0F;

            if (this.currentState == FishingHook.FishHookState.FLYING) {
                if (this.hookedIn != null) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.currentState = FishingHook.FishHookState.HOOKED_IN_ENTITY;
                    return;
                }

                if (flag) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.3D, 0.2D, 0.3D));
                    this.currentState = FishingHook.FishHookState.BOBBING;
                    return;
                }

                this.checkCollision();
            } else {
                if (this.currentState == FishingHook.FishHookState.HOOKED_IN_ENTITY) {
                    if (this.hookedIn != null) {
                        if (!this.hookedIn.isRemoved() && this.hookedIn.level().dimension() == this.level().dimension()) {
                            this.setPos(this.hookedIn.getX(), this.hookedIn.getY(0.8D), this.hookedIn.getZ());
                        } else {
                            this.setHookedEntity((Entity) null);
                            this.currentState = FishingHook.FishHookState.FLYING;
                        }
                    }

                    return;
                }

                if (this.currentState == FishingHook.FishHookState.BOBBING) {
                    Vec3 vec3d = this.getDeltaMovement();
                    double d0 = this.getY() + vec3d.y - (double) blockposition.getY() - (double) f;

                    if (Math.abs(d0) < 0.01D) {
                        d0 += Math.signum(d0) * 0.1D;
                    }

                    this.setDeltaMovement(vec3d.x * 0.9D, vec3d.y - d0 * (double) this.random.nextFloat() * 0.2D, vec3d.z * 0.9D);
                    if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
                        this.openWater = true;
                    } else {
                        this.openWater = this.openWater && this.outOfWaterTime < 10 && this.calculateOpenWater(blockposition);
                    }

                    if (flag) {
                        this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
                        if (this.biting) {
                            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.1D * (double) this.syncronizedRandom.nextFloat() * (double) this.syncronizedRandom.nextFloat(), 0.0D));
                        }

                        if (!this.level().isClientSide) {
                            this.catchingFish(blockposition);
                        }
                    } else {
                        this.outOfWaterTime = Math.min(10, this.outOfWaterTime + 1);
                    }
                }
            }

            if (!fluid.is(FluidTags.WATER)) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
            this.updateRotation();
            if (this.currentState == FishingHook.FishHookState.FLYING && (this.onGround() || this.horizontalCollision)) {
                this.setDeltaMovement(Vec3.ZERO);
            }

            double d1 = 0.92D;

            this.setDeltaMovement(this.getDeltaMovement().scale(0.92D));
            this.reapplyPosition();
        }
    }

    private boolean shouldStopFishing(net.minecraft.world.entity.player.Player player) {
        // Folia start - region threading
        if (!io.papermc.paper.util.TickThread.isTickThreadFor(player)) {
            return true;
        }
        // Folia end - region threading
        ItemStack itemstack = player.getMainHandItem();
        ItemStack itemstack1 = player.getOffhandItem();
        boolean flag = itemstack.is(Items.FISHING_ROD);
        boolean flag1 = itemstack1.is(Items.FISHING_ROD);

        if (!player.isRemoved() && player.isAlive() && (flag || flag1) && this.distanceToSqr((Entity) player) <= 1024.0D) {
            return false;
        } else {
            this.discard();
            return true;
        }
    }

    private void checkCollision() {
        HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        this.preOnHit(movingobjectposition); // CraftBukkit - projectile hit event
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) || entity.isAlive() && entity instanceof ItemEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (!this.level().isClientSide) {
            this.setHookedEntity(entityHitResult.getEntity());
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        this.setDeltaMovement(this.getDeltaMovement().normalize().scale(blockHitResult.distanceTo(this)));
    }

    public void setHookedEntity(@Nullable Entity entity) {
        this.hookedIn = entity;
        this.getEntityData().set(FishingHook.DATA_HOOKED_ENTITY, entity == null ? 0 : entity.getId() + 1);
    }

    private void catchingFish(BlockPos pos) {
        ServerLevel worldserver = (ServerLevel) this.level();
        int i = 1;
        BlockPos blockposition1 = pos.above();

        if (this.rainInfluenced && this.random.nextFloat() < 0.25F && this.level().isRainingAt(blockposition1)) { // CraftBukkit
            ++i;
        }

        if (this.skyInfluenced && this.random.nextFloat() < 0.5F && !this.level().canSeeSky(blockposition1)) { // CraftBukkit
            --i;
        }

        if (this.nibble > 0) {
            --this.nibble;
            if (this.nibble <= 0) {
                this.timeUntilLured = 0;
                this.timeUntilHooked = 0;
                this.getEntityData().set(FishingHook.DATA_BITING, false);
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) this.getPlayerOwner().getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.FAILED_ATTEMPT);
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);
                // CraftBukkit end
            }
        } else {
            float f;
            float f1;
            float f2;
            double d0;
            double d1;
            double d2;
            BlockState iblockdata;

            if (this.timeUntilHooked > 0) {
                this.timeUntilHooked -= i;
                if (this.timeUntilHooked > 0) {
                    this.fishAngle += (float) this.random.triangle(0.0D, 9.188D);
                    f = this.fishAngle * 0.017453292F;
                    f1 = Mth.sin(f);
                    f2 = Mth.cos(f);
                    d0 = this.getX() + (double) (f1 * (float) this.timeUntilHooked * 0.1F);
                    d1 = (double) ((float) Mth.floor(this.getY()) + 1.0F);
                    d2 = this.getZ() + (double) (f2 * (float) this.timeUntilHooked * 0.1F);
                    iblockdata = worldserver.getBlockState(BlockPos.containing(d0, d1 - 1.0D, d2));
                    if (iblockdata.is(Blocks.WATER)) {
                        if (this.random.nextFloat() < 0.15F) {
                            worldserver.sendParticles(ParticleTypes.BUBBLE, d0, d1 - 0.10000000149011612D, d2, 1, (double) f1, 0.1D, (double) f2, 0.0D);
                        }

                        float f3 = f1 * 0.04F;
                        float f4 = f2 * 0.04F;

                        worldserver.sendParticles(ParticleTypes.FISHING, d0, d1, d2, 0, (double) f4, 0.01D, (double) (-f3), 1.0D);
                        worldserver.sendParticles(ParticleTypes.FISHING, d0, d1, d2, 0, (double) (-f4), 0.01D, (double) f3, 1.0D);
                    }
                } else {
                    // CraftBukkit start
                    PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) this.getPlayerOwner().getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.BITE);
                    this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);
                    if (playerFishEvent.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                    double d3 = this.getY() + 0.5D;

                    worldserver.sendParticles(ParticleTypes.BUBBLE, this.getX(), d3, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), (double) this.getBbWidth(), 0.0D, (double) this.getBbWidth(), 0.20000000298023224D);
                    worldserver.sendParticles(ParticleTypes.FISHING, this.getX(), d3, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), (double) this.getBbWidth(), 0.0D, (double) this.getBbWidth(), 0.20000000298023224D);
                    this.nibble = Mth.nextInt(this.random, 20, 40);
                    this.getEntityData().set(FishingHook.DATA_BITING, true);
                }
            } else if (this.timeUntilLured > 0) {
                this.timeUntilLured -= i;
                f = 0.15F;
                if (this.timeUntilLured < 20) {
                    f += (float) (20 - this.timeUntilLured) * 0.05F;
                } else if (this.timeUntilLured < 40) {
                    f += (float) (40 - this.timeUntilLured) * 0.02F;
                } else if (this.timeUntilLured < 60) {
                    f += (float) (60 - this.timeUntilLured) * 0.01F;
                }

                if (this.random.nextFloat() < f) {
                    f1 = Mth.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                    f2 = Mth.nextFloat(this.random, 25.0F, 60.0F);
                    d0 = this.getX() + (double) (Mth.sin(f1) * f2) * 0.1D;
                    d1 = (double) ((float) Mth.floor(this.getY()) + 1.0F);
                    d2 = this.getZ() + (double) (Mth.cos(f1) * f2) * 0.1D;
                    iblockdata = worldserver.getBlockState(BlockPos.containing(d0, d1 - 1.0D, d2));
                    if (iblockdata.is(Blocks.WATER)) {
                        worldserver.sendParticles(ParticleTypes.SPLASH, d0, d1, d2, 2 + this.random.nextInt(2), 0.10000000149011612D, 0.0D, 0.10000000149011612D, 0.0D);
                    }
                }

                if (this.timeUntilLured <= 0) {
                    // CraftBukkit start - logic to modify fishing wait time, lure time, and lure angle
                    this.fishAngle = Mth.nextFloat(this.random, this.minLureAngle, this.maxLureAngle);
                    this.timeUntilHooked = Mth.nextInt(this.random, this.minLureTime, this.maxLureTime);
                    // CraftBukkit end
                }
            } else {
                // CraftBukkit start - logic to modify fishing wait time
                this.timeUntilLured = Mth.nextInt(this.random, this.minWaitTime, this.maxWaitTime);
                this.timeUntilLured -= (this.applyLure) ? (this.lureSpeed * 20 * 5 >= this.maxWaitTime ? this.timeUntilLured - 1 : this.lureSpeed * 20 * 5) : 0; // Paper - Fix Lure infinite loop
                // CraftBukkit end
            }
        }

    }

    public boolean calculateOpenWater(BlockPos pos) {
        FishingHook.OpenWaterType entityfishinghook_waterposition = FishingHook.OpenWaterType.INVALID;

        for (int i = -1; i <= 2; ++i) {
            FishingHook.OpenWaterType entityfishinghook_waterposition1 = this.getOpenWaterTypeForArea(pos.offset(-2, i, -2), pos.offset(2, i, 2));

            switch (entityfishinghook_waterposition1) {
                case INVALID:
                    return false;
                case ABOVE_WATER:
                    if (entityfishinghook_waterposition == FishingHook.OpenWaterType.INVALID) {
                        return false;
                    }
                    break;
                case INSIDE_WATER:
                    if (entityfishinghook_waterposition == FishingHook.OpenWaterType.ABOVE_WATER) {
                        return false;
                    }
            }

            entityfishinghook_waterposition = entityfishinghook_waterposition1;
        }

        return true;
    }

    private FishingHook.OpenWaterType getOpenWaterTypeForArea(BlockPos start, BlockPos end) {
        return (FishingHook.OpenWaterType) BlockPos.betweenClosedStream(start, end).map(this::getOpenWaterTypeForBlock).reduce((entityfishinghook_waterposition, entityfishinghook_waterposition1) -> {
            return entityfishinghook_waterposition == entityfishinghook_waterposition1 ? entityfishinghook_waterposition : FishingHook.OpenWaterType.INVALID;
        }).orElse(FishingHook.OpenWaterType.INVALID);
    }

    private FishingHook.OpenWaterType getOpenWaterTypeForBlock(BlockPos pos) {
        BlockState iblockdata = this.level().getBlockState(pos);

        if (!iblockdata.isAir() && !iblockdata.is(Blocks.LILY_PAD)) {
            FluidState fluid = iblockdata.getFluidState();

            return fluid.is(FluidTags.WATER) && fluid.isSource() && iblockdata.getCollisionShape(this.level(), pos).isEmpty() ? FishingHook.OpenWaterType.INSIDE_WATER : FishingHook.OpenWaterType.INVALID;
        } else {
            return FishingHook.OpenWaterType.ABOVE_WATER;
        }
    }

    public boolean isOpenWaterFishing() {
        return this.openWater;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {}

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {}

    // Paper start - add hand parameter
    @Deprecated
    @io.papermc.paper.annotation.DoNotUse
    public int retrieve(ItemStack usedItem) {
        return this.retrieve(net.minecraft.world.InteractionHand.MAIN_HAND, usedItem);
    }

    public int retrieve(net.minecraft.world.InteractionHand hand, ItemStack usedItem) {
        // Paper end
        net.minecraft.world.entity.player.Player entityhuman = this.getPlayerOwner();

        if (!this.level().isClientSide && entityhuman != null && !this.shouldStopFishing(entityhuman)) {
            int i = 0;

            if (this.hookedIn != null) {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), this.hookedIn.getBukkitEntity(), (FishHook) this.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), PlayerFishEvent.State.CAUGHT_ENTITY); // Paper - add hand
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
                // CraftBukkit end
                this.pullEntity(this.hookedIn);
                CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer) entityhuman, usedItem, this, Collections.emptyList());
                this.level().broadcastEntityEvent(this, (byte) 31);
                i = this.hookedIn instanceof ItemEntity ? 3 : 5;
            } else if (this.nibble > 0) {
                LootParams lootparams = (new LootParams.Builder((ServerLevel) this.level())).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.TOOL, usedItem).withParameter(LootContextParams.THIS_ENTITY, this).withLuck((float) this.luck + entityhuman.getLuck()).create(LootContextParamSets.FISHING);
                LootTable loottable = this.level().getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
                List<ItemStack> list = loottable.getRandomItems(lootparams);

                CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer) entityhuman, usedItem, this, list);
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    ItemStack itemstack1 = (ItemStack) iterator.next();
                    // Paper start, new EntityItem would throw if for whatever reason (mostly shitty datapacks) the itemstack1 turns out to be empty
                    // if the item stack is empty we instead just have our entityitem as null
                    ItemEntity entityitem = null;
                    if (!itemstack1.isEmpty()) {
                        entityitem = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemstack1);
                    }
                    // Paper end
                    // CraftBukkit start
                    PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), entityitem != null ? entityitem.getBukkitEntity() : null, (FishHook) this.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), PlayerFishEvent.State.CAUGHT_FISH); // Paper - entityitem may be null // Paper - add hand
                    playerFishEvent.setExpToDrop(this.random.nextInt(6) + 1);
                    this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);

                    if (playerFishEvent.isCancelled()) {
                        return 0;
                    }
                    // CraftBukkit end
                    double d0 = entityhuman.getX() - this.getX();
                    double d1 = entityhuman.getY() - this.getY();
                    double d2 = entityhuman.getZ() - this.getZ();
                    double d3 = 0.1D;

                    // Paper start, entity item can be null, so we need to check against this
                    if (entityitem != null) {
                        entityitem.setDeltaMovement(d0 * 0.1D, d1 * 0.1D + Math.sqrt(Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2)) * 0.08D, d2 * 0.1D);
                        this.level().addFreshEntity(entityitem);
                    }
                    // Paper end
                    // CraftBukkit start - this.random.nextInt(6) + 1 -> playerFishEvent.getExpToDrop()
                    if (playerFishEvent.getExpToDrop() > 0) {
                        entityhuman.level().addFreshEntity(new ExperienceOrb(entityhuman.level(), entityhuman.getX(), entityhuman.getY() + 0.5D, entityhuman.getZ() + 0.5D, playerFishEvent.getExpToDrop(), org.bukkit.entity.ExperienceOrb.SpawnReason.FISHING, this.getPlayerOwner(), this)); // Paper
                    }
                    // CraftBukkit end
                    if (itemstack1.is(ItemTags.FISHES)) {
                        entityhuman.awardStat(Stats.FISH_CAUGHT, 1);
                    }
                }

                i = 1;
            }

            if (this.onGround()) {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), PlayerFishEvent.State.IN_GROUND); // Paper - add hand
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
                // CraftBukkit end
                i = 2;
            }
            // CraftBukkit start
            if (i == 0) {
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand), PlayerFishEvent.State.REEL_IN); // Paper - add hand
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);
                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
            }
            // CraftBukkit end

            this.discard();
            return i;
        } else {
            return 0;
        }
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 31 && this.level().isClientSide && this.hookedIn instanceof net.minecraft.world.entity.player.Player && ((net.minecraft.world.entity.player.Player) this.hookedIn).isLocalPlayer()) {
            this.pullEntity(this.hookedIn);
        }

        super.handleEntityEvent(status);
    }

    public void pullEntity(Entity entity) {
        Entity entity1 = this.getOwner();

        if (entity1 != null) {
            Vec3 vec3d = (new Vec3(entity1.getX() - this.getX(), entity1.getY() - this.getY(), entity1.getZ() - this.getZ())).scale(0.1D);

            entity.setDeltaMovement(entity.getDeltaMovement().add(vec3d));
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Folia - region threading - move into preRemove
        super.remove(reason);
    }

    // Folia start - region threading
    @Override
    protected void preRemove(RemovalReason reason) {
        super.preRemove(reason);
        this.updateOwnerInfo((FishingHook) null);
    }
    // Folia end - region threading

    @Override
    public void onClientRemoval() {
        this.updateOwnerInfo((FishingHook) null);
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);
        this.updateOwnerInfo(this);
    }

    private void updateOwnerInfo(@Nullable FishingHook fishingBobber) {
        net.minecraft.world.entity.player.Player entityhuman = this.getPlayerOwner();

        if (entityhuman != null) {
            entityhuman.fishing = fishingBobber;
        }

    }

    @Nullable
    public net.minecraft.world.entity.player.Player getPlayerOwner() {
        Entity entity = this.getOwner();

        return entity instanceof net.minecraft.world.entity.player.Player ? (net.minecraft.world.entity.player.Player) entity : null;
    }

    @Nullable
    public Entity getHookedIn() {
        return this.hookedIn;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        Entity entity = this.getOwner();

        return new ClientboundAddEntityPacket(this, entity == null ? this.getId() : entity.getId());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        if (this.getPlayerOwner() == null) {
            int i = packet.getData();

            FishingHook.LOGGER.error("Failed to recreate fishing hook on client. {} (id: {}) is not a valid owner.", this.level().getEntity(i), i);
            this.kill();
        }

    }

    public static enum FishHookState {

        FLYING, HOOKED_IN_ENTITY, BOBBING;

        private FishHookState() {}
    }

    private static enum OpenWaterType {

        ABOVE_WATER, INSIDE_WATER, INVALID;

        private OpenWaterType() {}
    }
}
