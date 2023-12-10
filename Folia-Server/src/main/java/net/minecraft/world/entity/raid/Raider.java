package net.minecraft.world.entity.raid;

import com.google.common.collect.Lists;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PathfindToRaidGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

public abstract class Raider extends PatrollingMonster {

    protected static final EntityDataAccessor<Boolean> IS_CELEBRATING = SynchedEntityData.defineId(Raider.class, EntityDataSerializers.BOOLEAN);
    static final Predicate<ItemEntity> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive() && ItemStack.matches(entityitem.getItem(), Raid.getLeaderBannerInstance());
    };
    @Nullable
    protected Raid raid;
    private int wave;
    private boolean canJoinRaid;
    private int ticksOutsideRaid;

    protected Raider(EntityType<? extends Raider> type, Level world) {
        super(type, world);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new Raider.ObtainRaidLeaderBannerGoal<>(this));
        this.goalSelector.addGoal(3, new PathfindToRaidGoal<>(this));
        this.goalSelector.addGoal(4, new Raider.RaiderMoveThroughVillageGoal(this, 1.0499999523162842D, 1));
        this.goalSelector.addGoal(5, new Raider.RaiderCelebration(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Raider.IS_CELEBRATING, false);
    }

    public abstract void applyRaidBuffs(int wave, boolean unused);

    public boolean canJoinRaid() {
        return this.canJoinRaid;
    }

    public void setCanJoinRaid(boolean ableToJoinRaid) {
        this.canJoinRaid = ableToJoinRaid;
    }

    @Override
    public void aiStep() {
        if (this.level() instanceof ServerLevel && this.isAlive()) {
            Raid raid = this.getCurrentRaid();

            if (this.canJoinRaid()) {
                if (raid == null) {
                    if (this.level().getRedstoneGameTime() % 20L == 0L) { // Folia - region threading
                        Raid raid1 = ((ServerLevel) this.level()).getRaidAt(this.blockPosition());

                        if (raid1 != null && Raids.canJoinRaid(this, raid1)) {
                            raid1.joinRaid(raid1.getGroupsSpawned(), this, (BlockPos) null, true);
                        }
                    }
                } else {
                    LivingEntity entityliving = this.getTarget();

                    if (entityliving != null && (entityliving.getType() == EntityType.PLAYER || entityliving.getType() == EntityType.IRON_GOLEM)) {
                        this.noActionTime = 0;
                    }
                }
            }
        }

        super.aiStep();
    }

    @Override
    protected void updateNoActionTime() {
        this.noActionTime += 2;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (this.level() instanceof ServerLevel) {
            Entity entity = damageSource.getEntity();
            Raid raid = this.getCurrentRaid();

            if (raid != null) {
                if (this.isPatrolLeader()) {
                    raid.removeLeader(this.getWave());
                }

                if (entity != null && entity.getType() == EntityType.PLAYER) {
                    raid.addHeroOfTheVillage(entity);
                }

                raid.removeFromRaid(this, false);
            }

            if (this.isPatrolLeader() && raid == null && ((ServerLevel) this.level()).getRaidAt(this.blockPosition()) == null) {
                ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
                Player entityhuman = null;

                if (entity instanceof Player) {
                    entityhuman = (Player) entity;
                } else if (entity instanceof Wolf) {
                    Wolf entitywolf = (Wolf) entity;
                    LivingEntity entityliving = entitywolf.getOwner();

                    if (entitywolf.isTame() && entityliving instanceof Player) {
                        entityhuman = (Player) entityliving;
                    }
                }

                if (!itemstack.isEmpty() && ItemStack.matches(itemstack, Raid.getLeaderBannerInstance()) && entityhuman != null) {
                    MobEffectInstance mobeffect = entityhuman.getEffect(MobEffects.BAD_OMEN);
                    byte b0 = 1;
                    int i;

                    if (mobeffect != null) {
                        i = b0 + mobeffect.getAmplifier();
                        entityhuman.removeEffectNoUpdate(MobEffects.BAD_OMEN);
                    } else {
                        i = b0 - 1;
                    }

                    i = Mth.clamp(i, 0, 4);
                    MobEffectInstance mobeffect1 = new MobEffectInstance(MobEffects.BAD_OMEN, 120000, i, false, false, true);

                    if (!this.level().getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                        entityhuman.addEffect(mobeffect1, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.PATROL_CAPTAIN); // CraftBukkit
                    }
                }
            }
        }

        super.die(damageSource);
    }

    @Override
    public boolean canJoinPatrol() {
        return !this.hasActiveRaid();
    }

    public void setCurrentRaid(@Nullable Raid raid) {
        this.raid = raid;
    }

    @Nullable
    public Raid getCurrentRaid() {
        return this.raid;
    }

    public boolean hasActiveRaid() {
        return this.getCurrentRaid() != null && this.getCurrentRaid().isActive();
    }

    public void setWave(int wave) {
        this.wave = wave;
    }

    public int getWave() {
        return this.wave;
    }

    public boolean isCelebrating() {
        return (Boolean) this.entityData.get(Raider.IS_CELEBRATING);
    }

    public void setCelebrating(boolean celebrating) {
        this.entityData.set(Raider.IS_CELEBRATING, celebrating);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Wave", this.wave);
        nbt.putBoolean("CanJoinRaid", this.canJoinRaid);
        if (this.raid != null) {
            nbt.putInt("RaidId", this.raid.getId());
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.wave = nbt.getInt("Wave");
        this.canJoinRaid = nbt.getBoolean("CanJoinRaid");
        if (nbt.contains("RaidId", 3)) {
            if (this.level() instanceof ServerLevel) {
                this.raid = ((ServerLevel) this.level()).getRaids().get(nbt.getInt("RaidId"));
            }

            if (this.raid != null) {
                this.raid.addWaveMob(this.wave, this, false);
                if (this.isPatrolLeader()) {
                    this.raid.setLeader(this.wave, this);
                }
            }
        }

    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        ItemStack itemstack = item.getItem();
        boolean flag = this.hasActiveRaid() && this.getCurrentRaid().getLeader(this.getWave()) != null;

        if (this.hasActiveRaid() && !flag && ItemStack.matches(itemstack, Raid.getLeaderBannerInstance())) {
            // Paper start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, item, 0, false).isCancelled()) {
                return;
            }
            // Paper end
            EquipmentSlot enumitemslot = EquipmentSlot.HEAD;
            ItemStack itemstack1 = this.getItemBySlot(enumitemslot);
            double d0 = (double) this.getEquipmentDropChance(enumitemslot);

            if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                this.forceDrops = true; // Paper
                this.spawnAtLocation(itemstack1);
                this.forceDrops = false; // Paper
            }

            this.onItemPickup(item);
            this.setItemSlot(enumitemslot, itemstack);
            this.take(item, itemstack.getCount());
            item.discard();
            this.getCurrentRaid().setLeader(this.getWave(), this);
            this.setPatrolLeader(true);
        } else {
            super.pickUpItem(item);
        }

    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return this.getCurrentRaid() == null ? super.removeWhenFarAway(distanceSquared) : false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCurrentRaid() != null;
    }

    public int getTicksOutsideRaid() {
        return this.ticksOutsideRaid;
    }

    public void setTicksOutsideRaid(int outOfRaidCounter) {
        this.ticksOutsideRaid = outOfRaidCounter;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.hasActiveRaid()) {
            this.getCurrentRaid().updateBossbar();
        }

        return super.hurt(source, amount);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        this.setCanJoinRaid(this.getType() != EntityType.WITCH || spawnReason != MobSpawnType.NATURAL);
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);
    }

    public abstract SoundEvent getCelebrateSound();

    public class ObtainRaidLeaderBannerGoal<T extends Raider> extends Goal {

        private final T mob;

        public ObtainRaidLeaderBannerGoal(T entityraider) { // CraftBukkit - decompile error
            this.mob = entityraider;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || !this.mob.canPickUpLoot()) return false; // Paper - respect game and entity rules for picking up items
            Raid raid = this.mob.getCurrentRaid();

            if (this.mob.hasActiveRaid() && !this.mob.getCurrentRaid().isOver() && this.mob.canBeLeader() && !ItemStack.matches(this.mob.getItemBySlot(EquipmentSlot.HEAD), Raid.getLeaderBannerInstance())) {
                Raider entityraider = raid.getLeader(this.mob.getWave());

                if (entityraider == null || !entityraider.isAlive()) {
                    List<ItemEntity> list = this.mob.level().getEntitiesOfClass(ItemEntity.class, this.mob.getBoundingBox().inflate(16.0D, 8.0D, 16.0D), Raider.ALLOWED_ITEMS);

                    if (!list.isEmpty()) {
                        return this.mob.getNavigation().moveTo((Entity) list.get(0), 1.149999976158142D);
                    }
                }

                return false;
            } else {
                return false;
            }
        }

        @Override
        public void tick() {
            if (this.mob.getNavigation().getTargetPos().closerToCenterThan(this.mob.position(), 1.414D)) {
                List<ItemEntity> list = this.mob.level().getEntitiesOfClass(ItemEntity.class, this.mob.getBoundingBox().inflate(4.0D, 4.0D, 4.0D), Raider.ALLOWED_ITEMS);

                if (!list.isEmpty()) {
                    this.mob.pickUpItem((ItemEntity) list.get(0));
                }
            }

        }
    }

    private static class RaiderMoveThroughVillageGoal extends Goal {

        private final Raider raider;
        private final double speedModifier;
        private BlockPos poiPos;
        private final List<BlockPos> visited = Lists.newArrayList();
        private final int distanceToPoi;
        private boolean stuck;

        public RaiderMoveThroughVillageGoal(Raider raider, double speed, int distance) {
            this.raider = raider;
            this.speedModifier = speed;
            this.distanceToPoi = distance;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.updateVisited();
            return this.isValidRaid() && this.hasSuitablePoi() && this.raider.getTarget() == null;
        }

        private boolean isValidRaid() {
            return this.raider.hasActiveRaid() && !this.raider.getCurrentRaid().isOver();
        }

        private boolean hasSuitablePoi() {
            ServerLevel worldserver = (ServerLevel) this.raider.level();
            BlockPos blockposition = this.raider.blockPosition();
            Optional<BlockPos> optional = worldserver.getPoiManager().getRandom((holder) -> {
                return holder.is(PoiTypes.HOME);
            }, this::hasNotVisited, PoiManager.Occupancy.ANY, blockposition, 48, this.raider.random);

            if (optional.isEmpty()) {
                return false;
            } else {
                this.poiPos = ((BlockPos) optional.get()).immutable();
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.raider.getNavigation().isDone() ? false : this.raider.getTarget() == null && !this.poiPos.closerToCenterThan(this.raider.position(), (double) (this.raider.getBbWidth() + (float) this.distanceToPoi)) && !this.stuck;
        }

        @Override
        public void stop() {
            if (this.poiPos.closerToCenterThan(this.raider.position(), (double) this.distanceToPoi)) {
                this.visited.add(this.poiPos);
            }

        }

        @Override
        public void start() {
            super.start();
            this.raider.setNoActionTime(0);
            this.raider.getNavigation().moveTo((double) this.poiPos.getX(), (double) this.poiPos.getY(), (double) this.poiPos.getZ(), this.speedModifier);
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.raider.getNavigation().isDone()) {
                Vec3 vec3d = Vec3.atBottomCenterOf(this.poiPos);
                Vec3 vec3d1 = DefaultRandomPos.getPosTowards(this.raider, 16, 7, vec3d, 0.3141592741012573D);

                if (vec3d1 == null) {
                    vec3d1 = DefaultRandomPos.getPosTowards(this.raider, 8, 7, vec3d, 1.5707963705062866D);
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.raider.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, this.speedModifier);
            }

        }

        private boolean hasNotVisited(BlockPos pos) {
            Iterator iterator = this.visited.iterator();

            BlockPos blockposition1;

            do {
                if (!iterator.hasNext()) {
                    return true;
                }

                blockposition1 = (BlockPos) iterator.next();
            } while (!Objects.equals(pos, blockposition1));

            return false;
        }

        private void updateVisited() {
            if (this.visited.size() > 2) {
                this.visited.remove(0);
            }

        }
    }

    public class RaiderCelebration extends Goal {

        private final Raider mob;

        RaiderCelebration(Raider entityraider) {
            this.mob = entityraider;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Raid raid = this.mob.getCurrentRaid();

            return this.mob.isAlive() && this.mob.getTarget() == null && raid != null && raid.isLoss();
        }

        @Override
        public void start() {
            this.mob.setCelebrating(true);
            super.start();
        }

        @Override
        public void stop() {
            this.mob.setCelebrating(false);
            super.stop();
        }

        @Override
        public void tick() {
            if (!this.mob.isSilent() && this.mob.random.nextInt(this.adjustedTickDelay(100)) == 0) {
                Raider.this.playSound(Raider.this.getCelebrateSound(), Raider.this.getSoundVolume(), Raider.this.getVoicePitch());
            }

            if (!this.mob.isPassenger() && this.mob.random.nextInt(this.adjustedTickDelay(50)) == 0) {
                this.mob.getJumpControl().jump();
            }

            super.tick();
        }
    }

    protected class HoldGroundAttackGoal extends Goal {

        private final Raider mob;
        private final float hostileRadiusSqr;
        public final TargetingConditions shoutTargeting = TargetingConditions.forNonCombat().range(8.0D).ignoreLineOfSight().ignoreInvisibilityTesting();

        public HoldGroundAttackGoal(AbstractIllager entityillagerabstract, float f) {
            this.mob = entityillagerabstract;
            this.hostileRadiusSqr = f * f;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity entityliving = this.mob.getLastHurtByMob();

            return this.mob.getCurrentRaid() == null && this.mob.isPatrolling() && this.mob.getTarget() != null && !this.mob.isAggressive() && (entityliving == null || entityliving.getType() != EntityType.PLAYER);
        }

        @Override
        public void start() {
            super.start();
            this.mob.getNavigation().stop();
            List<Raider> list = this.mob.level().getNearbyEntities(Raider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0D, 8.0D, 8.0D));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Raider entityraider = (Raider) iterator.next();

                entityraider.setTarget(this.mob.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER, true); // CraftBukkit
            }

        }

        @Override
        public void stop() {
            super.stop();
            LivingEntity entityliving = this.mob.getTarget();

            if (entityliving != null) {
                List<Raider> list = this.mob.level().getNearbyEntities(Raider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0D, 8.0D, 8.0D));
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    Raider entityraider = (Raider) iterator.next();

                    entityraider.setTarget(this.mob.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER, true); // CraftBukkit
                    entityraider.setAggressive(true);
                }

                this.mob.setAggressive(true);
            }

        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity entityliving = this.mob.getTarget();

            if (entityliving != null) {
                if (this.mob.distanceToSqr((Entity) entityliving) > (double) this.hostileRadiusSqr) {
                    this.mob.getLookControl().setLookAt(entityliving, 30.0F, 30.0F);
                    if (this.mob.random.nextInt(50) == 0) {
                        this.mob.playAmbientSound();
                    }
                } else {
                    this.mob.setAggressive(true);
                }

                super.tick();
            }
        }
    }
}
