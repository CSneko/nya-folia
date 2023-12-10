package net.minecraft.world.phys;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class AABB {
    private static final double EPSILON = 1.0E-7D;
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    // Paper start
    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, boolean dummy) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    // Paper end

    public AABB(BlockPos pos) {
        this((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)(pos.getX() + 1), (double)(pos.getY() + 1), (double)(pos.getZ() + 1));
    }

    public AABB(BlockPos pos1, BlockPos pos2) {
        this((double)pos1.getX(), (double)pos1.getY(), (double)pos1.getZ(), (double)pos2.getX(), (double)pos2.getY(), (double)pos2.getZ());
    }

    public AABB(Vec3 pos1, Vec3 pos2) {
        this(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z);
    }

    public static AABB of(BoundingBox mutable) {
        return new AABB((double)mutable.minX(), (double)mutable.minY(), (double)mutable.minZ(), (double)(mutable.maxX() + 1), (double)(mutable.maxY() + 1), (double)(mutable.maxZ() + 1));
    }

    public static AABB unitCubeFromLowerCorner(Vec3 pos) {
        return new AABB(pos.x, pos.y, pos.z, pos.x + 1.0D, pos.y + 1.0D, pos.z + 1.0D);
    }

    public AABB setMinX(double minX) {
        return new AABB(minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double minY) {
        return new AABB(this.minX, minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double minZ) {
        return new AABB(this.minX, this.minY, minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double maxX) {
        return new AABB(this.minX, this.minY, this.minZ, maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double maxY) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, maxY, this.maxZ);
    }

    public AABB setMaxZ(double maxZ) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, maxZ);
    }

    public double min(Direction.Axis axis) {
        return axis.choose(this.minX, this.minY, this.minZ);
    }

    public double max(Direction.Axis axis) {
        return axis.choose(this.maxX, this.maxY, this.maxZ);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (!(object instanceof AABB)) {
            return false;
        } else {
            AABB aABB = (AABB)object;
            if (Double.compare(aABB.minX, this.minX) != 0) {
                return false;
            } else if (Double.compare(aABB.minY, this.minY) != 0) {
                return false;
            } else if (Double.compare(aABB.minZ, this.minZ) != 0) {
                return false;
            } else if (Double.compare(aABB.maxX, this.maxX) != 0) {
                return false;
            } else if (Double.compare(aABB.maxY, this.maxY) != 0) {
                return false;
            } else {
                return Double.compare(aABB.maxZ, this.maxZ) == 0;
            }
        }
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.minX);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minY);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minZ);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxX);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxY);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxZ);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    public AABB contract(double x, double y, double z) {
        double d = this.minX;
        double e = this.minY;
        double f = this.minZ;
        double g = this.maxX;
        double h = this.maxY;
        double i = this.maxZ;
        if (x < 0.0D) {
            d -= x;
        } else if (x > 0.0D) {
            g -= x;
        }

        if (y < 0.0D) {
            e -= y;
        } else if (y > 0.0D) {
            h -= y;
        }

        if (z < 0.0D) {
            f -= z;
        } else if (z > 0.0D) {
            i -= z;
        }

        return new AABB(d, e, f, g, h, i);
    }

    public AABB expandTowards(Vec3 scale) {
        return this.expandTowards(scale.x, scale.y, scale.z);
    }

    public AABB expandTowards(double x, double y, double z) {
        double d = this.minX;
        double e = this.minY;
        double f = this.minZ;
        double g = this.maxX;
        double h = this.maxY;
        double i = this.maxZ;
        if (x < 0.0D) {
            d += x;
        } else if (x > 0.0D) {
            g += x;
        }

        if (y < 0.0D) {
            e += y;
        } else if (y > 0.0D) {
            h += y;
        }

        if (z < 0.0D) {
            f += z;
        } else if (z > 0.0D) {
            i += z;
        }

        return new AABB(d, e, f, g, h, i);
    }

    public AABB inflate(double x, double y, double z) {
        double d = this.minX - x;
        double e = this.minY - y;
        double f = this.minZ - z;
        double g = this.maxX + x;
        double h = this.maxY + y;
        double i = this.maxZ + z;
        return new AABB(d, e, f, g, h, i);
    }

    public AABB inflate(double value) {
        return this.inflate(value, value, value);
    }

    public AABB intersect(AABB box) {
        double d = Math.max(this.minX, box.minX);
        double e = Math.max(this.minY, box.minY);
        double f = Math.max(this.minZ, box.minZ);
        double g = Math.min(this.maxX, box.maxX);
        double h = Math.min(this.maxY, box.maxY);
        double i = Math.min(this.maxZ, box.maxZ);
        return new AABB(d, e, f, g, h, i);
    }

    public AABB minmax(AABB box) {
        double d = Math.min(this.minX, box.minX);
        double e = Math.min(this.minY, box.minY);
        double f = Math.min(this.minZ, box.minZ);
        double g = Math.max(this.maxX, box.maxX);
        double h = Math.max(this.maxY, box.maxY);
        double i = Math.max(this.maxZ, box.maxZ);
        return new AABB(d, e, f, g, h, i);
    }

    public AABB move(double x, double y, double z) {
        return new AABB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AABB move(BlockPos blockPos) {
        return new AABB(this.minX + (double)blockPos.getX(), this.minY + (double)blockPos.getY(), this.minZ + (double)blockPos.getZ(), this.maxX + (double)blockPos.getX(), this.maxY + (double)blockPos.getY(), this.maxZ + (double)blockPos.getZ());
    }

    public AABB move(Vec3 vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    public boolean intersects(AABB box) {
        return this.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX < maxX && this.maxX > minX && this.minY < maxY && this.maxY > minY && this.minZ < maxZ && this.maxZ > minZ;
    }

    public boolean intersects(Vec3 pos1, Vec3 pos2) {
        return this.intersects(Math.min(pos1.x, pos2.x), Math.min(pos1.y, pos2.y), Math.min(pos1.z, pos2.z), Math.max(pos1.x, pos2.x), Math.max(pos1.y, pos2.y), Math.max(pos1.z, pos2.z));
    }

    public boolean contains(Vec3 pos) {
        return this.contains(pos.x, pos.y, pos.z);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public double getSize() {
        double d = this.getXsize();
        double e = this.getYsize();
        double f = this.getZsize();
        return (d + e + f) / 3.0D;
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double x, double y, double z) {
        return this.inflate(-x, -y, -z);
    }

    public AABB deflate(double value) {
        return this.inflate(-value);
    }

    public Optional<Vec3> clip(Vec3 min, Vec3 max) {
        double[] ds = new double[]{1.0D};
        double d = max.x - min.x;
        double e = max.y - min.y;
        double f = max.z - min.z;
        Direction direction = getDirection(this, min, ds, (Direction)null, d, e, f);
        if (direction == null) {
            return Optional.empty();
        } else {
            double g = ds[0];
            return Optional.of(min.add(g * d, g * e, g * f));
        }
    }

    @Nullable
    public static BlockHitResult clip(Iterable<AABB> boxes, Vec3 from, Vec3 to, BlockPos pos) {
        double[] ds = new double[]{1.0D};
        Direction direction = null;
        double d = to.x - from.x;
        double e = to.y - from.y;
        double f = to.z - from.z;

        for(AABB aABB : boxes) {
            direction = getDirection(aABB.move(pos), from, ds, direction, d, e, f);
        }

        if (direction == null) {
            return null;
        } else {
            double g = ds[0];
            return new BlockHitResult(from.add(g * d, g * e, g * f), direction, pos, false);
        }
    }

    @Nullable
    public static Direction getDirection(AABB box, Vec3 intersectingVector, double[] traceDistanceResult, @Nullable Direction approachDirection, double deltaX, double deltaY, double deltaZ) { // Paper - optimise collisions - public
        if (deltaX > 1.0E-7D) {
            approachDirection = clipPoint(traceDistanceResult, approachDirection, deltaX, deltaY, deltaZ, box.minX, box.minY, box.maxY, box.minZ, box.maxZ, Direction.WEST, intersectingVector.x, intersectingVector.y, intersectingVector.z);
        } else if (deltaX < -1.0E-7D) {
            approachDirection = clipPoint(traceDistanceResult, approachDirection, deltaX, deltaY, deltaZ, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ, Direction.EAST, intersectingVector.x, intersectingVector.y, intersectingVector.z);
        }

        if (deltaY > 1.0E-7D) {
            approachDirection = clipPoint(traceDistanceResult, approachDirection, deltaY, deltaZ, deltaX, box.minY, box.minZ, box.maxZ, box.minX, box.maxX, Direction.DOWN, intersectingVector.y, intersectingVector.z, intersectingVector.x);
        } else if (deltaY < -1.0E-7D) {
            approachDirection = clipPoint(traceDistanceResult, approachDirection, deltaY, deltaZ, deltaX, box.maxY, box.minZ, box.maxZ, box.minX, box.maxX, Direction.UP, intersectingVector.y, intersectingVector.z, intersectingVector.x);
        }

        if (deltaZ > 1.0E-7D) {
            approachDirection = clipPoint(traceDistanceResult, approachDirection, deltaZ, deltaX, deltaY, box.minZ, box.minX, box.maxX, box.minY, box.maxY, Direction.NORTH, intersectingVector.z, intersectingVector.x, intersectingVector.y);
        } else if (deltaZ < -1.0E-7D) {
            approachDirection = clipPoint(traceDistanceResult, approachDirection, deltaZ, deltaX, deltaY, box.maxZ, box.minX, box.maxX, box.minY, box.maxY, Direction.SOUTH, intersectingVector.z, intersectingVector.x, intersectingVector.y);
        }

        return approachDirection;
    }

    @Nullable
    private static Direction clipPoint(double[] traceDistanceResult, @Nullable Direction approachDirection, double deltaX, double deltaY, double deltaZ, double begin, double minX, double maxX, double minZ, double maxZ, Direction resultDirection, double startX, double startY, double startZ) {
        double d = (begin - startX) / deltaX;
        double e = startY + d * deltaY;
        double f = startZ + d * deltaZ;
        if (0.0D < d && d < traceDistanceResult[0] && minX - 1.0E-7D < e && e < maxX + 1.0E-7D && minZ - 1.0E-7D < f && f < maxZ + 1.0E-7D) {
            traceDistanceResult[0] = d;
            return resultDirection;
        } else {
            return approachDirection;
        }
    }

    public double distanceToSqr(Vec3 pos) {
        double d = Math.max(Math.max(this.minX - pos.x, pos.x - this.maxX), 0.0D);
        double e = Math.max(Math.max(this.minY - pos.y, pos.y - this.maxY), 0.0D);
        double f = Math.max(Math.max(this.minZ - pos.z, pos.z - this.maxZ), 0.0D);
        return Mth.lengthSquared(d, e, f);
    }

    @Override
    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5D, this.minX, this.maxX), Mth.lerp(0.5D, this.minY, this.maxY), Mth.lerp(0.5D, this.minZ, this.maxZ));
    }

    public static AABB ofSize(Vec3 center, double dx, double dy, double dz) {
        return new AABB(center.x - dx / 2.0D, center.y - dy / 2.0D, center.z - dz / 2.0D, center.x + dx / 2.0D, center.y + dy / 2.0D, center.z + dz / 2.0D);
    }
}
