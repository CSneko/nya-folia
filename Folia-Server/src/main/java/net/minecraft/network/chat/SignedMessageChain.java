package net.minecraft.network.chat;

import com.mojang.logging.LogUtils;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Signer;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.slf4j.Logger;

public class SignedMessageChain {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private SignedMessageLink nextLink;

    public SignedMessageChain(UUID sender, UUID sessionId) {
        this.nextLink = SignedMessageLink.root(sender, sessionId);
    }

    public SignedMessageChain.Encoder encoder(Signer signer) {
        return (body) -> {
            SignedMessageLink signedMessageLink = this.advanceLink();
            return signedMessageLink == null ? null : new MessageSignature(signer.sign((SignatureUpdater)((updatable) -> {
                PlayerChatMessage.updateSignature(updatable, signedMessageLink, body);
            })));
        };
    }

    public SignedMessageChain.Decoder decoder(ProfilePublicKey playerPublicKey) {
        SignatureValidator signatureValidator = playerPublicKey.createSignatureValidator();
        return (signature, body) -> {
            SignedMessageLink signedMessageLink = this.advanceLink();
            if (signedMessageLink == null) {
                throw new SignedMessageChain.DecodeException(Component.translatable("chat.disabled.chain_broken"), false);
            } else if (playerPublicKey.data().hasExpired()) {
                throw new SignedMessageChain.DecodeException(Component.translatable("chat.disabled.expiredProfileKey"), false);
            } else {
                PlayerChatMessage playerChatMessage = new PlayerChatMessage(signedMessageLink, signature, body, (Component)null, FilterMask.PASS_THROUGH);
                if (!playerChatMessage.verify(signatureValidator)) {
                    throw new SignedMessageChain.DecodeException(Component.translatable("multiplayer.disconnect.unsigned_chat"), true, org.bukkit.event.player.PlayerKickEvent.Cause.UNSIGNED_CHAT); // Paper - kick event causes
                } else {
                    if (playerChatMessage.hasExpiredServer(Instant.now())) {
                        LOGGER.warn("Received expired chat: '{}'. Is the client/server system time unsynchronized?", (Object)body.content());
                    }

                    return playerChatMessage;
                }
            }
        };
    }

    @Nullable
    private SignedMessageLink advanceLink() {
        SignedMessageLink signedMessageLink = this.nextLink;
        if (signedMessageLink != null) {
            this.nextLink = signedMessageLink.advance();
        }

        return signedMessageLink;
    }

    public static class DecodeException extends ThrowingComponent {
        private final boolean shouldDisconnect;
        public final org.bukkit.event.player.PlayerKickEvent.Cause kickCause; // Paper

        public DecodeException(Component message, boolean shouldDisconnect) {
            // Paper start
            this(message, shouldDisconnect, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
        }
        public DecodeException(Component message, boolean shouldDisconnect, org.bukkit.event.player.PlayerKickEvent.Cause kickCause) {
            // Paper end
            super(message);
            this.shouldDisconnect = shouldDisconnect;
            this.kickCause = kickCause; // Paper
        }

        public boolean shouldDisconnect() {
            return this.shouldDisconnect;
        }
    }

    @FunctionalInterface
    public interface Decoder {
        SignedMessageChain.Decoder REJECT_ALL = (signature, body) -> {
            throw new SignedMessageChain.DecodeException(Component.translatable("chat.disabled.missingProfileKey"), false);
        };

        static SignedMessageChain.Decoder unsigned(UUID uuid) {
            return (signature, body) -> {
                return PlayerChatMessage.unsigned(uuid, body.content());
            };
        }

        PlayerChatMessage unpack(@Nullable MessageSignature signature, SignedMessageBody body) throws SignedMessageChain.DecodeException;
    }

    @FunctionalInterface
    public interface Encoder {
        SignedMessageChain.Encoder UNSIGNED = (body) -> {
            return null;
        };

        @Nullable
        MessageSignature pack(SignedMessageBody body);
    }
}
