package net.minecraft.core;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

public class MappedRegistry<T> implements WritableRegistry<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    final ResourceKey<? extends Registry<T>> key;
    private final ObjectList<Holder.Reference<T>> byId = new ObjectArrayList<>(256);
    private final it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<T> toId = new it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<T>(2048);// Paper - use bigger expected size to reduce collisions and direct intent for FastUtil to be identity map
    private final Map<ResourceLocation, Holder.Reference<T>> byLocation = new HashMap<>(2048); // Paper - use bigger expected size to reduce collisions
    private final Map<ResourceKey<T>, Holder.Reference<T>> byKey = new HashMap<>(2048); // Paper - use bigger expected size to reduce collisions
    private final Map<T, Holder.Reference<T>> byValue = new IdentityHashMap<>(2048); // Paper - use bigger expected size to reduce collisions
    private final Map<T, Lifecycle> lifecycles = new IdentityHashMap<>(2048); // Paper - use bigger expected size to reduce collisions
    private Lifecycle registryLifecycle;
    private volatile Map<TagKey<T>, HolderSet.Named<T>> tags = new IdentityHashMap<>();
    private boolean frozen;
    @Nullable
    private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;
    @Nullable
    private List<Holder.Reference<T>> holdersInOrder;
    private int nextId;
    private final HolderLookup.RegistryLookup<T> lookup = new HolderLookup.RegistryLookup<T>() {
        @Override
        public ResourceKey<? extends Registry<? extends T>> key() {
            return MappedRegistry.this.key;
        }

        @Override
        public Lifecycle registryLifecycle() {
            return MappedRegistry.this.registryLifecycle();
        }

        @Override
        public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
            return MappedRegistry.this.getHolder(key);
        }

        @Override
        public Stream<Holder.Reference<T>> listElements() {
            return MappedRegistry.this.holders();
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
            return MappedRegistry.this.getTag(tag);
        }

        @Override
        public Stream<HolderSet.Named<T>> listTags() {
            return MappedRegistry.this.getTags().map(Pair::getSecond);
        }
    };

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle) {
        this(key, lifecycle, false);
    }

    public MappedRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, boolean intrusive) {
        this.toId.defaultReturnValue(-1); // Paper
        Bootstrap.checkBootstrapCalled(() -> {
            return "registry " + key;
        });
        this.key = key;
        this.registryLifecycle = lifecycle;
        if (intrusive) {
            this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
        }

    }

    @Override
    public ResourceKey<? extends Registry<T>> key() {
        return this.key;
    }

    @Override
    public String toString() {
        return "Registry[" + this.key + " (" + this.registryLifecycle + ")]";
    }

    private List<Holder.Reference<T>> holdersInOrder() {
        if (this.holdersInOrder == null) {
            this.holdersInOrder = this.byId.stream().filter(Objects::nonNull).toList();
        }

        return this.holdersInOrder;
    }

    private void validateWrite() {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen");
        }
    }

    private void validateWrite(ResourceKey<T> key) {
        if (this.frozen) {
            throw new IllegalStateException("Registry is already frozen (trying to add key " + key + ")");
        }
    }

    public Holder.Reference<T> registerMapping(int rawId, ResourceKey<T> key, T value, Lifecycle lifecycle) {
        this.validateWrite(key);
        Validate.notNull(key);
        Validate.notNull(value);
        if (this.byLocation.containsKey(key.location())) {
            Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + key + "' to registry"));
        }

        if (this.byValue.containsKey(value)) {
            Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + value + "' to registry"));
        }

        Holder.Reference<T> reference;
        if (this.unregisteredIntrusiveHolders != null) {
            reference = this.unregisteredIntrusiveHolders.remove(value);
            if (reference == null) {
                throw new AssertionError("Missing intrusive holder for " + key + ":" + value);
            }

            reference.bindKey(key);
        } else {
            reference = this.byKey.computeIfAbsent(key, (keyx) -> {
                return Holder.Reference.createStandAlone(this.holderOwner(), keyx);
            });
        }

        this.byKey.put(key, reference);
        this.byLocation.put(key.location(), reference);
        this.byValue.put(value, reference);
        this.byId.size(Math.max(this.byId.size(), rawId + 1));
        this.byId.set(rawId, reference);
        this.toId.put(value, rawId);
        if (this.nextId <= rawId) {
            this.nextId = rawId + 1;
        }

        this.lifecycles.put(value, lifecycle);
        this.registryLifecycle = this.registryLifecycle.add(lifecycle);
        this.holdersInOrder = null;
        return reference;
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T entry, Lifecycle lifecycle) {
        return this.registerMapping(this.nextId, key, entry, lifecycle);
    }

    @Nullable
    @Override
    public ResourceLocation getKey(T value) {
        Holder.Reference<T> reference = this.byValue.get(value);
        return reference != null ? reference.key().location() : null;
    }

    @Override
    public Optional<ResourceKey<T>> getResourceKey(T entry) {
        return Optional.ofNullable(this.byValue.get(entry)).map(Holder.Reference::key);
    }

    @Override
    public int getId(@Nullable T value) {
        return this.toId.getInt(value);
    }

    @Nullable
    @Override
    public T get(@Nullable ResourceKey<T> key) {
        return getValueFromNullable(this.byKey.get(key));
    }

    @Nullable
    @Override
    public T byId(int index) {
        return (T)(index >= 0 && index < this.byId.size() ? getValueFromNullable(this.byId.get(index)) : null);
    }

    @Override
    public Optional<Holder.Reference<T>> getHolder(int rawId) {
        return rawId >= 0 && rawId < this.byId.size() ? Optional.ofNullable(this.byId.get(rawId)) : Optional.empty();
    }

    @Override
    public Optional<Holder.Reference<T>> getHolder(ResourceKey<T> key) {
        return Optional.ofNullable(this.byKey.get(key));
    }

    @Override
    public Holder<T> wrapAsHolder(T value) {
        Holder.Reference<T> reference = this.byValue.get(value);
        return (Holder<T>)(reference != null ? reference : Holder.direct(value));
    }

    Holder.Reference<T> getOrCreateHolderOrThrow(ResourceKey<T> key) {
        return this.byKey.computeIfAbsent(key, (key2) -> {
            if (this.unregisteredIntrusiveHolders != null) {
                throw new IllegalStateException("This registry can't create new holders without value");
            } else {
                this.validateWrite(key2);
                return Holder.Reference.createStandAlone(this.holderOwner(), key2);
            }
        });
    }

    @Override
    public int size() {
        return this.byKey.size();
    }

    @Override
    public Lifecycle lifecycle(T entry) {
        return this.lifecycles.get(entry);
    }

    @Override
    public Lifecycle registryLifecycle() {
        return this.registryLifecycle;
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.transform(this.holdersInOrder().iterator(), Holder::value);
    }

    @Nullable
    @Override
    public T get(@Nullable ResourceLocation id) {
        Holder.Reference<T> reference = this.byLocation.get(id);
        return getValueFromNullable(reference);
    }

    @Nullable
    private static <T> T getValueFromNullable(@Nullable Holder.Reference<T> entry) {
        return (T)(entry != null ? entry.value() : null);
    }

    @Override
    public Set<ResourceLocation> keySet() {
        return Collections.unmodifiableSet(this.byLocation.keySet());
    }

    @Override
    public Set<ResourceKey<T>> registryKeySet() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    @Override
    public Set<Map.Entry<ResourceKey<T>, T>> entrySet() {
        return Collections.unmodifiableSet(Maps.transformValues(this.byKey, Holder::value).entrySet());
    }

    @Override
    public Stream<Holder.Reference<T>> holders() {
        return this.holdersInOrder().stream();
    }

    @Override
    public Stream<Pair<TagKey<T>, HolderSet.Named<T>>> getTags() {
        return this.tags.entrySet().stream().map((entry) -> {
            return Pair.of(entry.getKey(), entry.getValue());
        });
    }

    @Override
    public HolderSet.Named<T> getOrCreateTag(TagKey<T> tag) {
        HolderSet.Named<T> named = this.tags.get(tag);
        if (named == null) {
            named = this.createTag(tag);
            Map<TagKey<T>, HolderSet.Named<T>> map = new IdentityHashMap<>(this.tags);
            map.put(tag, named);
            this.tags = map;
        }

        return named;
    }

    private HolderSet.Named<T> createTag(TagKey<T> tag) {
        return new HolderSet.Named<>(this.holderOwner(), tag);
    }

    @Override
    public Stream<TagKey<T>> getTagNames() {
        return this.tags.keySet().stream();
    }

    @Override
    public boolean isEmpty() {
        return this.byKey.isEmpty();
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return Util.getRandomSafe(this.holdersInOrder(), random);
    }

    @Override
    public boolean containsKey(ResourceLocation id) {
        return this.byLocation.containsKey(id);
    }

    @Override
    public boolean containsKey(ResourceKey<T> key) {
        return this.byKey.containsKey(key);
    }

    @Override
    public Registry<T> freeze() {
        if (this.frozen) {
            return this;
        } else {
            this.frozen = true;
            this.byValue.forEach((value, entry) -> {
                entry.bindValue(value);
            });
            List<ResourceLocation> list = this.byKey.entrySet().stream().filter((entry) -> {
                return !entry.getValue().isBound();
            }).map((entry) -> {
                return entry.getKey().location();
            }).sorted().toList();
            if (!list.isEmpty()) {
                throw new IllegalStateException("Unbound values in registry " + this.key() + ": " + list);
            } else {
                if (this.unregisteredIntrusiveHolders != null) {
                    if (!this.unregisteredIntrusiveHolders.isEmpty()) {
                        throw new IllegalStateException("Some intrusive holders were not registered: " + this.unregisteredIntrusiveHolders.values());
                    }

                    this.unregisteredIntrusiveHolders = null;
                }

                return this;
            }
        }
    }

    @Override
    public Holder.Reference<T> createIntrusiveHolder(T value) {
        if (this.unregisteredIntrusiveHolders == null) {
            throw new IllegalStateException("This registry can't create intrusive holders");
        } else {
            this.validateWrite();
            return this.unregisteredIntrusiveHolders.computeIfAbsent(value, (valuex) -> {
                return Holder.Reference.createIntrusive(this.asLookup(), valuex);
            });
        }
    }

    @Override
    public Optional<HolderSet.Named<T>> getTag(TagKey<T> tag) {
        return Optional.ofNullable(this.tags.get(tag));
    }

    @Override
    public void bindTags(Map<TagKey<T>, List<Holder<T>>> tagEntries) {
        Map<Holder.Reference<T>, List<TagKey<T>>> map = new IdentityHashMap<>();
        this.byKey.values().forEach((entry) -> {
            map.put(entry, new ArrayList<>());
        });
        tagEntries.forEach((tag, entries) -> {
            for(Holder<T> holder : entries) {
                if (!holder.canSerializeIn(this.asLookup())) {
                    throw new IllegalStateException("Can't create named set " + tag + " containing value " + holder + " from outside registry " + this);
                }

                if (!(holder instanceof Holder.Reference)) {
                    throw new IllegalStateException("Found direct holder " + holder + " value in tag " + tag);
                }

                Holder.Reference<T> reference = (Holder.Reference)holder;
                map.get(reference).add(tag);
            }

        });
        Set<TagKey<T>> set = Sets.difference(this.tags.keySet(), tagEntries.keySet());
        if (!set.isEmpty()) {
            LOGGER.warn("Not all defined tags for registry {} are present in data pack: {}", this.key(), set.stream().map((tag) -> {
                return tag.location().toString();
            }).sorted().collect(Collectors.joining(", ")));
        }

        Map<TagKey<T>, HolderSet.Named<T>> map2 = new IdentityHashMap<>(this.tags);
        tagEntries.forEach((tag, entries) -> {
            map2.computeIfAbsent(tag, this::createTag).bind(entries);
        });
        map.forEach(Holder.Reference::bindTags);
        this.tags = map2;
    }

    @Override
    public void resetTags() {
        this.tags.values().forEach((entryList) -> {
            entryList.bind(List.of());
        });
        this.byKey.values().forEach((entry) -> {
            entry.bindTags(Set.of());
        });
    }

    @Override
    public HolderGetter<T> createRegistrationLookup() {
        this.validateWrite();
        return new HolderGetter<T>() {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
                return Optional.of(this.getOrThrow(key));
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
                return MappedRegistry.this.getOrCreateHolderOrThrow(key);
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                return Optional.of(this.getOrThrow(tag));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
                return MappedRegistry.this.getOrCreateTag(tag);
            }
        };
    }

    @Override
    public HolderOwner<T> holderOwner() {
        return this.lookup;
    }

    @Override
    public HolderLookup.RegistryLookup<T> asLookup() {
        return this.lookup;
    }
}
