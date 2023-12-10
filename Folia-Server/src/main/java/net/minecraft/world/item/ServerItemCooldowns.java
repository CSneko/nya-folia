package net.minecraft.world.item;

import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.server.level.ServerPlayer;

public class ServerItemCooldowns extends ItemCooldowns {
    private final ServerPlayer player;

    public ServerItemCooldowns(ServerPlayer player) {
        this.player = player;
    }

    // Paper start
    @Override
    public void addCooldown(Item item, int duration) {
        io.papermc.paper.event.player.PlayerItemCooldownEvent event = new io.papermc.paper.event.player.PlayerItemCooldownEvent(this.player.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(item), duration);
        if (event.callEvent()) {
            super.addCooldown(item, event.getCooldown());
        }
    }
    // Paper end

    @Override
    protected void onCooldownStarted(Item item, int duration) {
        super.onCooldownStarted(item, duration);
        this.player.connection.send(new ClientboundCooldownPacket(item, duration));
    }

    @Override
    protected void onCooldownEnded(Item item) {
        super.onCooldownEnded(item);
        this.player.connection.send(new ClientboundCooldownPacket(item, 0));
    }
}
