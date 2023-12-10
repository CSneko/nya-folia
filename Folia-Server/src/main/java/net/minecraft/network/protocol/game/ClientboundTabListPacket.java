package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public class ClientboundTabListPacket implements Packet<ClientGamePacketListener> {
    public final Component header;
    public final Component footer;
    // Paper start
    public net.kyori.adventure.text.Component adventure$header;
    public net.kyori.adventure.text.Component adventure$footer;
    // Paper end

    public ClientboundTabListPacket(Component header, Component footer) {
        this.header = header;
        this.footer = footer;
    }

    public ClientboundTabListPacket(FriendlyByteBuf buf) {
        this.header = buf.readComponent();
        this.footer = buf.readComponent();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // Paper start
        if (this.adventure$header != null && this.adventure$footer != null) {
            buf.writeComponent(this.adventure$header);
            buf.writeComponent(this.adventure$footer);
            return;
        }
        // Paper end
        buf.writeComponent(this.header);
        buf.writeComponent(this.footer);
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleTabListCustomisation(this);
    }

    public Component getHeader() {
        return this.header;
    }

    public Component getFooter() {
        return this.footer;
    }
}
