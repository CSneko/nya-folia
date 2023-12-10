package net.minecraft.server.level.progress;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;

public class LoggerChunkProgressListener implements ChunkProgressListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private int maxCount;// Paper - remove final
    private int count;
    private long startTime;
    private long nextTickTime = Long.MAX_VALUE;

    public LoggerChunkProgressListener(int radius) {
        // Paper start - Allow changing radius later for configurable spawn patch
        this.setChunkRadius(radius); // Move to method
    }

    @Override
    public void setChunkRadius(int radius) {
        // Paper end
        int i = radius * 2 + 1;
        this.maxCount = i * i;
    }

    @Override
    public void updateSpawnPos(ChunkPos spawnPos) {
        this.nextTickTime = Util.getMillis();
        this.startTime = this.nextTickTime;
    }

    @Override
    public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
        if (status == ChunkStatus.FULL) {
            ++this.count;
        }

        int i = this.getProgress();
        if (Util.getMillis() > this.nextTickTime) {
            this.nextTickTime += 500L;
            LOGGER.info(Component.translatable("menu.preparingSpawn", Mth.clamp(i, 0, 100)).getString());
        }

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        LOGGER.info("Time elapsed: {} ms", (long)(Util.getMillis() - this.startTime));
        this.nextTickTime = Long.MAX_VALUE;
    }

    public int getProgress() {
        return Mth.floor((float)this.count * 100.0F / (float)this.maxCount);
    }
}
