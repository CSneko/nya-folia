package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

class SaturationMobEffect extends InstantenousMobEffect {

    protected SaturationMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        super.applyEffectTick(entity, amplifier);
        if (!entity.level().isClientSide && entity instanceof Player) {
            Player entityhuman = (Player) entity;

            // CraftBukkit start
            int oldFoodLevel = entityhuman.getFoodData().foodLevel;
            org.bukkit.event.entity.FoodLevelChangeEvent event = CraftEventFactory.callFoodLevelChangeEvent(entityhuman, amplifier + 1 + oldFoodLevel);
            if (!event.isCancelled()) {
                entityhuman.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 1.0F);
            }

            ((CraftPlayer) entityhuman.getBukkitEntity()).sendHealthUpdate();
            // CraftBukkit end
        }

    }
}
