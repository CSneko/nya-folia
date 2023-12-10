package net.minecraft.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.util.ExtraCodecs;

public class RegistryOps<T> extends DelegatingOps<T> {
    private final RegistryOps.RegistryInfoLookup lookupProvider;

    private static RegistryOps.RegistryInfoLookup memoizeLookup(final RegistryOps.RegistryInfoLookup registryInfoGetter) {
        return new RegistryOps.RegistryInfoLookup() {
            private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new java.util.concurrent.ConcurrentHashMap<>(); // Paper - fix concurrent access to lookups field

            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return (Optional<RegistryOps.RegistryInfo<T>>)this.lookups.computeIfAbsent(registryRef, registryInfoGetter::lookup); // Paper - fix concurrent access to lookups field
            }
        };
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, final HolderLookup.Provider wrapperLookup) {
        return create(delegate, memoizeLookup(new RegistryOps.RegistryInfoLookup() {
            @Override
            public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryRef) {
                return wrapperLookup.lookup(registryRef).map((wrapper) -> {
                    return new RegistryOps.RegistryInfo<>(wrapper, wrapper, wrapper.registryLifecycle());
                });
            }
        }));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup registryInfoGetter) {
        return new RegistryOps<>(delegate, registryInfoGetter);
    }

    private RegistryOps(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup registryInfoGetter) {
        super(delegate);
        this.lookupProvider = registryInfoGetter;
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> registryRef) {
        return this.lookupProvider.lookup(registryRef).map(RegistryOps.RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> registryRef) {
        return this.lookupProvider.lookup(registryRef).map(RegistryOps.RegistryInfo::getter);
    }

    public static <E, O> RecordCodecBuilder<O, HolderGetter<E>> retrieveGetter(ResourceKey<? extends Registry<? extends E>> registryRef) {
        return ExtraCodecs.retrieveContext((ops) -> {
            if (ops instanceof RegistryOps<?> registryOps) {
                return registryOps.lookupProvider.lookup(registryRef).map((info) -> {
                    return DataResult.success(info.getter(), info.elementsLifecycle());
                }).orElseGet(() -> {
                    return DataResult.error(() -> {
                        return "Unknown registry: " + registryRef;
                    });
                });
            } else {
                return DataResult.error(() -> {
                    return "Not a registry ops";
                });
            }
        }).forGetter((object) -> {
            return null;
        });
    }

    public static <E, O> RecordCodecBuilder<O, Holder.Reference<E>> retrieveElement(ResourceKey<E> key) {
        ResourceKey<? extends Registry<E>> resourceKey = ResourceKey.createRegistryKey(key.registry());
        return ExtraCodecs.retrieveContext((ops) -> {
            if (ops instanceof RegistryOps<?> registryOps) {
                return registryOps.lookupProvider.lookup(resourceKey).flatMap((info) -> {
                    return info.getter().get(key);
                }).map(DataResult::success).orElseGet(() -> {
                    return DataResult.error(() -> {
                        return "Can't find value: " + key;
                    });
                });
            } else {
                return DataResult.error(() -> {
                    return "Not a registry ops";
                });
            }
        }).forGetter((object) -> {
            return null;
        });
    }

    public static record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {
    }

    public interface RegistryInfoLookup {
        <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef);
    }
}
