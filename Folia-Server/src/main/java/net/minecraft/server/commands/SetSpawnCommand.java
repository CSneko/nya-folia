package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class SetSpawnCommand {

    public SetSpawnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("spawnpoint").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).executes((commandcontext) -> {
            return SetSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), Collections.singleton(((CommandSourceStack) commandcontext.getSource()).getPlayerOrException()), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), 0.0F);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("targets", EntityArgument.players()).executes((commandcontext) -> {
            return SetSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), 0.0F);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return SetSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), 0.0F);
        })).then(net.minecraft.commands.Commands.argument("angle", AngleArgument.angle()).executes((commandcontext) -> {
            return SetSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), EntityArgument.getPlayers(commandcontext, "targets"), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), AngleArgument.getAngle(commandcontext, "angle"));
        })))));
    }

    private static int setSpawn(CommandSourceStack source, Collection<ServerPlayer> targets, BlockPos pos, float angle) {
        ResourceKey<Level> resourcekey = source.getLevel().dimension();
        Iterator iterator = targets.iterator();

        final Collection<ServerPlayer> actualTargets = new java.util.ArrayList<>(); // Paper
        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            // Paper start - PlayerSetSpawnEvent
            // Folia start - region threading
            entityplayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                player.setRespawnPosition(resourcekey, pos, angle, true, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.COMMAND);
            }, null, 1L);
            if (true) { // Folia end - region threading
                actualTargets.add(entityplayer);
            }
            // Paper end
        }
        // Paper start
        if (actualTargets.isEmpty()) {
            return 0;
        }
        // Paper end

        String s = resourcekey.location().toString();

        if (actualTargets.size() == 1) { // Paper
            source.sendSuccess(() -> {
                return Component.translatable("commands.spawnpoint.success.single", pos.getX(), pos.getY(), pos.getZ(), angle, s, ((ServerPlayer) actualTargets.iterator().next()).getDisplayName()); // Paper
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.spawnpoint.success.multiple", pos.getX(), pos.getY(), pos.getZ(), angle, s, actualTargets.size()); // Paper
            }, true);
        }

        return actualTargets.size(); // Paper
    }
}
