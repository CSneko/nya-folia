package org.bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.LevelEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EffectTest {

    private static List<Integer> collectNmsLevelEvents() throws ReflectiveOperationException {
        final List<Integer> events = new ArrayList<>();
        for (final Field field : LevelEvent.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType() == int.class) {
                events.add((int) field.get(null));
            }
        }
        return events;
    }

    private static boolean isNotDeprecated(Effect effect) throws ReflectiveOperationException {
        return !Effect.class.getDeclaredField(effect.name()).isAnnotationPresent(Deprecated.class);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void checkAllApiExists() throws ReflectiveOperationException {
        Map<Integer, Effect> toId = new HashMap<>();
        for (final Effect effect : Effect.values()) {
            if (isNotDeprecated(effect)) {
                final Effect put = toId.put(effect.getId(), effect);
                assertNull(put, "duplicate API effect: " + put);
            }
        }

        for (final Integer event : collectNmsLevelEvents()) {
            assertNotNull(toId.get(event), "missing API Effect: " + event);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void checkNoExtraApi() throws ReflectiveOperationException {
        Map<Integer, Effect> toId = new HashMap<>();
        for (final Effect effect : Effect.values()) {
            if (isNotDeprecated(effect)) {
                final Effect put = toId.put(effect.getId(), effect);
                assertNull(put, "duplicate API effect: " + put);
            }
        }

        final List<Integer> nmsEvents = collectNmsLevelEvents();
        for (final Map.Entry<Integer, Effect> entry : toId.entrySet()) {
            assertTrue(nmsEvents.contains(entry.getKey()), "Extra API Effect: " + entry.getValue());
        }
    }
}
