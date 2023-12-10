package net.minecraft.server.level.progress;

import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface ChunkProgressListener {
    void updateSpawnPos(ChunkPos spawnPos);

    void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status);

    void start();

    void stop();

    void setChunkRadius(int radius); // Paper - allow changing chunk radius
}
