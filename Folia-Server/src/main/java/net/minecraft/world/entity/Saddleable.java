package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public interface Saddleable {
    boolean isSaddleable();

    void equipSaddle(@Nullable SoundSource sound);
    // Paper start - Fix saddles losing nbt data - MC-191591
    default void equipSaddle(final @Nullable SoundSource sound, final @Nullable net.minecraft.world.item.ItemStack stack) {
        this.equipSaddle(sound);
    }
    // Paper end

    default SoundEvent getSaddleSoundEvent() {
        return SoundEvents.HORSE_SADDLE;
    }

    boolean isSaddled();
}
