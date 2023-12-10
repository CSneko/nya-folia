package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.event.entity.EntityCombustByEntityEvent; // CraftBukkit

public class SmallFireball extends Fireball {

    public SmallFireball(EntityType<? extends SmallFireball> type, Level world) {
        super(type, world);
    }

    public SmallFireball(Level world, LivingEntity owner, double velocityX, double velocityY, double velocityZ) {
        super(EntityType.SMALL_FIREBALL, owner, velocityX, velocityY, velocityZ, world);
        // CraftBukkit start
        if (owner != null && owner instanceof Mob) { // Folia - region threading
            this.isIncendiary = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        }
        // CraftBukkit end
    }

    public SmallFireball(Level world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(EntityType.SMALL_FIREBALL, x, y, z, velocityX, velocityY, velocityZ, world);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (!this.level().isClientSide) {
            Entity entity = entityHitResult.getEntity();
            Entity entity1 = this.getOwner();
            int i = entity.getRemainingFireTicks();

            // CraftBukkit start - Entity damage by entity event + combust event
            EntityCombustByEntityEvent event = new EntityCombustByEntityEvent((org.bukkit.entity.Projectile) this.getBukkitEntity(), entity.getBukkitEntity(), 5);
            entity.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                entity.setSecondsOnFire(event.getDuration(), false);
            }
            // CraftBukkit end
            if (!entity.hurt(this.damageSources().fireball(this, entity1), 5.0F)) {
                entity.setRemainingFireTicks(i);
            } else if (entity1 instanceof LivingEntity) {
                this.doEnchantDamageEffects((LivingEntity) entity1, entity);
            }

        }
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            Entity entity = this.getOwner();

            if (this.isIncendiary) { // CraftBukkit
                BlockPos blockposition = blockHitResult.getBlockPos().relative(blockHitResult.getDirection());

                if (this.level().isEmptyBlock(blockposition) && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition, this).isCancelled()) { // CraftBukkit
                    this.level().setBlockAndUpdate(blockposition, BaseFireBlock.getState(this.level(), blockposition));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            this.discard();
        }

    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
