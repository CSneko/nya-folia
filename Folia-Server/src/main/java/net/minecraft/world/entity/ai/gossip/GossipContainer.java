package net.minecraft.world.entity.ai.gossip;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import org.slf4j.Logger;

public class GossipContainer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DISCARD_THRESHOLD = 2;
    public final Map<UUID, GossipContainer.EntityGossips> gossips = Maps.newHashMap();

    @VisibleForDebug
    public Map<UUID, Object2IntMap<GossipType>> getGossipEntries() {
        Map<UUID, Object2IntMap<GossipType>> map = Maps.newHashMap();
        this.gossips.keySet().forEach((uuid) -> {
            GossipContainer.EntityGossips entityGossips = this.gossips.get(uuid);
            map.put(uuid, entityGossips.entries);
        });
        return map;
    }

    public void decay() {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while(iterator.hasNext()) {
            GossipContainer.EntityGossips entityGossips = iterator.next();
            entityGossips.decay();
            if (entityGossips.isEmpty()) {
                iterator.remove();
            }
        }

    }

    private Stream<GossipContainer.GossipEntry> unpack() {
        return this.gossips.entrySet().stream().flatMap((entry) -> {
            return entry.getValue().unpack(entry.getKey());
        });
    }

    // Paper start - Remove streams from reputation
    private List<GossipContainer.GossipEntry> decompress() {
        List<GossipContainer.GossipEntry> list = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        for (Map.Entry<UUID, GossipContainer.EntityGossips> entry : this.gossips.entrySet()) {
            for (GossipContainer.GossipEntry cur : entry.getValue().decompress(entry.getKey())) {
                if (cur.weightedValue() != 0) {
                    list.add(cur);
                }
            }
        }
        return list;
    }
    // Paper end

    private Collection<GossipContainer.GossipEntry> selectGossipsForTransfer(RandomSource random, int count) {
        List<GossipContainer.GossipEntry> list = this.decompress(); // Paper - Remove streams from reputation
        if (list.isEmpty()) {
            return Collections.emptyList();
        } else {
            int[] is = new int[list.size()];
            int i = 0;

            for(int j = 0; j < list.size(); ++j) {
                GossipContainer.GossipEntry gossipEntry = list.get(j);
                i += Math.abs(gossipEntry.weightedValue());
                is[j] = i - 1;
            }

            Set<GossipContainer.GossipEntry> set = Sets.newIdentityHashSet();

            for(int k = 0; k < count; ++k) {
                int l = random.nextInt(i);
                int m = Arrays.binarySearch(is, l);
                set.add(list.get(m < 0 ? -m - 1 : m));
            }

            return set;
        }
    }

    private GossipContainer.EntityGossips getOrCreate(UUID target) {
        return this.gossips.computeIfAbsent(target, (uuid) -> {
            return new GossipContainer.EntityGossips();
        });
    }

    public void transferFrom(GossipContainer from, RandomSource random, int count) {
        Collection<GossipContainer.GossipEntry> collection = from.selectGossipsForTransfer(random, count);
        collection.forEach((gossip) -> {
            int i = gossip.value - gossip.type.decayPerTransfer;
            if (i >= 2) {
                this.getOrCreate(gossip.target).entries.mergeInt(gossip.type, i, GossipContainer::mergeValuesForTransfer);
            }

        });
    }

    public int getReputation(UUID target, Predicate<GossipType> gossipTypeFilter) {
        GossipContainer.EntityGossips entityGossips = this.gossips.get(target);
        return entityGossips != null ? entityGossips.weightedValue(gossipTypeFilter) : 0;
    }

    public long getCountForType(GossipType type, DoublePredicate predicate) {
        return this.gossips.values().stream().filter((reputation) -> {
            return predicate.test((double)(reputation.entries.getOrDefault(type, 0) * type.weight));
        }).count();
    }

    public void add(UUID target, GossipType type, int value) {
        GossipContainer.EntityGossips entityGossips = this.getOrCreate(target);
        entityGossips.entries.mergeInt(type, value, (left, right) -> {
            return this.mergeValuesForAddition(type, left, right);
        });
        entityGossips.makeSureValueIsntTooLowOrTooHigh(type);
        if (entityGossips.isEmpty()) {
            this.gossips.remove(target);
        }

    }

    public void remove(UUID target, GossipType type, int value) {
        this.add(target, type, -value);
    }

    public void remove(UUID target, GossipType type) {
        GossipContainer.EntityGossips entityGossips = this.gossips.get(target);
        if (entityGossips != null) {
            entityGossips.remove(type);
            if (entityGossips.isEmpty()) {
                this.gossips.remove(target);
            }
        }

    }

    public void remove(GossipType type) {
        Iterator<GossipContainer.EntityGossips> iterator = this.gossips.values().iterator();

        while(iterator.hasNext()) {
            GossipContainer.EntityGossips entityGossips = iterator.next();
            entityGossips.remove(type);
            if (entityGossips.isEmpty()) {
                iterator.remove();
            }
        }

    }

    public <T> T store(DynamicOps<T> ops) {
        return GossipContainer.GossipEntry.LIST_CODEC.encodeStart(ops, this.decompress()).resultOrPartial((error) -> {
            LOGGER.warn("Failed to serialize gossips: {}", (Object)error);
        }).orElseGet(ops::emptyList);
    }

    public void update(Dynamic<?> dynamic) {
        GossipContainer.GossipEntry.LIST_CODEC.decode(dynamic).resultOrPartial((error) -> {
            LOGGER.warn("Failed to deserialize gossips: {}", (Object)error);
        }).stream().flatMap((pair) -> {
            return pair.getFirst().stream();
        }).forEach((entry) -> {
            this.getOrCreate(entry.target).entries.put(entry.type, entry.value);
        });
    }

    private static int mergeValuesForTransfer(int left, int right) {
        return Math.max(left, right);
    }

    private int mergeValuesForAddition(GossipType type, int left, int right) {
        int i = left + right;
        return i > type.max ? Math.max(type.max, left) : i;
    }

    public static class EntityGossips {
        final Object2IntMap<GossipType> entries = new Object2IntOpenHashMap<>();

        public int weightedValue(Predicate<GossipType> gossipTypeFilter) {
            // Paper start - Remove streams from reputation
            int weight = 0;
            for (Object2IntMap.Entry<GossipType> entry : entries.object2IntEntrySet()) {
                if (gossipTypeFilter.test(entry.getKey())) {
                    weight += entry.getIntValue() * entry.getKey().weight;
                }
            }
            return weight;
        }

        public List<GossipContainer.GossipEntry> decompress(UUID uuid) {
            List<GossipContainer.GossipEntry> list = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
            for (Object2IntMap.Entry<GossipType> entry : entries.object2IntEntrySet()) {
                list.add(new GossipContainer.GossipEntry(uuid, entry.getKey(), entry.getIntValue()));
            }
            return list;
            // Paper end
        }

        public Stream<GossipContainer.GossipEntry> unpack(UUID target) {
            return this.entries.object2IntEntrySet().stream().map((entry) -> {
                return new GossipContainer.GossipEntry(target, entry.getKey(), entry.getIntValue());
            });
        }

        public void decay() {
            ObjectIterator<Object2IntMap.Entry<GossipType>> objectIterator = this.entries.object2IntEntrySet().iterator();

            while(objectIterator.hasNext()) {
                Object2IntMap.Entry<GossipType> entry = objectIterator.next();
                int i = entry.getIntValue() - (entry.getKey()).decayPerDay;
                if (i < 2) {
                    objectIterator.remove();
                } else {
                    entry.setValue(i);
                }
            }

        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        public void makeSureValueIsntTooLowOrTooHigh(GossipType gossipType) {
            int i = this.entries.getInt(gossipType);
            if (i > gossipType.max) {
                this.entries.put(gossipType, gossipType.max);
            }

            if (i < 2) {
                this.remove(gossipType);
            }

        }

        public void remove(GossipType gossipType) {
            this.entries.removeInt(gossipType);
        }

        // Paper start - Add villager reputation API
        private static final GossipType[] TYPES = GossipType.values();
        public com.destroystokyo.paper.entity.villager.Reputation getPaperReputation() {
            Map<com.destroystokyo.paper.entity.villager.ReputationType, Integer> map = new java.util.EnumMap<>(com.destroystokyo.paper.entity.villager.ReputationType.class);
            for (Object2IntMap.Entry<GossipType> type : this.entries.object2IntEntrySet()) {
                map.put(toApi(type.getKey()), type.getIntValue());
            }

            return new com.destroystokyo.paper.entity.villager.Reputation(map);
        }

        public void assignFromPaperReputation(com.destroystokyo.paper.entity.villager.Reputation rep) {
            for (GossipType type : TYPES) {
                com.destroystokyo.paper.entity.villager.ReputationType api = toApi(type);

                if (rep.hasReputationSet(api)) {
                    int reputation = rep.getReputation(api);
                    if (reputation == 0) {
                        this.entries.removeInt(type);
                    } else {
                        this.entries.put(type, reputation);
                    }
                }
            }
        }

        private static com.destroystokyo.paper.entity.villager.ReputationType toApi(GossipType type) {
            return switch (type) {
                case MAJOR_NEGATIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MAJOR_NEGATIVE;
                case MINOR_NEGATIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MINOR_NEGATIVE;
                case MINOR_POSITIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MINOR_POSITIVE;
                case MAJOR_POSITIVE -> com.destroystokyo.paper.entity.villager.ReputationType.MAJOR_POSITIVE;
                case TRADING -> com.destroystokyo.paper.entity.villager.ReputationType.TRADING;
            };
        }
        // Paper end
    }

    static record GossipEntry(UUID target, GossipType type, int value) {
        public static final Codec<GossipContainer.GossipEntry> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(UUIDUtil.CODEC.fieldOf("Target").forGetter(GossipContainer.GossipEntry::target), GossipType.CODEC.fieldOf("Type").forGetter(GossipContainer.GossipEntry::type), ExtraCodecs.POSITIVE_INT.fieldOf("Value").forGetter(GossipContainer.GossipEntry::value)).apply(instance, GossipContainer.GossipEntry::new);
        });
        public static final Codec<List<GossipContainer.GossipEntry>> LIST_CODEC = CODEC.listOf();

        public int weightedValue() {
            return this.value * this.type.weight;
        }
    }
}
