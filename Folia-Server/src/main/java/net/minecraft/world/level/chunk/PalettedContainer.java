package net.minecraft.world.level.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {
    private static final int MIN_PALETTE_BITS = 0;
    private final PaletteResize<T> dummyPaletteResize = (newSize, added) -> {
        return 0;
    };
    public final IdMap<T> registry;
    private final T @org.jetbrains.annotations.Nullable [] presetValues; // Paper - Anti-Xray - Add preset values
    private volatile PalettedContainer.Data<T> data;
    private final PalettedContainer.Strategy strategy;
    // private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer"); // Paper - unused

    public void acquire() {
        // this.threadingDetector.checkAndLock(); // Paper - disable this - use proper synchronization
    }

    public void release() {
        // this.threadingDetector.checkAndUnlock(); // Paper - disable this
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse public static <T> Codec<PalettedContainer<T>> codecRW(IdMap<T> idList, Codec<T> entryCodec, PalettedContainer.Strategy paletteProvider, T defaultValue) { return PalettedContainer.codecRW(idList, entryCodec, paletteProvider, defaultValue, null); }
    public static <T> Codec<PalettedContainer<T>> codecRW(IdMap<T> idList, Codec<T> entryCodec, PalettedContainer.Strategy paletteProvider, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues) {
        PalettedContainerRO.Unpacker<T, PalettedContainer<T>> unpacker = (idListx, paletteProviderx, serialized) -> {
            return unpack(idListx, paletteProviderx, serialized, defaultValue, presetValues);
        };
        // Paper end
        return codec(idList, entryCodec, paletteProvider, defaultValue, unpacker);
    }

    public static <T> Codec<PalettedContainerRO<T>> codecRO(IdMap<T> idList, Codec<T> entryCodec, PalettedContainer.Strategy paletteProvider, T defaultValue) {
        PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> unpacker = (idListx, paletteProviderx, serialized) -> {
            return unpack(idListx, paletteProviderx, serialized, defaultValue, null).map((result) -> { // Paper - Anti-Xray - Add preset values
                return result;
            });
        };
        return codec(idList, entryCodec, paletteProvider, defaultValue, unpacker);
    }

    private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(IdMap<T> idList, Codec<T> entryCodec, PalettedContainer.Strategy provider, T defaultValue, PalettedContainerRO.Unpacker<T, C> reader) {
        return RecordCodecBuilder.<PalettedContainerRO.PackedData<T>>create((instance) -> { // Paper - decompile fix
            return instance.group(entryCodec.mapResult(ExtraCodecs.orElsePartial(defaultValue)).listOf().fieldOf("palette").forGetter(PalettedContainerRO.PackedData::paletteEntries), Codec.LONG_STREAM.optionalFieldOf("data").forGetter(PalettedContainerRO.PackedData::storage)).apply(instance, PalettedContainerRO.PackedData::new);
        }).comapFlatMap((serialized) -> {
            return reader.read(idList, provider, serialized);
        }, (container) -> {
            return container.pack(idList, provider);
        });
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse public PalettedContainer(IdMap<T> idList, PalettedContainer.Strategy paletteProvider, PalettedContainer.Configuration<T> dataProvider, BitStorage storage, List<T> paletteEntries) { this(idList, paletteProvider, dataProvider, storage, paletteEntries, null, null); }
    public PalettedContainer(IdMap<T> idList, PalettedContainer.Strategy paletteProvider, PalettedContainer.Configuration<T> dataProvider, BitStorage storage, List<T> paletteEntries, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        this.registry = idList;
        this.strategy = paletteProvider;
        this.data = new PalettedContainer.Data<>(dataProvider, storage, dataProvider.factory().create(dataProvider.bits(), idList, this, paletteEntries));

        if (presetValues != null && (dataProvider.factory() == PalettedContainer.Strategy.SINGLE_VALUE_PALETTE_FACTORY ? this.data.palette.valueFor(0) != defaultValue : dataProvider.factory() != PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY)) {
            // In 1.18 Mojang unfortunately removed code that already handled possible resize operations on read from disk for us
            // We readd this here but in a smarter way than it was before
            int maxSize = 1 << dataProvider.bits();

            for (T presetValue : presetValues) {
                if (this.data.palette.getSize() >= maxSize) {
                    java.util.Set<T> allValues = new java.util.HashSet<>(paletteEntries);
                    allValues.addAll(Arrays.asList(presetValues));
                    int newBits = Mth.ceillog2(allValues.size());

                    if (newBits > dataProvider.bits()) {
                        this.onResize(newBits, null);
                    }

                    break;
                }

                this.data.palette.idFor(presetValue);
            }
        }
        // Paper end
    }

    // Paper start - Anti-Xray - Add preset values
    private PalettedContainer(IdMap<T> idList, PalettedContainer.Strategy paletteProvider, PalettedContainer.Data<T> data, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        // Paper end
        this.registry = idList;
        this.strategy = paletteProvider;
        this.data = data;
    }

    // Paper start - Anti-Xray - Add preset values
    @Deprecated @io.papermc.paper.annotation.DoNotUse public PalettedContainer(IdMap<T> idList, T object, PalettedContainer.Strategy paletteProvider) { this(idList, object, paletteProvider, null); }
    public PalettedContainer(IdMap<T> idList, T object, PalettedContainer.Strategy paletteProvider, T @org.jetbrains.annotations.Nullable [] presetValues) {
        this.presetValues = presetValues;
        // Paper end
        this.strategy = paletteProvider;
        this.registry = idList;
        this.data = this.createOrReuseData((PalettedContainer.Data<T>)null, 0);
        this.data.palette.idFor(object);
    }

    private PalettedContainer.Data<T> createOrReuseData(@Nullable PalettedContainer.Data<T> previousData, int bits) {
        PalettedContainer.Configuration<T> configuration = this.strategy.getConfiguration(this.registry, bits);
        return previousData != null && configuration.equals(previousData.configuration()) ? previousData : configuration.createData(this.registry, this, this.strategy.size());
    }

    @Override
    public synchronized int onResize(int newBits, T object) { // Paper - synchronize
        PalettedContainer.Data<T> data = this.data;

        // Paper start - Anti-Xray - Add preset values
        if (this.presetValues != null && object != null && data.configuration().factory() == PalettedContainer.Strategy.SINGLE_VALUE_PALETTE_FACTORY) {
            int duplicates = 0;
            List<T> presetValues = Arrays.asList(this.presetValues);
            duplicates += presetValues.contains(object) ? 1 : 0;
            duplicates += presetValues.contains(data.palette.valueFor(0)) ? 1 : 0;
            newBits = Mth.ceillog2((1 << this.strategy.calculateBitsForSerialization(this.registry, 1 << newBits)) + presetValues.size() - duplicates);
        }

        PalettedContainer.Data<T> data2 = this.createOrReuseData(data, newBits);
        data2.copyFrom(data.palette, data.storage);
        this.data = data2;
        this.addPresetValues();
        return object == null ? -1 : data2.palette.idFor(object);
        // Paper end
    }

    // Paper start - Anti-Xray - Add preset values
    private void addPresetValues() {
        if (this.presetValues != null && this.data.configuration().factory() != PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
            for (T presetValue : this.presetValues) {
                this.data.palette.idFor(presetValue);
            }
        }
    }
    // Paper end

    public T getAndSet(int x, int y, int z, T value) {
        this.acquire();

        Object var5;
        try {
            var5 = this.getAndSet(this.strategy.getIndex(x, y, z), value);
        } finally {
            this.release();
        }

        return (T)var5;
    }

    public T getAndSetUnchecked(int x, int y, int z, T value) {
        return this.getAndSet(this.strategy.getIndex(x, y, z), value);
    }

    private synchronized T getAndSet(int index, T value) { // Paper - synchronize
        int i = this.data.palette.idFor(value);
        int j = this.data.storage.getAndSet(index, i);
        return this.data.palette.valueFor(j);
    }

    public void set(int x, int y, int z, T value) {
        this.acquire();

        try {
            this.set(this.strategy.getIndex(x, y, z), value);
        } finally {
            this.release();
        }

    }

    private synchronized void set(int index, T value) { // Paper - synchronize
        int i = this.data.palette.idFor(value);
        this.data.storage.set(index, i);
    }

    @Override
    public T get(int x, int y, int z) {
        return this.get(this.strategy.getIndex(x, y, z));
    }

    public T get(int index) { // Paper - public
        PalettedContainer.Data<T> data = this.data;
        return data.palette.valueFor(data.storage.get(index));
    }

    @Override
    public void getAll(Consumer<T> action) {
        Palette<T> palette = this.data.palette();
        IntSet intSet = new IntArraySet();
        this.data.storage.getAll(intSet::add);
        intSet.forEach((id) -> {
            action.accept(palette.valueFor(id));
        });
    }

    public synchronized void read(FriendlyByteBuf buf) { // Paper - synchronize
        this.acquire();

        try {
            int i = buf.readByte();
            PalettedContainer.Data<T> data = this.createOrReuseData(this.data, i);
            data.palette.read(buf);
            buf.readLongArray(data.storage.getRaw());
            this.data = data;
            this.addPresetValues(); // Paper - Anti-Xray - Add preset values (inefficient, but this isn't used by the server)
        } finally {
            this.release();
        }

    }

    // Paper start - Anti-Xray - Add chunk packet info
    @Override
    @Deprecated @io.papermc.paper.annotation.DoNotUse public void write(FriendlyByteBuf buf) { this.write(buf, null, 0); }
    @Override
    public synchronized void write(FriendlyByteBuf buf, @Nullable com.destroystokyo.paper.antixray.ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex) { // Paper - synchronize
        this.acquire();

        try {
            this.data.write(buf, chunkPacketInfo, chunkSectionIndex);

            if (chunkPacketInfo != null) {
                chunkPacketInfo.setPresetValues(chunkSectionIndex, this.presetValues);
            }
            // Paper end
        } finally {
            this.release();
        }

    }

    private static <T> DataResult<PalettedContainer<T>> unpack(IdMap<T> idList, PalettedContainer.Strategy paletteProvider, PalettedContainerRO.PackedData<T> serialized, T defaultValue, T @org.jetbrains.annotations.Nullable [] presetValues) { // Paper - Anti-Xray - Add preset values
        List<T> list = serialized.paletteEntries();
        int i = paletteProvider.size();
        int j = paletteProvider.calculateBitsForSerialization(idList, list.size());
        PalettedContainer.Configuration<T> configuration = paletteProvider.getConfiguration(idList, j);
        BitStorage bitStorage;
        if (j == 0) {
            bitStorage = new ZeroBitStorage(i);
        } else {
            Optional<LongStream> optional = serialized.storage();
            if (optional.isEmpty()) {
                return DataResult.error(() -> {
                    return "Missing values for non-zero storage";
                });
            }

            long[] ls = optional.get().toArray();

            try {
                if (configuration.factory() == PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
                    Palette<T> palette = new HashMapPalette<>(idList, j, (id, value) -> {
                        return 0;
                    }, list);
                    SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, ls);
                    int[] is = new int[i];
                    simpleBitStorage.unpack(is);
                    swapPalette(is, (id) -> {
                        return idList.getId(palette.valueFor(id));
                    });
                    bitStorage = new SimpleBitStorage(configuration.bits(), i, is);
                } else {
                    bitStorage = new SimpleBitStorage(configuration.bits(), i, ls);
                }
            } catch (SimpleBitStorage.InitializationException var13) {
                return DataResult.error(() -> {
                    return "Failed to read PalettedContainer: " + var13.getMessage();
                });
            }
        }

        return DataResult.success(new PalettedContainer<>(idList, paletteProvider, configuration, bitStorage, list, defaultValue, presetValues)); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public synchronized PalettedContainerRO.PackedData<T> pack(IdMap<T> idList, PalettedContainer.Strategy paletteProvider) { // Paper - synchronize
        this.acquire();

        PalettedContainerRO.PackedData var12;
        try {
            HashMapPalette<T> hashMapPalette = new HashMapPalette<>(idList, this.data.storage.getBits(), this.dummyPaletteResize);
            int i = paletteProvider.size();
            int[] is = new int[i];
            this.data.storage.unpack(is);
            swapPalette(is, (id) -> {
                return hashMapPalette.idFor(this.data.palette.valueFor(id));
            });
            int j = paletteProvider.calculateBitsForSerialization(idList, hashMapPalette.getSize());
            Optional<LongStream> optional;
            if (j != 0) {
                SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, is);
                optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
            } else {
                optional = Optional.empty();
            }

            var12 = new PalettedContainerRO.PackedData<>(hashMapPalette.getEntries(), optional);
        } finally {
            this.release();
        }

        return var12;
    }

    private static <T> void swapPalette(int[] is, IntUnaryOperator applier) {
        int i = -1;
        int j = -1;

        for(int k = 0; k < is.length; ++k) {
            int l = is[k];
            if (l != i) {
                i = l;
                j = applier.applyAsInt(l);
            }

            is[k] = j;
        }

    }

    @Override
    public int getSerializedSize() {
        return this.data.getSerializedSize();
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        return this.data.palette.maybeHas(predicate);
    }

    public PalettedContainer<T> copy() {
        return new PalettedContainer<>(this.registry, this.strategy, this.data.copy(), this.presetValues); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public PalettedContainer<T> recreate() {
        return new PalettedContainer<>(this.registry, this.data.palette.valueFor(0), this.strategy, this.presetValues); // Paper - Anti-Xray - Add preset values
    }

    @Override
    public void count(PalettedContainer.CountConsumer<T> counter) {
        if (this.data.palette.getSize() == 1) {
            counter.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
        } else {
            Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
            this.data.storage.getAll((key) -> {
                int2IntOpenHashMap.addTo(key, 1);
            });
            int2IntOpenHashMap.int2IntEntrySet().forEach((entry) -> {
                counter.accept(this.data.palette.valueFor(entry.getIntKey()), entry.getIntValue());
            });
        }
    }

    static record Configuration<T>(Palette.Factory factory, int bits) {
        public PalettedContainer.Data<T> createData(IdMap<T> idList, PaletteResize<T> listener, int size) {
            BitStorage bitStorage = (BitStorage)(this.bits == 0 ? new ZeroBitStorage(size) : new SimpleBitStorage(this.bits, size));
            Palette<T> palette = this.factory.create(this.bits, idList, listener, List.of());
            return new PalettedContainer.Data<>(this, bitStorage, palette);
        }
    }

    // Paper start
    public void forEachLocation(PalettedContainer.CountConsumer<T> consumer) {
        this.data.storage.forEach((int location, int data) -> {
            consumer.accept(this.data.palette.valueFor(data), location);
        });
    }
    // Paper end

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T object, int count);
    }

    static record Data<T>(PalettedContainer.Configuration<T> configuration, BitStorage storage, Palette<T> palette) {
        public void copyFrom(Palette<T> palette, BitStorage storage) {
            for(int i = 0; i < storage.getSize(); ++i) {
                T object = palette.valueFor(storage.get(i));
                this.storage.set(i, this.palette.idFor(object));
            }

        }

        public int getSerializedSize() {
            return 1 + this.palette.getSerializedSize() + VarInt.getByteSize(this.storage.getRaw().length) + this.storage.getRaw().length * 8;
        }

        // Paper start - Anti-Xray - Add chunk packet info
        public void write(FriendlyByteBuf buf, @Nullable com.destroystokyo.paper.antixray.ChunkPacketInfo<T> chunkPacketInfo, int chunkSectionIndex) {
            buf.writeByte(this.storage.getBits());
            this.palette.write(buf);

            if (chunkPacketInfo != null) {
                chunkPacketInfo.setBits(chunkSectionIndex, this.configuration.bits());
                chunkPacketInfo.setPalette(chunkSectionIndex, this.palette);
                chunkPacketInfo.setIndex(chunkSectionIndex, buf.writerIndex() + VarInt.getByteSize(this.storage.getRaw().length));
            }
            // Paper end

            buf.writeLongArray(this.storage.getRaw());
        }

        public PalettedContainer.Data<T> copy() {
            return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy());
        }
    }

    public abstract static class Strategy {
        public static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
        public static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
        public static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
        static final Palette.Factory GLOBAL_PALETTE_FACTORY = GlobalPalette::create;
        public static final PalettedContainer.Strategy SECTION_STATES = new PalettedContainer.Strategy(4) {
            @Override
            public <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> idList, int bits) {
                PalettedContainer.Configuration var10000;
                switch (bits) {
                    case 0:
                        var10000 = new PalettedContainer.Configuration(SINGLE_VALUE_PALETTE_FACTORY, bits);
                        break;
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        var10000 = new PalettedContainer.Configuration(LINEAR_PALETTE_FACTORY, 4);
                        break;
                    case 5:
                    case 6:
                    case 7:
                    case 8:
                        var10000 = new PalettedContainer.Configuration(HASHMAP_PALETTE_FACTORY, bits);
                        break;
                    default:
                        var10000 = new PalettedContainer.Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(idList.size()));
                }

                return var10000;
            }
        };
        public static final PalettedContainer.Strategy SECTION_BIOMES = new PalettedContainer.Strategy(2) {
            @Override
            public <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> idList, int bits) {
                PalettedContainer.Configuration var10000;
                switch (bits) {
                    case 0:
                        var10000 = new PalettedContainer.Configuration(SINGLE_VALUE_PALETTE_FACTORY, bits);
                        break;
                    case 1:
                    case 2:
                    case 3:
                        var10000 = new PalettedContainer.Configuration(LINEAR_PALETTE_FACTORY, bits);
                        break;
                    default:
                        var10000 = new PalettedContainer.Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(idList.size()));
                }

                return var10000;
            }
        };
        private final int sizeBits;

        Strategy(int edgeBits) {
            this.sizeBits = edgeBits;
        }

        public int size() {
            return 1 << this.sizeBits * 3;
        }

        public int getIndex(int x, int y, int z) {
            return (y << this.sizeBits | z) << this.sizeBits | x;
        }

        public abstract <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> idList, int bits);

        <A> int calculateBitsForSerialization(IdMap<A> idList, int size) {
            int i = Mth.ceillog2(size);
            PalettedContainer.Configuration<A> configuration = this.getConfiguration(idList, i);
            return configuration.factory() == GLOBAL_PALETTE_FACTORY ? i : configuration.bits();
        }
    }
}
