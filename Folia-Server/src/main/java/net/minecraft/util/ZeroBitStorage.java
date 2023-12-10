package net.minecraft.util;

import java.util.Arrays;
import java.util.function.IntConsumer;
import org.apache.commons.lang3.Validate;

public class ZeroBitStorage implements BitStorage {
    public static final long[] RAW = new long[0];
    private final int size;

    public ZeroBitStorage(int size) {
        this.size = size;
    }

    @Override
    public final int getAndSet(int index, int value) { // Paper - make final for inline
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper
        //Validate.inclusiveBetween(0L, 0L, (long)value); // Paper
        return 0;
    }

    @Override
    public final void set(int index, int value) { // Paper - make final for inline
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper
        //Validate.inclusiveBetween(0L, 0L, (long)value); // Paper
    }

    @Override
    public final int get(int index) { // Paper - make final for inline
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper
        return 0;
    }

    @Override
    public long[] getRaw() {
        return RAW;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getBits() {
        return 0;
    }

    // Paper start
    @Override
    public void forEach(DataBitConsumer consumer) {
        for(int i = 0; i < this.size; ++i) {
            consumer.accept(i, 0);
        }
    }
    // Paper end

    @Override
    public void getAll(IntConsumer action) {
        for(int i = 0; i < this.size; ++i) {
            action.accept(0);
        }

    }

    @Override
    public void unpack(int[] out) {
        Arrays.fill(out, 0, this.size, 0);
    }

    @Override
    public BitStorage copy() {
        return this;
    }
}
