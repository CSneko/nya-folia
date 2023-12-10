package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityUnleashEvent;
// CraftBukkit end

public abstract class PathfinderMob extends Mob {

    protected static final float DEFAULT_WALK_TARGET_VALUE = 0.0F;

    protected PathfinderMob(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    public org.bukkit.craftbukkit.entity.CraftCreature getBukkitCreature() { return (org.bukkit.craftbukkit.entity.CraftCreature) super.getBukkitEntity(); } // Paper
    public BlockPos movingTarget = null; public BlockPos getMovingTarget() { return movingTarget; } // Paper

    public float getWalkTargetValue(BlockPos pos) {
        return this.getWalkTargetValue(pos, this.level());
    }

    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return 0.0F;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor world, MobSpawnType spawnReason) {
        return this.getWalkTargetValue(this.blockPosition(), world) >= 0.0F;
    }

    public boolean isPathFinding() {
        return !this.getNavigation().isDone();
    }

    public boolean isPanicking() {
        return this.brain.hasMemoryValue(MemoryModuleType.IS_PANICKING) ? this.brain.getMemory(MemoryModuleType.IS_PANICKING).isPresent() : this.goalSelector.getRunningGoals().anyMatch((pathfindergoalwrapped) -> {
            return pathfindergoalwrapped.getGoal() instanceof PanicGoal;
        });
    }

    @Override
    protected void tickLeash() {
        super.tickLeash();
        Entity entity = this.getLeashHolder();

        if (entity != null && entity.level() == this.level()) {
            this.restrictTo(entity.blockPosition(), 5);
            float f = this.distanceTo(entity);

            if (this instanceof TamableAnimal && ((TamableAnimal) this).isInSittingPose()) {
                if (f > entity.level().paperConfig().misc.maxLeashDistance) { // Paper
                    // Paper start - drop leash variable
                    EntityUnleashEvent event = new EntityUnleashEvent(this.getBukkitEntity(), EntityUnleashEvent.UnleashReason.DISTANCE, true);
                    if (!event.callEvent()) { return; }
                    this.dropLeash(true, event.isDropLeash());
                    // Paper end
                }

                return;
            }

            this.onLeashDistance(f);
            if (f > entity.level().paperConfig().misc.maxLeashDistance) { // Paper
                // Paper start - drop leash variable
                EntityUnleashEvent event = new EntityUnleashEvent(this.getBukkitEntity(), EntityUnleashEvent.UnleashReason.DISTANCE, true);
                if (!event.callEvent()) { return; }
                this.dropLeash(true, event.isDropLeash());
                // Paper end
                this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            } else if (f > 6.0F) {
                double d0 = (entity.getX() - this.getX()) / (double) f;
                double d1 = (entity.getY() - this.getY()) / (double) f;
                double d2 = (entity.getZ() - this.getZ()) / (double) f;

                this.setDeltaMovement(this.getDeltaMovement().add(Math.copySign(d0 * d0 * 0.4D, d0), Math.copySign(d1 * d1 * 0.4D, d1), Math.copySign(d2 * d2 * 0.4D, d2)));
                this.checkSlowFallDistance();
            } else if (this.shouldStayCloseToLeashHolder() && !this.isPanicking()) {
                this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
                float f1 = 2.0F;
                Vec3 vec3d = (new Vec3(entity.getX() - this.getX(), entity.getY() - this.getY(), entity.getZ() - this.getZ())).normalize().scale((double) Math.max(f - 2.0F, 0.0F));

                this.getNavigation().moveTo(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z, this.followLeashSpeed());
            }
        }

    }

    protected boolean shouldStayCloseToLeashHolder() {
        return true;
    }

    protected double followLeashSpeed() {
        return 1.0D;
    }

    protected void onLeashDistance(float leashLength) {}
}
