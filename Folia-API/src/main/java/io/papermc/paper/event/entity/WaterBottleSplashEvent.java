package io.papermc.paper.event.entity;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.PotionSplashEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Called when a splash water potion "splashes" and affects
 * different entities in different ways.
 */
public class WaterBottleSplashEvent extends PotionSplashEvent {
    private final @NotNull Set<LivingEntity> rehydrate;
    private final @NotNull Set<LivingEntity> extinguish;

    public WaterBottleSplashEvent(
        final @NotNull ThrownPotion potion,
        final @NotNull Map<LivingEntity, Double> affectedEntities,
        final @NotNull Set<LivingEntity> rehydrate,
        final @NotNull Set<LivingEntity> extinguish
    ) {
        super(potion, affectedEntities);
        this.rehydrate = rehydrate;
        this.extinguish = extinguish;
    }

    /**
     * Gets an immutable collection of entities that
     * will take damage as a result of this event. Use
     * other methods on this class to modify which entities
     * take damage.
     *
     * @return an immutable collection of entities
     * @see #doNotDamageAsWaterSensitive(LivingEntity)
     * @see #damageAsWaterSensitive(LivingEntity)
     */
    @NotNull
    public @Unmodifiable Collection<LivingEntity> getToDamage() {
        return this.affectedEntities.entrySet().stream().filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Removes this entity from the group that
     * will be damaged.
     *
     * @param entity entity to remove
     */
    public void doNotDamageAsWaterSensitive(final @NotNull LivingEntity entity) {
        this.affectedEntities.remove(entity);
    }

    /**
     * Adds this entity to the group that
     * will be damaged
     *
     * @param entity entity to add
     */
    public void damageAsWaterSensitive(final @NotNull LivingEntity entity) {
        this.affectedEntities.put(entity, 1.0);
    }

    /**
     * Get a mutable collection of entities
     * that will be rehydrated by this.
     * <p>
     * As of 1.19.3 this only will contain Axolotls as they
     * are the only entity type that can be rehydrated, but
     * it may change in the future.
     *
     * @return the entities
     */
    @NotNull
    public Collection<LivingEntity> getToRehydrate() {
        return this.rehydrate;
    }

    /**
     * Get a mutable collection of entities that will
     * be extinguished as a result of this event.
     *
     * @return entities to be extinguished
     */
    @NotNull
    public Collection<LivingEntity> getToExtinguish() {
        return this.extinguish;
    }

    /**
     * @return a confusing collection, don't use it
     * @deprecated Use {@link #getToDamage()}
     */
    @Deprecated
    @Override
    public @NotNull Collection<LivingEntity> getAffectedEntities() {
        return super.getAffectedEntities();
    }

    /**
     * Doesn't make sense for this event as intensity doesn't vary.
     * @return a confusing value
     * @deprecated check if {@link #getToDamage()} contains an entity
     */
    @Deprecated
    @Override
    public double getIntensity(final @NotNull LivingEntity entity) {
        return super.getIntensity(entity);
    }

    /**
     * Doesn't make sense for this event as intensity doesn't vary.
     * @deprecated use {@link #damageAsWaterSensitive(LivingEntity)}
     * or {@link #doNotDamageAsWaterSensitive(LivingEntity)} to change which entities are
     * damaged
     */
    @Deprecated
    @Override
    public void setIntensity(final @NotNull LivingEntity entity, final double intensity) {
        super.setIntensity(entity, intensity);
    }
}
