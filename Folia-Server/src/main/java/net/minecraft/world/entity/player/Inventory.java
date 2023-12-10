package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class Inventory implements Container, Nameable {

    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    private static final int SELECTION_SIZE = 9;
    public static final int SLOT_OFFHAND = 40;
    public static final int NOT_FOUND_INDEX = -1;
    public static final int[] ALL_ARMOR_SLOTS = new int[]{0, 1, 2, 3};
    public static final int[] HELMET_SLOT_ONLY = new int[]{3};
    public final NonNullList<ItemStack> items;
    public final NonNullList<ItemStack> armor;
    public final NonNullList<ItemStack> offhand;
    public final List<NonNullList<ItemStack>> compartments;
    public int selected;
    public final Player player;
    private int timesChanged;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        List<ItemStack> combined = new ArrayList<ItemStack>(this.items.size() + this.armor.size() + this.offhand.size());
        for (List<net.minecraft.world.item.ItemStack> sub : this.compartments) {
            combined.addAll(sub);
        }

        return combined;
    }

    public List<ItemStack> getArmorContents() {
        return this.armor;
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

    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.player.getBukkitEntity();
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public Location getLocation() {
        return this.player.getBukkitEntity().getLocation();
    }
    // CraftBukkit end

    public Inventory(Player player) {
        this.items = NonNullList.withSize(36, ItemStack.EMPTY);
        this.armor = NonNullList.withSize(4, ItemStack.EMPTY);
        this.offhand = NonNullList.withSize(1, ItemStack.EMPTY);
        this.compartments = ImmutableList.of(this.items, this.armor, this.offhand);
        this.player = player;
    }

    public ItemStack getSelected() {
        return Inventory.isHotbarSlot(this.selected) ? (ItemStack) this.items.get(this.selected) : ItemStack.EMPTY;
    }

    public static int getSelectionSize() {
        return 9;
    }

    private boolean hasRemainingSpaceForItem(ItemStack existingStack, ItemStack stack) {
        return !existingStack.isEmpty() && ItemStack.isSameItemSameTags(existingStack, stack) && existingStack.isStackable() && existingStack.getCount() < existingStack.getMaxStackSize() && existingStack.getCount() < this.getMaxStackSize();
    }

    // CraftBukkit start - Watch method above! :D
    public int canHold(ItemStack itemstack) {
        int remains = itemstack.getCount();
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.getItem(i);
            if (itemstack1.isEmpty()) return itemstack.getCount();

            if (this.hasRemainingSpaceForItem(itemstack1, itemstack)) {
                remains -= (itemstack1.getMaxStackSize() < this.getMaxStackSize() ? itemstack1.getMaxStackSize() : this.getMaxStackSize()) - itemstack1.getCount();
            }
            if (remains <= 0) return itemstack.getCount();
        }
        ItemStack offhandItemStack = this.getItem(this.items.size() + this.armor.size());
        if (this.hasRemainingSpaceForItem(offhandItemStack, itemstack)) {
            remains -= (offhandItemStack.getMaxStackSize() < this.getMaxStackSize() ? offhandItemStack.getMaxStackSize() : this.getMaxStackSize()) - offhandItemStack.getCount();
        }
        if (remains <= 0) return itemstack.getCount();

        return itemstack.getCount() - remains;
    }
    // CraftBukkit end

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public void setPickedItem(ItemStack stack) {
        int i = this.findSlotMatchingItem(stack);

        if (Inventory.isHotbarSlot(i)) {
            this.selected = i;
        } else {
            if (i == -1) {
                this.selected = this.getSuitableHotbarSlot();
                if (!((ItemStack) this.items.get(this.selected)).isEmpty()) {
                    int j = this.getFreeSlot();

                    if (j != -1) {
                        this.items.set(j, (ItemStack) this.items.get(this.selected));
                    }
                }

                this.items.set(this.selected, stack);
            } else {
                this.pickSlot(i);
            }

        }
    }

    public void pickSlot(int slot) {
        // Paper start - Add PlayerPickItemEvent
        pickSlot(slot, this.getSuitableHotbarSlot());
    }

    public void pickSlot(int slot, int targetSlot) {
        this.selected = targetSlot;
        // Paper end
        ItemStack itemstack = (ItemStack) this.items.get(this.selected);

        this.items.set(this.selected, (ItemStack) this.items.get(slot));
        this.items.set(slot, itemstack);
    }

    public static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    public int findSlotMatchingItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty() && ItemStack.isSameItemSameTags(stack, (ItemStack) this.items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public int findSlotMatchingUnusedItem(ItemStack stack) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = (ItemStack) this.items.get(i);

            if (!((ItemStack) this.items.get(i)).isEmpty() && ItemStack.isSameItemSameTags(stack, (ItemStack) this.items.get(i)) && !((ItemStack) this.items.get(i)).isDamaged() && !itemstack1.isEnchanted() && !itemstack1.hasCustomHoverName()) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        int i;
        int j;

        for (j = 0; j < 9; ++j) {
            i = (this.selected + j) % 9;
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                return i;
            }
        }

        for (j = 0; j < 9; ++j) {
            i = (this.selected + j) % 9;
            if (!((ItemStack) this.items.get(i)).isEnchanted()) {
                return i;
            }
        }

        return this.selected;
    }

    public void swapPaint(double scrollAmount) {
        int i = (int) Math.signum(scrollAmount);

        for (this.selected -= i; this.selected < 0; this.selected += 9) {
            ;
        }

        while (this.selected >= 9) {
            this.selected -= 9;
        }

    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> shouldRemove, int maxCount, Container craftingInventory) {
        byte b0 = 0;
        boolean flag = maxCount == 0;
        int j = b0 + ContainerHelper.clearOrCountMatchingItems((Container) this, shouldRemove, maxCount - b0, flag);

        j += ContainerHelper.clearOrCountMatchingItems(craftingInventory, shouldRemove, maxCount - j, flag);
        ItemStack itemstack = this.player.containerMenu.getCarried();

        j += ContainerHelper.clearOrCountMatchingItems(itemstack, shouldRemove, maxCount - j, flag);
        if (itemstack.isEmpty()) {
            this.player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        return j;
    }

    private int addResource(ItemStack stack) {
        int i = this.getSlotWithRemainingSpace(stack);

        if (i == -1) {
            i = this.getFreeSlot();
        }

        return i == -1 ? stack.getCount() : this.addResource(i, stack);
    }

    private int addResource(int slot, ItemStack stack) {
        Item item = stack.getItem();
        int j = stack.getCount();
        ItemStack itemstack1 = this.getItem(slot);

        if (itemstack1.isEmpty()) {
            itemstack1 = new ItemStack(item, 0);
            if (stack.hasTag()) {
                itemstack1.setTag(stack.getTag().copy());
            }

            this.setItem(slot, itemstack1);
        }

        int k = j;

        if (j > itemstack1.getMaxStackSize() - itemstack1.getCount()) {
            k = itemstack1.getMaxStackSize() - itemstack1.getCount();
        }

        if (k > this.getMaxStackSize() - itemstack1.getCount()) {
            k = this.getMaxStackSize() - itemstack1.getCount();
        }

        if (k == 0) {
            return j;
        } else {
            j -= k;
            itemstack1.grow(k);
            itemstack1.setPopTime(5);
            return j;
        }
    }

    public int getSlotWithRemainingSpace(ItemStack stack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), stack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), stack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); ++i) {
                if (this.hasRemainingSpaceForItem((ItemStack) this.items.get(i), stack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            NonNullList<ItemStack> nonnulllist = (NonNullList) iterator.next();

            for (int i = 0; i < nonnulllist.size(); ++i) {
                if (!((ItemStack) nonnulllist.get(i)).isEmpty()) {
                    ((ItemStack) nonnulllist.get(i)).inventoryTick(this.player.level(), this.player, i, this.selected == i);
                }
            }
        }

    }

    public boolean add(ItemStack stack) {
        return this.add(-1, stack);
    }

    public boolean add(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            try {
                if (stack.isDamaged()) {
                    if (slot == -1) {
                        slot = this.getFreeSlot();
                    }

                    if (slot >= 0) {
                        this.items.set(slot, stack.copyAndClear());
                        ((ItemStack) this.items.get(slot)).setPopTime(5);
                        return true;
                    } else if (this.player.getAbilities().instabuild) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int j;

                    do {
                        j = stack.getCount();
                        if (slot == -1) {
                            stack.setCount(this.addResource(stack));
                        } else {
                            stack.setCount(this.addResource(slot, stack));
                        }
                    } while (!stack.isEmpty() && stack.getCount() < j);

                    if (stack.getCount() == j && this.player.getAbilities().instabuild) {
                        stack.setCount(0);
                        return true;
                    } else {
                        return stack.getCount() < j;
                    }
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Adding item to inventory");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Item being added");

                crashreportsystemdetails.setDetail("Item ID", (Object) Item.getId(stack.getItem()));
                crashreportsystemdetails.setDetail("Item data", (Object) stack.getDamageValue());
                crashreportsystemdetails.setDetail("Item name", () -> {
                    return stack.getHoverName().getString();
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    public void placeItemBackInInventory(ItemStack stack) {
        this.placeItemBackInInventory(stack, true);
    }

    public void placeItemBackInInventory(ItemStack stack, boolean notifiesClient) {
        while (true) {
            if (!stack.isEmpty()) {
                int i = this.getSlotWithRemainingSpace(stack);

                if (i == -1) {
                    i = this.getFreeSlot();
                }

                if (i != -1) {
                    int j = stack.getMaxStackSize() - this.getItem(i).getCount();

                    if (this.add(i, stack.split(j)) && notifiesClient && this.player instanceof ServerPlayer) {
                        ((ServerPlayer) this.player).connection.send(new ClientboundContainerSetSlotPacket(-2, 0, i, this.getItem(i)));
                    }
                    continue;
                }

                this.player.drop(stack, false);
            }

            return;
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        List<ItemStack> list = null;

        NonNullList nonnulllist;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); slot -= nonnulllist.size()) {
            nonnulllist = (NonNullList) iterator.next();
            if (slot < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }
        }

        return list != null && !((ItemStack) list.get(slot)).isEmpty() ? ContainerHelper.removeItem(list, slot, amount) : ItemStack.EMPTY;
    }

    public void removeItem(ItemStack stack) {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            NonNullList<ItemStack> nonnulllist = (NonNullList) iterator.next();

            for (int i = 0; i < nonnulllist.size(); ++i) {
                if (nonnulllist.get(i) == stack) {
                    nonnulllist.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }

    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        NonNullList<ItemStack> nonnulllist = null;

        NonNullList nonnulllist1;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); slot -= nonnulllist1.size()) {
            nonnulllist1 = (NonNullList) iterator.next();
            if (slot < nonnulllist1.size()) {
                nonnulllist = nonnulllist1;
                break;
            }
        }

        if (nonnulllist != null && !((ItemStack) nonnulllist.get(slot)).isEmpty()) {
            ItemStack itemstack = (ItemStack) nonnulllist.get(slot);

            nonnulllist.set(slot, ItemStack.EMPTY);
            return itemstack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        NonNullList<ItemStack> nonnulllist = null;

        NonNullList nonnulllist1;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); slot -= nonnulllist1.size()) {
            nonnulllist1 = (NonNullList) iterator.next();
            if (slot < nonnulllist1.size()) {
                nonnulllist = nonnulllist1;
                break;
            }
        }

        if (nonnulllist != null) {
            nonnulllist.set(slot, stack);
        }

    }

    public float getDestroySpeed(BlockState block) {
        return ((ItemStack) this.items.get(this.selected)).getDestroySpeed(block);
    }

    public ListTag save(ListTag nbtList) {
        CompoundTag nbttagcompound;
        int i;

        for (i = 0; i < this.items.size(); ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty()) {
                nbttagcompound = new CompoundTag();
                nbttagcompound.putByte("Slot", (byte) i);
                ((ItemStack) this.items.get(i)).save(nbttagcompound);
                nbtList.add(nbttagcompound);
            }
        }

        for (i = 0; i < this.armor.size(); ++i) {
            if (!((ItemStack) this.armor.get(i)).isEmpty()) {
                nbttagcompound = new CompoundTag();
                nbttagcompound.putByte("Slot", (byte) (i + 100));
                ((ItemStack) this.armor.get(i)).save(nbttagcompound);
                nbtList.add(nbttagcompound);
            }
        }

        for (i = 0; i < this.offhand.size(); ++i) {
            if (!((ItemStack) this.offhand.get(i)).isEmpty()) {
                nbttagcompound = new CompoundTag();
                nbttagcompound.putByte("Slot", (byte) (i + 150));
                ((ItemStack) this.offhand.get(i)).save(nbttagcompound);
                nbtList.add(nbttagcompound);
            }
        }

        return nbtList;
    }

    public void load(ListTag nbtList) {
        this.items.clear();
        this.armor.clear();
        this.offhand.clear();

        for (int i = 0; i < nbtList.size(); ++i) {
            CompoundTag nbttagcompound = nbtList.getCompound(i);
            int j = nbttagcompound.getByte("Slot") & 255;
            ItemStack itemstack = ItemStack.of(nbttagcompound);

            if (!itemstack.isEmpty()) {
                if (j >= 0 && j < this.items.size()) {
                    this.items.set(j, itemstack);
                } else if (j >= 100 && j < this.armor.size() + 100) {
                    this.armor.set(j - 100, itemstack);
                } else if (j >= 150 && j < this.offhand.size() + 150) {
                    this.offhand.set(j - 150, itemstack);
                }
            }
        }

    }

    @Override
    public int getContainerSize() {
        return this.items.size() + this.armor.size() + this.offhand.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                iterator = this.armor.iterator();

                do {
                    if (!iterator.hasNext()) {
                        iterator = this.offhand.iterator();

                        do {
                            if (!iterator.hasNext()) {
                                return true;
                            }

                            itemstack = (ItemStack) iterator.next();
                        } while (itemstack.isEmpty());

                        return false;
                    }

                    itemstack = (ItemStack) iterator.next();
                } while (itemstack.isEmpty());

                return false;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        List<ItemStack> list = null;

        NonNullList nonnulllist;

        for (Iterator iterator = this.compartments.iterator(); iterator.hasNext(); slot -= nonnulllist.size()) {
            nonnulllist = (NonNullList) iterator.next();
            if (slot < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }
        }

        return list == null ? ItemStack.EMPTY : (ItemStack) list.get(slot);
    }

    @Override
    public Component getName() {
        return Component.translatable("container.inventory");
    }

    public ItemStack getArmor(int slot) {
        return (ItemStack) this.armor.get(slot);
    }

    public void hurtArmor(DamageSource damageSource, float amount, int[] slots) {
        if (amount > 0.0F) {
            amount /= 4.0F;
            if (amount < 1.0F) {
                amount = 1.0F;
            }

            int[] aint1 = slots;
            int i = slots.length;

            for (int j = 0; j < i; ++j) {
                int k = aint1[j];
                ItemStack itemstack = (ItemStack) this.armor.get(k);

                if ((!damageSource.is(DamageTypeTags.IS_FIRE) || !itemstack.getItem().isFireResistant()) && itemstack.getItem() instanceof ArmorItem) {
                    itemstack.hurtAndBreak((int) amount, this.player, (entityhuman) -> {
                        entityhuman.broadcastBreakEvent(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, k));
                    });
                }
            }

        }
    }

    public void dropAll() {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();

            for (int i = 0; i < list.size(); ++i) {
                ItemStack itemstack = (ItemStack) list.get(i);

                if (!itemstack.isEmpty()) {
                    this.player.drop(itemstack, true, false);
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }

    }

    @Override
    public void setChanged() {
        ++this.timesChanged;
    }

    public int getTimesChanged() {
        return this.timesChanged;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.player.isRemoved() ? false : player.distanceToSqr((Entity) this.player) <= 64.0D;
    }

    public boolean contains(ItemStack stack) {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                ItemStack itemstack1 = (ItemStack) iterator1.next();

                if (!itemstack1.isEmpty() && ItemStack.isSameItemSameTags(itemstack1, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                ItemStack itemstack = (ItemStack) iterator1.next();

                if (!itemstack.isEmpty() && itemstack.is(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void replaceWith(Inventory other) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, other.getItem(i));
        }

        this.selected = other.selected;
    }

    @Override
    public void clearContent() {
        Iterator iterator = this.compartments.iterator();

        while (iterator.hasNext()) {
            List<ItemStack> list = (List) iterator.next();

            list.clear();
        }

    }

    public void fillStackedContents(StackedContents finder) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            finder.accountSimpleStack(itemstack);
        }

    }

    public ItemStack removeFromSelected(boolean entireStack) {
        ItemStack itemstack = this.getSelected();

        return itemstack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, entireStack ? itemstack.getCount() : 1);
    }
}
