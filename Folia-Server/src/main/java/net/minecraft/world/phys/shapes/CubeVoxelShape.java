package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class CubeVoxelShape extends VoxelShape {
    protected CubeVoxelShape(DiscreteVoxelShape voxels) {
        super(voxels);
        this.initCache(); // Paper - optimise collisions
    }

    @Override
    protected DoubleList getCoords(Direction.Axis axis) {
        return new CubePointRange(this.shape.getSize(axis));
    }

    @Override
    protected int findIndex(Direction.Axis axis, double coord) {
        int i = this.shape.getSize(axis);
        return Mth.floor(Mth.clamp(coord * (double)i, -1.0D, (double)i));
    }
}
