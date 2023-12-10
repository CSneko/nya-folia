package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

public class HashMapPalette<T> implements Palette<T> {
    private final IdMap<T> registry;
    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;

    public HashMapPalette(IdMap<T> idList, int bits, PaletteResize<T> listener, List<T> entries) {
        this(idList, bits, listener);
        entries.forEach(this.values::add);
    }

    public HashMapPalette(IdMap<T> idList, int indexBits, PaletteResize<T> listener) {
        this(idList, indexBits, listener, CrudeIncrementalIntIdentityHashBiMap.create((1 << indexBits) + 1)); // Paper - Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap
    }

    private HashMapPalette(IdMap<T> idList, int indexBits, PaletteResize<T> listener, CrudeIncrementalIntIdentityHashBiMap<T> map) {
        this.registry = idList;
        this.bits = indexBits;
        this.resizeHandler = listener;
        this.values = map;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> idList, PaletteResize<A> listener, List<A> entries) {
        return new HashMapPalette<>(idList, bits, listener, entries);
    }

    @Override
    public int idFor(T object) {
        int i = this.values.getId(object);
        if (i == -1) {
            // Paper start - Avoid unnecessary resize operation in CrudeIncrementalIntIdentityHashBiMap and optimize
            // We use size() instead of the result from add(K)
            // This avoids adding another object unnecessarily
            // Without this change, + 2 would be required in the constructor
            if (this.values.size() >= 1 << this.bits) {
                i = this.resizeHandler.onResize(this.bits + 1, object);
            } else {
                i = this.values.add(object);
            }
            // Paper end
        }

        return i;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        for(int i = 0; i < this.getSize(); ++i) {
            if (predicate.test(this.values.byId(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int id) {
        T object = this.values.byId(id);
        if (object == null) {
            throw new MissingPaletteEntryException(id);
        } else {
            return object;
        }
    }

    @Override
    public void read(FriendlyByteBuf buf) {
        this.values.clear();
        int i = buf.readVarInt();

        for(int j = 0; j < i; ++j) {
            this.values.add(this.registry.byIdOrThrow(buf.readVarInt()));
        }

    }

    @Override
    public void write(FriendlyByteBuf buf) {
        int i = this.getSize();
        buf.writeVarInt(i);

        for(int j = 0; j < i; ++j) {
            buf.writeVarInt(this.registry.getId(this.values.byId(j)));
        }

    }

    @Override
    public int getSerializedSize() {
        int i = VarInt.getByteSize(this.getSize());

        for(int j = 0; j < this.getSize(); ++j) {
            i += VarInt.getByteSize(this.registry.getId(this.values.byId(j)));
        }

        return i;
    }

    public List<T> getEntries() {
        ArrayList<T> arrayList = new ArrayList<>();
        this.values.iterator().forEachRemaining(arrayList::add);
        return arrayList;
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public Palette<T> copy() {
        return new HashMapPalette<>(this.registry, this.bits, this.resizeHandler, this.values.copy());
    }
}
