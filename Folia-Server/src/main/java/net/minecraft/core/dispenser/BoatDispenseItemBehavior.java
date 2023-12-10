package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class BoatDispenseItemBehavior extends DefaultDispenseItemBehavior {

    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior;
    private final Boat.Type type;
    private final boolean isChestBoat;

    public BoatDispenseItemBehavior(Boat.Type type) {
        this(type, false);
    }

    public BoatDispenseItemBehavior(Boat.Type boatType, boolean chest) {
        this.defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
        this.type = boatType;
        this.isChestBoat = chest;
    }

    @Override
    public ItemStack execute(BlockSource pointer, ItemStack stack) {
        Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
        ServerLevel worldserver = pointer.level();
        Vec3 vec3d = pointer.center();
        double d0 = 0.5625D + (double) EntityType.BOAT.getWidth() / 2.0D;
        double d1 = vec3d.x() + (double) enumdirection.getStepX() * d0;
        double d2 = vec3d.y() + (double) ((float) enumdirection.getStepY() * 1.125F);
        double d3 = vec3d.z() + (double) enumdirection.getStepZ() * d0;
        BlockPos blockposition = pointer.pos().relative(enumdirection);
        double d4;

        if (worldserver.getFluidState(blockposition).is(FluidTags.WATER)) {
            d4 = 1.0D;
        } else {
            if (!worldserver.getBlockState(blockposition).isAir() || !worldserver.getFluidState(blockposition.below()).is(FluidTags.WATER)) {
                return this.defaultDispenseItemBehavior.dispense(pointer, stack);
            }

            d4 = 0.0D;
        }

        // Object object = this.isChestBoat ? new ChestBoat(worldserver, d1, d2 + d4, d3) : new EntityBoat(worldserver, d1, d2 + d4, d3);
        // CraftBukkit start
        ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink at end and single item in event
        org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(d1, d2 + d4, d3));
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

        Object object = this.isChestBoat ? new ChestBoat(worldserver, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ()) : new Boat(worldserver, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ());
        // CraftBukkit end

        ((Boat) object).setVariant(this.type);
        ((Boat) object).setYRot(enumdirection.toYRot());
        if (worldserver.addFreshEntity((Entity) object) && shrink) stack.shrink(1); // Paper - if entity add was successful and supposed to shrink
        return stack;
    }

    @Override
    protected void playSound(BlockSource pointer) {
        pointer.level().levelEvent(1000, pointer.pos(), 0);
    }
}
