package net.minecraft.network.protocol.login;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;

public record ServerboundCustomQueryAnswerPacket(int transactionId, @Nullable CustomQueryAnswerPayload payload) implements Packet<ServerLoginPacketListener> {
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    public static ServerboundCustomQueryAnswerPacket read(FriendlyByteBuf buf) {
        int i = buf.readVarInt();
        return new ServerboundCustomQueryAnswerPacket(i, readPayload(i, buf));
    }

    private static CustomQueryAnswerPayload readPayload(int queryId, FriendlyByteBuf buf) {
        // Paper start - MC Utils - default query payloads
        FriendlyByteBuf buffer = buf.readNullable((buf2) -> {
            int i = buf2.readableBytes();
            if (i >= 0 && i <= MAX_PAYLOAD_SIZE) {
                return new FriendlyByteBuf(buf2.readBytes(i));
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + MAX_PAYLOAD_SIZE + " bytes");
            }
        });
        return buffer == null ? null : new net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket.QueryAnswerPayload(buffer);
        // Paper end - MC Utils - default query payloads
    }

    private static CustomQueryAnswerPayload readUnknownPayload(FriendlyByteBuf buf) {
        int i = buf.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buf.skipBytes(i);
            return DiscardedQueryAnswerPayload.INSTANCE;
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.transactionId);
        buf.writeNullable(this.payload, (bufx, response) -> {
            response.write(bufx);
        });
    }

    @Override
    public void handle(ServerLoginPacketListener listener) {
        listener.handleCustomQueryPacket(this);
    }

    // Paper start - MC Utils - default query payloads
    public static final class QueryAnswerPayload implements CustomQueryAnswerPayload {

        public final FriendlyByteBuf buffer;

        public QueryAnswerPayload(final net.minecraft.network.FriendlyByteBuf buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(final net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeBytes(this.buffer.copy());
        }
    }
    // Paper end - MC Utils - default query payloads

}
