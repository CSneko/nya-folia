package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class Bee extends Animal implements NeutralMob, FlyingAnimal {

    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Bee.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(Bee.class, EntityDataSerializers.INT);
    private static final int FLAG_ROLL = 2;
    private static final int FLAG_HAS_STUNG = 4;
    private static final int FLAG_HAS_NECTAR = 8;
    private static final int STING_DEATH_COUNTDOWN = 1200;
    private static final int TICKS_BEFORE_GOING_TO_KNOWN_FLOWER = 2400;
    private static final int TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME = 3600;
    private static final int MIN_ATTACK_DIST = 4;
    private static final int MAX_CROPS_GROWABLE = 10;
    private static final int POISON_SECONDS_NORMAL = 10;
    private static final int POISON_SECONDS_HARD = 18;
    private static final int TOO_FAR_DISTANCE = 32;
    private static final int HIVE_CLOSE_ENOUGH_DISTANCE = 2;
    private static final int PATHFIND_TO_HIVE_WHEN_CLOSER_THAN = 16;
    private static final int HIVE_SEARCH_DISTANCE = 20;
    public static final String TAG_CROPS_GROWN_SINCE_POLLINATION = "CropsGrownSincePollination";
    public static final String TAG_CANNOT_ENTER_HIVE_TICKS = "CannotEnterHiveTicks";
    public static final String TAG_TICKS_SINCE_POLLINATION = "TicksSincePollination";
    public static final String TAG_HAS_STUNG = "HasStung";
    public static final String TAG_HAS_NECTAR = "HasNectar";
    public static final String TAG_FLOWER_POS = "FlowerPos";
    public static final String TAG_HIVE_POS = "HivePos";
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    @Nullable
    private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    public int ticksWithoutNectarSinceExitingHive;
    public int stayOutOfHiveCountdown;
    public int numCropsGrownSincePollination;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200;
    int remainingCooldownBeforeLocatingNewHive;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_FLOWER = 200;
    int remainingCooldownBeforeLocatingNewFlower;
    @Nullable
    BlockPos savedFlowerPos;
    @Nullable
    public BlockPos hivePos;
    Bee.BeePollinateGoal beePollinateGoal;
    Bee.BeeGoToHiveGoal goToHiveGoal;
    private Bee.BeeGoToKnownFlowerGoal goToKnownFlowerGoal;
    private int underWaterTicks;

    public Bee(EntityType<? extends Bee> type, Level world) {
        super(type, world);
        this.remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);
        // Paper start - apply gravity to bees when they get stuck in the void, fixes MC-167279
        class BeeFlyingMoveControl extends FlyingMoveControl {
            public BeeFlyingMoveControl(final Mob entity, final int maxPitchChange, final boolean noGravity) {
                super(entity, maxPitchChange, noGravity);
            }

            @Override
            public void tick() {
                if (this.mob.getY() <= Bee.this.level().getMinBuildHeight()) {
                    this.mob.setNoGravity(false);
                }
                super.tick();
            }
        }
        this.moveControl = new BeeFlyingMoveControl(this, 20, true);
        // Paper end
        this.lookControl = new Bee.BeeLookControl(this);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Bee.DATA_FLAGS_ID, (byte) 0);
        this.entityData.define(Bee.DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return world.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Bee.BeeAttackGoal(this, 1.399999976158142D, true));
        this.goalSelector.addGoal(1, new Bee.BeeEnterHiveGoal());
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, Ingredient.of(ItemTags.FLOWERS), false));
        this.beePollinateGoal = new Bee.BeePollinateGoal();
        this.goalSelector.addGoal(4, this.beePollinateGoal);
        this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(5, new Bee.BeeLocateHiveGoal());
        this.goToHiveGoal = new Bee.BeeGoToHiveGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goToKnownFlowerGoal = new Bee.BeeGoToKnownFlowerGoal();
        this.goalSelector.addGoal(6, this.goToKnownFlowerGoal);
        this.goalSelector.addGoal(7, new Bee.BeeGrowCropGoal());
        this.goalSelector.addGoal(8, new Bee.BeeWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
        this.targetSelector.addGoal(1, (new Bee.BeeHurtByOtherGoal(this)).setAlertOthers(new Class[0]));
        this.targetSelector.addGoal(2, new Bee.BeeBecomeAngryTargetGoal(this));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.hasHive()) {
            nbt.put("HivePos", NbtUtils.writeBlockPos(this.getHivePos()));
        }

        if (this.hasSavedFlowerPos()) {
            nbt.put("FlowerPos", NbtUtils.writeBlockPos(this.getSavedFlowerPos()));
        }

        nbt.putBoolean("HasNectar", this.hasNectar());
        nbt.putBoolean("HasStung", this.hasStung());
        nbt.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        nbt.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        nbt.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.addPersistentAngerSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.hivePos = null;
        if (nbt.contains("HivePos")) {
            this.hivePos = NbtUtils.readBlockPos(nbt.getCompound("HivePos"));
        }

        this.savedFlowerPos = null;
        if (nbt.contains("FlowerPos")) {
            this.savedFlowerPos = NbtUtils.readBlockPos(nbt.getCompound("FlowerPos"));
        }

        super.readAdditionalSaveData(nbt);
        this.setHasNectar(nbt.getBoolean("HasNectar"));
        this.setHasStung(nbt.getBoolean("HasStung"));
        this.ticksWithoutNectarSinceExitingHive = nbt.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = nbt.getInt("CannotEnterHiveTicks");
        this.numCropsGrownSincePollination = nbt.getInt("CropsGrownSincePollination");
        this.readPersistentAngerSaveData(this.level(), nbt);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean flag = target.hurt(this.damageSources().sting(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));

        if (flag) {
            this.doEnchantDamageEffects(this, target);
            if (target instanceof LivingEntity) {
                ((LivingEntity) target).setStingerCount(((LivingEntity) target).getStingerCount() + 1);
                byte b0 = 0;

                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    b0 = 10;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    b0 = 18;
                }

                if (b0 > 0) {
                    ((LivingEntity) target).addEffect(new MobEffectInstance(MobEffects.POISON, b0 * 20, 0), this, EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            this.setHasStung(true);
            this.stopBeingAngry();
            this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; ++i) {
                this.spawnFluidParticle(this.level(), this.getX() - 0.30000001192092896D, this.getX() + 0.30000001192092896D, this.getZ() - 0.30000001192092896D, this.getZ() + 0.30000001192092896D, this.getY(0.5D), ParticleTypes.FALLING_NECTAR);
            }
        }

        this.updateRollAmount();
    }

    private void spawnFluidParticle(Level world, double lastX, double x, double lastZ, double z, double y, ParticleOptions effect) {
        world.addParticle(effect, Mth.lerp(world.random.nextDouble(), lastX, x), y, Mth.lerp(world.random.nextDouble(), lastZ, z), 0.0D, 0.0D, 0.0D);
    }

    void pathfindRandomlyTowards(BlockPos pos) {
        Vec3 vec3d = Vec3.atBottomCenterOf(pos);
        byte b0 = 0;
        BlockPos blockposition1 = this.blockPosition();
        int i = (int) vec3d.y - blockposition1.getY();

        if (i > 2) {
            b0 = 4;
        } else if (i < -2) {
            b0 = -4;
        }

        int j = 6;
        int k = 8;
        int l = blockposition1.distManhattan(pos);

        if (l < 15) {
            j = l / 2;
            k = l / 2;
        }

        Vec3 vec3d1 = AirRandomPos.getPosTowards(this, j, k, b0, vec3d, 0.3141592741012573D);

        if (vec3d1 != null) {
            this.navigation.setMaxVisitedNodesMultiplier(0.5F);
            this.navigation.moveTo(vec3d1.x, vec3d1.y, vec3d1.z, 1.0D);
        }
    }

    @Nullable
    public BlockPos getSavedFlowerPos() {
        return this.savedFlowerPos;
    }

    public boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    public void setSavedFlowerPos(BlockPos flowerPos) {
        this.savedFlowerPos = flowerPos;
    }

    @VisibleForDebug
    public int getTravellingTicks() {
        return Math.max(this.goToHiveGoal.travellingTicks, this.goToKnownFlowerGoal.travellingTicks);
    }

    @VisibleForDebug
    public List<BlockPos> getBlacklistedHives() {
        return this.goToHiveGoal.blacklistedTargets;
    }

    private boolean isTiredOfLookingForNectar() {
        return this.ticksWithoutNectarSinceExitingHive > 3600;
    }

    boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.beePollinateGoal.isPollinating() && !this.hasStung() && this.getTarget() == null) {
            boolean flag = this.isTiredOfLookingForNectar() || this.level().isRaining() || this.level().isNight() || this.hasNectar();

            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int cannotEnterHiveTicks) {
        this.stayOutOfHiveCountdown = cannotEnterHiveTicks;
    }

    public float getRollAmount(float tickDelta) {
        return Mth.lerp(tickDelta, this.rollAmountO, this.rollAmount);
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }

    }

    @Override
    protected void customServerAiStep() {
        boolean flag = this.hasStung();

        if (this.isInWaterOrBubble()) {
            ++this.underWaterTicks;
        } else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 20) {
            this.hurt(this.damageSources().drown(), 1.0F);
        }

        if (flag) {
            ++this.timeSinceSting;
            if (this.timeSinceSting % 5 == 0 && this.random.nextInt(Mth.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0) {
                this.hurt(this.damageSources().generic(), this.getHealth());
            }
        }

        if (!this.hasNectar()) {
            ++this.ticksWithoutNectarSinceExitingHive;
        }

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), false);
        }

    }

    public void resetTicksWithoutNectarSinceExitingHive() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    private boolean isHiveNearFire() {
        if (this.hivePos == null) {
            return false;
        } else {
            if (!this.level().isLoadedAndInBounds(this.hivePos)) return false; // Paper
            BlockEntity tileentity = this.level().getBlockEntity(this.hivePos);

            return tileentity instanceof BeehiveBlockEntity && ((BeehiveBlockEntity) tileentity).isFireNearby();
        }
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return (Integer) this.entityData.get(Bee.DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int angerTime) {
        this.entityData.set(Bee.DATA_REMAINING_ANGER_TIME, angerTime);
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID angryAt) {
        this.persistentAngerTarget = angryAt;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(Bee.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    private boolean doesHiveHaveSpace(BlockPos pos) {
        if (!this.level().isLoadedAndInBounds(pos)) return false; // Paper
        BlockEntity tileentity = this.level().getBlockEntity(pos);

        return tileentity instanceof BeehiveBlockEntity ? !((BeehiveBlockEntity) tileentity).isFull() : false;
    }

    @VisibleForDebug
    public boolean hasHive() {
        return this.hivePos != null;
    }

    @Nullable
    @VisibleForDebug
    public BlockPos getHivePos() {
        return this.hivePos;
    }

    @VisibleForDebug
    public GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendBeeInfo(this);
    }

    int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    private void resetNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination = 0;
    }

    void incrementNumCropsGrownSincePollination() {
        ++this.numCropsGrownSincePollination;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            if (this.stayOutOfHiveCountdown > 0) {
                --this.stayOutOfHiveCountdown;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                --this.remainingCooldownBeforeLocatingNewFlower;
            }

            boolean flag = this.isAngry() && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr((Entity) this) < 4.0D;

            this.setRolling(flag);
            if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }

    }

    boolean isHiveValid() {
        if (!this.hasHive()) {
            return false;
        } else if (this.isTooFarAway(this.hivePos)) {
            return false;
        } else {
            if (this.level().getChunkIfLoadedImmediately(this.hivePos.getX() >> 4, this.hivePos.getZ() >> 4) == null) return true; // Paper - just assume the hive is still there, no need to load the chunk(s)
            BlockEntity tileentity = this.level().getBlockEntity(this.hivePos);

            return tileentity != null && tileentity.getType() == BlockEntityType.BEEHIVE;
        }
    }

    public boolean hasNectar() {
        return this.getFlag(8);
    }

    public void setHasNectar(boolean hasNectar) {
        if (hasNectar) {
            this.resetTicksWithoutNectarSinceExitingHive();
        }

        this.setFlag(8, hasNectar);
    }

    public boolean hasStung() {
        return this.getFlag(4);
    }

    public void setHasStung(boolean hasStung) {
        this.setFlag(4, hasStung);
    }

    public net.kyori.adventure.util.TriState rollingOverride = net.kyori.adventure.util.TriState.NOT_SET; // Paper - Rolling override
    public boolean isRolling() {
        return this.getFlag(2);
    }

    public void setRolling(boolean nearTarget) {
        nearTarget = rollingOverride.toBooleanOrElse(nearTarget); // Paper - Rolling override
        this.setFlag(2, nearTarget);
    }

    boolean isTooFarAway(BlockPos pos) {
        return !this.closerThan(pos, 32);
    }

    private void setFlag(int bit, boolean value) {
        if (value) {
            this.entityData.set(Bee.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Bee.DATA_FLAGS_ID) | bit));
        } else {
            this.entityData.set(Bee.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Bee.DATA_FLAGS_ID) & ~bit));
        }

    }

    private boolean getFlag(int location) {
        return ((Byte) this.entityData.get(Bee.DATA_FLAGS_ID) & location) != 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.FLYING_SPEED, 0.6000000238418579D).add(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).add(Attributes.ATTACK_DAMAGE, 2.0D).add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation navigationflying = new FlyingPathNavigation(this, world) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }

            @Override
            public void tick() {
                if (!Bee.this.beePollinateGoal.isPollinating()) {
                    super.tick();
                }
            }
        };

        navigationflying.setCanOpenDoors(false);
        navigationflying.setCanFloat(false);
        navigationflying.setCanPassDoors(true);
        return navigationflying;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FLOWERS);
    }

    boolean isFlowerValid(BlockPos pos) {
        return this.level().isLoaded(pos) && this.level().getBlockState(pos).is(BlockTags.FLOWERS);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BEE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Nullable
    @Override
    public Bee getBreedOffspring(ServerLevel world, AgeableMob entity) {
        return (Bee) EntityType.BEE.create(world);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return this.isBaby() ? dimensions.height * 0.5F : dimensions.height * 0.5F;
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {}

    @Override
    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % Bee.TICKS_PER_FLAP == 0;
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    public void dropOffNectar() {
        this.setHasNectar(false);
        this.resetNumCropsGrownSincePollination();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            // CraftBukkit start - Only stop pollinating if entity was damaged
            boolean result = super.hurt(source, amount);
            if (result && !this.level().isClientSide) {
            // CraftBukkit end
                this.beePollinateGoal.stopPollinating();
            }

            return result; // CraftBukkit
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluid) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.01D, 0.0D));
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.2F));
    }

    boolean closerThan(BlockPos pos, int distance) {
        return pos.closerThan(this.blockPosition(), (double) distance);
    }

    private class BeePollinateGoal extends Bee.BaseBeeGoal {

        private static final int MIN_POLLINATION_TICKS = 400;
        private static final int MIN_FIND_FLOWER_RETRY_COOLDOWN = 20;
        private static final int MAX_FIND_FLOWER_RETRY_COOLDOWN = 60;
        private final Predicate<BlockState> VALID_POLLINATION_BLOCKS = (iblockdata) -> {
            return iblockdata.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) iblockdata.getValue(BlockStateProperties.WATERLOGGED) ? false : (iblockdata.is(BlockTags.FLOWERS) ? (iblockdata.is(Blocks.SUNFLOWER) ? iblockdata.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER : true) : false);
        };
        private static final double ARRIVAL_THRESHOLD = 0.1D;
        private static final int POSITION_CHANGE_CHANCE = 25;
        private static final float SPEED_MODIFIER = 0.35F;
        private static final float HOVER_HEIGHT_WITHIN_FLOWER = 0.6F;
        private static final float HOVER_POS_OFFSET = 0.33333334F;
        private int successfulPollinatingTicks;
        private int lastSoundPlayedTick;
        private boolean pollinating;
        @Nullable
        private Vec3 hoverPos;
        private int pollinatingTicks;
        private static final int MAX_POLLINATING_TICKS = 600;

        BeePollinateGoal() {
            super();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            if (Bee.this.remainingCooldownBeforeLocatingNewFlower > 0) {
                return false;
            } else if (Bee.this.hasNectar()) {
                return false;
            } else if (Bee.this.level().isRaining()) {
                return false;
            } else {
                Optional<BlockPos> optional = this.findNearbyFlower();

                if (optional.isPresent()) {
                    Bee.this.savedFlowerPos = (BlockPos) optional.get();
                    Bee.this.navigation.moveTo((double) Bee.this.savedFlowerPos.getX() + 0.5D, (double) Bee.this.savedFlowerPos.getY() + 0.5D, (double) Bee.this.savedFlowerPos.getZ() + 0.5D, 1.2000000476837158D);
                    return true;
                } else {
                    Bee.this.remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(Bee.this.random, 20, 60);
                    return false;
                }
            }
        }

        @Override
        public boolean canBeeContinueToUse() {
            if (!this.pollinating) {
                return false;
            } else if (!Bee.this.hasSavedFlowerPos()) {
                return false;
            } else if (Bee.this.level().isRaining()) {
                return false;
            } else if (this.hasPollinatedLongEnough()) {
                return Bee.this.random.nextFloat() < 0.2F;
            } else if (Bee.this.tickCount % 20 == 0 && !Bee.this.isFlowerValid(Bee.this.savedFlowerPos)) {
                Bee.this.savedFlowerPos = null;
                return false;
            } else {
                return true;
            }
        }

        private boolean hasPollinatedLongEnough() {
            return this.successfulPollinatingTicks > 400;
        }

        boolean isPollinating() {
            return this.pollinating;
        }

        void stopPollinating() {
            this.pollinating = false;
        }

        @Override
        public void start() {
            this.successfulPollinatingTicks = 0;
            this.pollinatingTicks = 0;
            this.lastSoundPlayedTick = 0;
            this.pollinating = true;
            Bee.this.resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (this.hasPollinatedLongEnough()) {
                Bee.this.setHasNectar(true);
            }

            this.pollinating = false;
            Bee.this.navigation.stop();
            Bee.this.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ++this.pollinatingTicks;
            if (this.pollinatingTicks > 600) {
                Bee.this.savedFlowerPos = null;
            } else if (Bee.this.savedFlowerPos != null) { // Paper - add null check since API can manipulate this
                Vec3 vec3d = Vec3.atBottomCenterOf(Bee.this.savedFlowerPos).add(0.0D, 0.6000000238418579D, 0.0D);

                if (vec3d.distanceTo(Bee.this.position()) > 1.0D) {
                    this.hoverPos = vec3d;
                    this.setWantedPos();
                } else {
                    if (this.hoverPos == null) {
                        this.hoverPos = vec3d;
                    }

                    boolean flag = Bee.this.position().distanceTo(this.hoverPos) <= 0.1D;
                    boolean flag1 = true;

                    if (!flag && this.pollinatingTicks > 600) {
                        Bee.this.savedFlowerPos = null;
                    } else {
                        if (flag) {
                            boolean flag2 = Bee.this.random.nextInt(25) == 0;

                            if (flag2) {
                                this.hoverPos = new Vec3(vec3d.x() + (double) this.getOffset(), vec3d.y(), vec3d.z() + (double) this.getOffset());
                                Bee.this.navigation.stop();
                            } else {
                                flag1 = false;
                            }

                            Bee.this.getLookControl().setLookAt(vec3d.x(), vec3d.y(), vec3d.z());
                        }

                        if (flag1) {
                            this.setWantedPos();
                        }

                        ++this.successfulPollinatingTicks;
                        if (Bee.this.random.nextFloat() < 0.05F && this.successfulPollinatingTicks > this.lastSoundPlayedTick + 60) {
                            this.lastSoundPlayedTick = this.successfulPollinatingTicks;
                            Bee.this.playSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
                        }

                    }
                }
            }
        }

        private void setWantedPos() {
            Bee.this.getMoveControl().setWantedPosition(this.hoverPos.x(), this.hoverPos.y(), this.hoverPos.z(), 0.3499999940395355D);
        }

        private float getOffset() {
            return (Bee.this.random.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private Optional<BlockPos> findNearbyFlower() {
            return this.findNearestBlock(this.VALID_POLLINATION_BLOCKS, 5.0D);
        }

        private Optional<BlockPos> findNearestBlock(Predicate<BlockState> predicate, double searchDistance) {
            BlockPos blockposition = Bee.this.blockPosition();
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int i = 0; (double) i <= searchDistance; i = i > 0 ? -i : 1 - i) {
                for (int j = 0; (double) j < searchDistance; ++j) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockposition_mutableblockposition.setWithOffset(blockposition, k, i - 1, l);
                            if (blockposition.closerThan(blockposition_mutableblockposition, searchDistance) && predicate.test(Bee.this.level().getBlockState(blockposition_mutableblockposition))) {
                                return Optional.of(blockposition_mutableblockposition);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        }
    }

    private class BeeLookControl extends LookControl {

        BeeLookControl(Mob entity) {
            super(entity);
        }

        @Override
        public void tick() {
            if (!Bee.this.isAngry()) {
                super.tick();
            }
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Bee.this.beePollinateGoal.isPollinating();
        }
    }

    private class BeeAttackGoal extends MeleeAttackGoal {

        BeeAttackGoal(PathfinderMob mob, double speed, boolean pauseWhenMobIdle) {
            super(mob, speed, pauseWhenMobIdle);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && Bee.this.isAngry() && !Bee.this.hasStung();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && Bee.this.isAngry() && !Bee.this.hasStung();
        }
    }

    private class BeeEnterHiveGoal extends Bee.BaseBeeGoal {

        BeeEnterHiveGoal() {
            super();
        }

        @Override
        public boolean canBeeUse() {
            if (Bee.this.hasHive() && Bee.this.wantsToEnterHive() && Bee.this.hivePos.closerToCenterThan(Bee.this.position(), 2.0D)) {
                if (!Bee.this.level().isLoadedAndInBounds(Bee.this.hivePos)) return false; // Paper
                BlockEntity tileentity = Bee.this.level().getBlockEntity(Bee.this.hivePos);

                if (tileentity instanceof BeehiveBlockEntity) {
                    BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

                    if (!tileentitybeehive.isFull()) {
                        return true;
                    }

                    Bee.this.hivePos = null;
                }
            }

            return false;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (!Bee.this.level().isLoadedAndInBounds(Bee.this.hivePos)) return; // Paper
            BlockEntity tileentity = Bee.this.level().getBlockEntity(Bee.this.hivePos);

            if (tileentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

                tileentitybeehive.addOccupant(Bee.this, Bee.this.hasNectar());
            }

        }
    }

    private class BeeLocateHiveGoal extends Bee.BaseBeeGoal {

        BeeLocateHiveGoal() {
            super();
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.remainingCooldownBeforeLocatingNewHive == 0 && !Bee.this.hasHive() && Bee.this.wantsToEnterHive();
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Bee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.findNearbyHivesWithSpace();

            if (!list.isEmpty()) {
                Iterator iterator = list.iterator();

                BlockPos blockposition;

                do {
                    if (!iterator.hasNext()) {
                        Bee.this.goToHiveGoal.clearBlacklist();
                        Bee.this.hivePos = (BlockPos) list.get(0);
                        return;
                    }

                    blockposition = (BlockPos) iterator.next();
                } while (Bee.this.goToHiveGoal.isTargetBlacklisted(blockposition));

                Bee.this.hivePos = blockposition;
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            BlockPos blockposition = Bee.this.blockPosition();
            PoiManager villageplace = ((ServerLevel) Bee.this.level()).getPoiManager();
            Stream<PoiRecord> stream = villageplace.getInRange((holder) -> {
                return holder.is(PoiTypeTags.BEE_HOME);
            }, blockposition, 20, PoiManager.Occupancy.ANY);

            return (List) stream.map(PoiRecord::getPos).filter(Bee.this::doesHiveHaveSpace).sorted(Comparator.comparingDouble((blockposition1) -> {
                return blockposition1.distSqr(blockposition);
            })).collect(Collectors.toList());
        }
    }

    @VisibleForDebug
    public class BeeGoToHiveGoal extends Bee.BaseBeeGoal {

        public static final int MAX_TRAVELLING_TICKS = 600;
        int travellingTicks;
        private static final int MAX_BLACKLISTED_TARGETS = 3;
        final List<BlockPos> blacklistedTargets;
        @Nullable
        private Path lastPath;
        private static final int TICKS_BEFORE_HIVE_DROP = 60;
        private int ticksStuck;

        BeeGoToHiveGoal() {
            super();
            this.travellingTicks = Bee.this.random.nextInt(10); // CraftBukkit - SPIGOT-7495: Give Bees another chance and let them use their own random, avoid concurrency issues
            this.blacklistedTargets = Lists.newArrayList();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            // Folia start - region threading
            if (Bee.this.hivePos != null && Bee.this.isTooFarAway(Bee.this.hivePos)) {
                Bee.this.hivePos = null;
            }
            // Folia end - region threading
            return Bee.this.hivePos != null && !Bee.this.hasRestriction() && Bee.this.wantsToEnterHive() && !this.hasReachedTarget(Bee.this.hivePos) && Bee.this.level().getBlockState(Bee.this.hivePos).is(BlockTags.BEEHIVES);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            Bee.this.navigation.stop();
            Bee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (Bee.this.hivePos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > this.adjustedTickDelay(600)) {
                    this.dropAndBlacklistHive();
                } else if (!Bee.this.navigation.isInProgress()) {
                    if (!Bee.this.closerThan(Bee.this.hivePos, 16)) {
                        if (Bee.this.isTooFarAway(Bee.this.hivePos)) {
                            this.dropHive();
                        } else {
                            Bee.this.pathfindRandomlyTowards(Bee.this.hivePos);
                        }
                    } else {
                        boolean flag = this.pathfindDirectlyTowards(Bee.this.hivePos);

                        if (!flag) {
                            this.dropAndBlacklistHive();
                        } else if (this.lastPath != null && Bee.this.navigation.getPath().sameAs(this.lastPath)) {
                            ++this.ticksStuck;
                            if (this.ticksStuck > 60) {
                                this.dropHive();
                                this.ticksStuck = 0;
                            }
                        } else {
                            this.lastPath = Bee.this.navigation.getPath();
                        }

                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(BlockPos pos) {
            Bee.this.navigation.setMaxVisitedNodesMultiplier(10.0F);
            Bee.this.navigation.moveTo((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), 1.0D);
            return Bee.this.navigation.getPath() != null && Bee.this.navigation.getPath().canReach();
        }

        boolean isTargetBlacklisted(BlockPos pos) {
            return this.blacklistedTargets.contains(pos);
        }

        private void blacklistTarget(BlockPos pos) {
            this.blacklistedTargets.add(pos);

            while (this.blacklistedTargets.size() > 3) {
                this.blacklistedTargets.remove(0);
            }

        }

        void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private void dropAndBlacklistHive() {
            if (Bee.this.hivePos != null) {
                this.blacklistTarget(Bee.this.hivePos);
            }

            this.dropHive();
        }

        private void dropHive() {
            Bee.this.hivePos = null;
            Bee.this.remainingCooldownBeforeLocatingNewHive = 200;
        }

        private boolean hasReachedTarget(BlockPos pos) {
            if (Bee.this.closerThan(pos, 2)) {
                return true;
            } else {
                Path pathentity = Bee.this.navigation.getPath();

                return pathentity != null && pathentity.getTarget().equals(pos) && pathentity.canReach() && pathentity.isDone();
            }
        }
    }

    public class BeeGoToKnownFlowerGoal extends Bee.BaseBeeGoal {

        private static final int MAX_TRAVELLING_TICKS = 600;
        int travellingTicks;

        BeeGoToKnownFlowerGoal() {
            super();
            this.travellingTicks = Bee.this.random.nextInt(10); // CraftBukkit - SPIGOT-7495: Give Bees another chance and let them use their own random, avoid concurrency issues
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            // Folia start - region threading
            if (Bee.this.savedFlowerPos != null && Bee.this.isTooFarAway(Bee.this.savedFlowerPos)) {
                Bee.this.savedFlowerPos = null;
            }
            // Folia end - region threading
            return Bee.this.savedFlowerPos != null && !Bee.this.hasRestriction() && this.wantsToGoToKnownFlower() && Bee.this.isFlowerValid(Bee.this.savedFlowerPos) && !Bee.this.closerThan(Bee.this.savedFlowerPos, 2);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            Bee.this.navigation.stop();
            Bee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (Bee.this.savedFlowerPos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > this.adjustedTickDelay(600)) {
                    Bee.this.savedFlowerPos = null;
                } else if (!Bee.this.navigation.isInProgress()) {
                    if (Bee.this.isTooFarAway(Bee.this.savedFlowerPos)) {
                        Bee.this.savedFlowerPos = null;
                    } else {
                        Bee.this.pathfindRandomlyTowards(Bee.this.savedFlowerPos);
                    }
                }
            }
        }

        private boolean wantsToGoToKnownFlower() {
            return Bee.this.ticksWithoutNectarSinceExitingHive > 2400;
        }
    }

    private class BeeGrowCropGoal extends Bee.BaseBeeGoal {

        static final int GROW_CHANCE = 30;

        BeeGrowCropGoal() {
            super();
        }

        @Override
        public boolean canBeeUse() {
            return Bee.this.getCropsGrownSincePollination() >= 10 ? false : (Bee.this.random.nextFloat() < 0.3F ? false : Bee.this.hasNectar() && Bee.this.isHiveValid());
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void tick() {
            if (Bee.this.random.nextInt(this.adjustedTickDelay(30)) == 0) {
                for (int i = 1; i <= 2; ++i) {
                    BlockPos blockposition = Bee.this.blockPosition().below(i);
                    BlockState iblockdata = Bee.this.level().getBlockState(blockposition);
                    Block block = iblockdata.getBlock();
                    BlockState iblockdata1 = null;

                    if (iblockdata.is(BlockTags.BEE_GROWABLES)) {
                        if (block instanceof CropBlock) {
                            CropBlock blockcrops = (CropBlock) block;

                            if (!blockcrops.isMaxAge(iblockdata)) {
                                iblockdata1 = blockcrops.getStateForAge(blockcrops.getAge(iblockdata) + 1);
                            }
                        } else {
                            int j;

                            if (block instanceof StemBlock) {
                                j = (Integer) iblockdata.getValue(StemBlock.AGE);
                                if (j < 7) {
                                    iblockdata1 = (BlockState) iblockdata.setValue(StemBlock.AGE, j + 1);
                                }
                            } else if (iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                                j = (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE);
                                if (j < 3) {
                                    iblockdata1 = (BlockState) iblockdata.setValue(SweetBerryBushBlock.AGE, j + 1);
                                }
                            } else if (iblockdata.is(Blocks.CAVE_VINES) || iblockdata.is(Blocks.CAVE_VINES_PLANT)) {
                                ((BonemealableBlock) iblockdata.getBlock()).performBonemeal((ServerLevel) Bee.this.level(), Bee.this.random, blockposition, iblockdata);
                            }
                        }

                        if (iblockdata1 != null && CraftEventFactory.callEntityChangeBlockEvent(Bee.this, blockposition, iblockdata1)) { // CraftBukkit
                            Bee.this.level().levelEvent(2005, blockposition, 0);
                            Bee.this.level().setBlockAndUpdate(blockposition, iblockdata1);
                            Bee.this.incrementNumCropsGrownSincePollination();
                        }
                    }
                }

            }
        }
    }

    private class BeeWanderGoal extends Goal {

        private static final int WANDER_THRESHOLD = 22;

        BeeWanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Bee.this.navigation.isDone() && Bee.this.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return Bee.this.navigation.isInProgress();
        }

        @Override
        public void start() {
            Vec3 vec3d = this.findPos();

            if (vec3d != null) {
                Bee.this.navigation.moveTo(Bee.this.navigation.createPath(BlockPos.containing(vec3d), 1), 1.0D);
            }

        }

        @Nullable
        private Vec3 findPos() {
            Vec3 vec3d;

            if (Bee.this.isHiveValid() && !Bee.this.closerThan(Bee.this.hivePos, 22)) {
                Vec3 vec3d1 = Vec3.atCenterOf(Bee.this.hivePos);

                vec3d = vec3d1.subtract(Bee.this.position()).normalize();
            } else {
                vec3d = Bee.this.getViewVector(0.0F);
            }

            boolean flag = true;
            Vec3 vec3d2 = HoverRandomPos.getPos(Bee.this, 8, 7, vec3d.x, vec3d.z, 1.5707964F, 3, 1);

            return vec3d2 != null ? vec3d2 : AirAndWaterRandomPos.getPos(Bee.this, 8, 4, -2, vec3d.x, vec3d.z, 1.5707963705062866D);
        }
    }

    private class BeeHurtByOtherGoal extends HurtByTargetGoal {

        BeeHurtByOtherGoal(Bee entitybee) {
            super(entitybee);
        }

        @Override
        public boolean canContinueToUse() {
            return Bee.this.isAngry() && super.canContinueToUse();
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (mob instanceof Bee && this.mob.hasLineOfSight(target)) {
                mob.setTarget(target, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit - reason
            }

        }
    }

    private static class BeeBecomeAngryTargetGoal extends NearestAttackableTargetGoal<Player> {

        BeeBecomeAngryTargetGoal(Bee bee) {
            // Objects.requireNonNull(entitybee); // CraftBukkit - decompile error
            super(bee, Player.class, 10, true, false, bee::isAngryAt);
        }

        @Override
        public boolean canUse() {
            return this.beeCanTarget() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            boolean flag = this.beeCanTarget();

            if (flag && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            } else {
                this.targetMob = null;
                return false;
            }
        }

        private boolean beeCanTarget() {
            Bee entitybee = (Bee) this.mob;

            return entitybee.isAngry() && !entitybee.hasStung();
        }
    }

    private abstract class BaseBeeGoal extends Goal {

        BaseBeeGoal() {}

        public abstract boolean canBeeUse();

        public abstract boolean canBeeContinueToUse();

        @Override
        public boolean canUse() {
            return this.canBeeUse() && !Bee.this.isAngry();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canBeeContinueToUse() && !Bee.this.isAngry();
        }
    }
}
