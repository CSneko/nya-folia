package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.AbstractGolem;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Golem;

public class CraftGolem extends CraftCreature implements Golem {
    public CraftGolem(CraftServer server, AbstractGolem entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public AbstractGolem getHandleRaw() {
        return (AbstractGolem)this.entity;
    }
    // Folia end - region threading

    @Override
    public AbstractGolem getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (AbstractGolem) this.entity;
    }

    @Override
    public String toString() {
        return "CraftGolem";
    }
}
