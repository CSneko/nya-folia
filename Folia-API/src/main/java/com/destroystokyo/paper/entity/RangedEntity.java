package com.destroystokyo.paper.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.jetbrains.annotations.NotNull;

public interface RangedEntity extends Mob {
    /**
     * Attack the specified entity using a ranged attack.
     *
     * @param target the entity to target
     * @param charge How "charged" the attack is (how far back the bow was pulled for Bow attacks).
     *               This should be a value between 0 and 1, represented as targetDistance/maxDistance.
     */
    void rangedAttack(@NotNull LivingEntity target, float charge);

    /**
     * Sets that the Entity is "charging" up an attack, by raising its hands
     *
     * @param raiseHands Whether the entities hands are raised to charge attack
     * @deprecated use {@link #setAggressive(boolean)}
     */
    @Deprecated
    void setChargingAttack(boolean raiseHands);

    /**
     * Alias to {@link LivingEntity#isHandRaised()}, if the entity is charging an attack
     * @return If entities hands are raised
     * @deprecated use {@link #isHandRaised()}
     */
    @Deprecated
    default boolean isChargingAttack() {
        return isHandRaised();
    }
}
