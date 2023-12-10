package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionUtils;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class CraftThrownPotion extends CraftThrowableProjectile implements ThrownPotion, org.bukkit.entity.SplashPotion, org.bukkit.entity.LingeringPotion { // Paper - implement other classes to avoid violating spawn method generic contracts
    public CraftThrownPotion(CraftServer server, net.minecraft.world.entity.projectile.ThrownPotion entity) {
        super(server, entity);
    }

    @Override
    public Collection<PotionEffect> getEffects() {
        ImmutableList.Builder<PotionEffect> builder = ImmutableList.builder();
        for (MobEffectInstance effect : PotionUtils.getMobEffects(this.getHandle().getItemRaw())) {
            builder.add(CraftPotionUtil.toBukkit(effect));
        }
        return builder.build();
    }

    @Override
    public ItemStack getItem() {
        return CraftItemStack.asBukkitCopy(this.getHandle().getItemRaw());
    }

    @Override
    public void setItem(ItemStack item) {
        Preconditions.checkArgument(item != null, "ItemStack cannot be null");
        // Preconditions.checkArgument(item.getType() == Material.LINGERING_POTION || item.getType() == Material.SPLASH_POTION, "ItemStack material must be Material.LINGERING_POTION or Material.SPLASH_POTION but was Material.%s", item.getType()); // Paper - Projectile API
        org.bukkit.inventory.meta.PotionMeta meta = (item.getType() == Material.LINGERING_POTION || item.getType() == Material.SPLASH_POTION) ? null : this.getPotionMeta(); // Paper - Projectile API

        this.getHandle().setItem(CraftItemStack.asNMSCopy(item));
        if (meta != null) this.setPotionMeta(meta); // Paper - Projectile API
    }

    // Paper start - Projectile API
    @Override
    public org.bukkit.inventory.meta.PotionMeta getPotionMeta() {
        return (org.bukkit.inventory.meta.PotionMeta) CraftItemStack.getItemMeta(this.getHandle().getItemRaw(), Material.SPLASH_POTION);
    }

    @Override
    public void setPotionMeta(org.bukkit.inventory.meta.PotionMeta meta) {
        net.minecraft.world.item.ItemStack item = this.getHandle().getItem();
        CraftItemStack.applyMetaToItem(item, meta);
        this.getHandle().setItem(item); // Reset item
    }

    @Override
    public void splash() {
        this.getHandle().splash(null);
    }
    // Paper end

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.projectile.ThrownPotion getHandleRaw() {
        return (net.minecraft.world.entity.projectile.ThrownPotion)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.projectile.ThrownPotion getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.projectile.ThrownPotion) this.entity;
    }
}
