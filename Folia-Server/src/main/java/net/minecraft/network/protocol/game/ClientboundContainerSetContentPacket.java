package net.minecraft.network.protocol.game;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.ItemStack;

public class ClientboundContainerSetContentPacket implements Packet<ClientGamePacketListener> {
    private final int containerId;
    private final int stateId;
    private final List<ItemStack> items;
    private final ItemStack carriedItem;

    public ClientboundContainerSetContentPacket(int syncId, int revision, NonNullList<ItemStack> contents, ItemStack cursorStack) {
        this.containerId = syncId;
        this.stateId = revision;
        this.items = NonNullList.withSize(contents.size(), ItemStack.EMPTY);

        for(int i = 0; i < contents.size(); ++i) {
            this.items.set(i, contents.get(i).copy());
        }

        this.carriedItem = cursorStack.copy();
    }

    public ClientboundContainerSetContentPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readUnsignedByte();
        this.stateId = buf.readVarInt();
        this.items = buf.readCollection(NonNullList::createWithCapacity, FriendlyByteBuf::readItem);
        this.carriedItem = buf.readItem();
    }

    // Paper start
    @Override
    public boolean packetTooLarge(net.minecraft.network.Connection manager) {
        for (int i = 0 ; i < this.items.size() ; i++) {
            manager.send(new ClientboundContainerSetSlotPacket(this.containerId, this.stateId, i, this.items.get(i)));
        }
        return true;
    }
    // Paper end

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeVarInt(this.stateId);
        buf.writeCollection(this.items, FriendlyByteBuf::writeItem);
        buf.writeItem(this.carriedItem);
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleContainerContent(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public List<ItemStack> getItems() {
        return this.items;
    }

    public ItemStack getCarriedItem() {
        return this.carriedItem;
    }

    public int getStateId() {
        return this.stateId;
    }
}
