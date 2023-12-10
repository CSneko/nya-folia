package net.minecraft.util.worldupdate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMaps;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenCustomHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

public class WorldUpgrader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder()).setDaemon(true).build();
    private final Registry<LevelStem> dimensions;
    private final Set<ResourceKey<Level>> levels;
    private final boolean eraseCache;
    private final LevelStorageSource.LevelStorageAccess levelStorage;
    private final Thread thread;
    private final DataFixer dataFixer;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile float progress;
    private volatile int totalChunks;
    private volatile int converted;
    private volatile int skipped;
    private final Object2FloatMap<ResourceKey<Level>> progressMap = Object2FloatMaps.synchronize(new Object2FloatOpenCustomHashMap(Util.identityStrategy()));
    private volatile Component status = Component.translatable("optimizeWorld.stage.counting");
    public static final Pattern REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    private final DimensionDataStorage overworldDataStorage;

    public WorldUpgrader(LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, Registry<LevelStem> dimensionOptionsRegistry, boolean eraseCache) {
        this.dimensions = dimensionOptionsRegistry;
        this.levels = (Set) java.util.stream.Stream.of(session.dimensionType).map(Registries::levelStemToLevel).collect(Collectors.toUnmodifiableSet()); // CraftBukkit
        this.eraseCache = eraseCache;
        this.dataFixer = dataFixer;
        this.levelStorage = session;
        this.overworldDataStorage = new DimensionDataStorage(this.levelStorage.getDimensionPath(Level.OVERWORLD).resolve("data").toFile(), dataFixer);
        this.thread = WorldUpgrader.THREAD_FACTORY.newThread(this::work);
        this.thread.setUncaughtExceptionHandler((thread, throwable) -> {
            WorldUpgrader.LOGGER.error("Error upgrading world", throwable);
            this.status = Component.translatable("optimizeWorld.stage.failed");
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException interruptedexception) {
            ;
        }

    }

    private void work() {
        this.totalChunks = 0;
        Builder<ResourceKey<Level>, ListIterator<ChunkPos>> builder = ImmutableMap.builder();

        List list;

        for (Iterator iterator = this.levels.iterator(); iterator.hasNext(); this.totalChunks += list.size()) {
            ResourceKey<Level> resourcekey = (ResourceKey) iterator.next();

            list = this.getAllChunkPos(resourcekey);
            builder.put(resourcekey, list.listIterator());
        }

        if (this.totalChunks == 0) {
            this.finished = true;
        } else {
            float f = (float) this.totalChunks;
            ImmutableMap<ResourceKey<Level>, ListIterator<ChunkPos>> immutablemap = builder.build();
            Builder<ResourceKey<Level>, ChunkStorage> builder1 = ImmutableMap.builder();
            Iterator iterator1 = this.levels.iterator();

            while (iterator1.hasNext()) {
                ResourceKey<Level> resourcekey1 = (ResourceKey) iterator1.next();
                Path path = this.levelStorage.getDimensionPath(resourcekey1);

                builder1.put(resourcekey1, new ChunkStorage(path.resolve("region"), this.dataFixer, true));
            }

            ImmutableMap<ResourceKey<Level>, ChunkStorage> immutablemap1 = builder1.build();
            long i = Util.getMillis();

            this.status = Component.translatable("optimizeWorld.stage.upgrading");

            while (this.running) {
                boolean flag = false;
                float f1 = 0.0F;

                float f2;

                for (Iterator iterator2 = this.levels.iterator(); iterator2.hasNext(); f1 += f2) {
                    ResourceKey<Level> resourcekey2 = (ResourceKey) iterator2.next();
                    ListIterator<ChunkPos> listiterator = (ListIterator) immutablemap.get(resourcekey2);
                    ChunkStorage ichunkloader = (ChunkStorage) immutablemap1.get(resourcekey2);

                    if (listiterator.hasNext()) {
                        ChunkPos chunkcoordintpair = (ChunkPos) listiterator.next();
                        boolean flag1 = false;

                        try {
                            CompoundTag nbttagcompound = (CompoundTag) ((Optional) ichunkloader.read(chunkcoordintpair).join()).orElse((Object) null);

                            if (nbttagcompound != null) {
                                int j = ChunkStorage.getVersion(nbttagcompound);
                                ChunkGenerator chunkgenerator = ((LevelStem) this.dimensions.getOrThrow(Registries.levelToLevelStem(resourcekey2))).generator();
                                CompoundTag nbttagcompound1 = ichunkloader.upgradeChunkTag(Registries.levelToLevelStem(resourcekey2), () -> { // CraftBukkit
                                    return this.overworldDataStorage;
                                }, nbttagcompound, chunkgenerator.getTypeNameForDataFixer(), chunkcoordintpair, null); // CraftBukkit
                                ChunkPos chunkcoordintpair1 = new ChunkPos(nbttagcompound1.getInt("xPos"), nbttagcompound1.getInt("zPos"));

                                if (!chunkcoordintpair1.equals(chunkcoordintpair)) {
                                    WorldUpgrader.LOGGER.warn("Chunk {} has invalid position {}", chunkcoordintpair, chunkcoordintpair1);
                                }

                                boolean flag2 = j < SharedConstants.getCurrentVersion().getDataVersion().getVersion();

                                if (this.eraseCache) {
                                    flag2 = flag2 || nbttagcompound1.contains("Heightmaps");
                                    nbttagcompound1.remove("Heightmaps");
                                    flag2 = flag2 || nbttagcompound1.contains("isLightOn");
                                    nbttagcompound1.remove("isLightOn");
                                    ListTag nbttaglist = nbttagcompound1.getList("sections", 10);

                                    for (int k = 0; k < nbttaglist.size(); ++k) {
                                        CompoundTag nbttagcompound2 = nbttaglist.getCompound(k);

                                        flag2 = flag2 || nbttagcompound2.contains("BlockLight");
                                        nbttagcompound2.remove("BlockLight");
                                        flag2 = flag2 || nbttagcompound2.contains("SkyLight");
                                        nbttagcompound2.remove("SkyLight");
                                    }
                                }

                                if (flag2) {
                                    ichunkloader.write(chunkcoordintpair, nbttagcompound1);
                                    flag1 = true;
                                }
                            }
                        } catch (CompletionException | ReportedException reportedexception) {
                            Throwable throwable = reportedexception.getCause();

                            if (!(throwable instanceof IOException)) {
                                throw reportedexception;
                            }

                            WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkcoordintpair, throwable);
                            // Paper start
                        } catch (IOException e) {
                            WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkcoordintpair, e);
                        }
                        // Paper end

                        if (flag1) {
                            ++this.converted;
                        } else {
                            ++this.skipped;
                        }

                        flag = true;
                    }

                    f2 = (float) listiterator.nextIndex() / f;
                    this.progressMap.put(resourcekey2, f2);
                }

                this.progress = f1;
                if (!flag) {
                    this.running = false;
                }
            }

            this.status = Component.translatable("optimizeWorld.stage.finished");
            UnmodifiableIterator unmodifiableiterator = immutablemap1.values().iterator();

            while (unmodifiableiterator.hasNext()) {
                ChunkStorage ichunkloader1 = (ChunkStorage) unmodifiableiterator.next();

                try {
                    ichunkloader1.close();
                } catch (IOException ioexception) {
                    WorldUpgrader.LOGGER.error("Error upgrading chunk", ioexception);
                }
            }

            this.overworldDataStorage.save();
            i = Util.getMillis() - i;
            WorldUpgrader.LOGGER.info("World optimizaton finished after {} ms", i);
            this.finished = true;
        }
    }

    private List<ChunkPos> getAllChunkPos(ResourceKey<Level> world) {
        File file = this.levelStorage.getDimensionPath(world).toFile();
        File file1 = new File(file, "region");
        File[] afile = file1.listFiles((file2, s) -> {
            return s.endsWith(".mca");
        });

        if (afile == null) {
            return ImmutableList.of();
        } else {
            List<ChunkPos> list = Lists.newArrayList();
            File[] afile1 = afile;
            int i = afile.length;

            for (int j = 0; j < i; ++j) {
                File file2 = afile1[j];
                Matcher matcher = WorldUpgrader.REGEX.matcher(file2.getName());

                if (matcher.matches()) {
                    int k = Integer.parseInt(matcher.group(1)) << 5;
                    int l = Integer.parseInt(matcher.group(2)) << 5;

                    try {
                        RegionFile regionfile = new RegionFile(file2.toPath(), file1.toPath(), true);

                        try {
                            for (int i1 = 0; i1 < 32; ++i1) {
                                for (int j1 = 0; j1 < 32; ++j1) {
                                    ChunkPos chunkcoordintpair = new ChunkPos(i1 + k, j1 + l);

                                    if (regionfile.doesChunkExist(chunkcoordintpair)) {
                                        list.add(chunkcoordintpair);
                                    }
                                }
                            }
                        } catch (Throwable throwable) {
                            try {
                                regionfile.close();
                            } catch (Throwable throwable1) {
                                throwable.addSuppressed(throwable1);
                            }

                            throw throwable;
                        }

                        regionfile.close();
                    } catch (Throwable throwable2) {
                        ;
                    }
                }
            }

            return list;
        }
    }

    public boolean isFinished() {
        return this.finished;
    }

    public Set<ResourceKey<Level>> levels() {
        return this.levels;
    }

    public float dimensionProgress(ResourceKey<Level> world) {
        return this.progressMap.getFloat(world);
    }

    public float getProgress() {
        return this.progress;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getConverted() {
        return this.converted;
    }

    public int getSkipped() {
        return this.skipped;
    }

    public Component getStatus() {
        return this.status;
    }
}
