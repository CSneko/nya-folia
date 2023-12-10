package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public abstract class DistanceManager {

    // Paper start - rewrite chunk system
    public io.papermc.paper.chunk.system.scheduling.ChunkHolderManager getChunkHolderManager() {
        return this.chunkMap.level.chunkTaskScheduler.chunkHolderManager;
    }
    // Paper end - rewrite chunk system

    static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap();
    // Paper - rewrite chunk system
    public static final int MOB_SPAWN_RANGE = 8; //private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8); // Paper - optimise chunk tick iteration
    // Paper - rewrite chunk system
    private final ChunkMap chunkMap; // Paper

    protected DistanceManager(Executor workerExecutor, Executor mainThreadExecutor, ChunkMap chunkMap) {
        // Paper - rewrite chunk system
        this.chunkMap = chunkMap; // Paper
    }

    protected void purgeStaleTickets() {
        this.getChunkHolderManager().tick(); // Paper - rewrite chunk system
    }

    private static int getTicketLevelAt(SortedArraySet<Ticket<?>> tickets) {
        return !tickets.isEmpty() ? ((Ticket) tickets.first()).getTicketLevel() : ChunkLevel.MAX_LEVEL + 1;
    }

    protected abstract boolean isChunkToRemove(long pos);

    @Nullable
    protected abstract ChunkHolder getChunk(long pos);

    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(long pos, int level, @Nullable ChunkHolder holder, int k);

    public boolean runAllUpdates(ChunkMap chunkStorage) {
        return this.getChunkHolderManager().processTicketUpdates(); // Paper - rewrite chunk system
    }

    boolean addTicket(long i, Ticket<?> ticket) { // CraftBukkit - void -> boolean
        org.spigotmc.AsyncCatcher.catchOp("ChunkMapDistance::addTicket"); // Paper
        return this.getChunkHolderManager().addTicketAtLevel((TicketType)ticket.getType(), i, ticket.getTicketLevel(), ticket.key); // Paper - rewrite chunk system
    }

    boolean removeTicket(long i, Ticket<?> ticket) { // CraftBukkit - void -> boolean
        org.spigotmc.AsyncCatcher.catchOp("ChunkMapDistance::removeTicket"); // Paper
        return this.getChunkHolderManager().removeTicketAtLevel((TicketType)ticket.getType(), i, ticket.getTicketLevel(), ticket.key); // Paper - rewrite chunk system
    }

    public <T> void addTicket(TicketType<T> type, ChunkPos pos, int level, T argument) {
        this.getChunkHolderManager().addTicketAtLevel(type, pos, level, argument); // Paper - rewrite chunk system
    }

    public <T> void removeTicket(TicketType<T> type, ChunkPos pos, int level, T argument) {
        this.getChunkHolderManager().removeTicketAtLevel(type, pos, level, argument); // Paper - rewrite chunk system
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int radius, T argument) {
        // CraftBukkit start
        this.addRegionTicketAtDistance(type, pos, radius, argument);
    }

    public <T> boolean addRegionTicketAtDistance(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        return this.getChunkHolderManager().addTicketAtLevel(tickettype, chunkcoordintpair, ChunkLevel.byStatus(FullChunkStatus.FULL) - i, t0); // Paper - rewrite chunk system
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int radius, T argument) {
        // CraftBukkit start
        this.removeRegionTicketAtDistance(type, pos, radius, argument);
    }

    public <T> boolean removeRegionTicketAtDistance(TicketType<T> tickettype, ChunkPos chunkcoordintpair, int i, T t0) {
        return this.getChunkHolderManager().removeTicketAtLevel(tickettype, chunkcoordintpair, ChunkLevel.byStatus(FullChunkStatus.FULL) - i, t0); // Paper - rewrite chunk system
    }

    // Paper - rewrite chunk system

    protected void updateChunkForced(ChunkPos pos, boolean forced) {
        Ticket<ChunkPos> ticket = new Ticket<>(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL, pos, 0L); // Paper - rewrite chunk system
        long i = pos.toLong();

        if (forced) {
            this.addTicket(i, ticket);
            //this.tickingTicketsTracker.addTicket(i, ticket); // Paper - no longer used
        } else {
            this.removeTicket(i, ticket);
            //this.tickingTicketsTracker.removeTicket(i, ticket); // Paper - no longer used
        }

    }

    public void addPlayer(SectionPos pos, ServerPlayer player) {
        ChunkPos chunkcoordintpair = pos.chunk();
        long i = chunkcoordintpair.toLong();

        // Paper - no longer used
        //this.naturalSpawnChunkCounter.update(i, 0, true); // Paper - optimise chunk tick iteration
        //this.playerTicketManager.update(i, 0, true); // Paper - no longer used
        //this.tickingTicketsTracker.addTicket(TicketType.PLAYER, chunkcoordintpair, this.getPlayerTicketLevel(), chunkcoordintpair); // Paper - no longer used
    }

    public void removePlayer(SectionPos pos, ServerPlayer player) {
        ChunkPos chunkcoordintpair = pos.chunk();
        long i = chunkcoordintpair.toLong();
        ObjectSet<ServerPlayer> objectset = (ObjectSet) this.playersPerChunk.get(i);
        if (objectset == null) return; // CraftBukkit - SPIGOT-6208

        if (objectset != null) objectset.remove(player); // Paper - some state corruption happens here, don't crash, clean up gracefully.
        if (objectset == null || objectset.isEmpty()) { // Paper
            this.playersPerChunk.remove(i);
            //this.naturalSpawnChunkCounter.update(i, Integer.MAX_VALUE, false); // Paper - optimise chunk tick iteration
            //this.playerTicketManager.update(i, Integer.MAX_VALUE, false); // Paper - no longer used
            //this.tickingTicketsTracker.removeTicket(TicketType.PLAYER, chunkcoordintpair, this.getPlayerTicketLevel(), chunkcoordintpair); // Paper - no longer used
        }

    }

    // Paper - rewrite chunk system

    public boolean inEntityTickingRange(long chunkPos) {
        // Paper start - replace player chunk loader system
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(chunkPos);
        return holder != null && holder.isEntityTickingReady();
        // Paper end - replace player chunk loader system
    }

    public boolean inBlockTickingRange(long chunkPos) {
        // Paper start - replace player chunk loader system
        ChunkHolder holder = this.chunkMap.getVisibleChunkIfPresent(chunkPos);
        return holder != null && holder.isTickingReady();
        // Paper end - replace player chunk loader system
    }

    protected String getTicketDebugString(long pos) {
        return this.getChunkHolderManager().getTicketDebugString(pos); // Paper - rewrite chunk system
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.chunkMap.setServerViewDistance(viewDistance);// Paper - route to player chunk manager
    }

    // Paper start
    public int getSimulationDistance() {
        return this.chunkMap.level.playerChunkLoader.getAPITickDistance();
    }
    // Paper end

    public void updateSimulationDistance(int simulationDistance) {
        this.chunkMap.level.playerChunkLoader.setTickDistance(simulationDistance); // Paper - route to player chunk manager
    }

    public int getNaturalSpawnChunkCount() {
        return this.chunkMap.level.getCurrentWorldData().mobSpawnMap.size(); // Paper - optimise chunk tick iteration // Folia - region threading
    }

    public boolean hasPlayersNearby(long chunkPos) {
        return this.chunkMap.level.getCurrentWorldData().mobSpawnMap.getObjectsInRange(chunkPos) != null; // Paper - optimise chunk tick iteration // Folia - region threading
    }

    public String getDebugStatus() {
        return "No DistanceManager stats available"; // Paper - rewrite chunk system
    }

    // Paper - rewrite chunk system

    // Paper - replace player chunk loader

    public void removeTicketsOnClosing() {
        // Paper - rewrite chunk system - this stupid hack ain't needed anymore
    }

    public boolean hasTickets() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    // CraftBukkit start
    public <T> void removeAllTicketsFor(TicketType<T> ticketType, int ticketLevel, T ticketIdentifier) {
        this.getChunkHolderManager().removeAllTicketsFor(ticketType, ticketLevel, ticketIdentifier); // Paper - rewrite chunk system
    }
    // CraftBukkit end

    /* Paper - rewrite chunk system
    private class ChunkTicketTracker extends ChunkTracker {

        private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;

        public ChunkTicketTracker() {
            super(DistanceManager.ChunkTicketTracker.MAX_LEVEL + 1, 16, 256);
        }

        @Override
        protected int getLevelFromSource(long id) {
            SortedArraySet<Ticket<?>> arraysetsorted = (SortedArraySet) DistanceManager.this.tickets.get(id);

            return arraysetsorted == null ? Integer.MAX_VALUE : (arraysetsorted.isEmpty() ? Integer.MAX_VALUE : ((Ticket) arraysetsorted.first()).getTicketLevel());
        }

        @Override
        protected int getLevel(long id) {
            if (!DistanceManager.this.isChunkToRemove(id)) {
                ChunkHolder playerchunk = DistanceManager.this.getChunk(id);

                if (playerchunk != null) {
                    return playerchunk.getTicketLevel();
                }
            }

            return DistanceManager.ChunkTicketTracker.MAX_LEVEL;
        }

        @Override
        protected void setLevel(long id, int level) {
            ChunkHolder playerchunk = DistanceManager.this.getChunk(id);
            int k = playerchunk == null ? DistanceManager.ChunkTicketTracker.MAX_LEVEL : playerchunk.getTicketLevel();

            if (k != level) {
                playerchunk = DistanceManager.this.updateChunkScheduling(id, level, playerchunk, k);
                if (playerchunk != null) {
                    DistanceManager.this.chunksToUpdateFutures.add(playerchunk);
                }

            }
        }

        public int runDistanceUpdates(int distance) {
            return this.runUpdates(distance);
        }
    }
    */ // Paper - rewrite chunk system

    private class FixedPlayerDistanceChunkTracker extends ChunkTracker {

        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(int i) {
            super(i + 2, 16, 256);
            this.maxDistance = i;
            this.chunks.defaultReturnValue((byte) (i + 2));
        }

        @Override
        protected int getLevel(long id) {
            return this.chunks.get(id);
        }

        @Override
        protected void setLevel(long id, int level) {
            byte b0;

            if (level > this.maxDistance) {
                b0 = this.chunks.remove(id);
            } else {
                b0 = this.chunks.put(id, (byte) level);
            }

            this.onLevelChange(id, b0, level);
        }

        protected void onLevelChange(long pos, int oldDistance, int distance) {}

        @Override
        protected int getLevelFromSource(long id) {
            return this.havePlayer(id) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long chunkPos) {
            ObjectSet<ServerPlayer> objectset = (ObjectSet) DistanceManager.this.playersPerChunk.get(chunkPos);

            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }

        private void dumpChunks(String path) {
            try {
                FileOutputStream fileoutputstream = new FileOutputStream(new File(path));

                try {
                    ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

                    while (objectiterator.hasNext()) {
                        it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry it_unimi_dsi_fastutil_longs_long2bytemap_entry = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry) objectiterator.next();
                        ChunkPos chunkcoordintpair = new ChunkPos(it_unimi_dsi_fastutil_longs_long2bytemap_entry.getLongKey());
                        String s1 = Byte.toString(it_unimi_dsi_fastutil_longs_long2bytemap_entry.getByteValue());

                        fileoutputstream.write((chunkcoordintpair.x + "\t" + chunkcoordintpair.z + "\t" + s1 + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable throwable) {
                    try {
                        fileoutputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                fileoutputstream.close();
            } catch (IOException ioexception) {
                DistanceManager.LOGGER.error("Failed to dump chunks to {}", path, ioexception);
            }

        }
    }

    /* Paper - rewrite chunk system
    private class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {

        private int viewDistance = 0;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(int i) {
            super(i);
            this.queueLevels.defaultReturnValue(i + 2);
        }

        @Override
        protected void onLevelChange(long pos, int oldDistance, int distance) {
            this.toUpdate.add(pos);
        }

        public void updateViewDistance(int watchDistance) {
            ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

            while (objectiterator.hasNext()) {
                it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry it_unimi_dsi_fastutil_longs_long2bytemap_entry = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry) objectiterator.next();
                byte b0 = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getByteValue();
                long j = it_unimi_dsi_fastutil_longs_long2bytemap_entry.getLongKey();

                this.onLevelChange(j, b0, this.haveTicketFor(b0), b0 <= watchDistance);
            }

            this.viewDistance = watchDistance;
        }

        private void onLevelChange(long pos, int distance, boolean oldWithinViewDistance, boolean withinViewDistance) {
            if (oldWithinViewDistance != withinViewDistance) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, DistanceManager.PLAYER_TICKET_LEVEL, new ChunkPos(pos));

                if (withinViewDistance) {
                    DistanceManager.this.ticketThrottlerInput.tell(ChunkTaskPriorityQueueSorter.message(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            if (this.haveTicketFor(this.getLevel(pos))) {
                                DistanceManager.this.addTicket(pos, ticket);
                                DistanceManager.this.ticketsToRelease.add(pos);
                            } else {
                                DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                                }, pos, false));
                            }

                        });
                    }, pos, () -> {
                        return distance;
                    }));
                } else {
                    DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            DistanceManager.this.removeTicket(pos, ticket);
                        });
                    }, pos, true));
                }
            }

        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.queueLevels.get(i);
                    int k = this.getLevel(i);

                    if (j != k) {
                        DistanceManager.this.ticketThrottler.onLevelChange(new ChunkPos(i), () -> {
                            return this.queueLevels.get(i);
                        }, k, (l) -> {
                            if (l >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(i);
                            } else {
                                this.queueLevels.put(i, l);
                            }

                        });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }

        }

        private boolean haveTicketFor(int distance) {
            return distance <= this.viewDistance;
        }
    }
    */ // Paper - rewrite chunk system
}
