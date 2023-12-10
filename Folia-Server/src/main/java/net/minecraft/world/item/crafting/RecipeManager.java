package net.minecraft.world.item.crafting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap; // CraftBukkit

public class RecipeManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    public Map<RecipeType<?>, Object2ObjectLinkedOpenHashMap<ResourceLocation, RecipeHolder<?>>> recipes = ImmutableMap.of(); // CraftBukkit
    public Map<ResourceLocation, RecipeHolder<?>> byName = ImmutableMap.of();
    private boolean hasErrors;

    public RecipeManager() {
        super(RecipeManager.GSON, "recipes");
    }

    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        this.hasErrors = false;
        // CraftBukkit start - SPIGOT-5667 make sure all types are populated and mutable
        Map<RecipeType<?>, Object2ObjectLinkedOpenHashMap<ResourceLocation, RecipeHolder<?>>> map1 = Maps.newHashMap();
        for (RecipeType<?> recipeType : BuiltInRegistries.RECIPE_TYPE) {
            map1.put(recipeType, new Object2ObjectLinkedOpenHashMap<>());
        }
        // CraftBukkit end
        Builder<ResourceLocation, RecipeHolder<?>> builder = ImmutableMap.builder();
        Iterator iterator = prepared.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<ResourceLocation, JsonElement> entry = (Entry) iterator.next();
            ResourceLocation minecraftkey = (ResourceLocation) entry.getKey();

            try {
                RecipeHolder<?> recipeholder = RecipeManager.fromJson(minecraftkey, GsonHelper.convertToJsonObject((JsonElement) entry.getValue(), "top element"));

                // CraftBukkit start
                (map1.computeIfAbsent(recipeholder.value().getType(), (recipes) -> {
                    return new Object2ObjectLinkedOpenHashMap<>();
                    // CraftBukkit end
                })).put(minecraftkey, recipeholder);
                builder.put(minecraftkey, recipeholder);
            } catch (IllegalArgumentException | JsonParseException jsonparseexception) {
                RecipeManager.LOGGER.error("Parsing error loading recipe {}", minecraftkey, jsonparseexception);
            }
        }

        this.recipes = (Map) map1.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry1) -> {
            return entry1.getValue(); // CraftBukkit // Paper - decompile fix - *shrugs internally* // todo: is this needed anymore?
        }));
        this.byName = Maps.newHashMap(builder.build()); // CraftBukkit
        RecipeManager.LOGGER.info("Loaded {} recipes", map1.size());
    }

    // CraftBukkit start
    public void addRecipe(RecipeHolder<?> irecipe) {
        org.spigotmc.AsyncCatcher.catchOp("Recipe Add"); // Spigot
        Object2ObjectLinkedOpenHashMap<ResourceLocation, RecipeHolder<?>> map = this.recipes.get(irecipe.value().getType()); // CraftBukkit

        if (this.byName.containsKey(irecipe.id()) || map.containsKey(irecipe.id())) {
            throw new IllegalStateException("Duplicate recipe ignored with ID " + irecipe.id());
        } else {
            map.putAndMoveToFirst(irecipe.id(), irecipe); // CraftBukkit - SPIGOT-4638: last recipe gets priority
            this.byName.put(irecipe.id(), irecipe);
        }
    }
    // CraftBukkit end

    public boolean hadErrorsLoading() {
        return this.hasErrors;
    }

    public <C extends Container, T extends Recipe<C>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, C inventory, Level world) {
        // CraftBukkit start
        Optional<RecipeHolder<T>> recipe = this.byType(type).values().stream().filter((recipeholder) -> {
            return recipeholder.value().matches(inventory, world);
        }).findFirst();
        inventory.setCurrentRecipe(recipe.orElse(null)); // CraftBukkit - Clear recipe when no recipe is found
        return recipe;
        // CraftBukkit end
    }

    public <C extends Container, T extends Recipe<C>> Optional<Pair<ResourceLocation, RecipeHolder<T>>> getRecipeFor(RecipeType<T> type, C inventory, Level world, @Nullable ResourceLocation id) {
        Map<ResourceLocation, RecipeHolder<T>> map = this.byType(type);

        if (id != null) {
            RecipeHolder<T> recipeholder = (RecipeHolder) map.get(id);

            if (recipeholder != null && recipeholder.value().matches(inventory, world)) {
                inventory.setCurrentRecipe(recipeholder); // Paper
                return Optional.of(Pair.of(id, recipeholder));
            }
        }

        inventory.setCurrentRecipe(null); // Paper - clear before it might be set again
        return map.entrySet().stream().filter((entry) -> {
            return ((RecipeHolder) entry.getValue()).value().matches(inventory, world);
        }).findFirst().map((entry) -> {
            inventory.setCurrentRecipe(entry.getValue()); // Paper
            return Pair.of((ResourceLocation) entry.getKey(), (RecipeHolder) entry.getValue());
        });
    }

    public <C extends Container, T extends Recipe<C>> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T> type) {
        return List.copyOf(this.byType(type).values());
    }

    public <C extends Container, T extends Recipe<C>> List<RecipeHolder<T>> getRecipesFor(RecipeType<T> type, C inventory, Level world) {
        return (List) this.byType(type).values().stream().filter((recipeholder) -> {
            return recipeholder.value().matches(inventory, world);
        }).sorted(Comparator.comparing((recipeholder) -> {
            return recipeholder.value().getResultItem(world.registryAccess()).getDescriptionId();
        })).collect(Collectors.toList());
    }

    private <C extends Container, T extends Recipe<C>> Map<ResourceLocation, RecipeHolder<T>> byType(RecipeType<T> type) {
        return (Map) this.recipes.getOrDefault(type, new Object2ObjectLinkedOpenHashMap<>()); // CraftBukkit
    }

    public <C extends Container, T extends Recipe<C>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> type, C inventory, Level world) {
        // Paper start - check last recipe used first
        return this.getRemainingItemsFor(type, inventory, world, null);
    }
    public <C extends Container, T extends Recipe<C>> NonNullList<ItemStack> getRemainingItemsFor(RecipeType<T> type, C inventory, Level world, @Nullable ResourceLocation firstToCheck) {
        Optional<RecipeHolder<T>> optional = firstToCheck == null ? this.getRecipeFor(type, inventory, world) : this.getRecipeFor(type, inventory, world, firstToCheck).map(Pair::getSecond);
        // Paper end

        if (optional.isPresent()) {
            return ((RecipeHolder) optional.get()).value().getRemainingItems(inventory);
        } else {
            NonNullList<ItemStack> nonnulllist = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);

            for (int i = 0; i < nonnulllist.size(); ++i) {
                nonnulllist.set(i, inventory.getItem(i));
            }

            return nonnulllist;
        }
    }

    public Optional<RecipeHolder<?>> byKey(ResourceLocation id) {
        return Optional.ofNullable((RecipeHolder) this.byName.get(id));
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return (Collection) this.recipes.values().stream().flatMap((map) -> {
            return map.values().stream();
        }).collect(Collectors.toSet());
    }

    public Stream<ResourceLocation> getRecipeIds() {
        return this.recipes.values().stream().flatMap((map) -> {
            return map.keySet().stream();
        });
    }

    protected static RecipeHolder<?> fromJson(ResourceLocation id, JsonObject json) {
        String s = GsonHelper.getAsString(json, "type");
        Codec<? extends Recipe<?>> codec = ((RecipeSerializer) BuiltInRegistries.RECIPE_SERIALIZER.getOptional(new ResourceLocation(s)).orElseThrow(() -> {
            return new JsonSyntaxException("Invalid or unsupported recipe type '" + s + "'");
        })).codec();
        Recipe<?> irecipe = (Recipe) Util.getOrThrow(codec.parse(JsonOps.INSTANCE, json), JsonParseException::new);

        return new RecipeHolder<>(id, irecipe);
    }

    public void replaceRecipes(Iterable<RecipeHolder<?>> recipes) {
        this.hasErrors = false;
        Map<RecipeType<?>, Object2ObjectLinkedOpenHashMap<ResourceLocation, RecipeHolder<?>>> map = Maps.newHashMap(); // CraftBukkit
        Builder<ResourceLocation, RecipeHolder<?>> builder = ImmutableMap.builder();

        recipes.forEach((recipeholder) -> {
            Map<ResourceLocation, RecipeHolder<?>> map1 = (Map) map.computeIfAbsent(recipeholder.value().getType(), (recipes_) -> { // Paper - remap fix
                return new Object2ObjectLinkedOpenHashMap<>(); // CraftBukkit
            });
            ResourceLocation minecraftkey = recipeholder.id();
            RecipeHolder<?> recipeholder1 = (RecipeHolder) map1.put(minecraftkey, recipeholder);

            builder.put(minecraftkey, recipeholder);
            if (recipeholder1 != null) {
                throw new IllegalStateException("Duplicate recipe ignored with ID " + minecraftkey);
            }
        });
        this.recipes = ImmutableMap.copyOf(map);
        this.byName = Maps.newHashMap(builder.build()); // CraftBukkit
    }

    // CraftBukkit start
    public boolean removeRecipe(ResourceLocation mcKey) {
        for (Object2ObjectLinkedOpenHashMap<ResourceLocation, RecipeHolder<?>> recipes : this.recipes.values()) {
            recipes.remove(mcKey);
        }

        return this.byName.remove(mcKey) != null;
    }

    public void clearRecipes() {
        this.recipes = Maps.newHashMap();

        for (RecipeType<?> recipeType : BuiltInRegistries.RECIPE_TYPE) {
            this.recipes.put(recipeType, new Object2ObjectLinkedOpenHashMap<>());
        }

        this.byName = Maps.newHashMap();
    }
    // CraftBukkit end

    public static <C extends Container, T extends Recipe<C>> RecipeManager.CachedCheck<C, T> createCheck(final RecipeType<T> type) {
        return new RecipeManager.CachedCheck<C, T>() {
            @Nullable
            private ResourceLocation lastRecipe;

            @Override
            public Optional<RecipeHolder<T>> getRecipeFor(C inventory, Level world) {
                RecipeManager craftingmanager = world.getRecipeManager();
                Optional<Pair<ResourceLocation, RecipeHolder<T>>> optional = craftingmanager.getRecipeFor(type, inventory, world, this.lastRecipe);

                if (optional.isPresent()) {
                    Pair<ResourceLocation, RecipeHolder<T>> pair = (Pair) optional.get();

                    this.lastRecipe = (ResourceLocation) pair.getFirst();
                    return Optional.of((RecipeHolder) pair.getSecond());
                } else {
                    return Optional.empty();
                }
            }
        };
    }

    public interface CachedCheck<C extends Container, T extends Recipe<C>> {

        Optional<RecipeHolder<T>> getRecipeFor(C inventory, Level world);
    }
}
