package net.minecraft.world.level.block;

import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.VoxelShape;

// CraftBukkit start
import java.util.Collections;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
// CraftBukkit end

public interface CaveVines {

    VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    BooleanProperty BERRIES = BlockStateProperties.BERRIES;

    static InteractionResult use(@Nullable Entity picker, BlockState state, Level world, BlockPos pos) {
        if ((Boolean) state.getValue(CaveVines.BERRIES)) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(picker, pos, (BlockState) state.setValue(CaveVines.BERRIES, false))) {
                return InteractionResult.SUCCESS;
            }

            if (picker instanceof Player) {
                PlayerHarvestBlockEvent event = CraftEventFactory.callPlayerHarvestBlockEvent(world, pos, (Player) picker, net.minecraft.world.InteractionHand.MAIN_HAND, Collections.singletonList(new ItemStack(Items.GLOW_BERRIES, 1)));
                if (event.isCancelled()) {
                    return InteractionResult.SUCCESS; // We need to return a success either way, because making it PASS or FAIL will result in a bug where cancelling while harvesting w/ block in hand places block
                }
                for (org.bukkit.inventory.ItemStack itemStack : event.getItemsHarvested()) {
                    Block.popResource(world, pos, CraftItemStack.asNMSCopy(itemStack));
                }
            } else {
                Block.popResource(world, pos, new ItemStack(Items.GLOW_BERRIES, 1));
            }
            // CraftBukkit end

            float f = Mth.randomBetween(world.random, 0.8F, 1.2F);

            world.playSound((Player) null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, f);
            BlockState iblockdata1 = (BlockState) state.setValue(CaveVines.BERRIES, false);

            world.setBlock(pos, iblockdata1, 2);
            world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(picker, iblockdata1));
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    static boolean hasGlowBerries(BlockState state) {
        return state.hasProperty(CaveVines.BERRIES) && (Boolean) state.getValue(CaveVines.BERRIES);
    }

    static ToIntFunction<BlockState> emission(int luminance) {
        return (iblockdata) -> {
            return (Boolean) iblockdata.getValue(BlockStateProperties.BERRIES) ? luminance : 0;
        };
    }
}
