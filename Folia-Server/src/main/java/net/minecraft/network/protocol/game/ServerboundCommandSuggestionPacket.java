package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ServerboundCommandSuggestionPacket implements Packet<ServerGamePacketListener> {
    private final int id;
    private final String command;

    public ServerboundCommandSuggestionPacket(int completionId, String partialCommand) {
        this.id = completionId;
        this.command = partialCommand;
    }

    public ServerboundCommandSuggestionPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.command = buf.readUtf(2048);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeUtf(this.command, 32500);
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleCustomCommandSuggestions(this);
    }

    public int getId() {
        return this.id;
    }

    public String getCommand() {
        return this.command;
    }
}
