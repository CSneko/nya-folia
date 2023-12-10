package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailState {
    private final Level level;
    private final BlockPos pos;
    private final BaseRailBlock block;
    private BlockState state;
    private final boolean isStraight;
    private final List<BlockPos> connections = Lists.newArrayList();

    // Paper start - prevent desync
    public boolean isValid() {
        return this.level.getBlockState(this.pos).getBlock() == this.state.getBlock();
    }
    // Paper end - prevent desync

    public RailState(Level world, BlockPos pos, BlockState state) {
        this.level = world;
        this.pos = pos;
        this.state = state;
        this.block = (BaseRailBlock)state.getBlock();
        RailShape railShape = state.getValue(this.block.getShapeProperty());
        this.isStraight = this.block.isStraight();
        this.updateConnections(railShape);
    }

    public List<BlockPos> getConnections() {
        return this.connections;
    }

    private void updateConnections(RailShape shape) {
        this.connections.clear();
        switch (shape) {
            case NORTH_SOUTH:
                this.connections.add(this.pos.north());
                this.connections.add(this.pos.south());
                break;
            case EAST_WEST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.east());
                break;
            case ASCENDING_EAST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.east().above());
                break;
            case ASCENDING_WEST:
                this.connections.add(this.pos.west().above());
                this.connections.add(this.pos.east());
                break;
            case ASCENDING_NORTH:
                this.connections.add(this.pos.north().above());
                this.connections.add(this.pos.south());
                break;
            case ASCENDING_SOUTH:
                this.connections.add(this.pos.north());
                this.connections.add(this.pos.south().above());
                break;
            case SOUTH_EAST:
                this.connections.add(this.pos.east());
                this.connections.add(this.pos.south());
                break;
            case SOUTH_WEST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.south());
                break;
            case NORTH_WEST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.north());
                break;
            case NORTH_EAST:
                this.connections.add(this.pos.east());
                this.connections.add(this.pos.north());
        }

    }

    private void removeSoftConnections() {
        for(int i = 0; i < this.connections.size(); ++i) {
            RailState railState = this.getRail(this.connections.get(i));
            if (railState != null && railState.connectsTo(this)) {
                this.connections.set(i, railState.pos);
            } else {
                this.connections.remove(i--);
            }
        }

    }

    private boolean hasRail(BlockPos pos) {
        return BaseRailBlock.isRail(this.level, pos) || BaseRailBlock.isRail(this.level, pos.above()) || BaseRailBlock.isRail(this.level, pos.below());
    }

    @Nullable
    private RailState getRail(BlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        if (BaseRailBlock.isRail(blockState)) {
            return new RailState(this.level, pos, blockState);
        } else {
            BlockPos blockPos = pos.above();
            blockState = this.level.getBlockState(blockPos);
            if (BaseRailBlock.isRail(blockState)) {
                return new RailState(this.level, blockPos, blockState);
            } else {
                blockPos = pos.below();
                blockState = this.level.getBlockState(blockPos);
                return BaseRailBlock.isRail(blockState) ? new RailState(this.level, blockPos, blockState) : null;
            }
        }
    }

    private boolean connectsTo(RailState other) {
        return this.hasConnection(other.pos);
    }

    private boolean hasConnection(BlockPos pos) {
        for(int i = 0; i < this.connections.size(); ++i) {
            BlockPos blockPos = this.connections.get(i);
            if (blockPos.getX() == pos.getX() && blockPos.getZ() == pos.getZ()) {
                return true;
            }
        }

        return false;
    }

    protected int countPotentialConnections() {
        int i = 0;

        for(Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.hasRail(this.pos.relative(direction))) {
                ++i;
            }
        }

        return i;
    }

    private boolean canConnectTo(RailState placementHelper) {
        return this.connectsTo(placementHelper) || this.connections.size() != 2;
    }

    private void connectTo(RailState placementHelper) {
        // Paper start - prevent desync
        if (!this.isValid() || !placementHelper.isValid()) {
            return;
        }
        // Paper end - prevent desync
        this.connections.add(placementHelper.pos);
        BlockPos blockPos = this.pos.north();
        BlockPos blockPos2 = this.pos.south();
        BlockPos blockPos3 = this.pos.west();
        BlockPos blockPos4 = this.pos.east();
        boolean bl = this.hasConnection(blockPos);
        boolean bl2 = this.hasConnection(blockPos2);
        boolean bl3 = this.hasConnection(blockPos3);
        boolean bl4 = this.hasConnection(blockPos4);
        RailShape railShape = null;
        if (bl || bl2) {
            railShape = RailShape.NORTH_SOUTH;
        }

        if (bl3 || bl4) {
            railShape = RailShape.EAST_WEST;
        }

        if (!this.isStraight) {
            if (bl2 && bl4 && !bl && !bl3) {
                railShape = RailShape.SOUTH_EAST;
            }

            if (bl2 && bl3 && !bl && !bl4) {
                railShape = RailShape.SOUTH_WEST;
            }

            if (bl && bl3 && !bl2 && !bl4) {
                railShape = RailShape.NORTH_WEST;
            }

            if (bl && bl4 && !bl2 && !bl3) {
                railShape = RailShape.NORTH_EAST;
            }
        }

        if (railShape == RailShape.NORTH_SOUTH) {
            if (BaseRailBlock.isRail(this.level, blockPos.above())) {
                railShape = RailShape.ASCENDING_NORTH;
            }

            if (BaseRailBlock.isRail(this.level, blockPos2.above())) {
                railShape = RailShape.ASCENDING_SOUTH;
            }
        }

        if (railShape == RailShape.EAST_WEST) {
            if (BaseRailBlock.isRail(this.level, blockPos4.above())) {
                railShape = RailShape.ASCENDING_EAST;
            }

            if (BaseRailBlock.isRail(this.level, blockPos3.above())) {
                railShape = RailShape.ASCENDING_WEST;
            }
        }

        if (railShape == null) {
            railShape = RailShape.NORTH_SOUTH;
        }

        this.state = this.state.setValue(this.block.getShapeProperty(), railShape);
        this.level.setBlock(this.pos, this.state, 3);
    }

    private boolean hasNeighborRail(BlockPos pos) {
        RailState railState = this.getRail(pos);
        if (railState == null) {
            return false;
        } else {
            railState.removeSoftConnections();
            return railState.canConnectTo(this);
        }
    }

    public RailState place(boolean powered, boolean forceUpdate, RailShape railShape) {
        BlockPos blockPos = this.pos.north();
        BlockPos blockPos2 = this.pos.south();
        BlockPos blockPos3 = this.pos.west();
        BlockPos blockPos4 = this.pos.east();
        boolean bl = this.hasNeighborRail(blockPos);
        boolean bl2 = this.hasNeighborRail(blockPos2);
        boolean bl3 = this.hasNeighborRail(blockPos3);
        boolean bl4 = this.hasNeighborRail(blockPos4);
        RailShape railShape2 = null;
        boolean bl5 = bl || bl2;
        boolean bl6 = bl3 || bl4;
        if (bl5 && !bl6) {
            railShape2 = RailShape.NORTH_SOUTH;
        }

        if (bl6 && !bl5) {
            railShape2 = RailShape.EAST_WEST;
        }

        boolean bl7 = bl2 && bl4;
        boolean bl8 = bl2 && bl3;
        boolean bl9 = bl && bl4;
        boolean bl10 = bl && bl3;
        if (!this.isStraight) {
            if (bl7 && !bl && !bl3) {
                railShape2 = RailShape.SOUTH_EAST;
            }

            if (bl8 && !bl && !bl4) {
                railShape2 = RailShape.SOUTH_WEST;
            }

            if (bl10 && !bl2 && !bl4) {
                railShape2 = RailShape.NORTH_WEST;
            }

            if (bl9 && !bl2 && !bl3) {
                railShape2 = RailShape.NORTH_EAST;
            }
        }

        if (railShape2 == null) {
            if (bl5 && bl6) {
                railShape2 = railShape;
            } else if (bl5) {
                railShape2 = RailShape.NORTH_SOUTH;
            } else if (bl6) {
                railShape2 = RailShape.EAST_WEST;
            }

            if (!this.isStraight) {
                if (powered) {
                    if (bl7) {
                        railShape2 = RailShape.SOUTH_EAST;
                    }

                    if (bl8) {
                        railShape2 = RailShape.SOUTH_WEST;
                    }

                    if (bl9) {
                        railShape2 = RailShape.NORTH_EAST;
                    }

                    if (bl10) {
                        railShape2 = RailShape.NORTH_WEST;
                    }
                } else {
                    if (bl10) {
                        railShape2 = RailShape.NORTH_WEST;
                    }

                    if (bl9) {
                        railShape2 = RailShape.NORTH_EAST;
                    }

                    if (bl8) {
                        railShape2 = RailShape.SOUTH_WEST;
                    }

                    if (bl7) {
                        railShape2 = RailShape.SOUTH_EAST;
                    }
                }
            }
        }

        if (railShape2 == RailShape.NORTH_SOUTH) {
            if (BaseRailBlock.isRail(this.level, blockPos.above())) {
                railShape2 = RailShape.ASCENDING_NORTH;
            }

            if (BaseRailBlock.isRail(this.level, blockPos2.above())) {
                railShape2 = RailShape.ASCENDING_SOUTH;
            }
        }

        if (railShape2 == RailShape.EAST_WEST) {
            if (BaseRailBlock.isRail(this.level, blockPos4.above())) {
                railShape2 = RailShape.ASCENDING_EAST;
            }

            if (BaseRailBlock.isRail(this.level, blockPos3.above())) {
                railShape2 = RailShape.ASCENDING_WEST;
            }
        }

        if (railShape2 == null) {
            railShape2 = railShape;
        }

        this.updateConnections(railShape2);
        this.state = this.state.setValue(this.block.getShapeProperty(), railShape2);
        if (forceUpdate || this.level.getBlockState(this.pos) != this.state) {
            this.level.setBlock(this.pos, this.state, 3);
            // Paper start - prevent desync
            if (!this.isValid()) {
                return this;
            }
            // Paper end - prevent desync

            for(int i = 0; i < this.connections.size(); ++i) {
                RailState railState = this.getRail(this.connections.get(i));
                if (railState != null && railState.isValid()) { // Paper - prevent desync
                    railState.removeSoftConnections();
                    if (railState.canConnectTo(this)) {
                        railState.connectTo(this);
                    }
                }
            }
        }

        return this;
    }

    public BlockState getState() {
        return this.level.getBlockState(this.pos); // Paper - prevent desync
    }
}
