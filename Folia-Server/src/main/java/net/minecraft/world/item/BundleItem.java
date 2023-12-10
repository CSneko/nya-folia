package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;

public class BundleItem extends Item {
    private static final String TAG_ITEMS = "Items";
    public static final int MAX_WEIGHT = 64;
    private static final int BUNDLE_IN_BUNDLE_WEIGHT = 4;
    private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);

    public BundleItem(Item.Properties settings) {
        super(settings);
    }

    public static float getFullnessDisplay(ItemStack stack) {
        return (float)getContentWeight(stack) / 64.0F;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction clickType, Player player) {
        if (clickType != ClickAction.SECONDARY) {
            return false;
        } else {
            ItemStack itemStack = slot.getItem();
            if (itemStack.isEmpty()) {
                this.playRemoveOneSound(player);
                removeOne(stack).ifPresent((removedStack) -> {
                    add(stack, slot.safeInsert(removedStack));
                });
            } else if (itemStack.getItem().canFitInsideContainerItems()) {
                int i = (64 - getContentWeight(stack)) / getWeight(itemStack);
                int j = add(stack, slot.safeTake(itemStack.getCount(), Math.max(0, i), player)); // Paper - prevent item addition on overfilled bundles - safeTake will yield EMPTY for amount == 0.
                if (j > 0) {
                    this.playInsertSound(player);
                }
            }

            return true;
        }
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack otherStack, Slot slot, ClickAction clickType, Player player, SlotAccess cursorStackReference) {
        if (clickType == ClickAction.SECONDARY && slot.allowModification(player)) {
            if (otherStack.isEmpty()) {
                removeOne(stack).ifPresent((itemStack) -> {
                    this.playRemoveOneSound(player);
                    cursorStackReference.set(itemStack);
                });
            } else {
                int i = add(stack, otherStack);
                if (i > 0) {
                    this.playInsertSound(player);
                    otherStack.shrink(i);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (dropContents(itemStack, user)) {
            this.playDropContentsSound(user);
            user.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
        } else {
            return InteractionResultHolder.fail(itemStack);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getContentWeight(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(1 + 12 * getContentWeight(stack) / 64, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    private static int add(ItemStack bundle, ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem().canFitInsideContainerItems()) {
            CompoundTag compoundTag = bundle.getOrCreateTag();
            if (!compoundTag.contains("Items")) {
                compoundTag.put("Items", new ListTag());
            }

            int i = getContentWeight(bundle);
            int j = getWeight(stack);
            int k = Math.min(stack.getCount(), (64 - i) / j);
            if (k <= 0) { // Paper - prevent item addition on overfilled bundles
                return 0;
            } else {
                ListTag listTag = compoundTag.getList("Items", 10);
                Optional<CompoundTag> optional = getMatchingItem(stack, listTag);
                if (optional.isPresent()) {
                    CompoundTag compoundTag2 = optional.get();
                    ItemStack itemStack = ItemStack.of(compoundTag2);
                    itemStack.grow(k);
                    itemStack.save(compoundTag2);
                    listTag.remove(compoundTag2);
                    listTag.add(0, (Tag)compoundTag2);
                } else {
                    ItemStack itemStack2 = stack.copyWithCount(k);
                    CompoundTag compoundTag3 = new CompoundTag();
                    itemStack2.save(compoundTag3);
                    listTag.add(0, (Tag)compoundTag3);
                }

                return k;
            }
        } else {
            return 0;
        }
    }

    private static Optional<CompoundTag> getMatchingItem(ItemStack stack, ListTag items) {
        return stack.is(Items.BUNDLE) ? Optional.empty() : items.stream().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).filter((item) -> {
            return ItemStack.isSameItemSameTags(ItemStack.of(item), stack);
        }).findFirst();
    }

    private static int getWeight(ItemStack stack) {
        if (stack.is(Items.BUNDLE)) {
            return 4 + getContentWeight(stack);
        } else {
            if ((stack.is(Items.BEEHIVE) || stack.is(Items.BEE_NEST)) && stack.hasTag()) {
                CompoundTag compoundTag = BlockItem.getBlockEntityData(stack);
                if (compoundTag != null && !compoundTag.getList("Bees", 10).isEmpty()) {
                    return 64;
                }
            }

            return 64 / stack.getMaxStackSize();
        }
    }

    private static int getContentWeight(ItemStack stack) {
        return getContents(stack).mapToInt((itemStack) -> {
            return getWeight(itemStack) * itemStack.getCount();
        }).sum();
    }

    private static Optional<ItemStack> removeOne(ItemStack stack) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (!compoundTag.contains("Items")) {
            return Optional.empty();
        } else {
            ListTag listTag = compoundTag.getList("Items", 10);
            if (listTag.isEmpty()) {
                return Optional.empty();
            } else {
                int i = 0;
                CompoundTag compoundTag2 = listTag.getCompound(0);
                ItemStack itemStack = ItemStack.of(compoundTag2);
                listTag.remove(0);
                if (listTag.isEmpty()) {
                    stack.removeTagKey("Items");
                }

                return Optional.of(itemStack);
            }
        }
    }

    private static boolean dropContents(ItemStack stack, Player player) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (!compoundTag.contains("Items")) {
            return false;
        } else {
            if (player instanceof ServerPlayer) {
                ListTag listTag = compoundTag.getList("Items", 10);

                for(int i = 0; i < listTag.size(); ++i) {
                    CompoundTag compoundTag2 = listTag.getCompound(i);
                    ItemStack itemStack = ItemStack.of(compoundTag2);
                    player.drop(itemStack, true);
                }
            }

            stack.removeTagKey("Items");
            return true;
        }
    }

    private static Stream<ItemStack> getContents(ItemStack stack) {
        CompoundTag compoundTag = stack.getTag();
        if (compoundTag == null) {
            return Stream.empty();
        } else {
            ListTag listTag = compoundTag.getList("Items", 10);
            return listTag.stream().map(CompoundTag.class::cast).map(ItemStack::of);
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        NonNullList<ItemStack> nonNullList = NonNullList.create();
        getContents(stack).forEach(nonNullList::add);
        return Optional.of(new BundleTooltip(nonNullList, getContentWeight(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
        tooltip.add(Component.translatable("item.minecraft.bundle.fullness", getContentWeight(stack), 64).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        ItemUtils.onContainerDestroyed(entity, getContents(entity.getItem()));
    }

    private void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playDropContentsSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }
}
