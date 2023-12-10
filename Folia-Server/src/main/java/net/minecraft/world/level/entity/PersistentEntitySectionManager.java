package net.minecraft.world.level.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import net.minecraft.world.level.ChunkPos;
// CraftBukkit start
import net.minecraft.world.level.chunk.storage.EntityStorage;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {

    static final Logger LOGGER = LogUtils.getLogger();
    final Set<UUID> knownUuids = Sets.newHashSet();
    final LevelCallback<T> callbacks;
    public final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage = new EntityLookup<>();
    final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    private final Long2ObjectMap<Visibility> chunkVisibility = new Long2ObjectOpenHashMap();
    private final Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new Long2ObjectOpenHashMap();
    private final LongSet chunksToUnload = new LongOpenHashSet();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(Class<T> entityClass, LevelCallback<T> handler, EntityPersistentStorage<T> dataAccess) {
        this.sectionStorage = new EntitySectionStorage<>(entityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = handler;
        this.permanentStorage = dataAccess;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.visibleEntityStorage, this.sectionStorage);
    }

    // CraftBukkit start - add method to get all entities in chunk
    public List<Entity> getEntities(ChunkPos chunkCoordIntPair) {
        return this.sectionStorage.getExistingSectionsInChunk(chunkCoordIntPair.toLong()).flatMap(EntitySection::getEntities).map(entity -> (Entity) entity).collect(Collectors.toList());
    }

    public boolean isPending(long pair) {
        return this.chunkLoadStatuses.get(pair) == ChunkLoadStatus.PENDING;
    }
    // CraftBukkit end

    void removeSectionIfEmpty(long sectionPos, EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionPos);
        }

    }

    private boolean addEntityUuid(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity add by UUID"); // Paper
        if (!this.knownUuids.add(entity.getUUID())) {
            PersistentEntitySectionManager.LOGGER.warn("UUID of added entity already exists: {}", entity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(T entity) {
        return this.addEntity(entity, false);
    }

    private boolean addEntity(T entity, boolean existing) {
        org.spigotmc.AsyncCatcher.catchOp("Entity add"); // Paper
        // Paper start - chunk system hooks
        if (existing) {
            // I don't want to know why this is a generic type.
            Entity entityCasted = (Entity)entity;
            boolean wasRemoved = entityCasted.isRemoved();
            io.papermc.paper.chunk.system.ChunkSystem.onEntityPreAdd((net.minecraft.server.level.ServerLevel) entityCasted.level(), entityCasted);
            if (!wasRemoved && entityCasted.isRemoved()) {
                // removed by callback
                return false;
            }
        }
        // Paper end - chunk system hooks
        if (!this.addEntityUuid(entity)) {
            return false;
        } else {
            long i = SectionPos.asLong(entity.blockPosition());
            EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);

            entitysection.add(entity);
            entity.setLevelCallback(new PersistentEntitySectionManager.Callback(entity, i, entitysection));
            if (!existing) {
                this.callbacks.onCreated(entity);
            }

            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(entity, entitysection.getStatus());

            if (visibility.isAccessible()) {
                this.startTracking(entity);
            }

            if (visibility.isTicking()) {
                this.startTicking(entity);
            }

            return true;
        }
    }

    static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility current) {
        return entity.isAlwaysTicking() ? Visibility.TICKING : current;
    }

    public void addLegacyChunkEntities(Stream<T> entities) {
        entities.forEach((entityaccess) -> {
            this.addEntity(entityaccess, true);
        });
    }

    public void addWorldGenChunkEntities(Stream<T> entities) {
        entities.forEach((entityaccess) -> {
            this.addEntity(entityaccess, false);
        });
    }

    void startTicking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity start ticking"); // Paper
        this.callbacks.onTickingStart(entity);
    }

    void stopTicking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity stop ticking"); // Paper
        this.callbacks.onTickingEnd(entity);
    }

    void startTracking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity start tracking"); // Paper
        this.visibleEntityStorage.add(entity);
        this.callbacks.onTrackingStart(entity);
    }

    void stopTracking(T entity) {
        org.spigotmc.AsyncCatcher.catchOp("Entity stop tracking"); // Paper
        this.callbacks.onTrackingEnd(entity);
        this.visibleEntityStorage.remove(entity);
    }

    public void updateChunkStatus(ChunkPos chunkPos, FullChunkStatus levelType) {
        Visibility visibility = Visibility.fromFullChunkStatus(levelType);

        this.updateChunkStatus(chunkPos, visibility);
    }

    public void updateChunkStatus(ChunkPos chunkPos, Visibility trackingStatus) {
        org.spigotmc.AsyncCatcher.catchOp("Update chunk status"); // Paper
        long i = chunkPos.toLong();

        if (trackingStatus == Visibility.HIDDEN) {
            this.chunkVisibility.remove(i);
            this.chunksToUnload.add(i);
        } else {
            this.chunkVisibility.put(i, trackingStatus);
            this.chunksToUnload.remove(i);
            this.ensureChunkQueuedForLoad(i);
        }

        this.sectionStorage.getExistingSectionsInChunk(i).forEach((entitysection) -> {
            Visibility visibility1 = entitysection.updateChunkStatus(trackingStatus);
            boolean flag = visibility1.isAccessible();
            boolean flag1 = trackingStatus.isAccessible();
            boolean flag2 = visibility1.isTicking();
            boolean flag3 = trackingStatus.isTicking();

            if (flag2 && !flag3) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::stopTicking);
            }

            if (flag && !flag1) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::stopTracking);
            } else if (!flag && flag1) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::startTracking);
            }

            if (!flag2 && flag3) {
                entitysection.getEntities().filter((entityaccess) -> {
                    return !entityaccess.isAlwaysTicking();
                }).forEach(this::startTicking);
            }

        });
    }

    public void ensureChunkQueuedForLoad(long chunkPos) {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk save"); // Paper
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager_b = (PersistentEntitySectionManager.ChunkLoadStatus) this.chunkLoadStatuses.get(chunkPos);

        if (persistententitysectionmanager_b == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPos);
        }

    }

    private boolean storeChunkSections(long chunkPos, Consumer<T> action) {
        // CraftBukkit start - add boolean for event call
        return this.storeChunkSections(chunkPos, action, false);
    }

    private boolean storeChunkSections(long i, Consumer<T> consumer, boolean callEvent) {
        // CraftBukkit end
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager_b = (PersistentEntitySectionManager.ChunkLoadStatus) this.chunkLoadStatuses.get(i);

        if (persistententitysectionmanager_b == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        } else {
            List<T> list = (List) this.sectionStorage.getExistingSectionsInChunk(i).flatMap((entitysection) -> {
                return entitysection.getEntities().filter(EntityAccess::shouldBeSaved);
            }).collect(Collectors.toList());

            if (list.isEmpty()) {
                if (persistententitysectionmanager_b == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                    if (callEvent) CraftEventFactory.callEntitiesUnloadEvent(((EntityStorage) this.permanentStorage).level, new ChunkPos(i), ImmutableList.of()); // CraftBukkit
                    this.permanentStorage.storeEntities(new ChunkEntities<>(new ChunkPos(i), ImmutableList.of()));
                }

                return true;
            } else if (persistententitysectionmanager_b == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
                this.requestChunkLoad(i);
                return false;
            } else {
                if (callEvent) CraftEventFactory.callEntitiesUnloadEvent(((EntityStorage) this.permanentStorage).level, new ChunkPos(i), list.stream().map(entity -> (Entity) entity).collect(Collectors.toList())); // CraftBukkit
                this.permanentStorage.storeEntities(new ChunkEntities<>(new ChunkPos(i), list));
                list.forEach(consumer);
                return true;
            }
        }
    }

    private void requestChunkLoad(long chunkPos) {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk load request"); // Paper
        this.chunkLoadStatuses.put(chunkPos, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        ChunkPos chunkcoordintpair = new ChunkPos(chunkPos);
        CompletableFuture completablefuture = this.permanentStorage.loadEntities(chunkcoordintpair);
        Queue queue = this.loadingInbox;

        Objects.requireNonNull(this.loadingInbox);
        completablefuture.thenAccept(queue::add).exceptionally((throwable) -> {
            PersistentEntitySectionManager.LOGGER.error("Failed to read chunk {}", chunkcoordintpair, throwable);
            return null;
        });
    }

    private boolean processChunkUnload(long chunkPos) {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk unload process"); // Paper
        boolean flag = this.storeChunkSections(chunkPos, (entityaccess) -> {
            entityaccess.getPassengersAndSelf().forEach(this::unloadEntity);
        }, true); // CraftBukkit - add boolean for event call

        if (!flag) {
            return false;
        } else {
            this.chunkLoadStatuses.remove(chunkPos);
            return true;
        }
    }

    private void unloadEntity(EntityAccess entity) {
        entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        entity.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload.removeIf((java.util.function.LongPredicate) (i) -> { // CraftBukkit - decompile error
            return this.chunkVisibility.get(i) != Visibility.HIDDEN ? true : this.processChunkUnload(i);
        });
    }

    private void processPendingLoads() {
        org.spigotmc.AsyncCatcher.catchOp("Entity chunk process pending loads"); // Paper
        ChunkEntities<T> chunkentities; // CraftBukkit - decompile error

        while ((chunkentities = (ChunkEntities) this.loadingInbox.poll()) != null) {
            chunkentities.getEntities().forEach((entityaccess) -> {
                this.addEntity(entityaccess, true);
            });
            this.chunkLoadStatuses.put(chunkentities.getPos().toLong(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
            // CraftBukkit start - call entity load event
            List<Entity> entities = this.getEntities(chunkentities.getPos());
            CraftEventFactory.callEntitiesLoadEvent(((EntityStorage) this.permanentStorage).level, chunkentities.getPos(), entities);
            // CraftBukkit end
        }

    }

    public void tick() {
        org.spigotmc.AsyncCatcher.catchOp("Entity manager tick"); // Paper
        this.processPendingLoads();
        this.processUnloads();
    }

    private LongSet getAllChunksToSave() {
        LongSet longset = this.sectionStorage.getAllChunksWithExistingSections();
        ObjectIterator objectiterator = Long2ObjectMaps.fastIterable(this.chunkLoadStatuses).iterator();

        while (objectiterator.hasNext()) {
            Entry<PersistentEntitySectionManager.ChunkLoadStatus> entry = (Entry) objectiterator.next();

            if (entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                longset.add(entry.getLongKey());
            }
        }

        return longset;
    }

    public void autoSave() {
        org.spigotmc.AsyncCatcher.catchOp("Entity manager autosave"); // Paper
        this.getAllChunksToSave().forEach((java.util.function.LongConsumer) (i) -> { // CraftBukkit - decompile error
            boolean flag = this.chunkVisibility.get(i) == Visibility.HIDDEN;

            if (flag) {
                this.processChunkUnload(i);
            } else {
                this.storeChunkSections(i, (entityaccess) -> {
                });
            }

        });
    }

    public void saveAll() {
        org.spigotmc.AsyncCatcher.catchOp("Entity manager save"); // Paper
        LongSet longset = this.getAllChunksToSave();

        while (!longset.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            longset.removeIf((java.util.function.LongPredicate) (i) -> { // CraftBukkit - decompile error
                boolean flag = this.chunkVisibility.get(i) == Visibility.HIDDEN;

                return flag ? this.processChunkUnload(i) : this.storeChunkSections(i, (entityaccess) -> {
                });
            });
        }

        this.permanentStorage.flush(true);
    }

    public void close() throws IOException {
        // CraftBukkit start - add save boolean
        this.close(true);
    }

    public void close(boolean save) throws IOException {
        if (save) {
            this.saveAll();
        }
        // CraftBukkit end
        this.permanentStorage.close();
    }

    public boolean isLoaded(UUID uuid) {
        return this.knownUuids.contains(uuid);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(BlockPos pos) {
        return ((Visibility) this.chunkVisibility.get(ChunkPos.asLong(pos))).isTicking();
    }

    public boolean canPositionTick(ChunkPos pos) {
        return ((Visibility) this.chunkVisibility.get(pos.toLong())).isTicking();
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        return this.chunkLoadStatuses.get(chunkPos) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(Writer writer) throws IOException {
        CsvOutput csvwriter = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("visibility").addColumn("load_status").addColumn("entity_count").build(writer);

        this.sectionStorage.getAllChunksWithExistingSections().forEach((java.util.function.LongConsumer) (i) -> { // CraftBukkit - decompile error
            PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager_b = (PersistentEntitySectionManager.ChunkLoadStatus) this.chunkLoadStatuses.get(i);

            this.sectionStorage.getExistingSectionPositionsInChunk(i).forEach((j) -> {
                EntitySection<T> entitysection = this.sectionStorage.getSection(j);

                if (entitysection != null) {
                    try {
                        csvwriter.writeRow(SectionPos.x(j), SectionPos.y(j), SectionPos.z(j), entitysection.getStatus(), persistententitysectionmanager_b, entitysection.size());
                    } catch (IOException ioexception) {
                        throw new UncheckedIOException(ioexception);
                    }
                }

            });
        });
    }

    @VisibleForDebug
    public String gatherStats() {
        int i = this.knownUuids.size();

        return i + "," + this.visibleEntityStorage.count() + "," + this.sectionStorage.count() + "," + this.chunkLoadStatuses.size() + "," + this.chunkVisibility.size() + "," + this.loadingInbox.size() + "," + this.chunksToUnload.size();
    }

    private static enum ChunkLoadStatus {

        FRESH, PENDING, LOADED;

        private ChunkLoadStatus() {}
    }

    private class Callback implements EntityInLevelCallback {

        private final T entity;
        private long currentSectionKey;
        private EntitySection<T> currentSection;

        Callback(EntityAccess entityaccess, long i, EntitySection entitysection) {
            this.entity = (T) entityaccess; // CraftBukkit - decompile error
            this.currentSectionKey = i;
            this.currentSection = entitysection;
        }

        @Override
        public void onMove() {
            BlockPos blockposition = this.entity.blockPosition();
            long i = SectionPos.asLong(blockposition);

            if (i != this.currentSectionKey) {
                org.spigotmc.AsyncCatcher.catchOp("Entity move"); // Paper
                Visibility visibility = this.currentSection.getStatus();

                if (!this.currentSection.remove(this.entity)) {
                    PersistentEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (moving to {})", new Object[]{this.entity, SectionPos.of(this.currentSectionKey), i});
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(i);

                entitysection.add(this.entity);
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                this.updateStatus(visibility, entitysection.getStatus());
            }

        }

        private void updateStatus(Visibility oldStatus, Visibility newStatus) {
            Visibility visibility2 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, oldStatus);
            Visibility visibility3 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, newStatus);

            if (visibility2 == visibility3) {
                if (visibility3.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }

            } else {
                boolean flag = visibility2.isAccessible();
                boolean flag1 = visibility3.isAccessible();

                if (flag && !flag1) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!flag && flag1) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean flag2 = visibility2.isTicking();
                boolean flag3 = visibility3.isTicking();

                if (flag2 && !flag3) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!flag2 && flag3) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (flag1) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }

            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            org.spigotmc.AsyncCatcher.catchOp("Entity remove"); // Paper
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (destroying due to {})", new Object[]{this.entity, SectionPos.of(this.currentSectionKey), reason});
            }

            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());

            if (visibility.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (visibility.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (reason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(PersistentEntitySectionManager.Callback.NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }
}
