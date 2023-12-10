package net.minecraft.world.entity.monster;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Evoker extends SpellcasterIllager {

    @Nullable
    private Sheep wololoTarget;

    public Evoker(EntityType<? extends Evoker> type, Level world) {
        super(type, world);
        this.xpReward = 10;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new Evoker.EvokerCastingSpellGoal());
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 0.6D, 1.0D));
        this.goalSelector.addGoal(4, new Evoker.EvokerSummonSpellGoal());
        this.goalSelector.addGoal(5, new Evoker.EvokerAttackSpellGoal());
        this.goalSelector.addGoal(6, new Evoker.EvokerWololoSpellGoal());
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{Raider.class})).setAlertOthers());
        this.targetSelector.addGoal(2, (new NearestAttackableTargetGoal<>(this, Player.class, true)).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, (new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false)).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, false));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.5D).add(Attributes.FOLLOW_RANGE, 12.0D).add(Attributes.MAX_HEALTH, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.EVOKER_CELEBRATE;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other == null ? false : (other == this ? true : (super.isAlliedTo(other) ? true : (other instanceof Vex ? this.isAlliedTo((Entity) ((Vex) other).getOwner()) : (other instanceof LivingEntity && ((LivingEntity) other).getMobType() == MobType.ILLAGER ? this.getTeam() == null && other.getTeam() == null : false))));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.EVOKER_HURT;
    }

    public void setWololoTarget(@Nullable Sheep wololoTarget) {
        this.wololoTarget = wololoTarget;
    }

    public @Nullable
    Sheep getWololoTarget() {
        return this.wololoTarget;
    }

    @Override
    protected SoundEvent getCastingSoundEvent() {
        return SoundEvents.EVOKER_CAST_SPELL;
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {}

    private class EvokerCastingSpellGoal extends SpellcasterIllager.SpellcasterCastingSpellGoal {

        EvokerCastingSpellGoal() {
            super();
        }

        @Override
        public void tick() {
            if (Evoker.this.getTarget() != null) {
                Evoker.this.getLookControl().setLookAt(Evoker.this.getTarget(), (float) Evoker.this.getMaxHeadYRot(), (float) Evoker.this.getMaxHeadXRot());
            } else if (Evoker.this.getWololoTarget() != null) {
                Evoker.this.getLookControl().setLookAt(Evoker.this.getWololoTarget(), (float) Evoker.this.getMaxHeadYRot(), (float) Evoker.this.getMaxHeadXRot());
            }

        }
    }

    private class EvokerSummonSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {

        private final TargetingConditions vexCountTargeting = TargetingConditions.forNonCombat().range(16.0D).ignoreLineOfSight().ignoreInvisibilityTesting();

        EvokerSummonSpellGoal() {
            super();
        }

        @Override
        public boolean canUse() {
            if (!super.canUse()) {
                return false;
            } else {
                int i = Evoker.this.level().getNearbyEntities(Vex.class, this.vexCountTargeting, Evoker.this, Evoker.this.getBoundingBox().inflate(16.0D)).size();

                return Evoker.this.random.nextInt(8) + 1 > i;
            }
        }

        @Override
        protected int getCastingTime() {
            return 100;
        }

        @Override
        protected int getCastingInterval() {
            return 340;
        }

        @Override
        protected void performSpellCasting() {
            ServerLevel worldserver = (ServerLevel) Evoker.this.level();

            for (int i = 0; i < 3; ++i) {
                BlockPos blockposition = Evoker.this.blockPosition().offset(-2 + Evoker.this.random.nextInt(5), 1, -2 + Evoker.this.random.nextInt(5));
                Vex entityvex = (Vex) EntityType.VEX.create(Evoker.this.level());

                if (entityvex != null) {
                    entityvex.moveTo(blockposition, 0.0F, 0.0F);
                    entityvex.finalizeSpawn(worldserver, Evoker.this.level().getCurrentDifficultyAt(blockposition), MobSpawnType.MOB_SUMMONED, (SpawnGroupData) null, (CompoundTag) null);
                    entityvex.setOwner(Evoker.this);
                    entityvex.setBoundOrigin(blockposition);
                    entityvex.setLimitedLife(20 * (30 + Evoker.this.random.nextInt(90)));
                    worldserver.addFreshEntityWithPassengers(entityvex, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPELL); // CraftBukkit - Add SpawnReason
                    worldserver.gameEvent(GameEvent.ENTITY_PLACE, blockposition, GameEvent.Context.of((Entity) Evoker.this));
                }
            }

        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_SUMMON;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.SUMMON_VEX;
        }
    }

    private class EvokerAttackSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {

        EvokerAttackSpellGoal() {
            super();
        }

        @Override
        protected int getCastingTime() {
            return 40;
        }

        @Override
        protected int getCastingInterval() {
            return 100;
        }

        @Override
        protected void performSpellCasting() {
            LivingEntity entityliving = Evoker.this.getTarget();
            double d0 = Math.min(entityliving.getY(), Evoker.this.getY());
            double d1 = Math.max(entityliving.getY(), Evoker.this.getY()) + 1.0D;
            float f = (float) Mth.atan2(entityliving.getZ() - Evoker.this.getZ(), entityliving.getX() - Evoker.this.getX());
            int i;

            if (Evoker.this.distanceToSqr((Entity) entityliving) < 9.0D) {
                float f1;

                for (i = 0; i < 5; ++i) {
                    f1 = f + (float) i * 3.1415927F * 0.4F;
                    this.createSpellEntity(Evoker.this.getX() + (double) Mth.cos(f1) * 1.5D, Evoker.this.getZ() + (double) Mth.sin(f1) * 1.5D, d0, d1, f1, 0);
                }

                for (i = 0; i < 8; ++i) {
                    f1 = f + (float) i * 3.1415927F * 2.0F / 8.0F + 1.2566371F;
                    this.createSpellEntity(Evoker.this.getX() + (double) Mth.cos(f1) * 2.5D, Evoker.this.getZ() + (double) Mth.sin(f1) * 2.5D, d0, d1, f1, 3);
                }
            } else {
                for (i = 0; i < 16; ++i) {
                    double d2 = 1.25D * (double) (i + 1);
                    int j = 1 * i;

                    this.createSpellEntity(Evoker.this.getX() + (double) Mth.cos(f) * d2, Evoker.this.getZ() + (double) Mth.sin(f) * d2, d0, d1, f, j);
                }
            }

        }

        private void createSpellEntity(double x, double z, double maxY, double y, float yaw, int warmup) {
            BlockPos blockposition = BlockPos.containing(x, y, z);
            boolean flag = false;
            double d4 = 0.0D;

            do {
                BlockPos blockposition1 = blockposition.below();
                BlockState iblockdata = Evoker.this.level().getBlockState(blockposition1);

                if (iblockdata.isFaceSturdy(Evoker.this.level(), blockposition1, Direction.UP)) {
                    if (!Evoker.this.level().isEmptyBlock(blockposition)) {
                        BlockState iblockdata1 = Evoker.this.level().getBlockState(blockposition);
                        VoxelShape voxelshape = iblockdata1.getCollisionShape(Evoker.this.level(), blockposition);

                        if (!voxelshape.isEmpty()) {
                            d4 = voxelshape.max(Direction.Axis.Y);
                        }
                    }

                    flag = true;
                    break;
                }

                blockposition = blockposition.below();
            } while (blockposition.getY() >= Mth.floor(maxY) - 1);

            if (flag) {
                Evoker.this.level().addFreshEntity(new EvokerFangs(Evoker.this.level(), x, (double) blockposition.getY() + d4, z, yaw, warmup, Evoker.this));
                Evoker.this.level().gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, (double) blockposition.getY() + d4, z), GameEvent.Context.of((Entity) Evoker.this));
            }

        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_ATTACK;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.FANGS;
        }
    }

    public class EvokerWololoSpellGoal extends SpellcasterIllager.SpellcasterUseSpellGoal {

        private final TargetingConditions wololoTargeting = TargetingConditions.forNonCombat().range(16.0D).selector((entityliving) -> {
            return ((Sheep) entityliving).getColor() == DyeColor.BLUE;
        });

        public EvokerWololoSpellGoal() {
            super();
        }

        @Override
        public boolean canUse() {
            if (Evoker.this.getTarget() != null) {
                return false;
            } else if (Evoker.this.isCastingSpell()) {
                return false;
            } else if (Evoker.this.tickCount < this.nextAttackTickCount) {
                return false;
            } else if (!Evoker.this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return false;
            } else {
                List<Sheep> list = Evoker.this.level().getNearbyEntities(Sheep.class, this.wololoTargeting, Evoker.this, Evoker.this.getBoundingBox().inflate(16.0D, 4.0D, 16.0D));

                if (list.isEmpty()) {
                    return false;
                } else {
                    Evoker.this.setWololoTarget((Sheep) list.get(Evoker.this.random.nextInt(list.size())));
                    return true;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return Evoker.this.getWololoTarget() != null && this.attackWarmupDelay > 0;
        }

        @Override
        public void stop() {
            super.stop();
            Evoker.this.setWololoTarget((Sheep) null);
        }

        @Override
        protected void performSpellCasting() {
            Sheep entitysheep = Evoker.this.getWololoTarget();

            if (entitysheep != null && entitysheep.isAlive()) {
                entitysheep.setColor(DyeColor.RED);
            }

        }

        @Override
        protected int getCastWarmupTime() {
            return 40;
        }

        @Override
        protected int getCastingTime() {
            return 60;
        }

        @Override
        protected int getCastingInterval() {
            return 140;
        }

        @Override
        protected SoundEvent getSpellPrepareSound() {
            return SoundEvents.EVOKER_PREPARE_WOLOLO;
        }

        @Override
        protected SpellcasterIllager.IllagerSpell getSpell() {
            return SpellcasterIllager.IllagerSpell.WOLOLO;
        }
    }
}
