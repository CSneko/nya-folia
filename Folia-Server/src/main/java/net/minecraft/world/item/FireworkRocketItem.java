package net.minecraft.world.item;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FireworkRocketItem extends Item {
    public static final byte[] CRAFTABLE_DURATIONS = new byte[]{1, 2, 3};
    public static final String TAG_FIREWORKS = "Fireworks";
    public static final String TAG_EXPLOSION = "Explosion";
    public static final String TAG_EXPLOSIONS = "Explosions";
    public static final String TAG_FLIGHT = "Flight";
    public static final String TAG_EXPLOSION_TYPE = "Type";
    public static final String TAG_EXPLOSION_TRAIL = "Trail";
    public static final String TAG_EXPLOSION_FLICKER = "Flicker";
    public static final String TAG_EXPLOSION_COLORS = "Colors";
    public static final String TAG_EXPLOSION_FADECOLORS = "FadeColors";
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15D;

    public FireworkRocketItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            ItemStack itemStack = context.getItemInHand();
            Vec3 vec3 = context.getClickLocation();
            Direction direction = context.getClickedFace();
            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(level, context.getPlayer(), vec3.x + (double)direction.getStepX() * 0.15D, vec3.y + (double)direction.getStepY() * 0.15D, vec3.z + (double)direction.getStepZ() * 0.15D, itemStack);
            fireworkRocketEntity.spawningEntity = context.getPlayer() == null ? null : context.getPlayer().getUUID(); // Paper
            // Paper start - PlayerLaunchProjectileEvent
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) context.getPlayer().getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack), (org.bukkit.entity.Firework) fireworkRocketEntity.getBukkitEntity());
            if (!event.callEvent() || !level.addFreshEntity(fireworkRocketEntity)) return InteractionResult.PASS;
            if (event.shouldConsume() && !context.getPlayer().getAbilities().instabuild) itemStack.shrink(1);
            else if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer) ((net.minecraft.server.level.ServerPlayer) context.getPlayer()).getBukkitEntity().updateInventory();
            // Paper end
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user.isFallFlying()) {
            ItemStack itemStack = user.getItemInHand(hand);
            if (!world.isClientSide) {
                FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(world, itemStack, user);
                fireworkRocketEntity.spawningEntity = user.getUUID(); // Paper
                // Paper start
                com.destroystokyo.paper.event.player.PlayerElytraBoostEvent event = new com.destroystokyo.paper.event.player.PlayerElytraBoostEvent((org.bukkit.entity.Player) user.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack), (org.bukkit.entity.Firework) fireworkRocketEntity.getBukkitEntity());
                if (event.callEvent() && world.addFreshEntity(fireworkRocketEntity)) {
                    user.awardStat(Stats.ITEM_USED.get(this));
                    if (event.shouldConsume() && !user.getAbilities().instabuild) {
                    itemStack.shrink(1);
                    } else ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                } else if (user instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                    // Paper end
                }

                // user.awardStat(Stats.ITEM_USED.get(this)); // Paper - move up
            }

            return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide());
        } else {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        CompoundTag compoundTag = stack.getTagElement("Fireworks");
        if (compoundTag != null) {
            if (compoundTag.contains("Flight", 99)) {
                tooltip.add(Component.translatable("item.minecraft.firework_rocket.flight").append(CommonComponents.SPACE).append(String.valueOf((int)compoundTag.getByte("Flight"))).withStyle(ChatFormatting.GRAY));
            }

            ListTag listTag = compoundTag.getList("Explosions", 10);
            if (!listTag.isEmpty()) {
                for(int i = 0; i < listTag.size(); ++i) {
                    CompoundTag compoundTag2 = listTag.getCompound(i);
                    List<Component> list = Lists.newArrayList();
                    FireworkStarItem.appendHoverText(compoundTag2, list);
                    if (!list.isEmpty()) {
                        for(int j = 1; j < list.size(); ++j) {
                            list.set(j, Component.literal("  ").append(list.get(j)).withStyle(ChatFormatting.GRAY));
                        }

                        tooltip.addAll(list);
                    }
                }
            }

        }
    }

    public static void setDuration(ItemStack stack, byte flight) {
        stack.getOrCreateTagElement("Fireworks").putByte("Flight", flight);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack itemStack = new ItemStack(this);
        setDuration(itemStack, (byte)1);
        return itemStack;
    }

    public static enum Shape {
        SMALL_BALL(0, "small_ball"),
        LARGE_BALL(1, "large_ball"),
        STAR(2, "star"),
        CREEPER(3, "creeper"),
        BURST(4, "burst");

        private static final IntFunction<FireworkRocketItem.Shape> BY_ID = ByIdMap.continuous(FireworkRocketItem.Shape::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private final int id;
        private final String name;

        private Shape(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public static FireworkRocketItem.Shape byId(int id) {
            return BY_ID.apply(id);
        }
    }
}
