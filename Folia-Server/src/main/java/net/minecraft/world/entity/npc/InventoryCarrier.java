package net.minecraft.world.entity.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public interface InventoryCarrier {

    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(Mob entity, InventoryCarrier inventoryOwner, ItemEntity item) {
        ItemStack itemstack = item.getItem();

        if (entity.wantsToPickUp(itemstack)) {
            SimpleContainer inventorysubcontainer = inventoryOwner.getInventory();
            boolean flag = inventorysubcontainer.canAddItem(itemstack);

            if (!flag) {
                return;
            }

            // CraftBukkit start
            ItemStack remaining = new SimpleContainer(inventorysubcontainer).addItem(itemstack);
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(entity, item, remaining.getCount(), false).isCancelled()) {
                return;
            }
            // CraftBukkit end

            entity.onItemPickup(item);
            int i = itemstack.getCount();
            ItemStack itemstack1 = inventorysubcontainer.addItem(itemstack);

            entity.take(item, i - itemstack1.getCount());
            if (itemstack1.isEmpty()) {
                item.discard();
            } else {
                itemstack.setCount(itemstack1.getCount());
            }
        }

    }

    default void readInventoryFromTag(CompoundTag nbt) {
        if (nbt.contains("Inventory", 9)) {
            this.getInventory().fromTag(nbt.getList("Inventory", 10));
        }

    }

    default void writeInventoryToTag(CompoundTag nbt) {
        nbt.put("Inventory", this.getInventory().createTag());
    }
}
