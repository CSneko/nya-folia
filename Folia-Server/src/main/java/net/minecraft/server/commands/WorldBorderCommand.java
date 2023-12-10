package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec2;

public class WorldBorderCommand {

    private static final SimpleCommandExceptionType ERROR_SAME_CENTER = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.center.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.nochange"));
    private static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.small"));
    private static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.big", 5.9999968E7D));
    private static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.far", 2.9999984E7D));
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_TIME = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.warning.time.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_DISTANCE = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.warning.distance.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_BUFFER = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.damage.buffer.failed"));
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_AMOUNT = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.damage.amount.failed"));

    public WorldBorderCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("worldborder").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(net.minecraft.commands.Commands.literal("add").then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("distance", DoubleArgumentType.doubleArg(-5.9999968E7D, 5.9999968E7D)).executes((commandcontext) -> {
            return WorldBorderCommand.setSize((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(commandcontext, "distance"), 0L);
        })).then(net.minecraft.commands.Commands.argument("time", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return WorldBorderCommand.setSize((CommandSourceStack) commandcontext.getSource(), ((CommandSourceStack) commandcontext.getSource()).getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(commandcontext, "distance"), ((CommandSourceStack) commandcontext.getSource()).getLevel().getWorldBorder().getLerpRemainingTime() + (long) IntegerArgumentType.getInteger(commandcontext, "time") * 1000L);
        }))))).then(net.minecraft.commands.Commands.literal("set").then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("distance", DoubleArgumentType.doubleArg(-5.9999968E7D, 5.9999968E7D)).executes((commandcontext) -> {
            return WorldBorderCommand.setSize((CommandSourceStack) commandcontext.getSource(), DoubleArgumentType.getDouble(commandcontext, "distance"), 0L);
        })).then(net.minecraft.commands.Commands.argument("time", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return WorldBorderCommand.setSize((CommandSourceStack) commandcontext.getSource(), DoubleArgumentType.getDouble(commandcontext, "distance"), (long) IntegerArgumentType.getInteger(commandcontext, "time") * 1000L);
        }))))).then(net.minecraft.commands.Commands.literal("center").then(net.minecraft.commands.Commands.argument("pos", Vec2Argument.vec2()).executes((commandcontext) -> {
            return WorldBorderCommand.setCenter((CommandSourceStack) commandcontext.getSource(), Vec2Argument.getVec2(commandcontext, "pos"));
        })))).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("damage").then(net.minecraft.commands.Commands.literal("amount").then(net.minecraft.commands.Commands.argument("damagePerBlock", FloatArgumentType.floatArg(0.0F)).executes((commandcontext) -> {
            return WorldBorderCommand.setDamageAmount((CommandSourceStack) commandcontext.getSource(), FloatArgumentType.getFloat(commandcontext, "damagePerBlock"));
        })))).then(net.minecraft.commands.Commands.literal("buffer").then(net.minecraft.commands.Commands.argument("distance", FloatArgumentType.floatArg(0.0F)).executes((commandcontext) -> {
            return WorldBorderCommand.setDamageBuffer((CommandSourceStack) commandcontext.getSource(), FloatArgumentType.getFloat(commandcontext, "distance"));
        }))))).then(net.minecraft.commands.Commands.literal("get").executes((commandcontext) -> {
            return WorldBorderCommand.getSize((CommandSourceStack) commandcontext.getSource());
        }))).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("warning").then(net.minecraft.commands.Commands.literal("distance").then(net.minecraft.commands.Commands.argument("distance", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return WorldBorderCommand.setWarningDistance((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "distance"));
        })))).then(net.minecraft.commands.Commands.literal("time").then(net.minecraft.commands.Commands.argument("time", IntegerArgumentType.integer(0)).executes((commandcontext) -> {
            return WorldBorderCommand.setWarningTime((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))));
    }

    private static int setDamageBuffer(CommandSourceStack source, float distance) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder(); // CraftBukkit

        if (worldborder.getDamageSafeZone() == (double) distance) {
            throw WorldBorderCommand.ERROR_SAME_DAMAGE_BUFFER.create();
        } else {
            worldborder.setDamageSafeZone((double) distance);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance));
            }, true);
            return (int) distance;
        }
    }

    private static int setDamageAmount(CommandSourceStack source, float damagePerBlock) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder(); // CraftBukkit

        if (worldborder.getDamagePerBlock() == (double) damagePerBlock) {
            throw WorldBorderCommand.ERROR_SAME_DAMAGE_AMOUNT.create();
        } else {
            worldborder.setDamagePerBlock((double) damagePerBlock);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", damagePerBlock));
            }, true);
            return (int) damagePerBlock;
        }
    }

    private static int setWarningTime(CommandSourceStack source, int time) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder(); // CraftBukkit

        if (worldborder.getWarningTime() == time) {
            throw WorldBorderCommand.ERROR_SAME_WARNING_TIME.create();
        } else {
            worldborder.setWarningTime(time);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.warning.time.success", time);
            }, true);
            return time;
        }
    }

    private static int setWarningDistance(CommandSourceStack source, int distance) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder(); // CraftBukkit

        if (worldborder.getWarningBlocks() == distance) {
            throw WorldBorderCommand.ERROR_SAME_WARNING_DISTANCE.create();
        } else {
            worldborder.setWarningBlocks(distance);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.warning.distance.success", distance);
            }, true);
            return distance;
        }
    }

    private static int getSize(CommandSourceStack source) {
        double d0 = source.getLevel().getWorldBorder().getSize(); // CraftBukkit

        source.sendSuccess(() -> {
            return Component.translatable("commands.worldborder.get", String.format(Locale.ROOT, "%.0f", d0));
        }, false);
        return Mth.floor(d0 + 0.5D);
    }

    private static int setCenter(CommandSourceStack source, Vec2 pos) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder(); // CraftBukkit

        if (worldborder.getCenterX() == (double) pos.x && worldborder.getCenterZ() == (double) pos.y) {
            throw WorldBorderCommand.ERROR_SAME_CENTER.create();
        } else if ((double) Math.abs(pos.x) <= 2.9999984E7D && (double) Math.abs(pos.y) <= 2.9999984E7D) {
            worldborder.setCenter((double) pos.x, (double) pos.y);
            source.sendSuccess(() -> {
                return Component.translatable("commands.worldborder.center.success", String.format(Locale.ROOT, "%.2f", pos.x), String.format(Locale.ROOT, "%.2f", pos.y));
            }, true);
            return 0;
        } else {
            throw WorldBorderCommand.ERROR_TOO_FAR_OUT.create();
        }
    }

    private static int setSize(CommandSourceStack source, double distance, long time) throws CommandSyntaxException {
        WorldBorder worldborder = source.getLevel().getWorldBorder(); // CraftBukkit
        double d1 = worldborder.getSize();

        if (d1 == distance) {
            throw WorldBorderCommand.ERROR_SAME_SIZE.create();
        } else if (distance < 1.0D) {
            throw WorldBorderCommand.ERROR_TOO_SMALL.create();
        } else if (distance > 5.9999968E7D) {
            throw WorldBorderCommand.ERROR_TOO_BIG.create();
        } else {
            if (time > 0L) {
                worldborder.lerpSizeBetween(d1, distance, time);
                if (distance > d1) {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.worldborder.set.grow", String.format(Locale.ROOT, "%.1f", distance), Long.toString(time / 1000L));
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.worldborder.set.shrink", String.format(Locale.ROOT, "%.1f", distance), Long.toString(time / 1000L));
                    }, true);
                }
            } else {
                worldborder.setSize(distance);
                source.sendSuccess(() -> {
                    return Component.translatable("commands.worldborder.set.immediate", String.format(Locale.ROOT, "%.1f", distance));
                }, true);
            }

            return (int) (distance - d1);
        }
    }
}
