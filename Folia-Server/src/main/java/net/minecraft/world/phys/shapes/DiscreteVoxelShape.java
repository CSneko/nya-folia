package net.minecraft.world.phys.shapes;

import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;

public abstract class DiscreteVoxelShape {
    private static final Direction.Axis[] AXIS_VALUES = Direction.Axis.values();
    protected final int xSize;
    protected final int ySize;
    protected final int zSize;

    // Paper start - optimise collisions
    private io.papermc.paper.util.collisions.CachedShapeData cachedShapeData;

    public final io.papermc.paper.util.collisions.CachedShapeData getOrCreateCachedShapeData() {
        if (this.cachedShapeData != null) {
            return this.cachedShapeData;
        }

        final DiscreteVoxelShape discreteVoxelShape = (DiscreteVoxelShape)(Object)this;

        final int sizeX = discreteVoxelShape.getXSize();
        final int sizeY = discreteVoxelShape.getYSize();
        final int sizeZ = discreteVoxelShape.getZSize();

        final int maxIndex = sizeX * sizeY * sizeZ; // exclusive

        final int longsRequired = (maxIndex + (Long.SIZE - 1)) >>> 6;
        long[] voxelSet;

        final boolean isEmpty = discreteVoxelShape.isEmpty();

        if (discreteVoxelShape instanceof BitSetDiscreteVoxelShape bitsetShape) {
            voxelSet = bitsetShape.storage.toLongArray();
            if (voxelSet.length < longsRequired) {
                // happens when the later long values are 0L, so we need to resize
                voxelSet = java.util.Arrays.copyOf(voxelSet, longsRequired);
            }
        } else {
            voxelSet = new long[longsRequired];
            if (!isEmpty) {
                final int mulX = sizeZ * sizeY;
                for (int x = 0; x < sizeX; ++x) {
                    for (int y = 0; y < sizeY; ++y) {
                        for (int z = 0; z < sizeZ; ++z) {
                            if (discreteVoxelShape.isFull(x, y, z)) {
                                // index = z + y*size_z + x*(size_z*size_y)
                                final int index = z + y * sizeZ + x * mulX;

                                voxelSet[index >>> 6] |= 1L << index;
                            }
                        }
                    }
                }
            }
        }

        final boolean hasSingleAABB = sizeX == 1 && sizeY == 1 && sizeZ == 1 && !isEmpty && discreteVoxelShape.isFull(0, 0, 0);

        final int minFullX = discreteVoxelShape.firstFull(Direction.Axis.X);
        final int minFullY = discreteVoxelShape.firstFull(Direction.Axis.Y);
        final int minFullZ = discreteVoxelShape.firstFull(Direction.Axis.Z);

        final int maxFullX = discreteVoxelShape.lastFull(Direction.Axis.X);
        final int maxFullY = discreteVoxelShape.lastFull(Direction.Axis.Y);
        final int maxFullZ = discreteVoxelShape.lastFull(Direction.Axis.Z);

        return this.cachedShapeData = new io.papermc.paper.util.collisions.CachedShapeData(
                sizeX, sizeY, sizeZ, voxelSet,
                minFullX, minFullY, minFullZ,
                maxFullX, maxFullY, maxFullZ,
                isEmpty, hasSingleAABB
        );
    }
    // Paper end - optimise collisions

    protected DiscreteVoxelShape(int sizeX, int sizeY, int sizeZ) {
        if (sizeX >= 0 && sizeY >= 0 && sizeZ >= 0) {
            this.xSize = sizeX;
            this.ySize = sizeY;
            this.zSize = sizeZ;
        } else {
            throw new IllegalArgumentException("Need all positive sizes: x: " + sizeX + ", y: " + sizeY + ", z: " + sizeZ);
        }
    }

    public boolean isFullWide(AxisCycle cycle, int x, int y, int z) {
        return this.isFullWide(cycle.cycle(x, y, z, Direction.Axis.X), cycle.cycle(x, y, z, Direction.Axis.Y), cycle.cycle(x, y, z, Direction.Axis.Z));
    }

    public boolean isFullWide(int x, int y, int z) {
        if (x >= 0 && y >= 0 && z >= 0) {
            return x < this.xSize && y < this.ySize && z < this.zSize ? this.isFull(x, y, z) : false;
        } else {
            return false;
        }
    }

    public boolean isFull(AxisCycle cycle, int x, int y, int z) {
        return this.isFull(cycle.cycle(x, y, z, Direction.Axis.X), cycle.cycle(x, y, z, Direction.Axis.Y), cycle.cycle(x, y, z, Direction.Axis.Z));
    }

    public abstract boolean isFull(int x, int y, int z);

    public abstract void fill(int x, int y, int z);

    public boolean isEmpty() {
        for(Direction.Axis axis : AXIS_VALUES) {
            if (this.firstFull(axis) >= this.lastFull(axis)) {
                return true;
            }
        }

        return false;
    }

    public abstract int firstFull(Direction.Axis axis);

    public abstract int lastFull(Direction.Axis axis);

    public int firstFull(Direction.Axis axis, int from, int to) {
        int i = this.getSize(axis);
        if (from >= 0 && to >= 0) {
            Direction.Axis axis2 = AxisCycle.FORWARD.cycle(axis);
            Direction.Axis axis3 = AxisCycle.BACKWARD.cycle(axis);
            if (from < this.getSize(axis2) && to < this.getSize(axis3)) {
                AxisCycle axisCycle = AxisCycle.between(Direction.Axis.X, axis);

                for(int j = 0; j < i; ++j) {
                    if (this.isFull(axisCycle, j, from, to)) {
                        return j;
                    }
                }

                return i;
            } else {
                return i;
            }
        } else {
            return i;
        }
    }

    public int lastFull(Direction.Axis axis, int from, int to) {
        if (from >= 0 && to >= 0) {
            Direction.Axis axis2 = AxisCycle.FORWARD.cycle(axis);
            Direction.Axis axis3 = AxisCycle.BACKWARD.cycle(axis);
            if (from < this.getSize(axis2) && to < this.getSize(axis3)) {
                int i = this.getSize(axis);
                AxisCycle axisCycle = AxisCycle.between(Direction.Axis.X, axis);

                for(int j = i - 1; j >= 0; --j) {
                    if (this.isFull(axisCycle, j, from, to)) {
                        return j + 1;
                    }
                }

                return 0;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public int getSize(Direction.Axis axis) {
        return axis.choose(this.xSize, this.ySize, this.zSize);
    }

    public int getXSize() {
        return this.getSize(Direction.Axis.X);
    }

    public int getYSize() {
        return this.getSize(Direction.Axis.Y);
    }

    public int getZSize() {
        return this.getSize(Direction.Axis.Z);
    }

    public void forAllEdges(DiscreteVoxelShape.IntLineConsumer callback, boolean coalesce) {
        this.forAllAxisEdges(callback, AxisCycle.NONE, coalesce);
        this.forAllAxisEdges(callback, AxisCycle.FORWARD, coalesce);
        this.forAllAxisEdges(callback, AxisCycle.BACKWARD, coalesce);
    }

    private void forAllAxisEdges(DiscreteVoxelShape.IntLineConsumer callback, AxisCycle direction, boolean coalesce) {
        AxisCycle axisCycle = direction.inverse();
        int i = this.getSize(axisCycle.cycle(Direction.Axis.X));
        int j = this.getSize(axisCycle.cycle(Direction.Axis.Y));
        int k = this.getSize(axisCycle.cycle(Direction.Axis.Z));

        for(int l = 0; l <= i; ++l) {
            for(int m = 0; m <= j; ++m) {
                int n = -1;

                for(int o = 0; o <= k; ++o) {
                    int p = 0;
                    int q = 0;

                    for(int r = 0; r <= 1; ++r) {
                        for(int s = 0; s <= 1; ++s) {
                            if (this.isFullWide(axisCycle, l + r - 1, m + s - 1, o)) {
                                ++p;
                                q ^= r ^ s;
                            }
                        }
                    }

                    if (p == 1 || p == 3 || p == 2 && (q & 1) == 0) {
                        if (coalesce) {
                            if (n == -1) {
                                n = o;
                            }
                        } else {
                            callback.consume(axisCycle.cycle(l, m, o, Direction.Axis.X), axisCycle.cycle(l, m, o, Direction.Axis.Y), axisCycle.cycle(l, m, o, Direction.Axis.Z), axisCycle.cycle(l, m, o + 1, Direction.Axis.X), axisCycle.cycle(l, m, o + 1, Direction.Axis.Y), axisCycle.cycle(l, m, o + 1, Direction.Axis.Z));
                        }
                    } else if (n != -1) {
                        callback.consume(axisCycle.cycle(l, m, n, Direction.Axis.X), axisCycle.cycle(l, m, n, Direction.Axis.Y), axisCycle.cycle(l, m, n, Direction.Axis.Z), axisCycle.cycle(l, m, o, Direction.Axis.X), axisCycle.cycle(l, m, o, Direction.Axis.Y), axisCycle.cycle(l, m, o, Direction.Axis.Z));
                        n = -1;
                    }
                }
            }
        }

    }

    public void forAllBoxes(DiscreteVoxelShape.IntLineConsumer consumer, boolean coalesce) {
        BitSetDiscreteVoxelShape.forAllBoxes(this, consumer, coalesce);
    }

    public void forAllFaces(DiscreteVoxelShape.IntFaceConsumer intFaceConsumer) {
        this.forAllAxisFaces(intFaceConsumer, AxisCycle.NONE);
        this.forAllAxisFaces(intFaceConsumer, AxisCycle.FORWARD);
        this.forAllAxisFaces(intFaceConsumer, AxisCycle.BACKWARD);
    }

    private void forAllAxisFaces(DiscreteVoxelShape.IntFaceConsumer intFaceConsumer, AxisCycle direction) {
        AxisCycle axisCycle = direction.inverse();
        Direction.Axis axis = axisCycle.cycle(Direction.Axis.Z);
        int i = this.getSize(axisCycle.cycle(Direction.Axis.X));
        int j = this.getSize(axisCycle.cycle(Direction.Axis.Y));
        int k = this.getSize(axis);
        Direction direction2 = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        Direction direction3 = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

        for(int l = 0; l < i; ++l) {
            for(int m = 0; m < j; ++m) {
                boolean bl = false;

                for(int n = 0; n <= k; ++n) {
                    boolean bl2 = n != k && this.isFull(axisCycle, l, m, n);
                    if (!bl && bl2) {
                        intFaceConsumer.consume(direction2, axisCycle.cycle(l, m, n, Direction.Axis.X), axisCycle.cycle(l, m, n, Direction.Axis.Y), axisCycle.cycle(l, m, n, Direction.Axis.Z));
                    }

                    if (bl && !bl2) {
                        intFaceConsumer.consume(direction3, axisCycle.cycle(l, m, n - 1, Direction.Axis.X), axisCycle.cycle(l, m, n - 1, Direction.Axis.Y), axisCycle.cycle(l, m, n - 1, Direction.Axis.Z));
                    }

                    bl = bl2;
                }
            }
        }

    }

    public interface IntFaceConsumer {
        void consume(Direction direction, int x, int y, int z);
    }

    public interface IntLineConsumer {
        void consume(int x1, int y1, int z1, int x2, int y2, int z2);
    }
}
