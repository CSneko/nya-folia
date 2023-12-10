package net.minecraft.world.item.crafting;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class Ingredient implements Predicate<ItemStack> {

    public static final Ingredient EMPTY = new Ingredient(Stream.empty());
    private final Ingredient.Value[] values;
    @Nullable
    public ItemStack[] itemStacks;
    @Nullable
    private IntList stackingIds;
    public boolean exact; // CraftBukkit
    public static final Codec<Ingredient> CODEC = Ingredient.codec(true);
    public static final Codec<Ingredient> CODEC_NONEMPTY = Ingredient.codec(false);

    public Ingredient(Stream<? extends Ingredient.Value> entries) {
        this.values = (Ingredient.Value[]) entries.toArray((i) -> {
            return new Ingredient.Value[i];
        });
    }

    private Ingredient(Ingredient.Value[] entries) {
        this.values = entries;
    }

    public ItemStack[] getItems() {
        if (this.itemStacks == null) {
            this.itemStacks = (ItemStack[]) Arrays.stream(this.values).flatMap((recipeitemstack_provider) -> {
                return recipeitemstack_provider.getItems().stream();
            }).distinct().toArray((i) -> {
                return new ItemStack[i];
            });
        }

        return this.itemStacks;
    }

    public boolean test(@Nullable ItemStack itemstack) {
        if (itemstack == null) {
            return false;
        } else if (this.isEmpty()) {
            return itemstack.isEmpty();
        } else {
            ItemStack[] aitemstack = this.getItems();
            int i = aitemstack.length;

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack1 = aitemstack[j];

                // CraftBukkit start
                if (this.exact) {
                    if (itemstack1.getItem() == itemstack.getItem() && ItemStack.isSameItemSameTags(itemstack, itemstack1)) {
                        return true;
                    }

                    continue;
                }
                // CraftBukkit end
                if (itemstack1.is(itemstack.getItem())) {
                    return true;
                }
            }

            return false;
        }
    }

    public IntList getStackingIds() {
        if (this.stackingIds == null) {
            ItemStack[] aitemstack = this.getItems();

            this.stackingIds = new IntArrayList(aitemstack.length);
            ItemStack[] aitemstack1 = aitemstack;
            int i = aitemstack.length;

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = aitemstack1[j];

                this.stackingIds.add(StackedContents.getStackingIndex(itemstack));
            }

            this.stackingIds.sort(IntComparators.NATURAL_COMPARATOR);
        }

        return this.stackingIds;
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeCollection(Arrays.asList(this.getItems()), FriendlyByteBuf::writeItem);
    }

    public JsonElement toJson(boolean allowEmpty) {
        Codec<Ingredient> codec = allowEmpty ? Ingredient.CODEC : Ingredient.CODEC_NONEMPTY;

        return (JsonElement) Util.getOrThrow(codec.encodeStart(JsonOps.INSTANCE, this), IllegalStateException::new);
    }

    public boolean isEmpty() {
        return this.values.length == 0;
    }

    public boolean equals(Object object) {
        if (object instanceof Ingredient) {
            Ingredient recipeitemstack = (Ingredient) object;

            return Arrays.equals(this.values, recipeitemstack.values);
        } else {
            return false;
        }
    }

    private static Ingredient fromValues(Stream<? extends Ingredient.Value> entries) {
        Ingredient recipeitemstack = new Ingredient(entries);

        return recipeitemstack.isEmpty() ? Ingredient.EMPTY : recipeitemstack;
    }

    public static Ingredient of() {
        return Ingredient.EMPTY;
    }

    public static Ingredient of(ItemLike... items) {
        return Ingredient.of(Arrays.stream(items).map(ItemStack::new));
    }

    public static Ingredient of(ItemStack... stacks) {
        return Ingredient.of(Arrays.stream(stacks));
    }

    public static Ingredient of(Stream<ItemStack> stacks) {
        return Ingredient.fromValues(stacks.filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).map(Ingredient.ItemValue::new));
    }

    public static Ingredient of(TagKey<Item> tag) {
        return Ingredient.fromValues(Stream.of(new Ingredient.TagValue(tag)));
    }

    public static Ingredient fromNetwork(FriendlyByteBuf buf) {
        return Ingredient.fromValues(buf.readList(FriendlyByteBuf::readItem).stream().map(Ingredient.ItemValue::new));
    }

    private static Codec<Ingredient> codec(boolean allowEmpty) {
        Codec<Ingredient.Value[]> codec = Codec.list(Ingredient.Value.CODEC).comapFlatMap((list) -> {
            return !allowEmpty && list.size() < 1 ? DataResult.error(() -> {
                return "Item array cannot be empty, at least one item must be defined";
            }) : DataResult.success((Ingredient.Value[]) list.toArray(new Ingredient.Value[0]));
        }, List::of);

        return ExtraCodecs.either(codec, Ingredient.Value.CODEC).flatComapMap((either) -> {
            return (Ingredient) either.map(Ingredient::new, (recipeitemstack_provider) -> {
                return new Ingredient(new Ingredient.Value[]{recipeitemstack_provider});
            });
        }, (recipeitemstack) -> {
            return recipeitemstack.values.length == 1 ? DataResult.success(Either.right(recipeitemstack.values[0])) : (recipeitemstack.values.length == 0 && !allowEmpty ? DataResult.error(() -> {
                return "Item array cannot be empty, at least one item must be defined";
            }) : DataResult.success(Either.left(recipeitemstack.values)));
        });
    }

    public interface Value {

        Codec<Ingredient.Value> CODEC = ExtraCodecs.xor(Ingredient.ItemValue.CODEC, Ingredient.TagValue.CODEC).xmap((either) -> {
            return (Ingredient.Value) either.map((recipeitemstack_stackprovider) -> {
                return recipeitemstack_stackprovider;
            }, (recipeitemstack_b) -> {
                return recipeitemstack_b;
            });
        }, (recipeitemstack_provider) -> {
            if (recipeitemstack_provider instanceof Ingredient.TagValue) {
                Ingredient.TagValue recipeitemstack_b = (Ingredient.TagValue) recipeitemstack_provider;

                return Either.right(recipeitemstack_b);
            } else if (recipeitemstack_provider instanceof Ingredient.ItemValue) {
                Ingredient.ItemValue recipeitemstack_stackprovider = (Ingredient.ItemValue) recipeitemstack_provider;

                return Either.left(recipeitemstack_stackprovider);
            } else {
                throw new UnsupportedOperationException("This is neither an item value nor a tag value.");
            }
        });

        Collection<ItemStack> getItems();
    }

    private static record TagValue(TagKey<Item> tag) implements Ingredient.Value {

        static final Codec<Ingredient.TagValue> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter((recipeitemstack_b) -> {
                return recipeitemstack_b.tag;
            })).apply(instance, Ingredient.TagValue::new);
        });

        public boolean equals(Object object) {
            if (object instanceof Ingredient.TagValue) {
                Ingredient.TagValue recipeitemstack_b = (Ingredient.TagValue) object;

                return recipeitemstack_b.tag.location().equals(this.tag.location());
            } else {
                return false;
            }
        }

        @Override
        public Collection<ItemStack> getItems() {
            List<ItemStack> list = Lists.newArrayList();
            Iterator iterator = BuiltInRegistries.ITEM.getTagOrEmpty(this.tag).iterator();

            while (iterator.hasNext()) {
                Holder<Item> holder = (Holder) iterator.next();

                list.add(new ItemStack(holder));
            }

            return list;
        }
    }

    public static record ItemValue(ItemStack item) implements Ingredient.Value {

        static final Codec<Ingredient.ItemValue> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(CraftingRecipeCodecs.ITEMSTACK_NONAIR_CODEC.fieldOf("item").forGetter((recipeitemstack_stackprovider) -> {
                return recipeitemstack_stackprovider.item;
            })).apply(instance, Ingredient.ItemValue::new);
        });

        public boolean equals(Object object) {
            if (!(object instanceof Ingredient.ItemValue)) {
                return false;
            } else {
                Ingredient.ItemValue recipeitemstack_stackprovider = (Ingredient.ItemValue) object;

                return recipeitemstack_stackprovider.item.getItem().equals(this.item.getItem()) && recipeitemstack_stackprovider.item.getCount() == this.item.getCount();
            }
        }

        @Override
        public Collection<ItemStack> getItems() {
            return Collections.singleton(this.item);
        }
    }
}
