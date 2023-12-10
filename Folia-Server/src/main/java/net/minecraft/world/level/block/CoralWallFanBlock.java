package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class CoralWallFanBlock extends BaseCoralWallFanBlock {

    private final Block deadBlock;

    protected CoralWallFanBlock(Block deadCoralBlock, BlockBehaviour.Properties settings) {
        super(settings);
        this.deadBlock = deadCoralBlock;
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        this.tryScheduleDieTick(state, world, pos);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!scanForWater(state, world, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, this.deadBlock.defaultBlockState().setValue(CoralWallFanBlock.WATERLOGGED, false).setValue(CoralWallFanBlock.FACING, state.getValue(CoralWallFanBlock.FACING))).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) ((BlockState) this.deadBlock.defaultBlockState().setValue(CoralWallFanBlock.WATERLOGGED, false)).setValue(CoralWallFanBlock.FACING, (Direction) state.getValue(CoralWallFanBlock.FACING)), 2);
        }

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction.getOpposite() == state.getValue(CoralWallFanBlock.FACING) && !state.canSurvive(world, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if ((Boolean) state.getValue(CoralWallFanBlock.WATERLOGGED)) {
                world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
            }

            this.tryScheduleDieTick(state, world, pos);
            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }
}
