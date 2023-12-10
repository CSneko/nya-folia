package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class HarvestFarmland extends Behavior<Villager> {

    private static final int HARVEST_DURATION = 200;
    public static final float SPEED_MODIFIER = 0.5F;
    @Nullable
    private BlockPos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();

    public HarvestFarmland() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    protected boolean checkExtraStartConditions(ServerLevel world, Villager entity) {
        if (!world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        } else if (entity.getVillagerData().getProfession() != VillagerProfession.FARMER) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = entity.blockPosition().mutable();

            this.validFarmlandAroundVillager.clear();

            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    for (int k = -1; k <= 1; ++k) {
                        blockposition_mutableblockposition.set(entity.getX() + (double) i, entity.getY() + (double) j, entity.getZ() + (double) k);
                        if (this.validPos(blockposition_mutableblockposition, world)) {
                            this.validFarmlandAroundVillager.add(new BlockPos(blockposition_mutableblockposition));
                        }
                    }
                }
            }

            this.aboveFarmlandPos = this.getValidFarmland(world);
            return this.aboveFarmlandPos != null;
        }
    }

    @Nullable
    private BlockPos getValidFarmland(ServerLevel world) {
        return this.validFarmlandAroundVillager.isEmpty() ? null : (BlockPos) this.validFarmlandAroundVillager.get(world.getRandom().nextInt(this.validFarmlandAroundVillager.size()));
    }

    private boolean validPos(BlockPos pos, ServerLevel world) {
        BlockState iblockdata = world.getBlockState(pos);
        Block block = iblockdata.getBlock();
        Block block1 = world.getBlockState(pos.below()).getBlock();

        return block instanceof CropBlock && ((CropBlock) block).isMaxAge(iblockdata) || iblockdata.isAir() && block1 instanceof FarmBlock;
    }

    protected void start(ServerLevel worldserver, Villager entityvillager, long i) {
        if (i > this.nextOkStartTime && this.aboveFarmlandPos != null) {
            entityvillager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new BlockPosTracker(this.aboveFarmlandPos))); // CraftBukkit - decompile error
            entityvillager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1))); // CraftBukkit - decompile error
        }

    }

    protected void stop(ServerLevel world, Villager entity, long time) {
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.timeWorkedSoFar = 0;
        this.nextOkStartTime = time + 40L;
    }

    protected void tick(ServerLevel world, Villager entity, long time) {
        if (this.aboveFarmlandPos == null || this.aboveFarmlandPos.closerToCenterThan(entity.position(), 1.0D)) {
            if (this.aboveFarmlandPos != null && time > this.nextOkStartTime) {
                BlockState iblockdata = world.getBlockState(this.aboveFarmlandPos);
                Block block = iblockdata.getBlock();
                Block block1 = world.getBlockState(this.aboveFarmlandPos.below()).getBlock();

                if (block instanceof CropBlock && ((CropBlock) block).isMaxAge(iblockdata)) {
                    if (CraftEventFactory.callEntityChangeBlockEvent(entity, this.aboveFarmlandPos, iblockdata.getFluidState().createLegacyBlock())) { // CraftBukkit // Paper - fix wrong block state
                    world.destroyBlock(this.aboveFarmlandPos, true, entity);
                    } // CraftBukkit
                }

                if (iblockdata.isAir() && block1 instanceof FarmBlock && entity.hasFarmSeeds()) {
                    SimpleContainer inventorysubcontainer = entity.getInventory();

                    for (int j = 0; j < inventorysubcontainer.getContainerSize(); ++j) {
                        ItemStack itemstack = inventorysubcontainer.getItem(j);
                        boolean flag = false;

                        if (!itemstack.isEmpty() && itemstack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) {
                            Item item = itemstack.getItem();

                            if (item instanceof BlockItem) {
                                BlockItem itemblock = (BlockItem) item;
                                BlockState iblockdata1 = itemblock.getBlock().defaultBlockState();

                                if (CraftEventFactory.callEntityChangeBlockEvent(entity, this.aboveFarmlandPos, iblockdata1)) { // CraftBukkit
                                world.setBlockAndUpdate(this.aboveFarmlandPos, iblockdata1);
                                world.gameEvent(GameEvent.BLOCK_PLACE, this.aboveFarmlandPos, GameEvent.Context.of(entity, iblockdata1));
                                flag = true;
                                } // CraftBukkit
                            }
                        }

                        if (flag) {
                            world.playSound((Player) null, (double) this.aboveFarmlandPos.getX(), (double) this.aboveFarmlandPos.getY(), (double) this.aboveFarmlandPos.getZ(), SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                            itemstack.shrink(1);
                            if (itemstack.isEmpty()) {
                                inventorysubcontainer.setItem(j, ItemStack.EMPTY);
                            }
                            break;
                        }
                    }
                }

                if (block instanceof CropBlock && !((CropBlock) block).isMaxAge(iblockdata)) {
                    this.validFarmlandAroundVillager.remove(this.aboveFarmlandPos);
                    this.aboveFarmlandPos = this.getValidFarmland(world);
                    if (this.aboveFarmlandPos != null) {
                        this.nextOkStartTime = time + 20L;
                        entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), 0.5F, 1))); // CraftBukkit - decompile error
                        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, (new BlockPosTracker(this.aboveFarmlandPos))); // CraftBukkit - decompile error
                    }
                }
            }

            ++this.timeWorkedSoFar;
        }
    }

    protected boolean canStillUse(ServerLevel worldserver, Villager entityvillager, long i) {
        return this.timeWorkedSoFar < 200;
    }
}
