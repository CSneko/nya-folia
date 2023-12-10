package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import java.util.Iterator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.projectiles.ProjectileSource;
// CraftBukkit end

public abstract class Projectile extends Entity implements TraceableEntity {

    @Nullable
    public UUID ownerUUID;
    @Nullable
    public Entity cachedOwner;
    public boolean leftOwner;
    public boolean hasBeenShot;

    // CraftBukkit start
    protected boolean hitCancelled = false;
    // CraftBukkit end

    Projectile(EntityType<? extends Projectile> type, Level world) {
        super(type, world);
    }

    public void setOwner(@Nullable Entity entity) {
        if (entity != null) {
            this.ownerUUID = entity.getUUID();
            this.cachedOwner = entity;
        }
        // Paper start - Fix null owners not being handled
        else {
            this.ownerUUID = null;
            this.cachedOwner = null;
            this.projectileSource = null;
        }
        // Paper end
        this.refreshProjectileSource(false); // Paper
    }
    // Paper start
    public void refreshProjectileSource(boolean fillCache) {
        if (fillCache) {
            this.getOwner();
        }
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved() && this.projectileSource == null && this.cachedOwner.getBukkitEntity() instanceof ProjectileSource projSource) {
            this.projectileSource = projSource;
        }
    }
    // Paper end

    // Folia start - region threading
    // In general, this is an entire mess. At the time of writing, there are fifty usages of getOwner.
    // Usage of this function is to avoid concurrency issues, even if it sacrifices behavior.
    @Nullable
    @Override
    public Entity getOwner() {
        Entity ret = this.getOwnerRaw();
        return io.papermc.paper.util.TickThread.isTickThreadFor(ret) ? ret : null;
    }
    // Folia end - region threading

    @Nullable
    public Entity getOwnerRaw() { // Folia - region threading
        io.papermc.paper.util.TickThread.ensureTickThread(this, "Cannot update owner state asynchronously"); // Folia - region threading
        if (this.cachedOwner != null && !this.cachedOwner.isRemoved()) {
            this.refreshProjectileSource(false); // Paper
            return this.cachedOwner;
        } else if (this.ownerUUID != null && this.level() instanceof ServerLevel) {
            this.cachedOwner = ((ServerLevel) this.level()).getEntity(this.ownerUUID);
            // Paper start - check all worlds
            if (this.cachedOwner == null) {
                for (final ServerLevel level : this.level().getServer().getAllLevels()) {
                    if (level == this.level()) continue;
                    final Entity entity = level.getEntity(this.ownerUUID);
                    if (entity != null) {
                        this.cachedOwner = entity;
                        break;
                    }
                }
            }
            // Paper end
            this.refreshProjectileSource(false); // Paper
            return this.cachedOwner;
        } else {
            return null;
        }
    }

    public Entity getEffectSource() {
        return (Entity) MoreObjects.firstNonNull(this.getOwner(), this);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (this.ownerUUID != null) {
            nbt.putUUID("Owner", this.ownerUUID);
        }

        if (this.leftOwner) {
            nbt.putBoolean("LeftOwner", true);
        }

        nbt.putBoolean("HasBeenShot", this.hasBeenShot);
    }

    protected boolean ownedBy(Entity entity) {
        return entity.getUUID().equals(this.ownerUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.hasUUID("Owner")) {
            this.ownerUUID = nbt.getUUID("Owner");
            this.cachedOwner = null;
            if (this instanceof ThrownEnderpearl && this.level() != null && this.level().paperConfig().fixes.disableUnloadedChunkEnderpearlExploit) { this.ownerUUID = null; } // Paper - Don't store shooter name for pearls to block enderpearl travel exploit
        }

        this.leftOwner = nbt.getBoolean("LeftOwner");
        this.hasBeenShot = nbt.getBoolean("HasBeenShot");
    }

    @Override
    public void tick() {
        if (!this.hasBeenShot) {
            this.gameEvent(GameEvent.PROJECTILE_SHOOT, this.getOwner());
            this.hasBeenShot = true;
        }

        if (!this.leftOwner) {
            this.leftOwner = this.checkLeftOwner();
        }

        super.tick();
    }

    private boolean checkLeftOwner() {
        Entity entity = this.getOwner();

        if (entity != null) {
            Iterator iterator = this.level().getEntities((Entity) this, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), (entity1) -> {
                return !entity1.isSpectator() && entity1.isPickable();
            }).iterator();

            while (iterator.hasNext()) {
                Entity entity1 = (Entity) iterator.next();

                if (entity1.getRootVehicle() == entity.getRootVehicle()) {
                    return false;
                }
            }
        }

        return true;
    }

    public void shoot(double x, double y, double z, float speed, float divergence) {
        Vec3 vec3d = (new Vec3(x, y, z)).normalize().add(this.random.triangle(0.0D, 0.0172275D * (double) divergence), this.random.triangle(0.0D, 0.0172275D * (double) divergence), this.random.triangle(0.0D, 0.0172275D * (double) divergence)).scale((double) speed);

        this.setDeltaMovement(vec3d);
        double d3 = vec3d.horizontalDistance();

        this.setYRot((float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D));
        this.setXRot((float) (Mth.atan2(vec3d.y, d3) * 57.2957763671875D));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void shootFromRotation(Entity shooter, float pitch, float yaw, float roll, float speed, float divergence) {
        float f5 = -Mth.sin(yaw * 0.017453292F) * Mth.cos(pitch * 0.017453292F);
        float f6 = -Mth.sin((pitch + roll) * 0.017453292F);
        float f7 = Mth.cos(yaw * 0.017453292F) * Mth.cos(pitch * 0.017453292F);

        this.shoot((double) f5, (double) f6, (double) f7, speed, divergence);
        Vec3 vec3d = shooter.getDeltaMovement();

        if (!shooter.level().paperConfig().misc.disableRelativeProjectileVelocity) this.setDeltaMovement(this.getDeltaMovement().add(vec3d.x, shooter.onGround() ? 0.0D : vec3d.y, vec3d.z)); // Paper - allow disabling relative velocity
    }

    // CraftBukkit start - call projectile hit event
    public void preOnHit(HitResult movingobjectposition) {
        org.bukkit.event.entity.ProjectileHitEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callProjectileHitEvent(this, movingobjectposition);
        this.hitCancelled = event != null && event.isCancelled();
        if (movingobjectposition.getType() == HitResult.Type.BLOCK || !this.hitCancelled) {
            this.onHit(movingobjectposition);
        }
    }
    // CraftBukkit end

    protected void onHit(HitResult hitResult) {
        HitResult.Type movingobjectposition_enummovingobjecttype = hitResult.getType();

        if (movingobjectposition_enummovingobjecttype == HitResult.Type.ENTITY) {
            this.onHitEntity((EntityHitResult) hitResult);
            this.level().gameEvent(GameEvent.PROJECTILE_LAND, hitResult.getLocation(), GameEvent.Context.of(this, (BlockState) null));
        } else if (movingobjectposition_enummovingobjecttype == HitResult.Type.BLOCK) {
            BlockHitResult movingobjectpositionblock = (BlockHitResult) hitResult;

            this.onHitBlock(movingobjectpositionblock);
            BlockPos blockposition = movingobjectpositionblock.getBlockPos();

            this.level().gameEvent(GameEvent.PROJECTILE_LAND, blockposition, GameEvent.Context.of(this, this.level().getBlockState(blockposition)));
        }

    }

    protected void onHitEntity(EntityHitResult entityHitResult) {}

    protected void onHitBlock(BlockHitResult blockHitResult) {
        // CraftBukkit start - cancellable hit event
        if (this.hitCancelled) {
            return;
        }
        // CraftBukkit end
        BlockState iblockdata = this.level().getBlockState(blockHitResult.getBlockPos());

        iblockdata.onProjectileHit(this.level(), iblockdata, blockHitResult, this);
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double d3 = Math.sqrt(x * x + z * z);

            this.setXRot((float) (Mth.atan2(y, d3) * 57.2957763671875D));
            this.setYRot((float) (Mth.atan2(x, z) * 57.2957763671875D));
            this.xRotO = this.getXRot();
            this.yRotO = this.getYRot();
            this.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        }

    }

    public boolean canHitEntity(Entity entity) {
        if (!entity.canBeHitByProjectile()) {
            return false;
        } else {
            Entity entity1 = this.getOwner();

            // Paper start - Cancel hit for vanished players
            if (entity1 instanceof net.minecraft.server.level.ServerPlayer && entity instanceof net.minecraft.server.level.ServerPlayer) {
                org.bukkit.entity.Player collided = (org.bukkit.entity.Player) entity.getBukkitEntity();
                org.bukkit.entity.Player shooter = (org.bukkit.entity.Player) entity1.getBukkitEntity();
                if (!shooter.canSee(collided)) {
                    return false;
                }
            }
            // Paper end
            return entity1 == null || this.leftOwner || !entity1.isPassengerOfSameVehicle(entity);
        }
    }

    protected void updateRotation() {
        Vec3 vec3d = this.getDeltaMovement();
        double d0 = vec3d.horizontalDistance();

        this.setXRot(Projectile.lerpRotation(this.xRotO, (float) (Mth.atan2(vec3d.y, d0) * 57.2957763671875D)));
        this.setYRot(Projectile.lerpRotation(this.yRotO, (float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D)));
    }

    protected static float lerpRotation(float prevRot, float newRot) {
        prevRot += Math.round((newRot - prevRot) / 360.0F) * 360.0F; // Paper - stop large look changes from crashing the server

        return Mth.lerp(0.2F, prevRot, newRot);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        Entity entity = this.getOwner();

        return new ClientboundAddEntityPacket(this, entity == null ? 0 : entity.getId());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Entity entity = this.level().getEntity(packet.getData());

        if (entity != null) {
            this.setOwner(entity);
        }

    }

    @Override
    public boolean mayInteract(Level world, BlockPos pos) {
        Entity entity = this.getOwner();

        return entity instanceof Player && io.papermc.paper.util.TickThread.isTickThreadFor(entity) ? entity.mayInteract(world, pos) : entity == null || world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // Folia - region threading
    }
}
