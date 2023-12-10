package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.ambient.AmbientCreature;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Ambient;

public class CraftAmbient extends CraftMob implements Ambient {
    public CraftAmbient(CraftServer server, AmbientCreature entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public AmbientCreature getHandleRaw() {
        return (AmbientCreature)this.entity;
    }
    // Folia end - region threading

    @Override
    public AmbientCreature getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (AmbientCreature) this.entity;
    }

    @Override
    public String toString() {
        return "CraftAmbient";
    }
}
