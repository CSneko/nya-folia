package net.minecraft.world.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;

public class SnowballItem extends Item {

    public SnowballItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);

        // CraftBukkit - moved down
        // world.playSound((EntityHuman) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!world.isClientSide) {
            Snowball entitysnowball = new Snowball(world, user);

            entitysnowball.setItem(itemstack);
            entitysnowball.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 1.0F);
            // Paper start
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) user.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), (org.bukkit.entity.Projectile) entitysnowball.getBukkitEntity());
            if (event.callEvent() && world.addFreshEntity(entitysnowball)) {
                user.awardStat(Stats.ITEM_USED.get(this));
                if (event.shouldConsume() && !user.getAbilities().instabuild) {
                    // Paper end
                    itemstack.shrink(1);
                } else if (user instanceof net.minecraft.server.level.ServerPlayer) {  // Paper
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();  // Paper
                }

                world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            } else { // Paper
                if (user instanceof net.minecraft.server.level.ServerPlayer) ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory(); // Paper
                return InteractionResultHolder.fail(itemstack); // Paper
            }
        }
        // CraftBukkit end

        /* // Paper tart - moved up
        user.awardStat(Stats.ITEM_USED.get(this));
        // CraftBukkit start - moved up
        /*
        if (!entityhuman.getAbilities().instabuild) {
            itemstack.shrink(1);
        }
        */
        // Paper end

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }
}
