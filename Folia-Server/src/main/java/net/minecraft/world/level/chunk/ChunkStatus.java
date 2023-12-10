package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class ChunkStatus {

    // Paper start - rewrite chunk system
    public boolean isParallelCapable; // Paper
    public int writeRadius = -1;
    public int loadRange = 0;

    protected static final java.util.List<ChunkStatus> statuses = new java.util.ArrayList<>();

    private ChunkStatus nextStatus;

    public final ChunkStatus getNextStatus() {
        return this.nextStatus;
    }

    public final boolean isEmptyLoadStatus() {
        return this.loadingTask == PASSTHROUGH_LOAD_TASK;
    }

    public final boolean isEmptyGenStatus() {
        return this == ChunkStatus.EMPTY;
    }

    public final java.util.concurrent.atomic.AtomicBoolean warnedAboutNoImmediateComplete = new java.util.concurrent.atomic.AtomicBoolean();
    // Paper end - rewrite chunk system

    public static final int MAX_STRUCTURE_DISTANCE = 8;
    private static final EnumSet<Heightmap.Types> PRE_FEATURES = EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
    public static final EnumSet<Heightmap.Types> POST_FEATURES = EnumSet.of(Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    private static final ChunkStatus.LoadingTask PASSTHROUGH_LOAD_TASK = (chunkstatus, worldserver, structuretemplatemanager, lightenginethreaded, function, ichunkaccess) -> {
        return CompletableFuture.completedFuture(Either.left(ichunkaccess));
    };
    public static final ChunkStatus EMPTY = ChunkStatus.registerSimple("empty", (ChunkStatus) null, -1, ChunkStatus.PRE_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, worldserver, chunkgenerator, list, ichunkaccess) -> {
    });
    public static final ChunkStatus STRUCTURE_STARTS = ChunkStatus.register("structure_starts", ChunkStatus.EMPTY, 0, false, ChunkStatus.PRE_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, executor, worldserver, chunkgenerator, structuretemplatemanager, lightenginethreaded, function, list, ichunkaccess) -> {
        if (worldserver.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            chunkgenerator.createStructures(worldserver.registryAccess(), worldserver.getChunkSource().getGeneratorState(), worldserver.structureManager(), ichunkaccess, structuretemplatemanager);
        }

        worldserver.onStructureStartsAvailable(ichunkaccess);
        return CompletableFuture.completedFuture(Either.left(ichunkaccess));
    }, (chunkstatus, worldserver, structuretemplatemanager, lightenginethreaded, function, ichunkaccess) -> {
        worldserver.onStructureStartsAvailable(ichunkaccess);
        return CompletableFuture.completedFuture(Either.left(ichunkaccess));
    });
    public static final ChunkStatus STRUCTURE_REFERENCES = ChunkStatus.registerSimple("structure_references", ChunkStatus.STRUCTURE_STARTS, 8, ChunkStatus.PRE_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, worldserver, chunkgenerator, list, ichunkaccess) -> {
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, list, chunkstatus, -1);

        chunkgenerator.createReferences(regionlimitedworldaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess);
    });
    public static final ChunkStatus BIOMES = ChunkStatus.register("biomes", ChunkStatus.STRUCTURE_REFERENCES, 8, ChunkStatus.PRE_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, executor, worldserver, chunkgenerator, structuretemplatemanager, lightenginethreaded, function, list, ichunkaccess) -> {
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, list, chunkstatus, -1);

        return chunkgenerator.createBiomes(executor, worldserver.getChunkSource().randomState(), Blender.of(regionlimitedworldaccess), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess).thenApply((ichunkaccess1) -> {
            return Either.left(ichunkaccess1);
        });
    });
    public static final ChunkStatus NOISE = ChunkStatus.register("noise", ChunkStatus.BIOMES, 8, ChunkStatus.PRE_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, executor, worldserver, chunkgenerator, structuretemplatemanager, lightenginethreaded, function, list, ichunkaccess) -> {
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, list, chunkstatus, 0);

        return chunkgenerator.fillFromNoise(executor, Blender.of(regionlimitedworldaccess), worldserver.getChunkSource().randomState(), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess).thenApply((ichunkaccess1) -> {
            if (ichunkaccess1 instanceof ProtoChunk) {
                ProtoChunk protochunk = (ProtoChunk) ichunkaccess1;
                BelowZeroRetrogen belowzeroretrogen = protochunk.getBelowZeroRetrogen();

                if (belowzeroretrogen != null) {
                    BelowZeroRetrogen.replaceOldBedrock(protochunk);
                    if (belowzeroretrogen.hasBedrockHoles()) {
                        belowzeroretrogen.applyBedrockMask(protochunk);
                    }
                }
            }

            return Either.left(ichunkaccess1);
        });
    });
    public static final ChunkStatus SURFACE = ChunkStatus.registerSimple("surface", ChunkStatus.NOISE, 8, ChunkStatus.PRE_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, worldserver, chunkgenerator, list, ichunkaccess) -> {
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, list, chunkstatus, 0);

        chunkgenerator.buildSurface(regionlimitedworldaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), worldserver.getChunkSource().randomState(), ichunkaccess);
    });
    public static final ChunkStatus CARVERS = ChunkStatus.registerSimple("carvers", ChunkStatus.SURFACE, 8, ChunkStatus.POST_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, worldserver, chunkgenerator, list, ichunkaccess) -> {
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, list, chunkstatus, 0);

        if (ichunkaccess instanceof ProtoChunk) {
            ProtoChunk protochunk = (ProtoChunk) ichunkaccess;

            Blender.addAroundOldChunksCarvingMaskFilter(regionlimitedworldaccess, protochunk);
        }

        chunkgenerator.applyCarvers(regionlimitedworldaccess, worldserver.getSeed(), worldserver.getChunkSource().randomState(), worldserver.getBiomeManager(), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess, GenerationStep.Carving.AIR);
    });
    public static final ChunkStatus FEATURES = ChunkStatus.registerSimple("features", ChunkStatus.CARVERS, 8, ChunkStatus.POST_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, worldserver, chunkgenerator, list, ichunkaccess) -> {
        Heightmap.primeHeightmaps(ichunkaccess, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, list, chunkstatus, 1);

        chunkgenerator.applyBiomeDecoration(regionlimitedworldaccess, ichunkaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess));
        Blender.generateBorderTicks(regionlimitedworldaccess, ichunkaccess);
    });
    public static final ChunkStatus INITIALIZE_LIGHT = ChunkStatus.register("initialize_light", ChunkStatus.FEATURES, 0, false, ChunkStatus.POST_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, executor, worldserver, chunkgenerator, structuretemplatemanager, lightenginethreaded, function, list, ichunkaccess) -> {
        return ChunkStatus.initializeLight(lightenginethreaded, ichunkaccess);
    }, (chunkstatus, worldserver, structuretemplatemanager, lightenginethreaded, function, ichunkaccess) -> {
        return ChunkStatus.initializeLight(lightenginethreaded, ichunkaccess);
    });
    public static final ChunkStatus LIGHT = ChunkStatus.register("light", ChunkStatus.INITIALIZE_LIGHT, 1, true, ChunkStatus.POST_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, executor, worldserver, chunkgenerator, structuretemplatemanager, lightenginethreaded, function, list, ichunkaccess) -> {
        return ChunkStatus.lightChunk(lightenginethreaded, ichunkaccess);
    }, (chunkstatus, worldserver, structuretemplatemanager, lightenginethreaded, function, ichunkaccess) -> {
        return ChunkStatus.lightChunk(lightenginethreaded, ichunkaccess);
    });
    public static final ChunkStatus SPAWN = ChunkStatus.registerSimple("spawn", ChunkStatus.LIGHT, 0, ChunkStatus.POST_FEATURES, ChunkStatus.ChunkType.PROTOCHUNK, (chunkstatus, worldserver, chunkgenerator, list, ichunkaccess) -> {
        if (!ichunkaccess.isUpgrading()) {
            chunkgenerator.spawnOriginalMobs(new WorldGenRegion(worldserver, list, chunkstatus, -1));
        }

    });
    public static final ChunkStatus FULL = ChunkStatus.register("full", ChunkStatus.SPAWN, 0, false, ChunkStatus.POST_FEATURES, ChunkStatus.ChunkType.LEVELCHUNK, (chunkstatus, executor, worldserver, chunkgenerator, structuretemplatemanager, lightenginethreaded, function, list, ichunkaccess) -> {
        return (CompletableFuture) function.apply(ichunkaccess);
    }, (chunkstatus, worldserver, structuretemplatemanager, lightenginethreaded, function, ichunkaccess) -> {
        return (CompletableFuture) function.apply(ichunkaccess);
    });
    private static final List<ChunkStatus> STATUS_BY_RANGE = ImmutableList.of(ChunkStatus.FULL, ChunkStatus.INITIALIZE_LIGHT, ChunkStatus.CARVERS, ChunkStatus.BIOMES, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_STARTS, new ChunkStatus[0]);
    private static final IntList RANGE_BY_STATUS = (IntList) Util.make(new IntArrayList(ChunkStatus.getStatusList().size()), (intarraylist) -> {
        int i = 0;

        for (int j = ChunkStatus.getStatusList().size() - 1; j >= 0; --j) {
            while (i + 1 < ChunkStatus.STATUS_BY_RANGE.size() && j <= ((ChunkStatus) ChunkStatus.STATUS_BY_RANGE.get(i + 1)).getIndex()) {
                ++i;
            }

            intarraylist.add(0, i);
        }

    });
    private final int index;
    private final ChunkStatus parent;
    private final ChunkStatus.GenerationTask generationTask;
    private final ChunkStatus.LoadingTask loadingTask;
    private final int range;
    private final boolean hasLoadDependencies;
    private final ChunkStatus.ChunkType chunkType;
    private final EnumSet<Heightmap.Types> heightmapsAfter;

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> initializeLight(ThreadedLevelLightEngine lightingProvider, ChunkAccess chunk) {
        chunk.initializeLightSources();
        ((ProtoChunk) chunk).setLightEngine(lightingProvider);
        boolean flag = ChunkStatus.isLighted(chunk);

        return CompletableFuture.completedFuture(Either.left(chunk)); // Paper - rewrite chunk system
    }

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> lightChunk(ThreadedLevelLightEngine lightingProvider, ChunkAccess chunk) {
        boolean flag = ChunkStatus.isLighted(chunk);

        return CompletableFuture.completedFuture(Either.left(chunk)); // Paper - rewrite chunk system
    }

    private static ChunkStatus registerSimple(String id, @Nullable ChunkStatus previous, int taskMargin, EnumSet<Heightmap.Types> heightMapTypes, ChunkStatus.ChunkType chunkType, ChunkStatus.SimpleGenerationTask task) {
        return ChunkStatus.register(id, previous, taskMargin, heightMapTypes, chunkType, task);
    }

    private static ChunkStatus register(String id, @Nullable ChunkStatus previous, int taskMargin, EnumSet<Heightmap.Types> heightMapTypes, ChunkStatus.ChunkType chunkType, ChunkStatus.GenerationTask task) {
        return ChunkStatus.register(id, previous, taskMargin, false, heightMapTypes, chunkType, task, ChunkStatus.PASSTHROUGH_LOAD_TASK);
    }

    private static ChunkStatus register(String id, @Nullable ChunkStatus previous, int taskMargin, boolean shouldAlwaysUpgrade, EnumSet<Heightmap.Types> heightMapTypes, ChunkStatus.ChunkType chunkType, ChunkStatus.GenerationTask generationTask, ChunkStatus.LoadingTask loadTask) {
        return (ChunkStatus) Registry.register(BuiltInRegistries.CHUNK_STATUS, id, new ChunkStatus(previous, taskMargin, shouldAlwaysUpgrade, heightMapTypes, chunkType, generationTask, loadTask));
    }

    public static List<ChunkStatus> getStatusList() {
        List<ChunkStatus> list = Lists.newArrayList();

        ChunkStatus chunkstatus;

        for (chunkstatus = ChunkStatus.FULL; chunkstatus.getParent() != chunkstatus; chunkstatus = chunkstatus.getParent()) {
            list.add(chunkstatus);
        }

        list.add(chunkstatus);
        Collections.reverse(list);
        return list;
    }

    private static boolean isLighted(ChunkAccess chunk) {
        return chunk.getStatus().isOrAfter(ChunkStatus.LIGHT) && chunk.isLightCorrect();
    }

    public static ChunkStatus getStatusAroundFullChunk(int level) {
        return level >= ChunkStatus.STATUS_BY_RANGE.size() ? ChunkStatus.EMPTY : (level < 0 ? ChunkStatus.FULL : (ChunkStatus) ChunkStatus.STATUS_BY_RANGE.get(level));
    }

    public static int maxDistance() {
        return ChunkStatus.STATUS_BY_RANGE.size();
    }

    public static int getDistance(ChunkStatus status) {
        return ChunkStatus.RANGE_BY_STATUS.getInt(status.getIndex());
    }

    ChunkStatus(@Nullable ChunkStatus previous, int taskMargin, boolean shouldAlwaysUpgrade, EnumSet<Heightmap.Types> heightMapTypes, ChunkStatus.ChunkType chunkType, ChunkStatus.GenerationTask generationTask, ChunkStatus.LoadingTask loadTask) {
        this.parent = previous == null ? this : previous;
        this.generationTask = generationTask;
        this.loadingTask = loadTask;
        this.range = taskMargin;
        this.hasLoadDependencies = shouldAlwaysUpgrade;
        this.chunkType = chunkType;
        this.heightmapsAfter = heightMapTypes;
        this.index = previous == null ? 0 : previous.getIndex() + 1;
        // Paper start
        this.nextStatus = this;
        if (statuses.size() > 0) {
            statuses.get(statuses.size() - 1).nextStatus = this;
        }
        statuses.add(this);
        // Paper end
    }

    public int getIndex() {
        return this.index;
    }

    public ChunkStatus getParent() {
        return this.parent;
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generate(Executor executor, ServerLevel world, ChunkGenerator generator, StructureTemplateManager structureTemplateManager, ThreadedLevelLightEngine lightingProvider, Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter, List<ChunkAccess> chunks) {
        ChunkAccess ichunkaccess = (ChunkAccess) chunks.get(chunks.size() / 2);
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onChunkGenerate(ichunkaccess.getPos(), world.dimension(), this.toString());

        return this.generationTask.doWork(this, executor, world, generator, structureTemplateManager, lightingProvider, fullChunkConverter, chunks, ichunkaccess).thenApply((either) -> {
            either.ifLeft((ichunkaccess1) -> {
                if (ichunkaccess1 instanceof ProtoChunk) {
                    ProtoChunk protochunk = (ProtoChunk) ichunkaccess1;

                    if (!protochunk.getStatus().isOrAfter(this)) {
                        protochunk.setStatus(this);
                    }
                }

            });
            if (profiledduration != null) {
                profiledduration.finish();
            }

            return either;
        });
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> load(ServerLevel world, StructureTemplateManager structureTemplateManager, ThreadedLevelLightEngine lightingProvider, Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter, ChunkAccess chunk) {
        return this.loadingTask.doWork(this, world, structureTemplateManager, lightingProvider, fullChunkConverter, chunk);
    }

    public int getRange() {
        return this.range;
    }

    public boolean hasLoadDependencies() {
        return this.hasLoadDependencies;
    }

    public ChunkStatus.ChunkType getChunkType() {
        return this.chunkType;
    }

    // Paper start
    public static ChunkStatus getStatus(String name) {
        try {
            // We need this otherwise we return EMPTY for invalid names
            ResourceLocation key = new ResourceLocation(name);
            return BuiltInRegistries.CHUNK_STATUS.getOptional(key).orElse(null);
        } catch (Exception ex) {
            return null; // invalid name
        }
    }
    // Paper end
    public static ChunkStatus byName(String id) {
        return (ChunkStatus) BuiltInRegistries.CHUNK_STATUS.get(ResourceLocation.tryParse(id));
    }

    public EnumSet<Heightmap.Types> heightmapsAfter() {
        return this.heightmapsAfter;
    }

    public boolean isOrAfter(ChunkStatus chunkStatus) {
        return this.getIndex() >= chunkStatus.getIndex();
    }

    public String toString() {
        return BuiltInRegistries.CHUNK_STATUS.getKey(this).toString();
    }

    public static enum ChunkType {

        PROTOCHUNK, LEVELCHUNK;

        private ChunkType() {}
    }

    private interface GenerationTask {

        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> doWork(ChunkStatus targetStatus, Executor executor, ServerLevel world, ChunkGenerator generator, StructureTemplateManager structureTemplateManager, ThreadedLevelLightEngine lightingProvider, Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter, List<ChunkAccess> chunks, ChunkAccess chunk);
    }

    private interface LoadingTask {

        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> doWork(ChunkStatus targetStatus, ServerLevel world, StructureTemplateManager structureTemplateManager, ThreadedLevelLightEngine lightingProvider, Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter, ChunkAccess chunk);
    }

    private interface SimpleGenerationTask extends ChunkStatus.GenerationTask {

        @Override
        default CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> doWork(ChunkStatus targetStatus, Executor executor, ServerLevel world, ChunkGenerator generator, StructureTemplateManager structureTemplateManager, ThreadedLevelLightEngine lightingProvider, Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> fullChunkConverter, List<ChunkAccess> chunks, ChunkAccess chunk) {
            this.doWork(targetStatus, world, generator, chunks, chunk);
            return CompletableFuture.completedFuture(Either.left(chunk));
        }

        void doWork(ChunkStatus targetStatus, ServerLevel world, ChunkGenerator chunkGenerator, List<ChunkAccess> chunks, ChunkAccess chunk);
    }
}
