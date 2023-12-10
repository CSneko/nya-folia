package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Marker;

public class CraftMarker extends CraftEntity implements Marker {

    public CraftMarker(CraftServer server, net.minecraft.world.entity.Marker entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.Marker getHandleRaw() {
        return (net.minecraft.world.entity.Marker)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.Marker getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.Marker) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftMarker";
    }
}
