package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerBullet extends Projectile {

    private static final double SPEED = 0.15D;
    @Nullable
    private Entity finalTarget;
    @Nullable
    public Direction currentMoveDirection;
    public int flightSteps;
    public double targetDeltaX;
    public double targetDeltaY;
    public double targetDeltaZ;
    @Nullable
    private UUID targetId;

    public ShulkerBullet(EntityType<? extends ShulkerBullet> type, Level world) {
        super(type, world);
        this.noPhysics = true;
    }

    public ShulkerBullet(Level world, LivingEntity owner, Entity target, Direction.Axis axis) {
        this(EntityType.SHULKER_BULLET, world);
        this.setOwner(owner);
        BlockPos blockposition = owner.blockPosition();
        double d0 = (double) blockposition.getX() + 0.5D;
        double d1 = (double) blockposition.getY() + 0.5D;
        double d2 = (double) blockposition.getZ() + 0.5D;

        this.moveTo(d0, d1, d2, this.getYRot(), this.getXRot());
        this.finalTarget = target;
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(axis);
        this.projectileSource = (org.bukkit.entity.LivingEntity) owner.getBukkitEntity(); // CraftBukkit
    }

    // CraftBukkit start
    public Entity getTarget() {
        return this.finalTarget;
    }

    public void setTarget(Entity e) {
        this.finalTarget = e;
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(Direction.Axis.X);
    }
    // CraftBukkit end

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.finalTarget != null) {
            nbt.putUUID("Target", this.finalTarget.getUUID());
        }

        if (this.currentMoveDirection != null) {
            nbt.putInt("Dir", this.currentMoveDirection.get3DDataValue());
        }

        nbt.putInt("Steps", this.flightSteps);
        nbt.putDouble("TXD", this.targetDeltaX);
        nbt.putDouble("TYD", this.targetDeltaY);
        nbt.putDouble("TZD", this.targetDeltaZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.flightSteps = nbt.getInt("Steps");
        this.targetDeltaX = nbt.getDouble("TXD");
        this.targetDeltaY = nbt.getDouble("TYD");
        this.targetDeltaZ = nbt.getDouble("TZD");
        if (nbt.contains("Dir", 99)) {
            this.currentMoveDirection = Direction.from3DDataValue(nbt.getInt("Dir"));
        }

        if (nbt.hasUUID("Target")) {
            this.targetId = nbt.getUUID("Target");
        }

    }

    @Override
    protected void defineSynchedData() {}

    @Nullable
    private Direction getMoveDirection() {
        return this.currentMoveDirection;
    }

    private void setMoveDirection(@Nullable Direction direction) {
        this.currentMoveDirection = direction;
    }

    private void selectNextMoveDirection(@Nullable Direction.Axis axis) {
        double d0 = 0.5D;
        BlockPos blockposition;

        if (this.finalTarget == null) {
            blockposition = this.blockPosition().below();
        } else {
            d0 = (double) this.finalTarget.getBbHeight() * 0.5D;
            blockposition = BlockPos.containing(this.finalTarget.getX(), this.finalTarget.getY() + d0, this.finalTarget.getZ());
        }

        double d1 = (double) blockposition.getX() + 0.5D;
        double d2 = (double) blockposition.getY() + d0;
        double d3 = (double) blockposition.getZ() + 0.5D;
        Direction enumdirection = null;

        if (!blockposition.closerToCenterThan(this.position(), 2.0D)) {
            BlockPos blockposition1 = this.blockPosition();
            List<Direction> list = Lists.newArrayList();

            if (axis != Direction.Axis.X) {
                if (blockposition1.getX() < blockposition.getX() && this.level().isEmptyBlock(blockposition1.east())) {
                    list.add(Direction.EAST);
                } else if (blockposition1.getX() > blockposition.getX() && this.level().isEmptyBlock(blockposition1.west())) {
                    list.add(Direction.WEST);
                }
            }

            if (axis != Direction.Axis.Y) {
                if (blockposition1.getY() < blockposition.getY() && this.level().isEmptyBlock(blockposition1.above())) {
                    list.add(Direction.UP);
                } else if (blockposition1.getY() > blockposition.getY() && this.level().isEmptyBlock(blockposition1.below())) {
                    list.add(Direction.DOWN);
                }
            }

            if (axis != Direction.Axis.Z) {
                if (blockposition1.getZ() < blockposition.getZ() && this.level().isEmptyBlock(blockposition1.south())) {
                    list.add(Direction.SOUTH);
                } else if (blockposition1.getZ() > blockposition.getZ() && this.level().isEmptyBlock(blockposition1.north())) {
                    list.add(Direction.NORTH);
                }
            }

            enumdirection = Direction.getRandom(this.random);
            if (list.isEmpty()) {
                for (int i = 5; !this.level().isEmptyBlock(blockposition1.relative(enumdirection)) && i > 0; --i) {
                    enumdirection = Direction.getRandom(this.random);
                }
            } else {
                enumdirection = (Direction) list.get(this.random.nextInt(list.size()));
            }

            d1 = this.getX() + (double) enumdirection.getStepX();
            d2 = this.getY() + (double) enumdirection.getStepY();
            d3 = this.getZ() + (double) enumdirection.getStepZ();
        }

        this.setMoveDirection(enumdirection);
        double d4 = d1 - this.getX();
        double d5 = d2 - this.getY();
        double d6 = d3 - this.getZ();
        double d7 = Math.sqrt(d4 * d4 + d5 * d5 + d6 * d6);

        if (d7 == 0.0D) {
            this.targetDeltaX = 0.0D;
            this.targetDeltaY = 0.0D;
            this.targetDeltaZ = 0.0D;
        } else {
            this.targetDeltaX = d4 / d7 * 0.15D;
            this.targetDeltaY = d5 / d7 * 0.15D;
            this.targetDeltaZ = d6 / d7 * 0.15D;
        }

        this.hasImpulse = true;
        this.flightSteps = 10 + this.random.nextInt(5) * 10;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
        }

    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d;

        if (!this.level().isClientSide) {
            if (this.finalTarget == null && this.targetId != null) {
                this.finalTarget = ((ServerLevel) this.level()).getEntity(this.targetId);
                if (this.finalTarget == null) {
                    this.targetId = null;
                }
            }

            if (this.finalTarget != null && this.finalTarget.isAlive() && (!(this.finalTarget instanceof Player) || !this.finalTarget.isSpectator())) {
                this.targetDeltaX = Mth.clamp(this.targetDeltaX * 1.025D, -1.0D, 1.0D);
                this.targetDeltaY = Mth.clamp(this.targetDeltaY * 1.025D, -1.0D, 1.0D);
                this.targetDeltaZ = Mth.clamp(this.targetDeltaZ * 1.025D, -1.0D, 1.0D);
                vec3d = this.getDeltaMovement();
                this.setDeltaMovement(vec3d.add((this.targetDeltaX - vec3d.x) * 0.2D, (this.targetDeltaY - vec3d.y) * 0.2D, (this.targetDeltaZ - vec3d.z) * 0.2D));
            } else if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

            if (movingobjectposition.getType() != HitResult.Type.MISS) {
                this.preOnHit(movingobjectposition); // CraftBukkit - projectile hit event
            }
        }

        this.checkInsideBlocks();
        vec3d = this.getDeltaMovement();
        this.setPos(this.getX() + vec3d.x, this.getY() + vec3d.y, this.getZ() + vec3d.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getX() - vec3d.x, this.getY() - vec3d.y + 0.15D, this.getZ() - vec3d.z, 0.0D, 0.0D, 0.0D);
        } else if (this.finalTarget != null && !this.finalTarget.isRemoved()) {
            if (this.flightSteps > 0) {
                --this.flightSteps;
                if (this.flightSteps == 0) {
                    this.selectNextMoveDirection(this.currentMoveDirection == null ? null : this.currentMoveDirection.getAxis());
                }
            }

            if (this.currentMoveDirection != null) {
                BlockPos blockposition = this.blockPosition();
                Direction.Axis enumdirection_enumaxis = this.currentMoveDirection.getAxis();

                if (this.level().loadedAndEntityCanStandOn(blockposition.relative(this.currentMoveDirection), this)) {
                    this.selectNextMoveDirection(enumdirection_enumaxis);
                } else {
                    BlockPos blockposition1 = this.finalTarget.blockPosition();

                    if (enumdirection_enumaxis == Direction.Axis.X && blockposition.getX() == blockposition1.getX() || enumdirection_enumaxis == Direction.Axis.Z && blockposition.getZ() == blockposition1.getZ() || enumdirection_enumaxis == Direction.Axis.Y && blockposition.getY() == blockposition1.getY()) {
                        this.selectNextMoveDirection(enumdirection_enumaxis);
                    }
                }
            }
        }

    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Entity entity = entityHitResult.getEntity();
        Entity entity1 = this.getOwner();
        LivingEntity entityliving = entity1 instanceof LivingEntity ? (LivingEntity) entity1 : null;
        boolean flag = entity.hurt(this.damageSources().mobProjectile(this, entityliving), 4.0F);

        if (flag) {
            this.doEnchantDamageEffects(entityliving, entity);
            if (entity instanceof LivingEntity) {
                LivingEntity entityliving1 = (LivingEntity) entity;

                entityliving1.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200), (Entity) MoreObjects.firstNonNull(entity1, this), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
        this.playSound(SoundEvents.SHULKER_BULLET_HIT, 1.0F, 1.0F);
    }

    private void destroy() {
        this.discard();
        this.level().gameEvent(GameEvent.ENTITY_DAMAGE, this.position(), GameEvent.Context.of((Entity) this));
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        this.destroy();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount, false)) {
            return false;
        }
        // CraftBukkit end
        if (!this.level().isClientSide) {
            this.playSound(SoundEvents.SHULKER_BULLET_HURT, 1.0F, 1.0F);
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 15, 0.2D, 0.2D, 0.2D, 0.0D);
            this.destroy();
        }

        return true;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        double d0 = packet.getXa();
        double d1 = packet.getYa();
        double d2 = packet.getZa();

        this.setDeltaMovement(d0, d1, d2);
    }
}
