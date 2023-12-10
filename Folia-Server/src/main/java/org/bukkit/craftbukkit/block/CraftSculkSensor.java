package org.bukkit.craftbukkit.block;

import com.google.common.base.Preconditions;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import org.bukkit.World;
import org.bukkit.block.SculkSensor;

public class CraftSculkSensor<T extends SculkSensorBlockEntity> extends CraftBlockEntityState<T> implements SculkSensor {

    public CraftSculkSensor(World world, T tileEntity) {
        super(world, tileEntity);
    }

    protected CraftSculkSensor(CraftSculkSensor<T> state) {
        super(state);
    }

    @Override
    public int getLastVibrationFrequency() {
        return this.getSnapshot().getLastVibrationFrequency();
    }

    @Override
    public void setLastVibrationFrequency(int lastVibrationFrequency) {
        Preconditions.checkArgument(0 <= lastVibrationFrequency && lastVibrationFrequency <= 15, "Vibration frequency must be between 0-15");
        this.getSnapshot().lastVibrationFrequency = lastVibrationFrequency;
    }

    @Override
    public CraftSculkSensor<T> copy() {
        return new CraftSculkSensor<>(this);
    }

    // Paper start
    @Override
    public int getListenerRange() {
        return this.getSnapshot().getListener().getListenerRadius();
    }

    @Override
    public void setListenerRange(int range) {
        Preconditions.checkArgument(range > 0, "Vibration listener range must be greater than 0");
        this.getSnapshot().rangeOverride = range;
    }
    // Paper end
}
