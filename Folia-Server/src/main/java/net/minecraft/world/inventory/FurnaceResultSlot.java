package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class FurnaceResultSlot extends Slot {

    private final Player player;
    private int removeCount;

    public FurnaceResultSlot(Player player, Container inventory, int index, int x, int y) {
        super(inventory, index, x, y);
        this.player = player;
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
    public void onTake(Player player, ItemStack stack) {
        this.checkTakeAchievements(stack);
        super.onTake(player, stack);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
        Player entityhuman = this.player;

        if (entityhuman instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entityhuman;
            Container iinventory = this.container;

            if (iinventory instanceof AbstractFurnaceBlockEntity) {
                AbstractFurnaceBlockEntity tileentityfurnace = (AbstractFurnaceBlockEntity) iinventory;

                tileentityfurnace.awardUsedRecipesAndPopExperience(entityplayer, stack, this.removeCount); // CraftBukkit
            }
        }

        this.removeCount = 0;
    }
}
