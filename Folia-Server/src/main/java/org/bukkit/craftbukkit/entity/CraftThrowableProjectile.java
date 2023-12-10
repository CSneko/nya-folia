package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.inventory.ItemStack;

public abstract class CraftThrowableProjectile extends CraftProjectile implements ThrowableProjectile {

    public CraftThrowableProjectile(CraftServer server, ThrowableItemProjectile entity) {
        super(server, entity);
    }

    @Override
    public ItemStack getItem() {
        if (this.getHandle().getItemRaw().isEmpty()) {
            return CraftItemStack.asBukkitCopy(new net.minecraft.world.item.ItemStack(this.getHandle().getDefaultItemPublic()));
        } else {
            return CraftItemStack.asBukkitCopy(this.getHandle().getItemRaw());
        }
    }

    @Override
    public void setItem(ItemStack item) {
        this.getHandle().setItem(CraftItemStack.asNMSCopy(item));
    }

    // Folia start - region threading
    @Override
    public ThrowableItemProjectile getHandleRaw() {
        return (ThrowableItemProjectile)this.entity;
    }
    // Folia end - region threading

    @Override
    public ThrowableItemProjectile getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (ThrowableItemProjectile) this.entity;
    }
}
