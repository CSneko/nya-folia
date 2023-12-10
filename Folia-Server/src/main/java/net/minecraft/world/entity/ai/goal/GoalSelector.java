package net.minecraft.world.entity.ai.goal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class GoalSelector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override
        public boolean canUse() {
            return false;
        }
    }) {
        @Override
        public boolean isRunning() {
            return false;
        }
    };
    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap<>(Goal.Flag.class);
    private final Set<WrappedGoal> availableGoals = Sets.newLinkedHashSet();
    private final Supplier<ProfilerFiller> profiler;
    private final EnumSet<Goal.Flag> disabledFlags = EnumSet.noneOf(Goal.Flag.class); // Paper unused, but dummy to prevent plugins from crashing as hard. Theyll need to support paper in a special case if this is super important, but really doesn't seem like it would be.
    private final com.destroystokyo.paper.util.set.OptimizedSmallEnumSet<net.minecraft.world.entity.ai.goal.Goal.Flag> goalTypes = new com.destroystokyo.paper.util.set.OptimizedSmallEnumSet<>(Goal.Flag.class); // Paper - remove streams from pathfindergoalselector
    private int tickCount;
    private int newGoalRate = 3;
    private int curRate;
    private static final Goal.Flag[] GOAL_FLAG_VALUES = Goal.Flag.values(); // Paper - remove streams from pathfindergoalselector

    public GoalSelector(Supplier<ProfilerFiller> profiler) {
        this.profiler = profiler;
    }

    public void addGoal(int priority, Goal goal) {
        this.availableGoals.add(new WrappedGoal(priority, goal));
    }

    @VisibleForTesting
    public void removeAllGoals(Predicate<Goal> predicate) {
        this.availableGoals.removeIf((goal) -> {
            return predicate.test(goal.getGoal());
        });
    }

    // Paper start
    public boolean inactiveTick() {
        this.curRate++;
        return this.curRate % this.newGoalRate == 0;
    }
    public boolean hasTasks() {
        for (WrappedGoal task : this.availableGoals) {
            if (task.isRunning()) {
                return true;
            }
        }
        return false;
    }
    // Paper end
    public void removeGoal(Goal goal) {
        // Paper start - remove streams from pathfindergoalselector
        for (java.util.Iterator<WrappedGoal> iterator = this.availableGoals.iterator(); iterator.hasNext();) {
            WrappedGoal goalWrapped = iterator.next();
            if (goalWrapped.getGoal() != goal) {
                continue;
            }
            if (goalWrapped.isRunning()) {
                goalWrapped.stop();
            }
            iterator.remove();
        }
        // Paper end - remove streams from pathfindergoalselector
    }

    private static boolean goalContainsAnyFlags(WrappedGoal goal, com.destroystokyo.paper.util.set.OptimizedSmallEnumSet<Goal.Flag> controls) {
        return goal.getFlags().hasCommonElements(controls); // Paper
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> goalsByControl) {
        // Paper start
        long flagIterator = goal.getFlags().getBackingSet();
        int wrappedGoalSize = goal.getFlags().size();
        for (int i = 0; i < wrappedGoalSize; ++i) {
            final Goal.Flag flag = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
            flagIterator ^= io.papermc.paper.util.IntegerUtil.getTrailingBit(flagIterator);
            // Paper end
            if (!goalsByControl.getOrDefault(flag, NO_GOAL).canBeReplacedBy(goal)) {
                return false;
            }
        }

        return true;
    }

    public void tick() {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("goalCleanup");

        for(WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (goalContainsAnyFlags(wrappedGoal, this.goalTypes) || !wrappedGoal.canContinueToUse())) {
                wrappedGoal.stop();
            }
        }

        Iterator<Map.Entry<Goal.Flag, WrappedGoal>> iterator = this.lockedFlags.entrySet().iterator();

        while(iterator.hasNext()) {
            Map.Entry<Goal.Flag, WrappedGoal> entry = iterator.next();
            if (!entry.getValue().isRunning()) {
                iterator.remove();
            }
        }

        profilerFiller.pop();
        profilerFiller.push("goalUpdate");

        for(WrappedGoal wrappedGoal2 : this.availableGoals) {
            // Paper start
            if (!wrappedGoal2.isRunning() && !goalContainsAnyFlags(wrappedGoal2, this.goalTypes) && goalCanBeReplacedForAllFlags(wrappedGoal2, this.lockedFlags) && wrappedGoal2.canUse()) {
                long flagIterator = wrappedGoal2.getFlags().getBackingSet();
                int wrappedGoalSize = wrappedGoal2.getFlags().size();
                for (int i = 0; i < wrappedGoalSize; ++i) {
                    final Goal.Flag flag = GOAL_FLAG_VALUES[Long.numberOfTrailingZeros(flagIterator)];
                    flagIterator ^= io.papermc.paper.util.IntegerUtil.getTrailingBit(flagIterator);
                    // Paper end
                    WrappedGoal wrappedGoal3 = this.lockedFlags.getOrDefault(flag, NO_GOAL);
                    wrappedGoal3.stop();
                    this.lockedFlags.put(flag, wrappedGoal2);
                }

                wrappedGoal2.start();
            }
        }

        profilerFiller.pop();
        this.tickRunningGoals(true);
    }

    public void tickRunningGoals(boolean tickAll) {
        ProfilerFiller profilerFiller = this.profiler.get();
        profilerFiller.push("goalTick");

        for(WrappedGoal wrappedGoal : this.availableGoals) {
            if (wrappedGoal.isRunning() && (tickAll || wrappedGoal.requiresUpdateEveryTick())) {
                wrappedGoal.tick();
            }
        }

        profilerFiller.pop();
    }

    public Set<WrappedGoal> getAvailableGoals() {
        return this.availableGoals;
    }

    public Stream<WrappedGoal> getRunningGoals() {
        return this.availableGoals.stream().filter(WrappedGoal::isRunning);
    }

    public void setNewGoalRate(int timeInterval) {
        this.newGoalRate = timeInterval;
    }

    public void disableControlFlag(Goal.Flag control) {
        this.goalTypes.addUnchecked(control); // Paper - remove streams from pathfindergoalselector
    }

    public void enableControlFlag(Goal.Flag control) {
        this.goalTypes.removeUnchecked(control); // Paper - remove streams from pathfindergoalselector
    }

    public void setControlFlag(Goal.Flag control, boolean enabled) {
        if (enabled) {
            this.enableControlFlag(control);
        } else {
            this.disableControlFlag(control);
        }

    }
}
