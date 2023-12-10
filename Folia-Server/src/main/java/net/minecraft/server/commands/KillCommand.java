package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class KillCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kill").requires((source) -> {
            return source.hasPermission(2);
        }).executes((context) -> {
            return kill(context.getSource(), ImmutableList.of(context.getSource().getEntityOrException()));
        }).then(Commands.argument("targets", EntityArgument.entities()).executes((context) -> {
            return kill(context.getSource(), EntityArgument.getEntities(context, "targets"));
        })));
    }

    private static int kill(CommandSourceStack source, Collection<? extends Entity> targets) {
        for(Entity entity : targets) {
            entity.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> { // Folia - region threading
            nmsEntity.kill(); // Folia - region threading
            }, null, 1L); // Folia - region threading
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> {
                return Component.translatable("commands.kill.success.single", targets.iterator().next().getDisplayName());
            }, true);
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.kill.success.multiple", targets.size());
            }, true);
        }

        return targets.size();
    }
}
