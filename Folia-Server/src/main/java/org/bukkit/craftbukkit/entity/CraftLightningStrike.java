package org.bukkit.craftbukkit.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;

public class CraftLightningStrike extends CraftEntity implements LightningStrike {
    public CraftLightningStrike(final CraftServer server, final LightningBolt entity) {
        super(server, entity);
    }

    @Override
    public boolean isEffect() {
        return this.getHandle().isEffect; // Paper
    }

    public int getFlashes() {
        return this.getHandle().flashes;
    }

    public void setFlashes(int flashes) {
        this.getHandle().flashes = flashes;
    }

    public int getLifeTicks() {
        return this.getHandle().life;
    }

    public void setLifeTicks(int ticks) {
        this.getHandle().life = ticks;
    }

    public Player getCausingPlayer() {
        ServerPlayer player = this.getHandle().getCause();
        return (player != null) ? player.getBukkitEntity() : null;
    }

    public void setCausingPlayer(Player player) {
        this.getHandle().setCause((player != null) ? ((CraftPlayer) player).getHandle() : null);
    }

    // Folia start - region threading
    @Override
    public LightningBolt getHandleRaw() {
        return (LightningBolt)this.entity;
    }
    // Folia end - region threading

    @Override
    public LightningBolt getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (LightningBolt) this.entity;
    }

    @Override
    public String toString() {
        return "CraftLightningStrike";
    }

    // Spigot start
    private final LightningStrike.Spigot spigot = new LightningStrike.Spigot() {

        @Override
        public boolean isSilent()
        {
            return false; // Paper - the concept of isSilent doesn't exist as sound is controlled by the client
        }
    };

    @Override
    public LightningStrike.Spigot spigot() {
        return this.spigot;
    }
    // Spigot end

    // Paper start
    @Override
    public int getFlashCount() {
        return getHandle().flashes;
    }

    @Override
    public void setFlashCount(int flashes) {
        com.google.common.base.Preconditions.checkArgument(flashes >= 0, "Flashes has to be a positive number!");
        getHandle().flashes = flashes;
    }

    @Override
    public @org.jetbrains.annotations.Nullable org.bukkit.entity.Entity getCausingEntity() {
        final var cause = this.getHandle().getCause();
        return cause == null ? null : cause.getBukkitEntity();
    }
    // Paper end
}
