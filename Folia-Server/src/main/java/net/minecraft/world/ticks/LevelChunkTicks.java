package net.minecraft.world.ticks;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

public class LevelChunkTicks<T> implements SerializableTickContainer<T>, TickContainerAccess<T> {
    private final Queue<ScheduledTick<T>> tickQueue = new PriorityQueue<>(ScheduledTick.DRAIN_ORDER);
    @Nullable
    private List<SavedTick<T>> pendingTicks;
    private final Set<ScheduledTick<?>> ticksPerPosition = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    @Nullable
    private BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded;

    // Paper start - add dirty flag
    private boolean dirty;
    private long lastSaved = Long.MIN_VALUE;

    public boolean isDirty(final long tick) {
        return this.dirty || (!this.tickQueue.isEmpty() && tick != this.lastSaved);
    }

    public void clearDirty() {
        this.dirty = false;
    }
    // Paper end - add dirty flag
    // Folia start - region threading
    public void offsetTicks(final long offset) {
        if (offset == 0 || this.tickQueue.isEmpty()) {
            return;
        }
        final ScheduledTick<T>[] queue = this.tickQueue.toArray(new ScheduledTick[0]);
        this.tickQueue.clear();
        for (final ScheduledTick<T> entry : queue) {
            final ScheduledTick<T> newEntry = new ScheduledTick<>(
                entry.type(), entry.pos(), entry.triggerTick() + offset, entry.subTickOrder()
            );
            this.tickQueue.add(newEntry);
        }
    }
    // Folia end - region threading

    public LevelChunkTicks() {
    }

    public LevelChunkTicks(List<SavedTick<T>> ticks) {
        this.pendingTicks = ticks;

        for(SavedTick<T> savedTick : ticks) {
            this.ticksPerPosition.add(ScheduledTick.probe(savedTick.type(), savedTick.pos()));
        }

    }

    public void setOnTickAdded(@Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> tickConsumer) {
        this.onTickAdded = tickConsumer;
    }

    @Nullable
    public ScheduledTick<T> peek() {
        return this.tickQueue.peek();
    }

    @Nullable
    public ScheduledTick<T> poll() {
        ScheduledTick<T> scheduledTick = this.tickQueue.poll();
        if (scheduledTick != null) {
            this.dirty = true; // Paper - add dirty flag
            this.ticksPerPosition.remove(scheduledTick);
        }

        return scheduledTick;
    }

    @Override
    public void schedule(ScheduledTick<T> orderedTick) {
        if (this.ticksPerPosition.add(orderedTick)) {
            this.dirty = true; // Paper - add dirty flag
            this.scheduleUnchecked(orderedTick);
        }

    }

    private void scheduleUnchecked(ScheduledTick<T> orderedTick) {
        this.tickQueue.add(orderedTick);
        if (this.onTickAdded != null) {
            this.onTickAdded.accept(this, orderedTick);
        }

    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return this.ticksPerPosition.contains(ScheduledTick.probe(type, pos));
    }

    public void removeIf(Predicate<ScheduledTick<T>> predicate) {
        Iterator<ScheduledTick<T>> iterator = this.tickQueue.iterator();

        while(iterator.hasNext()) {
            ScheduledTick<T> scheduledTick = iterator.next();
            if (predicate.test(scheduledTick)) {
                iterator.remove(); this.dirty = true; // Paper - add dirty flag
                this.ticksPerPosition.remove(scheduledTick);
            }
        }

    }

    public Stream<ScheduledTick<T>> getAll() {
        return this.tickQueue.stream();
    }

    @Override
    public int count() {
        return this.tickQueue.size() + (this.pendingTicks != null ? this.pendingTicks.size() : 0);
    }

    @Override
    public ListTag save(long l, Function<T, String> function) {
        this.lastSaved = l; // Paper - add dirty system to level ticks
        ListTag listTag = new ListTag();
        if (this.pendingTicks != null) {
            for(SavedTick<T> savedTick : this.pendingTicks) {
                listTag.add(savedTick.save(function));
            }
        }

        for(ScheduledTick<T> scheduledTick : this.tickQueue) {
            listTag.add(SavedTick.saveTick(scheduledTick, function, l));
        }

        return listTag;
    }

    public void unpack(long time) {
        if (this.pendingTicks != null) {
            // Paper start - add dirty system to level chunk ticks
            if (this.tickQueue.isEmpty()) {
                this.lastSaved = time;
            }
            // Paper end - add dirty system to level chunk ticks
            int i = -this.pendingTicks.size();

            for(SavedTick<T> savedTick : this.pendingTicks) {
                this.scheduleUnchecked(savedTick.unpack(time, (long)(i++)));
            }
        }

        this.pendingTicks = null;
    }

    public static <T> LevelChunkTicks<T> load(ListTag tickQueue, Function<String, Optional<T>> nameToTypeFunction, ChunkPos pos) {
        ImmutableList.Builder<SavedTick<T>> builder = ImmutableList.builder();
        SavedTick.loadTickList(tickQueue, nameToTypeFunction, pos, builder::add);
        return new LevelChunkTicks<>(builder.build());
    }
}
