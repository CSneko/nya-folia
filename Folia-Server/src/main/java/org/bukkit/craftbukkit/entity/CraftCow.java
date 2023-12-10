package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Cow;

public class CraftCow extends CraftAnimals implements Cow {

    public CraftCow(CraftServer server, net.minecraft.world.entity.animal.Cow entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.Cow getHandleRaw() {
        return (net.minecraft.world.entity.animal.Cow)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.Cow getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.Cow) this.entity;
    }

    @Override
    public String toString() {
        return "CraftCow";
    }
}
