package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
// CraftBukkit start
import java.util.concurrent.ExecutionException;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable {

    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    // Paper - nuke IO worker
    protected final DataFixer fixerUpper;
    @Nullable
    private volatile LegacyStructureDataHandler legacyStructureHandler;
    // Paper start - async chunk loading
    private final Object persistentDataLock = new Object(); // Paper
    public final RegionFileStorage regionFileCache;
    // Paper end - async chunk loading

    public ChunkStorage(Path directory, DataFixer dataFixer, boolean dsync) {
        this.fixerUpper = dataFixer;
        // Paper start - async chunk io
        // remove IO worker
        this.regionFileCache = new RegionFileStorage(directory, dsync, true); // Paper - nuke IOWorker // Paper
        // Paper end - async chunk io
    }

    public boolean isOldChunkAround(ChunkPos chunkPos, int checkRadius) {
        return true; // Paper - (for now, old unoptimised behavior) TODO implement later? the chunk status that blender uses SHOULD already have this radius loaded, no need to go back for it...
    }

    // CraftBukkit start
    private boolean check(ServerChunkCache cps, int x, int z) {
        if (true) return true; // Paper - this isn't even needed anymore, light is purged updating to 1.14+, why are we holding up the conversion process reading chunk data off disk - return true, we need to set light populated to true so the converter recognizes the chunk as being "full"
        ChunkPos pos = new ChunkPos(x, z);
        if (cps != null) {
            //com.google.common.base.Preconditions.checkState(org.bukkit.Bukkit.isPrimaryThread(), "primary thread"); // Paper - this function is now MT-Safe
            if (cps.getChunkAtIfCachedImmediately(x, z) != null) { // Paper - isLoaded is a ticket level check, not a chunk loaded check!
                return true;
            }
        }

        CompoundTag nbt;
        try {
            nbt = this.read(pos).get().orElse(null);
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        if (nbt != null) {
            CompoundTag level = nbt.getCompound("Level");
            if (level.getBoolean("TerrainPopulated")) {
                return true;
            }

            ChunkStatus status = ChunkStatus.byName(level.getString("Status"));
            if (status != null && status.isOrAfter(ChunkStatus.FEATURES)) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag upgradeChunkTag(ResourceKey<LevelStem> resourcekey, Supplier<DimensionDataStorage> supplier, CompoundTag nbttagcompound, Optional<ResourceKey<Codec<? extends ChunkGenerator>>> optional, ChunkPos pos, @Nullable LevelAccessor generatoraccess) {
        // CraftBukkit end
        nbttagcompound = nbttagcompound.copy(); // Paper - defensive copy, another thread might modify this
        int i = ChunkStorage.getVersion(nbttagcompound);

        // CraftBukkit start
        if (false && i < 1466) { // Paper - no longer needed, data converter system handles it now
            CompoundTag level = nbttagcompound.getCompound("Level");
            if (level.getBoolean("TerrainPopulated") && !level.getBoolean("LightPopulated")) {
                ServerChunkCache cps = (generatoraccess == null) ? null : ((ServerLevel) generatoraccess).getChunkSource();
                if (this.check(cps, pos.x - 1, pos.z) && this.check(cps, pos.x - 1, pos.z - 1) && this.check(cps, pos.x, pos.z - 1)) {
                    level.putBoolean("LightPopulated", true);
                }
            }
        }
        // CraftBukkit end

        if (i < 1493) {
            ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, nbttagcompound, i, 1493); // Paper - replace chunk converter
            if (nbttagcompound.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                synchronized (this.persistentDataLock) { // Paper - Async chunk loading
                LegacyStructureDataHandler persistentstructurelegacy = this.getLegacyStructureHandler(resourcekey, supplier);

                nbttagcompound = persistentstructurelegacy.updateFromLegacy(nbttagcompound);
                } // Paper - Async chunk loading
            }
        }

        // Spigot start - SPIGOT-6806: Quick and dirty way to prevent below zero generation in old chunks, by setting the status to heightmap instead of empty
        boolean stopBelowZero = false;
        boolean belowZeroGenerationInExistingChunks = (generatoraccess != null) ? ((ServerLevel) generatoraccess).spigotConfig.belowZeroGenerationInExistingChunks : org.spigotmc.SpigotConfig.belowZeroGenerationInExistingChunks;

        if (i <= 2730 && !belowZeroGenerationInExistingChunks) {
            stopBelowZero = "full".equals(nbttagcompound.getCompound("Level").getString("Status"));
        }
        // Spigot end

        ChunkStorage.injectDatafixingContext(nbttagcompound, resourcekey, optional);
        nbttagcompound = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.CHUNK, nbttagcompound, Math.max(1493, i), SharedConstants.getCurrentVersion().getDataVersion().getVersion()); // Paper - replace chunk converter
        if (i < SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            NbtUtils.addCurrentDataVersion(nbttagcompound);
        }

        // Spigot start
        if (stopBelowZero) {
            nbttagcompound.putString("Status", net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(ChunkStatus.SPAWN).toString());
        }
        // Spigot end

        nbttagcompound.remove("__context");
        return nbttagcompound;
    }

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<LevelStem> worldKey, Supplier<DimensionDataStorage> stateManagerGetter) { // CraftBukkit
        LegacyStructureDataHandler persistentstructurelegacy = this.legacyStructureHandler;

        if (persistentstructurelegacy == null) {
            synchronized (this.persistentDataLock) { // Paper - async chunk loading
                persistentstructurelegacy = this.legacyStructureHandler;
                if (persistentstructurelegacy == null) {
                    this.legacyStructureHandler = persistentstructurelegacy = LegacyStructureDataHandler.getLegacyStructureHandler(worldKey, (DimensionDataStorage) stateManagerGetter.get());
                }
            }
        }

        return persistentstructurelegacy;
    }

    public static void injectDatafixingContext(CompoundTag nbt, ResourceKey<LevelStem> worldKey, Optional<ResourceKey<Codec<? extends ChunkGenerator>>> generatorCodecKey) { // CraftBukkit
        CompoundTag nbttagcompound1 = new CompoundTag();

        nbttagcompound1.putString("dimension", worldKey.location().toString());
        generatorCodecKey.ifPresent((resourcekey1) -> {
            nbttagcompound1.putString("generator", resourcekey1.location().toString());
        });
        nbt.put("__context", nbttagcompound1);
    }

    public static int getVersion(CompoundTag nbt) {
        return NbtUtils.getDataVersion(nbt, -1);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        // Paper start - async chunk io
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.readSync(chunkPos)));
        } catch (Throwable thr) {
            return CompletableFuture.failedFuture(thr);
        }
    }
    @Nullable
    public CompoundTag readSync(ChunkPos chunkPos) throws IOException {
        return this.regionFileCache.read(chunkPos);
    }
    // Paper end - async chunk io

    // Paper start - async chunk io
    public void write(ChunkPos chunkPos, CompoundTag nbt) throws IOException {
        // Paper start
        if (nbt != null && !chunkPos.equals(ChunkSerializer.getChunkCoordinate(nbt))) {
            String world = (this instanceof net.minecraft.server.level.ChunkMap) ? ((net.minecraft.server.level.ChunkMap)this).level.getWorld().getName() : null;
            throw new IllegalArgumentException("Chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + chunkPos.toString()
                + " but compound says coordinate is " + ChunkSerializer.getChunkCoordinate(nbt).toString() + (world == null ? " for an unknown world" : (" for world: " + world)));
        }
        // Paper end
        this.regionFileCache.write(chunkPos, nbt);
        // Paper end - Async chunk loading
        if (this.legacyStructureHandler != null) {
            synchronized (this.persistentDataLock) { // Paper - Async chunk loading
            this.legacyStructureHandler.removeIndex(chunkPos.toLong());
            } // Paper - Async chunk loading
        }

    }

    public void flushWorker() {
        io.papermc.paper.chunk.system.io.RegionFileIOThread.flush(); // Paper - rewrite chunk system
    }

    public void close() throws IOException {
        this.regionFileCache.close(); // Paper - nuke IO worker
    }

    public ChunkScanAccess chunkScanner() {
        // Paper start - nuke IO worker
        return ((chunkPos, streamTagVisitor) -> {
            try {
                this.regionFileCache.scanChunk(chunkPos, streamTagVisitor);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        // Paper end
    }
}
