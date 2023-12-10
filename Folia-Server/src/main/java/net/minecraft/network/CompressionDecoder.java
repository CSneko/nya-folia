package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class CompressionDecoder extends ByteToMessageDecoder {
    public static final int MAXIMUM_COMPRESSED_LENGTH = 2097152;
    public static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8388608;
    private final Inflater inflater;
    private final com.velocitypowered.natives.compression.VelocityCompressor compressor; // Paper
    private int threshold;
    private boolean validateDecompressed;

    // Paper start
    public CompressionDecoder(int compressionThreshold, boolean rejectsBadPackets) {
        this(null, compressionThreshold, rejectsBadPackets);
    }
    public CompressionDecoder(com.velocitypowered.natives.compression.VelocityCompressor compressor, int compressionThreshold, boolean rejectsBadPackets) {
        this.threshold = compressionThreshold;
        this.validateDecompressed = rejectsBadPackets;
        this.inflater = compressor == null ? new Inflater() : null;
        this.compressor = compressor;
        // Paper end
    }

    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (byteBuf.readableBytes() != 0) {
            int i = VarInt.read(byteBuf);
            if (i == 0) {
                list.add(byteBuf.readBytes(byteBuf.readableBytes()));
            } else {
                if (this.validateDecompressed) {
                    if (i < this.threshold) {
                        throw new DecoderException("Badly compressed packet - size of " + i + " is below server threshold of " + this.threshold);
                    }

                    if (i > 8388608) {
                        throw new DecoderException("Badly compressed packet - size of " + i + " is larger than protocol maximum of 8388608");
                    }
                }

                if (inflater != null) { // Paper - use velocity compression - fallback to vanilla inflater
                this.setupInflaterInput(byteBuf);
                ByteBuf byteBuf2 = this.inflate(channelHandlerContext, i);
                this.inflater.reset();
                list.add(byteBuf2);
                return; // Paper - use velocity compression
                } // Paper - use velocity compression

                // Paper start - use velocity compression
                int claimedUncompressedSize = i; // OBFHELPER
                ByteBuf compatibleIn = com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible(channelHandlerContext.alloc(), this.compressor, byteBuf);
                ByteBuf uncompressed = com.velocitypowered.natives.util.MoreByteBufUtils.preferredBuffer(channelHandlerContext.alloc(), this.compressor, claimedUncompressedSize);
                try {
                    this.compressor.inflate(compatibleIn, uncompressed, claimedUncompressedSize);
                    list.add(uncompressed);
                    byteBuf.clear();
                } catch (Exception e) {
                    uncompressed.release();
                    throw e;
                } finally {
                    compatibleIn.release();
                }
                // Paper end - use velocity compression
            }
        }
    }

    // Paper start
    @Override
    public void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        if (this.compressor != null) {
            this.compressor.close();
        }
    }
    // Paper end

    private void setupInflaterInput(ByteBuf buf) {
        ByteBuffer byteBuffer;
        if (buf.nioBufferCount() > 0) {
            byteBuffer = buf.nioBuffer();
            buf.skipBytes(buf.readableBytes());
        } else {
            byteBuffer = ByteBuffer.allocateDirect(buf.readableBytes());
            buf.readBytes(byteBuffer);
            byteBuffer.flip();
        }

        this.inflater.setInput(byteBuffer);
    }

    private ByteBuf inflate(ChannelHandlerContext context, int expectedSize) throws DataFormatException {
        ByteBuf byteBuf = context.alloc().directBuffer(expectedSize);

        try {
            ByteBuffer byteBuffer = byteBuf.internalNioBuffer(0, expectedSize);
            int i = byteBuffer.position();
            this.inflater.inflate(byteBuffer);
            int j = byteBuffer.position() - i;
            if (j != expectedSize) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload " + j + " is does not match declared size " + expectedSize);
            } else {
                byteBuf.writerIndex(byteBuf.writerIndex() + j);
                return byteBuf;
            }
        } catch (Exception var7) {
            byteBuf.release();
            throw var7;
        }
    }

    public void setThreshold(int compressionThreshold, boolean rejectsBadPackets) {
        this.threshold = compressionThreshold;
        this.validateDecompressed = rejectsBadPackets;
    }
}
