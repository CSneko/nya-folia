package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;

public class GameRuleCommand {

    public GameRuleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = (LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("gamerule").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        });

        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                literalargumentbuilder.then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal(key.getId()).executes((commandcontext) -> {
                    return GameRuleCommand.queryRule((CommandSourceStack) commandcontext.getSource(), key);
                })).then(type.createArgument("value").executes((commandcontext) -> {
                    return GameRuleCommand.setRule(commandcontext, key);
                })));
            }
        });
        dispatcher.register(literalargumentbuilder);
    }

    static <T extends GameRules.Value<T>> int setRule(CommandContext<CommandSourceStack> context, GameRules.Key<T> key) {
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) context.getSource();
        T t0 = commandlistenerwrapper.getLevel().getGameRules().getRule(key); // CraftBukkit

        t0.setFromArgument(context, "value", key); // Paper
        commandlistenerwrapper.sendSuccess(() -> {
            return Component.translatable("commands.gamerule.set", key.getId(), t0.toString());
        }, true);
        return t0.getCommandResult();
    }

    static <T extends GameRules.Value<T>> int queryRule(CommandSourceStack source, GameRules.Key<T> key) {
        T t0 = source.getLevel().getGameRules().getRule(key); // CraftBukkit

        source.sendSuccess(() -> {
            return Component.translatable("commands.gamerule.query", key.getId(), t0.toString());
        }, false);
        return t0.getCommandResult();
    }
}
