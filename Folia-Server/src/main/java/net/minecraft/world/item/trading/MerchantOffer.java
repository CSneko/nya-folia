package net.minecraft.world.item.trading;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe; // CraftBukkit

public class MerchantOffer {

    public ItemStack baseCostA;
    public ItemStack costB;
    public final ItemStack result;
    public int uses;
    public int maxUses;
    public boolean rewardExp;
    public int specialPriceDiff;
    public int demand;
    public float priceMultiplier;
    public int xp;
    public boolean ignoreDiscounts; // Paper
    // CraftBukkit start
    private CraftMerchantRecipe bukkitHandle;

    public CraftMerchantRecipe asBukkit() {
        return (this.bukkitHandle == null) ? this.bukkitHandle = new CraftMerchantRecipe(this) : this.bukkitHandle;
    }

    public MerchantOffer(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2, int uses, int maxUses, int experience, float priceMultiplier, CraftMerchantRecipe bukkit) {
        this(itemstack, itemstack1, itemstack2, uses, maxUses, experience, priceMultiplier, 0, bukkit);
    }

    public MerchantOffer(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2, int uses, int maxUses, int experience, float priceMultiplier, int demand, CraftMerchantRecipe bukkit) {
        // Paper start - add ignoreDiscounts param
        this(itemstack, itemstack1, itemstack2, uses, maxUses, experience, priceMultiplier, demand, false, bukkit);
    }
    public MerchantOffer(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2, int uses, int maxUses, int experience, float priceMultiplier, boolean ignoreDiscounts, CraftMerchantRecipe bukkit) {
        this(itemstack, itemstack1, itemstack2, uses, maxUses, experience, priceMultiplier, 0, ignoreDiscounts, bukkit);
    }
    public MerchantOffer(ItemStack itemstack, ItemStack itemstack1, ItemStack itemstack2, int uses, int maxUses, int experience, float priceMultiplier, int demand, boolean ignoreDiscounts, CraftMerchantRecipe bukkit) {
        this(itemstack, itemstack1, itemstack2, uses, maxUses, experience, priceMultiplier, demand, ignoreDiscounts);
        // Paper end
        this.bukkitHandle = bukkit;
    }
    // CraftBukkit end

    public MerchantOffer(CompoundTag nbt) {
        this.rewardExp = true;
        this.xp = 1;
        this.baseCostA = ItemStack.of(nbt.getCompound("buy"));
        this.costB = ItemStack.of(nbt.getCompound("buyB"));
        this.result = ItemStack.of(nbt.getCompound("sell"));
        this.uses = nbt.getInt("uses");
        if (nbt.contains("maxUses", 99)) {
            this.maxUses = nbt.getInt("maxUses");
        } else {
            this.maxUses = 4;
        }

        if (nbt.contains("rewardExp", 1)) {
            this.rewardExp = nbt.getBoolean("rewardExp");
        }

        if (nbt.contains("xp", 3)) {
            this.xp = nbt.getInt("xp");
        }

        if (nbt.contains("priceMultiplier", 5)) {
            this.priceMultiplier = nbt.getFloat("priceMultiplier");
        }

        this.specialPriceDiff = nbt.getInt("specialPrice");
        this.demand = nbt.getInt("demand");
        this.ignoreDiscounts = nbt.getBoolean("Paper.IgnoreDiscounts"); // Paper
    }

    public MerchantOffer(ItemStack buyItem, ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier) {
        this(buyItem, ItemStack.EMPTY, sellItem, maxUses, merchantExperience, priceMultiplier);
    }

    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier) {
        this(firstBuyItem, secondBuyItem, sellItem, 0, maxUses, merchantExperience, priceMultiplier);
    }

    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier) {
        // Paper start - add ignoreDiscounts param
        this(firstBuyItem, secondBuyItem, sellItem, uses, maxUses, merchantExperience, priceMultiplier, false);
    }
    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier, boolean ignoreDiscounts) {
        this(firstBuyItem, secondBuyItem, sellItem, uses, maxUses, merchantExperience, priceMultiplier, 0, ignoreDiscounts);
    }

    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier, int demandBonus) {
        this(firstBuyItem, secondBuyItem, sellItem, uses, maxUses, merchantExperience, priceMultiplier, demandBonus, false);
    }
    public MerchantOffer(ItemStack firstBuyItem, ItemStack secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier, int demandBonus, boolean ignoreDiscounts) {
        this.ignoreDiscounts = ignoreDiscounts;
        // Paper end
        this.rewardExp = true;
        this.xp = 1;
        this.baseCostA = firstBuyItem;
        this.costB = secondBuyItem;
        this.result = sellItem;
        this.uses = uses;
        this.maxUses = maxUses;
        this.xp = merchantExperience;
        this.priceMultiplier = priceMultiplier;
        this.demand = demandBonus;
    }

    private MerchantOffer(MerchantOffer offer) {
        this.rewardExp = true;
        this.xp = 1;
        this.baseCostA = offer.baseCostA.copy();
        this.costB = offer.costB.copy();
        this.result = offer.result.copy();
        this.uses = offer.uses;
        this.maxUses = offer.maxUses;
        this.rewardExp = offer.rewardExp;
        this.specialPriceDiff = offer.specialPriceDiff;
        this.demand = offer.demand;
        this.priceMultiplier = offer.priceMultiplier;
        this.xp = offer.xp;
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA;
    }

    public ItemStack getCostA() {
        if (this.baseCostA.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            int i = this.baseCostA.getCount();
            if (i <= 0) return ItemStack.EMPTY; // CraftBukkit - SPIGOT-5476
            int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));

            return this.baseCostA.copyWithCount(Mth.clamp(i + j + this.specialPriceDiff, 1, this.baseCostA.getItem().getMaxStackSize()));
        }
    }

    public ItemStack getCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = Math.max(0, this.demand + this.uses - (this.maxUses - this.uses)); // Paper
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        ++this.uses;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int increment) {
        this.specialPriceDiff += increment;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int specialPrice) {
        this.specialPriceDiff = specialPrice;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public CompoundTag createTag() {
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.put("buy", this.baseCostA.save(new CompoundTag()));
        nbttagcompound.put("sell", this.result.save(new CompoundTag()));
        nbttagcompound.put("buyB", this.costB.save(new CompoundTag()));
        nbttagcompound.putInt("uses", this.uses);
        nbttagcompound.putInt("maxUses", this.maxUses);
        nbttagcompound.putBoolean("rewardExp", this.rewardExp);
        nbttagcompound.putInt("xp", this.xp);
        nbttagcompound.putFloat("priceMultiplier", this.priceMultiplier);
        nbttagcompound.putInt("specialPrice", this.specialPriceDiff);
        nbttagcompound.putInt("demand", this.demand);
        nbttagcompound.putBoolean("Paper.IgnoreDiscounts", this.ignoreDiscounts); // Paper
        return nbttagcompound;
    }

    public boolean satisfiedBy(ItemStack first, ItemStack second) {
        return this.isRequiredItem(first, this.getCostA()) && first.getCount() >= this.getCostA().getCount() && this.isRequiredItem(second, this.costB) && second.getCount() >= this.costB.getCount();
    }

    private boolean isRequiredItem(ItemStack given, ItemStack sample) {
        if (sample.isEmpty() && given.isEmpty()) {
            return true;
        } else {
            ItemStack itemstack2 = given.copy();

            if (itemstack2.getItem().canBeDepleted()) {
                itemstack2.setDamageValue(itemstack2.getDamageValue());
            }

            return ItemStack.isSameItem(itemstack2, sample) && (!sample.hasTag() || itemstack2.hasTag() && NbtUtils.compareNbt(sample.getTag(), itemstack2.getTag(), false));
        }
    }

    public boolean take(ItemStack firstBuyStack, ItemStack secondBuyStack) {
        if (!this.satisfiedBy(firstBuyStack, secondBuyStack)) {
            return false;
        } else {
            // CraftBukkit start
            if (!this.getCostA().isEmpty()) {
                firstBuyStack.shrink(this.getCostA().getCount());
            }
            // CraftBukkit end
            if (!this.getCostB().isEmpty()) {
                secondBuyStack.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }

    public MerchantOffer copy() {
        return new MerchantOffer(this);
    }
}
