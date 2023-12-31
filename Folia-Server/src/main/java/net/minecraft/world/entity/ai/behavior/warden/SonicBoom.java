package net.minecraft.world.entity.ai.behavior.warden;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;

public class SonicBoom extends Behavior<Warden> {
    private static final int DISTANCE_XZ = 15;
    private static final int DISTANCE_Y = 20;
    private static final double KNOCKBACK_VERTICAL = 0.5D;
    private static final double KNOCKBACK_HORIZONTAL = 2.5D;
    public static final int COOLDOWN = 40;
    private static final int TICKS_BEFORE_PLAYING_SOUND = Mth.ceil(34.0D);
    private static final int DURATION = Mth.ceil(60.0F);

    public SonicBoom() {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.SONIC_BOOM_COOLDOWN, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, MemoryStatus.REGISTERED, MemoryModuleType.SONIC_BOOM_SOUND_DELAY, MemoryStatus.REGISTERED), DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Warden entity) {
        return entity.closerThan(entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get(), 15.0D, 20.0D);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Warden entity, long time) {
        return true;
    }

    @Override
    protected void start(ServerLevel serverLevel, Warden warden, long l) {
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, (long)DURATION);
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_DELAY, Unit.INSTANCE, (long)TICKS_BEFORE_PLAYING_SOUND);
        serverLevel.broadcastEntityEvent(warden, (byte)62);
        warden.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
    }

    @Override
    protected void tick(ServerLevel world, Warden entity, long time) {
        entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent((target) -> {
            entity.getLookControl().setLookAt(target.position());
        });
        if (!entity.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY) && !entity.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN)) {
            entity.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, Unit.INSTANCE, (long)(DURATION - TICKS_BEFORE_PLAYING_SOUND));
            entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(entity::canTargetEntity).filter((target) -> {
                return entity.closerThan(target, 15.0D, 20.0D);
            }).ifPresent((target) -> {
                Vec3 vec3 = entity.position().add(0.0D, (double)1.6F, 0.0D);
                Vec3 vec32 = target.getEyePosition().subtract(vec3);
                Vec3 vec33 = vec32.normalize();

                for(int i = 1; i < Mth.floor(vec32.length()) + 7; ++i) {
                    Vec3 vec34 = vec3.add(vec33.scale((double)i));
                    world.sendParticles(ParticleTypes.SONIC_BOOM, vec34.x, vec34.y, vec34.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }

                entity.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
                target.hurt(world.damageSources().sonicBoom(entity), 10.0F);
                double d = 0.5D * (1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                double e = 2.5D * (1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                target.push(vec33.x() * e, vec33.y() * d, vec33.z() * e, entity); // Paper
            });
        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Warden warden, long l) {
        setCooldown(warden, 40);
    }

    public static void setCooldown(LivingEntity warden, int cooldown) {
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, (long)cooldown);
    }
}
