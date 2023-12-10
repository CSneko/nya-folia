package net.minecraft.world.inventory;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ItemCombinerMenu extends AbstractContainerMenu {

    private static final int INVENTORY_SLOTS_PER_ROW = 9;
    private static final int INVENTORY_SLOTS_PER_COLUMN = 3;
    protected final ContainerLevelAccess access;
    protected final Player player;
    protected final Container inputSlots;
    private final List<Integer> inputSlotIndexes;
    protected final ResultContainer resultSlots; // Paper - delay field init
    private final int resultSlotIndex;

    protected abstract boolean mayPickup(Player player, boolean present);

    protected abstract void onTake(Player player, ItemStack stack);

    protected abstract boolean isValidBlock(BlockState state);

    public ItemCombinerMenu(@Nullable MenuType<?> type, int syncId, Inventory playerInventory, ContainerLevelAccess context) {
        super(type, syncId);
        this.access = context;
        this.resultSlots = new ResultContainer(this.createBlockHolder(this.access)); // Paper - delay field init
        this.player = playerInventory.player;
        ItemCombinerMenuSlotDefinition itemcombinermenuslotdefinition = this.createInputSlotDefinitions();

        this.inputSlots = this.createContainer(itemcombinermenuslotdefinition.getNumOfInputSlots());
        this.inputSlotIndexes = itemcombinermenuslotdefinition.getInputSlotIndexes();
        this.resultSlotIndex = itemcombinermenuslotdefinition.getResultSlotIndex();
        this.createInputSlots(itemcombinermenuslotdefinition);
        this.createResultSlot(itemcombinermenuslotdefinition);
        this.createInventorySlots(playerInventory);
    }

    private void createInputSlots(ItemCombinerMenuSlotDefinition forgingSlotsManager) {
        Iterator iterator = forgingSlotsManager.getSlots().iterator();

        while (iterator.hasNext()) {
            final ItemCombinerMenuSlotDefinition.SlotDefinition itemcombinermenuslotdefinition_b = (ItemCombinerMenuSlotDefinition.SlotDefinition) iterator.next();

            this.addSlot(new Slot(this.inputSlots, itemcombinermenuslotdefinition_b.slotIndex(), itemcombinermenuslotdefinition_b.x(), itemcombinermenuslotdefinition_b.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return itemcombinermenuslotdefinition_b.mayPlace().test(stack);
                }
            });
        }

    }

    private void createResultSlot(ItemCombinerMenuSlotDefinition forgingSlotsManager) {
        this.addSlot(new Slot(this.resultSlots, forgingSlotsManager.getResultSlot().slotIndex(), forgingSlotsManager.getResultSlot().x(), forgingSlotsManager.getResultSlot().y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player playerEntity) {
                return ItemCombinerMenu.this.mayPickup(playerEntity, this.hasItem());
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                ItemCombinerMenu.this.onTake(player, stack);
            }
        });
    }

    private void createInventorySlots(Inventory playerInventory) {
        int i;

        for (i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }

    }

    public abstract void createResult();

    protected abstract ItemCombinerMenuSlotDefinition createInputSlotDefinitions();

    private SimpleContainer createContainer(int size) {
        return new SimpleContainer(this.createBlockHolder(this.access), size) { // Paper
            @Override
            public void setChanged() {
                super.setChanged();
                ItemCombinerMenu.this.slotsChanged(this);
            }
        };
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.inputSlots) {
            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, this instanceof SmithingMenu ? 3 : 2); // Paper
        }

    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(player, this.inputSlots);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return (Boolean) this.access.evaluate((world, blockposition) -> {
            return !this.isValidBlock(world.getBlockState(blockposition)) ? false : player.distanceToSqr((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D) <= 64.0D;
        }, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            int j = this.getInventorySlotStart();
            int k = this.getUseRowEnd();

            if (slot == this.getResultSlot()) {
                if (!this.moveItemStackTo(itemstack1, j, k, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
            } else if (this.inputSlotIndexes.contains(slot)) {
                if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.canMoveIntoInputSlots(itemstack1) && slot >= this.getInventorySlotStart() && slot < this.getUseRowEnd()) {
                int l = this.getSlotToQuickMoveTo(itemstack);

                if (!this.moveItemStackTo(itemstack1, l, this.getResultSlot(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot >= this.getInventorySlotStart() && slot < this.getInventorySlotEnd()) {
                if (!this.moveItemStackTo(itemstack1, this.getUseRowStart(), this.getUseRowEnd(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot >= this.getUseRowStart() && slot < this.getUseRowEnd() && !this.moveItemStackTo(itemstack1, this.getInventorySlotStart(), this.getInventorySlotEnd(), false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot1.setByPlayer(ItemStack.EMPTY);
            } else {
                slot1.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot1.onTake(player, itemstack1);
        }

        return itemstack;
    }

    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        return true;
    }

    public int getSlotToQuickMoveTo(ItemStack stack) {
        return this.inputSlots.isEmpty() ? 0 : (Integer) this.inputSlotIndexes.get(0);
    }

    public int getResultSlot() {
        return this.resultSlotIndex;
    }

    private int getInventorySlotStart() {
        return this.getResultSlot() + 1;
    }

    private int getInventorySlotEnd() {
        return this.getInventorySlotStart() + 27;
    }

    private int getUseRowStart() {
        return this.getInventorySlotEnd();
    }

    private int getUseRowEnd() {
        return this.getUseRowStart() + 9;
    }
}
