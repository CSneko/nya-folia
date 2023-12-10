package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public abstract class Behavior<E extends LivingEntity> implements BehaviorControl<E> {
    public static final int DEFAULT_DURATION = 60;
    protected final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private Behavior.Status status = Behavior.Status.STOPPED;
    private long endTimestamp;
    private final int minDuration;
    private final int maxDuration;
    // Paper start - configurable behavior tick rate and timings
    private final String configKey;
    private final co.aikar.timings.Timing timing;
    // Paper end

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryState) {
        this(requiredMemoryState, 60);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryState, int runTime) {
        this(requiredMemoryState, runTime, runTime);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryState, int minRunTime, int maxRunTime) {
        this.minDuration = minRunTime;
        this.maxDuration = maxRunTime;
        this.entryCondition = requiredMemoryState;
        // Paper start - configurable behavior tick rate and timings
        String key = io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(this.getClass().getName());
        int lastSeparator = key.lastIndexOf('.');
        if (lastSeparator != -1) {
            key = key.substring(lastSeparator + 1);
        }
        this.configKey = key.toLowerCase(java.util.Locale.ROOT);
        this.timing = co.aikar.timings.MinecraftTimings.getBehaviorTimings(configKey);
        // Paper end
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel world, E entity, long time) {
        // Paper start - behavior tick rate
        int tickRate = java.util.Objects.requireNonNullElse(world.paperConfig().tickRates.behavior.get(entity.getType(), this.configKey), -1);
        if (tickRate > -1 && time < this.endTimestamp + tickRate) {
            return false;
        }
        // Paper end
        if (this.hasRequiredMemories(entity) && this.checkExtraStartConditions(world, entity)) {
            this.status = Behavior.Status.RUNNING;
            int i = this.minDuration + world.getRandom().nextInt(this.maxDuration + 1 - this.minDuration);
            this.endTimestamp = time + (long)i;
            this.timing.startTiming(); // Paper - behavior timings
            this.start(world, entity, time);
            this.timing.stopTiming(); // Paper - behavior timings
            return true;
        } else {
            return false;
        }
    }

    protected void start(ServerLevel world, E entity, long time) {
    }

    @Override
    public final void tickOrStop(ServerLevel world, E entity, long time) {
        this.timing.startTiming(); // Paper - behavior timings
        if (!this.timedOut(time) && this.canStillUse(world, entity, time)) {
            this.tick(world, entity, time);
        } else {
            this.doStop(world, entity, time);
        }
        this.timing.stopTiming(); // Paper - behavior timings

    }

    protected void tick(ServerLevel world, E entity, long time) {
    }

    @Override
    public final void doStop(ServerLevel world, E entity, long time) {
        this.status = Behavior.Status.STOPPED;
        this.stop(world, entity, time);
    }

    protected void stop(ServerLevel world, E entity, long time) {
    }

    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return false;
    }

    protected boolean timedOut(long time) {
        return time > this.endTimestamp;
    }

    protected boolean checkExtraStartConditions(ServerLevel world, E entity) {
        return true;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    protected boolean hasRequiredMemories(E entity) {
        for(Map.Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            MemoryStatus memoryStatus = entry.getValue();
            if (!entity.getBrain().checkMemory(memoryModuleType, memoryStatus)) {
                return false;
            }
        }

        return true;
    }

    public static enum Status {
        STOPPED,
        RUNNING;
    }
}
