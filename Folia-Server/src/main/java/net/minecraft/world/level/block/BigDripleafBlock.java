package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Tilt;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class BigDripleafBlock extends HorizontalDirectionalBlock implements BonemealableBlock, SimpleWaterloggedBlock {

    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final EnumProperty<Tilt> TILT = BlockStateProperties.TILT;
    private static final int NO_TICK = -1;
    private static final Object2IntMap<Tilt> DELAY_UNTIL_NEXT_TILT_STATE = (Object2IntMap) Util.make(new Object2IntArrayMap(), (object2intarraymap) -> {
        object2intarraymap.defaultReturnValue(-1);
        object2intarraymap.put(Tilt.UNSTABLE, 10);
        object2intarraymap.put(Tilt.PARTIAL, 10);
        object2intarraymap.put(Tilt.FULL, 100);
    });
    private static final int MAX_GEN_HEIGHT = 5;
    private static final int STEM_WIDTH = 6;
    private static final int ENTITY_DETECTION_MIN_Y = 11;
    private static final int LOWEST_LEAF_TOP = 13;
    private static final Map<Tilt, VoxelShape> LEAF_SHAPES = ImmutableMap.of(Tilt.NONE, Block.box(0.0D, 11.0D, 0.0D, 16.0D, 15.0D, 16.0D), Tilt.UNSTABLE, Block.box(0.0D, 11.0D, 0.0D, 16.0D, 15.0D, 16.0D), Tilt.PARTIAL, Block.box(0.0D, 11.0D, 0.0D, 16.0D, 13.0D, 16.0D), Tilt.FULL, Shapes.empty());
    private static final VoxelShape STEM_SLICER = Block.box(0.0D, 13.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final Map<Direction, VoxelShape> STEM_SHAPES = ImmutableMap.of(Direction.NORTH, Shapes.joinUnoptimized(BigDripleafStemBlock.NORTH_SHAPE, BigDripleafBlock.STEM_SLICER, BooleanOp.ONLY_FIRST), Direction.SOUTH, Shapes.joinUnoptimized(BigDripleafStemBlock.SOUTH_SHAPE, BigDripleafBlock.STEM_SLICER, BooleanOp.ONLY_FIRST), Direction.EAST, Shapes.joinUnoptimized(BigDripleafStemBlock.EAST_SHAPE, BigDripleafBlock.STEM_SLICER, BooleanOp.ONLY_FIRST), Direction.WEST, Shapes.joinUnoptimized(BigDripleafStemBlock.WEST_SHAPE, BigDripleafBlock.STEM_SLICER, BooleanOp.ONLY_FIRST));
    private final Map<BlockState, VoxelShape> shapesCache;

    protected BigDripleafBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BigDripleafBlock.WATERLOGGED, false)).setValue(BigDripleafBlock.FACING, Direction.NORTH)).setValue(BigDripleafBlock.TILT, Tilt.NONE));
        this.shapesCache = this.getShapeForEachState(BigDripleafBlock::calculateShape);
    }

    private static VoxelShape calculateShape(BlockState state) {
        return Shapes.or((VoxelShape) BigDripleafBlock.LEAF_SHAPES.get(state.getValue(BigDripleafBlock.TILT)), (VoxelShape) BigDripleafBlock.STEM_SHAPES.get(state.getValue(BigDripleafBlock.FACING)));
    }

    public static void placeWithRandomHeight(LevelAccessor world, RandomSource random, BlockPos pos, Direction direction) {
        int i = Mth.nextInt(random, 2, 5);
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();
        int j = 0;

        while (j < i && BigDripleafBlock.canPlaceAt(world, blockposition_mutableblockposition, world.getBlockState(blockposition_mutableblockposition))) {
            ++j;
            blockposition_mutableblockposition.move(Direction.UP);
        }

        int k = pos.getY() + j - 1;

        blockposition_mutableblockposition.setY(pos.getY());

        while (blockposition_mutableblockposition.getY() < k) {
            BigDripleafStemBlock.place(world, blockposition_mutableblockposition, world.getFluidState(blockposition_mutableblockposition), direction);
            blockposition_mutableblockposition.move(Direction.UP);
        }

        BigDripleafBlock.place(world, blockposition_mutableblockposition, world.getFluidState(blockposition_mutableblockposition), direction);
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.SMALL_DRIPLEAF);
    }

    protected static boolean canPlaceAt(LevelHeightAccessor world, BlockPos pos, BlockState state) {
        return !world.isOutsideBuildHeight(pos) && BigDripleafBlock.canReplace(state);
    }

    protected static boolean place(LevelAccessor world, BlockPos pos, FluidState fluidState, Direction direction) {
        BlockState iblockdata = (BlockState) ((BlockState) Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(BigDripleafBlock.WATERLOGGED, fluidState.isSourceOfType(Fluids.WATER))).setValue(BigDripleafBlock.FACING, direction);

        return world.setBlock(pos, iblockdata, 3);
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        this.setTiltAndScheduleTick(state, world, hit.getBlockPos(), Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, projectile); // CraftBukkit
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(BigDripleafBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        return iblockdata1.is((Block) this) || iblockdata1.is(Blocks.BIG_DRIPLEAF_STEM) || iblockdata1.is(BlockTags.BIG_DRIPLEAF_PLACEABLE);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(world, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if ((Boolean) state.getValue(BigDripleafBlock.WATERLOGGED)) {
                world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
            }

            return direction == Direction.UP && neighborState.is((Block) this) ? Blocks.BIG_DRIPLEAF_STEM.withPropertiesOf(state) : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        BlockState iblockdata1 = world.getBlockState(pos.above());

        return BigDripleafBlock.canReplace(iblockdata1);
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockposition1 = pos.above();
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        if (BigDripleafBlock.canPlaceAt(world, blockposition1, iblockdata1)) {
            Direction enumdirection = (Direction) state.getValue(BigDripleafBlock.FACING);

            BigDripleafStemBlock.place(world, pos, state.getFluidState(), enumdirection);
            BigDripleafBlock.place(world, blockposition1, iblockdata1.getFluidState(), enumdirection);
        }

    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!world.isClientSide) {
            if (state.getValue(BigDripleafBlock.TILT) == Tilt.NONE && BigDripleafBlock.canEntityTilt(pos, entity) && !world.hasNeighborSignal(pos)) {
                // CraftBukkit start - tilt dripleaf
                org.bukkit.event.Cancellable cancellable;
                if (entity instanceof Player) {
                    cancellable = CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                } else {
                    cancellable = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                    world.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
                }

                if (cancellable.isCancelled()) {
                    return;
                }
                this.setTiltAndScheduleTick(state, world, pos, Tilt.UNSTABLE, (SoundEvent) null, entity);
                // CraftBukkit end
            }

        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.hasNeighborSignal(pos)) {
            BigDripleafBlock.resetTilt(state, world, pos);
        } else {
            Tilt tilt = (Tilt) state.getValue(BigDripleafBlock.TILT);

            if (tilt == Tilt.UNSTABLE) {
                this.setTiltAndScheduleTick(state, world, pos, Tilt.PARTIAL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, null); // CraftBukkit
            } else if (tilt == Tilt.PARTIAL) {
                this.setTiltAndScheduleTick(state, world, pos, Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, null); // CraftBukkit
            } else if (tilt == Tilt.FULL) {
                BigDripleafBlock.resetTilt(state, world, pos);
            }

        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.hasNeighborSignal(pos)) {
            BigDripleafBlock.resetTilt(state, world, pos);
        }

    }

    private static void playTiltSound(Level world, BlockPos pos, SoundEvent soundEvent) {
        float f = Mth.randomBetween(world.random, 0.8F, 1.2F);

        world.playSound((Player) null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, f);
    }

    private static boolean canEntityTilt(BlockPos pos, Entity entity) {
        return entity.onGround() && entity.position().y > (double) ((float) pos.getY() + 0.6875F);
    }

    // CraftBukkit start
    private void setTiltAndScheduleTick(BlockState iblockdata, Level world, BlockPos blockposition, Tilt tilt, @Nullable SoundEvent soundeffect, @Nullable Entity entity) {
        if (!BigDripleafBlock.setTilt(iblockdata, world, blockposition, tilt, entity)) return;
        // CraftBukkit end
        if (soundeffect != null) {
            BigDripleafBlock.playTiltSound(world, blockposition, soundeffect);
        }

        int i = BigDripleafBlock.DELAY_UNTIL_NEXT_TILT_STATE.getInt(tilt);

        if (i != -1) {
            world.scheduleTick(blockposition, (Block) this, i);
        }

    }

    private static void resetTilt(BlockState state, Level world, BlockPos pos) {
        BigDripleafBlock.setTilt(state, world, pos, Tilt.NONE, null); // CraftBukkit
        if (state.getValue(BigDripleafBlock.TILT) != Tilt.NONE) {
            BigDripleafBlock.playTiltSound(world, pos, SoundEvents.BIG_DRIPLEAF_TILT_UP);
        }

    }

    // CraftBukkit start
    private static boolean setTilt(BlockState iblockdata, Level world, BlockPos blockposition, Tilt tilt, @Nullable Entity entity) {
        if (entity != null) {
            if (!CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, iblockdata.setValue(BigDripleafBlock.TILT, tilt))) {
                return false;
            }
        }
        // CraftBukkit end
        Tilt tilt1 = (Tilt) iblockdata.getValue(BigDripleafBlock.TILT);

        world.setBlock(blockposition, (BlockState) iblockdata.setValue(BigDripleafBlock.TILT, tilt), 2);
        if (tilt.causesVibration() && tilt != tilt1) {
            world.gameEvent((Entity) null, GameEvent.BLOCK_CHANGE, blockposition);
        }

        return true; // CraftBukkit
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (VoxelShape) BigDripleafBlock.LEAF_SHAPES.get(state.getValue(BigDripleafBlock.TILT));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapesCache.get(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState iblockdata = ctx.getLevel().getBlockState(ctx.getClickedPos().below());
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        boolean flag = iblockdata.is(Blocks.BIG_DRIPLEAF) || iblockdata.is(Blocks.BIG_DRIPLEAF_STEM);

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(BigDripleafBlock.WATERLOGGED, fluid.isSourceOfType(Fluids.WATER))).setValue(BigDripleafBlock.FACING, flag ? (Direction) iblockdata.getValue(BigDripleafBlock.FACING) : ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BigDripleafBlock.WATERLOGGED, BigDripleafBlock.FACING, BigDripleafBlock.TILT);
    }
}
