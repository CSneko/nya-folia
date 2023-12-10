package net.minecraft.world.entity.animal.horse;

import com.mojang.serialization.Codec;
import java.util.Iterator;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class Llama extends AbstractChestedHorse implements VariantHolder<Llama.Variant>, RangedAttackMob {

    private static final int MAX_STRENGTH = 5;
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.WHEAT, Blocks.HAY_BLOCK.asItem());
    private static final EntityDataAccessor<Integer> DATA_STRENGTH_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SWAG_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.defineId(Llama.class, EntityDataSerializers.INT);
    boolean didSpit;
    @Nullable
    private Llama caravanHead;
    @Nullable
    public Llama caravanTail; // Paper

    public Llama(EntityType<? extends Llama> type, Level world) {
        super(type, world);
    }

    public boolean isTraderLlama() {
        return false;
    }

    // CraftBukkit start
    public void setStrengthPublic(int i) {
        this.setStrength(i);
    }
    // CraftBukkit end
    private void setStrength(int strength) {
        this.entityData.set(Llama.DATA_STRENGTH_ID, Math.max(1, Math.min(5, strength)));
    }

    private void setRandomStrength(RandomSource random) {
        int i = random.nextFloat() < 0.04F ? 5 : 3;

        this.setStrength(1 + random.nextInt(i));
    }

    public int getStrength() {
        return (Integer) this.entityData.get(Llama.DATA_STRENGTH_ID);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Variant", this.getVariant().id);
        nbt.putInt("Strength", this.getStrength());
        if (!this.inventory.getItem(1).isEmpty()) {
            nbt.put("DecorItem", this.inventory.getItem(1).save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.setStrength(nbt.getInt("Strength"));
        super.readAdditionalSaveData(nbt);
        this.setVariant(Llama.Variant.byId(nbt.getInt("Variant")));
        if (nbt.contains("DecorItem", 10)) {
            this.inventory.setItem(1, ItemStack.of(nbt.getCompound("DecorItem")));
        }

        this.updateContainerEquipment();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new LlamaFollowCaravanGoal(this, 2.0999999046325684D));
        this.goalSelector.addGoal(3, new RangedAttackGoal(this, 1.25D, 40, 20.0F));
        this.goalSelector.addGoal(3, new PanicGoal(this, 1.2D));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.25D, Ingredient.of(Items.HAY_BLOCK), false));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new Llama.LlamaHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Llama.LlamaAttackWolfGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseChestedHorseAttributes().add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Llama.DATA_STRENGTH_ID, 0);
        this.entityData.define(Llama.DATA_SWAG_ID, -1);
        this.entityData.define(Llama.DATA_VARIANT_ID, 0);
    }

    @Override
    public Llama.Variant getVariant() {
        return Llama.Variant.byId((Integer) this.entityData.get(Llama.DATA_VARIANT_ID));
    }

    public void setVariant(Llama.Variant variant) {
        this.entityData.set(Llama.DATA_VARIANT_ID, variant.id);
    }

    @Override
    protected int getInventorySize() {
        return this.hasChest() ? 2 + 3 * this.getInventoryColumns() : super.getInventorySize();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return Llama.FOOD_ITEMS.test(stack);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack item) {
        byte b0 = 0;
        byte b1 = 0;
        float f = 0.0F;
        boolean flag = false;

        if (item.is(Items.WHEAT)) {
            b0 = 10;
            b1 = 3;
            f = 2.0F;
        } else if (item.is(Blocks.HAY_BLOCK.asItem())) {
            b0 = 90;
            b1 = 6;
            f = 10.0F;
            if (this.isTamed() && this.getAge() == 0 && this.canFallInLove()) {
                flag = true;
                this.setInLove(player, item.copy()); // Paper
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING); // Paper
            flag = true;
        }

        if (this.isBaby() && b0 > 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.level().isClientSide) {
                this.ageUp(b0);
            }

            flag = true;
        }

        if (b1 > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper()) {
            flag = true;
            if (!this.level().isClientSide) {
                this.modifyTemper(b1);
            }
        }

        if (flag && !this.isSilent()) {
            SoundEvent soundeffect = this.getEatingSound();

            if (soundeffect != null) {
                this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), this.getEatingSound(), this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

        return flag;
    }

    @Override
    public boolean isImmobile() {
        return this.isDeadOrDying() || this.isEating();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        RandomSource randomsource = world.getRandom();

        this.setRandomStrength(randomsource);
        Llama.Variant entityllama_variant;

        if (entityData instanceof Llama.LlamaGroupData) {
            entityllama_variant = ((Llama.LlamaGroupData) entityData).variant;
        } else {
            entityllama_variant = (Llama.Variant) Util.getRandom((Object[]) Llama.Variant.values(), randomsource);
            entityData = new Llama.LlamaGroupData(entityllama_variant);
        }

        this.setVariant(entityllama_variant);
        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData, entityNbt);
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.LLAMA_ANGRY;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.LLAMA_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.LLAMA_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.LLAMA_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.LLAMA_EAT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.LLAMA_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.LLAMA_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.getStrength();
    }

    @Override
    public boolean canWearArmor() {
        return true;
    }

    @Override
    public boolean isWearingArmor() {
        return !this.inventory.getItem(1).isEmpty();
    }

    @Override
    public boolean isArmor(ItemStack item) {
        return item.is(ItemTags.WOOL_CARPETS);
    }

    @Override
    public boolean isSaddleable() {
        return false;
    }

    @Override
    public void containerChanged(Container sender) {
        DyeColor enumcolor = this.getSwag();

        super.containerChanged(sender);
        DyeColor enumcolor1 = this.getSwag();

        if (this.tickCount > 20 && enumcolor1 != null && enumcolor1 != enumcolor) {
            this.playSound(SoundEvents.LLAMA_SWAG, 0.5F, 1.0F);
        }

    }

    @Override
    protected void updateContainerEquipment() {
        if (!this.level().isClientSide) {
            super.updateContainerEquipment();
            this.setSwag(Llama.getDyeColor(this.inventory.getItem(1)));
        }
    }

    private void setSwag(@Nullable DyeColor color) {
        this.entityData.set(Llama.DATA_SWAG_ID, color == null ? -1 : color.getId());
    }

    @Nullable
    private static DyeColor getDyeColor(ItemStack color) {
        Block block = Block.byItem(color.getItem());

        return block instanceof WoolCarpetBlock ? ((WoolCarpetBlock) block).getColor() : null;
    }

    @Nullable
    public DyeColor getSwag() {
        int i = (Integer) this.entityData.get(Llama.DATA_SWAG_ID);

        return i == -1 ? null : DyeColor.byId(i);
    }

    @Override
    public int getMaxTemper() {
        return 30;
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && other instanceof Llama && this.canParent() && ((Llama) other).canParent();
    }

    @Nullable
    @Override
    public Llama getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Llama entityllama = this.makeNewLlama();

        if (entityllama != null) {
            this.setOffspringAttributes(entity, entityllama);
            Llama entityllama1 = (Llama) entity;
            int i = this.random.nextInt(Math.max(this.getStrength(), entityllama1.getStrength())) + 1;

            if (this.random.nextFloat() < 0.03F) {
                ++i;
            }

            entityllama.setStrength(i);
            entityllama.setVariant(this.random.nextBoolean() ? this.getVariant() : entityllama1.getVariant());
        }

        return entityllama;
    }

    @Nullable
    protected Llama makeNewLlama() {
        return (Llama) EntityType.LLAMA.create(this.level());
    }

    private void spit(LivingEntity target) {
        LlamaSpit entityllamaspit = new LlamaSpit(this.level(), this);
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333D) - entityllamaspit.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2) * 0.20000000298023224D;

        entityllamaspit.shoot(d0, d1 + d3, d2, 1.5F, 10.0F);
        if (!this.isSilent()) {
            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.LLAMA_SPIT, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }

        this.level().addFreshEntity(entityllamaspit);
        this.didSpit = true;
    }

    void setDidSpit(boolean spit) {
        this.didSpit = spit;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        int i = this.calculateFallDamage(fallDistance, damageMultiplier);

        if (i <= 0) {
            return false;
        } else {
            if (fallDistance >= 6.0F) {
                this.hurt(damageSource, (float) i);
                if (this.isVehicle()) {
                    Iterator iterator = this.getIndirectPassengers().iterator();

                    while (iterator.hasNext()) {
                        Entity entity = (Entity) iterator.next();

                        entity.hurt(damageSource, (float) i);
                    }
                }
            }

            this.playBlockFallSound();
            return true;
        }
    }

    public void leaveCaravan() {
        if (this.caravanHead != null) {
            this.caravanHead.caravanTail = null;
        }

        this.caravanHead = null;
    }

    public void joinCaravan(Llama llama) {
        this.caravanHead = llama;
        this.caravanHead.caravanTail = this;
    }

    public boolean hasCaravanTail() {
        return this.caravanTail != null;
    }

    public boolean inCaravan() {
        return this.caravanHead != null;
    }

    @Nullable
    public Llama getCaravanHead() {
        return this.caravanHead;
    }

    @Override
    protected double followLeashSpeed() {
        return 2.0D;
    }

    @Override
    protected void followMommy() {
        if (!this.inCaravan() && this.isBaby()) {
            super.followMommy();
        }

    }

    @Override
    public boolean canEatGrass() {
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        this.spit(target);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, 0.75D * (double) this.getEyeHeight(), (double) this.getBbWidth() * 0.5D);
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height - (this.isBaby() ? 0.8125F : 0.5F) * scaleFactor, -0.3F * scaleFactor);
    }

    public static enum Variant implements StringRepresentable {

        CREAMY(0, "creamy"), WHITE(1, "white"), BROWN(2, "brown"), GRAY(3, "gray");

        public static final Codec<Llama.Variant> CODEC = StringRepresentable.fromEnum(Llama.Variant::values);
        private static final IntFunction<Llama.Variant> BY_ID = ByIdMap.continuous(Llama.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        final int id;
        private final String name;

        private Variant(int i, String s) {
            this.id = i;
            this.name = s;
        }

        public int getId() {
            return this.id;
        }

        public static Llama.Variant byId(int id) {
            return (Llama.Variant) Llama.Variant.BY_ID.apply(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    private static class LlamaHurtByTargetGoal extends HurtByTargetGoal {

        public LlamaHurtByTargetGoal(Llama llama) {
            super(llama);
        }

        @Override
        public boolean canContinueToUse() {
            Mob entityinsentient = this.mob;

            if (entityinsentient instanceof Llama) {
                Llama entityllama = (Llama) entityinsentient;

                if (entityllama.didSpit) {
                    entityllama.setDidSpit(false);
                    return false;
                }
            }

            return super.canContinueToUse();
        }
    }

    private static class LlamaAttackWolfGoal extends NearestAttackableTargetGoal<Wolf> {

        public LlamaAttackWolfGoal(Llama llama) {
            super(llama, Wolf.class, 16, false, true, (entityliving) -> {
                return !((Wolf) entityliving).isTame();
            });
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.25D;
        }
    }

    private static class LlamaGroupData extends AgeableMob.AgeableMobGroupData {

        public final Llama.Variant variant;

        LlamaGroupData(Llama.Variant variant) {
            super(true);
            this.variant = variant;
        }
    }
}
