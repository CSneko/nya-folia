package net.minecraft.world.item.enchantment;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public class FrostWalkerEnchantment extends Enchantment {

    public FrostWalkerEnchantment(Enchantment.Rarity weight, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentCategory.ARMOR_FEET, slotTypes);
    }

    @Override
    public int getMinCost(int level) {
        return level * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 15;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    public static void onEntityMoved(LivingEntity entity, Level world, BlockPos blockPos, int level) {
        if (entity.onGround()) {
            BlockState iblockdata = Blocks.FROSTED_ICE.defaultBlockState();
            int j = Math.min(16, 2 + level);
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
            Iterator iterator = BlockPos.betweenClosed(blockPos.offset(-j, -1, -j), blockPos.offset(j, -1, j)).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition1 = (BlockPos) iterator.next();

                if (blockposition1.closerToCenterThan(entity.position(), (double) j)) {
                    blockposition_mutableblockposition.set(blockposition1.getX(), blockposition1.getY() + 1, blockposition1.getZ());
                    BlockState iblockdata1 = world.getBlockState(blockposition_mutableblockposition);

                    if (iblockdata1.isAir()) {
                        BlockState iblockdata2 = world.getBlockState(blockposition1);

                        if (iblockdata2 == FrostedIceBlock.meltsInto() && iblockdata.canSurvive(world, blockposition1) && world.isUnobstructed(iblockdata, blockposition1, CollisionContext.empty())) {
                            // CraftBukkit Start - Call EntityBlockFormEvent for Frost Walker
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition1, iblockdata, entity)) {
                                world.scheduleTick(blockposition1, Blocks.FROSTED_ICE, Mth.nextInt(entity.getRandom(), 60, 120));
                            }
                            // CraftBukkit End
                        }
                    }
                }
            }

        }
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && other != Enchantments.DEPTH_STRIDER;
    }
}
