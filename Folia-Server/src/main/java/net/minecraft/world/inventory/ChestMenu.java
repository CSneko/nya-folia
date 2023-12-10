package net.minecraft.world.inventory;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
// CraftBukkit end

public class ChestMenu extends AbstractContainerMenu {

    private static final int SLOTS_PER_ROW = 9;
    private final Container container;
    private final int containerRows;
    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Inventory player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventory inventory;
        if (this.container instanceof Inventory) {
            inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryPlayer((Inventory) this.container);
        } else if (this.container instanceof CompoundContainer) {
            inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) this.container);
        } else {
            inventory = new CraftInventory(this.container);
        }

        this.bukkitEntity = new CraftInventoryView(this.player.player.getBukkitEntity(), inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end

    private ChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, int rows) {
        this(type, syncId, playerInventory, new SimpleContainer(9 * rows), rows);
    }

    public static ChestMenu oneRow(int syncId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, syncId, playerInventory, 1);
    }

    public static ChestMenu twoRows(int syncId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x2, syncId, playerInventory, 2);
    }

    public static ChestMenu threeRows(int syncId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x3, syncId, playerInventory, 3);
    }

    public static ChestMenu fourRows(int syncId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x4, syncId, playerInventory, 4);
    }

    public static ChestMenu fiveRows(int syncId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x5, syncId, playerInventory, 5);
    }

    public static ChestMenu sixRows(int syncId, Inventory playerInventory) {
        return new ChestMenu(MenuType.GENERIC_9x6, syncId, playerInventory, 6);
    }

    public static ChestMenu threeRows(int syncId, Inventory playerInventory, Container inventory) {
        return new ChestMenu(MenuType.GENERIC_9x3, syncId, playerInventory, inventory, 3);
    }

    public static ChestMenu sixRows(int syncId, Inventory playerInventory, Container inventory) {
        return new ChestMenu(MenuType.GENERIC_9x6, syncId, playerInventory, inventory, 6);
    }

    public ChestMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container inventory, int rows) {
        super(type, syncId);
        checkContainerSize(inventory, rows * 9);
        this.container = inventory;
        this.containerRows = rows;
        inventory.startOpen(playerInventory.player);
        int k = (this.containerRows - 4) * 18;

        // CraftBukkit start - Save player
        this.player = playerInventory;
        // CraftBukkit end

        int l;
        int i1;

        for (l = 0; l < this.containerRows; ++l) {
            for (i1 = 0; i1 < 9; ++i1) {
                this.addSlot(new Slot(inventory, i1 + l * 9, 8 + i1 * 18, 18 + l * 18));
            }
        }

        for (l = 0; l < 3; ++l) {
            for (i1 = 0; i1 < 9; ++i1) {
                this.addSlot(new Slot(playerInventory, i1 + l * 9 + 9, 8 + i1 * 18, 103 + l * 18 + k));
            }
        }

        for (l = 0; l < 9; ++l) {
            this.addSlot(new Slot(playerInventory, l, 8 + l * 18, 161 + k));
        }

    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            if (slot < this.containerRows * 9) {
                if (!this.moveItemStackTo(itemstack1, this.containerRows * 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, this.containerRows * 9, false)) {
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
        this.container.stopOpen(player);
    }

    public Container getContainer() {
        return this.container;
    }

    public int getRowCount() {
        return this.containerRows;
    }
}
