package net.minecraft.world.level.block;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SugarCaneBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    protected SugarCaneBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(SugarCaneBlock.AGE, 0));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SugarCaneBlock.SHAPE;
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true);
        }

    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.isEmptyBlock(pos.above())) {
            int i;

            for (i = 1; world.getBlockState(pos.below(i)).is((Block) this); ++i) {
                ;
            }

            if (i < world.paperConfig().maxGrowthHeight.reeds) { // Paper - Configurable growth height
                int j = (Integer) state.getValue(SugarCaneBlock.AGE);

                int modifier = world.spigotConfig.caneModifier; // Spigot - SPIGOT-7159: Better modifier resolution
                if (j >= 15 || (modifier != 100 && random.nextFloat() < (modifier / (100.0f * 16)))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(world, pos.above(), this.defaultBlockState()); // CraftBukkit
                    world.setBlock(pos, (BlockState) state.setValue(SugarCaneBlock.AGE, 0), 4);
                } else if (modifier == 100 || random.nextFloat() < (modifier / (100.0f * 16))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    world.setBlock(pos, (BlockState) state.setValue(SugarCaneBlock.AGE, j + 1), 4);
                }
            }
        }

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(world, pos)) {
            world.scheduleTick(pos, (Block) this, 1);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockState iblockdata1 = world.getBlockState(pos.below());

        if (iblockdata1.is((Block) this)) {
            return true;
        } else {
            if (iblockdata1.is(BlockTags.DIRT) || iblockdata1.is(BlockTags.SAND)) {
                BlockPos blockposition1 = pos.below();
                Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext()) {
                    Direction enumdirection = (Direction) iterator.next();
                    BlockState iblockdata2 = world.getBlockState(blockposition1.relative(enumdirection));
                    FluidState fluid = world.getFluidState(blockposition1.relative(enumdirection));

                    if (fluid.is(FluidTags.WATER) || iblockdata2.is(Blocks.FROSTED_ICE)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SugarCaneBlock.AGE);
    }
}
