package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {

    protected SpreadingSnowyDirtBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    private static boolean canBeGrass(BlockState state, LevelReader world, BlockPos pos) {
    // Paper start
        return canBeGrass(world.getChunk(pos), state, world, pos);
    }
    private static boolean canBeGrass(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockState state, LevelReader world, BlockPos pos) {
    // Paper end
        BlockPos blockposition1 = pos.above();
        BlockState iblockdata1 = chunk.getBlockState(blockposition1); // Paper

        if (iblockdata1.is(Blocks.SNOW) && (Integer) iblockdata1.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        } else if (iblockdata1.getFluidState().getAmount() == 8) {
            return false;
        } else {
            int i = LightEngine.getLightBlockInto(world, state, pos, iblockdata1, blockposition1, Direction.UP, iblockdata1.getLightBlock(world, blockposition1));

            return i < world.getMaxLightLevel();
        }
    }

    private static boolean canPropagate(BlockState state, LevelReader world, BlockPos pos) {
    // Paper start
        return canPropagate(world.getChunk(pos), state, world, pos);
    }

    private static boolean canPropagate(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockState state, LevelReader world, BlockPos pos) {
    // Paper end
        BlockPos blockposition1 = pos.above();

        return SpreadingSnowyDirtBlock.canBeGrass(chunk, state, world, pos) && !chunk.getFluidState(blockposition1).is(FluidTags.WATER); // Paper
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (this instanceof GrassBlock && world.paperConfig().tickRates.grassSpread != 1 && (world.paperConfig().tickRates.grassSpread < 1 || (io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + pos.hashCode()) % world.paperConfig().tickRates.grassSpread != 0)) { return; } // Paper // Folia - regionised ticking
        // Paper start
        net.minecraft.world.level.chunk.ChunkAccess cachedBlockChunk = world.getChunkIfLoaded(pos);
        if (cachedBlockChunk == null) { // Is this needed?
            return;
        }
        if (!SpreadingSnowyDirtBlock.canBeGrass(cachedBlockChunk, state, world, pos)) {
        // Paper end
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, Blocks.DIRT.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else {
            if (world.getMaxLocalRawBrightness(pos.above()) >= 9) {
                BlockState iblockdata1 = this.defaultBlockState();

                for (int i = 0; i < 4; ++i) {
                    BlockPos blockposition1 = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
                    // Paper start
                    if (pos.getX() == blockposition1.getX() && pos.getY() == blockposition1.getY() && pos.getZ() == blockposition1.getZ()) {
                        continue;
                    }
                    net.minecraft.world.level.chunk.ChunkAccess access;
                    if (cachedBlockChunk.locX == blockposition1.getX() >> 4 && cachedBlockChunk.locZ == blockposition1.getZ() >> 4) {
                        access = cachedBlockChunk;
                    } else {
                        access = world.getChunkAt(blockposition1);
                    }
                    if (access.getBlockState(blockposition1).is(Blocks.DIRT) && SpreadingSnowyDirtBlock.canPropagate(access, iblockdata1, world, blockposition1)) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition1, (BlockState) iblockdata1.setValue(SpreadingSnowyDirtBlock.SNOWY, access.getBlockState(blockposition1.above()).is(Blocks.SNOW))); // CraftBukkit
                    // Paper end
                    }
                }
            }

        }
    }
}
