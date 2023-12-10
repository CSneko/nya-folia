package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;

public class DeOpCommands {
    private static final SimpleCommandExceptionType ERROR_NOT_OP = new SimpleCommandExceptionType(Component.translatable("commands.deop.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deop").requires((source) -> {
            return source.hasPermission(3);
        }).then(Commands.argument("targets", GameProfileArgument.gameProfile()).suggests((context, builder) -> {
            return SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerList().getOpNames(), builder);
        }).executes((context) -> {
            return deopPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets"));
        })));
    }

    private static int deopPlayers(CommandSourceStack source, Collection<GameProfile> targets) throws CommandSyntaxException {
        PlayerList playerList = source.getServer().getPlayerList();
        int i = 0;

        for(GameProfile gameProfile : targets) {
            if (playerList.isOp(gameProfile)) {
                playerList.deop(gameProfile);
                ++i;
                source.sendSuccess(() -> {
                    return Component.translatable("commands.deop.success", gameProfile.getName()); // Paper - fixes MC-253721
                }, true);
            }
        }

        if (i == 0) {
            throw ERROR_NOT_OP.create();
        } else {
            source.getServer().kickUnlistedPlayers(source);
            return i;
        }
    }
}
