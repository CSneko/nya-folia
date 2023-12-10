package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Iterator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

public class ReloadCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ReloadCommand() {}

    public static void reloadPacks(Collection<String> dataPacks, CommandSourceStack source) {
        source.getServer().reloadResources(dataPacks, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.COMMAND).exceptionally((throwable) -> {
            ReloadCommand.LOGGER.warn("Failed to execute reload", throwable);
            source.sendFailure(Component.translatable("commands.reload.failure"));
            return null;
        });
    }

    private static Collection<String> discoverNewPacks(PackRepository dataPackManager, WorldData saveProperties, Collection<String> enabledDataPacks) {
        dataPackManager.reload();
        Collection<String> collection1 = Lists.newArrayList(enabledDataPacks);
        Collection<String> collection2 = saveProperties.getDataConfiguration().dataPacks().getDisabled();
        Iterator iterator = dataPackManager.getAvailableIds().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            if (!collection2.contains(s) && !collection1.contains(s)) {
                collection1.add(s);
            }
        }

        return collection1;
    }

    // CraftBukkit start
    public static void reload(MinecraftServer minecraftserver) {
        PackRepository resourcepackrepository = minecraftserver.getPackRepository();
        WorldData savedata = minecraftserver.getWorldData();
        Collection<String> collection = resourcepackrepository.getSelectedIds();
        Collection<String> collection1 = ReloadCommand.discoverNewPacks(resourcepackrepository, savedata, collection);
        minecraftserver.reloadResources(collection1, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN); // Paper
    }
    // CraftBukkit end

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("reload").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).executes((commandcontext) -> {
            CommandSourceStack commandlistenerwrapper = (CommandSourceStack) commandcontext.getSource();
            MinecraftServer minecraftserver = commandlistenerwrapper.getServer();
            PackRepository resourcepackrepository = minecraftserver.getPackRepository();
            WorldData savedata = minecraftserver.getWorldData();
            Collection<String> collection = resourcepackrepository.getSelectedIds();
            Collection<String> collection1 = ReloadCommand.discoverNewPacks(resourcepackrepository, savedata, collection);

            commandlistenerwrapper.sendSuccess(() -> {
                return Component.translatable("commands.reload.success");
            }, true);
            ReloadCommand.reloadPacks(collection1, commandlistenerwrapper);
            return 0;
        }));
    }
}
