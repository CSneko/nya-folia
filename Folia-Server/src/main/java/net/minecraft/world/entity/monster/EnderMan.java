package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// CraftBukkit start;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class EnderMan extends Monster implements NeutralMob {

    private static final UUID SPEED_MODIFIER_ATTACKING_UUID = UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A0");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(EnderMan.SPEED_MODIFIER_ATTACKING_UUID, "Attacking speed boost", 0.15000000596046448D, AttributeModifier.Operation.ADDITION);
    private static final int DELAY_BETWEEN_CREEPY_STARE_SOUND = 400;
    private static final int MIN_DEAGGRESSION_TIME = 600;
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRY_STATE = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> DATA_CREEPY = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STARED_AT = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private int lastStareSound = Integer.MIN_VALUE;
    private int targetChangeTime;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;

    public EnderMan(EntityType<? extends EnderMan> type, Level world) {
        super(type, world);
        this.setMaxUpStep(1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EnderMan.EndermanFreezeWhenLookedAt(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D, 0.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new EnderMan.EndermanLeaveBlockGoal(this));
        this.goalSelector.addGoal(11, new EnderMan.EndermanTakeBlockGoal(this));
        this.targetSelector.addGoal(1, new EnderMan.EndermanLookForPlayerGoal(this, this::isAngryAt));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Endermite.class, true, false));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).add(Attributes.ATTACK_DAMAGE, 7.0D).add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // CraftBukkit start - fire event
        this.setTarget(target, EntityTargetEvent.TargetReason.UNKNOWN, true);
    }

    // Paper start
    private boolean tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason reason) {
        return new com.destroystokyo.paper.event.entity.EndermanEscapeEvent((org.bukkit.craftbukkit.entity.CraftEnderman) this.getBukkitEntity(), reason).callEvent();
    }
    // Paper end

    @Override
    public boolean setTarget(LivingEntity entityliving, EntityTargetEvent.TargetReason reason, boolean fireEvent) {
        if (!super.setTarget(entityliving, reason, fireEvent)) {
            return false;
        }
        entityliving = this.getTarget();
        // CraftBukkit end
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (entityliving == null) {
            this.targetChangeTime = 0;
            this.entityData.set(EnderMan.DATA_CREEPY, false);
            this.entityData.set(EnderMan.DATA_STARED_AT, false);
            attributemodifiable.removeModifier(EnderMan.SPEED_MODIFIER_ATTACKING.getId());
        } else {
            this.targetChangeTime = this.tickCount;
            this.entityData.set(EnderMan.DATA_CREEPY, true);
            if (!attributemodifiable.hasModifier(EnderMan.SPEED_MODIFIER_ATTACKING)) {
                attributemodifiable.addTransientModifier(EnderMan.SPEED_MODIFIER_ATTACKING);
            }
        }
        return true;

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(EnderMan.DATA_CARRY_STATE, Optional.empty());
        this.entityData.define(EnderMan.DATA_CREEPY, false);
        this.entityData.define(EnderMan.DATA_STARED_AT, false);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(EnderMan.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void setRemainingPersistentAngerTime(int angerTime) {
        this.remainingPersistentAngerTime = angerTime;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID angryAt) {
        this.persistentAngerTarget = angryAt;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void playStareSound() {
        if (this.tickCount >= this.lastStareSound + 400) {
            this.lastStareSound = this.tickCount;
            if (!this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ENDERMAN_STARE, this.getSoundSource(), 2.5F, 1.0F, false);
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (EnderMan.DATA_CREEPY.equals(data) && this.hasBeenStaredAt() && this.level().isClientSide) {
            this.playStareSound();
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        BlockState iblockdata = this.getCarriedBlock();

        if (iblockdata != null) {
            nbt.put("carriedBlockState", NbtUtils.writeBlockState(iblockdata));
        }

        this.addPersistentAngerSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        BlockState iblockdata = null;

        if (nbt.contains("carriedBlockState", 10)) {
            iblockdata = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), nbt.getCompound("carriedBlockState"));
            if (iblockdata.isAir()) {
                iblockdata = null;
            }
        }

        this.setCarriedBlock(iblockdata);
        this.readPersistentAngerSaveData(this.level(), nbt);
    }

    // Paper start - EndermanAttackPlayerEvent
    private boolean isLookingAtMe(Player player) {
        boolean shouldAttack = isLookingAtMe_check(player);
        com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent event = new com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent((org.bukkit.entity.Enderman) getBukkitEntity(), (org.bukkit.entity.Player) player.getBukkitEntity());
        event.setCancelled(!shouldAttack);
        return event.callEvent();
    }
    private boolean isLookingAtMe_check(Player player) {
        // Paper end
        ItemStack itemstack = (ItemStack) player.getInventory().armor.get(3);

        if (itemstack.is(Blocks.CARVED_PUMPKIN.asItem())) {
            return false;
        } else {
            Vec3 vec3d = player.getViewVector(1.0F).normalize();
            Vec3 vec3d1 = new Vec3(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
            double d0 = vec3d1.length();

            vec3d1 = vec3d1.normalize();
            double d1 = vec3d.dot(vec3d1);

            return d1 > 1.0D - 0.025D / d0 ? player.hasLineOfSight(this) : false;
        }
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 2.55F;
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height - 0.09375F * scaleFactor, 0.0F);
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.PORTAL, this.getRandomX(0.5D), this.getRandomY() - 0.25D, this.getRandomZ(0.5D), (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
            }
        }

        this.jumping = false;
        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }

        super.aiStep();
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void customServerAiStep() {
        if (this.level().isDay() && this.tickCount >= this.targetChangeTime + 600) {
            float f = this.getLightLevelDependentMagicValue();

            if (f > 0.5F && this.level().canSeeSky(this.blockPosition()) && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && this.tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.RUNAWAY)) { // Paper
                this.setTarget((LivingEntity) null);
                this.teleport();
            }
        }

        super.customServerAiStep();
    }

    public boolean teleport() {
        if (!this.level().isClientSide() && this.isAlive()) {
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * 64.0D;
            double d1 = this.getY() + (double) (this.random.nextInt(64) - 32);
            double d2 = this.getZ() + (this.random.nextDouble() - 0.5D) * 64.0D;

            return this.teleport(d0, d1, d2);
        } else {
            return false;
        }
    }

    public boolean teleportTowards(Entity entity) {
        Vec3 vec3d = new Vec3(this.getX() - entity.getX(), this.getY(0.5D) - entity.getEyeY(), this.getZ() - entity.getZ());

        vec3d = vec3d.normalize();
        double d0 = 16.0D;
        double d1 = this.getX() + (this.random.nextDouble() - 0.5D) * 8.0D - vec3d.x * 16.0D;
        double d2 = this.getY() + (double) (this.random.nextInt(16) - 8) - vec3d.y * 16.0D;
        double d3 = this.getZ() + (this.random.nextDouble() - 0.5D) * 8.0D - vec3d.z * 16.0D;

        return this.teleport(d1, d2, d3);
    }

    private boolean teleport(double x, double y, double z) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos(x, y, z);

        while (blockposition_mutableblockposition.getY() > this.level().getMinBuildHeight() && !this.level().getBlockState(blockposition_mutableblockposition).blocksMotion()) {
            blockposition_mutableblockposition.move(Direction.DOWN);
        }

        BlockState iblockdata = this.level().getBlockState(blockposition_mutableblockposition);
        boolean flag = iblockdata.blocksMotion();
        boolean flag1 = iblockdata.getFluidState().is(FluidTags.WATER);

        if (flag && !flag1) {
            Vec3 vec3d = this.position();
            boolean flag2 = this.randomTeleport(x, y, z, true);

            if (flag2) {
                this.level().gameEvent(GameEvent.TELEPORT, vec3d, GameEvent.Context.of((Entity) this));
                if (!this.isSilent()) {
                    this.level().playSound((Player) null, this.xo, this.yo, this.zo, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }
            }

            return flag2;
        } else {
            return false;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isCreepy() ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropCustomDeathLoot(source, lootingMultiplier, allowDrops);
        BlockState iblockdata = this.getCarriedBlock();

        if (iblockdata != null) {
            ItemStack itemstack = new ItemStack(Items.DIAMOND_AXE);

            itemstack.enchant(Enchantments.SILK_TOUCH, 1);
            LootParams.Builder lootparams_a = (new LootParams.Builder((ServerLevel) this.level())).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.TOOL, itemstack).withOptionalParameter(LootContextParams.THIS_ENTITY, this);
            List<ItemStack> list = iblockdata.getDrops(lootparams_a);
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemStack itemstack1 = (ItemStack) iterator.next();

                this.spawnAtLocation(itemstack1);
            }
        }

    }

    public void setCarriedBlock(@Nullable BlockState state) {
        this.entityData.set(EnderMan.DATA_CARRY_STATE, Optional.ofNullable(state));
    }

    @Nullable
    public BlockState getCarriedBlock() {
        return (BlockState) ((Optional) this.entityData.get(EnderMan.DATA_CARRY_STATE)).orElse((Object) null);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            boolean flag = source.getDirectEntity() instanceof ThrownPotion;
            boolean flag1;

            if (!source.is(DamageTypeTags.IS_PROJECTILE) && !flag) {
                flag1 = super.hurt(source, amount);
                if (!this.level().isClientSide() && !(source.getEntity() instanceof LivingEntity) && this.random.nextInt(10) != 0) {
                    this.teleport();
                }

                return flag1;
            } else {
                flag1 = flag && this.hurtWithCleanWater(source, (ThrownPotion) source.getDirectEntity(), amount);

                if (this.tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.INDIRECT)) { // Paper start
                for (int i = 0; i < 64; ++i) {
                    if (this.teleport()) {
                        return true;
                    }
                }
                } // Paper end

                return flag1;
            }
        }
    }

    private boolean hurtWithCleanWater(DamageSource source, ThrownPotion potion, float amount) {
        ItemStack itemstack = potion.getItem();
        Potion potionregistry = PotionUtils.getPotion(itemstack);
        List<MobEffectInstance> list = PotionUtils.getMobEffects(itemstack);
        boolean flag = potionregistry == Potions.WATER && list.isEmpty();

        return flag ? super.hurt(source, amount) : false;
    }

    public boolean isCreepy() {
        return (Boolean) this.entityData.get(EnderMan.DATA_CREEPY);
    }

    public boolean hasBeenStaredAt() {
        return (Boolean) this.entityData.get(EnderMan.DATA_STARED_AT);
    }

    public void setBeingStaredAt() {
        this.entityData.set(EnderMan.DATA_STARED_AT, true);
    }

    // Paper start
    public void setCreepy(boolean creepy) {
        this.entityData.set(EnderMan.DATA_CREEPY, creepy);
    }

    public void setHasBeenStaredAt(boolean hasBeenStaredAt) {
        this.entityData.set(EnderMan.DATA_STARED_AT, hasBeenStaredAt);
    }
    // Paper end

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCarriedBlock() != null;
    }

    private static class EndermanFreezeWhenLookedAt extends Goal {

        private final EnderMan enderman;
        @Nullable
        private LivingEntity target;

        public EndermanFreezeWhenLookedAt(EnderMan enderman) {
            this.enderman = enderman;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.target = this.enderman.getTarget();
            if (!(this.target instanceof Player)) {
                return false;
            } else {
                double d0 = this.target.distanceToSqr((Entity) this.enderman);

                return d0 > 256.0D ? false : this.enderman.isLookingAtMe((Player) this.target);
            }
        }

        @Override
        public void start() {
            this.enderman.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.enderman.getLookControl().setLookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
        }
    }

    private static class EndermanLeaveBlockGoal extends Goal {

        private final EnderMan enderman;

        public EndermanLeaveBlockGoal(EnderMan enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarriedBlock() == null ? false : (!this.enderman.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? false : this.enderman.getRandom().nextInt(reducedTickDelay(2000)) == 0);
        }

        @Override
        public void tick() {
            RandomSource randomsource = this.enderman.getRandom();
            Level world = this.enderman.level();
            int i = Mth.floor(this.enderman.getX() - 1.0D + randomsource.nextDouble() * 2.0D);
            int j = Mth.floor(this.enderman.getY() + randomsource.nextDouble() * 2.0D);
            int k = Mth.floor(this.enderman.getZ() - 1.0D + randomsource.nextDouble() * 2.0D);
            BlockPos blockposition = new BlockPos(i, j, k);
            BlockState iblockdata = world.getBlockStateIfLoaded(blockposition); // Paper
            if (iblockdata == null) return; // Paper
            BlockPos blockposition1 = blockposition.below();
            BlockState iblockdata1 = world.getBlockState(blockposition1);
            BlockState iblockdata2 = this.enderman.getCarriedBlock();

            if (iblockdata2 != null) {
                iblockdata2 = Block.updateFromNeighbourShapes(iblockdata2, this.enderman.level(), blockposition);
                if (this.canPlaceBlock(world, blockposition, iblockdata2, iblockdata, iblockdata1, blockposition1)) {
                    if (CraftEventFactory.callEntityChangeBlockEvent(this.enderman, blockposition, iblockdata2)) { // CraftBukkit - Place event
                    world.setBlock(blockposition, iblockdata2, 3);
                    world.gameEvent(GameEvent.BLOCK_PLACE, blockposition, GameEvent.Context.of(this.enderman, iblockdata2));
                    this.enderman.setCarriedBlock((BlockState) null);
                    } // CraftBukkit
                }

            }
        }

        private boolean canPlaceBlock(Level world, BlockPos posAbove, BlockState carriedState, BlockState stateAbove, BlockState state, BlockPos pos) {
            return stateAbove.isAir() && !state.isAir() && !state.is(Blocks.BEDROCK) && state.isCollisionShapeFullBlock(world, pos) && carriedState.canSurvive(world, posAbove) && world.getEntities(this.enderman, AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(posAbove))).isEmpty();
        }
    }

    private static class EndermanTakeBlockGoal extends Goal {

        private final EnderMan enderman;

        public EndermanTakeBlockGoal(EnderMan enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarriedBlock() != null ? false : (!this.enderman.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? false : this.enderman.getRandom().nextInt(reducedTickDelay(20)) == 0);
        }

        @Override
        public void tick() {
            RandomSource randomsource = this.enderman.getRandom();
            Level world = this.enderman.level();
            int i = Mth.floor(this.enderman.getX() - 2.0D + randomsource.nextDouble() * 4.0D);
            int j = Mth.floor(this.enderman.getY() + randomsource.nextDouble() * 3.0D);
            int k = Mth.floor(this.enderman.getZ() - 2.0D + randomsource.nextDouble() * 4.0D);
            BlockPos blockposition = new BlockPos(i, j, k);
            BlockState iblockdata = world.getBlockStateIfLoaded(blockposition); // Paper
            if (iblockdata == null) return; // Paper
            Vec3 vec3d = new Vec3((double) this.enderman.getBlockX() + 0.5D, (double) j + 0.5D, (double) this.enderman.getBlockZ() + 0.5D);
            Vec3 vec3d1 = new Vec3((double) i + 0.5D, (double) j + 0.5D, (double) k + 0.5D);
            BlockHitResult movingobjectpositionblock = world.clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.enderman));
            boolean flag = movingobjectpositionblock.getBlockPos().equals(blockposition);

            if (iblockdata.is(BlockTags.ENDERMAN_HOLDABLE) && flag) {
                if (CraftEventFactory.callEntityChangeBlockEvent(this.enderman, blockposition, iblockdata.getFluidState().createLegacyBlock())) { // CraftBukkit - Place event // Paper - fix wrong block state
                world.removeBlock(blockposition, false);
                world.gameEvent(GameEvent.BLOCK_DESTROY, blockposition, GameEvent.Context.of(this.enderman, iblockdata));
                this.enderman.setCarriedBlock(iblockdata.getBlock().defaultBlockState());
                } // CraftBukkit
            }

        }
    }

    private static class EndermanLookForPlayerGoal extends NearestAttackableTargetGoal<Player> {

        private final EnderMan enderman;
        @Nullable
        private Player pendingTarget;
        private int aggroTime;
        private int teleportTime;
        private final TargetingConditions startAggroTargetConditions;
        private final TargetingConditions continueAggroTargetConditions = TargetingConditions.forCombat().ignoreLineOfSight();
        private final Predicate<LivingEntity> isAngerInducing;

        public EndermanLookForPlayerGoal(EnderMan enderman, @Nullable Predicate<LivingEntity> targetPredicate) {
            super(enderman, Player.class, 10, false, false, targetPredicate);
            this.enderman = enderman;
            this.isAngerInducing = (entityliving) -> {
                return (enderman.isLookingAtMe((Player) entityliving) || enderman.isAngryAt(entityliving)) && !enderman.hasIndirectPassenger(entityliving);
            };
            this.startAggroTargetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(this.isAngerInducing);
        }

        @Override
        public boolean canUse() {
            this.pendingTarget = this.enderman.level().getNearestPlayer(this.startAggroTargetConditions, this.enderman);
            return this.pendingTarget != null;
        }

        @Override
        public void start() {
            this.aggroTime = this.adjustedTickDelay(5);
            this.teleportTime = 0;
            this.enderman.setBeingStaredAt();
        }

        @Override
        public void stop() {
            this.pendingTarget = null;
            super.stop();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.pendingTarget != null) {
                if (!this.isAngerInducing.test(this.pendingTarget)) {
                    return false;
                } else {
                    this.enderman.lookAt(this.pendingTarget, 10.0F, 10.0F);
                    return true;
                }
            } else {
                if (this.target != null) {
                    if (this.enderman.hasIndirectPassenger(this.target)) {
                        return false;
                    }

                    if (this.continueAggroTargetConditions.test(this.enderman, this.target)) {
                        return true;
                    }
                }

                return super.canContinueToUse();
            }
        }

        @Override
        public void tick() {
            if (this.enderman.getTarget() == null) {
                super.setTarget((LivingEntity) null);
            }

            if (this.pendingTarget != null) {
                if (--this.aggroTime <= 0) {
                    this.target = this.pendingTarget;
                    this.pendingTarget = null;
                    super.start();
                }
            } else {
                if (this.target != null && !this.enderman.isPassenger()) {
                    if (this.enderman.isLookingAtMe((Player) this.target)) {
                        if (this.target.distanceToSqr((Entity) this.enderman) < 16.0D && this.enderman.tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.STARE)) { // Paper
                            this.enderman.teleport();
                        }

                        this.teleportTime = 0;
                    } else if (this.target.distanceToSqr((Entity) this.enderman) > 256.0D && this.teleportTime++ >= this.adjustedTickDelay(30) && this.enderman.teleportTowards(this.target)) {
                        this.teleportTime = 0;
                    }
                }

                super.tick();
            }

        }
    }
}
