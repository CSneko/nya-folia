// mc-dev import
package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public class ServerboundUseItemOnPacket implements Packet<ServerGamePacketListener> {

    private final BlockHitResult blockHit;
    private final InteractionHand hand;
    private final int sequence;
    public long timestamp; // Spigot

    public ServerboundUseItemOnPacket(InteractionHand hand, BlockHitResult blockHitResult, int sequence) {
        this.hand = hand;
        this.blockHit = blockHitResult;
        this.sequence = sequence;
    }

    public ServerboundUseItemOnPacket(FriendlyByteBuf buf) {
        this.timestamp = System.currentTimeMillis(); // Spigot
        this.hand = (InteractionHand) buf.readEnum(InteractionHand.class);
        this.blockHit = buf.readBlockHitResult();
        this.sequence = buf.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeBlockHitResult(this.blockHit);
        buf.writeVarInt(this.sequence);
    }

    public void handle(ServerGamePacketListener listener) {
        listener.handleUseItemOn(this);
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public BlockHitResult getHitResult() {
        return this.blockHit;
    }

    public int getSequence() {
        return this.sequence;
    }
}
