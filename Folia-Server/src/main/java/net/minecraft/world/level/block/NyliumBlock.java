package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.NetherFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.lighting.LightEngine;

public class NyliumBlock extends Block implements BonemealableBlock {

    protected NyliumBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    private static boolean canBeNylium(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.above();
        BlockState iblockdata1 = world.getBlockState(blockposition1);
        int i = LightEngine.getLightBlockInto(world, state, pos, iblockdata1, blockposition1, Direction.UP, iblockdata1.getLightBlock(world, blockposition1));

        return i < world.getMaxLightLevel();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!NyliumBlock.canBeNylium(state, world, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, Blocks.NETHERRACK.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
        }

    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return world.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        BlockState iblockdata1 = world.getBlockState(pos);
        BlockPos blockposition1 = pos.above();
        ChunkGenerator chunkgenerator = world.getChunkSource().getGenerator();
        Registry<ConfiguredFeature<?, ?>> iregistry = world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);

        if (iblockdata1.is(Blocks.CRIMSON_NYLIUM)) {
            this.place(iregistry, NetherFeatures.CRIMSON_FOREST_VEGETATION_BONEMEAL, world, chunkgenerator, random, blockposition1);
        } else if (iblockdata1.is(Blocks.WARPED_NYLIUM)) {
            this.place(iregistry, NetherFeatures.WARPED_FOREST_VEGETATION_BONEMEAL, world, chunkgenerator, random, blockposition1);
            this.place(iregistry, NetherFeatures.NETHER_SPROUTS_BONEMEAL, world, chunkgenerator, random, blockposition1);
            if (random.nextInt(8) == 0) {
                this.place(iregistry, NetherFeatures.TWISTING_VINES_BONEMEAL, world, chunkgenerator, random, blockposition1);
            }
        }

    }

    private void place(Registry<ConfiguredFeature<?, ?>> registry, ResourceKey<ConfiguredFeature<?, ?>> key, ServerLevel world, ChunkGenerator chunkGenerator, RandomSource random, BlockPos pos) {
        registry.getHolder(key).ifPresent((holder_c) -> {
            ((ConfiguredFeature) holder_c.value()).place(world, chunkGenerator, random, pos);
        });
    }
}
