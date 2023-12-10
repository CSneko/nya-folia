package net.minecraft.world.entity.monster.hoglin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public interface HoglinBase {
    int ATTACK_ANIMATION_DURATION = 10;

    int getAttackAnimationRemainingTicks();

    static boolean hurtAndThrowTarget(LivingEntity attacker, LivingEntity target) {
        float f = (float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float g;
        if (!attacker.isBaby() && (int)f > 0) {
            g = f / 2.0F + (float)attacker.level().random.nextInt((int)f);
        } else {
            g = f;
        }

        boolean bl = target.hurt(attacker.damageSources().mobAttack(attacker), g);
        if (bl) {
            attacker.doEnchantDamageEffects(attacker, target);
            if (!attacker.isBaby()) {
                throwTarget(attacker, target);
            }
        }

        return bl;
    }

    static void throwTarget(LivingEntity attacker, LivingEntity target) {
        double d = attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        double e = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double f = d - e;
        if (!(f <= 0.0D)) {
            double g = target.getX() - attacker.getX();
            double h = target.getZ() - attacker.getZ();
            float i = (float)(attacker.level().random.nextInt(21) - 10);
            double j = f * (double)(attacker.level().random.nextFloat() * 0.5F + 0.2F);
            Vec3 vec3 = (new Vec3(g, 0.0D, h)).normalize().scale(j).yRot(i);
            double k = f * (double)attacker.level().random.nextFloat() * 0.5D;
            target.push(vec3.x, k, vec3.z, attacker); // Paper
            target.hurtMarked = true;
        }
    }
}
