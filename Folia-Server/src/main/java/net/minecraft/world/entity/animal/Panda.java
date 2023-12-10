package net.minecraft.world.entity.animal;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// CraftBukkit start;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class Panda extends Animal {

    private static final EntityDataAccessor<Integer> UNHAPPY_COUNTER = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SNEEZE_COUNTER = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EAT_COUNTER = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MAIN_GENE_ID = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> HIDDEN_GENE_ID = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Panda.class, EntityDataSerializers.BYTE);
    static final TargetingConditions BREED_TARGETING = TargetingConditions.forNonCombat().range(8.0D);
    private static final int FLAG_SNEEZE = 2;
    private static final int FLAG_ROLL = 4;
    private static final int FLAG_SIT = 8;
    private static final int FLAG_ON_BACK = 16;
    private static final int EAT_TICK_INTERVAL = 5;
    public static final int TOTAL_ROLL_STEPS = 32;
    private static final int TOTAL_UNHAPPY_TIME = 32;
    boolean gotBamboo;
    boolean didBite;
    public int rollCounter;
    private Vec3 rollDelta;
    private float sitAmount;
    private float sitAmountO;
    private float onBackAmount;
    private float onBackAmountO;
    private float rollAmount;
    private float rollAmountO;
    Panda.PandaLookAtPlayerGoal lookAtPlayerGoal;
    static final Predicate<ItemEntity> PANDA_ITEMS = (entityitem) -> {
        ItemStack itemstack = entityitem.getItem();

        return (itemstack.is(Blocks.BAMBOO.asItem()) || itemstack.is(Blocks.CAKE.asItem())) && entityitem.isAlive() && !entityitem.hasPickUpDelay();
    };

    public Panda(EntityType<? extends Panda> type, Level world) {
        super(type, world);
        this.moveControl = new Panda.PandaMoveControl(this);
        if (!this.isBaby()) {
            this.setCanPickUpLoot(true);
        }

    }

    @Override
    public boolean canTakeItem(ItemStack stack) {
        EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(stack);

        return !this.getItemBySlot(enumitemslot).isEmpty() ? false : enumitemslot == EquipmentSlot.MAINHAND && super.canTakeItem(stack);
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(Panda.UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int askForBambooTicks) {
        this.entityData.set(Panda.UNHAPPY_COUNTER, askForBambooTicks);
    }

    public boolean isSneezing() {
        return this.getFlag(2);
    }

    public boolean isSitting() {
        return this.getFlag(8);
    }

    public void sit(boolean sitting) {
        if (!new io.papermc.paper.event.entity.EntityToggleSitEvent(this.getBukkitEntity(), sitting).callEvent()) return; // Paper start - call EntityToggleSitEvent
        this.setFlag(8, sitting);
    }

    public boolean isOnBack() {
        return this.getFlag(16);
    }

    public void setOnBack(boolean lyingOnBack) {
        this.setFlag(16, lyingOnBack);
    }

    public boolean isEating() {
        return (Integer) this.entityData.get(Panda.EAT_COUNTER) > 0;
    }

    public void eat(boolean eating) {
        this.entityData.set(Panda.EAT_COUNTER, eating ? 1 : 0);
    }

    public int getEatCounter() {
        return (Integer) this.entityData.get(Panda.EAT_COUNTER);
    }

    public void setEatCounter(int eatingTicks) {
        this.entityData.set(Panda.EAT_COUNTER, eatingTicks);
    }

    public void sneeze(boolean sneezing) {
        this.setFlag(2, sneezing);
        if (!sneezing) {
            this.setSneezeCounter(0);
        }

    }

    public int getSneezeCounter() {
        return (Integer) this.entityData.get(Panda.SNEEZE_COUNTER);
    }

    public void setSneezeCounter(int sneezeProgress) {
        this.entityData.set(Panda.SNEEZE_COUNTER, sneezeProgress);
    }

    public Panda.Gene getMainGene() {
        return Panda.Gene.byId((Byte) this.entityData.get(Panda.MAIN_GENE_ID));
    }

    public void setMainGene(Panda.Gene gene) {
        if (gene.getId() > 6) {
            gene = Panda.Gene.getRandom(this.random);
        }

        this.entityData.set(Panda.MAIN_GENE_ID, (byte) gene.getId());
    }

    public Panda.Gene getHiddenGene() {
        return Panda.Gene.byId((Byte) this.entityData.get(Panda.HIDDEN_GENE_ID));
    }

    public void setHiddenGene(Panda.Gene gene) {
        if (gene.getId() > 6) {
            gene = Panda.Gene.getRandom(this.random);
        }

        this.entityData.set(Panda.HIDDEN_GENE_ID, (byte) gene.getId());
    }

    public boolean isRolling() {
        return this.getFlag(4);
    }

    public void roll(boolean playing) {
        this.setFlag(4, playing);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Panda.UNHAPPY_COUNTER, 0);
        this.entityData.define(Panda.SNEEZE_COUNTER, 0);
        this.entityData.define(Panda.MAIN_GENE_ID, (byte) 0);
        this.entityData.define(Panda.HIDDEN_GENE_ID, (byte) 0);
        this.entityData.define(Panda.DATA_ID_FLAGS, (byte) 0);
        this.entityData.define(Panda.EAT_COUNTER, 0);
    }

    private boolean getFlag(int bitmask) {
        return ((Byte) this.entityData.get(Panda.DATA_ID_FLAGS) & bitmask) != 0;
    }

    private void setFlag(int mask, boolean value) {
        byte b0 = (Byte) this.entityData.get(Panda.DATA_ID_FLAGS);

        if (value) {
            this.entityData.set(Panda.DATA_ID_FLAGS, (byte) (b0 | mask));
        } else {
            this.entityData.set(Panda.DATA_ID_FLAGS, (byte) (b0 & ~mask));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("MainGene", this.getMainGene().getSerializedName());
        nbt.putString("HiddenGene", this.getHiddenGene().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setMainGene(Panda.Gene.byName(nbt.getString("MainGene")));
        this.setHiddenGene(Panda.Gene.byName(nbt.getString("HiddenGene")));
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Panda entitypanda = (Panda) EntityType.PANDA.create(world);

        if (entitypanda != null) {
            if (entity instanceof Panda) {
                Panda entitypanda1 = (Panda) entity;

                entitypanda.setGeneFromParents(this, entitypanda1);
            }

            entitypanda.setAttributes();
        }

        return entitypanda;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new Panda.PandaPanicGoal(this, 2.0D));
        this.goalSelector.addGoal(2, new Panda.PandaBreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new Panda.PandaAttackGoal(this, 1.2000000476837158D, true));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.0D, Ingredient.of(Blocks.BAMBOO.asItem()), false));
        this.goalSelector.addGoal(6, new Panda.PandaAvoidGoal<>(this, Player.class, 8.0F, 2.0D, 2.0D));
        this.goalSelector.addGoal(6, new Panda.PandaAvoidGoal<>(this, Monster.class, 4.0F, 2.0D, 2.0D));
        this.goalSelector.addGoal(7, new Panda.PandaSitGoal());
        this.goalSelector.addGoal(8, new Panda.PandaLieOnBackGoal(this));
        this.goalSelector.addGoal(8, new Panda.PandaSneezeGoal(this));
        this.lookAtPlayerGoal = new Panda.PandaLookAtPlayerGoal(this, Player.class, 6.0F);
        this.goalSelector.addGoal(9, this.lookAtPlayerGoal);
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(12, new Panda.PandaRollGoal(this));
        this.goalSelector.addGoal(13, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(14, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, (new Panda.PandaHurtByTargetGoal(this, new Class[0])).setAlertOthers(new Class[0]));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.15000000596046448D).add(Attributes.ATTACK_DAMAGE, 6.0D);
    }

    public Panda.Gene getVariant() {
        return Panda.Gene.getVariantFromGenes(this.getMainGene(), this.getHiddenGene());
    }

    public boolean isLazy() {
        return this.getVariant() == Panda.Gene.LAZY;
    }

    public boolean isWorried() {
        return this.getVariant() == Panda.Gene.WORRIED;
    }

    public boolean isPlayful() {
        return this.getVariant() == Panda.Gene.PLAYFUL;
    }

    public boolean isBrown() {
        return this.getVariant() == Panda.Gene.BROWN;
    }

    public boolean isWeak() {
        return this.getVariant() == Panda.Gene.WEAK;
    }

    @Override
    public boolean isAggressive() {
        return this.getVariant() == Panda.Gene.AGGRESSIVE;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.playSound(SoundEvents.PANDA_BITE, 1.0F, 1.0F);
        if (!this.isAggressive()) {
            this.didBite = true;
        }

        return super.doHurtTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isWorried()) {
            if (this.level().isThundering() && !this.isInWater()) {
                this.sit(true);
                this.eat(false);
            } else if (!this.isEating()) {
                this.sit(false);
            }
        }

        LivingEntity entityliving = this.getTarget();

        if (entityliving == null) {
            this.gotBamboo = false;
            this.didBite = false;
        }

        if (this.getUnhappyCounter() > 0) {
            if (entityliving != null) {
                this.lookAt(entityliving, 90.0F, 90.0F);
            }

            if (this.getUnhappyCounter() == 29 || this.getUnhappyCounter() == 14) {
                this.playSound(SoundEvents.PANDA_CANT_BREED, 1.0F, 1.0F);
            }

            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }

        if (this.isSneezing()) {
            this.setSneezeCounter(this.getSneezeCounter() + 1);
            if (this.getSneezeCounter() > 20) {
                this.sneeze(false);
                this.afterSneeze();
            } else if (this.getSneezeCounter() == 1) {
                this.playSound(SoundEvents.PANDA_PRE_SNEEZE, 1.0F, 1.0F);
            }
        }

        if (this.isRolling()) {
            this.handleRoll();
        } else {
            this.rollCounter = 0;
        }

        if (this.isSitting()) {
            this.setXRot(0.0F);
        }

        this.updateSitAmount();
        this.handleEating();
        this.updateOnBackAnimation();
        this.updateRollAmount();
    }

    public boolean isScared() {
        return this.isWorried() && this.level().isThundering();
    }

    private void handleEating() {
        if (!this.isEating() && this.isSitting() && !this.isScared() && !this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && this.random.nextInt(80) == 1) {
            this.eat(true);
        } else if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() || !this.isSitting()) {
            this.eat(false);
        }

        if (this.isEating()) {
            this.addEatingParticles();
            if (!this.level().isClientSide && this.getEatCounter() > 80 && this.random.nextInt(20) == 1) {
                if (this.getEatCounter() > 100 && this.isFoodOrCake(this.getItemBySlot(EquipmentSlot.MAINHAND))) {
                    if (!this.level().isClientSide) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        this.gameEvent(GameEvent.EAT);
                    }

                    this.sit(false);
                }

                this.eat(false);
                return;
            }

            this.setEatCounter(this.getEatCounter() + 1);
        }

    }

    private void addEatingParticles() {
        if (this.getEatCounter() % 5 == 0) {
            this.playSound(SoundEvents.PANDA_EAT, 0.5F + 0.5F * (float) this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);

            for (int i = 0; i < 6; ++i) {
                Vec3 vec3d = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, ((double) this.random.nextFloat() - 0.5D) * 0.1D);

                vec3d = vec3d.xRot(-this.getXRot() * 0.017453292F);
                vec3d = vec3d.yRot(-this.getYRot() * 0.017453292F);
                double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
                Vec3 vec3d1 = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.8D, d0, 1.0D + ((double) this.random.nextFloat() - 0.5D) * 0.4D);

                vec3d1 = vec3d1.yRot(-this.yBodyRot * 0.017453292F);
                vec3d1 = vec3d1.add(this.getX(), this.getEyeY() + 1.0D, this.getZ());
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItemBySlot(EquipmentSlot.MAINHAND)), vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
            }
        }

    }

    private void updateSitAmount() {
        this.sitAmountO = this.sitAmount;
        if (this.isSitting()) {
            this.sitAmount = Math.min(1.0F, this.sitAmount + 0.15F);
        } else {
            this.sitAmount = Math.max(0.0F, this.sitAmount - 0.19F);
        }

    }

    private void updateOnBackAnimation() {
        this.onBackAmountO = this.onBackAmount;
        if (this.isOnBack()) {
            this.onBackAmount = Math.min(1.0F, this.onBackAmount + 0.15F);
        } else {
            this.onBackAmount = Math.max(0.0F, this.onBackAmount - 0.19F);
        }

    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.15F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.19F);
        }

    }

    public float getSitAmount(float tickDelta) {
        return Mth.lerp(tickDelta, this.sitAmountO, this.sitAmount);
    }

    public float getLieOnBackAmount(float tickDelta) {
        return Mth.lerp(tickDelta, this.onBackAmountO, this.onBackAmount);
    }

    public float getRollAmount(float tickDelta) {
        return Mth.lerp(tickDelta, this.rollAmountO, this.rollAmount);
    }

    private void handleRoll() {
        ++this.rollCounter;
        if (this.rollCounter > 32) {
            this.roll(false);
        } else {
            if (!this.level().isClientSide) {
                Vec3 vec3d = this.getDeltaMovement();

                if (this.rollCounter == 1) {
                    float f = this.getYRot() * 0.017453292F;
                    float f1 = this.isBaby() ? 0.1F : 0.2F;

                    this.rollDelta = new Vec3(vec3d.x + (double) (-Mth.sin(f) * f1), 0.0D, vec3d.z + (double) (Mth.cos(f) * f1));
                    this.setDeltaMovement(this.rollDelta.add(0.0D, 0.27D, 0.0D));
                } else if ((float) this.rollCounter != 7.0F && (float) this.rollCounter != 15.0F && (float) this.rollCounter != 23.0F) {
                    this.setDeltaMovement(this.rollDelta.x, vec3d.y, this.rollDelta.z);
                } else {
                    this.setDeltaMovement(0.0D, this.onGround() ? 0.27D : vec3d.y, 0.0D);
                }
            }

        }
    }

    private void afterSneeze() {
        Vec3 vec3d = this.getDeltaMovement();

        this.level().addParticle(ParticleTypes.SNEEZE, this.getX() - (double) (this.getBbWidth() + 1.0F) * 0.5D * (double) Mth.sin(this.yBodyRot * 0.017453292F), this.getEyeY() - 0.10000000149011612D, this.getZ() + (double) (this.getBbWidth() + 1.0F) * 0.5D * (double) Mth.cos(this.yBodyRot * 0.017453292F), vec3d.x, 0.0D, vec3d.z);
        this.playSound(SoundEvents.PANDA_SNEEZE, 1.0F, 1.0F);
        List<Panda> list = this.level().getEntitiesOfClass(Panda.class, this.getBoundingBox().inflate(10.0D));
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Panda entitypanda = (Panda) iterator.next();

            if (!entitypanda.isBaby() && entitypanda.onGround() && !entitypanda.isInWater() && entitypanda.canPerformAction()) {
                if (new com.destroystokyo.paper.event.entity.EntityJumpEvent(getBukkitLivingEntity()).callEvent()) { // Paper
                entitypanda.jumpFromGround();
                } else { this.setJumping(false); } // Paper - setJumping(false) stops a potential loop
            }
        }

        if (!this.level().isClientSide() && this.random.nextInt(700) == 0 && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.forceDrops = true; // Paper
            this.spawnAtLocation((ItemLike) Items.SLIME_BALL);
            this.forceDrops = false; // Paper
        }

    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        if (!CraftEventFactory.callEntityPickupItemEvent(this, item, 0, !(this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && Panda.PANDA_ITEMS.test(item))).isCancelled()) { // CraftBukkit
            this.onItemPickup(item);
            ItemStack itemstack = item.getItem();

            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            this.take(item, itemstack.getCount());
            item.discard();
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            this.sit(false);
        }

        return super.hurt(source, amount);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        RandomSource randomsource = world.getRandom();

        this.setMainGene(Panda.Gene.getRandom(randomsource));
        this.setHiddenGene(Panda.Gene.getRandom(randomsource));
        this.setAttributes();
        if (entityData == null) {
            entityData = new AgeableMob.AgeableMobGroupData(0.2F);
        }

        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData, entityNbt);
    }

    public void setGeneFromParents(Panda mother, @Nullable Panda father) {
        if (father == null) {
            if (this.random.nextBoolean()) {
                this.setMainGene(mother.getOneOfGenesRandomly());
                this.setHiddenGene(Panda.Gene.getRandom(this.random));
            } else {
                this.setMainGene(Panda.Gene.getRandom(this.random));
                this.setHiddenGene(mother.getOneOfGenesRandomly());
            }
        } else if (this.random.nextBoolean()) {
            this.setMainGene(mother.getOneOfGenesRandomly());
            this.setHiddenGene(father.getOneOfGenesRandomly());
        } else {
            this.setMainGene(father.getOneOfGenesRandomly());
            this.setHiddenGene(mother.getOneOfGenesRandomly());
        }

        if (this.random.nextInt(32) == 0) {
            this.setMainGene(Panda.Gene.getRandom(this.random));
        }

        if (this.random.nextInt(32) == 0) {
            this.setHiddenGene(Panda.Gene.getRandom(this.random));
        }

    }

    private Panda.Gene getOneOfGenesRandomly() {
        return this.random.nextBoolean() ? this.getMainGene() : this.getHiddenGene();
    }

    public void setAttributes() {
        if (this.isWeak()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0D);
        }

        if (this.isLazy()) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.07000000029802322D);
        }

    }

    void tryToSit() {
        if (!this.isInWater()) {
            this.setZza(0.0F);
            this.getNavigation().stop();
            this.sit(true);
        }

    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (this.isScared()) {
            return InteractionResult.PASS;
        } else if (this.isOnBack()) {
            this.setOnBack(false);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else if (this.isFood(itemstack)) {
            if (this.getTarget() != null) {
                this.gotBamboo = true;
            }

            if (this.isBaby()) {
                this.usePlayerItem(player, hand, itemstack);
                this.ageUp((int) ((float) (-this.getAge() / 20) * 0.1F), true);
            } else if (!this.level().isClientSide && this.getAge() == 0 && this.canFallInLove()) {
                final ItemStack breedCopy = itemstack.copy(); // Paper
                this.usePlayerItem(player, hand, itemstack);
                this.setInLove(player, breedCopy); // Paper
            } else {
                if (this.level().isClientSide || this.isSitting() || this.isInWater()) {
                    return InteractionResult.PASS;
                }

                this.tryToSit();
                this.eat(true);
                ItemStack itemstack1 = this.getItemBySlot(EquipmentSlot.MAINHAND);

                if (!itemstack1.isEmpty() && !player.getAbilities().instabuild) {
                    this.forceDrops = true; // Paper
                    this.spawnAtLocation(itemstack1);
                    this.forceDrops = false; // Paper
                }

                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(itemstack.getItem(), 1));
                this.usePlayerItem(player, hand, itemstack);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAggressive() ? SoundEvents.PANDA_AGGRESSIVE_AMBIENT : (this.isWorried() ? SoundEvents.PANDA_WORRIED_AMBIENT : SoundEvents.PANDA_AMBIENT);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.PANDA_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Blocks.BAMBOO.asItem());
    }

    private boolean isFoodOrCake(ItemStack stack) {
        return this.isFood(stack) || stack.is(Blocks.CAKE.asItem());
    }

    @Nullable
    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PANDA_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PANDA_HURT;
    }

    public boolean canPerformAction() {
        return !this.isOnBack() && !this.isScared() && !this.isEating() && !this.isRolling() && !this.isSitting();
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height - (this.isBaby() ? 0.4375F : 0.0F) * scaleFactor, 0.0F);
    }

    private static class PandaMoveControl extends MoveControl {

        private final Panda panda;

        public PandaMoveControl(Panda panda) {
            super(panda);
            this.panda = panda;
        }

        @Override
        public void tick() {
            if (this.panda.canPerformAction()) {
                super.tick();
            }
        }
    }

    public static enum Gene implements StringRepresentable {

        NORMAL(0, "normal", false), LAZY(1, "lazy", false), WORRIED(2, "worried", false), PLAYFUL(3, "playful", false), BROWN(4, "brown", true), WEAK(5, "weak", true), AGGRESSIVE(6, "aggressive", false);

        public static final StringRepresentable.EnumCodec<Panda.Gene> CODEC = StringRepresentable.fromEnum(Panda.Gene::values);
        private static final IntFunction<Panda.Gene> BY_ID = ByIdMap.continuous(Panda.Gene::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private static final int MAX_GENE = 6;
        private final int id;
        private final String name;
        private final boolean isRecessive;

        private Gene(int i, String s, boolean flag) {
            this.id = i;
            this.name = s;
            this.isRecessive = flag;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public boolean isRecessive() {
            return this.isRecessive;
        }

        static Panda.Gene getVariantFromGenes(Panda.Gene mainGene, Panda.Gene hiddenGene) {
            return mainGene.isRecessive() ? (mainGene == hiddenGene ? mainGene : Panda.Gene.NORMAL) : mainGene;
        }

        public static Panda.Gene byId(int id) {
            return (Panda.Gene) Panda.Gene.BY_ID.apply(id);
        }

        public static Panda.Gene byName(String name) {
            return (Panda.Gene) Panda.Gene.CODEC.byName(name, Panda.Gene.NORMAL);
        }

        public static Panda.Gene getRandom(RandomSource random) {
            int i = random.nextInt(16);

            return i == 0 ? Panda.Gene.LAZY : (i == 1 ? Panda.Gene.WORRIED : (i == 2 ? Panda.Gene.PLAYFUL : (i == 4 ? Panda.Gene.AGGRESSIVE : (i < 9 ? Panda.Gene.WEAK : (i < 11 ? Panda.Gene.BROWN : Panda.Gene.NORMAL)))));
        }
    }

    private static class PandaPanicGoal extends PanicGoal {

        private final Panda panda;

        public PandaPanicGoal(Panda panda, double speed) {
            super(panda, speed);
            this.panda = panda;
        }

        @Override
        protected boolean shouldPanic() {
            return this.mob.isFreezing() || this.mob.isOnFire();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.panda.isSitting()) {
                this.panda.getNavigation().stop();
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }

    private static class PandaBreedGoal extends BreedGoal {

        private final Panda panda;
        private int unhappyCooldown;

        public PandaBreedGoal(Panda panda, double chance) {
            super(panda, chance);
            this.panda = panda;
        }

        @Override
        public boolean canUse() {
            if (super.canUse() && this.panda.getUnhappyCounter() == 0) {
                if (!this.canFindBamboo()) {
                    if (this.unhappyCooldown <= this.panda.tickCount) {
                        this.panda.setUnhappyCounter(32);
                        this.unhappyCooldown = this.panda.tickCount + 600;
                        if (this.panda.isEffectiveAi()) {
                            Player entityhuman = this.level.getNearestPlayer(Panda.BREED_TARGETING, this.panda);

                            this.panda.lookAtPlayerGoal.setTarget(entityhuman);
                        }
                    }

                    return false;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

        private boolean canFindBamboo() {
            BlockPos blockposition = this.panda.blockPosition();
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 8; ++j) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockposition_mutableblockposition.setWithOffset(blockposition, k, i, l);
                            if (this.level.getBlockState(blockposition_mutableblockposition).is(Blocks.BAMBOO)) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
    }

    private static class PandaAttackGoal extends MeleeAttackGoal {

        private final Panda panda;

        public PandaAttackGoal(Panda panda, double speed, boolean pauseWhenMobIdle) {
            super(panda, speed, pauseWhenMobIdle);
            this.panda = panda;
        }

        @Override
        public boolean canUse() {
            return this.panda.canPerformAction() && super.canUse();
        }
    }

    private static class PandaAvoidGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Panda panda;

        public PandaAvoidGoal(Panda panda, Class<T> fleeFromType, float distance, double slowSpeed, double fastSpeed) {
            // Predicate predicate = IEntitySelector.NO_SPECTATORS;

            // Objects.requireNonNull(predicate);
            super(panda, fleeFromType, distance, slowSpeed, fastSpeed, EntitySelector.NO_SPECTATORS::test);
            this.panda = panda;
        }

        @Override
        public boolean canUse() {
            return this.panda.isWorried() && this.panda.canPerformAction() && super.canUse();
        }
    }

    private class PandaSitGoal extends Goal {

        private int cooldown;

        public PandaSitGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown <= Panda.this.tickCount && !Panda.this.isBaby() && !Panda.this.isInWater() && Panda.this.canPerformAction() && Panda.this.getUnhappyCounter() <= 0) {
                List<ItemEntity> list = Panda.this.level().getEntitiesOfClass(ItemEntity.class, Panda.this.getBoundingBox().inflate(6.0D, 6.0D, 6.0D), Panda.PANDA_ITEMS);

                return !list.isEmpty() || !Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            } else {
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !Panda.this.isInWater() && (Panda.this.isLazy() || Panda.this.random.nextInt(reducedTickDelay(600)) != 1) ? Panda.this.random.nextInt(reducedTickDelay(2000)) != 1 : false;
        }

        @Override
        public void tick() {
            if (!Panda.this.isSitting() && !Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                Panda.this.tryToSit();
            }

        }

        @Override
        public void start() {
            List<ItemEntity> list = Panda.this.level().getEntitiesOfClass(ItemEntity.class, Panda.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Panda.PANDA_ITEMS);

            if (!list.isEmpty() && Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                Panda.this.getNavigation().moveTo((Entity) list.get(0), 1.2000000476837158D);
            } else if (!Panda.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                Panda.this.tryToSit();
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemstack = Panda.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                Panda.this.forceDrops = true; // Paper
                Panda.this.spawnAtLocation(itemstack);
                Panda.this.forceDrops = false; // Paper
                Panda.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                int i = Panda.this.isLazy() ? Panda.this.random.nextInt(50) + 10 : Panda.this.random.nextInt(150) + 10;

                this.cooldown = Panda.this.tickCount + i * 20;
            }

            Panda.this.sit(false);
        }
    }

    private static class PandaLieOnBackGoal extends Goal {

        private final Panda panda;
        private int cooldown;

        public PandaLieOnBackGoal(Panda panda) {
            this.panda = panda;
        }

        @Override
        public boolean canUse() {
            return this.cooldown < this.panda.tickCount && this.panda.isLazy() && this.panda.canPerformAction() && this.panda.random.nextInt(reducedTickDelay(400)) == 1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.panda.isInWater() && (this.panda.isLazy() || this.panda.random.nextInt(reducedTickDelay(600)) != 1) ? this.panda.random.nextInt(reducedTickDelay(2000)) != 1 : false;
        }

        @Override
        public void start() {
            this.panda.setOnBack(true);
            this.cooldown = 0;
        }

        @Override
        public void stop() {
            this.panda.setOnBack(false);
            this.cooldown = this.panda.tickCount + 200;
        }
    }

    private static class PandaSneezeGoal extends Goal {

        private final Panda panda;

        public PandaSneezeGoal(Panda panda) {
            this.panda = panda;
        }

        @Override
        public boolean canUse() {
            return this.panda.isBaby() && this.panda.canPerformAction() ? (this.panda.isWeak() && this.panda.random.nextInt(reducedTickDelay(500)) == 1 ? true : this.panda.random.nextInt(reducedTickDelay(6000)) == 1) : false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.panda.sneeze(true);
        }
    }

    private static class PandaLookAtPlayerGoal extends LookAtPlayerGoal {

        private final Panda panda;

        public PandaLookAtPlayerGoal(Panda panda, Class<? extends LivingEntity> targetType, float range) {
            super(panda, targetType, range);
            this.panda = panda;
        }

        public void setTarget(LivingEntity target) {
            this.lookAt = target;
        }

        @Override
        public boolean canContinueToUse() {
            return this.lookAt != null && super.canContinueToUse();
        }

        @Override
        public boolean canUse() {
            if (this.mob.getRandom().nextFloat() >= this.probability) {
                return false;
            } else {
                if (this.lookAt == null) {
                    if (this.lookAtType == Player.class) {
                        this.lookAt = this.mob.level().getNearestPlayer(this.lookAtContext, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
                    } else {
                        this.lookAt = this.mob.level().getNearestEntity(this.mob.level().getEntitiesOfClass(this.lookAtType, this.mob.getBoundingBox().inflate((double) this.lookDistance, 3.0D, (double) this.lookDistance), (entityliving) -> {
                            return true;
                        }), this.lookAtContext, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
                    }
                }

                return this.panda.canPerformAction() && this.lookAt != null;
            }
        }

        @Override
        public void tick() {
            if (this.lookAt != null) {
                super.tick();
            }

        }
    }

    private static class PandaRollGoal extends Goal {

        private final Panda panda;

        public PandaRollGoal(Panda panda) {
            this.panda = panda;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if ((this.panda.isBaby() || this.panda.isPlayful()) && this.panda.onGround()) {
                if (!this.panda.canPerformAction()) {
                    return false;
                } else {
                    float f = this.panda.getYRot() * 0.017453292F;
                    float f1 = -Mth.sin(f);
                    float f2 = Mth.cos(f);
                    int i = (double) Math.abs(f1) > 0.5D ? Mth.sign((double) f1) : 0;
                    int j = (double) Math.abs(f2) > 0.5D ? Mth.sign((double) f2) : 0;

                    return this.panda.level().getBlockState(this.panda.blockPosition().offset(i, -1, j)).isAir() ? true : (this.panda.isPlayful() && this.panda.random.nextInt(reducedTickDelay(60)) == 1 ? true : this.panda.random.nextInt(reducedTickDelay(500)) == 1);
                }
            } else {
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.panda.roll(true);
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
    }

    private static class PandaHurtByTargetGoal extends HurtByTargetGoal {

        private final Panda panda;

        public PandaHurtByTargetGoal(Panda panda, Class<?>... noRevengeTypes) {
            super(panda, noRevengeTypes);
            this.panda = panda;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.panda.gotBamboo && !this.panda.didBite) {
                return super.canContinueToUse();
            } else {
                this.panda.setTarget((LivingEntity) null);
                return false;
            }
        }

        @Override
        protected void alertOther(Mob mob, LivingEntity target) {
            if (mob instanceof Panda && mob.isAggressive()) {
                mob.setTarget(target, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit
            }

        }
    }
}
