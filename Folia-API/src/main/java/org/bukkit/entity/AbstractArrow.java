package org.bukkit.entity;

import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an arrow.
 */
public interface AbstractArrow extends Projectile {

    /**
     * Gets the knockback strength for an arrow, which is the
     * {@link org.bukkit.enchantments.Enchantment#KNOCKBACK KnockBack} level
     * of the bow that shot it.
     *
     * @return the knockback strength value
     */
    public int getKnockbackStrength();

    /**
     * Sets the knockback strength for an arrow.
     *
     * @param knockbackStrength the knockback strength value
     */
    public void setKnockbackStrength(int knockbackStrength);

    /**
     * Gets the base amount of damage this arrow will do.
     *
     * Defaults to 2.0 for a normal arrow with
     * <code>0.5 * (1 + power level)</code> added for arrows fired from
     * enchanted bows.
     *
     * @return base damage amount
     */
    public double getDamage();

    /**
     * Sets the base amount of damage this arrow will do.
     *
     * @param damage new damage amount
     */
    public void setDamage(double damage);

    /**
     * Gets the number of times this arrow can pierce through an entity.
     *
     * @return pierce level
     */
    public int getPierceLevel();

    /**
     * Sets the number of times this arrow can pierce through an entity.
     *
     * Must be between 0 and 127 times.
     *
     * @param pierceLevel new pierce level
     */
    public void setPierceLevel(int pierceLevel);

    /**
     * Gets whether this arrow is critical.
     * <p>
     * Critical arrows have increased damage and cause particle effects.
     * <p>
     * Critical arrows generally occur when a player fully draws a bow before
     * firing.
     *
     * @return true if it is critical
     */
    public boolean isCritical();

    /**
     * Sets whether or not this arrow should be critical.
     *
     * @param critical whether or not it should be critical
     */
    public void setCritical(boolean critical);

    /**
     * Gets whether this arrow is in a block or not.
     * <p>
     * Arrows in a block are motionless and may be picked up by players.
     *
     * @return true if in a block
     */
    public boolean isInBlock();

    /**
     * Gets the block to which this arrow is attached.
     *
     * @return the attached block or null if not attached
     */
    @Nullable
    public Block getAttachedBlock();

    /**
     * Gets the current pickup status of this arrow.
     *
     * @return the pickup status of this arrow.
     */
    @NotNull
    public PickupStatus getPickupStatus();

    /**
     * Sets the current pickup status of this arrow.
     *
     * @param status new pickup status of this arrow.
     */
    public void setPickupStatus(@NotNull PickupStatus status);

    /**
     * Gets if this arrow was shot from a crossbow.
     *
     * @return if shot from a crossbow
     */
    public boolean isShotFromCrossbow();

    /**
     * Sets if this arrow was shot from a crossbow.
     *
     * @param shotFromCrossbow if shot from a crossbow
     */
    public void setShotFromCrossbow(boolean shotFromCrossbow);

    /**
     * Represents the pickup status of this arrow.
     */
    public enum PickupStatus {
        /**
         * The arrow cannot be picked up.
         */
        DISALLOWED,
        /**
         * The arrow can be picked up.
         */
        ALLOWED,
        /**
         * The arrow can only be picked up by players in creative mode.
         */
        CREATIVE_ONLY
    }

    // Paper start
    /**
     * Gets the ItemStack for this arrow.
     *
     * @return The ItemStack, as if a player picked up the arrow
     */
    @NotNull
    org.bukkit.inventory.ItemStack getItemStack();

    /**
     * Sets the amount of ticks this arrow has been alive in the world
     * This is used to determine when the arrow should be automatically despawned.
     *
     * @param ticks lifetime ticks
     */
    void setLifetimeTicks(int ticks);

    /**
     * Gets how many ticks this arrow has been in the world for.
     *
     * @return ticks this arrow has been in the world
     */
    int getLifetimeTicks();

    /**
     * Gets the sound that is played when this arrow hits an entity.
     *
     * @return sound that plays
     */
    @NotNull
    org.bukkit.Sound getHitSound();

    /**
     * Sets the sound that is played when this arrow hits an entity.
     *
     * @param sound sound that is played
     */
    void setHitSound(@NotNull org.bukkit.Sound sound);

    /**
     * Sets this arrow to "noclip" status.
     *
     * @param noPhysics true to set "noclip"
     */
    void setNoPhysics(boolean noPhysics);

    /**
     * Gets if this arrow has "noclip".
     *
     * @return true if noclip is active
     */
    boolean hasNoPhysics();

    /**
     * Gets the {@link PickupRule} for this arrow.
     *
     * <p>This is generally {@link PickupRule#ALLOWED} only if the arrow was
     * <b>not</b> fired from a bow with the infinity enchantment.</p>
     *
     * @return The pickup rule
     * @deprecated Use {@link Arrow#getPickupStatus()} as an upstream compatible replacement for this function
     */
    @Deprecated
    default PickupRule getPickupRule() {
        return PickupRule.valueOf(this.getPickupStatus().name());
    }

    /**
     * Set the rule for which players can pickup this arrow as an item.
     *
     * @param rule The pickup rule
     * @deprecated Use {@link Arrow#setPickupStatus(PickupStatus)} with {@link PickupStatus} as an upstream compatible replacement for this function
     */
    @Deprecated
    default void setPickupRule(PickupRule rule) {
        this.setPickupStatus(PickupStatus.valueOf(rule.name()));
    }

    @Deprecated
    enum PickupRule {
        DISALLOWED,
        ALLOWED,
        CREATIVE_ONLY;
    }
    // Paper end
}
