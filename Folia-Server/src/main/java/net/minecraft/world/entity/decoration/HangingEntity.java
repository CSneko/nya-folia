package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
// CraftBukkit start
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import org.bukkit.entity.Hanging;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
// CraftBukkit end

public abstract class HangingEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    protected static final Predicate<Entity> HANGING_ENTITY = (entity) -> {
        return entity instanceof HangingEntity;
    };
    private int checkInterval; { this.checkInterval = this.getId() % this.level().spigotConfig.hangingTickFrequency; } // Paper
    public BlockPos pos;
    protected Direction direction;

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level world) {
        super(type, world);
        this.direction = Direction.SOUTH;
    }

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level world, BlockPos pos) {
        this(type, world);
        this.pos = pos;
    }

    @Override
    protected void defineSynchedData() {}

    public void setDirection(Direction facing) {
        Validate.notNull(facing);
        Validate.isTrue(facing.getAxis().isHorizontal());
        this.direction = facing;
        this.setYRot((float) (this.direction.get2DDataValue() * 90));
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    protected void recalculateBoundingBox() {
        if (this.direction != null) {
            // CraftBukkit start code moved in to calculateBoundingBox
            this.setBoundingBox(HangingEntity.calculateBoundingBox(this, this.pos, this.direction, this.getWidth(), this.getHeight()));
            // CraftBukkit end
        }
    }

    // CraftBukkit start - break out BB calc into own method
    public static AABB calculateBoundingBox(@Nullable Entity entity, BlockPos blockPosition, Direction direction, int width, int height) {
        {
            double d0 = (double) blockPosition.getX() + 0.5D;
            double d1 = (double) blockPosition.getY() + 0.5D;
            double d2 = (double) blockPosition.getZ() + 0.5D;
            double d3 = 0.46875D;
            double d4 = HangingEntity.offs(width);
            double d5 = HangingEntity.offs(height);

            d0 -= (double) direction.getStepX() * 0.46875D;
            d2 -= (double) direction.getStepZ() * 0.46875D;
            d1 += d5;
            Direction enumdirection = direction.getCounterClockWise();

            d0 += d4 * (double) enumdirection.getStepX();
            d2 += d4 * (double) enumdirection.getStepZ();
            if (entity != null) {
                entity.setPosRaw(d0, d1, d2);
            }
            double d6 = (double) width;
            double d7 = (double) height;
            double d8 = (double) width;

            if (direction.getAxis() == Direction.Axis.Z) {
                d8 = 1.0D;
            } else {
                d6 = 1.0D;
            }

            d6 /= 32.0D;
            d7 /= 32.0D;
            d8 /= 32.0D;
            return new AABB(d0 - d6, d1 - d7, d2 - d8, d0 + d6, d1 + d7, d2 + d8);
        }
    }
    // CraftBukkit end

    private static double offs(int i) { // CraftBukkit - static
        return i % 32 == 0 ? 0.5D : 0.0D;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            this.checkBelowWorld();
            if (this.checkInterval++ == this.level().spigotConfig.hangingTickFrequency) { // Spigot
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    // CraftBukkit start - fire break events
                    BlockState material = this.level().getBlockState(this.blockPosition());
                    HangingBreakEvent.RemoveCause cause;

                    if (!material.isAir()) {
                        // TODO: This feels insufficient to catch 100% of suffocation cases
                        cause = HangingBreakEvent.RemoveCause.OBSTRUCTION;
                    } else {
                        cause = HangingBreakEvent.RemoveCause.PHYSICS;
                    }

                    HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), cause);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    if (this.isRemoved() || event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.discard();
                    this.dropItem((Entity) null);
                }
            }
        }

    }

    public boolean survives() {
        if (!this.level().noCollision((Entity) this)) {
            return false;
        } else {
            int i = Math.max(1, this.getWidth() / 16);
            int j = Math.max(1, this.getHeight() / 16);
            BlockPos blockposition = this.pos.relative(this.direction.getOpposite());
            Direction enumdirection = this.direction.getCounterClockWise();
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int k = 0; k < i; ++k) {
                for (int l = 0; l < j; ++l) {
                    int i1 = (i - 1) / -2;
                    int j1 = (j - 1) / -2;

                    blockposition_mutableblockposition.set(blockposition).move(enumdirection, k + i1).move(Direction.UP, l + j1);
                    BlockState iblockdata = this.level().getBlockState(blockposition_mutableblockposition);

                    if (!iblockdata.isSolid() && !DiodeBlock.isDiode(iblockdata)) {
                        return false;
                    }
                }
            }

            return this.level().getEntities((Entity) this, this.getBoundingBox(), HangingEntity.HANGING_ENTITY).isEmpty();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player) {
            Player entityhuman = (Player) attacker;

            return !this.level().mayInteract(entityhuman, this.pos) ? true : this.hurt(this.damageSources().playerAttack(entityhuman), 0.0F);
        } else {
            return false;
        }
    }

    @Override
    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            if (!this.isRemoved() && !this.level().isClientSide) {
                // CraftBukkit start - fire break events
                Entity damager = (source.isIndirect()) ? source.getEntity() : source.getDirectEntity();
                HangingBreakEvent event;
                if (damager != null) {
                    event = new HangingBreakByEntityEvent((Hanging) this.getBukkitEntity(), damager.getBukkitEntity(), source.is(DamageTypeTags.IS_EXPLOSION) ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.ENTITY);
                } else {
                    event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), source.is(DamageTypeTags.IS_EXPLOSION) ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.DEFAULT);
                }

                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (this.isRemoved() || event.isCancelled()) {
                    return true;
                }
                // CraftBukkit end

                this.kill();
                this.markHurt();
                this.dropItem(source.getEntity());
            }

            return true;
        }
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        if (!this.level().isClientSide && !this.isRemoved() && movement.lengthSqr() > 0.0D) {
            if (this.isRemoved()) return; // CraftBukkit

            // CraftBukkit start - fire break events
            // TODO - Does this need its own cause? Seems to only be triggered by pistons
            HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.PHYSICS);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (this.isRemoved() || event.isCancelled()) {
                return;
            }
            // CraftBukkit end

            this.kill();
            this.dropItem((Entity) null);
        }

    }

    @Override
    public void push(double deltaX, double deltaY, double deltaZ) {
        if (false && !this.level().isClientSide && !this.isRemoved() && deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > 0.0D) { // CraftBukkit - not needed
            this.kill();
            this.dropItem((Entity) null);
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        BlockPos blockposition = this.getPos();

        nbt.putInt("TileX", blockposition.getX());
        nbt.putInt("TileY", blockposition.getY());
        nbt.putInt("TileZ", blockposition.getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        BlockPos blockposition = new BlockPos(nbt.getInt("TileX"), nbt.getInt("TileY"), nbt.getInt("TileZ"));

        if (!blockposition.closerThan(this.blockPosition(), 16.0D)) {
            HangingEntity.LOGGER.error("Hanging entity at invalid position: {}", blockposition);
        } else {
            this.pos = blockposition;
        }
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void dropItem(@Nullable Entity entity);

    public abstract void playPlacementSound();

    @Override
    public ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        ItemEntity entityitem = new ItemEntity(this.level(), this.getX() + (double) ((float) this.direction.getStepX() * 0.15F), this.getY() + (double) yOffset, this.getZ() + (double) ((float) this.direction.getStepZ() * 0.15F), stack);

        entityitem.setDefaultPickUpDelay();
        this.level().addFreshEntity(entityitem);
        return entityitem;
    }

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.pos = BlockPos.containing(x, y, z);
        this.recalculateBoundingBox();
        this.hasImpulse = true;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public float rotate(Rotation rotation) {
        if (this.direction.getAxis() != Direction.Axis.Y) {
            switch (rotation) {
                case CLOCKWISE_180:
                    this.direction = this.direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    this.direction = this.direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    this.direction = this.direction.getClockWise();
            }
        }

        float f = Mth.wrapDegrees(this.getYRot());

        switch (rotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 90.0F;
            case CLOCKWISE_90:
                return f + 270.0F;
            default:
                return f;
        }
    }

    @Override
    public float mirror(Mirror mirror) {
        return this.rotate(mirror.getRotation(this.direction));
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {}

    @Override
    public void refreshDimensions() {}
}
