package net.minecraft.world.entity.projectile;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
// CraftBukkit end

public abstract class AbstractArrow extends Projectile {

    private static final double ARROW_BASE_DAMAGE = 2.0D;
    private static final EntityDataAccessor<Byte> ID_FLAGS = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PIERCE_LEVEL = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final int FLAG_CRIT = 1;
    private static final int FLAG_NOPHYSICS = 2;
    private static final int FLAG_CROSSBOW = 4;
    @Nullable
    private BlockState lastState;
    public boolean inGround;
    protected int inGroundTime;
    public AbstractArrow.Pickup pickup;
    public int shakeTime;
    public int life;
    protected double baseDamage; // Paper - private -> protected
    public int knockback;
    public SoundEvent soundEvent;
    @Nullable
    private IntOpenHashSet piercingIgnoreEntityIds;
    @Nullable
    private List<Entity> piercedAndKilledEntities;

    // Spigot Start
    @Override
    public void inactiveTick()
    {
        if ( this.inGround )
        {
            this.life += 1;
        }
        super.inactiveTick();
    }
    // Spigot End

    protected AbstractArrow(EntityType<? extends AbstractArrow> type, Level world) {
        super(type, world);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
        this.baseDamage = 2.0D;
        this.soundEvent = this.getDefaultHitGroundSoundEvent();
    }

    protected AbstractArrow(EntityType<? extends AbstractArrow> type, double x, double y, double z, Level world) {
        this(type, world);
        this.setPos(x, y, z);
    }

    protected AbstractArrow(EntityType<? extends AbstractArrow> type, LivingEntity owner, Level world) {
        this(type, owner.getX(), owner.getEyeY() - 0.10000000149011612D, owner.getZ(), world);
        this.setOwner(owner);
        if (owner instanceof Player) {
            this.pickup = AbstractArrow.Pickup.ALLOWED;
        }

    }

    public void setSoundEvent(SoundEvent sound) {
        this.soundEvent = sound;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 10.0D;

        if (Double.isNaN(d1)) {
            d1 = 1.0D;
        }

        d1 *= 64.0D * getViewScale();
        return distance < d1 * d1;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(AbstractArrow.ID_FLAGS, (byte) 0);
        this.entityData.define(AbstractArrow.PIERCE_LEVEL, (byte) 0);
    }

    @Override
    public void shoot(double x, double y, double z, float speed, float divergence) {
        super.shoot(x, y, z, speed, divergence);
        this.life = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        super.lerpMotion(x, y, z);
        this.life = 0;
    }

    @Override
    public void tick() {
        super.tick();
        // Folia start - region threading - make sure entities do not move into regions they do not own
        if (!io.papermc.paper.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)this.level(), this.position(), this.getDeltaMovement(), 1)) {
            return;
        }
        // Folia end - region threading - make sure entities do not move into regions they do not own
        boolean flag = this.isNoPhysics();
        Vec3 vec3d = this.getDeltaMovement();

        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double d0 = vec3d.horizontalDistance();

            this.setYRot((float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D));
            this.setXRot((float) (Mth.atan2(vec3d.y, d0) * 57.2957763671875D));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }

        BlockPos blockposition = this.blockPosition();
        BlockState iblockdata = this.level().getBlockState(blockposition);
        Vec3 vec3d1;

        if (!iblockdata.isAir() && !flag) {
            VoxelShape voxelshape = iblockdata.getCollisionShape(this.level(), blockposition);

            if (!voxelshape.isEmpty()) {
                vec3d1 = this.position();
                Iterator iterator = voxelshape.toAabbs().iterator();

                while (iterator.hasNext()) {
                    AABB axisalignedbb = (AABB) iterator.next();

                    if (axisalignedbb.move(blockposition).contains(vec3d1)) {
                        this.inGround = true;
                        break;
                    }
                }
            }
        }

        if (this.shakeTime > 0) {
            --this.shakeTime;
        }

        if (this.isInWaterOrRain() || iblockdata.is(Blocks.POWDER_SNOW)) {
            this.clearFire();
        }

        if (this.inGround && !flag) {
            if (this.lastState != iblockdata && this.shouldFall()) {
                this.startFalling();
            } else if (!this.level().isClientSide) {
                this.tickDespawn();
            }

            ++this.inGroundTime;
        } else {
            if (tickCount > 200) this.tickDespawn(); // Paper - tick despawnCounter regardless after 10 seconds
            this.inGroundTime = 0;
            Vec3 vec3d2 = this.position();

            vec3d1 = vec3d2.add(vec3d);
            Object object = this.level().clip(new ClipContext(vec3d2, vec3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

            if (((HitResult) object).getType() != HitResult.Type.MISS) {
                vec3d1 = ((HitResult) object).getLocation();
            }

            while (!this.isRemoved()) {
                EntityHitResult movingobjectpositionentity = this.findHitEntity(vec3d2, vec3d1);

                if (movingobjectpositionentity != null) {
                    object = movingobjectpositionentity;
                }

                if (object != null && ((HitResult) object).getType() == HitResult.Type.ENTITY) {
                    Entity entity = ((EntityHitResult) object).getEntity();
                    Entity entity1 = this.getOwner();

                    if (entity instanceof Player && entity1 instanceof Player && !((Player) entity1).canHarmPlayer((Player) entity)) {
                        object = null;
                        movingobjectpositionentity = null;
                    }
                }

                if (object != null && !flag) {
                    this.preOnHit((HitResult) object); // CraftBukkit - projectile hit event
                    this.hasImpulse = true;
                }

                if (movingobjectpositionentity == null || this.getPierceLevel() <= 0) {
                    break;
                }

                object = null;
            }

            vec3d = this.getDeltaMovement();
            double d1 = vec3d.x;
            double d2 = vec3d.y;
            double d3 = vec3d.z;

            if (this.isCritArrow()) {
                for (int i = 0; i < 4; ++i) {
                    this.level().addParticle(ParticleTypes.CRIT, this.getX() + d1 * (double) i / 4.0D, this.getY() + d2 * (double) i / 4.0D, this.getZ() + d3 * (double) i / 4.0D, -d1, -d2 + 0.2D, -d3);
                }
            }

            double d4 = this.getX() + d1;
            double d5 = this.getY() + d2;
            double d6 = this.getZ() + d3;
            double d7 = vec3d.horizontalDistance();

            if (flag) {
                this.setYRot((float) (Mth.atan2(-d1, -d3) * 57.2957763671875D));
            } else {
                this.setYRot((float) (Mth.atan2(d1, d3) * 57.2957763671875D));
            }

            this.setXRot((float) (Mth.atan2(d2, d7) * 57.2957763671875D));
            this.setXRot(lerpRotation(this.xRotO, this.getXRot()));
            this.setYRot(lerpRotation(this.yRotO, this.getYRot()));
            float f = 0.99F;
            float f1 = 0.05F;

            if (this.isInWater()) {
                for (int j = 0; j < 4; ++j) {
                    float f2 = 0.25F;

                    this.level().addParticle(ParticleTypes.BUBBLE, d4 - d1 * 0.25D, d5 - d2 * 0.25D, d6 - d3 * 0.25D, d1, d2, d3);
                }

                f = this.getWaterInertia();
            }

            this.setDeltaMovement(vec3d.scale((double) f));
            if (!this.isNoGravity() && !flag) {
                Vec3 vec3d3 = this.getDeltaMovement();

                this.setDeltaMovement(vec3d3.x, vec3d3.y - 0.05000000074505806D, vec3d3.z);
            }

            this.setPos(d4, d5, d6);
            this.checkInsideBlocks();
        }
    }

    // Paper start
    @Override
    public void preOnHit(HitResult hitResult) {
        super.preOnHit(hitResult);
        if (hitResult instanceof EntityHitResult entityHitResult && this.hitCancelled && this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }
            this.piercingIgnoreEntityIds.add(entityHitResult.getEntity().getId());
        }
    }
    // Paper end

    private boolean shouldFall() {
        return this.inGround && this.level().noCollision((new AABB(this.position(), this.position())).inflate(0.06D));
    }

    private void startFalling() {
        this.inGround = false;
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.multiply((double) (this.random.nextFloat() * 0.2F), (double) (this.random.nextFloat() * 0.2F), (double) (this.random.nextFloat() * 0.2F)));
        this.life = 0;
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        super.move(movementType, movement);
        if (movementType != MoverType.SELF && this.shouldFall()) {
            this.startFalling();
        }

    }

    protected void tickDespawn() {
        ++this.life;
        if (this.life >= (pickup == Pickup.CREATIVE_ONLY ? this.level().paperConfig().entities.spawning.creativeArrowDespawnRate.value() : (pickup == Pickup.DISALLOWED ? this.level().paperConfig().entities.spawning.nonPlayerArrowDespawnRate.value() : ((this instanceof ThrownTrident) ? this.level().spigotConfig.tridentDespawnRate : this.level().spigotConfig.arrowDespawnRate)))) { // Spigot // Paper - TODO: Extract this to init?
            this.discard();
        }

    }

    private void resetPiercedEntities() {
        if (this.piercedAndKilledEntities != null) {
            this.piercedAndKilledEntities.clear();
        }

        if (this.piercingIgnoreEntityIds != null) {
            this.piercingIgnoreEntityIds.clear();
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Entity entity = entityHitResult.getEntity();
        float f = (float) this.getDeltaMovement().length();
        int i = Mth.ceil(Mth.clamp((double) f * this.baseDamage, 0.0D, 2.147483647E9D));

        if (this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }

            if (this.piercedAndKilledEntities == null) {
                this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
            }

            if (this.piercingIgnoreEntityIds.size() >= this.getPierceLevel() + 1) {
                this.discard();
                return;
            }

            this.piercingIgnoreEntityIds.add(entity.getId());
        }

        if (this.isCritArrow()) {
            long j = (long) this.random.nextInt(i / 2 + 2);

            i = (int) Math.min(j + (long) i, 2147483647L);
        }

        Entity entity1 = this.getOwner();
        DamageSource damagesource;

        if (entity1 == null) {
            damagesource = this.damageSources().arrow(this, this);
        } else {
            damagesource = this.damageSources().arrow(this, entity1);
            if (entity1 instanceof LivingEntity) {
                ((LivingEntity) entity1).setLastHurtMob(entity);
            }
        }

        if (this.isCritArrow()) damagesource = damagesource.critical(); // Paper - add critical damage API
        boolean flag = entity.getType() == EntityType.ENDERMAN;
        int k = entity.getRemainingFireTicks();

        if (this.isOnFire() && !flag) {
            // CraftBukkit start
            EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), 5);
            org.bukkit.Bukkit.getPluginManager().callEvent(combustEvent);
            if (!combustEvent.isCancelled()) {
                entity.setSecondsOnFire(combustEvent.getDuration(), false);
            }
            // CraftBukkit end
        }

        if (entity.hurt(damagesource, (float) i)) {
            if (flag) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;

                if (!this.level().isClientSide && this.getPierceLevel() <= 0) {
                    entityliving.setArrowCount(entityliving.getArrowCount() + 1);
                }

                if (this.knockback > 0) {
                    double d0 = Math.max(0.0D, 1.0D - entityliving.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    Vec3 vec3d = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale((double) this.knockback * 0.6D * d0);

                    if (vec3d.lengthSqr() > 0.0D) {
                        entityliving.push(vec3d.x, 0.1D, vec3d.z, this); // Paper
                    }
                }

                if (!this.level().isClientSide && entity1 instanceof LivingEntity) {
                    EnchantmentHelper.doPostHurtEffects(entityliving, entity1);
                    EnchantmentHelper.doPostDamageEffects((LivingEntity) entity1, entityliving);
                }

                this.doPostHurtEffects(entityliving);
                if (entity1 != null && entityliving != entity1 && entityliving instanceof Player && entity1 instanceof ServerPlayer && !this.isSilent()) {
                    ((ServerPlayer) entity1).connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0.0F));
                }

                if (!entity.isAlive() && this.piercedAndKilledEntities != null) {
                    this.piercedAndKilledEntities.add(entityliving);
                }

                if (!this.level().isClientSide && entity1 instanceof ServerPlayer) {
                    ServerPlayer entityplayer = (ServerPlayer) entity1;

                    if (this.piercedAndKilledEntities != null && this.shotFromCrossbow()) {
                        CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(entityplayer, (Collection) this.piercedAndKilledEntities);
                    } else if (!entity.isAlive() && this.shotFromCrossbow()) {
                        CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(entityplayer, (Collection) Arrays.asList(entity));
                    }
                }
            }

            this.playSound(this.soundEvent, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
            if (this.getPierceLevel() <= 0) {
                this.discard();
            }
        } else {
            entity.setRemainingFireTicks(k);
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.1D));
            this.setYRot(this.getYRot() + 180.0F);
            this.yRotO += 180.0F;
            if (!this.level().isClientSide && this.getDeltaMovement().lengthSqr() < 1.0E-7D) {
                if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }

                this.discard();
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        this.lastState = this.level().getBlockState(blockHitResult.getBlockPos());
        super.onHitBlock(blockHitResult);
        Vec3 vec3d = blockHitResult.getLocation().subtract(this.getX(), this.getY(), this.getZ());

        this.setDeltaMovement(vec3d);
        Vec3 vec3d1 = vec3d.normalize().scale(0.05000000074505806D);

        this.setPosRaw(this.getX() - vec3d1.x, this.getY() - vec3d1.y, this.getZ() - vec3d1.z);
        this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.inGround = true;
        this.shakeTime = 7;
        this.setCritArrow(false);
        this.setPierceLevel((byte) 0);
        this.setSoundEvent(SoundEvents.ARROW_HIT);
        this.setShotFromCrossbow(false);
        this.resetPiercedEntities();
    }

    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ARROW_HIT;
    }

    protected final SoundEvent getHitGroundSoundEvent() {
        return this.soundEvent;
    }

    protected void doPostHurtEffects(LivingEntity target) {}

    @Nullable
    protected EntityHitResult findHitEntity(Vec3 currentPosition, Vec3 nextPosition) {
        return ProjectileUtil.getEntityHitResult(this.level(), this, currentPosition, nextPosition, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), this::canHitEntity);
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && (this.piercingIgnoreEntityIds == null || !this.piercingIgnoreEntityIds.contains(entity.getId()));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putShort("life", (short) this.life);
        if (this.lastState != null) {
            nbt.put("inBlockState", NbtUtils.writeBlockState(this.lastState));
        }

        nbt.putByte("shake", (byte) this.shakeTime);
        nbt.putBoolean("inGround", this.inGround);
        nbt.putByte("pickup", (byte) this.pickup.ordinal());
        nbt.putDouble("damage", this.baseDamage);
        nbt.putBoolean("crit", this.isCritArrow());
        nbt.putByte("PierceLevel", this.getPierceLevel());
        nbt.putString("SoundEvent", BuiltInRegistries.SOUND_EVENT.getKey(this.soundEvent).toString());
        nbt.putBoolean("ShotFromCrossbow", this.shotFromCrossbow());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.life = nbt.getShort("life");
        if (nbt.contains("inBlockState", 10)) {
            this.lastState = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), nbt.getCompound("inBlockState"));
        }

        this.shakeTime = nbt.getByte("shake") & 255;
        this.inGround = nbt.getBoolean("inGround");
        if (nbt.contains("damage", 99)) {
            this.baseDamage = nbt.getDouble("damage");
        }

        this.pickup = AbstractArrow.Pickup.byOrdinal(nbt.getByte("pickup"));
        this.setCritArrow(nbt.getBoolean("crit"));
        this.setPierceLevel(nbt.getByte("PierceLevel"));
        if (nbt.contains("SoundEvent", 8)) {
            this.soundEvent = (SoundEvent) BuiltInRegistries.SOUND_EVENT.getOptional(new ResourceLocation(nbt.getString("SoundEvent"))).orElse(this.getDefaultHitGroundSoundEvent());
        }

        this.setShotFromCrossbow(nbt.getBoolean("ShotFromCrossbow"));
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);
        if (entity instanceof Player) {
            this.pickup = ((Player) entity).getAbilities().instabuild ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
        }

    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide && (this.inGround || this.isNoPhysics()) && this.shakeTime <= 0) {
            // CraftBukkit start
            ItemStack itemstack = this.getPickupItem();
            if (this.pickup == Pickup.ALLOWED && !itemstack.isEmpty() && player.getInventory().canHold(itemstack) > 0) {
                ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemstack);
                PlayerPickupArrowEvent event = new PlayerPickupArrowEvent((org.bukkit.entity.Player) player.getBukkitEntity(), new org.bukkit.craftbukkit.entity.CraftItem(this.level().getCraftServer(), item), (org.bukkit.entity.AbstractArrow) this.getBukkitEntity());
                // event.setCancelled(!entityhuman.canPickUpLoot); TODO
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                itemstack = item.getItem();
            }

            if ((this.pickup == AbstractArrow.Pickup.ALLOWED && player.getInventory().add(itemstack)) || (this.pickup == AbstractArrow.Pickup.CREATIVE_ONLY && player.getAbilities().instabuild)) {
                // CraftBukkit end
                player.take(this, 1);
                this.discard();
            }

        }
    }

    protected boolean tryPickup(Player player) {
        switch (this.pickup) {
            case ALLOWED:
                return player.getInventory().add(this.getPickupItem());
            case CREATIVE_ONLY:
                return player.getAbilities().instabuild;
            default:
                return false;
        }
    }

    public abstract ItemStack getPickupItem();

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    public void setBaseDamage(double damage) {
        this.baseDamage = damage;
    }

    public double getBaseDamage() {
        return this.baseDamage;
    }

    public void setKnockback(int punch) {
        this.knockback = punch;
    }

    public int getKnockback() {
        return this.knockback;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 0.13F;
    }

    public void setCritArrow(boolean critical) {
        this.setFlag(1, critical);
    }

    public void setPierceLevel(byte level) {
        this.entityData.set(AbstractArrow.PIERCE_LEVEL, level);
    }

    private void setFlag(int index, boolean flag) {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        if (flag) {
            this.entityData.set(AbstractArrow.ID_FLAGS, (byte) (b0 | index));
        } else {
            this.entityData.set(AbstractArrow.ID_FLAGS, (byte) (b0 & ~index));
        }

    }

    public boolean isCritArrow() {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        return (b0 & 1) != 0;
    }

    public boolean shotFromCrossbow() {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        return (b0 & 4) != 0;
    }

    public byte getPierceLevel() {
        return (Byte) this.entityData.get(AbstractArrow.PIERCE_LEVEL);
    }

    public void setEnchantmentEffectsFromEntity(LivingEntity entity, float damageModifier) {
        int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER_ARROWS, entity);
        int j = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH_ARROWS, entity);

        this.setBaseDamage((double) (damageModifier * 2.0F) + this.random.triangle((double) this.level().getDifficulty().getId() * 0.11D, 0.57425D));
        if (i > 0) {
            this.setBaseDamage(this.getBaseDamage() + (double) i * 0.5D + 0.5D);
        }

        if (j > 0) {
            this.setKnockback(j);
        }

        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAMING_ARROWS, entity) > 0) {
            this.setSecondsOnFire(100);
        }

    }

    protected float getWaterInertia() {
        return 0.6F;
    }

    public void setNoPhysics(boolean noClip) {
        this.noPhysics = noClip;
        this.setFlag(2, noClip);
    }

    public boolean isNoPhysics() {
        return !this.level().isClientSide ? this.noPhysics : ((Byte) this.entityData.get(AbstractArrow.ID_FLAGS) & 2) != 0;
    }

    public void setShotFromCrossbow(boolean shotFromCrossbow) {
        this.setFlag(4, shotFromCrossbow);
    }

    public static enum Pickup {

        DISALLOWED, ALLOWED, CREATIVE_ONLY;

        private Pickup() {}

        public static AbstractArrow.Pickup byOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal > values().length) {
                ordinal = 0;
            }

            return values()[ordinal];
        }
    }
}
