package org.bukkit;

import static org.bukkit.support.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;
import org.junit.jupiter.api.Test;

public class InstrumentTest {
    @Test
    public void getByType() {
        for (Instrument instrument : Instrument.values()) {
            // Paper - byte magic values are still used

            assertThat(Instrument.getByType(instrument.getType()), is(instrument));
        }
    }
}
