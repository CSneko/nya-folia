package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import javax.crypto.Cipher;
import java.util.List;

public class CipherEncoder extends io.netty.handler.codec.MessageToMessageEncoder<ByteBuf> { // Paper - change superclass
    private final com.velocitypowered.natives.encryption.VelocityCipher cipher; // Paper

    public CipherEncoder(com.velocitypowered.natives.encryption.VelocityCipher cipher) {  // Paper
        this.cipher = cipher;  // Paper
    }

    protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        // Paper start
        ByteBuf compatible = com.velocitypowered.natives.util.MoreByteBufUtils.ensureCompatible(channelHandlerContext.alloc(), cipher, byteBuf);
        try {
            cipher.process(compatible);
            list.add(compatible);
        } catch (Exception e) {
            compatible.release(); // compatible will never be used if we throw an exception
            throw e;
        }
        // Paper end
    }

    // Paper start
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cipher.close();
    }
    // Paper end
}
