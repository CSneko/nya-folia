package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Sets;
import com.mojang.datafixers.kinds.OptionalBox.Mu;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

public class InteractWithDoor {

    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 20;
    private static final double SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN = 3.0D;
    private static final double MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS = 2.0D;

    public InteractWithDoor() {}

    public static BehaviorControl<LivingEntity> create() {
        MutableObject<Node> mutableobject = new MutableObject((Object) null);
        MutableInt mutableint = new MutableInt(0);

        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            return behaviorbuilder_b.group(behaviorbuilder_b.present(MemoryModuleType.PATH), behaviorbuilder_b.registered(MemoryModuleType.DOORS_TO_CLOSE), behaviorbuilder_b.registered(MemoryModuleType.NEAREST_LIVING_ENTITIES)).apply(behaviorbuilder_b, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (worldserver, entityliving, i) -> {
                    Path pathentity = (Path) behaviorbuilder_b.get(memoryaccessor);
                    Optional<Set<GlobalPos>> optional = behaviorbuilder_b.tryGet(memoryaccessor1);

                    if (!pathentity.notStarted() && !pathentity.isDone()) {
                        if (Objects.equals(mutableobject.getValue(), pathentity.getNextNode())) {
                            mutableint.setValue(20);
                        } else if (mutableint.decrementAndGet() > 0) {
                            return false;
                        }

                        mutableobject.setValue(pathentity.getNextNode());
                        Node pathpoint = pathentity.getPreviousNode();
                        Node pathpoint1 = pathentity.getNextNode();
                        BlockPos blockposition = pathpoint.asBlockPos();
                        BlockState iblockdata = worldserver.getBlockState(blockposition);

                        if (iblockdata.is(BlockTags.WOODEN_DOORS, (blockbase_blockdata) -> {
                            return blockbase_blockdata.getBlock() instanceof DoorBlock;
                        })) {
                            DoorBlock blockdoor = (DoorBlock) iblockdata.getBlock();

                            if (!blockdoor.isOpen(iblockdata)) {
                                // CraftBukkit start - entities opening doors
                                org.bukkit.event.entity.EntityInteractEvent event = new org.bukkit.event.entity.EntityInteractEvent(entityliving.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(entityliving.level(), blockposition));
                                entityliving.level().getCraftServer().getPluginManager().callEvent(event);
                                if (event.isCancelled()) {
                                    return false;
                                }
                                // CraftBukkit end
                                blockdoor.setOpen(entityliving, worldserver, iblockdata, blockposition, true);
                            }

                            optional = InteractWithDoor.rememberDoorToClose(memoryaccessor1, optional, worldserver, blockposition);
                        }

                        BlockPos blockposition1 = pathpoint1.asBlockPos();
                        BlockState iblockdata1 = worldserver.getBlockState(blockposition1);

                        if (iblockdata1.is(BlockTags.WOODEN_DOORS, (blockbase_blockdata) -> {
                            return blockbase_blockdata.getBlock() instanceof DoorBlock;
                        })) {
                            DoorBlock blockdoor1 = (DoorBlock) iblockdata1.getBlock();

                            if (!blockdoor1.isOpen(iblockdata1)) {
                                // CraftBukkit start - entities opening doors
                                org.bukkit.event.entity.EntityInteractEvent event = new org.bukkit.event.entity.EntityInteractEvent(entityliving.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(entityliving.level(), blockposition1));
                                entityliving.level().getCraftServer().getPluginManager().callEvent(event);
                                if (event.isCancelled()) {
                                    return false;
                                }
                                // CraftBukkit end
                                blockdoor1.setOpen(entityliving, worldserver, iblockdata1, blockposition1, true);
                                optional = InteractWithDoor.rememberDoorToClose(memoryaccessor1, optional, worldserver, blockposition1);
                            }
                        }

                        optional.ifPresent((set) -> {
                            InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(worldserver, entityliving, pathpoint, pathpoint1, set, behaviorbuilder_b.tryGet(memoryaccessor2));
                        });
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }

    public static void closeDoorsThatIHaveOpenedOrPassedThrough(ServerLevel world, LivingEntity entity, @Nullable Node lastNode, @Nullable Node currentNode, Set<GlobalPos> doors, Optional<List<LivingEntity>> otherMobs) {
        Iterator iterator = doors.iterator();

        while (iterator.hasNext()) {
            GlobalPos globalpos = (GlobalPos) iterator.next();
            BlockPos blockposition = globalpos.pos();

            if ((lastNode == null || !lastNode.asBlockPos().equals(blockposition)) && (currentNode == null || !currentNode.asBlockPos().equals(blockposition))) {
                if (InteractWithDoor.isDoorTooFarAway(world, entity, globalpos)) {
                    iterator.remove();
                } else {
                    BlockState iblockdata = world.getBlockState(blockposition);

                    if (!iblockdata.is(BlockTags.WOODEN_DOORS, (blockbase_blockdata) -> {
                        return blockbase_blockdata.getBlock() instanceof DoorBlock;
                    })) {
                        iterator.remove();
                    } else {
                        DoorBlock blockdoor = (DoorBlock) iblockdata.getBlock();

                        if (!blockdoor.isOpen(iblockdata)) {
                            iterator.remove();
                        } else if (InteractWithDoor.areOtherMobsComingThroughDoor(entity, blockposition, otherMobs)) {
                            iterator.remove();
                        } else {
                            blockdoor.setOpen(entity, world, iblockdata, blockposition, false);
                            iterator.remove();
                        }
                    }
                }
            }
        }

    }

    private static boolean areOtherMobsComingThroughDoor(LivingEntity entity, BlockPos pos, Optional<List<LivingEntity>> otherMobs) {
        return otherMobs.isEmpty() ? false : (otherMobs.get()).stream().filter((entityliving1) -> { // CraftBukkit - decompile error
            return entityliving1.getType() == entity.getType();
        }).filter((entityliving1) -> {
            return pos.closerToCenterThan(entityliving1.position(), 2.0D);
        }).anyMatch((entityliving1) -> {
            return InteractWithDoor.isMobComingThroughDoor(entityliving1.getBrain(), pos);
        });
    }

    private static boolean isMobComingThroughDoor(Brain<?> brain, BlockPos pos) {
        if (!brain.hasMemoryValue(MemoryModuleType.PATH)) {
            return false;
        } else {
            Path pathentity = (Path) brain.getMemory(MemoryModuleType.PATH).get();

            if (pathentity.isDone()) {
                return false;
            } else {
                Node pathpoint = pathentity.getPreviousNode();

                if (pathpoint == null) {
                    return false;
                } else {
                    Node pathpoint1 = pathentity.getNextNode();

                    return pos.equals(pathpoint.asBlockPos()) || pos.equals(pathpoint1.asBlockPos());
                }
            }
        }
    }

    private static boolean isDoorTooFarAway(ServerLevel world, LivingEntity entity, GlobalPos doorPos) {
        return doorPos.dimension() != world.dimension() || !doorPos.pos().closerToCenterThan(entity.position(), 3.0D);
    }

    private static Optional<Set<GlobalPos>> rememberDoorToClose(MemoryAccessor<Mu, Set<GlobalPos>> queryResult, Optional<Set<GlobalPos>> doors, ServerLevel world, BlockPos pos) {
        GlobalPos globalpos = GlobalPos.of(world.dimension(), pos);

        return Optional.of((Set) doors.map((set) -> {
            set.add(globalpos);
            return set;
        }).orElseGet(() -> {
            Set<GlobalPos> set = Sets.newHashSet(new GlobalPos[]{globalpos});

            queryResult.set(set);
            return set;
        }));
    }
}
