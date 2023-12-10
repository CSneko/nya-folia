package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.stream.IntStream;
import javax.annotation.concurrent.Immutable;
import net.minecraft.Util;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

@Immutable
public class Vec3i implements Comparable<Vec3i> {
    public static final Codec<Vec3i> CODEC = Codec.INT_STREAM.comapFlatMap((stream) -> {
        return Util.fixedSize(stream, 3).map((coordinates) -> {
            return new Vec3i(coordinates[0], coordinates[1], coordinates[2]);
        });
    }, (vec) -> {
        return IntStream.of(vec.getX(), vec.getY(), vec.getZ());
    });
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    protected int x; // Paper - protected
    protected int y; // Paper - protected
    protected int z; // Paper - protected

    public static Codec<Vec3i> offsetCodec(int maxAbsValue) {
        return ExtraCodecs.validate(CODEC, (vec) -> {
            return Math.abs(vec.getX()) < maxAbsValue && Math.abs(vec.getY()) < maxAbsValue && Math.abs(vec.getZ()) < maxAbsValue ? DataResult.success(vec) : DataResult.error(() -> {
                return "Position out of range, expected at most " + maxAbsValue + ": " + vec;
            });
        });
    }

    // Paper start
    public final boolean isInsideBuildHeightAndWorldBoundsHorizontal(net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor) {
        return getX() >= -30000000 && getZ() >= -30000000 && getX() < 30000000 && getZ() < 30000000 && !levelHeightAccessor.isOutsideBuildHeight(getY());
    }
    // Paper end

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public final boolean equals(Object object) { // Paper
        if (this == object) {
            return true;
        } else if (!(object instanceof Vec3i)) {
            return false;
        } else {
            Vec3i vec3i = (Vec3i)object;
            if (this.getX() != vec3i.getX()) {
                return false;
            } else if (this.getY() != vec3i.getY()) {
                return false;
            } else {
                return this.getZ() == vec3i.getZ();
            }
        }
    }

    @Override
    public final int hashCode() { // Paper
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    @Override
    public int compareTo(Vec3i vec3i) {
        if (this.getY() == vec3i.getY()) {
            return this.getZ() == vec3i.getZ() ? this.getX() - vec3i.getX() : this.getZ() - vec3i.getZ();
        } else {
            return this.getY() - vec3i.getY();
        }
    }

    public final int getX() { // Paper
        return this.x;
    }

    public final int getY() { // Paper
        return this.y;
    }

    public final int getZ() { // Paper
        return this.z;
    }

    protected Vec3i setX(int x) {
        this.x = x;
        return this;
    }

    protected Vec3i setY(int y) {
        this.y = y;
        return this;
    }

    protected Vec3i setZ(int z) {
        this.z = z;
        return this;
    }

    public Vec3i offset(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0 ? this : new Vec3i(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public Vec3i offset(Vec3i vec) {
        return this.offset(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vec3i subtract(Vec3i vec) {
        return this.offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    public Vec3i multiply(int scale) {
        if (scale == 1) {
            return this;
        } else {
            return scale == 0 ? ZERO : new Vec3i(this.getX() * scale, this.getY() * scale, this.getZ() * scale);
        }
    }

    public Vec3i above() {
        return this.above(1);
    }

    public Vec3i above(int distance) {
        return this.relative(Direction.UP, distance);
    }

    public Vec3i below() {
        return this.below(1);
    }

    public Vec3i below(int distance) {
        return this.relative(Direction.DOWN, distance);
    }

    public Vec3i north() {
        return this.north(1);
    }

    public Vec3i north(int distance) {
        return this.relative(Direction.NORTH, distance);
    }

    public Vec3i south() {
        return this.south(1);
    }

    public Vec3i south(int distance) {
        return this.relative(Direction.SOUTH, distance);
    }

    public Vec3i west() {
        return this.west(1);
    }

    public Vec3i west(int distance) {
        return this.relative(Direction.WEST, distance);
    }

    public Vec3i east() {
        return this.east(1);
    }

    public Vec3i east(int distance) {
        return this.relative(Direction.EAST, distance);
    }

    public Vec3i relative(Direction direction) {
        return this.relative(direction, 1);
    }

    public Vec3i relative(Direction direction, int distance) {
        return distance == 0 ? this : new Vec3i(this.getX() + direction.getStepX() * distance, this.getY() + direction.getStepY() * distance, this.getZ() + direction.getStepZ() * distance);
    }

    public Vec3i relative(Direction.Axis axis, int distance) {
        if (distance == 0) {
            return this;
        } else {
            int i = axis == Direction.Axis.X ? distance : 0;
            int j = axis == Direction.Axis.Y ? distance : 0;
            int k = axis == Direction.Axis.Z ? distance : 0;
            return new Vec3i(this.getX() + i, this.getY() + j, this.getZ() + k);
        }
    }

    public Vec3i cross(Vec3i vec) {
        return new Vec3i(this.getY() * vec.getZ() - this.getZ() * vec.getY(), this.getZ() * vec.getX() - this.getX() * vec.getZ(), this.getX() * vec.getY() - this.getY() * vec.getX());
    }

    public boolean closerThan(Vec3i vec, double distance) {
        return this.distSqr(vec) < Mth.square(distance);
    }

    public boolean closerToCenterThan(Position pos, double distance) {
        return this.distToCenterSqr(pos) < Mth.square(distance);
    }

    public double distSqr(Vec3i vec) {
        return this.distToLowCornerSqr((double)vec.getX(), (double)vec.getY(), (double)vec.getZ());
    }

    public double distToCenterSqr(Position pos) {
        return this.distToCenterSqr(pos.x(), pos.y(), pos.z());
    }

    public double distToCenterSqr(double x, double y, double z) {
        double d = (double)this.getX() + 0.5D - x;
        double e = (double)this.getY() + 0.5D - y;
        double f = (double)this.getZ() + 0.5D - z;
        return d * d + e * e + f * f;
    }

    public double distToLowCornerSqr(double x, double y, double z) {
        double d = (double)this.getX() - x;
        double e = (double)this.getY() - y;
        double f = (double)this.getZ() - z;
        return d * d + e * e + f * f;
    }

    public int distManhattan(Vec3i vec) {
        float f = (float)Math.abs(vec.getX() - this.getX());
        float g = (float)Math.abs(vec.getY() - this.getY());
        float h = (float)Math.abs(vec.getZ() - this.getZ());
        return (int)(f + g + h);
    }

    public int get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }
}
