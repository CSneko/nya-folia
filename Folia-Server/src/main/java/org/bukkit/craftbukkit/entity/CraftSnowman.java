package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.SnowGolem;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Snowman;

public class CraftSnowman extends CraftGolem implements Snowman, com.destroystokyo.paper.entity.CraftRangedEntity<SnowGolem>, io.papermc.paper.entity.PaperShearable { // Paper
    public CraftSnowman(CraftServer server, SnowGolem entity) {
        super(server, entity);
    }

    @Override
    public boolean isDerp() {
        return !this.getHandle().hasPumpkin();
    }

    @Override
    public void setDerp(boolean derpMode) {
        this.getHandle().setPumpkin(!derpMode);
    }

    // Folia start - region threading
    @Override
    public SnowGolem getHandleRaw() {
        return (SnowGolem)this.entity;
    }
    // Folia end - region threading

    @Override
    public SnowGolem getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (SnowGolem) this.entity;
    }

    @Override
    public String toString() {
        return "CraftSnowman";
    }
}
