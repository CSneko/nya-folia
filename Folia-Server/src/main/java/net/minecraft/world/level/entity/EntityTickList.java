package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

public class EntityTickList {
    private final io.papermc.paper.util.maplist.IteratorSafeOrderedReferenceSet<Entity> entities = new io.papermc.paper.util.maplist.IteratorSafeOrderedReferenceSet<>(true); // Paper - rewrite this, always keep this updated - why would we EVER tick an entity that's not ticking?

    private void ensureActiveIsNotIterated() {
        // Paper - replace with better logic, do not delay removals

    }

    public void add(Entity entity) {
        io.papermc.paper.util.TickThread.ensureTickThread("Asynchronous entity ticklist addition"); // Paper
        this.ensureActiveIsNotIterated();
        this.entities.add(entity); // Paper - replace with better logic, do not delay removals/additions
    }

    public void remove(Entity entity) {
        io.papermc.paper.util.TickThread.ensureTickThread("Asynchronous entity ticklist removal"); // Paper
        this.ensureActiveIsNotIterated();
        this.entities.remove(entity); // Paper - replace with better logic, do not delay removals/additions
    }

    public boolean contains(Entity entity) {
        return this.entities.contains(entity); // Paper - replace with better logic, do not delay removals/additions
    }

    public void forEach(Consumer<Entity> action) {
        io.papermc.paper.util.TickThread.ensureTickThread("Asynchronous entity ticklist iteration"); // Paper
        // Paper start - replace with better logic, do not delay removals/additions
        // To ensure nothing weird happens with dimension travelling, do not iterate over new entries...
        // (by dfl iterator() is configured to not iterate over new entries)
        io.papermc.paper.util.maplist.IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.entities.iterator();
        try {
            while (iterator.hasNext()) {
                action.accept(iterator.next());
            }
        } finally {
            iterator.finishedIterating();
        }
        // Paper end - replace with better logic, do not delay removals/additions
    }
}
