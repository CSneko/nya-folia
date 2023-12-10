package net.minecraft.world.item.crafting;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public abstract class CustomRecipe implements CraftingRecipe {

    private final CraftingBookCategory category;

    public CustomRecipe(CraftingBookCategory category) {
        this.category = category;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        return new org.bukkit.craftbukkit.inventory.CraftComplexRecipe(id, this);
    }
    // CraftBukkit end
}
