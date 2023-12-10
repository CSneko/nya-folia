package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Vindicator;

public class CraftVindicator extends CraftIllager implements Vindicator {

    public CraftVindicator(CraftServer server, net.minecraft.world.entity.monster.Vindicator entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Vindicator getHandleRaw() {
        return (net.minecraft.world.entity.monster.Vindicator)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Vindicator getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Vindicator) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftVindicator";
    }

    @Override
    public boolean isJohnny() {
        return this.getHandle().isJohnny;
    }

    @Override
    public void setJohnny(boolean johnny) {
        this.getHandle().isJohnny = johnny;
    }
}
