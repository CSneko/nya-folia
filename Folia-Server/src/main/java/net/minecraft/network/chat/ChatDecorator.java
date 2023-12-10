package net.minecraft.network.chat;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.CompletableFuture; // Paper

@FunctionalInterface
public interface ChatDecorator {
    ChatDecorator PLAIN = (sender, message) -> {
        return CompletableFuture.completedFuture(message); // Paper
    };

    @io.papermc.paper.annotation.DoNotUse @Deprecated // Paper
    CompletableFuture<Component> decorate(@Nullable ServerPlayer sender, Component message); // Paper - make a completable future again

    // Paper start
    default CompletableFuture<Result> decorate(@Nullable ServerPlayer sender, @Nullable net.minecraft.commands.CommandSourceStack commandSourceStack, Component message) {
        throw new UnsupportedOperationException("Must override this implementation");
    }

    static ChatDecorator create(ImprovedChatDecorator delegate) {
        return new ChatDecorator() {
            @Override
            public CompletableFuture<Component> decorate(@Nullable ServerPlayer sender, Component message) {
                return this.decorate(sender, null, message).thenApply(Result::component);
            }

            @Override
            public CompletableFuture<Result> decorate(@Nullable ServerPlayer sender, @Nullable net.minecraft.commands.CommandSourceStack commandSourceStack, Component message) {
                return delegate.decorate(sender, commandSourceStack, message);
            }
        };
    }

    @FunctionalInterface
    interface ImprovedChatDecorator {
        CompletableFuture<Result> decorate(@Nullable ServerPlayer sender, @Nullable net.minecraft.commands.CommandSourceStack commandSourceStack, Component message);
    }

    interface Result {
        boolean hasNoFormatting();

        Component component();

        MessagePair message();

        boolean modernized();
    }

    record MessagePair(net.kyori.adventure.text.Component component, String legacyMessage) { }

    record LegacyResult(Component component, String format, MessagePair message, boolean hasNoFormatting, boolean modernized) implements Result {
        public LegacyResult(net.kyori.adventure.text.Component component, String format, MessagePair message, boolean hasNoFormatting, boolean modernified) {
            this(io.papermc.paper.adventure.PaperAdventure.asVanilla(component), format, message, hasNoFormatting, modernified);
        }
        public LegacyResult {
            component = component instanceof io.papermc.paper.adventure.AdventureComponent adventureComponent ? adventureComponent.deepConverted() : component;
        }
    }

    record ModernResult(Component component, boolean hasNoFormatting, boolean modernized) implements Result {
        public ModernResult(net.kyori.adventure.text.Component component, boolean hasNoFormatting, boolean modernized) {
            this(io.papermc.paper.adventure.PaperAdventure.asVanilla(component), hasNoFormatting, modernized);
        }

        @Override
        public MessagePair message() {
            final net.kyori.adventure.text.Component adventureComponent = io.papermc.paper.adventure.PaperAdventure.WRAPPER_AWARE_SERIALIZER.deserialize(this.component);
            return new MessagePair(adventureComponent, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(adventureComponent));
        }
    }
    // Paper end
}
