package net.minecraft.util;

import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Validate;

public class SimpleBitStorage implements BitStorage {
    private static final int[] MAGIC = new int[]{-1, -1, 0, Integer.MIN_VALUE, 0, 0, 1431655765, 1431655765, 0, Integer.MIN_VALUE, 0, 1, 858993459, 858993459, 0, 715827882, 715827882, 0, 613566756, 613566756, 0, Integer.MIN_VALUE, 0, 2, 477218588, 477218588, 0, 429496729, 429496729, 0, 390451572, 390451572, 0, 357913941, 357913941, 0, 330382099, 330382099, 0, 306783378, 306783378, 0, 286331153, 286331153, 0, Integer.MIN_VALUE, 0, 3, 252645135, 252645135, 0, 238609294, 238609294, 0, 226050910, 226050910, 0, 214748364, 214748364, 0, 204522252, 204522252, 0, 195225786, 195225786, 0, 186737708, 186737708, 0, 178956970, 178956970, 0, 171798691, 171798691, 0, 165191049, 165191049, 0, 159072862, 159072862, 0, 153391689, 153391689, 0, 148102320, 148102320, 0, 143165576, 143165576, 0, 138547332, 138547332, 0, Integer.MIN_VALUE, 0, 4, 130150524, 130150524, 0, 126322567, 126322567, 0, 122713351, 122713351, 0, 119304647, 119304647, 0, 116080197, 116080197, 0, 113025455, 113025455, 0, 110127366, 110127366, 0, 107374182, 107374182, 0, 104755299, 104755299, 0, 102261126, 102261126, 0, 99882960, 99882960, 0, 97612893, 97612893, 0, 95443717, 95443717, 0, 93368854, 93368854, 0, 91382282, 91382282, 0, 89478485, 89478485, 0, 87652393, 87652393, 0, 85899345, 85899345, 0, 84215045, 84215045, 0, 82595524, 82595524, 0, 81037118, 81037118, 0, 79536431, 79536431, 0, 78090314, 78090314, 0, 76695844, 76695844, 0, 75350303, 75350303, 0, 74051160, 74051160, 0, 72796055, 72796055, 0, 71582788, 71582788, 0, 70409299, 70409299, 0, 69273666, 69273666, 0, 68174084, 68174084, 0, Integer.MIN_VALUE, 0, 5};
    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;
    private final int valuesPerLong;
    private final int divideMul; private final long divideMulUnsigned; // Paper - referenced in b(int) with 2 Integer.toUnsignedLong calls
    private final int divideAdd; private final long divideAddUnsigned; // Paper
    private final int divideShift;

    public SimpleBitStorage(int elementBits, int size, int[] data) {
        this(elementBits, size);
        int i = 0;

        int j;
        for(j = 0; j <= size - this.valuesPerLong; j += this.valuesPerLong) {
            long l = 0L;

            for(int k = this.valuesPerLong - 1; k >= 0; --k) {
                l <<= elementBits;
                l |= (long)data[j + k] & this.mask;
            }

            this.data[i++] = l;
        }

        int m = size - j;
        if (m > 0) {
            long n = 0L;

            for(int o = m - 1; o >= 0; --o) {
                n <<= elementBits;
                n |= (long)data[j + o] & this.mask;
            }

            this.data[i] = n;
        }

    }

    public SimpleBitStorage(int elementBits, int size) {
        this(elementBits, size, (long[])null);
    }

    public SimpleBitStorage(int elementBits, int size, @Nullable long[] data) {
        Validate.inclusiveBetween(1L, 32L, (long)elementBits);
        this.size = size;
        this.bits = elementBits;
        this.mask = (1L << elementBits) - 1L;
        this.valuesPerLong = (char)(64 / elementBits);
        int i = 3 * (this.valuesPerLong - 1);
        this.divideMul = MAGIC[i + 0]; this.divideMulUnsigned = Integer.toUnsignedLong(this.divideMul); // Paper
        this.divideAdd = MAGIC[i + 1]; this.divideAddUnsigned = Integer.toUnsignedLong(this.divideAdd); // Paper
        this.divideShift = MAGIC[i + 2];
        int j = (size + this.valuesPerLong - 1) / this.valuesPerLong;
        if (data != null) {
            if (data.length != j) {
                throw new SimpleBitStorage.InitializationException("Invalid length given for storage, got: " + data.length + " but expected: " + j);
            }

            this.data = data;
        } else {
            this.data = new long[j];
        }

    }

    private int cellIndex(int index) {
        //long l = Integer.toUnsignedLong(this.divideMul); // Paper
        //long m = Integer.toUnsignedLong(this.divideAdd); // Paper
        return (int) ((long) index * this.divideMulUnsigned + this.divideAddUnsigned >> 32 >> this.divideShift); // Paper
    }

    @Override
    public final int getAndSet(int index, int value) { // Paper - make final for inline
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper
        //Validate.inclusiveBetween(0L, this.mask, (long)value); // Paper
        int i = this.cellIndex(index);
        long l = this.data[i];
        int j = (index - i * this.valuesPerLong) * this.bits;
        int k = (int)(l >> j & this.mask);
        this.data[i] = l & ~(this.mask << j) | ((long)value & this.mask) << j;
        return k;
    }

    @Override
    public final void set(int index, int value) { // Paper - make final for inline
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper
        //Validate.inclusiveBetween(0L, this.mask, (long)value); // Paper
        int i = this.cellIndex(index);
        long l = this.data[i];
        int j = (index - i * this.valuesPerLong) * this.bits;
        this.data[i] = l & ~(this.mask << j) | ((long)value & this.mask) << j;
    }

    @Override
    public final int get(int index) { // Paper - make final for inline
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        int i = this.cellIndex(index);
        long l = this.data[i];
        int j = (index - i * this.valuesPerLong) * this.bits;
        return (int)(l >> j & this.mask);
    }

    @Override
    public long[] getRaw() {
        return this.data;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getBits() {
        return this.bits;
    }

    // Paper start
    @Override
    public final void forEach(DataBitConsumer consumer) {
        int i = 0;
        long[] along = this.data;
        int j = along.length;

        for (int k = 0; k < j; ++k) {
            long l = along[k];

            for (int i1 = 0; i1 < this.valuesPerLong; ++i1) {
                consumer.accept(i, (int) (l & this.mask));
                l >>= this.bits;
                ++i;
                if (i >= this.size) {
                    return;
                }
            }
        }
    }
    // Paper end

    @Override
    public void getAll(IntConsumer action) {
        int i = 0;

        for(long l : this.data) {
            for(int j = 0; j < this.valuesPerLong; ++j) {
                action.accept((int)(l & this.mask));
                l >>= this.bits;
                ++i;
                if (i >= this.size) {
                    return;
                }
            }
        }

    }

    @Override
    public void unpack(int[] out) {
        int i = this.data.length;
        int j = 0;

        for(int k = 0; k < i - 1; ++k) {
            long l = this.data[k];

            for(int m = 0; m < this.valuesPerLong; ++m) {
                out[j + m] = (int)(l & this.mask);
                l >>= this.bits;
            }

            j += this.valuesPerLong;
        }

        int n = this.size - j;
        if (n > 0) {
            long o = this.data[i - 1];

            for(int p = 0; p < n; ++p) {
                out[j + p] = (int)(o & this.mask);
                o >>= this.bits;
            }
        }

    }

    @Override
    public BitStorage copy() {
        return new SimpleBitStorage(this.bits, this.size, (long[])this.data.clone());
    }

    public static class InitializationException extends RuntimeException {
        InitializationException(String message) {
            super(message);
        }
    }
}
