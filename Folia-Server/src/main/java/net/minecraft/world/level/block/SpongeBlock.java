package net.minecraft.world.level.block;

// CraftBukkit start
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.bukkit.event.block.SpongeAbsorbEvent;
// CraftBukkit end

public class SpongeBlock extends Block {

    public static final int MAX_DEPTH = 6;
    public static final int MAX_COUNT = 64;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    protected SpongeBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            this.tryAbsorbWater(world, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        this.tryAbsorbWater(world, pos);
        super.neighborChanged(state, world, pos, sourceBlock, sourcePos, notify);
    }

    protected void tryAbsorbWater(Level world, BlockPos pos) {
        if (this.removeWaterBreadthFirstSearch(world, pos)) {
            world.setBlock(pos, Blocks.WET_SPONGE.defaultBlockState(), 2);
            world.playSound((Player) null, pos, SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    private boolean removeWaterBreadthFirstSearch(Level world, BlockPos pos) {
        BlockStateListPopulator blockList = new BlockStateListPopulator(world); // CraftBukkit - Use BlockStateListPopulator
        BlockPos.breadthFirstTraversal(pos, 6, 65, (blockposition1, consumer) -> {
            Direction[] aenumdirection = SpongeBlock.ALL_DIRECTIONS;
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                consumer.accept(blockposition1.relative(enumdirection));
            }

        }, (blockposition1) -> {
            if (blockposition1.equals(pos)) {
                return true;
            } else {
                // CraftBukkit start
                BlockState iblockdata = blockList.getBlockState(blockposition1);
                FluidState fluid = blockList.getFluidState(blockposition1);
                // CraftBukkit end

                if (!fluid.is(FluidTags.WATER)) {
                    return false;
                } else {
                    Block block = iblockdata.getBlock();

                    if (block instanceof BucketPickup) {
                        BucketPickup ifluidsource = (BucketPickup) block;

                        if (!ifluidsource.pickupBlock((Player) null, blockList, blockposition1, iblockdata).isEmpty()) { // CraftBukkit
                            return true;
                        }
                    }

                    if (iblockdata.getBlock() instanceof LiquidBlock) {
                        blockList.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 3); // CraftBukkit
                    } else {
                        if (!iblockdata.is(Blocks.KELP) && !iblockdata.is(Blocks.KELP_PLANT) && !iblockdata.is(Blocks.SEAGRASS) && !iblockdata.is(Blocks.TALL_SEAGRASS)) {
                            return false;
                        }

                        // CraftBukkit start
                        // TileEntity tileentity = iblockdata.hasBlockEntity() ? world.getBlockEntity(blockposition1) : null;

                        // dropResources(iblockdata, world, blockposition1, tileentity);
                        blockList.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 3);
                        // CraftBukkit end
                    }

                    return true;
                }
            }
        });
        // CraftBukkit start
        List<CraftBlockState> blocks = blockList.getList(); // Is a clone
        if (!blocks.isEmpty()) {
            final org.bukkit.block.Block bblock = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());

            SpongeAbsorbEvent event = new SpongeAbsorbEvent(bblock, (List<org.bukkit.block.BlockState>) (List) blocks);
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            for (CraftBlockState block : blocks) {
                BlockPos blockposition1 = block.getPosition();
                BlockState iblockdata = world.getBlockState(blockposition1);
                FluidState fluid = world.getFluidState(blockposition1);

                if (fluid.is(FluidTags.WATER)) {
                    if (iblockdata.getBlock() instanceof BucketPickup && !((BucketPickup) iblockdata.getBlock()).pickupBlock((Player) null, blockList, blockposition1, iblockdata).isEmpty()) {
                        // NOP
                    } else if (iblockdata.getBlock() instanceof LiquidBlock) {
                        // NOP
                    } else if (iblockdata.is(Blocks.KELP) || iblockdata.is(Blocks.KELP_PLANT) || iblockdata.is(Blocks.SEAGRASS) || iblockdata.is(Blocks.TALL_SEAGRASS)) {
                        BlockEntity tileentity = iblockdata.hasBlockEntity() ? world.getBlockEntity(blockposition1) : null;

                        // Paper start
                        if (block.getHandle().isAir()) {
                        dropResources(iblockdata, world, blockposition1, tileentity);
                        }
                        // Paper end
                    }
                }
                world.setBlock(blockposition1, block.getHandle(), block.getFlag());
            }

            return true;
        }
        return false;
        // CraftBukkit end
    }
}
