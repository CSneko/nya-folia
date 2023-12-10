package net.minecraft.world.inventory;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class PlayerEnderChestContainer extends SimpleContainer {

    @Nullable
    private EnderChestBlockEntity activeChest;
    // CraftBukkit start
    private final Player owner;

    public InventoryHolder getBukkitOwner() {
        return this.owner.getBukkitEntity();
    }

    @Override
    public Location getLocation() {
        return this.activeChest != null ? CraftLocation.toBukkit(this.activeChest.getBlockPos(), this.activeChest.getLevel().getWorld()) : null;
    }

    public PlayerEnderChestContainer(Player owner) {
        super(27);
        this.owner = owner;
        // CraftBukkit end
    }

    public void setActiveChest(EnderChestBlockEntity blockEntity) {
        this.activeChest = blockEntity;
    }

    public boolean isActiveChest(EnderChestBlockEntity blockEntity) {
        return this.activeChest == blockEntity;
    }

    @Override
    public void fromTag(ListTag nbtList) {
        int i;

        for (i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, ItemStack.EMPTY);
        }

        for (i = 0; i < nbtList.size(); ++i) {
            CompoundTag nbttagcompound = nbtList.getCompound(i);
            int j = nbttagcompound.getByte("Slot") & 255;

            if (j >= 0 && j < this.getContainerSize()) {
                this.setItem(j, ItemStack.of(nbttagcompound));
            }
        }

    }

    @Override
    public ListTag createTag() {
        ListTag nbttaglist = new ListTag();

        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                CompoundTag nbttagcompound = new CompoundTag();

                nbttagcompound.putByte("Slot", (byte) i);
                itemstack.save(nbttagcompound);
                nbttaglist.add(nbttagcompound);
            }
        }

        return nbttaglist;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.activeChest != null && !this.activeChest.stillValid(player) ? false : super.stillValid(player);
    }

    @Override
    public void startOpen(Player player) {
        if (this.activeChest != null) {
            this.activeChest.startOpen(player);
        }

        super.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        if (this.activeChest != null) {
            this.activeChest.stopOpen(player);
        }

        super.stopOpen(player);
        this.activeChest = null;
    }
}
