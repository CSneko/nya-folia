package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.ChestedHorse;

public abstract class CraftChestedHorse extends CraftAbstractHorse implements ChestedHorse {

    public CraftChestedHorse(CraftServer server, AbstractChestedHorse entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public AbstractChestedHorse getHandleRaw() {
        return (AbstractChestedHorse)this.entity;
    }
    // Folia end - region threading

    @Override
    public AbstractChestedHorse getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (AbstractChestedHorse) super.getHandle();
    }

    @Override
    public boolean isCarryingChest() {
        return this.getHandle().hasChest();
    }

    @Override
    public void setCarryingChest(boolean chest) {
        if (chest == this.isCarryingChest()) return;
        this.getHandle().setChest(chest);
        this.getHandle().createInventory();
    }
}
