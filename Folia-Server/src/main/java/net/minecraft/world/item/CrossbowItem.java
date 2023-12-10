package net.minecraft.world.item;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CrossbowItem extends ProjectileWeaponItem implements Vanishable {

    private static final String TAG_CHARGED = "Charged";
    private static final String TAG_CHARGED_PROJECTILES = "ChargedProjectiles";
    private static final int MAX_CHARGE_DURATION = 25;
    public static final int DEFAULT_RANGE = 8;
    private boolean startSoundPlayed = false;
    private boolean midLoadSoundPlayed = false;
    private static final float START_SOUND_PERCENT = 0.2F;
    private static final float MID_SOUND_PERCENT = 0.5F;
    private static final float ARROW_POWER = 3.15F;
    private static final float FIREWORK_POWER = 1.6F;

    public CrossbowItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return CrossbowItem.ARROW_OR_FIREWORK;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return CrossbowItem.ARROW_ONLY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);

        if (CrossbowItem.isCharged(itemstack)) {
            CrossbowItem.performShooting(world, user, hand, itemstack, CrossbowItem.getShootingPower(itemstack), 1.0F);
            CrossbowItem.setCharged(itemstack, false);
            return InteractionResultHolder.consume(itemstack);
        } else if (!user.getProjectile(itemstack).isEmpty()) {
            if (!CrossbowItem.isCharged(itemstack)) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
                user.startUsingItem(hand);
            }

            return InteractionResultHolder.consume(itemstack);
        } else {
            return InteractionResultHolder.fail(itemstack);
        }
    }

    private static float getShootingPower(ItemStack stack) {
        return CrossbowItem.containsChargedProjectile(stack, Items.FIREWORK_ROCKET) ? 1.6F : 3.15F;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        int j = this.getUseDuration(stack) - remainingUseTicks;
        float f = CrossbowItem.getPowerForTime(j, stack);

        // Paper start - EntityLoadCrossbowEvent
        if (f >= 1.0F && !CrossbowItem.isCharged(stack) /*&& CrossbowItem.tryLoadProjectiles(entityliving, itemstack)*/) {
            final io.papermc.paper.event.entity.EntityLoadCrossbowEvent event = new io.papermc.paper.event.entity.EntityLoadCrossbowEvent(user.getBukkitLivingEntity(), stack.asBukkitMirror(), user.getUsedItemHand() == InteractionHand.MAIN_HAND ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            if (!event.callEvent() || !tryLoadProjectiles(user, stack, event.shouldConsumeItem())) {
                if (user instanceof ServerPlayer player) player.containerMenu.sendAllDataToRemote();
                return;
            }
            // Paper end
            CrossbowItem.setCharged(stack, true);
            SoundSource soundcategory = user instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;

            world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END, soundcategory, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }

    }

    @io.papermc.paper.annotation.DoNotUse // Paper
    private static boolean tryLoadProjectiles(LivingEntity shooter, ItemStack crossbow) {
        // Paper start
        return CrossbowItem.tryLoadProjectiles(shooter, crossbow, true);
    }
    private static boolean tryLoadProjectiles(LivingEntity shooter, ItemStack crossbow, boolean consume) {
        // Paper end
        int i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, crossbow);
        int j = i == 0 ? 1 : 3;
        boolean flag = !consume || shooter instanceof Player && ((Player) shooter).getAbilities().instabuild; // Paper - add consume
        ItemStack itemstack1 = shooter.getProjectile(crossbow);
        ItemStack itemstack2 = itemstack1.copy();

        for (int k = 0; k < j; ++k) {
            if (k > 0) {
                itemstack1 = itemstack2.copy();
            }

            if (itemstack1.isEmpty() && flag) {
                itemstack1 = new ItemStack(Items.ARROW);
                itemstack2 = itemstack1.copy();
            }

            if (!CrossbowItem.loadProjectile(shooter, crossbow, itemstack1, k > 0, flag)) {
                return false;
            }
        }

        return true;
    }

    private static boolean loadProjectile(LivingEntity shooter, ItemStack crossbow, ItemStack projectile, boolean simulated, boolean creative) {
        if (projectile.isEmpty()) {
            return false;
        } else {
            boolean flag2 = creative && projectile.getItem() instanceof ArrowItem;
            ItemStack itemstack2;

            if (!flag2 && !creative && !simulated) {
                itemstack2 = projectile.split(1);
                if (projectile.isEmpty() && shooter instanceof Player) {
                    ((Player) shooter).getInventory().removeItem(projectile);
                }
            } else {
                itemstack2 = projectile.copy();
            }

            CrossbowItem.addChargedProjectile(crossbow, itemstack2);
            return true;
        }
    }

    public static boolean isCharged(ItemStack stack) {
        CompoundTag nbttagcompound = stack.getTag();

        return nbttagcompound != null && nbttagcompound.getBoolean("Charged");
    }

    public static void setCharged(ItemStack stack, boolean charged) {
        CompoundTag nbttagcompound = stack.getOrCreateTag();

        nbttagcompound.putBoolean("Charged", charged);
    }

    private static void addChargedProjectile(ItemStack crossbow, ItemStack projectile) {
        CompoundTag nbttagcompound = crossbow.getOrCreateTag();
        ListTag nbttaglist;

        if (nbttagcompound.contains("ChargedProjectiles", 9)) {
            nbttaglist = nbttagcompound.getList("ChargedProjectiles", 10);
        } else {
            nbttaglist = new ListTag();
        }

        CompoundTag nbttagcompound1 = new CompoundTag();

        projectile.save(nbttagcompound1);
        nbttaglist.add(nbttagcompound1);
        nbttagcompound.put("ChargedProjectiles", nbttaglist);
    }

    private static List<ItemStack> getChargedProjectiles(ItemStack crossbow) {
        List<ItemStack> list = Lists.newArrayList();
        CompoundTag nbttagcompound = crossbow.getTag();

        if (nbttagcompound != null && nbttagcompound.contains("ChargedProjectiles", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("ChargedProjectiles", 10);

            if (nbttaglist != null) {
                for (int i = 0; i < nbttaglist.size(); ++i) {
                    CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);

                    list.add(ItemStack.of(nbttagcompound1));
                }
            }
        }

        return list;
    }

    private static void clearChargedProjectiles(ItemStack crossbow) {
        CompoundTag nbttagcompound = crossbow.getTag();

        if (nbttagcompound != null) {
            ListTag nbttaglist = nbttagcompound.getList("ChargedProjectiles", 9);

            nbttaglist.clear();
            nbttagcompound.put("ChargedProjectiles", nbttaglist);
        }

    }

    public static boolean containsChargedProjectile(ItemStack crossbow, Item projectile) {
        return CrossbowItem.getChargedProjectiles(crossbow).stream().anyMatch((itemstack1) -> {
            return itemstack1.is(projectile);
        });
    }

    private static void shootProjectile(Level world, LivingEntity shooter, InteractionHand hand, ItemStack crossbow, ItemStack projectile, float soundPitch, boolean creative, float speed, float divergence, float simulated) {
        if (!world.isClientSide) {
            boolean flag1 = projectile.is(Items.FIREWORK_ROCKET);
            Object object;

            if (flag1) {
                object = new FireworkRocketEntity(world, projectile, shooter, shooter.getX(), shooter.getEyeY() - 0.15000000596046448D, shooter.getZ(), true);
                ((FireworkRocketEntity) object).spawningEntity = shooter.getUUID(); // Paper
            } else {
                object = CrossbowItem.getArrow(world, shooter, crossbow, projectile);
                if (creative || simulated != 0.0F) {
                    ((AbstractArrow) object).pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
            }

            if (shooter instanceof CrossbowAttackMob) {
                CrossbowAttackMob icrossbow = (CrossbowAttackMob) shooter;

                icrossbow.shootCrossbowProjectile(icrossbow.getTarget(), crossbow, (Projectile) object, simulated);
            } else {
                Vec3 vec3d = shooter.getUpVector(1.0F);
                Quaternionf quaternionf = (new Quaternionf()).setAngleAxis((double) (simulated * 0.017453292F), vec3d.x, vec3d.y, vec3d.z);
                Vec3 vec3d1 = shooter.getViewVector(1.0F);
                Vector3f vector3f = vec3d1.toVector3f().rotate(quaternionf);

                ((Projectile) object).shoot((double) vector3f.x(), (double) vector3f.y(), (double) vector3f.z(), speed, divergence);
            }
            // CraftBukkit start
            org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(shooter, crossbow, projectile, (Entity) object, shooter.getUsedItemHand(), soundPitch, true);
            if (event.isCancelled()) {
                event.getProjectile().remove();
                return;
            }
            // CraftBukkit end

            crossbow.hurtAndBreak(flag1 ? 3 : 1, shooter, (entityliving1) -> {
                entityliving1.broadcastBreakEvent(hand);
            });
            // CraftBukkit start
            if (event.getProjectile() == ((Entity) object).getBukkitEntity()) {
                if (!world.addFreshEntity((Entity) object)) {
                    if (shooter instanceof ServerPlayer) {
                        ((ServerPlayer) shooter).getBukkitEntity().updateInventory();
                    }
                    return;
                }
            }
            // CraftBukkit end
            world.playSound((Player) null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, soundPitch);
        }
    }

    private static AbstractArrow getArrow(Level world, LivingEntity entity, ItemStack crossbow, ItemStack arrow) {
        ArrowItem itemarrow = (ArrowItem) (arrow.getItem() instanceof ArrowItem ? arrow.getItem() : Items.ARROW);
        AbstractArrow entityarrow = itemarrow.createArrow(world, arrow, entity);

        if (entity instanceof Player) {
            entityarrow.setCritArrow(true);
        }

        entityarrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        entityarrow.setShotFromCrossbow(true);
        int i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, crossbow);

        if (i > 0) {
            entityarrow.setPierceLevel((byte) i);
        }

        return entityarrow;
    }

    public static void performShooting(Level world, LivingEntity entity, InteractionHand hand, ItemStack stack, float speed, float divergence) {
        List<ItemStack> list = CrossbowItem.getChargedProjectiles(stack);
        float[] afloat = CrossbowItem.getShotPitches(entity.getRandom());

        for (int i = 0; i < list.size(); ++i) {
            ItemStack itemstack1 = (ItemStack) list.get(i);
            boolean flag = entity instanceof Player && ((Player) entity).getAbilities().instabuild;

            if (!itemstack1.isEmpty()) {
                if (i == 0) {
                    CrossbowItem.shootProjectile(world, entity, hand, stack, itemstack1, afloat[i], flag, speed, divergence, 0.0F);
                } else if (i == 1) {
                    CrossbowItem.shootProjectile(world, entity, hand, stack, itemstack1, afloat[i], flag, speed, divergence, -10.0F);
                } else if (i == 2) {
                    CrossbowItem.shootProjectile(world, entity, hand, stack, itemstack1, afloat[i], flag, speed, divergence, 10.0F);
                }
            }
        }

        CrossbowItem.onCrossbowShot(world, entity, stack);
    }

    private static float[] getShotPitches(RandomSource random) {
        boolean flag = random.nextBoolean();

        return new float[]{1.0F, CrossbowItem.getRandomShotPitch(flag, random), CrossbowItem.getRandomShotPitch(!flag, random)};
    }

    private static float getRandomShotPitch(boolean flag, RandomSource random) {
        float f = flag ? 0.63F : 0.43F;

        return 1.0F / (random.nextFloat() * 0.5F + 1.8F) + f;
    }

    private static void onCrossbowShot(Level world, LivingEntity entity, ItemStack stack) {
        if (entity instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entity;

            if (!world.isClientSide) {
                CriteriaTriggers.SHOT_CROSSBOW.trigger(entityplayer, stack);
            }

            entityplayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        CrossbowItem.clearChargedProjectiles(stack);
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClientSide) {
            int j = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, stack);
            SoundEvent soundeffect = this.getStartSound(j);
            SoundEvent soundeffect1 = j == 0 ? SoundEvents.CROSSBOW_LOADING_MIDDLE : null;
            float f = (float) (stack.getUseDuration() - remainingUseTicks) / (float) CrossbowItem.getChargeDuration(stack);

            if (f < 0.2F) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
            }

            if (f >= 0.2F && !this.startSoundPlayed) {
                this.startSoundPlayed = true;
                world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), soundeffect, SoundSource.PLAYERS, 0.5F, 1.0F);
            }

            if (f >= 0.5F && soundeffect1 != null && !this.midLoadSoundPlayed) {
                this.midLoadSoundPlayed = true;
                world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), soundeffect1, SoundSource.PLAYERS, 0.5F, 1.0F);
            }
        }

    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return CrossbowItem.getChargeDuration(stack) + 3;
    }

    public static int getChargeDuration(ItemStack stack) {
        int i = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.QUICK_CHARGE, stack);

        return i == 0 ? 25 : 25 - 5 * i;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    private SoundEvent getStartSound(int stage) {
        switch (stage) {
            case 1:
                return SoundEvents.CROSSBOW_QUICK_CHARGE_1;
            case 2:
                return SoundEvents.CROSSBOW_QUICK_CHARGE_2;
            case 3:
                return SoundEvents.CROSSBOW_QUICK_CHARGE_3;
            default:
                return SoundEvents.CROSSBOW_LOADING_START;
        }
    }

    private static float getPowerForTime(int useTicks, ItemStack stack) {
        float f = (float) useTicks / (float) CrossbowItem.getChargeDuration(stack);

        if (f > 1.0F) {
            f = 1.0F;
        }

        return f;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        List<ItemStack> list1 = CrossbowItem.getChargedProjectiles(stack);

        if (CrossbowItem.isCharged(stack) && !list1.isEmpty()) {
            ItemStack itemstack1 = (ItemStack) list1.get(0);

            tooltip.add(Component.translatable("item.minecraft.crossbow.projectile").append(CommonComponents.SPACE).append(itemstack1.getDisplayName()));
            if (context.isAdvanced() && itemstack1.is(Items.FIREWORK_ROCKET)) {
                List<Component> list2 = Lists.newArrayList();

                Items.FIREWORK_ROCKET.appendHoverText(itemstack1, world, list2, context);
                if (!list2.isEmpty()) {
                    for (int i = 0; i < list2.size(); ++i) {
                        list2.set(i, Component.literal("  ").append((Component) list2.get(i)).withStyle(ChatFormatting.GRAY));
                    }

                    tooltip.addAll(list2);
                }
            }

        }
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return stack.is((Item) this);
    }

    @Override
    public int getDefaultProjectileRange() {
        return 8;
    }
}
