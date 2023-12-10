package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.AbstractFish;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Fish;

public class CraftFish extends CraftWaterMob implements Fish, io.papermc.paper.entity.PaperBucketable { // Paper - Bucketable API

    public CraftFish(CraftServer server, AbstractFish entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public AbstractFish getHandleRaw() {
        return (AbstractFish)this.entity;
    }
    // Folia end - region threading

    @Override
    public AbstractFish getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (AbstractFish) this.entity;
    }

    @Override
    public String toString() {
        return "CraftFish";
    }
}
