package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public final class NaturalSpawner {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_SPAWN_DISTANCE = 24;
    public static final int SPAWN_DISTANCE_CHUNK = 8;
    public static final int SPAWN_DISTANCE_BLOCK = 128;
    static final int MAGIC_NUMBER = (int) Math.pow(17.0D, 2.0D);
    public static final MobCategory[] SPAWNING_CATEGORIES = (MobCategory[]) Stream.of(MobCategory.values()).filter((enumcreaturetype) -> {
        return enumcreaturetype != MobCategory.MISC;
    }).toArray((i) -> {
        return new MobCategory[i];
    });

    private NaturalSpawner() {}

    public static NaturalSpawner.SpawnState createState(int spawningChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkSource, LocalMobCapCalculator densityCapper) {
        // Paper start - add countMobs parameter
        return createState(spawningChunkCount, entities, chunkSource, densityCapper, false);
    }

    public static NaturalSpawner.SpawnState createState(int spawningChunkCount, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkSource, LocalMobCapCalculator densityCapper, boolean countMobs) {
        // Paper end
        PotentialCalculator spawnercreatureprobabilities = new PotentialCalculator();
        Object2IntOpenHashMap<MobCategory> object2intopenhashmap = new Object2IntOpenHashMap();
        Iterator iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                if (entityinsentient.isPersistenceRequired() || entityinsentient.requiresCustomPersistence()) {
                    continue;
                }
            }

            MobCategory enumcreaturetype = entity.getType().getCategory();

            if (enumcreaturetype != MobCategory.MISC) {
                // Paper start - Only count natural spawns
                if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                    !(entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL ||
                        entity.spawnReason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                    continue;
                }
                // Paper end
                BlockPos blockposition = entity.blockPosition();

                chunkSource.query(ChunkPos.asLong(blockposition), (chunk) -> {
                    MobSpawnSettings.MobSpawnCost biomesettingsmobs_b = NaturalSpawner.getRoughBiome(blockposition, chunk).getMobSettings().getMobSpawnCost(entity.getType());

                    if (biomesettingsmobs_b != null) {
                        spawnercreatureprobabilities.addCharge(entity.blockPosition(), biomesettingsmobs_b.charge());
                    }

                    if (densityCapper != null && entity instanceof Mob) { // Paper
                        densityCapper.addMob(chunk.getPos(), enumcreaturetype);
                    }

                    object2intopenhashmap.addTo(enumcreaturetype, 1);
                    // Paper start
                    if (countMobs) {
                        chunk.level.getChunkSource().chunkMap.updatePlayerMobTypeMap(entity);
                    }
                    // Paper end
                });
            }
        }

        return new NaturalSpawner.SpawnState(spawningChunkCount, object2intopenhashmap, spawnercreatureprobabilities, densityCapper);
    }

    static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        return (Biome) chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).value();
    }

    public static void spawnForChunk(ServerLevel world, LevelChunk chunk, NaturalSpawner.SpawnState info, boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn) {
        world.getProfiler().push("spawner");
        world.timings.mobSpawn.startTiming(); // Spigot
        MobCategory[] aenumcreaturetype = NaturalSpawner.SPAWNING_CATEGORIES;
        int i = aenumcreaturetype.length;

        LevelData worlddata = world.getLevelData(); // CraftBukkit - Other mob type spawn tick rate

        for (int j = 0; j < i; ++j) {
            MobCategory enumcreaturetype = aenumcreaturetype[j];
            // CraftBukkit start - Use per-world spawn limits
            boolean spawnThisTick = true;
            int limit = enumcreaturetype.getMaxInstancesPerChunk();
            SpawnCategory spawnCategory = CraftSpawnCategory.toBukkit(enumcreaturetype);
            if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                spawnThisTick = world.ticksPerSpawnCategory.getLong(spawnCategory) != 0 && world.getRedstoneGameTime() % world.ticksPerSpawnCategory.getLong(spawnCategory) == 0; // Folia - region threading
                limit = world.getWorld().getSpawnLimit(spawnCategory);
            }

            if (!spawnThisTick || limit == 0) {
                continue;
            }

            // Paper start - only allow spawns upto the limit per chunk and update count afterwards
            int currEntityCount = info.mobCategoryCounts.getInt(enumcreaturetype);
            int k1 = limit * info.getSpawnableChunkCount() / NaturalSpawner.MAGIC_NUMBER;
            int difference = k1 - currEntityCount;

            if (world.paperConfig().entities.spawning.perPlayerMobSpawns) {
                int minDiff = Integer.MAX_VALUE;
                final com.destroystokyo.paper.util.maplist.ReferenceList<net.minecraft.server.level.ServerPlayer> inRange =
                    world.chunkSource.chunkMap.getNearbyPlayers().getPlayers(chunk.getPos(), io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE);
                if (inRange != null) {
                    final Object[] backingSet = inRange.getRawData();
                    for (int k = 0, len = inRange.size(); k < len; k++) {
                        minDiff = Math.min(limit - world.getChunkSource().chunkMap.getMobCountNear((net.minecraft.server.level.ServerPlayer)backingSet[k], enumcreaturetype), minDiff);
                    }
                }
                difference = (minDiff == Integer.MAX_VALUE) ? 0 : minDiff;
            }
            if ((spawnAnimals || !enumcreaturetype.isFriendly()) && (spawnMonsters || enumcreaturetype.isFriendly()) && (rareSpawn || !enumcreaturetype.isPersistent()) && difference > 0) {
                // Paper end
                // CraftBukkit end
                Objects.requireNonNull(info);
                NaturalSpawner.SpawnPredicate spawnercreature_c = info::canSpawn;

                Objects.requireNonNull(info);
                // Paper start
                int spawnCount = NaturalSpawner.spawnCategoryForChunk(enumcreaturetype, world, chunk, spawnercreature_c, info::afterSpawn,
                    difference, world.paperConfig().entities.spawning.perPlayerMobSpawns ? world.getChunkSource().chunkMap::updatePlayerMobTypeMap : null);
                info.mobCategoryCounts.mergeInt(enumcreaturetype, spawnCount, Integer::sum);
                // Paper end
            }
        }

        world.timings.mobSpawn.stopTiming(); // Spigot
        world.getProfiler().pop();
    }

    // Paper start
    public static int globalLimitForCategory(final ServerLevel level, final MobCategory category, final int spawnableChunkCount) {
        final int categoryLimit = level.getWorld().getSpawnLimitUnsafe(CraftSpawnCategory.toBukkit(category));
        if (categoryLimit < 1) {
            return categoryLimit;
        }
        return categoryLimit * spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
    }
    // Paper end

    public static void spawnCategoryForChunk(MobCategory group, ServerLevel world, LevelChunk chunk, NaturalSpawner.SpawnPredicate checker, NaturalSpawner.AfterSpawnCallback runner) {
        // Paper start - add parameters and int ret type
        spawnCategoryForChunk(group, world, chunk, checker, runner, Integer.MAX_VALUE, null);
    }
    public static int spawnCategoryForChunk(MobCategory group, ServerLevel world, LevelChunk chunk, NaturalSpawner.SpawnPredicate checker, NaturalSpawner.AfterSpawnCallback runner, int maxSpawns, Consumer<Entity> trackEntity) {
        // Paper end - add parameters and int ret type
        BlockPos blockposition = NaturalSpawner.getRandomPosWithin(world, chunk);

        if (blockposition.getY() >= world.getMinBuildHeight() + 1) {
            return NaturalSpawner.spawnCategoryForPosition(group, world, chunk, blockposition, checker, runner, maxSpawns, trackEntity); // Paper
        }
        return 0; // Paper
    }

    @VisibleForDebug
    public static void spawnCategoryForPosition(MobCategory group, ServerLevel world, BlockPos pos) {
        NaturalSpawner.spawnCategoryForPosition(group, world, world.getChunk(pos), pos, (entitytypes, blockposition1, ichunkaccess) -> {
            return true;
        }, (entityinsentient, ichunkaccess) -> {
        });
    }

    // Paper start - add maxSpawns parameter and return spawned mobs
    public static void spawnCategoryForPosition(MobCategory group, ServerLevel world, ChunkAccess chunk, BlockPos pos, NaturalSpawner.SpawnPredicate checker, NaturalSpawner.AfterSpawnCallback runner) {
        spawnCategoryForPosition(group, world,chunk, pos, checker, runner, Integer.MAX_VALUE, null);
    }
    public static int spawnCategoryForPosition(MobCategory group, ServerLevel world, ChunkAccess chunk, BlockPos pos, NaturalSpawner.SpawnPredicate checker, NaturalSpawner.AfterSpawnCallback runner, int maxSpawns, Consumer<Entity> trackEntity) {
    // Paper end - add maxSpawns parameter and return spawned mobs
        StructureManager structuremanager = world.structureManager();
        ChunkGenerator chunkgenerator = world.getChunkSource().getGenerator();
        int i = pos.getY();
        BlockState iblockdata = world.getBlockStateIfLoadedAndInBounds(pos); // Paper - don't load chunks for mob spawn
        int j = 0; // Paper - moved up

        if (iblockdata != null && !iblockdata.isRedstoneConductor(chunk, pos)) { // Paper - don't load chunks for mob spawn
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
            //int j = 0; // Paper - moved up
            int k = 0;

            while (k < 3) {
                int l = pos.getX();
                int i1 = pos.getZ();
                boolean flag = true;
                MobSpawnSettings.SpawnerData biomesettingsmobs_c = null;
                SpawnGroupData groupdataentity = null;
                int j1 = Mth.ceil(world.random.nextFloat() * 4.0F);
                int k1 = 0;
                int l1 = 0;

                while (true) {
                    if (l1 < j1) {
                        label53:
                        {
                            l += world.random.nextInt(6) - world.random.nextInt(6);
                            i1 += world.random.nextInt(6) - world.random.nextInt(6);
                            blockposition_mutableblockposition.set(l, i, i1);
                            double d0 = (double) l + 0.5D;
                            double d1 = (double) i1 + 0.5D;
                            Player entityhuman = world.getNearestPlayer(d0, (double) i, d1, -1.0D, false);

                            if (entityhuman != null) {
                                double d2 = entityhuman.distanceToSqr(d0, (double) i, d1);

                                if (world.isLoadedAndInBounds(blockposition_mutableblockposition) && NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(world, chunk, blockposition_mutableblockposition, d2)) { // Paper - don't load chunks for mob spawn
                                    if (biomesettingsmobs_c == null) {
                                        Optional<MobSpawnSettings.SpawnerData> optional = NaturalSpawner.getRandomSpawnMobAt(world, structuremanager, chunkgenerator, group, world.random, blockposition_mutableblockposition);

                                        if (optional.isEmpty()) {
                                            break label53;
                                        }

                                        biomesettingsmobs_c = (MobSpawnSettings.SpawnerData) optional.get();
                                        j1 = biomesettingsmobs_c.minCount + world.random.nextInt(1 + biomesettingsmobs_c.maxCount - biomesettingsmobs_c.minCount);
                                    }

                                    // Paper start
                                    PreSpawnStatus doSpawning = isValidSpawnPostitionForType(world, group, structuremanager, chunkgenerator, biomesettingsmobs_c, blockposition_mutableblockposition, d2);
                                    // Paper start - mob count backoff
                                    if (doSpawning == PreSpawnStatus.ABORT || doSpawning == PreSpawnStatus.CANCELLED) {
                                        world.getChunkSource().chunkMap.updateFailurePlayerMobTypeMap(blockposition_mutableblockposition.getX() >> 4, blockposition_mutableblockposition.getZ() >> 4, group);
                                    }
                                    // Paper end - mob count backoff
                                    if (doSpawning == PreSpawnStatus.ABORT) {
                                        return j; // Paper
                                    }
                                    if (doSpawning == PreSpawnStatus.SUCCESS && checker.test(biomesettingsmobs_c.type, blockposition_mutableblockposition, chunk)) {
                                        // Paper end
                                        Mob entityinsentient = NaturalSpawner.getMobForSpawn(world, biomesettingsmobs_c.type);

                                        if (entityinsentient == null) {
                                            return j; // Paper
                                        }

                                        entityinsentient.moveTo(d0, (double) i, d1, world.random.nextFloat() * 360.0F, 0.0F);
                                        if (NaturalSpawner.isValidPositionForMob(world, entityinsentient, d2)) {
                                            groupdataentity = entityinsentient.finalizeSpawn(world, world.getCurrentDifficultyAt(entityinsentient.blockPosition()), MobSpawnType.NATURAL, groupdataentity, (CompoundTag) null);
                                            // CraftBukkit start
                                            // SPIGOT-7045: Give ocelot babies back their special spawn reason. Note: This is the only modification required as ocelots count as monsters which means they only spawn during normal chunk ticking and do not spawn during chunk generation as starter mobs.
                                            world.addFreshEntityWithPassengers(entityinsentient, (entityinsentient instanceof net.minecraft.world.entity.animal.Ocelot && !((org.bukkit.entity.Ageable) entityinsentient.getBukkitEntity()).isAdult()) ? SpawnReason.OCELOT_BABY : SpawnReason.NATURAL);
                                            if (!entityinsentient.isRemoved()) {
                                                ++j;
                                                ++k1;
                                                runner.run(entityinsentient, chunk);
                                                // Paper start
                                                if (trackEntity != null) {
                                                    trackEntity.accept(entityinsentient);
                                                }
                                                // Paper end
                                            }
                                            // CraftBukkit end
                                            if (j >= entityinsentient.getMaxSpawnClusterSize() || j >= maxSpawns) { // Paper
                                                return j; // Paper
                                            }

                                            if (entityinsentient.isMaxGroupSizeReached(k1)) {
                                                break label53;
                                            }
                                        }
                                    }
                                }
                            }

                            ++l1;
                            continue;
                        }
                    }

                    ++k;
                    break;
                }
            }

        }
        return j; // Paper
    }

    private static boolean isRightDistanceToPlayerAndSpawnPoint(ServerLevel world, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double squaredDistance) {
        return squaredDistance <= 576.0D ? false : (world.getSharedSpawnPos().closerToCenterThan(new Vec3((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D), 24.0D) ? false : Objects.equals(new ChunkPos(pos), chunk.getPos()) || world.isNaturalSpawningAllowed((BlockPos) pos));
    }

    // Paper start
    private enum PreSpawnStatus {
        FAIL,
        SUCCESS,
        CANCELLED,
        ABORT
    }
    private static PreSpawnStatus isValidSpawnPostitionForType(ServerLevel world, MobCategory group, StructureManager structureAccessor, ChunkGenerator chunkGenerator, MobSpawnSettings.SpawnerData spawnEntry, BlockPos.MutableBlockPos pos, double squaredDistance) {
        // Paper end
        EntityType<?> entitytypes = spawnEntry.type;

        // Paper start
        com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event;
        org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.fromName(EntityType.getKey(entitytypes).getPath());
        if (type != null) {
            event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                io.papermc.paper.util.MCUtil.toLocation(world, pos),
                type, SpawnReason.NATURAL
            );
            if (!event.callEvent()) {
                if (event.shouldAbortSpawn()) {
                    return PreSpawnStatus.ABORT; // Paper
                }
                return PreSpawnStatus.CANCELLED; // Paper
            }
        }
        // Paper end
        if (entitytypes.getCategory() == MobCategory.MISC) {
            return PreSpawnStatus.FAIL; // Paper
        } else if (!entitytypes.canSpawnFarFromPlayer() && squaredDistance > (double) (entitytypes.getCategory().getDespawnDistance() * entitytypes.getCategory().getDespawnDistance())) {
            return PreSpawnStatus.FAIL; // Paper
        } else if (entitytypes.canSummon() && NaturalSpawner.canSpawnMobAt(world, structureAccessor, chunkGenerator, group, spawnEntry, pos)) {
            SpawnPlacements.Type entitypositiontypes_surface = SpawnPlacements.getPlacementType(entitytypes);

            boolean isValid = !NaturalSpawner.isSpawnPositionOk(entitypositiontypes_surface, world, pos, entitytypes) ? false : (!SpawnPlacements.checkSpawnRules(entitytypes, world, MobSpawnType.NATURAL, pos, world.random) ? false : world.noCollision(entitytypes.getAABB((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D))); // Paper
            return isValid ? PreSpawnStatus.SUCCESS : PreSpawnStatus.FAIL; // Paper
        } else {
            return PreSpawnStatus.FAIL; // Paper
        }
    }

    @Nullable
    private static Mob getMobForSpawn(ServerLevel world, EntityType<?> type) {
        try {
            Entity entity = type.create(world);

            if (entity instanceof Mob) {
                Mob entityinsentient = (Mob) entity;

                return entityinsentient;
            }

            NaturalSpawner.LOGGER.warn("Can't spawn entity of type: {}", BuiltInRegistries.ENTITY_TYPE.getKey(type));
        } catch (Exception exception) {
            NaturalSpawner.LOGGER.warn("Failed to create mob", exception);
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exception); // Paper
        }

        return null;
    }

    private static boolean isValidPositionForMob(ServerLevel world, Mob entity, double squaredDistance) {
        return squaredDistance > (double) (entity.getType().getCategory().getDespawnDistance() * entity.getType().getCategory().getDespawnDistance()) && entity.removeWhenFarAway(squaredDistance) ? false : entity.checkSpawnRules(world, MobSpawnType.NATURAL) && entity.checkSpawnObstruction(world);
    }

    private static Optional<MobSpawnSettings.SpawnerData> getRandomSpawnMobAt(ServerLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, MobCategory spawnGroup, RandomSource random, BlockPos pos) {
        Holder<Biome> holder = world.getBiome(pos);

        return spawnGroup == MobCategory.WATER_AMBIENT && holder.is(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS) && random.nextFloat() < 0.98F ? Optional.empty() : NaturalSpawner.mobsAt(world, structureAccessor, chunkGenerator, spawnGroup, pos, holder).getRandom(random);
    }

    private static boolean canSpawnMobAt(ServerLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, MobCategory spawnGroup, MobSpawnSettings.SpawnerData spawnEntry, BlockPos pos) {
        return NaturalSpawner.mobsAt(world, structureAccessor, chunkGenerator, spawnGroup, pos, (Holder) null).unwrap().contains(spawnEntry);
    }

    private static WeightedRandomList<MobSpawnSettings.SpawnerData> mobsAt(ServerLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, MobCategory spawnGroup, BlockPos pos, @Nullable Holder<Biome> biomeEntry) {
        return NaturalSpawner.isInNetherFortressBounds(pos, world, spawnGroup, structureAccessor) ? NetherFortressStructure.FORTRESS_ENEMIES : chunkGenerator.getMobsAt(biomeEntry != null ? biomeEntry : world.getBiome(pos), structureAccessor, spawnGroup, pos);
    }

    public static boolean isInNetherFortressBounds(BlockPos pos, ServerLevel world, MobCategory spawnGroup, StructureManager structureAccessor) {
        if (spawnGroup == MobCategory.MONSTER && world.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) {
            Structure structure = (Structure) structureAccessor.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.FORTRESS);

            return structure == null ? false : structureAccessor.getStructureAt(pos, structure).isValid();
        } else {
            return false;
        }
    }

    private static BlockPos getRandomPosWithin(Level world, LevelChunk chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        int i = chunkcoordintpair.getMinBlockX() + world.random.nextInt(16);
        int j = chunkcoordintpair.getMinBlockZ() + world.random.nextInt(16);
        int k = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, j) + 1;
        int l = Mth.randomBetweenInclusive(world.random, world.getMinBuildHeight(), k);

        return new BlockPos(i, l, j);
    }

    public static boolean isValidEmptySpawnBlock(BlockGetter blockView, BlockPos pos, BlockState state, FluidState fluidState, EntityType<?> entityType) {
        return state.isCollisionShapeFullBlock(blockView, pos) ? false : (state.isSignalSource() ? false : (!fluidState.isEmpty() ? false : (state.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) ? false : !entityType.isBlockDangerous(state))));
    }

    public static boolean isSpawnPositionOk(SpawnPlacements.Type location, LevelReader world, BlockPos pos, @Nullable EntityType<?> entityType) {
        if (location == SpawnPlacements.Type.NO_RESTRICTIONS) {
            return true;
        } else if (entityType != null && world.getWorldBorder().isWithinBounds(pos)) {
            BlockState iblockdata = world.getBlockState(pos);
            FluidState fluid = world.getFluidState(pos);
            BlockPos blockposition1 = pos.above();
            BlockPos blockposition2 = pos.below();

            switch (location) {
                case IN_WATER:
                    return fluid.is(FluidTags.WATER) && !world.getBlockState(blockposition1).isRedstoneConductor(world, blockposition1);
                case IN_LAVA:
                    return fluid.is(FluidTags.LAVA);
                case ON_GROUND:
                default:
                    BlockState iblockdata1 = world.getBlockState(blockposition2);

                    return !iblockdata1.isValidSpawn(world, blockposition2, entityType) ? false : NaturalSpawner.isValidEmptySpawnBlock(world, pos, iblockdata, fluid, entityType) && NaturalSpawner.isValidEmptySpawnBlock(world, blockposition1, world.getBlockState(blockposition1), world.getFluidState(blockposition1), entityType);
            }
        } else {
            return false;
        }
    }

    public static void spawnMobsForChunkGeneration(ServerLevelAccessor world, Holder<Biome> biomeEntry, ChunkPos chunkPos, RandomSource random) {
        MobSpawnSettings biomesettingsmobs = ((Biome) biomeEntry.value()).getMobSettings();
        WeightedRandomList<MobSpawnSettings.SpawnerData> weightedrandomlist = biomesettingsmobs.getMobs(MobCategory.CREATURE);

        if (!weightedrandomlist.isEmpty()) {
            int i = chunkPos.getMinBlockX();
            int j = chunkPos.getMinBlockZ();

            while (random.nextFloat() < biomesettingsmobs.getCreatureProbability()) {
                Optional<MobSpawnSettings.SpawnerData> optional = weightedrandomlist.getRandom(random);

                if (!optional.isEmpty()) {
                    MobSpawnSettings.SpawnerData biomesettingsmobs_c = (MobSpawnSettings.SpawnerData) optional.get();
                    int k = biomesettingsmobs_c.minCount + random.nextInt(1 + biomesettingsmobs_c.maxCount - biomesettingsmobs_c.minCount);
                    SpawnGroupData groupdataentity = null;
                    int l = i + random.nextInt(16);
                    int i1 = j + random.nextInt(16);
                    int j1 = l;
                    int k1 = i1;

                    for (int l1 = 0; l1 < k; ++l1) {
                        boolean flag = false;

                        for (int i2 = 0; !flag && i2 < 4; ++i2) {
                            BlockPos blockposition = NaturalSpawner.getTopNonCollidingPos(world, biomesettingsmobs_c.type, l, i1);

                            if (biomesettingsmobs_c.type.canSummon() && NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(biomesettingsmobs_c.type), world, blockposition, biomesettingsmobs_c.type)) {
                                float f = biomesettingsmobs_c.type.getWidth();
                                double d0 = Mth.clamp((double) l, (double) i + (double) f, (double) i + 16.0D - (double) f);
                                double d1 = Mth.clamp((double) i1, (double) j + (double) f, (double) j + 16.0D - (double) f);

                                if (!world.noCollision(biomesettingsmobs_c.type.getAABB(d0, (double) blockposition.getY(), d1)) || !SpawnPlacements.checkSpawnRules(biomesettingsmobs_c.type, world, MobSpawnType.CHUNK_GENERATION, BlockPos.containing(d0, (double) blockposition.getY(), d1), world.getRandom())) {
                                    continue;
                                }

                                Entity entity;

                                try {
                                    entity = biomesettingsmobs_c.type.create(world.getLevel());
                                } catch (Exception exception) {
                                    NaturalSpawner.LOGGER.warn("Failed to create mob", exception);
                                    com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exception); // Paper
                                    continue;
                                }

                                if (entity == null) {
                                    continue;
                                }

                                entity.moveTo(d0, (double) blockposition.getY(), d1, random.nextFloat() * 360.0F, 0.0F);
                                if (entity instanceof Mob) {
                                    Mob entityinsentient = (Mob) entity;

                                    if (entityinsentient.checkSpawnRules(world, MobSpawnType.CHUNK_GENERATION) && entityinsentient.checkSpawnObstruction(world)) {
                                        groupdataentity = entityinsentient.finalizeSpawn(world, world.getCurrentDifficultyAt(entityinsentient.blockPosition()), MobSpawnType.CHUNK_GENERATION, groupdataentity, (CompoundTag) null);
                                        world.addFreshEntityWithPassengers(entityinsentient, SpawnReason.CHUNK_GEN); // CraftBukkit
                                        flag = true;
                                    }
                                }
                            }

                            l += random.nextInt(5) - random.nextInt(5);

                            for (i1 += random.nextInt(5) - random.nextInt(5); l < i || l >= i + 16 || i1 < j || i1 >= j + 16; i1 = k1 + random.nextInt(5) - random.nextInt(5)) {
                                l = j1 + random.nextInt(5) - random.nextInt(5);
                            }
                        }
                    }
                }
            }

        }
    }

    private static BlockPos getTopNonCollidingPos(LevelReader world, EntityType<?> entityType, int x, int z) {
        int k = world.getHeight(SpawnPlacements.getHeightmapType(entityType), x, z);
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos(x, k, z);

        if (world.dimensionType().hasCeiling()) {
            do {
                blockposition_mutableblockposition.move(Direction.DOWN);
            } while (!world.getBlockState(blockposition_mutableblockposition).isAir());

            do {
                blockposition_mutableblockposition.move(Direction.DOWN);
            } while (world.getBlockState(blockposition_mutableblockposition).isAir() && blockposition_mutableblockposition.getY() > world.getMinBuildHeight());
        }

        if (SpawnPlacements.getPlacementType(entityType) == SpawnPlacements.Type.ON_GROUND) {
            BlockPos blockposition = blockposition_mutableblockposition.below();

            if (world.getBlockState(blockposition).isPathfindable(world, blockposition, PathComputationType.LAND)) {
                return blockposition;
            }
        }

        return blockposition_mutableblockposition.immutable();
    }

    @FunctionalInterface
    public interface ChunkGetter {

        void query(long pos, Consumer<LevelChunk> chunkConsumer);
    }

    public static class SpawnState {

        private final int spawnableChunkCount;
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
        private final PotentialCalculator spawnPotential;
        private final Object2IntMap<MobCategory> unmodifiableMobCategoryCounts;
        private final LocalMobCapCalculator localMobCapCalculator;
        @Nullable
        private BlockPos lastCheckedPos;
        @Nullable
        private EntityType<?> lastCheckedType;
        private double lastCharge;

        SpawnState(int spawningChunkCount, Object2IntOpenHashMap<MobCategory> groupToCount, PotentialCalculator densityField, LocalMobCapCalculator densityCapper) {
            this.spawnableChunkCount = spawningChunkCount;
            this.mobCategoryCounts = groupToCount;
            this.spawnPotential = densityField;
            this.localMobCapCalculator = densityCapper;
            this.unmodifiableMobCategoryCounts = Object2IntMaps.unmodifiable(groupToCount);
        }

        private boolean canSpawn(EntityType<?> type, BlockPos pos, ChunkAccess chunk) {
            this.lastCheckedPos = pos;
            this.lastCheckedType = type;
            MobSpawnSettings.MobSpawnCost biomesettingsmobs_b = NaturalSpawner.getRoughBiome(pos, chunk).getMobSettings().getMobSpawnCost(type);

            if (biomesettingsmobs_b == null) {
                this.lastCharge = 0.0D;
                return true;
            } else {
                double d0 = biomesettingsmobs_b.charge();

                this.lastCharge = d0;
                double d1 = this.spawnPotential.getPotentialEnergyChange(pos, d0);

                return d1 <= biomesettingsmobs_b.energyBudget();
            }
        }

        private void afterSpawn(Mob entity, ChunkAccess chunk) {
            EntityType<?> entitytypes = entity.getType();
            BlockPos blockposition = entity.blockPosition();
            double d0;

            if (blockposition.equals(this.lastCheckedPos) && entitytypes == this.lastCheckedType) {
                d0 = this.lastCharge;
            } else {
                MobSpawnSettings.MobSpawnCost biomesettingsmobs_b = NaturalSpawner.getRoughBiome(blockposition, chunk).getMobSettings().getMobSpawnCost(entitytypes);

                if (biomesettingsmobs_b != null) {
                    d0 = biomesettingsmobs_b.charge();
                } else {
                    d0 = 0.0D;
                }
            }

            this.spawnPotential.addCharge(blockposition, d0);
            MobCategory enumcreaturetype = entitytypes.getCategory();

            this.mobCategoryCounts.addTo(enumcreaturetype, 1);
            if (this.localMobCapCalculator != null) this.localMobCapCalculator.addMob(new ChunkPos(blockposition), enumcreaturetype); // Paper
        }

        public int getSpawnableChunkCount() {
            return this.spawnableChunkCount;
        }

        public Object2IntMap<MobCategory> getMobCategoryCounts() {
            return this.unmodifiableMobCategoryCounts;
        }

        // CraftBukkit start
        boolean canSpawnForCategory(MobCategory enumcreaturetype, ChunkPos chunkcoordintpair, int limit) {
            int i = limit * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
            // CraftBukkit end

            if (this.localMobCapCalculator == null) return this.mobCategoryCounts.getInt(enumcreaturetype) < i; // Paper
            return this.mobCategoryCounts.getInt(enumcreaturetype) >= i ? false : this.localMobCapCalculator.canSpawn(enumcreaturetype, chunkcoordintpair);
        }
    }

    @FunctionalInterface
    public interface SpawnPredicate {

        boolean test(EntityType<?> type, BlockPos pos, ChunkAccess chunk);
    }

    @FunctionalInterface
    public interface AfterSpawnCallback {

        void run(Mob entity, ChunkAccess chunk);
    }
}
