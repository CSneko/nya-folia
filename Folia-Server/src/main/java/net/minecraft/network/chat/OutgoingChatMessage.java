package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {
    Component content();

    void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params);

    // Paper start
    default void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params, @javax.annotation.Nullable Component unsigned) {
        this.sendToPlayer(sender, filterMaskEnabled, params);
    }
    // Paper end

    static OutgoingChatMessage create(PlayerChatMessage message) {
        return (OutgoingChatMessage)(message.isSystem() ? new OutgoingChatMessage.Disguised(message.decoratedContent()) : new OutgoingChatMessage.Player(message));
    }

    public static record Disguised(Component content) implements OutgoingChatMessage {
        @Override
        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params) {
           // Paper start
            this.sendToPlayer(sender, filterMaskEnabled, params, null);
        }
        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params, @javax.annotation.Nullable Component unsigned) {
            sender.connection.sendDisguisedChatMessage(unsigned != null ? unsigned : this.content, params);
            // Paper end
        }
    }

    public static record Player(PlayerChatMessage message) implements OutgoingChatMessage {
        @Override
        public Component content() {
            return this.message.decoratedContent();
        }

        @Override
        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params) {
            // Paper start
            this.sendToPlayer(sender, filterMaskEnabled, params, null);
        }
        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params, @javax.annotation.Nullable Component unsigned) {
            // Paper end
            PlayerChatMessage playerChatMessage = this.message.filter(filterMaskEnabled);
            playerChatMessage = unsigned != null ? playerChatMessage.withUnsignedContent(unsigned) : playerChatMessage; // Paper
            if (!playerChatMessage.isFullyFiltered()) {
                sender.connection.sendPlayerChatMessage(playerChatMessage, params);
            }

        }
    }
}
