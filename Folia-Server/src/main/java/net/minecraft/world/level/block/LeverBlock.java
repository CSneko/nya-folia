package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    protected static final int DEPTH = 6;
    protected static final int WIDTH = 6;
    protected static final int HEIGHT = 8;
    protected static final VoxelShape NORTH_AABB = Block.box(5.0D, 4.0D, 10.0D, 11.0D, 12.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0D, 4.0D, 0.0D, 11.0D, 12.0D, 6.0D);
    protected static final VoxelShape WEST_AABB = Block.box(10.0D, 4.0D, 5.0D, 16.0D, 12.0D, 11.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 4.0D, 5.0D, 6.0D, 12.0D, 11.0D);
    protected static final VoxelShape UP_AABB_Z = Block.box(5.0D, 0.0D, 4.0D, 11.0D, 6.0D, 12.0D);
    protected static final VoxelShape UP_AABB_X = Block.box(4.0D, 0.0D, 5.0D, 12.0D, 6.0D, 11.0D);
    protected static final VoxelShape DOWN_AABB_Z = Block.box(5.0D, 10.0D, 4.0D, 11.0D, 16.0D, 12.0D);
    protected static final VoxelShape DOWN_AABB_X = Block.box(4.0D, 10.0D, 5.0D, 12.0D, 16.0D, 11.0D);

    protected LeverBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LeverBlock.FACING, Direction.NORTH)).setValue(LeverBlock.POWERED, false)).setValue(LeverBlock.FACE, AttachFace.WALL));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        switch ((AttachFace) state.getValue(LeverBlock.FACE)) {
            case FLOOR:
                switch (((Direction) state.getValue(LeverBlock.FACING)).getAxis()) {
                    case X:
                        return LeverBlock.UP_AABB_X;
                    case Z:
                    default:
                        return LeverBlock.UP_AABB_Z;
                }
            case WALL:
                switch ((Direction) state.getValue(LeverBlock.FACING)) {
                    case EAST:
                        return LeverBlock.EAST_AABB;
                    case WEST:
                        return LeverBlock.WEST_AABB;
                    case SOUTH:
                        return LeverBlock.SOUTH_AABB;
                    case NORTH:
                    default:
                        return LeverBlock.NORTH_AABB;
                }
            case CEILING:
            default:
                switch (((Direction) state.getValue(LeverBlock.FACING)).getAxis()) {
                    case X:
                        return LeverBlock.DOWN_AABB_X;
                    case Z:
                    default:
                        return LeverBlock.DOWN_AABB_Z;
                }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockState iblockdata1;

        if (world.isClientSide) {
            iblockdata1 = (BlockState) state.cycle(LeverBlock.POWERED);
            if ((Boolean) iblockdata1.getValue(LeverBlock.POWERED)) {
                LeverBlock.makeParticle(iblockdata1, world, pos, 1.0F);
            }

            return InteractionResult.SUCCESS;
        } else {
            // CraftBukkit start - Interact Lever
            boolean powered = state.getValue(LeverBlock.POWERED); // Old powered state
            org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getCraftServer().getPluginManager().callEvent(eventRedstone);

            if ((eventRedstone.getNewCurrent() > 0) != (!powered)) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end

            iblockdata1 = this.pull(state, world, pos);
            float f = (Boolean) iblockdata1.getValue(LeverBlock.POWERED) ? 0.6F : 0.5F;

            world.playSound((Player) null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
            world.gameEvent((Entity) player, (Boolean) iblockdata1.getValue(LeverBlock.POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
            return InteractionResult.CONSUME;
        }
    }

    public BlockState pull(BlockState state, Level world, BlockPos pos) {
        state = (BlockState) state.cycle(LeverBlock.POWERED);
        world.setBlock(pos, state, 3);
        this.updateNeighbours(state, world, pos);
        return state;
    }

    private static void makeParticle(BlockState state, LevelAccessor world, BlockPos pos, float alpha) {
        Direction enumdirection = ((Direction) state.getValue(LeverBlock.FACING)).getOpposite();
        Direction enumdirection1 = getConnectedDirection(state).getOpposite();
        double d0 = (double) pos.getX() + 0.5D + 0.1D * (double) enumdirection.getStepX() + 0.2D * (double) enumdirection1.getStepX();
        double d1 = (double) pos.getY() + 0.5D + 0.1D * (double) enumdirection.getStepY() + 0.2D * (double) enumdirection1.getStepY();
        double d2 = (double) pos.getZ() + 0.5D + 0.1D * (double) enumdirection.getStepZ() + 0.2D * (double) enumdirection1.getStepZ();

        world.addParticle(new DustParticleOptions(DustParticleOptions.REDSTONE_PARTICLE_COLOR, alpha), d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(LeverBlock.POWERED) && random.nextFloat() < 0.25F) {
            LeverBlock.makeParticle(state, world, pos, 0.5F);
        }

    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock())) {
            if ((Boolean) state.getValue(LeverBlock.POWERED)) {
                this.updateNeighbours(state, world, pos);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LeverBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LeverBlock.POWERED) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    private void updateNeighbours(BlockState state, Level world, BlockPos pos) {
        world.updateNeighborsAt(pos, this);
        world.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LeverBlock.FACE, LeverBlock.FACING, LeverBlock.POWERED);
    }
}
