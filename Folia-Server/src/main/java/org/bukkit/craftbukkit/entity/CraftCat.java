package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.animal.CatVariant;
import org.bukkit.DyeColor;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Cat;

public class CraftCat extends CraftTameableAnimal implements Cat {

    public CraftCat(CraftServer server, net.minecraft.world.entity.animal.Cat entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.Cat getHandleRaw() {
        return (net.minecraft.world.entity.animal.Cat)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.Cat getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.Cat) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftCat";
    }

    @Override
    public Type getCatType() {
        return CraftType.minecraftToBukkit(this.getHandle().getVariant());
    }

    @Override
    public void setCatType(Type type) {
        Preconditions.checkArgument(type != null, "Cannot have null Type");

        this.getHandle().setVariant(CraftType.bukkitToMinecraft(type));
    }

    @Override
    public DyeColor getCollarColor() {
        return DyeColor.getByWoolData((byte) this.getHandle().getCollarColor().getId());
    }

    @Override
    public void setCollarColor(DyeColor color) {
        this.getHandle().setCollarColor(net.minecraft.world.item.DyeColor.byId(color.getWoolData()));
    }

    public static class CraftType {

        public static Type minecraftToBukkit(CatVariant minecraft) {
            Preconditions.checkArgument(minecraft != null);

            Registry<CatVariant> registry = CraftRegistry.getMinecraftRegistry(Registries.CAT_VARIANT);

            return Type.values()[registry.getId(minecraft)];
        }

        public static CatVariant bukkitToMinecraft(Type bukkit) {
            Preconditions.checkArgument(bukkit != null);

            return CraftRegistry.getMinecraftRegistry(Registries.CAT_VARIANT)
                    .byId(bukkit.ordinal());
        }
    }

    // Paper Start - More cat api
    @Override
    public void setLyingDown(boolean lyingDown) {
        this.getHandle().setLying(lyingDown);
    }

    @Override
    public boolean isLyingDown() {
        return this.getHandle().isLying();
    }

    @Override
    public void setHeadUp(boolean headUp) {
        this.getHandle().setRelaxStateOne(headUp);
    }

    @Override
    public boolean isHeadUp() {
        return this.getHandle().isRelaxStateOne();
    }
    // Paper End - More cat api
}
