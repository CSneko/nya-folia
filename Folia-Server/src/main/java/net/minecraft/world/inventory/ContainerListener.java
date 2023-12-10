package net.minecraft.world.inventory;

import net.minecraft.world.item.ItemStack;

public interface ContainerListener {
    void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack);

    // Paper start
    default void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack oldStack, ItemStack stack) {
        slotChanged(handler, slotId, stack);
    }
    // Paper end

    void dataChanged(AbstractContainerMenu handler, int property, int value);
}
