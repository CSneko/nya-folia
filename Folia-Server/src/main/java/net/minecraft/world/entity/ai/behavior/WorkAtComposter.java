package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class WorkAtComposter extends WorkAtPoi {
    private static final List<Item> COMPOSTABLE_ITEMS = ImmutableList.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);

    @Override
    protected void useWorkstation(ServerLevel world, Villager entity) {
        Optional<GlobalPos> optional = entity.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (!optional.isEmpty()) {
            GlobalPos globalPos = optional.get();
            BlockState blockState = world.getBlockState(globalPos.pos());
            if (blockState.is(Blocks.COMPOSTER)) {
                this.makeBread(entity);
                this.compostItems(world, entity, globalPos, blockState);
            }

        }
    }

    private void compostItems(ServerLevel world, Villager entity, GlobalPos pos, BlockState composterState) {
        BlockPos blockPos = pos.pos();
        if (composterState.getValue(ComposterBlock.LEVEL) == 8) {
            composterState = ComposterBlock.extractProduce(entity, composterState, world, blockPos);
        }

        int i = 20;
        int j = 10;
        int[] is = new int[COMPOSTABLE_ITEMS.size()];
        SimpleContainer simpleContainer = entity.getInventory();
        int k = simpleContainer.getContainerSize();
        BlockState blockState = composterState;

        for(int l = k - 1; l >= 0 && i > 0; --l) {
            ItemStack itemStack = simpleContainer.getItem(l);
            int m = COMPOSTABLE_ITEMS.indexOf(itemStack.getItem());
            if (m != -1) {
                int n = itemStack.getCount();
                int o = is[m] + n;
                is[m] = o;
                int p = Math.min(Math.min(o - 10, i), n);
                if (p > 0) {
                    i -= p;

                    for(int q = 0; q < p; ++q) {
                        blockState = ComposterBlock.insertItem(entity, blockState, world, itemStack, blockPos);
                        if (blockState.getValue(ComposterBlock.LEVEL) == 7) {
                            this.spawnComposterFillEffects(world, composterState, blockPos, blockState);
                            return;
                        }
                    }
                }
            }
        }

        this.spawnComposterFillEffects(world, composterState, blockPos, blockState);
    }

    private void spawnComposterFillEffects(ServerLevel world, BlockState oldState, BlockPos pos, BlockState newState) {
        world.levelEvent(1500, pos, newState != oldState ? 1 : 0);
    }

    private void makeBread(Villager entity) {
        SimpleContainer simpleContainer = entity.getInventory();
        if (simpleContainer.countItem(Items.BREAD) <= 36) {
            int i = simpleContainer.countItem(Items.WHEAT);
            int j = 3;
            int k = 3;
            int l = Math.min(3, i / 3);
            if (l != 0) {
                int m = l * 3;
                simpleContainer.removeItemType(Items.WHEAT, m);
                ItemStack itemStack = simpleContainer.addItem(new ItemStack(Items.BREAD, l));
                if (!itemStack.isEmpty()) {
                    entity.forceDrops = true; // Paper
                    entity.spawnAtLocation(itemStack, 0.5F);
                    entity.forceDrops = false; // Paper
                }

            }
        }
    }
}
