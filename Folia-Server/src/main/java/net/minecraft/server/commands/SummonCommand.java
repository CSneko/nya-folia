package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SummonCommand {

    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed"));
    private static final SimpleCommandExceptionType ERROR_DUPLICATE_UUID = new SimpleCommandExceptionType(Component.translatable("commands.summon.failed.uuid"));
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));

    public SummonCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("summon").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("entity", ResourceArgument.resource(registryAccess, Registries.ENTITY_TYPE)).suggests(SuggestionProviders.SUMMONABLE_ENTITIES).executes((commandcontext) -> {
            return SummonCommand.spawnEntity((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"), ((CommandSourceStack) commandcontext.getSource()).getPosition(), new CompoundTag(), true);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("pos", Vec3Argument.vec3()).executes((commandcontext) -> {
            return SummonCommand.spawnEntity((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"), Vec3Argument.getVec3(commandcontext, "pos"), new CompoundTag(), true);
        })).then(net.minecraft.commands.Commands.argument("nbt", CompoundTagArgument.compoundTag()).executes((commandcontext) -> {
            return SummonCommand.spawnEntity((CommandSourceStack) commandcontext.getSource(), ResourceArgument.getSummonableEntityType(commandcontext, "entity"), Vec3Argument.getVec3(commandcontext, "pos"), CompoundTagArgument.getCompoundTag(commandcontext, "nbt"), false);
        })))));
    }

    public static Entity createEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType, Vec3 pos, CompoundTag nbt, boolean initialize) throws CommandSyntaxException {
        BlockPos blockposition = BlockPos.containing(pos);

        if (!Level.isInSpawnableBounds(blockposition)) {
            throw SummonCommand.INVALID_POSITION.create();
        } else {
            CompoundTag nbttagcompound1 = nbt.copy();

            nbttagcompound1.putString("id", entityType.key().location().toString());
            ServerLevel worldserver = source.getLevel();
            Entity entity = EntityType.loadEntityRecursive(nbttagcompound1, worldserver, (entity1) -> {
                entity1.moveTo(pos.x, pos.y, pos.z, entity1.getYRot(), entity1.getXRot());
                entity1.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND; // Paper
                return entity1;
            });

            if (entity == null) {
                throw SummonCommand.ERROR_FAILED.create();
            } else {
                // Folia start - region threading
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    worldserver, entity.chunkPosition().x, entity.chunkPosition().z, () -> {
                        if (initialize && entity instanceof Mob) {
                            ((Mob) entity).finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, (SpawnGroupData) null, (CompoundTag) null);
                        }
                        worldserver.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND);
                    }
                );
                // Folia end - region threading

                if (false) { // CraftBukkit - pass a spawn reason of "COMMAND" // Folia - region threading
                    throw SummonCommand.ERROR_DUPLICATE_UUID.create();
                } else {
                    return entity;
                }
            }
        }
    }

    private static int spawnEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType, Vec3 pos, CompoundTag nbt, boolean initialize) throws CommandSyntaxException {
        Entity entity = SummonCommand.createEntity(source, entityType, pos, nbt, initialize);

        source.sendSuccess(() -> {
            return Component.translatable("commands.summon.success", entity.getDisplayName());
        }, true);
        return 1;
    }
}
