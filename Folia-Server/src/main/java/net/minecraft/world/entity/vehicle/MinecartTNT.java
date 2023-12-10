package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
// CraftBukkit start
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class MinecartTNT extends AbstractMinecart {

    private static final byte EVENT_PRIME = 10;
    public int fuse = -1;

    public MinecartTNT(EntityType<? extends MinecartTNT> type, Level world) {
        super(type, world);
    }

    public MinecartTNT(Level world, double x, double y, double z) {
        super(EntityType.TNT_MINECART, world, x, y, z);
    }

    @Override
    public AbstractMinecart.Type getMinecartType() {
        return AbstractMinecart.Type.TNT;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.TNT.defaultBlockState();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuse > 0) {
            // Paper start - Configurable TNT entity height nerf
            if (this.level().paperConfig().fixes.tntEntityHeightNerf.test(v -> this.getY() > v)) {
                this.discard();
                return;
            }
            // Paper end
            --this.fuse;
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
        } else if (this.fuse == 0) {
            this.explode(this.getDeltaMovement().horizontalDistanceSqr());
        }

        if (this.horizontalCollision) {
            double d0 = this.getDeltaMovement().horizontalDistanceSqr();

            if (d0 >= 0.009999999776482582D) {
                this.explode(d0);
            }
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity entity = source.getDirectEntity();

        if (entity instanceof AbstractArrow) {
            AbstractArrow entityarrow = (AbstractArrow) entity;

            if (entityarrow.isOnFire()) {
                DamageSource damagesource1 = this.damageSources().explosion(this, source.getEntity());

                this.explode(damagesource1, entityarrow.getDeltaMovement().lengthSqr());
            }
        }

        return super.hurt(source, amount);
    }

    @Override
    public void destroy(DamageSource damageSource) {
        double d0 = this.getDeltaMovement().horizontalDistanceSqr();

        if (!damageSource.is(DamageTypeTags.IS_FIRE) && !damageSource.is(DamageTypeTags.IS_EXPLOSION) && d0 < 0.009999999776482582D) {
            super.destroy(damageSource);
        } else {
            if (this.fuse < 0) {
                this.primeFuse();
                this.fuse = this.random.nextInt(20) + this.random.nextInt(20);
            }

        }
    }

    @Override
    protected Item getDropItem() {
        return Items.TNT_MINECART;
    }

    public void explode(double power) {
        this.explode((DamageSource) null, power);
    }

    protected void explode(@Nullable DamageSource damageSource, double power) {
        if (!this.level().isClientSide) {
            double d1 = Math.sqrt(power);

            if (d1 > 5.0D) {
                d1 = 5.0D;
            }

            // CraftBukkit start
            ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), (float) (4.0D + this.random.nextDouble() * 1.5D * d1), false);
            this.level().getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.fuse = -1;
                return;
            }
            this.level().explode(this, damageSource, (ExplosionDamageCalculator) null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.TNT);
            // CraftBukkit end
            this.discard();
        }

    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (fallDistance >= 3.0F) {
            float f2 = fallDistance / 10.0F;

            this.explode((double) (f2 * f2));
        }

        return super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
    }

    @Override
    public void activateMinecart(int x, int y, int z, boolean powered) {
        if (powered && this.fuse < 0) {
            this.primeFuse();
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 10) {
            this.primeFuse();
        } else {
            super.handleEntityEvent(status);
        }

    }

    public void primeFuse() {
        this.fuse = 80;
        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte) 10);
            if (!this.isSilent()) {
                this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isPrimed() {
        return this.fuse > -1;
    }

    @Override
    public float getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState, float max) {
        return this.isPrimed() && (blockState.is(BlockTags.RAILS) || world.getBlockState(pos.above()).is(BlockTags.RAILS)) ? 0.0F : super.getBlockExplosionResistance(explosion, world, pos, blockState, fluidState, max);
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float explosionPower) {
        return this.isPrimed() && (state.is(BlockTags.RAILS) || world.getBlockState(pos.above()).is(BlockTags.RAILS)) ? false : super.shouldBlockExplode(explosion, world, pos, state, explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("TNTFuse", 99)) {
            this.fuse = nbt.getInt("TNTFuse");
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("TNTFuse", this.fuse);
    }
}
