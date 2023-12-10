package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import org.slf4j.Logger;

public class EntityStorage implements EntityPersistentStorage<Entity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTITIES_TAG = "Entities";
    private static final String POSITION_TAG = "Position";
    public final ServerLevel level;
    // Paper - rewrite chunk system
    private final LongSet emptyChunks = new LongOpenHashSet();
    // Paper - rewrite chunk system
    protected final DataFixer fixerUpper;

    public EntityStorage(ServerLevel world, Path path, DataFixer dataFixer, boolean dsync, Executor executor) {
        this.level = world;
        this.fixerUpper = dataFixer;
        // Paper - rewrite chunk system
    }

    @Override
    public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system - copy out read logic into readEntities
    }

    // Paper start - rewrite chunk system
    public static List<Entity> readEntities(ServerLevel level, CompoundTag compoundTag) {
        ListTag listTag = compoundTag.getList("Entities", 10);
        List<Entity> list = EntityType.loadEntitiesRecursive(listTag, level).collect(ImmutableList.toImmutableList());
        return list;
    }
    // Paper end - rewrite chunk system

    public static ChunkPos readChunkPos(CompoundTag chunkNbt) { // Paper - public
        int[] is = chunkNbt.getIntArray("Position");
        return new ChunkPos(is[0], is[1]);
    }

    private static void writeChunkPos(CompoundTag chunkNbt, ChunkPos pos) {
        chunkNbt.put("Position", new IntArrayTag(new int[]{pos.x, pos.z}));
    }

    private static ChunkEntities<Entity> emptyChunk(ChunkPos pos) {
        return new ChunkEntities<>(pos, ImmutableList.of());
    }

    @Override
    public void storeEntities(ChunkEntities<Entity> dataList) {
        // Paper start - rewrite chunk system
        if (true) {
            throw new UnsupportedOperationException();
        }
        // Paper end - rewrite chunk system
        ChunkPos chunkPos = dataList.getPos();
        if (dataList.isEmpty()) {
            if (this.emptyChunks.add(chunkPos.toLong())) {
                // Paper - rewrite chunk system
            }

        } else {
            // Paper - move into saveEntityChunk0
            this.emptyChunks.remove(chunkPos.toLong());
        }
    }

    // Paper start - rewrite chunk system
    public static void copyEntities(final CompoundTag from, final CompoundTag into) {
        if (from == null) {
            return;
        }
        final ListTag entitiesFrom = from.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (entitiesFrom == null || entitiesFrom.isEmpty()) {
            return;
        }

        final ListTag entitiesInto = into.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
        into.put("Entities", entitiesInto); // this is in case into doesn't have any entities
        entitiesInto.addAll(0, entitiesFrom.copy()); // need to copy, this is coming from the save thread
    }

    public static CompoundTag saveEntityChunk(List<Entity> entities, ChunkPos chunkPos, ServerLevel level) {
        return saveEntityChunk0(entities, chunkPos, level, false);
    }
    private static CompoundTag saveEntityChunk0(List<Entity> entities, ChunkPos chunkPos, ServerLevel level, boolean force) {
        if (!force && entities.isEmpty()) {
            return null;
        }

        ListTag listTag = new ListTag();
        final java.util.Map<net.minecraft.world.entity.EntityType<?>, Integer> savedEntityCounts = new java.util.HashMap<>(); // Paper
        entities.forEach((entity) -> { // diff here: use entities parameter
            // Paper start
            final EntityType<?> entityType = entity.getType();
            final int saveLimit = level.paperConfig().chunks.entityPerChunkSaveLimit.getOrDefault(entityType, -1);
            if (saveLimit > -1) {
                if (savedEntityCounts.getOrDefault(entityType, 0) >= saveLimit) {
                    return;
                }
                savedEntityCounts.merge(entityType, 1, Integer::sum);
            }
            // Paper end
            CompoundTag compoundTag = new CompoundTag();
            if (entity.save(compoundTag)) {
                listTag.add(compoundTag);
            }

        });
        CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundTag.put("Entities", listTag);
        writeChunkPos(compoundTag, chunkPos);
        // Paper - remove worker usage

        return !force && listTag.isEmpty() ? null : compoundTag;
    }
    // Paper end - rewrite chunk system

    @Override
    public void flush(boolean sync) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public static CompoundTag upgradeChunkTag(CompoundTag chunkNbt) { // Paper - public and static
        int i = NbtUtils.getDataVersion(chunkNbt, -1);
        return ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.ENTITY_CHUNK, chunkNbt, i, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()); // Paper - route to new converter system
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }
}
