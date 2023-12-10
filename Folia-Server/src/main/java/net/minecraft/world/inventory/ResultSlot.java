package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public class ResultSlot extends Slot {
    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ResultSlot(Player player, CraftingContainer input, Container inventory, int index, int x, int y) {
        super(inventory, index, x, y);
        this.player = player;
        this.craftSlots = input;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount += Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void onSwapCraft(int amount) {
        this.removeCount += amount;
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        if (this.removeCount > 0) {
            stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
        }

        Container var3 = this.container;
        if (var3 instanceof RecipeCraftingHolder recipeCraftingHolder) {
            recipeCraftingHolder.awardUsedRecipes(this.player, this.craftSlots.getItems());
        }

        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        this.checkTakeAchievements(stack);
        NonNullList<ItemStack> nonNullList = player.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, this.craftSlots, player.level(), this.craftSlots.getCurrentRecipe() != null ? this.craftSlots.getCurrentRecipe().id() : null); // Paper - check last recipe used first

        for(int i = 0; i < nonNullList.size(); ++i) {
            ItemStack itemStack = this.craftSlots.getItem(i);
            ItemStack itemStack2 = nonNullList.get(i);
            if (!itemStack.isEmpty()) {
                this.craftSlots.removeItem(i, 1);
                itemStack = this.craftSlots.getItem(i);
            }

            if (!itemStack2.isEmpty()) {
                if (itemStack.isEmpty()) {
                    this.craftSlots.setItem(i, itemStack2);
                } else if (ItemStack.isSameItemSameTags(itemStack, itemStack2)) {
                    itemStack2.grow(itemStack.getCount());
                    this.craftSlots.setItem(i, itemStack2);
                } else if (!this.player.getInventory().add(itemStack2)) {
                    this.player.drop(itemStack2, false);
                }
            }
        }

    }
}
