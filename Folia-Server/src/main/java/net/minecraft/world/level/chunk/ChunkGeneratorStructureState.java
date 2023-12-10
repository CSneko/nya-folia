// mc-dev import
package net.minecraft.world.level.chunk;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.slf4j.Logger;

// Spigot start
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spigotmc.SpigotWorldConfig;
// Spigot end

public class ChunkGeneratorStructureState {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final RandomState randomState;
    private final BiomeSource biomeSource;
    private final long levelSeed;
    private final long concentricRingsSeed;
    private final Map<Structure, List<StructurePlacement>> placementsForStructure = new Object2ObjectOpenHashMap();
    private final Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> ringPositions = new Object2ObjectArrayMap();
    private boolean hasGeneratedPositions;
    private final List<Holder<StructureSet>> possibleStructureSets;
    public final SpigotWorldConfig conf; // Paper

    public static ChunkGeneratorStructureState createForFlat(RandomState randomstate, long i, BiomeSource worldchunkmanager, Stream<Holder<StructureSet>> stream, SpigotWorldConfig conf) { // Spigot
        List<Holder<StructureSet>> list = stream.filter((holder) -> {
            return ChunkGeneratorStructureState.hasBiomesForStructureSet((StructureSet) holder.value(), worldchunkmanager);
        }).toList();

        return new ChunkGeneratorStructureState(randomstate, worldchunkmanager, i, 0L, ChunkGeneratorStructureState.injectSpigot(list, conf), conf); // Spigot
    }

    public static ChunkGeneratorStructureState createForNormal(RandomState randomstate, long i, BiomeSource worldchunkmanager, HolderLookup<StructureSet> holderlookup, SpigotWorldConfig conf) { // Spigot
        List<Holder<StructureSet>> list = (List) holderlookup.listElements().filter((holder_c) -> {
            return ChunkGeneratorStructureState.hasBiomesForStructureSet((StructureSet) holder_c.value(), worldchunkmanager);
        }).collect(Collectors.toUnmodifiableList());

        return new ChunkGeneratorStructureState(randomstate, worldchunkmanager, i, i, ChunkGeneratorStructureState.injectSpigot(list, conf), conf); // Spigot
    }
    // Paper start - horrible hack because spigot creates a ton of direct Holders which lose track of the identifying key
    public static final class KeyedRandomSpreadStructurePlacement extends RandomSpreadStructurePlacement {
        public final net.minecraft.resources.ResourceKey<StructureSet> key;
        public KeyedRandomSpreadStructurePlacement(net.minecraft.resources.ResourceKey<StructureSet> key, net.minecraft.core.Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt, java.util.Optional<StructurePlacement.ExclusionZone> exclusionZone, int spacing, int separation, net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType spreadType) {
            super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
            this.key = key;
        }
    }
    // Paper end

    // Spigot start
    private static List<Holder<StructureSet>> injectSpigot(List<Holder<StructureSet>> list, SpigotWorldConfig conf) {
        return list.stream().map((holder) -> {
            StructureSet structureset = holder.value();
            final Holder<StructureSet> newHolder; // Paper
            if (structureset.placement() instanceof RandomSpreadStructurePlacement randomConfig && holder.unwrapKey().orElseThrow().location().getNamespace().equals(net.minecraft.resources.ResourceLocation.DEFAULT_NAMESPACE)) { // Paper - check namespace cause datapacks could add structure sets with the same path
                String name = holder.unwrapKey().orElseThrow().location().getPath();
                int seed = randomConfig.salt;

                switch (name) {
                    case "desert_pyramids":
                        seed = conf.desertSeed;
                        break;
                    case "end_cities":
                        seed = conf.endCitySeed;
                        break;
                    case "nether_complexes":
                        seed = conf.netherSeed;
                        break;
                    case "igloos":
                        seed = conf.iglooSeed;
                        break;
                    case "jungle_temples":
                        seed = conf.jungleSeed;
                        break;
                    case "woodland_mansions":
                        seed = conf.mansionSeed;
                        break;
                    case "ocean_monuments":
                        seed = conf.monumentSeed;
                        break;
                    case "nether_fossils":
                        seed = conf.fossilSeed;
                        break;
                    case "ocean_ruins":
                        seed = conf.oceanSeed;
                        break;
                    case "pillager_outposts":
                        seed = conf.outpostSeed;
                        break;
                    case "ruined_portals":
                        seed = conf.portalSeed;
                        break;
                    case "shipwrecks":
                        seed = conf.shipwreckSeed;
                        break;
                    case "swamp_huts":
                        seed = conf.swampSeed;
                        break;
                    case "villages":
                        seed = conf.villageSeed;
                        break;
                    // Paper start
                    case "ancient_cities":
                        seed = conf.ancientCitySeed;
                        break;
                    case "trail_ruins":
                        seed = conf.trailRuinsSeed;
                        break;
                    // Paper end
                }

            // Paper start
                structureset = new StructureSet(structureset.structures(), new KeyedRandomSpreadStructurePlacement(holder.unwrapKey().orElseThrow(), randomConfig.locateOffset, randomConfig.frequencyReductionMethod, randomConfig.frequency, seed, randomConfig.exclusionZone, randomConfig.spacing(), randomConfig.separation(), randomConfig.spreadType()));
                newHolder = Holder.direct(structureset); // I really wish we didn't have to do this here
            } else {
                newHolder = holder;
            }
            return newHolder;
            // Paper end
        }).collect(Collectors.toUnmodifiableList());
    }
    // Spigot end

    private static boolean hasBiomesForStructureSet(StructureSet structureSet, BiomeSource biomeSource) {
        Stream<Holder<Biome>> stream = structureSet.structures().stream().flatMap((structureset_a) -> {
            Structure structure = (Structure) structureset_a.structure().value();

            return structure.biomes().stream();
        });
        Set set = biomeSource.possibleBiomes();

        Objects.requireNonNull(set);
        return stream.anyMatch(set::contains);
    }

    private ChunkGeneratorStructureState(RandomState noiseConfig, BiomeSource biomeSource, long structureSeed, long concentricRingSeed, List<Holder<StructureSet>> structureSets, SpigotWorldConfig conf) { // Paper
        this.randomState = noiseConfig;
        this.levelSeed = structureSeed;
        this.biomeSource = biomeSource;
        this.concentricRingsSeed = concentricRingSeed;
        this.possibleStructureSets = structureSets;
        this.conf = conf; // Paper
    }

    public List<Holder<StructureSet>> possibleStructureSets() {
        return this.possibleStructureSets;
    }

    private void generatePositions() {
        Set<Holder<Biome>> set = this.biomeSource.possibleBiomes();

        this.possibleStructureSets().forEach((holder) -> {
            StructureSet structureset = (StructureSet) holder.value();
            boolean flag = false;
            Iterator iterator = structureset.structures().iterator();

            while (iterator.hasNext()) {
                StructureSet.StructureSelectionEntry structureset_a = (StructureSet.StructureSelectionEntry) iterator.next();
                Structure structure = (Structure) structureset_a.structure().value();
                Stream stream = structure.biomes().stream();

                Objects.requireNonNull(set);
                if (stream.anyMatch(set::contains)) {
                    ((List) this.placementsForStructure.computeIfAbsent(structure, (structure1) -> {
                        return new ArrayList();
                    })).add(structureset.placement());
                    flag = true;
                }
            }

            if (flag) {
                StructurePlacement structureplacement = structureset.placement();

                if (structureplacement instanceof ConcentricRingsStructurePlacement) {
                    ConcentricRingsStructurePlacement concentricringsstructureplacement = (ConcentricRingsStructurePlacement) structureplacement;

                    this.ringPositions.put(concentricringsstructureplacement, this.generateRingPositions(holder, concentricringsstructureplacement));
                }
            }

        });
    }

    private CompletableFuture<List<ChunkPos>> generateRingPositions(Holder<StructureSet> structureSetEntry, ConcentricRingsStructurePlacement placement) {
        if (placement.count() == 0) {
            return CompletableFuture.completedFuture(List.of());
        } else {
            Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
            int i = placement.distance();
            int j = placement.count();
            List<CompletableFuture<ChunkPos>> list = new ArrayList(j);
            int k = placement.spread();
            HolderSet<Biome> holderset = placement.preferredBiomes();
            RandomSource randomsource = RandomSource.create();

            // Paper start
            if (this.conf.strongholdSeed != null && structureSetEntry.is(net.minecraft.world.level.levelgen.structure.BuiltinStructureSets.STRONGHOLDS)) {
                randomsource.setSeed(this.conf.strongholdSeed);
            } else {
            // Paper end
            randomsource.setSeed(this.concentricRingsSeed);
            } // Paper
            double d0 = randomsource.nextDouble() * 3.141592653589793D * 2.0D;
            int l = 0;
            int i1 = 0;

            for (int j1 = 0; j1 < j; ++j1) {
                double d1 = (double) (4 * i + i * i1 * 6) + (randomsource.nextDouble() - 0.5D) * (double) i * 2.5D;
                int k1 = (int) Math.round(Math.cos(d0) * d1);
                int l1 = (int) Math.round(Math.sin(d0) * d1);
                RandomSource randomsource1 = randomsource.fork();

                list.add(CompletableFuture.supplyAsync(() -> {
                    BiomeSource worldchunkmanager = this.biomeSource;
                    int i2 = SectionPos.sectionToBlockCoord(k1, 8);
                    int j2 = SectionPos.sectionToBlockCoord(l1, 8);

                    Objects.requireNonNull(holderset);
                    Pair<BlockPos, Holder<Biome>> pair = worldchunkmanager.findBiomeHorizontal(i2, 0, j2, 112, holderset::contains, randomsource1, this.randomState.sampler());

                    if (pair != null) {
                        BlockPos blockposition = (BlockPos) pair.getFirst();

                        return new ChunkPos(SectionPos.blockToSectionCoord(blockposition.getX()), SectionPos.blockToSectionCoord(blockposition.getZ()));
                    } else {
                        return new ChunkPos(k1, l1);
                    }
                }, Util.backgroundExecutor()));
                d0 += 6.283185307179586D / (double) k;
                ++l;
                if (l == k) {
                    ++i1;
                    l = 0;
                    k += 2 * k / (i1 + 1);
                    k = Math.min(k, j - j1);
                    d0 += randomsource.nextDouble() * 3.141592653589793D * 2.0D;
                }
            }

            return Util.sequence(list).thenApply((list1) -> {
                double d2 = (double) stopwatch.stop().elapsed(TimeUnit.MILLISECONDS) / 1000.0D;

                ChunkGeneratorStructureState.LOGGER.debug("Calculation for {} took {}s", structureSetEntry, d2);
                return list1;
            });
        }
    }

    public void ensureStructuresGenerated() {
        if (!this.hasGeneratedPositions) {
            this.generatePositions();
            this.hasGeneratedPositions = true;
        }

    }

    @Nullable
    public List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement placement) {
        this.ensureStructuresGenerated();
        CompletableFuture<List<ChunkPos>> completablefuture = (CompletableFuture) this.ringPositions.get(placement);

        return completablefuture != null ? (List) completablefuture.join() : null;
    }

    public List<StructurePlacement> getPlacementsForStructure(Holder<Structure> structureEntry) {
        this.ensureStructuresGenerated();
        return (List) this.placementsForStructure.getOrDefault(structureEntry.value(), List.of());
    }

    public RandomState randomState() {
        return this.randomState;
    }

    public boolean hasStructureChunkInRange(Holder<StructureSet> structureSetEntry, int centerChunkX, int centerChunkZ, int chunkCount) {
        StructurePlacement structureplacement = ((StructureSet) structureSetEntry.value()).placement();

        for (int l = centerChunkX - chunkCount; l <= centerChunkX + chunkCount; ++l) {
            for (int i1 = centerChunkZ - chunkCount; i1 <= centerChunkZ + chunkCount; ++i1) {
                if (structureplacement.isStructureChunk(this, l, i1, structureplacement instanceof KeyedRandomSpreadStructurePlacement keyed ? keyed.key : null)) { // Paper - add missing structure set configs
                    return true;
                }
            }
        }

        return false;
    }

    public long getLevelSeed() {
        return this.levelSeed;
    }
}
