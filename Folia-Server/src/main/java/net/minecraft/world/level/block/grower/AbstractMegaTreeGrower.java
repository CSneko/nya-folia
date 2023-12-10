package net.minecraft.world.level.block.grower;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public abstract class AbstractMegaTreeGrower extends AbstractTreeGrower {

    public AbstractMegaTreeGrower() {}

    @Override
    public boolean growTree(ServerLevel world, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, RandomSource random) {
        for (int i = 0; i >= -1; --i) {
            for (int j = 0; j >= -1; --j) {
                if (AbstractMegaTreeGrower.isTwoByTwoSapling(state, world, pos, i, j)) {
                    return this.placeMega(world, chunkGenerator, pos, state, random, i, j);
                }
            }
        }

        return super.growTree(world, chunkGenerator, pos, state, random);
    }

    @Nullable
    protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random);

    public boolean placeMega(ServerLevel world, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, RandomSource random, int x, int z) {
        ResourceKey<ConfiguredFeature<?, ?>> resourcekey = this.getConfiguredMegaFeature(random);

        if (resourcekey == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder = (Holder) world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(resourcekey).orElse(null); // CraftBukkit - decompile error

            if (holder == null) {
                return false;
            } else {
                this.setTreeType(holder); // CraftBukkit
                ConfiguredFeature<?, ?> worldgenfeatureconfigured = (ConfiguredFeature) holder.value();
                BlockState iblockdata1 = Blocks.AIR.defaultBlockState();

                world.setBlock(pos.offset(x, 0, z), iblockdata1, 4);
                world.setBlock(pos.offset(x + 1, 0, z), iblockdata1, 4);
                world.setBlock(pos.offset(x, 0, z + 1), iblockdata1, 4);
                world.setBlock(pos.offset(x + 1, 0, z + 1), iblockdata1, 4);
                if (worldgenfeatureconfigured.place(world, chunkGenerator, random, pos.offset(x, 0, z))) {
                    return true;
                } else {
                    world.setBlock(pos.offset(x, 0, z), state, 4);
                    world.setBlock(pos.offset(x + 1, 0, z), state, 4);
                    world.setBlock(pos.offset(x, 0, z + 1), state, 4);
                    world.setBlock(pos.offset(x + 1, 0, z + 1), state, 4);
                    return false;
                }
            }
        }
    }

    public static boolean isTwoByTwoSapling(BlockState state, BlockGetter world, BlockPos pos, int x, int z) {
        Block block = state.getBlock();

        return world.getBlockState(pos.offset(x, 0, z)).is(block) && world.getBlockState(pos.offset(x + 1, 0, z)).is(block) && world.getBlockState(pos.offset(x, 0, z + 1)).is(block) && world.getBlockState(pos.offset(x + 1, 0, z + 1)).is(block);
    }
}
