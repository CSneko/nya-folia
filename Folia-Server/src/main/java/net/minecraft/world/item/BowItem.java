package net.minecraft.world.item;

import java.util.function.Predicate;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public class BowItem extends ProjectileWeaponItem implements Vanishable {

    public static final int MAX_DRAW_DURATION = 20;
    public static final int DEFAULT_RANGE = 15;

    public BowItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof Player) {
            Player entityhuman = (Player) user;
            boolean flag = entityhuman.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;
            ItemStack itemstack1 = entityhuman.getProjectile(stack);

            if (!itemstack1.isEmpty() || flag) {
                if (itemstack1.isEmpty()) {
                    itemstack1 = new ItemStack(Items.ARROW);
                }

                int j = this.getUseDuration(stack) - remainingUseTicks;
                float f = BowItem.getPowerForTime(j);

                if ((double) f >= 0.1D) {
                    boolean flag1 = flag && itemstack1.is(Items.ARROW);

                    if (!world.isClientSide) {
                        ArrowItem itemarrow = (ArrowItem) (itemstack1.getItem() instanceof ArrowItem ? itemstack1.getItem() : Items.ARROW);
                        AbstractArrow entityarrow = itemarrow.createArrow(world, itemstack1, entityhuman);

                        entityarrow.shootFromRotation(entityhuman, entityhuman.getXRot(), entityhuman.getYRot(), 0.0F, f * 3.0F, 1.0F);
                        if (f == 1.0F) {
                            entityarrow.setCritArrow(true);
                        }

                        int k = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);

                        if (k > 0) {
                            entityarrow.setBaseDamage(entityarrow.getBaseDamage() + (double) k * 0.5D + 0.5D);
                        }

                        int l = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);

                        if (l > 0) {
                            entityarrow.setKnockback(l);
                        }

                        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0) {
                            entityarrow.setSecondsOnFire(100);
                        }
                        // CraftBukkit start
                        org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(entityhuman, stack, itemstack1, entityarrow, entityhuman.getUsedItemHand(), f, !flag1);
                        if (event.isCancelled()) {
                            event.getProjectile().remove();
                            return;
                        }
                        flag1 = !event.shouldConsumeItem();
                        // CraftBukkit end

                        stack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                            entityhuman1.broadcastBreakEvent(entityhuman.getUsedItemHand());
                        });
                        if (flag1 || entityhuman.getAbilities().instabuild && (itemstack1.is(Items.SPECTRAL_ARROW) || itemstack1.is(Items.TIPPED_ARROW))) {
                            entityarrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        }

                        // CraftBukkit start
                        if (event.getProjectile() == entityarrow.getBukkitEntity()) {
                            if (!world.addFreshEntity(entityarrow)) {
                                if (entityhuman instanceof net.minecraft.server.level.ServerPlayer) {
                                    ((net.minecraft.server.level.ServerPlayer) entityhuman).getBukkitEntity().updateInventory();
                                }
                                return;
                            }
                        }
                        // CraftBukkit end
                    }

                    world.playSound((Player) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + f * 0.5F);
                    if (!flag1 && !entityhuman.getAbilities().instabuild) {
                        itemstack1.shrink(1);
                        if (itemstack1.isEmpty()) {
                            entityhuman.getInventory().removeItem(itemstack1);
                        }
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(this));
                }
            }
        }
    }

    public static float getPowerForTime(int useTicks) {
        float f = (float) useTicks / 20.0F;

        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }

        return f;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);
        boolean flag = !user.getProjectile(itemstack).isEmpty();

        if (!user.getAbilities().instabuild && !flag) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            user.startUsingItem(hand);
            return InteractionResultHolder.consume(itemstack);
        }
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return BowItem.ARROW_ONLY;
    }

    @Override
    public int getDefaultProjectileRange() {
        return 15;
    }
}
