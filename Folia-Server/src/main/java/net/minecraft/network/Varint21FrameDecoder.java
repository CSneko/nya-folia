package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;
import javax.annotation.Nullable;

public class Varint21FrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_VARINT21_BYTES = 3;
    private final ByteBuf helperBuf = Unpooled.directBuffer(3);
    @Nullable
    private final BandwidthDebugMonitor monitor;

    public Varint21FrameDecoder(@Nullable BandwidthDebugMonitor packetSizeLogger) {
        this.monitor = packetSizeLogger;
    }

    protected void handlerRemoved0(ChannelHandlerContext channelHandlerContext) {
        this.helperBuf.release();
    }

    private static boolean copyVarint(ByteBuf source, ByteBuf sizeBuf) {
        for(int i = 0; i < 3; ++i) {
            if (!source.isReadable()) {
                return false;
            }

            byte b = source.readByte();
            sizeBuf.writeByte(b);
            if (!VarInt.hasContinuationBit(b)) {
                return true;
            }
        }

        throw new CorruptedFrameException("length wider than 21-bit");
    }

    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        // Paper start - if channel is not active just discard the packet
        if (!channelHandlerContext.channel().isActive()) {
            byteBuf.skipBytes(byteBuf.readableBytes());
            return;
        }
        // Paper end - if channel is not active just discard the packet
        byteBuf.markReaderIndex();
        this.helperBuf.clear();
        if (!copyVarint(byteBuf, this.helperBuf)) {
            byteBuf.resetReaderIndex();
        } else {
            int i = VarInt.read(this.helperBuf);
            if (byteBuf.readableBytes() < i) {
                byteBuf.resetReaderIndex();
            } else {
                if (this.monitor != null) {
                    this.monitor.onReceive(i + VarInt.getByteSize(i));
                }

                list.add(byteBuf.readBytes(i));
            }
        }
    }
}
