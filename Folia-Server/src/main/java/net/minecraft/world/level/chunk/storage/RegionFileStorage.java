package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

public class RegionFileStorage implements AutoCloseable {

    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap();
    private final Path folder;
    private final boolean sync;
    private final boolean isChunkData; // Paper

    // Paper start - cache regionfile does not exist state
    static final int MAX_NON_EXISTING_CACHE = 1024 * 64;
    private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet nonExistingRegionFiles = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
    private synchronized boolean doesRegionFilePossiblyExist(long position) {
        if (this.nonExistingRegionFiles.contains(position)) {
            this.nonExistingRegionFiles.addAndMoveToFirst(position);
            return false;
        }
        return true;
    }

    private synchronized void createRegionFile(long position) {
        this.nonExistingRegionFiles.remove(position);
    }

    private synchronized void markNonExisting(long position) {
        if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
            while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                this.nonExistingRegionFiles.removeLastLong();
            }
        }
    }

    public synchronized boolean doesRegionFileNotExistNoIO(ChunkPos pos) {
        long key = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
        return !this.doesRegionFilePossiblyExist(key);
    }
    // Paper end - cache regionfile does not exist state

    protected RegionFileStorage(Path directory, boolean dsync) { // Paper - protected constructor
        // Paper start - add isChunkData param
        this(directory, dsync, false);
    }
    RegionFileStorage(Path directory, boolean dsync, boolean isChunkData) {
        this.isChunkData = isChunkData;
        // Paper end - add isChunkData param
        this.folder = directory;
        this.sync = dsync;
    }

    // Paper start
    @Nullable
    public static ChunkPos getRegionFileCoordinates(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x << 5, z << 5);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    
    public synchronized RegionFile getRegionFileIfLoaded(ChunkPos chunkcoordintpair) {
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ()));
    }

    public synchronized boolean chunkExists(ChunkPos pos) throws IOException {
        RegionFile regionfile = getRegionFile(pos, true);

        return regionfile != null ? regionfile.hasChunk(pos) : false;
    }

    public synchronized RegionFile getRegionFile(ChunkPos chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit
        return this.getRegionFile(chunkcoordintpair, existingOnly, false);
    }
    public synchronized RegionFile getRegionFile(ChunkPos chunkcoordintpair, boolean existingOnly, boolean lock) throws IOException {
        // Paper end
        long i = ChunkPos.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ()); final long regionPos = i; // Paper - OBFHELPER
        RegionFile regionfile = (RegionFile) this.regionCache.getAndMoveToFirst(i);

        if (regionfile != null) {
            // Paper start
            if (lock) {
                // must be in this synchronized block
                regionfile.fileLock.lock();
            }
            // Paper end
            return regionfile;
        } else {
            // Paper start - cache regionfile does not exist state
            if (existingOnly && !this.doesRegionFilePossiblyExist(regionPos)) {
                return null;
            }
            // Paper end - cache regionfile does not exist state
            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper - configurable
                ((RegionFile) this.regionCache.removeLast()).close();
            }

            // Paper - only create directory if not existing only - moved down
            Path path = this.folder;
            int j = chunkcoordintpair.getRegionX();
            Path path1 = path.resolve("r." + j + "." + chunkcoordintpair.getRegionZ() + ".mca"); // Paper - diff on change
            if (existingOnly && !java.nio.file.Files.exists(path1)) { // Paper start - cache regionfile does not exist state
                this.markNonExisting(regionPos);
                return null; // CraftBukkit
            } else {
                this.createRegionFile(regionPos);
            }
            // Paper end - cache regionfile does not exist state
            FileUtil.createDirectoriesSafe(this.folder); // Paper - only create directory if not existing only - moved from above
            RegionFile regionfile1 = new RegionFile(path1, this.folder, this.sync, this.isChunkData); // Paper - allow for chunk regionfiles to regen header

            this.regionCache.putAndMoveToFirst(i, regionfile1);
            // Paper start
            if (lock) {
                // must be in this synchronized block
                regionfile1.fileLock.lock();
            }
            // Paper end
            return regionfile1;
        }
    }

    // Paper start
    private static void printOversizedLog(String msg, Path file, int x, int z) {
        org.apache.logging.log4j.LogManager.getLogger().fatal(msg + " (" + file.toString().replaceAll(".+[\\\\/]", "") + " - " + x + "," + z + ") Go clean it up to remove this message. /minecraft:tp " + (x<<4)+" 128 "+(z<<4) + " - DO NOT REPORT THIS TO PAPER - You may ask for help on Discord, but do not file an issue. These error messages can not be removed.");
    }

    private static final int DEFAULT_SIZE_THRESHOLD = 1024 * 8;
    private static final int OVERZEALOUS_TOTAL_THRESHOLD = 1024 * 64;
    private static final int OVERZEALOUS_THRESHOLD = 1024;
    private static int SIZE_THRESHOLD = DEFAULT_SIZE_THRESHOLD;
    private static void resetFilterThresholds() {
        SIZE_THRESHOLD = Math.max(1024 * 4, Integer.getInteger("Paper.FilterThreshhold", DEFAULT_SIZE_THRESHOLD));
    }
    static {
        resetFilterThresholds();
    }

    static boolean isOverzealous() {
        return SIZE_THRESHOLD == OVERZEALOUS_THRESHOLD;
    }


    private static CompoundTag readOversizedChunk(RegionFile regionfile, ChunkPos chunkCoordinate) throws IOException {
        synchronized (regionfile) {
            try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkCoordinate)) {
                CompoundTag oversizedData = regionfile.getOversizedData(chunkCoordinate.x, chunkCoordinate.z);
                CompoundTag chunk = NbtIo.read((DataInput) datainputstream);
                if (oversizedData == null) {
                    return chunk;
                }
                CompoundTag oversizedLevel = oversizedData.getCompound("Level");

                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "Entities", "Entities");
                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "TileEntities", "TileEntities");

                return chunk;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
        }
    }

    private static void mergeChunkList(CompoundTag level, CompoundTag oversizedLevel, String key, String oversizedKey) {
        net.minecraft.nbt.ListTag levelList = level.getList(key, 10);
        net.minecraft.nbt.ListTag oversizedList = oversizedLevel.getList(oversizedKey, 10);

        if (!oversizedList.isEmpty()) {
            levelList.addAll(oversizedList);
            level.put(key, levelList);
        }
    }

    private static int getNBTSize(net.minecraft.nbt.Tag nbtBase) {
        DataOutputStream test = new DataOutputStream(new org.apache.commons.io.output.NullOutputStream());
        try {
            nbtBase.write(test);
            return test.size();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // Paper End

    @Nullable
    public CompoundTag read(ChunkPos pos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(pos, true, true); // Paper
        if (regionfile == null) {
            return null;
        }
        // Paper start - Add regionfile parameter
        return this.read(pos, regionfile);
    }
    public CompoundTag read(ChunkPos pos, RegionFile regionfile) throws IOException {
        // We add the regionfile parameter to avoid the potential deadlock (on fileLock) if we went back to obtain a regionfile
        // if we decide to re-read
        // Paper end
        // CraftBukkit end
        try { // Paper
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(pos);

        // Paper start
        if (regionfile.isOversized(pos.x, pos.z)) {
            printOversizedLog("Loading Oversized Chunk!", regionfile.regionFile, pos.x, pos.z);
            return readOversizedChunk(regionfile, pos);
        }
        // Paper end
        CompoundTag nbttagcompound;
        label43:
        {
            try {
                if (datainputstream != null) {
                    nbttagcompound = NbtIo.read((DataInput) datainputstream);
                    // Paper start - recover from corrupt regionfile header
                    if (this.isChunkData) {
                        ChunkPos chunkPos = ChunkSerializer.getChunkCoordinate(nbttagcompound);
                        if (!chunkPos.equals(pos)) {
                            net.minecraft.server.MinecraftServer.LOGGER.error("Attempting to read chunk data at " + pos + " but got chunk data for " + chunkPos + " instead! Attempting regionfile recalculation for regionfile " + regionfile.regionFile.toAbsolutePath());
                            if (regionfile.recalculateHeader()) {
                                regionfile.fileLock.lock(); // otherwise we will unlock twice and only lock once.
                                return this.read(pos, regionfile);
                            }
                            net.minecraft.server.MinecraftServer.LOGGER.error("Can't recalculate regionfile header, regenerating chunk " + pos + " for " + regionfile.regionFile.toAbsolutePath());
                            return null;
                        }
                    }
                    // Paper end - recover from corrupt regionfile header
                    break label43;
                }

                nbttagcompound = null;
            } catch (Throwable throwable) {
                if (datainputstream != null) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }

            if (datainputstream != null) {
                datainputstream.close();
            }

            return nbttagcompound;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

        return nbttagcompound;
        } finally { // Paper start
            regionfile.fileLock.unlock();
        } // Paper end
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor scanner) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(chunkPos, true);
        if (regionfile == null) {
            return;
        }
        // CraftBukkit end
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkPos);

        try {
            if (datainputstream != null) {
                NbtIo.parse(datainputstream, scanner, NbtAccounter.unlimitedHeap());
            }
        } catch (Throwable throwable) {
            if (datainputstream != null) {
                try {
                    datainputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

    }

    protected void write(ChunkPos pos, @Nullable CompoundTag nbt) throws IOException {
        RegionFile regionfile = this.getRegionFile(pos, nbt == null, true); // CraftBukkit // Paper // Paper start - rewrite chunk system
        if (nbt == null && regionfile == null) {
            return;
        }
        // Paper end - rewrite chunk system
        try { // Paper
        int attempts = 0; Exception laste = null; while (attempts++ < 5) { try { // Paper

        if (nbt == null) {
            regionfile.clear(pos);
        } else {
            DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(pos);

            try {
                NbtIo.write(nbt, (DataOutput) dataoutputstream);
                regionfile.setStatus(pos.x, pos.z, ChunkSerializer.getStatus(nbt)); // Paper - cache status on disk
                regionfile.setOversized(pos.x, pos.z, false); // Paper - We don't do this anymore, mojang stores differently, but clear old meta flag if it exists to get rid of our own meta file once last oversized is gone
                dataoutputstream.close(); // Paper - only write if successful
            // Paper start - don't write garbage data to disk if writing serialization fails
            } catch (RegionFileSizeException e) {
                attempts = 5; // Don't retry
                regionfile.clear(pos);
                throw e;
                // Paper end - don't write garbage data to disk if writing serialization fails
            } catch (Throwable throwable) {
                if (dataoutputstream != null) {
                    try {
                        //dataoutputstream.close(); // Paper - don't write garbage data to disk if writing serialization fails
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }
            // Paper - move into try block to only write if successfully serialized
        }
        // Paper start
        return;
        } catch (Exception ex)  {
            laste = ex;
        }
        }

        if (laste != null) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(laste);
            net.minecraft.server.MinecraftServer.LOGGER.error("Failed to save chunk " + pos, laste);
        }
        // Paper end
        } finally { // Paper start
            regionfile.fileLock.unlock();
        } // Paper end
    }

    public synchronized void close() throws IOException { // Paper -> synchronized
        ExceptionCollector<IOException> exceptionsuppressor = new ExceptionCollector<>();
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptionsuppressor.add(ioexception);
            }
        }

        exceptionsuppressor.throwIfPresent();
    }

    public synchronized void flush() throws IOException { // Paper - synchronize
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            regionfile.flush();
        }

    }

    // Paper start
    public static final class RegionFileSizeException extends RuntimeException {

        public RegionFileSizeException(String message) {
            super(message);
        }
    }
    // Paper end
}
