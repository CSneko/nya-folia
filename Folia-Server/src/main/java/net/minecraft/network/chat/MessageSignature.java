package net.minecraft.network.chat;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;

public record MessageSignature(byte[] bytes) {
    public net.kyori.adventure.chat.SignedMessage.Signature adventure() { return () -> this.bytes; } // Paper
    public static final Codec<MessageSignature> CODEC = ExtraCodecs.BASE64_STRING.xmap(MessageSignature::new, MessageSignature::bytes);
    public static final int BYTES = 256;

    public MessageSignature {
        Preconditions.checkState(bytes.length == 256, "Invalid message signature size"); // Paper - decompile fix
    }

    public static MessageSignature read(FriendlyByteBuf buf) {
        byte[] bs = new byte[256];
        buf.readBytes(bs);
        return new MessageSignature(bs);
    }

    public static void write(FriendlyByteBuf buf, MessageSignature signature) {
        buf.writeBytes(signature.bytes);
    }

    public boolean verify(SignatureValidator verifier, SignatureUpdater updatable) {
        return verifier.validate(updatable, this.bytes);
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(this.bytes);
    }

    @Override
    public boolean equals(Object object) {
        if (this != object) {
            if (object instanceof MessageSignature) {
                MessageSignature messageSignature = (MessageSignature)object;
                if (Arrays.equals(this.bytes, messageSignature.bytes)) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }

    @Override
    public String toString() {
        return Base64.getEncoder().encodeToString(this.bytes);
    }

    public MessageSignature.Packed pack(MessageSignatureCache storage) {
        int i = storage.pack(this);
        return i != -1 ? new MessageSignature.Packed(i) : new MessageSignature.Packed(this);
    }

    public static record Packed(int id, @Nullable MessageSignature fullSignature) {
        public static final int FULL_SIGNATURE = -1;

        public Packed(MessageSignature signature) {
            this(-1, signature);
        }

        public Packed(int id) {
            this(id, (MessageSignature)null);
        }

        public static MessageSignature.Packed read(FriendlyByteBuf buf) {
            int i = buf.readVarInt() - 1;
            return i == -1 ? new MessageSignature.Packed(MessageSignature.read(buf)) : new MessageSignature.Packed(i);
        }

        public static void write(FriendlyByteBuf buf, MessageSignature.Packed indexed) {
            buf.writeVarInt(indexed.id() + 1);
            if (indexed.fullSignature() != null) {
                MessageSignature.write(buf, indexed.fullSignature());
            }

        }

        public Optional<MessageSignature> unpack(MessageSignatureCache storage) {
            return this.fullSignature != null ? Optional.of(this.fullSignature) : Optional.ofNullable(storage.unpack(this.id));
        }
    }
}
