package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public abstract class AbstractHurtingProjectile extends Projectile {

    public double xPower;
    public double yPower;
    public double zPower;
    public float bukkitYield = 1; // CraftBukkit
    public boolean isIncendiary = true; // CraftBukkit

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, Level world) {
        super(type, world);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, double directionX, double directionY, double directionZ, Level world) {
        this(type, world);
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        // CraftBukkit start - Added setDirection method
        this.setDirection(directionX, directionY, directionZ);
    }

    public void setDirection(double d3, double d4, double d5) {
        // CraftBukkit end
        double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);

        if (d6 != 0.0D) {
            this.xPower = d3 / d6 * 0.1D;
            this.yPower = d4 / d6 * 0.1D;
            this.zPower = d5 / d6 * 0.1D;
        }

    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, LivingEntity owner, double directionX, double directionY, double directionZ, Level world) {
        this(type, owner.getX(), owner.getY(), owner.getZ(), directionX, directionY, directionZ, world);
        this.setOwner(owner);
        this.setRot(owner.getYRot(), owner.getXRot());
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1)) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (!this.level().isClientSide && (entity != null && entity.isRemoved() || !this.level().hasChunkAt(this.blockPosition()))) {
            this.discard();
        } else {
            super.tick();
            // Folia start - region threading - make sure entities do not move into regions they do not own
            if (!io.papermc.paper.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)this.level(), this.position(), this.getDeltaMovement(), 1)) {
                return;
            }
            // Folia end - region threading - make sure entities do not move into regions they do not own
            if (this.shouldBurn()) {
                this.setSecondsOnFire(1);
            }

            HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

            if (movingobjectposition.getType() != HitResult.Type.MISS) {
                this.preOnHit(movingobjectposition); // CraftBukkit - projectile hit event

                // CraftBukkit start - Fire ProjectileHitEvent
                if (this.isRemoved()) {
                    // CraftEventFactory.callProjectileHitEvent(this, movingobjectposition); // Paper - this is an undesired duplicate event
                }
                // CraftBukkit end
            }

            this.checkInsideBlocks();
            Vec3 vec3d = this.getDeltaMovement();
            double d0 = this.getX() + vec3d.x;
            double d1 = this.getY() + vec3d.y;
            double d2 = this.getZ() + vec3d.z;

            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            float f = this.getInertia();

            if (this.isInWater()) {
                for (int i = 0; i < 4; ++i) {
                    float f1 = 0.25F;

                    this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3d.x * 0.25D, d1 - vec3d.y * 0.25D, d2 - vec3d.z * 0.25D, vec3d.x, vec3d.y, vec3d.z);
                }

                f = 0.8F;
            }

            this.setDeltaMovement(vec3d.add(this.xPower, this.yPower, this.zPower).scale((double) f));
            this.level().addParticle(this.getTrailParticle(), d0, d1 + 0.5D, d2, 0.0D, 0.0D, 0.0D);
            this.setPos(d0, d1, d2);
        }
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    protected boolean shouldBurn() {
        return true;
    }

    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    protected float getInertia() {
        return 0.95F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.put("power", this.newDoubleList(new double[]{this.xPower, this.yPower, this.zPower}));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("power", 9)) {
            ListTag nbttaglist = nbt.getList("power", 6);

            if (nbttaglist.size() == 3) {
                this.xPower = nbttaglist.getDouble(0);
                this.yPower = nbttaglist.getDouble(1);
                this.zPower = nbttaglist.getDouble(2);
            }
        }

    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            this.markHurt();
            Entity entity = source.getEntity();

            if (entity != null) {
                if (!this.level().isClientSide) {
                    // CraftBukkit start
                    if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount, false)) {
                        return false;
                    }
                    // CraftBukkit end
                    Vec3 vec3d = entity.getLookAngle();

                    this.setDeltaMovement(vec3d);
                    this.xPower = vec3d.x * 0.1D;
                    this.yPower = vec3d.y * 0.1D;
                    this.zPower = vec3d.z * 0.1D;
                    this.setOwner(entity);
                }

                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        Entity entity = this.getOwner();
        int i = entity == null ? 0 : entity.getId();

        return new ClientboundAddEntityPacket(this.getId(), this.getUUID(), this.getX(), this.getY(), this.getZ(), this.getXRot(), this.getYRot(), this.getType(), i, new Vec3(this.xPower, this.yPower, this.zPower), 0.0D);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        double d0 = packet.getXa();
        double d1 = packet.getYa();
        double d2 = packet.getZa();
        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

        if (d3 != 0.0D) {
            this.xPower = d0 / d3 * 0.1D;
            this.yPower = d1 / d3 * 0.1D;
            this.zPower = d2 / d3 * 0.1D;
        }

    }
}
