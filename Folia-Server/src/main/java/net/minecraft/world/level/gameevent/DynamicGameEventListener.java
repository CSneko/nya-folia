package net.minecraft.world.level.gameevent;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

public class DynamicGameEventListener<T extends GameEventListener> {
    private final T listener;
    @Nullable
    private SectionPos lastSection;

    public DynamicGameEventListener(T listener) {
        this.listener = listener;
    }

    public void add(ServerLevel world) {
        this.move(world);
    }

    public T getListener() {
        return this.listener;
    }

    public void remove(ServerLevel world) {
        ifChunkExists(world, this.lastSection, (dispatcher) -> {
            dispatcher.unregister(this.listener);
        });
    }

    public void move(ServerLevel world) {
        this.listener.getListenerSource().getPosition(world).map(SectionPos::of).ifPresent((sectionPos) -> {
            if (this.lastSection == null || !this.lastSection.equals(sectionPos)) {
                ifChunkExists(world, this.lastSection, (dispatcher) -> {
                    dispatcher.unregister(this.listener);
                });
                this.lastSection = sectionPos;
                ifChunkExists(world, this.lastSection, (dispatcher) -> {
                    dispatcher.register(this.listener);
                });
            }

        });
    }

    private static void ifChunkExists(LevelReader world, @Nullable SectionPos sectionPos, Consumer<GameEventListenerRegistry> dispatcherConsumer) {
        if (sectionPos != null) {
            ChunkAccess chunkAccess = world.getChunkIfLoadedImmediately(sectionPos.getX(), sectionPos.getZ()); // Paper - can cause sync loads while completing a chunk, resulting in deadlock
            if (chunkAccess != null) {
                dispatcherConsumer.accept(chunkAccess.getListenerRegistry(sectionPos.y()));
            }

        }
    }
}
