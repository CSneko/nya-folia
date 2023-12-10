package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;

public class WeatherCommand {
    private static final int DEFAULT_TIME = -1;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weather").requires((source) -> {
            return source.hasPermission(2);
        }).then(Commands.literal("clear").executes((context) -> {
            return setClear(context.getSource(), -1);
        }).then(Commands.argument("duration", TimeArgument.time(1)).executes((context) -> {
            return setClear(context.getSource(), IntegerArgumentType.getInteger(context, "duration"));
        }))).then(Commands.literal("rain").executes((context) -> {
            return setRain(context.getSource(), -1);
        }).then(Commands.argument("duration", TimeArgument.time(1)).executes((context) -> {
            return setRain(context.getSource(), IntegerArgumentType.getInteger(context, "duration"));
        }))).then(Commands.literal("thunder").executes((context) -> {
            return setThunder(context.getSource(), -1);
        }).then(Commands.argument("duration", TimeArgument.time(1)).executes((context) -> {
            return setThunder(context.getSource(), IntegerArgumentType.getInteger(context, "duration"));
        }))));
    }

    private static int getDuration(CommandSourceStack source, int duration, IntProvider provider) {
        return duration == -1 ? provider.sample(source.getLevel().getRandom()) : duration;
    }

    private static int setClear(CommandSourceStack source, int duration) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        source.getLevel().setWeatherParameters(getDuration(source, duration, ServerLevel.RAIN_DELAY), 0, false, false);
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.clear");
        }, true);
        }); // Folia - region threading
        return duration;
    }

    private static int setRain(CommandSourceStack source, int duration) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        source.getLevel().setWeatherParameters(0, getDuration(source, duration, ServerLevel.RAIN_DURATION), true, false);
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.rain");
        }, true);
        }); // Folia - region threading
        return duration;
    }

    private static int setThunder(CommandSourceStack source, int duration) {
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        source.getLevel().setWeatherParameters(0, getDuration(source, duration, ServerLevel.THUNDER_DURATION), true, true);
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.thunder");
        }, true);
        }); // Folia - region threading
        return duration;
    }
}
