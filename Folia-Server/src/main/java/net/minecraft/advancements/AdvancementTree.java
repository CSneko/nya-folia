package net.minecraft.advancements;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AdvancementTree {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<ResourceLocation, AdvancementNode> nodes = new Object2ObjectOpenHashMap();
    private final Set<AdvancementNode> roots = new ObjectLinkedOpenHashSet();
    private final Set<AdvancementNode> tasks = new ObjectLinkedOpenHashSet();
    @Nullable
    private AdvancementTree.Listener listener;

    public AdvancementTree() {}

    private void remove(AdvancementNode advancement) {
        Iterator iterator = advancement.children().iterator();

        while (iterator.hasNext()) {
            AdvancementNode advancementnode1 = (AdvancementNode) iterator.next();

            this.remove(advancementnode1);
        }

        AdvancementTree.LOGGER.debug("Forgot about advancement {}", advancement.holder()); // Paper
        this.nodes.remove(advancement.holder().id());
        if (advancement.parent() == null) {
            this.roots.remove(advancement);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementRoot(advancement);
            }
        } else {
            this.tasks.remove(advancement);
            if (this.listener != null) {
                this.listener.onRemoveAdvancementTask(advancement);
            }
        }

    }

    public void remove(Set<ResourceLocation> advancements) {
        Iterator iterator = advancements.iterator();

        while (iterator.hasNext()) {
            ResourceLocation minecraftkey = (ResourceLocation) iterator.next();
            AdvancementNode advancementnode = (AdvancementNode) this.nodes.get(minecraftkey);

            if (advancementnode == null) {
                AdvancementTree.LOGGER.warn("Told to remove advancement {} but I don't know what that is", minecraftkey);
            } else {
                this.remove(advancementnode);
            }
        }

    }

    public void addAll(Collection<AdvancementHolder> advancements) {
        ArrayList<AdvancementHolder> arraylist = new ArrayList(advancements); // CraftBukkit - decompile error

        while (!arraylist.isEmpty()) {
            if (!arraylist.removeIf(this::tryInsert)) {
                AdvancementTree.LOGGER.error("Couldn't load advancements: {}", arraylist);
                break;
            }
        }

        // AdvancementTree.LOGGER.info("Loaded {} advancements", this.nodes.size()); // CraftBukkit - moved to AdvancementDataWorld#reload
    }

    private boolean tryInsert(AdvancementHolder advancement) {
        Optional<ResourceLocation> optional = advancement.value().parent();
        Map map = this.nodes;

        Objects.requireNonNull(this.nodes);
        AdvancementNode advancementnode = (AdvancementNode) optional.map(map::get).orElse((Object) null);

        if (advancementnode == null && optional.isPresent()) {
            return false;
        } else {
            AdvancementNode advancementnode1 = new AdvancementNode(advancement, advancementnode);

            if (advancementnode != null) {
                advancementnode.addChild(advancementnode1);
            }

            this.nodes.put(advancement.id(), advancementnode1);
            if (advancementnode == null) {
                this.roots.add(advancementnode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementRoot(advancementnode1);
                }
            } else {
                this.tasks.add(advancementnode1);
                if (this.listener != null) {
                    this.listener.onAddAdvancementTask(advancementnode1);
                }
            }

            return true;
        }
    }

    public void clear() {
        this.nodes.clear();
        this.roots.clear();
        this.tasks.clear();
        if (this.listener != null) {
            this.listener.onAdvancementsCleared();
        }

    }

    public Iterable<AdvancementNode> roots() {
        return this.roots;
    }

    public Collection<AdvancementNode> nodes() {
        return this.nodes.values();
    }

    @Nullable
    public AdvancementNode get(ResourceLocation id) {
        return (AdvancementNode) this.nodes.get(id);
    }

    @Nullable
    public AdvancementNode get(AdvancementHolder advancement) {
        return (AdvancementNode) this.nodes.get(advancement.id());
    }

    public void setListener(@Nullable AdvancementTree.Listener listener) {
        this.listener = listener;
        if (listener != null) {
            Iterator iterator = this.roots.iterator();

            AdvancementNode advancementnode;

            while (iterator.hasNext()) {
                advancementnode = (AdvancementNode) iterator.next();
                listener.onAddAdvancementRoot(advancementnode);
            }

            iterator = this.tasks.iterator();

            while (iterator.hasNext()) {
                advancementnode = (AdvancementNode) iterator.next();
                listener.onAddAdvancementTask(advancementnode);
            }
        }

    }

    public interface Listener {

        void onAddAdvancementRoot(AdvancementNode root);

        void onRemoveAdvancementRoot(AdvancementNode root);

        void onAddAdvancementTask(AdvancementNode dependent);

        void onRemoveAdvancementTask(AdvancementNode dependent);

        void onAdvancementsCleared();
    }
}
