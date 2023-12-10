package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.WaterAnimal;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.WaterMob;

public class CraftWaterMob extends CraftCreature implements WaterMob {

    public CraftWaterMob(CraftServer server, WaterAnimal entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public WaterAnimal getHandleRaw() {
        return (WaterAnimal)this.entity;
    }
    // Folia end - region threading

    @Override
    public WaterAnimal getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (WaterAnimal) this.entity;
    }

    @Override
    public String toString() {
        return "CraftWaterMob";
    }
}
