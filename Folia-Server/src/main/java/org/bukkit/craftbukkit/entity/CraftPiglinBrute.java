package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.PiglinBrute;

public class CraftPiglinBrute extends CraftPiglinAbstract implements PiglinBrute {

    public CraftPiglinBrute(CraftServer server, net.minecraft.world.entity.monster.piglin.PiglinBrute entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.piglin.PiglinBrute getHandleRaw() {
        return (net.minecraft.world.entity.monster.piglin.PiglinBrute)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.piglin.PiglinBrute getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.piglin.PiglinBrute) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftPiglinBrute";
    }
}
