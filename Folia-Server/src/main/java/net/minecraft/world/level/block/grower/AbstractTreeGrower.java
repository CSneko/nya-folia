package net.minecraft.world.level.block.grower;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
// CraftBukkit start
import net.minecraft.data.worldgen.features.TreeFeatures;
import org.bukkit.TreeType;
// CraftBukkit end

public abstract class AbstractTreeGrower {

    public AbstractTreeGrower() {}

    @Nullable
    protected abstract ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean bees);

    public boolean growTree(ServerLevel world, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> resourcekey = this.getConfiguredFeature(random, this.hasFlowers(world, pos));

        if (resourcekey == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder = (Holder) world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(resourcekey).orElse(null); // CraftBukkit - decompile error

            if (holder == null) {
                return false;
            } else {
                this.setTreeType(holder); // CraftBukkit
                ConfiguredFeature<?, ?> worldgenfeatureconfigured = (ConfiguredFeature) holder.value();
                BlockState iblockdata1 = world.getFluidState(pos).createLegacyBlock();

                world.setBlock(pos, iblockdata1, 4);
                if (worldgenfeatureconfigured.place(world, chunkGenerator, random, pos)) {
                    if (world.getBlockState(pos) == iblockdata1) {
                        world.sendBlockUpdated(pos, state, iblockdata1, 2);
                    }

                    return true;
                } else {
                    world.setBlock(pos, state, 4);
                    return false;
                }
            }
        }
    }

    private boolean hasFlowers(LevelAccessor world, BlockPos pos) {
        Iterator iterator = BlockPos.MutableBlockPos.betweenClosed(pos.below().north(2).west(2), pos.above().south(2).east(2)).iterator();

        BlockPos blockposition1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            blockposition1 = (BlockPos) iterator.next();
        } while (!world.getBlockState(blockposition1).is(BlockTags.FLOWERS));

        return true;
    }

    // CraftBukkit start
    protected void setTreeType(Holder<ConfiguredFeature<?, ?>> holder) {
        ResourceKey<ConfiguredFeature<?, ?>> worldgentreeabstract = holder.unwrapKey().get();
        TreeType treeType; // Folia - region threading
        if (worldgentreeabstract == TreeFeatures.OAK || worldgentreeabstract == TreeFeatures.OAK_BEES_005) {
            treeType = TreeType.TREE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.HUGE_RED_MUSHROOM) {
            treeType = TreeType.RED_MUSHROOM; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.HUGE_BROWN_MUSHROOM) {
            treeType = TreeType.BROWN_MUSHROOM; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.JUNGLE_TREE) {
            treeType = TreeType.COCOA_TREE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.JUNGLE_TREE_NO_VINE) {
            treeType = TreeType.SMALL_JUNGLE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.PINE) {
            treeType = TreeType.TALL_REDWOOD; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.SPRUCE) {
            treeType = TreeType.REDWOOD; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.ACACIA) {
            treeType = TreeType.ACACIA; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.BIRCH || worldgentreeabstract == TreeFeatures.BIRCH_BEES_005) {
            treeType = TreeType.BIRCH; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.SUPER_BIRCH_BEES_0002) {
            treeType = TreeType.TALL_BIRCH; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.SWAMP_OAK) {
            treeType = TreeType.SWAMP; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.FANCY_OAK || worldgentreeabstract == TreeFeatures.FANCY_OAK_BEES_005) {
            treeType = TreeType.BIG_TREE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.JUNGLE_BUSH) {
            treeType = TreeType.JUNGLE_BUSH; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.DARK_OAK) {
            treeType = TreeType.DARK_OAK; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.MEGA_SPRUCE) {
            treeType = TreeType.MEGA_REDWOOD; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.MEGA_PINE) {
            treeType = TreeType.MEGA_REDWOOD; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.MEGA_JUNGLE_TREE) {
            treeType = TreeType.JUNGLE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.AZALEA_TREE) {
            treeType = TreeType.AZALEA; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.MANGROVE) {
            treeType = TreeType.MANGROVE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.TALL_MANGROVE) {
            treeType = TreeType.TALL_MANGROVE; // Folia - region threading
        } else if (worldgentreeabstract == TreeFeatures.CHERRY || worldgentreeabstract == TreeFeatures.CHERRY_BEES_005) {
            treeType = TreeType.CHERRY; // Folia - region threading
        } else {
            throw new IllegalArgumentException("Unknown tree generator " + worldgentreeabstract);
        }
        SaplingBlock.treeTypeRT.set(treeType); // Folia - region threading
    }
    // CraftBukkit end
}
