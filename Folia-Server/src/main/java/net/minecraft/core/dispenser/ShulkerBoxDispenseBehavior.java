package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.DispenserBlock;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class ShulkerBoxDispenseBehavior extends OptionalDispenseItemBehavior {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ShulkerBoxDispenseBehavior() {}

    @Override
    protected ItemStack execute(BlockSource pointer, ItemStack stack) {
        this.setSuccess(false);
        Item item = stack.getItem();

        if (item instanceof BlockItem) {
            Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
            BlockPos blockposition = pointer.pos().relative(enumdirection);
            Direction enumdirection1 = pointer.level().isEmptyBlock(blockposition.below()) ? enumdirection : Direction.UP;

            // CraftBukkit start
            org.bukkit.block.Block bukkitBlock = CraftBlock.at(pointer.level(), pointer.pos());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - single item in event

            BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
            if (!DispenserBlock.eventFired.get().booleanValue()) { // Folia - region threading
                pointer.level().getCraftServer().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                return stack;
            }

            if (!event.getItem().equals(craftItem)) {
                // Chain to handler for new item
                ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                    idispensebehavior.dispense(pointer, eventStack);
                    return stack;
                }
            }
            // CraftBukkit end

            try {
                this.setSuccess(((BlockItem) item).place(new DirectionalPlaceContext(pointer.level(), blockposition, enumdirection, stack, enumdirection1)).consumesAction());
            } catch (Exception exception) {
                ShulkerBoxDispenseBehavior.LOGGER.error("Error trying to place shulker box at {}", blockposition, exception);
            }
        }

        return stack;
    }
}
