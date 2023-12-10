package net.minecraft.world.entity.ai.navigation;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class PathNavigation {
    private static final int MAX_TIME_RECOMPUTE = 20;
    private static final int STUCK_CHECK_INTERVAL = 100;
    private static final float STUCK_THRESHOLD_DISTANCE_FACTOR = 0.25F;
    protected final Mob mob;
    protected final Level level;
    @Nullable
    protected Path path;
    protected double speedModifier;
    protected int tick;
    protected int lastStuckCheck;
    protected Vec3 lastStuckCheckPos = Vec3.ZERO;
    protected Vec3i timeoutCachedNode = Vec3i.ZERO;
    protected long timeoutTimer;
    protected long lastTimeoutCheck;
    protected double timeoutLimit;
    protected float maxDistanceToWaypoint = 0.5F;
    protected boolean hasDelayedRecomputation;
    protected long timeLastRecompute;
    protected NodeEvaluator nodeEvaluator;
    @Nullable
    private BlockPos targetPos;
    private int reachRange;
    private float maxVisitedNodesMultiplier = 1.0F;
    public final PathFinder pathFinder;
    private boolean isStuck;

    public PathNavigation(Mob entity, Level world) {
        this.mob = entity;
        this.level = world;
        int i = Mth.floor(entity.getAttributeValue(Attributes.FOLLOW_RANGE) * 16.0D);
        this.pathFinder = this.createPathFinder(i);
    }

    public void resetMaxVisitedNodesMultiplier() {
        this.maxVisitedNodesMultiplier = 1.0F;
    }

    public void setMaxVisitedNodesMultiplier(float rangeMultiplier) {
        this.maxVisitedNodesMultiplier = rangeMultiplier;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    protected abstract PathFinder createPathFinder(int range);

    public void setSpeedModifier(double speed) {
        this.speedModifier = speed;
    }

    public void recomputePath() {
        if (this.tick - this.timeLastRecompute > 20L) { // Folia - region threading
            if (this.targetPos != null) {
                this.path = null;
                this.path = this.createPath(this.targetPos, this.reachRange);
                this.timeLastRecompute = this.tick; // Folia - region threading
                this.hasDelayedRecomputation = false;
            }
        } else {
            this.hasDelayedRecomputation = true;
        }

    }

    @Nullable
    public final Path createPath(double x, double y, double z, int distance) {
        return this.createPath(BlockPos.containing(x, y, z), distance);
    }

    @Nullable
    public Path createPath(Stream<BlockPos> positions, int distance) {
        return this.createPath(positions.collect(Collectors.toSet()), 8, false, distance);
    }

    @Nullable
    public Path createPath(Set<BlockPos> positions, int distance) {
        return this.createPath(positions, 8, false, distance);
    }

    @Nullable
    public Path createPath(BlockPos target, int distance) {
        // Paper start - add target entity parameter
        return this.createPath(target, null, distance);
    }
    @Nullable
    public Path createPath(BlockPos target, @Nullable Entity entity, int distance) {
        return this.createPath(ImmutableSet.of(target), entity, 8, false, distance);
        // Paper end
    }

    @Nullable
    public Path createPath(BlockPos target, int minDistance, int maxDistance) {
        return this.createPath(ImmutableSet.of(target), 8, false, minDistance, (float)maxDistance);
    }

    @Nullable
    public Path createPath(Entity entity, int distance) {
        return this.createPath(ImmutableSet.of(entity.blockPosition()), entity, 16, true, distance); // Paper
    }

    @Nullable
    protected Path createPath(Set<BlockPos> positions, int range, boolean useHeadPos, int distance) {
        return this.createPath(positions, range, useHeadPos, distance, (float)this.mob.getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    @Nullable
    protected Path createPath(Set<BlockPos> positions, int range, boolean useHeadPos, int distance, float followRange) {
        return this.createPath(positions, null, range, useHeadPos, distance, followRange);
    }

    @Nullable
    protected Path createPath(Set<BlockPos> positions, @Nullable Entity target, int range, boolean useHeadPos, int distance) {
        return this.createPath(positions, target, range, useHeadPos, distance, (float) this.mob.getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    @Nullable protected Path createPath(Set<BlockPos> positions, @Nullable Entity target, int range, boolean useHeadPos, int distance, float followRange) {
        // Paper end
        if (positions.isEmpty()) {
            return null;
        } else if (this.mob.getY() < (double)this.level.getMinBuildHeight()) {
            return null;
        } else if (!this.canUpdatePath()) {
            return null;
        } else if (this.path != null && !this.path.isDone() && positions.contains(this.targetPos)) {
            return this.path;
        } else {
            // Paper start - Pathfind event
            boolean copiedSet = false;
            for (BlockPos possibleTarget : positions) {
                if (!this.mob.getCommandSenderWorld().getWorldBorder().isWithinBounds(possibleTarget) || !new com.destroystokyo.paper.event.entity.EntityPathfindEvent(this.mob.getBukkitEntity(), // Paper - don't path out of world border
                    io.papermc.paper.util.MCUtil.toLocation(this.mob.level(), possibleTarget), target == null ? null : target.getBukkitEntity()).callEvent()) {
                    if (!copiedSet) {
                        copiedSet = true;
                        positions = new java.util.HashSet<>(positions);
                    }
                    // note: since we copy the set this remove call is safe, since we're iterating over the old copy
                    positions.remove(possibleTarget);
                    if (positions.isEmpty()) {
                        return null;
                    }
                }
            }
            // Paper end
            this.level.getProfiler().push("pathfind");
            BlockPos blockPos = useHeadPos ? this.mob.blockPosition().above() : this.mob.blockPosition();
            int i = (int)(followRange + (float)range);
            PathNavigationRegion pathNavigationRegion = new PathNavigationRegion(this.level, blockPos.offset(-i, -i, -i), blockPos.offset(i, i, i));
            Path path = this.pathFinder.findPath(pathNavigationRegion, this.mob, positions, followRange, distance, this.maxVisitedNodesMultiplier);
            this.level.getProfiler().pop();
            if (path != null && path.getTarget() != null) {
                this.targetPos = path.getTarget();
                this.reachRange = distance;
                this.resetStuckTimeout();
            }

            return path;
        }
    }

    public boolean moveTo(double x, double y, double z, double speed) {
        return this.moveTo(this.createPath(x, y, z, 1), speed);
    }

    // Paper start - optimise pathfinding
    private int lastFailure = 0;
    private int pathfindFailures = 0;
    // Paper end

    public boolean moveTo(Entity entity, double speed) {
        // Paper start - Pathfinding optimizations
        if (this.pathfindFailures > 10 && this.path == null && this.tick < this.lastFailure + 40) { // Folia - region threading
            return false;
        }
        // Paper end
        Path path = this.createPath(entity, 1);
        // Paper start - Pathfinding optimizations
        if (path != null && this.moveTo(path, speed)) {
            this.lastFailure = 0;
            this.pathfindFailures = 0;
            return true;
        } else {
            this.pathfindFailures++;
            this.lastFailure = this.tick; // Folia - region threading
            return false;
        }
        // Paper end
    }

    public boolean moveTo(@Nullable Path path, double speed) {
        if (path == null) {
            this.path = null;
            return false;
        } else {
            if (!path.sameAs(this.path)) {
                this.path = path;
            }

            if (this.isDone()) {
                return false;
            } else {
                this.trimPath();
                if (this.path.getNodeCount() <= 0) {
                    return false;
                } else {
                    this.speedModifier = speed;
                    Vec3 vec3 = this.getTempMobPos();
                    this.lastStuckCheck = this.tick;
                    this.lastStuckCheckPos = vec3;
                    return true;
                }
            }
        }
    }

    @Nullable
    public Path getPath() {
        return this.path;
    }

    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 vec3 = this.getTempMobPos();
                Vec3 vec32 = this.path.getNextEntityPos(this.mob);
                if (vec3.y > vec32.y && !this.mob.onGround() && Mth.floor(vec3.x) == Mth.floor(vec32.x) && Mth.floor(vec3.z) == Mth.floor(vec32.z)) {
                    this.path.advance();
                }
            }

            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);
            if (!this.isDone()) {
                Vec3 vec33 = this.path.getNextEntityPos(this.mob);
                this.mob.getMoveControl().setWantedPosition(vec33.x, this.getGroundY(vec33), vec33.z, this.speedModifier);
            }
        }
    }

    protected double getGroundY(Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        return this.level.getBlockState(blockPos.below()).isAir() ? pos.y : WalkNodeEvaluator.getFloorLevel(this.level, blockPos);
    }

    protected void followThePath() {
        Vec3 vec3 = this.getTempMobPos();
        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F ? this.mob.getBbWidth() / 2.0F : 0.75F - this.mob.getBbWidth() / 2.0F;
        Vec3i vec3i = this.path.getNextNodePos();
        double d = Math.abs(this.mob.getX() - ((double)vec3i.getX() + 0.5D));
        double e = Math.abs(this.mob.getY() - (double)vec3i.getY());
        double f = Math.abs(this.mob.getZ() - ((double)vec3i.getZ() + 0.5D));
        boolean bl = d < (double)this.maxDistanceToWaypoint && f < (double)this.maxDistanceToWaypoint && e < 1.0D;
        if (bl || this.canCutCorner(this.path.getNextNode().type) && this.shouldTargetNextNodeInDirection(vec3)) {
            this.path.advance();
        }

        this.doStuckDetection(vec3);
    }

    private boolean shouldTargetNextNodeInDirection(Vec3 currentPos) {
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            return false;
        } else {
            Vec3 vec3 = Vec3.atBottomCenterOf(this.path.getNextNodePos());
            if (!currentPos.closerThan(vec3, 2.0D)) {
                return false;
            } else if (this.canMoveDirectly(currentPos, this.path.getNextEntityPos(this.mob))) {
                return true;
            } else {
                Vec3 vec32 = Vec3.atBottomCenterOf(this.path.getNodePos(this.path.getNextNodeIndex() + 1));
                Vec3 vec33 = vec3.subtract(currentPos);
                Vec3 vec34 = vec32.subtract(currentPos);
                double d = vec33.lengthSqr();
                double e = vec34.lengthSqr();
                boolean bl = e < d;
                boolean bl2 = d < 0.5D;
                if (!bl && !bl2) {
                    return false;
                } else {
                    Vec3 vec35 = vec33.normalize();
                    Vec3 vec36 = vec34.normalize();
                    return vec36.dot(vec35) < 0.0D;
                }
            }
        }
    }

    protected void doStuckDetection(Vec3 currentPos) {
        if (this.tick - this.lastStuckCheck > 100) {
            float f = this.mob.getSpeed() >= 1.0F ? this.mob.getSpeed() : this.mob.getSpeed() * this.mob.getSpeed();
            float g = f * 100.0F * 0.25F;
            if (currentPos.distanceToSqr(this.lastStuckCheckPos) < (double)(g * g)) {
                this.isStuck = true;
                this.stop();
            } else {
                this.isStuck = false;
            }

            this.lastStuckCheck = this.tick;
            this.lastStuckCheckPos = currentPos;
        }

        if (this.path != null && !this.path.isDone()) {
            Vec3i vec3i = this.path.getNextNodePos();
            long l = this.level.getGameTime();
            if (vec3i.equals(this.timeoutCachedNode)) {
                this.timeoutTimer += l - this.lastTimeoutCheck;
            } else {
                this.timeoutCachedNode = vec3i;
                double d = currentPos.distanceTo(Vec3.atBottomCenterOf(this.timeoutCachedNode));
                this.timeoutLimit = this.mob.getSpeed() > 0.0F ? d / (double)this.mob.getSpeed() * 20.0D : 0.0D;
            }

            if (this.timeoutLimit > 0.0D && (double)this.timeoutTimer > this.timeoutLimit * 3.0D) {
                this.timeoutPath();
            }

            this.lastTimeoutCheck = l;
        }

    }

    private void timeoutPath() {
        this.resetStuckTimeout();
        this.stop();
    }

    private void resetStuckTimeout() {
        this.timeoutCachedNode = Vec3i.ZERO;
        this.timeoutTimer = 0L;
        this.timeoutLimit = 0.0D;
        this.isStuck = false;
    }

    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public boolean isInProgress() {
        return !this.isDone();
    }

    public void stop() {
        this.path = null;
    }

    protected abstract Vec3 getTempMobPos();

    protected abstract boolean canUpdatePath();

    protected void trimPath() {
        if (this.path != null) {
            for(int i = 0; i < this.path.getNodeCount(); ++i) {
                Node node = this.path.getNode(i);
                Node node2 = i + 1 < this.path.getNodeCount() ? this.path.getNode(i + 1) : null;
                BlockState blockState = this.level.getBlockState(new BlockPos(node.x, node.y, node.z));
                if (blockState.is(BlockTags.CAULDRONS)) {
                    this.path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z));
                    if (node2 != null && node.y >= node2.y) {
                        this.path.replaceNode(i + 1, node.cloneAndMove(node2.x, node.y + 1, node2.z));
                    }
                }
            }

        }
    }

    protected boolean canMoveDirectly(Vec3 origin, Vec3 target) {
        return false;
    }

    public boolean canCutCorner(BlockPathTypes nodeType) {
        return nodeType != BlockPathTypes.DANGER_FIRE && nodeType != BlockPathTypes.DANGER_OTHER && nodeType != BlockPathTypes.WALKABLE_DOOR;
    }

    protected static boolean isClearForMovementBetween(Mob entity, Vec3 startPos, Vec3 entityPos, boolean includeFluids) {
        Vec3 vec3 = new Vec3(entityPos.x, entityPos.y + (double)entity.getBbHeight() * 0.5D, entityPos.z);
        return entity.level().clip(new ClipContext(startPos, vec3, ClipContext.Block.COLLIDER, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
    }

    public boolean isStableDestination(BlockPos pos) {
        BlockPos blockPos = pos.below();
        return this.level.getBlockState(blockPos).isSolidRender(this.level, blockPos);
    }

    public NodeEvaluator getNodeEvaluator() {
        return this.nodeEvaluator;
    }

    public void setCanFloat(boolean canSwim) {
        this.nodeEvaluator.setCanFloat(canSwim);
    }

    public boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    public boolean shouldRecomputePath(BlockPos pos) {
        if (this.hasDelayedRecomputation) {
            return false;
        } else if (this.path != null && !this.path.isDone() && this.path.getNodeCount() != 0) {
            Node node = this.path.getEndNode();
            Vec3 vec3 = new Vec3(((double)node.x + this.mob.getX()) / 2.0D, ((double)node.y + this.mob.getY()) / 2.0D, ((double)node.z + this.mob.getZ()) / 2.0D);
            return pos.closerToCenterThan(vec3, (double)(this.path.getNodeCount() - this.path.getNextNodeIndex()));
        } else {
            return false;
        }
    }

    public float getMaxDistanceToWaypoint() {
        return this.maxDistanceToWaypoint;
    }

    public boolean isStuck() {
        return this.isStuck;
    }
}
