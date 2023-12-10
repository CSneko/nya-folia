package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class StartAttacking {

    public StartAttacking() {}

    public static <E extends Mob> BehaviorControl<E> create(Function<E, Optional<? extends LivingEntity>> targetGetter) {
        return StartAttacking.create((entityinsentient) -> {
            return true;
        }, targetGetter);
    }

    public static <E extends Mob> BehaviorControl<E> create(Predicate<E> startCondition, Function<E, Optional<? extends LivingEntity>> targetGetter) {
        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            return behaviorbuilder_b.group(behaviorbuilder_b.absent(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_b.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)).apply(behaviorbuilder_b, (memoryaccessor, memoryaccessor1) -> {
                return (worldserver, entityinsentient, i) -> {
                    if (!startCondition.test(entityinsentient)) {
                        return false;
                    } else {
                        Optional<? extends LivingEntity> optional = (Optional) targetGetter.apply(entityinsentient);

                        if (optional.isEmpty()) {
                            return false;
                        } else {
                            LivingEntity entityliving = (LivingEntity) optional.get();

                            if (!entityinsentient.canAttack(entityliving)) {
                                return false;
                            } else {
                                // CraftBukkit start
                                EntityTargetEvent event = CraftEventFactory.callEntityTargetLivingEvent(entityinsentient, entityliving, (entityliving instanceof ServerPlayer) ? EntityTargetEvent.TargetReason.CLOSEST_PLAYER : EntityTargetEvent.TargetReason.CLOSEST_ENTITY);
                                if (event.isCancelled()) {
                                    return false;
                                }
                                if (event.getTarget() == null) {
                                    memoryaccessor.erase();
                                    return true;
                                }
                                entityliving = ((CraftLivingEntity) event.getTarget()).getHandle();
                                // CraftBukkit end
                                memoryaccessor.set(entityliving);
                                memoryaccessor1.erase();
                                return true;
                            }
                        }
                    }
                };
            });
        });
    }
}
