package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class PointedDripstoneBlock extends Block implements Fallable, SimpleWaterloggedBlock {

    public static final DirectionProperty TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE = 11;
    private static final int DELAY_BEFORE_FALLING = 2;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK = 0.02F;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID_SOURCE = 0.12F;
    private static final int MAX_SEARCH_LENGTH_BETWEEN_STALACTITE_TIP_AND_CAULDRON = 11;
    private static final float WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.17578125F;
    private static final float LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.05859375F;
    private static final double MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE = 0.6D;
    private static final float STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE = 1.0F;
    private static final int STALACTITE_MAX_DAMAGE = 40;
    private static final int MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION = 6;
    private static final float STALAGMITE_FALL_DISTANCE_OFFSET = 2.0F;
    private static final int STALAGMITE_FALL_DAMAGE_MODIFIER = 2;
    private static final float AVERAGE_DAYS_PER_GROWTH = 5.0F;
    private static final float GROWTH_PROBABILITY_PER_RANDOM_TICK = 0.011377778F;
    private static final int MAX_GROWTH_LENGTH = 7;
    private static final int MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING = 10;
    private static final float STALACTITE_DRIP_START_PIXEL = 0.6875F;
    private static final VoxelShape TIP_MERGE_SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape TIP_SHAPE_UP = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape TIP_SHAPE_DOWN = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape FRUSTUM_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private static final VoxelShape MIDDLE_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    private static final VoxelShape BASE_SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final float MAX_HORIZONTAL_OFFSET = 0.125F;
    private static final VoxelShape REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

    public PointedDripstoneBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)).setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP)).setValue(PointedDripstoneBlock.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PointedDripstoneBlock.TIP_DIRECTION, PointedDripstoneBlock.THICKNESS, PointedDripstoneBlock.WATERLOGGED);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return PointedDripstoneBlock.isValidPointedDripstonePlacement(world, pos, (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(PointedDripstoneBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        if (direction != Direction.UP && direction != Direction.DOWN) {
            return state;
        } else {
            Direction enumdirection1 = (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION);

            if (enumdirection1 == Direction.DOWN && world.getBlockTicks().hasScheduledTick(pos, this)) {
                return state;
            } else if (direction == enumdirection1.getOpposite() && !this.canSurvive(state, world, pos)) {
                if (enumdirection1 == Direction.DOWN) {
                    world.scheduleTick(pos, (Block) this, 2);
                } else {
                    world.scheduleTick(pos, (Block) this, 1);
                }

                return state;
            } else {
                boolean flag = state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP_MERGE;
                DripstoneThickness dripstonethickness = PointedDripstoneBlock.calculateDripstoneThickness(world, pos, enumdirection1, flag);

                return (BlockState) state.setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness);
            }
        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockposition = hit.getBlockPos();

        if (!world.isClientSide && projectile.mayInteract(world, blockposition) && projectile instanceof ThrownTrident && projectile.getDeltaMovement().length() > 0.6D) {
            // CraftBukkit start
            if (!CraftEventFactory.callEntityChangeBlockEvent(projectile, blockposition, state.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                return;
            }
            // CraftBukkit end
            world.destroyBlock(blockposition, true);
        }

    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP && state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP) {
            CraftEventFactory.blockDamageRT.set(CraftBlock.at(world, pos)); // CraftBukkit // Folia - region threading
            entity.causeFallDamage(fallDistance + 2.0F, 2.0F, world.damageSources().stalagmite());
            CraftEventFactory.blockDamageRT.set(null); // CraftBukkit // Folia - region threading
        } else {
            super.fallOn(world, state, pos, entity, fallDistance);
        }

    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (PointedDripstoneBlock.canDrip(state)) {
            float f = random.nextFloat();

            if (f <= 0.12F) {
                PointedDripstoneBlock.getFluidAboveStalactite(world, pos, state).filter((pointeddripstoneblock_a) -> {
                    return f < 0.02F || PointedDripstoneBlock.canFillCauldron(pointeddripstoneblock_a.fluid);
                }).ifPresent((pointeddripstoneblock_a) -> {
                    PointedDripstoneBlock.spawnDripParticle(world, pos, state, pointeddripstoneblock_a.fluid);
                });
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (PointedDripstoneBlock.isStalagmite(state) && !this.canSurvive(state, world, pos)) {
            world.destroyBlock(pos, true);
        } else {
            PointedDripstoneBlock.spawnFallingStalactite(state, world, pos);
        }

    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        PointedDripstoneBlock.maybeTransferFluid(state, world, pos, random.nextFloat());
        if (random.nextFloat() < 0.011377778F && PointedDripstoneBlock.isStalactiteStartPos(state, world, pos)) {
            PointedDripstoneBlock.growStalactiteOrStalagmiteIfPossible(state, world, pos, random);
        }

    }

    @VisibleForTesting
    public static void maybeTransferFluid(BlockState state, ServerLevel world, BlockPos pos, float dripChance) {
        if (dripChance <= 0.17578125F || dripChance <= 0.05859375F) {
            if (PointedDripstoneBlock.isStalactiteStartPos(state, world, pos)) {
                Optional<PointedDripstoneBlock.FluidInfo> optional = PointedDripstoneBlock.getFluidAboveStalactite(world, pos, state);

                if (!optional.isEmpty()) {
                    Fluid fluidtype = ((PointedDripstoneBlock.FluidInfo) optional.get()).fluid;
                    float f1;

                    if (fluidtype == Fluids.WATER) {
                        f1 = 0.17578125F;
                    } else {
                        if (fluidtype != Fluids.LAVA) {
                            return;
                        }

                        f1 = 0.05859375F;
                    }

                    if (dripChance < f1) {
                        BlockPos blockposition1 = PointedDripstoneBlock.findTip(state, world, pos, 11, false);

                        if (blockposition1 != null) {
                            if (((PointedDripstoneBlock.FluidInfo) optional.get()).sourceState.is(Blocks.MUD) && fluidtype == Fluids.WATER) {
                                BlockState iblockdata1 = Blocks.CLAY.defaultBlockState();

                                // Paper start
                                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, ((PointedDripstoneBlock.FluidInfo) optional.get()).pos, iblockdata1)) {
                                Block.pushEntitiesUp(((PointedDripstoneBlock.FluidInfo) optional.get()).sourceState, iblockdata1, world, ((PointedDripstoneBlock.FluidInfo) optional.get()).pos);
                                world.gameEvent(GameEvent.BLOCK_CHANGE, ((PointedDripstoneBlock.FluidInfo) optional.get()).pos, GameEvent.Context.of(iblockdata1));
                                world.levelEvent(1504, blockposition1, 0);
                                }
                                //Paper end
                            } else {
                                BlockPos blockposition2 = PointedDripstoneBlock.findFillableCauldronBelowStalactiteTip(world, blockposition1, fluidtype);

                                if (blockposition2 != null) {
                                    world.levelEvent(1504, blockposition1, 0);
                                    int i = blockposition1.getY() - blockposition2.getY();
                                    int j = 50 + i;
                                    BlockState iblockdata2 = world.getBlockState(blockposition2);

                                    world.scheduleTick(blockposition2, iblockdata2.getBlock(), j);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        Direction enumdirection = ctx.getNearestLookingVerticalDirection().getOpposite();
        Direction enumdirection1 = PointedDripstoneBlock.calculateTipDirection(world, blockposition, enumdirection);

        if (enumdirection1 == null) {
            return null;
        } else {
            boolean flag = !ctx.isSecondaryUseActive();
            DripstoneThickness dripstonethickness = PointedDripstoneBlock.calculateDripstoneThickness(world, blockposition, enumdirection1, flag);

            return dripstonethickness == null ? null : (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, enumdirection1)).setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness)).setValue(PointedDripstoneBlock.WATERLOGGED, world.getFluidState(blockposition).getType() == Fluids.WATER);
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(PointedDripstoneBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        DripstoneThickness dripstonethickness = (DripstoneThickness) state.getValue(PointedDripstoneBlock.THICKNESS);
        VoxelShape voxelshape;

        if (dripstonethickness == DripstoneThickness.TIP_MERGE) {
            voxelshape = PointedDripstoneBlock.TIP_MERGE_SHAPE;
        } else if (dripstonethickness == DripstoneThickness.TIP) {
            if (state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN) {
                voxelshape = PointedDripstoneBlock.TIP_SHAPE_DOWN;
            } else {
                voxelshape = PointedDripstoneBlock.TIP_SHAPE_UP;
            }
        } else if (dripstonethickness == DripstoneThickness.FRUSTUM) {
            voxelshape = PointedDripstoneBlock.FRUSTUM_SHAPE;
        } else if (dripstonethickness == DripstoneThickness.MIDDLE) {
            voxelshape = PointedDripstoneBlock.MIDDLE_SHAPE;
        } else {
            voxelshape = PointedDripstoneBlock.BASE_SHAPE;
        }

        Vec3 vec3d = state.getOffset(world, pos);

        return voxelshape.move(vec3d.x, 0.0D, vec3d.z);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    @Override
    public float getMaxHorizontalOffset() {
        return 0.125F;
    }

    @Override
    public void onBrokenAfterFall(Level world, BlockPos pos, FallingBlockEntity fallingBlockEntity) {
        if (!fallingBlockEntity.isSilent()) {
            world.levelEvent(1045, pos, 0);
        }

    }

    @Override
    public DamageSource getFallDamageSource(Entity attacker) {
        return attacker.damageSources().fallingStalactite(attacker);
    }

    private static void spawnFallingStalactite(BlockState state, ServerLevel world, BlockPos pos) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();

        for (BlockState iblockdata1 = state; PointedDripstoneBlock.isStalactite(iblockdata1); iblockdata1 = world.getBlockState(blockposition_mutableblockposition)) {
            FallingBlockEntity entityfallingblock = FallingBlockEntity.fall(world, blockposition_mutableblockposition, iblockdata1);

            if (PointedDripstoneBlock.isTip(iblockdata1, true)) {
                int i = Math.max(1 + pos.getY() - blockposition_mutableblockposition.getY(), 6);
                float f = 1.0F * (float) i;

                entityfallingblock.setHurtsEntities(f, 40);
                break;
            }

            blockposition_mutableblockposition.move(Direction.DOWN);
        }

    }

    @VisibleForTesting
    public static void growStalactiteOrStalagmiteIfPossible(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockState iblockdata1 = world.getBlockState(pos.above(1));
        BlockState iblockdata2 = world.getBlockState(pos.above(2));

        if (PointedDripstoneBlock.canGrow(iblockdata1, iblockdata2)) {
            BlockPos blockposition1 = PointedDripstoneBlock.findTip(state, world, pos, 7, false);

            if (blockposition1 != null) {
                BlockState iblockdata3 = world.getBlockState(blockposition1);

                if (PointedDripstoneBlock.canDrip(iblockdata3) && PointedDripstoneBlock.canTipGrow(iblockdata3, world, blockposition1)) {
                    if (random.nextBoolean()) {
                        PointedDripstoneBlock.grow(world, blockposition1, Direction.DOWN);
                    } else {
                        PointedDripstoneBlock.growStalagmiteBelow(world, blockposition1);
                    }

                }
            }
        }
    }

    private static void growStalagmiteBelow(ServerLevel world, BlockPos pos) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();

        for (int i = 0; i < 10; ++i) {
            blockposition_mutableblockposition.move(Direction.DOWN);
            BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition);

            if (!iblockdata.getFluidState().isEmpty()) {
                return;
            }

            if (PointedDripstoneBlock.isUnmergedTipWithDirection(iblockdata, Direction.UP) && PointedDripstoneBlock.canTipGrow(iblockdata, world, blockposition_mutableblockposition)) {
                PointedDripstoneBlock.grow(world, blockposition_mutableblockposition, Direction.UP);
                return;
            }

            if (PointedDripstoneBlock.isValidPointedDripstonePlacement(world, blockposition_mutableblockposition, Direction.UP) && !world.isWaterAt(blockposition_mutableblockposition.below())) {
                PointedDripstoneBlock.grow(world, blockposition_mutableblockposition.below(), Direction.UP);
                return;
            }

            if (!PointedDripstoneBlock.canDripThrough(world, blockposition_mutableblockposition, iblockdata)) {
                return;
            }
        }

    }

    private static void grow(ServerLevel world, BlockPos pos, Direction direction) {
        BlockPos blockposition1 = pos.relative(direction);
        BlockState iblockdata = world.getBlockState(blockposition1);

        if (PointedDripstoneBlock.isUnmergedTipWithDirection(iblockdata, direction.getOpposite())) {
            PointedDripstoneBlock.createMergedTips(iblockdata, world, blockposition1);
        } else if (iblockdata.isAir() || iblockdata.is(Blocks.WATER)) {
            PointedDripstoneBlock.createDripstone(world, blockposition1, direction, DripstoneThickness.TIP, pos); // CraftBukkit
        }

    }

    private static void createDripstone(LevelAccessor generatoraccess, BlockPos blockposition, Direction enumdirection, DripstoneThickness dripstonethickness, BlockPos source) { // CraftBukkit
        BlockState iblockdata = (BlockState) ((BlockState) ((BlockState) Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, enumdirection)).setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness)).setValue(PointedDripstoneBlock.WATERLOGGED, generatoraccess.getFluidState(blockposition).getType() == Fluids.WATER);

        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(generatoraccess, source, blockposition, iblockdata, 3); // CraftBukkit
    }

    private static void createMergedTips(BlockState state, LevelAccessor world, BlockPos pos) {
        BlockPos blockposition1;
        BlockPos blockposition2;

        if (state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP) {
            blockposition1 = pos;
            blockposition2 = pos.above();
        } else {
            blockposition2 = pos;
            blockposition1 = pos.below();
        }

        PointedDripstoneBlock.createDripstone(world, blockposition2, Direction.DOWN, DripstoneThickness.TIP_MERGE, pos); // CraftBukkit
        PointedDripstoneBlock.createDripstone(world, blockposition1, Direction.UP, DripstoneThickness.TIP_MERGE, pos); // CraftBukkit
    }

    public static void spawnDripParticle(Level world, BlockPos pos, BlockState state) {
        PointedDripstoneBlock.getFluidAboveStalactite(world, pos, state).ifPresent((pointeddripstoneblock_a) -> {
            PointedDripstoneBlock.spawnDripParticle(world, pos, state, pointeddripstoneblock_a.fluid);
        });
    }

    private static void spawnDripParticle(Level world, BlockPos pos, BlockState state, Fluid fluid) {
        Vec3 vec3d = state.getOffset(world, pos);
        double d0 = 0.0625D;
        double d1 = (double) pos.getX() + 0.5D + vec3d.x;
        double d2 = (double) ((float) (pos.getY() + 1) - 0.6875F) - 0.0625D;
        double d3 = (double) pos.getZ() + 0.5D + vec3d.z;
        Fluid fluidtype1 = PointedDripstoneBlock.getDripFluid(world, fluid);
        SimpleParticleType particletype = fluidtype1.is(FluidTags.LAVA) ? ParticleTypes.DRIPPING_DRIPSTONE_LAVA : ParticleTypes.DRIPPING_DRIPSTONE_WATER;

        world.addParticle(particletype, d1, d2, d3, 0.0D, 0.0D, 0.0D);
    }

    @Nullable
    private static BlockPos findTip(BlockState state, LevelAccessor world, BlockPos pos, int range, boolean allowMerged) {
        if (PointedDripstoneBlock.isTip(state, allowMerged)) {
            return pos;
        } else {
            Direction enumdirection = (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION);
            BiPredicate<BlockPos, BlockState> bipredicate = (blockposition1, iblockdata1) -> {
                return iblockdata1.is(Blocks.POINTED_DRIPSTONE) && iblockdata1.getValue(PointedDripstoneBlock.TIP_DIRECTION) == enumdirection;
            };

            return (BlockPos) PointedDripstoneBlock.findBlockVertical(world, pos, enumdirection.getAxisDirection(), bipredicate, (iblockdata1) -> {
                return PointedDripstoneBlock.isTip(iblockdata1, allowMerged);
            }, range).orElse(null); // CraftBukkit - decompile error
        }
    }

    @Nullable
    private static Direction calculateTipDirection(LevelReader world, BlockPos pos, Direction direction) {
        Direction enumdirection1;

        if (PointedDripstoneBlock.isValidPointedDripstonePlacement(world, pos, direction)) {
            enumdirection1 = direction;
        } else {
            if (!PointedDripstoneBlock.isValidPointedDripstonePlacement(world, pos, direction.getOpposite())) {
                return null;
            }

            enumdirection1 = direction.getOpposite();
        }

        return enumdirection1;
    }

    private static DripstoneThickness calculateDripstoneThickness(LevelReader world, BlockPos pos, Direction direction, boolean tryMerge) {
        Direction enumdirection1 = direction.getOpposite();
        BlockState iblockdata = world.getBlockState(pos.relative(direction));

        if (PointedDripstoneBlock.isPointedDripstoneWithDirection(iblockdata, enumdirection1)) {
            return !tryMerge && iblockdata.getValue(PointedDripstoneBlock.THICKNESS) != DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP : DripstoneThickness.TIP_MERGE;
        } else if (!PointedDripstoneBlock.isPointedDripstoneWithDirection(iblockdata, direction)) {
            return DripstoneThickness.TIP;
        } else {
            DripstoneThickness dripstonethickness = (DripstoneThickness) iblockdata.getValue(PointedDripstoneBlock.THICKNESS);

            if (dripstonethickness != DripstoneThickness.TIP && dripstonethickness != DripstoneThickness.TIP_MERGE) {
                BlockState iblockdata1 = world.getBlockState(pos.relative(enumdirection1));

                return !PointedDripstoneBlock.isPointedDripstoneWithDirection(iblockdata1, direction) ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            } else {
                return DripstoneThickness.FRUSTUM;
            }
        }
    }

    public static boolean canDrip(BlockState state) {
        return PointedDripstoneBlock.isStalactite(state) && state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP && !(Boolean) state.getValue(PointedDripstoneBlock.WATERLOGGED);
    }

    private static boolean canTipGrow(BlockState state, ServerLevel world, BlockPos pos) {
        Direction enumdirection = (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION);
        BlockPos blockposition1 = pos.relative(enumdirection);
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        return !iblockdata1.getFluidState().isEmpty() ? false : (iblockdata1.isAir() ? true : PointedDripstoneBlock.isUnmergedTipWithDirection(iblockdata1, enumdirection.getOpposite()));
    }

    private static Optional<BlockPos> findRootBlock(Level world, BlockPos pos, BlockState state, int range) {
        Direction enumdirection = (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION);
        BiPredicate<BlockPos, BlockState> bipredicate = (blockposition1, iblockdata1) -> {
            return iblockdata1.is(Blocks.POINTED_DRIPSTONE) && iblockdata1.getValue(PointedDripstoneBlock.TIP_DIRECTION) == enumdirection;
        };

        return PointedDripstoneBlock.findBlockVertical(world, pos, enumdirection.getOpposite().getAxisDirection(), bipredicate, (iblockdata1) -> {
            return !iblockdata1.is(Blocks.POINTED_DRIPSTONE);
        }, range);
    }

    private static boolean isValidPointedDripstonePlacement(LevelReader world, BlockPos pos, Direction direction) {
        BlockPos blockposition1 = pos.relative(direction.getOpposite());
        BlockState iblockdata = world.getBlockState(blockposition1);

        return iblockdata.isFaceSturdy(world, blockposition1, direction) || PointedDripstoneBlock.isPointedDripstoneWithDirection(iblockdata, direction);
    }

    private static boolean isTip(BlockState state, boolean allowMerged) {
        if (!state.is(Blocks.POINTED_DRIPSTONE)) {
            return false;
        } else {
            DripstoneThickness dripstonethickness = (DripstoneThickness) state.getValue(PointedDripstoneBlock.THICKNESS);

            return dripstonethickness == DripstoneThickness.TIP || allowMerged && dripstonethickness == DripstoneThickness.TIP_MERGE;
        }
    }

    private static boolean isUnmergedTipWithDirection(BlockState state, Direction direction) {
        return PointedDripstoneBlock.isTip(state, false) && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == direction;
    }

    private static boolean isStalactite(BlockState state) {
        return PointedDripstoneBlock.isPointedDripstoneWithDirection(state, Direction.DOWN);
    }

    private static boolean isStalagmite(BlockState state) {
        return PointedDripstoneBlock.isPointedDripstoneWithDirection(state, Direction.UP);
    }

    private static boolean isStalactiteStartPos(BlockState state, LevelReader world, BlockPos pos) {
        return PointedDripstoneBlock.isStalactite(state) && !world.getBlockState(pos.above()).is(Blocks.POINTED_DRIPSTONE);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    private static boolean isPointedDripstoneWithDirection(BlockState state, Direction direction) {
        return state.is(Blocks.POINTED_DRIPSTONE) && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == direction;
    }

    @Nullable
    private static BlockPos findFillableCauldronBelowStalactiteTip(Level world, BlockPos pos, Fluid fluid) {
        Predicate<BlockState> predicate = (iblockdata) -> {
            return iblockdata.getBlock() instanceof AbstractCauldronBlock && ((AbstractCauldronBlock) iblockdata.getBlock()).canReceiveStalactiteDrip(fluid);
        };
        BiPredicate<BlockPos, BlockState> bipredicate = (blockposition1, iblockdata) -> {
            return PointedDripstoneBlock.canDripThrough(world, blockposition1, iblockdata);
        };

        return (BlockPos) PointedDripstoneBlock.findBlockVertical(world, pos, Direction.DOWN.getAxisDirection(), bipredicate, predicate, 11).orElse(null); // CraftBukkit - decompile error
    }

    @Nullable
    public static BlockPos findStalactiteTipAboveCauldron(Level world, BlockPos pos) {
        BiPredicate<BlockPos, BlockState> bipredicate = (blockposition1, iblockdata) -> {
            return PointedDripstoneBlock.canDripThrough(world, blockposition1, iblockdata);
        };

        return (BlockPos) PointedDripstoneBlock.findBlockVertical(world, pos, Direction.UP.getAxisDirection(), bipredicate, PointedDripstoneBlock::canDrip, 11).orElse(null); // CraftBukkit - decompile error
    }

    public static Fluid getCauldronFillFluidType(ServerLevel world, BlockPos pos) {
        return (Fluid) PointedDripstoneBlock.getFluidAboveStalactite(world, pos, world.getBlockState(pos)).map((pointeddripstoneblock_a) -> {
            return pointeddripstoneblock_a.fluid;
        }).filter(PointedDripstoneBlock::canFillCauldron).orElse(Fluids.EMPTY);
    }

    private static Optional<PointedDripstoneBlock.FluidInfo> getFluidAboveStalactite(Level world, BlockPos pos, BlockState state) {
        return !PointedDripstoneBlock.isStalactite(state) ? Optional.empty() : PointedDripstoneBlock.findRootBlock(world, pos, state, 11).map((blockposition1) -> {
            BlockPos blockposition2 = blockposition1.above();
            BlockState iblockdata1 = world.getBlockState(blockposition2);
            Object object;

            if (iblockdata1.is(Blocks.MUD) && !world.dimensionType().ultraWarm()) {
                object = Fluids.WATER;
            } else {
                object = world.getFluidState(blockposition2).getType();
            }

            return new PointedDripstoneBlock.FluidInfo(blockposition2, (Fluid) object, iblockdata1);
        });
    }

    private static boolean canFillCauldron(Fluid fluid) {
        return fluid == Fluids.LAVA || fluid == Fluids.WATER;
    }

    private static boolean canGrow(BlockState dripstoneBlockState, BlockState waterState) {
        return dripstoneBlockState.is(Blocks.DRIPSTONE_BLOCK) && waterState.is(Blocks.WATER) && waterState.getFluidState().isSource();
    }

    private static Fluid getDripFluid(Level world, Fluid fluid) {
        return (Fluid) (fluid.isSame(Fluids.EMPTY) ? (world.dimensionType().ultraWarm() ? Fluids.LAVA : Fluids.WATER) : fluid);
    }

    private static Optional<BlockPos> findBlockVertical(LevelAccessor world, BlockPos pos, Direction.AxisDirection direction, BiPredicate<BlockPos, BlockState> continuePredicate, Predicate<BlockState> stopPredicate, int range) {
        Direction enumdirection = Direction.get(direction, Direction.Axis.Y);
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();

        for (int j = 1; j < range; ++j) {
            blockposition_mutableblockposition.move(enumdirection);
            BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition);

            if (stopPredicate.test(iblockdata)) {
                return Optional.of(blockposition_mutableblockposition.immutable());
            }

            if (world.isOutsideBuildHeight(blockposition_mutableblockposition.getY()) || !continuePredicate.test(blockposition_mutableblockposition, iblockdata)) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static boolean canDripThrough(BlockGetter world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        } else if (state.isSolidRender(world, pos)) {
            return false;
        } else if (!state.getFluidState().isEmpty()) {
            return false;
        } else {
            VoxelShape voxelshape = state.getCollisionShape(world, pos);

            return !Shapes.joinIsNotEmpty(PointedDripstoneBlock.REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK, voxelshape, BooleanOp.AND);
        }
    }

    static record FluidInfo(BlockPos pos, Fluid fluid, BlockState sourceState) {

    }
}
