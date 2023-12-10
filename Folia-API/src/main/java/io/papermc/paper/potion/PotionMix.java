package io.papermc.paper.potion;

import java.util.function.Predicate;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a potion mix made in a Brewing Stand.
 */
@ApiStatus.NonExtendable
public class PotionMix implements Keyed {

    private final NamespacedKey key;
    private final ItemStack result;
    private final RecipeChoice input;
    private final RecipeChoice ingredient;

    /**
     * Creates a new potion mix. Add it to the server with {@link org.bukkit.potion.PotionBrewer#addPotionMix(PotionMix)}.
     *
     * @param key a unique key for the mix
     * @param result the resulting itemstack that will appear in the 3 bottom slots
     * @param input the input placed into the bottom 3 slots
     * @param ingredient the ingredient placed into the top slot
     */
    public PotionMix(@NotNull NamespacedKey key, @NotNull ItemStack result, @NotNull RecipeChoice input, @NotNull RecipeChoice ingredient) {
        this.key = key;
        this.result = result;
        this.input = input;
        this.ingredient = ingredient;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return this.key;
    }

    /**
     * Gets the resulting itemstack after the brew has finished.
     *
     * @return the result itemstack
     */
    public @NotNull ItemStack getResult() {
        return this.result;
    }

    /**
     * Gets the input for the bottom 3 slots in the brewing stand.
     *
     * @return the bottom 3 slot ingredients
     */
    public @NotNull RecipeChoice getInput() {
        return this.input;
    }

    /**
     * Gets the ingredient in the top slot of the brewing stand.
     *
     * @return the top slot input
     */
    public @NotNull RecipeChoice getIngredient() {
        return this.ingredient;
    }

    /**
     * Create a {@link RecipeChoice} based on a Predicate. These RecipeChoices are only
     * valid for {@link PotionMix}, not anywhere else RecipeChoices may be used.
     *
     * @param stackPredicate a predicate for an itemstack.
     * @return a new RecipeChoice
     */
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull RecipeChoice createPredicateChoice(@NotNull Predicate<ItemStack> stackPredicate) {
        return new PredicateRecipeChoice(stackPredicate);
    }

    @Override
    public String toString() {
        return "PotionMix{" +
            "result=" + this.result +
            ", base=" + this.input +
            ", addition=" + this.ingredient +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PotionMix potionMix = (PotionMix) o;
        return this.key.equals(potionMix.key) && this.result.equals(potionMix.result) && this.input.equals(potionMix.input) && this.ingredient.equals(potionMix.ingredient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.key, this.result, this.input, this.ingredient);
    }
}
