package org.bukkit.inventory.meta;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a spawn egg and it's spawned type.
 */
public interface SpawnEggMeta extends ItemMeta {

    /**
     * Get the type of entity this egg will spawn.
     *
     * @return The entity type. May be null for implementation specific default.
     * @deprecated different types are different items
     */
    @Deprecated(forRemoval = true) // Paper
    @Contract("-> fail")
    EntityType getSpawnedType();

    /**
     * Set the type of entity this egg will spawn.
     *
     * @param type The entity type. May be null for implementation specific
     * default.
     * @deprecated different types are different items
     */
    @Deprecated(forRemoval = true) // Paper
    @Contract("_ -> fail")
    void setSpawnedType(EntityType type);

    // Paper start
    /**
     * Get the custom type of entity this egg will spawn.
     *
     * @return the entity type or null if no custom type is set
     */
    @org.jetbrains.annotations.Nullable EntityType getCustomSpawnedType();

    /**
     * Set the custom type of entity this egg will spawn.
     *
     * @param type the entity type or null to clear the custom type
     */
    void setCustomSpawnedType(@org.jetbrains.annotations.Nullable EntityType type);
    // Paper end

    @NotNull
    @Override
    SpawnEggMeta clone();
}
