package io.papermc.paper.threadedregions;

import ca.spottedleaf.concurrentutil.scheduler.SchedulerThreadPool;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import com.mojang.logging.LogUtils;
import io.papermc.paper.chunk.system.scheduling.ChunkHolderManager;
import io.papermc.paper.configuration.GlobalConfiguration;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class TickRegions implements ThreadedRegionizer.RegionCallbacks<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static int getRegionChunkShift() {
        return 4;
    }

    private static boolean initialised;
    private static TickRegionScheduler scheduler;

    public static TickRegionScheduler getScheduler() {
        return scheduler;
    }

    public static void init(final GlobalConfiguration.ThreadedRegions config) {
        if (initialised) {
            return;
        }
        initialised = true;

        int tickThreads;
        if (config.threads <= 0) {
            tickThreads = Runtime.getRuntime().availableProcessors() / 2;
            if (tickThreads <= 4) {
                tickThreads = 1;
            } else {
                tickThreads =  tickThreads / 4;
            }
        } else {
            tickThreads = config.threads;
        }

        scheduler = new TickRegionScheduler(tickThreads);
        LOGGER.info("Regionised ticking is enabled with " + tickThreads + " tick threads");
    }

    @Override
    public TickRegionData createNewData(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
        return new TickRegionData(region);
    }

    @Override
    public TickRegionSectionData createNewSectionData(final int sectionX, final int sectionZ, final int sectionShift) {
        return null;
    }

    @Override
    public void onRegionCreate(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
        final TickRegionData data = region.getData();
        // post-region merge/split regioninfo update
        data.getRegionStats().updateFrom(data.getOrCreateRegionizedData(data.world.worldRegionData));
    }

    @Override
    public void onRegionDestroy(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
        // nothing for now
        // Folia start - profiler
        if (region.getData().profiler != null) {
            region.getData().profiler.stopProfiler();
        }
        // Folia end - profiler
    }

    @Override
    public void onRegionActive(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
        final TickRegionData data = region.getData();

        data.tickHandle.checkInitialSchedule();
        scheduler.scheduleRegion(data.tickHandle);
    }

    @Override
    public void onRegionInactive(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
        final TickRegionData data = region.getData();

        scheduler.descheduleRegion(data.tickHandle);
        // old handle cannot be scheduled anymore, copy to a new handle
        data.tickHandle = data.tickHandle.copy();
    }

    @Override
    public void preMerge(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> from,
                         final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> into) {
        // Folia start - profiler
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = from.getData().profiler;
        if (profiler != null) {
            profiler.profilerGroup.preMerge(from, into);
        }
        // Folia end - profiler
    }

    @Override
    public void preSplit(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> from,
                         final java.util.List<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> into) {
        // Folia start - profiler
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = from.getData().profiler;
        if (profiler != null) {
            profiler.profilerGroup.preSplit(from, into);
        }
        // Folia end - profiler
    }

    public static final class TickRegionSectionData implements ThreadedRegionizer.ThreadedRegionSectionData {}

    public static final class RegionStats {

        private final AtomicInteger entityCount = new AtomicInteger();
        private final AtomicInteger playerCount = new AtomicInteger();
        private final AtomicInteger chunkCount = new AtomicInteger();

        public int getEntityCount() {
            return this.entityCount.get();
        }

        public int getPlayerCount() {
            return this.playerCount.get();
        }

        public int getChunkCount() {
            return this.chunkCount.get();
        }

        void updateFrom(final RegionizedWorldData data) {
            this.entityCount.setRelease(data == null ? 0 : data.getEntityCount());
            this.playerCount.setRelease(data == null ? 0 : data.getPlayerCount());
            this.chunkCount.setRelease(data == null ? 0 : data.getChunkCount());
        }

        static void updateCurrentRegion() {
            TickRegionScheduler.getCurrentRegion().getData().getRegionStats().updateFrom(TickRegionScheduler.getCurrentRegionizedWorldData());
        }
    }

    public static final class TickRegionData implements ThreadedRegionizer.ThreadedRegionData<TickRegionData, TickRegionSectionData> {

        private static final AtomicLong ID_GENERATOR = new AtomicLong();
        /** Never 0L, since 0L is reserved for global region. */
        public final long id = ID_GENERATOR.incrementAndGet();

        public final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region;
        public final ServerLevel world;

        // generic regionised data
        private final Reference2ReferenceOpenHashMap<RegionizedData<?>, Object> regionizedData = new Reference2ReferenceOpenHashMap<>();

        // tick data
        private ConcreteRegionTickHandle tickHandle = new ConcreteRegionTickHandle(this, SchedulerThreadPool.DEADLINE_NOT_SET);

        // queue data
        private final RegionizedTaskQueue.RegionTaskQueueData taskQueueData;

        // chunk holder manager data
        private final ChunkHolderManager.HolderManagerRegionData holderManagerRegionData = new ChunkHolderManager.HolderManagerRegionData();

        // async-safe read-only region data
        private final RegionStats regionStats;

        public volatile ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler; // Folia - profiler

        private TickRegionData(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region) {
            this.region = region;
            this.world = region.regioniser.world;
            this.taskQueueData = new RegionizedTaskQueue.RegionTaskQueueData(this.world.taskQueueRegionData);
            this.regionStats = new RegionStats();
        }

        public RegionStats getRegionStats() {
            return this.regionStats;
        }

        public RegionizedTaskQueue.RegionTaskQueueData getTaskQueueData() {
            return this.taskQueueData;
        }

        // the value returned can be invalidated at any time, except when the caller
        // is ticking this region
        public TickRegionScheduler.RegionScheduleHandle getRegionSchedulingHandle() {
            return this.tickHandle;
        }

        public long getCurrentTick() {
            return this.tickHandle.getCurrentTick();
        }

        public ChunkHolderManager.HolderManagerRegionData getHolderManagerRegionData() {
            return this.holderManagerRegionData;
        }

        <T> T getRegionizedData(final RegionizedData<T> regionizedData) {
            return (T)this.regionizedData.get(regionizedData);
        }

        <T> T getOrCreateRegionizedData(final RegionizedData<T> regionizedData) {
            T ret = (T)this.regionizedData.get(regionizedData);

            if (ret != null) {
                return ret;
            }

            ret = regionizedData.createNewValue();
            this.regionizedData.put(regionizedData, ret);

            return ret;
        }

        @Override
        public void split(final ThreadedRegionizer<TickRegionData, TickRegionSectionData> regioniser,
                          final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> into,
                          final ReferenceOpenHashSet<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> regions) {
            final int shift = regioniser.sectionChunkShift;

            // tick data
            // note: here it is OK force us to access tick handle, as this region is owned (and thus not scheduled),
            // and the other regions to split into are not scheduled yet.
            for (final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region : regions) {
                final TickRegionData data = region.getData();
                data.tickHandle.copyDeadlineAndTickCount(this.tickHandle);
            }

            // generic regionised data
            for (final Iterator<Reference2ReferenceMap.Entry<RegionizedData<?>, Object>> dataIterator = this.regionizedData.reference2ReferenceEntrySet().fastIterator();
                 dataIterator.hasNext();) {
                final Reference2ReferenceMap.Entry<RegionizedData<?>, Object> regionDataEntry = dataIterator.next();
                final RegionizedData<?> data = regionDataEntry.getKey();
                final Object from = regionDataEntry.getValue();

                final ReferenceOpenHashSet<Object> dataSet = new ReferenceOpenHashSet<>(regions.size(), 0.75f);

                for (final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region : regions) {
                    dataSet.add(region.getData().getOrCreateRegionizedData(data));
                }

                final Long2ReferenceOpenHashMap<Object> regionToData = new Long2ReferenceOpenHashMap<>(into.size(), 0.75f);

                for (final Iterator<Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>>> regionIterator = into.long2ReferenceEntrySet().fastIterator();
                     regionIterator.hasNext();) {
                    final Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> entry = regionIterator.next();
                    final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region = entry.getValue();
                    final Object to = region.getData().getOrCreateRegionizedData(data);

                    regionToData.put(entry.getLongKey(), to);
                }

                ((RegionizedData<Object>)data).getCallback().split(from, shift, regionToData, dataSet);
            }

            // chunk holder manager data
            {
                final ReferenceOpenHashSet<ChunkHolderManager.HolderManagerRegionData> dataSet = new ReferenceOpenHashSet<>(regions.size(), 0.75f);

                for (final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region : regions) {
                    dataSet.add(region.getData().holderManagerRegionData);
                }

                final Long2ReferenceOpenHashMap<ChunkHolderManager.HolderManagerRegionData> regionToData = new Long2ReferenceOpenHashMap<>(into.size(), 0.75f);

                for (final Iterator<Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>>> regionIterator = into.long2ReferenceEntrySet().fastIterator();
                     regionIterator.hasNext();) {
                    final Long2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData>> entry = regionIterator.next();
                    final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> region = entry.getValue();
                    final ChunkHolderManager.HolderManagerRegionData to = region.getData().holderManagerRegionData;

                    regionToData.put(entry.getLongKey(), to);
                }

                this.holderManagerRegionData.split(shift, regionToData, dataSet);
            }

            // task queue
            this.taskQueueData.split(regioniser, into);
        }

        @Override
        public void mergeInto(final ThreadedRegionizer.ThreadedRegion<TickRegionData, TickRegionSectionData> into) {
            // Note: merge target is always a region being released from ticking
            final TickRegionData data = into.getData();
            final long currentTickTo = data.getCurrentTick();
            final long currentTickFrom = this.getCurrentTick();

            // here we can access tickHandle because the target (into) is the region being released, so it is
            // not actually scheduled
            // there's not really a great solution to the tick problem, no matter what it'll be messed up
            // we will pick the greatest time delay so that tps will not exceed TICK_RATE
            data.tickHandle.updateSchedulingToMax(this.tickHandle);

            // generic regionised data
            final long fromTickOffset = currentTickTo - currentTickFrom; // see merge jd
            for (final Iterator<Reference2ReferenceMap.Entry<RegionizedData<?>, Object>> iterator = this.regionizedData.reference2ReferenceEntrySet().fastIterator();
                 iterator.hasNext();) {
                final Reference2ReferenceMap.Entry<RegionizedData<?>, Object> entry = iterator.next();
                final RegionizedData<?> regionizedData = entry.getKey();
                final Object from = entry.getValue();
                final Object to = into.getData().getOrCreateRegionizedData(regionizedData);

                ((RegionizedData<Object>)regionizedData).getCallback().merge(from, to, fromTickOffset);
            }

            // chunk holder manager data
            this.holderManagerRegionData.merge(into.getData().holderManagerRegionData, fromTickOffset);

            // task queue
            this.taskQueueData.mergeInto(data.taskQueueData);
        }
    }

    private static final class ConcreteRegionTickHandle extends TickRegionScheduler.RegionScheduleHandle {

        private final TickRegionData region;

        private ConcreteRegionTickHandle(final TickRegionData region, final long start) {
            super(region, start);
            this.region = region;
        }

        private ConcreteRegionTickHandle copy() {
            final ConcreteRegionTickHandle ret = new ConcreteRegionTickHandle(this.region, this.getScheduledStart());

            ret.currentTick = this.currentTick;
            ret.lastTickStart = this.lastTickStart;
            ret.tickSchedule.setLastPeriod(this.tickSchedule.getLastPeriod());

            return ret;
        }

        private void updateSchedulingToMax(final ConcreteRegionTickHandle from) {
            if (from.getScheduledStart() == SchedulerThreadPool.DEADLINE_NOT_SET) {
                return;
            }

            if (this.getScheduledStart() == SchedulerThreadPool.DEADLINE_NOT_SET) {
                this.updateScheduledStart(from.getScheduledStart());
                return;
            }

            this.updateScheduledStart(TimeUtil.getGreatestTime(from.getScheduledStart(), this.getScheduledStart()));
        }

        private void copyDeadlineAndTickCount(final ConcreteRegionTickHandle from) {
            this.currentTick = from.currentTick;

            if (from.getScheduledStart() == SchedulerThreadPool.DEADLINE_NOT_SET) {
                return;
            }

            this.tickSchedule.setLastPeriod(from.tickSchedule.getLastPeriod());
            this.setScheduledStart(from.getScheduledStart());
        }

        private void checkInitialSchedule() {
            if (this.getScheduledStart() == SchedulerThreadPool.DEADLINE_NOT_SET) {
                this.updateScheduledStart(System.nanoTime() + TickRegionScheduler.TIME_BETWEEN_TICKS);
            }
        }

        @Override
        protected boolean tryMarkTicking() {
            return this.region.region.tryMarkTicking(ConcreteRegionTickHandle.this::isMarkedAsNonSchedulable);
        }

        @Override
        protected boolean markNotTicking() {
            return this.region.region.markNotTicking();
        }

        // Folia start - profiler
        @Override
        protected void addTickTime(final TickRegionScheduler.TickTime time) {
            super.addTickTime(time);

            final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler();
            profiler.addTickTime(time);
            profiler.checkStop();
         }
         // Folia end - profiler

        @Override
        protected void tickRegion(final int tickCount, final long startTime, final long scheduledEnd) {
            final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
            profiler.startTick(); try { // Folia - profiler
            MinecraftServer.getServer().tickServer(startTime, scheduledEnd, TimeUnit.MILLISECONDS.toMillis(10L), this.region);
            } finally { profiler.stopTick(); } // Folia - profiler
        }

        @Override
        protected boolean runRegionTasks(final BooleanSupplier canContinue) {
            final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia start - profiler
            profiler.startInBetweenTick(); try { // Folia - profiler
            final RegionizedTaskQueue.RegionTaskQueueData queue = this.region.taskQueueData;

            boolean processedChunkTask = false;

            boolean executeChunkTask = true;
            boolean executeTickTask = true;
            do {
                if (executeTickTask) {
                    executeTickTask = queue.executeTickTask();
                }
                if (executeChunkTask) {
                    processedChunkTask |= (executeChunkTask = queue.executeChunkTask());
                }
            } while ((executeChunkTask | executeTickTask) && canContinue.getAsBoolean());

            if (processedChunkTask) {
                // if we processed any chunk tasks, try to process ticket level updates for full status changes
                this.region.world.chunkTaskScheduler.chunkHolderManager.processTicketUpdates();
            }
            return true;
            } finally { profiler.stopInBetweenTick(); } // Folia - profiler
        }

        @Override
        protected boolean hasIntermediateTasks() {
            return this.region.taskQueueData.hasTasks();
        }
    }
}
