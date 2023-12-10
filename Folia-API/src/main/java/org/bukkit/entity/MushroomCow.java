package org.bukkit.entity;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a mushroom {@link Cow}
 */
public interface MushroomCow extends Cow, io.papermc.paper.entity.Shearable { // Paper

    /**
     * Get the variant of this cow.
     *
     * @return cow variant
     */
    @NotNull
    public Variant getVariant();

    /**
     * Set the variant of this cow.
     *
     * @param variant cow variant
     */
    public void setVariant(@NotNull Variant variant);

    /**
     * Represents the variant of a cow - ie its color.
     */
    public enum Variant {
        /**
         * Red mushroom cow.
         */
        RED,
        /**
         * Brown mushroom cow.
         */
        BROWN;
    }
    // Paper start

    /**
     * Gets how long the effect applied to stew
     * from this mushroom cow is.
     *
     * @return duration of the effect (in ticks)
     */
    int getStewEffectDuration();

    /**
     * Sets how long the effect applied to stew
     * from this mushroom cow is.
     *
     * @param duration duration of the effect (in ticks)
     */
    void setStewEffectDuration(int duration);

    /**
     * Gets the type of effect applied to stew
     * from this mushroom cow is.
     *
     * @return effect type, or null if an effect is currently not set
     */
    @org.jetbrains.annotations.Nullable
    org.bukkit.potion.PotionEffectType getStewEffectType();

    /**
     * Sets the type of effect applied to stew
     * from this mushroom cow is.
     *
     * @param type new effect type
     *             or null if this cow does not give effects
     */
    void setStewEffect(@org.jetbrains.annotations.Nullable org.bukkit.potion.PotionEffectType type);
    // Paper end
}
