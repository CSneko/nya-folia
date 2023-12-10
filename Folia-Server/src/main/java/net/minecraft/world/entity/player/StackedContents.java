package net.minecraft.world.entity.player;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public class StackedContents {
    private static final int EMPTY = 0;
    public final Int2IntMap contents = new Int2IntOpenHashMap();
    @Nullable public io.papermc.paper.inventory.recipe.StackedContentsExtraMap extrasMap = null; // Paper

    public void accountSimpleStack(ItemStack stack) {
        if (this.extrasMap != null && stack.hasTag() && this.extrasMap.accountStack(stack, Math.min(64, stack.getCount()))) return; // Paper - max of 64 due to accountStack method below
        if (!stack.isDamaged() && !stack.isEnchanted() && !stack.hasCustomHoverName()) {
            this.accountStack(stack);
        }

    }

    public void accountStack(ItemStack stack) {
        this.accountStack(stack, 64);
    }

    public void accountStack(ItemStack stack, int maxCount) {
        if (!stack.isEmpty()) {
            int i = getStackingIndex(stack);
            int j = Math.min(maxCount, stack.getCount());
            if (this.extrasMap != null && stack.hasTag() && this.extrasMap.accountStack(stack, j)) return; // Paper - if an exact ingredient, don't include it
            this.put(i, j);
        }

    }

    public static int getStackingIndex(ItemStack stack) {
        return BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    boolean has(int itemId) {
        return this.contents.get(itemId) > 0;
    }

    int take(int itemId, int count) {
        int i = this.contents.get(itemId);
        if (i >= count) {
            this.contents.put(itemId, i - count);
            return itemId;
        } else {
            return 0;
        }
    }

    public void put(int itemId, int count) {
        this.contents.put(itemId, this.contents.get(itemId) + count);
    }

    public boolean canCraft(Recipe<?> recipe, @Nullable IntList output) {
        return this.canCraft(recipe, output, 1);
    }

    public boolean canCraft(Recipe<?> recipe, @Nullable IntList output, int multiplier) {
        return (new StackedContents.RecipePicker(recipe)).tryPick(multiplier, output);
    }

    public int getBiggestCraftableStack(RecipeHolder<?> recipe, @Nullable IntList output) {
        return this.getBiggestCraftableStack(recipe, Integer.MAX_VALUE, output);
    }

    public int getBiggestCraftableStack(RecipeHolder<?> recipe, int limit, @Nullable IntList output) {
        return (new StackedContents.RecipePicker(recipe.value())).tryPickAll(limit, output);
    }

    public static ItemStack fromStackingIndex(int itemId) {
        return itemId == 0 ? ItemStack.EMPTY : new ItemStack(Item.byId(itemId));
    }

    // Paper start
    public void initialize(final Recipe<?> recipe) {
        this.extrasMap = new io.papermc.paper.inventory.recipe.StackedContentsExtraMap(this, recipe);
    }

    public static int maxStackSizeFromStackingIndex(final int itemId, @Nullable final StackedContents contents) {
        if (contents != null && contents.extrasMap != null && itemId >= BuiltInRegistries.ITEM.size()) {
            return fromStackingIndexExtras(itemId, contents.extrasMap).getMaxStackSize();
        }
        return fromStackingIndex(itemId).getMaxStackSize();
    }

    public static ItemStack fromStackingIndexExtras(final int itemId, final io.papermc.paper.inventory.recipe.StackedContentsExtraMap extrasMap) {
        return extrasMap.getById(itemId).copy();
    }
    // Paper end

    public void clear() {
        this.contents.clear();
    }

    class RecipePicker {
        private final Recipe<?> recipe;
        private final List<Ingredient> ingredients = Lists.newArrayList();
        private final int ingredientCount;
        private final int[] items;
        private final int itemCount;
        private final BitSet data;
        private final IntList path = new IntArrayList();

        public RecipePicker(Recipe<?> recipe) {
            this.recipe = recipe;
            this.ingredients.addAll(recipe.getIngredients());
            this.ingredients.removeIf(Ingredient::isEmpty);
            this.ingredientCount = this.ingredients.size();
            this.items = this.getUniqueAvailableIngredientItems();
            this.itemCount = this.items.length;
            this.data = new BitSet(this.ingredientCount + this.itemCount + this.ingredientCount + this.ingredientCount * this.itemCount);

            for(int i = 0; i < this.ingredients.size(); ++i) {
                IntList intList = this.getStackingIds(this.ingredients.get(i)); // Paper

                for(int j = 0; j < this.itemCount; ++j) {
                    if (intList.contains(this.items[j])) {
                        this.data.set(this.getIndex(true, j, i));
                    }
                }
            }

        }

        public boolean tryPick(int multiplier, @Nullable IntList output) {
            if (multiplier <= 0) {
                return true;
            } else {
                int i;
                for(i = 0; this.dfs(multiplier); ++i) {
                    StackedContents.this.take(this.items[this.path.getInt(0)], multiplier);
                    int j = this.path.size() - 1;
                    this.setSatisfied(this.path.getInt(j));

                    for(int k = 0; k < j; ++k) {
                        this.toggleResidual((k & 1) == 0, this.path.get(k), this.path.get(k + 1));
                    }

                    this.path.clear();
                    this.data.clear(0, this.ingredientCount + this.itemCount);
                }

                boolean bl = i == this.ingredientCount;
                boolean bl2 = bl && output != null;
                if (bl2) {
                    output.clear();
                }

                this.data.clear(0, this.ingredientCount + this.itemCount + this.ingredientCount);
                int l = 0;

                for(Ingredient ingredient : this.recipe.getIngredients()) {
                    if (bl2 && ingredient.isEmpty()) {
                        output.add(0);
                    } else {
                        for(int m = 0; m < this.itemCount; ++m) {
                            if (this.hasResidual(false, l, m)) {
                                this.toggleResidual(true, m, l);
                                StackedContents.this.put(this.items[m], multiplier);
                                if (bl2) {
                                    output.add(this.items[m]);
                                }
                            }
                        }

                        ++l;
                    }
                }

                return bl;
            }
        }

        private int[] getUniqueAvailableIngredientItems() {
            IntCollection intCollection = new IntAVLTreeSet();

            for(Ingredient ingredient : this.ingredients) {
                intCollection.addAll(this.getStackingIds(ingredient)); // Paper
            }

            IntIterator intIterator = intCollection.iterator();

            while(intIterator.hasNext()) {
                if (!StackedContents.this.has(intIterator.nextInt())) {
                    intIterator.remove();
                }
            }

            return intCollection.toIntArray();
        }

        private boolean dfs(int multiplier) {
            int i = this.itemCount;

            for(int j = 0; j < i; ++j) {
                if (StackedContents.this.contents.get(this.items[j]) >= multiplier) {
                    this.visit(false, j);

                    while(!this.path.isEmpty()) {
                        int k = this.path.size();
                        boolean bl = (k & 1) == 1;
                        int l = this.path.getInt(k - 1);
                        if (!bl && !this.isSatisfied(l)) {
                            break;
                        }

                        int m = bl ? this.ingredientCount : i;

                        for(int n = 0; n < m; ++n) {
                            if (!this.hasVisited(bl, n) && this.hasConnection(bl, l, n) && this.hasResidual(bl, l, n)) {
                                this.visit(bl, n);
                                break;
                            }
                        }

                        int o = this.path.size();
                        if (o == k) {
                            this.path.removeInt(o - 1);
                        }
                    }

                    if (!this.path.isEmpty()) {
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean isSatisfied(int itemId) {
            return this.data.get(this.getSatisfiedIndex(itemId));
        }

        private void setSatisfied(int itemId) {
            this.data.set(this.getSatisfiedIndex(itemId));
        }

        private int getSatisfiedIndex(int itemId) {
            return this.ingredientCount + this.itemCount + itemId;
        }

        private boolean hasConnection(boolean reversed, int itemIndex, int ingredientIndex) {
            return this.data.get(this.getIndex(reversed, itemIndex, ingredientIndex));
        }

        private boolean hasResidual(boolean reversed, int itemIndex, int ingredientIndex) {
            return reversed != this.data.get(1 + this.getIndex(reversed, itemIndex, ingredientIndex));
        }

        private void toggleResidual(boolean reversed, int itemIndex, int ingredientIndex) {
            this.data.flip(1 + this.getIndex(reversed, itemIndex, ingredientIndex));
        }

        private int getIndex(boolean reversed, int itemIndex, int ingredientIndex) {
            int i = reversed ? itemIndex * this.ingredientCount + ingredientIndex : ingredientIndex * this.ingredientCount + itemIndex;
            return this.ingredientCount + this.itemCount + this.ingredientCount + 2 * i;
        }

        private void visit(boolean reversed, int itemId) {
            this.data.set(this.getVisitedIndex(reversed, itemId));
            this.path.add(itemId);
        }

        private boolean hasVisited(boolean reversed, int itemId) {
            return this.data.get(this.getVisitedIndex(reversed, itemId));
        }

        private int getVisitedIndex(boolean reversed, int itemId) {
            return (reversed ? 0 : this.ingredientCount) + itemId;
        }

        public int tryPickAll(int minimum, @Nullable IntList output) {
            int i = 0;
            int j = Math.min(minimum, this.getMinIngredientCount()) + 1;

            while(true) {
                int k = (i + j) / 2;
                if (this.tryPick(k, (IntList)null)) {
                    if (j - i <= 1) {
                        if (k > 0) {
                            this.tryPick(k, output);
                        }

                        return k;
                    }

                    i = k;
                } else {
                    j = k;
                }
            }
        }

        private int getMinIngredientCount() {
            int i = Integer.MAX_VALUE;

            for(Ingredient ingredient : this.ingredients) {
                int j = 0;

                for(int k : this.getStackingIds(ingredient)) { // Paper
                    j = Math.max(j, StackedContents.this.contents.get(k));
                }

                if (i > 0) {
                    i = Math.min(i, j);
                }
            }

            return i;
        }

        // Paper start - improve exact recipe choices
        private IntList getStackingIds(final Ingredient ingredient) {
            if (StackedContents.this.extrasMap != null) {
                final IntList ids = StackedContents.this.extrasMap.extraStackingIds.get(ingredient);
                if (ids != null) {
                    return ids;
                }
            }
            return ingredient.getStackingIds();
        }
        // Paper end - improve exact recipe choices
    }
}
