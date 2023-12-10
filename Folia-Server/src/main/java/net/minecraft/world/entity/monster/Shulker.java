package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// CraftBukkit start
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class Shulker extends AbstractGolem implements VariantHolder<Optional<DyeColor>>, Enemy {

    private static final UUID COVERED_ARMOR_MODIFIER_UUID = UUID.fromString("7E0292F2-9434-48D5-A29F-9583AF7DF27F");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(Shulker.COVERED_ARMOR_MODIFIER_UUID, "Covered armor bonus", 20.0D, AttributeModifier.Operation.ADDITION);
    protected static final EntityDataAccessor<Direction> DATA_ATTACH_FACE_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.DIRECTION);
    protected static final EntityDataAccessor<Byte> DATA_PEEK_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Byte> DATA_COLOR_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.BYTE);
    private static final int TELEPORT_STEPS = 6;
    private static final byte NO_COLOR = 16;
    private static final byte DEFAULT_COLOR = 16;
    private static final int MAX_TELEPORT_DISTANCE = 8;
    private static final int OTHER_SHULKER_SCAN_RADIUS = 8;
    private static final int OTHER_SHULKER_LIMIT = 5;
    private static final float PEEK_PER_TICK = 0.05F;
    static final Vector3f FORWARD = (Vector3f) Util.make(() -> {
        Vec3i baseblockposition = Direction.SOUTH.getNormal();

        return new Vector3f((float) baseblockposition.getX(), (float) baseblockposition.getY(), (float) baseblockposition.getZ());
    });
    private float currentPeekAmountO;
    private float currentPeekAmount;
    @Nullable
    private BlockPos clientOldAttachPosition;
    private int clientSideTeleportInterpolation;
    private static final float MAX_LID_OPEN = 1.0F;

    public Shulker(EntityType<? extends Shulker> type, Level world) {
        super(type, world);
        this.xpReward = 5;
        this.lookControl = new Shulker.ShulkerLookControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.02F, true));
        this.goalSelector.addGoal(4, new Shulker.ShulkerAttackGoal());
        this.goalSelector.addGoal(7, new Shulker.ShulkerPeekGoal());
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{this.getClass()})).setAlertOthers());
        this.targetSelector.addGoal(2, new Shulker.ShulkerNearestAttackGoal(this));
        this.targetSelector.addGoal(3, new Shulker.ShulkerDefenseAttackGoal(this));
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SHULKER_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        if (!this.isClosed()) {
            super.playAmbientSound();
        }

    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SHULKER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isClosed() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHULKER_HURT;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Shulker.DATA_ATTACH_FACE_ID, Direction.DOWN);
        this.entityData.define(Shulker.DATA_PEEK_ID, (byte) 0);
        this.entityData.define(Shulker.DATA_COLOR_ID, (byte) 16);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0D);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Shulker.ShulkerBodyRotationControl(this);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setAttachFace(Direction.from3DDataValue(nbt.getByte("AttachFace")));
        this.entityData.set(Shulker.DATA_PEEK_ID, nbt.getByte("Peek"));
        if (nbt.contains("Color", 99)) {
            this.entityData.set(Shulker.DATA_COLOR_ID, nbt.getByte("Color"));
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putByte("AttachFace", (byte) this.getAttachFace().get3DDataValue());
        nbt.putByte("Peek", (Byte) this.entityData.get(Shulker.DATA_PEEK_ID));
        nbt.putByte("Color", (Byte) this.entityData.get(Shulker.DATA_COLOR_ID));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !this.isPassenger() && !this.canStayAt(this.blockPosition(), this.getAttachFace())) {
            this.findNewAttachment();
        }

        if (this.updatePeekAmount()) {
            this.onPeekAmountChange();
        }

        if (this.level().isClientSide) {
            if (this.clientSideTeleportInterpolation > 0) {
                --this.clientSideTeleportInterpolation;
            } else {
                this.clientOldAttachPosition = null;
            }
        }

    }

    private void findNewAttachment() {
        Direction enumdirection = this.findAttachableSurface(this.blockPosition());

        if (enumdirection != null) {
            this.setAttachFace(enumdirection);
        } else {
            this.teleportSomewhere();
        }

    }

    @Override
    protected AABB makeBoundingBox() {
        float f = Shulker.getPhysicalPeek(this.currentPeekAmount);
        Direction enumdirection = this.getAttachFace().getOpposite();
        float f1 = this.getType().getWidth() / 2.0F;

        return Shulker.getProgressAabb(enumdirection, f).move(this.getX() - (double) f1, this.getY(), this.getZ() - (double) f1);
    }

    private static float getPhysicalPeek(float openProgress) {
        return 0.5F - Mth.sin((0.5F + openProgress) * 3.1415927F) * 0.5F;
    }

    private boolean updatePeekAmount() {
        this.currentPeekAmountO = this.currentPeekAmount;
        float f = (float) this.getRawPeekAmount() * 0.01F;

        if (this.currentPeekAmount == f) {
            return false;
        } else {
            if (this.currentPeekAmount > f) {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount - 0.05F, f, 1.0F);
            } else {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount + 0.05F, 0.0F, f);
            }

            return true;
        }
    }

    private void onPeekAmountChange() {
        this.reapplyPosition();
        float f = Shulker.getPhysicalPeek(this.currentPeekAmount);
        float f1 = Shulker.getPhysicalPeek(this.currentPeekAmountO);
        Direction enumdirection = this.getAttachFace().getOpposite();
        float f2 = f - f1;

        if (f2 > 0.0F) {
            List<Entity> list = this.level().getEntities((Entity) this, Shulker.getProgressDeltaAabb(enumdirection, f1, f).move(this.getX() - 0.5D, this.getY(), this.getZ() - 0.5D), EntitySelector.NO_SPECTATORS.and((entity) -> {
                return !entity.isPassengerOfSameVehicle(this);
            }));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (!(entity instanceof Shulker) && !entity.noPhysics) {
                    entity.move(MoverType.SHULKER, new Vec3((double) (f2 * (float) enumdirection.getStepX()), (double) (f2 * (float) enumdirection.getStepY()), (double) (f2 * (float) enumdirection.getStepZ())));
                }
            }

        }
    }

    public static AABB getProgressAabb(Direction direction, float extraLength) {
        return Shulker.getProgressDeltaAabb(direction, -1.0F, extraLength);
    }

    public static AABB getProgressDeltaAabb(Direction direction, float prevExtraLength, float extraLength) {
        double d0 = (double) Math.max(prevExtraLength, extraLength);
        double d1 = (double) Math.min(prevExtraLength, extraLength);

        return (new AABB(BlockPos.ZERO)).expandTowards((double) direction.getStepX() * d0, (double) direction.getStepY() * d0, (double) direction.getStepZ() * d0).contract((double) (-direction.getStepX()) * (1.0D + d1), (double) (-direction.getStepY()) * (1.0D + d1), (double) (-direction.getStepZ()) * (1.0D + d1));
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        if (this.level().isClientSide()) {
            this.clientOldAttachPosition = null;
            this.clientSideTeleportInterpolation = 0;
        }

        this.setAttachFace(Direction.DOWN);
        return super.startRiding(entity, force);
    }

    @Override
    public void stopRiding() {
        super.stopRiding();
        if (this.level().isClientSide) {
            this.clientOldAttachPosition = this.blockPosition();
        }

        this.yBodyRotO = 0.0F;
        this.yBodyRot = 0.0F;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        this.setYRot(0.0F);
        this.yHeadRot = this.getYRot();
        this.setOldPosAndRot();
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData, entityNbt);
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        if (movementType == MoverType.SHULKER_BOX) {
            this.teleportSomewhere();
        } else {
            super.move(movementType, movement);
        }

    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    public void setDeltaMovement(Vec3 velocity) {}

    @Override
    public void setPos(double x, double y, double z) {
        BlockPos blockposition = this.blockPosition();

        if (this.isPassenger()) {
            super.setPos(x, y, z);
        } else {
            super.setPos((double) Mth.floor(x) + 0.5D, (double) Mth.floor(y + 0.5D), (double) Mth.floor(z) + 0.5D);
        }

        if (this.tickCount != 0) {
            BlockPos blockposition1 = this.blockPosition();

            if (!blockposition1.equals(blockposition)) {
                this.entityData.set(Shulker.DATA_PEEK_ID, (byte) 0);
                this.hasImpulse = true;
                if (this.level().isClientSide && !this.isPassenger() && !blockposition1.equals(this.clientOldAttachPosition)) {
                    this.clientOldAttachPosition = blockposition;
                    this.clientSideTeleportInterpolation = 6;
                    this.xOld = this.getX();
                    this.yOld = this.getY();
                    this.zOld = this.getZ();
                }
            }

        }
    }

    @Nullable
    protected Direction findAttachableSurface(BlockPos pos) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (this.canStayAt(pos, enumdirection)) {
                return enumdirection;
            }
        }

        return null;
    }

    boolean canStayAt(BlockPos pos, Direction direction) {
        if (this.isPositionBlocked(pos)) {
            return false;
        } else {
            Direction enumdirection1 = direction.getOpposite();

            if (!this.level().loadedAndEntityCanStandOnFace(pos.relative(direction), this, enumdirection1)) {
                return false;
            } else {
                AABB axisalignedbb = Shulker.getProgressAabb(enumdirection1, 1.0F).move(pos).deflate(1.0E-6D);

                return this.level().noCollision(this, axisalignedbb);
            }
        }
    }

    private boolean isPositionBlocked(BlockPos pos) {
        BlockState iblockdata = this.level().getBlockState(pos);

        if (iblockdata.isAir()) {
            return false;
        } else {
            boolean flag = iblockdata.is(Blocks.MOVING_PISTON) && pos.equals(this.blockPosition());

            return !flag;
        }
    }

    protected boolean teleportSomewhere() {
        if (!this.isNoAi() && this.isAlive()) {
            BlockPos blockposition = this.blockPosition();

            for (int i = 0; i < 5; ++i) {
                BlockPos blockposition1 = blockposition.offset(Mth.randomBetweenInclusive(this.random, -8, 8), Mth.randomBetweenInclusive(this.random, -8, 8), Mth.randomBetweenInclusive(this.random, -8, 8));

                if (blockposition1.getY() > this.level().getMinBuildHeight() && this.level().isEmptyBlock(blockposition1) && this.level().getWorldBorder().isWithinBounds(blockposition1) && this.level().noCollision(this, (new AABB(blockposition1)).deflate(1.0E-6D))) {
                    Direction enumdirection = this.findAttachableSurface(blockposition1);

                    if (enumdirection != null) {
                        // CraftBukkit start
                        EntityTeleportEvent teleportEvent = CraftEventFactory.callEntityTeleportEvent(this, blockposition1.getX(), blockposition1.getY(), blockposition1.getZ());
                        if (teleportEvent.isCancelled()) {
                            return false;
                        } else {
                            blockposition1 = CraftLocation.toBlockPosition(teleportEvent.getTo());
                        }
                        // CraftBukkit end
                        this.unRide();
                        this.setAttachFace(enumdirection);
                        this.playSound(SoundEvents.SHULKER_TELEPORT, 1.0F, 1.0F);
                        this.setPos((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY(), (double) blockposition1.getZ() + 0.5D);
                        this.level().gameEvent(GameEvent.TELEPORT, blockposition, GameEvent.Context.of((Entity) this));
                        this.entityData.set(Shulker.DATA_PEEK_ID, (byte) 0);
                        this.setTarget((LivingEntity) null);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.lerpSteps = 0;
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity entity;

        if (this.isClosed()) {
            entity = source.getDirectEntity();
            if (entity instanceof AbstractArrow) {
                return false;
            }
        }

        if (!super.hurt(source, amount)) {
            return false;
        } else {
            if ((double) this.getHealth() < (double) this.getMaxHealth() * 0.5D && this.random.nextInt(4) == 0) {
                this.teleportSomewhere();
            } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                entity = source.getDirectEntity();
                if (entity != null && entity.getType() == EntityType.SHULKER_BULLET) {
                    this.hitByShulkerBullet();
                }
            }

            return true;
        }
    }

    private boolean isClosed() {
        return this.getRawPeekAmount() == 0;
    }

    private void hitByShulkerBullet() {
        Vec3 vec3d = this.position();
        AABB axisalignedbb = this.getBoundingBox();

        if (!this.isClosed() && this.teleportSomewhere()) {
            int i = this.level().getEntities((EntityTypeTest) EntityType.SHULKER, axisalignedbb.inflate(8.0D), Entity::isAlive).size();
            float f = (float) (i - 1) / 5.0F;

            if (this.level().random.nextFloat() >= f) {
                Shulker entityshulker = (Shulker) EntityType.SHULKER.create(this.level());

                if (entityshulker != null) {
                    entityshulker.setVariant(this.getVariant());
                    entityshulker.moveTo(vec3d);
                    this.level().addFreshEntity(entityshulker, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - the mysteries of life
                }

            }
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    public Direction getAttachFace() {
        return (Direction) this.entityData.get(Shulker.DATA_ATTACH_FACE_ID);
    }

    public void setAttachFace(Direction face) {
        this.entityData.set(Shulker.DATA_ATTACH_FACE_ID, face);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Shulker.DATA_ATTACH_FACE_ID.equals(data)) {
            this.setBoundingBox(this.makeBoundingBox());
        }

        super.onSyncedDataUpdated(data);
    }

    public int getRawPeekAmount() {
        return (Byte) this.entityData.get(Shulker.DATA_PEEK_ID);
    }

    public void setRawPeekAmount(int peekAmount) {
        if (!this.level().isClientSide) {
            this.getAttribute(Attributes.ARMOR).removeModifier(Shulker.COVERED_ARMOR_MODIFIER.getId());
            if (peekAmount == 0) {
                this.getAttribute(Attributes.ARMOR).addPermanentModifier(Shulker.COVERED_ARMOR_MODIFIER);
                this.playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_CLOSE);
            } else {
                this.playSound(SoundEvents.SHULKER_OPEN, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_OPEN);
            }
        }

        this.entityData.set(Shulker.DATA_PEEK_ID, (byte) peekAmount);
    }

    public float getClientPeekAmount(float delta) {
        return Mth.lerp(delta, this.currentPeekAmountO, this.currentPeekAmount);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 0.5F;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.yBodyRot = 0.0F;
        this.yBodyRotO = 0.0F;
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public float getPickRadius() {
        return 0.0F;
    }

    public Optional<Vec3> getRenderPosition(float tickDelta) {
        if (this.clientOldAttachPosition != null && this.clientSideTeleportInterpolation > 0) {
            double d0 = (double) ((float) this.clientSideTeleportInterpolation - tickDelta) / 6.0D;

            d0 *= d0;
            BlockPos blockposition = this.blockPosition();
            double d1 = (double) (blockposition.getX() - this.clientOldAttachPosition.getX()) * d0;
            double d2 = (double) (blockposition.getY() - this.clientOldAttachPosition.getY()) * d0;
            double d3 = (double) (blockposition.getZ() - this.clientOldAttachPosition.getZ()) * d0;

            return Optional.of(new Vec3(-d1, -d2, -d3));
        } else {
            return Optional.empty();
        }
    }

    public void setVariant(Optional<DyeColor> variant) {
        this.entityData.set(Shulker.DATA_COLOR_ID, (Byte) variant.map((enumcolor) -> {
            return (byte) enumcolor.getId();
        }).orElse((byte) 16));
    }

    @Override
    public Optional<DyeColor> getVariant() {
        return Optional.ofNullable(this.getColor());
    }

    @Nullable
    public DyeColor getColor() {
        byte b0 = (Byte) this.entityData.get(Shulker.DATA_COLOR_ID);

        return b0 != 16 && b0 <= 15 ? DyeColor.byId(b0) : null;
    }

    private class ShulkerLookControl extends LookControl {

        public ShulkerLookControl(Mob entity) {
            super(entity);
        }

        @Override
        protected void clampHeadRotationToBody() {}

        @Override
        protected Optional<Float> getYRotD() {
            Direction enumdirection = Shulker.this.getAttachFace().getOpposite();
            Vector3f vector3f = enumdirection.getRotation().transform(new Vector3f(Shulker.FORWARD));
            Vec3i baseblockposition = enumdirection.getNormal();
            Vector3f vector3f1 = new Vector3f((float) baseblockposition.getX(), (float) baseblockposition.getY(), (float) baseblockposition.getZ());

            vector3f1.cross(vector3f);
            double d0 = this.wantedX - this.mob.getX();
            double d1 = this.wantedY - this.mob.getEyeY();
            double d2 = this.wantedZ - this.mob.getZ();
            Vector3f vector3f2 = new Vector3f((float) d0, (float) d1, (float) d2);
            float f = vector3f1.dot(vector3f2);
            float f1 = vector3f.dot(vector3f2);

            return Math.abs(f) <= 1.0E-5F && Math.abs(f1) <= 1.0E-5F ? Optional.empty() : Optional.of((float) (Mth.atan2((double) (-f), (double) f1) * 57.2957763671875D));
        }

        @Override
        protected Optional<Float> getXRotD() {
            return Optional.of(0.0F);
        }
    }

    private class ShulkerAttackGoal extends Goal {

        private int attackTime;

        public ShulkerAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity entityliving = Shulker.this.getTarget();

            return entityliving != null && entityliving.isAlive() ? Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL : false;
        }

        @Override
        public void start() {
            this.attackTime = 20;
            Shulker.this.setRawPeekAmount(100);
        }

        @Override
        public void stop() {
            Shulker.this.setRawPeekAmount(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL) {
                --this.attackTime;
                LivingEntity entityliving = Shulker.this.getTarget();

                if (entityliving != null) {
                    Shulker.this.getLookControl().setLookAt(entityliving, 180.0F, 180.0F);
                    double d0 = Shulker.this.distanceToSqr((Entity) entityliving);

                    if (d0 < 400.0D) {
                        if (this.attackTime <= 0) {
                            this.attackTime = 20 + Shulker.this.random.nextInt(10) * 20 / 2;
                            Shulker.this.level().addFreshEntity(new ShulkerBullet(Shulker.this.level(), Shulker.this, entityliving, Shulker.this.getAttachFace().getAxis()));
                            Shulker.this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (Shulker.this.random.nextFloat() - Shulker.this.random.nextFloat()) * 0.2F + 1.0F);
                        }
                    } else {
                        Shulker.this.setTarget((LivingEntity) null);
                    }

                    super.tick();
                }
            }
        }
    }

    private class ShulkerPeekGoal extends Goal {

        private int peekTime;

        ShulkerPeekGoal() {}

        @Override
        public boolean canUse() {
            return Shulker.this.getTarget() == null && Shulker.this.random.nextInt(reducedTickDelay(40)) == 0 && Shulker.this.canStayAt(Shulker.this.blockPosition(), Shulker.this.getAttachFace());
        }

        @Override
        public boolean canContinueToUse() {
            return Shulker.this.getTarget() == null && this.peekTime > 0;
        }

        @Override
        public void start() {
            this.peekTime = this.adjustedTickDelay(20 * (1 + Shulker.this.random.nextInt(3)));
            Shulker.this.setRawPeekAmount(30);
        }

        @Override
        public void stop() {
            if (Shulker.this.getTarget() == null) {
                Shulker.this.setRawPeekAmount(0);
            }

        }

        @Override
        public void tick() {
            --this.peekTime;
        }
    }

    private class ShulkerNearestAttackGoal extends NearestAttackableTargetGoal<Player> {

        public ShulkerNearestAttackGoal(Shulker entityshulker) {
            super(entityshulker, Player.class, true);
        }

        @Override
        public boolean canUse() {
            return Shulker.this.level().getDifficulty() == Difficulty.PEACEFUL ? false : super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double distance) {
            Direction enumdirection = ((Shulker) this.mob).getAttachFace();

            return enumdirection.getAxis() == Direction.Axis.X ? this.mob.getBoundingBox().inflate(4.0D, distance, distance) : (enumdirection.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(distance, distance, 4.0D) : this.mob.getBoundingBox().inflate(distance, 4.0D, distance));
        }
    }

    private static class ShulkerDefenseAttackGoal extends NearestAttackableTargetGoal<LivingEntity> {

        public ShulkerDefenseAttackGoal(Shulker shulker) {
            super(shulker, LivingEntity.class, 10, true, false, (entityliving) -> {
                return entityliving instanceof Enemy;
            });
        }

        @Override
        public boolean canUse() {
            return this.mob.getTeam() == null ? false : super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double distance) {
            Direction enumdirection = ((Shulker) this.mob).getAttachFace();

            return enumdirection.getAxis() == Direction.Axis.X ? this.mob.getBoundingBox().inflate(4.0D, distance, distance) : (enumdirection.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(distance, distance, 4.0D) : this.mob.getBoundingBox().inflate(distance, 4.0D, distance));
        }
    }

    private static class ShulkerBodyRotationControl extends BodyRotationControl {

        public ShulkerBodyRotationControl(Mob entity) {
            super(entity);
        }

        @Override
        public void clientTick() {}
    }
}
