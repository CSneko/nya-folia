package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

public class SitWhenOrderedToGoal extends Goal {

    private final TamableAnimal mob;

    public SitWhenOrderedToGoal(TamableAnimal tameable) {
        this.mob = tameable;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.isOrderedToSit();
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isTame()) {
            return this.mob.isOrderedToSit() && this.mob.getTarget() == null; // CraftBukkit - Allow sitting for wild animals
        } else if (this.mob.isInWaterOrBubble()) {
            return false;
        } else if (!this.mob.onGround()) {
            return false;
        } else {
            LivingEntity entityliving = this.mob.getOwner();

            return entityliving == null ? true : (this.mob.distanceToSqr((Entity) entityliving) < 144.0D && entityliving.getLastHurtByMob() != null ? false : this.mob.isOrderedToSit());
        }
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.mob.setInSittingPose(true);
    }

    @Override
    public void stop() {
        this.mob.setInSittingPose(false);
    }
}
