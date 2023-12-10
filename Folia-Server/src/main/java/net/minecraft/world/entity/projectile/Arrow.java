package net.minecraft.world.entity.projectile;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

public class Arrow extends AbstractArrow {

    private static final int EXPOSED_POTION_DECAY_TIME = 600;
    private static final int NO_EFFECT_COLOR = -1;
    private static final EntityDataAccessor<Integer> ID_EFFECT_COLOR = SynchedEntityData.defineId(Arrow.class, EntityDataSerializers.INT);
    private static final byte EVENT_POTION_PUFF = 0;
    public Potion potion;
    public final Set<MobEffectInstance> effects;
    private boolean fixedColor;

    public Arrow(EntityType<? extends Arrow> type, Level world) {
        super(type, world);
        this.potion = Potions.EMPTY;
        this.effects = Sets.newHashSet();
    }

    public Arrow(Level world, double x, double y, double z) {
        super(EntityType.ARROW, x, y, z, world);
        this.potion = Potions.EMPTY;
        this.effects = Sets.newHashSet();
    }

    public Arrow(Level world, LivingEntity owner) {
        super(EntityType.ARROW, owner, world);
        this.potion = Potions.EMPTY;
        this.effects = Sets.newHashSet();
    }

    public void setEffectsFromItem(ItemStack stack) {
        if (stack.is(Items.TIPPED_ARROW)) {
            this.potion = PotionUtils.getPotion(stack);
            Collection<MobEffectInstance> collection = PotionUtils.getCustomEffects(stack);

            if (!collection.isEmpty()) {
                Iterator iterator = collection.iterator();

                while (iterator.hasNext()) {
                    MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                    this.effects.add(new MobEffectInstance(mobeffect));
                }
            }

            int i = Arrow.getCustomColor(stack);

            if (i == -1) {
                this.updateColor();
            } else {
                this.setFixedColor(i);
            }
        } else if (stack.is(Items.ARROW)) {
            this.potion = Potions.EMPTY;
            this.effects.clear();
            this.entityData.set(Arrow.ID_EFFECT_COLOR, -1);
        }

    }

    public static int getCustomColor(ItemStack stack) {
        CompoundTag nbttagcompound = stack.getTag();

        return nbttagcompound != null && nbttagcompound.contains("CustomPotionColor", 99) ? nbttagcompound.getInt("CustomPotionColor") : -1;
    }

    public void updateColor() {
        this.fixedColor = false;
        if (this.potion == Potions.EMPTY && this.effects.isEmpty()) {
            this.entityData.set(Arrow.ID_EFFECT_COLOR, -1);
        } else {
            this.entityData.set(Arrow.ID_EFFECT_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, this.effects)));
        }

    }

    public void addEffect(MobEffectInstance effect) {
        this.effects.add(effect);
        this.getEntityData().set(Arrow.ID_EFFECT_COLOR, PotionUtils.getColor((Collection) PotionUtils.getAllEffects(this.potion, this.effects)));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(Arrow.ID_EFFECT_COLOR, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.inGround) {
                if (this.inGroundTime % 5 == 0) {
                    this.makeParticle(1);
                }
            } else {
                this.makeParticle(2);
            }
        } else if (this.inGround && this.inGroundTime != 0 && !this.effects.isEmpty() && this.inGroundTime >= 600) {
            this.level().broadcastEntityEvent(this, (byte) 0);
            this.potion = Potions.EMPTY;
            this.effects.clear();
            this.entityData.set(Arrow.ID_EFFECT_COLOR, -1);
        }

    }

    private void makeParticle(int amount) {
        int j = this.getColor();

        if (j != -1 && amount > 0) {
            double d0 = (double) (j >> 16 & 255) / 255.0D;
            double d1 = (double) (j >> 8 & 255) / 255.0D;
            double d2 = (double) (j >> 0 & 255) / 255.0D;

            for (int k = 0; k < amount; ++k) {
                this.level().addParticle(ParticleTypes.ENTITY_EFFECT, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), d0, d1, d2);
            }

        }
    }

    public int getColor() {
        return (Integer) this.entityData.get(Arrow.ID_EFFECT_COLOR);
    }

    public void setFixedColor(int color) {
        this.fixedColor = true;
        this.entityData.set(Arrow.ID_EFFECT_COLOR, color);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.potion != Potions.EMPTY) {
            nbt.putString("Potion", BuiltInRegistries.POTION.getKey(this.potion).toString());
        }

        if (this.fixedColor) {
            nbt.putInt("Color", this.getColor());
        }

        if (!this.effects.isEmpty()) {
            ListTag nbttaglist = new ListTag();
            Iterator iterator = this.effects.iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                nbttaglist.add(mobeffect.save(new CompoundTag()));
            }

            nbt.put("custom_potion_effects", nbttaglist);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Potion", 8)) {
            this.potion = PotionUtils.getPotion(nbt);
        }

        Iterator iterator = PotionUtils.getCustomEffects(nbt).iterator();

        while (iterator.hasNext()) {
            MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

            this.addEffect(mobeffect);
        }

        if (nbt.contains("Color", 99)) {
            this.setFixedColor(nbt.getInt("Color"));
        } else {
            this.updateColor();
        }

    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        Entity entity = this.getEffectSource();
        Iterator iterator = this.potion.getEffects().iterator();

        MobEffectInstance mobeffect;

        while (iterator.hasNext()) {
            mobeffect = (MobEffectInstance) iterator.next();
            target.addEffect(new MobEffectInstance(mobeffect.getEffect(), Math.max(mobeffect.mapDuration((i) -> {
                return i / 8;
            }), 1), mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible()), entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
        }

        if (!this.effects.isEmpty()) {
            iterator = this.effects.iterator();

            while (iterator.hasNext()) {
                mobeffect = (MobEffectInstance) iterator.next();
                target.addEffect(mobeffect, entity, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ARROW); // CraftBukkit
            }
        }

    }

    @Override
    public ItemStack getPickupItem() {
        if (this.effects.isEmpty() && this.potion == Potions.EMPTY) {
            return new ItemStack(Items.ARROW);
        } else {
            ItemStack itemstack = new ItemStack(Items.TIPPED_ARROW);

            PotionUtils.setPotion(itemstack, this.potion);
            PotionUtils.setCustomEffects(itemstack, this.effects);
            if (this.fixedColor) {
                itemstack.getOrCreateTag().putInt("CustomPotionColor", this.getColor());
            }

            return itemstack;
        }
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 0) {
            int i = this.getColor();

            if (i != -1) {
                double d0 = (double) (i >> 16 & 255) / 255.0D;
                double d1 = (double) (i >> 8 & 255) / 255.0D;
                double d2 = (double) (i >> 0 & 255) / 255.0D;

                for (int j = 0; j < 20; ++j) {
                    this.level().addParticle(ParticleTypes.ENTITY_EFFECT, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), d0, d1, d2);
                }
            }
        } else {
            super.handleEntityEvent(status);
        }

    }
}
