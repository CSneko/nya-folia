package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.PathfinderMob;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Creature;

public class CraftCreature extends CraftMob implements Creature {
    public CraftCreature(CraftServer server, PathfinderMob entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public PathfinderMob getHandleRaw() {
        return (PathfinderMob)this.entity;
    }
    // Folia end - region threading

    @Override
    public PathfinderMob getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (PathfinderMob) this.entity;
    }

    @Override
    public String toString() {
        return "CraftCreature";
    }
}
