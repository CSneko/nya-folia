package net.minecraft.world.entity.ai.village.poi;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SectionStorage;

public class PoiManager extends SectionStorage<PoiSection> {
    public static final int MAX_VILLAGE_DISTANCE = 6;
    public static final int VILLAGE_SECTION_SIZE = 1;
    // Paper start - rewrite chunk system
    // the vanilla tracker needs to be replaced because it does not support level removes
    public final net.minecraft.server.level.ServerLevel world;
    private final io.papermc.paper.util.misc.Delayed26WayDistancePropagator3D villageDistanceTracker = new io.papermc.paper.util.misc.Delayed26WayDistancePropagator3D();
    static final int POI_DATA_SOURCE = 7;
    public static int convertBetweenLevels(final int level) {
        return POI_DATA_SOURCE - level;
    }

    protected void updateDistanceTracking(long section) {
        synchronized (this.villageDistanceTracker) { // Folia - region threading
        if (this.isVillageCenter(section)) {
            this.villageDistanceTracker.setSource(section, POI_DATA_SOURCE);
        } else {
            this.villageDistanceTracker.removeSource(section);
        }
        } // Folia - region threading
    }
    // Paper end - rewrite chunk system


    public PoiManager(Path path, DataFixer dataFixer, boolean dsync, RegistryAccess registryManager, LevelHeightAccessor world) {
        super(path, PoiSection::codec, PoiSection::new, dataFixer, DataFixTypes.POI_CHUNK, dsync, registryManager, world);
        this.world = (net.minecraft.server.level.ServerLevel)world; // Paper - rewrite chunk system
    }

    public void add(BlockPos pos, Holder<PoiType> type) {
        this.getOrCreate(SectionPos.asLong(pos)).add(pos, type);
    }

    public void remove(BlockPos pos) {
        this.getOrLoad(SectionPos.asLong(pos)).ifPresent((poiSet) -> {
            poiSet.remove(pos);
        });
    }

    public long getCountInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        return this.getInRange(typePredicate, pos, radius, occupationStatus).count();
    }

    public boolean existsAtPosition(ResourceKey<PoiType> type, BlockPos pos) {
        return this.exists(pos, (entry) -> {
            return entry.is(type);
        });
    }

    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        int i = Math.floorDiv(radius, 16) + 1;
        return ChunkPos.rangeClosed(new ChunkPos(pos), i).flatMap((chunkPos) -> {
            return this.getInChunk(typePredicate, chunkPos, occupationStatus);
        }).filter((poi) -> {
            BlockPos blockPos2 = poi.getPos();
            return Math.abs(blockPos2.getX() - pos.getX()) <= radius && Math.abs(blockPos2.getZ() - pos.getZ()) <= radius;
        });
    }

    public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        int i = radius * radius;
        return this.getInSquare(typePredicate, pos, radius, occupationStatus).filter((poi) -> {
            return poi.getPos().distSqr(pos) <= (double)i;
        });
    }

    @VisibleForDebug
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> typePredicate, ChunkPos chunkPos, PoiManager.Occupancy occupationStatus) {
        return IntStream.range(this.levelHeightAccessor.getMinSection(), this.levelHeightAccessor.getMaxSection()).boxed().map((integer) -> {
            return this.getOrLoad(SectionPos.of(chunkPos, integer).asLong());
        }).filter(Optional::isPresent).flatMap((optional) -> {
            return optional.get().getRecords(typePredicate, occupationStatus);
        });
    }

    public Stream<BlockPos> findAll(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        return this.getInRange(typePredicate, pos, radius, occupationStatus).map(PoiRecord::getPos).filter(posPredicate);
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        return this.getInRange(typePredicate, pos, radius, occupationStatus).filter((poi) -> {
            return posPredicate.test(poi.getPos());
        }).map((poi) -> {
            return Pair.of(poi.getPoiType(), poi.getPos());
        });
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        return this.findAllWithType(typePredicate, posPredicate, pos, radius, occupationStatus).sorted(Comparator.comparingDouble((pair) -> {
            return pair.getSecond().distSqr(pos);
        }));
    }

    public Optional<BlockPos> find(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findAnyPoiPosition(this, typePredicate, posPredicate, pos, radius, occupationStatus, false);
        return Optional.ofNullable(ret);
        // Paper end
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findClosestPoiDataPosition(this, typePredicate, null, pos, radius, radius * radius, occupationStatus, false);
        return Optional.ofNullable(ret);
        // Paper end - re-route to faster logic
    }

    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        // Paper start - re-route to faster logic
        return Optional.ofNullable(io.papermc.paper.util.PoiAccess.findClosestPoiDataTypeAndPosition(
            this, typePredicate, null, pos, radius, radius * radius, occupationStatus, false
        ));
        // Paper end - re-route to faster logic
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findClosestPoiDataPosition(this, typePredicate, posPredicate, pos, radius, radius * radius, occupationStatus, false);
        return Optional.ofNullable(ret);
        // Paper end - re-route to faster logic
    }

    public Optional<BlockPos> take(Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> biPredicate, BlockPos pos, int radius) {
        // Paper start - re-route to faster logic
        final @javax.annotation.Nullable PoiRecord closest = io.papermc.paper.util.PoiAccess.findClosestPoiDataRecord(
            this, typePredicate, biPredicate, pos, radius, radius * radius, Occupancy.HAS_SPACE, false
        );
        return Optional.ofNullable(closest).map(poi -> {
            // Paper end - re-route to faster logic
            poi.acquireTicket();
            return poi.getPos();
        });
    }

    public Optional<BlockPos> getRandom(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> positionPredicate, PoiManager.Occupancy occupationStatus, BlockPos pos, int radius, RandomSource random) {
        // Paper start - re-route to faster logic
        List<PoiRecord> list = new java.util.ArrayList<>();
        io.papermc.paper.util.PoiAccess.findAnyPoiRecords(
            this, typePredicate, positionPredicate, pos, radius, occupationStatus, false, Integer.MAX_VALUE, list
        );

        // the old method shuffled the list and then tried to find the first element in it that
        // matched positionPredicate, however we moved positionPredicate into the poi search. This means we can avoid a
        // shuffle entirely, and just pick a random element from list
        if (list.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(list.get(random.nextInt(list.size())).getPos());
        // Paper end - re-route to faster logic
    }

    public boolean release(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).map((poiSet) -> {
            return poiSet.release(pos);
        }).orElseThrow(() -> {
            return Util.pauseInIde(new IllegalStateException("POI never registered at " + pos));
        });
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> predicate) {
        return this.getOrLoad(SectionPos.asLong(pos)).map((poiSet) -> {
            return poiSet.exists(pos, predicate);
        }).orElse(false);
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).flatMap((poiSet) -> {
            return poiSet.getType(pos);
        });
    }

    /** @deprecated */
    @Deprecated
    @VisibleForDebug
    public int getFreeTickets(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).map((poiSet) -> {
            return poiSet.getFreeTickets(pos);
        }).orElse(0);
    }

    public int sectionsToVillage(SectionPos pos) {
        synchronized (this.villageDistanceTracker) { // Folia - region threading
        this.villageDistanceTracker.propagateUpdates(); // Paper - replace distance tracking util
        return convertBetweenLevels(this.villageDistanceTracker.getLevel(io.papermc.paper.util.CoordinateUtils.getChunkSectionKey(pos))); // Paper - replace distance tracking util
        } // Folia - region threading
    }

    boolean isVillageCenter(long pos) {
        Optional<PoiSection> optional = this.get(pos);
        return optional == null ? false : optional.map((poiSet) -> {
            return poiSet.getRecords((entry) -> {
                return entry.is(PoiTypeTags.VILLAGE);
            }, PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent();
        }).orElse(false);
    }

    @Override
    public void tick(BooleanSupplier shouldKeepTicking) {
        synchronized (this.villageDistanceTracker) { // Folia - region threading
        this.villageDistanceTracker.propagateUpdates(); // Paper - rewrite chunk system
        } // Folia - region threading
    }

    @Override
    public void setDirty(long pos) {
        // Paper start - rewrite chunk system
        int chunkX = io.papermc.paper.util.CoordinateUtils.getChunkSectionX(pos);
        int chunkZ = io.papermc.paper.util.CoordinateUtils.getChunkSectionZ(pos);
        io.papermc.paper.chunk.system.scheduling.ChunkHolderManager manager = this.world.chunkTaskScheduler.chunkHolderManager;
        io.papermc.paper.chunk.system.poi.PoiChunk chunk = manager.getPoiChunkIfLoaded(chunkX, chunkZ, false);
        if (chunk != null) {
            chunk.setDirty(true);
        }
        this.updateDistanceTracking(pos);
        // Paper end - rewrite chunk system
    }

    @Override
    protected void onSectionLoad(long pos) {
        this.updateDistanceTracking(pos); // Paper - move to new distance tracking util
    }

    // Paper start - rewrite chunk system
    @Override
    public Optional<PoiSection> get(long pos) {
        int chunkX = io.papermc.paper.util.CoordinateUtils.getChunkSectionX(pos);
        int chunkY = io.papermc.paper.util.CoordinateUtils.getChunkSectionY(pos);
        int chunkZ = io.papermc.paper.util.CoordinateUtils.getChunkSectionZ(pos);

        io.papermc.paper.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        io.papermc.paper.chunk.system.scheduling.ChunkHolderManager manager = this.world.chunkTaskScheduler.chunkHolderManager;
        io.papermc.paper.chunk.system.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);

        return ret == null ? Optional.empty() : ret.getSectionForVanilla(chunkY);
    }

    @Override
    public Optional<PoiSection> getOrLoad(long pos) {
        int chunkX = io.papermc.paper.util.CoordinateUtils.getChunkSectionX(pos);
        int chunkY = io.papermc.paper.util.CoordinateUtils.getChunkSectionY(pos);
        int chunkZ = io.papermc.paper.util.CoordinateUtils.getChunkSectionZ(pos);

        io.papermc.paper.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        io.papermc.paper.chunk.system.scheduling.ChunkHolderManager manager = this.world.chunkTaskScheduler.chunkHolderManager;

        if (chunkY >= io.papermc.paper.util.WorldUtil.getMinSection(this.world) &&
            chunkY <= io.papermc.paper.util.WorldUtil.getMaxSection(this.world)) {
            io.papermc.paper.chunk.system.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
            if (ret != null) {
                return ret.getSectionForVanilla(chunkY);
            } else {
                return manager.loadPoiChunk(chunkX, chunkZ).getSectionForVanilla(chunkY);
            }
        }
        // retain vanilla behavior: do not load section if out of bounds!
        return Optional.empty();
    }

    @Override
    protected PoiSection getOrCreate(long pos) {
        int chunkX = io.papermc.paper.util.CoordinateUtils.getChunkSectionX(pos);
        int chunkY = io.papermc.paper.util.CoordinateUtils.getChunkSectionY(pos);
        int chunkZ = io.papermc.paper.util.CoordinateUtils.getChunkSectionZ(pos);

        io.papermc.paper.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        io.papermc.paper.chunk.system.scheduling.ChunkHolderManager manager = this.world.chunkTaskScheduler.chunkHolderManager;

        io.papermc.paper.chunk.system.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
        if (ret != null) {
            return ret.getOrCreateSection(chunkY);
        } else {
            return manager.loadPoiChunk(chunkX, chunkZ).getOrCreateSection(chunkY);
        }
    }

    public void onUnload(long coordinate) { // Paper - rewrite chunk system
        int chunkX = io.papermc.paper.util.MCUtil.getCoordinateX(coordinate);
        int chunkZ = io.papermc.paper.util.MCUtil.getCoordinateZ(coordinate);
        io.papermc.paper.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Unloading poi chunk off-main");
        for (int section = this.levelHeightAccessor.getMinSection(); section < this.levelHeightAccessor.getMaxSection(); ++section) {
            long sectionPos = SectionPos.asLong(chunkX, section, chunkZ);
            this.updateDistanceTracking(sectionPos);
        }
    }

    public void loadInPoiChunk(io.papermc.paper.chunk.system.poi.PoiChunk poiChunk) {
        int chunkX = poiChunk.chunkX;
        int chunkZ = poiChunk.chunkZ;
        io.papermc.paper.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Loading poi chunk off-main");
        for (int sectionY = this.levelHeightAccessor.getMinSection(); sectionY < this.levelHeightAccessor.getMaxSection(); ++sectionY) {
            PoiSection section = poiChunk.getSection(sectionY);
            if (section != null && !section.isEmpty()) {
                this.onSectionLoad(SectionPos.asLong(chunkX, sectionY, chunkZ));
            }
        }
    }

    public void checkConsistency(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int minY = io.papermc.paper.util.WorldUtil.getMinSection(chunk);
        int maxY = io.papermc.paper.util.WorldUtil.getMaxSection(chunk);
        LevelChunkSection[] sections = chunk.getSections();
        for (int section = minY; section <= maxY; ++section) {
            this.checkConsistencyWithBlocks(SectionPos.of(chunkX, section, chunkZ), sections[section - minY]);
        }
    }
    // Paper end - rewrite chunk system

    public void checkConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection chunkSection) {
        Util.ifElse(this.getOrLoad(sectionPos.asLong()), (poiSet) -> {
            poiSet.refresh((populator) -> {
                if (mayHavePoi(chunkSection)) {
                    this.updateFromSection(chunkSection, sectionPos, populator);
                }

            });
        }, () -> {
            if (mayHavePoi(chunkSection)) {
                PoiSection poiSection = this.getOrCreate(sectionPos.asLong());
                this.updateFromSection(chunkSection, sectionPos, poiSection::add);
            }

        });
    }

    private static boolean mayHavePoi(LevelChunkSection chunkSection) {
        return chunkSection.maybeHas(PoiTypes::hasPoi);
    }

    private void updateFromSection(LevelChunkSection chunkSection, SectionPos sectionPos, BiConsumer<BlockPos, Holder<PoiType>> populator) {
        sectionPos.blocksInside().forEach((pos) -> {
            BlockState blockState = chunkSection.getBlockState(SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()));
            PoiTypes.forState(blockState).ifPresent((poiType) -> {
                populator.accept(pos, poiType);
            });
        });
    }

    public void ensureLoadedAndValid(LevelReader world, BlockPos pos, int radius) {
        SectionPos.aroundChunk(new ChunkPos(pos), Math.floorDiv(radius, 16), this.levelHeightAccessor.getMinSection(), this.levelHeightAccessor.getMaxSection()).map((sectionPos) -> {
            return Pair.of(sectionPos, this.getOrLoad(sectionPos.asLong()));
        }).filter((pair) -> {
            return !pair.getSecond().map(PoiSection::isValid).orElse(false);
        }).map((pair) -> {
            return pair.getFirst().chunk();
        }).filter((chunkPos) -> {
            return true; // Paper - rewrite chunk system
        }).forEach((chunkPos) -> {
            world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
        });
    }

    final class DistanceTracker extends SectionTracker {
        private final Long2ByteMap levels = new Long2ByteOpenHashMap();

        protected DistanceTracker() {
            super(7, 16, 256);
            this.levels.defaultReturnValue((byte)7);
        }

        @Override
        protected int getLevelFromSource(long id) {
            return PoiManager.this.isVillageCenter(id) ? 0 : 7; // Paper - rewrite chunk system - diff on change, this specifies the source level to use for distance tracking
        }

        @Override
        protected int getLevel(long id) {
            return this.levels.get(id);
        }

        @Override
        protected void setLevel(long id, int level) {
            if (level > 6) {
                this.levels.remove(id);
            } else {
                this.levels.put(id, (byte)level);
            }

        }

        public void runAllUpdates() {
            super.runUpdates(Integer.MAX_VALUE);
        }
    }

    // Paper start - Asynchronous chunk io
    @javax.annotation.Nullable
    @Override
    public net.minecraft.nbt.CompoundTag read(ChunkPos chunkcoordintpair) throws java.io.IOException {
        // Paper start - rewrite chunk system
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            return io.papermc.paper.chunk.system.io.RegionFileIOThread.loadData(
                this.world, chunkcoordintpair.x, chunkcoordintpair.z, io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.POI_DATA,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
            );
        }
        // Paper end - rewrite chunk system
        return super.read(chunkcoordintpair);
    }

    @Override
    public void write(ChunkPos chunkcoordintpair, net.minecraft.nbt.CompoundTag nbttagcompound) throws java.io.IOException {
        // Paper start - rewrite chunk system
        if (!io.papermc.paper.chunk.system.io.RegionFileIOThread.isRegionFileThread()) {
            io.papermc.paper.chunk.system.io.RegionFileIOThread.scheduleSave(
                this.world, chunkcoordintpair.x, chunkcoordintpair.z, nbttagcompound,
                io.papermc.paper.chunk.system.io.RegionFileIOThread.RegionFileType.POI_DATA);
            return;
        }
        // Paper end - rewrite chunk system
        super.write(chunkcoordintpair, nbttagcompound);
    }
    // Paper end

    public static enum Occupancy {
        HAS_SPACE(PoiRecord::hasSpace),
        IS_OCCUPIED(PoiRecord::isOccupied),
        ANY((poi) -> {
            return true;
        });

        private final Predicate<? super PoiRecord> test;

        private Occupancy(Predicate<? super PoiRecord> predicate) {
            this.test = predicate;
        }

        public Predicate<? super PoiRecord> getTest() {
            return this.test;
        }
    }
}
