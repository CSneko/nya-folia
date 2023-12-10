package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Optional;
import net.minecraft.ResourceLocationException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class PlaceCommand {

    private static final SimpleCommandExceptionType ERROR_FEATURE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.feature.failed"));
    private static final SimpleCommandExceptionType ERROR_JIGSAW_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.jigsaw.failed"));
    private static final SimpleCommandExceptionType ERROR_STRUCTURE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.structure.failed"));
    private static final DynamicCommandExceptionType ERROR_TEMPLATE_INVALID = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("commands.place.template.invalid", object);
    });
    private static final SimpleCommandExceptionType ERROR_TEMPLATE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.template.failed"));
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TEMPLATES = (commandcontext, suggestionsbuilder) -> {
        StructureTemplateManager structuretemplatemanager = ((CommandSourceStack) commandcontext.getSource()).getLevel().getStructureManager();

        return SharedSuggestionProvider.suggestResource(structuretemplatemanager.listTemplates(), suggestionsbuilder);
    };

    public PlaceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("place").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(net.minecraft.commands.Commands.literal("feature").then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("feature", ResourceKeyArgument.key(Registries.CONFIGURED_FEATURE)).executes((commandcontext) -> {
            return PlaceCommand.placeFeature((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getConfiguredFeature(commandcontext, "feature"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()));
        })).then(net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return PlaceCommand.placeFeature((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getConfiguredFeature(commandcontext, "feature"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"));
        }))))).then(net.minecraft.commands.Commands.literal("jigsaw").then(net.minecraft.commands.Commands.argument("pool", ResourceKeyArgument.key(Registries.TEMPLATE_POOL)).then(net.minecraft.commands.Commands.argument("target", ResourceLocationArgument.id()).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("max_depth", IntegerArgumentType.integer(1, 7)).executes((commandcontext) -> {
            return PlaceCommand.placeJigsaw((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructureTemplatePool(commandcontext, "pool"), ResourceLocationArgument.getId(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "max_depth"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()));
        })).then(net.minecraft.commands.Commands.argument("position", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return PlaceCommand.placeJigsaw((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructureTemplatePool(commandcontext, "pool"), ResourceLocationArgument.getId(commandcontext, "target"), IntegerArgumentType.getInteger(commandcontext, "max_depth"), BlockPosArgument.getLoadedBlockPos(commandcontext, "position"));
        }))))))).then(net.minecraft.commands.Commands.literal("structure").then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE)).executes((commandcontext) -> {
            return PlaceCommand.placeStructure((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructure(commandcontext, "structure"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()));
        })).then(net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return PlaceCommand.placeStructure((CommandSourceStack) commandcontext.getSource(), ResourceKeyArgument.getStructure(commandcontext, "structure"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"));
        }))))).then(net.minecraft.commands.Commands.literal("template").then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("template", ResourceLocationArgument.id()).suggests(PlaceCommand.SUGGEST_TEMPLATES).executes((commandcontext) -> {
            return PlaceCommand.placeTemplate((CommandSourceStack) commandcontext.getSource(), ResourceLocationArgument.getId(commandcontext, "template"), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), Rotation.NONE, Mirror.NONE, 1.0F, 0);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return PlaceCommand.placeTemplate((CommandSourceStack) commandcontext.getSource(), ResourceLocationArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), Rotation.NONE, Mirror.NONE, 1.0F, 0);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("rotation", TemplateRotationArgument.templateRotation()).executes((commandcontext) -> {
            return PlaceCommand.placeTemplate((CommandSourceStack) commandcontext.getSource(), ResourceLocationArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), Mirror.NONE, 1.0F, 0);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("mirror", TemplateMirrorArgument.templateMirror()).executes((commandcontext) -> {
            return PlaceCommand.placeTemplate((CommandSourceStack) commandcontext.getSource(), ResourceLocationArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), 1.0F, 0);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("integrity", FloatArgumentType.floatArg(0.0F, 1.0F)).executes((commandcontext) -> {
            return PlaceCommand.placeTemplate((CommandSourceStack) commandcontext.getSource(), ResourceLocationArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), FloatArgumentType.getFloat(commandcontext, "integrity"), 0);
        })).then(net.minecraft.commands.Commands.argument("seed", IntegerArgumentType.integer()).executes((commandcontext) -> {
            return PlaceCommand.placeTemplate((CommandSourceStack) commandcontext.getSource(), ResourceLocationArgument.getId(commandcontext, "template"), BlockPosArgument.getLoadedBlockPos(commandcontext, "pos"), TemplateRotationArgument.getRotation(commandcontext, "rotation"), TemplateMirrorArgument.getMirror(commandcontext, "mirror"), FloatArgumentType.getFloat(commandcontext, "integrity"), IntegerArgumentType.getInteger(commandcontext, "seed"));
        })))))))));
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    public static int placeFeature(CommandSourceStack source, Holder.Reference<ConfiguredFeature<?, ?>> feature, BlockPos pos) throws CommandSyntaxException {
        ServerLevel worldserver = source.getLevel();
        ConfiguredFeature<?, ?> worldgenfeatureconfigured = (ConfiguredFeature) feature.value();
        ChunkPos chunkcoordintpair = new ChunkPos(pos);

        PlaceCommand.checkLoaded(worldserver, new ChunkPos(chunkcoordintpair.x - 1, chunkcoordintpair.z - 1), new ChunkPos(chunkcoordintpair.x + 1, chunkcoordintpair.z + 1));
        // Folia start - region threading
        worldserver.loadChunksAsync(
            pos, 16, net.minecraft.world.level.chunk.ChunkStatus.FULL,
            ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.NORMAL,
            (chunks) -> {
                try {
        // Folia end - region threading
        if (!worldgenfeatureconfigured.place(worldserver, worldserver.getChunkSource().getGenerator(), worldserver.getRandom(), pos)) {
            throw PlaceCommand.ERROR_FEATURE_FAILED.create();
        } else {
            String s = feature.key().location().toString();

            source.sendSuccess(() -> {
                return Component.translatable("commands.place.feature.success", s, pos.getX(), pos.getY(), pos.getZ());
            }, true);
            return; // Folia - region threading
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public static int placeJigsaw(CommandSourceStack source, Holder<StructureTemplatePool> structurePool, ResourceLocation id, int maxDepth, BlockPos pos) throws CommandSyntaxException {
        ServerLevel worldserver = source.getLevel();

        // Folia start - region threading
        worldserver.loadChunksAsync(
            pos, 16, net.minecraft.world.level.chunk.ChunkStatus.FULL,
            ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.NORMAL,
            (chunks) -> {
                try {
        // Folia end - region threading
        if (!JigsawPlacement.generateJigsaw(worldserver, structurePool, id, maxDepth, pos, false)) {
            throw PlaceCommand.ERROR_JIGSAW_FAILED.create();
        } else {
            source.sendSuccess(() -> {
                return Component.translatable("commands.place.jigsaw.success", pos.getX(), pos.getY(), pos.getZ());
            }, true);
            return; // Folia start - region threading
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public static int placeStructure(CommandSourceStack source, Holder.Reference<Structure> structure, BlockPos pos) throws CommandSyntaxException {
        ServerLevel worldserver = source.getLevel();
        Structure structure1 = (Structure) structure.value();
        ChunkGenerator chunkgenerator = worldserver.getChunkSource().getGenerator();
        // Folia start - region threading
        worldserver.loadChunksAsync(
            pos, 16, net.minecraft.world.level.chunk.ChunkStatus.FULL,
            ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.NORMAL,
            (chunks) -> {
                try {
        // Folia end - region threading
        StructureStart structurestart = structure1.generate(source.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(), worldserver.getChunkSource().randomState(), worldserver.getStructureManager(), worldserver.getSeed(), new ChunkPos(pos), 0, worldserver, (holder) -> {
            return true;
        });

        if (!structurestart.isValid()) {
            throw PlaceCommand.ERROR_STRUCTURE_FAILED.create();
        } else {
            structurestart.generationEventCause = org.bukkit.event.world.AsyncStructureGenerateEvent.Cause.COMMAND; // CraftBukkit - set AsyncStructureGenerateEvent.Cause.COMMAND as generation cause
            BoundingBox structureboundingbox = structurestart.getBoundingBox();
            ChunkPos chunkcoordintpair = new ChunkPos(SectionPos.blockToSectionCoord(structureboundingbox.minX()), SectionPos.blockToSectionCoord(structureboundingbox.minZ()));
            ChunkPos chunkcoordintpair1 = new ChunkPos(SectionPos.blockToSectionCoord(structureboundingbox.maxX()), SectionPos.blockToSectionCoord(structureboundingbox.maxZ()));

            PlaceCommand.checkLoaded(worldserver, chunkcoordintpair, chunkcoordintpair1);
            ChunkPos.rangeClosed(chunkcoordintpair, chunkcoordintpair1).forEach((chunkcoordintpair2) -> {
                structurestart.placeInChunk(worldserver, worldserver.structureManager(), chunkgenerator, worldserver.getRandom(), new BoundingBox(chunkcoordintpair2.getMinBlockX(), worldserver.getMinBuildHeight(), chunkcoordintpair2.getMinBlockZ(), chunkcoordintpair2.getMaxBlockX(), worldserver.getMaxBuildHeight(), chunkcoordintpair2.getMaxBlockZ()), chunkcoordintpair2);
            });
            String s = structure.key().location().toString();

            source.sendSuccess(() -> {
                return Component.translatable("commands.place.structure.success", s, pos.getX(), pos.getY(), pos.getZ());
            }, true);
            return; // Folia - region threading
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public static int placeTemplate(CommandSourceStack source, ResourceLocation id, BlockPos pos, Rotation rotation, Mirror mirror, float integrity, int seed) throws CommandSyntaxException {
        ServerLevel worldserver = source.getLevel();
        // Folia start - region threading
        worldserver.loadChunksAsync(
            pos, 16, net.minecraft.world.level.chunk.ChunkStatus.FULL,
            ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.NORMAL,
            (chunks) -> {
                try {
        // Folia end - region threading
        StructureTemplateManager structuretemplatemanager = worldserver.getStructureManager();

        Optional optional;

        try {
            optional = structuretemplatemanager.get(id);
        } catch (ResourceLocationException resourcekeyinvalidexception) {
            throw PlaceCommand.ERROR_TEMPLATE_INVALID.create(id);
        }

        if (optional.isEmpty()) {
            throw PlaceCommand.ERROR_TEMPLATE_INVALID.create(id);
        } else {
            StructureTemplate definedstructure = (StructureTemplate) optional.get();

            PlaceCommand.checkLoaded(worldserver, new ChunkPos(pos), new ChunkPos(pos.offset(definedstructure.getSize())));
            StructurePlaceSettings definedstructureinfo = (new StructurePlaceSettings()).setMirror(mirror).setRotation(rotation);

            if (integrity < 1.0F) {
                definedstructureinfo.clearProcessors().addProcessor(new BlockRotProcessor(integrity)).setRandom(StructureBlockEntity.createRandom((long) seed));
            }

            boolean flag = definedstructure.placeInWorld(worldserver, pos, pos, definedstructureinfo, StructureBlockEntity.createRandom((long) seed), 2);

            if (!flag) {
                throw PlaceCommand.ERROR_TEMPLATE_FAILED.create();
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable("commands.place.template.success", id, pos.getX(), pos.getY(), pos.getZ());
                }, true);
                return; // Folia - region threading
            }
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    private static void checkLoaded(ServerLevel world, ChunkPos pos1, ChunkPos pos2) throws CommandSyntaxException {
        if (ChunkPos.rangeClosed(pos1, pos2).filter((chunkcoordintpair2) -> {
            return !world.isLoaded(chunkcoordintpair2.getWorldPosition());
        }).findAny().isPresent()) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
    }
}
