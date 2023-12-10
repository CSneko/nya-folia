package net.minecraft.commands.arguments;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class EntityArgument implements ArgumentType<EntitySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "@e", "@e[type=foo]", "dd12be42-52a9-4a91-a8a1-11c01849e498");
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_ENTITY = new SimpleCommandExceptionType(Component.translatable("argument.entity.toomany"));
    public static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER = new SimpleCommandExceptionType(Component.translatable("argument.player.toomany"));
    public static final SimpleCommandExceptionType ERROR_ONLY_PLAYERS_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.player.entities"));
    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND = new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND = new SimpleCommandExceptionType(Component.translatable("argument.entity.notfound.player"));
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.not_allowed"));
    final boolean single;
    final boolean playersOnly;

    protected EntityArgument(boolean singleTarget, boolean playersOnly) {
        this.single = singleTarget;
        this.playersOnly = playersOnly;
    }

    public static EntityArgument entity() {
        return new EntityArgument(true, false);
    }

    public static Entity getEntity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findSingleEntity((CommandSourceStack) context.getSource());
    }

    public static EntityArgument entities() {
        return new EntityArgument(false, false);
    }

    public static Collection<? extends Entity> getEntities(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Collection<? extends Entity> collection = EntityArgument.getOptionalEntities(context, name);

        if (collection.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else {
            return collection;
        }
    }

    public static Collection<? extends Entity> getOptionalEntities(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findEntities((CommandSourceStack) context.getSource());
    }

    public static Collection<ServerPlayer> getOptionalPlayers(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findPlayers((CommandSourceStack) context.getSource());
    }

    public static EntityArgument player() {
        return new EntityArgument(true, true);
    }

    public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return ((EntitySelector) context.getArgument(name, EntitySelector.class)).findSinglePlayer((CommandSourceStack) context.getSource());
    }

    public static EntityArgument players() {
        return new EntityArgument(false, true);
    }

    public static Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        List<ServerPlayer> list = ((EntitySelector) context.getArgument(name, EntitySelector.class)).findPlayers((CommandSourceStack) context.getSource());

        if (list.isEmpty()) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return list;
        }
    }

    public EntitySelector parse(StringReader stringreader) throws CommandSyntaxException {
        // CraftBukkit start
        return this.parse(stringreader, false);
    }

    public EntitySelector parse(StringReader stringreader, boolean overridePermissions) throws CommandSyntaxException {
        // CraftBukkit end
        boolean flag = false;
        EntitySelectorParser argumentparserselector = new EntitySelectorParser(stringreader);
        EntitySelector entityselector = argumentparserselector.parse(overridePermissions); // CraftBukkit

        if (entityselector.getMaxResults() > 1 && this.single) {
            if (this.playersOnly) {
                stringreader.setCursor(0);
                throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.createWithContext(stringreader);
            } else {
                stringreader.setCursor(0);
                throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.createWithContext(stringreader);
            }
        } else if (entityselector.includesEntities() && this.playersOnly && !entityselector.isSelfSelector()) {
            stringreader.setCursor(0);
            throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(stringreader);
        } else {
            return entityselector;
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandcontext, SuggestionsBuilder suggestionsbuilder) {
        Object object = commandcontext.getSource();

        if (object instanceof SharedSuggestionProvider) {
            SharedSuggestionProvider icompletionprovider = (SharedSuggestionProvider) object;
            StringReader stringreader = new StringReader(suggestionsbuilder.getInput());

            stringreader.setCursor(suggestionsbuilder.getStart());
            // Paper start
            final boolean permission = object instanceof CommandSourceStack stack
                    ? stack.bypassSelectorPermissions || stack.hasPermission(2, "minecraft.command.selector")
                    : icompletionprovider.hasPermission(2);
            EntitySelectorParser argumentparserselector = new EntitySelectorParser(stringreader, permission, true); // Paper
            // Paper end

            try {
                argumentparserselector.parse();
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }

            return argumentparserselector.fillSuggestions(suggestionsbuilder, (suggestionsbuilder1) -> {
                // Paper start
                final Collection<String> collection;
                if (icompletionprovider instanceof CommandSourceStack commandSourceStack && commandSourceStack.getEntity() instanceof ServerPlayer sourcePlayer) {
                    collection = new java.util.ArrayList<>();
                    for (final ServerPlayer player : commandSourceStack.getServer().getPlayerList().getPlayers()) {
                        if (sourcePlayer.getBukkitEntity().canSee(player.getBukkitEntity())) {
                            collection.add(player.getGameProfile().getName());
                        }
                    }
                } else {
                    collection = icompletionprovider.getOnlinePlayerNames();
                }
                // Paper end
                Iterable<String> iterable = this.playersOnly ? collection : Iterables.concat(collection, icompletionprovider.getSelectedEntities());

                SharedSuggestionProvider.suggest((Iterable) iterable, suggestionsbuilder1);
            });
        } else {
            return Suggestions.empty();
        }
    }

    public Collection<String> getExamples() {
        return EntityArgument.EXAMPLES;
    }

    public static class Info implements ArgumentTypeInfo<EntityArgument, EntityArgument.Info.Template> {

        private static final byte FLAG_SINGLE = 1;
        private static final byte FLAG_PLAYERS_ONLY = 2;

        public Info() {}

        public void serializeToNetwork(EntityArgument.Info.Template properties, FriendlyByteBuf buf) {
            int i = 0;

            if (properties.single) {
                i |= 1;
            }

            if (properties.playersOnly) {
                i |= 2;
            }

            buf.writeByte(i);
        }

        @Override
        public EntityArgument.Info.Template deserializeFromNetwork(FriendlyByteBuf buf) {
            byte b0 = buf.readByte();

            return new EntityArgument.Info.Template((b0 & 1) != 0, (b0 & 2) != 0);
        }

        public void serializeToJson(EntityArgument.Info.Template properties, JsonObject json) {
            json.addProperty("amount", properties.single ? "single" : "multiple");
            json.addProperty("type", properties.playersOnly ? "players" : "entities");
        }

        public EntityArgument.Info.Template unpack(EntityArgument argumentType) {
            return new EntityArgument.Info.Template(argumentType.single, argumentType.playersOnly);
        }

        public final class Template implements ArgumentTypeInfo.Template<EntityArgument> {

            final boolean single;
            final boolean playersOnly;

            Template(boolean flag, boolean flag1) {
                this.single = flag;
                this.playersOnly = flag1;
            }

            @Override
            public EntityArgument instantiate(CommandBuildContext commandRegistryAccess) {
                return new EntityArgument(this.single, this.playersOnly);
            }

            @Override
            public ArgumentTypeInfo<EntityArgument, ?> type() {
                return Info.this;
            }
        }
    }
}
