package net.minecraft.world.entity.ai.goal;

import java.util.function.Predicate;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;

public class BreakDoorGoal extends DoorInteractGoal {

    private static final int DEFAULT_DOOR_BREAK_TIME = 240;
    private final Predicate<Difficulty> validDifficulties;
    protected int breakTime;
    protected int lastBreakProgress;
    protected int doorBreakTime;

    public BreakDoorGoal(Mob mob, Predicate<Difficulty> difficultySufficientPredicate) {
        super(mob);
        this.lastBreakProgress = -1;
        this.doorBreakTime = -1;
        this.validDifficulties = difficultySufficientPredicate;
    }

    public BreakDoorGoal(Mob mob, int maxProgress, Predicate<Difficulty> difficultySufficientPredicate) {
        this(mob, difficultySufficientPredicate);
        this.doorBreakTime = maxProgress;
    }

    protected int getDoorBreakTime() {
        return Math.max(240, this.doorBreakTime);
    }

    @Override
    public boolean canUse() {
        return !super.canUse() ? false : (!this.mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? false : this.isValidDifficulty(this.mob.level().getDifficulty()) && !this.isOpen());
    }

    @Override
    public void start() {
        super.start();
        this.breakTime = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.breakTime <= this.getDoorBreakTime() && !this.isOpen() && this.doorPos.closerToCenterThan(this.mob.position(), 2.0D) && this.isValidDifficulty(this.mob.level().getDifficulty());
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.level().destroyBlockProgress(this.mob.getId(), this.doorPos, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mob.getRandom().nextInt(20) == 0) {
            this.mob.level().levelEvent(1019, this.doorPos, 0);
            if (!this.mob.swinging) {
                this.mob.swing(this.mob.getUsedItemHand());
            }
        }

        ++this.breakTime;
        int i = (int) ((float) this.breakTime / (float) this.getDoorBreakTime() * 10.0F);

        if (i != this.lastBreakProgress) {
            this.mob.level().destroyBlockProgress(this.mob.getId(), this.doorPos, i);
            this.lastBreakProgress = i;
        }

        if (this.breakTime == this.getDoorBreakTime() && this.isValidDifficulty(this.mob.level().getDifficulty())) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreakDoorEvent(this.mob, this.doorPos, this.mob.level().getFluidState(this.doorPos).createLegacyBlock()).isCancelled()) { // Paper - fix wrong block state
                this.start();
                return;
            }
            // CraftBukkit end
            final net.minecraft.world.level.block.state.BlockState oldState = this.mob.level().getBlockState(this.doorPos); // Paper - fix MC-263999
            this.mob.level().removeBlock(this.doorPos, false);
            this.mob.level().levelEvent(1021, this.doorPos, 0);
            this.mob.level().levelEvent(2001, this.doorPos, Block.getId(oldState)); // Paper - fix MC-263999
        }

    }

    private boolean isValidDifficulty(Difficulty difficulty) {
        return this.validDifficulties.test(difficulty);
    }
}
