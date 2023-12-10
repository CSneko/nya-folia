package net.minecraft.world.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
// CraftBukkit end

public class AreaEffectCloud extends Entity implements TraceableEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TIME_BETWEEN_APPLICATIONS = 5;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAITING = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ParticleOptions> DATA_PARTICLE = SynchedEntityData.defineId(AreaEffectCloud.class, EntityDataSerializers.PARTICLE);
    private static final float MAX_RADIUS = 32.0F;
    private static final float MINIMAL_RADIUS = 0.5F;
    private static final float DEFAULT_RADIUS = 3.0F;
    public static final float DEFAULT_WIDTH = 6.0F;
    public static final float HEIGHT = 0.5F;
    private static final String TAG_EFFECTS = "effects";
    public Potion potion;
    public List<MobEffectInstance> effects;
    private final Map<Entity, Integer> victims;
    private int duration;
    public int waitTime;
    public int reapplicationDelay;
    private boolean fixedColor;
    public int durationOnUse;
    public float radiusOnUse;
    public float radiusPerTick;
    @Nullable
    private net.minecraft.world.entity.LivingEntity owner;
    @Nullable
    public UUID ownerUUID;

    public AreaEffectCloud(EntityType<? extends AreaEffectCloud> type, Level world) {
        super(type, world);
        this.potion = Potions.EMPTY;
        this.effects = Lists.newArrayList();
        this.victims = Maps.newHashMap();
        this.duration = 600;
        this.waitTime = 20;
        this.reapplicationDelay = 20;
        this.noPhysics = true;
    }

    public AreaEffectCloud(Level world, double x, double y, double z) {
        this(EntityType.AREA_EFFECT_CLOUD, world);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(AreaEffectCloud.DATA_COLOR, 0);
        this.getEntityData().define(AreaEffectCloud.DATA_RADIUS, 3.0F);
        this.getEntityData().define(AreaEffectCloud.DATA_WAITING, false);
        this.getEntityData().define(AreaEffectCloud.DATA_PARTICLE, ParticleTypes.ENTITY_EFFECT);
    }

    public void setRadius(float radius) {
        if (!this.level().isClientSide) {
            this.getEntityData().set(AreaEffectCloud.DATA_RADIUS, Mth.clamp(radius, 0.0F, 32.0F));
        }

    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    public float getRadius() {
        return (Float) this.getEntityData().get(AreaEffectCloud.DATA_RADIUS);
    }

    public void setPotion(Potion potion) {
        this.potion = potion;
        if (!this.fixedColor) {
            this.updateColor();
        }

    }

    public void updateColor() {
        if (this.potion == Potions.EMPTY && this.effects.isEmpty()) {
            this.getEntityData().set(AreaEffectCloud.DATA_COLOR, 0);
        } else {
            this.getEntityData().set(AreaEffectCloud.DATA_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, this.effects)));
        }

    }

    public void addEffect(MobEffectInstance effect) {
        this.effects.add(effect);
        if (!this.fixedColor) {
            this.updateColor();
        }

    }

    public int getColor() {
        return (Integer) this.getEntityData().get(AreaEffectCloud.DATA_COLOR);
    }

    public void setFixedColor(int rgb) {
        this.fixedColor = true;
        this.getEntityData().set(AreaEffectCloud.DATA_COLOR, rgb);
    }

    public ParticleOptions getParticle() {
        return (ParticleOptions) this.getEntityData().get(AreaEffectCloud.DATA_PARTICLE);
    }

    public void setParticle(ParticleOptions particle) {
        this.getEntityData().set(AreaEffectCloud.DATA_PARTICLE, particle);
    }

    protected void setWaiting(boolean waiting) {
        this.getEntityData().set(AreaEffectCloud.DATA_WAITING, waiting);
    }

    public boolean isWaiting() {
        return (Boolean) this.getEntityData().get(AreaEffectCloud.DATA_WAITING);
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    // Spigot start - copied from below
    @Override
    public void inactiveTick() {
        super.inactiveTick();

        if (this.tickCount >= this.waitTime + this.duration) {
            this.discard();
            return;
        }
    }
    // Spigot end

    @Override
    public void tick() {
        super.tick();
        boolean flag = this.isWaiting();
        float f = this.getRadius();

        if (this.level().isClientSide) {
            if (flag && this.random.nextBoolean()) {
                return;
            }

            ParticleOptions particleparam = this.getParticle();
            int i;
            float f1;

            if (flag) {
                i = 2;
                f1 = 0.2F;
            } else {
                i = Mth.ceil(3.1415927F * f * f);
                f1 = f;
            }

            for (int j = 0; j < i; ++j) {
                float f2 = this.random.nextFloat() * 6.2831855F;
                float f3 = Mth.sqrt(this.random.nextFloat()) * f1;
                double d0 = this.getX() + (double) (Mth.cos(f2) * f3);
                double d1 = this.getY();
                double d2 = this.getZ() + (double) (Mth.sin(f2) * f3);
                double d3;
                double d4;
                double d5;

                if (particleparam.getType() == ParticleTypes.ENTITY_EFFECT) {
                    int k = flag && this.random.nextBoolean() ? 16777215 : this.getColor();

                    d3 = (double) ((float) (k >> 16 & 255) / 255.0F);
                    d4 = (double) ((float) (k >> 8 & 255) / 255.0F);
                    d5 = (double) ((float) (k & 255) / 255.0F);
                } else if (flag) {
                    d3 = 0.0D;
                    d4 = 0.0D;
                    d5 = 0.0D;
                } else {
                    d3 = (0.5D - this.random.nextDouble()) * 0.15D;
                    d4 = 0.009999999776482582D;
                    d5 = (0.5D - this.random.nextDouble()) * 0.15D;
                }

                this.level().addAlwaysVisibleParticle(particleparam, d0, d1, d2, d3, d4, d5);
            }
        } else {
            if (this.tickCount >= this.waitTime + this.duration) {
                this.discard();
                return;
            }

            boolean flag1 = this.tickCount < this.waitTime;

            if (flag != flag1) {
                this.setWaiting(flag1);
            }

            if (flag1) {
                return;
            }

            if (this.radiusPerTick != 0.0F) {
                f += this.radiusPerTick;
                if (f < 0.5F) {
                    this.discard();
                    return;
                }

                this.setRadius(f);
            }

            if (this.tickCount % 5 == 0) {
                this.victims.entrySet().removeIf((entry) -> {
                    return this.tickCount >= (Integer) entry.getValue();
                });
                List<MobEffectInstance> list = Lists.newArrayList();
                Iterator iterator = this.potion.getEffects().iterator();

                while (iterator.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                    list.add(new MobEffectInstance(mobeffect.getEffect(), mobeffect.mapDuration((l) -> {
                        return l / 4;
                    }), mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible()));
                }

                list.addAll(this.effects);
                if (list.isEmpty()) {
                    this.victims.clear();
                } else {
                    List<net.minecraft.world.entity.LivingEntity> list1 = this.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, this.getBoundingBox());

                    if (!list1.isEmpty()) {
                        Iterator iterator1 = list1.iterator();

                        List<LivingEntity> entities = new java.util.ArrayList<LivingEntity>(); // CraftBukkit
                        while (iterator1.hasNext()) {
                            net.minecraft.world.entity.LivingEntity entityliving = (net.minecraft.world.entity.LivingEntity) iterator1.next();

                            if (!this.victims.containsKey(entityliving) && entityliving.isAffectedByPotions()) {
                                double d6 = entityliving.getX() - this.getX();
                                double d7 = entityliving.getZ() - this.getZ();
                                double d8 = d6 * d6 + d7 * d7;

                                if (d8 <= (double) (f * f)) {
                                    // CraftBukkit start
                                    entities.add((LivingEntity) entityliving.getBukkitEntity());
                                }
                            }
                        }
                        org.bukkit.event.entity.AreaEffectCloudApplyEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callAreaEffectCloudApplyEvent(this, entities);
                        if (!event.isCancelled()) {
                            for (LivingEntity entity : event.getAffectedEntities()) {
                                if (entity instanceof CraftLivingEntity) {
                                    net.minecraft.world.entity.LivingEntity entityliving = ((CraftLivingEntity) entity).getHandle();
                                    // CraftBukkit end
                                    this.victims.put(entityliving, this.tickCount + this.reapplicationDelay);
                                    Iterator iterator2 = list.iterator();

                                    while (iterator2.hasNext()) {
                                        MobEffectInstance mobeffect1 = (MobEffectInstance) iterator2.next();

                                        if (mobeffect1.getEffect().isInstantenous()) {
                                            mobeffect1.getEffect().applyInstantenousEffect(this, this.getOwner(), entityliving, mobeffect1.getAmplifier(), 0.5D);
                                        } else {
                                            entityliving.addEffect(new MobEffectInstance(mobeffect1), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD); // CraftBukkit
                                        }
                                    }

                                    if (this.radiusOnUse != 0.0F) {
                                        f += this.radiusOnUse;
                                        if (f < 0.5F) {
                                            this.discard();
                                            return;
                                        }

                                        this.setRadius(f);
                                    }

                                    if (this.durationOnUse != 0) {
                                        this.duration += this.durationOnUse;
                                        if (this.duration <= 0) {
                                            this.discard();
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    public float getRadiusOnUse() {
        return this.radiusOnUse;
    }

    public void setRadiusOnUse(float radiusOnUse) {
        this.radiusOnUse = radiusOnUse;
    }

    public float getRadiusPerTick() {
        return this.radiusPerTick;
    }

    public void setRadiusPerTick(float radiusGrowth) {
        this.radiusPerTick = radiusGrowth;
    }

    public int getDurationOnUse() {
        return this.durationOnUse;
    }

    public void setDurationOnUse(int durationOnUse) {
        this.durationOnUse = durationOnUse;
    }

    public int getWaitTime() {
        return this.waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void setOwner(@Nullable net.minecraft.world.entity.LivingEntity owner) {
        this.owner = owner;
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Nullable
    @Override
    public net.minecraft.world.entity.LivingEntity getOwner() {
        if (this.owner == null && this.ownerUUID != null && this.level() instanceof ServerLevel) {
            Entity entity = ((ServerLevel) this.level()).getEntity(this.ownerUUID);

            if (entity instanceof net.minecraft.world.entity.LivingEntity) {
                this.owner = (net.minecraft.world.entity.LivingEntity) entity;
            }
        }

        return this.owner;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.tickCount = nbt.getInt("Age");
        this.duration = nbt.getInt("Duration");
        this.waitTime = nbt.getInt("WaitTime");
        this.reapplicationDelay = nbt.getInt("ReapplicationDelay");
        this.durationOnUse = nbt.getInt("DurationOnUse");
        this.radiusOnUse = nbt.getFloat("RadiusOnUse");
        this.radiusPerTick = nbt.getFloat("RadiusPerTick");
        this.setRadius(nbt.getFloat("Radius"));
        if (nbt.hasUUID("Owner")) {
            this.ownerUUID = nbt.getUUID("Owner");
        }

        if (nbt.contains("Particle", 8)) {
            try {
                this.setParticle(ParticleArgument.readParticle(new StringReader(nbt.getString("Particle")), (HolderLookup) BuiltInRegistries.PARTICLE_TYPE.asLookup()));
            } catch (CommandSyntaxException commandsyntaxexception) {
                AreaEffectCloud.LOGGER.warn("Couldn't load custom particle {}", nbt.getString("Particle"), commandsyntaxexception);
            }
        }

        if (nbt.contains("Color", 99)) {
            this.setFixedColor(nbt.getInt("Color"));
        }

        if (nbt.contains("Potion", 8)) {
            this.setPotion(PotionUtils.getPotion(nbt));
        }

        if (nbt.contains("effects", 9)) {
            ListTag nbttaglist = nbt.getList("effects", 10);

            this.effects.clear();

            for (int i = 0; i < nbttaglist.size(); ++i) {
                MobEffectInstance mobeffect = MobEffectInstance.load(nbttaglist.getCompound(i));

                if (mobeffect != null) {
                    this.addEffect(mobeffect);
                }
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putInt("Age", this.tickCount);
        nbt.putInt("Duration", this.duration);
        nbt.putInt("WaitTime", this.waitTime);
        nbt.putInt("ReapplicationDelay", this.reapplicationDelay);
        nbt.putInt("DurationOnUse", this.durationOnUse);
        nbt.putFloat("RadiusOnUse", this.radiusOnUse);
        nbt.putFloat("RadiusPerTick", this.radiusPerTick);
        nbt.putFloat("Radius", this.getRadius());
        nbt.putString("Particle", this.getParticle().writeToString());
        if (this.ownerUUID != null) {
            nbt.putUUID("Owner", this.ownerUUID);
        }

        if (this.fixedColor) {
            nbt.putInt("Color", this.getColor());
        }

        if (this.potion != Potions.EMPTY) {
            nbt.putString("Potion", BuiltInRegistries.POTION.getKey(this.potion).toString());
        }

        if (!this.effects.isEmpty()) {
            ListTag nbttaglist = new ListTag();
            Iterator iterator = this.effects.iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                nbttaglist.add(mobeffect.save(new CompoundTag()));
            }

            nbt.put("effects", nbttaglist);
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (AreaEffectCloud.DATA_RADIUS.equals(data)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(data);
    }

    public Potion getPotion() {
        return this.potion;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }
}
