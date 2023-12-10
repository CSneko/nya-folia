package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

public class TryLaySpawnOnWaterNearLand {
    public static BehaviorControl<LivingEntity> create(Block frogSpawn) {
        return BehaviorBuilder.create((context) -> {
            return context.group(context.absent(MemoryModuleType.ATTACK_TARGET), context.present(MemoryModuleType.WALK_TARGET), context.present(MemoryModuleType.IS_PREGNANT)).apply(context, (attackTarget, walkTarget, isPregnant) -> {
                return (world, entity, time) -> {
                    if (!entity.isInWater() && entity.onGround()) {
                        BlockPos blockPos = entity.blockPosition().below();

                        for(Direction direction : Direction.Plane.HORIZONTAL) {
                            BlockPos blockPos2 = blockPos.relative(direction);
                            if (world.getBlockState(blockPos2).getCollisionShape(world, blockPos2).getFaceShape(Direction.UP).isEmpty() && world.getFluidState(blockPos2).is(Fluids.WATER)) {
                                BlockPos blockPos3 = blockPos2.above();
                                if (world.getBlockState(blockPos3).isAir()) {
                                    BlockState blockState = frogSpawn.defaultBlockState();
                                    // Paper start
                                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, blockPos3, blockState)) {
                                        isPregnant.erase(); // forgot pregnant memory
                                        return true;
                                    }
                                    // Paper end
                                    world.setBlock(blockPos3, blockState, 3);
                                    world.gameEvent(GameEvent.BLOCK_PLACE, blockPos3, GameEvent.Context.of(entity, blockState));
                                    world.playSound((Player)null, entity, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                                    isPregnant.erase();
                                    return true;
                                }
                            }
                        }

                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
