package net.minecraft.world.level.block.state.properties;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;

public class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> {
    private final ImmutableSet<T> values;
    private final Map<String, T> names = Maps.newHashMap();

    // Paper start - optimise iblockdata state lookup
    private int[] idLookupTable;

    @Override
    public final int getIdFor(final T value) {
        return this.idLookupTable[value.ordinal()];
    }
    // Paper end - optimise iblockdata state lookup

    protected EnumProperty(String name, Class<T> type, Collection<T> values) {
        super(name, type);
        this.values = ImmutableSet.copyOf(values);

        for(T enum_ : values) {
            String string = enum_.getSerializedName();
            if (this.names.containsKey(string)) {
                throw new IllegalArgumentException("Multiple values have the same name '" + string + "'");
            }

            this.names.put(string, enum_);
        }

        // Paper start - optimise iblockdata state lookup
        int id = 0;
        this.idLookupTable = new int[type.getEnumConstants().length];
        java.util.Arrays.fill(this.idLookupTable, -1);
        for (final T value : this.getPossibleValues()) {
            this.idLookupTable[value.ordinal()] = id++;
        }
        // Paper end - optimise iblockdata state lookup
    }

    @Override
    public Collection<T> getPossibleValues() {
        return this.values;
    }

    @Override
    public Optional<T> getValue(String name) {
        return Optional.ofNullable(this.names.get(name));
    }

    @Override
    public String getName(T value) {
        return value.getSerializedName();
    }

    public boolean equals_unused(Object object) { // Paper
        if (this == object) {
            return true;
        } else {
            if (object instanceof EnumProperty) {
                EnumProperty<?> enumProperty = (EnumProperty)object;
                if (super.equals(object)) {
                    return this.values.equals(enumProperty.values) && this.names.equals(enumProperty.names);
                }
            }

            return false;
        }
    }

    @Override
    public int generateHashCode() {
        int i = super.generateHashCode();
        i = 31 * i + this.values.hashCode();
        return 31 * i + this.names.hashCode();
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> type) {
        return create(name, type, (enum_) -> {
            return true;
        });
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> type, Predicate<T> filter) {
        return create(name, type, Arrays.<T>stream(type.getEnumConstants()).filter(filter).collect(Collectors.toList()));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> type, T... values) {
        return create(name, type, Lists.newArrayList(values));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> type, Collection<T> values) {
        return new EnumProperty<>(name, type, values);
    }
}
