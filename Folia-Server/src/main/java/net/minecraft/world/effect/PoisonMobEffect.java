package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class PoisonMobEffect extends MobEffect {

    protected PoisonMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        if (entity.getHealth() > 1.0F) {
            entity.hurt(entity.damageSources().poison, 1.0F);  // CraftBukkit - DamageSource.MAGIC -> CraftEventFactory.POISON
        }

    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int k = 25 >> amplifier;

        return k > 0 ? duration % k == 0 : true;
    }
}
