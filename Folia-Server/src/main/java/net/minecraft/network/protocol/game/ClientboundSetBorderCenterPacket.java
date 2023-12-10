package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.border.WorldBorder;

public class ClientboundSetBorderCenterPacket implements Packet<ClientGamePacketListener> {

    private final double newCenterX;
    private final double newCenterZ;

    public ClientboundSetBorderCenterPacket(WorldBorder worldBorder) {
        // CraftBukkit start - multiply out nether border
        this.newCenterX = worldBorder.getCenterX() * (worldBorder.world != null ? worldBorder.world.dimensionType().coordinateScale() : 1.0);
        this.newCenterZ = worldBorder.getCenterZ() * (worldBorder.world != null ? worldBorder.world.dimensionType().coordinateScale() : 1.0);
        // CraftBukkit end
    }

    public ClientboundSetBorderCenterPacket(FriendlyByteBuf buf) {
        this.newCenterX = buf.readDouble();
        this.newCenterZ = buf.readDouble();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(this.newCenterX);
        buf.writeDouble(this.newCenterZ);
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleSetBorderCenter(this);
    }

    public double getNewCenterZ() {
        return this.newCenterZ;
    }

    public double getNewCenterX() {
        return this.newCenterX;
    }
}
