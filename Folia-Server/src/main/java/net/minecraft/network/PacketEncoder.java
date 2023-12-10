package net.minecraft.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.io.IOException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

public class PacketEncoder extends MessageToByteEncoder<Packet<?>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final AttributeKey<ConnectionProtocol.CodecData<?>> codecKey;

    public PacketEncoder(AttributeKey<ConnectionProtocol.CodecData<?>> protocolKey) {
        this.codecKey = protocolKey;
    }

    protected void encode(ChannelHandlerContext channelHandlerContext, Packet<?> packet, ByteBuf byteBuf) throws Exception {
        Attribute<ConnectionProtocol.CodecData<?>> attribute = channelHandlerContext.channel().attr(this.codecKey);
        ConnectionProtocol.CodecData<?> codecData = attribute.get();
        if (codecData == null) {
            throw new RuntimeException("ConnectionProtocol unknown: " + packet);
        } else {
            int i = codecData.packetId(packet);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(Connection.PACKET_SENT_MARKER, "OUT: [{}:{}] {}", codecData.protocol().id(), i, packet.getClass().getName());
            }

            if (i == -1) {
                throw new IOException("Can't serialize unregistered packet");
            } else {
                FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(byteBuf);
                friendlyByteBuf.writeVarInt(i);
                friendlyByteBuf.adventure$locale = channelHandlerContext.channel().attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).get(); // Paper

                try {
                    int j = friendlyByteBuf.writerIndex();
                    packet.write(friendlyByteBuf);
                    int k = friendlyByteBuf.writerIndex() - j;
                    if (false && k > 8388608) { // Paper - disable
                        throw new IllegalArgumentException("Packet too big (is " + k + ", should be less than 8388608): " + packet);
                    }

                    JvmProfiler.INSTANCE.onPacketSent(codecData.protocol(), i, channelHandlerContext.channel().remoteAddress(), k);
                } catch (Throwable var13) {
                    // Paper start - Give proper error message
                    String packetName = io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(packet.getClass().getName());
                    if (packetName.contains(".")) {
                        packetName = packetName.substring(packetName.lastIndexOf(".") + 1);
                    }

                    LOGGER.error("Packet encoding of packet {} (ID: {}) threw (skippable? {})", packetName, i, packet.isSkippable(), var13);
                    // Paper end
                    if (packet.isSkippable()) {
                        throw new SkipPacketException(var13);
                    }

                    throw var13;
                } finally {
                    // Paper start
                    int packetLength = friendlyByteBuf.readableBytes();
                    if (packetLength > MAX_PACKET_SIZE) {
                        throw new PacketTooLargeException(packet, this.codecKey, packetLength);
                    }
                    // Paper end
                    ProtocolSwapHandler.swapProtocolIfNeeded(attribute, packet);
                }

            }
        }
    }

    // Paper start
    private static int MAX_PACKET_SIZE = 8388608;

    public static class PacketTooLargeException extends RuntimeException {
        private final Packet<?> packet;
        public final AttributeKey<ConnectionProtocol.CodecData<?>> codecKey;

        PacketTooLargeException(Packet<?> packet, AttributeKey<ConnectionProtocol.CodecData<?>> codecKey, int packetLength) {
            super("PacketTooLarge - " + packet.getClass().getSimpleName() + " is " + packetLength + ". Max is " + MAX_PACKET_SIZE);
            this.packet = packet;
            this.codecKey = codecKey;
        }

        public Packet<?> getPacket() {
            return this.packet;
        }
    }
    // Paper end
}
