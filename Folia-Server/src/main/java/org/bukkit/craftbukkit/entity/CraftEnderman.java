package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.material.MaterialData;

public class CraftEnderman extends CraftMonster implements Enderman {
    public CraftEnderman(CraftServer server, EnderMan entity) {
        super(server, entity);
    }

    @Override public boolean teleportRandomly() { return getHandle().teleport(); } // Paper
    @Override
    public MaterialData getCarriedMaterial() {
        BlockState blockData = this.getHandle().getCarriedBlock();
        return (blockData == null) ? Material.AIR.getNewData((byte) 0) : CraftMagicNumbers.getMaterial(blockData);
    }

    @Override
    public BlockData getCarriedBlock() {
        BlockState blockData = this.getHandle().getCarriedBlock();
        return (blockData == null) ? null : CraftBlockData.fromData(blockData);
    }

    @Override
    public void setCarriedMaterial(MaterialData data) {
        this.getHandle().setCarriedBlock(CraftMagicNumbers.getBlock(data));
    }

    @Override
    public void setCarriedBlock(BlockData blockData) {
        this.getHandle().setCarriedBlock(blockData == null ? null : ((CraftBlockData) blockData).getState());
    }

    // Paper start
    @Override
    public boolean isScreaming() {
        return this.getHandle().isCreepy();
    }

    @Override
    public void setScreaming(boolean screaming) {
        this.getHandle().setCreepy(screaming);
    }

    @Override
    public boolean hasBeenStaredAt() {
        return this.getHandle().hasBeenStaredAt();
    }

    @Override
    public void setHasBeenStaredAt(boolean hasBeenStaredAt) {
        this.getHandle().setHasBeenStaredAt(hasBeenStaredAt);
    }
    // Paper end

    // Folia start - region threading
    @Override
    public EnderMan getHandleRaw() {
        return (EnderMan)this.entity;
    }
    // Folia end - region threading

    @Override
    public EnderMan getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (EnderMan) this.entity;
    }

    @Override
    public String toString() {
        return "CraftEnderman";
    }

    @Override
    public boolean teleport() {
        return this.getHandle().teleport();
    }

    @Override
    public boolean teleportTowards(Entity entity) {
        Preconditions.checkArgument(entity != null, "entity cannot be null");

        return this.getHandle().teleportTowards(((CraftEntity) entity).getHandle());
    }
}
