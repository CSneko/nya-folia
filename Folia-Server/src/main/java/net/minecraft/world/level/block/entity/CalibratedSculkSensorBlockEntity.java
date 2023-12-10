package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;

public class CalibratedSculkSensorBlockEntity extends SculkSensorBlockEntity {
    public CalibratedSculkSensorBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CALIBRATED_SCULK_SENSOR, pos, state);
    }

    @Override
    public VibrationSystem.User createVibrationUser() {
        return new CalibratedSculkSensorBlockEntity.VibrationUser(this.getBlockPos());
    }
    // Paper start
    @Override
    protected void saveRangeOverride(final net.minecraft.nbt.CompoundTag nbt) {
        if (this.rangeOverride != null && this.rangeOverride != 16) nbt.putInt(PAPER_LISTENER_RANGE_NBT_KEY, this.rangeOverride); // only save if it's different from the default
    }
    // Paper end

    protected class VibrationUser extends SculkSensorBlockEntity.VibrationUser {
        public VibrationUser(BlockPos pos) {
            super(pos);
        }

        @Override
        public int getListenerRadius() {
            if (CalibratedSculkSensorBlockEntity.this.rangeOverride != null) return CalibratedSculkSensorBlockEntity.this.rangeOverride; // Paper
            return 16;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel world, BlockPos pos, GameEvent event, @Nullable GameEvent.Context emitter) {
            int i = this.getBackSignal(world, this.blockPos, CalibratedSculkSensorBlockEntity.this.getBlockState());
            return i != 0 && VibrationSystem.getGameEventFrequency(event) != i ? false : super.canReceiveVibration(world, pos, event, emitter);
        }

        private int getBackSignal(Level world, BlockPos pos, BlockState state) {
            Direction direction = state.getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
            return world.getSignal(pos.relative(direction), direction);
        }
    }
}
