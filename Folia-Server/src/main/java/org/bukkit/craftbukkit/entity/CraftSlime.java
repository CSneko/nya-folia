package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Slime;

public class CraftSlime extends CraftMob implements Slime, CraftEnemy {

    public CraftSlime(CraftServer server, net.minecraft.world.entity.monster.Slime entity) {
        super(server, entity);
    }

    @Override
    public int getSize() {
        return this.getHandle().getSize();
    }

    @Override
    public void setSize(int size) {
        this.getHandle().setSize(size, /* true */ getHandle().isAlive()); // Paper - fix dead slime setSize invincibility
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Slime getHandleRaw() {
        return (net.minecraft.world.entity.monster.Slime)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Slime getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Slime) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSlime";
    }

    // Paper start
    @Override
    public boolean canWander() {
        return getHandle().canWander();
    }

    @Override
    public void setWander(boolean canWander) {
        getHandle().setWander(canWander);
    }
    // Paper end
}
