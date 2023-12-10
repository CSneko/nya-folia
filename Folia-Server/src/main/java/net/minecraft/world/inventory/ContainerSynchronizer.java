package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public interface ContainerSynchronizer {
    void sendInitialData(AbstractContainerMenu handler, NonNullList<ItemStack> stacks, ItemStack cursorStack, int[] properties);

    default void sendOffHandSlotChange() {} // Paper
    void sendSlotChange(AbstractContainerMenu handler, int slot, ItemStack stack);

    void sendCarriedChange(AbstractContainerMenu handler, ItemStack stack);

    void sendDataChange(AbstractContainerMenu handler, int property, int value);
}
