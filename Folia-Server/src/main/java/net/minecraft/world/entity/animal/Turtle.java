package net.minecraft.world.entity.animal;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class Turtle extends Animal {

    private static final EntityDataAccessor<BlockPos> HOME_POS = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> HAS_EGG = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LAYING_EGG = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> TRAVEL_POS = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> GOING_HOME = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TRAVELLING = SynchedEntityData.defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    public static final Ingredient FOOD_ITEMS = Ingredient.of(Blocks.SEAGRASS.asItem());
    int layEggCounter;
    public static final Predicate<LivingEntity> BABY_ON_LAND_SELECTOR = (entityliving) -> {
        return entityliving.isBaby() && !entityliving.isInWater();
    };

    public Turtle(EntityType<? extends Turtle> type, Level world) {
        super(type, world);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_IRON_CLOSED, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_OPEN, -1.0F);
        this.moveControl = new Turtle.TurtleMoveControl(this);
        this.setMaxUpStep(1.0F);
    }

    public void setHomePos(BlockPos pos) {
        this.entityData.set(Turtle.HOME_POS, pos.immutable()); // Paper - called with mutablepos...
    }

    public BlockPos getHomePos() {
        return (BlockPos) this.entityData.get(Turtle.HOME_POS);
    }

    void setTravelPos(BlockPos pos) {
        this.entityData.set(Turtle.TRAVEL_POS, pos);
    }

    BlockPos getTravelPos() {
        return (BlockPos) this.entityData.get(Turtle.TRAVEL_POS);
    }

    public boolean hasEgg() {
        return (Boolean) this.entityData.get(Turtle.HAS_EGG);
    }

    public void setHasEgg(boolean hasEgg) {
        this.entityData.set(Turtle.HAS_EGG, hasEgg);
    }

    public boolean isLayingEgg() {
        return (Boolean) this.entityData.get(Turtle.LAYING_EGG);
    }

    void setLayingEgg(boolean diggingSand) {
        this.layEggCounter = diggingSand ? 1 : 0;
        this.entityData.set(Turtle.LAYING_EGG, diggingSand);
    }

    public boolean isGoingHome() {
        return (Boolean) this.entityData.get(Turtle.GOING_HOME);
    }

    public void setGoingHome(boolean landBound) {
        this.entityData.set(Turtle.GOING_HOME, landBound);
    }

    public boolean isTravelling() {
        return (Boolean) this.entityData.get(Turtle.TRAVELLING);
    }

    public void setTravelling(boolean traveling) {
        this.entityData.set(Turtle.TRAVELLING, traveling);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Turtle.HOME_POS, BlockPos.ZERO);
        this.entityData.define(Turtle.HAS_EGG, false);
        this.entityData.define(Turtle.TRAVEL_POS, BlockPos.ZERO);
        this.entityData.define(Turtle.GOING_HOME, false);
        this.entityData.define(Turtle.TRAVELLING, false);
        this.entityData.define(Turtle.LAYING_EGG, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("HomePosX", this.getHomePos().getX());
        nbt.putInt("HomePosY", this.getHomePos().getY());
        nbt.putInt("HomePosZ", this.getHomePos().getZ());
        nbt.putBoolean("HasEgg", this.hasEgg());
        nbt.putInt("TravelPosX", this.getTravelPos().getX());
        nbt.putInt("TravelPosY", this.getTravelPos().getY());
        nbt.putInt("TravelPosZ", this.getTravelPos().getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        int i = nbt.getInt("HomePosX");
        int j = nbt.getInt("HomePosY");
        int k = nbt.getInt("HomePosZ");

        this.setHomePos(new BlockPos(i, j, k));
        super.readAdditionalSaveData(nbt);
        this.setHasEgg(nbt.getBoolean("HasEgg"));
        int l = nbt.getInt("TravelPosX");
        int i1 = nbt.getInt("TravelPosY");
        int j1 = nbt.getInt("TravelPosZ");

        this.setTravelPos(new BlockPos(l, i1, j1));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        this.setHomePos(this.blockPosition());
        this.setTravelPos(BlockPos.ZERO);
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);
    }

    public static boolean checkTurtleSpawnRules(EntityType<Turtle> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return pos.getY() < world.getSeaLevel() + 4 && TurtleEggBlock.onSand(world, pos) && isBrightEnoughToSpawn(world, pos);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Turtle.TurtlePanicGoal(this, 1.2D));
        this.goalSelector.addGoal(1, new Turtle.TurtleBreedGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new Turtle.TurtleLayEggGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.1D, Turtle.FOOD_ITEMS, false));
        this.goalSelector.addGoal(3, new Turtle.TurtleGoToWaterGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new Turtle.TurtleGoHomeGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new Turtle.TurtleTravelGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new Turtle.TurtleRandomStrollGoal(this, 1.0D, 100));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0D).add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return !this.isInWater() && this.onGround() && !this.isBaby() ? SoundEvents.TURTLE_AMBIENT_LAND : super.getAmbientSound();
    }

    @Override
    protected void playSwimSound(float volume) {
        super.playSwimSound(volume * 1.5F);
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.TURTLE_SWIM;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isBaby() ? SoundEvents.TURTLE_HURT_BABY : SoundEvents.TURTLE_HURT;
    }

    @Nullable
    @Override
    public SoundEvent getDeathSound() {
        return this.isBaby() ? SoundEvents.TURTLE_DEATH_BABY : SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundEvent soundeffect = this.isBaby() ? SoundEvents.TURTLE_SHAMBLE_BABY : SoundEvents.TURTLE_SHAMBLE;

        this.playSound(soundeffect, 0.15F, 1.0F);
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.hasEgg();
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.15F;
    }

    @Override
    public float getScale() {
        return this.isBaby() ? 0.3F : 1.0F;
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new Turtle.TurtlePathNavigation(this, world);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity) {
        return (AgeableMob) EntityType.TURTLE.create(world);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Blocks.SEAGRASS.asItem());
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return !this.isGoingHome() && world.getFluidState(pos).is(FluidTags.WATER) ? 10.0F : (TurtleEggBlock.onSand(world, pos) ? 10.0F : world.getPathfindingCostFromLightLevels(pos));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isLayingEgg() && this.layEggCounter >= 1 && this.layEggCounter % 5 == 0) {
            BlockPos blockposition = this.blockPosition();

            if (TurtleEggBlock.onSand(this.level(), blockposition)) {
                this.level().levelEvent(2001, blockposition, Block.getId(this.level().getBlockState(blockposition.below())));
                this.gameEvent(GameEvent.ENTITY_ACTION);
            }
        }

    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.isBaby() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.forceDrops = true; // CraftBukkit
            this.spawnAtLocation(Items.SCUTE, 1);
            this.forceDrops = false; // CraftBukkit
        }

    }

    @Override
    public void travel(Vec3 movementInput) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(0.1F, movementInput);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null && (!this.isGoingHome() || !this.getHomePos().closerToCenterThan(this.position(), 20.0D))) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(movementInput);
        }

    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {
        org.bukkit.craftbukkit.event.CraftEventFactory.entityDamageRT.set(lightning); // CraftBukkit // Folia - region threading
        this.hurt(this.damageSources().lightningBolt(), Float.MAX_VALUE);
        org.bukkit.craftbukkit.event.CraftEventFactory.entityDamageRT.set(null); // CraftBukkit // Folia - region threading
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height + (this.isBaby() ? 0.0F : 0.15625F) * scaleFactor, -0.25F * scaleFactor);
    }

    private static class TurtleMoveControl extends MoveControl {

        private final Turtle turtle;

        TurtleMoveControl(Turtle turtle) {
            super(turtle);
            this.turtle = turtle;
        }

        private void updateSpeed() {
            if (this.turtle.isInWater()) {
                this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
                if (!this.turtle.getHomePos().closerToCenterThan(this.turtle.position(), 16.0D)) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.08F));
                }

                if (this.turtle.isBaby()) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 3.0F, 0.06F));
                }
            } else if (this.turtle.onGround()) {
                this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.06F));
            }

        }

        @Override
        public void tick() {
            this.updateSpeed();
            if (this.operation == MoveControl.Operation.MOVE_TO && !this.turtle.getNavigation().isDone()) {
                double d0 = this.wantedX - this.turtle.getX();
                double d1 = this.wantedY - this.turtle.getY();
                double d2 = this.wantedZ - this.turtle.getZ();
                double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

                if (d3 < 9.999999747378752E-6D) {
                    this.mob.setSpeed(0.0F);
                } else {
                    d1 /= d3;
                    float f = (float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F;

                    this.turtle.setYRot(this.rotlerp(this.turtle.getYRot(), f, 90.0F));
                    this.turtle.yBodyRot = this.turtle.getYRot();
                    float f1 = (float) (this.speedModifier * this.turtle.getAttributeValue(Attributes.MOVEMENT_SPEED));

                    this.turtle.setSpeed(Mth.lerp(0.125F, this.turtle.getSpeed(), f1));
                    this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0D, (double) this.turtle.getSpeed() * d1 * 0.1D, 0.0D));
                }
            } else {
                this.turtle.setSpeed(0.0F);
            }
        }
    }

    private static class TurtlePanicGoal extends PanicGoal {

        TurtlePanicGoal(Turtle turtle, double speed) {
            super(turtle, speed);
        }

        @Override
        public boolean canUse() {
            if (!this.shouldPanic()) {
                return false;
            } else {
                BlockPos blockposition = this.lookForWater(this.mob.level(), this.mob, 7);

                if (blockposition != null) {
                    this.posX = (double) blockposition.getX();
                    this.posY = (double) blockposition.getY();
                    this.posZ = (double) blockposition.getZ();
                    return true;
                } else {
                    return this.findRandomPosition();
                }
            }
        }
    }

    private static class TurtleBreedGoal extends BreedGoal {

        private final Turtle turtle;

        TurtleBreedGoal(Turtle turtle, double speed) {
            super(turtle, speed);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.turtle.hasEgg();
        }

        @Override
        protected void breed() {
            ServerPlayer entityplayer = this.animal.getLoveCause();

            if (entityplayer == null && this.partner.getLoveCause() != null) {
                entityplayer = this.partner.getLoveCause();
            }
            // Paper start - Add EntityFertilizeEggEvent event
            io.papermc.paper.event.entity.EntityFertilizeEggEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityFertilizeEggEvent(this.animal, this.partner);
            if (event.isCancelled()) return;
            // Paper end - Add EntityFertilizeEggEvent event

            if (entityplayer != null) {
                entityplayer.awardStat(Stats.ANIMALS_BRED);
                CriteriaTriggers.BRED_ANIMALS.trigger(entityplayer, this.animal, this.partner, (AgeableMob) null);
            }

            this.turtle.setHasEgg(true);
            this.animal.setAge(6000);
            this.partner.setAge(6000);
            this.animal.resetLove();
            this.partner.resetLove();
            RandomSource randomsource = this.animal.getRandom();

            if (this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                if(event.getExperience() > 0) this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), event.getExperience(), org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, entityplayer)); // Paper - Add EntityFertilizeEggEvent event
            }

        }
    }

    private static class TurtleLayEggGoal extends MoveToBlockGoal {

        private final Turtle turtle;

        TurtleLayEggGoal(Turtle turtle, double speed) {
            super(turtle, speed, 16);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return this.turtle.hasEgg() && this.turtle.getHomePos().closerToCenterThan(this.turtle.position(), 9.0D) ? super.canUse() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.turtle.hasEgg() && this.turtle.getHomePos().closerToCenterThan(this.turtle.position(), 9.0D);
        }

        @Override
        public void tick() {
            super.tick();
            BlockPos blockposition = this.turtle.blockPosition();

            if (!this.turtle.isInWater() && this.isReachedTarget()) {
                if (this.turtle.layEggCounter < 1) {
                    this.turtle.setLayingEgg(new com.destroystokyo.paper.event.entity.TurtleStartDiggingEvent((org.bukkit.entity.Turtle) this.turtle.getBukkitEntity(), io.papermc.paper.util.MCUtil.toLocation(this.turtle.level(), this.getTargetPosition())).callEvent()); // Paper
                } else if (this.turtle.layEggCounter > this.adjustedTickDelay(200)) {
                    Level world = this.turtle.level();

                    // CraftBukkit start
                    // Paper start
                    int eggCount = this.turtle.random.nextInt(4) + 1;
                    com.destroystokyo.paper.event.entity.TurtleLayEggEvent layEggEvent = new com.destroystokyo.paper.event.entity.TurtleLayEggEvent((org.bukkit.entity.Turtle) this.turtle.getBukkitEntity(), io.papermc.paper.util.MCUtil.toLocation(this.turtle.level(), this.blockPos.above()), eggCount);
                    if (layEggEvent.callEvent() && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.turtle, this.blockPos.above(), Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, layEggEvent.getEggCount()))) {
                    world.playSound((Player) null, blockposition, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 0.3F, 0.9F + world.random.nextFloat() * 0.2F);
                    BlockPos blockposition1 = this.blockPos.above();
                    BlockState iblockdata = (BlockState) Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, layEggEvent.getEggCount()); // Paper

                    world.setBlock(blockposition1, iblockdata, 3);
                    world.gameEvent(GameEvent.BLOCK_PLACE, blockposition1, GameEvent.Context.of(this.turtle, iblockdata));
                    } // CraftBukkit
                    this.turtle.setHasEgg(false);
                    this.turtle.setLayingEgg(false);
                    this.turtle.setInLoveTime(600);
                }

                if (this.turtle.isLayingEgg()) {
                    ++this.turtle.layEggCounter;
                }
            }

        }

        @Override
        protected boolean isValidTarget(LevelReader world, BlockPos pos) {
            return !world.isEmptyBlock(pos.above()) ? false : TurtleEggBlock.isSand(world, pos);
        }
    }

    private static class TurtleGoToWaterGoal extends MoveToBlockGoal {

        private static final int GIVE_UP_TICKS = 1200;
        private final Turtle turtle;

        TurtleGoToWaterGoal(Turtle turtle, double speed) {
            super(turtle, turtle.isBaby() ? 2.0D : speed, 24);
            this.turtle = turtle;
            this.verticalSearchStart = -1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.isInWater() && this.tryTicks <= 1200 && this.isValidTarget(this.turtle.level(), this.blockPos);
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() && !this.turtle.isInWater() ? super.canUse() : (!this.turtle.isGoingHome() && !this.turtle.isInWater() && !this.turtle.hasEgg() ? super.canUse() : false);
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 160 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader world, BlockPos pos) {
            return world.getBlockState(pos).is(Blocks.WATER);
        }
    }

    private static class TurtleGoHomeGoal extends Goal {

        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;
        private int closeToHomeTryTicks;
        private static final int GIVE_UP_TICKS = 600;

        TurtleGoHomeGoal(Turtle turtle, double speed) {
            this.turtle = turtle;
            this.speedModifier = speed;
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() ? false : (this.turtle.hasEgg() ? true : (this.turtle.getRandom().nextInt(reducedTickDelay(700)) != 0 ? false : !this.turtle.getHomePos().closerToCenterThan(this.turtle.position(), 64.0D))) && new com.destroystokyo.paper.event.entity.TurtleGoHomeEvent((org.bukkit.entity.Turtle) this.turtle.getBukkitEntity()).callEvent(); // Paper
        }

        @Override
        public void start() {
            this.turtle.setGoingHome(true);
            this.stuck = false;
            this.closeToHomeTryTicks = 0;
        }

        @Override
        public void stop() {
            this.turtle.setGoingHome(false);
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.getHomePos().closerToCenterThan(this.turtle.position(), 7.0D) && !this.stuck && this.closeToHomeTryTicks <= this.adjustedTickDelay(600);
        }

        @Override
        public void tick() {
            BlockPos blockposition = this.turtle.getHomePos();
            boolean flag = blockposition.closerToCenterThan(this.turtle.position(), 16.0D);

            if (flag) {
                ++this.closeToHomeTryTicks;
            }

            if (this.turtle.getNavigation().isDone()) {
                Vec3 vec3d = Vec3.atBottomCenterOf(blockposition);
                Vec3 vec3d1 = DefaultRandomPos.getPosTowards(this.turtle, 16, 3, vec3d, 0.3141592741012573D);

                if (vec3d1 == null) {
                    vec3d1 = DefaultRandomPos.getPosTowards(this.turtle, 8, 7, vec3d, 1.5707963705062866D);
                }

                if (vec3d1 != null && !flag && !this.turtle.level().getBlockState(BlockPos.containing(vec3d1)).is(Blocks.WATER)) {
                    vec3d1 = DefaultRandomPos.getPosTowards(this.turtle, 16, 5, vec3d, 1.5707963705062866D);
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.turtle.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, this.speedModifier);
            }

        }
    }

    private static class TurtleTravelGoal extends Goal {

        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;

        TurtleTravelGoal(Turtle turtle, double speed) {
            this.turtle = turtle;
            this.speedModifier = speed;
        }

        @Override
        public boolean canUse() {
            return !this.turtle.isGoingHome() && !this.turtle.hasEgg() && this.turtle.isInWater();
        }

        @Override
        public void start() {
            boolean flag = true;
            boolean flag1 = true;
            RandomSource randomsource = this.turtle.random;
            int i = randomsource.nextInt(1025) - 512;
            int j = randomsource.nextInt(9) - 4;
            int k = randomsource.nextInt(1025) - 512;

            if ((double) j + this.turtle.getY() > (double) (this.turtle.level().getSeaLevel() - 1)) {
                j = 0;
            }

            BlockPos blockposition = BlockPos.containing((double) i + this.turtle.getX(), (double) j + this.turtle.getY(), (double) k + this.turtle.getZ());

            this.turtle.setTravelPos(blockposition);
            this.turtle.setTravelling(true);
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.turtle.getNavigation().isDone()) {
                Vec3 vec3d = Vec3.atBottomCenterOf(this.turtle.getTravelPos());
                Vec3 vec3d1 = DefaultRandomPos.getPosTowards(this.turtle, 16, 3, vec3d, 0.3141592741012573D);

                if (vec3d1 == null) {
                    vec3d1 = DefaultRandomPos.getPosTowards(this.turtle, 8, 7, vec3d, 1.5707963705062866D);
                }

                if (vec3d1 != null) {
                    int i = Mth.floor(vec3d1.x);
                    int j = Mth.floor(vec3d1.z);
                    boolean flag = true;

                    if (!this.turtle.level().hasChunksAt(i - 34, j - 34, i + 34, j + 34)) {
                        vec3d1 = null;
                    }
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.turtle.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, this.speedModifier);
            }

        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.getNavigation().isDone() && !this.stuck && !this.turtle.isGoingHome() && !this.turtle.isInLove() && !this.turtle.hasEgg();
        }

        @Override
        public void stop() {
            this.turtle.setTravelling(false);
            super.stop();
        }
    }

    private static class TurtleRandomStrollGoal extends RandomStrollGoal {

        private final Turtle turtle;

        TurtleRandomStrollGoal(Turtle turtle, double speed, int chance) {
            super(turtle, speed, chance);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return !this.mob.isInWater() && !this.turtle.isGoingHome() && !this.turtle.hasEgg() ? super.canUse() : false;
        }
    }

    private static class TurtlePathNavigation extends AmphibiousPathNavigation {

        TurtlePathNavigation(Turtle owner, Level world) {
            super(owner, world);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            Mob entityinsentient = this.mob;

            if (entityinsentient instanceof Turtle) {
                Turtle entityturtle = (Turtle) entityinsentient;

                if (entityturtle.isTravelling()) {
                    return this.level.getBlockState(pos).is(Blocks.WATER);
                }
            }

            return !this.level.getBlockState(pos.below()).isAir();
        }
    }
}
