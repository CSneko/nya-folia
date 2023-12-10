package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.SpectralArrow;

public class CraftSpectralArrow extends CraftArrow implements SpectralArrow {

    public CraftSpectralArrow(CraftServer server, net.minecraft.world.entity.projectile.SpectralArrow entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.SpectralArrow getHandleRaw() {
        return (net.minecraft.world.entity.projectile.SpectralArrow)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.SpectralArrow getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.SpectralArrow) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSpectralArrow";
    }

    @Override
    public int getGlowingTicks() {
        return this.getHandle().duration;
    }

    @Override
    public void setGlowingTicks(int duration) {
        this.getHandle().duration = duration;
    }
}
