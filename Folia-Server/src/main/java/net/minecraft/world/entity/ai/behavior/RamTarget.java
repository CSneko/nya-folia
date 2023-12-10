package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class RamTarget extends Behavior<Goat> {
    public static final int TIME_OUT_DURATION = 200;
    public static final float RAM_SPEED_FORCE_FACTOR = 1.65F;
    private final Function<Goat, UniformInt> getTimeBetweenRams;
    private final TargetingConditions ramTargeting;
    private final float speed;
    private final ToDoubleFunction<Goat> getKnockbackForce;
    private Vec3 ramDirection;
    private final Function<Goat, SoundEvent> getImpactSound;
    private final Function<Goat, SoundEvent> getHornBreakSound;

    public RamTarget(Function<Goat, UniformInt> cooldownRangeFactory, TargetingConditions targetPredicate, float speed, ToDoubleFunction<Goat> strengthMultiplierFactory, Function<Goat, SoundEvent> impactSoundFactory, Function<Goat, SoundEvent> hornBreakSoundFactory) {
        super(ImmutableMap.of(MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_PRESENT), 200);
        this.getTimeBetweenRams = cooldownRangeFactory;
        this.ramTargeting = targetPredicate;
        this.speed = speed;
        this.getKnockbackForce = strengthMultiplierFactory;
        this.getImpactSound = impactSoundFactory;
        this.getHornBreakSound = hornBreakSoundFactory;
        this.ramDirection = Vec3.ZERO;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Goat entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Goat entity, long time) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET);
    }

    @Override
    protected void start(ServerLevel serverLevel, Goat goat, long l) {
        BlockPos blockPos = goat.blockPosition();
        Brain<?> brain = goat.getBrain();
        Vec3 vec3 = brain.getMemory(MemoryModuleType.RAM_TARGET).get();
        this.ramDirection = (new Vec3((double)blockPos.getX() - vec3.x(), 0.0D, (double)blockPos.getZ() - vec3.z())).normalize();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(vec3, this.speed, 0));
    }

    @Override
    protected void tick(ServerLevel world, Goat entity, long time) {
        List<LivingEntity> list = world.getNearbyEntities(LivingEntity.class, this.ramTargeting, entity, entity.getBoundingBox());
        Brain<?> brain = entity.getBrain();
        if (!list.isEmpty()) {
            LivingEntity livingEntity = list.get(0);
            livingEntity.hurt(world.damageSources().noAggroMobAttack(entity), (float)entity.getAttributeValue(Attributes.ATTACK_DAMAGE));
            int i = entity.hasEffect(MobEffects.MOVEMENT_SPEED) ? entity.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() + 1 : 0;
            int j = entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() + 1 : 0;
            float f = 0.25F * (float)(i - j);
            float g = Mth.clamp(entity.getSpeed() * 1.65F, 0.2F, 3.0F) + f;
            float h = livingEntity.isDamageSourceBlocked(world.damageSources().mobAttack(entity)) ? 0.5F : 1.0F;
            livingEntity.knockback((double)(h * g) * this.getKnockbackForce.applyAsDouble(entity), this.ramDirection.x(), this.ramDirection.z(), entity); // Paper
            this.finishRam(world, entity);
            world.playSound((Player)null, entity, this.getImpactSound.apply(entity), SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (this.hasRammedHornBreakingBlock(world, entity)) {
            world.playSound((Player)null, entity, this.getImpactSound.apply(entity), SoundSource.NEUTRAL, 1.0F, 1.0F);
            boolean bl = entity.dropHorn();
            if (bl) {
                world.playSound((Player)null, entity, this.getHornBreakSound.apply(entity), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }

            this.finishRam(world, entity);
        } else {
            Optional<WalkTarget> optional = brain.getMemory(MemoryModuleType.WALK_TARGET);
            Optional<Vec3> optional2 = brain.getMemory(MemoryModuleType.RAM_TARGET);
            boolean bl2 = optional.isEmpty() || optional2.isEmpty() || optional.get().getTarget().currentPosition().closerThan(optional2.get(), 0.25D);
            if (bl2) {
                this.finishRam(world, entity);
            }
        }

    }

    private boolean hasRammedHornBreakingBlock(ServerLevel world, Goat goat) {
        Vec3 vec3 = goat.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
        BlockPos blockPos = BlockPos.containing(goat.position().add(vec3));
        return world.getBlockState(blockPos).is(BlockTags.SNAPS_GOAT_HORN) || world.getBlockState(blockPos.above()).is(BlockTags.SNAPS_GOAT_HORN);
    }

    protected void finishRam(ServerLevel world, Goat goat) {
        world.broadcastEntityEvent(goat, (byte)59);
        goat.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, this.getTimeBetweenRams.apply(goat).sample(world.random));
        goat.getBrain().eraseMemory(MemoryModuleType.RAM_TARGET);
    }
}
