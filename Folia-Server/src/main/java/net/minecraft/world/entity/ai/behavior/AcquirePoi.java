package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableLong;

public class AcquirePoi {
    public static final int SCAN_RANGE = 48;

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiPredicate, MemoryModuleType<GlobalPos> poiPosModule, boolean onlyRunIfChild, Optional<Byte> entityStatus) {
        return create(poiPredicate, poiPosModule, poiPosModule, onlyRunIfChild, entityStatus);
    }

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiPredicate, MemoryModuleType<GlobalPos> poiPosModule, MemoryModuleType<GlobalPos> potentialPoiPosModule, boolean onlyRunIfChild, Optional<Byte> entityStatus) {
        int i = 5;
        int j = 20;
        MutableLong mutableLong = new MutableLong(0L);
        Long2ObjectMap<AcquirePoi.JitteredLinearRetry> long2ObjectMap = new Long2ObjectOpenHashMap<>();
        OneShot<PathfinderMob> oneShot = BehaviorBuilder.create((instance) -> {
            return instance.group(instance.absent(potentialPoiPosModule)).apply(instance, (queryResult) -> {
                return (world, entity, time) -> {
                    if (onlyRunIfChild && entity.isBaby()) {
                        return false;
                    } else if (mutableLong.getValue() == 0L) {
                        mutableLong.setValue(world.getGameTime() + (long)world.random.nextInt(20));
                        return false;
                    } else if (world.getGameTime() < mutableLong.getValue()) {
                        return false;
                    } else {
                        mutableLong.setValue(time + 20L + (long)world.getRandom().nextInt(20));
                        if (entity.getNavigation().isStuck()) mutableLong.add(200); // Paper - Wait an additional 10s to check again if they're stuck
                        PoiManager poiManager = world.getPoiManager();
                        long2ObjectMap.long2ObjectEntrySet().removeIf((entry) -> {
                            return !entry.getValue().isStillValid(time);
                        });
                        Predicate<BlockPos> predicate2 = (pos) -> {
                            AcquirePoi.JitteredLinearRetry jitteredLinearRetry = long2ObjectMap.get(pos.asLong());
                            if (jitteredLinearRetry == null) {
                                return true;
                            } else if (!jitteredLinearRetry.shouldRetry(time)) {
                                return false;
                            } else {
                                jitteredLinearRetry.markAttempt(time);
                                return true;
                            }
                        };
                        // Paper start - optimise POI access
                        java.util.List<Pair<Holder<PoiType>, BlockPos>> poiposes = new java.util.ArrayList<>();
                        io.papermc.paper.util.PoiAccess.findNearestPoiPositions(poiManager, poiPredicate, predicate2, entity.blockPosition(), 48, 48*48, PoiManager.Occupancy.HAS_SPACE, false, 5, poiposes);
                        Set<Pair<Holder<PoiType>, BlockPos>> set = new java.util.HashSet<>(poiposes);
                        // Paper end - optimise POI access
                        Path path = findPathToPois(entity, set);
                        if (path != null && path.canReach()) {
                            BlockPos blockPos = path.getTarget();
                            poiManager.getType(blockPos).ifPresent((poiType) -> {
                                poiManager.take(poiPredicate, (holder, blockPos2) -> {
                                    return blockPos2.equals(blockPos);
                                }, blockPos, 1);
                                queryResult.set(GlobalPos.of(world.dimension(), blockPos));
                                entityStatus.ifPresent((status) -> {
                                    world.broadcastEntityEvent(entity, status);
                                });
                                long2ObjectMap.clear();
                                DebugPackets.sendPoiTicketCountPacket(world, blockPos);
                            });
                        } else {
                            for(Pair<Holder<PoiType>, BlockPos> pair : set) {
                                long2ObjectMap.computeIfAbsent(pair.getSecond().asLong(), (m) -> {
                                    return new AcquirePoi.JitteredLinearRetry(world.random, time);
                                });
                            }
                        }

                        return true;
                    }
                };
            });
        });
        return potentialPoiPosModule == poiPosModule ? oneShot : BehaviorBuilder.create((context) -> {
            return context.group(context.absent(poiPosModule)).apply(context, (poiPos) -> {
                return oneShot;
            });
        });
    }

    @Nullable
    public static Path findPathToPois(Mob entity, Set<Pair<Holder<PoiType>, BlockPos>> pois) {
        if (pois.isEmpty()) {
            return null;
        } else {
            Set<BlockPos> set = new HashSet<>();
            int i = 1;

            for(Pair<Holder<PoiType>, BlockPos> pair : pois) {
                i = Math.max(i, pair.getFirst().value().validRange());
                set.add(pair.getSecond());
            }

            return entity.getNavigation().createPath(set, i);
        }
    }

    static class JitteredLinearRetry {
        private static final int MIN_INTERVAL_INCREASE = 40;
        private static final int MAX_INTERVAL_INCREASE = 80;
        private static final int MAX_RETRY_PATHFINDING_INTERVAL = 400;
        private final RandomSource random;
        private long previousAttemptTimestamp;
        private long nextScheduledAttemptTimestamp;
        private int currentDelay;

        JitteredLinearRetry(RandomSource random, long time) {
            this.random = random;
            this.markAttempt(time);
        }

        public void markAttempt(long time) {
            this.previousAttemptTimestamp = time;
            int i = this.currentDelay + this.random.nextInt(40) + 40;
            this.currentDelay = Math.min(i, 400);
            this.nextScheduledAttemptTimestamp = time + (long)this.currentDelay;
        }

        public boolean isStillValid(long time) {
            return time - this.previousAttemptTimestamp < 400L;
        }

        public boolean shouldRetry(long time) {
            return time >= this.nextScheduledAttemptTimestamp;
        }

        @Override
        public String toString() {
            return "RetryMarker{, previousAttemptAt=" + this.previousAttemptTimestamp + ", nextScheduledAttemptAt=" + this.nextScheduledAttemptTimestamp + ", currentDelay=" + this.currentDelay + "}";
        }
    }
}
