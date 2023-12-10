package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class PoweredRailBlock extends BaseRailBlock {

    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected PoweredRailBlock(BlockBehaviour.Properties settings) {
        super(true, settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH)).setValue(PoweredRailBlock.POWERED, false)).setValue(PoweredRailBlock.WATERLOGGED, false));
    }

    protected boolean findPoweredRailSignal(Level world, BlockPos pos, BlockState state, boolean flag, int distance) {
        if (distance >= 8) {
            return false;
        } else {
            int j = pos.getX();
            int k = pos.getY();
            int l = pos.getZ();
            boolean flag1 = true;
            RailShape blockpropertytrackposition = (RailShape) state.getValue(PoweredRailBlock.SHAPE);

            switch (blockpropertytrackposition) {
                case NORTH_SOUTH:
                    if (flag) {
                        ++l;
                    } else {
                        --l;
                    }
                    break;
                case EAST_WEST:
                    if (flag) {
                        --j;
                    } else {
                        ++j;
                    }
                    break;
                case ASCENDING_EAST:
                    if (flag) {
                        --j;
                    } else {
                        ++j;
                        ++k;
                        flag1 = false;
                    }

                    blockpropertytrackposition = RailShape.EAST_WEST;
                    break;
                case ASCENDING_WEST:
                    if (flag) {
                        --j;
                        ++k;
                        flag1 = false;
                    } else {
                        ++j;
                    }

                    blockpropertytrackposition = RailShape.EAST_WEST;
                    break;
                case ASCENDING_NORTH:
                    if (flag) {
                        ++l;
                    } else {
                        --l;
                        ++k;
                        flag1 = false;
                    }

                    blockpropertytrackposition = RailShape.NORTH_SOUTH;
                    break;
                case ASCENDING_SOUTH:
                    if (flag) {
                        ++l;
                        ++k;
                        flag1 = false;
                    } else {
                        --l;
                    }

                    blockpropertytrackposition = RailShape.NORTH_SOUTH;
            }

            return this.isSameRailWithPower(world, new BlockPos(j, k, l), flag, distance, blockpropertytrackposition) ? true : flag1 && this.isSameRailWithPower(world, new BlockPos(j, k - 1, l), flag, distance, blockpropertytrackposition);
        }
    }

    protected boolean isSameRailWithPower(Level world, BlockPos pos, boolean flag, int distance, RailShape shape) {
        BlockState iblockdata = !io.papermc.paper.util.TickThread.isTickThreadFor((net.minecraft.server.level.ServerLevel)world, pos) ? null : world.getBlockStateIfLoaded(pos); // Folia - block updates in unloaded chunks

        if (iblockdata == null || !iblockdata.is((Block) this)) { // Folia - block updates in unloaded chunks
            return false;
        } else {
            RailShape blockpropertytrackposition1 = (RailShape) iblockdata.getValue(PoweredRailBlock.SHAPE);

            return shape == RailShape.EAST_WEST && (blockpropertytrackposition1 == RailShape.NORTH_SOUTH || blockpropertytrackposition1 == RailShape.ASCENDING_NORTH || blockpropertytrackposition1 == RailShape.ASCENDING_SOUTH) ? false : (shape == RailShape.NORTH_SOUTH && (blockpropertytrackposition1 == RailShape.EAST_WEST || blockpropertytrackposition1 == RailShape.ASCENDING_EAST || blockpropertytrackposition1 == RailShape.ASCENDING_WEST) ? false : ((Boolean) iblockdata.getValue(PoweredRailBlock.POWERED) ? (world.hasNeighborSignal(pos) ? true : this.findPoweredRailSignal(world, pos, iblockdata, flag, distance + 1)) : false));
        }
    }

    @Override
    protected void updateState(BlockState state, Level world, BlockPos pos, Block neighbor) {
        boolean flag = (Boolean) state.getValue(PoweredRailBlock.POWERED);
        boolean flag1 = world.hasNeighborSignal(pos) || this.findPoweredRailSignal(world, pos, state, true, 0) || this.findPoweredRailSignal(world, pos, state, false, 0);

        if (flag1 != flag) {
            // CraftBukkit start
            int power = flag ? 15 : 0;
            int newPower = CraftEventFactory.callRedstoneChange(world, pos, power, 15 - power).getNewCurrent();
            if (newPower == power) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.setValue(PoweredRailBlock.POWERED, flag1), 3);
            world.updateNeighborsAt(pos.below(), this);
            if (((RailShape) state.getValue(PoweredRailBlock.SHAPE)).isAscending()) {
                world.updateNeighborsAt(pos.above(), this);
            }
        }

    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return PoweredRailBlock.SHAPE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                switch ((RailShape) state.getValue(PoweredRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                }
            case COUNTERCLOCKWISE_90:
                switch ((RailShape) state.getValue(PoweredRailBlock.SHAPE)) {
                    case NORTH_SOUTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                }
            case CLOCKWISE_90:
                switch ((RailShape) state.getValue(PoweredRailBlock.SHAPE)) {
                    case NORTH_SOUTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                }
            default:
                return state;
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        RailShape blockpropertytrackposition = (RailShape) state.getValue(PoweredRailBlock.SHAPE);

        switch (mirror) {
            case LEFT_RIGHT:
                switch (blockpropertytrackposition) {
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    default:
                        return super.mirror(state, mirror);
                }
            case FRONT_BACK:
                switch (blockpropertytrackposition) {
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    default:
                        break;
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_WEST);
                }
        }

        return super.mirror(state, mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PoweredRailBlock.SHAPE, PoweredRailBlock.POWERED, PoweredRailBlock.WATERLOGGED);
    }
}
