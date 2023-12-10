package org.bukkit.inventory;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a smithing recipe.
 */
public class SmithingRecipe implements Recipe, Keyed {

    private final NamespacedKey key;
    private final ItemStack result;
    private final RecipeChoice base;
    private final RecipeChoice addition;
    private final boolean copyNbt; // Paper

    /**
     * Create a smithing recipe to produce the specified result ItemStack.
     *
     * @param key The unique recipe key
     * @param result The item you want the recipe to create.
     * @param base The base ingredient
     * @param addition The addition ingredient
     * @deprecated as of Minecraft 1.20, smithing recipes are now separated into two
     * distinct recipe types, {@link SmithingTransformRecipe} and {@link SmithingTrimRecipe}.
     * This class now acts as a base class to these two classes and will do nothing when
     * added to the server.
     */
    @Deprecated
    public SmithingRecipe(@NotNull NamespacedKey key, @NotNull ItemStack result, @NotNull RecipeChoice base, @NotNull RecipeChoice addition) {
        // Paper start
        this(key, result, base, addition, true);
    }
    /**
     * Create a smithing recipe to produce the specified result ItemStack.
     *
     * @param key The unique recipe key
     * @param result The item you want the recipe to create.
     * @param base The base ingredient
     * @param addition The addition ingredient
     * @param copyNbt whether to copy the nbt from the input base item to the output
     * @deprecated use {@link SmithingTrimRecipe} or {@link SmithingTransformRecipe}
     */
    @Deprecated
    public SmithingRecipe(@NotNull NamespacedKey key, @NotNull ItemStack result, @NotNull RecipeChoice base, @NotNull RecipeChoice addition, boolean copyNbt) {
        this.copyNbt = copyNbt;
        // Paper end
        this.key = key;
        this.result = result;
        this.base = base;
        this.addition = addition;
    }

    /**
     * Get the base recipe item.
     *
     * @return base choice
     */
    @NotNull
    public RecipeChoice getBase() {
        return base.clone();
    }

    /**
     * Get the addition recipe item.
     *
     * @return addition choice
     */
    @NotNull
    public RecipeChoice getAddition() {
        return addition.clone();
    }

    @NotNull
    @Override
    public ItemStack getResult() {
        return result.clone();
    }

    @NotNull
    @Override
    public NamespacedKey getKey() {
        return this.key;
    }

    // Paper start
    /**
     * Whether or not to copy the NBT of the input base item to the output.
     *
     * @return true to copy the NBT (default for vanilla smithing recipes)
     */
    public boolean willCopyNbt() {
        return copyNbt;
    }
    // Paper end
}
