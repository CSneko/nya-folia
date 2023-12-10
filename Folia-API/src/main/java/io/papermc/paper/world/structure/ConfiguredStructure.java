package io.papermc.paper.world.structure;

import io.papermc.paper.registry.Reference;
import java.util.Objects;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.StructureType;
import org.bukkit.generator.structure.Structure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a configured structure each with a
 * {@link StructureType}. Multiple ConfiguredStructures can have
 * the same {@link StructureType}.
 * @deprecated use {@link Structure}
 */
@Deprecated(forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "1.21")
public final class ConfiguredStructure implements Keyed {

    public static final Reference<ConfiguredStructure> PILLAGER_OUTPOST = create("pillager_outpost");
    public static final Reference<ConfiguredStructure> MINESHAFT = create("mineshaft");
    public static final Reference<ConfiguredStructure> MINESHAFT_MESA = create("mineshaft_mesa");
    public static final Reference<ConfiguredStructure> WOODLAND_MANSION = create("mansion");
    public static final Reference<ConfiguredStructure> JUNGLE_TEMPLE = create("jungle_pyramid");
    public static final Reference<ConfiguredStructure> DESERT_PYRAMID = create("desert_pyramid");
    public static final Reference<ConfiguredStructure> IGLOO = create("igloo");
    public static final Reference<ConfiguredStructure> SHIPWRECK = create("shipwreck");
    public static final Reference<ConfiguredStructure> SHIPWRECK_BEACHED = create("shipwreck_beached");
    public static final Reference<ConfiguredStructure> SWAMP_HUT = create("swamp_hut");
    public static final Reference<ConfiguredStructure> STRONGHOLD = create("stronghold");
    public static final Reference<ConfiguredStructure> OCEAN_MONUMENT = create("monument");
    public static final Reference<ConfiguredStructure> OCEAN_RUIN_COLD = create("ocean_ruin_cold");
    public static final Reference<ConfiguredStructure> OCEAN_RUIN_WARM = create("ocean_ruin_warm");
    public static final Reference<ConfiguredStructure> FORTRESS = create("fortress");
    public static final Reference<ConfiguredStructure> NETHER_FOSSIL = create("nether_fossil");
    public static final Reference<ConfiguredStructure> END_CITY = create("end_city");
    public static final Reference<ConfiguredStructure> BURIED_TREASURE = create("buried_treasure");
    public static final Reference<ConfiguredStructure> BASTION_REMNANT = create("bastion_remnant");
    public static final Reference<ConfiguredStructure> VILLAGE_PLAINS = create("village_plains");
    public static final Reference<ConfiguredStructure> VILLAGE_DESERT = create("village_desert");
    public static final Reference<ConfiguredStructure> VILLAGE_SAVANNA = create("village_savanna");
    public static final Reference<ConfiguredStructure> VILLAGE_SNOWY = create("village_snowy");
    public static final Reference<ConfiguredStructure> VILLAGE_TAIGA = create("village_taiga");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_STANDARD = create("ruined_portal");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_DESERT = create("ruined_portal_desert");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_JUNGLE = create("ruined_portal_jungle");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_SWAMP = create("ruined_portal_swamp");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_MOUNTAIN = create("ruined_portal_mountain");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_OCEAN = create("ruined_portal_ocean");
    public static final Reference<ConfiguredStructure> RUINED_PORTAL_NETHER = create("ruined_portal_nether");
    // public static final Reference<ConfiguredStructure> ANCIENT_CITY = create("ancient_city"); // TODO remove when upstream adds "jigsaw" StructureType

    private final NamespacedKey key;
    private final StructureType structureType;

    ConfiguredStructure(@NotNull NamespacedKey key, @NotNull StructureType structureType) {
        this.key = key;
        this.structureType = structureType;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return this.key;
    }

    /**
     * Gets the structure type for this configure structure.
     *
     * @return the structure type
     */
    public @NotNull StructureType getStructureType() {
        return this.structureType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfiguredStructure structure = (ConfiguredStructure) o;
        return this.key.equals(structure.key) && this.structureType.equals(structure.structureType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.key, this.structureType);
    }

    @Override
    public String toString() {
        return "ConfiguredStructure{" +
            "key=" + this.key +
            ", structureType=" + this.structureType +
            '}';
    }

    private static @NotNull Reference<ConfiguredStructure> create(@NotNull String name) {
        return Reference.create(Registry.CONFIGURED_STRUCTURE, NamespacedKey.minecraft(name));
    }

    @ApiStatus.Internal
    public @NotNull Structure toModern() {
        return Objects.requireNonNull(Registry.STRUCTURE.get(this.key));
    }

    @ApiStatus.Internal
    public static @Nullable ConfiguredStructure fromModern(@NotNull Structure structure) {
        return Registry.CONFIGURED_STRUCTURE.get(structure.getKey());
    }
}
