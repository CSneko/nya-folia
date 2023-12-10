package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ClearInventoryCommands {
    private static final DynamicCommandExceptionType ERROR_SINGLE = new DynamicCommandExceptionType((playerName) -> {
        return Component.translatable("clear.failed.single", playerName);
    });
    private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType((playerCount) -> {
        return Component.translatable("clear.failed.multiple", playerCount);
    });

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        dispatcher.register(Commands.literal("clear").requires((source) -> {
            return source.hasPermission(2);
        }).executes((context) -> {
            return clearInventory(context.getSource(), Collections.singleton(context.getSource().getPlayerOrException()), (stack) -> {
                return true;
            }, -1);
        }).then(Commands.argument("targets", EntityArgument.players()).executes((context) -> {
            return clearInventory(context.getSource(), EntityArgument.getPlayers(context, "targets"), (stack) -> {
                return true;
            }, -1);
        }).then(Commands.argument("item", ItemPredicateArgument.itemPredicate(commandRegistryAccess)).executes((context) -> {
            return clearInventory(context.getSource(), EntityArgument.getPlayers(context, "targets"), ItemPredicateArgument.getItemPredicate(context, "item"), -1);
        }).then(Commands.argument("maxCount", IntegerArgumentType.integer(0)).executes((context) -> {
            return clearInventory(context.getSource(), EntityArgument.getPlayers(context, "targets"), ItemPredicateArgument.getItemPredicate(context, "item"), IntegerArgumentType.getInteger(context, "maxCount"));
        })))));
    }

    private static int clearInventory(CommandSourceStack source, Collection<ServerPlayer> targets, Predicate<ItemStack> item, int maxCount) throws CommandSyntaxException {
        int i = 0;

        for(ServerPlayer serverPlayer : targets) {
            ++i;
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
            serverPlayer.getInventory().clearOrCountMatchingItems(item, maxCount, serverPlayer.inventoryMenu.getCraftSlots());
            serverPlayer.containerMenu.broadcastChanges();
            serverPlayer.inventoryMenu.slotsChanged(serverPlayer.getInventory());
            }, null, 1L); // Folia - region threading
        }

        if (i == 0) {
            if (targets.size() == 1) {
                throw ERROR_SINGLE.create(targets.iterator().next().getName());
            } else {
                throw ERROR_MULTIPLE.create(targets.size());
            }
        } else {
            int j = i;
            if (maxCount == 0) {
                if (targets.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.clear.test.single", j, targets.iterator().next().getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable("commands.clear.test.multiple", j, targets.size());
                    }, true);
                }
            } else if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.clear.success.single", j, targets.iterator().next().getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.clear.success.multiple", j, targets.size());
                }, true);
            }

            return i;
        }
    }
}
