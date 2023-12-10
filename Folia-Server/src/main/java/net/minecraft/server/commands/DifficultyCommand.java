package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

public class DifficultyCommand {

    private static final DynamicCommandExceptionType ERROR_ALREADY_DIFFICULT = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("commands.difficulty.failure", object);
    });

    public DifficultyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = net.minecraft.commands.Commands.literal("difficulty");
        Difficulty[] aenumdifficulty = Difficulty.values();
        int i = aenumdifficulty.length;

        for (int j = 0; j < i; ++j) {
            Difficulty enumdifficulty = aenumdifficulty[j];

            literalargumentbuilder.then(net.minecraft.commands.Commands.literal(enumdifficulty.getKey()).executes((commandcontext) -> {
                return DifficultyCommand.setDifficulty((CommandSourceStack) commandcontext.getSource(), enumdifficulty);
            }));
        }

        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) literalargumentbuilder.requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).executes((commandcontext) -> {
            Difficulty enumdifficulty1 = ((CommandSourceStack) commandcontext.getSource()).getLevel().getDifficulty();

            ((CommandSourceStack) commandcontext.getSource()).sendSuccess(() -> {
                return Component.translatable("commands.difficulty.query", enumdifficulty1.getDisplayName());
            }, false);
            return enumdifficulty1.getId();
        }));
    }

    public static int setDifficulty(CommandSourceStack source, Difficulty difficulty) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        net.minecraft.server.level.ServerLevel worldServer = source.getLevel(); // CraftBukkit

        if (worldServer.getDifficulty() == difficulty) { // CraftBukkit
            throw DifficultyCommand.ERROR_ALREADY_DIFFICULT.create(difficulty.getKey());
        } else {
            minecraftserver.setDifficulty(worldServer, difficulty, true); // Paper - don't skip other difficulty-changing logic (fix upstream's fix)
            source.sendSuccess(() -> {
                return Component.translatable("commands.difficulty.success", difficulty.getDisplayName());
            }, true);
            return 0;
        }
    }
}
