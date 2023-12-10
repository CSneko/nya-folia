package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class OffsetDoubleList extends AbstractDoubleList {
    public final DoubleList delegate; // Paper - optimise collisions - public
    public final double offset; // Paper - optimise collisions - public

    public OffsetDoubleList(DoubleList oldList, double offset) {
        this.delegate = oldList;
        this.offset = offset;
    }

    public double getDouble(int i) {
        return this.delegate.getDouble(i) + this.offset;
    }

    public int size() {
        return this.delegate.size();
    }
}
