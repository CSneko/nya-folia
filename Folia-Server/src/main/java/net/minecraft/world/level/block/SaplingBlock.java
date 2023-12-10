package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public class SaplingBlock extends BushBlock implements BonemealableBlock {

    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);
    private final AbstractTreeGrower treeGrower;
    public static final ThreadLocal<TreeType> treeTypeRT = new ThreadLocal<>(); // CraftBukkit // Folia - region threading

    protected SaplingBlock(AbstractTreeGrower generator, BlockBehaviour.Properties settings) {
        super(settings);
        this.treeGrower = generator;
        this.registerDefaultState((net.minecraft.world.level.block.state.BlockState) ((net.minecraft.world.level.block.state.BlockState) this.stateDefinition.any()).setValue(SaplingBlock.STAGE, 0));
    }

    @Override
    public VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SaplingBlock.SHAPE;
    }

    @Override
    public void randomTick(net.minecraft.world.level.block.state.BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextFloat() < (world.spigotConfig.saplingModifier / (100.0f * 7))) { // Spigot - SPIGOT-7159: Better modifier resolution
            this.advanceTree(world, pos, state, random);
        }

    }

    public void advanceTree(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, RandomSource random) {
        if ((Integer) state.getValue(SaplingBlock.STAGE) == 0) {
            world.setBlock(pos, (net.minecraft.world.level.block.state.BlockState) state.cycle(SaplingBlock.STAGE), 4);
        } else {
            // CraftBukkit start
            io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading
            if (worldData.captureTreeGeneration) { // Folia - region threading
                this.treeGrower.growTree(world, world.getChunkSource().getGenerator(), pos, state, random);
            } else {
                worldData.captureTreeGeneration = true; // Folia - region threading
                this.treeGrower.growTree(world, world.getChunkSource().getGenerator(), pos, state, random);
                worldData.captureTreeGeneration = false; // Folia - region threading
                if (worldData.capturedBlockStates.size() > 0) { // Folia - region threading
                    TreeType treeType = SaplingBlock.treeTypeRT.get(); // Folia - region threading
                    SaplingBlock.treeTypeRT.set(null); // Folia - region threading
                    Location location = CraftLocation.toBukkit(pos, world.getWorld());
                    java.util.List<BlockState> blocks = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                    worldData.capturedBlockStates.clear(); // Folia - region threading
                    StructureGrowEvent event = null;
                    if (treeType != null) {
                        event = new StructureGrowEvent(location, treeType, false, null, blocks);
                        org.bukkit.Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event == null || !event.isCancelled()) {
                        for (BlockState blockstate : blocks) {
                            blockstate.update(true);
                            world.checkCapturedTreeStateForObserverNotify(pos, (org.bukkit.craftbukkit.block.CraftBlockState) blockstate); // Paper - notify observers even if grow failed
                        }
                    }
                }
            }
            // CraftBukkit end
        }

    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return (double) world.random.nextFloat() < 0.45D;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.advanceTree(world, pos, state, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        builder.add(SaplingBlock.STAGE);
    }
}
