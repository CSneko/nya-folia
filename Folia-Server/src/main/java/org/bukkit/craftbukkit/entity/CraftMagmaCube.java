package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.MagmaCube;

public class CraftMagmaCube extends CraftSlime implements MagmaCube {

    public CraftMagmaCube(CraftServer server, net.minecraft.world.entity.monster.MagmaCube entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.MagmaCube getHandleRaw() {
        return (net.minecraft.world.entity.monster.MagmaCube)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.MagmaCube getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.MagmaCube) this.entity;
    }

    @Override
    public String toString() {
        return "CraftMagmaCube";
    }
}
