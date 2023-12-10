package net.minecraft.server.network;

import com.google.common.collect.Comparators;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public class PlayerChunkSender {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final float MIN_CHUNKS_PER_TICK = 0.01F;
    public static final float MAX_CHUNKS_PER_TICK = 64.0F;
    private static final float START_CHUNKS_PER_TICK = 9.0F;
    private static final int MAX_UNACKNOWLEDGED_BATCHES = 10;
    private final LongSet pendingChunks = new LongOpenHashSet();
    private final boolean memoryConnection;
    private float desiredChunksPerTick = 9.0F;
    private float batchQuota;
    private int unacknowledgedBatches;
    private int maxUnacknowledgedBatches = 1;

    public PlayerChunkSender(boolean local) {
        this.memoryConnection = local;
    }

    public void markChunkPendingToSend(LevelChunk chunk) {
        this.pendingChunks.add(chunk.getPos().toLong());
    }

    // Paper start - rewrite player chunk loader
    public static void dropChunkStatic(ServerPlayer player, ChunkPos pos) {
        player.serverLevel().chunkSource.chunkMap.getVisibleChunkIfPresent(pos.toLong()).removePlayer(player);
        player.connection.send(new ClientboundForgetLevelChunkPacket(pos));
        // Paper start
        if (io.papermc.paper.event.packet.PlayerChunkUnloadEvent.getHandlerList().getRegisteredListeners().length > 0) {
            new io.papermc.paper.event.packet.PlayerChunkUnloadEvent(player.getBukkitEntity().getWorld().getChunkAt(pos.longKey), player.getBukkitEntity()).callEvent();
        }
        // Paper end
    }
    // Paper end - rewrite player chunk loader

    public void dropChunk(ServerPlayer player, ChunkPos pos) {
        if (!this.pendingChunks.remove(pos.toLong()) && player.isAlive()) {
           dropChunkStatic(player, pos); // Paper - rewrite player chunk loader - move into own method
        }

    }

    public void sendNextChunks(ServerPlayer player) {
        if (true) return; // Paper - rewrite player chunk loader
        if (this.unacknowledgedBatches < this.maxUnacknowledgedBatches) {
            float f = Math.max(1.0F, this.desiredChunksPerTick);
            this.batchQuota = Math.min(this.batchQuota + this.desiredChunksPerTick, f);
            if (!(this.batchQuota < 1.0F)) {
                if (!this.pendingChunks.isEmpty()) {
                    ServerLevel serverLevel = player.serverLevel();
                    ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
                    List<LevelChunk> list = this.collectChunksToSend(chunkMap, player.chunkPosition());
                    if (!list.isEmpty()) {
                        ServerGamePacketListenerImpl serverGamePacketListenerImpl = player.connection;
                        ++this.unacknowledgedBatches;
                        serverGamePacketListenerImpl.send(new ClientboundChunkBatchStartPacket());

                        for(LevelChunk levelChunk : list) {
                            sendChunk(serverGamePacketListenerImpl, serverLevel, levelChunk);
                        }

                        serverGamePacketListenerImpl.send(new ClientboundChunkBatchFinishedPacket(list.size()));
                        this.batchQuota -= (float)list.size();
                    }
                }
            }
        }
    }

    public static void sendChunk(ServerGamePacketListenerImpl handler, ServerLevel world, LevelChunk chunk) { // Paper - rewrite chunk loader - public
        handler.player.serverLevel().chunkSource.chunkMap.getVisibleChunkIfPresent(chunk.getPos().toLong()).addPlayer(handler.player);
        // Paper start - Anti-Xray
        final boolean shouldModify = world.chunkPacketBlockController.shouldModify(handler.player, chunk);
        handler.send(new ClientboundLevelChunkWithLightPacket(chunk, world.getLightEngine(), (BitSet)null, (BitSet)null, shouldModify));
        // Paper end - Anti-Xray
        // Paper start - PlayerChunkLoadEvent
        if (io.papermc.paper.event.packet.PlayerChunkLoadEvent.getHandlerList().getRegisteredListeners().length > 0) {
            new io.papermc.paper.event.packet.PlayerChunkLoadEvent(new org.bukkit.craftbukkit.CraftChunk(chunk), handler.getPlayer().getBukkitEntity()).callEvent();
        }
        // Paper end - PlayerChunkLoadEvent
        ChunkPos chunkPos = chunk.getPos();
        DebugPackets.sendPoiPacketsForChunk(world, chunkPos);
    }

    private List<LevelChunk> collectChunksToSend(ChunkMap chunkStorage, ChunkPos playerPos) {
        int i = Mth.floor(this.batchQuota);
        List<LevelChunk> list2;
        if (!this.memoryConnection && this.pendingChunks.size() > i) {
            list2 = this.pendingChunks.stream().collect(Comparators.least(i, Comparator.comparingInt(playerPos::distanceSquared))).stream().mapToLong(Long::longValue).mapToObj(chunkStorage::getChunkToSend).filter(Objects::nonNull).toList();
        } else {
            list2 = this.pendingChunks.longStream().mapToObj(chunkStorage::getChunkToSend).filter(Objects::nonNull).sorted(Comparator.comparingInt((chunk) -> {
                return playerPos.distanceSquared(chunk.getPos());
            })).toList();
        }

        for(LevelChunk levelChunk : list2) {
            this.pendingChunks.remove(levelChunk.getPos().toLong());
        }

        return list2;
    }

    public void onChunkBatchReceivedByClient(float desiredBatchSize) {
        if (true) return; // Paper - rewrite player chunk loader
        --this.unacknowledgedBatches;
        this.desiredChunksPerTick = Double.isNaN((double)desiredBatchSize) ? 0.01F : Mth.clamp(desiredBatchSize, 0.01F, 64.0F);
        if (this.unacknowledgedBatches == 0) {
            this.batchQuota = 1.0F;
        }

        this.maxUnacknowledgedBatches = 10;
    }

    public boolean isPending(long chunkPos) {
        return this.pendingChunks.contains(chunkPos);
    }
}
