package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.slf4j.Logger;

public class SectionStorage<R> extends RegionFileStorage implements AutoCloseable { // Paper - nuke IOWorker
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    // Paper - remove mojang I/O thread
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final Function<Runnable, Codec<R>> codec;
    private final Function<Runnable, R> factory;
    private final DataFixer fixerUpper;
    private final DataFixTypes type;
    public final RegistryAccess registryAccess; // Paper - rewrite chunk system
    protected final LevelHeightAccessor levelHeightAccessor;

    public SectionStorage(Path path, Function<Runnable, Codec<R>> codecFactory, Function<Runnable, R> factory, DataFixer dataFixer, DataFixTypes dataFixTypes, boolean dsync, RegistryAccess dynamicRegistryManager, LevelHeightAccessor world) {
        super(path, dsync); // Paper - remove mojang I/O thread
        this.codec = codecFactory;
        this.factory = factory;
        this.fixerUpper = dataFixer;
        this.type = dataFixTypes;
        this.registryAccess = dynamicRegistryManager;
        this.levelHeightAccessor = world;
        // Paper - remove mojang I/O thread
    }

    protected void tick(BooleanSupplier shouldKeepTicking) {
        while(this.hasWork() && shouldKeepTicking.getAsBoolean()) {
            ChunkPos chunkPos = SectionPos.of(this.dirty.firstLong()).chunk();
            this.writeColumn(chunkPos);
        }

    }

    public boolean hasWork() {
        return !this.dirty.isEmpty();
    }

    @Nullable
    public Optional<R> get(long pos) { // Paper - public
        return this.storage.get(pos);
    }

    public Optional<R> getOrLoad(long pos) { // Paper - public
        if (this.outsideStoredRange(pos)) {
            return Optional.empty();
        } else {
            Optional<R> optional = this.get(pos);
            if (optional != null) {
                return optional;
            } else {
                this.readColumn(SectionPos.of(pos).chunk());
                optional = this.get(pos);
                if (optional == null) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
                } else {
                    return optional;
                }
            }
        }
    }

    protected boolean outsideStoredRange(long pos) {
        int i = SectionPos.sectionToBlockCoord(SectionPos.y(pos));
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }

    protected R getOrCreate(long pos) {
        if (this.outsideStoredRange(pos)) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("sectionPos out of bounds"));
        } else {
            Optional<R> optional = this.getOrLoad(pos);
            if (optional.isPresent()) {
                return optional.get();
            } else {
                R object = this.factory.apply(() -> {
                    this.setDirty(pos);
                });
                this.storage.put(pos, Optional.of(object));
                return object;
            }
        }
    }

    private void readColumn(ChunkPos pos) {
        throw new IllegalStateException("Only chunk system can load in state, offending class:" + this.getClass().getName()); // Paper - rewrite chunk system
    }

    private CompletableFuture<Optional<CompoundTag>> tryRead(ChunkPos pos) {
        // Paper start - rewrite chunk system
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.read(pos)));
        } catch (Throwable thr) {
            return CompletableFuture.failedFuture(thr);
        }
        // Paper end - rewrite chunk system
    }

    private <T> void readColumn(ChunkPos pos, DynamicOps<T> ops, @Nullable T data) {
        if (true) throw new IllegalStateException("Only chunk system can load in state, offending class:" + this.getClass().getName()); // Paper - rewrite chunk system
        if (data == null) {
            for(int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); ++i) {
                this.storage.put(getKey(pos, i), Optional.empty());
            }
        } else {
            Dynamic<T> dynamic = new Dynamic<>(ops, data);
            int j = getVersion(dynamic);
            int k = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            boolean bl = j != k;
            // Paper start - route to new converter system
            Dynamic<T> dynamic2;
            if (this.type == net.minecraft.util.datafix.DataFixTypes.POI_CHUNK) {
                dynamic2 = new Dynamic<>(dynamic.getOps(), (T)ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.POI_CHUNK, (CompoundTag)dynamic.getValue(), j, k));
            } else {
                dynamic2 = this.type.update(this.fixerUpper, dynamic, j, k);
            }
            // Paper end - route to new converter system
            OptionalDynamic<T> optionalDynamic = dynamic2.get("Sections");

            for(int l = this.levelHeightAccessor.getMinSection(); l < this.levelHeightAccessor.getMaxSection(); ++l) {
                long m = getKey(pos, l);
                Optional<R> optional = optionalDynamic.get(Integer.toString(l)).result().flatMap((dynamicx) -> {
                    return this.codec.apply(() -> {
                        this.setDirty(m);
                    }).parse(dynamicx).resultOrPartial(LOGGER::error);
                });
                this.storage.put(m, optional);
                optional.ifPresent((sections) -> {
                    this.onSectionLoad(m);
                    if (bl) {
                        this.setDirty(m);
                    }

                });
            }
        }

    }

    private void writeColumn(ChunkPos pos) {
        RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, this.registryAccess);
        Dynamic<Tag> dynamic = this.writeColumn(pos, registryOps);
        Tag tag = dynamic.getValue();
        if (tag instanceof CompoundTag) {
            try { this.write(pos, (CompoundTag)tag); } catch (IOException ioexception) { SectionStorage.LOGGER.error("Error writing data to disk", ioexception); } // Paper - nuke IOWorker
        } else {
            LOGGER.error("Expected compound tag, got {}", (Object)tag);
        }

    }

    private <T> Dynamic<T> writeColumn(ChunkPos chunkPos, DynamicOps<T> ops) {
        Map<T, T> map = Maps.newHashMap();

        for(int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); ++i) {
            long l = getKey(chunkPos, i);
            this.dirty.remove(l);
            Optional<R> optional = this.storage.get(l);
            if (optional != null && !optional.isEmpty()) {
                DataResult<T> dataResult = this.codec.apply(() -> {
                    this.setDirty(l);
                }).encodeStart(ops, optional.get());
                String string = Integer.toString(i);
                dataResult.resultOrPartial(LOGGER::error).ifPresent((object) -> {
                    map.put(ops.createString(string), object);
                });
            }
        }

        return new Dynamic<>(ops, ops.createMap(ImmutableMap.of(ops.createString("Sections"), ops.createMap(map), ops.createString("DataVersion"), ops.createInt(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))));
    }

    private static long getKey(ChunkPos chunkPos, int y) {
        return SectionPos.asLong(chunkPos.x, y, chunkPos.z);
    }

    protected void onSectionLoad(long pos) {
    }

    protected void setDirty(long pos) {
        Optional<R> optional = this.storage.get(pos);
        if (optional != null && !optional.isEmpty()) {
            this.dirty.add(pos);
        } else {
            LOGGER.warn("No data for position: {}", (Object)SectionPos.of(pos));
        }
    }

    private static int getVersion(Dynamic<?> dynamic) {
        return dynamic.get("DataVersion").asInt(1945); // Paper - diff on change, constant used in ChunkLoadTask
    }

    public void flush(ChunkPos pos) {
        if (this.hasWork()) {
            for(int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); ++i) {
                long l = getKey(pos, i);
                if (this.dirty.contains(l)) {
                    this.writeColumn(pos);
                    return;
                }
            }
        }

    }

    @Override
    public void close() throws IOException {
        //this.worker.close(); // Paper - nuke I/O worker - don't call the worker
        super.close(); // Paper - nuke I/O worker - call super.close method which is responsible for closing used files.
    }

    // Paper - rewrite chunk system
}
