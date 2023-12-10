package net.minecraft.util;

import java.util.function.IntConsumer;

public interface BitStorage {
    int getAndSet(int index, int value);

    void set(int index, int value);

    int get(int index);

    long[] getRaw();

    int getSize();

    int getBits();

    void getAll(IntConsumer action);

    void unpack(int[] out);

    BitStorage copy();

    // Paper start
    void forEach(DataBitConsumer consumer);

    @FunctionalInterface
    interface DataBitConsumer {

        void accept(int location, int data);

    }
    // Paper end
}
