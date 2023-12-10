package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class VoxelShape {
    public final DiscreteVoxelShape shape; // Paper - optimise collisions - public
    @Nullable
    private VoxelShape[] faces;

    // Paper start - optimise collisions
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private AABB singleAABBRepresentation;
    private double[] rootCoordinatesX;
    private double[] rootCoordinatesY;
    private double[] rootCoordinatesZ;

    private io.papermc.paper.util.collisions.CachedShapeData cachedShapeData;
    private boolean isEmpty;

    private io.papermc.paper.util.collisions.CachedToAABBs cachedToAABBs;
    private AABB cachedBounds;

    private Boolean isFullBlock;

    private Boolean occludesFullBlock;

    // must be power of two
    private static final int MERGED_CACHE_SIZE = 16;

    private io.papermc.paper.util.collisions.MergedORCache[] mergedORCache;

    public final double offsetX() {
        return this.offsetX;
    }

    public final double offsetY() {
        return this.offsetY;
    }

    public final double offsetZ() {
        return this.offsetZ;
    }

    public final AABB getSingleAABBRepresentation() {
        return this.singleAABBRepresentation;
    }

    public final double[] rootCoordinatesX() {
        return this.rootCoordinatesX;
    }

    public final double[] rootCoordinatesY() {
        return this.rootCoordinatesY;
    }

    public final double[] rootCoordinatesZ() {
        return this.rootCoordinatesZ;
    }

    private static double[] extractRawArray(final DoubleList list) {
        if (list instanceof it.unimi.dsi.fastutil.doubles.DoubleArrayList rawList) {
            final double[] raw = rawList.elements();
            final int expected = rawList.size();
            if (raw.length == expected) {
                return raw;
            } else {
                return java.util.Arrays.copyOf(raw, expected);
            }
        } else {
            return list.toDoubleArray();
        }
    }

    public final void initCache() {
        this.cachedShapeData = this.shape.getOrCreateCachedShapeData();
        this.isEmpty = this.cachedShapeData.isEmpty();

        final DoubleList xList = this.getCoords(Direction.Axis.X);
        final DoubleList yList = this.getCoords(Direction.Axis.Y);
        final DoubleList zList = this.getCoords(Direction.Axis.Z);

        if (xList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetX = offsetDoubleList.offset;
            this.rootCoordinatesX = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesX = extractRawArray(xList);
        }

        if (yList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetY = offsetDoubleList.offset;
            this.rootCoordinatesY = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesY = extractRawArray(yList);
        }

        if (zList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetZ = offsetDoubleList.offset;
            this.rootCoordinatesZ = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesZ = extractRawArray(zList);
        }

        if (this.cachedShapeData.hasSingleAABB()) {
            this.singleAABBRepresentation = new AABB(
                    this.rootCoordinatesX[0] + this.offsetX, this.rootCoordinatesY[0] + this.offsetY, this.rootCoordinatesZ[0] + this.offsetZ,
                    this.rootCoordinatesX[1] + this.offsetX, this.rootCoordinatesY[1] + this.offsetY, this.rootCoordinatesZ[1] + this.offsetZ
            );
            this.cachedBounds = this.singleAABBRepresentation;
        }
    }

    public final io.papermc.paper.util.collisions.CachedShapeData getCachedVoxelData() {
        return this.cachedShapeData;
    }

    private VoxelShape[] faceShapeClampedCache;

    public final VoxelShape getFaceShapeClamped(final Direction direction) {
        if (this.isEmpty) {
            return (VoxelShape)(Object)this;
        }
        if ((VoxelShape)(Object)this == Shapes.block()) {
            return (VoxelShape)(Object)this;
        }

        VoxelShape[] cache = this.faceShapeClampedCache;
        if (cache != null) {
            final VoxelShape ret = cache[direction.ordinal()];
            if (ret != null) {
                return ret;
            }
        }


        if (cache == null) {
            this.faceShapeClampedCache = cache = new VoxelShape[6];
        }

        final Direction.Axis axis = direction.getAxis();

        final VoxelShape ret;

        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            if (DoubleMath.fuzzyEquals(this.max(axis), 1.0, io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)) {
                ret = tryForceBlock(new SliceShape((VoxelShape)(Object)this, axis, this.shape.getSize(axis) - 1));
            } else {
                ret = Shapes.empty();
            }
        } else {
            if (DoubleMath.fuzzyEquals(this.min(axis), 0.0, io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)) {
                ret = tryForceBlock(new SliceShape((VoxelShape)(Object)this, axis, 0));
            } else {
                ret = Shapes.empty();
            }
        }

        cache[direction.ordinal()] = ret;

        return ret;
    }

    private static VoxelShape tryForceBlock(final VoxelShape other) {
        if (other == Shapes.block()) {
            return other;
        }

        final AABB otherAABB = other.getSingleAABBRepresentation();
        if (otherAABB == null) {
            return other;
        }

        if (Shapes.block().getSingleAABBRepresentation().equals(otherAABB)) {
            return Shapes.block();
        }

        return other;
    }

    private boolean computeOccludesFullBlock() {
        if (this.isEmpty) {
            this.occludesFullBlock = Boolean.FALSE;
            return false;
        }

        if (this.isFullBlock()) {
            this.occludesFullBlock = Boolean.TRUE;
            return true;
        }

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            // check if the bounding box encloses the full cube
            final boolean ret =
                (singleAABB.minY <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON && singleAABB.maxY >= (1 - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)) &&
                (singleAABB.minX <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON && singleAABB.maxX >= (1 - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)) &&
                (singleAABB.minZ <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON && singleAABB.maxZ >= (1 - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON));
            this.occludesFullBlock = Boolean.valueOf(ret);
            return ret;
        }

        final boolean ret = !Shapes.joinIsNotEmpty(Shapes.block(), ((VoxelShape)(Object)this), BooleanOp.ONLY_FIRST);
        this.occludesFullBlock = Boolean.valueOf(ret);
        return ret;
    }

    public final boolean occludesFullBlock() {
        final Boolean ret = this.occludesFullBlock;
        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeOccludesFullBlock();
    }

    public final boolean occludesFullBlockIfCached() {
        final Boolean ret = this.occludesFullBlock;
        return ret != null ? ret.booleanValue() : false;
    }

    private static int hash(final VoxelShape key) {
        return it.unimi.dsi.fastutil.HashCommon.mix(System.identityHashCode(key));
    }

    public final VoxelShape orUnoptimized(final VoxelShape other) {
        // don't cache simple cases
        if (((VoxelShape)(Object)this) == other) {
            return other;
        }

        if (this.isEmpty) {
            return other;
        }

        if (other.isEmpty()) {
            return (VoxelShape)(Object)this;
        }

        // try this cache first
        final int thisCacheKey = hash(other) & (MERGED_CACHE_SIZE - 1);
        final io.papermc.paper.util.collisions.MergedORCache cached = this.mergedORCache == null ? null : this.mergedORCache[thisCacheKey];
        if (cached != null && cached.key() == other) {
            return cached.result();
        }

        // try other cache
        final int otherCacheKey = hash(this) & (MERGED_CACHE_SIZE - 1);
        final io.papermc.paper.util.collisions.MergedORCache otherCache = other.mergedORCache == null ? null : other.mergedORCache[otherCacheKey];
        if (otherCache != null && otherCache.key() == this) {
            return otherCache.result();
        }

        // note: unsure if joinUnoptimized(1, 2, OR) == joinUnoptimized(2, 1, OR) for all cases
        final VoxelShape result = Shapes.joinUnoptimized(this, other, BooleanOp.OR);

        if (cached != null && otherCache == null) {
            // try to use second cache instead of replacing an entry in this cache
            if (other.mergedORCache == null) {
                other.mergedORCache = new io.papermc.paper.util.collisions.MergedORCache[MERGED_CACHE_SIZE];
            }
            other.mergedORCache[otherCacheKey] = new io.papermc.paper.util.collisions.MergedORCache(this, result);
        } else {
            // line is not occupied or other cache line is full
            // always bias to replace this cache, as this cache is the first we check
            if (this.mergedORCache == null) {
                this.mergedORCache = new io.papermc.paper.util.collisions.MergedORCache[MERGED_CACHE_SIZE];
            }
            this.mergedORCache[thisCacheKey] = new io.papermc.paper.util.collisions.MergedORCache(other, result);
        }

        return result;
    }

    private boolean computeFullBlock() {
        Boolean ret;
        if (this.isEmpty) {
            ret = Boolean.FALSE;
        } else if ((VoxelShape)(Object)this == Shapes.block()) {
            ret = Boolean.TRUE;
        } else {
            final AABB singleAABB = this.singleAABBRepresentation;
            if (singleAABB == null) {
                final io.papermc.paper.util.collisions.CachedShapeData shapeData = this.cachedShapeData;
                final int sMinX = shapeData.minFullX();
                final int sMinY = shapeData.minFullY();
                final int sMinZ = shapeData.minFullZ();

                final int sMaxX = shapeData.maxFullX();
                final int sMaxY = shapeData.maxFullY();
                final int sMaxZ = shapeData.maxFullZ();

                if (Math.abs(this.rootCoordinatesX[sMinX] + this.offsetX) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesY[sMinY] + this.offsetY) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesZ[sMinZ] + this.offsetZ) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&

                    Math.abs(1.0 - (this.rootCoordinatesX[sMaxX] + this.offsetX)) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesY[sMaxY] + this.offsetY)) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesZ[sMaxZ] + this.offsetZ)) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) {

                    // index = z + y*sizeZ + x*(sizeZ*sizeY)

                    final int sizeY = shapeData.sizeY();
                    final int sizeZ = shapeData.sizeZ();

                    final long[] bitset = shapeData.voxelSet();

                    ret = Boolean.TRUE;

                    check_full:
                    for (int x = sMinX; x < sMaxX; ++x) {
                        for (int y = sMinY; y < sMaxY; ++y) {
                            final int baseIndex = y*sizeZ + x*(sizeZ*sizeY);
                            if (!io.papermc.paper.util.collisions.FlatBitsetUtil.isRangeSet(bitset, baseIndex + sMinZ, baseIndex + sMaxZ)) {
                                ret = Boolean.FALSE;
                                break check_full;
                            }
                        }
                    }
                } else {
                    ret = Boolean.FALSE;
                }
            } else {
                ret = Boolean.valueOf(
                        Math.abs(singleAABB.minX) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(singleAABB.minY) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(singleAABB.minZ) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&

                           Math.abs(1.0 - singleAABB.maxX) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(1.0 - singleAABB.maxY) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON &&
                           Math.abs(1.0 - singleAABB.maxZ) <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON
                );
            }
        }

        this.isFullBlock = ret;

        return ret.booleanValue();
    }

    public boolean isFullBlock() {
        final Boolean ret = this.isFullBlock;

        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeFullBlock();
    }
    // Paper end - optimise collisions

    protected VoxelShape(DiscreteVoxelShape voxels) { // Paper - protected
        this.shape = voxels;
    }

    public double min(Direction.Axis axis) {
        // Paper start - optimise collisions
        final io.papermc.paper.util.collisions.CachedShapeData shapeData = this.cachedShapeData;
        switch (axis) {
            case X: {
                final int idx = shapeData.minFullX();
                return idx >= shapeData.sizeX() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.minFullY();
                return idx >= shapeData.sizeY() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.minFullZ();
                return idx >= shapeData.sizeZ() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.POSITIVE_INFINITY;
            }
        }
        // Paper end - optimise collisions
    }

    public double max(Direction.Axis axis) {
        // Paper start - optimise collisions
        final io.papermc.paper.util.collisions.CachedShapeData shapeData = this.cachedShapeData;
        switch (axis) {
            case X: {
                final int idx = shapeData.maxFullX();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.maxFullY();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.maxFullZ();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.NEGATIVE_INFINITY;
            }
        }
        // Paper end - optimise collisions
    }

    public AABB bounds() {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            throw Util.pauseInIde(new UnsupportedOperationException("No bounds for empty shape."));
        }
        AABB cached = this.cachedBounds;
        if (cached != null) {
            return cached;
        }

        final io.papermc.paper.util.collisions.CachedShapeData shapeData = this.cachedShapeData;

        final double[] coordsX = this.rootCoordinatesX;
        final double[] coordsY = this.rootCoordinatesY;
        final double[] coordsZ = this.rootCoordinatesZ;

        final double offX = this.offsetX;
        final double offY = this.offsetY;
        final double offZ = this.offsetZ;

        // note: if not empty, then there is one full AABB so no bounds checks are needed on the minFull/maxFull indices
        cached = new AABB(
                coordsX[shapeData.minFullX()] + offX,
                coordsY[shapeData.minFullY()] + offY,
                coordsZ[shapeData.minFullZ()] + offZ,

                coordsX[shapeData.maxFullX()] + offX,
                coordsY[shapeData.maxFullY()] + offY,
                coordsZ[shapeData.maxFullZ()] + offZ
        );

        this.cachedBounds = cached;
        return cached;
        // Paper end - optimise collisions
    }

    public VoxelShape singleEncompassing() {
        return this.isEmpty() ? Shapes.empty() : Shapes.box(this.min(Direction.Axis.X), this.min(Direction.Axis.Y), this.min(Direction.Axis.Z), this.max(Direction.Axis.X), this.max(Direction.Axis.Y), this.max(Direction.Axis.Z));
    }

    protected double get(Direction.Axis axis, int index) {
        return this.getCoords(axis).getDouble(index);
    }

    protected abstract DoubleList getCoords(Direction.Axis axis);

    public boolean isEmpty() {
        return this.isEmpty; // Paper - optimise collisions
    }

    // Paper start - optimise collisions
    private static DoubleList offsetList(final DoubleList src, final double by) {
        if (src instanceof OffsetDoubleList offsetDoubleList) {
            return new OffsetDoubleList(offsetDoubleList.delegate, by + offsetDoubleList.offset);
        }
        return new OffsetDoubleList(src, by);
    }
    // Paper end - optimise collisions

    public VoxelShape move(double x, double y, double z) {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return Shapes.empty();
        }

        final ArrayVoxelShape ret = new ArrayVoxelShape(
                this.shape,
                offsetList(this.getCoords(Direction.Axis.X), x),
                offsetList(this.getCoords(Direction.Axis.Y), y),
                offsetList(this.getCoords(Direction.Axis.Z), z)
        );

        final io.papermc.paper.util.collisions.CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            ((VoxelShape)ret).cachedToAABBs = io.papermc.paper.util.collisions.CachedToAABBs.offset(cachedToAABBs, x, y, z);
        }

        return ret;
        // Paper end - optimise collisions
    }

    public VoxelShape optimize() {
        // Paper start - optimise collisions
        // Optimise merge strategy to increase the number of simple joins, and additionally forward the toAabbs cache
        // to result
        if (this.isEmpty) {
            return Shapes.empty();
        }

        if (this.singleAABBRepresentation != null) {
            // note: the isFullBlock() is fuzzy, and Shapes.create() is also fuzzy which would return block()
            return this.isFullBlock() ? Shapes.block() : this;
        }

        final List<AABB> aabbs = this.toAabbs();

        if (aabbs.size() == 1) {
            final AABB singleAABB = aabbs.get(0);
            final VoxelShape ret = Shapes.create(singleAABB);

            // forward AABB cache
            if (ret.cachedToAABBs == null) {
                ret.cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        } else {
            // reduce complexity of joins by splitting the merges (old complexity: n^2, new: nlogn)

            // set up flat array so that this merge is done in-place
            final VoxelShape[] tmp = new VoxelShape[aabbs.size()];

            // initialise as unmerged
            for (int i = 0, len = aabbs.size(); i < len; ++i) {
                tmp[i] = Shapes.create(aabbs.get(i));
            }

            int size = aabbs.size();
            while (size > 1) {
                int newSize = 0;
                for (int i = 0; i < size; i += 2) {
                    final int next = i + 1;
                    if (next >= size) {
                        // nothing to merge with, so leave it for next iteration
                        tmp[newSize++] = tmp[i];
                        break;
                    } else {
                        // merge with adjacent
                        final VoxelShape first = tmp[i];
                        final VoxelShape second = tmp[next];

                        tmp[newSize++] = Shapes.joinUnoptimized(first, second, BooleanOp.OR);
                    }
                }
                size = newSize;
            }

            final VoxelShape ret = tmp[0];

            // forward AABB cache
            if (ret.cachedToAABBs == null) {
                ret.cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        }
        // Paper end - optimise collisions
    }

    public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        this.shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            consumer.consume(this.get(Direction.Axis.X, minX), this.get(Direction.Axis.Y, minY), this.get(Direction.Axis.Z, minZ), this.get(Direction.Axis.X, maxX), this.get(Direction.Axis.Y, maxY), this.get(Direction.Axis.Z, maxZ));
        }, true);
    }

    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        DoubleList doubleList = this.getCoords(Direction.Axis.X);
        DoubleList doubleList2 = this.getCoords(Direction.Axis.Y);
        DoubleList doubleList3 = this.getCoords(Direction.Axis.Z);
        this.shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            consumer.consume(doubleList.getDouble(minX), doubleList2.getDouble(minY), doubleList3.getDouble(minZ), doubleList.getDouble(maxX), doubleList2.getDouble(maxY), doubleList3.getDouble(maxZ));
        }, true);
    }

    // Paper start - optimise collisions
    private List<AABB> toAabbsUncached() {
        final List<AABB> ret = new java.util.ArrayList<>();
        if (this.singleAABBRepresentation != null) {
            ret.add(this.singleAABBRepresentation);
        } else {
            this.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                ret.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
            });
        }

        // cache result
        this.cachedToAABBs = new io.papermc.paper.util.collisions.CachedToAABBs(ret, false, 0.0, 0.0, 0.0);

        return ret;
    }
    // Paper end - optimise collisions

    public List<AABB> toAabbs() {
        // Paper start - optimise collisions
        io.papermc.paper.util.collisions.CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            if (!cachedToAABBs.isOffset()) {
                return cachedToAABBs.aabbs();
            }

            // all we need to do is offset the cache
            cachedToAABBs = cachedToAABBs.removeOffset();
            // update cache
            this.cachedToAABBs = cachedToAABBs;

            return cachedToAABBs.aabbs();
        }

        // make new cache
        return this.toAabbsUncached();
        // Paper end - optimise collisions
    }

    public double min(Direction.Axis axis, double from, double to) {
        Direction.Axis axis2 = AxisCycle.FORWARD.cycle(axis);
        Direction.Axis axis3 = AxisCycle.BACKWARD.cycle(axis);
        int i = this.findIndex(axis2, from);
        int j = this.findIndex(axis3, to);
        int k = this.shape.firstFull(axis, i, j);
        return k >= this.shape.getSize(axis) ? Double.POSITIVE_INFINITY : this.get(axis, k);
    }

    public double max(Direction.Axis axis, double from, double to) {
        Direction.Axis axis2 = AxisCycle.FORWARD.cycle(axis);
        Direction.Axis axis3 = AxisCycle.BACKWARD.cycle(axis);
        int i = this.findIndex(axis2, from);
        int j = this.findIndex(axis3, to);
        int k = this.shape.lastFull(axis, i, j);
        return k <= 0 ? Double.NEGATIVE_INFINITY : this.get(axis, k);
    }

    protected int findIndex(Direction.Axis axis, double coord) {
        return Mth.binarySearch(0, this.shape.getSize(axis) + 1, (i) -> {
            return coord < this.get(axis, i);
        }) - 1;
    }

    // Paper start - optimise collisions
    /**
     * Copy of AABB#clip but for one AABB
     */
    private static BlockHitResult clip(final AABB aabb, final Vec3 from, final Vec3 to, final BlockPos offset) {
        final double[] minDistanceArr = new double[] { 1.0 };
        final double diffX = to.x - from.x;
        final double diffY = to.y - from.y;
        final double diffZ = to.z - from.z;

        final Direction direction = AABB.getDirection(aabb.move(offset), from, minDistanceArr, null, diffX, diffY, diffZ);

        if (direction == null) {
            return null;
        }

        final double minDistance = minDistanceArr[0];
        return new BlockHitResult(from.add(minDistance * diffX, minDistance * diffY, minDistance * diffZ), direction, offset, false);
    }
    // Paper end - optimise collisions

    @Nullable
    public BlockHitResult clip(Vec3 start, Vec3 end, BlockPos pos) {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return null;
        }

        final Vec3 directionOpposite = end.subtract(start);
        if (directionOpposite.lengthSqr() < io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) {
            return null;
        }

        final Vec3 fromBehind = start.add(directionOpposite.scale(0.001));
        final double fromBehindOffsetX = fromBehind.x - (double)pos.getX();
        final double fromBehindOffsetY = fromBehind.y - (double)pos.getY();
        final double fromBehindOffsetZ = fromBehind.z - (double)pos.getZ();

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            if (singleAABB.contains(fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
                return new BlockHitResult(fromBehind, Direction.getNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), pos, true);
            }
            return clip(singleAABB, start, end, pos);
        }

        if (io.papermc.paper.util.CollisionUtil.strictlyContains(this, fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
            return new BlockHitResult(fromBehind, Direction.getNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), pos, true);
        }

        return AABB.clip(this.toAabbs(), start, end, pos);
        // Paper end - optimise collisions
    }

    public Optional<Vec3> closestPointTo(Vec3 target) {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return Optional.empty();
        }

        Vec3 ret = null;
        double retDistance = Double.MAX_VALUE;

        final List<AABB> aabbs = this.toAabbs();
        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final AABB aabb = aabbs.get(i);
            final double x = Mth.clamp(target.x, aabb.minX, aabb.maxX);
            final double y = Mth.clamp(target.y, aabb.minY, aabb.maxY);
            final double z = Mth.clamp(target.z, aabb.minZ, aabb.maxZ);

            double dist = target.distanceToSqr(x, y, z);
            if (dist < retDistance) {
                ret = new Vec3(x, y, z);
                retDistance = dist;
            }
        }

        return Optional.ofNullable(ret);
        // Paper end - optimise collisions
    }

    public VoxelShape getFaceShape(Direction facing) {
        if (!this.isEmpty() && this != Shapes.block()) {
            if (this.faces != null) {
                VoxelShape voxelShape = this.faces[facing.ordinal()];
                if (voxelShape != null) {
                    return voxelShape;
                }
            } else {
                this.faces = new VoxelShape[6];
            }

            VoxelShape voxelShape2 = this.calculateFace(facing);
            this.faces[facing.ordinal()] = voxelShape2;
            return voxelShape2;
        } else {
            return this;
        }
    }

    private VoxelShape calculateFace(Direction direction) {
        Direction.Axis axis = direction.getAxis();
        DoubleList doubleList = this.getCoords(axis);
        if (doubleList.size() == 2 && DoubleMath.fuzzyEquals(doubleList.getDouble(0), 0.0D, 1.0E-7D) && DoubleMath.fuzzyEquals(doubleList.getDouble(1), 1.0D, 1.0E-7D)) {
            return this;
        } else {
            Direction.AxisDirection axisDirection = direction.getAxisDirection();
            int i = this.findIndex(axis, axisDirection == Direction.AxisDirection.POSITIVE ? 0.9999999D : 1.0E-7D);
            return new SliceShape(this, axis, i);
        }
    }

    public double collide(Direction.Axis axis, AABB box, double maxDist) {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return maxDist;
        }
        if (Math.abs(maxDist) < io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON) {
            return 0.0;
        }
        switch (axis) {
            case X: {
                return io.papermc.paper.util.CollisionUtil.collideX(this, box, maxDist);
            }
            case Y: {
                return io.papermc.paper.util.CollisionUtil.collideY(this, box, maxDist);
            }
            case Z: {
                return io.papermc.paper.util.CollisionUtil.collideZ(this, box, maxDist);
            }
            default: {
                throw new RuntimeException("Unknown axis: " + axis);
            }
        }
        // Paper end - optimise collisions
    }

    protected double collideX(AxisCycle axisCycle, AABB box, double maxDist) {
        if (this.isEmpty()) {
            return maxDist;
        } else if (Math.abs(maxDist) < 1.0E-7D) {
            return 0.0D;
        } else {
            AxisCycle axisCycle2 = axisCycle.inverse();
            Direction.Axis axis = axisCycle2.cycle(Direction.Axis.X);
            Direction.Axis axis2 = axisCycle2.cycle(Direction.Axis.Y);
            Direction.Axis axis3 = axisCycle2.cycle(Direction.Axis.Z);
            double d = box.max(axis);
            double e = box.min(axis);
            int i = this.findIndex(axis, e + 1.0E-7D);
            int j = this.findIndex(axis, d - 1.0E-7D);
            int k = Math.max(0, this.findIndex(axis2, box.min(axis2) + 1.0E-7D));
            int l = Math.min(this.shape.getSize(axis2), this.findIndex(axis2, box.max(axis2) - 1.0E-7D) + 1);
            int m = Math.max(0, this.findIndex(axis3, box.min(axis3) + 1.0E-7D));
            int n = Math.min(this.shape.getSize(axis3), this.findIndex(axis3, box.max(axis3) - 1.0E-7D) + 1);
            int o = this.shape.getSize(axis);
            if (maxDist > 0.0D) {
                for(int p = j + 1; p < o; ++p) {
                    for(int q = k; q < l; ++q) {
                        for(int r = m; r < n; ++r) {
                            if (this.shape.isFullWide(axisCycle2, p, q, r)) {
                                double f = this.get(axis, p) - d;
                                if (f >= -1.0E-7D) {
                                    maxDist = Math.min(maxDist, f);
                                }

                                return maxDist;
                            }
                        }
                    }
                }
            } else if (maxDist < 0.0D) {
                for(int s = i - 1; s >= 0; --s) {
                    for(int t = k; t < l; ++t) {
                        for(int u = m; u < n; ++u) {
                            if (this.shape.isFullWide(axisCycle2, s, t, u)) {
                                double g = this.get(axis, s + 1) - e;
                                if (g <= 1.0E-7D) {
                                    maxDist = Math.max(maxDist, g);
                                }

                                return maxDist;
                            }
                        }
                    }
                }
            }

            return maxDist;
        }
    }

    @Override
    public String toString() {
        return this.isEmpty() ? "EMPTY" : "VoxelShape[" + this.bounds() + "]";
    }
}
