package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class EntitySection<T extends EntityAccess> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ClassInstanceMultiMap<T> storage;
    private Visibility chunkStatus;

    public EntitySection(Class<T> entityClass, Visibility status) {
        this.chunkStatus = status;
        this.storage = new ClassInstanceMultiMap<>(entityClass);
    }

    public void add(T entity) {
        this.storage.add(entity);
    }

    public boolean remove(T entity) {
        return this.storage.remove(entity);
    }

    public AbortableIterationConsumer.Continuation getEntities(AABB box, AbortableIterationConsumer<T> consumer) {
        for(T entityAccess : this.storage) {
            if (entityAccess.getBoundingBox().intersects(box) && consumer.accept(entityAccess).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    public <U extends T> AbortableIterationConsumer.Continuation getEntities(EntityTypeTest<T, U> type, AABB box, AbortableIterationConsumer<? super U> consumer) {
        Collection<? extends T> collection = this.storage.find(type.getBaseClass());
        if (collection.isEmpty()) {
            return AbortableIterationConsumer.Continuation.CONTINUE;
        } else {
            for(T entityAccess : collection) {
                U entityAccess2 = (U)((EntityAccess)type.tryCast(entityAccess));
                if (entityAccess2 != null && entityAccess.getBoundingBox().intersects(box) && consumer.accept(entityAccess2).shouldAbort()) { // Paper - decompile fix
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        }
    }

    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    public Stream<T> getEntities() {
        return this.storage.stream();
    }

    public Visibility getStatus() {
        return this.chunkStatus;
    }

    public Visibility updateChunkStatus(Visibility status) {
        Visibility visibility = this.chunkStatus;
        this.chunkStatus = status;
        return visibility;
    }

    @VisibleForDebug
    public int size() {
        return this.storage.size();
    }
}
