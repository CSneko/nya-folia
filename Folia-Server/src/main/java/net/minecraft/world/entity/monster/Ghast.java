package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class Ghast extends FlyingMob implements Enemy {

    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(Ghast.class, EntityDataSerializers.BOOLEAN);
    private int explosionPower = 1;

    public Ghast(EntityType<? extends Ghast> type, Level world) {
        super(type, world);
        this.xpReward = 5;
        this.moveControl = new Ghast.GhastMoveControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastLookGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastShootFireballGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entityliving) -> {
            return Math.abs(entityliving.getY() - this.getY()) <= 4.0D;
        }));
    }

    public boolean isCharging() {
        return (Boolean) this.entityData.get(Ghast.DATA_IS_CHARGING);
    }

    public void setCharging(boolean shooting) {
        this.entityData.set(Ghast.DATA_IS_CHARGING, shooting);
    }

    public int getExplosionPower() {
        return this.explosionPower;
    }

    // Paper start
    public void setExplosionPower(int explosionPower) {
        this.explosionPower = explosionPower;
    }
    // Paper end

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    private static boolean isReflectedFireball(DamageSource damageSource) {
        return damageSource.getDirectEntity() instanceof LargeFireball && damageSource.getEntity() instanceof Player;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return !Ghast.isReflectedFireball(damageSource) && super.isInvulnerableTo(damageSource);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (Ghast.isReflectedFireball(source)) {
            super.hurt(source, 1000.0F);
            return true;
        } else {
            return this.isInvulnerableTo(source) ? false : super.hurt(source, amount);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Ghast.DATA_IS_CHARGING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.FOLLOW_RANGE, 100.0D);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 5.0F;
    }

    public static boolean checkGhastSpawnRules(EntityType<Ghast> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(20) == 0 && checkMobSpawnRules(type, world, spawnReason, pos, random);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, dimensions.height + 0.0625F * scaleFactor, 0.0F);
    }

    @Override
    protected float ridingOffset(Entity vehicle) {
        return 0.5F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putByte("ExplosionPower", (byte) this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("ExplosionPower", 99)) {
            this.explosionPower = nbt.getByte("ExplosionPower");
        }

    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 2.6F;
    }

    private static class GhastMoveControl extends MoveControl {

        private final Ghast ghast;
        private int floatDuration;

        public GhastMoveControl(Ghast ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                if (this.floatDuration-- <= 0) {
                    this.floatDuration += this.ghast.getRandom().nextInt(5) + 2;
                    Vec3 vec3d = new Vec3(this.wantedX - this.ghast.getX(), this.wantedY - this.ghast.getY(), this.wantedZ - this.ghast.getZ());
                    double d0 = vec3d.length();

                    vec3d = vec3d.normalize();
                    if (this.canReach(vec3d, Mth.ceil(d0))) {
                        this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(vec3d.scale(0.1D)));
                    } else {
                        this.operation = MoveControl.Operation.WAIT;
                    }
                }

            }
        }

        private boolean canReach(Vec3 direction, int steps) {
            AABB axisalignedbb = this.ghast.getBoundingBox();

            for (int j = 1; j < steps; ++j) {
                axisalignedbb = axisalignedbb.move(direction);
                if (!this.ghast.level().noCollision(this.ghast, axisalignedbb)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static class RandomFloatAroundGoal extends Goal {

        private final Ghast ghast;

        public RandomFloatAroundGoal(Ghast ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl controllermove = this.ghast.getMoveControl();

            if (!controllermove.hasWanted()) {
                return true;
            } else {
                double d0 = controllermove.getWantedX() - this.ghast.getX();
                double d1 = controllermove.getWantedY() - this.ghast.getY();
                double d2 = controllermove.getWantedZ() - this.ghast.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;

                return d3 < 1.0D || d3 > 3600.0D;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource randomsource = this.ghast.getRandom();
            double d0 = this.ghast.getX() + (double) ((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double d1 = this.ghast.getY() + (double) ((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double d2 = this.ghast.getZ() + (double) ((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);

            this.ghast.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
        }
    }

    private static class GhastLookGoal extends Goal {

        private final Ghast ghast;

        public GhastLookGoal(Ghast ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.ghast.getTarget() == null) {
                Vec3 vec3d = this.ghast.getDeltaMovement();

                this.ghast.setYRot(-((float) Mth.atan2(vec3d.x, vec3d.z)) * 57.295776F);
                this.ghast.yBodyRot = this.ghast.getYRot();
            } else {
                LivingEntity entityliving = this.ghast.getTarget();
                double d0 = 64.0D;

                if (entityliving.distanceToSqr((Entity) this.ghast) < 4096.0D) {
                    double d1 = entityliving.getX() - this.ghast.getX();
                    double d2 = entityliving.getZ() - this.ghast.getZ();

                    this.ghast.setYRot(-((float) Mth.atan2(d1, d2)) * 57.295776F);
                    this.ghast.yBodyRot = this.ghast.getYRot();
                }
            }

        }
    }

    private static class GhastShootFireballGoal extends Goal {

        private final Ghast ghast;
        public int chargeTime;

        public GhastShootFireballGoal(Ghast ghast) {
            this.ghast = ghast;
        }

        @Override
        public boolean canUse() {
            return this.ghast.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity entityliving = this.ghast.getTarget();

            if (entityliving != null) {
                double d0 = 64.0D;

                if (entityliving.distanceToSqr((Entity) this.ghast) < 4096.0D && this.ghast.hasLineOfSight(entityliving)) {
                    Level world = this.ghast.level();

                    ++this.chargeTime;
                    if (this.chargeTime == 10 && !this.ghast.isSilent()) {
                        world.levelEvent((Player) null, 1015, this.ghast.blockPosition(), 0);
                    }

                    if (this.chargeTime == 20) {
                        double d1 = 4.0D;
                        Vec3 vec3d = this.ghast.getViewVector(1.0F);
                        double d2 = entityliving.getX() - (this.ghast.getX() + vec3d.x * 4.0D);
                        double d3 = entityliving.getY(0.5D) - (0.5D + this.ghast.getY(0.5D));
                        double d4 = entityliving.getZ() - (this.ghast.getZ() + vec3d.z * 4.0D);

                        if (!this.ghast.isSilent()) {
                            world.levelEvent((Player) null, 1016, this.ghast.blockPosition(), 0);
                        }

                        LargeFireball entitylargefireball = new LargeFireball(world, this.ghast, d2, d3, d4, this.ghast.getExplosionPower());

                        // CraftBukkit - set bukkitYield when setting explosionpower
                        entitylargefireball.bukkitYield = entitylargefireball.explosionPower = this.ghast.getExplosionPower();
                        entitylargefireball.setPos(this.ghast.getX() + vec3d.x * 4.0D, this.ghast.getY(0.5D) + 0.5D, entitylargefireball.getZ() + vec3d.z * 4.0D);
                        world.addFreshEntity(entitylargefireball);
                        this.chargeTime = -40;
                    }
                } else if (this.chargeTime > 0) {
                    --this.chargeTime;
                }

                this.ghast.setCharging(this.chargeTime > 10);
            }
        }
    }
}
