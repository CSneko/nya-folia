package net.minecraft.world.entity.ai.goal.target;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.scores.Team;
// CraftBukkit start
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public abstract class TargetGoal extends Goal {

    private static final int EMPTY_REACH_CACHE = 0;
    private static final int CAN_REACH_CACHE = 1;
    private static final int CANT_REACH_CACHE = 2;
    protected final Mob mob;
    protected final boolean mustSee;
    private final boolean mustReach;
    private int reachCache;
    private int reachCacheTime;
    private int unseenTicks;
    @Nullable
    protected LivingEntity targetMob;
    protected int unseenMemoryTicks;

    public TargetGoal(Mob mob, boolean checkVisibility) {
        this(mob, checkVisibility, false);
    }

    public TargetGoal(Mob mob, boolean checkVisibility, boolean checkNavigable) {
        this.unseenMemoryTicks = 60;
        this.mob = mob;
        this.mustSee = checkVisibility;
        this.mustReach = checkNavigable;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity entityliving = this.mob.getTarget();

        if (entityliving == null) {
            entityliving = this.targetMob;
        }

        if (entityliving == null) {
            return false;
        } else if (!this.mob.canAttack(entityliving)) {
            return false;
        } else {
            Team scoreboardteambase = this.mob.getTeam();
            Team scoreboardteambase1 = entityliving.getTeam();

            if (scoreboardteambase != null && scoreboardteambase1 == scoreboardteambase) {
                return false;
            } else {
                double d0 = this.getFollowDistance();

                if (this.mob.distanceToSqr((Entity) entityliving) > d0 * d0) {
                    return false;
                } else {
                    if (this.mustSee) {
                        if (this.mob.getSensing().hasLineOfSight(entityliving)) {
                            this.unseenTicks = 0;
                        } else if (++this.unseenTicks > reducedTickDelay(this.unseenMemoryTicks)) {
                            return false;
                        }
                    }

                    this.mob.setTarget(entityliving, EntityTargetEvent.TargetReason.CLOSEST_ENTITY, true); // CraftBukkit
                    return true;
                }
            }
        }
    }

    protected double getFollowDistance() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public void start() {
        this.reachCache = 0;
        this.reachCacheTime = 0;
        this.unseenTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.setTarget((LivingEntity) null, EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit
        this.targetMob = null;
    }

    protected boolean canAttack(@Nullable LivingEntity target, TargetingConditions targetPredicate) {
        if (target == null) {
            return false;
        } else if (!targetPredicate.test(this.mob, target)) {
            return false;
        } else if (!this.mob.isWithinRestriction(target.blockPosition())) {
            return false;
        } else {
            if (this.mustReach) {
                if (--this.reachCacheTime <= 0) {
                    this.reachCache = 0;
                }

                if (this.reachCache == 0) {
                    this.reachCache = this.canReach(target) ? 1 : 2;
                }

                if (this.reachCache == 2) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean canReach(LivingEntity entity) {
        this.reachCacheTime = reducedTickDelay(10 + this.mob.getRandom().nextInt(5));
        Path pathentity = this.mob.getNavigation().createPath((Entity) entity, 0);

        if (pathentity == null) {
            return false;
        } else {
            Node pathpoint = pathentity.getEndNode();

            if (pathpoint == null) {
                return false;
            } else {
                int i = pathpoint.x - entity.getBlockX();
                int j = pathpoint.z - entity.getBlockZ();

                return (double) (i * i + j * j) <= 2.25D;
            }
        }
    }

    public TargetGoal setUnseenMemoryTicks(int time) {
        this.unseenMemoryTicks = time;
        return this;
    }
}
