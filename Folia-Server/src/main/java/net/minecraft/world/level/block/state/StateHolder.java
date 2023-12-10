package net.minecraft.world.level.block.state;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.properties.Property;

public abstract class StateHolder<O, S> {
    public static final String NAME_TAG = "Name";
    public static final String PROPERTIES_TAG = "Properties";
    public static final Function<Map.Entry<Property<?>, Comparable<?>>, String> PROPERTY_ENTRY_TO_STRING_FUNCTION = new Function<Map.Entry<Property<?>, Comparable<?>>, String>() {
        @Override
        public String apply(@Nullable Map.Entry<Property<?>, Comparable<?>> entry) {
            if (entry == null) {
                return "<NULL>";
            } else {
                Property<?> property = entry.getKey();
                return property.getName() + "=" + this.getName(property, entry.getValue());
            }
        }

        private <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
            return property.getName((T)value);
        }
    };
    protected final O owner;
    private final ImmutableMap<Property<?>, Comparable<?>> values;
    private Table<Property<?>, Comparable<?>, S> neighbours;
    protected final MapCodec<S> propertiesCodec;
    protected final io.papermc.paper.util.table.ZeroCollidingReferenceStateTable optimisedTable; // Paper - optimise state lookup

    protected StateHolder(O owner, ImmutableMap<Property<?>, Comparable<?>> entries, MapCodec<S> codec) {
        this.owner = owner;
        this.values = entries;
        this.propertiesCodec = codec;
        this.optimisedTable = new io.papermc.paper.util.table.ZeroCollidingReferenceStateTable(this, entries); // Paper - optimise state lookup
    }

    public <T extends Comparable<T>> S cycle(Property<T> property) {
        return this.setValue(property, findNextInCollection(property.getPossibleValues(), this.getValue(property)));
    }

    protected static <T> T findNextInCollection(Collection<T> values, T value) {
        Iterator<T> iterator = values.iterator();

        while(iterator.hasNext()) {
            if (iterator.next().equals(value)) {
                if (iterator.hasNext()) {
                    return iterator.next();
                }

                return values.iterator().next();
            }
        }

        return iterator.next();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.owner);
        if (!this.getValues().isEmpty()) {
            stringBuilder.append('[');
            stringBuilder.append(this.getValues().entrySet().stream().map(PROPERTY_ENTRY_TO_STRING_FUNCTION).collect(Collectors.joining(",")));
            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }

    public Collection<Property<?>> getProperties() {
        return Collections.unmodifiableCollection(this.values.keySet());
    }

    public <T extends Comparable<T>> boolean hasProperty(Property<T> property) {
        return this.optimisedTable.get(property) != null; // Paper - optimise state lookup
    }

    public <T extends Comparable<T>> T getValue(Property<T> property) {
        Comparable<?> comparable = this.optimisedTable.get(property); // Paper - optimise state lookup
        if (comparable == null) {
            throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
        } else {
            return property.getValueClass().cast(comparable);
        }
    }

    public <T extends Comparable<T>> Optional<T> getOptionalValue(Property<T> property) {
        Comparable<?> comparable = this.optimisedTable.get(property); // Paper - optimise state lookup
        return comparable == null ? Optional.empty() : Optional.of(property.getValueClass().cast(comparable));
    }

    public <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value) {
        // Paper start - optimise state lookup
        final S ret = (S)this.optimisedTable.get(property, value);
        if (ret == null) {
            throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
        }
        return ret;
        // Paper end - optimise state lookup
    }

    public <T extends Comparable<T>, V extends T> S trySetValue(Property<T> property, V value) {
        Comparable<?> comparable = this.values.get(property);
        if (comparable != null && !comparable.equals(value)) {
            S object = this.neighbours.get(property, value);
            if (object == null) {
                throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
            } else {
                return object;
            }
        } else {
            return (S)this;
        }
    }

    public void populateNeighbours(Map<Map<Property<?>, Comparable<?>>, S> states) {
        if (this.neighbours != null) {
            throw new IllegalStateException();
        } else {
            Table<Property<?>, Comparable<?>, S> table = HashBasedTable.create();

            for(Map.Entry<Property<?>, Comparable<?>> entry : this.values.entrySet()) {
                Property<?> property = entry.getKey();

                for(Comparable<?> comparable : property.getPossibleValues()) {
                    if (!comparable.equals(entry.getValue())) {
                        table.put(property, comparable, states.get(this.makeNeighbourValues(property, comparable)));
                    }
                }
            }

            this.neighbours = (Table<Property<?>, Comparable<?>, S>)(table.isEmpty() ? table : ArrayTable.create(table)); this.optimisedTable.loadInTable((Table)this.neighbours, this.values); // Paper - optimise state lookup
        }
    }

    private Map<Property<?>, Comparable<?>> makeNeighbourValues(Property<?> property, Comparable<?> value) {
        Map<Property<?>, Comparable<?>> map = Maps.newHashMap(this.values);
        map.put(property, value);
        return map;
    }

    public ImmutableMap<Property<?>, Comparable<?>> getValues() {
        return this.values;
    }

    protected static <O, S extends StateHolder<O, S>> Codec<S> codec(Codec<O> codec, Function<O, S> ownerToStateFunction) {
        return codec.dispatch("Name", (stateHolder) -> {
            return stateHolder.owner;
        }, (object) -> {
            S stateHolder = ownerToStateFunction.apply(object);
            return stateHolder.getValues().isEmpty() ? Codec.unit(stateHolder) : stateHolder.propertiesCodec.codec().optionalFieldOf("Properties").xmap((optional) -> {
                return optional.orElse(stateHolder);
            }, Optional::of).codec();
        });
    }
}
