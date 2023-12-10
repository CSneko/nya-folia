package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownTrident extends AbstractArrow {

    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    public ItemStack tridentItem;
    public boolean dealtDamage;
    public int clientSideReturnTridentTickCount;

    public ThrownTrident(EntityType<? extends ThrownTrident> type, Level world) {
        super(type, world);
        this.baseDamage = net.minecraft.world.item.TridentItem.BASE_DAMAGE; // Paper
        this.tridentItem = new ItemStack(Items.TRIDENT);
    }

    public ThrownTrident(Level world, LivingEntity owner, ItemStack stack) {
        super(EntityType.TRIDENT, owner, world);
        this.baseDamage = net.minecraft.world.item.TridentItem.BASE_DAMAGE; // Paper
        this.tridentItem = new ItemStack(Items.TRIDENT);
        this.tridentItem = stack.copy();
        this.entityData.set(ThrownTrident.ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(stack));
        this.entityData.set(ThrownTrident.ID_FOIL, stack.hasFoil());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ThrownTrident.ID_LOYALTY, (byte) 0);
        this.entityData.define(ThrownTrident.ID_FOIL, false);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity entity = this.getOwner();
        byte b0 = (Byte) this.entityData.get(ThrownTrident.ID_LOYALTY);

        if (b0 > 0 && (this.dealtDamage || this.isNoPhysics()) && entity != null) {
            if (!this.isAcceptibleReturnOwner()) {
                if (!this.level().isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }

                this.discard();
            } else {
                this.setNoPhysics(true);
                Vec3 vec3d = entity.getEyePosition().subtract(this.position());

                this.setPosRaw(this.getX(), this.getY() + vec3d.y * 0.015D * (double) b0, this.getZ());
                if (this.level().isClientSide) {
                    this.yOld = this.getY();
                }

                double d0 = 0.05D * (double) b0;

                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(vec3d.normalize().scale(d0)));
                if (this.clientSideReturnTridentTickCount == 0) {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }

                ++this.clientSideReturnTridentTickCount;
            }
        }

        super.tick();
    }

    private boolean isAcceptibleReturnOwner() {
        Entity entity = this.getOwner();

        return entity != null && entity.isAlive() ? !(entity instanceof ServerPlayer) || !entity.isSpectator() : false;
    }

    @Override
    public ItemStack getPickupItem() {
        return this.tridentItem.copy();
    }

    public boolean isFoil() {
        return (Boolean) this.entityData.get(ThrownTrident.ID_FOIL);
    }

    // Paper start
    public void setFoil(boolean foil) {
        this.entityData.set(ThrownTrident.ID_FOIL, foil);
    }

    public int getLoyalty() {
        return this.entityData.get(ThrownTrident.ID_LOYALTY);
    }

    public void setLoyalty(byte loyalty) {
        this.entityData.set(ThrownTrident.ID_LOYALTY, loyalty);
    }
    // Paper end

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 currentPosition, Vec3 nextPosition) {
        return this.dealtDamage ? null : super.findHitEntity(currentPosition, nextPosition);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        float f = (float) this.baseDamage; // Paper

        if (entity instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) entity;

            f += EnchantmentHelper.getDamageBonus(this.tridentItem, entityliving.getMobType());
        }

        Entity entity1 = this.getOwner();
        DamageSource damagesource = this.damageSources().trident(this, (Entity) (entity1 == null ? this : entity1));

        this.dealtDamage = true;
        SoundEvent soundeffect = SoundEvents.TRIDENT_HIT;

        if (entity.hurt(damagesource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving1 = (LivingEntity) entity;

                if (entity1 instanceof LivingEntity) {
                    EnchantmentHelper.doPostHurtEffects(entityliving1, entity1);
                    EnchantmentHelper.doPostDamageEffects((LivingEntity) entity1, entityliving1);
                }

                this.doPostHurtEffects(entityliving1);
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        float f1 = 1.0F;

        if (this.level() instanceof ServerLevel && this.level().isThundering() && this.isChanneling()) {
            BlockPos blockposition = entity.blockPosition();

            if (this.level().canSeeSky(blockposition)) {
                LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(this.level());

                if (entitylightning != null) {
                    entitylightning.moveTo(Vec3.atBottomCenterOf(blockposition));
                    entitylightning.setCause(entity1 instanceof ServerPlayer ? (ServerPlayer) entity1 : null);
                    ((ServerLevel) this.level()).strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.TRIDENT); // CraftBukkit
                    soundeffect = SoundEvents.TRIDENT_THUNDER;
                    f1 = 5.0F;
                }
            }
        }

        this.playSound(soundeffect, f1, 1.0F);
    }

    public boolean isChanneling() {
        return EnchantmentHelper.hasChanneling(this.tridentItem);
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(Player player) {
        if (this.ownedBy(player) || this.getOwner() == null) {
            super.playerTouch(player);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Trident", 10)) {
            this.tridentItem = ItemStack.of(nbt.getCompound("Trident"));
        }

        this.dealtDamage = nbt.getBoolean("DealtDamage");
        this.entityData.set(ThrownTrident.ID_LOYALTY, (byte) EnchantmentHelper.getLoyalty(this.tridentItem));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.put("Trident", this.tridentItem.save(new CompoundTag()));
        nbt.putBoolean("DealtDamage", this.dealtDamage);
    }

    @Override
    public void tickDespawn() {
        byte b0 = (Byte) this.entityData.get(ThrownTrident.ID_LOYALTY);

        if (this.pickup != AbstractArrow.Pickup.ALLOWED || b0 <= 0) {
            super.tickDespawn();
        }

    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return true;
    }
}
