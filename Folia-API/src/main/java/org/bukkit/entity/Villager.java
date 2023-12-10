package org.bukkit.entity;

import java.util.Locale;
import java.util.Map; // Paper
import java.util.UUID; // Paper
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a villager NPC
 */
public interface Villager extends AbstractVillager {

    /**
     * Gets the current profession of this villager.
     *
     * @return Current profession.
     */
    @NotNull
    public Profession getProfession();

    /**
     * Sets the new profession of this villager.
     *
     * @param profession New profession.
     */
    public void setProfession(@NotNull Profession profession);

    /**
     * Gets the current type of this villager.
     *
     * @return Current type.
     */
    @NotNull
    public Type getVillagerType();

    /**
     * Sets the new type of this villager.
     *
     * @param type New type.
     */
    public void setVillagerType(@NotNull Type type);

    /**
     * Gets the level of this villager.
     *
     * A villager with a level of 1 and no experience is liable to lose its
     * profession.
     *
     * @return this villager's level
     */
    public int getVillagerLevel();

    /**
     * Sets the level of this villager.
     *
     * A villager with a level of 1 and no experience is liable to lose its
     * profession.
     *
     * This doesn't update the trades of this villager.
     *
     * @param level the new level
     * @throws IllegalArgumentException if level not between [1, 5]
     * @see #increaseLevel(int)
     */
    public void setVillagerLevel(int level);

    /**
     * Gets the trading experience of this villager.
     *
     * @return trading experience
     */
    public int getVillagerExperience();

    /**
     * Sets the trading experience of this villager.
     *
     * @param experience new experience
     * @throws IllegalArgumentException if experience &lt; 0
     */
    public void setVillagerExperience(int experience);

    // Paper start
    /**
     * Increases the level of this villager.
     * The villager will also unlock new recipes unlike the raw
     * method {@link #setVillagerLevel(int)}.
     * <p>
     * A villager with a level of 1 and no experience is liable to lose its
     * profession.
     * <p>
     * A master villager has a level of 5 in its profession and
     * will unlock 10 trades (2 per level).
     *
     * @param amount The amount of level
     * @return Whether trades are unlocked
     * @throws IllegalArgumentException if current level plus the amount
     * isn't between [1, 5] or the amount isn't positive
     * @see #setVillagerLevel(int)
     */
    boolean increaseLevel(int amount);

    /**
     * Gives to this villager some potential new trades
     * based to its profession and level.
     * @param amount The amount of trades to give
     * @return Whether trades are added
     * @throws IllegalArgumentException if the amount isn't positive
     */
    boolean addTrades(int amount);

    /**
     * Gets the amount of times a villager has restocked their trades today
     * @return The amount of trade restocks.
     */
    public int getRestocksToday();

    /**
     * Sets the amount of times a villager has restocked their trades today
     * @param restocksToday new restock count
     */
    public void setRestocksToday(int restocksToday);
    // Paper end

    /**
     * Attempts to make this villager sleep at the given location.
     * <br>
     * The location must be in the current world and have a bed placed at the
     * location. The villager will put its head on the specified block while
     * sleeping.
     *
     * @param location the location of the bed
     * @return whether the sleep was successful
     */
    public boolean sleep(@NotNull Location location);

    /**
     * Causes this villager to wake up if he's currently sleeping.
     *
     * @throws IllegalStateException if not sleeping
     */
    public void wakeup();

    /**
     * Causes this villager to shake his head.
     */
    public void shakeHead();

    /**
     * Convert this Villager into a ZombieVillager as if it was killed by a
     * Zombie.
     *
     * <b>Note:</b> this will fire a EntityTransformEvent
     *
     * @return the converted entity {@link ZombieVillager} or null if the
     * conversion its cancelled
     */
    @Nullable
    public ZombieVillager zombify();

    /**
     * Represents Villager type, usually corresponding to what biome they spawn
     * in.
     */
    public enum Type implements Keyed {

        DESERT,
        JUNGLE,
        PLAINS,
        SAVANNA,
        SNOW,
        SWAMP,
        TAIGA;
        private final NamespacedKey key;

        private Type() {
            this.key = NamespacedKey.minecraft(this.name().toLowerCase(Locale.ROOT));
        }

        @NotNull
        @Override
        public NamespacedKey getKey() {
            return key;
        }
    }

    /**
     * Represents the various different Villager professions there may be.
     * Villagers have different trading options depending on their profession,
     */
    public enum Profession implements Keyed, net.kyori.adventure.translation.Translatable { // Paper
        NONE,
        /**
         * Armorer profession. Wears a black apron. Armorers primarily trade for
         * iron armor, chainmail armor, and sometimes diamond armor.
         */
        ARMORER,
        /**
         * Butcher profession. Wears a white apron. Butchers primarily trade for
         * raw and cooked food.
         */
        BUTCHER,
        /**
         * Cartographer profession. Wears a white robe. Cartographers primarily
         * trade for explorer maps and some paper.
         */
        CARTOGRAPHER,
        /**
         * Cleric profession. Wears a purple robe. Clerics primarily trade for
         * rotten flesh, gold ingot, redstone, lapis, ender pearl, glowstone,
         * and bottle o' enchanting.
         */
        CLERIC,
        /**
         * Farmer profession. Wears a brown robe. Farmers primarily trade for
         * food-related items.
         */
        FARMER,
        /**
         * Fisherman profession. Wears a brown robe. Fisherman primarily trade
         * for fish, as well as possibly selling string and/or coal.
         */
        FISHERMAN,
        /**
         * Fletcher profession. Wears a brown robe. Fletchers primarily trade
         * for string, bows, and arrows.
         */
        FLETCHER,
        /**
         * Leatherworker profession. Wears a white apron. Leatherworkers
         * primarily trade for leather, and leather armor, as well as saddles.
         */
        LEATHERWORKER,
        /**
         * Librarian profession. Wears a white robe. Librarians primarily trade
         * for paper, books, and enchanted books.
         */
        LIBRARIAN,
        /**
         * Mason profession.
         */
        MASON,
        /**
         * Nitwit profession. Wears a green apron, cannot trade. Nitwit
         * villagers do not do anything. They do not have any trades by default.
         */
        NITWIT,
        /**
         * Shepherd profession. Wears a brown robe. Shepherds primarily trade for
         * wool items, and shears.
         */
        SHEPHERD,
        /**
         * Toolsmith profession. Wears a black apron. Tool smiths primarily
         * trade for iron and diamond tools.
         */
        TOOLSMITH,
        /**
         * Weaponsmith profession. Wears a black apron. Weapon smiths primarily
         * trade for iron and diamond weapons, sometimes enchanted.
         */
        WEAPONSMITH;
        private final NamespacedKey key;

        private Profession() {
            this.key = NamespacedKey.minecraft(this.name().toLowerCase(Locale.ROOT));
        }

        @NotNull
        @Override
        public NamespacedKey getKey() {
            return key;
        }

        // Paper start
        @Override
        public @NotNull String translationKey() {
            return "entity.minecraft.villager." + this.key.getKey();
        }
        // Paper end
    }

    // Paper start - Add villager reputation API
    /**
     * Get the {@link com.destroystokyo.paper.entity.villager.Reputation reputation}
     * for a specific player by {@link UUID}.
     *
     * @param uniqueId The {@link UUID} of the player to get the reputation of.
     * @return The player's copied reputation with this villager.
     */
    @Nullable
    public com.destroystokyo.paper.entity.villager.Reputation getReputation(@NotNull UUID uniqueId);

    /**
     * Get all {@link com.destroystokyo.paper.entity.villager.Reputation reputations}
     * for all players mapped by their {@link UUID unique IDs}.
     *
     * @return All {@link com.destroystokyo.paper.entity.villager.Reputation reputations} for all players
     * in a copied map.
     */
    @NotNull
    public Map<UUID, com.destroystokyo.paper.entity.villager.Reputation> getReputations();

    /**
     * Set the {@link com.destroystokyo.paper.entity.villager.Reputation reputation}
     * for a specific player by {@link UUID}.
     *
     * @param uniqueId The {@link UUID} of the player to set the reputation of.
     * @param reputation The {@link com.destroystokyo.paper.entity.villager.Reputation reputation} to set.
     */
    public void setReputation(@NotNull UUID uniqueId, @NotNull com.destroystokyo.paper.entity.villager.Reputation reputation);

    /**
     * Set all {@link com.destroystokyo.paper.entity.villager.Reputation reputations}
     * for all players mapped by their {@link UUID unique IDs}.
     *
     * @param reputations All {@link com.destroystokyo.paper.entity.villager.Reputation reputations}
     * for all players mapped by their {@link UUID unique IDs}.
     */
    public void setReputations(@NotNull Map<UUID, com.destroystokyo.paper.entity.villager.Reputation> reputations);

    /**
     * Clear all reputations from this villager. This removes every single
     * reputation regardless of its impact and the player associated.
     */
    public void clearReputations();
    // Paper end
}
