package net.minecraft.core.dispenser;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class ShearsDispenseItemBehavior extends OptionalDispenseItemBehavior {

    public ShearsDispenseItemBehavior() {}

    @Override
    protected ItemStack execute(BlockSource pointer, ItemStack stack) {
        ServerLevel worldserver = pointer.level();
        // CraftBukkit start
        org.bukkit.block.Block bukkitBlock = CraftBlock.at(worldserver, pointer.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack);

        BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
        if (!DispenserBlock.eventFired.get()) { // Folia - region threading
            worldserver.getCraftServer().getPluginManager().callEvent(event);
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

        if (!worldserver.isClientSide()) {
            BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));

            this.setSuccess(ShearsDispenseItemBehavior.tryShearBeehive(worldserver, blockposition) || ShearsDispenseItemBehavior.tryShearLivingEntity(worldserver, blockposition, bukkitBlock, craftItem)); // CraftBukkit
            if (this.isSuccess() && stack.hurt(1, worldserver.getRandom(), (ServerPlayer) null)) {
                stack.setCount(0);
            }
        }

        return stack;
    }

    private static boolean tryShearBeehive(ServerLevel world, BlockPos pos) {
        BlockState iblockdata = world.getBlockState(pos);

        if (iblockdata.is(BlockTags.BEEHIVES, (blockbase_blockdata) -> {
            return blockbase_blockdata.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockbase_blockdata.getBlock() instanceof BeehiveBlock;
        })) {
            int i = (Integer) iblockdata.getValue(BeehiveBlock.HONEY_LEVEL);

            if (i >= 5) {
                world.playSound((Player) null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                BeehiveBlock.dropHoneycomb(world, pos);
                ((BeehiveBlock) iblockdata.getBlock()).releaseBeesAndResetHoneyLevel(world, iblockdata, pos, (Player) null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                world.gameEvent((Entity) null, GameEvent.SHEAR, pos);
                return true;
            }
        }

        return false;
    }

    private static boolean tryShearLivingEntity(ServerLevel worldserver, BlockPos blockposition, org.bukkit.block.Block bukkitBlock, CraftItemStack craftItem) { // CraftBukkit - add args
        List<LivingEntity> list = worldserver.getEntitiesOfClass(LivingEntity.class, new AABB(blockposition), EntitySelector.NO_SPECTATORS);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            LivingEntity entityliving = (LivingEntity) iterator.next();

            if (entityliving instanceof Shearable) {
                Shearable ishearable = (Shearable) entityliving;

                if (ishearable.readyForShearing()) {
                    // CraftBukkit start
                    if (CraftEventFactory.callBlockShearEntityEvent(entityliving, bukkitBlock, craftItem).isCancelled()) {
                        continue;
                    }
                    // CraftBukkit end
                    ishearable.shear(SoundSource.BLOCKS);
                    worldserver.gameEvent((Entity) null, GameEvent.SHEAR, blockposition);
                    return true;
                }
            }
        }

        return false;
    }
}
