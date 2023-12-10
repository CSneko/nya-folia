package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Bat;

public class CraftBat extends CraftAmbient implements Bat {
    public CraftBat(CraftServer server, net.minecraft.world.entity.ambient.Bat entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.ambient.Bat getHandleRaw() {
        return (net.minecraft.world.entity.ambient.Bat)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.ambient.Bat getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.ambient.Bat) this.entity;
    }

    @Override
    public String toString() {
        return "CraftBat";
    }

    @Override
    public boolean isAwake() {
        return !this.getHandle().isResting();
    }

    @Override
    public void setAwake(boolean state) {
        this.getHandle().setResting(!state);
    }
    // Paper start
    @Override
    public org.bukkit.Location getTargetLocation() {
        net.minecraft.core.BlockPos pos = this.getHandle().targetPosition;
        if (pos == null) {
            return null;
        }

        return io.papermc.paper.util.MCUtil.toLocation(this.getHandle().level(), pos);
    }

    @Override
    public void setTargetLocation(org.bukkit.Location location) {
        net.minecraft.core.BlockPos pos = null;
        if (location != null) {
            pos = io.papermc.paper.util.MCUtil.toBlockPosition(location);
        }

        this.getHandle().targetPosition = pos;
    }
    // Paper end
}
