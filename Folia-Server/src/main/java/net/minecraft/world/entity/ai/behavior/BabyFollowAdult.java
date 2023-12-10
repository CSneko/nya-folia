package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
// CraftBukkit end

public class BabyFollowAdult {

    public BabyFollowAdult() {}

    public static OneShot<AgeableMob> create(UniformInt executionRange, float speed) {
        return BabyFollowAdult.create(executionRange, (entityliving) -> {
            return speed;
        });
    }

    public static OneShot<AgeableMob> create(UniformInt executionRange, Function<LivingEntity, Float> speed) {
        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            return behaviorbuilder_b.group(behaviorbuilder_b.present(MemoryModuleType.NEAREST_VISIBLE_ADULT), behaviorbuilder_b.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_b.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_b, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (worldserver, entityageable, i) -> {
                    if (!entityageable.isBaby()) {
                        return false;
                    } else {
                        LivingEntity entityageable1 = (AgeableMob) behaviorbuilder_b.get(memoryaccessor); // CraftBukkit - type

                        if (entityageable.closerThan(entityageable1, (double) (executionRange.getMaxValue() + 1)) && !entityageable.closerThan(entityageable1, (double) executionRange.getMinValue())) {
                            // CraftBukkit start
                            EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(entityageable, entityageable1, EntityTargetEvent.TargetReason.FOLLOW_LEADER);
                            if (event.isCancelled()) {
                                return false;
                            }
                            if (event.getTarget() == null) {
                                memoryaccessor.erase();
                                return true;
                            }
                            entityageable1 = ((CraftLivingEntity) event.getTarget()).getHandle();
                            // CraftBukkit end
                            WalkTarget memorytarget = new WalkTarget(new EntityTracker(entityageable1, false), (Float) speed.apply(entityageable), executionRange.getMinValue() - 1);

                            memoryaccessor1.set(new EntityTracker(entityageable1, true));
                            memoryaccessor2.set(memorytarget);
                            return true;
                        } else {
                            return false;
                        }
                    }
                };
            });
        });
    }
}
