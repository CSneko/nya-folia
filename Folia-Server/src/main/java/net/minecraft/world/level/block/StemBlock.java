package net.minecraft.world.level.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class StemBlock extends BushBlock implements BonemealableBlock {

    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    protected static final float AABB_OFFSET = 1.0F;
    protected static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{Block.box(7.0D, 0.0D, 7.0D, 9.0D, 2.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 4.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 6.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 8.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 10.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 12.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 14.0D, 9.0D), Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D)};
    private final StemGrownBlock fruit;
    private final Supplier<Item> seedSupplier;

    protected StemBlock(StemGrownBlock gourdBlock, Supplier<Item> pickBlockItem, BlockBehaviour.Properties settings) {
        super(settings);
        this.fruit = gourdBlock;
        this.seedSupplier = pickBlockItem;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(StemBlock.AGE, 0));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return StemBlock.SHAPE_BY_AGE[(Integer) state.getValue(StemBlock.AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(Blocks.FARMLAND);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getRawBrightness(pos, 0) >= 9) {
            float f = CropBlock.getGrowthSpeed(this, world, pos);

            if (random.nextFloat() < ((this == Blocks.PUMPKIN_STEM ? world.spigotConfig.pumpkinModifier : world.spigotConfig.melonModifier) / (100.0f * (Math.floor((25.0F / f) + 1))))) { // Spigot - SPIGOT-7159: Better modifier resolution
                int i = (Integer) state.getValue(StemBlock.AGE);

                if (i < 7) {
                    state = (BlockState) state.setValue(StemBlock.AGE, i + 1);
                    CraftEventFactory.handleBlockGrowEvent(world, pos, state, 2); // CraftBukkit
                } else {
                    Direction enumdirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    BlockPos blockposition1 = pos.relative(enumdirection);
                    BlockState iblockdata1 = world.getBlockState(blockposition1.below());

                    if (world.getBlockState(blockposition1).isAir() && (iblockdata1.is(Blocks.FARMLAND) || iblockdata1.is(BlockTags.DIRT))) {
                        // CraftBukkit start
                        if (!CraftEventFactory.handleBlockGrowEvent(world, blockposition1, this.fruit.defaultBlockState())) {
                            return;
                        }
                        // CraftBukkit end
                        world.setBlockAndUpdate(pos, (BlockState) this.fruit.getAttachedStem().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, enumdirection));
                    }
                }
            }

        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return new ItemStack((ItemLike) this.seedSupplier.get());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return (Integer) state.getValue(StemBlock.AGE) != 7;
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        int i = Math.min(7, (Integer) state.getValue(StemBlock.AGE) + Mth.nextInt(world.random, 2, 5));
        BlockState iblockdata1 = (BlockState) state.setValue(StemBlock.AGE, i);

        CraftEventFactory.handleBlockGrowEvent(world, pos, iblockdata1, 2); // CraftBukkit
        if (i == 7) {
            iblockdata1.randomTick(world, pos, world.random);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StemBlock.AGE);
    }

    public StemGrownBlock getFruit() {
        return this.fruit;
    }
}
