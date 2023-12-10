package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class KickCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kick").requires((source) -> {
            return source.hasPermission(3);
        }).then(Commands.argument("targets", EntityArgument.players()).executes((context) -> {
            return kickPlayers(context.getSource(), EntityArgument.getPlayers(context, "targets"), Component.translatable("multiplayer.disconnect.kicked"));
        }).then(Commands.argument("reason", MessageArgument.message()).executes((context) -> {
            return kickPlayers(context.getSource(), EntityArgument.getPlayers(context, "targets"), MessageArgument.getMessage(context, "reason"));
        }))));
    }

    private static int kickPlayers(CommandSourceStack source, Collection<ServerPlayer> targets, Component reason) {
        for(ServerPlayer serverPlayer : targets) {
            serverPlayer.connection.disconnect(reason, org.bukkit.event.player.PlayerKickEvent.Cause.KICK_COMMAND); // Paper - kick event cause
            source.sendSuccess(() -> {
                return Component.translatable("commands.kick.success", serverPlayer.getDisplayName(), reason);
            }, true);
        }

        return targets.size();
    }
}
