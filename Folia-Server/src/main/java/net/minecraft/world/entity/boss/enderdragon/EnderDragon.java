package net.minecraft.world.entity.boss.enderdragon;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.joml.Vector3f;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
// CraftBukkit end

public class EnderDragon extends Mob implements Enemy {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
    private static final TargetingConditions CRYSTAL_DESTROY_TARGETING = TargetingConditions.forCombat().range(64.0D);
    private static final int GROWL_INTERVAL_MIN = 200;
    private static final int GROWL_INTERVAL_MAX = 400;
    private static final float SITTING_ALLOWED_DAMAGE_PERCENTAGE = 0.25F;
    private static final String DRAGON_DEATH_TIME_KEY = "DragonDeathTime";
    private static final String DRAGON_PHASE_KEY = "DragonPhase";
    public final double[][] positions = new double[64][3];
    public int posPointer = -1;
    public final EnderDragonPart[] subEntities;
    public final EnderDragonPart head;
    private final EnderDragonPart neck;
    private final EnderDragonPart body;
    private final EnderDragonPart tail1;
    private final EnderDragonPart tail2;
    private final EnderDragonPart tail3;
    private final EnderDragonPart wing1;
    private final EnderDragonPart wing2;
    public float oFlapTime;
    public float flapTime;
    public boolean inWall;
    public int dragonDeathTime;
    public float yRotA;
    @Nullable
    public EndCrystal nearestCrystal;
    @Nullable
    private EndDragonFight dragonFight;
    private BlockPos fightOrigin;
    private final EnderDragonPhaseManager phaseManager;
    private int growlTime;
    private float sittingDamageReceived;
    private final Node[] nodes;
    private final int[] nodeAdjacency;
    private final BinaryHeap openSet;
    private final Explosion explosionSource; // CraftBukkit - reusable source for CraftTNTPrimed.getSource()
    // Paper start - add var for save custom podium
    @Nullable
    private BlockPos podium;
    // Paper end

    public EnderDragon(EntityType<? extends EnderDragon> entitytypes, Level world) {
        super(EntityType.ENDER_DRAGON, world);
        this.fightOrigin = BlockPos.ZERO;
        this.growlTime = 100;
        this.nodes = new Node[24];
        this.nodeAdjacency = new int[24];
        this.openSet = new BinaryHeap();
        this.head = new EnderDragonPart(this, "head", 1.0F, 1.0F);
        this.neck = new EnderDragonPart(this, "neck", 3.0F, 3.0F);
        this.body = new EnderDragonPart(this, "body", 5.0F, 3.0F);
        this.tail1 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.tail2 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.tail3 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.wing1 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
        this.wing2 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
        this.subEntities = new EnderDragonPart[]{this.head, this.neck, this.body, this.tail1, this.tail2, this.tail3, this.wing1, this.wing2};
        this.setHealth(this.getMaxHealth());
        this.noPhysics = true;
        this.noCulling = true;
        this.phaseManager = new EnderDragonPhaseManager(this);
        this.explosionSource = new Explosion(world, this, null, null, Double.NaN, Double.NaN, Double.NaN, Float.NaN, true, Explosion.BlockInteraction.DESTROY); // CraftBukkit
    }

    public void setDragonFight(EndDragonFight fight) {
        this.dragonFight = fight;
    }

    public void setFightOrigin(BlockPos fightOrigin) {
        this.fightOrigin = fightOrigin;
    }

    public BlockPos getFightOrigin() {
        return this.fightOrigin;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200.0D);
    }

    // Paper start
    public BlockPos getPodium() {
        if (this.podium == null) {
            return EndPodiumFeature.getLocation(this.getFightOrigin());
        }
        return this.podium;
    }

    public void setPodium(@Nullable BlockPos blockPos) {
        this.podium = blockPos;
    }
    // Paper end

    @Override
    public boolean isFlapping() {
        float f = Mth.cos(this.flapTime * 6.2831855F);
        float f1 = Mth.cos(this.oFlapTime * 6.2831855F);

        return f1 <= -0.3F && f >= -0.3F;
    }

    @Override
    public void onFlap() {
        if (this.level().isClientSide && !this.isSilent()) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, this.getSoundSource(), 5.0F, 0.8F + this.random.nextFloat() * 0.3F, false);
        }

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(EnderDragon.DATA_PHASE, EnderDragonPhase.HOVERING.getId());
    }

    public double[] getLatencyPos(int segmentNumber, float tickDelta) {
        if (this.isDeadOrDying()) {
            tickDelta = 0.0F;
        }

        tickDelta = 1.0F - tickDelta;
        int j = this.posPointer - segmentNumber & 63;
        int k = this.posPointer - segmentNumber - 1 & 63;
        double[] adouble = new double[3];
        double d0 = this.positions[j][0];
        double d1 = Mth.wrapDegrees(this.positions[k][0] - d0);

        adouble[0] = d0 + d1 * (double) tickDelta;
        d0 = this.positions[j][1];
        d1 = this.positions[k][1] - d0;
        adouble[1] = d0 + d1 * (double) tickDelta;
        adouble[2] = Mth.lerp((double) tickDelta, this.positions[j][2], this.positions[k][2]);
        return adouble;
    }

    @Override
    public void aiStep() {
        this.processFlappingMovement();
        if (this.level().isClientSide) {
            this.setHealth(this.getHealth());
            if (!this.isSilent() && !this.phaseManager.getCurrentPhase().isSitting() && --this.growlTime < 0) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, this.getSoundSource(), 2.5F, 0.8F + this.random.nextFloat() * 0.3F, false);
                this.growlTime = 200 + this.random.nextInt(200);
            }
        }

        if (this.dragonFight == null) {
            Level world = this.level();

            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;
                EndDragonFight enderdragonbattle = worldserver.getDragonFight();

                if (enderdragonbattle != null && this.getUUID().equals(enderdragonbattle.getDragonUUID())) {
                    this.dragonFight = enderdragonbattle;
                }
            }
        }

        this.oFlapTime = this.flapTime;
        float f;

        if (this.isDeadOrDying()) {
            float f1 = (this.random.nextFloat() - 0.5F) * 8.0F;

            f = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level().addParticle(ParticleTypes.EXPLOSION, this.getX() + (double) f1, this.getY() + 2.0D + (double) f, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        } else {
            this.checkCrystals();
            Vec3 vec3d = this.getDeltaMovement();

            f = 0.2F / ((float) vec3d.horizontalDistance() * 10.0F + 1.0F);
            f *= (float) Math.pow(2.0D, vec3d.y);
            if (this.phaseManager.getCurrentPhase().isSitting()) {
                this.flapTime += 0.1F;
            } else if (this.inWall) {
                this.flapTime += f * 0.5F;
            } else {
                this.flapTime += f;
            }

            this.setYRot(Mth.wrapDegrees(this.getYRot()));
            if (this.isNoAi()) {
                this.flapTime = 0.5F;
            } else {
                if (this.posPointer < 0) {
                    for (int i = 0; i < this.positions.length; ++i) {
                        this.positions[i][0] = (double) this.getYRot();
                        this.positions[i][1] = this.getY();
                    }
                }

                if (++this.posPointer == this.positions.length) {
                    this.posPointer = 0;
                }

                this.positions[this.posPointer][0] = (double) this.getYRot();
                this.positions[this.posPointer][1] = this.getY();
                float f3;
                float f4;
                float f5;

                if (this.level().isClientSide) {
                    if (this.lerpSteps > 0) {
                        this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
                        --this.lerpSteps;
                    }

                    this.phaseManager.getCurrentPhase().doClientTick();
                } else {
                    DragonPhaseInstance idragoncontroller = this.phaseManager.getCurrentPhase();

                    idragoncontroller.doServerTick();
                    if (this.phaseManager.getCurrentPhase() != idragoncontroller) {
                        idragoncontroller = this.phaseManager.getCurrentPhase();
                        idragoncontroller.doServerTick();
                    }

                    Vec3 vec3d1 = idragoncontroller.getFlyTargetLocation();

                    if (vec3d1 != null && idragoncontroller.getPhase() != EnderDragonPhase.HOVERING) { // CraftBukkit - Don't move when hovering
                        double d0 = vec3d1.x - this.getX();
                        double d1 = vec3d1.y - this.getY();
                        double d2 = vec3d1.z - this.getZ();
                        double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                        float f6 = idragoncontroller.getFlySpeed();
                        double d4 = Math.sqrt(d0 * d0 + d2 * d2);

                        if (d4 > 0.0D) {
                            d1 = Mth.clamp(d1 / d4, (double) (-f6), (double) f6);
                        }

                        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d1 * 0.01D, 0.0D));
                        this.setYRot(Mth.wrapDegrees(this.getYRot()));
                        Vec3 vec3d2 = vec3d1.subtract(this.getX(), this.getY(), this.getZ()).normalize();
                        Vec3 vec3d3 = (new Vec3((double) Mth.sin(this.getYRot() * 0.017453292F), this.getDeltaMovement().y, (double) (-Mth.cos(this.getYRot() * 0.017453292F)))).normalize();

                        f3 = Math.max(((float) vec3d3.dot(vec3d2) + 0.5F) / 1.5F, 0.0F);
                        if (Math.abs(d0) > 9.999999747378752E-6D || Math.abs(d2) > 9.999999747378752E-6D) {
                            f4 = Mth.clamp(Mth.wrapDegrees(180.0F - (float) Mth.atan2(d0, d2) * 57.295776F - this.getYRot()), -50.0F, 50.0F);
                            this.yRotA *= 0.8F;
                            this.yRotA += f4 * idragoncontroller.getTurnSpeed();
                            this.setYRot(this.getYRot() + this.yRotA * 0.1F);
                        }

                        f4 = (float) (2.0D / (d3 + 1.0D));
                        f5 = 0.06F;
                        this.moveRelative(0.06F * (f3 * f4 + (1.0F - f4)), new Vec3(0.0D, 0.0D, -1.0D));
                        if (this.inWall) {
                            this.move(MoverType.SELF, this.getDeltaMovement().scale(0.800000011920929D));
                        } else {
                            this.move(MoverType.SELF, this.getDeltaMovement());
                        }

                        Vec3 vec3d4 = this.getDeltaMovement().normalize();
                        double d5 = 0.8D + 0.15D * (vec3d4.dot(vec3d3) + 1.0D) / 2.0D;

                        this.setDeltaMovement(this.getDeltaMovement().multiply(d5, 0.9100000262260437D, d5));
                    }
                }

                this.yBodyRot = this.getYRot();
                Vec3[] avec3d = new Vec3[this.subEntities.length];

                for (int j = 0; j < this.subEntities.length; ++j) {
                    avec3d[j] = new Vec3(this.subEntities[j].getX(), this.subEntities[j].getY(), this.subEntities[j].getZ());
                }

                float f7 = (float) (this.getLatencyPos(5, 1.0F)[1] - this.getLatencyPos(10, 1.0F)[1]) * 10.0F * 0.017453292F;
                float f8 = Mth.cos(f7);
                float f9 = Mth.sin(f7);
                float f10 = this.getYRot() * 0.017453292F;
                float f11 = Mth.sin(f10);
                float f12 = Mth.cos(f10);

                this.tickPart(this.body, (double) (f11 * 0.5F), 0.0D, (double) (-f12 * 0.5F));
                this.tickPart(this.wing1, (double) (f12 * 4.5F), 2.0D, (double) (f11 * 4.5F));
                this.tickPart(this.wing2, (double) (f12 * -4.5F), 2.0D, (double) (f11 * -4.5F));
                if (!this.level().isClientSide && this.hurtTime == 0) {
                    this.knockBack(this.level().getEntities((Entity) this, this.wing1.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.knockBack(this.level().getEntities((Entity) this, this.wing2.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.hurt(this.level().getEntities((Entity) this, this.head.getBoundingBox().inflate(1.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.hurt(this.level().getEntities((Entity) this, this.neck.getBoundingBox().inflate(1.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                }

                float f13 = Mth.sin(this.getYRot() * 0.017453292F - this.yRotA * 0.01F);
                float f14 = Mth.cos(this.getYRot() * 0.017453292F - this.yRotA * 0.01F);
                float f15 = this.getHeadYOffset();

                this.tickPart(this.head, (double) (f13 * 6.5F * f8), (double) (f15 + f9 * 6.5F), (double) (-f14 * 6.5F * f8));
                this.tickPart(this.neck, (double) (f13 * 5.5F * f8), (double) (f15 + f9 * 5.5F), (double) (-f14 * 5.5F * f8));
                double[] adouble = this.getLatencyPos(5, 1.0F);

                int k;

                for (k = 0; k < 3; ++k) {
                    EnderDragonPart entitycomplexpart = null;

                    if (k == 0) {
                        entitycomplexpart = this.tail1;
                    }

                    if (k == 1) {
                        entitycomplexpart = this.tail2;
                    }

                    if (k == 2) {
                        entitycomplexpart = this.tail3;
                    }

                    double[] adouble1 = this.getLatencyPos(12 + k * 2, 1.0F);
                    float f16 = this.getYRot() * 0.017453292F + this.rotWrap(adouble1[0] - adouble[0]) * 0.017453292F;

                    f3 = Mth.sin(f16);
                    f4 = Mth.cos(f16);
                    f5 = 1.5F;
                    float f17 = (float) (k + 1) * 2.0F;

                    this.tickPart(entitycomplexpart, (double) (-(f11 * 1.5F + f3 * f17) * f8), adouble1[1] - adouble[1] - (double) ((f17 + 1.5F) * f9) + 1.5D, (double) ((f12 * 1.5F + f4 * f17) * f8));
                }

                if (!this.level().isClientSide) {
                    this.inWall = this.checkWalls(this.head.getBoundingBox()) | this.checkWalls(this.neck.getBoundingBox()) | this.checkWalls(this.body.getBoundingBox());
                    if (this.dragonFight != null) {
                        this.dragonFight.updateDragon(this);
                    }
                }

                for (k = 0; k < this.subEntities.length; ++k) {
                    this.subEntities[k].xo = avec3d[k].x;
                    this.subEntities[k].yo = avec3d[k].y;
                    this.subEntities[k].zo = avec3d[k].z;
                    this.subEntities[k].xOld = avec3d[k].x;
                    this.subEntities[k].yOld = avec3d[k].y;
                    this.subEntities[k].zOld = avec3d[k].z;
                }

            }
        }
    }

    private void tickPart(EnderDragonPart enderDragonPart, double dx, double dy, double dz) {
        enderDragonPart.setPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    private float getHeadYOffset() {
        if (this.phaseManager.getCurrentPhase().isSitting()) {
            return -1.0F;
        } else {
            double[] adouble = this.getLatencyPos(5, 1.0F);
            double[] adouble1 = this.getLatencyPos(0, 1.0F);

            return (float) (adouble[1] - adouble1[1]);
        }
    }

    private void checkCrystals() {
        if (this.nearestCrystal != null) {
            if (this.nearestCrystal.isRemoved()) {
                this.nearestCrystal = null;
            } else if (this.tickCount % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                // CraftBukkit start
                EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), 1.0F, EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    this.setHealth((float) (this.getHealth() + event.getAmount()));
                }
                // CraftBukkit end
            }
        }

        if (this.random.nextInt(10) == 0) {
            List<EndCrystal> list = this.level().getEntitiesOfClass(EndCrystal.class, this.getBoundingBox().inflate(32.0D));
            EndCrystal entityendercrystal = null;
            double d0 = Double.MAX_VALUE;
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                EndCrystal entityendercrystal1 = (EndCrystal) iterator.next();
                double d1 = entityendercrystal1.distanceToSqr((Entity) this);

                if (d1 < d0) {
                    d0 = d1;
                    entityendercrystal = entityendercrystal1;
                }
            }

            this.nearestCrystal = entityendercrystal;
        }

    }

    private void knockBack(List<Entity> entities) {
        double d0 = (this.body.getBoundingBox().minX + this.body.getBoundingBox().maxX) / 2.0D;
        double d1 = (this.body.getBoundingBox().minZ + this.body.getBoundingBox().maxZ) / 2.0D;
        Iterator iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) {
                double d2 = entity.getX() - d0;
                double d3 = entity.getZ() - d1;
                double d4 = Math.max(d2 * d2 + d3 * d3, 0.1D);

                entity.push(d2 / d4 * 4.0D, 0.20000000298023224D, d3 / d4 * 4.0D, this); // Paper
                if (!this.phaseManager.getCurrentPhase().isSitting() && ((LivingEntity) entity).getLastHurtByMobTimestamp() < entity.tickCount - 2) {
                    entity.hurt(this.damageSources().mobAttack(this), 5.0F);
                    this.doEnchantDamageEffects(this, entity);
                }
            }
        }

    }

    private void hurt(List<Entity> entities) {
        Iterator iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof LivingEntity) {
                entity.hurt(this.damageSources().mobAttack(this), 10.0F);
                this.doEnchantDamageEffects(this, entity);
            }
        }

    }

    private float rotWrap(double yawDegrees) {
        return (float) Mth.wrapDegrees(yawDegrees);
    }

    private boolean checkWalls(AABB box) {
        int i = Mth.floor(box.minX);
        int j = Mth.floor(box.minY);
        int k = Mth.floor(box.minZ);
        int l = Mth.floor(box.maxX);
        int i1 = Mth.floor(box.maxY);
        int j1 = Mth.floor(box.maxZ);
        boolean flag = false;
        boolean flag1 = false;
        // CraftBukkit start - Create a list to hold all the destroyed blocks
        List<org.bukkit.block.Block> destroyedBlocks = new java.util.ArrayList<org.bukkit.block.Block>();
        // CraftBukkit end

        for (int k1 = i; k1 <= l; ++k1) {
            for (int l1 = j; l1 <= i1; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    BlockPos blockposition = new BlockPos(k1, l1, i2);
                    BlockState iblockdata = this.level().getBlockState(blockposition);

                    if (!iblockdata.isAir() && !iblockdata.is(BlockTags.DRAGON_TRANSPARENT)) {
                        if (this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && !iblockdata.is(BlockTags.DRAGON_IMMUNE)) {
                            // CraftBukkit start - Add blocks to list rather than destroying them
                            // flag1 = this.level().removeBlock(blockposition, false) || flag1;
                            flag1 = true;
                            destroyedBlocks.add(CraftBlock.at(this.level(), blockposition));
                            // CraftBukkit end
                        } else {
                            flag = true;
                        }
                    }
                }
            }
        }

        // CraftBukkit start - Set off an EntityExplodeEvent for the dragon exploding all these blocks
        // SPIGOT-4882: don't fire event if nothing hit
        if (!flag1) {
            return flag;
        }

        org.bukkit.entity.Entity bukkitEntity = this.getBukkitEntity();
        EntityExplodeEvent event = new EntityExplodeEvent(bukkitEntity, bukkitEntity.getLocation(), destroyedBlocks, 0F);
        bukkitEntity.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            // This flag literally means 'Dragon hit something hard' (Obsidian, White Stone or Bedrock) and will cause the dragon to slow down.
            // We should consider adding an event extension for it, or perhaps returning true if the event is cancelled.
            return flag;
        } else if (event.getYield() == 0F) {
            // Yield zero ==> no drops
            for (org.bukkit.block.Block block : event.blockList()) {
                this.level().removeBlock(new BlockPos(block.getX(), block.getY(), block.getZ()), false);
            }
        } else {
            for (org.bukkit.block.Block block : event.blockList()) {
                org.bukkit.Material blockId = block.getType();
                if (blockId.isAir()) {
                    continue;
                }

                CraftBlock craftBlock = ((CraftBlock) block);
                BlockPos blockposition = craftBlock.getPosition();

                Block nmsBlock = craftBlock.getNMS().getBlock();
                if (nmsBlock.dropFromExplosion(this.explosionSource)) {
                    BlockEntity tileentity = craftBlock.getNMS().hasBlockEntity() ? this.level().getBlockEntity(blockposition) : null;
                    LootParams.Builder loottableinfo_builder = (new LootParams.Builder((ServerLevel) this.level())).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockposition)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withParameter(LootContextParams.EXPLOSION_RADIUS, 1.0F / event.getYield()).withOptionalParameter(LootContextParams.BLOCK_ENTITY, tileentity);

                    craftBlock.getNMS().getDrops(loottableinfo_builder).forEach((itemstack) -> {
                        Block.popResource(this.level(), blockposition, itemstack);
                    });
                    craftBlock.getNMS().spawnAfterBreak((ServerLevel) this.level(), blockposition, ItemStack.EMPTY, false);
                }
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = this.level().getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                if(!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.EXPLOSION, explosionSource.getIndirectSourceEntity().getBukkitEntity()).callEvent())
                    continue;
                // Paper end
                nmsBlock.wasExploded(this.level(), blockposition, this.explosionSource);

                this.level().removeBlock(blockposition, false);
            }
        }
        // CraftBukkit end

        if (flag1) {
            BlockPos blockposition1 = new BlockPos(i + this.random.nextInt(l - i + 1), j + this.random.nextInt(i1 - j + 1), k + this.random.nextInt(j1 - k + 1));

            this.level().levelEvent(2008, blockposition1, 0);
        }

        return flag;
    }

    public boolean hurt(EnderDragonPart part, DamageSource source, float amount) {
        if (this.phaseManager.getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            return false;
        } else {
            amount = this.phaseManager.getCurrentPhase().onHurt(source, amount);
            if (part != this.head) {
                amount = amount / 4.0F + Math.min(amount, 1.0F);
            }

            if (amount < 0.01F) {
                return false;
            } else {
                if (source.getEntity() instanceof Player || source.is(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS)) {
                    float f1 = this.getHealth();

                    this.reallyHurt(source, amount);
                    if (this.isDeadOrDying() && !this.phaseManager.getCurrentPhase().isSitting()) {
                        this.setHealth(1.0F);
                        this.phaseManager.setPhase(EnderDragonPhase.DYING);
                    }

                    if (this.phaseManager.getCurrentPhase().isSitting()) {
                        this.sittingDamageReceived = this.sittingDamageReceived + f1 - this.getHealth();
                        if (this.sittingDamageReceived > 0.25F * this.getMaxHealth()) {
                            this.sittingDamageReceived = 0.0F;
                            this.phaseManager.setPhase(EnderDragonPhase.TAKEOFF);
                        }
                    }
                }

                return true;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.level().isClientSide ? this.hurt(this.body, source, amount) : false;
    }

    protected boolean reallyHurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    public void kill() {
        // Paper start
        this.silentDeath = true;
        org.bukkit.event.entity.EntityDeathEvent deathEvent =  org.bukkit.craftbukkit.event.CraftEventFactory.callEntityDeathEvent(this);
        if (deathEvent.isCancelled()) {
            this.silentDeath = false; // Reset to default if event was cancelled
            return;
        }
        // Paper end

        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
            this.dragonFight.setDragonKilled(this);
        }

    }

    // CraftBukkit start - SPIGOT-2420: Special case, the ender dragon drops 12000 xp for the first kill and 500 xp for every other kill and this over time.
    @Override
    public int getExpReward() {
        // CraftBukkit - Moved from #tickDeath method
        boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        short short0 = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            short0 = 12000;
        }

        return flag ? short0 : 0;
    }
    // CraftBukkit end

    @Override
    protected void tickDeath() {
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
        }

        ++this.dragonDeathTime;
        if (this.dragonDeathTime >= 180 && this.dragonDeathTime <= 200) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        }

        // CraftBukkit start - SPIGOT-2420: Moved up to #getExpReward method
        /*
        boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        short short0 = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            short0 = 12000;
        }
        */
        int short0 = this.expToDrop;
        // CraftBukkit end

        if (this.level() instanceof ServerLevel) {
            if (this.dragonDeathTime > 150 && this.dragonDeathTime % 5 == 0 && true) {  // CraftBukkit - SPIGOT-2420: Already checked for the game rule when calculating the xp
                ExperienceOrb.award((ServerLevel) this.level(), this.position(), Mth.floor((float) short0 * 0.08F), org.bukkit.entity.ExperienceOrb.SpawnReason.ENTITY_DEATH, this.lastHurtByPlayer, this); // Paper
            }

            if (this.dragonDeathTime == 1 && !this.isSilent()) {
                // CraftBukkit start - Use relative location for far away sounds
                // this.level().globalLevelEvent(1028, this.blockPosition(), 0);
                int viewDistance = ((ServerLevel) this.level()).getCraftServer().getViewDistance() * 16;
                for (net.minecraft.server.level.ServerPlayer player : this.level().spigotConfig.dragonDeathSoundRadius > 0 ? ((ServerLevel) this.level()).players() : this.level().getServer().getPlayerList().players) { // Paper
                    double deltaX = this.getX() - player.getX();
                    double deltaZ = this.getZ() - player.getZ();
                    double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                    if ( this.level().spigotConfig.dragonDeathSoundRadius > 0 && distanceSquared > this.level().spigotConfig.dragonDeathSoundRadius * this.level().spigotConfig.dragonDeathSoundRadius ) continue; // Spigot
                    if (distanceSquared > viewDistance * viewDistance) {
                        double deltaLength = Math.sqrt(distanceSquared);
                        double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                        double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(1028, new BlockPos((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                    } else {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(1028, new BlockPos((int) this.getX(), (int) this.getY(), (int) this.getZ()), 0, true));
                    }
                }
                // CraftBukkit end
            }
        }

        this.move(MoverType.SELF, new Vec3(0.0D, 0.10000000149011612D, 0.0D));
        if (this.dragonDeathTime == 200 && this.level() instanceof ServerLevel) {
            if (true) { // CraftBukkit - SPIGOT-2420: Already checked for the game rule when calculating the xp
                ExperienceOrb.award((ServerLevel) this.level(), this.position(), Mth.floor((float) short0 * 0.2F), org.bukkit.entity.ExperienceOrb.SpawnReason.ENTITY_DEATH, this.lastHurtByPlayer, this); // Paper
            }

            if (this.dragonFight != null) {
                this.dragonFight.setDragonKilled(this);
            }

            this.remove(Entity.RemovalReason.KILLED);
            this.gameEvent(GameEvent.ENTITY_DIE);
        }

    }

    public int findClosestNode() {
        if (this.nodes[0] == null) {
            for (int i = 0; i < 24; ++i) {
                int j = 5;
                int k;
                int l;

                if (i < 12) {
                    k = Mth.floor(60.0F * Mth.cos(2.0F * (-3.1415927F + 0.2617994F * (float) i)));
                    l = Mth.floor(60.0F * Mth.sin(2.0F * (-3.1415927F + 0.2617994F * (float) i)));
                } else {
                    int i1;

                    if (i < 20) {
                        i1 = i - 12;
                        k = Mth.floor(40.0F * Mth.cos(2.0F * (-3.1415927F + 0.3926991F * (float) i1)));
                        l = Mth.floor(40.0F * Mth.sin(2.0F * (-3.1415927F + 0.3926991F * (float) i1)));
                        j += 10;
                    } else {
                        i1 = i - 20;
                        k = Mth.floor(20.0F * Mth.cos(2.0F * (-3.1415927F + 0.7853982F * (float) i1)));
                        l = Mth.floor(20.0F * Mth.sin(2.0F * (-3.1415927F + 0.7853982F * (float) i1)));
                    }
                }

                int j1 = Math.max(this.level().getSeaLevel() + 10, this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(k, 0, l)).getY() + j);

                this.nodes[i] = new Node(k, j1, l);
            }

            this.nodeAdjacency[0] = 6146;
            this.nodeAdjacency[1] = 8197;
            this.nodeAdjacency[2] = 8202;
            this.nodeAdjacency[3] = 16404;
            this.nodeAdjacency[4] = 32808;
            this.nodeAdjacency[5] = 32848;
            this.nodeAdjacency[6] = 65696;
            this.nodeAdjacency[7] = 131392;
            this.nodeAdjacency[8] = 131712;
            this.nodeAdjacency[9] = 263424;
            this.nodeAdjacency[10] = 526848;
            this.nodeAdjacency[11] = 525313;
            this.nodeAdjacency[12] = 1581057;
            this.nodeAdjacency[13] = 3166214;
            this.nodeAdjacency[14] = 2138120;
            this.nodeAdjacency[15] = 6373424;
            this.nodeAdjacency[16] = 4358208;
            this.nodeAdjacency[17] = 12910976;
            this.nodeAdjacency[18] = 9044480;
            this.nodeAdjacency[19] = 9706496;
            this.nodeAdjacency[20] = 15216640;
            this.nodeAdjacency[21] = 13688832;
            this.nodeAdjacency[22] = 11763712;
            this.nodeAdjacency[23] = 8257536;
        }

        return this.findClosestNode(this.getX(), this.getY(), this.getZ());
    }

    public int findClosestNode(double x, double y, double z) {
        float f = 10000.0F;
        int i = 0;
        Node pathpoint = new Node(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        byte b0 = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            b0 = 12;
        }

        for (int j = b0; j < 24; ++j) {
            if (this.nodes[j] != null) {
                float f1 = this.nodes[j].distanceToSqr(pathpoint);

                if (f1 < f) {
                    f = f1;
                    i = j;
                }
            }
        }

        return i;
    }

    @Nullable
    public Path findPath(int from, int to, @Nullable Node pathNode) {
        Node pathpoint1;

        for (int k = 0; k < 24; ++k) {
            pathpoint1 = this.nodes[k];
            pathpoint1.closed = false;
            pathpoint1.h = 0.0F;
            pathpoint1.f = 0.0F;
            pathpoint1.h = 0.0F;
            pathpoint1.cameFrom = null;
            pathpoint1.heapIdx = -1;
        }

        Node pathpoint2 = this.nodes[from];

        pathpoint1 = this.nodes[to];
        pathpoint2.f = 0.0F;
        pathpoint2.h = pathpoint2.distanceTo(pathpoint1);
        pathpoint2.h = pathpoint2.h;
        this.openSet.clear();
        this.openSet.insert(pathpoint2);
        Node pathpoint3 = pathpoint2;
        byte b0 = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            b0 = 12;
        }

        label70:
        while (!this.openSet.isEmpty()) {
            Node pathpoint4 = this.openSet.pop();

            if (pathpoint4.equals(pathpoint1)) {
                if (pathNode != null) {
                    pathNode.cameFrom = pathpoint1;
                    pathpoint1 = pathNode;
                }

                return this.reconstructPath(pathpoint2, pathpoint1);
            }

            if (pathpoint4.distanceTo(pathpoint1) < pathpoint3.distanceTo(pathpoint1)) {
                pathpoint3 = pathpoint4;
            }

            pathpoint4.closed = true;
            int l = 0;
            int i1 = 0;

            while (true) {
                if (i1 < 24) {
                    if (this.nodes[i1] != pathpoint4) {
                        ++i1;
                        continue;
                    }

                    l = i1;
                }

                i1 = b0;

                while (true) {
                    if (i1 >= 24) {
                        continue label70;
                    }

                    if ((this.nodeAdjacency[l] & 1 << i1) > 0) {
                        Node pathpoint5 = this.nodes[i1];

                        if (!pathpoint5.closed) {
                            float f = pathpoint4.f + pathpoint4.distanceTo(pathpoint5);

                            if (!pathpoint5.inOpenSet() || f < pathpoint5.f) {
                                pathpoint5.cameFrom = pathpoint4;
                                pathpoint5.f = f;
                                pathpoint5.h = pathpoint5.distanceTo(pathpoint1);
                                if (pathpoint5.inOpenSet()) {
                                    this.openSet.changeCost(pathpoint5, pathpoint5.f + pathpoint5.h);
                                } else {
                                    pathpoint5.h = pathpoint5.f + pathpoint5.h;
                                    this.openSet.insert(pathpoint5);
                                }
                            }
                        }
                    }

                    ++i1;
                }
            }
        }

        if (pathpoint3 == pathpoint2) {
            return null;
        } else {
            EnderDragon.LOGGER.debug("Failed to find path from {} to {}", from, to);
            if (pathNode != null) {
                pathNode.cameFrom = pathpoint3;
                pathpoint3 = pathNode;
            }

            return this.reconstructPath(pathpoint2, pathpoint3);
        }
    }

    private Path reconstructPath(Node unused, Node node) {
        List<Node> list = Lists.newArrayList();
        Node pathpoint2 = node;

        list.add(0, node);

        while (pathpoint2.cameFrom != null) {
            pathpoint2 = pathpoint2.cameFrom;
            list.add(0, pathpoint2);
        }

        return new Path(list, new BlockPos(node.x, node.y, node.z), true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("DragonPhase", this.phaseManager.getCurrentPhase().getPhase().getId());
        nbt.putInt("DragonDeathTime", this.dragonDeathTime);
        nbt.putInt("Bukkit.expToDrop", this.expToDrop); // CraftBukkit - SPIGOT-2420: The ender dragon drops xp over time which can also happen between server starts
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("DragonPhase")) {
            this.phaseManager.setPhase(EnderDragonPhase.getById(nbt.getInt("DragonPhase")));
        }

        if (nbt.contains("DragonDeathTime")) {
            this.dragonDeathTime = nbt.getInt("DragonDeathTime");
        }

        // CraftBukkit start - SPIGOT-2420: The ender dragon drops xp over time which can also happen between server starts
        if (nbt.contains("Bukkit.expToDrop")) {
            this.expToDrop = nbt.getInt("Bukkit.expToDrop");
        }
        // CraftBukkit end
    }

    @Override
    public void checkDespawn() {}

    public EnderDragonPart[] getSubEntities() {
        return this.subEntities;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    public float getSoundVolume() {
        return 5.0F;
    }

    public float getHeadPartYOffset(int segmentOffset, double[] segment1, double[] segment2) {
        DragonPhaseInstance idragoncontroller = this.phaseManager.getCurrentPhase();
        EnderDragonPhase<? extends DragonPhaseInstance> dragoncontrollerphase = idragoncontroller.getPhase();
        double d0;

        if (dragoncontrollerphase != EnderDragonPhase.LANDING && dragoncontrollerphase != EnderDragonPhase.TAKEOFF) {
            if (idragoncontroller.isSitting()) {
                d0 = (double) segmentOffset;
            } else if (segmentOffset == 6) {
                d0 = 0.0D;
            } else {
                d0 = segment2[1] - segment1[1];
            }
        } else {
            BlockPos blockposition = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getPodium()); // Paper - use custom podium
            double d1 = Math.max(Math.sqrt(blockposition.distToCenterSqr(this.position())) / 4.0D, 1.0D);

            d0 = (double) segmentOffset / d1;
        }

        return (float) d0;
    }

    public Vec3 getHeadLookVector(float tickDelta) {
        DragonPhaseInstance idragoncontroller = this.phaseManager.getCurrentPhase();
        EnderDragonPhase<? extends DragonPhaseInstance> dragoncontrollerphase = idragoncontroller.getPhase();
        float f1;
        Vec3 vec3d;

        if (dragoncontrollerphase != EnderDragonPhase.LANDING && dragoncontrollerphase != EnderDragonPhase.TAKEOFF) {
            if (idragoncontroller.isSitting()) {
                float f2 = this.getXRot();

                f1 = 1.5F;
                this.setXRot(-45.0F);
                vec3d = this.getViewVector(tickDelta);
                this.setXRot(f2);
            } else {
                vec3d = this.getViewVector(tickDelta);
            }
        } else {
            BlockPos blockposition = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getPodium()); // Paper - use custom podium

            f1 = Math.max((float) Math.sqrt(blockposition.distToCenterSqr(this.position())) / 4.0F, 1.0F);
            float f3 = 6.0F / f1;
            float f4 = this.getXRot();
            float f5 = 1.5F;

            this.setXRot(-f3 * 1.5F * 5.0F);
            vec3d = this.getViewVector(tickDelta);
            this.setXRot(f4);
        }

        return vec3d;
    }

    public void onCrystalDestroyed(EndCrystal endCrystal, BlockPos pos, DamageSource source) {
        Player entityhuman;

        if (source.getEntity() instanceof Player) {
            entityhuman = (Player) source.getEntity();
        } else {
            entityhuman = this.level().getNearestPlayer(EnderDragon.CRYSTAL_DESTROY_TARGETING, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
        }

        if (endCrystal == this.nearestCrystal) {
            this.hurt(this.head, this.damageSources().explosion(endCrystal, entityhuman), 10.0F);
        }

        this.phaseManager.getCurrentPhase().onCrystalDestroyed(endCrystal, pos, source, entityhuman);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (EnderDragon.DATA_PHASE.equals(data) && this.level().isClientSide) {
            this.phaseManager.setPhase(EnderDragonPhase.getById((Integer) this.getEntityData().get(EnderDragon.DATA_PHASE)));
        }

        super.onSyncedDataUpdated(data);
    }

    public EnderDragonPhaseManager getPhaseManager() {
        return this.phaseManager;
    }

    @Nullable
    public EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        EnderDragonPart[] aentitycomplexpart = this.getSubEntities();

        for (int i = 0; i < aentitycomplexpart.length; ++i) {
            aentitycomplexpart[i].setId(i + packet.getId());
        }

    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target.canBeSeenAsEnemy();
    }

    @Override
    protected Vector3f getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vector3f(0.0F, this.body.getBbHeight(), 0.0F);
    }
}
