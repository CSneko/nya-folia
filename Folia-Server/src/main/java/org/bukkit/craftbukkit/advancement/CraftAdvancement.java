package org.bukkit.craftbukkit.advancement;

import java.util.Collection;
import java.util.Collections;
import net.minecraft.advancements.AdvancementHolder;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;

public class CraftAdvancement implements org.bukkit.advancement.Advancement {

    private final AdvancementHolder handle;

    public CraftAdvancement(AdvancementHolder handle) {
        this.handle = handle;
    }

    public AdvancementHolder getHandle() {
        return this.handle;
    }

    @Override
    public NamespacedKey getKey() {
        return CraftNamespacedKey.fromMinecraft(this.handle.id());
    }

    @Override
    public Collection<String> getCriteria() {
        return Collections.unmodifiableCollection(this.handle.value().criteria().keySet());
    }

    // Paper start
    @Override
    public io.papermc.paper.advancement.AdvancementDisplay getDisplay() {
        return this.handle.value().display().map(d -> d.paper).orElse(null);
    }

    @Deprecated
    @io.papermc.paper.annotation.DoNotUse
    public AdvancementDisplay getDisplay0() { // May be called by plugins via Commodore
        return this.handle.value().display().map(CraftAdvancementDisplay::new).orElse(null);
    }

    @Override
    public net.kyori.adventure.text.Component displayName() {
        return io.papermc.paper.adventure.PaperAdventure.asAdventure(net.minecraft.advancements.Advancement.name(this.handle));
    }

    @Override
    public org.bukkit.advancement.Advancement getParent() {
        return this.handle.value().parent()
            .map(net.minecraft.server.MinecraftServer.getServer().getAdvancements()::get)
            .map(AdvancementHolder::toBukkit)
            .orElse(null);
    }

    @Override
    public Collection<org.bukkit.advancement.Advancement> getChildren() {
        final com.google.common.collect.ImmutableList.Builder<org.bukkit.advancement.Advancement> children = com.google.common.collect.ImmutableList.<org.bukkit.advancement.Advancement>builder();
        final net.minecraft.advancements.AdvancementNode advancementNode = net.minecraft.server.MinecraftServer.getServer().getAdvancements().tree().get(this.handle);
        if (advancementNode != null) {
            for (final net.minecraft.advancements.AdvancementNode child : advancementNode.children()) {
                children.add(child.holder().toBukkit());
            }
        }
        return children.build();
    }

    @Override
    public org.bukkit.advancement.Advancement getRoot() {
        final net.minecraft.advancements.AdvancementNode advancementNode = net.minecraft.server.MinecraftServer.getServer().getAdvancements().tree().get(this.handle);
        return java.util.Objects.requireNonNull(advancementNode, "could not find internal advancement node for advancement " + this.handle.id()).root().holder().toBukkit();
    }
    // Paper end
}
