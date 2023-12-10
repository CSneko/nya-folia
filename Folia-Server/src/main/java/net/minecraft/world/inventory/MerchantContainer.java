package net.minecraft.world.inventory;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftAbstractVillager;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class MerchantContainer implements Container {

    private final Merchant merchant;
    private final NonNullList<ItemStack> itemStacks;
    @Nullable
    private MerchantOffer activeOffer;
    public int selectionHint;
    private int futureXp;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.itemStacks;
    }

    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
        this.merchant.setTradingPlayer((Player) null); // SPIGOT-4860
    }

    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int i) {
        this.maxStack = i;
    }

    public org.bukkit.inventory.InventoryHolder getOwner() {
        return (this.merchant instanceof AbstractVillager) ? (CraftAbstractVillager) ((AbstractVillager) this.merchant).getBukkitEntity() : null;
    }

    @Override
    public Location getLocation() {
        return (this.merchant instanceof AbstractVillager) ? ((AbstractVillager) this.merchant).getBukkitEntity().getLocation() : null; // Paper
    }
    // CraftBukkit end

    public MerchantContainer(Merchant merchant) {
        this.itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);
        this.merchant = merchant;
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.itemStacks.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        return (ItemStack) this.itemStacks.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = (ItemStack) this.itemStacks.get(slot);

        if (slot == 2 && !itemstack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, slot, itemstack.getCount());
        } else {
            ItemStack itemstack1 = ContainerHelper.removeItem(this.itemStacks, slot, amount);

            if (!itemstack1.isEmpty() && this.isPaymentSlot(slot)) {
                this.updateSellItem();
            }

            return itemstack1;
        }
    }

    private boolean isPaymentSlot(int slot) {
        return slot == 0 || slot == 1;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.itemStacks, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.itemStacks.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        if (this.isPaymentSlot(slot)) {
            this.updateSellItem();
        }

    }

    @Override
    public boolean stillValid(Player player) {
        return this.merchant.getTradingPlayer() == player;
    }

    @Override
    public void setChanged() {
        this.updateSellItem();
    }

    public void updateSellItem() {
        this.activeOffer = null;
        ItemStack itemstack;
        ItemStack itemstack1;

        if (((ItemStack) this.itemStacks.get(0)).isEmpty()) {
            itemstack = (ItemStack) this.itemStacks.get(1);
            itemstack1 = ItemStack.EMPTY;
        } else {
            itemstack = (ItemStack) this.itemStacks.get(0);
            itemstack1 = (ItemStack) this.itemStacks.get(1);
        }

        if (itemstack.isEmpty()) {
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        } else {
            MerchantOffers merchantrecipelist = this.merchant.getOffers();

            if (!merchantrecipelist.isEmpty()) {
                MerchantOffer merchantrecipe = merchantrecipelist.getRecipeFor(itemstack, itemstack1, this.selectionHint);

                if (merchantrecipe == null || merchantrecipe.isOutOfStock()) {
                    this.activeOffer = merchantrecipe;
                    merchantrecipe = merchantrecipelist.getRecipeFor(itemstack1, itemstack, this.selectionHint);
                }

                if (merchantrecipe != null && !merchantrecipe.isOutOfStock()) {
                    this.activeOffer = merchantrecipe;
                    this.setItem(2, merchantrecipe.assemble());
                    this.futureXp = merchantrecipe.getXp();
                } else {
                    this.setItem(2, ItemStack.EMPTY);
                    this.futureXp = 0;
                }
            }

            this.merchant.notifyTradeUpdated(this.getItem(2));
        }
    }

    @Nullable
    public MerchantOffer getActiveOffer() {
        return this.activeOffer;
    }

    public void setSelectionHint(int index) {
        this.selectionHint = index;
        this.updateSellItem();
    }

    @Override
    public void clearContent() {
        this.itemStacks.clear();
    }

    public int getFutureXp() {
        return this.futureXp;
    }
}
