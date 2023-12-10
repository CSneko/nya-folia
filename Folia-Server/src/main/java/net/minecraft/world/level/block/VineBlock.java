package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class VineBlock extends Block {

    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != Direction.DOWN;
    }).collect(Util.toMap());
    protected static final float AABB_OFFSET = 1.0F;
    private static final VoxelShape UP_AABB = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape WEST_AABB = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_AABB = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);
    private final Map<BlockState, VoxelShape> shapesCache;

    public VineBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(VineBlock.UP, false)).setValue(VineBlock.NORTH, false)).setValue(VineBlock.EAST, false)).setValue(VineBlock.SOUTH, false)).setValue(VineBlock.WEST, false));
        this.shapesCache = ImmutableMap.copyOf((Map) this.stateDefinition.getPossibleStates().stream().collect(Collectors.toMap(Function.identity(), VineBlock::calculateShape)));
    }

    private static VoxelShape calculateShape(BlockState state) {
        VoxelShape voxelshape = Shapes.empty();

        if ((Boolean) state.getValue(VineBlock.UP)) {
            voxelshape = VineBlock.UP_AABB;
        }

        if ((Boolean) state.getValue(VineBlock.NORTH)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.NORTH_AABB);
        }

        if ((Boolean) state.getValue(VineBlock.SOUTH)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.SOUTH_AABB);
        }

        if ((Boolean) state.getValue(VineBlock.EAST)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.EAST_AABB);
        }

        if ((Boolean) state.getValue(VineBlock.WEST)) {
            voxelshape = Shapes.or(voxelshape, VineBlock.WEST_AABB);
        }

        return voxelshape.isEmpty() ? Shapes.block() : voxelshape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapesCache.get(state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return this.hasFaces(this.getUpdatedState(state, world, pos));
    }

    private boolean hasFaces(BlockState state) {
        return this.countFaces(state) > 0;
    }

    private int countFaces(BlockState state) {
        int i = 0;
        Iterator iterator = VineBlock.PROPERTY_BY_DIRECTION.values().iterator();

        while (iterator.hasNext()) {
            BooleanProperty blockstateboolean = (BooleanProperty) iterator.next();

            if ((Boolean) state.getValue(blockstateboolean)) {
                ++i;
            }
        }

        return i;
    }

    private boolean canSupportAtFace(BlockGetter world, BlockPos pos, Direction side) {
        if (side == Direction.DOWN) {
            return false;
        } else {
            BlockPos blockposition1 = pos.relative(side);

            if (VineBlock.isAcceptableNeighbour(world, blockposition1, side)) {
                return true;
            } else if (side.getAxis() == Direction.Axis.Y) {
                return false;
            } else {
                BooleanProperty blockstateboolean = (BooleanProperty) VineBlock.PROPERTY_BY_DIRECTION.get(side);
                BlockState iblockdata = world.getBlockState(pos.above());

                return iblockdata.is((Block) this) && (Boolean) iblockdata.getValue(blockstateboolean);
            }
        }
    }

    public static boolean isAcceptableNeighbour(BlockGetter world, BlockPos pos, Direction direction) {
        return MultifaceBlock.canAttachTo(world, direction, pos, world.getBlockState(pos));
    }

    private BlockState getUpdatedState(BlockState state, BlockGetter world, BlockPos pos) {
        BlockPos blockposition1 = pos.above();

        if ((Boolean) state.getValue(VineBlock.UP)) {
            state = (BlockState) state.setValue(VineBlock.UP, VineBlock.isAcceptableNeighbour(world, blockposition1, Direction.DOWN));
        }

        BlockState iblockdata1 = null;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BooleanProperty blockstateboolean = VineBlock.getPropertyForFace(enumdirection);

            if ((Boolean) state.getValue(blockstateboolean)) {
                boolean flag = this.canSupportAtFace(world, pos, enumdirection);

                if (!flag) {
                    if (iblockdata1 == null) {
                        iblockdata1 = world.getBlockState(blockposition1);
                    }

                    flag = iblockdata1.is((Block) this) && (Boolean) iblockdata1.getValue(blockstateboolean);
                }

                state = (BlockState) state.setValue(blockstateboolean, flag);
            }
        }

        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        } else {
            BlockState iblockdata2 = this.getUpdatedState(state, world, pos);

            return !this.hasFaces(iblockdata2) ? Blocks.AIR.defaultBlockState() : iblockdata2;
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getGameRules().getBoolean(GameRules.RULE_DO_VINES_SPREAD)) {
            if (random.nextFloat() < (world.spigotConfig.vineModifier / (100.0f * 4))) { // Spigot - SPIGOT-7159: Better modifier resolution
                Direction enumdirection = Direction.getRandom(random);
                BlockPos blockposition1 = pos.above();
                BlockPos blockposition2;
                BlockState iblockdata1;
                Direction enumdirection1;

                if (enumdirection.getAxis().isHorizontal() && !(Boolean) state.getValue(VineBlock.getPropertyForFace(enumdirection))) {
                    if (this.canSpread(world, pos)) {
                        blockposition2 = pos.relative(enumdirection);
                        iblockdata1 = world.getBlockState(blockposition2);
                        if (iblockdata1.isAir()) {
                            enumdirection1 = enumdirection.getClockWise();
                            Direction enumdirection2 = enumdirection.getCounterClockWise();
                            boolean flag = (Boolean) state.getValue(VineBlock.getPropertyForFace(enumdirection1));
                            boolean flag1 = (Boolean) state.getValue(VineBlock.getPropertyForFace(enumdirection2));
                            BlockPos blockposition3 = blockposition2.relative(enumdirection1);
                            BlockPos blockposition4 = blockposition2.relative(enumdirection2);

                            // CraftBukkit start - Call BlockSpreadEvent
                            BlockPos source = pos;

                            if (flag && VineBlock.isAcceptableNeighbour(world, blockposition3, enumdirection1)) {
                                CraftEventFactory.handleBlockSpreadEvent(world, source, blockposition2, (BlockState) this.defaultBlockState().setValue(VineBlock.getPropertyForFace(enumdirection1), true), 2);
                            } else if (flag1 && VineBlock.isAcceptableNeighbour(world, blockposition4, enumdirection2)) {
                                CraftEventFactory.handleBlockSpreadEvent(world, source, blockposition2, (BlockState) this.defaultBlockState().setValue(VineBlock.getPropertyForFace(enumdirection2), true), 2);
                            } else {
                                Direction enumdirection3 = enumdirection.getOpposite();

                                if (flag && world.isEmptyBlock(blockposition3) && VineBlock.isAcceptableNeighbour(world, pos.relative(enumdirection1), enumdirection3)) {
                                    CraftEventFactory.handleBlockSpreadEvent(world, source, blockposition3, (BlockState) this.defaultBlockState().setValue(VineBlock.getPropertyForFace(enumdirection3), true), 2);
                                } else if (flag1 && world.isEmptyBlock(blockposition4) && VineBlock.isAcceptableNeighbour(world, pos.relative(enumdirection2), enumdirection3)) {
                                    CraftEventFactory.handleBlockSpreadEvent(world, source, blockposition4, (BlockState) this.defaultBlockState().setValue(VineBlock.getPropertyForFace(enumdirection3), true), 2);
                                } else if ((double) random.nextFloat() < 0.05D && VineBlock.isAcceptableNeighbour(world, blockposition2.above(), Direction.UP)) {
                                    CraftEventFactory.handleBlockSpreadEvent(world, source, blockposition2, (BlockState) this.defaultBlockState().setValue(VineBlock.UP, true), 2);
                                }
                                // CraftBukkit end
                            }
                        } else if (VineBlock.isAcceptableNeighbour(world, blockposition2, enumdirection)) {
                            CraftEventFactory.handleBlockGrowEvent(world, pos, (BlockState) state.setValue(VineBlock.getPropertyForFace(enumdirection), true), 2); // CraftBukkit
                        }

                    }
                } else {
                    if (enumdirection == Direction.UP && pos.getY() < world.getMaxBuildHeight() - 1) {
                        if (this.canSupportAtFace(world, pos, enumdirection)) {
                            CraftEventFactory.handleBlockGrowEvent(world, pos, (BlockState) state.setValue(VineBlock.UP, true), 2); // CraftBukkit
                            return;
                        }

                        if (world.isEmptyBlock(blockposition1)) {
                            if (!this.canSpread(world, pos)) {
                                return;
                            }

                            BlockState iblockdata2 = state;
                            Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                            while (iterator.hasNext()) {
                                enumdirection1 = (Direction) iterator.next();
                                if (random.nextBoolean() || !VineBlock.isAcceptableNeighbour(world, blockposition1.relative(enumdirection1), enumdirection1)) {
                                    iblockdata2 = (BlockState) iblockdata2.setValue(VineBlock.getPropertyForFace(enumdirection1), false);
                                }
                            }

                            if (this.hasHorizontalConnection(iblockdata2)) {
                                CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition1, iblockdata2, 2); // CraftBukkit
                            }

                            return;
                        }
                    }

                    if (pos.getY() > world.getMinBuildHeight()) {
                        blockposition2 = pos.below();
                        iblockdata1 = world.getBlockState(blockposition2);
                        if (iblockdata1.isAir() || iblockdata1.is((Block) this)) {
                            BlockState iblockdata3 = iblockdata1.isAir() ? this.defaultBlockState() : iblockdata1;
                            BlockState iblockdata4 = this.copyRandomFaces(state, iblockdata3, random);

                            if (iblockdata3 != iblockdata4 && this.hasHorizontalConnection(iblockdata4)) {
                                CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition2, iblockdata4, 2); // CraftBukkit
                            }
                        }
                    }

                }
            }
        }
    }

    private BlockState copyRandomFaces(BlockState above, BlockState state, RandomSource random) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();

            if (random.nextBoolean()) {
                BooleanProperty blockstateboolean = VineBlock.getPropertyForFace(enumdirection);

                if ((Boolean) above.getValue(blockstateboolean)) {
                    state = (BlockState) state.setValue(blockstateboolean, true);
                }
            }
        }

        return state;
    }

    private boolean hasHorizontalConnection(BlockState state) {
        return (Boolean) state.getValue(VineBlock.NORTH) || (Boolean) state.getValue(VineBlock.EAST) || (Boolean) state.getValue(VineBlock.SOUTH) || (Boolean) state.getValue(VineBlock.WEST);
    }

    private boolean canSpread(BlockGetter world, BlockPos pos) {
        boolean flag = true;
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(pos.getX() - 4, pos.getY() - 1, pos.getZ() - 4, pos.getX() + 4, pos.getY() + 1, pos.getZ() + 4);
        int i = 5;
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();

            if (world.getBlockState(blockposition1).is((Block) this)) {
                --i;
                if (i <= 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        BlockState iblockdata1 = context.getLevel().getBlockState(context.getClickedPos());

        return iblockdata1.is((Block) this) ? this.countFaces(iblockdata1) < VineBlock.PROPERTY_BY_DIRECTION.size() : super.canBeReplaced(state, context);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState iblockdata = ctx.getLevel().getBlockState(ctx.getClickedPos());
        boolean flag = iblockdata.is((Block) this);
        BlockState iblockdata1 = flag ? iblockdata : this.defaultBlockState();
        Direction[] aenumdirection = ctx.getNearestLookingDirections();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (enumdirection != Direction.DOWN) {
                BooleanProperty blockstateboolean = VineBlock.getPropertyForFace(enumdirection);
                boolean flag1 = flag && (Boolean) iblockdata.getValue(blockstateboolean);

                if (!flag1 && this.canSupportAtFace(ctx.getLevel(), ctx.getClickedPos(), enumdirection)) {
                    return (BlockState) iblockdata1.setValue(blockstateboolean, true);
                }
            }
        }

        return flag ? iblockdata1 : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VineBlock.UP, VineBlock.NORTH, VineBlock.EAST, VineBlock.SOUTH, VineBlock.WEST);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.SOUTH))).setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.NORTH))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.EAST))).setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.SOUTH))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.NORTH))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.EAST))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.SOUTH))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    public static BooleanProperty getPropertyForFace(Direction direction) {
        return (BooleanProperty) VineBlock.PROPERTY_BY_DIRECTION.get(direction);
    }
}
