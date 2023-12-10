package net.minecraft.network.protocol.game;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.VisibleForTesting;

public class VecDeltaCodec {
    private static final double TRUNCATION_STEPS = 4096.0D;
    private Vec3 base = Vec3.ZERO;

    @VisibleForTesting
    static long encode(double value) {
        return Math.round(value * 4096.0D); // Paper - diff on change
    }

    @VisibleForTesting
    static double decode(long value) {
        return (double)value / 4096.0D; // Paper - diff on change
    }

    public Vec3 decode(long x, long y, long z) {
        if (x == 0L && y == 0L && z == 0L) {
            return this.base;
        } else {
            double d = x == 0L ? this.base.x : decode(encode(this.base.x) + x);
            double e = y == 0L ? this.base.y : decode(encode(this.base.y) + y);
            double f = z == 0L ? this.base.z : decode(encode(this.base.z) + z);
            return new Vec3(d, e, f);
        }
    }

    public long encodeX(Vec3 pos) {
        return encode(pos.x) - encode(this.base.x);
    }

    public long encodeY(Vec3 pos) {
        return encode(pos.y) - encode(this.base.y);
    }

    public long encodeZ(Vec3 pos) {
        return encode(pos.z) - encode(this.base.z);
    }

    public Vec3 delta(Vec3 pos) {
        return pos.subtract(this.base);
    }

    public void setBase(Vec3 pos) {
        this.base = pos;
    }
}
