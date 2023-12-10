package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EffectCommands {

    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.clear.everything.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.clear.specific.failed"));

    public EffectCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("effect").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("clear").executes((commandcontext) -> {
            return EffectCommands.clearEffects((CommandSourceStack) commandcontext.getSource(), ImmutableList.of(((CommandSourceStack) commandcontext.getSource()).getEntityOrException()));
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return EffectCommands.clearEffects((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"));
        })).then(net.minecraft.commands.Commands.argument("effect", ResourceArgument.resource(registryAccess, Registries.MOB_EFFECT)).executes((commandcontext) -> {
            return EffectCommands.clearEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"));
        }))))).then(net.minecraft.commands.Commands.literal("give").then(net.minecraft.commands.Commands.argument("targets", EntityArgument.entities()).then(((RequiredArgumentBuilder) ((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("effect", ResourceArgument.resource(registryAccess, Registries.MOB_EFFECT)).executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), (Integer) null, 0, true);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("seconds", IntegerArgumentType.integer(1, 1000000)).executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), 0, true);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("amplifier", IntegerArgumentType.integer(0, 255)).executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), IntegerArgumentType.getInteger(commandcontext, "amplifier"), true);
        })).then(net.minecraft.commands.Commands.argument("hideParticles", BoolArgumentType.bool()).executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), IntegerArgumentType.getInteger(commandcontext, "seconds"), IntegerArgumentType.getInteger(commandcontext, "amplifier"), !BoolArgumentType.getBool(commandcontext, "hideParticles"));
        }))))).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("infinite").executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), -1, 0, true);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("amplifier", IntegerArgumentType.integer(0, 255)).executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), -1, IntegerArgumentType.getInteger(commandcontext, "amplifier"), true);
        })).then(net.minecraft.commands.Commands.argument("hideParticles", BoolArgumentType.bool()).executes((commandcontext) -> {
            return EffectCommands.giveEffect((CommandSourceStack) commandcontext.getSource(), EntityArgument.getEntities(commandcontext, "targets"), ResourceArgument.getMobEffect(commandcontext, "effect"), -1, IntegerArgumentType.getInteger(commandcontext, "amplifier"), !BoolArgumentType.getBool(commandcontext, "hideParticles"));
        }))))))));
    }

    private static int giveEffect(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> statusEffect, @Nullable Integer seconds, int amplifier, boolean showParticles) throws CommandSyntaxException {
        MobEffect mobeffectlist = (MobEffect) statusEffect.value();
        int j = 0;
        int k;

        if (seconds != null) {
            if (mobeffectlist.isInstantenous()) {
                k = seconds;
            } else if (seconds == -1) {
                k = -1;
            } else {
                k = seconds * 20;
            }
        } else if (mobeffectlist.isInstantenous()) {
            k = 1;
        } else {
            k = 600;
        }

        Iterator iterator = targets.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) {
                MobEffectInstance mobeffect = new MobEffectInstance(mobeffectlist, k, amplifier, false, showParticles);

                // Folia start - region threading
                entity.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
                    if (!(nmsEntity instanceof LivingEntity)) {
                        return;
                    }
                    ((LivingEntity) nmsEntity).addEffect(mobeffect, null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND);
                }, null, 1L);
                // Folia end - region threading
                if (true) { // CraftBukkit // Folia - region threading
                    ++j;
                }
            }
        }

        if (j == 0) {
            throw EffectCommands.ERROR_GIVE_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.give.success.single", mobeffectlist.getDisplayName(), ((Entity) targets.iterator().next()).getDisplayName(), k / 20);
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.give.success.multiple", mobeffectlist.getDisplayName(), targets.size(), k / 20);
                }, true);
            }

            return j;
        }
    }

    private static int clearEffects(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int i = 0;
        Iterator iterator = targets.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) { // CraftBukkit // Folia - region threading
                ++i;
                // Folia start - region threading
                entity.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
                    if (!(nmsEntity instanceof LivingEntity)) {
                        return;
                    }
                    ((LivingEntity) nmsEntity).removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND);
                }, null, 1L);
                // Folia end - region threading
            }
        }

        if (i == 0) {
            throw EffectCommands.ERROR_CLEAR_EVERYTHING_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.everything.success.single", ((Entity) targets.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.everything.success.multiple", targets.size());
                }, true);
            }

            return i;
        }
    }

    private static int clearEffect(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> statusEffect) throws CommandSyntaxException {
        MobEffect mobeffectlist = (MobEffect) statusEffect.value();
        int i = 0;
        Iterator iterator = targets.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) { // CraftBukkit // Folia - region threading
                ++i;
                // Folia start - region threading
                entity.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
                    if (!(nmsEntity instanceof LivingEntity)) {
                        return;
                    }
                    ((LivingEntity) nmsEntity).removeEffect(mobeffectlist, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.COMMAND);
                }, null, 1L);
                // Folia end - region threading
            }
        }

        if (i == 0) {
            throw EffectCommands.ERROR_CLEAR_SPECIFIC_FAILED.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.specific.success.single", mobeffectlist.getDisplayName(), ((Entity) targets.iterator().next()).getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.effect.clear.specific.success.multiple", mobeffectlist.getDisplayName(), targets.size());
                }, true);
            }

            return i;
        }
    }
}
