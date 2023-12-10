package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LootCommand {

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_LOOT_TABLE = (commandcontext, suggestionsbuilder) -> {
        LootDataManager lootdatamanager = ((CommandSourceStack) commandcontext.getSource()).getServer().getLootData();

        return SharedSuggestionProvider.suggestResource((Iterable) lootdatamanager.getKeys(LootDataType.TABLE), suggestionsbuilder);
    };
    private static final DynamicCommandExceptionType ERROR_NO_HELD_ITEMS = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("commands.drop.no_held_items", object);
    });
    private static final DynamicCommandExceptionType ERROR_NO_LOOT_TABLE = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("commands.drop.no_loot_table", object);
    });

    public LootCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        dispatcher.register((LiteralArgumentBuilder) LootCommand.addTargets((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("loot").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        }), (argumentbuilder, commandloot_b) -> {
            return argumentbuilder.then(net.minecraft.commands.Commands.literal("fish").then(net.minecraft.commands.Commands.argument("loot_table", ResourceLocationArgument.id()).suggests(LootCommand.SUGGEST_LOOT_TABLE).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
                return LootCommand.dropFishingLoot(commandcontext, ResourceLocationArgument.getId(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemStack.EMPTY, commandloot_b);
            })).then(net.minecraft.commands.Commands.argument("tool", ItemArgument.item(commandRegistryAccess)).executes((commandcontext) -> {
                return LootCommand.dropFishingLoot(commandcontext, ResourceLocationArgument.getId(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemArgument.getItem(commandcontext, "tool").createItemStack(1, false), commandloot_b);
            }))).then(net.minecraft.commands.Commands.literal("mainhand").executes((commandcontext) -> {
                return LootCommand.dropFishingLoot(commandcontext, ResourceLocationArgument.getId(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), LootCommand.getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.MAINHAND), commandloot_b);
            }))).then(net.minecraft.commands.Commands.literal("offhand").executes((commandcontext) -> {
                return LootCommand.dropFishingLoot(commandcontext, ResourceLocationArgument.getId(commandcontext, "loot_table"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), LootCommand.getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.OFFHAND), commandloot_b);
            }))))).then(net.minecraft.commands.Commands.literal("loot").then(net.minecraft.commands.Commands.argument("loot_table", ResourceLocationArgument.id()).suggests(LootCommand.SUGGEST_LOOT_TABLE).executes((commandcontext) -> {
                return LootCommand.dropChestLoot(commandcontext, ResourceLocationArgument.getId(commandcontext, "loot_table"), commandloot_b);
            }))).then(net.minecraft.commands.Commands.literal("kill").then(net.minecraft.commands.Commands.argument("target", EntityArgument.entity()).executes((commandcontext) -> {
                return LootCommand.dropKillLoot(commandcontext, EntityArgument.getEntity(commandcontext, "target"), commandloot_b);
            }))).then(net.minecraft.commands.Commands.literal("mine").then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) ((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
                return LootCommand.dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemStack.EMPTY, commandloot_b);
            })).then(net.minecraft.commands.Commands.argument("tool", ItemArgument.item(commandRegistryAccess)).executes((commandcontext) -> {
                return LootCommand.dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), ItemArgument.getItem(commandcontext, "tool").createItemStack(1, false), commandloot_b);
            }))).then(net.minecraft.commands.Commands.literal("mainhand").executes((commandcontext) -> {
                return LootCommand.dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), LootCommand.getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.MAINHAND), commandloot_b);
            }))).then(net.minecraft.commands.Commands.literal("offhand").executes((commandcontext) -> {
                return LootCommand.dropBlockLoot(commandcontext, BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), LootCommand.getSourceHandItem((CommandSourceStack) commandcontext.getSource(), EquipmentSlot.OFFHAND), commandloot_b);
            }))));
        }));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTargets(T rootArgument, LootCommand.TailProvider sourceConstructor) {
        return (T) rootArgument.then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("replace").then(net.minecraft.commands.Commands.literal("entity").then(net.minecraft.commands.Commands.argument("entities", EntityArgument.entities()).then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("slot", SlotArgument.slot()), (commandcontext, list, commandloot_a) -> { // CraftBukkit - decompile error
            return LootCommand.entityReplace(EntityArgument.getEntities(commandcontext, "entities"), SlotArgument.getSlot(commandcontext, "slot"), list.size(), list, commandloot_a);
        }).then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("count", IntegerArgumentType.integer(0)), (commandcontext, list, commandloot_a) -> {
            return LootCommand.entityReplace(EntityArgument.getEntities(commandcontext, "entities"), SlotArgument.getSlot(commandcontext, "slot"), IntegerArgumentType.getInteger(commandcontext, "count"), list, commandloot_a);
        })))))).then(net.minecraft.commands.Commands.literal("block").then(net.minecraft.commands.Commands.argument("targetPos", BlockPosArgument.blockPos()).then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("slot", SlotArgument.slot()), (commandcontext, list, commandloot_a) -> {
            return LootCommand.blockReplace((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "targetPos"), SlotArgument.getSlot(commandcontext, "slot"), list.size(), list, commandloot_a);
        }).then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("count", IntegerArgumentType.integer(0)), (commandcontext, list, commandloot_a) -> {
            return LootCommand.blockReplace((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "targetPos"), IntegerArgumentType.getInteger(commandcontext, "slot"), IntegerArgumentType.getInteger(commandcontext, "count"), list, commandloot_a);
        })))))).then(net.minecraft.commands.Commands.literal("insert").then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("targetPos", BlockPosArgument.blockPos()), (commandcontext, list, commandloot_a) -> {
            return LootCommand.blockDistribute((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getLoadedBlockPos(commandcontext, "targetPos"), list, commandloot_a);
        }))).then(net.minecraft.commands.Commands.literal("give").then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("players", EntityArgument.players()), (commandcontext, list, commandloot_a) -> {
            return LootCommand.playerGive(EntityArgument.getPlayers(commandcontext, "players"), list, commandloot_a);
        }))).then(net.minecraft.commands.Commands.literal("spawn").then(sourceConstructor.construct(net.minecraft.commands.Commands.argument("targetPos", Vec3Argument.vec3()), (commandcontext, list, commandloot_a) -> {
            return LootCommand.dropInWorld((CommandSourceStack) commandcontext.getSource(), Vec3Argument.getVec3(commandcontext, "targetPos"), list, commandloot_a);
        })));
    }

    private static Container getContainer(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        BlockEntity tileentity = source.getLevel().getBlockEntity(pos);

        if (!(tileentity instanceof Container)) {
            throw ItemCommands.ERROR_TARGET_NOT_A_CONTAINER.create(pos.getX(), pos.getY(), pos.getZ());
        } else {
            return (Container) tileentity;
        }
    }

    private static int blockDistribute(CommandSourceStack source, BlockPos targetPos, List<ItemStack> stacks, LootCommand.Callback messageSender) throws CommandSyntaxException {
        Container iinventory = LootCommand.getContainer(source, targetPos);
        List<ItemStack> list1 = Lists.newArrayListWithCapacity(stacks.size());
        Iterator iterator = stacks.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (LootCommand.distributeToContainer(iinventory, itemstack.copy())) {
                iinventory.setChanged();
                list1.add(itemstack);
            }
        }

        messageSender.accept(list1);
        return list1.size();
    }

    private static boolean distributeToContainer(Container inventory, ItemStack stack) {
        boolean flag = false;

        for (int i = 0; i < inventory.getContainerSize() && !stack.isEmpty(); ++i) {
            ItemStack itemstack1 = inventory.getItem(i);

            if (inventory.canPlaceItem(i, stack)) {
                if (itemstack1.isEmpty()) {
                    inventory.setItem(i, stack);
                    flag = true;
                    break;
                }

                if (LootCommand.canMergeItems(itemstack1, stack)) {
                    int j = stack.getMaxStackSize() - itemstack1.getCount();
                    int k = Math.min(stack.getCount(), j);

                    stack.shrink(k);
                    itemstack1.grow(k);
                    flag = true;
                }
            }
        }

        return flag;
    }

    private static int blockReplace(CommandSourceStack source, BlockPos targetPos, int slot, int stackCount, List<ItemStack> stacks, LootCommand.Callback messageSender) throws CommandSyntaxException {
        Container iinventory = LootCommand.getContainer(source, targetPos);
        int k = iinventory.getContainerSize();

        if (slot >= 0 && slot < k) {
            List<ItemStack> list1 = Lists.newArrayListWithCapacity(stacks.size());

            for (int l = 0; l < stackCount; ++l) {
                int i1 = slot + l;
                ItemStack itemstack = l < stacks.size() ? (ItemStack) stacks.get(l) : ItemStack.EMPTY;

                if (iinventory.canPlaceItem(i1, itemstack)) {
                    iinventory.setItem(i1, itemstack);
                    list1.add(itemstack);
                }
            }

            messageSender.accept(list1);
            return list1.size();
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() <= first.getMaxStackSize() && ItemStack.isSameItemSameTags(first, second);
    }

    private static int playerGive(Collection<ServerPlayer> players, List<ItemStack> stacks, LootCommand.Callback messageSender) throws CommandSyntaxException {
        List<ItemStack> list1 = Lists.newArrayListWithCapacity(stacks.size());
        Iterator iterator = stacks.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();
            Iterator iterator1 = players.iterator();

            while (iterator1.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

                if (entityplayer.getInventory().add(itemstack.copy())) {
                    list1.add(itemstack);
                }
            }
        }

        messageSender.accept(list1);
        return list1.size();
    }

    private static void setSlots(Entity entity, List<ItemStack> stacks, int slot, int stackCount, List<ItemStack> addedStacks) {
        for (int k = 0; k < stackCount; ++k) {
            ItemStack itemstack = k < stacks.size() ? (ItemStack) stacks.get(k) : ItemStack.EMPTY;
            SlotAccess slotaccess = entity.getSlot(slot + k);

            if (slotaccess != SlotAccess.NULL && slotaccess.set(itemstack.copy())) {
                addedStacks.add(itemstack);
            }
        }

    }

    private static int entityReplace(Collection<? extends Entity> targets, int slot, int stackCount, List<ItemStack> stacks, LootCommand.Callback messageSender) throws CommandSyntaxException {
        List<ItemStack> list1 = Lists.newArrayListWithCapacity(stacks.size());
        Iterator iterator = targets.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                LootCommand.setSlots(entity, stacks, slot, stackCount, list1);
                entityplayer.containerMenu.broadcastChanges();
            } else {
                LootCommand.setSlots(entity, stacks, slot, stackCount, list1);
            }
        }

        messageSender.accept(list1);
        return list1.size();
    }

    private static int dropInWorld(CommandSourceStack source, Vec3 pos, List<ItemStack> stacks, LootCommand.Callback messageSender) throws CommandSyntaxException {
        ServerLevel worldserver = source.getLevel();

        stacks.removeIf(ItemStack::isEmpty); // CraftBukkit - SPIGOT-6959 Remove empty items for avoid throw an error in new EntityItem
        stacks.forEach((itemstack) -> {
            ItemEntity entityitem = new ItemEntity(worldserver, pos.x, pos.y, pos.z, itemstack.copy());

            entityitem.setDefaultPickUpDelay();
            worldserver.addFreshEntity(entityitem);
        });
        messageSender.accept(stacks);
        return stacks.size();
    }

    private static void callback(CommandSourceStack source, List<ItemStack> stacks) {
        if (stacks.size() == 1) {
            ItemStack itemstack = (ItemStack) stacks.get(0);

            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.single", itemstack.getCount(), itemstack.getDisplayName());
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.multiple", stacks.size());
            }, false);
        }

    }

    private static void callback(CommandSourceStack source, List<ItemStack> stacks, ResourceLocation lootTable) {
        if (stacks.size() == 1) {
            ItemStack itemstack = (ItemStack) stacks.get(0);

            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.single_with_table", itemstack.getCount(), itemstack.getDisplayName(), lootTable);
            }, false);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.drop.success.multiple_with_table", stacks.size(), lootTable);
            }, false);
        }

    }

    private static ItemStack getSourceHandItem(CommandSourceStack source, EquipmentSlot slot) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();

        if (entity instanceof LivingEntity) {
            return ((LivingEntity) entity).getItemBySlot(slot);
        } else {
            throw LootCommand.ERROR_NO_HELD_ITEMS.create(entity.getDisplayName());
        }
    }

    private static int dropBlockLoot(CommandContext<CommandSourceStack> context, BlockPos pos, ItemStack stack, LootCommand.DropConsumer constructor) throws CommandSyntaxException {
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) context.getSource();
        ServerLevel worldserver = commandlistenerwrapper.getLevel();
        BlockState iblockdata = worldserver.getBlockState(pos);
        BlockEntity tileentity = worldserver.getBlockEntity(pos);
        LootParams.Builder lootparams_a = (new LootParams.Builder(worldserver)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.BLOCK_STATE, iblockdata).withOptionalParameter(LootContextParams.BLOCK_ENTITY, tileentity).withOptionalParameter(LootContextParams.THIS_ENTITY, commandlistenerwrapper.getEntity()).withParameter(LootContextParams.TOOL, stack);
        List<ItemStack> list = iblockdata.getDrops(lootparams_a);

        return constructor.accept(context, list, (list1) -> {
            LootCommand.callback(commandlistenerwrapper, list1, iblockdata.getBlock().getLootTable());
        });
    }

    private static int dropKillLoot(CommandContext<CommandSourceStack> context, Entity entity, LootCommand.DropConsumer constructor) throws CommandSyntaxException {
        if (!(entity instanceof LivingEntity)) {
            throw LootCommand.ERROR_NO_LOOT_TABLE.create(entity.getDisplayName());
        } else {
            ResourceLocation minecraftkey = ((LivingEntity) entity).getLootTable();
            CommandSourceStack commandlistenerwrapper = (CommandSourceStack) context.getSource();
            LootParams.Builder lootparams_a = new LootParams.Builder(commandlistenerwrapper.getLevel());
            Entity entity1 = commandlistenerwrapper.getEntity();

            if (entity1 instanceof Player) {
                Player entityhuman = (Player) entity1;

                lootparams_a.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, entityhuman);
            }

            lootparams_a.withParameter(LootContextParams.DAMAGE_SOURCE, entity.damageSources().magic());
            lootparams_a.withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, entity1);
            lootparams_a.withOptionalParameter(LootContextParams.KILLER_ENTITY, entity1);
            lootparams_a.withParameter(LootContextParams.THIS_ENTITY, entity);
            lootparams_a.withParameter(LootContextParams.ORIGIN, commandlistenerwrapper.getPosition());
            LootParams lootparams = lootparams_a.create(LootContextParamSets.ENTITY);
            LootTable loottable = commandlistenerwrapper.getServer().getLootData().getLootTable(minecraftkey);
            List<ItemStack> list = loottable.getRandomItems(lootparams);

            return constructor.accept(context, list, (list1) -> {
                LootCommand.callback(commandlistenerwrapper, list1, minecraftkey);
            });
        }
    }

    private static int dropChestLoot(CommandContext<CommandSourceStack> context, ResourceLocation lootTable, LootCommand.DropConsumer constructor) throws CommandSyntaxException {
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) context.getSource();
        LootParams lootparams = (new LootParams.Builder(commandlistenerwrapper.getLevel())).withOptionalParameter(LootContextParams.THIS_ENTITY, commandlistenerwrapper.getEntity()).withParameter(LootContextParams.ORIGIN, commandlistenerwrapper.getPosition()).create(LootContextParamSets.CHEST);

        return LootCommand.drop(context, lootTable, lootparams, constructor);
    }

    private static int dropFishingLoot(CommandContext<CommandSourceStack> context, ResourceLocation lootTable, BlockPos pos, ItemStack stack, LootCommand.DropConsumer constructor) throws CommandSyntaxException {
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) context.getSource();
        LootParams lootparams = (new LootParams.Builder(commandlistenerwrapper.getLevel())).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, stack).withOptionalParameter(LootContextParams.THIS_ENTITY, commandlistenerwrapper.getEntity()).create(LootContextParamSets.FISHING);

        return LootCommand.drop(context, lootTable, lootparams, constructor);
    }

    private static int drop(CommandContext<CommandSourceStack> context, ResourceLocation lootTable, LootParams lootContextParameters, LootCommand.DropConsumer constructor) throws CommandSyntaxException {
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) context.getSource();
        LootTable loottable = commandlistenerwrapper.getServer().getLootData().getLootTable(lootTable);
        List<ItemStack> list = loottable.getRandomItems(lootContextParameters);

        return constructor.accept(context, list, (list1) -> {
            LootCommand.callback(commandlistenerwrapper, list1);
        });
    }

    @FunctionalInterface
    private interface TailProvider {

        ArgumentBuilder<CommandSourceStack, ?> construct(ArgumentBuilder<CommandSourceStack, ?> builder, LootCommand.DropConsumer target);
    }

    @FunctionalInterface
    private interface DropConsumer {

        int accept(CommandContext<CommandSourceStack> context, List<ItemStack> items, LootCommand.Callback messageSender) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface Callback {

        void accept(List<ItemStack> items) throws CommandSyntaxException;
    }
}
