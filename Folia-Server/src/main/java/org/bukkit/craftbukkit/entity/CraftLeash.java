package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.LeashHitch;

public class CraftLeash extends CraftHanging implements LeashHitch {
    public CraftLeash(CraftServer server, LeashFenceKnotEntity entity) {
        super(server, entity);
    }

    @Override
    public boolean setFacingDirection(BlockFace face, boolean force) {
        Preconditions.checkArgument(face == BlockFace.SELF, "%s is not a valid facing direction", face);

        return force || this.getHandle().generation || this.getHandle().survives();
    }

    @Override
    public BlockFace getFacing() {
        // Leash hitch has no facing direction, so we return self
        return BlockFace.SELF;
    }

    // Folia start - region threading
    @Override
    public LeashFenceKnotEntity getHandleRaw() {
        return (LeashFenceKnotEntity)this.entity;
    }
    // Folia end - region threading

    @Override
    public LeashFenceKnotEntity getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (LeashFenceKnotEntity) this.entity;
    }

    @Override
    public String toString() {
        return "CraftLeash";
    }
}
