package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;

public class SliceShape extends VoxelShape {
    private final VoxelShape delegate;
    private final Direction.Axis axis;
    private static final DoubleList SLICE_COORDS = new CubePointRange(1);

    public SliceShape(VoxelShape shape, Direction.Axis axis, int sliceWidth) {
        super(makeSlice(shape.shape, axis, sliceWidth));
        this.delegate = shape;
        this.axis = axis;
        this.initCache(); // Paper - optimise collisions
    }

    private static DiscreteVoxelShape makeSlice(DiscreteVoxelShape voxelSet, Direction.Axis axis, int sliceWidth) {
        return new SubShape(voxelSet, axis.choose(sliceWidth, 0, 0), axis.choose(0, sliceWidth, 0), axis.choose(0, 0, sliceWidth), axis.choose(sliceWidth + 1, voxelSet.xSize, voxelSet.xSize), axis.choose(voxelSet.ySize, sliceWidth + 1, voxelSet.ySize), axis.choose(voxelSet.zSize, voxelSet.zSize, sliceWidth + 1));
    }

    @Override
    protected DoubleList getCoords(Direction.Axis axis) {
        return axis == this.axis ? SLICE_COORDS : this.delegate.getCoords(axis);
    }
}
