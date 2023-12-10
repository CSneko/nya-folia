package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    private final int x;
    private final int z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;
    // Paper start - Async-Anti-Xray - Ready flag for the connection
    private volatile boolean ready;

    @Override
    public boolean isReady() {
        return this.ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
    // Paper end

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine lightProvider, @Nullable BitSet skyBits, @Nullable BitSet blockBits) { this(chunk, lightProvider, skyBits, blockBits, true); }
    public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine lightProvider, @Nullable BitSet skyBits, @Nullable BitSet blockBits, boolean modifyBlocks) {
        ChunkPos chunkPos = chunk.getPos();
        this.x = chunkPos.x;
        this.z = chunkPos.z;
        com.destroystokyo.paper.antixray.ChunkPacketInfo<net.minecraft.world.level.block.state.BlockState> chunkPacketInfo = modifyBlocks ? chunk.getLevel().chunkPacketBlockController.getChunkPacketInfo(this, chunk) : null;
        this.chunkData = new ClientboundLevelChunkPacketData(chunk, chunkPacketInfo);
        // Paper end
        this.lightData = new ClientboundLightUpdatePacketData(chunkPos, lightProvider, skyBits, blockBits);
        chunk.getLevel().chunkPacketBlockController.modifyBlocks(this, chunkPacketInfo); // Paper - Anti-Xray - Modify blocks
    }

    public ClientboundLevelChunkWithLightPacket(FriendlyByteBuf buf) {
        this.x = buf.readInt();
        this.z = buf.readInt();
        this.chunkData = new ClientboundLevelChunkPacketData(buf, this.x, this.z);
        this.lightData = new ClientboundLightUpdatePacketData(buf, this.x, this.z);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.z);
        this.chunkData.write(buf);
        this.lightData.write(buf);
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleLevelChunkWithLight(this);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public ClientboundLevelChunkPacketData getChunkData() {
        return this.chunkData;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }

    // Paper start - handle over-sized TE packets
    @Override
    public java.util.List<Packet<?>> getExtraPackets() {
        return this.chunkData.getExtraPackets();
    }
    // Paper end
}
