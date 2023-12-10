package io.papermc.paper.entity;

import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an entity that can be bucketed.
 */
public interface Bucketable extends Entity {

    /**
     * Gets if this entity originated from a bucket.
     *
     * @return originated from bucket
     */
    boolean isFromBucket();

    /**
     * Sets if this entity originated from a bucket.
     *
     * @param fromBucket is from a bucket
     */
    void setFromBucket(boolean fromBucket);

    /**
     * Gets the base itemstack of this entity in a bucket form.
     *
     * @return bucket form
     */
    @NotNull
    ItemStack getBaseBucketItem();

    /**
     * Gets the sound that is played when this entity
     * is picked up in a bucket.
     * @return bucket pickup sound
     */
    @NotNull
    Sound getPickupSound();
}
