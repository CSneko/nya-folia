package net.minecraft.world.level.block;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class CactusBlock extends Block {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final int MAX_AGE = 15;
    protected static final int AABB_OFFSET = 1;
    protected static final VoxelShape COLLISION_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);
    protected static final VoxelShape OUTLINE_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    protected CactusBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CactusBlock.AGE, 0));
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true);
        }

    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockPos blockposition1 = pos.above();

        if (world.isEmptyBlock(blockposition1)) {
            int i;

            for (i = 1; world.getBlockState(pos.below(i)).is((Block) this); ++i) {
                ;
            }

            if (i < world.paperConfig().maxGrowthHeight.cactus) { // Paper - Configurable growth height
                int j = (Integer) state.getValue(CactusBlock.AGE);

                int modifier = world.spigotConfig.cactusModifier; // Spigot - SPIGOT-7159: Better modifier resolution
                if (j >= 15 || (modifier != 100 && random.nextFloat() < (modifier / (100.0f * 16)))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    CraftEventFactory.handleBlockGrowEvent(world, blockposition1, this.defaultBlockState()); // CraftBukkit
                    BlockState iblockdata1 = (BlockState) state.setValue(CactusBlock.AGE, 0);

                    world.setBlock(pos, iblockdata1, 4);
                    world.neighborChanged(iblockdata1, blockposition1, this, pos, false);
                } else if (modifier == 100 || random.nextFloat() < (modifier / (100.0f * 16))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    world.setBlock(pos, (BlockState) state.setValue(CactusBlock.AGE, j + 1), 4);
                }

            }
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CactusBlock.COLLISION_SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CactusBlock.OUTLINE_SHAPE;
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
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        Direction enumdirection;
        BlockState iblockdata1;

        do {
            if (!iterator.hasNext()) {
                BlockState iblockdata2 = world.getBlockState(pos.below());

                return (iblockdata2.is(Blocks.CACTUS) || iblockdata2.is(BlockTags.SAND)) && !world.getBlockState(pos.above()).liquid();
            }

            enumdirection = (Direction) iterator.next();
            iblockdata1 = world.getBlockState(pos.relative(enumdirection));
        } while (!iblockdata1.isSolid() && !world.getFluidState(pos.relative(enumdirection)).is(FluidTags.LAVA));

        return false;
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        CraftEventFactory.blockDamageRT.set(world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ())); // CraftBukkit // Folia - region threading
        entity.hurt(world.damageSources().cactus(), 1.0F);
        CraftEventFactory.blockDamageRT.set(null); // CraftBukkit // Folia - region threading
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CactusBlock.AGE);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }
}
