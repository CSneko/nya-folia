package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.crafting.Ingredient;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
// CraftBukkit end

public class TemptGoal extends Goal {

    private static final TargetingConditions TEMP_TARGETING = TargetingConditions.forNonCombat().range(10.0D).ignoreLineOfSight();
    private final TargetingConditions targetingConditions;
    protected final PathfinderMob mob;
    private final double speedModifier;
    private double px;
    private double py;
    private double pz;
    private double pRotX;
    private double pRotY;
    @Nullable
    protected LivingEntity player; // CraftBukkit
    private int calmDown;
    private boolean isRunning;
    private final Ingredient items;
    private final boolean canScare;

    public TemptGoal(PathfinderMob entity, double speed, Ingredient food, boolean canBeScared) {
        this.mob = entity;
        this.speedModifier = speed;
        this.items = food;
        this.canScare = canBeScared;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.targetingConditions = TemptGoal.TEMP_TARGETING.copy().selector(this::shouldFollow);
    }

    @Override
    public boolean canUse() {
        if (this.calmDown > 0) {
            --this.calmDown;
            return false;
        } else {
            this.player = this.mob.level().getNearestPlayer(this.targetingConditions, this.mob);
            // CraftBukkit start
            if (this.player != null) {
                EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(this.mob, this.player, EntityTargetEvent.TargetReason.TEMPT);
                if (event.isCancelled()) {
                    return false;
                }
                this.player = (event.getTarget() == null) ? null : ((CraftLivingEntity) event.getTarget()).getHandle();
            }
            // CraftBukkit end
            return this.player != null;
        }
    }

    private boolean shouldFollow(LivingEntity entity) {
        return this.items.test(entity.getMainHandItem()) || this.items.test(entity.getOffhandItem());
    }

    @Override
    public boolean canContinueToUse() {
        if (this.canScare()) {
            if (this.mob.distanceToSqr((Entity) this.player) < 36.0D) {
                if (this.player.distanceToSqr(this.px, this.py, this.pz) > 0.010000000000000002D) {
                    return false;
                }

                if (Math.abs((double) this.player.getXRot() - this.pRotX) > 5.0D || Math.abs((double) this.player.getYRot() - this.pRotY) > 5.0D) {
                    return false;
                }
            } else {
                this.px = this.player.getX();
                this.py = this.player.getY();
                this.pz = this.player.getZ();
            }

            this.pRotX = (double) this.player.getXRot();
            this.pRotY = (double) this.player.getYRot();
        }

        return this.canUse();
    }

    protected boolean canScare() {
        return this.canScare;
    }

    @Override
    public void start() {
        this.px = this.player.getX();
        this.py = this.player.getY();
        this.pz = this.player.getZ();
        this.isRunning = true;
    }

    @Override
    public void stop() {
        this.player = null;
        this.mob.getNavigation().stop();
        this.calmDown = reducedTickDelay(100);
        this.isRunning = false;
    }

    @Override
    public void tick() {
        this.mob.getLookControl().setLookAt(this.player, (float) (this.mob.getMaxHeadYRot() + 20), (float) this.mob.getMaxHeadXRot());
        if (this.mob.distanceToSqr((Entity) this.player) < 6.25D) {
            this.mob.getNavigation().stop();
        } else {
            this.mob.getNavigation().moveTo((Entity) this.player, this.speedModifier);
        }

    }

    public boolean isRunning() {
        return this.isRunning;
    }
}
