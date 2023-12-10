package net.minecraft.world.level.block.state.properties;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Optional;

public class BooleanProperty extends Property<Boolean> {
    private final ImmutableSet<Boolean> values = ImmutableSet.of(true, false);

    // Paper start - optimise iblockdata state lookup
    @Override
    public final int getIdFor(final Boolean value) {
        return value.booleanValue() ? 1 : 0;
    }
    // Paper end - optimise iblockdata state lookup

    protected BooleanProperty(String name) {
        super(name, Boolean.class);
    }

    @Override
    public Collection<Boolean> getPossibleValues() {
        return this.values;
    }

    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }

    @Override
    public Optional<Boolean> getValue(String name) {
        return !"true".equals(name) && !"false".equals(name) ? Optional.empty() : Optional.of(Boolean.valueOf(name));
    }

    @Override
    public String getName(Boolean value) {
        return value.toString();
    }

    public boolean equals_unused(Object object) { // Paper
        if (this == object) {
            return true;
        } else {
            if (object instanceof BooleanProperty) {
                BooleanProperty booleanProperty = (BooleanProperty)object;
                if (super.equals(object)) {
                    return this.values.equals(booleanProperty.values);
                }
            }

            return false;
        }
    }

    @Override
    public int generateHashCode() {
        return 31 * super.generateHashCode() + this.values.hashCode();
    }
}
