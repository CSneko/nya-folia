package net.minecraft.server.level;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.util.DebugBuffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
// CraftBukkit end

public class ChunkHolder {

    public static final Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> UNLOADED_CHUNK = Either.right(ChunkHolder.ChunkLoadingFailure.UNLOADED);
    public static final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_CHUNK);
    public static final Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> UNLOADED_LEVEL_CHUNK = Either.right(ChunkHolder.ChunkLoadingFailure.UNLOADED);
    private static final Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> NOT_DONE_YET = Either.right(ChunkHolder.ChunkLoadingFailure.UNLOADED);
    private static final CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    // Paper - rewrite chunk system
    private final LevelHeightAccessor levelHeightAccessor;
    // Paper - rewrite chunk system
    @Nullable
    private final DebugBuffer<ChunkHolder.ChunkSaveDebug> chunkToSaveHistory;
    // Paper - rewrite chunk system
    public final ChunkPos pos;
    private boolean hasChangedSections;
    private final ShortSet[] changedBlocksPerSection;
    private final BitSet blockChangedLightSectionFilter;
    private final BitSet skyChangedLightSectionFilter;
    private final LevelLightEngine lightEngine;
    private final ChunkHolder.LevelChangeListener onLevelChange;
    public final ChunkHolder.PlayerProvider playerProvider;
    // Paper - rewrite chunk system

    private final ChunkMap chunkMap; // Paper
    // Paper start - no-tick view distance
    public final LevelChunk getSendingChunk() {
        // it's important that we use getChunkAtIfLoadedImmediately to mirror the chunk sending logic used
        // in Chunk's neighbour callback
        LevelChunk ret = this.chunkMap.level.getChunkSource().getChunkAtIfLoadedImmediately(this.pos.x, this.pos.z);
        if (ret != null && ret.areNeighboursLoaded(1)) {
            return ret;
        }
        return null;
    }
    // Paper end - no-tick view distance

    // Paper start
    public void onChunkAdd() {
        // Paper start - optimise chunk tick iteration
        if (this.needsBroadcastChanges()) {
            this.chunkMap.level.needsChangeBroadcasting.add(this); // Folia - region threading
        }
        // Paper end - optimise chunk tick iteration
    }

    public void onChunkRemove() {
        // Paper start - optimise chunk tick iteration
        if (this.needsBroadcastChanges()) {
            this.chunkMap.level.needsChangeBroadcasting.remove(this); // Folia - region threading
        }
        // Paper end - optimise chunk tick iteration
    }
    // Paper end

    public final io.papermc.paper.chunk.system.scheduling.NewChunkHolder newChunkHolder; // Paper - rewrite chunk system

    // Paper start - replace player chunk loader
    private final com.destroystokyo.paper.util.maplist.ReferenceList<ServerPlayer> playersSentChunkTo = new com.destroystokyo.paper.util.maplist.ReferenceList<>();

    public void addPlayer(ServerPlayer player) {
        if (!this.playersSentChunkTo.add(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + this.chunkMap.level.getWorld().getName() + "' to player " + player);
        }
    }

    public void removePlayer(ServerPlayer player) {
        if (!this.playersSentChunkTo.remove(player)) {
            throw new IllegalStateException("Have not sent chunk " + this.pos + " in world '" + this.chunkMap.level.getWorld().getName() + "' to player " + player);
        }
    }

    public boolean hasChunkBeenSent() {
        return this.playersSentChunkTo.size() != 0;
    }

    public boolean hasBeenSent(ServerPlayer to) {
        return this.playersSentChunkTo.contains(to);
    }
    // Paper end - replace player chunk loader
    public ChunkHolder(ChunkPos pos, LevelHeightAccessor world, LevelLightEngine lightingProvider, ChunkHolder.PlayerProvider playersWatchingChunkProvider, io.papermc.paper.chunk.system.scheduling.NewChunkHolder newChunkHolder) { // Paper - rewrite chunk system
        this.newChunkHolder = newChunkHolder; // Paper - rewrite chunk system
        this.chunkToSaveHistory = null;
        this.blockChangedLightSectionFilter = new BitSet();
        this.skyChangedLightSectionFilter = new BitSet();
        // Paper - rewrite chunk system
        this.pos = pos;
        this.levelHeightAccessor = world;
        this.lightEngine = lightingProvider;
        this.onLevelChange = null; // Paper - rewrite chunk system
        this.playerProvider = playersWatchingChunkProvider;
        // Paper - rewrite chunk system
        this.changedBlocksPerSection = new ShortSet[world.getSectionsCount()];
        this.chunkMap = (ChunkMap)playersWatchingChunkProvider; // Paper
    }

    // Paper start
    public @Nullable ChunkAccess getAvailableChunkNow() {
        return this.newChunkHolder.getCurrentChunk(); // Paper - rewrite chunk system
    }
    // Paper end
    // CraftBukkit start
    public LevelChunk getFullChunkNow() {
        // Paper start - rewrite chunk system
        ChunkAccess chunk = this.getAvailableChunkNow();
        if (!this.isFullChunkReady() || !(chunk instanceof LevelChunk)) return null; // instanceof to avoid a race condition on off-main threads
        return (LevelChunk)chunk;
        // Paper end - rewrite chunk system
    }

    public LevelChunk getFullChunkNowUnchecked() {
        // Paper start - rewrite chunk system
        ChunkAccess chunk = this.getAvailableChunkNow();
        return chunk instanceof LevelChunk ? (LevelChunk)chunk : null;
        // Paper end - rewrite chunk system
    }
    // CraftBukkit end

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getFutureIfPresentUnchecked(ChunkStatus leastStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getFutureIfPresent(ChunkStatus leastStatus) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public final CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> getTickingChunkFuture() { // Paper - final for inline
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public final CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> getEntityTickingChunkFuture() { // Paper - final for inline
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public final CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> getFullChunkFuture() { // Paper - final for inline
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    public final LevelChunk getTickingChunk() { // Paper - final for inline
        // Paper start - rewrite chunk system
        if (!this.isTickingReady()) {
            return null;
        }
        return (LevelChunk)this.getAvailableChunkNow();
        // Paper end - rewrite chunk system
    }

    public CompletableFuture<?> getChunkSendSyncFuture() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @Nullable
    public LevelChunk getChunkToSend() {
        return this.getSendingChunk(); // Paper - rewrite chunk system
    }

    @Nullable
    public final LevelChunk getFullChunk() { // Paper - final for inline
        // Paper start - rewrite chunk system
        if (!this.isFullChunkReady()) {
            return null;
        }
        return (LevelChunk)this.getAvailableChunkNow();
        // Paper end - rewrite chunk system
    }

    @Nullable
    public ChunkStatus getLastAvailableStatus() {
        return this.newChunkHolder.getCurrentGenStatus(); // Paper - rewrite chunk system
    }

    // Paper start
    public ChunkStatus getChunkHolderStatus() {
        return this.newChunkHolder.getCurrentGenStatus(); // Paper - rewrite chunk system
    }
    // Paper end

    @Nullable
    public ChunkAccess getLastAvailable() {
        return this.newChunkHolder.getCurrentChunk(); // Paper - rewrite chunk system
    }

    // Paper - rewrite chunk system

    public void blockChanged(BlockPos pos) {
        // Paper start - replace player chunk loader
        if (this.playersSentChunkTo.size() == 0) {
            return;
        }
        // Paper end - replace player chunk loader
        LevelChunk chunk = this.getSendingChunk(); // Paper - no-tick view distance

        if (chunk != null) {
            int i = this.levelHeightAccessor.getSectionIndex(pos.getY());

            if (i < 0 || i >= this.changedBlocksPerSection.length) return; // CraftBukkit - SPIGOT-6086, SPIGOT-6296
            if (this.changedBlocksPerSection[i] == null) {
                this.hasChangedSections = true; this.addToBroadcastMap(); // Paper - optimise chunk tick iteration
                this.changedBlocksPerSection[i] = new ShortOpenHashSet();
            }

            this.changedBlocksPerSection[i].add(SectionPos.sectionRelativePos(pos));
        }
    }

    public void sectionLightChanged(LightLayer lightType, int y) {
        // Paper start - no-tick view distance

        if (true) {
            ChunkAccess ichunkaccess = this.getAvailableChunkNow();

            if (ichunkaccess != null) {
                ichunkaccess.setUnsaved(true);
                LevelChunk chunk = this.getSendingChunk();
                // Paper end - no-tick view distance

                if (this.playersSentChunkTo.size() != 0 && chunk != null) { // Paper - replace player chunk loader
                    int j = this.lightEngine.getMinLightSection();
                    int k = this.lightEngine.getMaxLightSection();

                    if (y >= j && y <= k) {
                        this.addToBroadcastMap(); // Paper - optimise chunk tick iteration
                        int l = y - j;

                        if (lightType == LightLayer.SKY) {
                            this.skyChangedLightSectionFilter.set(l);
                        } else {
                            this.blockChangedLightSectionFilter.set(l);
                        }

                    }
                }
            }
        }
    }

    // Paper start - optimise chunk tick iteration
    public final boolean needsBroadcastChanges() {
        return this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty();
    }

    private void addToBroadcastMap() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.chunkMap.level, this.pos, "Asynchronous ChunkHolder update is not allowed");
        this.chunkMap.level.needsChangeBroadcasting.add(this); // Folia - region threading
    }
    // Paper end - optimise chunk tick iteration

    public void broadcastChanges(LevelChunk chunk) {
        if (this.needsBroadcastChanges()) { // Paper - moved into above, other logic needs to call
            Level world = chunk.getLevel();
            List list;

            if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
                list = this.getPlayers(true); // Paper - rewrite chunk system
                if (!list.isEmpty()) {
                    ClientboundLightUpdatePacket packetplayoutlightupdate = new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter);

                    this.broadcast(list, packetplayoutlightupdate);
                }

                this.skyChangedLightSectionFilter.clear();
                this.blockChangedLightSectionFilter.clear();
            }

            if (this.hasChangedSections) {
                list = this.getPlayers(false); // Paper - rewrite chunk system

                for (int i = 0; i < this.changedBlocksPerSection.length; ++i) {
                    ShortSet shortset = this.changedBlocksPerSection[i];

                    if (shortset != null) {
                        this.changedBlocksPerSection[i] = null;
                        if (!list.isEmpty()) {
                            int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                            SectionPos sectionposition = SectionPos.of(chunk.getPos(), j);

                            if (shortset.size() == 1) {
                                BlockPos blockposition = sectionposition.relativeToBlockPos(shortset.iterator().nextShort());
                                BlockState iblockdata = world.getBlockState(blockposition);

                                this.broadcast(list, new ClientboundBlockUpdatePacket(blockposition, iblockdata));
                                this.broadcastBlockEntityIfNeeded(list, world, blockposition, iblockdata);
                            } else {
                                LevelChunkSection chunksection = chunk.getSection(i);
                                ClientboundSectionBlocksUpdatePacket packetplayoutmultiblockchange = new ClientboundSectionBlocksUpdatePacket(sectionposition, shortset, chunksection);

                                this.broadcast(list, packetplayoutmultiblockchange);
                                // CraftBukkit start
                                List finalList = list;
                                packetplayoutmultiblockchange.runUpdates((blockposition1, iblockdata1) -> {
                                    this.broadcastBlockEntityIfNeeded(finalList, world, blockposition1, iblockdata1);
                                    // CraftBukkit end
                                });
                            }
                        }
                    }
                }

                this.hasChangedSections = false;
            }
        }
    }

    private void broadcastBlockEntityIfNeeded(List<ServerPlayer> players, Level world, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) {
            this.broadcastBlockEntity(players, world, pos);
        }

    }

    private void broadcastBlockEntity(List<ServerPlayer> players, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity != null) {
            Packet<?> packet = tileentity.getUpdatePacket();

            if (packet != null) {
                this.broadcast(players, packet);
            }
        }

    }

    // Paper start - rewrite chunk system
    public List<ServerPlayer> getPlayers(boolean onlyOnWatchDistanceEdge){
        List<ServerPlayer> ret = new java.util.ArrayList<>();
        for (int i = 0, len = this.playersSentChunkTo.size(); i < len; ++i) {
            ServerPlayer player = this.playersSentChunkTo.getUnchecked(i);
            if (onlyOnWatchDistanceEdge && !this.chunkMap.level.playerChunkLoader.isChunkSent(player, this.pos.x, this.pos.z, onlyOnWatchDistanceEdge)) {
                continue;
            }
            ret.add(player);
        }

        return ret;
    }

    public void broadcast(Packet<?> packet, boolean onlyOnWatchDistanceEdge) {
        this.broadcast(this.getPlayers(onlyOnWatchDistanceEdge), packet);
    }
    // Paper end - rewrite chunk system

    private void broadcast(List<ServerPlayer> players, Packet<?> packet) {
        players.forEach((entityplayer) -> {
            entityplayer.connection.send(packet);
        });
    }

    // Paper - rewrite chunk system

    public FullChunkStatus getFullStatus() {
        return this.newChunkHolder.getChunkStatus(); // Paper - rewrite chunk system) {
    }

    public final ChunkPos getPos() { // Paper - final for inline
        return this.pos;
    }

    public final int getTicketLevel() { // Paper - final for inline
        return this.newChunkHolder.getTicketLevel(); // Paper - rewrite chunk system
    }

    // Paper - rewrite chunk system

    public void replaceProtoChunk(ImposterProtoChunk chunk) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public List<Pair<ChunkStatus, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>> getAllFutures() {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    @FunctionalInterface
    public interface LevelChangeListener {

        void onLevelChange(ChunkPos pos, IntSupplier levelGetter, int targetLevel, IntConsumer levelSetter);
    }

    public interface PlayerProvider {

        List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge);
    }

    private static final class ChunkSaveDebug {

        private final Thread thread;
        private final CompletableFuture<?> future;
        private final String source;

        ChunkSaveDebug(Thread thread, CompletableFuture<?> action, String actionDesc) {
            this.thread = thread;
            this.future = action;
            this.source = actionDesc;
        }
    }

    public interface ChunkLoadingFailure {

        ChunkHolder.ChunkLoadingFailure UNLOADED = new ChunkHolder.ChunkLoadingFailure() {
            public String toString() {
                return "UNLOADED";
            }
        };
    }

    // Paper start
    public final boolean isEntityTickingReady() {
        return this.newChunkHolder.isEntityTickingReady(); // Paper - rewrite chunk system
    }

    public final boolean isTickingReady() {
        return this.newChunkHolder.isTickingReady(); // Paper - rewrite chunk system
    }

    public final boolean isFullChunkReady() {
        return this.newChunkHolder.isFullChunkReady(); // Paper - rewrite chunk system
    }
    // Paper end
}
