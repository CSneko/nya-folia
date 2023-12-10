package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SignItem extends StandingAndWallBlockItem {

    public static BlockPos openSign; // CraftBukkit

    public SignItem(Item.Properties settings, Block standingBlock, Block wallBlock) {
        super(standingBlock, wallBlock, settings, Direction.DOWN);
    }

    public SignItem(Item.Properties settings, Block standingBlock, Block wallBlock, Direction verticalAttachmentDirection) {
        super(standingBlock, wallBlock, settings, verticalAttachmentDirection);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean flag = super.updateCustomBlockEntityTag(pos, world, player, stack, state);

        if (!world.isClientSide && !flag && player != null) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof SignBlockEntity) {
                SignBlockEntity tileentitysign = (SignBlockEntity) tileentity;
                Block block = world.getBlockState(pos).getBlock();

                if (block instanceof SignBlock) {
                    SignBlock blocksign = (SignBlock) block;

                    // CraftBukkit start - SPIGOT-4678
                    // blocksign.openTextEdit(entityhuman, tileentitysign, true);
                    SignItem.openSign = pos;
                    // CraftBukkit end
                }
            }
        }

        return flag;
    }
}
