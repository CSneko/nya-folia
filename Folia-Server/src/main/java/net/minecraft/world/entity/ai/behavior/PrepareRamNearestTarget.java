package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class PrepareRamNearestTarget<E extends PathfinderMob> extends Behavior<E> {

    public static final int TIME_OUT_DURATION = 160;
    private final ToIntFunction<E> getCooldownOnFail;
    private final int minRamDistance;
    private final int maxRamDistance;
    private final float walkSpeed;
    private final TargetingConditions ramTargeting;
    private final int ramPrepareTime;
    private final Function<E, SoundEvent> getPrepareRamSound;
    private Optional<Long> reachedRamPositionTimestamp = Optional.empty();
    private Optional<PrepareRamNearestTarget.RamCandidate> ramCandidate = Optional.empty();

    public PrepareRamNearestTarget(ToIntFunction<E> cooldownFactory, int minDistance, int maxDistance, float speed, TargetingConditions targetPredicate, int prepareTime, Function<E, SoundEvent> soundFactory) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_ABSENT), 160);
        this.getCooldownOnFail = cooldownFactory;
        this.minRamDistance = minDistance;
        this.maxRamDistance = maxDistance;
        this.walkSpeed = speed;
        this.ramTargeting = targetPredicate;
        this.ramPrepareTime = prepareTime;
        this.getPrepareRamSound = soundFactory;
    }

    protected void start(ServerLevel worldserver, PathfinderMob entitycreature, long i) {
        Brain<?> behaviorcontroller = entitycreature.getBrain();

        behaviorcontroller.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).flatMap((nearestvisiblelivingentities) -> {
            return nearestvisiblelivingentities.findClosest((entityliving) -> {
                return this.ramTargeting.test(entitycreature, entityliving);
            });
        }).ifPresent((entityliving) -> {
            // CraftBukkit start
            EntityTargetEvent event = CraftEventFactory.callEntityTargetLivingEvent(entitycreature, entityliving, (entityliving instanceof ServerPlayer) ? EntityTargetEvent.TargetReason.CLOSEST_PLAYER : EntityTargetEvent.TargetReason.CLOSEST_ENTITY);
            if (event.isCancelled() || event.getTarget() == null) {
                return;
            }
            entityliving = ((CraftLivingEntity) event.getTarget()).getHandle();
            // CraftBukkit end
            this.chooseRamPosition(entitycreature, entityliving);
        });
    }

    protected void stop(ServerLevel world, E entity, long time) {
        Brain<?> behaviorcontroller = entity.getBrain();

        if (!behaviorcontroller.hasMemoryValue(MemoryModuleType.RAM_TARGET)) {
            world.broadcastEntityEvent(entity, (byte) 59);
            behaviorcontroller.setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, this.getCooldownOnFail.applyAsInt(entity)); // CraftBukkit - decompile error
        }

    }

    protected boolean canStillUse(ServerLevel worldserver, PathfinderMob entitycreature, long i) {
        return this.ramCandidate.isPresent() && ((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTarget().isAlive();
    }

    protected void tick(ServerLevel worldserver, E e0, long i) {
        if (!this.ramCandidate.isEmpty()) {
            e0.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getStartPosition(), this.walkSpeed, 0))); // CraftBukkit - decompile error
            e0.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new EntityTracker(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTarget(), true))); // CraftBukkit - decompile error
            boolean flag = !((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTarget().blockPosition().equals(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTargetPosition());

            if (flag) {
                worldserver.broadcastEntityEvent(e0, (byte) 59);
                e0.getNavigation().stop();
                this.chooseRamPosition(e0, ((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).target);
            } else {
                BlockPos blockposition = e0.blockPosition();

                if (blockposition.equals(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getStartPosition())) {
                    worldserver.broadcastEntityEvent(e0, (byte) 58);
                    if (this.reachedRamPositionTimestamp.isEmpty()) {
                        this.reachedRamPositionTimestamp = Optional.of(i);
                    }

                    if (i - (Long) this.reachedRamPositionTimestamp.get() >= (long) this.ramPrepareTime) {
                        e0.getBrain().setMemory(MemoryModuleType.RAM_TARGET, this.getEdgeOfBlock(blockposition, ((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTargetPosition())); // CraftBukkit - decompile error
                        worldserver.playSound((Player) null, (Entity) e0, (SoundEvent) this.getPrepareRamSound.apply(e0), SoundSource.NEUTRAL, 1.0F, e0.getVoicePitch());
                        this.ramCandidate = Optional.empty();
                    }
                }
            }

        }
    }

    private Vec3 getEdgeOfBlock(BlockPos start, BlockPos end) {
        double d0 = 0.5D;
        double d1 = 0.5D * (double) Mth.sign((double) (end.getX() - start.getX()));
        double d2 = 0.5D * (double) Mth.sign((double) (end.getZ() - start.getZ()));

        return Vec3.atBottomCenterOf(end).add(d1, 0.0D, d2);
    }

    private Optional<BlockPos> calculateRammingStartPosition(PathfinderMob entity, LivingEntity target) {
        BlockPos blockposition = target.blockPosition();

        if (!this.isWalkableBlock(entity, blockposition)) {
            return Optional.empty();
        } else {
            List<BlockPos> list = Lists.newArrayList();
            BlockPos.MutableBlockPos blockposition_mutableblockposition = blockposition.mutable();
            Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

            while (iterator.hasNext()) {
                Direction enumdirection = (Direction) iterator.next();

                blockposition_mutableblockposition.set(blockposition);
                int i = 0;

                while (true) {
                    if (i < this.maxRamDistance) {
                        if (this.isWalkableBlock(entity, blockposition_mutableblockposition.move(enumdirection))) {
                            ++i;
                            continue;
                        }

                        blockposition_mutableblockposition.move(enumdirection.getOpposite());
                    }

                    if (blockposition_mutableblockposition.distManhattan(blockposition) >= this.minRamDistance) {
                        list.add(blockposition_mutableblockposition.immutable());
                    }
                    break;
                }
            }

            PathNavigation navigationabstract = entity.getNavigation();
            Stream<BlockPos> stream = list.stream(); // CraftBukkit - decompile error
            BlockPos blockposition1 = entity.blockPosition();

            Objects.requireNonNull(blockposition1);
            return stream.sorted(Comparator.comparingDouble(blockposition1::distSqr)).filter((blockposition2) -> {
                Path pathentity = navigationabstract.createPath(blockposition2, 0);

                return pathentity != null && pathentity.canReach();
            }).findFirst();
        }
    }

    private boolean isWalkableBlock(PathfinderMob entity, BlockPos target) {
        return entity.getNavigation().isStableDestination(target) && entity.getPathfindingMalus(WalkNodeEvaluator.getBlockPathTypeStatic(entity.level(), target.mutable())) == 0.0F;
    }

    private void chooseRamPosition(PathfinderMob entity, LivingEntity target) {
        this.reachedRamPositionTimestamp = Optional.empty();
        this.ramCandidate = this.calculateRammingStartPosition(entity, target).map((blockposition) -> {
            return new PrepareRamNearestTarget.RamCandidate(blockposition, target.blockPosition(), target);
        });
    }

    public static class RamCandidate {

        private final BlockPos startPosition;
        private final BlockPos targetPosition;
        final LivingEntity target;

        public RamCandidate(BlockPos start, BlockPos end, LivingEntity entity) {
            this.startPosition = start;
            this.targetPosition = end;
            this.target = entity;
        }

        public BlockPos getStartPosition() {
            return this.startPosition;
        }

        public BlockPos getTargetPosition() {
            return this.targetPosition;
        }

        public LivingEntity getTarget() {
            return this.target;
        }
    }
}
