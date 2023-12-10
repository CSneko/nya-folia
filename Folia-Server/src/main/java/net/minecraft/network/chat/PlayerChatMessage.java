package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;

// Paper start
public record PlayerChatMessage(SignedMessageLink link, @Nullable MessageSignature signature, SignedMessageBody signedBody, @Nullable Component unsignedContent, FilterMask filterMask, @Nullable net.minecraft.network.chat.ChatDecorator.Result result) {
    public PlayerChatMessage(SignedMessageLink link, @Nullable MessageSignature signature, SignedMessageBody signedBody, @Nullable Component unsignedContent, FilterMask filterMask) {
        this(link, signature, signedBody, unsignedContent, filterMask, null);
    }
    public PlayerChatMessage withResult(net.minecraft.network.chat.ChatDecorator.Result result) {
        final PlayerChatMessage msg = this.withUnsignedContent(result.component());
        return new PlayerChatMessage(msg.link, msg.signature, msg.signedBody, msg.unsignedContent, msg.filterMask, result);
    }
    public net.minecraft.network.chat.ChatDecorator.Result requireResult() {
        return Objects.requireNonNull(this.result, "Requires a decoration result to be set here");
    }
    public final class AdventureView implements net.kyori.adventure.chat.SignedMessage {
        private AdventureView() {
        }
        @Override
        public @org.jetbrains.annotations.NotNull Instant timestamp() {
            return PlayerChatMessage.this.timeStamp();
        }
        @Override
        public long salt() {
            return PlayerChatMessage.this.salt();
        }
        @Override
        public @org.jetbrains.annotations.Nullable Signature signature() {
            return PlayerChatMessage.this.signature == null ? null : PlayerChatMessage.this.signature.adventure();
        }
        @Override
        public net.kyori.adventure.text.@org.jetbrains.annotations.Nullable Component unsignedContent() {
            return PlayerChatMessage.this.unsignedContent() == null ? null : io.papermc.paper.adventure.PaperAdventure.asAdventure(PlayerChatMessage.this.unsignedContent());
        }
        @Override
        public @org.jetbrains.annotations.NotNull String message() {
            return PlayerChatMessage.this.signedContent();
        }
        @Override
        public @org.jetbrains.annotations.NotNull net.kyori.adventure.identity.Identity identity() {
            return net.kyori.adventure.identity.Identity.identity(PlayerChatMessage.this.sender());
        }
        public PlayerChatMessage playerChatMessage() {
            return PlayerChatMessage.this;
        }
    }
    public AdventureView adventureView() {
        return new AdventureView();
    }
    // Paper end
    public static final MapCodec<PlayerChatMessage> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(SignedMessageLink.CODEC.fieldOf("link").forGetter(PlayerChatMessage::link), MessageSignature.CODEC.optionalFieldOf("signature").forGetter((message) -> {
            return Optional.ofNullable(message.signature);
        }), SignedMessageBody.MAP_CODEC.forGetter(PlayerChatMessage::signedBody), ExtraCodecs.COMPONENT.optionalFieldOf("unsigned_content").forGetter((message) -> {
            return Optional.ofNullable(message.unsignedContent);
        }), FilterMask.CODEC.optionalFieldOf("filter_mask", FilterMask.PASS_THROUGH).forGetter(PlayerChatMessage::filterMask)).apply(instance, (link, signature, signedBody, unsignedContent, filterMask) -> {
            return new PlayerChatMessage(link, signature.orElse((MessageSignature)null), signedBody, unsignedContent.orElse((Component)null), filterMask);
        });
    });
    private static final UUID SYSTEM_SENDER = Util.NIL_UUID;
    public static final Duration MESSAGE_EXPIRES_AFTER_SERVER = Duration.ofMinutes(5L);
    public static final Duration MESSAGE_EXPIRES_AFTER_CLIENT = MESSAGE_EXPIRES_AFTER_SERVER.plus(Duration.ofMinutes(2L));

    public static PlayerChatMessage system(String content) {
        return unsigned(SYSTEM_SENDER, content);
    }

    public static PlayerChatMessage unsigned(UUID sender, String content) {
        SignedMessageBody signedMessageBody = SignedMessageBody.unsigned(content);
        SignedMessageLink signedMessageLink = SignedMessageLink.unsigned(sender);
        return new PlayerChatMessage(signedMessageLink, (MessageSignature)null, signedMessageBody, (Component)null, FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage withUnsignedContent(Component unsignedContent) {
        Component component = !(unsignedContent instanceof io.papermc.paper.adventure.AdventureComponent advComponent ? advComponent.deepConverted() : unsignedContent).equals(Component.literal(this.signedContent())) ? unsignedContent : null; // Paper
        return new PlayerChatMessage(this.link, this.signature, this.signedBody, component, this.filterMask);
    }

    public PlayerChatMessage removeUnsignedContent() {
        return this.unsignedContent != null ? new PlayerChatMessage(this.link, this.signature, this.signedBody, (Component)null, this.filterMask) : this;
    }

    public PlayerChatMessage filter(FilterMask filterMask) {
        return this.filterMask.equals(filterMask) ? this : new PlayerChatMessage(this.link, this.signature, this.signedBody, this.unsignedContent, filterMask);
    }

    public PlayerChatMessage filter(boolean enabled) {
        return this.filter(enabled ? this.filterMask : FilterMask.PASS_THROUGH);
    }

    public static void updateSignature(SignatureUpdater.Output updater, SignedMessageLink link, SignedMessageBody body) throws SignatureException {
        updater.update(Ints.toByteArray(1));
        link.updateSignature(updater);
        body.updateSignature(updater);
    }

    public boolean verify(SignatureValidator verifier) {
        return this.signature != null && this.signature.verify(verifier, (updater) -> {
            updateSignature(updater, this.link, this.signedBody);
        });
    }

    public String signedContent() {
        return this.signedBody.content();
    }

    public Component decoratedContent() {
        return Objects.requireNonNullElseGet(this.unsignedContent, () -> {
            return Component.literal(this.signedContent());
        });
    }

    public Instant timeStamp() {
        return this.signedBody.timeStamp();
    }

    public long salt() {
        return this.signedBody.salt();
    }

    public boolean hasExpiredServer(Instant currentTime) {
        return currentTime.isAfter(this.timeStamp().plus(MESSAGE_EXPIRES_AFTER_SERVER));
    }

    public boolean hasExpiredClient(Instant currentTime) {
        return currentTime.isAfter(this.timeStamp().plus(MESSAGE_EXPIRES_AFTER_CLIENT));
    }

    public UUID sender() {
        return this.link.sender();
    }

    public boolean isSystem() {
        return this.sender().equals(SYSTEM_SENDER);
    }

    public boolean hasSignature() {
        return this.signature != null;
    }

    public boolean hasSignatureFrom(UUID sender) {
        return this.hasSignature() && this.link.sender().equals(sender);
    }

    public boolean isFullyFiltered() {
        return this.filterMask.isFullyFiltered();
    }
}
