package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.event.hanging.HangingPlaceEvent;
// CraftBukkit end

public class HangingEntityItem extends Item {

    private static final Component TOOLTIP_RANDOM_VARIANT = Component.translatable("painting.random").withStyle(ChatFormatting.GRAY);
    private final EntityType<? extends HangingEntity> type;

    public HangingEntityItem(EntityType<? extends HangingEntity> type, Item.Properties settings) {
        super(settings);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockposition = context.getClickedPos();
        Direction enumdirection = context.getClickedFace();
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        net.minecraft.world.entity.player.Player entityhuman = context.getPlayer();
        ItemStack itemstack = context.getItemInHand();

        if (entityhuman != null && !this.mayPlace(entityhuman, enumdirection, itemstack, blockposition1)) {
            return InteractionResult.FAIL;
        } else {
            Level world = context.getLevel();
            Object object;

            if (this.type == EntityType.PAINTING) {
                Optional<Painting> optional = Painting.create(world, blockposition1, enumdirection);

                if (optional.isEmpty()) {
                    return InteractionResult.CONSUME;
                }

                object = (HangingEntity) optional.get();
            } else if (this.type == EntityType.ITEM_FRAME) {
                object = new ItemFrame(world, blockposition1, enumdirection);
            } else {
                if (this.type != EntityType.GLOW_ITEM_FRAME) {
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }

                object = new GlowItemFrame(world, blockposition1, enumdirection);
            }

            CompoundTag nbttagcompound = itemstack.getTag();

            if (nbttagcompound != null) {
                EntityType.updateCustomEntityTag(world, entityhuman, (Entity) object, nbttagcompound);
            }

            if (((HangingEntity) object).survives()) {
                if (!world.isClientSide) {
                    // CraftBukkit start - fire HangingPlaceEvent
                    Player who = (context.getPlayer() == null) ? null : (Player) context.getPlayer().getBukkitEntity();
                    org.bukkit.block.Block blockClicked = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(enumdirection);
                    org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand());

                    HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) ((HangingEntity) object).getBukkitEntity(), who, blockClicked, blockFace, hand, org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack));
                    world.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    ((HangingEntity) object).playPlacementSound();
                    world.gameEvent((Entity) entityhuman, GameEvent.ENTITY_PLACE, ((HangingEntity) object).position());
                    world.addFreshEntity((Entity) object);
                }

                itemstack.shrink(1);
                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.CONSUME;
            }
        }
    }

    protected boolean mayPlace(net.minecraft.world.entity.player.Player player, Direction side, ItemStack stack, BlockPos pos) {
        return !side.getAxis().isVertical() && player.mayUseItemAt(pos, side, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, tooltip, context);
        if (this.type == EntityType.PAINTING) {
            CompoundTag nbttagcompound = stack.getTag();

            if (nbttagcompound != null && nbttagcompound.contains("EntityTag", 10)) {
                CompoundTag nbttagcompound1 = nbttagcompound.getCompound("EntityTag");

                Painting.loadVariant(nbttagcompound1).ifPresentOrElse((holder) -> {
                    holder.unwrapKey().ifPresent((resourcekey) -> {
                        tooltip.add(Component.translatable(resourcekey.location().toLanguageKey("painting", "title")).withStyle(ChatFormatting.YELLOW));
                        tooltip.add(Component.translatable(resourcekey.location().toLanguageKey("painting", "author")).withStyle(ChatFormatting.GRAY));
                    });
                    tooltip.add(Component.translatable("painting.dimensions", Mth.positiveCeilDiv(((PaintingVariant) holder.value()).getWidth(), 16), Mth.positiveCeilDiv(((PaintingVariant) holder.value()).getHeight(), 16)));
                }, () -> {
                    tooltip.add(HangingEntityItem.TOOLTIP_RANDOM_VARIANT);
                });
            } else if (context.isCreative()) {
                tooltip.add(HangingEntityItem.TOOLTIP_RANDOM_VARIANT);
            }
        }

    }
}
