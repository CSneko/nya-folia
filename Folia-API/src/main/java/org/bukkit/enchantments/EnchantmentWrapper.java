package org.bukkit.enchantments;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A simple wrapper for ease of selecting {@link Enchantment}s
 */
public class EnchantmentWrapper extends Enchantment {
    public EnchantmentWrapper(@NotNull String name) {
        super(NamespacedKey.minecraft(name));
    }

    /**
     * Gets the enchantment bound to this wrapper
     *
     * @return Enchantment
     */
    @NotNull
    public Enchantment getEnchantment() {
        return Enchantment.getByKey(getKey());
    }

    @Override
    public int getMaxLevel() {
        return getEnchantment().getMaxLevel();
    }

    @Override
    public int getStartLevel() {
        return getEnchantment().getStartLevel();
    }

    @NotNull
    @Override
    public EnchantmentTarget getItemTarget() {
        return getEnchantment().getItemTarget();
    }

    @Override
    public boolean canEnchantItem(@NotNull ItemStack item) {
        return getEnchantment().canEnchantItem(item);
    }

    @NotNull
    @Override
    public String getName() {
        return getEnchantment().getName();
    }

    @Override
    public boolean isTreasure() {
        return getEnchantment().isTreasure();
    }

    @Override
    public boolean isCursed() {
        return getEnchantment().isCursed();
    }

    @Override
    public boolean conflictsWith(@NotNull Enchantment other) {
        return getEnchantment().conflictsWith(other);
    }
    // Paper start
    @NotNull
    @Override
    public net.kyori.adventure.text.Component displayName(int level) {
        return getEnchantment().displayName(level);
    }

    @Override
    public @NotNull String translationKey() {
        return getEnchantment().translationKey();
    }

    @Override
    public boolean isTradeable() {
        return getEnchantment().isTradeable();
    }

    @Override
    public boolean isDiscoverable() {
        return getEnchantment().isDiscoverable();
    }

    @Override
    public int getMinModifiedCost(int level) {
        return getEnchantment().getMinModifiedCost(level);
    }

    @Override
    public int getMaxModifiedCost(int level) {
        return getEnchantment().getMaxModifiedCost(level);
    }

    @NotNull
    @Override
    public io.papermc.paper.enchantments.EnchantmentRarity getRarity() {
        return getEnchantment().getRarity();
    }

    @Override
    public float getDamageIncrease(int level, @NotNull org.bukkit.entity.EntityCategory entityCategory) {
        return getEnchantment().getDamageIncrease(level, entityCategory);
    }

    @NotNull
    @Override
    public java.util.Set<org.bukkit.inventory.EquipmentSlot> getActiveSlots() {
        return getEnchantment().getActiveSlots();
    }
    // Paper end
}
