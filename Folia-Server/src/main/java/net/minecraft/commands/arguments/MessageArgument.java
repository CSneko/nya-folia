package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;

public class MessageArgument implements SignedArgument<MessageArgument.Message> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Hello world!", "foo", "@e", "Hello @p :)");

    public static MessageArgument message() {
        return new MessageArgument();
    }

    public static Component getMessage(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        MessageArgument.Message message = context.getArgument(name, MessageArgument.Message.class);
        return message.resolveComponent(context.getSource());
    }

    public static void resolveChatMessage(CommandContext<CommandSourceStack> context, String name, Consumer<PlayerChatMessage> callback) throws CommandSyntaxException {
        MessageArgument.Message message = context.getArgument(name, MessageArgument.Message.class);
        CommandSourceStack commandSourceStack = context.getSource();
        Component component = message.resolveComponent(commandSourceStack);
        CommandSigningContext commandSigningContext = commandSourceStack.getSigningContext();
        PlayerChatMessage playerChatMessage = commandSigningContext.getArgument(name);
        if (playerChatMessage != null) {
            resolveSignedMessage(callback, commandSourceStack, playerChatMessage.withUnsignedContent(component));
        } else {
            resolveDisguisedMessage(callback, commandSourceStack, PlayerChatMessage.system(message.text).withUnsignedContent(component));
        }

    }

    private static void resolveSignedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        MinecraftServer minecraftServer = source.getServer();
        CompletableFuture<FilteredText> completableFuture = filterPlainText(source, message);
        CompletableFuture<ChatDecorator.Result> componentFuture = minecraftServer.getChatDecorator().decorate(source.getPlayer(), source, message.decoratedContent()); // Paper
        source.getChatMessageChainer().append((executor) -> {
            return CompletableFuture.allOf(completableFuture, componentFuture).thenAcceptAsync((filtered) -> {
                PlayerChatMessage playerChatMessage2 = message.withUnsignedContent(componentFuture.join().component()).filter(completableFuture.join().mask()); // Paper
                callback.accept(playerChatMessage2);
            }, executor);
        });
    }

    private static void resolveDisguisedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        ChatDecorator chatDecorator = source.getServer().getChatDecorator();
        // Paper start
        source.getChatMessageChainer().append(executor -> {
            CompletableFuture<ChatDecorator.Result> componentFuture = chatDecorator.decorate(source.getPlayer(), source, message.decoratedContent());
            return componentFuture.thenAcceptAsync((result) -> {
                callback.accept(message.withUnsignedContent(result.component()));
            }, executor);
        });
        // Paper end
    }

    private static CompletableFuture<FilteredText> filterPlainText(CommandSourceStack source, PlayerChatMessage message) {
        ServerPlayer serverPlayer = source.getPlayer();
        return serverPlayer != null && message.hasSignatureFrom(serverPlayer.getUUID()) ? serverPlayer.getTextFilter().processStreamMessage(message.signedContent()) : CompletableFuture.completedFuture(FilteredText.passThrough(message.signedContent()));
    }

    public MessageArgument.Message parse(StringReader stringReader) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(stringReader, true);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Message {
        final String text;
        private final MessageArgument.Part[] parts;

        public Message(String contents, MessageArgument.Part[] selectors) {
            this.text = contents;
            this.parts = selectors;
        }

        public String getText() {
            return this.text;
        }

        public MessageArgument.Part[] getParts() {
            return this.parts;
        }

        Component resolveComponent(CommandSourceStack source) throws CommandSyntaxException {
            return this.toComponent(source, source.hasPermission(2));
        }

        public Component toComponent(CommandSourceStack source, boolean canUseSelectors) throws CommandSyntaxException {
            if (this.parts.length != 0 && canUseSelectors) {
                MutableComponent mutableComponent = Component.literal(this.text.substring(0, this.parts[0].getStart()));
                int i = this.parts[0].getStart();

                for(MessageArgument.Part part : this.parts) {
                    Component component = part.toComponent(source);
                    if (i < part.getStart()) {
                        mutableComponent.append(this.text.substring(i, part.getStart()));
                    }

                    if (component != null) {
                        mutableComponent.append(component);
                    }

                    i = part.getEnd();
                }

                if (i < this.text.length()) {
                    mutableComponent.append(this.text.substring(i));
                }

                return mutableComponent;
            } else {
                return Component.literal(this.text);
            }
        }

        public static MessageArgument.Message parseText(StringReader reader, boolean canUseSelectors) throws CommandSyntaxException {
            String string = reader.getString().substring(reader.getCursor(), reader.getTotalLength());
            if (!canUseSelectors) {
                reader.setCursor(reader.getTotalLength());
                return new MessageArgument.Message(string, new MessageArgument.Part[0]);
            } else {
                List<MessageArgument.Part> list = Lists.newArrayList();
                int i = reader.getCursor();

                while(true) {
                    int j;
                    EntitySelector entitySelector;
                    while(true) {
                        if (!reader.canRead()) {
                            return new MessageArgument.Message(string, list.toArray(new MessageArgument.Part[0]));
                        }

                        if (reader.peek() == '@') {
                            j = reader.getCursor();

                            try {
                                EntitySelectorParser entitySelectorParser = new EntitySelectorParser(reader);
                                entitySelector = entitySelectorParser.parse();
                                break;
                            } catch (CommandSyntaxException var8) {
                                if (var8.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE && var8.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) {
                                    throw var8;
                                }

                                reader.setCursor(j + 1);
                            }
                        } else {
                            reader.skip();
                        }
                    }

                    list.add(new MessageArgument.Part(j - i, reader.getCursor() - i, entitySelector));
                }
            }
        }
    }

    public static class Part {
        private final int start;
        private final int end;
        private final EntitySelector selector;

        public Part(int start, int end, EntitySelector selector) {
            this.start = start;
            this.end = end;
            this.selector = selector;
        }

        public int getStart() {
            return this.start;
        }

        public int getEnd() {
            return this.end;
        }

        public EntitySelector getSelector() {
            return this.selector;
        }

        @Nullable
        public Component toComponent(CommandSourceStack source) throws CommandSyntaxException {
            return EntitySelector.joinNames(this.selector.findEntities(source));
        }
    }
}
