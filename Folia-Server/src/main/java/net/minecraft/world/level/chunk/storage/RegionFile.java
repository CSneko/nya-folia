// mc-dev import
package net.minecraft.world.level.chunk.storage;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.InflaterInputStream; // Paper
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag; // Paper
import net.minecraft.nbt.NbtIo; // Paper
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class RegionFile implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTOR_BYTES = 4096;
    @VisibleForTesting
    protected static final int SECTOR_INTS = 1024;
    private static final int CHUNK_HEADER_SIZE = 5;
    private static final int HEADER_OFFSET = 0;
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);
    private static final String EXTERNAL_FILE_EXTENSION = ".mcc";
    private static final int EXTERNAL_STREAM_FLAG = 128;
    private static final int EXTERNAL_CHUNK_THRESHOLD = 256;
    private static final int CHUNK_NOT_PRESENT = 0;
    private final FileChannel file;
    private final Path externalFileDir;
    final RegionFileVersion version;
    private final ByteBuffer header;
    private final IntBuffer offsets;
    private final IntBuffer timestamps;
    @VisibleForTesting
    protected final RegionBitmap usedSectors;
    public final java.util.concurrent.locks.ReentrantLock fileLock = new java.util.concurrent.locks.ReentrantLock(); // Paper
    public final Path regionFile; // Paper

    // Paper start - try to recover from RegionFile header corruption
    private static long roundToSectors(long bytes) {
        long sectors = bytes >>> 12; // 4096 = 2^12
        long remainingBytes = bytes & 4095;
        long sign = -remainingBytes; // sign is 1 if nonzero
        return sectors + (sign >>> 63);
    }

    private static final CompoundTag OVERSIZED_COMPOUND = new CompoundTag();

    private CompoundTag attemptRead(long sector, int chunkDataLength, long fileLength) throws IOException {
        try {
            if (chunkDataLength < 0) {
                return null;
            }

            long offset = sector * 4096L + 4L; // offset for chunk data

            if ((offset + chunkDataLength) > fileLength) {
                return null;
            }

            ByteBuffer chunkData = ByteBuffer.allocate(chunkDataLength);
            if (chunkDataLength != this.file.read(chunkData, offset)) {
                return null;
            }

            ((java.nio.Buffer)chunkData).flip();

            byte compressionType = chunkData.get();
            if (compressionType < 0) { // compressionType & 128 != 0
                // oversized chunk
                return OVERSIZED_COMPOUND;
            }

            RegionFileVersion compression = RegionFileVersion.fromId(compressionType);
            if (compression == null) {
                return null;
            }

            InputStream input = compression.wrap(new ByteArrayInputStream(chunkData.array(), chunkData.position(), chunkDataLength - chunkData.position()));

            return NbtIo.read(new DataInputStream(input));
        } catch (Exception ex) {
            return null;
        }
    }

    private int getLength(long sector) throws IOException {
        ByteBuffer length = ByteBuffer.allocate(4);
        if (4 != this.file.read(length, sector * 4096L)) {
            return -1;
        }

        return length.getInt(0);
    }

    private void backupRegionFile() {
        Path backup = this.regionFile.getParent().resolve(this.regionFile.getFileName() + "." + new java.util.Random().nextLong() + ".backup");
        this.backupRegionFile(backup);
    }

    private void backupRegionFile(Path to) {
        try {
            this.file.force(true);
            LOGGER.warn("Backing up regionfile \"" + this.regionFile.toAbsolutePath() + "\" to " + to.toAbsolutePath());
            java.nio.file.Files.copy(this.regionFile, to, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.warn("Backed up the regionfile to " + to.toAbsolutePath());
        } catch (IOException ex) {
            LOGGER.error("Failed to backup to " + to.toAbsolutePath(), ex);
        }
    }

    private static boolean inSameRegionfile(ChunkPos first, ChunkPos second) {
        return (first.x & ~31) == (second.x & ~31) && (first.z & ~31) == (second.z & ~31);
    }

    // note: only call for CHUNK regionfiles
    boolean recalculateHeader() throws IOException {
        if (!this.canRecalcHeader) {
            return false;
        }
        ChunkPos ourLowerLeftPosition = RegionFileStorage.getRegionFileCoordinates(this.regionFile);
        if (ourLowerLeftPosition == null) {
            LOGGER.error("Unable to get chunk location of regionfile " + this.regionFile.toAbsolutePath() + ", cannot recover header");
            return false;
        }
        synchronized (this) {
            LOGGER.warn("Corrupt regionfile header detected! Attempting to re-calculate header offsets for regionfile " + this.regionFile.toAbsolutePath(), new Throwable());

            // try to backup file so maybe it could be sent to us for further investigation

            this.backupRegionFile();
            CompoundTag[] compounds = new CompoundTag[32 * 32]; // only in the regionfile (i.e exclude mojang/aikar oversized data)
            int[] rawLengths = new int[32 * 32]; // length of chunk data including 4 byte length field, bytes
            int[] sectorOffsets = new int[32 * 32]; // in sectors
            boolean[] hasAikarOversized = new boolean[32 * 32];

            long fileLength = this.file.size();
            long totalSectors = roundToSectors(fileLength);

            // search the regionfile from start to finish for the most up-to-date chunk data

            for (long i = 2, maxSector = Math.min((long)(Integer.MAX_VALUE >>> 8), totalSectors); i < maxSector; ++i) { // first two sectors are header, skip
                int chunkDataLength = this.getLength(i);
                CompoundTag compound = this.attemptRead(i, chunkDataLength, fileLength);
                if (compound == null || compound == OVERSIZED_COMPOUND) {
                    continue;
                }

                ChunkPos chunkPos = ChunkSerializer.getChunkCoordinate(compound);
                if (!inSameRegionfile(ourLowerLeftPosition, chunkPos)) {
                    LOGGER.error("Ignoring absolute chunk " + chunkPos + " in regionfile as it is not contained in the bounds of the regionfile '" + this.regionFile.toAbsolutePath() + "'. It should be in regionfile (" + (chunkPos.x >> 5) + "," + (chunkPos.z >> 5) + ")");
                    continue;
                }
                int location = (chunkPos.x & 31) | ((chunkPos.z & 31) << 5);

                CompoundTag otherCompound = compounds[location];

                if (otherCompound != null && ChunkSerializer.getLastWorldSaveTime(otherCompound) > ChunkSerializer.getLastWorldSaveTime(compound)) {
                    continue; // don't overwrite newer data.
                }

                // aikar oversized?
                Path aikarOversizedFile = this.getOversizedFile(chunkPos.x, chunkPos.z);
                boolean isAikarOversized = false;
                if (Files.exists(aikarOversizedFile)) {
                    try {
                        CompoundTag aikarOversizedCompound = this.getOversizedData(chunkPos.x, chunkPos.z);
                        if (ChunkSerializer.getLastWorldSaveTime(compound) == ChunkSerializer.getLastWorldSaveTime(aikarOversizedCompound)) {
                            // best we got for an id. hope it's good enough
                            isAikarOversized = true;
                        }
                    } catch (Exception ex) {
                        LOGGER.error("Failed to read aikar oversized data for absolute chunk (" + chunkPos.x + "," + chunkPos.z + ") in regionfile " + this.regionFile.toAbsolutePath() + ", oversized data for this chunk will be lost", ex);
                        // fall through, if we can't read aikar oversized we can't risk corrupting chunk data
                    }
                }

                hasAikarOversized[location] = isAikarOversized;
                compounds[location] = compound;
                rawLengths[location] = chunkDataLength + 4;
                sectorOffsets[location] = (int)i;

                int chunkSectorLength = (int)roundToSectors(rawLengths[location]);
                i += chunkSectorLength;
                --i; // gets incremented next iteration
            }

            // forge style oversized data is already handled by the local search, and aikar data we just hope
            // we get it right as aikar data has no identifiers we could use to try and find its corresponding
            // local data compound

            java.nio.file.Path containingFolder = this.externalFileDir;
            Path[] regionFiles = Files.list(containingFolder).toArray(Path[]::new);
            boolean[] oversized = new boolean[32 * 32];
            RegionFileVersion[] oversizedCompressionTypes = new RegionFileVersion[32 * 32];

            if (regionFiles != null) {
                int lowerXBound = ourLowerLeftPosition.x; // inclusive
                int lowerZBound = ourLowerLeftPosition.z; // inclusive
                int upperXBound = lowerXBound + 32 - 1; // inclusive
                int upperZBound = lowerZBound + 32 - 1; // inclusive

                // read mojang oversized data
                for (Path regionFile : regionFiles) {
                    ChunkPos oversizedCoords = getOversizedChunkPair(regionFile);
                    if (oversizedCoords == null) {
                        continue;
                    }

                    if ((oversizedCoords.x < lowerXBound || oversizedCoords.x > upperXBound) || (oversizedCoords.z < lowerZBound || oversizedCoords.z > upperZBound)) {
                        continue; // not in our regionfile
                    }

                    // ensure oversized data is valid & is newer than data in the regionfile

                    int location = (oversizedCoords.x & 31) | ((oversizedCoords.z & 31) << 5);

                    byte[] chunkData;
                    try {
                        chunkData = Files.readAllBytes(regionFile);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to read oversized chunk data in file " + regionFile.toAbsolutePath() + ", data will be lost", ex);
                        continue;
                    }

                    CompoundTag compound = null;

                    // We do not know the compression type, as it's stored in the regionfile. So we need to try all of them
                    RegionFileVersion compression = null;
                    for (RegionFileVersion compressionType : RegionFileVersion.VERSIONS.values()) {
                        try {
                            DataInputStream in = new DataInputStream(compressionType.wrap(new ByteArrayInputStream(chunkData))); // typical java
                            compound = NbtIo.read((java.io.DataInput)in);
                            compression = compressionType;
                            break; // reaches here iff readNBT does not throw
                        } catch (Exception ex) {
                            continue;
                        }
                    }

                    if (compound == null) {
                        LOGGER.error("Failed to read oversized chunk data in file " + regionFile.toAbsolutePath() + ", it's corrupt. Its data will be lost");
                        continue;
                    }

                    if (!ChunkSerializer.getChunkCoordinate(compound).equals(oversizedCoords)) {
                        LOGGER.error("Can't use oversized chunk stored in " + regionFile.toAbsolutePath() + ", got absolute chunkpos: " + ChunkSerializer.getChunkCoordinate(compound) + ", expected " + oversizedCoords);
                        continue;
                    }

                    if (compounds[location] == null || ChunkSerializer.getLastWorldSaveTime(compound) > ChunkSerializer.getLastWorldSaveTime(compounds[location])) {
                        oversized[location] = true;
                        oversizedCompressionTypes[location] = compression;
                    }
                }
            }

            // now we need to calculate a new offset header

            int[] calculatedOffsets = new int[32 * 32];
            RegionBitmap newSectorAllocations = new RegionBitmap();
            newSectorAllocations.force(0, 2); // make space for header

            // allocate sectors for normal chunks

            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);

                    if (oversized[location]) {
                        continue;
                    }

                    int rawLength = rawLengths[location]; // bytes
                    int sectorOffset = sectorOffsets[location]; // sectors
                    int sectorLength = (int)roundToSectors(rawLength);

                    if (newSectorAllocations.tryAllocate(sectorOffset, sectorLength)) {
                        calculatedOffsets[location] = sectorOffset << 8 | (sectorLength > 255 ? 255 : sectorLength); // support forge style oversized
                    } else {
                        LOGGER.error("Failed to allocate space for local chunk (overlapping data??) at (" + chunkX + "," + chunkZ + ") in regionfile " + this.regionFile.toAbsolutePath() + ", chunk will be regenerated");
                    }
                }
            }

            // allocate sectors for oversized chunks

            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);

                    if (!oversized[location]) {
                        continue;
                    }

                    int sectorOffset = newSectorAllocations.allocate(1);
                    int sectorLength = 1;

                    try {
                        this.file.write(this.createExternalStub(oversizedCompressionTypes[location]), sectorOffset * 4096);
                        // only allocate in the new offsets if the write succeeds
                        calculatedOffsets[location] = sectorOffset << 8 | (sectorLength > 255 ? 255 : sectorLength); // support forge style oversized
                    } catch (IOException ex) {
                        newSectorAllocations.free(sectorOffset, sectorLength);
                        LOGGER.error("Failed to write new oversized chunk data holder, local chunk at (" + chunkX + "," + chunkZ + ") in regionfile " + this.regionFile.toAbsolutePath() + " will be regenerated");
                    }
                }
            }

            // rewrite aikar oversized data

            this.oversizedCount = 0;
            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);
                    int isAikarOversized = hasAikarOversized[location] ? 1 : 0;

                    this.oversizedCount += isAikarOversized;
                    this.oversized[location] = (byte)isAikarOversized;
                }
            }

            if (this.oversizedCount > 0) {
                try {
                    this.writeOversizedMeta();
                } catch (Exception ex) {
                    LOGGER.error("Failed to write aikar oversized chunk meta, all aikar style oversized chunk data will be lost for regionfile " + this.regionFile.toAbsolutePath(), ex);
                    Files.deleteIfExists(this.getOversizedMetaFile());
                }
            } else {
                Files.deleteIfExists(this.getOversizedMetaFile());
            }

            this.usedSectors.copyFrom(newSectorAllocations);

            // before we overwrite the old sectors, print a summary of the chunks that got changed.

            LOGGER.info("Starting summary of changes for regionfile " + this.regionFile.toAbsolutePath());

            for (int chunkX = 0; chunkX < 32; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 32; ++chunkZ) {
                    int location = chunkX | (chunkZ << 5);

                    int oldOffset = this.offsets.get(location);
                    int newOffset = calculatedOffsets[location];

                    if (oldOffset == newOffset) {
                        continue;
                    }

                    this.offsets.put(location, newOffset); // overwrite incorrect offset

                    if (oldOffset == 0) {
                        // found lost data
                        LOGGER.info("Found missing data for local chunk (" + chunkX + "," + chunkZ + ") in regionfile " + this.regionFile.toAbsolutePath());
                    } else if (newOffset == 0) {
                        LOGGER.warn("Data for local chunk (" + chunkX + "," + chunkZ + ") could not be recovered in regionfile " + this.regionFile.toAbsolutePath() + ", it will be regenerated");
                    } else {
                        LOGGER.info("Local chunk (" + chunkX + "," + chunkZ + ") changed to point to newer data or correct chunk in regionfile " + this.regionFile.toAbsolutePath());
                    }
                }
            }

            LOGGER.info("End of change summary for regionfile " + this.regionFile.toAbsolutePath());

            // simply destroy the timestamp header, it's not used

            for (int i = 0; i < 32 * 32; ++i) {
                this.timestamps.put(i, calculatedOffsets[i] != 0 ? (int)System.currentTimeMillis() : 0); // write a valid timestamp for valid chunks, I do not want to find out whatever dumb program actually checks this
            }

            // write new header
            try {
                this.flush();
                this.file.force(true); // try to ensure it goes through...
                LOGGER.info("Successfully wrote new header to disk for regionfile " + this.regionFile.toAbsolutePath());
            } catch (IOException ex) {
                LOGGER.error("Failed to write new header to disk for regionfile " + this.regionFile.toAbsolutePath(), ex);
            }
        }

        return true;
    }

    final boolean canRecalcHeader; // final forces compile fail on new constructor
    // Paper end

    // Paper start - Cache chunk status
    private final net.minecraft.world.level.chunk.ChunkStatus[] statuses = new net.minecraft.world.level.chunk.ChunkStatus[32 * 32];

    private boolean closed;

    // invoked on write/read
    public void setStatus(int x, int z, net.minecraft.world.level.chunk.ChunkStatus status) {
        if (this.closed) {
            // We've used an invalid region file.
            throw new IllegalStateException("RegionFile is closed");
        }
        this.statuses[getChunkLocation(x, z)] = status;
    }

    public net.minecraft.world.level.chunk.ChunkStatus getStatusIfCached(int x, int z) {
        if (this.closed) {
            // We've used an invalid region file.
            throw new IllegalStateException("RegionFile is closed");
        }
        final int location = getChunkLocation(x, z);
        return this.statuses[location];
    }
    // Paper end

    public RegionFile(Path file, Path directory, boolean dsync) throws IOException {
        this(file, directory, RegionFileVersion.getCompressionFormat(), dsync); // Paper - Configurable region compression format
    }
    // Paper start - add can recalc flag
    public RegionFile(Path file, Path directory, boolean dsync, boolean canRecalcHeader) throws IOException {
        this(file, directory, RegionFileVersion.getCompressionFormat(), dsync, canRecalcHeader); // Paper - Configurable region compression format
    }
    // Paper end - add can recalc flag

    public RegionFile(Path file, Path directory, RegionFileVersion outputChunkStreamVersion, boolean dsync) throws IOException {
        // Paper start - add can recalc flag
        this(file, directory, outputChunkStreamVersion, dsync, false);
    }
    public RegionFile(Path file, Path directory, RegionFileVersion outputChunkStreamVersion, boolean dsync, boolean canRecalcHeader) throws IOException {
        this.canRecalcHeader = canRecalcHeader;
        // Paper end - add can recalc flag
        this.header = ByteBuffer.allocateDirect(8192);
        this.regionFile = file; // Paper
        initOversizedState(); // Paper
        this.usedSectors = new RegionBitmap();
        this.version = outputChunkStreamVersion;
        if (!Files.isDirectory(directory, new LinkOption[0])) {
            throw new IllegalArgumentException("Expected directory, got " + directory.toAbsolutePath());
        } else {
            this.externalFileDir = directory;
            this.offsets = this.header.asIntBuffer();
            ((java.nio.Buffer) this.offsets).limit(1024); // CraftBukkit - decompile error
            ((java.nio.Buffer) this.header).position(4096); // CraftBukkit - decompile error
            this.timestamps = this.header.asIntBuffer();
            if (dsync) {
                this.file = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.file = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }

            this.usedSectors.force(0, 2);
            ((java.nio.Buffer) this.header).position(0); // CraftBukkit - decompile error
            int i = this.file.read(this.header, 0L);

            if (i != -1) {
                if (i != 8192) {
                    RegionFile.LOGGER.warn("Region file {} has truncated header: {}", file, i);
                }

                final long j = Files.size(file); final long regionFileSize = j; // Paper - recalculate header on header corruption

                boolean needsHeaderRecalc = false; // Paper - recalculate header on header corruption
                boolean hasBackedUp = false; // Paper - recalculate header on header corruption
                for (int k = 0; k < 1024; ++k) { final int headerLocation = k; // Paper - we expect this to be the header location
                    final int l = this.offsets.get(k);

                    if (l != 0) {
                        final int i1 = RegionFile.getSectorNumber(l); final int offset = i1; // Paper - we expect this to be offset in file in sectors
                        int j1 = RegionFile.getNumSectors(l); final int sectorLength; // Paper - diff on change, we expect this to be sector length of region - watch out for reassignments
                        // Spigot start
                        if (j1 == 255) {
                            // We're maxed out, so we need to read the proper length from the section
                            ByteBuffer realLen = ByteBuffer.allocate(4);
                            this.file.read(realLen, i1 * 4096);
                            j1 = (realLen.getInt(0) + 4) / 4096 + 1;
                        }
                        // Spigot end
                        sectorLength = j1; // Paper - diff on change, we expect this to be sector length of region

                        if (i1 < 2) {
                            RegionFile.LOGGER.warn("Region file {} has invalid sector at index: {}; sector {} overlaps with header", new Object[]{file, k, i1});
                            //this.offsets.put(k, 0); // Paper - we catch this, but need it in the header for the summary change
                        } else if (j1 == 0) {
                            RegionFile.LOGGER.warn("Region file {} has an invalid sector at index: {}; size has to be > 0", file, k);
                            //this.offsets.put(k, 0); // Paper - we catch this, but need it in the header for the summary change
                        } else if ((long) i1 * 4096L > j) {
                            RegionFile.LOGGER.warn("Region file {} has an invalid sector at index: {}; sector {} is out of bounds", new Object[]{file, k, i1});
                            //this.offsets.put(k, 0); // Paper - we catch this, but need it in the header for the summary change
                        } else {
                            //this.usedSectors.force(i1, j1); // Paper - move this down so we can check if it fails to allocate
                        }
                        // Paper start - recalculate header on header corruption
                        if (offset < 2 || sectorLength <= 0 || ((long)offset * 4096L) > regionFileSize) {
                            if (canRecalcHeader) {
                                LOGGER.error("Detected invalid header for regionfile " + this.regionFile.toAbsolutePath() + "! Recalculating header...");
                                needsHeaderRecalc = true;
                                break;
                            } else {
                                // location = chunkX | (chunkZ << 5);
                                LOGGER.error("Detected invalid header for regionfile " + this.regionFile.toAbsolutePath() +
                                        "! Cannot recalculate, removing local chunk (" + (headerLocation & 31) + "," + (headerLocation >>> 5) + ") from header");
                                if (!hasBackedUp) {
                                    hasBackedUp = true;
                                    this.backupRegionFile();
                                }
                                this.timestamps.put(headerLocation, 0); // be consistent, delete the timestamp too
                                this.offsets.put(headerLocation, 0); // delete the entry from header
                                continue;
                            }
                        }
                        boolean failedToAllocate = !this.usedSectors.tryAllocate(offset, sectorLength);
                        if (failedToAllocate) {
                            LOGGER.error("Overlapping allocation by local chunk (" + (headerLocation & 31) + "," + (headerLocation >>> 5) + ") in regionfile " + this.regionFile.toAbsolutePath());
                        }
                        if (failedToAllocate & !canRecalcHeader) {
                            // location = chunkX | (chunkZ << 5);
                            LOGGER.error("Detected invalid header for regionfile " + this.regionFile.toAbsolutePath() +
                                    "! Cannot recalculate, removing local chunk (" + (headerLocation & 31) + "," + (headerLocation >>> 5) + ") from header");
                            if (!hasBackedUp) {
                                hasBackedUp = true;
                                this.backupRegionFile();
                            }
                            this.timestamps.put(headerLocation, 0); // be consistent, delete the timestamp too
                            this.offsets.put(headerLocation, 0); // delete the entry from header
                            continue;
                        }
                        needsHeaderRecalc |= failedToAllocate;
                        // Paper end - recalculate header on header corruption
                    }
                }
                // Paper start - recalculate header on header corruption
                // we move the recalc here so comparison to old header is correct when logging to console
                if (needsHeaderRecalc) { // true if header gave us overlapping allocations or had other issues
                    LOGGER.error("Recalculating regionfile " + this.regionFile.toAbsolutePath() + ", header gave erroneous offsets & locations");
                    this.recalculateHeader();
                }
                // Paper end
            }

        }
    }

    private Path getExternalChunkPath(ChunkPos chunkPos) {
        String s = "c." + chunkPos.x + "." + chunkPos.z + ".mcc"; // Paper - diff on change

        return this.externalFileDir.resolve(s);
    }

    // Paper start
    private static ChunkPos getOversizedChunkPair(Path file) {
        String fileName = file.getFileName().toString();

        if (!fileName.startsWith("c.") || !fileName.endsWith(".mcc")) {
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    // Paper end

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException {
        int i = this.getOffset(pos);

        if (i == 0) {
            return null;
        } else {
            int j = RegionFile.getSectorNumber(i);
            int k = RegionFile.getNumSectors(i);
            // Spigot start
            if (k == 255) {
                ByteBuffer realLen = ByteBuffer.allocate(4);
                this.file.read(realLen, j * 4096);
                k = (realLen.getInt(0) + 4) / 4096 + 1;
            }
            // Spigot end
            int l = k * 4096;
            ByteBuffer bytebuffer = ByteBuffer.allocate(l);

            this.file.read(bytebuffer, (long) (j * 4096));
            ((java.nio.Buffer) bytebuffer).flip(); // CraftBukkit - decompile error
            if (bytebuffer.remaining() < 5) {
                RegionFile.LOGGER.error("Chunk {} header is truncated: expected {} but read {}", new Object[]{pos, l, bytebuffer.remaining()});
                // Paper start - recalculate header on regionfile corruption
                if (this.canRecalcHeader && this.recalculateHeader()) {
                    return this.getChunkDataInputStream(pos);
                }
                // Paper end - recalculate header on regionfile corruption
                return null;
            } else {
                int i1 = bytebuffer.getInt();
                byte b0 = bytebuffer.get();

                if (i1 == 0) {
                    RegionFile.LOGGER.warn("Chunk {} is allocated, but stream is missing", pos);
                    // Paper start - recalculate header on regionfile corruption
                    if (this.canRecalcHeader && this.recalculateHeader()) {
                        return this.getChunkDataInputStream(pos);
                    }
                    // Paper end - recalculate header on regionfile corruption
                    return null;
                } else {
                    int j1 = i1 - 1;

                    if (RegionFile.isExternalStreamChunk(b0)) {
                        if (j1 != 0) {
                            RegionFile.LOGGER.warn("Chunk has both internal and external streams");
                            // Paper start - recalculate header on regionfile corruption
                            if (this.canRecalcHeader && this.recalculateHeader()) {
                                return this.getChunkDataInputStream(pos);
                            }
                            // Paper end - recalculate header on regionfile corruption
                        }

                        // Paper start - recalculate header on regionfile corruption
                        final DataInputStream ret = this.createExternalChunkInputStream(pos, RegionFile.getExternalChunkVersion(b0));
                        if (ret == null && this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(pos);
                        }
                        return ret;
                        // Paper end - recalculate header on regionfile corruption
                    } else if (j1 > bytebuffer.remaining()) {
                        RegionFile.LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", new Object[]{pos, j1, bytebuffer.remaining()});
                        // Paper start - recalculate header on regionfile corruption
                        if (this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(pos);
                        }
                        // Paper end - recalculate header on regionfile corruption
                        return null;
                    } else if (j1 < 0) {
                        RegionFile.LOGGER.error("Declared size {} of chunk {} is negative", i1, pos);
                        // Paper start - recalculate header on regionfile corruption
                        if (this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(pos);
                        }
                        // Paper end - recalculate header on regionfile corruption
                        return null;
                    } else {
                        // Paper start - recalculate header on regionfile corruption
                        final DataInputStream ret = this.createChunkInputStream(pos, b0, RegionFile.createStream(bytebuffer, j1));
                        if (ret == null && this.canRecalcHeader && this.recalculateHeader()) {
                            return this.getChunkDataInputStream(pos);
                        }
                        return ret;
                        // Paper end - recalculate header on regionfile corruption
                    }
                }
            }
        }
    }

    private static int getTimestamp() {
        return (int) (Util.getEpochMillis() / 1000L);
    }

    private static boolean isExternalStreamChunk(byte flags) {
        return (flags & 128) != 0;
    }

    private static byte getExternalChunkVersion(byte flags) {
        return (byte) (flags & -129);
    }

    @Nullable
    private DataInputStream createChunkInputStream(ChunkPos pos, byte flags, InputStream stream) throws IOException {
        RegionFileVersion regionfilecompression = RegionFileVersion.fromId(flags);

        if (regionfilecompression == null) {
            RegionFile.LOGGER.error("Chunk {} has invalid chunk stream version {}", pos, flags);
            return null;
        } else {
            return new DataInputStream(regionfilecompression.wrap(stream));
        }
    }

    @Nullable
    private DataInputStream createExternalChunkInputStream(ChunkPos pos, byte flags) throws IOException {
        Path path = this.getExternalChunkPath(pos);

        if (!Files.isRegularFile(path, new LinkOption[0])) {
            RegionFile.LOGGER.error("External chunk path {} is not file", path);
            return null;
        } else {
            return this.createChunkInputStream(pos, flags, Files.newInputStream(path));
        }
    }

    private static ByteArrayInputStream createStream(ByteBuffer buffer, int length) {
        return new ByteArrayInputStream(buffer.array(), buffer.position(), length);
    }

    private int packSectorOffset(int offset, int size) {
        return offset << 8 | size;
    }

    private static int getNumSectors(int sectorData) {
        return sectorData & 255;
    }

    private static int getSectorNumber(int sectorData) {
        return sectorData >> 8 & 16777215;
    }

    private static int sizeToSectors(int byteCount) {
        return (byteCount + 4096 - 1) / 4096;
    }

    public synchronized boolean doesChunkExist(ChunkPos pos) { // Paper - synchronized
        int i = this.getOffset(pos);

        if (i == 0) {
            return false;
        } else {
            int j = RegionFile.getSectorNumber(i);
            int k = RegionFile.getNumSectors(i);
            ByteBuffer bytebuffer = ByteBuffer.allocate(5);

            try {
                this.file.read(bytebuffer, (long) (j * 4096));
                ((java.nio.Buffer) bytebuffer).flip(); // CraftBukkit - decompile error
                if (bytebuffer.remaining() != 5) {
                    return false;
                } else {
                    int l = bytebuffer.getInt();
                    byte b0 = bytebuffer.get();

                    if (RegionFile.isExternalStreamChunk(b0)) {
                        if (!RegionFileVersion.isValidVersion(RegionFile.getExternalChunkVersion(b0))) {
                            return false;
                        }

                        if (!Files.isRegularFile(this.getExternalChunkPath(pos), new LinkOption[0])) {
                            return false;
                        }
                    } else {
                        if (!RegionFileVersion.isValidVersion(b0)) {
                            return false;
                        }

                        if (l == 0) {
                            return false;
                        }

                        int i1 = l - 1;

                        if (i1 < 0 || i1 > 4096 * k) {
                            return false;
                        }
                    }

                    return true;
                }
            } catch (IOException ioexception) {
                com.destroystokyo.paper.util.SneakyThrow.sneaky(ioexception); // Paper - we want the upper try/catch to retry this
                return false;
            }
        }
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) throws IOException {
        return new DataOutputStream(this.version.wrap((OutputStream) (new RegionFile.ChunkBuffer(pos))));
    }

    public void flush() throws IOException {
        this.file.force(true);
    }

    public void clear(ChunkPos pos) throws IOException {
        int i = RegionFile.getOffsetIndex(pos);
        int j = this.offsets.get(i);

        if (j != 0) {
            this.offsets.put(i, 0);
            this.timestamps.put(i, RegionFile.getTimestamp());
            this.writeHeader();
            Files.deleteIfExists(this.getExternalChunkPath(pos));
            this.usedSectors.free(RegionFile.getSectorNumber(j), RegionFile.getNumSectors(j));
        }
    }

    protected synchronized void write(ChunkPos pos, ByteBuffer buf) throws IOException {
        int i = RegionFile.getOffsetIndex(pos);
        int j = this.offsets.get(i);
        int k = RegionFile.getSectorNumber(j);
        int l = RegionFile.getNumSectors(j);
        int i1 = buf.remaining();
        int j1 = RegionFile.sizeToSectors(i1);
        int k1;
        RegionFile.CommitOp regionfile_b;

        if (j1 >= 256) {
            Path path = this.getExternalChunkPath(pos);

            RegionFile.LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", new Object[]{pos, i1, path});
            j1 = 1;
            k1 = this.usedSectors.allocate(j1);
            regionfile_b = this.writeToExternalFile(path, buf);
            ByteBuffer bytebuffer1 = this.createExternalStub();

            this.file.write(bytebuffer1, (long) (k1 * 4096));
        } else {
            k1 = this.usedSectors.allocate(j1);
            regionfile_b = () -> {
                Files.deleteIfExists(this.getExternalChunkPath(pos));
            };
            this.file.write(buf, (long) (k1 * 4096));
        }

        this.offsets.put(i, this.packSectorOffset(k1, j1));
        this.timestamps.put(i, RegionFile.getTimestamp());
        this.writeHeader();
        regionfile_b.run();
        if (k != 0) {
            this.usedSectors.free(k, l);
        }

    }

    private ByteBuffer createExternalStub() {
        // Paper start - add version param
        return this.createExternalStub(this.version);
    }
    private ByteBuffer createExternalStub(RegionFileVersion version) {
        // Paper end - add version param
        ByteBuffer bytebuffer = ByteBuffer.allocate(5);

        bytebuffer.putInt(1);
        bytebuffer.put((byte) (version.getId() | 128)); // Paper - replace with version param
        ((java.nio.Buffer) bytebuffer).flip(); // CraftBukkit - decompile error
        return bytebuffer;
    }

    private RegionFile.CommitOp writeToExternalFile(Path path, ByteBuffer buf) throws IOException {
        Path path1 = Files.createTempFile(this.externalFileDir, "tmp", (String) null);
        FileChannel filechannel = FileChannel.open(path1, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        try {
            ((java.nio.Buffer) buf).position(5); // CraftBukkit - decompile error
            filechannel.write(buf);
        } catch (Throwable throwable) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(throwable); // Paper
            if (filechannel != null) {
                try {
                    filechannel.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (filechannel != null) {
            filechannel.close();
        }

        return () -> {
            Files.move(path1, path, StandardCopyOption.REPLACE_EXISTING);
        };
    }

    private void writeHeader() throws IOException {
        ((java.nio.Buffer) this.header).position(0); // CraftBukkit - decompile error
        this.file.write(this.header, 0L);
    }

    private int getOffset(ChunkPos pos) {
        return this.offsets.get(RegionFile.getOffsetIndex(pos));
    }

    public boolean hasChunk(ChunkPos pos) {
        return this.getOffset(pos) != 0;
    }

    private static int getChunkLocation(int x, int z) { return (x & 31) + (z & 31) * 32; } // Paper - OBFHELPER - sort of, mirror of logic below
    private static int getOffsetIndex(ChunkPos pos) {
        return pos.getRegionLocalX() + pos.getRegionLocalZ() * 32;
    }

    public void close() throws IOException {
        // Paper start - Prevent regionfiles from being closed during use
        this.fileLock.lock();
        synchronized (this) {
        try {
        // Paper end
        this.closed = true; // Paper
        try {
            this.padToFullSector();
        } finally {
            try {
                this.file.force(true);
            } finally {
                this.file.close();
            }
        }
        } finally { // Paper start - Prevent regionfiles from being closed during use
            this.fileLock.unlock();
        }
        } // Paper end

    }

    private void padToFullSector() throws IOException {
        int i = (int) this.file.size();
        int j = RegionFile.sizeToSectors(i) * 4096;

        if (i != j) {
            ByteBuffer bytebuffer = RegionFile.PADDING_BUFFER.duplicate();

            ((java.nio.Buffer) bytebuffer).position(0); // CraftBukkit - decompile error
            this.file.write(bytebuffer, (long) (j - 1));
        }

    }

    // Paper start
    private final byte[] oversized = new byte[1024];
    private int oversizedCount = 0;

    private synchronized void initOversizedState() throws IOException {
        Path metaFile = getOversizedMetaFile();
        if (Files.exists(metaFile)) {
            final byte[] read = java.nio.file.Files.readAllBytes(metaFile);
            System.arraycopy(read, 0, oversized, 0, oversized.length);
            for (byte temp : oversized) {
                oversizedCount += temp;
            }
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + (z & 31) * 32;
    }
    synchronized boolean isOversized(int x, int z) {
        return this.oversized[getChunkIndex(x, z)] == 1;
    }
    synchronized void setOversized(int x, int z, boolean oversized) throws IOException {
        final int offset = getChunkIndex(x, z);
        boolean previous = this.oversized[offset] == 1;
        this.oversized[offset] = (byte) (oversized ? 1 : 0);
        if (!previous && oversized) {
            oversizedCount++;
        } else if (!oversized && previous) {
            oversizedCount--;
        }
        if (previous && !oversized) {
            Path oversizedFile = getOversizedFile(x, z);
            if (Files.exists(oversizedFile)) {
                Files.delete(oversizedFile);
            }
        }
        if (oversizedCount > 0) {
            if (previous != oversized) {
                writeOversizedMeta();
            }
        } else if (previous) {
            Path oversizedMetaFile = getOversizedMetaFile();
            if (Files.exists(oversizedMetaFile)) {
                Files.delete(oversizedMetaFile);
            }
        }
    }

    private void writeOversizedMeta() throws IOException {
        java.nio.file.Files.write(getOversizedMetaFile(), oversized);
    }

    private Path getOversizedMetaFile() {
        return this.regionFile.getParent().resolve(this.regionFile.getFileName().toString().replaceAll("\\.mca$", "") + ".oversized.nbt");
    }

    private Path getOversizedFile(int x, int z) {
        return this.regionFile.getParent().resolve(this.regionFile.getFileName().toString().replaceAll("\\.mca$", "") + "_oversized_" + x + "_" + z + ".nbt");
    }

    synchronized CompoundTag getOversizedData(int x, int z) throws IOException {
        Path file = getOversizedFile(x, z);
        try (DataInputStream out = new DataInputStream(new java.io.BufferedInputStream(new InflaterInputStream(Files.newInputStream(file))))) {
            return NbtIo.read((java.io.DataInput) out);
        }

    }

    public static final int MAX_CHUNK_SIZE = 500 * 1024 * 1024; // Paper - don't write garbage data to disk if writing serialization fails

    // Paper end
    private class ChunkBuffer extends ByteArrayOutputStream {

        private final ChunkPos pos;

        public ChunkBuffer(ChunkPos chunkcoordintpair) {
            super(8096);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(RegionFile.this.version.getId());
            this.pos = chunkcoordintpair;
        }

        // Paper start - don't write garbage data to disk if writing serialization fails
        @Override
        public void write(final int b) {
            if (this.count > MAX_CHUNK_SIZE) {
                throw new RegionFileStorage.RegionFileSizeException("Region file too large: " + this.count);
            }
            super.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            if (this.count + len > MAX_CHUNK_SIZE) {
                throw new RegionFileStorage.RegionFileSizeException("Region file too large: " + (this.count + len));
            }
            super.write(b, off, len);
        }
        // Paper end

        public void close() throws IOException {
            ByteBuffer bytebuffer = ByteBuffer.wrap(this.buf, 0, this.count);

            bytebuffer.putInt(0, this.count - 5 + 1);
            RegionFile.this.write(this.pos, bytebuffer);
        }
    }

    private interface CommitOp {

        void run() throws IOException;
    }
}
