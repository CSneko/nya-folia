package net.minecraft.world.entity.animal;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class Pufferfish extends AbstractFish {

    private static final EntityDataAccessor<Integer> PUFF_STATE = SynchedEntityData.defineId(Pufferfish.class, EntityDataSerializers.INT);
    int inflateCounter;
    int deflateTimer;
    private static final Predicate<LivingEntity> SCARY_MOB = (entityliving) -> {
        return entityliving instanceof Player && ((Player) entityliving).isCreative() ? false : entityliving.getType() == EntityType.AXOLOTL || entityliving.getMobType() != MobType.WATER;
    };
    static final TargetingConditions targetingConditions = TargetingConditions.forNonCombat().ignoreInvisibilityTesting().ignoreLineOfSight().selector(Pufferfish.SCARY_MOB);
    public static final int STATE_SMALL = 0;
    public static final int STATE_MID = 1;
    public static final int STATE_FULL = 2;

    public Pufferfish(EntityType<? extends Pufferfish> type, Level world) {
        super(type, world);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Pufferfish.PUFF_STATE, 0);
    }

    public int getPuffState() {
        return (Integer) this.entityData.get(Pufferfish.PUFF_STATE);
    }

    public void setPuffState(int puffState) {
        this.entityData.set(Pufferfish.PUFF_STATE, puffState);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Pufferfish.PUFF_STATE.equals(data)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("PuffState", this.getPuffState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setPuffState(Math.min(nbt.getInt("PuffState"), 2));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.PUFFERFISH_BUCKET);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new Pufferfish.PufferfishPuffGoal(this));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && this.isEffectiveAi()) {
            if (this.inflateCounter > 0) {
                boolean increase = true; // Paper - Add PufferFishStateChangeEvent
                if (this.getPuffState() == 0) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), 1).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_UP, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(1);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                } else if (this.inflateCounter > 40 && this.getPuffState() == 1) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), 2).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_UP, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(2);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                }

                if (increase) { // Paper - Add PufferFishStateChangeEvent
                ++this.inflateCounter;
                } // Paper - Add PufferFishStateChangeEvent
            } else if (this.getPuffState() != 0) {
                boolean increase = true; // Paper - Add PufferFishStateChangeEvent
                if (this.deflateTimer > 60 && this.getPuffState() == 2) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), 1).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_OUT, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(1);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                } else if (this.deflateTimer > 100 && this.getPuffState() == 1) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), 0).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.playSound(SoundEvents.PUFFER_FISH_BLOW_OUT, this.getSoundVolume(), this.getVoicePitch());
                    this.setPuffState(0);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                }

                if (increase) { // Paper - Add PufferFishStateChangeEvent
                ++this.deflateTimer;
                } // Paper - Add PufferFishStateChangeEvent
            }
        }

        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.getPuffState() > 0) {
            List<Mob> list = this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(0.3D), (entityinsentient) -> {
                return Pufferfish.targetingConditions.test(this, entityinsentient);
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Mob entityinsentient = (Mob) iterator.next();

                if (entityinsentient.isAlive()) {
                    this.touch(entityinsentient);
                }
            }
        }

    }

    private void touch(Mob mob) {
        int i = this.getPuffState();

        if (mob.hurt(this.damageSources().mobAttack(this), (float) (1 + i))) {
            mob.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * i, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            this.playSound(SoundEvents.PUFFER_FISH_STING, 1.0F, 1.0F);
        }

    }

    @Override
    public void playerTouch(Player player) {
        int i = this.getPuffState();

        if (player instanceof ServerPlayer && i > 0 && player.hurt(this.damageSources().mobAttack(this), (float) (1 + i))) {
            if (!this.isSilent()) {
                ((ServerPlayer) player).connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.PUFFER_FISH_STING, 0.0F));
            }

            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * i, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }

    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PUFFER_FISH_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PUFFER_FISH_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PUFFER_FISH_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.PUFFER_FISH_FLOP;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(Pufferfish.getScale(this.getPuffState()));
    }

    private static float getScale(int puffState) {
        switch (puffState) {
            case 0:
                return 0.5F;
            case 1:
                return 0.7F;
            default:
                return 1.0F;
        }
    }

    private static class PufferfishPuffGoal extends Goal {

        private final Pufferfish fish;

        public PufferfishPuffGoal(Pufferfish pufferfish) {
            this.fish = pufferfish;
        }

        @Override
        public boolean canUse() {
            List<LivingEntity> list = this.fish.level().getEntitiesOfClass(LivingEntity.class, this.fish.getBoundingBox().inflate(2.0D), (entityliving) -> {
                return Pufferfish.targetingConditions.test(this.fish, entityliving);
            });

            return !list.isEmpty();
        }

        @Override
        public void start() {
            this.fish.inflateCounter = 1;
            this.fish.deflateTimer = 0;
        }

        @Override
        public void stop() {
            this.fish.inflateCounter = 0;
        }
    }
}
