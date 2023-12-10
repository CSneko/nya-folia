package net.minecraft.network.protocol.common;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundCustomPayloadPacket(CustomPacketPayload payload) implements Packet<ServerCommonPacketListener> {

    private static final int MAX_PAYLOAD_SIZE = 32767;
    private static final Map<ResourceLocation, FriendlyByteBuf.Reader<? extends CustomPacketPayload>> KNOWN_TYPES = ImmutableMap.<ResourceLocation, FriendlyByteBuf.Reader<? extends CustomPacketPayload>>builder().build(); // CraftBukkit - no special handling

    public ServerboundCustomPayloadPacket(FriendlyByteBuf buf) {
        this(readPayload(buf.readResourceLocation(), buf));
    }

    private static CustomPacketPayload readPayload(ResourceLocation id, FriendlyByteBuf buf) {
        FriendlyByteBuf.Reader<? extends CustomPacketPayload> packetdataserializer_a = (FriendlyByteBuf.Reader) ServerboundCustomPayloadPacket.KNOWN_TYPES.get(id);

        return (CustomPacketPayload) (packetdataserializer_a != null ? (CustomPacketPayload) packetdataserializer_a.apply(buf) : readUnknownPayload(id, buf));
    }

    private static UnknownPayload readUnknownPayload(ResourceLocation minecraftkey, FriendlyByteBuf packetdataserializer) { // CraftBukkit
        int i = packetdataserializer.readableBytes();

        if (i >= 0 && i <= 32767) {
            // CraftBukkit start
            return new UnknownPayload(minecraftkey, packetdataserializer.readBytes(i));
            // CraftBukkit end
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 32767 bytes");
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.payload.id());
        this.payload.write(buf);
    }

    public void handle(ServerCommonPacketListener listener) {
        listener.handleCustomPayload(this);
    }

    // CraftBukkit start
    public record UnknownPayload(ResourceLocation id, io.netty.buffer.ByteBuf data) implements CustomPacketPayload {

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBytes(this.data);
        }
    }
    // CraftBukkit end
}
