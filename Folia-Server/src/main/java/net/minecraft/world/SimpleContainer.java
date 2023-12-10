package net.minecraft.world;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class SimpleContainer implements Container, StackedContentsCompatible {

    private final int size;
    public final NonNullList<ItemStack> items;
    @Nullable
    private List<ContainerListener> listeners;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;
    protected @Nullable org.bukkit.inventory.InventoryHolder bukkitOwner; // Paper - annotation

    public List<ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int i) {
        this.maxStack = i;
    }

    public org.bukkit.inventory.InventoryHolder getOwner() {
        // Paper start
        if (this.bukkitOwner == null && this.bukkitOwnerCreator != null) {
            this.bukkitOwner = this.bukkitOwnerCreator.get();
        }
        // Paper end
        return this.bukkitOwner;
    }

    @Override
    public Location getLocation() {
        // Paper start
        // When the block inventory does not have a tile state that implements getLocation, e. g. composters
        if (this.bukkitOwner instanceof org.bukkit.inventory.BlockInventoryHolder blockInventoryHolder) {
            return blockInventoryHolder.getBlock().getLocation();
        }
        // When the bukkit owner is a bukkit entity, but does not implement Container itself, e. g. horses
        if (this.bukkitOwner instanceof org.bukkit.entity.Entity entity) {
            return entity.getLocation();
        }
        // Paper end
        return null;
    }

    public SimpleContainer(SimpleContainer original) {
        this(original.size);
        for (int slot = 0; slot < original.size; slot++) {
            this.items.set(slot, original.items.get(slot).copy());
        }
    }

    public SimpleContainer(int size) {
        this(size, null);
    }
    // Paper start
    private @Nullable java.util.function.Supplier<? extends org.bukkit.inventory.InventoryHolder> bukkitOwnerCreator;
    public SimpleContainer(java.util.function.Supplier<? extends org.bukkit.inventory.InventoryHolder> bukkitOwnerCreator, int size) {
        this(size);
        this.bukkitOwnerCreator = bukkitOwnerCreator;
    }
    // Paper end

    public SimpleContainer(int i, org.bukkit.inventory.InventoryHolder owner) {
        this.bukkitOwner = owner;
        // CraftBukkit end
        this.size = i;
        this.items = NonNullList.withSize(i, ItemStack.EMPTY);
    }

    public SimpleContainer(ItemStack... items) {
        this.size = items.length;
        this.items = NonNullList.of(ItemStack.EMPTY, items);
    }

    public void addListener(ContainerListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(ContainerListener listener) {
        if (this.listeners != null) {
            this.listeners.remove(listener);
        }

    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? (ItemStack) this.items.get(slot) : ItemStack.EMPTY;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> list = (List) this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).collect(Collectors.toList());

        this.clearContent();
        return list;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = ContainerHelper.removeItem(this.items, slot, amount);

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    public ItemStack removeItemType(Item item, int count) {
        ItemStack itemstack = new ItemStack(item, 0);

        for (int j = this.size - 1; j >= 0; --j) {
            ItemStack itemstack1 = this.getItem(j);

            if (itemstack1.getItem().equals(item)) {
                int k = count - itemstack.getCount();
                ItemStack itemstack2 = itemstack1.split(k);

                itemstack.grow(itemstack2.getCount());
                if (itemstack.getCount() == count) {
                    break;
                }
            }
        }

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = stack.copy();

            this.moveItemToOccupiedSlotsWithSameType(itemstack1);
            if (itemstack1.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                this.moveItemToEmptySlots(itemstack1);
                return itemstack1.isEmpty() ? ItemStack.EMPTY : itemstack1;
            }
        }
    }

    public boolean canAddItem(ItemStack stack) {
        boolean flag = false;
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack1 = (ItemStack) iterator.next();

            if (itemstack1.isEmpty() || ItemStack.isSameItemSameTags(itemstack1, stack) && itemstack1.getCount() < itemstack1.getMaxStackSize()) {
                flag = true;
                break;
            }
        }

        return flag;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack itemstack = (ItemStack) this.items.get(slot);

        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.items.set(slot, ItemStack.EMPTY);
            return itemstack;
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        this.setChanged();
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public void setChanged() {
        if (this.listeners != null) {
            Iterator iterator = this.listeners.iterator();

            while (iterator.hasNext()) {
                ContainerListener iinventorylistener = (ContainerListener) iterator.next();

                iinventorylistener.containerChanged(this);
            }
        }

    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public void fillStackedContents(StackedContents finder) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            finder.accountStack(itemstack);
        }

    }

    public String toString() {
        return ((List) this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).collect(Collectors.toList())).toString();
    }

    private void moveItemToEmptySlots(ItemStack stack) {
        for (int i = 0; i < this.size; ++i) {
            ItemStack itemstack1 = this.getItem(i);

            if (itemstack1.isEmpty()) {
                this.setItem(i, stack.copyAndClear());
                return;
            }
        }

    }

    private void moveItemToOccupiedSlotsWithSameType(ItemStack stack) {
        for (int i = 0; i < this.size; ++i) {
            ItemStack itemstack1 = this.getItem(i);

            if (ItemStack.isSameItemSameTags(itemstack1, stack)) {
                this.moveItemsBetweenStacks(stack, itemstack1);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

    }

    private void moveItemsBetweenStacks(ItemStack source, ItemStack target) {
        int i = Math.min(this.getMaxStackSize(), target.getMaxStackSize());
        int j = Math.min(source.getCount(), i - target.getCount());

        if (j > 0) {
            target.grow(j);
            source.shrink(j);
            this.setChanged();
        }

    }

    public void fromTag(ListTag nbtList) {
        this.clearContent();

        for (int i = 0; i < nbtList.size(); ++i) {
            ItemStack itemstack = ItemStack.of(nbtList.getCompound(i));

            if (!itemstack.isEmpty()) {
                this.addItem(itemstack);
            }
        }

    }

    public ListTag createTag() {
        ListTag nbttaglist = new ListTag();

        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                nbttaglist.add(itemstack.save(new CompoundTag()));
            }
        }

        return nbttaglist;
    }
}
