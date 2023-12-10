package net.minecraft.commands;

import com.google.common.collect.Maps;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.AdvancementCommands;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.server.commands.BanIpCommands;
import net.minecraft.server.commands.BanListCommands;
import net.minecraft.server.commands.BanPlayerCommands;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.commands.DamageCommand;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.commands.DeOpCommands;
import net.minecraft.server.commands.DebugCommand;
import net.minecraft.server.commands.DebugConfigCommand;
import net.minecraft.server.commands.DefaultGameModeCommands;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.server.commands.EmoteCommands;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.commands.HelpCommand;
import net.minecraft.server.commands.ItemCommands;
import net.minecraft.server.commands.JfrCommand;
import net.minecraft.server.commands.KickCommand;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.commands.LootCommand;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.commands.OpCommand;
import net.minecraft.server.commands.PardonCommand;
import net.minecraft.server.commands.PardonIpCommand;
import net.minecraft.server.commands.ParticleCommand;
import net.minecraft.server.commands.PerfCommand;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.server.commands.RandomCommand;
import net.minecraft.server.commands.RecipeCommand;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.commands.ReturnCommand;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.server.commands.SaveAllCommand;
import net.minecraft.server.commands.SaveOffCommand;
import net.minecraft.server.commands.SaveOnCommand;
import net.minecraft.server.commands.SayCommand;
import net.minecraft.server.commands.ScheduleCommand;
import net.minecraft.server.commands.ScoreboardCommand;
import net.minecraft.server.commands.SeedCommand;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.commands.SetPlayerIdleTimeoutCommand;
import net.minecraft.server.commands.SetSpawnCommand;
import net.minecraft.server.commands.SetWorldSpawnCommand;
import net.minecraft.server.commands.SpawnArmorTrimsCommand;
import net.minecraft.server.commands.SpectateCommand;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.commands.StopCommand;
import net.minecraft.server.commands.StopSoundCommand;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.commands.TagCommand;
import net.minecraft.server.commands.TeamCommand;
import net.minecraft.server.commands.TeamMsgCommand;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.commands.TitleCommand;
import net.minecraft.server.commands.TriggerCommand;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.commands.WhitelistCommand;
import net.minecraft.server.commands.WorldBorderCommand;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

// CraftBukkit start
import com.google.common.base.Joiner;
import java.util.Collection;
import java.util.LinkedHashSet;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerCommandEvent;
// CraftBukkit end

public class Commands {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LEVEL_ALL = 0;
    public static final int LEVEL_MODERATORS = 1;
    public static final int LEVEL_GAMEMASTERS = 2;
    public static final int LEVEL_ADMINS = 3;
    public static final int LEVEL_OWNERS = 4;
    private final com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher = new com.mojang.brigadier.CommandDispatcher();
    public final java.util.List<CommandNode<CommandSourceStack>> vanillaCommandNodes = new java.util.ArrayList<>(); // Paper

    public Commands(Commands.CommandSelection environment, CommandBuildContext commandRegistryAccess) {
        this(); // CraftBukkit
        AdvancementCommands.register(this.dispatcher);
        AttributeCommand.register(this.dispatcher, commandRegistryAccess);
        ExecuteCommand.register(this.dispatcher, commandRegistryAccess);
        //BossBarCommands.register(this.dispatcher); // Folia - region threading - TODO
        ClearInventoryCommands.register(this.dispatcher, commandRegistryAccess);
        //CloneCommands.register(this.dispatcher, commandRegistryAccess); // Folia - region threading - TODO
        DamageCommand.register(this.dispatcher, commandRegistryAccess);
        //DataCommands.register(this.dispatcher); // Folia - region threading - TODO
        //DataPackCommand.register(this.dispatcher); // Folia - region threading - TODO
        //DebugCommand.register(this.dispatcher); // Folia - region threading - TODO
        DefaultGameModeCommands.register(this.dispatcher);
        DifficultyCommand.register(this.dispatcher);
        EffectCommands.register(this.dispatcher, commandRegistryAccess);
        EmoteCommands.register(this.dispatcher);
        EnchantCommand.register(this.dispatcher, commandRegistryAccess);
        ExperienceCommand.register(this.dispatcher);
        FillCommand.register(this.dispatcher, commandRegistryAccess);
        FillBiomeCommand.register(this.dispatcher, commandRegistryAccess);
        ForceLoadCommand.register(this.dispatcher);
        //FunctionCommand.register(this.dispatcher); // Folia - region threading - TODO
        GameModeCommand.register(this.dispatcher);
        GameRuleCommand.register(this.dispatcher);
        GiveCommand.register(this.dispatcher, commandRegistryAccess);
        HelpCommand.register(this.dispatcher);
        //ItemCommands.register(this.dispatcher, commandRegistryAccess); // Folia - region threading - TODO later
        KickCommand.register(this.dispatcher);
        KillCommand.register(this.dispatcher);
        ListPlayersCommand.register(this.dispatcher);
        LocateCommand.register(this.dispatcher, commandRegistryAccess);
        //LootCommand.register(this.dispatcher, commandRegistryAccess); // Folia - region threading - TODO later
        MsgCommand.register(this.dispatcher);
        ParticleCommand.register(this.dispatcher, commandRegistryAccess);
        PlaceCommand.register(this.dispatcher);
        PlaySoundCommand.register(this.dispatcher);
        RandomCommand.register(this.dispatcher);
        //ReloadCommand.register(this.dispatcher); // Folia - region threading
        RecipeCommand.register(this.dispatcher);
        //ReturnCommand.register(this.dispatcher); // Folia - region threading - TODO later
        //RideCommand.register(this.dispatcher); // Folia - region threading - TODO later
        SayCommand.register(this.dispatcher);
        //ScheduleCommand.register(this.dispatcher); // Folia - region threading
        //ScoreboardCommand.register(this.dispatcher); // Folia - region threading - TODO later
        SeedCommand.register(this.dispatcher, environment != Commands.CommandSelection.INTEGRATED);
        SetBlockCommand.register(this.dispatcher, commandRegistryAccess);
        SetSpawnCommand.register(this.dispatcher);
        SetWorldSpawnCommand.register(this.dispatcher);
        //SpectateCommand.register(this.dispatcher); // Folia - region threading - TODO later
        //SpreadPlayersCommand.register(this.dispatcher); // Folia - region threading - TODO later
        StopSoundCommand.register(this.dispatcher);
        SummonCommand.register(this.dispatcher, commandRegistryAccess);
        //TagCommand.register(this.dispatcher); // Folia - region threading - TODO later
        //TeamCommand.register(this.dispatcher); // Folia - region threading - TODO later
        //TeamMsgCommand.register(this.dispatcher); // Folia - region threading - TODO later
        TeleportCommand.register(this.dispatcher);
        TellRawCommand.register(this.dispatcher);
        TimeCommand.register(this.dispatcher);
        TitleCommand.register(this.dispatcher);
        //TriggerCommand.register(this.dispatcher); // Folia - region threading - TODO later
        WeatherCommand.register(this.dispatcher);
        //WorldBorderCommand.register(this.dispatcher); // Folia - region threading - TODO later
        if (JvmProfiler.INSTANCE.isAvailable()) {
            JfrCommand.register(this.dispatcher);
        }

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            TestCommand.register(this.dispatcher);
            SpawnArmorTrimsCommand.register(this.dispatcher);
            if (environment.includeDedicated) {
                DebugConfigCommand.register(this.dispatcher);
            }
        }

        if (environment.includeDedicated) {
            BanIpCommands.register(this.dispatcher);
            BanListCommands.register(this.dispatcher);
            BanPlayerCommands.register(this.dispatcher);
            DeOpCommands.register(this.dispatcher);
            OpCommand.register(this.dispatcher);
            PardonCommand.register(this.dispatcher);
            PardonIpCommand.register(this.dispatcher);
            //PerfCommand.register(this.dispatcher); // Folia - region threading - TODO later
            //SaveAllCommand.register(this.dispatcher); // Folia - region threading - TODO later
            SaveOffCommand.register(this.dispatcher);
            SaveOnCommand.register(this.dispatcher);
            SetPlayerIdleTimeoutCommand.register(this.dispatcher);
            StopCommand.register(this.dispatcher);
            WhitelistCommand.register(this.dispatcher);
        }

        if (environment.includeIntegrated) {
            PublishCommand.register(this.dispatcher);
        }
        this.vanillaCommandNodes.addAll(this.dispatcher.getRoot().getChildren()); // Paper

        // Paper start
        for (final CommandNode<CommandSourceStack> node : this.dispatcher.getRoot().getChildren()) {
            if (node.getRequirement() == com.mojang.brigadier.builder.ArgumentBuilder.<CommandSourceStack>defaultRequirement()) {
                node.requirement = stack -> stack.source == CommandSource.NULL || stack.getBukkitSender().hasPermission(org.bukkit.craftbukkit.command.VanillaCommandWrapper.getPermission(node));
            }
        }
        // Paper end
        // CraftBukkit start
    }

    public Commands() {
        // CraftBukkkit end
        this.dispatcher.setConsumer((commandcontext, flag, i) -> {
            ((CommandSourceStack) commandcontext.getSource()).onCommandComplete(commandcontext, flag, i);
        });
    }

    public static <S> ParseResults<S> mapSource(ParseResults<S> parseResults, UnaryOperator<S> sourceMapper) {
        CommandContextBuilder<S> commandcontextbuilder = parseResults.getContext();
        CommandContextBuilder<S> commandcontextbuilder1 = commandcontextbuilder.withSource(sourceMapper.apply(commandcontextbuilder.getSource()));

        return new ParseResults(commandcontextbuilder1, parseResults.getReader(), parseResults.getExceptions());
    }

    // CraftBukkit start
    public int dispatchServerCommand(CommandSourceStack sender, String command) {
        Joiner joiner = Joiner.on(" ");
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        ServerCommandEvent event = new ServerCommandEvent(sender.getBukkitSender(), command);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        command = event.getCommand();

        String[] args = command.split(" ");
        if (args.length == 0) return 0; // Paper - empty commands shall not be dispatched

        String cmd = args[0];
        if (cmd.startsWith("minecraft:")) cmd = cmd.substring("minecraft:".length());
        if (cmd.startsWith("bukkit:")) cmd = cmd.substring("bukkit:".length());

        // Block disallowed commands
        if (cmd.equalsIgnoreCase("stop") || cmd.equalsIgnoreCase("kick") || cmd.equalsIgnoreCase("op")
                || cmd.equalsIgnoreCase("deop") || cmd.equalsIgnoreCase("ban") || cmd.equalsIgnoreCase("ban-ip")
                || cmd.equalsIgnoreCase("pardon") || cmd.equalsIgnoreCase("pardon-ip") || cmd.equalsIgnoreCase("reload")) {
            return 0;
        }

        // Handle vanilla commands;
        if (sender.getLevel().getCraftServer().getCommandBlockOverride(args[0])) {
            args[0] = "minecraft:" + args[0];
        }

        String newCommand = joiner.join(args);
        return this.performPrefixedCommand(sender, newCommand, newCommand);
    }
    // CraftBukkit end

    public int performPrefixedCommand(CommandSourceStack source, String command) {
        // CraftBukkit start
        return this.performPrefixedCommand(source, command, command);
    }

    public int performPrefixedCommand(CommandSourceStack commandlistenerwrapper, String s, String label) {
        s = s.startsWith("/") ? s.substring(1) : s;
        return this.performCommand(this.dispatcher.parse(s, commandlistenerwrapper), s, label);
        // CraftBukkit end
    }

    public int performCommand(ParseResults<CommandSourceStack> parseResults, String command) {
        return this.performCommand(parseResults, command, command);
    }

    public int performCommand(ParseResults<CommandSourceStack> parseresults, String s, String label) { // CraftBukkit
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) parseresults.getContext().getSource();

        commandlistenerwrapper.getServer().getProfiler().push(() -> {
            return "/" + s;
        });

        byte b0;

        try {
            byte b1;

            try {
                int i = this.dispatcher.execute(parseresults);

                return i;
            } catch (CommandRuntimeException commandexception) {
                commandlistenerwrapper.sendFailure(commandexception.getComponent());
                b1 = 0;
                return b1;
            } catch (CommandSyntaxException commandsyntaxexception) {
                // Paper start
                final net.kyori.adventure.text.TextComponent.Builder builder = net.kyori.adventure.text.Component.text();
                if ((parseresults.getContext().getNodes().isEmpty() || !this.vanillaCommandNodes.contains(parseresults.getContext().getNodes().get(0).getNode()))) {
                    if (!org.spigotmc.SpigotConfig.unknownCommandMessage.isEmpty()) {
                        builder.append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.unknownCommandMessage));
                    }
                } else {
                // commandlistenerwrapper.sendFailure(ComponentUtils.fromMessage(commandsyntaxexception.getRawMessage()));
                builder.color(net.kyori.adventure.text.format.NamedTextColor.RED).append(io.papermc.paper.brigadier.PaperBrigadier.componentFromMessage(commandsyntaxexception.getRawMessage()));
                // Paper end
                if (commandsyntaxexception.getInput() != null && commandsyntaxexception.getCursor() >= 0) {
                    int j = Math.min(commandsyntaxexception.getInput().length(), commandsyntaxexception.getCursor());
                    MutableComponent ichatmutablecomponent = Component.empty().withStyle(ChatFormatting.GRAY).withStyle((chatmodifier) -> {
                        return chatmodifier.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + label)); // CraftBukkit // Paper
                    });

                    if (j > 10) {
                        ichatmutablecomponent.append(CommonComponents.ELLIPSIS);
                    }

                    ichatmutablecomponent.append(commandsyntaxexception.getInput().substring(Math.max(0, j - 10), j));
                    if (j < commandsyntaxexception.getInput().length()) {
                        MutableComponent ichatmutablecomponent1 = Component.literal(commandsyntaxexception.getInput().substring(j)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE);

                        ichatmutablecomponent.append((Component) ichatmutablecomponent1);
                    }

                    ichatmutablecomponent.append((Component) Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
                // Paper start
                    // commandlistenerwrapper.sendFailure(ichatmutablecomponent);
                    builder
                        .append(net.kyori.adventure.text.Component.newline())
                        .append(io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent));
                }
                }
                org.bukkit.event.command.UnknownCommandEvent event = new org.bukkit.event.command.UnknownCommandEvent(commandlistenerwrapper.getBukkitSender(), s, org.spigotmc.SpigotConfig.unknownCommandMessage.isEmpty() ? null : builder.build());
                org.bukkit.Bukkit.getServer().getPluginManager().callEvent(event);
                if (event.message() != null) {
                    commandlistenerwrapper.sendFailure(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.message()), false);
                // Paper end
                }

                b1 = 0;
                return b1;
            } catch (Exception exception) {
                MutableComponent ichatmutablecomponent2 = Component.literal(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());

                if (Commands.LOGGER.isDebugEnabled()) {
                    Commands.LOGGER.error("Command exception: /{}", s, exception);
                    StackTraceElement[] astacktraceelement = exception.getStackTrace();

                    for (int k = 0; k < Math.min(astacktraceelement.length, 3); ++k) {
                        ichatmutablecomponent2.append("\n\n").append(astacktraceelement[k].getMethodName()).append("\n ").append(astacktraceelement[k].getFileName()).append(":").append(String.valueOf(astacktraceelement[k].getLineNumber()));
                    }
                }

                commandlistenerwrapper.sendFailure(Component.translatable("command.failed").withStyle((chatmodifier) -> {
                    return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ichatmutablecomponent2));
                }));
                if (SharedConstants.IS_RUNNING_IN_IDE) {
                    commandlistenerwrapper.sendFailure(Component.literal(Util.describeError(exception)));
                    Commands.LOGGER.error("'/{}' threw an exception", s, exception);
                }

                b0 = 0;
            }
        } finally {
            commandlistenerwrapper.getServer().getProfiler().pop();
        }

        return b0;
    }

    public void sendCommands(ServerPlayer player) {
        // Paper start - Send empty commands if tab completion is disabled
        if ( org.spigotmc.SpigotConfig.tabComplete < 0 ) { //return; // Spigot
            player.connection.send(new ClientboundCommandsPacket(new RootCommandNode<>()));
            return;
        }
        // Paper end
        // CraftBukkit start
        // Register Vanilla commands into builtRoot as before
        // Paper start - Async command map building
        COMMAND_SENDING_POOL.execute(() -> {
                this.sendAsync(player);
        });
    }

    public static final java.util.concurrent.ThreadPoolExecutor COMMAND_SENDING_POOL = new java.util.concurrent.ThreadPoolExecutor(
        0, 2, 60L, java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat("Paper Async Command Builder Thread Pool - %1$d")
            .setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER))
            .build(),
        new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy()
    );

    private void sendAsync(ServerPlayer player) {
        // Paper end - Async command map building
        Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map = Maps.newIdentityHashMap(); // Use identity to prevent aliasing issues
        RootCommandNode vanillaRoot = new RootCommandNode();

        RootCommandNode<CommandSourceStack> vanilla = player.server.vanillaCommandDispatcher.getDispatcher().getRoot();
        map.put(vanilla, vanillaRoot);
        this.fillUsableCommands(vanilla, vanillaRoot, player.createCommandSourceStack(), (Map) map);

        // Now build the global commands in a second pass
        RootCommandNode<SharedSuggestionProvider> rootcommandnode = new RootCommandNode();

        map.put(this.dispatcher.getRoot(), rootcommandnode);
        this.fillUsableCommands(this.dispatcher.getRoot(), rootcommandnode, player.createCommandSourceStack(), map);

        Collection<String> bukkit = new LinkedHashSet<>();
        for (CommandNode node : rootcommandnode.getChildren()) {
            bukkit.add(node.getName());
        }
        // Paper start - Async command map building
        new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent<CommandSourceStack>(player.getBukkitEntity(), (RootCommandNode) rootcommandnode, false).callEvent(); // Paper
        // Folia start - region threading
        // ignore if retired
        player.getBukkitEntity().taskScheduler.schedule((updatedPlayer) -> {
            runSync((ServerPlayer)updatedPlayer, bukkit, rootcommandnode);
        }, null, 1L);
        // Folia end - region threading
    }

    private void runSync(ServerPlayer player, Collection<String> bukkit, RootCommandNode<SharedSuggestionProvider> rootcommandnode) {
        // Paper end - Async command map building
        new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent<CommandSourceStack>(player.getBukkitEntity(), (RootCommandNode) rootcommandnode, false).callEvent(); // Paper
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player.getBukkitEntity(), new LinkedHashSet<>(bukkit));
        event.getPlayer().getServer().getPluginManager().callEvent(event);

        // Remove labels that were removed during the event
        for (String orig : bukkit) {
            if (!event.getCommands().contains(orig)) {
                rootcommandnode.removeCommand(orig);
            }
        }
        // CraftBukkit end
        player.connection.send(new ClientboundCommandsPacket(rootcommandnode));
    }

    private void fillUsableCommands(CommandNode<CommandSourceStack> tree, CommandNode<SharedSuggestionProvider> result, CommandSourceStack source, Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> resultNodes) {
        Iterator iterator = tree.getChildren().iterator();

        boolean registeredAskServerSuggestionsForTree = false; // Paper - tell clients to ask server for suggestions for EntityArguments
        while (iterator.hasNext()) {
            CommandNode<CommandSourceStack> commandnode2 = (CommandNode) iterator.next();
            // Paper start
            if (commandnode2.clientNode != null) {
                commandnode2 = commandnode2.clientNode;
            }
            // Paper end
            if ( !org.spigotmc.SpigotConfig.sendNamespaced && commandnode2.getName().contains( ":" ) ) continue; // Spigot

            if (commandnode2.canUse(source)) {
                ArgumentBuilder argumentbuilder = commandnode2.createBuilder(); // CraftBukkit - decompile error

                argumentbuilder.requires((icompletionprovider) -> {
                    return true;
                });
                if (argumentbuilder.getCommand() != null) {
                    argumentbuilder.executes((commandcontext) -> {
                        return 0;
                    });
                }

                if (argumentbuilder instanceof RequiredArgumentBuilder) {
                    RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredargumentbuilder = (RequiredArgumentBuilder) argumentbuilder;

                    if (requiredargumentbuilder.getSuggestionsProvider() != null) {
                        requiredargumentbuilder.suggests(SuggestionProviders.safelySwap(requiredargumentbuilder.getSuggestionsProvider()));
                        // Paper start - tell clients to ask server for suggestions for EntityArguments
                        registeredAskServerSuggestionsForTree = requiredargumentbuilder.getSuggestionsProvider() == net.minecraft.commands.synchronization.SuggestionProviders.ASK_SERVER;
                    } else if (io.papermc.paper.configuration.GlobalConfiguration.get().commands.fixTargetSelectorTagCompletion && !registeredAskServerSuggestionsForTree && requiredargumentbuilder.getType() instanceof net.minecraft.commands.arguments.EntityArgument) {
                        requiredargumentbuilder.suggests(requiredargumentbuilder.getType()::listSuggestions);
                        registeredAskServerSuggestionsForTree = true; // You can only
                        // Paper end - tell clients to ask server for suggestions for EntityArguments
                    }
                }

                if (argumentbuilder.getRedirect() != null) {
                    argumentbuilder.redirect((CommandNode) resultNodes.get(argumentbuilder.getRedirect()));
                }

                CommandNode commandnode3 = argumentbuilder.build(); // CraftBukkit - decompile error

                resultNodes.put(commandnode2, commandnode3);
                result.addChild(commandnode3);
                if (!commandnode2.getChildren().isEmpty()) {
                    this.fillUsableCommands(commandnode2, commandnode3, source, resultNodes);
                }
            }
        }

    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String literal) {
        return LiteralArgumentBuilder.literal(literal);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static Predicate<String> createValidator(Commands.ParseFunction parser) {
        return (s) -> {
            try {
                parser.parse(new StringReader(s));
                return true;
            } catch (CommandSyntaxException commandsyntaxexception) {
                return false;
            }
        };
    }

    public com.mojang.brigadier.CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.dispatcher;
    }

    @Nullable
    public static <S> CommandSyntaxException getParseException(ParseResults<S> parse) {
        return !parse.getReader().canRead() ? null : (parse.getExceptions().size() == 1 ? (CommandSyntaxException) parse.getExceptions().values().iterator().next() : (parse.getContext().getRange().isEmpty() ? CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader()) : CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader())));
    }

    public static CommandBuildContext createValidationContext(final HolderLookup.Provider registryLookup) {
        return new CommandBuildContext() {
            @Override
            public <T> HolderLookup<T> holderLookup(ResourceKey<? extends Registry<T>> registryRef) {
                final HolderLookup.RegistryLookup<T> holderlookup_c = registryLookup.lookupOrThrow(registryRef);

                return new HolderLookup.Delegate<T>(holderlookup_c) {
                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.of(this.getOrThrow(tag));
                    }

                    @Override
                    public HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
                        Optional<HolderSet.Named<T>> optional = holderlookup_c.get(tag);

                        return (HolderSet.Named) optional.orElseGet(() -> {
                            return HolderSet.emptyNamed(holderlookup_c, tag);
                        });
                    }
                };
            }
        };
    }

    public static void validate() {
        CommandBuildContext commandbuildcontext = Commands.createValidationContext(VanillaRegistries.createLookup());
        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher = (new Commands(Commands.CommandSelection.ALL, commandbuildcontext)).getDispatcher();
        RootCommandNode<CommandSourceStack> rootcommandnode = com_mojang_brigadier_commanddispatcher.getRoot();

        com_mojang_brigadier_commanddispatcher.findAmbiguities((commandnode, commandnode1, commandnode2, collection) -> {
            Commands.LOGGER.warn("Ambiguity between arguments {} and {} with inputs: {}", new Object[]{com_mojang_brigadier_commanddispatcher.getPath(commandnode1), com_mojang_brigadier_commanddispatcher.getPath(commandnode2), collection});
        });
        Set<ArgumentType<?>> set = ArgumentUtils.findUsedArgumentTypes(rootcommandnode);
        Set<ArgumentType<?>> set1 = (Set) set.stream().filter((argumenttype) -> {
            return !ArgumentTypeInfos.isClassRecognized(argumenttype.getClass());
        }).collect(Collectors.toSet());

        if (!set1.isEmpty()) {
            Commands.LOGGER.warn("Missing type registration for following arguments:\n {}", set1.stream().map((argumenttype) -> {
                return "\t" + argumenttype;
            }).collect(Collectors.joining(",\n")));
            throw new IllegalStateException("Unregistered argument types");
        }
    }

    public static enum CommandSelection {

        ALL(true, true), DEDICATED(false, true), INTEGRATED(true, false);

        final boolean includeIntegrated;
        final boolean includeDedicated;

        private CommandSelection(boolean flag, boolean flag1) {
            this.includeIntegrated = flag;
            this.includeDedicated = flag1;
        }
    }

    @FunctionalInterface
    public interface ParseFunction {

        void parse(StringReader reader) throws CommandSyntaxException;
    }
}
