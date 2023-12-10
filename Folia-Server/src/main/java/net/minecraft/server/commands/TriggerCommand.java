package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class TriggerCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_PRIMED = new SimpleCommandExceptionType(Component.translatable("commands.trigger.failed.unprimed"));
    private static final SimpleCommandExceptionType ERROR_INVALID_OBJECTIVE = new SimpleCommandExceptionType(Component.translatable("commands.trigger.failed.invalid"));

    public TriggerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("trigger").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("objective", ObjectiveArgument.objective()).suggests((commandcontext, suggestionsbuilder) -> {
            return TriggerCommand.suggestObjectives((CommandSourceStack) commandcontext.getSource(), suggestionsbuilder);
        }).executes((commandcontext) -> {
            return TriggerCommand.simpleTrigger((CommandSourceStack) commandcontext.getSource(), TriggerCommand.getScore(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException(), ObjectiveArgument.getObjective(commandcontext, "objective")));
        })).then(net.minecraft.commands.Commands.literal("add").then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return TriggerCommand.addValue((CommandSourceStack) commandcontext.getSource(), TriggerCommand.getScore(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException(), ObjectiveArgument.getObjective(commandcontext, "objective")), IntegerArgumentType.getInteger(commandcontext, "value"));
        })))).then(net.minecraft.commands.Commands.literal("set").then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return TriggerCommand.setValue((CommandSourceStack) commandcontext.getSource(), TriggerCommand.getScore(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException(), ObjectiveArgument.getObjective(commandcontext, "objective")), IntegerArgumentType.getInteger(commandcontext, "value"));
        })))));
    }

    public static CompletableFuture<Suggestions> suggestObjectives(CommandSourceStack source, SuggestionsBuilder builder) {
        Entity entity = source.getEntity();
        List<String> list = Lists.newArrayList();

        if (entity != null) {
            ServerScoreboard scoreboardserver = source.getServer().getScoreboard();
            String s = entity.getScoreboardName();
            Iterator iterator = scoreboardserver.getObjectives().iterator();

            while (iterator.hasNext()) {
                Objective scoreboardobjective = (Objective) iterator.next();

                if (scoreboardobjective.getCriteria() == ObjectiveCriteria.TRIGGER && scoreboardserver.hasPlayerScore(s, scoreboardobjective)) {
                    Score scoreboardscore = scoreboardserver.getOrCreatePlayerScore(s, scoreboardobjective);

                    if (!scoreboardscore.isLocked()) {
                        list.add(scoreboardobjective.getName());
                    }
                }
            }
        }

        return SharedSuggestionProvider.suggest((Iterable) list, builder);
    }

    private static int addValue(CommandSourceStack source, Score score, int value) {
        score.add(value);
        source.sendSuccess(() -> {
            return Component.translatable("commands.trigger.add.success", score.getObjective().getFormattedDisplayName(), value);
        }, true);
        return score.getScore();
    }

    private static int setValue(CommandSourceStack source, Score score, int value) {
        score.setScore(value);
        source.sendSuccess(() -> {
            return Component.translatable("commands.trigger.set.success", score.getObjective().getFormattedDisplayName(), value);
        }, true);
        return value;
    }

    private static int simpleTrigger(CommandSourceStack source, Score score) {
        score.add(1);
        source.sendSuccess(() -> {
            return Component.translatable("commands.trigger.simple.success", score.getObjective().getFormattedDisplayName());
        }, true);
        return score.getScore();
    }

    private static Score getScore(ServerPlayer player, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw TriggerCommand.ERROR_INVALID_OBJECTIVE.create();
        } else {
            Scoreboard scoreboard = player.getServer().getScoreboard(); // CraftBukkit - SPIGOT-6917: use main scoreboard
            String s = player.getScoreboardName();

            if (!scoreboard.hasPlayerScore(s, objective)) {
                throw TriggerCommand.ERROR_NOT_PRIMED.create();
            } else {
                Score scoreboardscore = scoreboard.getOrCreatePlayerScore(s, objective);

                if (scoreboardscore.isLocked()) {
                    throw TriggerCommand.ERROR_NOT_PRIMED.create();
                } else {
                    scoreboardscore.setLocked(true);
                    return scoreboardscore;
                }
            }
        }
    }
}
