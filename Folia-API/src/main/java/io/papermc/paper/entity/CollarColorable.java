package io.papermc.paper.entity;

import org.bukkit.DyeColor;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Entities that can have their collars colored.
 */
public interface CollarColorable extends LivingEntity {

    /**
     * Get the collar color of this entity
     *
     * @return the color of the collar
     */
    @NotNull DyeColor getCollarColor();

    /**
     * Set the collar color of this entity
     *
     * @param color the color to apply
     */
    void setCollarColor(@NotNull DyeColor color);
}
