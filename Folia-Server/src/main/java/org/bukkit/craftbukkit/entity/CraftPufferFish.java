package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.Pufferfish;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.PufferFish;

public class CraftPufferFish extends CraftFish implements PufferFish {

    public CraftPufferFish(CraftServer server, Pufferfish entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public Pufferfish getHandleRaw() {
        return (Pufferfish)this.entity;
    }
    // Folia end - region threading

    @Override
    public Pufferfish getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (Pufferfish) super.getHandle();
    }

    @Override
    public int getPuffState() {
        return this.getHandle().getPuffState();
    }

    @Override
    public void setPuffState(int state) {
        this.getHandle().setPuffState(state);
    }

    @Override
    public String toString() {
        return "CraftPufferFish";
    }
}
