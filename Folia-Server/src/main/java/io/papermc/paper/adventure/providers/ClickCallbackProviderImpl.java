package io.papermc.paper.adventure.providers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("UnstableApiUsage") // permitted provider
public class ClickCallbackProviderImpl implements ClickCallback.Provider {

    public static final CallbackManager CALLBACK_MANAGER = new CallbackManager();

    @Override
    public @NotNull ClickEvent create(final @NotNull ClickCallback<Audience> callback, final ClickCallback.@NotNull Options options) {
        return ClickEvent.runCommand("/paper:callback " + CALLBACK_MANAGER.addCallback(callback, options));
    }

    public static final class CallbackManager {

        private final java.util.concurrent.ConcurrentHashMap<UUID, StoredCallback> callbacks = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - region threading
        // Folia - region threading

        private CallbackManager() {
        }

        public UUID addCallback(final @NotNull ClickCallback<Audience> callback, final ClickCallback.@NotNull Options options) {
            final UUID id = UUID.randomUUID();
            final StoredCallback scb = new StoredCallback(callback, options, id); // Folia - region threading
            this.callbacks.put(scb.id(), scb); // Folia - region threading
            return id;
        }

        public void handleQueue(final int currentTick) {
            // Evict expired entries
            if (currentTick % 100 == 0) {
                this.callbacks.values().removeIf(StoredCallback::expired); // Folia - region threading - don't read uses field
            }

            // Folia - region threading
        }

        public void runCallback(final @NotNull Audience audience, final UUID id) {
            // Folia start - region threading
            final StoredCallback[] use = new StoredCallback[1];
            this.callbacks.computeIfPresent(id, (final UUID keyInMap, final StoredCallback value) -> {
                if (!value.valid()) {
                    return null;
                }
                use[0] = value;
                value.takeUse();
                return value.valid() ? value : null;
            });
            final StoredCallback callback = use[0];
            if (callback != null) { //TODO Message if expired/invalid?
                // Folia end - region threading
                callback.callback.accept(audience);
            }
        }
    }

    private static final class StoredCallback {
        private final long startedAt = System.nanoTime();
        private final ClickCallback<Audience> callback;
        private final long lifetime;
        private final UUID id;
        private int remainingUses;

        private StoredCallback(final @NotNull ClickCallback<Audience> callback, final ClickCallback.@NotNull Options options, final UUID id) {
            this.callback = callback;
            this.lifetime = options.lifetime().toNanos();
            this.remainingUses = options.uses();
            this.id = id;
        }

        public void takeUse() {
            if (this.remainingUses != ClickCallback.UNLIMITED_USES) {
                this.remainingUses--;
            }
        }

        public boolean hasRemainingUses() {
            return this.remainingUses == ClickCallback.UNLIMITED_USES || this.remainingUses > 0;
        }

        public boolean expired() {
            return System.nanoTime() - this.startedAt >= this.lifetime;
        }

        public boolean valid() {
            return hasRemainingUses() && !expired();
        }

        public UUID id() {
            return this.id;
        }
    }
}
