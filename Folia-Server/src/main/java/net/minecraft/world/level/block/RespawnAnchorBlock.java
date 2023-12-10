package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class RespawnAnchorBlock extends Block {

    public static final int MIN_CHARGES = 0;
    public static final int MAX_CHARGES = 4;
    public static final IntegerProperty CHARGE = BlockStateProperties.RESPAWN_ANCHOR_CHARGES;
    private static final ImmutableList<Vec3i> RESPAWN_HORIZONTAL_OFFSETS = ImmutableList.of(new Vec3i(0, 0, -1), new Vec3i(-1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, 0), new Vec3i(-1, 0, -1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, 1));
    private static final ImmutableList<Vec3i> RESPAWN_OFFSETS = (new Builder()).addAll(RespawnAnchorBlock.RESPAWN_HORIZONTAL_OFFSETS).addAll(RespawnAnchorBlock.RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::below).iterator()).addAll(RespawnAnchorBlock.RESPAWN_HORIZONTAL_OFFSETS.stream().map(Vec3i::above).iterator()).add(new Vec3i(0, 1, 0)).build();

    public RespawnAnchorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(RespawnAnchorBlock.CHARGE, 0));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (hand == InteractionHand.MAIN_HAND && !RespawnAnchorBlock.isRespawnFuel(itemstack) && RespawnAnchorBlock.isRespawnFuel(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionResult.PASS;
        } else if (RespawnAnchorBlock.isRespawnFuel(itemstack) && RespawnAnchorBlock.canBeCharged(state)) {
            RespawnAnchorBlock.charge(player, world, pos, state);
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else if ((Integer) state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
            return InteractionResult.PASS;
        } else if (!RespawnAnchorBlock.canSetSpawn(world)) {
            if (!world.isClientSide) {
                this.explode(state, world, pos);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            if (!world.isClientSide) {
                ServerPlayer entityplayer = (ServerPlayer) player;

                if (entityplayer.getRespawnDimension() != world.dimension() || !pos.equals(entityplayer.getRespawnPosition())) {
                    if (entityplayer.setRespawnPosition(world.dimension(), pos, 0.0F, false, true, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR)) { // Paper - PlayerSetSpawnEvent
                    world.playSound((Player) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                    // Paper start - handle failed set spawn
                    } else {
                        return InteractionResult.FAIL;
                    }
                    // Paper end
                }
            }

            return InteractionResult.CONSUME;
        }
    }

    private static boolean isRespawnFuel(ItemStack stack) {
        return stack.is(Items.GLOWSTONE);
    }

    private static boolean canBeCharged(BlockState state) {
        return (Integer) state.getValue(RespawnAnchorBlock.CHARGE) < 4;
    }

    private static boolean isWaterThatWouldFlow(BlockPos pos, Level world) {
        FluidState fluid = world.getFluidState(pos);

        if (!fluid.is(FluidTags.WATER)) {
            return false;
        } else if (fluid.isSource()) {
            return true;
        } else {
            float f = (float) fluid.getAmount();

            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluid1 = world.getFluidState(pos.below());

                return !fluid1.is(FluidTags.WATER);
            }
        }
    }

    private void explode(BlockState state, Level world, final BlockPos explodedPos) {
        final org.bukkit.block.BlockState explodedBlockState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(explodedPos, state, null); // Paper - exploded block state
        world.removeBlock(explodedPos, false);
        Stream<Direction> stream = Direction.Plane.HORIZONTAL.stream(); // CraftBukkit - decompile error

        Objects.requireNonNull(explodedPos);
        boolean flag = stream.map(explodedPos::relative).anyMatch((blockposition1) -> {
            return RespawnAnchorBlock.isWaterThatWouldFlow(blockposition1, world);
        });
        final boolean flag1 = flag || world.getFluidState(explodedPos.above()).is(FluidTags.WATER);
        ExplosionDamageCalculator explosiondamagecalculator = new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState) {
                return pos.equals(explodedPos) && flag1 ? Optional.of(Blocks.WATER.getExplosionResistance()) : super.getBlockExplosionResistance(explosion, world, pos, blockState, fluidState);
            }
        };
        Vec3 vec3d = explodedPos.getCenter();

        world.explode((Entity) null, world.damageSources().badRespawnPointExplosion(vec3d, explodedBlockState), explosiondamagecalculator, vec3d, 5.0F, true, Level.ExplosionInteraction.BLOCK); // Paper
    }

    public static boolean canSetSpawn(Level world) {
        return world.dimensionType().respawnAnchorWorks();
    }

    public static void charge(@Nullable Entity charger, Level world, BlockPos pos, BlockState state) {
        BlockState iblockdata1 = (BlockState) state.setValue(RespawnAnchorBlock.CHARGE, (Integer) state.getValue(RespawnAnchorBlock.CHARGE) + 1);

        world.setBlock(pos, iblockdata1, 3);
        world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(charger, iblockdata1));
        world.playSound((Player) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(RespawnAnchorBlock.CHARGE) != 0) {
            if (random.nextInt(100) == 0) {
                world.playSound((Player) null, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            double d0 = (double) pos.getX() + 0.5D + (0.5D - random.nextDouble());
            double d1 = (double) pos.getY() + 1.0D;
            double d2 = (double) pos.getZ() + 0.5D + (0.5D - random.nextDouble());
            double d3 = (double) random.nextFloat() * 0.04D;

            world.addParticle(ParticleTypes.REVERSE_PORTAL, d0, d1, d2, 0.0D, d3, 0.0D);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RespawnAnchorBlock.CHARGE);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    public static int getScaledChargeLevel(BlockState state, int maxLevel) {
        return Mth.floor((float) ((Integer) state.getValue(RespawnAnchorBlock.CHARGE) - 0) / 4.0F * (float) maxLevel);
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return RespawnAnchorBlock.getScaledChargeLevel(state, 15);
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> entity, CollisionGetter world, BlockPos pos) {
        Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(entity, world, pos, true);

        return optional.isPresent() ? optional : RespawnAnchorBlock.findStandUpPosition(entity, world, pos, false);
    }

    private static Optional<Vec3> findStandUpPosition(EntityType<?> entity, CollisionGetter world, BlockPos pos, boolean ignoreInvalidPos) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        UnmodifiableIterator unmodifiableiterator = RespawnAnchorBlock.RESPAWN_OFFSETS.iterator();

        Vec3 vec3d;

        do {
            if (!unmodifiableiterator.hasNext()) {
                return Optional.empty();
            }

            Vec3i baseblockposition = (Vec3i) unmodifiableiterator.next();

            blockposition_mutableblockposition.set(pos).move(baseblockposition);
            vec3d = DismountHelper.findSafeDismountLocation(entity, world, blockposition_mutableblockposition, ignoreInvalidPos);
        } while (vec3d == null);

        return Optional.of(vec3d);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }
}
