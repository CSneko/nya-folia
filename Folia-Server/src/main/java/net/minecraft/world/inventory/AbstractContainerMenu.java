package net.minecraft.world.inventory;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

// CraftBukkit start
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
// CraftBukkit end

public abstract class AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SLOT_CLICKED_OUTSIDE = -999;
    public static final int QUICKCRAFT_TYPE_CHARITABLE = 0;
    public static final int QUICKCRAFT_TYPE_GREEDY = 1;
    public static final int QUICKCRAFT_TYPE_CLONE = 2;
    public static final int QUICKCRAFT_HEADER_START = 0;
    public static final int QUICKCRAFT_HEADER_CONTINUE = 1;
    public static final int QUICKCRAFT_HEADER_END = 2;
    public static final int CARRIED_SLOT_SIZE = Integer.MAX_VALUE;
    public NonNullList<ItemStack> lastSlots = NonNullList.create();
    public NonNullList<Slot> slots = NonNullList.create();
    public List<DataSlot> dataSlots = Lists.newArrayList();
    private ItemStack carried;
    public NonNullList<ItemStack> remoteSlots;
    public IntList remoteDataSlots;
    private ItemStack remoteCarried;
    private int stateId;
    @Nullable
    private final MenuType<?> menuType;
    public final int containerId;
    private int quickcraftType;
    private int quickcraftStatus;
    private final Set<Slot> quickcraftSlots;
    private final List<ContainerListener> containerListeners;
    @Nullable
    private ContainerSynchronizer synchronizer;
    private boolean suppressRemoteUpdates;

    // CraftBukkit start
    public boolean checkReachable = true;
    public abstract InventoryView getBukkitView();
    public void transferTo(AbstractContainerMenu other, org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        InventoryView source = this.getBukkitView(), destination = other.getBukkitView();
        ((CraftInventory) source.getTopInventory()).getInventory().onClose(player);
        ((CraftInventory) source.getBottomInventory()).getInventory().onClose(player);
        ((CraftInventory) destination.getTopInventory()).getInventory().onOpen(player);
        ((CraftInventory) destination.getBottomInventory()).getInventory().onOpen(player);
    }
    private Component title;
    public final Component getTitle() {
        // Paper start - return chat component with empty text instead of throwing error
        // Preconditions.checkState(this.title != null, "Title not set");
        if(this.title == null){
            return Component.literal("");
        }
        // Paper end
        return this.title;
    }
    public final void setTitle(Component title) {
        Preconditions.checkState(this.title == null, "Title already set");
        this.title = title;
    }
    // CraftBukkit end

    protected AbstractContainerMenu(@Nullable MenuType<?> type, int syncId) {
        this.carried = ItemStack.EMPTY;
        this.remoteSlots = NonNullList.create();
        this.remoteDataSlots = new IntArrayList();
        this.remoteCarried = ItemStack.EMPTY;
        this.quickcraftType = -1;
        this.quickcraftSlots = Sets.newHashSet();
        this.containerListeners = Lists.newArrayList();
        this.menuType = type;
        this.containerId = syncId;
    }

    protected static boolean stillValid(ContainerLevelAccess context, Player player, Block block) {
        return (Boolean) context.evaluate((world, blockposition) -> {
            return !world.getBlockState(blockposition).is(block) ? false : player.distanceToSqr((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D) <= 64.0D;
        }, true);
    }

    public MenuType<?> getType() {
        if (this.menuType == null) {
            throw new UnsupportedOperationException("Unable to construct this menu by type");
        } else {
            return this.menuType;
        }
    }

    protected static void checkContainerSize(Container inventory, int expectedSize) {
        int j = inventory.getContainerSize();

        if (j < expectedSize) {
            throw new IllegalArgumentException("Container size " + j + " is smaller than expected " + expectedSize);
        }
    }

    protected static void checkContainerDataCount(ContainerData data, int expectedCount) {
        int j = data.getCount();

        if (j < expectedCount) {
            throw new IllegalArgumentException("Container data count " + j + " is smaller than expected " + expectedCount);
        }
    }

    public boolean isValidSlotIndex(int slot) {
        return slot == -1 || slot == -999 || slot < this.slots.size();
    }

    protected Slot addSlot(Slot slot) {
        slot.index = this.slots.size();
        this.slots.add(slot);
        this.lastSlots.add(ItemStack.EMPTY);
        this.remoteSlots.add(ItemStack.EMPTY);
        return slot;
    }

    protected DataSlot addDataSlot(DataSlot property) {
        this.dataSlots.add(property);
        this.remoteDataSlots.add(0);
        return property;
    }

    protected void addDataSlots(ContainerData propertyDelegate) {
        for (int i = 0; i < propertyDelegate.getCount(); ++i) {
            this.addDataSlot(DataSlot.forContainer(propertyDelegate, i));
        }

    }

    public void addSlotListener(ContainerListener listener) {
        if (!this.containerListeners.contains(listener)) {
            this.containerListeners.add(listener);
            this.broadcastChanges();
        }
    }

    public void setSynchronizer(ContainerSynchronizer handler) {
        this.synchronizer = handler;
        this.sendAllDataToRemote();
    }

    public void sendAllDataToRemote() {
        int i = 0;

        int j;

        for (j = this.slots.size(); i < j; ++i) {
            this.remoteSlots.set(i, ((Slot) this.slots.get(i)).getItem().copy());
        }

        this.remoteCarried = this.getCarried().copy();
        i = 0;

        for (j = this.dataSlots.size(); i < j; ++i) {
            this.remoteDataSlots.set(i, ((DataSlot) this.dataSlots.get(i)).get());
        }

        if (this.synchronizer != null) {
            this.synchronizer.sendInitialData(this, this.remoteSlots, this.remoteCarried, this.remoteDataSlots.toIntArray());
            this.synchronizer.sendOffHandSlotChange(); // Paper - update player's offhand since the offhand slot is not added to the slots for menus but can be changed by swapping from a menu slot
        }

    }

    // CraftBukkit start
    public void broadcastCarriedItem() {
        this.remoteCarried = this.getCarried().copy();
        if (this.synchronizer != null) {
            this.synchronizer.sendCarriedChange(this, this.remoteCarried);
        }
    }
    // CraftBukkit end

    public void removeSlotListener(ContainerListener listener) {
        this.containerListeners.remove(listener);
    }

    public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> nonnulllist = NonNullList.create();
        Iterator iterator = this.slots.iterator();

        while (iterator.hasNext()) {
            Slot slot = (Slot) iterator.next();

            nonnulllist.add(slot.getItem());
        }

        return nonnulllist;
    }

    public void broadcastChanges() {
        int i;

        for (i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            Objects.requireNonNull(itemstack);
            Supplier<ItemStack> supplier = Suppliers.memoize(itemstack::copy);

            this.triggerSlotListeners(i, itemstack, supplier);
            this.synchronizeSlotToRemote(i, itemstack, supplier);
        }

        this.synchronizeCarriedToRemote();

        for (i = 0; i < this.dataSlots.size(); ++i) {
            DataSlot containerproperty = (DataSlot) this.dataSlots.get(i);
            int j = containerproperty.get();

            if (containerproperty.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(i, j);
            }

            this.synchronizeDataSlotToRemote(i, j);
        }

    }

    public void broadcastFullState() {
        int i;

        for (i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            Objects.requireNonNull(itemstack);
            this.triggerSlotListeners(i, itemstack, itemstack::copy);
        }

        for (i = 0; i < this.dataSlots.size(); ++i) {
            DataSlot containerproperty = (DataSlot) this.dataSlots.get(i);

            if (containerproperty.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(i, containerproperty.get());
            }
        }

        this.sendAllDataToRemote();
    }

    private void updateDataSlotListeners(int index, int value) {
        Iterator iterator = this.containerListeners.iterator();

        while (iterator.hasNext()) {
            ContainerListener icrafting = (ContainerListener) iterator.next();

            icrafting.dataChanged(this, index, value);
        }

    }

    private void triggerSlotListeners(int slot, ItemStack stack, Supplier<ItemStack> copySupplier) {
        ItemStack itemstack1 = (ItemStack) this.lastSlots.get(slot);

        if (!ItemStack.matches(itemstack1, stack)) {
            ItemStack itemstack2 = (ItemStack) copySupplier.get();

            this.lastSlots.set(slot, itemstack2);
            Iterator iterator = this.containerListeners.iterator();

            while (iterator.hasNext()) {
                ContainerListener icrafting = (ContainerListener) iterator.next();

                icrafting.slotChanged(this, slot, itemstack1, itemstack2); // Paper
            }
        }

    }

    private void synchronizeSlotToRemote(int slot, ItemStack stack, Supplier<ItemStack> copySupplier) {
        if (!this.suppressRemoteUpdates) {
            ItemStack itemstack1 = (ItemStack) this.remoteSlots.get(slot);

            if (!ItemStack.matches(itemstack1, stack)) {
                ItemStack itemstack2 = (ItemStack) copySupplier.get();

                this.remoteSlots.set(slot, itemstack2);
                if (this.synchronizer != null) {
                    this.synchronizer.sendSlotChange(this, slot, itemstack2);
                }
            }

        }
    }

    private void synchronizeDataSlotToRemote(int id, int value) {
        if (!this.suppressRemoteUpdates) {
            int k = this.remoteDataSlots.getInt(id);

            if (k != value) {
                this.remoteDataSlots.set(id, value);
                if (this.synchronizer != null) {
                    this.synchronizer.sendDataChange(this, id, value);
                }
            }

        }
    }

    private void synchronizeCarriedToRemote() {
        if (!this.suppressRemoteUpdates) {
            if (!ItemStack.matches(this.getCarried(), this.remoteCarried)) {
                this.remoteCarried = this.getCarried().copy();
                if (this.synchronizer != null) {
                    this.synchronizer.sendCarriedChange(this, this.remoteCarried);
                }
            }

        }
    }

    public void setRemoteSlot(int slot, ItemStack stack) {
        this.remoteSlots.set(slot, stack.copy());
    }

    public void setRemoteSlotNoCopy(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.remoteSlots.size()) {
            this.remoteSlots.set(slot, stack);
        } else {
            AbstractContainerMenu.LOGGER.debug("Incorrect slot index: {} available slots: {}", slot, this.remoteSlots.size());
        }
    }

    public void setRemoteCarried(ItemStack stack) {
        this.remoteCarried = stack.copy();
    }

    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    public Slot getSlot(int index) {
        return (Slot) this.slots.get(index);
    }

    public abstract ItemStack quickMoveStack(Player player, int slot);

    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        try {
            this.doClick(slotIndex, button, actionType, player);
        } catch (Exception exception) {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Container click");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Click info");

            crashreportsystemdetails.setDetail("Menu Type", () -> {
                return this.menuType != null ? BuiltInRegistries.MENU.getKey(this.menuType).toString() : "<no type>";
            });
            crashreportsystemdetails.setDetail("Menu Class", () -> {
                return this.getClass().getCanonicalName();
            });
            crashreportsystemdetails.setDetail("Slot Count", (Object) this.slots.size());
            crashreportsystemdetails.setDetail("Slot", (Object) slotIndex);
            crashreportsystemdetails.setDetail("Button", (Object) button);
            crashreportsystemdetails.setDetail("Type", (Object) actionType);
            throw new ReportedException(crashreport);
        }
    }

    private void doClick(int slotIndex, int button, ClickType actionType, Player player) {
        Inventory playerinventory = player.getInventory();
        Slot slot;
        ItemStack itemstack;
        int k;
        ItemStack itemstack1;
        int l;

        if (actionType == ClickType.QUICK_CRAFT) {
            int i1 = this.quickcraftStatus;

            this.quickcraftStatus = AbstractContainerMenu.getQuickcraftHeader(button);
            if ((i1 != 1 || this.quickcraftStatus != 2) && i1 != this.quickcraftStatus) {
                this.resetQuickCraft();
            } else if (this.getCarried().isEmpty()) {
                this.resetQuickCraft();
            } else if (this.quickcraftStatus == 0) {
                this.quickcraftType = AbstractContainerMenu.getQuickcraftType(button);
                if (AbstractContainerMenu.isValidQuickcraftType(this.quickcraftType, player)) {
                    this.quickcraftStatus = 1;
                    this.quickcraftSlots.clear();
                } else {
                    this.resetQuickCraft();
                }
            } else if (this.quickcraftStatus == 1) {
                if (slotIndex < 0) return; // Paper
                slot = (Slot) this.slots.get(slotIndex);
                itemstack = this.getCarried();
                if (AbstractContainerMenu.canItemQuickReplace(slot, itemstack, true) && slot.mayPlace(itemstack) && (this.quickcraftType == 2 || itemstack.getCount() > this.quickcraftSlots.size()) && this.canDragTo(slot)) {
                    this.quickcraftSlots.add(slot);
                }
            } else if (this.quickcraftStatus == 2) {
                if (!this.quickcraftSlots.isEmpty()) {
                    if (false && this.quickcraftSlots.size() == 1) { // CraftBukkit - treat everything as a drag since we are unable to easily call InventoryClickEvent instead
                        k = ((Slot) this.quickcraftSlots.iterator().next()).index;
                        this.resetQuickCraft();
                        this.doClick(k, this.quickcraftType, ClickType.PICKUP, player);
                        return;
                    }

                    itemstack1 = this.getCarried().copy();
                    if (itemstack1.isEmpty()) {
                        this.resetQuickCraft();
                        return;
                    }

                    l = this.getCarried().getCount();
                    Iterator iterator = this.quickcraftSlots.iterator();

                    Map<Integer, ItemStack> draggedSlots = new HashMap<Integer, ItemStack>(); // CraftBukkit - Store slots from drag in map (raw slot id -> new stack)
                    while (iterator.hasNext()) {
                        Slot slot1 = (Slot) iterator.next();
                        ItemStack itemstack2 = this.getCarried();

                        if (slot1 != null && AbstractContainerMenu.canItemQuickReplace(slot1, itemstack2, true) && slot1.mayPlace(itemstack2) && (this.quickcraftType == 2 || itemstack2.getCount() >= this.quickcraftSlots.size()) && this.canDragTo(slot1)) {
                            int j1 = slot1.hasItem() ? slot1.getItem().getCount() : 0;
                            int k1 = Math.min(itemstack1.getMaxStackSize(), slot1.getMaxStackSize(itemstack1));
                            int l1 = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(this.quickcraftSlots, this.quickcraftType, itemstack1) + j1, k1);

                            l -= l1 - j1;
                            // slot1.setByPlayer(itemstack1.copyWithCount(l1));
                            draggedSlots.put(slot1.index, itemstack1.copyWithCount(l1)); // CraftBukkit - Put in map instead of setting
                        }
                    }

                    // CraftBukkit start - InventoryDragEvent
                    InventoryView view = this.getBukkitView();
                    org.bukkit.inventory.ItemStack newcursor = CraftItemStack.asCraftMirror(itemstack1);
                    newcursor.setAmount(l);
                    Map<Integer, org.bukkit.inventory.ItemStack> eventmap = new HashMap<Integer, org.bukkit.inventory.ItemStack>();
                    for (Map.Entry<Integer, ItemStack> ditem : draggedSlots.entrySet()) {
                        eventmap.put(ditem.getKey(), CraftItemStack.asBukkitCopy(ditem.getValue()));
                    }

                    // It's essential that we set the cursor to the new value here to prevent item duplication if a plugin closes the inventory.
                    ItemStack oldCursor = this.getCarried();
                    this.setCarried(CraftItemStack.asNMSCopy(newcursor));

                    InventoryDragEvent event = new InventoryDragEvent(view, (newcursor.getType() != org.bukkit.Material.AIR ? newcursor : null), CraftItemStack.asBukkitCopy(oldCursor), this.quickcraftType == 1, eventmap);
                    player.level().getCraftServer().getPluginManager().callEvent(event);

                    // Whether or not a change was made to the inventory that requires an update.
                    boolean needsUpdate = event.getResult() != Result.DEFAULT;

                    if (event.getResult() != Result.DENY) {
                        for (Map.Entry<Integer, ItemStack> dslot : draggedSlots.entrySet()) {
                            view.setItem(dslot.getKey(), CraftItemStack.asBukkitCopy(dslot.getValue()));
                        }
                        // The only time the carried item will be set to null is if the inventory is closed by the server.
                        // If the inventory is closed by the server, then the cursor items are dropped.  This is why we change the cursor early.
                        if (this.getCarried() != null) {
                            this.setCarried(CraftItemStack.asNMSCopy(event.getCursor()));
                            needsUpdate = true;
                        }
                    } else {
                        this.setCarried(oldCursor);
                    }

                    if (needsUpdate && player instanceof ServerPlayer) {
                        this.sendAllDataToRemote();
                    }
                    // CraftBukkit end
                }

                this.resetQuickCraft();
            } else {
                this.resetQuickCraft();
            }
        } else if (this.quickcraftStatus != 0) {
            this.resetQuickCraft();
        } else {
            int i2;

            if ((actionType == ClickType.PICKUP || actionType == ClickType.QUICK_MOVE) && (button == 0 || button == 1)) {
                ClickAction clickaction = button == 0 ? ClickAction.PRIMARY : ClickAction.SECONDARY;

                if (slotIndex == -999) {
                    if (!this.getCarried().isEmpty()) {
                        if (clickaction == ClickAction.PRIMARY) {
                            // CraftBukkit start
                            ItemStack carried = this.getCarried();
                            this.setCarried(ItemStack.EMPTY);
                            player.drop(carried, true);
                            // CraftBukkit start
                        } else {
                            player.drop(this.getCarried().split(1), true);
                        }
                    }
                } else if (actionType == ClickType.QUICK_MOVE) {
                    if (slotIndex < 0) {
                        return;
                    }

                    slot = (Slot) this.slots.get(slotIndex);
                    if (!slot.mayPickup(player)) {
                        return;
                    }

                    for (itemstack = this.quickMoveStack(player, slotIndex); !itemstack.isEmpty() && ItemStack.isSameItem(slot.getItem(), itemstack); itemstack = this.quickMoveStack(player, slotIndex)) {
                        ;
                    }
                } else {
                    if (slotIndex < 0) {
                        return;
                    }

                    slot = (Slot) this.slots.get(slotIndex);
                    itemstack = slot.getItem();
                    ItemStack itemstack3 = this.getCarried();

                    player.updateTutorialInventoryAction(itemstack3, slot.getItem(), clickaction);
                    if (!this.tryItemClickBehaviourOverride(player, clickaction, slot, itemstack, itemstack3)) {
                        if (itemstack.isEmpty()) {
                            if (!itemstack3.isEmpty()) {
                                i2 = clickaction == ClickAction.PRIMARY ? itemstack3.getCount() : 1;
                                this.setCarried(slot.safeInsert(itemstack3, i2));
                            }
                        } else if (slot.mayPickup(player)) {
                            if (itemstack3.isEmpty()) {
                                i2 = clickaction == ClickAction.PRIMARY ? itemstack.getCount() : (itemstack.getCount() + 1) / 2;
                                Optional<ItemStack> optional = slot.tryRemove(i2, Integer.MAX_VALUE, player);

                                optional.ifPresent((itemstack4) -> {
                                    this.setCarried(itemstack4);
                                    slot.onTake(player, itemstack4);
                                });
                            } else if (slot.mayPlace(itemstack3)) {
                                if (ItemStack.isSameItemSameTags(itemstack, itemstack3)) {
                                    i2 = clickaction == ClickAction.PRIMARY ? itemstack3.getCount() : 1;
                                    this.setCarried(slot.safeInsert(itemstack3, i2));
                                } else if (itemstack3.getCount() <= slot.getMaxStackSize(itemstack3)) {
                                    this.setCarried(itemstack);
                                    slot.setByPlayer(itemstack3);
                                }
                            } else if (ItemStack.isSameItemSameTags(itemstack, itemstack3)) {
                                Optional<ItemStack> optional1 = slot.tryRemove(itemstack.getCount(), itemstack3.getMaxStackSize() - itemstack3.getCount(), player);

                                optional1.ifPresent((itemstack4) -> {
                                    itemstack3.grow(itemstack4.getCount());
                                    slot.onTake(player, itemstack4);
                                });
                            }
                        }
                    }

                    slot.setChanged();
                    // CraftBukkit start - Make sure the client has the right slot contents
                    if (player instanceof ServerPlayer && slot.getMaxStackSize() != 64) {
                        ((ServerPlayer) player).connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), slot.index, slot.getItem()));
                        // Updating a crafting inventory makes the client reset the result slot, have to send it again
                        if (this.getBukkitView().getType() == InventoryType.WORKBENCH || this.getBukkitView().getType() == InventoryType.CRAFTING) {
                            ((ServerPlayer) player).connection.send(new ClientboundContainerSetSlotPacket(this.containerId, this.incrementStateId(), 0, this.getSlot(0).getItem()));
                        }
                    }
                    // CraftBukkit end
                }
            } else {
                Slot slot2;
                int j2;

                if (actionType == ClickType.SWAP) {
                    if (slotIndex < 0 || button < 0) return; // Paper
                    slot2 = (Slot) this.slots.get(slotIndex);
                    itemstack1 = playerinventory.getItem(button);
                    itemstack = slot2.getItem();
                    if (!itemstack1.isEmpty() || !itemstack.isEmpty()) {
                        if (itemstack1.isEmpty()) {
                            if (slot2.mayPickup(player)) {
                                playerinventory.setItem(button, itemstack);
                                slot2.onSwapCraft(itemstack.getCount());
                                slot2.setByPlayer(ItemStack.EMPTY);
                                slot2.onTake(player, itemstack);
                            }
                        } else if (itemstack.isEmpty()) {
                            if (slot2.mayPlace(itemstack1)) {
                                j2 = slot2.getMaxStackSize(itemstack1);
                                if (itemstack1.getCount() > j2) {
                                    slot2.setByPlayer(itemstack1.split(j2));
                                } else {
                                    playerinventory.setItem(button, ItemStack.EMPTY);
                                    slot2.setByPlayer(itemstack1);
                                }
                            }
                        } else if (slot2.mayPickup(player) && slot2.mayPlace(itemstack1)) {
                            j2 = slot2.getMaxStackSize(itemstack1);
                            if (itemstack1.getCount() > j2) {
                                slot2.setByPlayer(itemstack1.split(j2));
                                slot2.onTake(player, itemstack);
                                if (!playerinventory.add(itemstack)) {
                                    player.drop(itemstack, true);
                                }
                            } else {
                                playerinventory.setItem(button, itemstack);
                                slot2.setByPlayer(itemstack1);
                                slot2.onTake(player, itemstack);
                            }
                        }
                    }
                } else if (actionType == ClickType.CLONE && player.getAbilities().instabuild && this.getCarried().isEmpty() && slotIndex >= 0) {
                    slot2 = (Slot) this.slots.get(slotIndex);
                    if (slot2.hasItem()) {
                        itemstack1 = slot2.getItem();
                        this.setCarried(itemstack1.copyWithCount(itemstack1.getMaxStackSize()));
                    }
                } else if (actionType == ClickType.THROW && this.getCarried().isEmpty() && slotIndex >= 0) {
                    slot2 = (Slot) this.slots.get(slotIndex);
                    k = button == 0 ? 1 : slot2.getItem().getCount();
                    itemstack = slot2.safeTake(k, Integer.MAX_VALUE, player);
                    player.drop(itemstack, true);
                } else if (actionType == ClickType.PICKUP_ALL && slotIndex >= 0) {
                    slot2 = (Slot) this.slots.get(slotIndex);
                    itemstack1 = this.getCarried();
                    if (!itemstack1.isEmpty() && (!slot2.hasItem() || !slot2.mayPickup(player))) {
                        l = button == 0 ? 0 : this.slots.size() - 1;
                        j2 = button == 0 ? 1 : -1;

                        for (i2 = 0; i2 < 2; ++i2) {
                            for (int k2 = l; k2 >= 0 && k2 < this.slots.size() && itemstack1.getCount() < itemstack1.getMaxStackSize(); k2 += j2) {
                                Slot slot3 = (Slot) this.slots.get(k2);

                                if (slot3.hasItem() && AbstractContainerMenu.canItemQuickReplace(slot3, itemstack1, true) && slot3.mayPickup(player) && this.canTakeItemForPickAll(itemstack1, slot3)) {
                                    ItemStack itemstack4 = slot3.getItem();

                                    if (i2 != 0 || itemstack4.getCount() != itemstack4.getMaxStackSize()) {
                                        ItemStack itemstack5 = slot3.safeTake(itemstack4.getCount(), itemstack1.getMaxStackSize() - itemstack1.getCount(), player);

                                        itemstack1.grow(itemstack5.getCount());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private boolean tryItemClickBehaviourOverride(Player player, ClickAction clickType, Slot slot, ItemStack stack, ItemStack cursorStack) {
        FeatureFlagSet featureflagset = player.level().enabledFeatures();

        return cursorStack.isItemEnabled(featureflagset) && cursorStack.overrideStackedOnOther(slot, clickType, player) ? true : stack.isItemEnabled(featureflagset) && stack.overrideOtherStackedOnMe(cursorStack, slot, clickType, player, this.createCarriedSlotAccess());
    }

    private SlotAccess createCarriedSlotAccess() {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return AbstractContainerMenu.this.getCarried();
            }

            @Override
            public boolean set(ItemStack stack) {
                AbstractContainerMenu.this.setCarried(stack);
                return true;
            }
        };
    }

    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return true;
    }

    public void removed(Player player) {
        if (player instanceof ServerPlayer) {
            ItemStack itemstack = this.getCarried();

            if (!itemstack.isEmpty()) {
                this.setCarried(ItemStack.EMPTY); // CraftBukkit - SPIGOT-4556 - from below
                if (player.isAlive() && !((ServerPlayer) player).hasDisconnected()) {
                    player.getInventory().placeItemBackInInventory(itemstack);
                } else {
                    player.drop(itemstack, false);
                }

                // this.setCarried(ItemStack.EMPTY); // CraftBukkit - moved up
            }
        }

    }

    protected void clearContainer(Player player, Container inventory) {
        int i;

        if (player.isAlive() && (!(player instanceof ServerPlayer) || !((ServerPlayer) player).hasDisconnected())) {
            for (i = 0; i < inventory.getContainerSize(); ++i) {
                Inventory playerinventory = player.getInventory();

                if (playerinventory.player instanceof ServerPlayer) {
                    playerinventory.placeItemBackInInventory(inventory.removeItemNoUpdate(i));
                }
            }

        } else {
            for (i = 0; i < inventory.getContainerSize(); ++i) {
                player.drop(inventory.removeItemNoUpdate(i), false);
            }

        }
    }

    public void slotsChanged(Container inventory) {
        this.broadcastChanges();
    }

    public void setItem(int slot, int revision, ItemStack stack) {
        this.getSlot(slot).set(stack);
        this.stateId = revision;
    }

    public void initializeContents(int revision, List<ItemStack> stacks, ItemStack cursorStack) {
        for (int j = 0; j < stacks.size(); ++j) {
            this.getSlot(j).set((ItemStack) stacks.get(j));
        }

        this.carried = cursorStack;
        this.stateId = revision;
    }

    public void setData(int id, int value) {
        ((DataSlot) this.dataSlots.get(id)).set(value);
    }

    public abstract boolean stillValid(Player player);

    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean fromLast) {
        // Paper start
        return this.moveItemStackTo(stack, startIndex, endIndex, fromLast, false);
    }
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean fromLast, boolean isCheck) {
        if (isCheck) {
            stack = stack.copy();
        }
        // Paper end
        boolean flag1 = false;
        int k = startIndex;

        if (fromLast) {
            k = endIndex - 1;
        }

        Slot slot;
        ItemStack itemstack1;

        if (stack.isStackable()) {
            while (!stack.isEmpty()) {
                if (fromLast) {
                    if (k < startIndex) {
                        break;
                    }
                } else if (k >= endIndex) {
                    break;
                }

                slot = (Slot) this.slots.get(k);
                itemstack1 = slot.getItem();
                // Paper start - clone if only a check
                if (isCheck) {
                    itemstack1 = itemstack1.copy();
                }
                // Paper end
                if (!itemstack1.isEmpty() && ItemStack.isSameItemSameTags(stack, itemstack1)) {
                    int l = itemstack1.getCount() + stack.getCount();

                    if (l <= stack.getMaxStackSize()) {
                        stack.setCount(0);
                        itemstack1.setCount(l);
                        if (!isCheck) { // Paper - dont update if only a check
                        slot.setChanged();
                        } // Paper
                        flag1 = true;
                    } else if (itemstack1.getCount() < stack.getMaxStackSize()) {
                        stack.shrink(stack.getMaxStackSize() - itemstack1.getCount());
                        itemstack1.setCount(stack.getMaxStackSize());
                        if (!isCheck) { // Paper - dont update if only a check
                        slot.setChanged();
                        } // Paper
                        flag1 = true;
                    }
                }

                if (fromLast) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        if (!stack.isEmpty()) {
            if (fromLast) {
                k = endIndex - 1;
            } else {
                k = startIndex;
            }

            while (true) {
                if (fromLast) {
                    if (k < startIndex) {
                        break;
                    }
                } else if (k >= endIndex) {
                    break;
                }

                slot = (Slot) this.slots.get(k);
                itemstack1 = slot.getItem();
                // Paper start - clone if only a check
                if (isCheck) {
                    itemstack1 = itemstack1.copy();
                }
                // Paper end
                if (itemstack1.isEmpty() && slot.mayPlace(stack)) {
                    if (stack.getCount() > slot.getMaxStackSize()) {
                        // Paper start - dont set slot if only check
                        if (isCheck) {
                            stack.shrink(slot.getMaxStackSize());
                        } else {
                        // Paper end
                        slot.setByPlayer(stack.split(slot.getMaxStackSize()));
                        } // Paper
                    } else {
                        // Paper start - dont set slot if only check
                        if (isCheck) {
                            stack.shrink(stack.getCount());
                        } else {
                        // Paper end
                        slot.setByPlayer(stack.split(stack.getCount()));
                        } // Paper
                    }

                    if (!isCheck) { // Paper - dont update if only check
                    slot.setChanged();
                    } // Paper
                    flag1 = true;
                    break;
                }

                if (fromLast) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        return flag1;
    }

    public static int getQuickcraftType(int quickCraftData) {
        return quickCraftData >> 2 & 3;
    }

    public static int getQuickcraftHeader(int quickCraftData) {
        return quickCraftData & 3;
    }

    public static int getQuickcraftMask(int quickCraftStage, int buttonId) {
        return quickCraftStage & 3 | (buttonId & 3) << 2;
    }

    public static boolean isValidQuickcraftType(int stage, Player player) {
        return stage == 0 ? true : (stage == 1 ? true : stage == 2 && player.getAbilities().instabuild);
    }

    protected void resetQuickCraft() {
        this.quickcraftStatus = 0;
        this.quickcraftSlots.clear();
    }

    public static boolean canItemQuickReplace(@Nullable Slot slot, ItemStack stack, boolean allowOverflow) {
        boolean flag1 = slot == null || !slot.hasItem();

        return !flag1 && ItemStack.isSameItemSameTags(stack, slot.getItem()) ? slot.getItem().getCount() + (allowOverflow ? 0 : stack.getCount()) <= stack.getMaxStackSize() : flag1;
    }

    public static int getQuickCraftPlaceCount(Set<Slot> slots, int mode, ItemStack stack) {
        int j;

        switch (mode) {
            case 0:
                j = Mth.floor((float) stack.getCount() / (float) slots.size());
                break;
            case 1:
                j = 1;
                break;
            case 2:
                j = stack.getItem().getMaxStackSize();
                break;
            default:
                j = stack.getCount();
        }

        return j;
    }

    public boolean canDragTo(Slot slot) {
        return true;
    }

    public static int getRedstoneSignalFromBlockEntity(@Nullable BlockEntity entity) {
        return entity instanceof Container ? AbstractContainerMenu.getRedstoneSignalFromContainer((Container) entity) : 0;
    }

    public static int getRedstoneSignalFromContainer(@Nullable Container inventory) {
        if (inventory == null) {
            return 0;
        } else {
            float f = 0.0F;

            for (int i = 0; i < inventory.getContainerSize(); ++i) {
                ItemStack itemstack = inventory.getItem(i);

                if (!itemstack.isEmpty()) {
                    f += (float) itemstack.getCount() / (float) Math.min(inventory.getMaxStackSize(), itemstack.getMaxStackSize());
                }
            }

            f /= (float) inventory.getContainerSize();
            return Mth.lerpDiscrete(f, 0, 15);
        }
    }

    public void setCarried(ItemStack stack) {
        this.carried = stack;
    }

    public ItemStack getCarried() {
        // CraftBukkit start
        if (this.carried.isEmpty()) {
            this.setCarried(ItemStack.EMPTY);
        }
        // CraftBukkit end
        return this.carried;
    }

    public void suppressRemoteUpdates() {
        this.suppressRemoteUpdates = true;
    }

    public void resumeRemoteUpdates() {
        this.suppressRemoteUpdates = false;
    }

    public void transferState(AbstractContainerMenu handler) {
        Table<Container, Integer, Integer> table = HashBasedTable.create();

        Slot slot;
        int i;

        for (i = 0; i < handler.slots.size(); ++i) {
            slot = (Slot) handler.slots.get(i);
            table.put(slot.container, slot.getContainerSlot(), i);
        }

        for (i = 0; i < this.slots.size(); ++i) {
            slot = (Slot) this.slots.get(i);
            Integer integer = (Integer) table.get(slot.container, slot.getContainerSlot());

            if (integer != null) {
                this.lastSlots.set(i, (ItemStack) handler.lastSlots.get(integer));
                this.remoteSlots.set(i, (ItemStack) handler.remoteSlots.get(integer));
            }
        }

    }

    public OptionalInt findSlot(Container inventory, int index) {
        for (int j = 0; j < this.slots.size(); ++j) {
            Slot slot = (Slot) this.slots.get(j);

            if (slot.container == inventory && index == slot.getContainerSlot()) {
                return OptionalInt.of(j);
            }
        }

        return OptionalInt.empty();
    }

    public int getStateId() {
        return this.stateId;
    }

    public int incrementStateId() {
        this.stateId = this.stateId + 1 & 32767;
        return this.stateId;
    }

    // Paper start - add missing BlockInventoryHolder to inventories
    // The reason this is a supplier, is that the createHolder method uses the bukkit InventoryView#getTopInventory to get the inventory in question
    // and that can't be obtained safely until the AbstractContainerMenu has been fully constructed. Using a supplier lazily
    // initializes the InventoryHolder safely.
    protected final Supplier<org.bukkit.inventory.BlockInventoryHolder> createBlockHolder(final ContainerLevelAccess context) {
        //noinspection ConstantValue
        Preconditions.checkArgument(context != null, "context was null");
        return () -> context.createBlockHolder(this);
    }
    // Paper end
}
