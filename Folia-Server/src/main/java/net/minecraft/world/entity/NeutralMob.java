package net.minecraft.world.entity;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public interface NeutralMob {

    String TAG_ANGER_TIME = "AngerTime";
    String TAG_ANGRY_AT = "AngryAt";

    int getRemainingPersistentAngerTime();

    void setRemainingPersistentAngerTime(int angerTime);

    @Nullable
    UUID getPersistentAngerTarget();

    void setPersistentAngerTarget(@Nullable UUID angryAt);

    void startPersistentAngerTimer();

    default void addPersistentAngerSaveData(CompoundTag nbt) {
        nbt.putInt("AngerTime", this.getRemainingPersistentAngerTime());
        if (this.getPersistentAngerTarget() != null) {
            nbt.putUUID("AngryAt", this.getPersistentAngerTarget());
        }

    }

    default void readPersistentAngerSaveData(Level world, CompoundTag nbt) {
        this.setRemainingPersistentAngerTime(nbt.getInt("AngerTime"));
        if (world instanceof ServerLevel) {
            if (!nbt.hasUUID("AngryAt")) {
                this.setPersistentAngerTarget((UUID) null);
            } else {
                UUID uuid = nbt.getUUID("AngryAt");

                this.setPersistentAngerTarget(uuid);
                // Paper - Moved diff to separate method
                // If this entity already survived its first tick, e.g. is loaded and ticked in sync, actively
                // tick the initial persistent anger.
                // If not, let the first tick on the baseTick call the method later down the line.
                if (this instanceof Entity entity && !entity.firstTick) this.tickInitialPersistentAnger(world);
            }
        }
    }

    default void updatePersistentAnger(ServerLevel world, boolean angerPersistent) {
        LivingEntity entityliving = this.getTarget();
        UUID uuid = this.getPersistentAngerTarget();

        if ((entityliving == null || entityliving.isDeadOrDying()) && uuid != null && world.getEntity(uuid) instanceof Mob) {
            this.stopBeingAngry();
        } else {
            if (entityliving != null && !Objects.equals(uuid, entityliving.getUUID())) {
                this.setPersistentAngerTarget(entityliving.getUUID());
                this.startPersistentAngerTimer();
            }

            if (this.getRemainingPersistentAngerTime() > 0 && (entityliving == null || entityliving.getType() != EntityType.PLAYER || !angerPersistent)) {
                this.setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime() - 1);
                if (this.getRemainingPersistentAngerTime() == 0) {
                    this.stopBeingAngry();
                }
            }

        }
    }

    default boolean isAngryAt(LivingEntity entity) {
        return !this.canAttack(entity) ? false : (entity.getType() == EntityType.PLAYER && this.isAngryAtAllPlayers(entity.level()) ? true : entity.getUUID().equals(this.getPersistentAngerTarget()));
    }

    default boolean isAngryAtAllPlayers(Level world) {
        return world.getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        return this.getRemainingPersistentAngerTime() > 0;
    }

    default void playerDied(Player player) {
        if (player.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            if (player.getUUID().equals(this.getPersistentAngerTarget())) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob((LivingEntity) null);
        this.setPersistentAngerTarget((UUID) null);
        this.setTarget((LivingEntity) null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit
        this.setRemainingPersistentAngerTime(0);
    }

    @Nullable
    LivingEntity getLastHurtByMob();

    void setLastHurtByMob(@Nullable LivingEntity attacker);

    void setLastHurtByPlayer(@Nullable Player attacking);

    void setTarget(@Nullable LivingEntity target);

    boolean setTarget(@Nullable LivingEntity entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason reason, boolean fireEvent); // CraftBukkit

    boolean canAttack(LivingEntity target);

    @Nullable
    LivingEntity getTarget();

    // Paper start - Update last hurt when ticking
    default void tickInitialPersistentAnger(Level level) {
        UUID target = getPersistentAngerTarget();
        if (target == null) {
            return;
        }

        Entity entity = ((ServerLevel) level).getEntity(target);

        if (entity != null) {
            if (entity instanceof Mob) {
                this.setLastHurtByMob((Mob) entity);
            }

            if (entity.getType() == EntityType.PLAYER) {
                this.setLastHurtByPlayer((Player) entity);
            }

        }
    }
    // Paper end
}
