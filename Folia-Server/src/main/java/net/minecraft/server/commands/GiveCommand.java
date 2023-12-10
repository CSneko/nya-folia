package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Iterator;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GiveCommand {

    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public GiveCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("give").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(net.minecraft.commands.Commands.argument("targets", EntityArgument.players()).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("item", ItemArgument.item(commandRegistryAccess)).executes((commandcontext) -> {
            return GiveCommand.giveItem((CommandSourceStack) commandcontext.getSource(), ItemArgument.getItem(commandcontext, "item"), EntityArgument.getPlayers(commandcontext, "targets"), 1);
        })).then(net.minecraft.commands.Commands.argument("count", IntegerArgumentType.integer(1)).executes((commandcontext) -> {
            return GiveCommand.giveItem((CommandSourceStack) commandcontext.getSource(), ItemArgument.getItem(commandcontext, "item"), EntityArgument.getPlayers(commandcontext, "targets"), IntegerArgumentType.getInteger(commandcontext, "count"));
        })))));
    }

    private static int giveItem(CommandSourceStack source, ItemInput item, Collection<ServerPlayer> targets, int count) throws CommandSyntaxException {
        int j = item.getItem().getMaxStackSize();
        int k = j * 100;
        ItemStack itemstack = item.createItemStack(count, false);

        if (count > k) {
            source.sendFailure(Component.translatable("commands.give.failed.toomanyitems", k, itemstack.getDisplayName()));
            return 0;
        } else {
            Iterator iterator = targets.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();
                int l = count;

                while (l > 0) {
                    int i1 = Math.min(j, l);

                    l -= i1;
                    ItemStack itemstack1 = item.createItemStack(i1, false);
                    entityplayer.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> { // Folia - region threading
                    boolean flag = entityplayer.getInventory().add(itemstack1);
                    ItemEntity entityitem;

                    if (flag && itemstack1.isEmpty()) {
                        itemstack1.setCount(1);
                        entityitem = entityplayer.drop(itemstack1, false, false, false); // SPIGOT-2942: Add boolean to call event
                        if (entityitem != null) {
                            entityitem.makeFakeItem();
                        }

                        entityplayer.level().playSound((Player) null, entityplayer.getX(), entityplayer.getY(), entityplayer.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, ((entityplayer.getRandom().nextFloat() - entityplayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
                        entityplayer.containerMenu.broadcastChanges();
                    } else {
                        entityitem = entityplayer.drop(itemstack1, false);
                        if (entityitem != null) {
                            entityitem.setNoPickUpDelay();
                            entityitem.setTarget(entityplayer.getUUID());
                        }
                    }
                    }, null, 1L); // Folia - region threading
                }
            }

            if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.give.success.single", count, itemstack.getDisplayName(), ((ServerPlayer) targets.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.give.success.single", count, itemstack.getDisplayName(), targets.size());
                }, true);
            }

            return targets.size();
        }
    }
}
