package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;

public class ShufflingList<U> implements Iterable<U> {
    public final List<ShufflingList.WeightedEntry<U>> entries; // Paper - public
    private final RandomSource random = RandomSource.create();
    private final boolean isUnsafe; // Paper

    public ShufflingList() {
        // Paper start
        this(true);
    }
    public ShufflingList(boolean isUnsafe) {
        this.isUnsafe = isUnsafe;
        // Paper end
        this.entries = Lists.newArrayList();
    }

    private ShufflingList(List<ShufflingList.WeightedEntry<U>> list) {
        // Paper start
        this(list, true);
    }
    private ShufflingList(List<ShufflingList.WeightedEntry<U>> list, boolean isUnsafe) {
        this.isUnsafe = isUnsafe;
        // Paper end
        this.entries = Lists.newArrayList(list);
    }

    public static <U> Codec<ShufflingList<U>> codec(Codec<U> codec) {
        return ShufflingList.WeightedEntry.codec(codec).listOf().xmap(ShufflingList::new, (weightedList) -> {
            return weightedList.entries;
        });
    }

    public ShufflingList<U> add(U data, int weight) {
        this.entries.add(new ShufflingList.WeightedEntry<>(data, weight));
        return this;
    }

    public ShufflingList<U> shuffle() {
        // Paper start - make concurrent safe, work off a clone of the list
        List<ShufflingList.WeightedEntry<U>> list = this.isUnsafe ? Lists.newArrayList(this.entries) : this.entries;
        list.forEach(entry -> entry.setRandom(this.random.nextFloat()));
        list.sort(Comparator.comparingDouble(ShufflingList.WeightedEntry::getRandWeight));
        return this.isUnsafe ? new ShufflingList<>(list, this.isUnsafe) : this;
        // Paper end
    }

    public Stream<U> stream() {
        return this.entries.stream().map(ShufflingList.WeightedEntry::getData);
    }

    @Override
    public Iterator<U> iterator() {
        return Iterators.transform(this.entries.iterator(), ShufflingList.WeightedEntry::getData);
    }

    @Override
    public String toString() {
        return "ShufflingList[" + this.entries + "]";
    }

    public static class WeightedEntry<T> {
        final T data;
        final int weight;
        private double randWeight;

        WeightedEntry(T data, int weight) {
            this.weight = weight;
            this.data = data;
        }

        private double getRandWeight() {
            return this.randWeight;
        }

        void setRandom(float random) {
            this.randWeight = -Math.pow((double)random, (double)(1.0F / (float)this.weight));
        }

        public T getData() {
            return this.data;
        }

        public int getWeight() {
            return this.weight;
        }

        @Override
        public String toString() {
            return this.weight + ":" + this.data;
        }

        public static <E> Codec<ShufflingList.WeightedEntry<E>> codec(final Codec<E> codec) {
            return new Codec<ShufflingList.WeightedEntry<E>>() {
                public <T> DataResult<Pair<ShufflingList.WeightedEntry<E>, T>> decode(DynamicOps<T> dynamicOps, T object) {
                    Dynamic<T> dynamic = new Dynamic<>(dynamicOps, object);
                    return dynamic.get("data").flatMap(codec::parse).map((data) -> {
                        return new ShufflingList.WeightedEntry<>(data, dynamic.get("weight").asInt(1));
                    }).map((entry) -> {
                        return Pair.of(entry, dynamicOps.empty());
                    });
                }

                public <T> DataResult<T> encode(ShufflingList.WeightedEntry<E> weightedEntry, DynamicOps<T> dynamicOps, T object) {
                    return dynamicOps.mapBuilder().add("weight", dynamicOps.createInt(weightedEntry.weight)).add("data", codec.encodeStart(dynamicOps, weightedEntry.data)).build(object);
                }
            };
        }
    }
}
