package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

public class GameModeCommand {
    public static final int PERMISSION_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gamemode").requires((source) -> {
            return source.hasPermission(2);
        }).then(Commands.argument("gamemode", GameModeArgument.gameMode()).executes((commandContext) -> {
            return setMode(commandContext, Collections.singleton(commandContext.getSource().getPlayerOrException()), GameModeArgument.getGameMode(commandContext, "gamemode"));
        }).then(Commands.argument("target", EntityArgument.players()).executes((commandContext) -> {
            return setMode(commandContext, EntityArgument.getPlayers(commandContext, "target"), GameModeArgument.getGameMode(commandContext, "gamemode"));
        }))));
    }

    private static void logGamemodeChange(CommandSourceStack source, ServerPlayer player, GameType gameMode) {
        Component component = Component.translatable("gameMode." + gameMode.getName());
        if (source.getEntity() == player) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.gamemode.success.self", component);
            }, true);
        } else {
            if (source.getLevel().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
                player.sendSystemMessage(Component.translatable("gameMode.changed", component));
            }

            source.sendSuccess(() -> {
                return Component.translatable("commands.gamemode.success.other", player.getDisplayName(), component);
            }, true);
        }

    }

    private static int setMode(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, GameType gameMode) {
        int i = 0;

        for(ServerPlayer serverPlayer : targets) {
            serverPlayer.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> { // Folia - region threading
            // Paper start - extend PlayerGameModeChangeEvent
            org.bukkit.event.player.PlayerGameModeChangeEvent event = serverPlayer.setGameMode(gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.COMMAND, net.kyori.adventure.text.Component.empty());
            if (event != null && !event.isCancelled()) {
                logGamemodeChange(context.getSource(), serverPlayer, gameMode);
                // Folia - region threading
            } else if (event != null && event.cancelMessage() != null) {
                context.getSource().sendSuccess(() -> io.papermc.paper.adventure.PaperAdventure.asVanilla(event.cancelMessage()), true);
                // Paper end
            }
            }, null, 1L); // Folia - region threading
            ++i; // Folia - region threading
        }

        return i;
    }
}
