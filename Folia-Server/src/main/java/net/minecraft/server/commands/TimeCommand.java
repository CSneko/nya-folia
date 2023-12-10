package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Iterator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.event.world.TimeSkipEvent;
// CraftBukkit end

public class TimeCommand {

    public TimeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("time").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("set").then(net.minecraft.commands.Commands.literal("day").executes((commandcontext) -> {
            return TimeCommand.setTime((CommandSourceStack) commandcontext.getSource(), 1000);
        }))).then(net.minecraft.commands.Commands.literal("noon").executes((commandcontext) -> {
            return TimeCommand.setTime((CommandSourceStack) commandcontext.getSource(), 6000);
        }))).then(net.minecraft.commands.Commands.literal("night").executes((commandcontext) -> {
            return TimeCommand.setTime((CommandSourceStack) commandcontext.getSource(), 13000);
        }))).then(net.minecraft.commands.Commands.literal("midnight").executes((commandcontext) -> {
            return TimeCommand.setTime((CommandSourceStack) commandcontext.getSource(), 18000);
        }))).then(net.minecraft.commands.Commands.argument("time", TimeArgument.time()).executes((commandcontext) -> {
            return TimeCommand.setTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(net.minecraft.commands.Commands.literal("add").then(net.minecraft.commands.Commands.argument("time", TimeArgument.time()).executes((commandcontext) -> {
            return TimeCommand.addTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("query").then(net.minecraft.commands.Commands.literal("daytime").executes((commandcontext) -> {
            return TimeCommand.queryTime((CommandSourceStack) commandcontext.getSource(), TimeCommand.getDayTime(((CommandSourceStack) commandcontext.getSource()).getLevel()));
        }))).then(net.minecraft.commands.Commands.literal("gametime").executes((commandcontext) -> {
            return TimeCommand.queryTime((CommandSourceStack) commandcontext.getSource(), (int) (((CommandSourceStack) commandcontext.getSource()).getLevel().getGameTime() % 2147483647L));
        }))).then(net.minecraft.commands.Commands.literal("day").executes((commandcontext) -> {
            return TimeCommand.queryTime((CommandSourceStack) commandcontext.getSource(), (int) (((CommandSourceStack) commandcontext.getSource()).getLevel().getDayTime() / 24000L % 2147483647L));
        }))));
    }

    private static int getDayTime(ServerLevel world) {
        return (int) (world.getDayTime() % 24000L);
    }

    private static int queryTime(CommandSourceStack source, int time) {
        source.sendSuccess(() -> {
            return Component.translatable("commands.time.query", time);
        }, false);
        return time;
    }

    public static int setTime(CommandSourceStack source, int time) {
        Iterator iterator = io.papermc.paper.configuration.GlobalConfiguration.get().commands.timeCommandAffectsAllWorlds ? source.getServer().getAllLevels().iterator() : com.google.common.collect.Iterators.singletonIterator(source.getLevel()); // CraftBukkit - SPIGOT-6496: Only set the time for the world the command originates in // Paper - add config option for spigot's change

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
            // CraftBukkit start
            TimeSkipEvent event = new TimeSkipEvent(worldserver.getWorld(), TimeSkipEvent.SkipReason.COMMAND, time - worldserver.getDayTime());
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                worldserver.setDayTime((long) worldserver.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
            }); // Folia - region threading
        }

        source.sendSuccess(() -> {
            return Component.translatable("commands.time.set", time);
        }, true);
        return TimeCommand.getDayTime(source.getLevel());
    }

    public static int addTime(CommandSourceStack source, int time) {
        Iterator iterator = io.papermc.paper.configuration.GlobalConfiguration.get().commands.timeCommandAffectsAllWorlds ? source.getServer().getAllLevels().iterator() : com.google.common.collect.Iterators.singletonIterator(source.getLevel()); // CraftBukkit - SPIGOT-6496: Only set the time for the world the command originates in // Paper - add config option for spigot's change

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
            // CraftBukkit start
            TimeSkipEvent event = new TimeSkipEvent(worldserver.getWorld(), TimeSkipEvent.SkipReason.COMMAND, time);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                worldserver.setDayTime(worldserver.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
            }); // Folia - region threading
        }

        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        int j = TimeCommand.getDayTime(source.getLevel());

        source.sendSuccess(() -> {
            return Component.translatable("commands.time.set", j);
        }, true);
        }); // Folia - region threading
        return 0; // Folia - region threading
    }
}
