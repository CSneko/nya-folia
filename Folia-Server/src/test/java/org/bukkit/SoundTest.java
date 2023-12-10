package org.bukkit;

import static org.bukkit.support.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

public class SoundTest extends AbstractTestingBase {

    @Test
    public void testGetSound() {
        for (Sound sound : Sound.values()) {
            assertThat(CraftSound.bukkitToMinecraft(sound), is(not(nullValue())), sound.name());
        }
    }

    @Test
    public void testReverse() {
        for (ResourceLocation effect : BuiltInRegistries.SOUND_EVENT.keySet()) {
            assertNotNull(Sound.valueOf(effect.getPath().replace('.', '_').toUpperCase(java.util.Locale.ENGLISH)), effect + "");
        }
    }

    @Test
    public void testCategory() {
        for (SoundCategory category : SoundCategory.values()) {
            assertNotNull(net.minecraft.sounds.SoundSource.valueOf(category.name()), category + "");
        }
    }

    @Test
    public void testCategoryReverse() {
        for (net.minecraft.sounds.SoundSource category : net.minecraft.sounds.SoundSource.values()) {
            assertNotNull(SoundCategory.valueOf(category.name()), category + "");
        }
    }
}
