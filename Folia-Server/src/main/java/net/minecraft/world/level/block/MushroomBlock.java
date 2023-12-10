package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.TreeType;
// CraftBukkit end

public class MushroomBlock extends BushBlock implements BonemealableBlock {

    protected static final float AABB_OFFSET = 3.0F;
    protected static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;

    public MushroomBlock(BlockBehaviour.Properties settings, ResourceKey<ConfiguredFeature<?, ?>> featureKey) {
        super(settings);
        this.feature = featureKey;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return MushroomBlock.SHAPE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < (world.spigotConfig.mushroomModifier / (100.0f * 25))) { // Spigot - SPIGOT-7159: Better modifier resolution
            int i = 5;
            boolean flag = true;
            Iterator iterator = BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4)).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition1 = (BlockPos) iterator.next();

                if (world.getBlockState(blockposition1).is((Block) this)) {
                    --i;
                    if (i <= 0) {
                        return;
                    }
                }
            }

            BlockPos blockposition2 = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
            final BlockPos sourcePos = pos; // Paper

            for (int j = 0; j < 4; ++j) {
                if (world.isEmptyBlock(blockposition2) && state.canSurvive(world, blockposition2)) {
                    pos = blockposition2;
                }

                blockposition2 = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
            }

            if (world.isEmptyBlock(blockposition2) && state.canSurvive(world, blockposition2)) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, sourcePos, blockposition2, state, 2); // CraftBukkit // Paper
            }
        }

    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.isSolidRender(world, pos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        return iblockdata1.is(BlockTags.MUSHROOM_GROW_BLOCK) ? true : world.getRawBrightness(pos, 0) < 13 && this.mayPlaceOn(iblockdata1, world, blockposition1);
    }

    public boolean growMushroom(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
        Optional<? extends Holder<ConfiguredFeature<?, ?>>> optional = world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(this.feature);

        if (optional.isEmpty()) {
            return false;
        } else {
            world.removeBlock(pos, false);
            SaplingBlock.treeTypeRT.set((this == Blocks.BROWN_MUSHROOM) ? TreeType.BROWN_MUSHROOM : TreeType.RED_MUSHROOM); // CraftBukkit // Paper // Folia - region threading
            if (((ConfiguredFeature) ((Holder) optional.get()).value()).place(world, world.getChunkSource().getGenerator(), random, pos)) {
                return true;
            } else {
                world.setBlock(pos, state, 3);
                return false;
            }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return (double) random.nextFloat() < 0.4D;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        this.growMushroom(world, pos, state, random);
    }
}
