package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmithingTransformRecipe;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class SmithingTransformRecipe implements SmithingRecipe {

    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;
    final ItemStack result;
    final boolean copyNBT; // Paper

    public SmithingTransformRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
        // Paper start
        this(template, base, addition, result, true);
    }
    public SmithingTransformRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result, boolean copyNBT) {
        this.copyNBT = copyNBT;
        // Paper end
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    @Override
    public boolean matches(Container inventory, Level world) {
        return this.template.test(inventory.getItem(0)) && this.base.test(inventory.getItem(1)) && this.addition.test(inventory.getItem(2));
    }

    @Override
    public ItemStack assemble(Container inventory, RegistryAccess registryManager) {
        ItemStack itemstack = this.result.copy();
        if (this.copyNBT) { // Paper - copy nbt conditionally
        CompoundTag nbttagcompound = inventory.getItem(1).getTag();

        if (nbttagcompound != null) {
            itemstack.setTag(nbttagcompound.copy());
        }
        } // Paper

        return itemstack;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return this.result;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return this.template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return this.addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SMITHING_TRANSFORM;
    }

    @Override
    public boolean isIncomplete() {
        return Stream.of(this.template, this.base, this.addition).anyMatch(Ingredient::isEmpty);
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);

        CraftSmithingTransformRecipe recipe = new CraftSmithingTransformRecipe(id, result, CraftRecipe.toBukkit(this.template), CraftRecipe.toBukkit(this.base), CraftRecipe.toBukkit(this.addition), this.copyNBT); // Paper

        return recipe;
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<SmithingTransformRecipe> {

        private static final Codec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Ingredient.CODEC.fieldOf("template").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.template;
            }), Ingredient.CODEC.fieldOf("base").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.base;
            }), Ingredient.CODEC.fieldOf("addition").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.addition;
            }), CraftingRecipeCodecs.ITEMSTACK_OBJECT_CODEC.fieldOf("result").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.result;
            })).apply(instance, SmithingTransformRecipe::new);
        });

        public Serializer() {}

        @Override
        public Codec<SmithingTransformRecipe> codec() {
            return SmithingTransformRecipe.Serializer.CODEC;
        }

        @Override
        public SmithingTransformRecipe fromNetwork(FriendlyByteBuf buf) {
            Ingredient recipeitemstack = Ingredient.fromNetwork(buf);
            Ingredient recipeitemstack1 = Ingredient.fromNetwork(buf);
            Ingredient recipeitemstack2 = Ingredient.fromNetwork(buf);
            ItemStack itemstack = buf.readItem();

            return new SmithingTransformRecipe(recipeitemstack, recipeitemstack1, recipeitemstack2, itemstack);
        }

        public void toNetwork(FriendlyByteBuf buf, SmithingTransformRecipe recipe) {
            recipe.template.toNetwork(buf);
            recipe.base.toNetwork(buf);
            recipe.addition.toNetwork(buf);
            buf.writeItem(recipe.result);
        }
    }
}
