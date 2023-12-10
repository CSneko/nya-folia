package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.frog.Frog;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Entity;

public class CraftFrog extends CraftAnimals implements org.bukkit.entity.Frog {

    public CraftFrog(CraftServer server, Frog entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public Frog getHandleRaw() {
        return (Frog)this.entity;
    }
    // Folia end - region threading

    @Override
    public Frog getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (Frog) this.entity;
    }

    @Override
    public String toString() {
        return "CraftFrog";
    }

    @Override
    public Entity getTongueTarget() {
        return this.getHandle().getTongueTarget().map(net.minecraft.world.entity.Entity::getBukkitEntity).orElse(null);
    }

    @Override
    public void setTongueTarget(Entity target) {
        if (target == null) {
            this.getHandle().eraseTongueTarget();
        } else {
            this.getHandle().setTongueTarget(((CraftEntity) target).getHandle());
        }
    }

    @Override
    public Variant getVariant() {
        return CraftVariant.minecraftToBukkit(this.getHandle().getVariant());
    }

    @Override
    public void setVariant(Variant variant) {
        Preconditions.checkArgument(variant != null, "variant");

        this.getHandle().setVariant(CraftVariant.bukkitToMinecraft(variant));
    }

    public static class CraftVariant {

        public static Variant minecraftToBukkit(FrogVariant minecraft) {
            Preconditions.checkArgument(minecraft != null);

            net.minecraft.core.Registry<FrogVariant> registry = CraftRegistry.getMinecraftRegistry(Registries.FROG_VARIANT);
            Variant bukkit = Registry.FROG_VARIANT.get(CraftNamespacedKey.fromMinecraft(registry.getResourceKey(minecraft).orElseThrow().location()));

            Preconditions.checkArgument(bukkit != null);

            return bukkit;
        }

        public static FrogVariant bukkitToMinecraft(Variant bukkit) {
            Preconditions.checkArgument(bukkit != null);

            return CraftRegistry.getMinecraftRegistry(Registries.FROG_VARIANT)
                    .getOptional(CraftNamespacedKey.toMinecraft(bukkit.getKey())).orElseThrow();
        }
    }
}
