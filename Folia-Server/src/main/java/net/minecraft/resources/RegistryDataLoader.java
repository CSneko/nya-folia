package net.minecraft.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;

public class RegistryDataLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.CODEC), new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE, Structure.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC), new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.CODEC), new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC));
    public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC));

    public static RegistryAccess.Frozen load(ResourceManager resourceManager, RegistryAccess baseRegistryManager, List<RegistryDataLoader.RegistryData<?>> entries) {
        Map<ResourceKey<?>, Exception> map = new HashMap<>();
        List<Pair<WritableRegistry<?>, RegistryDataLoader.Loader>> list = entries.stream().map((entry) -> {
            return entry.create(Lifecycle.stable(), map);
        }).toList();
        RegistryOps.RegistryInfoLookup registryInfoLookup = createContext(baseRegistryManager, list);
        list.forEach((loader) -> {
            loader.getSecond().load(resourceManager, registryInfoLookup);
        });
        list.forEach((loader) -> {
            Registry<?> registry = loader.getFirst();

            try {
                registry.freeze();
            } catch (Exception var4) {
                map.put(registry.key(), var4);
            }

        });
        if (!map.isEmpty()) {
            logErrors(map);
            throw new IllegalStateException("Failed to load registries due to above errors");
        } else {
            return (new RegistryAccess.ImmutableRegistryAccess(list.stream().map(Pair::getFirst).toList())).freeze();
        }
    }

    private static RegistryOps.RegistryInfoLookup createContext(RegistryAccess baseRegistryManager, List<Pair<WritableRegistry<?>, RegistryDataLoader.Loader>> additionalRegistries) {
        final Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> map = new HashMap<>();
        baseRegistryManager.registries().forEach((entry) -> {
            map.put(entry.key(), createInfoForContextRegistry(entry.value()));
        });
        additionalRegistries.forEach((pair) -> {
            map.put(pair.getFirst().key(), createInfoForNewRegistry(pair.getFirst()));
        });
        return new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return Optional.ofNullable((RegistryOps.RegistryInfo<T>) map.get(registryRef)); // Paper - decompile fix
            }
        };
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForNewRegistry(WritableRegistry<T> registry) {
        return new RegistryOps.RegistryInfo<>(registry.asLookup(), registry.createRegistrationLookup(), registry.registryLifecycle());
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForContextRegistry(Registry<T> registry) {
        return new RegistryOps.RegistryInfo<>(registry.asLookup(), registry.asTagAddingLookup(), registry.registryLifecycle());
    }

    private static void logErrors(Map<ResourceKey<?>, Exception> exceptions) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        Map<ResourceLocation, Map<ResourceLocation, Exception>> map = exceptions.entrySet().stream().collect(Collectors.groupingBy((entry) -> {
            return entry.getKey().registry();
        }, Collectors.toMap((entry) -> {
            return entry.getKey().location();
        }, Map.Entry::getValue)));
        map.entrySet().stream().sorted(Entry.comparingByKey()).forEach((entry) -> {
            printWriter.printf("> Errors in registry %s:%n", entry.getKey());
            entry.getValue().entrySet().stream().sorted(Entry.comparingByKey()).forEach((elementEntry) -> {
                printWriter.printf(">> Errors in element %s:%n", elementEntry.getKey());
                elementEntry.getValue().printStackTrace(printWriter);
            });
        });
        printWriter.flush();
        LOGGER.error("Registry loading errors:\n{}", (Object)stringWriter);
    }

    private static String registryDirPath(ResourceLocation id) {
        return id.getPath();
    }

    static <E> void loadRegistryContents(RegistryOps.RegistryInfoLookup registryInfoGetter, ResourceManager resourceManager, ResourceKey<? extends Registry<E>> registryRef, WritableRegistry<E> newRegistry, Decoder<E> decoder, Map<ResourceKey<?>, Exception> exceptions) {
        String string = registryDirPath(registryRef.location());
        FileToIdConverter fileToIdConverter = FileToIdConverter.json(string);
        RegistryOps<JsonElement> registryOps = RegistryOps.create(JsonOps.INSTANCE, registryInfoGetter);

        for(Map.Entry<ResourceLocation, Resource> entry : fileToIdConverter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            ResourceKey<E> resourceKey = ResourceKey.create(registryRef, fileToIdConverter.fileToId(resourceLocation));
            Resource resource = entry.getValue();

            try (Reader reader = resource.openAsReader()) {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                DataResult<E> dataResult = decoder.parse(registryOps, jsonElement);
                E object = dataResult.getOrThrow(false, (error) -> {
                });
                newRegistry.register(resourceKey, object, resource.isBuiltin() ? Lifecycle.stable() : dataResult.lifecycle());
            } catch (Exception var20) {
                exceptions.put(resourceKey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse %s from pack %s", resourceLocation, resource.sourcePackId()), var20));
            }
        }

    }

    interface Loader {
        void load(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup registryInfoGetter);
    }

    public static record RegistryData<T>(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec) {
        Pair<WritableRegistry<?>, RegistryDataLoader.Loader> create(Lifecycle lifecycle, Map<ResourceKey<?>, Exception> exceptions) {
            WritableRegistry<T> writableRegistry = new MappedRegistry<>(this.key, lifecycle);
            RegistryDataLoader.Loader loader = (resourceManager, registryInfoGetter) -> {
                RegistryDataLoader.loadRegistryContents(registryInfoGetter, resourceManager, this.key, writableRegistry, this.elementCodec, exceptions);
            };
            return Pair.of(writableRegistry, loader);
        }
    }
}
