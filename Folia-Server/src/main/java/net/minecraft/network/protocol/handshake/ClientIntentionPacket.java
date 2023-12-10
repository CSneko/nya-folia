// mc-dev import
package net.minecraft.network.protocol.handshake;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public record ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention) implements Packet<ServerHandshakePacketListener> {

    private static final int MAX_HOST_LENGTH = 255;

    public ClientIntentionPacket(FriendlyByteBuf buf) {
        // Spigot - increase max hostName length
        this(buf.readVarInt(), buf.readUtf(Short.MAX_VALUE), buf.readUnsignedShort(), ClientIntent.byId(buf.readVarInt()));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.protocolVersion);
        buf.writeUtf(this.hostName);
        buf.writeShort(this.port);
        buf.writeVarInt(this.intention.id());
    }

    public void handle(ServerHandshakePacketListener listener) {
        listener.handleIntention(this);
    }

    @Override
    public ConnectionProtocol nextProtocol() {
        return this.intention.protocol();
    }
}
