package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.event.hanging.HangingPlaceEvent;
// CraftBukkit end

public class LeadItem extends Item {

    public LeadItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        if (iblockdata.is(BlockTags.FENCES)) {
            Player entityhuman = context.getPlayer();

            if (!world.isClientSide && entityhuman != null) {
                LeadItem.bindPlayerMobs(entityhuman, world, blockposition, context.getHand()); // CraftBukkit - Pass hand
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static InteractionResult bindPlayerMobs(Player entityhuman, Level world, BlockPos blockposition, net.minecraft.world.InteractionHand enumhand) { // CraftBukkit - Add EnumHand
        LeashFenceKnotEntity entityleash = null;
        boolean flag = false;
        double d0 = 7.0D;
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();
        List<Mob> list = world.getEntitiesOfClass(Mob.class, new AABB((double) i - 7.0D, (double) j - 7.0D, (double) k - 7.0D, (double) i + 7.0D, (double) j + 7.0D, (double) k + 7.0D));
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Mob entityinsentient = (Mob) iterator.next();

            if (entityinsentient.getLeashHolder() == entityhuman) {
                if (entityleash == null) {
                    entityleash = LeashFenceKnotEntity.getOrCreateKnot(world, blockposition);

                    // CraftBukkit start - fire HangingPlaceEvent
                    org.bukkit.inventory.EquipmentSlot hand = CraftEquipmentSlot.getHand(enumhand);
                    HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) entityleash.getBukkitEntity(), entityhuman != null ? (org.bukkit.entity.Player) entityhuman.getBukkitEntity() : null, world.getWorld().getBlockAt(i, j, k), org.bukkit.block.BlockFace.SELF, hand);
                    world.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        entityleash.discard();
                        return InteractionResult.PASS;
                    }
                    // CraftBukkit end
                    entityleash.playPlacementSound();
                }

                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLeashEntityEvent(entityinsentient, entityleash, entityhuman, enumhand).isCancelled()) {
                    continue;
                }
                // CraftBukkit end

                entityinsentient.setLeashedTo(entityleash, true);
                flag = true;
            }
        }

        if (flag) {
            world.gameEvent(GameEvent.BLOCK_ATTACH, blockposition, GameEvent.Context.of((Entity) entityhuman));
        }

        return flag ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    // CraftBukkit start
    public static InteractionResult bindPlayerMobs(Player player, Level world, BlockPos pos) {
        return LeadItem.bindPlayerMobs(player, world, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    // CraftBukkit end
}
