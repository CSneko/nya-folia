package net.minecraft.world.phys.shapes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.math.DoubleMath;
import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class Shapes {
    public static final double EPSILON = 1.0E-7D;
    public static final double BIG_EPSILON = 1.0E-6D;
    private static final VoxelShape BLOCK = Util.make(() -> {
        // Paper start - optimise collisions - force arrayvoxelshape
        final DiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        shape.fill(0, 0, 0);

        return new ArrayVoxelShape(
            shape,
            io.papermc.paper.util.CollisionUtil.ZERO_ONE, io.papermc.paper.util.CollisionUtil.ZERO_ONE, io.papermc.paper.util.CollisionUtil.ZERO_ONE
        );
        // Paper end - optimise collisions - force arrayvoxelshape
    });
    public static final VoxelShape INFINITY = box(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    private static final VoxelShape EMPTY = new ArrayVoxelShape(new BitSetDiscreteVoxelShape(0, 0, 0), (DoubleList)(new DoubleArrayList(new double[]{0.0D})), (DoubleList)(new DoubleArrayList(new double[]{0.0D})), (DoubleList)(new DoubleArrayList(new double[]{0.0D})));

    // Paper start - optimise collisions - force arrayvoxelshape
    private static final DoubleArrayList[] PARTS_BY_BITS = new DoubleArrayList[] {
            DoubleArrayList.wrap(generateCubeParts(1 << 0)),
            DoubleArrayList.wrap(generateCubeParts(1 << 1)),
            DoubleArrayList.wrap(generateCubeParts(1 << 2)),
            DoubleArrayList.wrap(generateCubeParts(1 << 3))
    };

    private static double[] generateCubeParts(final int parts) {
        // note: parts is a power of two, so we do not need to worry about loss of precision here
        // note: parts is from [2^0, 2^3]
        final double inc = 1.0 / (double)parts;

        final double[] ret = new double[parts + 1];
        double val = 0.0;
        for (int i = 0; i <= parts; ++i) {
            ret[i] = val;
            val += inc;
        }

        return ret;
    }
    // Paper end - optimise collisions - force arrayvoxelshape

    public static VoxelShape empty() {
        return EMPTY;
    }

    public static VoxelShape block() {
        return BLOCK;
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(minX > maxX) && !(minY > maxY) && !(minZ > maxZ)) {
            return create(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            throw new IllegalArgumentException("The min values need to be smaller or equals to the max values");
        }
    }

    public static VoxelShape create(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(maxX - minX < 1.0E-7D) && !(maxY - minY < 1.0E-7D) && !(maxZ - minZ < 1.0E-7D)) {
            // Paper start - optimise collisions
            // force ArrayVoxelShape in every case
            final int bitsX = findBits(minX, maxX);
            final int bitsY = findBits(minY, maxY);
            final int bitsZ = findBits(minZ, maxZ);
            if (bitsX >= 0 && bitsY >= 0 && bitsZ >= 0) {
                if (bitsX == 0 && bitsY == 0 && bitsZ == 0) {
                    return BLOCK;
                } else {
                    final int sizeX = 1 << bitsX;
                    final int sizeY = 1 << bitsY;
                    final int sizeZ = 1 << bitsZ;
                    final BitSetDiscreteVoxelShape shape = BitSetDiscreteVoxelShape.withFilledBounds(
                            sizeX, sizeY, sizeZ,
                            (int)Math.round(minX * (double)sizeX), (int)Math.round(minY * (double)sizeY), (int)Math.round(minZ * (double)sizeZ),
                            (int)Math.round(maxX * (double)sizeX), (int)Math.round(maxY * (double)sizeY), (int)Math.round(maxZ * (double)sizeZ)
                    );
                    return new ArrayVoxelShape(
                            shape,
                            PARTS_BY_BITS[bitsX],
                            PARTS_BY_BITS[bitsY],
                            PARTS_BY_BITS[bitsZ]
                    );
                }
            } else {
                return new ArrayVoxelShape(
                        BLOCK.shape,
                        minX == 0.0 && maxX == 1.0 ? io.papermc.paper.util.CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minX, maxX }),
                        minY == 0.0 && maxY == 1.0 ? io.papermc.paper.util.CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minY, maxY }),
                        minZ == 0.0 && maxZ == 1.0 ? io.papermc.paper.util.CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minZ, maxZ })
                );
            }
            // Paper end - optimise collisions
        } else {
            return empty();
        }
    }

    public static VoxelShape create(AABB box) {
        return create(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @VisibleForTesting
    protected static int findBits(double min, double max) {
        if (!(min < -1.0E-7D) && !(max > 1.0000001D)) {
            for(int i = 0; i <= 3; ++i) {
                int j = 1 << i;
                double d = min * (double)j;
                double e = max * (double)j;
                boolean bl = Math.abs(d - (double)Math.round(d)) < 1.0E-7D * (double)j;
                boolean bl2 = Math.abs(e - (double)Math.round(e)) < 1.0E-7D * (double)j;
                if (bl && bl2) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    protected static long lcm(int a, int b) {
        return (long)a * (long)(b / IntMath.gcd(a, b));
    }

    public static VoxelShape or(VoxelShape first, VoxelShape second) {
        return join(first, second, BooleanOp.OR);
    }

    public static VoxelShape or(VoxelShape first, VoxelShape... others) {
        // Paper start - optimise collisions
        int size = others.length;
        if (size == 0) {
            return first;
        }

        // reduce complexity of joins by splitting the merges

        // add extra slot for first shape
        ++size;
        final VoxelShape[] tmp = Arrays.copyOf(others, size);
        // insert first shape
        tmp[size - 1] = first;

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
                    final VoxelShape one = tmp[i];
                    final VoxelShape second = tmp[next];

                    tmp[newSize++] = Shapes.or(one, second);
                }
            }
            size = newSize;
        }

        return tmp[0];
        // Paper end - optimise collisions
    }

    public static VoxelShape join(VoxelShape first, VoxelShape second, BooleanOp function) {
        return io.papermc.paper.util.CollisionUtil.joinOptimized(first, second, function); // Paper - optimise collisions
    }

    public static VoxelShape joinUnoptimized(VoxelShape one, VoxelShape two, BooleanOp function) {
        return io.papermc.paper.util.CollisionUtil.joinUnoptimized(one, two, function); // Paper - optimise collisions
    }

    public static boolean joinIsNotEmpty(VoxelShape shape1, VoxelShape shape2, BooleanOp predicate) {
        return io.papermc.paper.util.CollisionUtil.isJoinNonEmpty(shape1, shape2, predicate); // Paper - optimise collisions
    }

    private static boolean joinIsNotEmpty(IndexMerger mergedX, IndexMerger mergedY, IndexMerger mergedZ, DiscreteVoxelShape shape1, DiscreteVoxelShape shape2, BooleanOp predicate) {
        return !mergedX.forMergedIndexes((x1, x2, index1) -> {
            return mergedY.forMergedIndexes((y1, y2, index2) -> {
                return mergedZ.forMergedIndexes((z1, z2, index3) -> {
                    return !predicate.apply(shape1.isFullWide(x1, y1, z1), shape2.isFullWide(x2, y2, z2));
                });
            });
        });
    }

    public static double collide(Direction.Axis axis, AABB box, Iterable<VoxelShape> shapes, double maxDist) {
        for(VoxelShape voxelShape : shapes) {
            if (Math.abs(maxDist) < 1.0E-7D) {
                return 0.0D;
            }

            maxDist = voxelShape.collide(axis, box, maxDist);
        }

        return maxDist;
    }

    public static boolean blockOccudes(VoxelShape shape, VoxelShape neighbor, Direction direction) {
        // Paper start - optimise collisions
        final boolean firstBlock = shape == BLOCK;
        final boolean secondBlock = neighbor == BLOCK;

        if (firstBlock & secondBlock) {
            return true;
        }

        if (shape.isEmpty() | neighbor.isEmpty()) {
            return false;
        }

        // we optimise getOpposite, so we can use it
        // secondly, use our cache to retrieve sliced shape
        final VoxelShape newFirst = shape.getFaceShapeClamped(direction);
        if (newFirst.isEmpty()) {
            return false;
        }
        final VoxelShape newSecond = neighbor.getFaceShapeClamped(direction.getOpposite());
        if (newSecond.isEmpty()) {
            return false;
        }

        return !joinIsNotEmpty(newFirst, newSecond, BooleanOp.ONLY_FIRST);
        // Paper end - optimise collisions
    }

    public static VoxelShape getFaceShape(VoxelShape shape, Direction direction) {
        return shape.getFaceShapeClamped(direction); // Paper - optimise collisions
    }

    // Paper start - optimise collisions
    private static boolean mergedMayOccludeBlock(final VoxelShape shape1, final VoxelShape shape2) {
        // if the combined bounds of the two shapes cannot occlude, then neither can the merged
        final AABB bounds1 = shape1.bounds();
        final AABB bounds2 = shape2.bounds();

        final double minX = Math.min(bounds1.minX, bounds2.minX);
        final double minY = Math.min(bounds1.minY, bounds2.minY);
        final double minZ = Math.min(bounds1.minZ, bounds2.minZ);

        final double maxX = Math.max(bounds1.maxX, bounds2.maxX);
        final double maxY = Math.max(bounds1.maxY, bounds2.maxY);
        final double maxZ = Math.max(bounds1.maxZ, bounds2.maxZ);

        return (minX <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON && maxX >= (1 - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)) &&
            (minY <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON && maxY >= (1 - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)) &&
            (minZ <= io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON && maxZ >= (1 - io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON));
    }
    // Paper end - optimise collisions

    public static boolean mergedFaceOccludes(VoxelShape one, VoxelShape two, Direction direction) {
        // Paper start - optimise collisions
        // see if any of the shapes on their own occludes, only if cached
        if (one.occludesFullBlockIfCached() || two.occludesFullBlockIfCached()) {
            return true;
        }

        if (one.isEmpty() & two.isEmpty()) {
            return false;
        }

        // we optimise getOpposite, so we can use it
        // secondly, use our cache to retrieve sliced shape
        final VoxelShape newFirst = one.getFaceShapeClamped(direction);
        final VoxelShape newSecond = two.getFaceShapeClamped(direction.getOpposite());

        // see if any of the shapes on their own occludes, only if cached
        if (newFirst.occludesFullBlockIfCached() || newSecond.occludesFullBlockIfCached()) {
            return true;
        }

        final boolean firstEmpty = newFirst.isEmpty();
        final boolean secondEmpty = newSecond.isEmpty();

        if (firstEmpty & secondEmpty) {
            return false;
        }

        if (firstEmpty | secondEmpty) {
            return secondEmpty ? newFirst.occludesFullBlock() : newSecond.occludesFullBlock();
        }

        if (newFirst == newSecond) {
            return newFirst.occludesFullBlock();
        }

        return mergedMayOccludeBlock(newFirst, newSecond) && newFirst.orUnoptimized(newSecond).occludesFullBlock();
        // Paper end - optimise collisions
    }

    public static boolean faceShapeOccludes(VoxelShape one, VoxelShape two) {
        // Paper start - optimise collisions
        if (one.occludesFullBlockIfCached() || two.occludesFullBlockIfCached()) {
            return true;
        }

        final boolean s1Empty = one.isEmpty();
        final boolean s2Empty = two.isEmpty();
        if (s1Empty & s2Empty) {
            return false;
        }

        if (s1Empty | s2Empty) {
            return s2Empty ? one.occludesFullBlock() : two.occludesFullBlock();
        }

        if (one == two) {
            return one.occludesFullBlock();
        }

        return mergedMayOccludeBlock(one, two) && (one.orUnoptimized(two)).occludesFullBlock();
        // Paper end - optimise collisions
    }

    @VisibleForTesting
    private static IndexMerger createIndexMerger(int size, DoubleList first, DoubleList second, boolean includeFirst, boolean includeSecond) { // Paper - private
        // Paper start - fast track the most common scenario
        // doublelist is usually a DoubleArrayList with Infinite head/tails that falls to the final else clause
        // This is actually the most common path, so jump to it straight away
        if (first.getDouble(0) == Double.NEGATIVE_INFINITY && first.getDouble(first.size() - 1) == Double.POSITIVE_INFINITY) {
            return new IndirectMerger(first, second, includeFirst, includeSecond);
        }
        // Split out rest to hopefully inline the above
        return lessCommonMerge(size, first, second, includeFirst, includeSecond);
    }

    private static IndexMerger lessCommonMerge(int size, DoubleList first, DoubleList second, boolean includeFirst, boolean includeSecond) {
        int i = first.size() - 1;
        int j = second.size() - 1;
        // Paper note - Rewrite below as optimized order if instead of nasty ternary
        if (first instanceof CubePointRange && second instanceof CubePointRange) {
            long l = lcm(i, j);
            if ((long)size * l <= 256L) {
                return new DiscreteCubeMerger(i, j);
            }
        }

        // Paper start - Identical happens more often than Disjoint
        if (i == j && Objects.equals(first, second)) {
            if (first instanceof IdenticalMerger) {
                return (IndexMerger) first;
            } else if (second instanceof IdenticalMerger) {
                return (IndexMerger) second;
            }
            return new IdenticalMerger(first);
        } else if (first.getDouble(i) < second.getDouble(0) - 1.0E-7D) {
            return new NonOverlappingMerger(first, second, false);
        } else if (second.getDouble(j) < first.getDouble(0) - 1.0E-7D) {
            return new NonOverlappingMerger(second, first, true);
        } else {
            return new IndirectMerger(first, second, includeFirst, includeSecond);
        }
        // Paper end
    }

    public interface DoubleLineConsumer {
        void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }
}
