package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;

public abstract class ChunkGenerator {

    public static final Codec<ChunkGenerator> CODEC = BuiltInRegistries.CHUNK_GENERATOR.byNameCodec().dispatchStable(ChunkGenerator::codec, Function.identity());
    protected final BiomeSource biomeSource;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    public final Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    public ChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, (holder) -> {
            return ((Biome) holder.value()).getGenerationSettings();
        });
    }

    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter) {
        this.biomeSource = biomeSource;
        this.generationSettingsGetter = generationSettingsGetter;
        this.featuresPerStep = Suppliers.memoize(() -> {
            return FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), (holder) -> {
                return ((BiomeGenerationSettings) generationSettingsGetter.apply(holder)).features();
            }, true);
        });
    }

    protected abstract Codec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderlookup, RandomState randomstate, long i, org.spigotmc.SpigotWorldConfig conf) { // Spigot
        return ChunkGeneratorStructureState.createForNormal(randomstate, i, this.biomeSource, holderlookup, conf); // Spigot
    }

    public Optional<ResourceKey<Codec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState noiseConfig, Blender blender, StructureManager structureAccessor, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            chunk.fillBiomesFromNoise(this.biomeSource, noiseConfig.sampler());
            return chunk;
        }), executor); // Paper - run with supplied executor
    }

    public abstract void applyCarvers(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig, BiomeManager biomeAccess, StructureManager structureAccessor, ChunkAccess chunk, GenerationStep.Carving carverStep);

    @Nullable
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel world, HolderSet<Structure> structures, BlockPos center, int radius, boolean skipReferencedStructures) {
        // Paper start - StructuresLocateEvent
        final org.bukkit.World bukkitWorld = world.getWorld();
        final org.bukkit.Location origin = io.papermc.paper.util.MCUtil.toLocation(world, center);
        final List<org.bukkit.generator.structure.Structure> apiStructures = structures.stream().map(Holder::value).map(nms -> org.bukkit.craftbukkit.generator.structure.CraftStructure.minecraftToBukkit(nms, world.registryAccess())).toList();
        if (!apiStructures.isEmpty()) {
            final io.papermc.paper.event.world.StructuresLocateEvent event = new io.papermc.paper.event.world.StructuresLocateEvent(bukkitWorld, origin, apiStructures, radius, skipReferencedStructures);
            if (!event.callEvent()) {
                return null;
            }
            if (event.getResult() != null) {
                return Pair.of(io.papermc.paper.util.MCUtil.toBlockPos(event.getResult().pos()), world.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(org.bukkit.craftbukkit.generator.structure.CraftStructure.bukkitToMinecraft(event.getResult().structure())));
            }
            center = io.papermc.paper.util.MCUtil.toBlockPosition(event.getOrigin());
            radius = event.getRadius();
            skipReferencedStructures = event.shouldFindUnexplored();
            structures = HolderSet.direct(api -> world.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(org.bukkit.craftbukkit.generator.structure.CraftStructure.bukkitToMinecraft(api)), event.getStructures());
        }
        // Paper end
        ChunkGeneratorStructureState chunkgeneratorstructurestate = world.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> map = new Object2ObjectArrayMap();
        Iterator iterator = structures.iterator();

        while (iterator.hasNext()) {
            Holder<Structure> holder = (Holder) iterator.next();
            Iterator iterator1 = chunkgeneratorstructurestate.getPlacementsForStructure(holder).iterator();

            while (iterator1.hasNext()) {
                StructurePlacement structureplacement = (StructurePlacement) iterator1.next();

                ((Set) map.computeIfAbsent(structureplacement, (structureplacement1) -> {
                    return new ObjectArraySet();
                })).add(holder);
            }
        }

        if (map.isEmpty()) {
            return null;
        } else {
            Pair<BlockPos, Holder<Structure>> pair = null;
            double d0 = Double.MAX_VALUE;
            StructureManager structuremanager = world.structureManager();
            List<Entry<StructurePlacement, Set<Holder<Structure>>>> list = new ArrayList(map.size());
            Iterator iterator2 = map.entrySet().iterator();

            while (iterator2.hasNext()) {
                Entry<StructurePlacement, Set<Holder<Structure>>> entry = (Entry) iterator2.next();
                StructurePlacement structureplacement1 = (StructurePlacement) entry.getKey();

                if (structureplacement1 instanceof ConcentricRingsStructurePlacement) {
                    ConcentricRingsStructurePlacement concentricringsstructureplacement = (ConcentricRingsStructurePlacement) structureplacement1;
                    Pair<BlockPos, Holder<Structure>> pair1 = this.getNearestGeneratedStructure((Set) entry.getValue(), world, structuremanager, center, skipReferencedStructures, concentricringsstructureplacement);

                    if (pair1 != null) {
                        BlockPos blockposition1 = (BlockPos) pair1.getFirst();
                        double d1 = center.distSqr(blockposition1);

                        if (d1 < d0) {
                            d0 = d1;
                            pair = pair1;
                        }
                    }
                } else if (structureplacement1 instanceof RandomSpreadStructurePlacement) {
                    list.add(entry);
                }
            }

            if (!list.isEmpty()) {
                int j = SectionPos.blockToSectionCoord(center.getX());
                int k = SectionPos.blockToSectionCoord(center.getZ());

                for (int l = 0; l <= radius; ++l) {
                    boolean flag1 = false;
                    Iterator iterator3 = list.iterator();

                    while (iterator3.hasNext()) {
                        Entry<StructurePlacement, Set<Holder<Structure>>> entry1 = (Entry) iterator3.next();
                        RandomSpreadStructurePlacement randomspreadstructureplacement = (RandomSpreadStructurePlacement) entry1.getKey();
                        Pair<BlockPos, Holder<Structure>> pair2 = ChunkGenerator.getNearestGeneratedStructure((Set) entry1.getValue(), world, structuremanager, j, k, l, skipReferencedStructures, chunkgeneratorstructurestate.getLevelSeed(), randomspreadstructureplacement);

                        if (pair2 != null) {
                            flag1 = true;
                            double d2 = center.distSqr((Vec3i) pair2.getFirst());

                            if (d2 < d0) {
                                d0 = d2;
                                pair = pair2;
                            }
                        }
                    }

                    if (flag1) {
                        return pair;
                    }
                }
            }

            return pair;
        }
    }

    @Nullable
    private Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(Set<Holder<Structure>> structures, ServerLevel world, StructureManager structureAccessor, BlockPos center, boolean skipReferencedStructures, ConcentricRingsStructurePlacement placement) {
        List<ChunkPos> list = world.getChunkSource().getGeneratorState().getRingPositionsFor(placement);

        if (list == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        } else {
            Pair<BlockPos, Holder<Structure>> pair = null;
            double d0 = Double.MAX_VALUE;
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ChunkPos chunkcoordintpair = (ChunkPos) iterator.next();
                if (!world.getWorldBorder().isChunkInBounds(chunkcoordintpair.x, chunkcoordintpair.z)) { continue; } // Paper

                blockposition_mutableblockposition.set(SectionPos.sectionToBlockCoord(chunkcoordintpair.x, 8), 32, SectionPos.sectionToBlockCoord(chunkcoordintpair.z, 8));
                double d1 = blockposition_mutableblockposition.distSqr(center);
                boolean flag1 = pair == null || d1 < d0;

                if (flag1) {
                    Pair<BlockPos, Holder<Structure>> pair1 = ChunkGenerator.getStructureGeneratingAt(structures, world, structureAccessor, skipReferencedStructures, placement, chunkcoordintpair);

                    if (pair1 != null) {
                        pair = pair1;
                        d0 = d1;
                    }
                }
            }

            return pair;
        }
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(Set<Holder<Structure>> structures, LevelReader world, StructureManager structureAccessor, int centerChunkX, int centerChunkZ, int radius, boolean skipReferencedStructures, long seed, RandomSpreadStructurePlacement placement) {
        int i1 = placement.spacing();

        for (int j1 = -radius; j1 <= radius; ++j1) {
            // Paper start - iterate over border chunks instead of entire square chunk area
            boolean flag1 = j1 == -radius || j1 == radius; final boolean onBorderAlongZAxis = flag1; // Paper - OBFHELPER

            for (int k1 = -radius; k1 <= radius; k1 += onBorderAlongZAxis ? 1 : radius * 2) {
                // boolean flag2 = k1 == -radius || k1 == radius;

                // if (flag1 || flag2) {
                if (true) {
                    // Paper end
                    int l1 = centerChunkX + i1 * j1;
                    int i2 = centerChunkZ + i1 * k1;
                    ChunkPos chunkcoordintpair = placement.getPotentialStructureChunk(seed, l1, i2);
                    Pair<BlockPos, Holder<Structure>> pair = ChunkGenerator.getStructureGeneratingAt(structures, world, structureAccessor, skipReferencedStructures, placement, chunkcoordintpair);

                    if (pair != null) {
                        return pair;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(Set<Holder<Structure>> structures, LevelReader world, StructureManager structureAccessor, boolean skipReferencedStructures, StructurePlacement placement, ChunkPos pos) {
        Iterator iterator = structures.iterator();

        Holder holder;
        StructureStart structurestart;

        do {
            do {
                do {
                    StructureCheckResult structurecheckresult;

                    do {
                        if (!iterator.hasNext()) {
                            return null;
                        }

                        holder = (Holder) iterator.next();
                        structurecheckresult = structureAccessor.checkStructurePresence(pos, (Structure) holder.value(), skipReferencedStructures);
                    } while (structurecheckresult == StructureCheckResult.START_NOT_PRESENT);

                    if (!skipReferencedStructures && structurecheckresult == StructureCheckResult.START_PRESENT) {
                        return Pair.of(placement.getLocatePos(pos), holder);
                    }

                    ChunkAccess ichunkaccess = world.syncLoadNonFull(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS); // Paper - rewrite chunk system

                    structurestart = structureAccessor.getStartForStructure(SectionPos.bottomOf(ichunkaccess), (Structure) holder.value(), ichunkaccess);
                } while (structurestart == null);
            } while (!structurestart.isValid());
        } while (skipReferencedStructures && !ChunkGenerator.tryAddReference(structureAccessor, structurestart));

        return Pair.of(placement.getLocatePos(structurestart.getChunkPos()), holder);
    }

    private static boolean tryAddReference(StructureManager structureAccessor, StructureStart start) {
        if (start.tryReference()) { // Folia - region threading
            structureAccessor.addReference(start);
            return true;
        } else {
            return false;
        }
    }

    public void addVanillaDecorations(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager) { // CraftBukkit
        ChunkPos chunkcoordintpair = ichunkaccess.getPos();

        if (!SharedConstants.debugVoidTerrain(chunkcoordintpair)) {
            SectionPos sectionposition = SectionPos.of(chunkcoordintpair, generatoraccessseed.getMinSection());
            BlockPos blockposition = sectionposition.origin();
            Registry<Structure> iregistry = generatoraccessseed.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> map = (Map) iregistry.stream().collect(Collectors.groupingBy((structure) -> {
                return structure.step().ordinal();
            }));
            List<FeatureSorter.StepFeatureData> list = (List) this.featuresPerStep.get();
            WorldgenRandom seededrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            long i = seededrandom.setDecorationSeed(generatoraccessseed.getSeed(), blockposition.getX(), blockposition.getZ());
            Set<Holder<Biome>> set = new ObjectArraySet();

            ChunkPos.rangeClosed(sectionposition.chunk(), 1).forEach((chunkcoordintpair1) -> {
                ChunkAccess ichunkaccess1 = generatoraccessseed.getChunk(chunkcoordintpair1.x, chunkcoordintpair1.z);
                LevelChunkSection[] achunksection = ichunkaccess1.getSections();
                int j = achunksection.length;

                for (int k = 0; k < j; ++k) {
                    LevelChunkSection chunksection = achunksection[k];
                    PalettedContainerRO<Holder<Biome>> palettedcontainerro = chunksection.getBiomes(); // CraftBukkit - decompile error

                    Objects.requireNonNull(set);
                    palettedcontainerro.getAll(set::add);
                }

            });
            set.retainAll(this.biomeSource.possibleBiomes());
            int j = list.size();

            try {
                Registry<PlacedFeature> iregistry1 = generatoraccessseed.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
                int k = Math.max(GenerationStep.Decoration.values().length, j);

                for (int l = 0; l < k; ++l) {
                    int i1 = 0;
                    Iterator iterator;
                    CrashReportCategory crashreportsystemdetails;

                    if (structuremanager.shouldGenerateStructures()) {
                        List<Structure> list1 = (List) map.getOrDefault(l, Collections.emptyList());

                        for (iterator = list1.iterator(); iterator.hasNext(); ++i1) {
                            Structure structure = (Structure) iterator.next();

                            seededrandom.setFeatureSeed(i, i1, l);
                            Supplier<String> supplier = () -> { // CraftBukkit - decompile error
                                Optional optional = iregistry.getResourceKey(structure).map(Object::toString);

                                Objects.requireNonNull(structure);
                                return (String) optional.orElseGet(structure::toString);
                            };

                            try {
                                generatoraccessseed.setCurrentlyGenerating(supplier);
                                structuremanager.startsForStructure(sectionposition, structure).forEach((structurestart) -> {
                                    structurestart.placeInChunk(generatoraccessseed, structuremanager, this, seededrandom, ChunkGenerator.getWritableArea(ichunkaccess), chunkcoordintpair);
                                });
                            } catch (Exception exception) {
                                CrashReport crashreport = CrashReport.forThrowable(exception, "Feature placement");

                                crashreportsystemdetails = crashreport.addCategory("Feature");
                                Objects.requireNonNull(supplier);
                                crashreportsystemdetails.setDetail("Description", supplier::get);
                                throw new ReportedException(crashreport);
                            }
                        }
                    }

                    if (l < j) {
                        IntArraySet intarrayset = new IntArraySet();

                        iterator = set.iterator();

                        while (iterator.hasNext()) {
                            Holder<Biome> holder = (Holder) iterator.next();
                            List<HolderSet<PlacedFeature>> list2 = ((BiomeGenerationSettings) this.generationSettingsGetter.apply(holder)).features();

                            if (l < list2.size()) {
                                HolderSet<PlacedFeature> holderset = (HolderSet) list2.get(l);
                                FeatureSorter.StepFeatureData featuresorter_b = (FeatureSorter.StepFeatureData) list.get(l);

                                holderset.stream().map(Holder::value).forEach((placedfeature) -> {
                                    intarrayset.add(featuresorter_b.indexMapping().applyAsInt(placedfeature));
                                });
                            }
                        }

                        int j1 = intarrayset.size();
                        int[] aint = intarrayset.toIntArray();

                        Arrays.sort(aint);
                        FeatureSorter.StepFeatureData featuresorter_b1 = (FeatureSorter.StepFeatureData) list.get(l);

                        for (int k1 = 0; k1 < j1; ++k1) {
                            int l1 = aint[k1];
                            PlacedFeature placedfeature = (PlacedFeature) featuresorter_b1.features().get(l1);
                            Supplier<String> supplier1 = () -> {
                                Optional optional = iregistry1.getResourceKey(placedfeature).map(Object::toString);

                                Objects.requireNonNull(placedfeature);
                                return (String) optional.orElseGet(placedfeature::toString);
                            };

                            // Paper start - change populationSeed used in random
                            long featurePopulationSeed = i;
                            final long configFeatureSeed = generatoraccessseed.getMinecraftWorld().paperConfig().featureSeeds.features.getLong(placedfeature.feature());
                            if (configFeatureSeed != -1) {
                                featurePopulationSeed = seededrandom.setDecorationSeed(configFeatureSeed, blockposition.getX(), blockposition.getZ()); // See seededrandom.setDecorationSeed from above
                            }
                            seededrandom.setFeatureSeed(featurePopulationSeed, l1, l);
                            // Paper end

                            try {
                                generatoraccessseed.setCurrentlyGenerating(supplier1);
                                placedfeature.placeWithBiomeCheck(generatoraccessseed, this, seededrandom, blockposition);
                            } catch (Exception exception1) {
                                CrashReport crashreport1 = CrashReport.forThrowable(exception1, "Feature placement");

                                crashreportsystemdetails = crashreport1.addCategory("Feature");
                                Objects.requireNonNull(supplier1);
                                crashreportsystemdetails.setDetail("Description", supplier1::get);
                                throw new ReportedException(crashreport1);
                            }
                        }
                    }
                }

                generatoraccessseed.setCurrentlyGenerating((Supplier) null);
            } catch (Exception exception2) {
                CrashReport crashreport2 = CrashReport.forThrowable(exception2, "Biome decoration");

                crashreport2.addCategory("Generation").setDetail("CenterX", (Object) chunkcoordintpair.x).setDetail("CenterZ", (Object) chunkcoordintpair.z).setDetail("Seed", (Object) i);
                throw new ReportedException(crashreport2);
            }
        }
    }

   // CraftBukkit start
    public void applyBiomeDecoration(WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor) {
        this.applyBiomeDecoration(world, chunk, structureAccessor, true);
    }

    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla) {
        if (vanilla) {
            this.addVanillaDecorations(generatoraccessseed, ichunkaccess, structuremanager);
        }

        org.bukkit.World world = generatoraccessseed.getMinecraftWorld().getWorld();
        // only call when a populator is present (prevents unnecessary entity conversion)
        if (!world.getPopulators().isEmpty()) {
            org.bukkit.craftbukkit.generator.CraftLimitedRegion limitedRegion = new org.bukkit.craftbukkit.generator.CraftLimitedRegion(generatoraccessseed, ichunkaccess.getPos());
            int x = ichunkaccess.getPos().x;
            int z = ichunkaccess.getPos().z;
            for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                WorldgenRandom seededrandom = new WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(generatoraccessseed.getSeed()));
                seededrandom.setDecorationSeed(generatoraccessseed.getSeed(), x, z);
                populator.populate(world, new org.bukkit.craftbukkit.util.RandomSourceWrapper.RandomWrapper(seededrandom), x, z, limitedRegion);
            }
            limitedRegion.saveEntities();
            limitedRegion.breakLink();
        }
    }
    // CraftBukkit end

    private static BoundingBox getWritableArea(ChunkAccess chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        int i = chunkcoordintpair.getMinBlockX();
        int j = chunkcoordintpair.getMinBlockZ();
        LevelHeightAccessor levelheightaccessor = chunk.getHeightAccessorForGeneration();
        int k = levelheightaccessor.getMinBuildHeight() + 1;
        int l = levelheightaccessor.getMaxBuildHeight() - 1;

        return new BoundingBox(i, k, j, i + 15, l, j + 15);
    }

    public abstract void buildSurface(WorldGenRegion region, StructureManager structures, RandomState noiseConfig, ChunkAccess chunk);

    public abstract void spawnOriginalMobs(WorldGenRegion region);

    public int getSpawnHeight(LevelHeightAccessor world) {
        return 64;
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager accessor, MobCategory group, BlockPos pos) {
        Map<Structure, LongSet> map = accessor.getAllStructuresAt(pos);
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Structure, LongSet> entry = (Entry) iterator.next();
            Structure structure = (Structure) entry.getKey();
            StructureSpawnOverride structurespawnoverride = (StructureSpawnOverride) structure.spawnOverrides().get(group);

            if (structurespawnoverride != null) {
                MutableBoolean mutableboolean = new MutableBoolean(false);
                Predicate<StructureStart> predicate = structurespawnoverride.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE ? (structurestart) -> {
                    return accessor.structureHasPieceAt(pos, structurestart);
                } : (structurestart) -> {
                    return structurestart.getBoundingBox().isInside(pos);
                };

                accessor.fillStartsForStructure(structure, (LongSet) entry.getValue(), (structurestart) -> {
                    if (mutableboolean.isFalse() && predicate.test(structurestart)) {
                        mutableboolean.setTrue();
                    }

                });
                if (mutableboolean.isTrue()) {
                    return structurespawnoverride.spawns();
                }
            }
        }

        return ((Biome) biome.value()).getMobSettings().getMobs(group);
    }

    public void createStructures(RegistryAccess registryManager, ChunkGeneratorStructureState placementCalculator, StructureManager structureAccessor, ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        SectionPos sectionposition = SectionPos.bottomOf(chunk);
        RandomState randomstate = placementCalculator.randomState();

        placementCalculator.possibleStructureSets().forEach((holder) -> {
            StructurePlacement structureplacement = ((StructureSet) holder.value()).placement();
            List<StructureSet.StructureSelectionEntry> list = ((StructureSet) holder.value()).structures();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                StructureSet.StructureSelectionEntry structureset_a = (StructureSet.StructureSelectionEntry) iterator.next();
                StructureStart structurestart = structureAccessor.getStartForStructure(sectionposition, (Structure) structureset_a.structure().value(), chunk);

                if (structurestart != null && structurestart.isValid()) {
                    return;
                }
            }

            if (structureplacement.isStructureChunk(placementCalculator, chunkcoordintpair.x, chunkcoordintpair.z, structureplacement instanceof net.minecraft.world.level.chunk.ChunkGeneratorStructureState.KeyedRandomSpreadStructurePlacement keyed ? keyed.key : null)) { // Paper - add missing structure set configs
                if (list.size() == 1) {
                    this.tryGenerateStructure((StructureSet.StructureSelectionEntry) list.get(0), structureAccessor, registryManager, randomstate, structureTemplateManager, placementCalculator.getLevelSeed(), chunk, chunkcoordintpair, sectionposition);
                } else {
                    ArrayList<StructureSet.StructureSelectionEntry> arraylist = new ArrayList(list.size());

                    arraylist.addAll(list);
                    WorldgenRandom seededrandom = new WorldgenRandom(new LegacyRandomSource(0L));

                    seededrandom.setLargeFeatureSeed(placementCalculator.getLevelSeed(), chunkcoordintpair.x, chunkcoordintpair.z);
                    int i = 0;

                    StructureSet.StructureSelectionEntry structureset_a1;

                    for (Iterator iterator1 = arraylist.iterator(); iterator1.hasNext(); i += structureset_a1.weight()) {
                        structureset_a1 = (StructureSet.StructureSelectionEntry) iterator1.next();
                    }

                    while (!arraylist.isEmpty()) {
                        int j = seededrandom.nextInt(i);
                        int k = 0;
                        Iterator iterator2 = arraylist.iterator();

                        while (true) {
                            if (iterator2.hasNext()) {
                                StructureSet.StructureSelectionEntry structureset_a2 = (StructureSet.StructureSelectionEntry) iterator2.next();

                                j -= structureset_a2.weight();
                                if (j >= 0) {
                                    ++k;
                                    continue;
                                }
                            }

                            StructureSet.StructureSelectionEntry structureset_a3 = (StructureSet.StructureSelectionEntry) arraylist.get(k);

                            if (this.tryGenerateStructure(structureset_a3, structureAccessor, registryManager, randomstate, structureTemplateManager, placementCalculator.getLevelSeed(), chunk, chunkcoordintpair, sectionposition)) {
                                return;
                            }

                            arraylist.remove(k);
                            i -= structureset_a3.weight();
                            break;
                        }
                    }

                }
            }
        });
    }

    private boolean tryGenerateStructure(StructureSet.StructureSelectionEntry weightedEntry, StructureManager structureAccessor, RegistryAccess dynamicRegistryManager, RandomState noiseConfig, StructureTemplateManager structureManager, long seed, ChunkAccess chunk, ChunkPos pos, SectionPos sectionPos) {
        Structure structure = (Structure) weightedEntry.structure().value();
        int j = ChunkGenerator.fetchReferences(structureAccessor, chunk, sectionPos, structure);
        HolderSet<Biome> holderset = structure.biomes();

        Objects.requireNonNull(holderset);
        Predicate<Holder<Biome>> predicate = holderset::contains;
        StructureStart structurestart = structure.generate(dynamicRegistryManager, this, this.biomeSource, noiseConfig, structureManager, seed, pos, j, chunk, predicate);

        if (structurestart.isValid()) {
            // CraftBukkit start
            BoundingBox box = structurestart.getBoundingBox();
            org.bukkit.event.world.AsyncStructureSpawnEvent event = new org.bukkit.event.world.AsyncStructureSpawnEvent(structureAccessor.level.getMinecraftWorld().getWorld(), org.bukkit.craftbukkit.generator.structure.CraftStructure.minecraftToBukkit(structure, dynamicRegistryManager), new org.bukkit.util.BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), pos.x, pos.z);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return true;
            }
            // CraftBukkit end
            structureAccessor.setStartForStructure(sectionPos, structure, structurestart, chunk);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager structureAccessor, ChunkAccess chunk, SectionPos sectionPos, Structure structure) {
        StructureStart structurestart = structureAccessor.getStartForStructure(sectionPos, structure, chunk);

        return structurestart != null ? structurestart.getReferences() : 0;
    }

    public void createReferences(WorldGenLevel world, StructureManager structureAccessor, ChunkAccess chunk) {
        boolean flag = true;
        ChunkPos chunkcoordintpair = chunk.getPos();
        int i = chunkcoordintpair.x;
        int j = chunkcoordintpair.z;
        int k = chunkcoordintpair.getMinBlockX();
        int l = chunkcoordintpair.getMinBlockZ();
        SectionPos sectionposition = SectionPos.bottomOf(chunk);

        for (int i1 = i - 8; i1 <= i + 8; ++i1) {
            for (int j1 = j - 8; j1 <= j + 8; ++j1) {
                long k1 = ChunkPos.asLong(i1, j1);
                Iterator iterator = world.getChunk(i1, j1).getAllStarts().values().iterator();

                while (iterator.hasNext()) {
                    StructureStart structurestart = (StructureStart) iterator.next();

                    try {
                        if (structurestart.isValid() && structurestart.getBoundingBox().intersects(k, l, k + 15, l + 15)) {
                            structureAccessor.addReferenceForStructure(sectionposition, structurestart.getStructure(), k1, chunk);
                            DebugPackets.sendStructurePacket(world, structurestart);
                        }
                    } catch (Exception exception) {
                        CrashReport crashreport = CrashReport.forThrowable(exception, "Generating structure reference");
                        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Structure");
                        Optional<? extends Registry<Structure>> optional = world.registryAccess().registry(Registries.STRUCTURE);

                        crashreportsystemdetails.setDetail("Id", () -> {
                            return (String) optional.map((iregistry) -> {
                                return iregistry.getKey(structurestart.getStructure()).toString();
                            }).orElse("UNKNOWN");
                        });
                        crashreportsystemdetails.setDetail("Name", () -> {
                            return BuiltInRegistries.STRUCTURE_TYPE.getKey(structurestart.getStructure().type()).toString();
                        });
                        crashreportsystemdetails.setDetail("Class", () -> {
                            return structurestart.getStructure().getClass().getCanonicalName();
                        });
                        throw new ReportedException(crashreport);
                    }
                }
            }
        }

    }

    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState noiseConfig, StructureManager structureAccessor, ChunkAccess chunk);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig);

    public abstract net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig);

    public int getFirstFreeHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig) {
        return this.getBaseHeight(x, z, heightmap, world, noiseConfig);
    }

    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig) {
        return this.getBaseHeight(x, z, heightmap, world, noiseConfig) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos);

    /** @deprecated */
    @Deprecated
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biomeEntry) {
        return (BiomeGenerationSettings) this.generationSettingsGetter.apply(biomeEntry);
    }
}
