package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.JumpGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class Fox extends Animal implements VariantHolder<Fox.Type> {

    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.BYTE);
    private static final int FLAG_SITTING = 1;
    public static final int FLAG_CROUCHING = 4;
    public static final int FLAG_INTERESTED = 8;
    public static final int FLAG_POUNCING = 16;
    private static final int FLAG_SLEEPING = 32;
    private static final int FLAG_FACEPLANTED = 64;
    private static final int FLAG_DEFENDING = 128;
    public static final EntityDataAccessor<Optional<UUID>> DATA_TRUSTED_ID_0 = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final EntityDataAccessor<Optional<UUID>> DATA_TRUSTED_ID_1 = SynchedEntityData.defineId(Fox.class, EntityDataSerializers.OPTIONAL_UUID);
    static final Predicate<ItemEntity> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive();
    };
    private static final Predicate<Entity> TRUSTED_TARGET_SELECTOR = (entity) -> {
        if (!(entity instanceof LivingEntity)) {
            return false;
        } else {
            LivingEntity entityliving = (LivingEntity) entity;

            return entityliving.getLastHurtMob() != null && entityliving.getLastHurtMobTimestamp() < entityliving.tickCount + 600;
        }
    };
    static final Predicate<Entity> STALKABLE_PREY = (entity) -> {
        return entity instanceof Chicken || entity instanceof Rabbit;
    };
    private static final Predicate<Entity> AVOID_PLAYERS = (entity) -> {
        return !entity.isDiscrete() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    };
    private static final int MIN_TICKS_BEFORE_EAT = 600;
    private Goal landTargetGoal;
    private Goal turtleEggTargetGoal;
    private Goal fishTargetGoal;
    private float interestedAngle;
    private float interestedAngleO;
    float crouchAmount;
    float crouchAmountO;
    private int ticksSinceEaten;

    public Fox(EntityType<? extends Fox> type, Level world) {
        super(type, world);
        this.lookControl = new Fox.FoxLookControl();
        this.moveControl = new Fox.FoxMoveControl();
        this.setPathfindingMalus(BlockPathTypes.DANGER_OTHER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_OTHER, 0.0F);
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Fox.DATA_TRUSTED_ID_0, Optional.empty());
        this.entityData.define(Fox.DATA_TRUSTED_ID_1, Optional.empty());
        this.entityData.define(Fox.DATA_TYPE_ID, 0);
        this.entityData.define(Fox.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.landTargetGoal = new NearestAttackableTargetGoal<>(this, Animal.class, 10, false, false, (entityliving) -> {
            return entityliving instanceof Chicken || entityliving instanceof Rabbit;
        });
        this.turtleEggTargetGoal = new NearestAttackableTargetGoal<>(this, Turtle.class, 10, false, false, Turtle.BABY_ON_LAND_SELECTOR);
        this.fishTargetGoal = new NearestAttackableTargetGoal<>(this, AbstractFish.class, 20, false, false, (entityliving) -> {
            return entityliving instanceof AbstractSchoolingFish;
        });
        this.goalSelector.addGoal(0, new Fox.FoxFloatGoal());
        this.goalSelector.addGoal(0, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new Fox.FaceplantGoal());
        this.goalSelector.addGoal(2, new Fox.FoxPanicGoal(2.2D));
        this.goalSelector.addGoal(3, new Fox.FoxBreedGoal(1.0D));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 16.0F, 1.6D, 1.4D, (entityliving) -> {
            return Fox.AVOID_PLAYERS.test(entityliving) && !this.trusts(entityliving.getUUID()) && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Wolf.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
            return !((Wolf) entityliving).isTame() && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, PolarBear.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
            return !this.isDefending();
        }));
        this.goalSelector.addGoal(5, new Fox.StalkPreyGoal());
        this.goalSelector.addGoal(6, new Fox.FoxPounceGoal());
        this.goalSelector.addGoal(6, new Fox.SeekShelterGoal(1.25D));
        this.goalSelector.addGoal(7, new Fox.FoxMeleeAttackGoal(1.2000000476837158D, true));
        this.goalSelector.addGoal(7, new Fox.SleepGoal());
        this.goalSelector.addGoal(8, new Fox.FoxFollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(9, new Fox.FoxStrollThroughVillageGoal(32, 200));
        this.goalSelector.addGoal(10, new Fox.FoxEatBerriesGoal(1.2000000476837158D, 12, 1));
        this.goalSelector.addGoal(10, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(11, new Fox.FoxSearchForItemsGoal());
        this.goalSelector.addGoal(12, new Fox.FoxLookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(13, new Fox.PerchAndSearchGoal());
        this.targetSelector.addGoal(3, new Fox.DefendTrustedTargetGoal(LivingEntity.class, false, false, (entityliving) -> {
            return Fox.TRUSTED_TARGET_SELECTOR.test(entityliving) && !this.trusts(entityliving.getUUID());
        }));
    }

    @Override
    public SoundEvent getEatingSound(ItemStack stack) {
        return SoundEvents.FOX_EAT;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && this.isEffectiveAi()) {
            ++this.ticksSinceEaten;
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (this.canEat(itemstack)) {
                if (this.ticksSinceEaten > 600) {
                    ItemStack itemstack1 = itemstack.finishUsingItem(this.level(), this);

                    if (!itemstack1.isEmpty()) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, itemstack1);
                    }

                    this.ticksSinceEaten = 0;
                } else if (this.ticksSinceEaten > 560 && this.random.nextFloat() < 0.1F) {
                    this.playSound(this.getEatingSound(itemstack), 1.0F, 1.0F);
                    this.level().broadcastEntityEvent(this, (byte) 45);
                }
            }

            LivingEntity entityliving = this.getTarget();

            if (entityliving == null || !entityliving.isAlive()) {
                this.setIsCrouching(false);
                this.setIsInterested(false);
            }
        }

        if (this.isSleeping() || this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }

        super.aiStep();
        if (this.isDefending() && this.random.nextFloat() < 0.05F) {
            this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
        }

    }

    @Override
    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    private boolean canEat(ItemStack stack) {
        return stack.getItem().isEdible() && this.getTarget() == null && this.onGround() && !this.isSleeping();
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance localDifficulty) {
        if (random.nextFloat() < 0.2F) {
            float f = random.nextFloat();
            ItemStack itemstack;

            if (f < 0.05F) {
                itemstack = new ItemStack(Items.EMERALD);
            } else if (f < 0.2F) {
                itemstack = new ItemStack(Items.EGG);
            } else if (f < 0.4F) {
                itemstack = random.nextBoolean() ? new ItemStack(Items.RABBIT_FOOT) : new ItemStack(Items.RABBIT_HIDE);
            } else if (f < 0.6F) {
                itemstack = new ItemStack(Items.WHEAT);
            } else if (f < 0.8F) {
                itemstack = new ItemStack(Items.LEATHER);
            } else {
                itemstack = new ItemStack(Items.FEATHER);
            }

            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 45) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                for (int i = 0; i < 8; ++i) {
                    Vec3 vec3d = (new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D)).xRot(-this.getXRot() * 0.017453292F).yRot(-this.getYRot() * 0.017453292F);

                    this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, itemstack), this.getX() + this.getLookAngle().x / 2.0D, this.getY(), this.getZ() + this.getLookAngle().z / 2.0D, vec3d.x, vec3d.y + 0.05D, vec3d.z);
                }
            }
        } else {
            super.handleEntityEvent(status);
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Nullable
    @Override
    public Fox getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Fox entityfox = (Fox) EntityType.FOX.create(world);

        if (entityfox != null) {
            entityfox.setVariant(this.random.nextBoolean() ? this.getVariant() : ((Fox) entity).getVariant());
        }

        return entityfox;
    }

    public static boolean checkFoxSpawnRules(EntityType<Fox> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getBlockState(pos.below()).is(BlockTags.FOXES_SPAWNABLE_ON) && isBrightEnoughToSpawn(world, pos);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        Holder<Biome> holder = world.getBiome(this.blockPosition());
        Fox.Type entityfox_type = Fox.Type.byBiome(holder);
        boolean flag = false;

        if (entityData instanceof Fox.FoxGroupData) {
            Fox.FoxGroupData entityfox_i = (Fox.FoxGroupData) entityData;

            entityfox_type = entityfox_i.type;
            if (entityfox_i.getGroupSize() >= 2) {
                flag = true;
            }
        } else {
            entityData = new Fox.FoxGroupData(entityfox_type);
        }

        this.setVariant(entityfox_type);
        if (flag) {
            this.setAge(-24000);
        }

        if (world instanceof ServerLevel) {
            this.setTargetGoals();
        }

        this.populateDefaultEquipmentSlots(world.getRandom(), difficulty);
        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData, entityNbt);
    }

    private void setTargetGoals() {
        if (this.getVariant() == Fox.Type.RED) {
            this.targetSelector.addGoal(4, this.landTargetGoal);
            this.targetSelector.addGoal(4, this.turtleEggTargetGoal);
            this.targetSelector.addGoal(6, this.fishTargetGoal);
        } else {
            this.targetSelector.addGoal(4, this.fishTargetGoal);
            this.targetSelector.addGoal(6, this.landTargetGoal);
            this.targetSelector.addGoal(6, this.turtleEggTargetGoal);
        }

    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (this.isFood(stack)) {
            this.playSound(this.getEatingSound(stack), 1.0F, 1.0F);
        }

        super.usePlayerItem(player, hand, stack);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return this.isBaby() ? dimensions.height * 0.85F : 0.4F;
    }

    @Override
    public Fox.Type getVariant() {
        return Fox.Type.byId((Integer) this.entityData.get(Fox.DATA_TYPE_ID));
    }

    public void setVariant(Fox.Type variant) {
        this.entityData.set(Fox.DATA_TYPE_ID, variant.getId());
    }

    List<UUID> getTrustedUUIDs() {
        List<UUID> list = Lists.newArrayList();

        list.add((UUID) ((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_0)).orElse((Object) null));
        list.add((UUID) ((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_1)).orElse((Object) null));
        return list;
    }

    void addTrustedUUID(@Nullable UUID uuid) {
        if (((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_0)).isPresent()) {
            this.entityData.set(Fox.DATA_TRUSTED_ID_1, Optional.ofNullable(uuid));
        } else {
            this.entityData.set(Fox.DATA_TRUSTED_ID_0, Optional.ofNullable(uuid));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        List<UUID> list = this.getTrustedUUIDs();
        ListTag nbttaglist = new ListTag();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            UUID uuid = (UUID) iterator.next();

            if (uuid != null) {
                nbttaglist.add(NbtUtils.createUUID(uuid));
            }
        }

        nbt.put("Trusted", nbttaglist);
        nbt.putBoolean("Sleeping", this.isSleeping());
        nbt.putString("Type", this.getVariant().getSerializedName());
        nbt.putBoolean("Sitting", this.isSitting());
        nbt.putBoolean("Crouching", this.isCrouching());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ListTag nbttaglist = nbt.getList("Trusted", 11);
        Iterator iterator = nbttaglist.iterator();

        while (iterator.hasNext()) {
            Tag nbtbase = (Tag) iterator.next();

            this.addTrustedUUID(NbtUtils.loadUUID(nbtbase));
        }

        this.setSleeping(nbt.getBoolean("Sleeping"));
        this.setVariant(Fox.Type.byName(nbt.getString("Type")));
        this.setSitting(nbt.getBoolean("Sitting"), false); // Paper
        this.setIsCrouching(nbt.getBoolean("Crouching"));
        if (this.level() instanceof ServerLevel) {
            this.setTargetGoals();
        }

    }

    public boolean isSitting() {
        return this.getFlag(1);
    }

    public void setSitting(boolean sitting) {
        this.setSitting(sitting, true);
    }
    // Paper start
    public void setSitting(boolean sitting, boolean fireEvent) {
        if (fireEvent && !new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), sitting).callEvent()) return;
    // Paper end
        this.setFlag(1, sitting);
    }

    public boolean isFaceplanted() {
        return this.getFlag(64);
    }

    public void setFaceplanted(boolean walking) {
        this.setFlag(64, walking);
    }

    public boolean isDefending() {
        return this.getFlag(128);
    }

    public void setDefending(boolean aggressive) {
        this.setFlag(128, aggressive);
    }

    @Override
    public boolean isSleeping() {
        return this.getFlag(32);
    }

    public void setSleeping(boolean sleeping) {
        this.setFlag(32, sleeping);
    }

    private void setFlag(int mask, boolean value) {
        if (value) {
            this.entityData.set(Fox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) | mask));
        } else {
            this.entityData.set(Fox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) & ~mask));
        }

    }

    private boolean getFlag(int bitmask) {
        return ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) & bitmask) != 0;
    }

    @Override
    public boolean canTakeItem(ItemStack stack) {
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(stack);

        return !this.getItemBySlot(enumitemslot).isEmpty() ? false : enumitemslot == EquipmentSlot.MAINHAND && super.canTakeItem(stack);
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        Item item = stack.getItem();
        ItemStack itemstack1 = this.getItemBySlot(EquipmentSlot.MAINHAND);

        return itemstack1.isEmpty() || this.ticksSinceEaten > 0 && item.isEdible() && !itemstack1.getItem().isEdible();
    }

    private void spitOutItem(ItemStack stack) {
        if (!stack.isEmpty() && !this.level().isClientSide) {
            ItemEntity entityitem = new ItemEntity(this.level(), this.getX() + this.getLookAngle().x, this.getY() + 1.0D, this.getZ() + this.getLookAngle().z, stack);

            entityitem.setPickUpDelay(40);
            entityitem.setThrower(this.getUUID());
            this.playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
            this.spawnAtLocation(entityitem); // Paper - call EntityDropItemEvent
        }
    }

    private void dropItemStack(ItemStack stack) {
        ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), stack);

        this.spawnAtLocation(entityitem); // Paper - call EntityDropItemEvent
    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        ItemStack itemstack = item.getItem();

        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, item, itemstack.getCount() - 1, !this.canHoldItem(itemstack)).isCancelled()) { // CraftBukkit - call EntityPickupItemEvent
            itemstack = item.getItem(); // CraftBukkit - update ItemStack from event
            int i = itemstack.getCount();

            if (i > 1) {
                this.dropItemStack(itemstack.split(i - 1));
            }

            this.spitOutItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
            this.onItemPickup(item);
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack.split(1));
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            this.take(item, itemstack.getCount());
            item.discard();
            this.ticksSinceEaten = 0;
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isEffectiveAi()) {
            boolean flag = this.isInWater();

            if (flag || this.getTarget() != null || this.level().isThundering()) {
                this.wakeUp();
            }

            if (flag || this.isSleeping()) {
                this.setSitting(false);
            }

            if (this.isFaceplanted() && this.level().random.nextFloat() < 0.2F) {
                BlockPos blockposition = this.blockPosition();
                BlockState iblockdata = this.level().getBlockState(blockposition);

                this.level().levelEvent(2001, blockposition, Block.getId(iblockdata));
            }
        }

        this.interestedAngleO = this.interestedAngle;
        if (this.isInterested()) {
            this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
        } else {
            this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
        }

        this.crouchAmountO = this.crouchAmount;
        if (this.isCrouching()) {
            this.crouchAmount += 0.2F;
            if (this.crouchAmount > 3.0F) {
                this.crouchAmount = 3.0F;
            }
        } else {
            this.crouchAmount = 0.0F;
        }

    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FOX_FOOD);
    }

    @Override
    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
        ((Fox) child).addTrustedUUID(player.getUUID());
    }

    public boolean isPouncing() {
        return this.getFlag(16);
    }

    public void setIsPouncing(boolean chasing) {
        this.setFlag(16, chasing);
    }

    public boolean isJumping() {
        return this.jumping;
    }

    public boolean isFullyCrouched() {
        return this.crouchAmount == 3.0F;
    }

    public void setIsCrouching(boolean crouching) {
        this.setFlag(4, crouching);
    }

    @Override
    public boolean isCrouching() {
        return this.getFlag(4);
    }

    public void setIsInterested(boolean rollingHead) {
        this.setFlag(8, rollingHead);
    }

    public boolean isInterested() {
        return this.getFlag(8);
    }

    public float getHeadRollAngle(float tickDelta) {
        return Mth.lerp(tickDelta, this.interestedAngleO, this.interestedAngle) * 0.11F * 3.1415927F;
    }

    public float getCrouchAmount(float tickDelta) {
        return Mth.lerp(tickDelta, this.crouchAmountO, this.crouchAmount);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (this.isDefending() && target == null) {
            this.setDefending(false);
        }

        super.setTarget(target);
    }

    @Override
    protected int calculateFallDamage(float fallDistance, float damageMultiplier) {
        return Mth.ceil((fallDistance - 5.0F) * damageMultiplier);
    }

    void wakeUp() {
        this.setSleeping(false);
    }

    void clearStates() {
        this.setIsInterested(false);
        this.setIsCrouching(false);
        this.setSitting(false);
        this.setSleeping(false);
        this.setDefending(false);
        this.setFaceplanted(false);
    }

    boolean canMove() {
        return !this.isSleeping() && !this.isSitting() && !this.isFaceplanted();
    }

    @Override
    public void playAmbientSound() {
        SoundEvent soundeffect = this.getAmbientSound();

        if (soundeffect == SoundEvents.FOX_SCREECH) {
            this.playSound(soundeffect, 2.0F, this.getVoicePitch());
        } else {
            super.playAmbientSound();
        }

    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEvents.FOX_SLEEP;
        } else {
            if (!this.level().isDay() && this.random.nextFloat() < 0.1F) {
                List<Player> list = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0D, 16.0D, 16.0D), EntitySelector.NO_SPECTATORS);

                if (list.isEmpty()) {
                    return SoundEvents.FOX_SCREECH;
                }
            }

            return SoundEvents.FOX_AMBIENT;
        }
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Nullable
    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    boolean trusts(UUID uuid) {
        return this.getTrustedUUIDs().contains(uuid);
    }

    @Override
    // Paper start - Cancellable death event
    protected org.bukkit.event.entity.EntityDeathEvent dropAllDeathLoot(DamageSource source) {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND).copy(); // Paper - modified by supercall

        org.bukkit.event.entity.EntityDeathEvent deathEvent = super.dropAllDeathLoot(source);

        // Below is code to drop

        if (deathEvent == null || deathEvent.isCancelled()) {
            return deathEvent;
        }
        // Paper end

        if (!itemstack.isEmpty()) {
            this.spawnAtLocation(itemstack);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        return deathEvent; // Paper
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height + -0.0625F * scaleFactor, -0.25F * scaleFactor);
    }

    public static boolean isPathClear(Fox fox, LivingEntity chasedEntity) {
        double d0 = chasedEntity.getZ() - fox.getZ();
        double d1 = chasedEntity.getX() - fox.getX();
        double d2 = d0 / d1;
        boolean flag = true;

        for (int i = 0; i < 6; ++i) {
            double d3 = d2 == 0.0D ? 0.0D : d0 * (double) ((float) i / 6.0F);
            double d4 = d2 == 0.0D ? d1 * (double) ((float) i / 6.0F) : d3 / d2;

            for (int j = 1; j < 4; ++j) {
                if (!fox.level().getBlockState(BlockPos.containing(fox.getX() + d4, fox.getY() + (double) j, fox.getZ() + d3)).canBeReplaced()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.55F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public class FoxLookControl extends LookControl {

        public FoxLookControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (!Fox.this.isSleeping()) {
                super.tick();
            }

        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Fox.this.isPouncing() && !Fox.this.isCrouching() && !Fox.this.isInterested() && !Fox.this.isFaceplanted();
        }
    }

    private class FoxMoveControl extends MoveControl {

        public FoxMoveControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (Fox.this.canMove()) {
                super.tick();
            }

        }
    }

    private class FoxFloatGoal extends FloatGoal {

        public FoxFloatGoal() {
            super(Fox.this);
        }

        @Override
        public void start() {
            super.start();
            Fox.this.clearStates();
        }

        @Override
        public boolean canUse() {
            return Fox.this.isInWater() && Fox.this.getFluidHeight(FluidTags.WATER) > 0.25D || Fox.this.isInLava();
        }
    }

    private class FaceplantGoal extends Goal {

        int countdown;

        public FaceplantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Fox.this.isFaceplanted();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && this.countdown > 0;
        }

        @Override
        public void start() {
            this.countdown = this.adjustedTickDelay(40);
        }

        @Override
        public void stop() {
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void tick() {
            --this.countdown;
        }
    }

    private class FoxPanicGoal extends PanicGoal {

        public FoxPanicGoal(double d0) {
            super(Fox.this, d0);
        }

        @Override
        public boolean shouldPanic() {
            return !Fox.this.isDefending() && super.shouldPanic();
        }
    }

    private class FoxBreedGoal extends BreedGoal {

        public FoxBreedGoal(double d0) {
            super(Fox.this, d0);
        }

        @Override
        public void start() {
            ((Fox) this.animal).clearStates();
            ((Fox) this.partner).clearStates();
            super.start();
        }

        @Override
        protected void breed() {
            ServerLevel worldserver = (ServerLevel) this.level;
            Fox entityfox = (Fox) this.animal.getBreedOffspring(worldserver, this.partner);

            if (entityfox != null) {
                ServerPlayer entityplayer = this.animal.getLoveCause();
                ServerPlayer entityplayer1 = this.partner.getLoveCause();
                ServerPlayer entityplayer2 = entityplayer;

                if (entityplayer != null) {
                    entityfox.addTrustedUUID(entityplayer.getUUID());
                } else {
                    entityplayer2 = entityplayer1;
                }

                if (entityplayer1 != null && entityplayer != entityplayer1) {
                    entityfox.addTrustedUUID(entityplayer1.getUUID());
                }
                // CraftBukkit start - call EntityBreedEvent
                entityfox.setAge(-24000);
                entityfox.moveTo(this.animal.getX(), this.animal.getY(), this.animal.getZ(), 0.0F, 0.0F);
                int experience = this.animal.getRandom().nextInt(7) + 1;
                org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityfox, this.animal, this.partner, entityplayer, this.animal.breedItem, experience);
                if (entityBreedEvent.isCancelled()) {
                    return;
                }
                experience = entityBreedEvent.getExperience();
                // CraftBukkit end

                if (entityplayer2 != null) {
                    entityplayer2.awardStat(Stats.ANIMALS_BRED);
                    CriteriaTriggers.BRED_ANIMALS.trigger(entityplayer2, this.animal, this.partner, entityfox);
                }

                this.animal.setAge(6000);
                this.partner.setAge(6000);
                this.animal.resetLove();
                this.partner.resetLove();
                worldserver.addFreshEntityWithPassengers(entityfox, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - added SpawnReason
                this.level.broadcastEntityEvent(this.animal, (byte) 18);
                if (this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                    // CraftBukkit start - use event experience
                    if (experience > 0) {
                        this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), experience, org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, entityplayer, entityfox)); // Paper
                    }
                    // CraftBukkit end
                }

            }
        }
    }

    private class StalkPreyGoal extends Goal {

        public StalkPreyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (Fox.this.isSleeping()) {
                return false;
            } else {
                LivingEntity entityliving = Fox.this.getTarget();

                return entityliving != null && entityliving.isAlive() && Fox.STALKABLE_PREY.test(entityliving) && Fox.this.distanceToSqr((Entity) entityliving) > 36.0D && !Fox.this.isCrouching() && !Fox.this.isInterested() && !Fox.this.jumping;
            }
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void stop() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null && Fox.isPathClear(Fox.this, entityliving)) {
                Fox.this.setIsInterested(true);
                Fox.this.setIsCrouching(true);
                Fox.this.getNavigation().stop();
                Fox.this.getLookControl().setLookAt(entityliving, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
            } else {
                Fox.this.setIsInterested(false);
                Fox.this.setIsCrouching(false);
            }

        }

        @Override
        public void tick() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null) {
                Fox.this.getLookControl().setLookAt(entityliving, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
                if (Fox.this.distanceToSqr((Entity) entityliving) <= 36.0D) {
                    Fox.this.setIsInterested(true);
                    Fox.this.setIsCrouching(true);
                    Fox.this.getNavigation().stop();
                } else {
                    Fox.this.getNavigation().moveTo((Entity) entityliving, 1.5D);
                }

            }
        }
    }

    public class FoxPounceGoal extends JumpGoal {

        public FoxPounceGoal() {}

        @Override
        public boolean canUse() {
            if (!Fox.this.isFullyCrouched()) {
                return false;
            } else {
                LivingEntity entityliving = Fox.this.getTarget();

                if (entityliving != null && entityliving.isAlive()) {
                    if (entityliving.getMotionDirection() != entityliving.getDirection()) {
                        return false;
                    } else {
                        boolean flag = Fox.isPathClear(Fox.this, entityliving);

                        if (!flag) {
                            Fox.this.getNavigation().createPath((Entity) entityliving, 0);
                            Fox.this.setIsCrouching(false);
                            Fox.this.setIsInterested(false);
                        }

                        return flag;
                    }
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null && entityliving.isAlive()) {
                double d0 = Fox.this.getDeltaMovement().y;

                return (d0 * d0 >= 0.05000000074505806D || Math.abs(Fox.this.getXRot()) >= 15.0F || !Fox.this.onGround()) && !Fox.this.isFaceplanted();
            } else {
                return false;
            }
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            Fox.this.setJumping(true);
            Fox.this.setIsPouncing(true);
            Fox.this.setIsInterested(false);
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null) {
                Fox.this.getLookControl().setLookAt(entityliving, 60.0F, 30.0F);
                Vec3 vec3d = (new Vec3(entityliving.getX() - Fox.this.getX(), entityliving.getY() - Fox.this.getY(), entityliving.getZ() - Fox.this.getZ())).normalize();

                Fox.this.setDeltaMovement(Fox.this.getDeltaMovement().add(vec3d.x * 0.8D, 0.9D, vec3d.z * 0.8D));
            }

            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setIsCrouching(false);
            Fox.this.crouchAmount = 0.0F;
            Fox.this.crouchAmountO = 0.0F;
            Fox.this.setIsInterested(false);
            Fox.this.setIsPouncing(false);
        }

        @Override
        public void tick() {
            LivingEntity entityliving = Fox.this.getTarget();

            if (entityliving != null) {
                Fox.this.getLookControl().setLookAt(entityliving, 60.0F, 30.0F);
            }

            if (!Fox.this.isFaceplanted()) {
                Vec3 vec3d = Fox.this.getDeltaMovement();

                if (vec3d.y * vec3d.y < 0.029999999329447746D && Fox.this.getXRot() != 0.0F) {
                    Fox.this.setXRot(Mth.rotLerp(0.2F, Fox.this.getXRot(), 0.0F));
                } else {
                    double d0 = vec3d.horizontalDistance();
                    double d1 = Math.signum(-vec3d.y) * Math.acos(d0 / vec3d.length()) * 57.2957763671875D;

                    Fox.this.setXRot((float) d1);
                }
            }

            if (entityliving != null && Fox.this.distanceTo(entityliving) <= 2.0F) {
                Fox.this.doHurtTarget(entityliving);
            } else if (Fox.this.getXRot() > 0.0F && Fox.this.onGround() && (float) Fox.this.getDeltaMovement().y != 0.0F && Fox.this.level().getBlockState(Fox.this.blockPosition()).is(Blocks.SNOW)) {
                Fox.this.setXRot(60.0F);
                Fox.this.setTarget((LivingEntity) null);
                Fox.this.setFaceplanted(true);
            }

        }
    }

    private class SeekShelterGoal extends FleeSunGoal {

        private int interval = reducedTickDelay(100);

        public SeekShelterGoal(double d0) {
            super(Fox.this, d0);
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.isSleeping() && this.mob.getTarget() == null) {
                if (Fox.this.level().isThundering() && Fox.this.level().canSeeSky(this.mob.blockPosition())) {
                    return this.setWantedPos();
                } else if (this.interval > 0) {
                    --this.interval;
                    return false;
                } else {
                    this.interval = 100;
                    BlockPos blockposition = this.mob.blockPosition();

                    return Fox.this.level().isDay() && Fox.this.level().canSeeSky(blockposition) && !((ServerLevel) Fox.this.level()).isVillage(blockposition) && this.setWantedPos();
                }
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }
    }

    private class FoxMeleeAttackGoal extends MeleeAttackGoal {

        public FoxMeleeAttackGoal(double d0, boolean flag) {
            super(Fox.this, d0, flag);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (this.canPerformAttack(target)) {
                this.resetAttackCooldown();
                this.mob.doHurtTarget(target);
                Fox.this.playSound(SoundEvents.FOX_BITE, 1.0F, 1.0F);
            }

        }

        @Override
        public void start() {
            Fox.this.setIsInterested(false);
            super.start();
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSitting() && !Fox.this.isSleeping() && !Fox.this.isCrouching() && !Fox.this.isFaceplanted() && super.canUse();
        }
    }

    private class SleepGoal extends Fox.FoxBehaviorGoal {

        private static final int WAIT_TIME_BEFORE_SLEEP = reducedTickDelay(140);
        private int countdown;

        public SleepGoal() {
            super();
            this.countdown = Fox.this.random.nextInt(Fox.SleepGoal.WAIT_TIME_BEFORE_SLEEP);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return Fox.this.xxa == 0.0F && Fox.this.yya == 0.0F && Fox.this.zza == 0.0F ? this.canSleep() || Fox.this.isSleeping() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canSleep();
        }

        private boolean canSleep() {
            if (this.countdown > 0) {
                --this.countdown;
                return false;
            } else {
                return Fox.this.level().isDay() && this.hasShelter() && !this.alertable() && !Fox.this.isInPowderSnow;
            }
        }

        @Override
        public void stop() {
            this.countdown = Fox.this.random.nextInt(Fox.SleepGoal.WAIT_TIME_BEFORE_SLEEP);
            Fox.this.clearStates();
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setIsCrouching(false);
            Fox.this.setIsInterested(false);
            Fox.this.setJumping(false);
            Fox.this.setSleeping(true);
            Fox.this.getNavigation().stop();
            Fox.this.getMoveControl().setWantedPosition(Fox.this.getX(), Fox.this.getY(), Fox.this.getZ(), 0.0D);
        }
    }

    private class FoxFollowParentGoal extends FollowParentGoal {

        private final Fox fox;

        public FoxFollowParentGoal(Fox entityfox, double d0) {
            super(entityfox, d0);
            this.fox = entityfox;
        }

        @Override
        public boolean canUse() {
            return !this.fox.isDefending() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.fox.isDefending() && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.fox.clearStates();
            super.start();
        }
    }

    private class FoxStrollThroughVillageGoal extends StrollThroughVillageGoal {

        public FoxStrollThroughVillageGoal(int i, int j) {
            super(Fox.this, j);
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.canFoxMove();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.canFoxMove();
        }

        private boolean canFoxMove() {
            return !Fox.this.isSleeping() && !Fox.this.isSitting() && !Fox.this.isDefending() && Fox.this.getTarget() == null;
        }
    }

    public class FoxEatBerriesGoal extends MoveToBlockGoal {

        private static final int WAIT_TICKS = 40;
        protected int ticksWaited;

        public FoxEatBerriesGoal(double d0, int i, int j) {
            super(Fox.this, d0, i, j);
        }

        @Override
        public double acceptedDistance() {
            return 2.0D;
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 100 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader world, BlockPos pos) {
            BlockState iblockdata = world.getBlockState(pos);

            return iblockdata.is(Blocks.SWEET_BERRY_BUSH) && (Integer) iblockdata.getValue(SweetBerryBushBlock.AGE) >= 2 || CaveVines.hasGlowBerries(iblockdata);
        }

        @Override
        public void tick() {
            if (this.isReachedTarget()) {
                if (this.ticksWaited >= 40) {
                    this.onReachedTarget();
                } else {
                    ++this.ticksWaited;
                }
            } else if (!this.isReachedTarget() && Fox.this.random.nextFloat() < 0.05F) {
                Fox.this.playSound(SoundEvents.FOX_SNIFF, 1.0F, 1.0F);
            }

            super.tick();
        }

        protected void onReachedTarget() {
            if (Fox.this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                BlockState iblockdata = Fox.this.level().getBlockState(this.blockPos);

                if (iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                    this.pickSweetBerries(iblockdata);
                } else if (CaveVines.hasGlowBerries(iblockdata)) {
                    this.pickGlowBerry(iblockdata);
                }

            }
        }

        private void pickGlowBerry(BlockState state) {
            CaveVines.use(Fox.this, state, Fox.this.level(), this.blockPos);
        }

        private void pickSweetBerries(BlockState state) {
            int i = (Integer) state.getValue(SweetBerryBushBlock.AGE);

            state.setValue(SweetBerryBushBlock.AGE, 1);
            // CraftBukkit start - call EntityChangeBlockEvent
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(Fox.this, this.blockPos, state.setValue(SweetBerryBushBlock.AGE, 1))) {
                return;
            }
            // CraftBukkit end
            int j = 1 + Fox.this.level().random.nextInt(2) + (i == 3 ? 1 : 0);
            ItemStack itemstack = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (itemstack.isEmpty()) {
                Fox.this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
                --j;
            }

            if (j > 0) {
                Block.popResource(Fox.this.level(), this.blockPos, new ItemStack(Items.SWEET_BERRIES, j));
            }

            Fox.this.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
            Fox.this.level().setBlock(this.blockPos, (BlockState) state.setValue(SweetBerryBushBlock.AGE, 1), 2);
            Fox.this.level().gameEvent(GameEvent.BLOCK_CHANGE, this.blockPos, GameEvent.Context.of((Entity) Fox.this));
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSleeping() && super.canUse();
        }

        @Override
        public void start() {
            this.ticksWaited = 0;
            Fox.this.setSitting(false);
            super.start();
        }
    }

    private class FoxSearchForItemsGoal extends Goal {

        public FoxSearchForItemsGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                return false;
            } else if (Fox.this.getTarget() == null && Fox.this.getLastHurtByMob() == null) {
                if (!Fox.this.canMove()) {
                    return false;
                } else if (Fox.this.getRandom().nextInt(reducedTickDelay(10)) != 0) {
                    return false;
                } else {
                    List<ItemEntity> list = Fox.this.level().getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);

                    return !list.isEmpty() && Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
                }
            } else {
                return false;
            }
        }

        @Override
        public void tick() {
            List<ItemEntity> list = Fox.this.level().getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);
            ItemStack itemstack = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (itemstack.isEmpty() && !list.isEmpty()) {
                Fox.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            }

        }

        @Override
        public void start() {
            List<ItemEntity> list = Fox.this.level().getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                Fox.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            }

        }
    }

    private class FoxLookAtPlayerGoal extends LookAtPlayerGoal {

        public FoxLookAtPlayerGoal(Mob mob, Class targetType, float range) {
            super(mob, targetType, range);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }
    }

    private class PerchAndSearchGoal extends Fox.FoxBehaviorGoal {

        private double relX;
        private double relZ;
        private int lookTime;
        private int looksRemaining;

        public PerchAndSearchGoal() {
            super();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return Fox.this.getLastHurtByMob() == null && Fox.this.getRandom().nextFloat() < 0.02F && !Fox.this.isSleeping() && Fox.this.getTarget() == null && Fox.this.getNavigation().isDone() && !this.alertable() && !Fox.this.isPouncing() && !Fox.this.isCrouching();
        }

        @Override
        public boolean canContinueToUse() {
            return this.looksRemaining > 0;
        }

        @Override
        public void start() {
            this.resetLook();
            this.looksRemaining = 2 + Fox.this.getRandom().nextInt(3);
            Fox.this.setSitting(true);
            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setSitting(false);
        }

        @Override
        public void tick() {
            --this.lookTime;
            if (this.lookTime <= 0) {
                --this.looksRemaining;
                this.resetLook();
            }

            Fox.this.getLookControl().setLookAt(Fox.this.getX() + this.relX, Fox.this.getEyeY(), Fox.this.getZ() + this.relZ, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
        }

        private void resetLook() {
            double d0 = 6.283185307179586D * Fox.this.getRandom().nextDouble();

            this.relX = Math.cos(d0);
            this.relZ = Math.sin(d0);
            this.lookTime = this.adjustedTickDelay(80 + Fox.this.getRandom().nextInt(20));
        }
    }

    private class DefendTrustedTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

        @Nullable
        private LivingEntity trustedLastHurtBy;
        @Nullable
        private LivingEntity trustedLastHurt;
        private int timestamp;

        public DefendTrustedTargetGoal(Class oclass, boolean flag, boolean flag1, @Nullable Predicate<LivingEntity> predicate) { // CraftBukkit - decompile error
            super(Fox.this, oclass, 10, flag, flag1, predicate);
        }

        @Override
        public boolean canUse() {
            if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
                return false;
            } else {
                Iterator iterator = Fox.this.getTrustedUUIDs().iterator();

                while (iterator.hasNext()) {
                    UUID uuid = (UUID) iterator.next();

                    if (uuid != null && Fox.this.level() instanceof ServerLevel) {
                        Entity entity = ((ServerLevel) Fox.this.level()).getEntity(uuid);

                        if (entity instanceof LivingEntity) {
                            LivingEntity entityliving = (LivingEntity) entity;

                            this.trustedLastHurt = entityliving;
                            this.trustedLastHurtBy = entityliving.getLastHurtByMob();
                            int i = entityliving.getLastHurtByMobTimestamp();

                            return i != this.timestamp && this.canAttack(this.trustedLastHurtBy, this.targetConditions);
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public void start() {
            this.setTarget(this.trustedLastHurtBy);
            this.target = this.trustedLastHurtBy;
            if (this.trustedLastHurt != null) {
                this.timestamp = this.trustedLastHurt.getLastHurtByMobTimestamp();
            }

            Fox.this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
            Fox.this.setDefending(true);
            Fox.this.wakeUp();
            super.start();
        }
    }

    public static enum Type implements StringRepresentable {

        RED(0, "red"), SNOW(1, "snow");

        public static final StringRepresentable.EnumCodec<Fox.Type> CODEC = StringRepresentable.fromEnum(Fox.Type::values);
        private static final IntFunction<Fox.Type> BY_ID = ByIdMap.continuous(Fox.Type::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private final int id;
        private final String name;

        private Type(int i, String s) {
            this.id = i;
            this.name = s;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public static Fox.Type byName(String name) {
            return (Fox.Type) Fox.Type.CODEC.byName(name, Fox.Type.RED);
        }

        public static Fox.Type byId(int id) {
            return (Fox.Type) Fox.Type.BY_ID.apply(id);
        }

        public static Fox.Type byBiome(Holder<Biome> biome) {
            return biome.is(BiomeTags.SPAWNS_SNOW_FOXES) ? Fox.Type.SNOW : Fox.Type.RED;
        }
    }

    public static class FoxGroupData extends AgeableMob.AgeableMobGroupData {

        public final Fox.Type type;

        public FoxGroupData(Fox.Type type) {
            super(false);
            this.type = type;
        }
    }

    private abstract class FoxBehaviorGoal extends Goal {

        private final TargetingConditions alertableTargeting = TargetingConditions.forCombat().range(12.0D).ignoreLineOfSight().selector(Fox.this.new FoxAlertableEntitiesSelector());

        FoxBehaviorGoal() {}

        protected boolean hasShelter() {
            BlockPos blockposition = BlockPos.containing(Fox.this.getX(), Fox.this.getBoundingBox().maxY, Fox.this.getZ());

            return !Fox.this.level().canSeeSky(blockposition) && Fox.this.getWalkTargetValue(blockposition) >= 0.0F;
        }

        protected boolean alertable() {
            return !Fox.this.level().getNearbyEntities(LivingEntity.class, this.alertableTargeting, Fox.this, Fox.this.getBoundingBox().inflate(12.0D, 6.0D, 12.0D)).isEmpty();
        }
    }

    public class FoxAlertableEntitiesSelector implements Predicate<LivingEntity> {

        public FoxAlertableEntitiesSelector() {}

        public boolean test(LivingEntity entityliving) {
            return entityliving instanceof Fox ? false : (!(entityliving instanceof Chicken) && !(entityliving instanceof Rabbit) && !(entityliving instanceof Monster) ? (entityliving instanceof TamableAnimal ? !((TamableAnimal) entityliving).isTame() : (entityliving instanceof Player && (entityliving.isSpectator() || ((Player) entityliving).isCreative()) ? false : (Fox.this.trusts(entityliving.getUUID()) ? false : !entityliving.isSleeping() && !entityliving.isDiscrete()))) : true);
        }
    }
}
