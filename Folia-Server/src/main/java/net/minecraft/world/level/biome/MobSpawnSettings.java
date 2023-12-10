package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.Weight;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.slf4j.Logger;

public class MobSpawnSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float DEFAULT_CREATURE_SPAWN_PROBABILITY = 0.1F;
    public static final WeightedRandomList<MobSpawnSettings.SpawnerData> EMPTY_MOB_LIST = WeightedRandomList.create();
    public static final MobSpawnSettings EMPTY = (new MobSpawnSettings.Builder()).build();
    public static final MapCodec<MobSpawnSettings> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.floatRange(0.0F, 0.9999999F).optionalFieldOf("creature_spawn_probability", 0.1F).forGetter((mobSpawnSettings) -> {
            return mobSpawnSettings.creatureGenerationProbability;
        }), Codec.simpleMap(MobCategory.CODEC, WeightedRandomList.codec(MobSpawnSettings.SpawnerData.CODEC).promotePartial(Util.prefix("Spawn data: ", LOGGER::error)), StringRepresentable.keys(MobCategory.values())).fieldOf("spawners").forGetter((mobSpawnSettings) -> {
            return mobSpawnSettings.spawners;
        }), Codec.simpleMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), MobSpawnSettings.MobSpawnCost.CODEC, BuiltInRegistries.ENTITY_TYPE).fieldOf("spawn_costs").forGetter((mobSpawnSettings) -> {
            return mobSpawnSettings.mobSpawnCosts;
        })).apply(instance, MobSpawnSettings::new);
    });
    private final float creatureGenerationProbability;
    private final Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners;
    private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts;

    MobSpawnSettings(float creatureSpawnProbability, Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> spawners, Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> spawnCosts) {
        this.creatureGenerationProbability = creatureSpawnProbability;
        this.spawners = ImmutableMap.copyOf(spawners);
        this.mobSpawnCosts = ImmutableMap.copyOf(spawnCosts);
    }

    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobs(MobCategory spawnGroup) {
        return this.spawners.getOrDefault(spawnGroup, EMPTY_MOB_LIST);
    }

    @Nullable
    public MobSpawnSettings.MobSpawnCost getMobSpawnCost(EntityType<?> entityType) {
        return this.mobSpawnCosts.get(entityType);
    }

    public float getCreatureProbability() {
        return this.creatureGenerationProbability;
    }

    public static class Builder {
        // Paper start - keep track of data in a pair set to give O(1) contains calls - we have to hook removals incase plugins mess with it
        public static class MobList extends java.util.ArrayList<MobSpawnSettings.SpawnerData> {
            java.util.Set<MobSpawnSettings.SpawnerData> biomes = new java.util.HashSet<>();

            @Override
            public boolean contains(Object o) {
                return biomes.contains(o);
            }

            @Override
            public boolean add(MobSpawnSettings.SpawnerData BiomeSettingsMobs) {
                biomes.add(BiomeSettingsMobs);
                return super.add(BiomeSettingsMobs);
            }

            @Override
            public MobSpawnSettings.SpawnerData remove(int index) {
                MobSpawnSettings.SpawnerData removed = super.remove(index);
                if (removed != null) {
                    biomes.remove(removed);
                }
                return removed;
            }

            @Override
            public void clear() {
                biomes.clear();
                super.clear();
            }
        }
        // use toImmutableEnumMap collector
        private final Map<MobCategory, List<MobSpawnSettings.SpawnerData>> spawners = (Map) Stream.of(MobCategory.values()).collect(Maps.toImmutableEnumMap((mobCategory) -> {
            return mobCategory;
        }, (mobCategory) -> {
            return new MobList(); // Use MobList instead of ArrayList
        }));
        // Paper end
        private final Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts = Maps.newLinkedHashMap();
        private float creatureGenerationProbability = 0.1F;

        public MobSpawnSettings.Builder addSpawn(MobCategory spawnGroup, MobSpawnSettings.SpawnerData spawnEntry) {
            this.spawners.get(spawnGroup).add(spawnEntry);
            return this;
        }

        public MobSpawnSettings.Builder addMobCharge(EntityType<?> entityType, double mass, double gravityLimit) {
            this.mobSpawnCosts.put(entityType, new MobSpawnSettings.MobSpawnCost(gravityLimit, mass));
            return this;
        }

        public MobSpawnSettings.Builder creatureGenerationProbability(float probability) {
            this.creatureGenerationProbability = probability;
            return this;
        }

        public MobSpawnSettings build() {
            return new MobSpawnSettings(this.creatureGenerationProbability, this.spawners.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, (entry) -> {
                return WeightedRandomList.create(entry.getValue());
            })), ImmutableMap.copyOf(this.mobSpawnCosts));
        }
    }

    public static record MobSpawnCost(double energyBudget, double charge) {
        public static final Codec<MobSpawnSettings.MobSpawnCost> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.DOUBLE.fieldOf("energy_budget").forGetter((spawnDensity) -> {
                return spawnDensity.energyBudget;
            }), Codec.DOUBLE.fieldOf("charge").forGetter((spawnDensity) -> {
                return spawnDensity.charge;
            })).apply(instance, MobSpawnSettings.MobSpawnCost::new);
        });
    }

    public static class SpawnerData extends WeightedEntry.IntrusiveBase {
        public static final Codec<MobSpawnSettings.SpawnerData> CODEC = ExtraCodecs.validate(RecordCodecBuilder.create((instance) -> {
            return instance.group(BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter((spawnEntry) -> {
                return spawnEntry.type;
            }), Weight.CODEC.fieldOf("weight").forGetter(WeightedEntry.IntrusiveBase::getWeight), ExtraCodecs.POSITIVE_INT.fieldOf("minCount").forGetter((spawnEntry) -> {
                return spawnEntry.minCount;
            }), ExtraCodecs.POSITIVE_INT.fieldOf("maxCount").forGetter((spawnEntry) -> {
                return spawnEntry.maxCount;
            })).apply(instance, MobSpawnSettings.SpawnerData::new);
        }), (spawnEntry) -> {
            return spawnEntry.minCount > spawnEntry.maxCount ? DataResult.error(() -> {
                return "minCount needs to be smaller or equal to maxCount";
            }) : DataResult.success(spawnEntry);
        });
        public final EntityType<?> type;
        public final int minCount;
        public final int maxCount;

        public SpawnerData(EntityType<?> type, int weight, int minGroupSize, int maxGroupSize) {
            this(type, Weight.of(weight), minGroupSize, maxGroupSize);
        }

        public SpawnerData(EntityType<?> type, Weight weight, int minGroupSize, int maxGroupSize) {
            super(weight);
            this.type = type.getCategory() == MobCategory.MISC ? EntityType.PIG : type;
            this.minCount = minGroupSize;
            this.maxCount = maxGroupSize;
        }

        @Override
        public String toString() {
            return EntityType.getKey(this.type) + "*(" + this.minCount + "-" + this.maxCount + "):" + this.getWeight();
        }
    }
}
