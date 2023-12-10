package net.minecraft.world.entity.ai.targeting;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public class TargetingConditions {
    public static final TargetingConditions DEFAULT = forCombat();
    private static final double MIN_VISIBILITY_DISTANCE_FOR_INVISIBLE_TARGET = 2.0D;
    private final boolean isCombat;
    public double range = -1.0D; // Paper - public
    private boolean checkLineOfSight = true;
    private boolean testInvisible = true;
    @Nullable
    private Predicate<LivingEntity> selector;

    private TargetingConditions(boolean attackable) {
        this.isCombat = attackable;
    }

    public static TargetingConditions forCombat() {
        return new TargetingConditions(true);
    }

    public static TargetingConditions forNonCombat() {
        return new TargetingConditions(false);
    }

    public TargetingConditions copy() {
        TargetingConditions targetingConditions = this.isCombat ? forCombat() : forNonCombat();
        targetingConditions.range = this.range;
        targetingConditions.checkLineOfSight = this.checkLineOfSight;
        targetingConditions.testInvisible = this.testInvisible;
        targetingConditions.selector = this.selector;
        return targetingConditions;
    }

    public TargetingConditions range(double baseMaxDistance) {
        this.range = baseMaxDistance;
        return this;
    }

    public TargetingConditions ignoreLineOfSight() {
        this.checkLineOfSight = false;
        return this;
    }

    public TargetingConditions ignoreInvisibilityTesting() {
        this.testInvisible = false;
        return this;
    }

    public TargetingConditions selector(@Nullable Predicate<LivingEntity> predicate) {
        this.selector = predicate;
        return this;
    }

    public boolean test(@Nullable LivingEntity baseEntity, LivingEntity targetEntity) {
        if (baseEntity == targetEntity) {
            return false;
        } else if (!targetEntity.canBeSeenByAnyone()) {
            return false;
        } else if (this.selector != null && !this.selector.test(targetEntity)) {
            return false;
        } else {
            if (baseEntity == null) {
                if (this.isCombat && (!targetEntity.canBeSeenAsEnemy() || targetEntity.level().getDifficulty() == Difficulty.PEACEFUL)) {
                    return false;
                }
            } else {
                if (this.isCombat && (!baseEntity.canAttack(targetEntity) || !baseEntity.canAttackType(targetEntity.getType()) || baseEntity.isAlliedTo(targetEntity))) {
                    return false;
                }

                if (this.range > 0.0D) {
                    double d = this.testInvisible ? targetEntity.getVisibilityPercent(baseEntity) : 1.0D;
                    double e = Math.max((this.useFollowRange ? this.getFollowRange(baseEntity) : this.range) * d, 2.0D); // Paper
                    double f = baseEntity.distanceToSqr(targetEntity.getX(), targetEntity.getY(), targetEntity.getZ());
                    if (f > e * e) {
                        return false;
                    }
                }

                if (this.checkLineOfSight && baseEntity instanceof Mob) {
                    Mob mob = (Mob)baseEntity;
                    if (!mob.getSensing().hasLineOfSight(targetEntity)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    // Paper start
    private boolean useFollowRange = false;

    public TargetingConditions useFollowRange() {
        this.useFollowRange = true;
        return this;
    }

    private double getFollowRange(LivingEntity entityliving) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance attributeinstance = entityliving.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        return attributeinstance == null ? 16.0D : attributeinstance.getValue();
    }
    // Paper end
}
