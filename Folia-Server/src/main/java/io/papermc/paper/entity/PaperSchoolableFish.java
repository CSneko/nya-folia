package io.papermc.paper.entity;

import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftFish;
import org.jetbrains.annotations.NotNull;

public class PaperSchoolableFish extends CraftFish implements SchoolableFish {

    public PaperSchoolableFish(CraftServer server, AbstractSchoolingFish entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public AbstractSchoolingFish getHandleRaw() {
        return (AbstractSchoolingFish)this.entity;
    }
    // Folia end - region threading

    @Override
    public AbstractSchoolingFish getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (AbstractSchoolingFish) super.getHandle();
    }

    @Override
    public void startFollowing(@NotNull SchoolableFish fish) {
        if (this.getHandle().isFollower()) { // If following a fish already, properly remove the old one
            this.stopFollowing();
        }

        this.getHandle().startFollowing(((PaperSchoolableFish) fish).getHandle());
    }

    @Override
    public void stopFollowing() {
        this.getHandle().stopFollowing();
    }

    @Override
    public int getSchoolSize() {
        return this.getHandle().schoolSize;
    }

    @Override
    public int getMaxSchoolSize() {
        return this.getHandle().getMaxSchoolSize();
    }

    @Override
    public SchoolableFish getSchoolLeader() {
        AbstractSchoolingFish leader = this.getHandle().leader;
        if (leader == null) {
            return null;
        }

        return (SchoolableFish) leader.getBukkitEntity();
    }
}
