package net.minecraft.network.protocol.game;

import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class ClientboundSectionBlocksUpdatePacket implements Packet<ClientGamePacketListener> {

    private static final int POS_IN_SECTION_BITS = 12;
    private final SectionPos sectionPos;
    private final short[] positions;
    private final BlockState[] states;

    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, ShortSet positions, LevelChunkSection section) {
        this.sectionPos = sectionPos;
        int i = positions.size();

        this.positions = new short[i];
        this.states = new BlockState[i];
        int j = 0;

        for (ShortIterator shortiterator = positions.iterator(); shortiterator.hasNext(); ++j) {
            short short0 = (Short) shortiterator.next();

            this.positions[j] = short0;
            this.states[j] = (section != null) ? section.getBlockState(SectionPos.sectionRelativeX(short0), SectionPos.sectionRelativeY(short0), SectionPos.sectionRelativeZ(short0)) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(); // CraftBukkit - SPIGOT-6076, Mojang bug when empty chunk section notified
        }

    }

    // CraftBukkit start - Add constructor
    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionposition, ShortSet shortset, BlockState[] states) {
        this.sectionPos = sectionposition;
        this.positions = shortset.toShortArray();
        this.states = states;
    }
    // CraftBukkit end

    public ClientboundSectionBlocksUpdatePacket(FriendlyByteBuf buf) {
        this.sectionPos = SectionPos.of(buf.readLong());
        int i = buf.readVarInt();

        this.positions = new short[i];
        this.states = new BlockState[i];

        for (int j = 0; j < i; ++j) {
            long k = buf.readVarLong();

            this.positions[j] = (short) ((int) (k & 4095L));
            this.states[j] = (BlockState) Block.BLOCK_STATE_REGISTRY.byId((int) (k >>> 12));
        }

    }

    // Paper start
    public ClientboundSectionBlocksUpdatePacket(SectionPos sectionPos, it.unimi.dsi.fastutil.shorts.Short2ObjectMap<BlockState> blockChanges) {
        this.sectionPos = sectionPos;
        this.positions = blockChanges.keySet().toShortArray();
        this.states = blockChanges.values().toArray(new BlockState[0]);
    }
    // Paper end

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(this.sectionPos.asLong());
        buf.writeVarInt(this.positions.length);

        for (int i = 0; i < this.positions.length; ++i) {
            buf.writeVarLong((long) Block.getId(this.states[i]) << 12 | (long) this.positions[i]);
        }

    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleChunkBlocksUpdate(this);
    }

    public void runUpdates(BiConsumer<BlockPos, BlockState> visitor) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

        for (int i = 0; i < this.positions.length; ++i) {
            short short0 = this.positions[i];

            blockposition_mutableblockposition.set(this.sectionPos.relativeToBlockX(short0), this.sectionPos.relativeToBlockY(short0), this.sectionPos.relativeToBlockZ(short0));
            visitor.accept(blockposition_mutableblockposition, this.states[i]);
        }

    }
}
