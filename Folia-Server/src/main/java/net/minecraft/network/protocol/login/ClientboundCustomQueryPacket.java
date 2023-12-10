package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundCustomQueryPacket(int transactionId, CustomQueryPayload payload) implements Packet<ClientLoginPacketListener> {
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    public ClientboundCustomQueryPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), readPayload(buf.readResourceLocation(), buf));
    }

    private static CustomQueryPayload readPayload(ResourceLocation id, FriendlyByteBuf buf) {
        return readUnknownPayload(id, buf);
    }

    private static DiscardedQueryPayload readUnknownPayload(ResourceLocation id, FriendlyByteBuf buf) {
        int i = buf.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buf.skipBytes(i);
            return new DiscardedQueryPayload(id);
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.transactionId);
        buf.writeResourceLocation(this.payload.id());
        this.payload.write(buf);
    }

    @Override
    public void handle(ClientLoginPacketListener listener) {
        listener.handleCustomQuery(this);
    }

    // Paper start - MC Utils - default query payloads
    public static record PlayerInfoChannelPayload(ResourceLocation id, FriendlyByteBuf buffer) implements CustomQueryPayload {

        @Override
        public void write(final FriendlyByteBuf buf) {
            buf.writeBytes(this.buffer.copy());
        }
    }
    // Paper end - MC Utils - default query payloads
}
