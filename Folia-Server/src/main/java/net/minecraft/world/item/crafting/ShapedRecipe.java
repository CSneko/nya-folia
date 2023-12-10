package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.NotImplementedException;

// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.inventory.RecipeChoice;
// CraftBukkit end

public class ShapedRecipe extends io.papermc.paper.inventory.recipe.RecipeBookExactChoiceRecipe<CraftingContainer> implements CraftingRecipe { // Paper - improve exact recipe choices

    final int width;
    final int height;
    final NonNullList<Ingredient> recipeItems;
    final ItemStack result;
    final String group;
    final CraftingBookCategory category;
    final boolean showNotification;

    public ShapedRecipe(String group, CraftingBookCategory category, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification) {
        this.group = group;
        this.category = category;
        this.width = width;
        this.height = height;
        this.recipeItems = ingredients;
        this.result = result;
        this.showNotification = showNotification;
        this.checkExactIngredients(); // Paper - improve exact recipe choices
    }

    public ShapedRecipe(String group, CraftingBookCategory category, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result) {
        this(group, category, width, height, ingredients, result, true);
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(this.result);
        CraftShapedRecipe recipe = new CraftShapedRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        switch (this.height) {
        case 1:
            switch (this.width) {
            case 1:
                recipe.shape("a");
                break;
            case 2:
                recipe.shape("ab");
                break;
            case 3:
                recipe.shape("abc");
                break;
            }
            break;
        case 2:
            switch (this.width) {
            case 1:
                recipe.shape("a","b");
                break;
            case 2:
                recipe.shape("ab","cd");
                break;
            case 3:
                recipe.shape("abc","def");
                break;
            }
            break;
        case 3:
            switch (this.width) {
            case 1:
                recipe.shape("a","b","c");
                break;
            case 2:
                recipe.shape("ab","cd","ef");
                break;
            case 3:
                recipe.shape("abc","def","ghi");
                break;
            }
            break;
        }
        char c = 'a';
        for (Ingredient list : this.recipeItems) {
            RecipeChoice choice = CraftRecipe.toBukkit(list);
            if (choice != null) {
                recipe.setIngredient(c, choice);
            }

            c++;
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SHAPED_RECIPE;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.recipeItems;
    }

    @Override
    public boolean showNotification() {
        return this.showNotification;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= this.width && height >= this.height;
    }

    public boolean matches(CraftingContainer inventory, Level world) {
        for (int i = 0; i <= inventory.getWidth() - this.width; ++i) {
            for (int j = 0; j <= inventory.getHeight() - this.height; ++j) {
                if (this.matches(inventory, i, j, true)) {
                    return true;
                }

                if (this.matches(inventory, i, j, false)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean matches(CraftingContainer inv, int offsetX, int offsetY, boolean flipped) {
        for (int k = 0; k < inv.getWidth(); ++k) {
            for (int l = 0; l < inv.getHeight(); ++l) {
                int i1 = k - offsetX;
                int j1 = l - offsetY;
                Ingredient recipeitemstack = Ingredient.EMPTY;

                if (i1 >= 0 && j1 >= 0 && i1 < this.width && j1 < this.height) {
                    if (flipped) {
                        recipeitemstack = (Ingredient) this.recipeItems.get(this.width - i1 - 1 + j1 * this.width);
                    } else {
                        recipeitemstack = (Ingredient) this.recipeItems.get(i1 + j1 * this.width);
                    }
                }

                if (!recipeitemstack.test(inv.getItem(k + l * inv.getWidth()))) {
                    return false;
                }
            }
        }

        return true;
    }

    public ItemStack assemble(CraftingContainer inventory, RegistryAccess registryManager) {
        return this.getResultItem(registryManager).copy();
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    @VisibleForTesting
    static String[] shrink(List<String> pattern) {
        int i = Integer.MAX_VALUE;
        int j = 0;
        int k = 0;
        int l = 0;

        for (int i1 = 0; i1 < pattern.size(); ++i1) {
            String s = (String) pattern.get(i1);

            i = Math.min(i, ShapedRecipe.firstNonSpace(s));
            int j1 = ShapedRecipe.lastNonSpace(s);

            j = Math.max(j, j1);
            if (j1 < 0) {
                if (k == i1) {
                    ++k;
                }

                ++l;
            } else {
                l = 0;
            }
        }

        if (pattern.size() == l) {
            return new String[0];
        } else {
            String[] astring = new String[pattern.size() - l - k];

            for (int k1 = 0; k1 < astring.length; ++k1) {
                astring[k1] = ((String) pattern.get(k1 + k)).substring(i, j + 1);
            }

            return astring;
        }
    }

    @Override
    public boolean isIncomplete() {
        NonNullList<Ingredient> nonnulllist = this.getIngredients();

        return nonnulllist.isEmpty() || nonnulllist.stream().filter((recipeitemstack) -> {
            return !recipeitemstack.isEmpty();
        }).anyMatch((recipeitemstack) -> {
            return recipeitemstack.getItems().length == 0;
        });
    }

    private static int firstNonSpace(String line) {
        int i;

        for (i = 0; i < line.length() && line.charAt(i) == ' '; ++i) {
            ;
        }

        return i;
    }

    private static int lastNonSpace(String pattern) {
        int i;

        for (i = pattern.length() - 1; i >= 0 && pattern.charAt(i) == ' '; --i) {
            ;
        }

        return i;
    }

    public static class Serializer implements RecipeSerializer<ShapedRecipe> {

        static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().flatXmap((list) -> {
            if (list.size() > 3) {
                return DataResult.error(() -> {
                    return "Invalid pattern: too many rows, 3 is maximum";
                });
            } else if (list.isEmpty()) {
                return DataResult.error(() -> {
                    return "Invalid pattern: empty pattern not allowed";
                });
            } else {
                int i = ((String) list.get(0)).length();
                Iterator iterator = list.iterator();

                String s;

                do {
                    if (!iterator.hasNext()) {
                        return DataResult.success(list);
                    }

                    s = (String) iterator.next();
                    if (s.length() > 3) {
                        return DataResult.error(() -> {
                            return "Invalid pattern: too many columns, 3 is maximum";
                        });
                    }
                } while (i == s.length());

                return DataResult.error(() -> {
                    return "Invalid pattern: each row must be the same width";
                });
            }
        }, DataResult::success);
        static final Codec<String> SINGLE_CHARACTER_STRING_CODEC = Codec.STRING.flatXmap((s) -> {
            return s.length() != 1 ? DataResult.error(() -> {
                return "Invalid key entry: '" + s + "' is an invalid symbol (must be 1 character only).";
            }) : (" ".equals(s) ? DataResult.error(() -> {
                return "Invalid key entry: ' ' is a reserved symbol.";
            }) : DataResult.success(s));
        }, DataResult::success);
        private static final Codec<ShapedRecipe> CODEC = ShapedRecipe.Serializer.RawShapedRecipe.CODEC.flatXmap((shapedrecipes_serializer_rawshapedrecipe) -> {
            String[] astring = ShapedRecipe.shrink(shapedrecipes_serializer_rawshapedrecipe.pattern);
            int i = astring[0].length();
            int j = astring.length;
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i * j, Ingredient.EMPTY);
            Set<String> set = Sets.newHashSet(shapedrecipes_serializer_rawshapedrecipe.key.keySet());

            for (int k = 0; k < astring.length; ++k) {
                String s = astring[k];

                for (int l = 0; l < s.length(); ++l) {
                    String s1 = s.substring(l, l + 1);
                    Ingredient recipeitemstack = s1.equals(" ") ? Ingredient.EMPTY : (Ingredient) shapedrecipes_serializer_rawshapedrecipe.key.get(s1);

                    if (recipeitemstack == null) {
                        return DataResult.error(() -> {
                            return "Pattern references symbol '" + s1 + "' but it's not defined in the key";
                        });
                    }

                    set.remove(s1);
                    nonnulllist.set(l + i * k, recipeitemstack);
                }
            }

            if (!set.isEmpty()) {
                return DataResult.error(() -> {
                    return "Key defines symbols that aren't used in pattern: " + set;
                });
            } else {
                ShapedRecipe shapedrecipes = new ShapedRecipe(shapedrecipes_serializer_rawshapedrecipe.group, shapedrecipes_serializer_rawshapedrecipe.category, i, j, nonnulllist, shapedrecipes_serializer_rawshapedrecipe.result, shapedrecipes_serializer_rawshapedrecipe.showNotification);

                return DataResult.success(shapedrecipes);
            }
        }, (shapedrecipes) -> {
            throw new NotImplementedException("Serializing ShapedRecipe is not implemented yet.");
        });

        public Serializer() {}

        @Override
        public Codec<ShapedRecipe> codec() {
            return ShapedRecipe.Serializer.CODEC;
        }

        @Override
        public ShapedRecipe fromNetwork(FriendlyByteBuf buf) {
            int i = buf.readVarInt();
            int j = buf.readVarInt();
            String s = buf.readUtf();
            CraftingBookCategory craftingbookcategory = (CraftingBookCategory) buf.readEnum(CraftingBookCategory.class);
            NonNullList<Ingredient> nonnulllist = NonNullList.withSize(i * j, Ingredient.EMPTY);

            for (int k = 0; k < nonnulllist.size(); ++k) {
                nonnulllist.set(k, Ingredient.fromNetwork(buf));
            }

            ItemStack itemstack = buf.readItem();
            boolean flag = buf.readBoolean();

            return new ShapedRecipe(s, craftingbookcategory, i, j, nonnulllist, itemstack, flag);
        }

        public void toNetwork(FriendlyByteBuf buf, ShapedRecipe recipe) {
            buf.writeVarInt(recipe.width);
            buf.writeVarInt(recipe.height);
            buf.writeUtf(recipe.group);
            buf.writeEnum(recipe.category);
            Iterator iterator = recipe.recipeItems.iterator();

            while (iterator.hasNext()) {
                Ingredient recipeitemstack = (Ingredient) iterator.next();

                recipeitemstack.toNetwork(buf);
            }

            buf.writeItem(recipe.result);
            buf.writeBoolean(recipe.showNotification);
        }

        private static record RawShapedRecipe(String group, CraftingBookCategory category, Map<String, Ingredient> key, List<String> pattern, ItemStack result, boolean showNotification) {

            public static final Codec<ShapedRecipe.Serializer.RawShapedRecipe> CODEC = RecordCodecBuilder.create((instance) -> {
                return instance.group(ExtraCodecs.strictOptionalField(Codec.STRING, "group", "").forGetter((shapedrecipes_serializer_rawshapedrecipe) -> {
                    return shapedrecipes_serializer_rawshapedrecipe.group;
                }), CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter((shapedrecipes_serializer_rawshapedrecipe) -> {
                    return shapedrecipes_serializer_rawshapedrecipe.category;
                }), ExtraCodecs.strictUnboundedMap(ShapedRecipe.Serializer.SINGLE_CHARACTER_STRING_CODEC, Ingredient.CODEC_NONEMPTY).fieldOf("key").forGetter((shapedrecipes_serializer_rawshapedrecipe) -> {
                    return shapedrecipes_serializer_rawshapedrecipe.key;
                }), ShapedRecipe.Serializer.PATTERN_CODEC.fieldOf("pattern").forGetter((shapedrecipes_serializer_rawshapedrecipe) -> {
                    return shapedrecipes_serializer_rawshapedrecipe.pattern;
                }), CraftingRecipeCodecs.ITEMSTACK_OBJECT_CODEC.fieldOf("result").forGetter((shapedrecipes_serializer_rawshapedrecipe) -> {
                    return shapedrecipes_serializer_rawshapedrecipe.result;
                }), ExtraCodecs.strictOptionalField(Codec.BOOL, "show_notification", true).forGetter((shapedrecipes_serializer_rawshapedrecipe) -> {
                    return shapedrecipes_serializer_rawshapedrecipe.showNotification;
                })).apply(instance, ShapedRecipe.Serializer.RawShapedRecipe::new);
            });
        }
    }
}
