package net.minecraft.world.entity.projectile;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEggThrowEvent;
// CraftBukkit end

public class ThrownEgg extends ThrowableItemProjectile {

    public ThrownEgg(net.minecraft.world.entity.EntityType<? extends ThrownEgg> type, Level world) {
        super(type, world);
    }

    public ThrownEgg(Level world, LivingEntity owner) {
        super(net.minecraft.world.entity.EntityType.EGG, owner, world);
    }

    public ThrownEgg(Level world, double x, double y, double z) {
        super(net.minecraft.world.entity.EntityType.EGG, x, y, z, world);
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 3) {
            double d0 = 0.08D;

            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        entityHitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            // CraftBukkit start
            boolean hatching = this.random.nextInt(8) == 0;
            if (true) {
            // CraftBukkit end
                byte b0 = 1;

                if (this.random.nextInt(32) == 0) {
                    b0 = 4;
                }

                // CraftBukkit start
                EntityType hatchingType = EntityType.CHICKEN;

                Entity shooter = this.getOwner();
                if (!hatching) {
                    b0 = 0;
                }
                if (shooter instanceof ServerPlayer) {
                    PlayerEggThrowEvent event = new PlayerEggThrowEvent((Player) shooter.getBukkitEntity(), (org.bukkit.entity.Egg) this.getBukkitEntity(), hatching, b0, hatchingType);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    b0 = event.getNumHatches();
                    hatching = event.isHatching();
                    hatchingType = event.getHatchingType();
                    // If hatching is set to false, ensure child count is 0
                    if (!hatching) {
                        b0 = 0;
                    }
                }
                // CraftBukkit end
                // Paper start
                com.destroystokyo.paper.event.entity.ThrownEggHatchEvent event = new com.destroystokyo.paper.event.entity.ThrownEggHatchEvent((org.bukkit.entity.Egg) getBukkitEntity(), hatching, b0, hatchingType);
                event.callEvent();
                hatching = event.isHatching();
                b0 = hatching ? event.getNumHatches() : 0; // If hatching is set to false, ensure child count is 0
                hatchingType = event.getHatchingType();
                // Paper end

                for (int i = 0; i < b0; ++i) {
                    Entity entitychicken = this.level().getWorld().createEntity(new org.bukkit.Location(this.level().getWorld(), this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F), hatchingType.getEntityClass()); // CraftBukkit

                    if (entitychicken != null) {
                        // CraftBukkit start
                        if (entitychicken.getBukkitEntity() instanceof Ageable) {
                            ((Ageable) entitychicken.getBukkitEntity()).setBaby();
                        }
                        this.level().addFreshEntity(entitychicken, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG);
                        // CraftBukkit end
                    }
                }
            }

            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }

    }

    @Override
    protected Item getDefaultItem() {
        return Items.EGG;
    }
}
