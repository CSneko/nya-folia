package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.kinds.K1;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;

public class GoToWantedItem {

    public GoToWantedItem() {}

    public static BehaviorControl<LivingEntity> create(float speed, boolean requiresWalkTarget, int radius) {
        return GoToWantedItem.create((entityliving) -> {
            return true;
        }, speed, requiresWalkTarget, radius);
    }

    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> startCondition, float speed, boolean requiresWalkTarget, int radius) {
        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            BehaviorBuilder<E, ? extends MemoryAccessor<? extends K1, WalkTarget>> behaviorbuilder = requiresWalkTarget ? behaviorbuilder_b.registered(MemoryModuleType.WALK_TARGET) : behaviorbuilder_b.absent(MemoryModuleType.WALK_TARGET);

            return behaviorbuilder_b.group(behaviorbuilder_b.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder, behaviorbuilder_b.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM), behaviorbuilder_b.registered(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS)).apply(behaviorbuilder_b, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (worldserver, entityliving, j) -> {
                    ItemEntity entityitem = (ItemEntity) behaviorbuilder_b.get(memoryaccessor2);

                    if (behaviorbuilder_b.tryGet(memoryaccessor3).isEmpty() && startCondition.test(entityliving) && entityitem.closerThan(entityliving, (double) radius) && entityliving.level().getWorldBorder().isWithinBounds(entityitem.blockPosition())) {
                        // CraftBukkit start
                        if (entityliving instanceof net.minecraft.world.entity.animal.allay.Allay) {
                            org.bukkit.event.entity.EntityTargetEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetEvent(entityliving, entityitem, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY);

                            if (event.isCancelled()) {
                                return false;
                            }
                            if (!(event.getTarget() instanceof org.bukkit.craftbukkit.entity.CraftItem)) { // Paper - only erase allay memory on non-item targets
                                memoryaccessor2.erase();
                                return false; // Paper - only erase allay memory on non-item targets
                            }

                            entityitem = (ItemEntity) ((org.bukkit.craftbukkit.entity.CraftEntity) event.getTarget()).getHandle();
                        }
                        // CraftBukkit end
                        WalkTarget memorytarget = new WalkTarget(new EntityTracker(entityitem, false), speed, 0);

                        memoryaccessor.set(new EntityTracker(entityitem, true));
                        memoryaccessor1.set(memorytarget);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
