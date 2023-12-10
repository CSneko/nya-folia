package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {
    private Direction enumdirection; // Paper

    // CraftBukkit start
    private boolean dropper;

    public DefaultDispenseItemBehavior(boolean dropper) {
        this.dropper = dropper;
    }
    // CraftBukkit end

    public DefaultDispenseItemBehavior() {}

    @Override
    public final ItemStack dispense(BlockSource pointer, ItemStack stack) {
        enumdirection = pointer.state().getValue(DispenserBlock.FACING); // Paper - cache facing direction
        ItemStack itemstack1 = this.execute(pointer, stack);

        this.playSound(pointer);
        this.playAnimation(pointer, enumdirection); // Paper - cache facing direction
        return itemstack1;
    }

    protected ItemStack execute(BlockSource pointer, ItemStack stack) {
        // Paper - cached enum direction
        Position iposition = DispenserBlock.getDispensePosition(pointer);
        ItemStack itemstack1 = stack.split(1);

        // CraftBukkit start
        if (!DefaultDispenseItemBehavior.spawnItem(pointer.level(), itemstack1, 6, enumdirection, pointer, this.dropper)) {
            stack.grow(1);
        }
        // CraftBukkit end
        return stack;
    }

    // CraftBukkit start - void -> boolean return, IPosition -> ISourceBlock last argument, dropper
    public static boolean spawnItem(Level world, ItemStack itemstack, int i, Direction enumdirection, BlockSource sourceblock, boolean dropper) {
        if (itemstack.isEmpty()) return true;
        Position iposition = DispenserBlock.getDispensePosition(sourceblock);
        // CraftBukkit end
        double d0 = iposition.x();
        double d1 = iposition.y();
        double d2 = iposition.z();

        if (enumdirection.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125D;
        } else {
            d1 -= 0.15625D;
        }

        ItemEntity entityitem = new ItemEntity(world, d0, d1, d2, itemstack);
        double d3 = world.random.nextDouble() * 0.1D + 0.2D;

        entityitem.setDeltaMovement(world.random.triangle((double) enumdirection.getStepX() * d3, 0.0172275D * (double) i), world.random.triangle(0.2D, 0.0172275D * (double) i), world.random.triangle((double) enumdirection.getStepZ() * d3, 0.0172275D * (double) i));

        // CraftBukkit start
        org.bukkit.block.Block block = CraftBlock.at(world, sourceblock.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), CraftVector.toBukkit(entityitem.getDeltaMovement()));
        if (!DispenserBlock.eventFired.get()) { // Folia - region threading
            world.getCraftServer().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            return false;
        }

        entityitem.setItem(CraftItemStack.asNMSCopy(event.getItem()));
        entityitem.setDeltaMovement(CraftVector.toNMS(event.getVelocity()));

        if (!dropper && !event.getItem().getType().equals(craftItem.getType())) {
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior.getClass() != DefaultDispenseItemBehavior.class) {
                idispensebehavior.dispense(sourceblock, eventStack);
            } else {
                world.addFreshEntity(entityitem);
            }
            return false;
        }

        world.addFreshEntity(entityitem);

        return true;
        // CraftBukkit end
    }

    protected void playSound(BlockSource pointer) {
        pointer.level().levelEvent(1000, pointer.pos(), 0);
    }

    protected void playAnimation(BlockSource pointer, Direction side) {
        pointer.level().levelEvent(2000, pointer.pos(), side.get3DDataValue());
    }
}
