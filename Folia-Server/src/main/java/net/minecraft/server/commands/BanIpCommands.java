package net.minecraft.server.commands;

import com.google.common.net.InetAddresses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;

public class BanIpCommands {
    private static final SimpleCommandExceptionType ERROR_INVALID_IP = new SimpleCommandExceptionType(Component.translatable("commands.banip.invalid"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.banip.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ban-ip").requires((source) -> {
            return source.hasPermission(3);
        }).then(Commands.argument("target", StringArgumentType.word()).executes((context) -> {
            return banIpOrName(context.getSource(), StringArgumentType.getString(context, "target"), (Component)null);
        }).then(Commands.argument("reason", MessageArgument.message()).executes((context) -> {
            return banIpOrName(context.getSource(), StringArgumentType.getString(context, "target"), MessageArgument.getMessage(context, "reason"));
        }))));
    }

    private static int banIpOrName(CommandSourceStack source, String target, @Nullable Component reason) throws CommandSyntaxException {
        if (InetAddresses.isInetAddress(target)) {
            return banIp(source, target, reason);
        } else {
            ServerPlayer serverPlayer = source.getServer().getPlayerList().getPlayerByName(target);
            if (serverPlayer != null) {
                return banIp(source, serverPlayer.getIpAddress(), reason);
            } else {
                throw ERROR_INVALID_IP.create();
            }
        }
    }

    private static int banIp(CommandSourceStack source, String targetIp, @Nullable Component reason) throws CommandSyntaxException {
        IpBanList ipBanList = source.getServer().getPlayerList().getIpBans();
        if (ipBanList.isBanned(targetIp)) {
            throw ERROR_ALREADY_BANNED.create();
        } else {
            List<ServerPlayer> list = source.getServer().getPlayerList().getPlayersWithAddress(targetIp);
            IpBanListEntry ipBanListEntry = new IpBanListEntry(targetIp, (Date)null, source.getTextName(), (Date)null, reason == null ? null : reason.getString());
            ipBanList.add(ipBanListEntry);
            source.sendSuccess(() -> {
                return Component.translatable("commands.banip.success", targetIp, ipBanListEntry.getReason());
            }, true);
            if (!list.isEmpty()) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.banip.info", list.size(), EntitySelector.joinNames(list));
                }, true);
            }

            for(ServerPlayer serverPlayer : list) {
                serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned"), org.bukkit.event.player.PlayerKickEvent.Cause.IP_BANNED); // Paper - kick event cause
            }

            return list.size();
        }
    }
}
