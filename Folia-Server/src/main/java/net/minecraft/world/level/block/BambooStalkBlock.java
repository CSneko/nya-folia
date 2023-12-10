package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BambooStalkBlock extends Block implements BonemealableBlock {

    protected static final float SMALL_LEAVES_AABB_OFFSET = 3.0F;
    protected static final float LARGE_LEAVES_AABB_OFFSET = 5.0F;
    protected static final float COLLISION_AABB_OFFSET = 1.5F;
    protected static final VoxelShape SMALL_SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    protected static final VoxelShape LARGE_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    protected static final VoxelShape COLLISION_SHAPE = Block.box(6.5D, 0.0D, 6.5D, 9.5D, 16.0D, 9.5D);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_1;
    public static final EnumProperty<BambooLeaves> LEAVES = BlockStateProperties.BAMBOO_LEAVES;
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    public static final int MAX_HEIGHT = 16;
    public static final int STAGE_GROWING = 0;
    public static final int STAGE_DONE_GROWING = 1;
    public static final int AGE_THIN_BAMBOO = 0;
    public static final int AGE_THICK_BAMBOO = 1;

    public BambooStalkBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BambooStalkBlock.AGE, 0)).setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE)).setValue(BambooStalkBlock.STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BambooStalkBlock.AGE, BambooStalkBlock.LEAVES, BambooStalkBlock.STAGE);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape = state.getValue(BambooStalkBlock.LEAVES) == BambooLeaves.LARGE ? BambooStalkBlock.LARGE_SHAPE : BambooStalkBlock.SMALL_SHAPE;
        Vec3 vec3d = state.getOffset(world, pos);

        return voxelshape.move(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Vec3 vec3d = state.getOffset(world, pos);

        return BambooStalkBlock.COLLISION_SHAPE.move(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());

        if (!fluid.isEmpty()) {
            return null;
        } else {
            BlockState iblockdata = ctx.getLevel().getBlockState(ctx.getClickedPos().below());

            if (iblockdata.is(BlockTags.BAMBOO_PLANTABLE_ON)) {
                if (iblockdata.is(Blocks.BAMBOO_SAPLING)) {
                    return (BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, 0);
                } else if (iblockdata.is(Blocks.BAMBOO)) {
                    int i = (Integer) iblockdata.getValue(BambooStalkBlock.AGE) > 0 ? 1 : 0;

                    return (BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, i);
                } else {
                    BlockState iblockdata1 = ctx.getLevel().getBlockState(ctx.getClickedPos().above());

                    return iblockdata1.is(Blocks.BAMBOO) ? (BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, (Integer) iblockdata1.getValue(BambooStalkBlock.AGE)) : Blocks.BAMBOO_SAPLING.defaultBlockState();
                }
            } else {
                return null;
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true);
        }

    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(BambooStalkBlock.STAGE) == 0;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(BambooStalkBlock.STAGE) == 0) {
            if (random.nextFloat() < (world.spigotConfig.bambooModifier / (100.0f * 3)) && world.isEmptyBlock(pos.above()) && world.getRawBrightness(pos.above(), 0) >= 9) { // Spigot - SPIGOT-7159: Better modifier resolution
                int i = this.getHeightBelowUpToMax(world, pos) + 1;

                if (i < world.paperConfig().maxGrowthHeight.bamboo.max) { // Paper
                    this.growBamboo(state, world, pos, random, i);
                }
            }

        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return world.getBlockState(pos.below()).is(BlockTags.BAMBOO_PLANTABLE_ON);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(world, pos)) {
            world.scheduleTick(pos, (Block) this, 1);
        }

        if (direction == Direction.UP && neighborState.is(Blocks.BAMBOO) && (Integer) neighborState.getValue(BambooStalkBlock.AGE) > (Integer) state.getValue(BambooStalkBlock.AGE)) {
            world.setBlock(pos, (BlockState) state.cycle(BambooStalkBlock.AGE), 2);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        int i = this.getHeightAboveUpToMax(world, pos);
        int j = this.getHeightBelowUpToMax(world, pos);

        return i + j + 1 < ((Level) world).paperConfig().maxGrowthHeight.bamboo.max && (Integer) world.getBlockState(pos.above(i)).getValue(BambooStalkBlock.STAGE) != 1; // Paper
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        int i = this.getHeightAboveUpToMax(world, pos);
        int j = this.getHeightBelowUpToMax(world, pos);
        int k = i + j + 1;
        int l = 1 + random.nextInt(2);

        for (int i1 = 0; i1 < l; ++i1) {
            BlockPos blockposition1 = pos.above(i);
            BlockState iblockdata1 = world.getBlockState(blockposition1);

            if (k >= world.paperConfig().maxGrowthHeight.bamboo.max || !iblockdata1.is(Blocks.BAMBOO) || (Integer) iblockdata1.getValue(BambooStalkBlock.STAGE) == 1 || !world.isEmptyBlock(blockposition1.above())) { // CraftBukkit - If the BlockSpreadEvent was cancelled, we have no bamboo here // Paper - Configurable cactus bamboo and reed growth heights
                return;
            }

            this.growBamboo(iblockdata1, world, blockposition1, random, k);
            ++i;
            ++k;
        }

    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        return player.getMainHandItem().getItem() instanceof SwordItem ? 1.0F : super.getDestroyProgress(state, player, world, pos);
    }

    protected void growBamboo(BlockState state, Level world, BlockPos pos, RandomSource random, int height) {
        BlockState iblockdata1 = world.getBlockState(pos.below());
        BlockPos blockposition1 = pos.below(2);
        BlockState iblockdata2 = world.getBlockState(blockposition1);
        BambooLeaves blockpropertybamboosize = BambooLeaves.NONE;
        boolean shouldUpdateOthers = false; // CraftBukkit

        if (height >= 1) {
            if (iblockdata1.is(Blocks.BAMBOO) && iblockdata1.getValue(BambooStalkBlock.LEAVES) != BambooLeaves.NONE) {
                if (iblockdata1.is(Blocks.BAMBOO) && iblockdata1.getValue(BambooStalkBlock.LEAVES) != BambooLeaves.NONE) {
                    blockpropertybamboosize = BambooLeaves.LARGE;
                    if (iblockdata2.is(Blocks.BAMBOO)) {
                        // CraftBukkit start - moved down
                        // world.setBlock(blockposition.below(), (IBlockData) iblockdata1.setValue(BlockBamboo.LEAVES, BlockPropertyBambooSize.SMALL), 3);
                        // world.setBlock(blockposition1, (IBlockData) iblockdata2.setValue(BlockBamboo.LEAVES, BlockPropertyBambooSize.NONE), 3);
                        shouldUpdateOthers = true;
                        // CraftBukkit end
                    }
                }
            } else {
                blockpropertybamboosize = BambooLeaves.SMALL;
            }
        }

        int j = (Integer) state.getValue(BambooStalkBlock.AGE) != 1 && !iblockdata2.is(Blocks.BAMBOO) ? 0 : 1;
        int k = (height < world.paperConfig().maxGrowthHeight.bamboo.min || random.nextFloat() >= 0.25F) && height != (world.paperConfig().maxGrowthHeight.bamboo.max - 1) ? 0 : 1; // Paper

        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, pos, pos.above(), (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, j)).setValue(BambooStalkBlock.LEAVES, blockpropertybamboosize)).setValue(BambooStalkBlock.STAGE, k), 3)) {
            if (shouldUpdateOthers) {
                world.setBlock(pos.below(), (BlockState) iblockdata1.setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL), 3);
                world.setBlock(blockposition1, (BlockState) iblockdata2.setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE), 3);
            }
        }
        // CraftBukkit end
    }

    protected int getHeightAboveUpToMax(BlockGetter world, BlockPos pos) {
        int i;

        for (i = 0; i < ((Level) world).paperConfig().maxGrowthHeight.bamboo.max && world.getBlockState(pos.above(i + 1)).is(Blocks.BAMBOO); ++i) { // Paper
            ;
        }

        return i;
    }

    protected int getHeightBelowUpToMax(BlockGetter world, BlockPos pos) {
        int i;

        for (i = 0; i < ((Level) world).paperConfig().maxGrowthHeight.bamboo.max && world.getBlockState(pos.below(i + 1)).is(Blocks.BAMBOO); ++i) { // Paper
            ;
        }

        return i;
    }
}
