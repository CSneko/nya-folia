package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public class ClientboundLevelChunkPacketData {
    private static final int TWO_MEGABYTES = 2097152;
    private final CompoundTag heightmaps;
    private final byte[] buffer;
    private final List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntitiesData;
    // Paper start
    private final java.util.List<net.minecraft.network.protocol.Packet<?>> extraPackets = new java.util.ArrayList<>();
    private static final int TE_LIMIT = Integer.getInteger("Paper.excessiveTELimit", 750);

    public List<net.minecraft.network.protocol.Packet<?>> getExtraPackets() {
        return this.extraPackets;
    }
    // Paper end

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse public ClientboundLevelChunkPacketData(LevelChunk chunk) { this(chunk, null); }
    public ClientboundLevelChunkPacketData(LevelChunk chunk, com.destroystokyo.paper.antixray.ChunkPacketInfo<net.minecraft.world.level.block.state.BlockState> chunkPacketInfo) {
        // Paper end
        this.heightmaps = new CompoundTag();

        for(Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (entry.getKey().sendToClient()) {
                this.heightmaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        this.buffer = new byte[calculateChunkSize(chunk)];

        // Paper start - Anti-Xray - Add chunk packet info
        if (chunkPacketInfo != null) {
            chunkPacketInfo.setBuffer(this.buffer);
        }

        extractChunkData(new FriendlyByteBuf(this.getWriteBuffer()), chunk, chunkPacketInfo);
        // Paper end
        this.blockEntitiesData = Lists.newArrayList();
        int totalTileEntities = 0; // Paper

        for(Map.Entry<BlockPos, BlockEntity> entry2 : chunk.getBlockEntities().entrySet()) {
            // Paper start
            if (++totalTileEntities > TE_LIMIT) {
                var packet = entry2.getValue().getUpdatePacket();
                if (packet != null) {
                    this.extraPackets.add(packet);
                    continue;
                }
            }
            // Paper end
            this.blockEntitiesData.add(ClientboundLevelChunkPacketData.BlockEntityInfo.create(entry2.getValue()));
        }

    }

    public ClientboundLevelChunkPacketData(FriendlyByteBuf buf, int x, int z) {
        this.heightmaps = buf.readNbt();
        if (this.heightmaps == null) {
            throw new RuntimeException("Can't read heightmap in packet for [" + x + ", " + z + "]");
        } else {
            int i = buf.readVarInt();
            if (i > 2097152) { // Paper - diff on change - if this changes, update PacketEncoder
                throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
            } else {
                this.buffer = new byte[i];
                buf.readBytes(this.buffer);
                this.blockEntitiesData = buf.readList(ClientboundLevelChunkPacketData.BlockEntityInfo::new);
            }
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(this.heightmaps);
        buf.writeVarInt(this.buffer.length);
        buf.writeBytes(this.buffer);
        buf.writeCollection(this.blockEntitiesData, (buf2, entry) -> {
            entry.write(buf2);
        });
    }

    private static int calculateChunkSize(LevelChunk chunk) {
        int i = 0;

        for(LevelChunkSection levelChunkSection : chunk.getSections()) {
            i += levelChunkSection.getSerializedSize();
        }

        return i;
    }

    private ByteBuf getWriteBuffer() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(this.buffer);
        byteBuf.writerIndex(0);
        return byteBuf;
    }

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse public static void extractChunkData(FriendlyByteBuf buf, LevelChunk chunk) { ClientboundLevelChunkPacketData.extractChunkData(buf, chunk, null); }
    public static void extractChunkData(FriendlyByteBuf buf, LevelChunk chunk, com.destroystokyo.paper.antixray.ChunkPacketInfo<net.minecraft.world.level.block.state.BlockState> chunkPacketInfo) {
        int chunkSectionIndex = 0;

        for(LevelChunkSection levelChunkSection : chunk.getSections()) {
            levelChunkSection.write(buf, chunkPacketInfo, chunkSectionIndex);
            chunkSectionIndex++;
            // Paper end
        }

    }

    public Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> getBlockEntitiesTagsConsumer(int x, int z) {
        return (visitor) -> {
            this.getBlockEntitiesTags(visitor, x, z);
        };
    }

    private void getBlockEntitiesTags(ClientboundLevelChunkPacketData.BlockEntityTagOutput consumer, int x, int z) {
        int i = 16 * x;
        int j = 16 * z;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for(ClientboundLevelChunkPacketData.BlockEntityInfo blockEntityInfo : this.blockEntitiesData) {
            int k = i + SectionPos.sectionRelative(blockEntityInfo.packedXZ >> 4);
            int l = j + SectionPos.sectionRelative(blockEntityInfo.packedXZ);
            mutableBlockPos.set(k, blockEntityInfo.y, l);
            consumer.accept(mutableBlockPos, blockEntityInfo.type, blockEntityInfo.tag);
        }

    }

    public FriendlyByteBuf getReadBuffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.buffer));
    }

    public CompoundTag getHeightmaps() {
        return this.heightmaps;
    }

    static class BlockEntityInfo {
        final int packedXZ;
        final int y;
        final BlockEntityType<?> type;
        @Nullable
        final CompoundTag tag;

        private BlockEntityInfo(int localXz, int y, BlockEntityType<?> type, @Nullable CompoundTag nbt) {
            this.packedXZ = localXz;
            this.y = y;
            this.type = type;
            this.tag = nbt;
        }

        private BlockEntityInfo(FriendlyByteBuf buf) {
            this.packedXZ = buf.readByte();
            this.y = buf.readShort();
            this.type = buf.readById(BuiltInRegistries.BLOCK_ENTITY_TYPE);
            this.tag = buf.readNbt();
        }

        void write(FriendlyByteBuf buf) {
            buf.writeByte(this.packedXZ);
            buf.writeShort(this.y);
            buf.writeId(BuiltInRegistries.BLOCK_ENTITY_TYPE, this.type);
            buf.writeNbt(this.tag);
        }

        static ClientboundLevelChunkPacketData.BlockEntityInfo create(BlockEntity blockEntity) {
            CompoundTag compoundTag = blockEntity.getUpdateTag();
            BlockPos blockPos = blockEntity.getBlockPos();
            int i = SectionPos.sectionRelative(blockPos.getX()) << 4 | SectionPos.sectionRelative(blockPos.getZ());
            blockEntity.sanitizeSentNbt(compoundTag); // Paper - Sanitize sent data
            return new ClientboundLevelChunkPacketData.BlockEntityInfo(i, blockPos.getY(), blockEntity.getType(), compoundTag.isEmpty() ? null : compoundTag);
        }
    }

    @FunctionalInterface
    public interface BlockEntityTagOutput {
        void accept(BlockPos pos, BlockEntityType<?> type, @Nullable CompoundTag nbt);
    }
}
