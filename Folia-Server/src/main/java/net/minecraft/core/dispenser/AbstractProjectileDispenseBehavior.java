package net.minecraft.core.dispenser;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
// CraftBukkit start
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public abstract class AbstractProjectileDispenseBehavior extends DefaultDispenseItemBehavior {

    public AbstractProjectileDispenseBehavior() {}

    @Override
    public ItemStack execute(BlockSource pointer, ItemStack stack) {
        ServerLevel worldserver = pointer.level();
        Position iposition = DispenserBlock.getDispensePosition(pointer);
        Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
        Projectile iprojectile = this.getProjectile(worldserver, iposition, stack);

        // CraftBukkit start
        // iprojectile.shoot((double) enumdirection.getStepX(), (double) ((float) enumdirection.getStepY() + 0.1F), (double) enumdirection.getStepZ(), this.getPower(), this.getUncertainty());
        ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below and single item in event
        org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) enumdirection.getStepX(), (double) ((float) enumdirection.getStepY() + 0.1F), (double) enumdirection.getStepZ()));
        if (!DispenserBlock.eventFired.get()) { // Folia - region threading
            worldserver.getCraftServer().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // stack.grow(1); // Paper - shrink below
            return stack;
        }

        boolean shrink = true; // Paper
        if (!event.getItem().equals(craftItem)) {
            shrink = false; // Paper - shrink below
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                idispensebehavior.dispense(pointer, eventStack);
                return stack;
            }
        }

        iprojectile.shoot(event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), this.getPower(), this.getUncertainty());
        ((Entity) iprojectile).projectileSource = new org.bukkit.craftbukkit.projectiles.CraftBlockProjectileSource(pointer.blockEntity());
        // CraftBukkit end
        worldserver.addFreshEntity(iprojectile);
        if (shrink) stack.shrink(1); // Paper - actually handle here
        return stack;
    }

    @Override
    protected void playSound(BlockSource pointer) {
        pointer.level().levelEvent(1002, pointer.pos(), 0);
    }

    protected abstract Projectile getProjectile(Level world, Position position, ItemStack stack);

    protected float getUncertainty() {
        return 6.0F;
    }

    protected float getPower() {
        return 1.1F;
    }
}
