package io.papermc.paper.potion;

import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@DefaultQualifier(NonNull.class)
record PredicateRecipeChoice(Predicate<ItemStack> itemStackPredicate) implements RecipeChoice, Cloneable {

    @Override
    @Deprecated
    public ItemStack getItemStack() {
        throw new UnsupportedOperationException("PredicateRecipeChoice does not support this");
    }

    @Override
    public RecipeChoice clone() {
        try {
            return (PredicateRecipeChoice) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public boolean test(final ItemStack itemStack) {
        return this.itemStackPredicate.test(itemStack);
    }
}
