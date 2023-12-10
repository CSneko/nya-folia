package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockRedstoneEvent;
// CraftBukkit end

public class LightningRodBlock extends RodBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int ACTIVATION_TICKS = 8;
    public static final int RANGE = 128;
    private static final int SPARK_CYCLE = 200;

    public LightningRodBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LightningRodBlock.FACING, Direction.UP)).setValue(LightningRodBlock.WATERLOGGED, false)).setValue(LightningRodBlock.POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        boolean flag = fluid.getType() == Fluids.WATER;

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(LightningRodBlock.FACING, ctx.getClickedFace())).setValue(LightningRodBlock.WATERLOGGED, flag);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(LightningRodBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(LightningRodBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LightningRodBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LightningRodBlock.POWERED) && state.getValue(LightningRodBlock.FACING) == direction ? 15 : 0;
    }

    public void onLightningStrike(BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        boolean powered = state.getValue(LightningRodBlock.POWERED);
        int old = (powered) ? 15 : 0;
        int current = (!powered) ? 15 : 0;

        BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(CraftBlock.at(world, pos), old, current);
        world.getCraftServer().getPluginManager().callEvent(eventRedstone);

        if (eventRedstone.getNewCurrent() <= 0) {
            return;
        }
        // CraftBukkit end
        world.setBlock(pos, (BlockState) state.setValue(LightningRodBlock.POWERED, true), 3);
        this.updateNeighbours(state, world, pos);
        world.scheduleTick(pos, (Block) this, 8);
        world.levelEvent(3002, pos, ((Direction) state.getValue(LightningRodBlock.FACING)).getAxis().ordinal());
    }

    private void updateNeighbours(BlockState state, Level world, BlockPos pos) {
        world.updateNeighborsAt(pos.relative(((Direction) state.getValue(LightningRodBlock.FACING)).getOpposite()), this);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        world.setBlock(pos, (BlockState) state.setValue(LightningRodBlock.POWERED, false), 3);
        this.updateNeighbours(state, world, pos);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (world.isThundering() && (long) world.random.nextInt(200) <= world.getRedstoneGameTime() % 200L && pos.getY() == world.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1) { // Folia - region threading
            ParticleUtils.spawnParticlesAlongAxis(((Direction) state.getValue(LightningRodBlock.FACING)).getAxis(), world, pos, 0.125D, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(1, 2));
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if ((Boolean) state.getValue(LightningRodBlock.POWERED)) {
                this.updateNeighbours(state, world, pos);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!state.is(oldState.getBlock())) {
            if ((Boolean) state.getValue(LightningRodBlock.POWERED) && !world.getBlockTicks().hasScheduledTick(pos, this)) {
                world.setBlock(pos, (BlockState) state.setValue(LightningRodBlock.POWERED, false), 18);
            }

        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (world.isThundering() && projectile instanceof ThrownTrident && ((ThrownTrident) projectile).isChanneling()) {
            BlockPos blockposition = hit.getBlockPos();

            if (world.canSeeSky(blockposition)) {
                LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(world);

                if (entitylightning != null) {
                    entitylightning.moveTo(Vec3.atBottomCenterOf(blockposition.above()));
                    Entity entity = projectile.getOwner();

                    entitylightning.setCause(entity instanceof ServerPlayer ? (ServerPlayer) entity : null);
                    ((ServerLevel) world).strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.TRIDENT); // CraftBukkit
                }

                world.playSound((Player) null, blockposition, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.0F, 1.0F);
            }
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LightningRodBlock.FACING, LightningRodBlock.POWERED, LightningRodBlock.WATERLOGGED);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }
}
