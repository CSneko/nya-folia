// mc-dev import
package net.minecraft.world.level.chunk;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.VisibleForDebug;

public class DataLayer {

    public static final int LAYER_COUNT = 16;
    public static final int LAYER_SIZE = 128;
    public static final int SIZE = 2048;
    private static final int NIBBLE_SIZE = 4;
    @Nullable
    protected byte[] data;
    private int defaultValue;

    public DataLayer() {
        this(0);
    }

    public DataLayer(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    public DataLayer(byte[] bytes) {
        this.data = bytes;
        this.defaultValue = 0;
        if (bytes.length != 2048) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException("DataLayer should be 2048 bytes not: " + bytes.length));
        }
    }

    public int get(int x, int y, int z) {
        return this.get(DataLayer.getIndex(x, y, z));
    }

    public void set(int x, int y, int z, int value) {
        this.set(DataLayer.getIndex(x, y, z), value);
    }

    private static int getIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private int get(int index) {
        if (this.data == null) {
            return this.defaultValue;
        } else {
            int j = DataLayer.getByteIndex(index);
            int k = DataLayer.getNibbleIndex(index);

            return this.data[j] >> 4 * k & 15;
        }
    }

    private void set(int index, int value) {
        byte[] abyte = this.getData();
        int k = DataLayer.getByteIndex(index);
        int l = DataLayer.getNibbleIndex(index);
        int i1 = ~(15 << 4 * l);
        int j1 = (value & 15) << 4 * l;

        abyte[k] = (byte) (abyte[k] & i1 | j1);
    }

    private static int getNibbleIndex(int i) {
        return i & 1;
    }

    private static int getByteIndex(int i) {
        return i >> 1;
    }

    public void fill(int defaultValue) {
        this.defaultValue = defaultValue;
        this.data = null;
    }

    private static byte packFilled(int value) {
        byte b0 = (byte) value;

        for (int j = 4; j < 8; j += 4) {
            b0 = (byte) (b0 | value << j);
        }

        return b0;
    }

    public byte[] getData() {
        if (this.data == null) {
            this.data = new byte[2048];
            if (this.defaultValue != 0) {
                Arrays.fill(this.data, DataLayer.packFilled(this.defaultValue));
            }
        }

        return this.data;
    }

    public DataLayer copy() {
        return this.data == null ? new DataLayer(this.defaultValue) : new DataLayer((byte[]) this.data.clone());
    }

    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < 4096; ++i) {
            stringbuilder.append(Integer.toHexString(this.get(i)));
            if ((i & 15) == 15) {
                stringbuilder.append("\n");
            }

            if ((i & 255) == 255) {
                stringbuilder.append("\n");
            }
        }

        return stringbuilder.toString();
    }

    @VisibleForDebug
    public String layerToString(int unused) {
        StringBuilder stringbuilder = new StringBuilder();

        for (int j = 0; j < 256; ++j) {
            stringbuilder.append(Integer.toHexString(this.get(j)));
            if ((j & 15) == 15) {
                stringbuilder.append("\n");
            }
        }

        return stringbuilder.toString();
    }

    public boolean isDefinitelyHomogenous() {
        return this.data == null;
    }

    public boolean isDefinitelyFilledWith(int expectedDefaultValue) {
        return this.data == null && this.defaultValue == expectedDefaultValue;
    }

    public boolean isEmpty() {
        return this.data == null && this.defaultValue == 0;
    }
}
