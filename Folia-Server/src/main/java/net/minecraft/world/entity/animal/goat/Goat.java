package net.minecraft.world.entity.animal.goat;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerBucketFillEvent;
// CraftBukkit end

public class Goat extends Animal {

    public static final EntityDimensions LONG_JUMPING_DIMENSIONS = EntityDimensions.scalable(0.9F, 1.3F).scale(0.7F);
    private static final int ADULT_ATTACK_DAMAGE = 2;
    private static final int BABY_ATTACK_DAMAGE = 1;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Goat>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.GOAT_TEMPTATIONS);
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATE_RECENTLY, MemoryModuleType.BREED_TARGET, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, new MemoryModuleType[]{MemoryModuleType.IS_TEMPTED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleType.RAM_TARGET, MemoryModuleType.IS_PANICKING});
    public static final int GOAT_FALL_DAMAGE_REDUCTION = 10;
    public static final double GOAT_SCREAMING_CHANCE = 0.02D;
    public static final double UNIHORN_CHANCE = 0.10000000149011612D;
    private static final EntityDataAccessor<Boolean> DATA_IS_SCREAMING_GOAT = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_HAS_LEFT_HORN = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_HAS_RIGHT_HORN = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    private boolean isLoweringHead;
    private int lowerHeadTick;

    public Goat(EntityType<? extends Goat> type, Level world) {
        super(type, world);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(BlockPathTypes.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_POWDER_SNOW, -1.0F);
    }

    public ItemStack createHorn() {
        RandomSource randomsource = RandomSource.create((long) this.getUUID().hashCode());
        TagKey<Instrument> tagkey = this.isScreamingGoat() ? InstrumentTags.SCREAMING_GOAT_HORNS : InstrumentTags.REGULAR_GOAT_HORNS;
        HolderSet<Instrument> holderset = BuiltInRegistries.INSTRUMENT.getOrCreateTag(tagkey);

        return InstrumentItem.create(Items.GOAT_HORN, (Holder) holderset.getRandomElement(randomsource).get());
    }

    @Override
    protected Brain.Provider<Goat> brainProvider() {
        return Brain.provider(Goat.MEMORY_TYPES, Goat.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return GoatAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.20000000298023224D).add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
            this.removeHorns();
        } else {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
            this.addHorns();
        }

    }

    @Override
    protected int calculateFallDamage(float fallDistance, float damageMultiplier) {
        return super.calculateFallDamage(fallDistance, damageMultiplier) - 10;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_AMBIENT : SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_HURT : SoundEvents.GOAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_DEATH : SoundEvents.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.GOAT_STEP, 0.15F, 1.0F);
    }

    protected SoundEvent getMilkingSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK;
    }

    @Nullable
    @Override
    public Goat getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Goat goat = (Goat) EntityType.GOAT.create(world);

        if (goat != null) {
            boolean flag;
            label22:
            {
                label21:
                {
                    GoatAi.initMemories(goat, world.getRandom());
                    Object object = world.getRandom().nextBoolean() ? this : entity;

                    if (object instanceof Goat) {
                        Goat goat1 = (Goat) object;

                        if (goat1.isScreamingGoat()) {
                            break label21;
                        }
                    }

                    if (world.getRandom().nextDouble() >= 0.02D) {
                        flag = false;
                        break label22;
                    }
                }

                flag = true;
            }

            boolean flag1 = flag;

            goat.setScreamingGoat(flag1);
        }

        return goat;
    }

    @Override
    public Brain<Goat> getBrain() {
        return (Brain<Goat>) super.getBrain(); // CraftBukkit - decompile error
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("goatBrain");
        this.getBrain().tick((ServerLevel) this.level(), this);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("goatActivityUpdate");
        GoatAi.updateActivity(this);
        this.level().getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    public int getMaxHeadYRot() {
        return 15;
    }

    @Override
    public void setYHeadRot(float headYaw) {
        int i = this.getMaxHeadYRot();
        float f1 = Mth.degreesDifference(this.yBodyRot, headYaw);
        float f2 = Mth.clamp(f1, (float) (-i), (float) i);

        super.setYHeadRot(this.yBodyRot + f2);
    }

    @Override
    public SoundEvent getEatingSound(ItemStack stack) {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_EAT : SoundEvents.GOAT_EAT;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BUCKET) && !this.isBaby()) {
            // CraftBukkit start - Got milk?
            PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent((ServerLevel) player.level(), player, this.blockPosition(), this.blockPosition(), null, itemstack, Items.MILK_BUCKET, hand);

            if (event.isCancelled()) {
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            player.playSound(this.getMilkingSound(), 1.0F, 1.0F);
            ItemStack itemstack1 = ItemUtils.createFilledResult(itemstack, player, CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit

            player.setItemInHand(hand, itemstack1);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else {
            boolean isFood = this.isFood(itemstack); // Paper - track before stack is possibly decreased to 0 (Fixes MC-244739)
            InteractionResult enuminteractionresult = super.mobInteract(player, hand);

            if (enuminteractionresult.consumesAction() && isFood) { // Paper
                this.level().playSound((Player) null, (Entity) this, this.getEatingSound(itemstack), SoundSource.NEUTRAL, 1.0F, Mth.randomBetween(this.level().random, 0.8F, 1.2F));
            }

            return enuminteractionresult;
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        RandomSource randomsource = world.getRandom();

        GoatAi.initMemories(this, randomsource);
        this.setScreamingGoat(randomsource.nextDouble() < 0.02D);
        this.ageBoundaryReached();
        if (!this.isBaby() && (double) randomsource.nextFloat() < 0.10000000149011612D) {
            EntityDataAccessor<Boolean> datawatcherobject = randomsource.nextBoolean() ? Goat.DATA_HAS_LEFT_HORN : Goat.DATA_HAS_RIGHT_HORN;

            this.entityData.set(datawatcherobject, false);
        }

        return super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.LONG_JUMPING ? Goat.LONG_JUMPING_DIMENSIONS.scale(this.getScale()) : super.getDimensions(pose);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("IsScreamingGoat", this.isScreamingGoat());
        nbt.putBoolean("HasLeftHorn", this.hasLeftHorn());
        nbt.putBoolean("HasRightHorn", this.hasRightHorn());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setScreamingGoat(nbt.getBoolean("IsScreamingGoat"));
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, nbt.getBoolean("HasLeftHorn"));
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, nbt.getBoolean("HasRightHorn"));
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 58) {
            this.isLoweringHead = true;
        } else if (status == 59) {
            this.isLoweringHead = false;
        } else {
            super.handleEntityEvent(status);
        }

    }

    @Override
    public void aiStep() {
        if (this.isLoweringHead) {
            ++this.lowerHeadTick;
        } else {
            this.lowerHeadTick -= 2;
        }

        this.lowerHeadTick = Mth.clamp(this.lowerHeadTick, 0, 20);
        super.aiStep();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Goat.DATA_IS_SCREAMING_GOAT, false);
        this.entityData.define(Goat.DATA_HAS_LEFT_HORN, true);
        this.entityData.define(Goat.DATA_HAS_RIGHT_HORN, true);
    }

    public boolean hasLeftHorn() {
        return (Boolean) this.entityData.get(Goat.DATA_HAS_LEFT_HORN);
    }

    public boolean hasRightHorn() {
        return (Boolean) this.entityData.get(Goat.DATA_HAS_RIGHT_HORN);
    }

    public boolean dropHorn() {
        boolean flag = this.hasLeftHorn();
        boolean flag1 = this.hasRightHorn();

        if (!flag && !flag1) {
            return false;
        } else {
            EntityDataAccessor datawatcherobject;

            if (!flag) {
                datawatcherobject = Goat.DATA_HAS_RIGHT_HORN;
            } else if (!flag1) {
                datawatcherobject = Goat.DATA_HAS_LEFT_HORN;
            } else {
                datawatcherobject = this.random.nextBoolean() ? Goat.DATA_HAS_LEFT_HORN : Goat.DATA_HAS_RIGHT_HORN;
            }

            this.entityData.set(datawatcherobject, false);
            Vec3 vec3d = this.position();
            ItemStack itemstack = this.createHorn();
            double d0 = (double) Mth.randomBetween(this.random, -0.2F, 0.2F);
            double d1 = (double) Mth.randomBetween(this.random, 0.3F, 0.7F);
            double d2 = (double) Mth.randomBetween(this.random, -0.2F, 0.2F);
            ItemEntity entityitem = new ItemEntity(this.level(), vec3d.x(), vec3d.y(), vec3d.z(), itemstack, d0, d1, d2);

            return this.spawnAtLocation(entityitem) != null; // Paper - call EntityDropItemEvent by calling spawnAtLocation.
        }
    }

    public void addHorns() {
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, true);
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, true);
    }

    public void removeHorns() {
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, false);
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, false);
    }

    public boolean isScreamingGoat() {
        return (Boolean) this.entityData.get(Goat.DATA_IS_SCREAMING_GOAT);
    }

    public void setScreamingGoat(boolean screaming) {
        this.entityData.set(Goat.DATA_IS_SCREAMING_GOAT, screaming);
    }

    public float getRammingXHeadRot() {
        return (float) this.lowerHeadTick / 20.0F * 30.0F * 0.017453292F;
    }

    public static boolean checkGoatSpawnRules(EntityType<? extends Animal> entityType, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getBlockState(pos.below()).is(BlockTags.GOATS_SPAWNABLE_ON) && isBrightEnoughToSpawn(world, pos);
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height - 0.1875F * scaleFactor, 0.0F);
    }

    // Paper start - Goat ram API
    public void ram(net.minecraft.world.entity.LivingEntity entity) {
        Brain<Goat> brain = this.getBrain();
        brain.setMemory(MemoryModuleType.RAM_TARGET, entity.position());
        brain.eraseMemory(MemoryModuleType.RAM_COOLDOWN_TICKS);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        brain.setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.RAM);
    }
    // Paper end
}
