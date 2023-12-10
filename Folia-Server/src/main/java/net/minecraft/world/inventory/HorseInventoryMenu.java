package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.inventory.InventoryView;
// CraftBukkit end

public class HorseInventoryMenu extends AbstractContainerMenu {

    private final Container horseContainer;
    private final AbstractHorse horse;

    // CraftBukkit start
    org.bukkit.craftbukkit.inventory.CraftInventoryView bukkitEntity;
    Inventory player;

    @Override
    public InventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        return this.bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), this.horseContainer.getOwner().getInventory(), this);
    }

    public HorseInventoryMenu(int syncId, Inventory playerInventory, Container inventory, final AbstractHorse entity) {
        super((MenuType) null, syncId);
        this.player = playerInventory;
        // CraftBukkit end
        this.horseContainer = inventory;
        this.horse = entity;
        boolean flag = true;

        inventory.startOpen(playerInventory.player);
        boolean flag1 = true;

        this.addSlot(new Slot(inventory, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.SADDLE) && !this.hasItem() && entity.isSaddleable();
            }

            @Override
            public boolean isActive() {
                return entity.isSaddleable();
            }
        });
        this.addSlot(new Slot(inventory, 1, 8, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return entity.isArmor(stack);
            }

            @Override
            public boolean isActive() {
                return entity.canWearArmor();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        int j;
        int k;

        if (this.hasChest(entity)) {
            for (j = 0; j < 3; ++j) {
                for (k = 0; k < ((AbstractChestedHorse) entity).getInventoryColumns(); ++k) {
                    this.addSlot(new Slot(inventory, 2 + k + j * ((AbstractChestedHorse) entity).getInventoryColumns(), 80 + k * 18, 18 + j * 18));
                }
            }
        }

        for (j = 0; j < 3; ++j) {
            for (k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + j * 9 + 9, 8 + k * 18, 102 + j * 18 + -18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 142));
        }

    }

    @Override
    public boolean stillValid(Player player) {
        return !this.horse.hasInventoryChanged(this.horseContainer) && this.horseContainer.stillValid(player) && this.horse.isAlive() && this.horse.distanceTo(player) < 8.0F;
    }

    private boolean hasChest(AbstractHorse horse) {
        return horse instanceof AbstractChestedHorse && ((AbstractChestedHorse) horse).hasChest();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            int j = this.horseContainer.getContainerSize();

            if (slot < j) {
                if (!this.moveItemStackTo(itemstack1, j, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(itemstack1) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(itemstack1)) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (j <= 2 || !this.moveItemStackTo(itemstack1, 2, j, false)) {
                int k = j + 27;
                int l = k + 9;

                if (slot >= k && slot < l) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slot >= j && slot < k) {
                    if (!this.moveItemStackTo(itemstack1, k, l, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, k, k, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot1.setByPlayer(ItemStack.EMPTY);
            } else {
                slot1.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.horseContainer.stopOpen(player);
    }
}
