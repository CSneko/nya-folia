package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Illusioner;

public class CraftIllusioner extends CraftSpellcaster implements Illusioner, com.destroystokyo.paper.entity.CraftRangedEntity<net.minecraft.world.entity.monster.Illusioner> { // Paper

    public CraftIllusioner(CraftServer server, net.minecraft.world.entity.monster.Illusioner entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Illusioner getHandleRaw() {
        return (net.minecraft.world.entity.monster.Illusioner)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Illusioner getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Illusioner) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftIllusioner";
    }
}
