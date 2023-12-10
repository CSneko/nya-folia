package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class FireworkRocketEntity extends Projectile implements ItemSupplier {

    public static final EntityDataAccessor<ItemStack> DATA_ID_FIREWORKS_ITEM = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<OptionalInt> DATA_ATTACHED_TO_TARGET = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    public static final EntityDataAccessor<Boolean> DATA_SHOT_AT_ANGLE = SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.BOOLEAN);
    public int life;
    public int lifetime;
    @Nullable
    public LivingEntity attachedToEntity;
    public java.util.UUID spawningEntity; // Paper

    public FireworkRocketEntity(EntityType<? extends FireworkRocketEntity> type, Level world) {
        super(type, world);
    }

    public FireworkRocketEntity(Level world, double x, double y, double z, ItemStack stack) {
        super(EntityType.FIREWORK_ROCKET, world);
        this.life = 0;
        this.setPos(x, y, z);
        int i = 1;

        if (!stack.isEmpty() && stack.hasTag()) {
            this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, stack.copy());
            i += stack.getOrCreateTagElement("Fireworks").getByte("Flight");
        }

        this.setDeltaMovement(this.random.triangle(0.0D, 0.002297D), 0.05D, this.random.triangle(0.0D, 0.002297D));
        this.lifetime = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    public FireworkRocketEntity(Level world, @Nullable Entity entity, double x, double y, double z, ItemStack stack) {
        this(world, x, y, z, stack);
        this.setOwner(entity);
    }

    public FireworkRocketEntity(Level world, ItemStack stack, LivingEntity shooter) {
        this(world, shooter, shooter.getX(), shooter.getY(), shooter.getZ(), stack);
        this.entityData.set(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.of(shooter.getId()));
        this.attachedToEntity = shooter;
    }

    public FireworkRocketEntity(Level world, ItemStack stack, double x, double y, double z, boolean shotAtAngle) {
        this(world, x, y, z, stack);
        this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, shotAtAngle);
    }

    public FireworkRocketEntity(Level world, ItemStack stack, Entity entity, double x, double y, double z, boolean shotAtAngle) {
        this(world, stack, x, y, z, shotAtAngle);
        this.setOwner(entity);
    }

    // Spigot Start - copied from tick
    @Override
    public void inactiveTick() {
        this.life += 1;

        if (!this.level().isClientSide && this.life > this.lifetime) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }
        super.inactiveTick();
    }
    // Spigot End

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, ItemStack.EMPTY);
        this.entityData.define(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.empty());
        this.entityData.define(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, false);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D && !this.isAttachedToEntity();
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return super.shouldRender(cameraX, cameraY, cameraZ) && !this.isAttachedToEntity();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d;

        if (this.isAttachedToEntity()) {
            if (this.attachedToEntity == null) {
                ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).ifPresent((i) -> {
                    Entity entity = this.level().getEntity(i);

                    if (entity instanceof LivingEntity) {
                        this.attachedToEntity = (LivingEntity) entity;
                    }

                });
            }
            // Folia start - region threading
            if (this.attachedToEntity != null && !io.papermc.paper.util.TickThread.isTickThreadFor(this.attachedToEntity)) {
                this.attachedToEntity = null;
            }
            // Folia end - region threading

            if (this.attachedToEntity != null) {
                if (this.attachedToEntity.isFallFlying()) {
                    Vec3 vec3d1 = this.attachedToEntity.getLookAngle();
                    double d0 = 1.5D;
                    double d1 = 0.1D;
                    Vec3 vec3d2 = this.attachedToEntity.getDeltaMovement();

                    this.attachedToEntity.setDeltaMovement(vec3d2.add(vec3d1.x * 0.1D + (vec3d1.x * 1.5D - vec3d2.x) * 0.5D, vec3d1.y * 0.1D + (vec3d1.y * 1.5D - vec3d2.y) * 0.5D, vec3d1.z * 0.1D + (vec3d1.z * 1.5D - vec3d2.z) * 0.5D));
                    vec3d = this.attachedToEntity.getHandHoldingItemAngle(Items.FIREWORK_ROCKET);
                } else {
                    vec3d = Vec3.ZERO;
                }

                this.setPos(this.attachedToEntity.getX() + vec3d.x, this.attachedToEntity.getY() + vec3d.y, this.attachedToEntity.getZ() + vec3d.z);
                this.setDeltaMovement(this.attachedToEntity.getDeltaMovement());
            }
        } else {
            if (!this.isShotAtAngle()) {
                double d2 = this.horizontalCollision ? 1.0D : 1.15D;

                this.setDeltaMovement(this.getDeltaMovement().multiply(d2, 1.0D, d2).add(0.0D, 0.04D, 0.0D));
            }

            vec3d = this.getDeltaMovement();
            this.move(MoverType.SELF, vec3d);
            this.setDeltaMovement(vec3d);
        }

        HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        if (!this.noPhysics) {
            this.preOnHit(movingobjectposition); // CraftBukkit - projectile hit event
            this.hasImpulse = true;
        }

        this.updateRotation();
        if (this.life == 0 && !this.isSilent()) {
            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 1.0F);
        }

        ++this.life;
        if (this.level().isClientSide && this.life % 2 < 2) {
            this.level().addParticle(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.05D, -this.getDeltaMovement().y * 0.5D, this.random.nextGaussian() * 0.05D);
        }

        if (!this.level().isClientSide && this.life > this.lifetime) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }

    }

    private void explode() {
        this.level().broadcastEntityEvent(this, (byte) 17);
        this.gameEvent(GameEvent.EXPLODE, this.getOwner());
        this.dealExplosionDamage();
        this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (!this.level().isClientSide) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        BlockPos blockposition = new BlockPos(blockHitResult.getBlockPos());

        this.level().getBlockState(blockposition).entityInside(this.level(), blockposition, this);
        if (!this.level().isClientSide() && this.hasExplosion()) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode();
            }
            // CraftBukkit end
        }

        super.onHitBlock(blockHitResult);
    }

    private boolean hasExplosion() {
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
        CompoundTag nbttagcompound = itemstack.isEmpty() ? null : itemstack.getTagElement("Fireworks");
        ListTag nbttaglist = nbttagcompound != null ? nbttagcompound.getList("Explosions", 10) : null;

        return nbttaglist != null && !nbttaglist.isEmpty();
    }

    private void dealExplosionDamage() {
        float f = 0.0F;
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
        CompoundTag nbttagcompound = itemstack.isEmpty() ? null : itemstack.getTagElement("Fireworks");
        ListTag nbttaglist = nbttagcompound != null ? nbttagcompound.getList("Explosions", 10) : null;

        if (nbttaglist != null && !nbttaglist.isEmpty()) {
            f = 5.0F + (float) (nbttaglist.size() * 2);
        }

        if (f > 0.0F) {
            if (this.attachedToEntity != null) {
                CraftEventFactory.entityDamageRT.set(this); // CraftBukkit // Folia - region threading
                this.attachedToEntity.hurt(this.damageSources().fireworks(this, this.getOwner()), 5.0F + (float) (nbttaglist.size() * 2));
                CraftEventFactory.entityDamageRT.set(null); // CraftBukkit // Folia - region threading
            }

            double d0 = 5.0D;
            Vec3 vec3d = this.position();
            List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(5.0D));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                LivingEntity entityliving = (LivingEntity) iterator.next();

                if (entityliving != this.attachedToEntity && this.distanceToSqr((Entity) entityliving) <= 25.0D) {
                    boolean flag = false;

                    for (int i = 0; i < 2; ++i) {
                        Vec3 vec3d1 = new Vec3(entityliving.getX(), entityliving.getY(0.5D * (double) i), entityliving.getZ());
                        BlockHitResult movingobjectpositionblock = this.level().clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

                        if (movingobjectpositionblock.getType() == HitResult.Type.MISS) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        float f1 = f * (float) Math.sqrt((5.0D - (double) this.distanceTo(entityliving)) / 5.0D);

                        CraftEventFactory.entityDamageRT.set(this); // CraftBukkit // Folia - region threading
                        entityliving.hurt(this.damageSources().fireworks(this, this.getOwner()), f1);
                        CraftEventFactory.entityDamageRT.set(null); // CraftBukkit // Folia - region threading
                    }
                }
            }
        }

    }

    private boolean isAttachedToEntity() {
        return ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).isPresent();
    }

    public boolean isShotAtAngle() {
        return (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE);
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 17 && this.level().isClientSide) {
            if (!this.hasExplosion()) {
                for (int i = 0; i < this.random.nextInt(3) + 2; ++i) {
                    this.level().addParticle(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.05D, 0.005D, this.random.nextGaussian() * 0.05D);
                }
            } else {
                ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
                CompoundTag nbttagcompound = itemstack.isEmpty() ? null : itemstack.getTagElement("Fireworks");
                Vec3 vec3d = this.getDeltaMovement();

                this.level().createFireworks(this.getX(), this.getY(), this.getZ(), vec3d.x, vec3d.y, vec3d.z, nbttagcompound);
            }
        }

        super.handleEntityEvent(status);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Life", this.life);
        nbt.putInt("LifeTime", this.lifetime);
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);

        if (!itemstack.isEmpty()) {
            nbt.put("FireworksItem", itemstack.save(new CompoundTag()));
        }

        nbt.putBoolean("ShotAtAngle", (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE));
        // Paper start
        if (this.spawningEntity != null) {
            nbt.putUUID("SpawningEntity", this.spawningEntity);
        }
        // Paper end
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.life = nbt.getInt("Life");
        this.lifetime = nbt.getInt("LifeTime");
        ItemStack itemstack = ItemStack.of(nbt.getCompound("FireworksItem"));

        if (!itemstack.isEmpty()) {
            this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, itemstack);
        }

        if (nbt.contains("ShotAtAngle")) {
            this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, nbt.getBoolean("ShotAtAngle"));
        }
        // Paper start
        if (nbt.hasUUID("SpawningEntity")) {
            this.spawningEntity = nbt.getUUID("SpawningEntity");
        }
        // Paper end
    }

    @Override
    public ItemStack getItem() {
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);

        return itemstack.isEmpty() ? new ItemStack(Items.FIREWORK_ROCKET) : itemstack;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
