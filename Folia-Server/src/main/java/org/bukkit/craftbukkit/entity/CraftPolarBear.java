package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.PolarBear;

public class CraftPolarBear extends CraftAnimals implements PolarBear {

    public CraftPolarBear(CraftServer server, net.minecraft.world.entity.animal.PolarBear entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.PolarBear getHandleRaw() {
        return (net.minecraft.world.entity.animal.PolarBear)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.PolarBear getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.PolarBear) this.entity;
    }

    @Override
    public String toString() {
        return "CraftPolarBear";
    }

    // Paper start
    @Override
    public boolean isStanding() {
        return this.getHandle().isStanding();
    }

    @Override
    public void setStanding(boolean standing) {
        this.getHandle().setStanding(standing);
    }
    // Paper end
}
