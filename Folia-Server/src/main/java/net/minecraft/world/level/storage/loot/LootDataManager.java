package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.slf4j.Logger;

public class LootDataManager implements PreparableReloadListener, LootDataResolver {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).create();
    public static final LootDataId<LootTable> EMPTY_LOOT_TABLE_KEY = new LootDataId<>(LootDataType.TABLE, BuiltInLootTables.EMPTY);
    private Map<LootDataId<?>, ?> elements = Map.of();
    private Multimap<LootDataType<?>, ResourceLocation> typeKeys = ImmutableMultimap.of();

    public LootDataManager() {}

    @Override
    public final CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier synchronizer, ResourceManager manager, ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor prepareExecutor, Executor applyExecutor) {
        Map<LootDataType<?>, Map<ResourceLocation, ?>> map = new HashMap();
        CompletableFuture<?>[] acompletablefuture = (CompletableFuture[]) LootDataType.values().map((lootdatatype) -> {
            return LootDataManager.scheduleElementParse(lootdatatype, manager, prepareExecutor, map);
        }).toArray((i) -> {
            return new CompletableFuture[i];
        });
        CompletableFuture completablefuture = CompletableFuture.allOf(acompletablefuture);

        Objects.requireNonNull(synchronizer);
        return completablefuture.thenCompose(synchronizer::wait).thenAcceptAsync((ovoid) -> {
            this.apply(map);
        }, applyExecutor);
    }

    private static <T> CompletableFuture<?> scheduleElementParse(LootDataType<T> type, ResourceManager resourceManager, Executor executor, Map<LootDataType<?>, Map<ResourceLocation, ?>> results) {
        Map<ResourceLocation, T> map1 = new HashMap();

        results.put(type, map1);
        return CompletableFuture.runAsync(() -> {
            Map<ResourceLocation, JsonElement> map2 = new HashMap();

            SimpleJsonResourceReloadListener.scanDirectory(resourceManager, type.directory(), LootDataManager.GSON, map2);
            map2.forEach((minecraftkey, jsonelement) -> {
                type.deserialize(minecraftkey, jsonelement).ifPresent((object) -> {
                    map1.put(minecraftkey, object);
                });
            });
        }, executor);
    }

    private void apply(Map<LootDataType<?>, Map<ResourceLocation, ?>> lootData) {
        Object object = ((Map) lootData.get(LootDataType.TABLE)).remove(BuiltInLootTables.EMPTY);

        if (object != null) {
            LootDataManager.LOGGER.warn("Datapack tried to redefine {} loot table, ignoring", BuiltInLootTables.EMPTY);
        }

        Builder<LootDataId<?>, Object> builder = ImmutableMap.builder();
        com.google.common.collect.ImmutableMultimap.Builder<LootDataType<?>, ResourceLocation> com_google_common_collect_immutablemultimap_builder = ImmutableMultimap.builder();

        lootData.forEach((lootdatatype, map1) -> {
            map1.forEach((minecraftkey, object1) -> {
                builder.put(new LootDataId<>(lootdatatype, minecraftkey), object1);
                com_google_common_collect_immutablemultimap_builder.put(lootdatatype, minecraftkey);
            });
        });
        builder.put(LootDataManager.EMPTY_LOOT_TABLE_KEY, LootTable.EMPTY);
        final Map<LootDataId<?>, ?> map1 = builder.build();
        ValidationContext lootcollector = new ValidationContext(LootContextParamSets.ALL_PARAMS, new LootDataResolver() {
            @Nullable
            @Override
            public <T> T getElement(LootDataId<T> key) {
                return (T) map1.get(key); // CraftBukkit - decompile error
            }
        });

        map1.forEach((lootdataid, object1) -> {
            LootDataManager.castAndValidate(lootcollector, lootdataid, object1);
        });
        lootcollector.getProblems().forEach((s, s1) -> {
            LootDataManager.LOGGER.warn("Found loot table element validation problem in {}: {}", s, s1);
        });
        // CraftBukkit start
        map1.forEach((key, lootTable) -> {
            if (lootTable instanceof LootTable table) { // Paper - use correct variable
                table.craftLootTable = new CraftLootTable(CraftNamespacedKey.fromMinecraft(key.location()), table);
            }
        });
        // CraftBukkit end
        this.elements = map1;
        this.typeKeys = com_google_common_collect_immutablemultimap_builder.build();
    }

    private static <T> void castAndValidate(ValidationContext reporter, LootDataId<T> key, Object value) {
        key.type().runValidation(reporter, key, (T) value); // CraftBukkit - decompile error
    }

    @Nullable
    @Override
    public <T> T getElement(LootDataId<T> key) {
        return (T) this.elements.get(key); // CraftBukkit - decompile error
    }

    public Collection<ResourceLocation> getKeys(LootDataType<?> type) {
        return this.typeKeys.get(type);
    }
}
