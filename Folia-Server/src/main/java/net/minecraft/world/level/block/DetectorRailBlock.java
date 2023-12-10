package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class DetectorRailBlock extends BaseRailBlock {

    public static final EnumProperty<RailShape> SHAPE = BlockStateProperties.RAIL_SHAPE_STRAIGHT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PRESSED_CHECK_PERIOD = 20;

    public DetectorRailBlock(BlockBehaviour.Properties settings) {
        super(true, settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DetectorRailBlock.POWERED, false)).setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH)).setValue(DetectorRailBlock.WATERLOGGED, false));
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!world.isClientSide) {
            if (!(Boolean) state.getValue(DetectorRailBlock.POWERED)) {
                this.checkPressed(world, pos, state);
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(DetectorRailBlock.POWERED)) {
            this.checkPressed(world, pos, state);
        }
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(DetectorRailBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return !(Boolean) state.getValue(DetectorRailBlock.POWERED) ? 0 : (direction == Direction.UP ? 15 : 0);
    }

    private void checkPressed(Level world, BlockPos pos, BlockState state) {
        if (this.canSurvive(state, world, pos)) {
            if (state.getBlock() != this) { return; } // Paper - not our block, don't do anything
            boolean flag = (Boolean) state.getValue(DetectorRailBlock.POWERED);
            boolean flag1 = false;
            List<AbstractMinecart> list = this.getInteractingMinecartOfType(world, pos, AbstractMinecart.class, (entity) -> {
                return true;
            });

            if (!list.isEmpty()) {
                flag1 = true;
            }

            BlockState iblockdata1;
            // CraftBukkit start
            if (flag != flag1) {
                org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());

                BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, flag ? 15 : 0, flag1 ? 15 : 0);
                world.getCraftServer().getPluginManager().callEvent(eventRedstone);

                flag1 = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end

            if (flag1 && !flag) {
                iblockdata1 = (BlockState) state.setValue(DetectorRailBlock.POWERED, true);
                world.setBlock(pos, iblockdata1, 3);
                this.updatePowerToConnected(world, pos, iblockdata1, true);
                world.updateNeighborsAt(pos, this);
                world.updateNeighborsAt(pos.below(), this);
                world.setBlocksDirty(pos, state, iblockdata1);
            }

            if (!flag1 && flag) {
                iblockdata1 = (BlockState) state.setValue(DetectorRailBlock.POWERED, false);
                world.setBlock(pos, iblockdata1, 3);
                this.updatePowerToConnected(world, pos, iblockdata1, false);
                world.updateNeighborsAt(pos, this);
                world.updateNeighborsAt(pos.below(), this);
                world.setBlocksDirty(pos, state, iblockdata1);
            }

            if (flag1) {
                world.scheduleTick(pos, (Block) this, 20);
            }

            world.updateNeighbourForOutputSignal(pos, this);
        }
    }

    protected void updatePowerToConnected(Level world, BlockPos pos, BlockState state, boolean unpowering) {
        RailState minecarttracklogic = new RailState(world, pos, state);
        List<BlockPos> list = minecarttracklogic.getConnections();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();
            BlockState iblockdata1 = !io.papermc.paper.util.TickThread.isTickThreadFor((ServerLevel)world, blockposition1) ? null : world.getBlockStateIfLoaded(blockposition1); // Folia - block updates in unloaded chunks

            if (iblockdata1 != null) world.neighborChanged(iblockdata1, blockposition1, iblockdata1.getBlock(), pos, false); // Folia - block updates in unloaded chunks
        }

    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            BlockState iblockdata2 = this.updateState(state, world, pos, notify);

            this.checkPressed(world, pos, iblockdata2);
        }
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return DetectorRailBlock.SHAPE;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        if ((Boolean) state.getValue(DetectorRailBlock.POWERED)) {
            List<MinecartCommandBlock> list = this.getInteractingMinecartOfType(world, pos, MinecartCommandBlock.class, (entity) -> {
                return true;
            });

            if (!list.isEmpty()) {
                return ((MinecartCommandBlock) list.get(0)).getCommandBlock().getSuccessCount();
            }

            List<AbstractMinecart> list1 = this.getInteractingMinecartOfType(world, pos, AbstractMinecart.class, EntitySelector.CONTAINER_ENTITY_SELECTOR);

            if (!list1.isEmpty()) {
                return AbstractContainerMenu.getRedstoneSignalFromContainer((Container) list1.get(0));
            }
        }

        return 0;
    }

    private <T extends AbstractMinecart> List<T> getInteractingMinecartOfType(Level world, BlockPos pos, Class<T> entityClass, Predicate<Entity> entityPredicate) {
        return world.getEntitiesOfClass(entityClass, this.getSearchBB(pos), entityPredicate);
    }

    private AABB getSearchBB(BlockPos pos) {
        double d0 = 0.2D;

        return new AABB((double) pos.getX() + 0.2D, (double) pos.getY(), (double) pos.getZ() + 0.2D, (double) (pos.getX() + 1) - 0.2D, (double) (pos.getY() + 1) - 0.2D, (double) (pos.getZ() + 1) - 0.2D);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                switch ((RailShape) state.getValue(DetectorRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                }
            case COUNTERCLOCKWISE_90:
                switch ((RailShape) state.getValue(DetectorRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_SOUTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                }
            case CLOCKWISE_90:
                switch ((RailShape) state.getValue(DetectorRailBlock.SHAPE)) {
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_SOUTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.EAST_WEST);
                    case EAST_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH);
                }
            default:
                return state;
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        RailShape blockpropertytrackposition = (RailShape) state.getValue(DetectorRailBlock.SHAPE);

        switch (mirror) {
            case LEFT_RIGHT:
                switch (blockpropertytrackposition) {
                    case ASCENDING_NORTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_SOUTH);
                    case ASCENDING_SOUTH:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_NORTH);
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    default:
                        return super.mirror(state, mirror);
                }
            case FRONT_BACK:
                switch (blockpropertytrackposition) {
                    case ASCENDING_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_WEST);
                    case ASCENDING_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.ASCENDING_EAST);
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    default:
                        break;
                    case SOUTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_WEST);
                    case SOUTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.SOUTH_EAST);
                    case NORTH_WEST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_EAST);
                    case NORTH_EAST:
                        return (BlockState) state.setValue(DetectorRailBlock.SHAPE, RailShape.NORTH_WEST);
                }
        }

        return super.mirror(state, mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DetectorRailBlock.SHAPE, DetectorRailBlock.POWERED, DetectorRailBlock.WATERLOGGED);
    }
}
