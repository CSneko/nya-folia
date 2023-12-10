// mc-dev import
package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class ByteArrayTag extends CollectionTag<ByteTag> {

    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<ByteArrayTag> TYPE = new TagType.VariableSize<ByteArrayTag>() {
        @Override
        public ByteArrayTag load(DataInput input, NbtAccounter tracker) throws IOException {
            return new ByteArrayTag(readAccounted(input, tracker));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter tracker) throws IOException {
            return visitor.visit(readAccounted(input, tracker));
        }

        private static byte[] readAccounted(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.accountBytes(24L);
            int i = input.readInt();
            com.google.common.base.Preconditions.checkArgument( i < 1 << 24); // Spigot

            tracker.accountBytes(1L, (long) i);
            byte[] abyte = new byte[i];

            input.readFully(abyte);
            return abyte;
        }

        @Override
        public void skip(DataInput input, NbtAccounter tracker) throws IOException {
            input.skipBytes(input.readInt() * 1);
        }

        @Override
        public String getName() {
            return "BYTE[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Byte_Array";
        }
    };
    private byte[] data;

    public ByteArrayTag(byte[] value) {
        this.data = value;
    }

    public ByteArrayTag(List<Byte> value) {
        this(ByteArrayTag.toArray(value));
    }

    private static byte[] toArray(List<Byte> list) {
        byte[] abyte = new byte[list.size()];

        for (int i = 0; i < list.size(); ++i) {
            Byte obyte = (Byte) list.get(i);

            abyte[i] = obyte == null ? 0 : obyte;
        }

        return abyte;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);
        output.write(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 24 + 1 * this.data.length;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public TagType<ByteArrayTag> getType() {
        return ByteArrayTag.TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    @Override
    public Tag copy() {
        byte[] abyte = new byte[this.data.length];

        System.arraycopy(this.data, 0, abyte, 0, this.data.length);
        return new ByteArrayTag(abyte);
    }

    public boolean equals(Object object) {
        return this == object ? true : object instanceof ByteArrayTag && Arrays.equals(this.data, ((ByteArrayTag) object).data);
    }

    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitByteArray(this);
    }

    public byte[] getAsByteArray() {
        return this.data;
    }

    public int size() {
        return this.data.length;
    }

    public ByteTag get(int i) {
        return ByteTag.valueOf(this.data[i]);
    }

    public ByteTag set(int i, ByteTag nbttagbyte) {
        byte b0 = this.data[i];

        this.data[i] = nbttagbyte.getAsByte();
        return ByteTag.valueOf(b0);
    }

    public void add(int i, ByteTag nbttagbyte) {
        this.data = ArrayUtils.add(this.data, i, nbttagbyte.getAsByte());
    }

    @Override
    public boolean setTag(int index, Tag element) {
        if (element instanceof NumericTag) {
            this.data[index] = ((NumericTag) element).getAsByte();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag element) {
        if (element instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, index, ((NumericTag) element).getAsByte());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public ByteTag remove(int i) {
        byte b0 = this.data[i];

        this.data = ArrayUtils.remove(this.data, i);
        return ByteTag.valueOf(b0);
    }

    @Override
    public byte getElementType() {
        return 1;
    }

    public void clear() {
        this.data = new byte[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
