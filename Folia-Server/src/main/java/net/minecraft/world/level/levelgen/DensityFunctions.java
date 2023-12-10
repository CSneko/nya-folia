package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.slf4j.Logger;

public final class DensityFunctions {
    private static final Codec<DensityFunction> CODEC = BuiltInRegistries.DENSITY_FUNCTION_TYPE.byNameCodec().dispatch((densityFunction) -> {
        return densityFunction.codec().codec();
    }, Function.identity());
    protected static final double MAX_REASONABLE_NOISE_VALUE = 1000000.0D;
    static final Codec<Double> NOISE_VALUE_CODEC = Codec.doubleRange(-1000000.0D, 1000000.0D);
    public static final Codec<DensityFunction> DIRECT_CODEC = Codec.either(NOISE_VALUE_CODEC, CODEC).xmap((either) -> {
        return either.map(DensityFunctions::constant, Function.identity());
    }, (densityFunction) -> {
        if (densityFunction instanceof DensityFunctions.Constant constant) {
            return Either.left(constant.value());
        } else {
            return Either.right(densityFunction);
        }
    });

    public static Codec<? extends DensityFunction> bootstrap(Registry<Codec<? extends DensityFunction>> registry) {
        register(registry, "blend_alpha", DensityFunctions.BlendAlpha.CODEC);
        register(registry, "blend_offset", DensityFunctions.BlendOffset.CODEC);
        register(registry, "beardifier", DensityFunctions.BeardifierMarker.CODEC);
        register(registry, "old_blended_noise", BlendedNoise.CODEC);

        for(DensityFunctions.Marker.Type type : DensityFunctions.Marker.Type.values()) {
            register(registry, type.getSerializedName(), type.codec);
        }

        register(registry, "noise", DensityFunctions.Noise.CODEC);
        register(registry, "end_islands", DensityFunctions.EndIslandDensityFunction.CODEC);
        register(registry, "weird_scaled_sampler", DensityFunctions.WeirdScaledSampler.CODEC);
        register(registry, "shifted_noise", DensityFunctions.ShiftedNoise.CODEC);
        register(registry, "range_choice", DensityFunctions.RangeChoice.CODEC);
        register(registry, "shift_a", DensityFunctions.ShiftA.CODEC);
        register(registry, "shift_b", DensityFunctions.ShiftB.CODEC);
        register(registry, "shift", DensityFunctions.Shift.CODEC);
        register(registry, "blend_density", DensityFunctions.BlendDensity.CODEC);
        register(registry, "clamp", DensityFunctions.Clamp.CODEC);

        for(DensityFunctions.Mapped.Type type2 : DensityFunctions.Mapped.Type.values()) {
            register(registry, type2.getSerializedName(), type2.codec);
        }

        for(DensityFunctions.TwoArgumentSimpleFunction.Type type3 : DensityFunctions.TwoArgumentSimpleFunction.Type.values()) {
            register(registry, type3.getSerializedName(), type3.codec);
        }

        register(registry, "spline", DensityFunctions.Spline.CODEC);
        register(registry, "constant", DensityFunctions.Constant.CODEC);
        return register(registry, "y_clamped_gradient", DensityFunctions.YClampedGradient.CODEC);
    }

    private static Codec<? extends DensityFunction> register(Registry<Codec<? extends DensityFunction>> registry, String id, KeyDispatchDataCodec<? extends DensityFunction> codecHolder) {
        return Registry.register(registry, id, codecHolder.codec());
    }

    static <A, O> KeyDispatchDataCodec<O> singleArgumentCodec(Codec<A> codec, Function<A, O> creator, Function<O, A> argumentGetter) {
        return KeyDispatchDataCodec.of(codec.fieldOf("argument").xmap(creator, argumentGetter));
    }

    static <O> KeyDispatchDataCodec<O> singleFunctionArgumentCodec(Function<DensityFunction, O> creator, Function<O, DensityFunction> argumentGetter) {
        return singleArgumentCodec(DensityFunction.HOLDER_HELPER_CODEC, creator, argumentGetter);
    }

    static <O> KeyDispatchDataCodec<O> doubleFunctionArgumentCodec(BiFunction<DensityFunction, DensityFunction, O> creator, Function<O, DensityFunction> argument1Getter, Function<O, DensityFunction> argument2Getter) {
        return KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument1").forGetter(argument1Getter), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument2").forGetter(argument2Getter)).apply(instance, creator);
        }));
    }

    static <O> KeyDispatchDataCodec<O> makeCodec(MapCodec<O> mapCodec) {
        return KeyDispatchDataCodec.of(mapCodec);
    }

    private DensityFunctions() {
    }

    public static DensityFunction interpolated(DensityFunction inputFunction) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.Interpolated, inputFunction);
    }

    public static DensityFunction flatCache(DensityFunction inputFunction) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.FlatCache, inputFunction);
    }

    public static DensityFunction cache2d(DensityFunction inputFunction) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.Cache2D, inputFunction);
    }

    public static DensityFunction cacheOnce(DensityFunction inputFunction) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.CacheOnce, inputFunction);
    }

    public static DensityFunction cacheAllInCell(DensityFunction inputFunction) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.CacheAllInCell, inputFunction);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseParameters, @Deprecated double scaleXz, double scaleY, double min, double max) {
        return mapFromUnitTo(new DensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseParameters), scaleXz, scaleY), min, max);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseParameters, double scaleY, double min, double max) {
        return mappedNoise(noiseParameters, 1.0D, scaleY, min, max);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseParameters, double min, double max) {
        return mappedNoise(noiseParameters, 1.0D, 1.0D, min, max);
    }

    public static DensityFunction shiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale, Holder<NormalNoise.NoiseParameters> noiseParameters) {
        return new DensityFunctions.ShiftedNoise(shiftX, zero(), shiftZ, xzScale, 0.0D, new DensityFunction.NoiseHolder(noiseParameters));
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseParameters) {
        return noise(noiseParameters, 1.0D, 1.0D);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseParameters, double scaleXz, double scaleY) {
        return new DensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseParameters), scaleXz, scaleY);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseParameters, double scaleY) {
        return noise(noiseParameters, 1.0D, scaleY);
    }

    public static DensityFunction rangeChoice(DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange) {
        return new DensityFunctions.RangeChoice(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityFunction shiftA(Holder<NormalNoise.NoiseParameters> noiseParameters) {
        return new DensityFunctions.ShiftA(new DensityFunction.NoiseHolder(noiseParameters));
    }

    public static DensityFunction shiftB(Holder<NormalNoise.NoiseParameters> noiseParameters) {
        return new DensityFunctions.ShiftB(new DensityFunction.NoiseHolder(noiseParameters));
    }

    public static DensityFunction shift(Holder<NormalNoise.NoiseParameters> noiseParameters) {
        return new DensityFunctions.Shift(new DensityFunction.NoiseHolder(noiseParameters));
    }

    public static DensityFunction blendDensity(DensityFunction input) {
        return new DensityFunctions.BlendDensity(input);
    }

    public static DensityFunction endIslands(long seed) {
        return new DensityFunctions.EndIslandDensityFunction(seed);
    }

    public static DensityFunction weirdScaledSampler(DensityFunction input, Holder<NormalNoise.NoiseParameters> parameters, DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper) {
        return new DensityFunctions.WeirdScaledSampler(input, new DensityFunction.NoiseHolder(parameters), mapper);
    }

    public static DensityFunction add(DensityFunction a, DensityFunction b) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.ADD, a, b);
    }

    public static DensityFunction mul(DensityFunction a, DensityFunction b) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MUL, a, b);
    }

    public static DensityFunction min(DensityFunction a, DensityFunction b) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MIN, a, b);
    }

    public static DensityFunction max(DensityFunction a, DensityFunction b) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MAX, a, b);
    }

    public static DensityFunction spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
        return new DensityFunctions.Spline(spline);
    }

    public static DensityFunction zero() {
        return DensityFunctions.Constant.ZERO;
    }

    public static DensityFunction constant(double density) {
        return new DensityFunctions.Constant(density);
    }

    public static DensityFunction yClampedGradient(int fromY, int toY, double fromValue, double toValue) {
        return new DensityFunctions.YClampedGradient(fromY, toY, fromValue, toValue);
    }

    public static DensityFunction map(DensityFunction input, DensityFunctions.Mapped.Type type) {
        return DensityFunctions.Mapped.create(type, input);
    }

    private static DensityFunction mapFromUnitTo(DensityFunction function, double min, double max) {
        double d = (min + max) * 0.5D;
        double e = (max - min) * 0.5D;
        return add(constant(d), mul(constant(e), function));
    }

    public static DensityFunction blendAlpha() {
        return DensityFunctions.BlendAlpha.INSTANCE;
    }

    public static DensityFunction blendOffset() {
        return DensityFunctions.BlendOffset.INSTANCE;
    }

    public static DensityFunction lerp(DensityFunction delta, DensityFunction start, DensityFunction end) {
        if (start instanceof DensityFunctions.Constant constant) {
            return lerp(delta, constant.value, end);
        } else {
            DensityFunction densityFunction = cacheOnce(delta);
            DensityFunction densityFunction2 = add(mul(densityFunction, constant(-1.0D)), constant(1.0D));
            return add(mul(start, densityFunction2), mul(end, densityFunction));
        }
    }

    public static DensityFunction lerp(DensityFunction delta, double start, DensityFunction end) {
        return add(mul(delta, add(end, constant(-start))), constant(start));
    }

    static record Ap2(DensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2, double minValue, double maxValue) implements DensityFunctions.TwoArgumentSimpleFunction {
        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            double d = this.argument1.compute(pos);
            double var10000;
            switch (this.type) {
                case ADD:
                    var10000 = d + this.argument2.compute(pos);
                    break;
                case MAX:
                    var10000 = d > this.argument2.maxValue() ? d : Math.max(d, this.argument2.compute(pos));
                    break;
                case MIN:
                    var10000 = d < this.argument2.minValue() ? d : Math.min(d, this.argument2.compute(pos));
                    break;
                case MUL:
                    var10000 = d == 0.0D ? 0.0D : d * this.argument2.compute(pos);
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            return var10000;
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            this.argument1.fillArray(densities, applier);
            switch (this.type) {
                case ADD:
                    double[] ds = new double[densities.length];
                    this.argument2.fillArray(ds, applier);

                    for(int i = 0; i < densities.length; ++i) {
                        densities[i] += ds[i];
                    }
                    break;
                case MAX:
                    double g = this.argument2.maxValue();

                    for(int l = 0; l < densities.length; ++l) {
                        double h = densities[l];
                        densities[l] = h > g ? h : Math.max(h, this.argument2.compute(applier.forIndex(l)));
                    }
                    break;
                case MIN:
                    double e = this.argument2.minValue();

                    for(int k = 0; k < densities.length; ++k) {
                        double f = densities[k];
                        densities[k] = f < e ? f : Math.min(f, this.argument2.compute(applier.forIndex(k)));
                    }
                    break;
                case MUL:
                    for(int j = 0; j < densities.length; ++j) {
                        double d = densities[j];
                        densities[j] = d == 0.0D ? 0.0D : d * this.argument2.compute(applier.forIndex(j));
                    }
            }

        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(DensityFunctions.TwoArgumentSimpleFunction.create(this.type, this.argument1.mapAll(visitor), this.argument2.mapAll(visitor)));
        }
    }

    protected static enum BeardifierMarker implements DensityFunctions.BeardifierOrMarker {
        INSTANCE;

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return 0.0D;
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            Arrays.fill(densities, 0.0D);
        }

        @Override
        public double minValue() {
            return 0.0D;
        }

        @Override
        public double maxValue() {
            return 0.0D;
        }
    }

    public interface BeardifierOrMarker extends DensityFunction.SimpleFunction {
        KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(DensityFunctions.BeardifierMarker.INSTANCE));

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static enum BlendAlpha implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return 1.0D;
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            Arrays.fill(densities, 1.0D);
        }

        @Override
        public double minValue() {
            return 1.0D;
        }

        @Override
        public double maxValue() {
            return 1.0D;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    static record BlendDensity(DensityFunction input) implements DensityFunctions.TransformerWithContext {
        static final KeyDispatchDataCodec<DensityFunctions.BlendDensity> CODEC = DensityFunctions.singleFunctionArgumentCodec(DensityFunctions.BlendDensity::new, DensityFunctions.BlendDensity::input);

        @Override
        public double transform(DensityFunction.FunctionContext pos, double density) {
            return pos.getBlender().blendDensity(pos, density);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.BlendDensity(this.input.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static enum BlendOffset implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return 0.0D;
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            Arrays.fill(densities, 0.0D);
        }

        @Override
        public double minValue() {
            return 0.0D;
        }

        @Override
        public double maxValue() {
            return 0.0D;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static record Clamp(DensityFunction input, double minValue, double maxValue) implements DensityFunctions.PureTransformer {
        private static final MapCodec<DensityFunctions.Clamp> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(DensityFunction.DIRECT_CODEC.fieldOf("input").forGetter(DensityFunctions.Clamp::input), DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min").forGetter(DensityFunctions.Clamp::minValue), DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max").forGetter(DensityFunctions.Clamp::maxValue)).apply(instance, DensityFunctions.Clamp::new);
        });
        public static final KeyDispatchDataCodec<DensityFunctions.Clamp> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double transform(double density) {
            return Mth.clamp(density, this.minValue, this.maxValue);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return new DensityFunctions.Clamp(this.input.mapAll(visitor), this.minValue, this.maxValue);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    static record Constant(double value) implements DensityFunction.SimpleFunction {
        static final KeyDispatchDataCodec<DensityFunctions.Constant> CODEC = DensityFunctions.singleArgumentCodec(DensityFunctions.NOISE_VALUE_CODEC, DensityFunctions.Constant::new, DensityFunctions.Constant::value);
        static final DensityFunctions.Constant ZERO = new DensityFunctions.Constant(0.0D);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.value;
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            Arrays.fill(densities, this.value);
        }

        @Override
        public double minValue() {
            return this.value;
        }

        @Override
        public double maxValue() {
            return this.value;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static final class EndIslandDensityFunction implements DensityFunction.SimpleFunction {
        public static final KeyDispatchDataCodec<DensityFunctions.EndIslandDensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(new DensityFunctions.EndIslandDensityFunction(0L)));
        private static final float ISLAND_THRESHOLD = -0.9F;
        private final SimplexNoise islandNoise;
        // Paper start
        private static final class NoiseCache {
            public long[] keys = new long[8192];
            public float[] values = new float[8192];
            public NoiseCache() {
                java.util.Arrays.fill(keys, Long.MIN_VALUE);
            }
        }
        private static final ThreadLocal<java.util.Map<SimplexNoise, NoiseCache>> noiseCache = ThreadLocal.withInitial(java.util.WeakHashMap::new);
        // Paper end

        public EndIslandDensityFunction(long seed) {
            RandomSource randomSource = new LegacyRandomSource(seed);
            randomSource.consumeCount(17292);
            this.islandNoise = new SimplexNoise(randomSource);
        }

        private static float getHeightValue(SimplexNoise sampler, int x, int z) {
            int i = x / 2;
            int j = z / 2;
            int k = x % 2;
            int l = z % 2;
            float f = 100.0F - Mth.sqrt((long) x * (long) x + (long) z * (long) z) * 8.0F; // Paper - cast ints to long to avoid integer overflow
            f = Mth.clamp(f, -100.0F, 80.0F);

            NoiseCache cache = noiseCache.get().computeIfAbsent(sampler, noiseKey -> new NoiseCache()); // Paper
            for(int m = -12; m <= 12; ++m) {
                for(int n = -12; n <= 12; ++n) {
                    long o = (long)(i + m);
                    long p = (long)(j + n);
                    // Paper start - Significantly improve end generation performance by using a noise cache
                    long key = net.minecraft.world.level.ChunkPos.asLong((int) o, (int) p);
                    int index = (int) it.unimi.dsi.fastutil.HashCommon.mix(key) & 8191;
                    float g = Float.MIN_VALUE;
                    if (cache.keys[index] == key) {
                        g = cache.values[index];
                    } else {
                        if (o * o + p * p > 4096L && sampler.getValue((double)o, (double)p) < (double)-0.9F) {
                            g = (Mth.abs((float) o) * 3439.0F + Mth.abs((float) p) * 147.0F) % 13.0F + 9.0F;
                        }
                        cache.keys[index] = key;
                        cache.values[index] = g;
                    }
                    if (g != Float.MIN_VALUE) {
                        // Paper end
                        float h = (float)(k - m * 2);
                        float q = (float)(l - n * 2);
                        float r = 100.0F - Mth.sqrt(h * h + q * q) * g;
                        r = Mth.clamp(r, -100.0F, 80.0F);
                        f = Math.max(f, r);
                    }
                }
            }

            return f;
        }

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return ((double)getHeightValue(this.islandNoise, pos.blockX() / 8, pos.blockZ() / 8) - 8.0D) / 128.0D;
        }

        @Override
        public double minValue() {
            return -0.84375D;
        }

        @Override
        public double maxValue() {
            return 0.5625D;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    @VisibleForDebug
    public static record HolderHolder(Holder<DensityFunction> function) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.function.value().compute(pos);
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            this.function.value().fillArray(densities, applier);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.HolderHolder(new Holder.Direct<>(this.function.value().mapAll(visitor))));
        }

        @Override
        public double minValue() {
            return this.function.isBound() ? this.function.value().minValue() : Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return this.function.isBound() ? this.function.value().maxValue() : Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Calling .codec() on HolderHolder");
        }
    }

    protected static record Mapped(DensityFunctions.Mapped.Type type, DensityFunction input, double minValue, double maxValue) implements DensityFunctions.PureTransformer {
        public static DensityFunctions.Mapped create(DensityFunctions.Mapped.Type type, DensityFunction input) {
            double d = input.minValue();
            double e = transform(type, d);
            double f = transform(type, input.maxValue());
            return type != DensityFunctions.Mapped.Type.ABS && type != DensityFunctions.Mapped.Type.SQUARE ? new DensityFunctions.Mapped(type, input, e, f) : new DensityFunctions.Mapped(type, input, Math.max(0.0D, d), Math.max(e, f));
        }

        private static double transform(DensityFunctions.Mapped.Type type, double density) {
            double var10000;
            switch (type) {
                case ABS:
                    var10000 = Math.abs(density);
                    break;
                case SQUARE:
                    var10000 = density * density;
                    break;
                case CUBE:
                    var10000 = density * density * density;
                    break;
                case HALF_NEGATIVE:
                    var10000 = density > 0.0D ? density : density * 0.5D;
                    break;
                case QUARTER_NEGATIVE:
                    var10000 = density > 0.0D ? density : density * 0.25D;
                    break;
                case SQUEEZE:
                    double d = Mth.clamp(density, -1.0D, 1.0D);
                    var10000 = d / 2.0D - d * d * d / 24.0D;
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            return var10000;
        }

        @Override
        public double transform(double density) {
            return transform(this.type, density);
        }

        @Override
        public DensityFunctions.Mapped mapAll(DensityFunction.Visitor visitor) {
            return create(this.type, this.input.mapAll(visitor));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type.codec;
        }

        static enum Type implements StringRepresentable {
            ABS("abs"),
            SQUARE("square"),
            CUBE("cube"),
            HALF_NEGATIVE("half_negative"),
            QUARTER_NEGATIVE("quarter_negative"),
            SQUEEZE("squeeze");

            private final String name;
            final KeyDispatchDataCodec<DensityFunctions.Mapped> codec = DensityFunctions.singleFunctionArgumentCodec((input) -> {
                return DensityFunctions.Mapped.create(this, input);
            }, DensityFunctions.Mapped::input);

            private Type(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected static record Marker(DensityFunctions.Marker.Type type, DensityFunction wrapped) implements DensityFunctions.MarkerOrMarked {
        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.wrapped.compute(pos);
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            this.wrapped.fillArray(densities, applier);
        }

        @Override
        public double minValue() {
            return this.wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return this.wrapped.maxValue();
        }

        static enum Type implements StringRepresentable {
            Interpolated("interpolated"),
            FlatCache("flat_cache"),
            Cache2D("cache_2d"),
            CacheOnce("cache_once"),
            CacheAllInCell("cache_all_in_cell");

            private final String name;
            final KeyDispatchDataCodec<DensityFunctions.MarkerOrMarked> codec = DensityFunctions.singleFunctionArgumentCodec((densityFunction) -> {
                return new DensityFunctions.Marker(this, densityFunction);
            }, DensityFunctions.MarkerOrMarked::wrapped);

            private Type(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    public interface MarkerOrMarked extends DensityFunction {
        DensityFunctions.Marker.Type type();

        DensityFunction wrapped();

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        @Override
        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Marker(this.type(), this.wrapped().mapAll(visitor)));
        }
    }

    static record MulOrAdd(DensityFunctions.MulOrAdd.Type specificType, DensityFunction input, double minValue, double maxValue, double argument) implements DensityFunctions.PureTransformer, DensityFunctions.TwoArgumentSimpleFunction {
        @Override
        public DensityFunctions.TwoArgumentSimpleFunction.Type type() {
            return this.specificType == DensityFunctions.MulOrAdd.Type.MUL ? DensityFunctions.TwoArgumentSimpleFunction.Type.MUL : DensityFunctions.TwoArgumentSimpleFunction.Type.ADD;
        }

        @Override
        public DensityFunction argument1() {
            return DensityFunctions.constant(this.argument);
        }

        @Override
        public DensityFunction argument2() {
            return this.input;
        }

        @Override
        public double transform(double density) {
            double var10000;
            switch (this.specificType) {
                case MUL:
                    var10000 = density * this.argument;
                    break;
                case ADD:
                    var10000 = density + this.argument;
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            return var10000;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            DensityFunction densityFunction = this.input.mapAll(visitor);
            double d = densityFunction.minValue();
            double e = densityFunction.maxValue();
            double f;
            double g;
            if (this.specificType == DensityFunctions.MulOrAdd.Type.ADD) {
                f = d + this.argument;
                g = e + this.argument;
            } else if (this.argument >= 0.0D) {
                f = d * this.argument;
                g = e * this.argument;
            } else {
                f = e * this.argument;
                g = d * this.argument;
            }

            return new DensityFunctions.MulOrAdd(this.specificType, densityFunction, f, g, this.argument);
        }

        static enum Type {
            MUL,
            ADD;
        }
    }

    protected static record Noise(DensityFunction.NoiseHolder noise, double xzScale, double yScale) implements DensityFunction {
        public static final MapCodec<DensityFunctions.Noise> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.Noise::noise), Codec.DOUBLE.fieldOf("xz_scale").forGetter(DensityFunctions.Noise::xzScale), Codec.DOUBLE.fieldOf("y_scale").forGetter(DensityFunctions.Noise::yScale)).apply(instance, DensityFunctions.Noise::new);
        });
        public static final KeyDispatchDataCodec<DensityFunctions.Noise> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.noise.getValue((double)pos.blockX() * this.xzScale, (double)pos.blockY() * this.yScale, (double)pos.blockZ() * this.xzScale);
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            applier.fillAllDirectly(densities, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Noise(visitor.visitNoise(this.noise), this.xzScale, this.yScale));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    interface PureTransformer extends DensityFunction {
        DensityFunction input();

        @Override
        default double compute(DensityFunction.FunctionContext pos) {
            return this.transform(this.input().compute(pos));
        }

        @Override
        default void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            this.input().fillArray(densities, applier);

            for(int i = 0; i < densities.length; ++i) {
                densities[i] = this.transform(densities[i]);
            }

        }

        double transform(double density);
    }

    static record RangeChoice(DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange) implements DensityFunction {
        public static final MapCodec<DensityFunctions.RangeChoice> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(DensityFunctions.RangeChoice::input), DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min_inclusive").forGetter(DensityFunctions.RangeChoice::minInclusive), DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max_exclusive").forGetter(DensityFunctions.RangeChoice::maxExclusive), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_in_range").forGetter(DensityFunctions.RangeChoice::whenInRange), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_out_of_range").forGetter(DensityFunctions.RangeChoice::whenOutOfRange)).apply(instance, DensityFunctions.RangeChoice::new);
        });
        public static final KeyDispatchDataCodec<DensityFunctions.RangeChoice> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            double d = this.input.compute(pos);
            return d >= this.minInclusive && d < this.maxExclusive ? this.whenInRange.compute(pos) : this.whenOutOfRange.compute(pos);
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            this.input.fillArray(densities, applier);

            for(int i = 0; i < densities.length; ++i) {
                double d = densities[i];
                if (d >= this.minInclusive && d < this.maxExclusive) {
                    densities[i] = this.whenInRange.compute(applier.forIndex(i));
                } else {
                    densities[i] = this.whenOutOfRange.compute(applier.forIndex(i));
                }
            }

        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.RangeChoice(this.input.mapAll(visitor), this.minInclusive, this.maxExclusive, this.whenInRange.mapAll(visitor), this.whenOutOfRange.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Math.min(this.whenInRange.minValue(), this.whenOutOfRange.minValue());
        }

        @Override
        public double maxValue() {
            return Math.max(this.whenInRange.maxValue(), this.whenOutOfRange.maxValue());
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static record Shift(DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.Shift> CODEC = DensityFunctions.singleArgumentCodec(DensityFunction.NoiseHolder.CODEC, DensityFunctions.Shift::new, DensityFunctions.Shift::offsetNoise);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.compute((double)pos.blockX(), (double)pos.blockY(), (double)pos.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Shift(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static record ShiftA(DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.ShiftA> CODEC = DensityFunctions.singleArgumentCodec(DensityFunction.NoiseHolder.CODEC, DensityFunctions.ShiftA::new, DensityFunctions.ShiftA::offsetNoise);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.compute((double)pos.blockX(), 0.0D, (double)pos.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftA(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static record ShiftB(DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.ShiftB> CODEC = DensityFunctions.singleArgumentCodec(DensityFunction.NoiseHolder.CODEC, DensityFunctions.ShiftB::new, DensityFunctions.ShiftB::offsetNoise);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return this.compute((double)pos.blockZ(), (double)pos.blockX(), 0.0D);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftB(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    interface ShiftNoise extends DensityFunction {
        DensityFunction.NoiseHolder offsetNoise();

        @Override
        default double minValue() {
            return -this.maxValue();
        }

        @Override
        default double maxValue() {
            return this.offsetNoise().maxValue() * 4.0D;
        }

        default double compute(double x, double y, double z) {
            return this.offsetNoise().getValue(x * 0.25D, y * 0.25D, z * 0.25D) * 4.0D;
        }

        @Override
        default void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            applier.fillAllDirectly(densities, this);
        }
    }

    protected static record ShiftedNoise(DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ, double xzScale, double yScale, DensityFunction.NoiseHolder noise) implements DensityFunction {
        private static final MapCodec<DensityFunctions.ShiftedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_x").forGetter(DensityFunctions.ShiftedNoise::shiftX), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_y").forGetter(DensityFunctions.ShiftedNoise::shiftY), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_z").forGetter(DensityFunctions.ShiftedNoise::shiftZ), Codec.DOUBLE.fieldOf("xz_scale").forGetter(DensityFunctions.ShiftedNoise::xzScale), Codec.DOUBLE.fieldOf("y_scale").forGetter(DensityFunctions.ShiftedNoise::yScale), DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.ShiftedNoise::noise)).apply(instance, DensityFunctions.ShiftedNoise::new);
        });
        public static final KeyDispatchDataCodec<DensityFunctions.ShiftedNoise> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            double d = (double)pos.blockX() * this.xzScale + this.shiftX.compute(pos);
            double e = (double)pos.blockY() * this.yScale + this.shiftY.compute(pos);
            double f = (double)pos.blockZ() * this.xzScale + this.shiftZ.compute(pos);
            return this.noise.getValue(d, e, f);
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            applier.fillAllDirectly(densities, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftedNoise(this.shiftX.mapAll(visitor), this.shiftY.mapAll(visitor), this.shiftZ.mapAll(visitor), this.xzScale, this.yScale, visitor.visitNoise(this.noise)));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    public static record Spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) implements DensityFunction {
        private static final Codec<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> SPLINE_CODEC = CubicSpline.codec(DensityFunctions.Spline.Coordinate.CODEC);
        private static final MapCodec<DensityFunctions.Spline> DATA_CODEC = SPLINE_CODEC.fieldOf("spline").xmap(DensityFunctions.Spline::new, DensityFunctions.Spline::spline);
        public static final KeyDispatchDataCodec<DensityFunctions.Spline> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return (double)this.spline.apply(new DensityFunctions.Spline.Point(pos));
        }

        @Override
        public double minValue() {
            return (double)this.spline.minValue();
        }

        @Override
        public double maxValue() {
            return (double)this.spline.maxValue();
        }

        @Override
        public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            applier.fillAllDirectly(densities, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Spline(this.spline.mapAll((densityFunctionWrapper) -> {
                return densityFunctionWrapper.mapAll(visitor);
            })));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        public static record Coordinate(Holder<DensityFunction> function) implements ToFloatFunction<DensityFunctions.Spline.Point> {
            public static final Codec<DensityFunctions.Spline.Coordinate> CODEC = DensityFunction.CODEC.xmap(DensityFunctions.Spline.Coordinate::new, DensityFunctions.Spline.Coordinate::function);

            @Override
            public String toString() {
                Optional<ResourceKey<DensityFunction>> optional = this.function.unwrapKey();
                if (optional.isPresent()) {
                    ResourceKey<DensityFunction> resourceKey = optional.get();
                    if (resourceKey == NoiseRouterData.CONTINENTS) {
                        return "continents";
                    }

                    if (resourceKey == NoiseRouterData.EROSION) {
                        return "erosion";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES) {
                        return "weirdness";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES_FOLDED) {
                        return "ridges";
                    }
                }

                return "Coordinate[" + this.function + "]";
            }

            @Override
            public float apply(DensityFunctions.Spline.Point x) {
                return (float)this.function.value().compute(x.context());
            }

            @Override
            public float minValue() {
                return this.function.isBound() ? (float)this.function.value().minValue() : Float.NEGATIVE_INFINITY;
            }

            @Override
            public float maxValue() {
                return this.function.isBound() ? (float)this.function.value().maxValue() : Float.POSITIVE_INFINITY;
            }

            public DensityFunctions.Spline.Coordinate mapAll(DensityFunction.Visitor visitor) {
                return new DensityFunctions.Spline.Coordinate(new Holder.Direct<>(this.function.value().mapAll(visitor)));
            }
        }

        public static record Point(DensityFunction.FunctionContext context) {
        }
    }

    interface TransformerWithContext extends DensityFunction {
        DensityFunction input();

        @Override
        default double compute(DensityFunction.FunctionContext pos) {
            return this.transform(pos, this.input().compute(pos));
        }

        @Override
        default void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
            this.input().fillArray(densities, applier);

            for(int i = 0; i < densities.length; ++i) {
                densities[i] = this.transform(applier.forIndex(i), densities[i]);
            }

        }

        double transform(DensityFunction.FunctionContext pos, double density);
    }

    interface TwoArgumentSimpleFunction extends DensityFunction {
        Logger LOGGER = LogUtils.getLogger();

        static DensityFunctions.TwoArgumentSimpleFunction create(DensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2) {
            double d = argument1.minValue();
            double e = argument2.minValue();
            double f = argument1.maxValue();
            double g = argument2.maxValue();
            if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MIN || type == DensityFunctions.TwoArgumentSimpleFunction.Type.MAX) {
                boolean bl = d >= g;
                boolean bl2 = e >= f;
                if (bl || bl2) {
                    LOGGER.warn("Creating a " + type + " function between two non-overlapping inputs: " + argument1 + " and " + argument2);
                }
            }

            double var10000;
            switch (type) {
                case ADD:
                    var10000 = d + e;
                    break;
                case MAX:
                    var10000 = Math.max(d, e);
                    break;
                case MIN:
                    var10000 = Math.min(d, e);
                    break;
                case MUL:
                    var10000 = d > 0.0D && e > 0.0D ? d * e : (f < 0.0D && g < 0.0D ? f * g : Math.min(d * g, f * e));
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            double h = var10000;
            switch (type) {
                case ADD:
                    var10000 = f + g;
                    break;
                case MAX:
                    var10000 = Math.max(f, g);
                    break;
                case MIN:
                    var10000 = Math.min(f, g);
                    break;
                case MUL:
                    var10000 = d > 0.0D && e > 0.0D ? f * g : (f < 0.0D && g < 0.0D ? d * e : Math.max(d * e, f * g));
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            double i = var10000;
            if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MUL || type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD) {
                if (argument1 instanceof DensityFunctions.Constant) {
                    DensityFunctions.Constant constant = (DensityFunctions.Constant)argument1;
                    return new DensityFunctions.MulOrAdd(type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? DensityFunctions.MulOrAdd.Type.ADD : DensityFunctions.MulOrAdd.Type.MUL, argument2, h, i, constant.value);
                }

                if (argument2 instanceof DensityFunctions.Constant) {
                    DensityFunctions.Constant constant2 = (DensityFunctions.Constant)argument2;
                    return new DensityFunctions.MulOrAdd(type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? DensityFunctions.MulOrAdd.Type.ADD : DensityFunctions.MulOrAdd.Type.MUL, argument1, h, i, constant2.value);
                }
            }

            return new DensityFunctions.Ap2(type, argument1, argument2, h, i);
        }

        DensityFunctions.TwoArgumentSimpleFunction.Type type();

        DensityFunction argument1();

        DensityFunction argument2();

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        public static enum Type implements StringRepresentable {
            ADD("add"),
            MUL("mul"),
            MIN("min"),
            MAX("max");

            final KeyDispatchDataCodec<DensityFunctions.TwoArgumentSimpleFunction> codec = DensityFunctions.doubleFunctionArgumentCodec((densityFunction, densityFunction2) -> {
                return DensityFunctions.TwoArgumentSimpleFunction.create(this, densityFunction, densityFunction2);
            }, DensityFunctions.TwoArgumentSimpleFunction::argument1, DensityFunctions.TwoArgumentSimpleFunction::argument2);
            private final String name;

            private Type(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected static record WeirdScaledSampler(DensityFunction input, DensityFunction.NoiseHolder noise, DensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper) implements DensityFunctions.TransformerWithContext {
        private static final MapCodec<DensityFunctions.WeirdScaledSampler> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(DensityFunctions.WeirdScaledSampler::input), DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.WeirdScaledSampler::noise), DensityFunctions.WeirdScaledSampler.RarityValueMapper.CODEC.fieldOf("rarity_value_mapper").forGetter(DensityFunctions.WeirdScaledSampler::rarityValueMapper)).apply(instance, DensityFunctions.WeirdScaledSampler::new);
        });
        public static final KeyDispatchDataCodec<DensityFunctions.WeirdScaledSampler> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double transform(DensityFunction.FunctionContext pos, double density) {
            double d = this.rarityValueMapper.mapper.get(density);
            return d * Math.abs(this.noise.getValue((double)pos.blockX() / d, (double)pos.blockY() / d, (double)pos.blockZ() / d));
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.WeirdScaledSampler(this.input.mapAll(visitor), visitor.visitNoise(this.noise), this.rarityValueMapper));
        }

        @Override
        public double minValue() {
            return 0.0D;
        }

        @Override
        public double maxValue() {
            return this.rarityValueMapper.maxRarity * this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        public static enum RarityValueMapper implements StringRepresentable {
            TYPE1("type_1", NoiseRouterData.QuantizedSpaghettiRarity::getSpaghettiRarity3D, 2.0D),
            TYPE2("type_2", NoiseRouterData.QuantizedSpaghettiRarity::getSphaghettiRarity2D, 3.0D);

            public static final Codec<DensityFunctions.WeirdScaledSampler.RarityValueMapper> CODEC = StringRepresentable.fromEnum(DensityFunctions.WeirdScaledSampler.RarityValueMapper::values);
            private final String name;
            final Double2DoubleFunction mapper;
            final double maxRarity;

            private RarityValueMapper(String name, Double2DoubleFunction scaleFunction, double maxValueMultiplier) {
                this.name = name;
                this.mapper = scaleFunction;
                this.maxRarity = maxValueMultiplier;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    static record YClampedGradient(int fromY, int toY, double fromValue, double toValue) implements DensityFunction.SimpleFunction {
        private static final MapCodec<DensityFunctions.YClampedGradient> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2).fieldOf("from_y").forGetter(DensityFunctions.YClampedGradient::fromY), Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2).fieldOf("to_y").forGetter(DensityFunctions.YClampedGradient::toY), DensityFunctions.NOISE_VALUE_CODEC.fieldOf("from_value").forGetter(DensityFunctions.YClampedGradient::fromValue), DensityFunctions.NOISE_VALUE_CODEC.fieldOf("to_value").forGetter(DensityFunctions.YClampedGradient::toValue)).apply(instance, DensityFunctions.YClampedGradient::new);
        });
        public static final KeyDispatchDataCodec<DensityFunctions.YClampedGradient> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext pos) {
            return Mth.clampedMap((double)pos.blockY(), (double)this.fromY, (double)this.toY, this.fromValue, this.toValue);
        }

        @Override
        public double minValue() {
            return Math.min(this.fromValue, this.toValue);
        }

        @Override
        public double maxValue() {
            return Math.max(this.fromValue, this.toValue);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }
}
