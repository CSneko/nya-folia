package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class MinecartItem extends Item {

    private static final DispenseItemBehavior DISPENSE_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior() {
        private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

        @Override
        public ItemStack execute(BlockSource pointer, ItemStack stack) {
            Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
            ServerLevel worldserver = pointer.level();
            Vec3 vec3d = pointer.center();
            double d0 = vec3d.x() + (double) enumdirection.getStepX() * 1.125D;
            double d1 = Math.floor(vec3d.y()) + (double) enumdirection.getStepY();
            double d2 = vec3d.z() + (double) enumdirection.getStepZ() * 1.125D;
            BlockPos blockposition = pointer.pos().relative(enumdirection);
            BlockState iblockdata = worldserver.getBlockState(blockposition);
            RailShape blockpropertytrackposition = iblockdata.getBlock() instanceof BaseRailBlock ? (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;
            double d3;

            if (iblockdata.is(BlockTags.RAILS)) {
                if (blockpropertytrackposition.isAscending()) {
                    d3 = 0.6D;
                } else {
                    d3 = 0.1D;
                }
            } else {
                if (!iblockdata.isAir() || !worldserver.getBlockState(blockposition.below()).is(BlockTags.RAILS)) {
                    return this.defaultDispenseItemBehavior.dispense(pointer, stack);
                }

                BlockState iblockdata1 = worldserver.getBlockState(blockposition.below());
                RailShape blockpropertytrackposition1 = iblockdata1.getBlock() instanceof BaseRailBlock ? (RailShape) iblockdata1.getValue(((BaseRailBlock) iblockdata1.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;

                if (enumdirection != Direction.DOWN && blockpropertytrackposition1.isAscending()) {
                    d3 = -0.4D;
                } else {
                    d3 = -0.9D;
                }
            }

            // CraftBukkit start
            // EntityMinecartAbstract entityminecartabstract = EntityMinecartAbstract.createMinecart(worldserver, d0, d1 + d3, d2, ((ItemMinecart) itemstack.getItem()).type);
            ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below and single item in event
            org.bukkit.block.Block block2 = CraftBlock.at(worldserver, pointer.pos());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

            BlockDispenseEvent event = new BlockDispenseEvent(block2, craftItem.clone(), new org.bukkit.util.Vector(d0, d1 + d3, d2));
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

            itemstack1 = CraftItemStack.asNMSCopy(event.getItem());
            AbstractMinecart entityminecartabstract = AbstractMinecart.createMinecart(worldserver, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), ((MinecartItem) itemstack1.getItem()).type);

            if (stack.hasCustomHoverName()) {
                entityminecartabstract.setCustomName(stack.getHoverName());
            }

            if (worldserver.addFreshEntity(entityminecartabstract) && shrink) stack.shrink(1); // Paper - actually handle here
            // CraftBukkit end
            return stack;
        }

        @Override
        protected void playSound(BlockSource pointer) {
            pointer.level().levelEvent(1000, pointer.pos(), 0);
        }
    };
    final AbstractMinecart.Type type;

    public MinecartItem(AbstractMinecart.Type type, Item.Properties settings) {
        super(settings);
        this.type = type;
        DispenserBlock.registerBehavior(this, MinecartItem.DISPENSE_ITEM_BEHAVIOR);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        if (!iblockdata.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        } else {
            ItemStack itemstack = context.getItemInHand();

            if (!world.isClientSide) {
                RailShape blockpropertytrackposition = iblockdata.getBlock() instanceof BaseRailBlock ? (RailShape) iblockdata.getValue(((BaseRailBlock) iblockdata.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;
                double d0 = 0.0D;

                if (blockpropertytrackposition.isAscending()) {
                    d0 = 0.5D;
                }

                AbstractMinecart entityminecartabstract = AbstractMinecart.createMinecart(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.0625D + d0, (double) blockposition.getZ() + 0.5D, this.type);

                if (itemstack.hasCustomHoverName()) {
                    entityminecartabstract.setCustomName(itemstack.getHoverName());
                }

                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(context, entityminecartabstract).isCancelled()) {
                    return InteractionResult.FAIL;
                }
                // CraftBukkit end
                if (!world.addFreshEntity(entityminecartabstract)) return InteractionResult.PASS; // CraftBukkit
                world.gameEvent(GameEvent.ENTITY_PLACE, blockposition, GameEvent.Context.of(context.getPlayer(), world.getBlockState(blockposition.below())));
            }

            itemstack.shrink(1);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }
}
