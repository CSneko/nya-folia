package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
// CraftBukkit end

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper {

    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    private NonNullList<ItemStack> items;
    private int cooldownTime;
    private long tickedGameTime;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

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

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    public HopperBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.HOPPER, pos, state);
        this.items = NonNullList.withSize(5, ItemStack.EMPTY);
        this.cooldownTime = -1;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbt)) {
            ContainerHelper.loadAllItems(nbt, this.items);
        }

        this.cooldownTime = nbt.getInt("TransferCooldown");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (!this.trySaveLootTable(nbt)) {
            ContainerHelper.saveAllItems(nbt, this.items);
        }

        nbt.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        this.unpackLootTable((Player) null);
        return ContainerHelper.removeItem(this.getItems(), slot, amount);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.unpackLootTable((Player) null);
        this.getItems().set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.hopper");
    }

    public static void pushItemsTick(Level world, BlockPos pos, BlockState state, HopperBlockEntity blockEntity) {
        --blockEntity.cooldownTime;
        blockEntity.tickedGameTime = world.getGameTime();
        if (!blockEntity.isOnCooldown()) {
            blockEntity.setCooldown(0);
            // Spigot start
            boolean result = HopperBlockEntity.tryMoveItems(world, pos, state, blockEntity, () -> {
                return HopperBlockEntity.suckInItems(world, blockEntity);
            });
            if (!result && blockEntity.level.spigotConfig.hopperCheck > 1) {
                blockEntity.setCooldown(blockEntity.level.spigotConfig.hopperCheck);
            }
            // Spigot end
        }

    }

    // Paper start - optimize hoppers
    private static final int HOPPER_EMPTY = 0;
    private static final int HOPPER_HAS_ITEMS = 1;
    private static final int HOPPER_IS_FULL = 2;

    private static int getFullState(final HopperBlockEntity tileEntity) {
        tileEntity.unpackLootTable(null);

        final List<ItemStack> hopperItems = tileEntity.getItems();

        boolean empty = true;
        boolean full = true;

        for (int i = 0, len = hopperItems.size(); i < len; ++i) {
            final ItemStack stack = hopperItems.get(i);
            if (stack.isEmpty()) {
                full = false;
                continue;
            }

            if (!full) {
                // can't be full
                return HOPPER_HAS_ITEMS;
            }

            empty = false;

            if (stack.getCount() != stack.getMaxStackSize()) {
                // can't be full or empty
                return HOPPER_HAS_ITEMS;
            }
        }

        return empty ? HOPPER_EMPTY : (full ? HOPPER_IS_FULL : HOPPER_HAS_ITEMS);
    }
    // Paper end - optimize hoppers

    private static boolean tryMoveItems(Level world, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, BooleanSupplier booleansupplier) {
        if (world.isClientSide) {
            return false;
        } else {
            if (!blockEntity.isOnCooldown() && (Boolean) state.getValue(HopperBlock.ENABLED)) {
                boolean flag = false;

                int fullState = getFullState(blockEntity); // Paper - optimize hoppers

                if (fullState != HOPPER_EMPTY) { // Paper - optimize hoppers
                    flag = HopperBlockEntity.ejectItems(world, pos, state, (Container) blockEntity, blockEntity); // CraftBukkit
                }

                if (fullState != HOPPER_IS_FULL || flag) { // Paper - optimize hoppers
                    flag |= booleansupplier.getAsBoolean();
                }

                if (flag) {
                    blockEntity.setCooldown(world.spigotConfig.hopperTransfer); // Spigot
                    setChanged(world, pos, state);
                    return true;
                }
            }

            return false;
        }
    }

    private boolean inventoryFull() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (!itemstack.isEmpty() && itemstack.getCount() == itemstack.getMaxStackSize());

        return false;
    }

    // Paper start - Optimize Hoppers
    // Folia - region threading - moved to RegionizedWorldData

    private static boolean hopperPush(final Level level, final Container destination, final Direction direction, final HopperBlockEntity hopper) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        worldData.skipPushModeEventFire = worldData.skipHopperEvents; // Folia - region threading
        boolean foundItem = false;
        for (int i = 0; i < hopper.getContainerSize(); ++i) {
            final ItemStack item = hopper.getItem(i);
            if (!item.isEmpty()) {
                foundItem = true;
                ItemStack origItemStack = item;
                ItemStack movedItem = origItemStack;

                final int originalItemCount = origItemStack.getCount();
                final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
                origItemStack.setCount(movedItemCount);

                // We only need to fire the event once to give protection plugins a chance to cancel this event
                // Because nothing uses getItem, every event call should end up the same result.
                if (!worldData.skipPushModeEventFire) { // Folia - region threading
                    movedItem = callPushMoveEvent(destination, movedItem, hopper);
                    if (movedItem == null) { // cancelled
                        origItemStack.setCount(originalItemCount);
                        return false;
                    }
                }

                final ItemStack remainingItem = addItem(hopper, destination, movedItem, direction);
                final int remainingItemCount = remainingItem.getCount();
                if (remainingItemCount != movedItemCount) {
                    origItemStack = origItemStack.copy(true);
                    origItemStack.setCount(originalItemCount);
                    if (!origItemStack.isEmpty()) {
                        origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
                    }
                    hopper.setItem(i, origItemStack);
                    destination.setChanged();
                    return true;
                }
                origItemStack.setCount(originalItemCount);
            }
        }
        if (foundItem && level.paperConfig().hopper.cooldownWhenFull) { // Inventory was full - cooldown
            hopper.setCooldown(level.spigotConfig.hopperTransfer);
        }
        return false;
    }

    private static boolean hopperPull(final Level level, final Hopper hopper, final Container container, ItemStack origItemStack, final int i) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        ItemStack movedItem = origItemStack;
        final int originalItemCount = origItemStack.getCount();
        final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
        container.setChanged(); // original logic always marks source inv as changed even if no move happens.
        movedItem.setCount(movedItemCount);

        if (!worldData.skipPullModeEventFire) { // Folia - region threading
            movedItem = callPullMoveEvent(hopper, container, movedItem);
            if (movedItem == null) { // cancelled
                origItemStack.setCount(originalItemCount);
                // Drastically improve performance by returning true.
                // No plugin could of relied on the behavior of false as the other call
                // site for IMIE did not exhibit the same behavior
                return true;
            }
        }

        final ItemStack remainingItem = addItem(container, hopper, movedItem, null);
        final int remainingItemCount = remainingItem.getCount();
        if (remainingItemCount != movedItemCount) {
            origItemStack = origItemStack.copy(true);
            origItemStack.setCount(originalItemCount);
            if (!origItemStack.isEmpty()) {
                origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
            }

            IGNORE_TILE_UPDATES.set(true); // Folia - region threading
            container.setItem(i, origItemStack);
            IGNORE_TILE_UPDATES.set(false); // Folia - region threading
            container.setChanged();
            return true;
        }
        origItemStack.setCount(originalItemCount);

        if (level.paperConfig().hopper.cooldownWhenFull) {
            cooldownHopper(hopper);
        }

        return false;
    }

    @Nullable
    private static ItemStack callPushMoveEvent(Container iinventory, ItemStack itemstack, HopperBlockEntity hopper) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        final Inventory destinationInventory = getInventory(iinventory);
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(hopper.getOwner(false).getInventory(),
            CraftItemStack.asCraftMirror(itemstack), destinationInventory, true);
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            worldData.skipPushModeEventFire = true; // Folia - region threading
        }
        if (!result) {
            cooldownHopper(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemstack;
        }
    }

    @Nullable
    private static ItemStack callPullMoveEvent(final Hopper hopper, final Container container, final ItemStack itemstack) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        final Inventory sourceInventory = getInventory(container);
        final Inventory destination = getInventory(hopper);

        // Mirror is safe as no plugins ever use this item
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(sourceInventory, CraftItemStack.asCraftMirror(itemstack), destination, false);
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            worldData.skipPullModeEventFire = true; // Folia - region threading
        }
        if (!result) {
            cooldownHopper(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemstack;
        }
    }

    private static Inventory getInventory(final Container container) {
        final Inventory sourceInventory;
        if (container instanceof CompoundContainer compoundContainer) {
            // Have to special-case large chests as they work oddly
            sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
        } else if (container instanceof BlockEntity blockEntity) {
            sourceInventory = blockEntity.getOwner(false).getInventory();
        } else if (container.getOwner() != null) {
            sourceInventory = container.getOwner().getInventory();
        } else {
            sourceInventory = new CraftInventory(container);
        }
        return sourceInventory;
    }

    private static void cooldownHopper(final Hopper hopper) {
        if (hopper instanceof HopperBlockEntity blockEntity && blockEntity.getLevel() != null) {
            blockEntity.setCooldown(blockEntity.getLevel().spigotConfig.hopperTransfer);
        }
    }

    private static boolean allMatch(Container iinventory, Direction enumdirection, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (iinventory instanceof WorldlyContainer) {
            for (int i : ((WorldlyContainer) iinventory).getSlotsForFace(enumdirection)) {
                if (!test.test(iinventory.getItem(i), i)) {
                    return false;
                }
            }
        } else {
            int size = iinventory.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (!test.test(iinventory.getItem(i), i)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean anyMatch(Container iinventory, Direction enumdirection, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (iinventory instanceof WorldlyContainer) {
            for (int i : ((WorldlyContainer) iinventory).getSlotsForFace(enumdirection)) {
                if (test.test(iinventory.getItem(i), i)) {
                    return true;
                }
            }
        } else {
            int size = iinventory.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (test.test(iinventory.getItem(i), i)) {
                    return true;
                }
            }
        }
        return true;
    }
    private static final java.util.function.BiPredicate<ItemStack, Integer> STACK_SIZE_TEST = (itemstack, i) -> itemstack.getCount() >= itemstack.getMaxStackSize();
    private static final java.util.function.BiPredicate<ItemStack, Integer> IS_EMPTY_TEST = (itemstack, i) -> itemstack.isEmpty();
    // Paper end

    private static boolean ejectItems(Level world, BlockPos blockposition, BlockState iblockdata, Container iinventory, HopperBlockEntity hopper) { // CraftBukkit
        Container iinventory1 = HopperBlockEntity.getAttachedContainer(world, blockposition, iblockdata);

        if (iinventory1 == null) {
            return false;
        } else {
            Direction enumdirection = ((Direction) iblockdata.getValue(HopperBlock.FACING)).getOpposite();

            if (HopperBlockEntity.isFullContainer(iinventory1, enumdirection)) {
                return false;
            } else {
                // Paper start - replace logic; MAKE SURE TO CHECK FOR DIFFS ON UPDATES
                return hopperPush(world, iinventory1, enumdirection, hopper);
                // for (int i = 0; i < iinventory.getContainerSize(); ++i) {
                //     if (!iinventory.getItem(i).isEmpty()) {
                //         ItemStack itemstack = iinventory.getItem(i).copy();
                //         // ItemStack itemstack1 = addItem(iinventory, iinventory1, iinventory.removeItem(i, 1), enumdirection);

                //         // CraftBukkit start - Call event when pushing items into other inventories
                //         CraftItemStack oitemstack = CraftItemStack.asCraftMirror(iinventory.removeItem(i, world.spigotConfig.hopperAmount)); // Spigot

                //         Inventory destinationInventory;
                //        // Have to special case large chests as they work oddly
                //         if (iinventory1 instanceof CompoundContainer) {
                //             destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory1);
                //         } else if (iinventory1.getOwner() != null) {
                //             destinationInventory = iinventory1.getOwner().getInventory();
                //         } else {
                //             destinationInventory = new CraftInventory(iinventory);
                //         }

                //         InventoryMoveItemEvent event = new InventoryMoveItemEvent(iinventory.getOwner().getInventory(), oitemstack.clone(), destinationInventory, true);
                //         world.getCraftServer().getPluginManager().callEvent(event);
                //         if (event.isCancelled()) {
                //             hopper.setItem(i, itemstack);
                //             hopper.setCooldown(world.spigotConfig.hopperTransfer); // Spigot
                //             return false;
                //         }
                //         int origCount = event.getItem().getAmount(); // Spigot
                //         ItemStack itemstack1 = HopperBlockEntity.addItem(iinventory, iinventory1, CraftItemStack.asNMSCopy(event.getItem()), enumdirection);
                        // CraftBukkit end

                //         if (itemstack1.isEmpty()) {
                //             iinventory1.setChanged();
                //             return true;
                //         }

                //         itemstack.shrink(origCount - itemstack1.getCount()); // Spigot
                //         iinventory.setItem(i, itemstack);
                //     }
                // }

                // return false;
                // Paper end
            }
        }
    }

    private static IntStream getSlots(Container inventory, Direction side) {
        return inventory instanceof WorldlyContainer ? IntStream.of(((WorldlyContainer) inventory).getSlotsForFace(side)) : IntStream.range(0, inventory.getContainerSize());
    }

    private static boolean isFullContainer(Container inventory, Direction direction) {
        // Paper start - optimize hoppers
        if (inventory instanceof WorldlyContainer worldlyContainer) {
            for (final int slot : worldlyContainer.getSlotsForFace(direction)) {
                final ItemStack stack = inventory.getItem(slot);
                if (stack.getCount() < stack.getMaxStackSize()) {
                    return false;
                }
            }
            return true;
        } else {
            for (int slot = 0, max = inventory.getContainerSize(); slot < max; ++slot) {
                final ItemStack stack = inventory.getItem(slot);
                if (stack.getCount() < stack.getMaxStackSize()) {
                    return false;
                }
            }
            return true;
        }
        // Paper end - optimize hoppers
    }

    private static boolean isEmptyContainer(Container inv, Direction facing) {
        return allMatch(inv, facing, IS_EMPTY_TEST);
    }

    public static boolean suckInItems(Level world, Hopper hopper) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        Container iinventory = HopperBlockEntity.getSourceContainer(world, hopper);

        if (iinventory != null) {
            Direction enumdirection = Direction.DOWN;

            // Paper start - optimize hoppers and remove streams
            worldData.skipPullModeEventFire = worldData.skipHopperEvents; // Folia - region threading
            // merge container isEmpty check and move logic into one loop
            if (iinventory instanceof WorldlyContainer worldlyContainer) {
                for (final int slot : worldlyContainer.getSlotsForFace(enumdirection)) {
                    ItemStack item = worldlyContainer.getItem(slot);
                    if (item.isEmpty() || !canTakeItemFromContainer(hopper, iinventory, item, slot, enumdirection)) {
                        continue;
                    }
                    if (hopperPull(world, hopper, iinventory, item, slot)) {
                        return true;
                    }
                }
                return false;
            } else {
                for (int slot = 0, max = iinventory.getContainerSize(); slot < max; ++slot) {
                    ItemStack item = iinventory.getItem(slot);
                    if (item.isEmpty() || !canTakeItemFromContainer(hopper, iinventory, item, slot, enumdirection)) {
                        continue;
                    }
                    if (hopperPull(world, hopper, iinventory, item, slot)) {
                        return true;
                    }
                }
                return false;
            }
            // Paper end
        } else {
            Iterator iterator = HopperBlockEntity.getItemsAtAndAbove(world, hopper).iterator();

            ItemEntity entityitem;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                entityitem = (ItemEntity) iterator.next();
            } while (!HopperBlockEntity.addItem(hopper, entityitem));

            return true;
        }
    }

    @io.papermc.paper.annotation.DoNotUse // Paper - method unused as logic is inlined above
    private static boolean a(Hopper ihopper, Container iinventory, int i, Direction enumdirection, Level world) { // Spigot
        ItemStack itemstack = iinventory.getItem(i);

        // Paper start - replace pull logic; MAKE SURE TO CHECK FOR DIFFS WHEN UPDATING
        if (!itemstack.isEmpty() && HopperBlockEntity.canTakeItemFromContainer(ihopper, iinventory, itemstack, i, enumdirection)) { // If this logic changes, update above. this is left unused incase reflective plugins
            return hopperPull(world, ihopper, iinventory, itemstack, i);
            // ItemStack itemstack1 = itemstack.copy();
            // // ItemStack itemstack2 = addItem(iinventory, ihopper, iinventory.removeItem(i, 1), (EnumDirection) null);
            // // CraftBukkit start - Call event on collection of items from inventories into the hopper
            // CraftItemStack oitemstack = CraftItemStack.asCraftMirror(iinventory.removeItem(i, world.spigotConfig.hopperAmount)); // Spigot

            // Inventory sourceInventory;
            // // Have to special case large chests as they work oddly
            // if (iinventory instanceof CompoundContainer) {
            //     sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
            // } else if (iinventory.getOwner() != null) {
            //     sourceInventory = iinventory.getOwner().getInventory();
            // } else {
            //     sourceInventory = new CraftInventory(iinventory);
            // }

            // InventoryMoveItemEvent event = new InventoryMoveItemEvent(sourceInventory, oitemstack.clone(), ihopper.getOwner().getInventory(), false);

            // Bukkit.getServer().getPluginManager().callEvent(event);
            // if (event.isCancelled()) {
            //     iinventory.setItem(i, itemstack1);

                // if (ihopper instanceof HopperBlockEntity) {
                //     ((HopperBlockEntity) ihopper).setCooldown(world.spigotConfig.hopperTransfer); // Spigot
                // }

                // return false;
            // }
            // int origCount = event.getItem().getAmount(); // Spigot
            // ItemStack itemstack2 = HopperBlockEntity.addItem(iinventory, ihopper, CraftItemStack.asNMSCopy(event.getItem()), null);
            // // CraftBukkit end

            // if (itemstack2.isEmpty()) {
            //     iinventory.setChanged();
            //     return true;
            // }

            // itemstack1.shrink(origCount - itemstack2.getCount()); // Spigot
            // iinventory.setItem(i, itemstack1);
            // Paper end
        }

        return false;
    }

    public static boolean addItem(Container inventory, ItemEntity itemEntity) {
        boolean flag = false;
        // CraftBukkit start
        if (InventoryPickupItemEvent.getHandlerList().getRegisteredListeners().length > 0) { // Paper - optimize hoppers
        InventoryPickupItemEvent event = new InventoryPickupItemEvent(getInventory(inventory), (org.bukkit.entity.Item) itemEntity.getBukkitEntity()); // Paper - use getInventory() to avoid snapshot creation
        itemEntity.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        // CraftBukkit end
        } // Paper - optimize hoppers
        ItemStack itemstack = itemEntity.getItem().copy();
        ItemStack itemstack1 = HopperBlockEntity.addItem((Container) null, inventory, itemstack, (Direction) null);

        if (itemstack1.isEmpty()) {
            flag = true;
            itemEntity.setItem(ItemStack.EMPTY);
            itemEntity.discard();
        } else {
            itemEntity.setItem(itemstack1);
        }

        return flag;
    }

    public static ItemStack addItem(@Nullable Container from, Container to, ItemStack stack, @Nullable Direction side) {
        int i;

        if (to instanceof WorldlyContainer) {
            WorldlyContainer iworldinventory = (WorldlyContainer) to;

            if (side != null) {
                int[] aint = iworldinventory.getSlotsForFace(side);

                for (i = 0; i < aint.length && !stack.isEmpty(); ++i) {
                    stack = HopperBlockEntity.tryMoveInItem(from, to, stack, aint[i], side);
                }

                return stack;
            }
        }

        int j = to.getContainerSize();

        for (i = 0; i < j && !stack.isEmpty(); ++i) {
            stack = HopperBlockEntity.tryMoveInItem(from, to, stack, i, side);
        }

        return stack;
    }

    private static boolean canPlaceItemInContainer(Container inventory, ItemStack stack, int slot, @Nullable Direction side) {
        if (!inventory.canPlaceItem(slot, stack)) {
            return false;
        } else {
            boolean flag;

            if (inventory instanceof WorldlyContainer) {
                WorldlyContainer iworldinventory = (WorldlyContainer) inventory;

                if (!iworldinventory.canPlaceItemThroughFace(slot, stack, side)) {
                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }
    }

    private static boolean canTakeItemFromContainer(Container hopperInventory, Container fromInventory, ItemStack stack, int slot, Direction facing) {
        if (!fromInventory.canTakeItem(hopperInventory, slot, stack)) {
            return false;
        } else {
            boolean flag;

            if (fromInventory instanceof WorldlyContainer) {
                WorldlyContainer iworldinventory = (WorldlyContainer) fromInventory;

                if (!iworldinventory.canTakeItemThroughFace(slot, stack, facing)) {
                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }
    }

    private static ItemStack tryMoveInItem(@Nullable Container from, Container to, ItemStack stack, int slot, @Nullable Direction side) {
        ItemStack itemstack1 = to.getItem(slot);

        if (HopperBlockEntity.canPlaceItemInContainer(to, stack, slot, side)) {
            boolean flag = false;
            boolean flag1 = to.isEmpty();

            if (itemstack1.isEmpty()) {
                // Spigot start - SPIGOT-6693, InventorySubcontainer#setItem
                ItemStack leftover = ItemStack.EMPTY; // Paper
                if (!stack.isEmpty() && stack.getCount() > to.getMaxStackSize()) {
                    leftover = stack; // Paper
                    stack = stack.split(to.getMaxStackSize());
                }
                // Spigot end
                IGNORE_TILE_UPDATES.set(Boolean.TRUE); // Paper // Folia - region threading
                to.setItem(slot, stack);
                IGNORE_TILE_UPDATES.set(Boolean.FALSE); // Paper // Folia - region threading
                stack = leftover; // Paper
                flag = true;
            } else if (HopperBlockEntity.canMergeItems(itemstack1, stack)) {
                int j = Math.min(stack.getMaxStackSize(), to.getMaxStackSize()) - itemstack1.getCount(); // Paper
                int k = Math.min(stack.getCount(), j);

                stack.shrink(k);
                itemstack1.grow(k);
                flag = k > 0;
            }

            if (flag) {
                if (flag1 && to instanceof HopperBlockEntity) {
                    HopperBlockEntity tileentityhopper = (HopperBlockEntity) to;

                    if (!tileentityhopper.isOnCustomCooldown()) {
                        byte b0 = 0;

                        if (from instanceof HopperBlockEntity) {
                            HopperBlockEntity tileentityhopper1 = (HopperBlockEntity) from;

                            if (tileentityhopper.tickedGameTime >= tileentityhopper1.tickedGameTime) {
                                b0 = 1;
                            }
                        }

                        tileentityhopper.setCooldown(tileentityhopper.level.spigotConfig.hopperTransfer - b0); // Spigot
                    }
                }

                to.setChanged();
            }
        }

        return stack;
    }

    // CraftBukkit start
    @Nullable
    private static Container runHopperInventorySearchEvent(Container inventory, CraftBlock hopper, CraftBlock searchLocation, HopperInventorySearchEvent.ContainerType containerType) {
        HopperInventorySearchEvent event = new HopperInventorySearchEvent((inventory != null) ? new CraftInventory(inventory) : null, containerType, hopper, searchLocation);
        Bukkit.getServer().getPluginManager().callEvent(event);
        CraftInventory craftInventory = (CraftInventory) event.getInventory();
        return (craftInventory != null) ? craftInventory.getInventory() : null;
    }
    // CraftBukkit end

    @Nullable
    private static Container getAttachedContainer(Level world, BlockPos pos, BlockState state) {
        Direction enumdirection = (Direction) state.getValue(HopperBlock.FACING);

        // CraftBukkit start
        BlockPos searchPosition = pos.relative(enumdirection);
        Container inventory = HopperBlockEntity.getContainerAt(world, pos.relative(enumdirection));

        CraftBlock hopper = CraftBlock.at(world, pos);
        CraftBlock searchBlock = CraftBlock.at(world, searchPosition);
        return HopperBlockEntity.runHopperInventorySearchEvent(inventory, hopper, searchBlock, HopperInventorySearchEvent.ContainerType.DESTINATION);
        // CraftBukkit end
    }

    @Nullable
    private static Container getSourceContainer(Level world, Hopper hopper) {
        // CraftBukkit start
        Container inventory = HopperBlockEntity.getContainerAt(world, hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());

        BlockPos blockPosition = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
        CraftBlock hopper1 = CraftBlock.at(world, blockPosition);
        CraftBlock container = CraftBlock.at(world, blockPosition.above());
        return HopperBlockEntity.runHopperInventorySearchEvent(inventory, hopper1, container, HopperInventorySearchEvent.ContainerType.SOURCE);
        // CraftBukkit end
    }

    // Paper start - optimize hopper item suck in
    static final AABB HOPPER_ITEM_SUCK_OVERALL = Hopper.SUCK.bounds();
    static final AABB[] HOPPER_ITEM_SUCK_INDIVIDUAL = Hopper.SUCK.toAabbs().toArray(new AABB[0]);
    // Paper end - optimize hopper item suck in

    public static List<ItemEntity> getItemsAtAndAbove(Level world, Hopper hopper) {
        // Paper start - optimize hopper item suck in
        // eliminate multiple getEntitiesOfClass() but maintain the voxelshape collision by moving
        // the individual AABB checks into the predicate
        final double shiftX = hopper.getLevelX() - 0.5D;
        final double shiftY = hopper.getLevelY() - 0.5D;
        final double shiftZ = hopper.getLevelZ() - 0.5D;
        return world.getEntitiesOfClass(ItemEntity.class, HOPPER_ITEM_SUCK_OVERALL.move(shiftX, shiftY, shiftZ), (final Entity entity) -> {
            if (!entity.isAlive()) { // EntitySelector.ENTITY_STILL_ALIVE
                return false;
            }

            for (final AABB aabb : HOPPER_ITEM_SUCK_INDIVIDUAL) {
                if (aabb.move(shiftX, shiftY, shiftZ).intersects(entity.getBoundingBox())) {
                    return true;
                }
            }

            return false;
        });
        // Paper end - optimize hopper item suck in
    }

    @Nullable
    public static Container getContainerAt(Level world, BlockPos pos) {
        return HopperBlockEntity.getContainerAt(world, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, true); // Paper
    }

    @Nullable
    private static Container getContainerAt(Level world, double x, double y, double z) {
        // Paper start - add optimizeEntities parameter
        return HopperBlockEntity.getContainerAt(world, x, y, z, false);
    }
    @Nullable
    private static Container getContainerAt(Level world, double x, double y, double z, final boolean optimizeEntities) {
        // Paper end - add optimizeEntities parameter
        Object object = null;
        BlockPos blockposition = BlockPos.containing(x, y, z);
        if ( !world.spigotConfig.hopperCanLoadChunks && !world.hasChunkAt( blockposition ) ) return null; // Spigot
        BlockState iblockdata = world.getBlockState(blockposition);
        Block block = iblockdata.getBlock();

        if (block instanceof WorldlyContainerHolder) {
            object = ((WorldlyContainerHolder) block).getContainer(iblockdata, world, blockposition);
        } else if (iblockdata.hasBlockEntity()) {
            BlockEntity tileentity = world.getBlockEntity(blockposition);

            if (tileentity instanceof Container) {
                object = (Container) tileentity;
                if (object instanceof ChestBlockEntity && block instanceof ChestBlock) {
                    object = ChestBlock.getContainer((ChestBlock) block, iblockdata, world, blockposition, true);
                }
            }
        }

        if (object == null && (!optimizeEntities || !world.paperConfig().hopper.ignoreOccludingBlocks || !iblockdata.getBukkitMaterial().isOccluding())) { // Paper
            List<Entity> list = world.getEntitiesOfClass((Class)Container.class, new AABB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D), EntitySelector.CONTAINER_ENTITY_SELECTOR); // Paper - optimize hoppers, use getEntitiesOfClass

            if (!list.isEmpty()) {
                object = (Container) list.get(world.random.nextInt(list.size()));
            }
        }

        return (Container) object;
    }

    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() < first.getMaxStackSize() && first.is(second.getItem()) && first.getDamageValue() == second.getDamageValue() && ((first.isEmpty() && second.isEmpty()) || java.util.Objects.equals(first.getTag(), second.getTag())); // Paper - used to return true for full itemstacks?!
    }

    @Override
    public double getLevelX() {
        return (double) this.worldPosition.getX() + 0.5D;
    }

    @Override
    public double getLevelY() {
        return (double) this.worldPosition.getY() + 0.5D;
    }

    @Override
    public double getLevelZ() {
        return (double) this.worldPosition.getZ() + 0.5D;
    }

    private void setCooldown(int transferCooldown) {
        this.cooldownTime = transferCooldown;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        this.items = list;
    }

    public static void entityInside(Level world, BlockPos pos, BlockState state, Entity entity, HopperBlockEntity blockEntity) {
        if (entity instanceof ItemEntity) {
            ItemEntity entityitem = (ItemEntity) entity;

            if (!entityitem.getItem().isEmpty() && Shapes.joinIsNotEmpty(Shapes.create(entity.getBoundingBox().move((double) (-pos.getX()), (double) (-pos.getY()), (double) (-pos.getZ()))), blockEntity.getSuckShape(), BooleanOp.AND)) {
                HopperBlockEntity.tryMoveItems(world, pos, state, blockEntity, () -> {
                    return HopperBlockEntity.addItem(blockEntity, entityitem);
                });
            }
        }

    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory playerInventory) {
        return new HopperMenu(syncId, playerInventory, this);
    }
}
