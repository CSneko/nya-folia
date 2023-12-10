package net.minecraft.server.rcon;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NetworkDataOutputStream {
    private final ByteArrayOutputStream outputStream;
    private final DataOutputStream dataOutputStream;

    public NetworkDataOutputStream(int size) {
        this.outputStream = new ByteArrayOutputStream(size);
        this.dataOutputStream = new DataOutputStream(this.outputStream);
    }

    public void writeBytes(byte[] values) throws IOException {
        this.dataOutputStream.write(values, 0, values.length);
    }

    public void writeString(String value) throws IOException {
        this.dataOutputStream.writeBytes(value);
        this.dataOutputStream.write(0);
    }

    // Paper start - unchecked exception variant to use in Stream API
    public void writeStringUnchecked(String string) {
        try {
            writeString(string);
        } catch (IOException e) {
            com.destroystokyo.paper.util.SneakyThrow.sneaky(e);
        }
    }
    // Paper end

    public void write(int value) throws IOException {
        this.dataOutputStream.write(value);
    }

    public void writeShort(short value) throws IOException {
        this.dataOutputStream.writeShort(Short.reverseBytes(value));
    }

    public void writeInt(int value) throws IOException {
        this.dataOutputStream.writeInt(Integer.reverseBytes(value));
    }

    public void writeFloat(float value) throws IOException {
        this.dataOutputStream.writeInt(Integer.reverseBytes(Float.floatToIntBits(value)));
    }

    public byte[] toByteArray() {
        return this.outputStream.toByteArray();
    }

    public void reset() {
        this.outputStream.reset();
    }
}
