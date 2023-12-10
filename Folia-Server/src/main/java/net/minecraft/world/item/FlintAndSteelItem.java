package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

public class FlintAndSteelItem extends Item {

    public FlintAndSteelItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player entityhuman = context.getPlayer();
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        if (!CampfireBlock.canLight(iblockdata) && !CandleBlock.canLight(iblockdata) && !CandleCakeBlock.canLight(iblockdata)) {
            BlockPos blockposition1 = blockposition.relative(context.getClickedFace());

            if (BaseFireBlock.canBePlacedAt(world, blockposition1, context.getHorizontalDirection())) {
                // CraftBukkit start - Store the clicked block
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition1, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, entityhuman).isCancelled()) {
                    context.getItemInHand().hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                        entityhuman1.broadcastBreakEvent(context.getHand());
                    });
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                world.playSound(entityhuman, blockposition1, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.4F + 0.8F);
                BlockState iblockdata1 = BaseFireBlock.getState(world, blockposition1);

                world.setBlock(blockposition1, iblockdata1, 11);
                world.gameEvent((Entity) entityhuman, GameEvent.BLOCK_PLACE, blockposition);
                ItemStack itemstack = context.getItemInHand();

                if (entityhuman instanceof ServerPlayer) {
                    CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) entityhuman, blockposition1, itemstack);
                    itemstack.hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                        entityhuman1.broadcastBreakEvent(context.getHand());
                    });
                }

                return InteractionResult.sidedSuccess(world.isClientSide());
            } else {
                return InteractionResult.FAIL;
            }
        } else {
            // CraftBukkit start - Store the clicked block
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, entityhuman).isCancelled()) {
                context.getItemInHand().hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(context.getHand());
                });
                return InteractionResult.PASS;
            }
            // CraftBukkit end
            world.playSound(entityhuman, blockposition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.4F + 0.8F);
            world.setBlock(blockposition, (BlockState) iblockdata.setValue(BlockStateProperties.LIT, true), 11);
            world.gameEvent((Entity) entityhuman, GameEvent.BLOCK_CHANGE, blockposition);
            if (entityhuman != null) {
                context.getItemInHand().hurtAndBreak(1, entityhuman, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(context.getHand());
                });
            }

            return InteractionResult.sidedSuccess(world.isClientSide());
        }
    }
}
