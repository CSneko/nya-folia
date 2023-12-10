package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
// CraftBukkit start
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.LivingEntity;
// CraftBukkit end

public class ThrownPotion extends ThrowableItemProjectile implements ItemSupplier {

    public static final double SPLASH_RANGE = 4.0D;
    private static final double SPLASH_RANGE_SQ = 16.0D;
    public static final Predicate<net.minecraft.world.entity.LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = (entityliving) -> {
        return entityliving.isSensitiveToWater() || entityliving.isOnFire();
    };

    public ThrownPotion(EntityType<? extends ThrownPotion> type, Level world) {
        super(type, world);
    }

    public ThrownPotion(Level world, net.minecraft.world.entity.LivingEntity owner) {
        super(EntityType.POTION, owner, world);
    }

    public ThrownPotion(Level world, double x, double y, double z) {
        super(EntityType.POTION, x, y, z, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Potion potionregistry = PotionUtils.getPotion(itemstack);
            List<MobEffectInstance> list = PotionUtils.getMobEffects(itemstack);
            boolean flag = potionregistry == Potions.WATER && list.isEmpty();
            Direction enumdirection = blockHitResult.getDirection();
            BlockPos blockposition = blockHitResult.getBlockPos();
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (flag) {
                this.dowseFire(blockposition1);
                this.dowseFire(blockposition1.relative(enumdirection.getOpposite()));
                Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext()) {
                    Direction enumdirection1 = (Direction) iterator.next();

                    this.dowseFire(blockposition1.relative(enumdirection1));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        // Paper start - More projectile API
        this.splash(hitResult);
    }
    public void splash(@org.jetbrains.annotations.Nullable HitResult hitResult) {
        // Paper end - More projectile API
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Potion potionregistry = PotionUtils.getPotion(itemstack);
            List<MobEffectInstance> list = PotionUtils.getMobEffects(itemstack);
            boolean flag = potionregistry == Potions.WATER && list.isEmpty();
            boolean showParticles = true; // Paper

            if (flag) {
                showParticles = this.applyWater(); // Paper
            } else if (true || !list.isEmpty()) { // CraftBukkit - Call event even if no effects to apply
                if (this.isLingering()) {
                    showParticles = this.makeAreaOfEffectCloud(itemstack, potionregistry, hitResult); // CraftBukkit - Pass MovingObjectPosition // Paper
                } else {
                    showParticles = this.applySplash(list, hitResult != null && hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) hitResult).getEntity() : null, hitResult); // CraftBukkit - Pass MovingObjectPosition // Paper - nullable hitResult
                }
            }

            if (showParticles) { // Paper
            int i = potionregistry.hasInstantEffects() ? 2007 : 2002;

            this.level().levelEvent(i, this.blockPosition(), PotionUtils.getColor(itemstack));
            } // Paper
            this.discard();
        }
    }

    private static final Predicate<net.minecraft.world.entity.LivingEntity> APPLY_WATER_GET_ENTITIES_PREDICATE = ThrownPotion.WATER_SENSITIVE_OR_ON_FIRE.or(Axolotl.class::isInstance); // Paper
    private boolean applyWater() { // Paper
        AABB axisalignedbb = this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        // Paper start
        List<net.minecraft.world.entity.LivingEntity> list = this.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, axisalignedbb, ThrownPotion.APPLY_WATER_GET_ENTITIES_PREDICATE);
        Map<LivingEntity, Double> affected = new HashMap<>();
        java.util.Set<LivingEntity> rehydrate = new java.util.HashSet<>();
        java.util.Set<LivingEntity> extinguish = new java.util.HashSet<>();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            net.minecraft.world.entity.LivingEntity entityliving = (net.minecraft.world.entity.LivingEntity) iterator.next();
            if (entityliving instanceof Axolotl axolotl) {
                rehydrate.add(((org.bukkit.entity.Axolotl) axolotl.getBukkitEntity()));
            }
            double d0 = this.distanceToSqr((Entity) entityliving);

            if (d0 < 16.0D) {
                if (entityliving.isSensitiveToWater()) {
                    affected.put(entityliving.getBukkitLivingEntity(), 1.0);
                }

                if (entityliving.isOnFire() && entityliving.isAlive()) {
                    extinguish.add(entityliving.getBukkitLivingEntity());
                }
            }
        }

        io.papermc.paper.event.entity.WaterBottleSplashEvent event = new io.papermc.paper.event.entity.WaterBottleSplashEvent(
            (org.bukkit.entity.ThrownPotion) this.getBukkitEntity(), affected, rehydrate, extinguish
        );
        if (event.callEvent()) {
            for (LivingEntity affectedEntity : event.getToDamage()) {
                ((CraftLivingEntity) affectedEntity).getHandle().hurt(this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
            }
            for (LivingEntity toExtinguish : event.getToExtinguish()) {
                ((CraftLivingEntity) toExtinguish).getHandle().extinguishFire();
            }
            for (LivingEntity toRehydrate : event.getToRehydrate()) {
                if (((CraftLivingEntity) toRehydrate).getHandle() instanceof Axolotl axolotl) {
                    axolotl.rehydrate();
                }
            }
            // Paper end
        }
        return !event.isCancelled(); // Paper

    }

    private boolean applySplash(List<MobEffectInstance> list, @Nullable Entity entity, @Nullable HitResult position) { // CraftBukkit - Pass MovingObjectPosition // Paper - return boolean & nullable hitResult
        AABB axisalignedbb = this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<net.minecraft.world.entity.LivingEntity> list1 = this.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, axisalignedbb);
        Map<LivingEntity, Double> affected = new HashMap<LivingEntity, Double>(); // CraftBukkit

        if (!list1.isEmpty()) {
            Entity entity1 = this.getEffectSource();
            Iterator iterator = list1.iterator();

            while (iterator.hasNext()) {
                net.minecraft.world.entity.LivingEntity entityliving = (net.minecraft.world.entity.LivingEntity) iterator.next();

                if (entityliving.isAffectedByPotions()) {
                    double d0 = this.distanceToSqr((Entity) entityliving);

                    if (d0 < 16.0D) {
                        double d1;

                        // Paper - diff on change, used when calling the splash event for water splash potions
                        if (entityliving == entity) {
                            d1 = 1.0D;
                        } else {
                            d1 = 1.0D - Math.sqrt(d0) / 4.0D;
                        }

                        // CraftBukkit start
                        affected.put((LivingEntity) entityliving.getBukkitEntity(), d1);
                    }
                }
            }
        }

        org.bukkit.event.entity.PotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPotionSplashEvent(this, position, affected);
        if (!event.isCancelled() && list != null && !list.isEmpty()) { // do not process effects if there are no effects to process
            Entity entity1 = this.getEffectSource();
            for (LivingEntity victim : event.getAffectedEntities()) {
                if (!(victim instanceof CraftLivingEntity)) {
                    continue;
                }

                net.minecraft.world.entity.LivingEntity entityliving = ((CraftLivingEntity) victim).getHandle();
                double d1 = event.getIntensity(victim);
                // CraftBukkit end

                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator1.next();
                    MobEffect mobeffectlist = mobeffect.getEffect();
                    // CraftBukkit start - Abide by PVP settings - for players only!
                    if (!this.level().pvpMode && this.getOwner() instanceof ServerPlayer && entityliving instanceof ServerPlayer && entityliving != this.getOwner()) {
                        if (mobeffectlist == MobEffects.MOVEMENT_SLOWDOWN || mobeffectlist == MobEffects.DIG_SLOWDOWN || mobeffectlist == MobEffects.HARM || mobeffectlist == MobEffects.BLINDNESS
                                || mobeffectlist == MobEffects.HUNGER || mobeffectlist == MobEffects.WEAKNESS || mobeffectlist == MobEffects.POISON) {
                            continue;
                        }
                    }
                    // CraftBukkit end

                    if (mobeffectlist.isInstantenous()) {
                        mobeffectlist.applyInstantenousEffect(this, this.getOwner(), entityliving, mobeffect.getAmplifier(), d1);
                    } else {
                        int i = mobeffect.mapDuration((j) -> {
                            return (int) (d1 * (double) j + 0.5D);
                        });
                        MobEffectInstance mobeffect1 = new MobEffectInstance(mobeffectlist, i, mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible());

                        if (!mobeffect1.endsWithin(20)) {
                            entityliving.addEffect(mobeffect1, entity1, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_SPLASH); // CraftBukkit
                        }
                    }
                }
            }
        }
        return !event.isCancelled(); // Paper

    }

    private boolean makeAreaOfEffectCloud(ItemStack itemstack, Potion potionregistry, @Nullable HitResult position) { // CraftBukkit - Pass MovingObjectPosition // Paper - return boolean & nullable hitResult
        AreaEffectCloud entityareaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        Entity entity = this.getOwner();

        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            entityareaeffectcloud.setOwner((net.minecraft.world.entity.LivingEntity) entity);
        }

        entityareaeffectcloud.setRadius(3.0F);
        entityareaeffectcloud.setRadiusOnUse(-0.5F);
        entityareaeffectcloud.setWaitTime(10);
        entityareaeffectcloud.setRadiusPerTick(-entityareaeffectcloud.getRadius() / (float) entityareaeffectcloud.getDuration());
        entityareaeffectcloud.setPotion(potionregistry);
        Iterator iterator = PotionUtils.getCustomEffects(itemstack).iterator();

        boolean noEffects = potionregistry.getEffects().isEmpty(); // Paper
        while (iterator.hasNext()) {
            MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

            entityareaeffectcloud.addEffect(new MobEffectInstance(mobeffect));
            noEffects = false; // Paper
        }

        CompoundTag nbttagcompound = itemstack.getTag();

        if (nbttagcompound != null && nbttagcompound.contains("CustomPotionColor", 99)) {
            entityareaeffectcloud.setFixedColor(nbttagcompound.getInt("CustomPotionColor"));
        }

        // CraftBukkit start
        org.bukkit.event.entity.LingeringPotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLingeringPotionSplashEvent(this, position, entityareaeffectcloud);
        if (!(event.isCancelled() || entityareaeffectcloud.isRemoved() || (!event.allowsEmptyCreation() && (noEffects && entityareaeffectcloud.effects.isEmpty() && entityareaeffectcloud.getPotion().getEffects().isEmpty())))) { // Paper - don't spawn area effect cloud if the effects were empty and not changed during the event handling
            this.level().addFreshEntity(entityareaeffectcloud);
        } else {
            entityareaeffectcloud.discard();
        }
        // CraftBukkit end
        return !event.isCancelled(); // Paper
    }

    public boolean isLingering() {
        return this.getItem().is(Items.LINGERING_POTION);
    }

    private void dowseFire(BlockPos pos) {
        BlockState iblockdata = this.level().getBlockState(pos);

        if (iblockdata.is(BlockTags.FIRE)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, pos, iblockdata.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                this.level().destroyBlock(pos, false, this);
            }
            // CraftBukkit end
        } else if (AbstractCandleBlock.isLit(iblockdata)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, pos, iblockdata.setValue(AbstractCandleBlock.LIT, false))) {
                AbstractCandleBlock.extinguish((Player) null, iblockdata, this.level(), pos);
            }
            // CraftBukkit end
        } else if (CampfireBlock.isLitCampfire(iblockdata)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, pos, iblockdata.setValue(CampfireBlock.LIT, false))) {
                this.level().levelEvent((Player) null, 1009, pos, 0);
                CampfireBlock.dowse(this.getOwner(), this.level(), pos, iblockdata);
                this.level().setBlockAndUpdate(pos, (BlockState) iblockdata.setValue(CampfireBlock.LIT, false));
            }
            // CraftBukkit end
        }

    }
}
