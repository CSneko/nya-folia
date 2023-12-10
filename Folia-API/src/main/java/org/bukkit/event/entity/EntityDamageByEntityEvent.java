package org.bukkit.event.entity;

import com.google.common.base.Function;
import java.util.Map;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an entity is damaged by an entity
 */
public class EntityDamageByEntityEvent extends EntityDamageEvent {
    private final Entity damager;

    @Deprecated // Paper - add critical damage API
    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, final double damage) {
        super(damagee, cause, damage);
        this.damager = damager;
        this.critical = false; // Paper - add critical damage API
    }

    @Deprecated // Paper - add critical damage API
    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, @NotNull final Map<DamageModifier, Double> modifiers, @NotNull final Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions) {
        // Paper start - add critical damage API
        this(damager, damagee, cause, modifiers, modifierFunctions, false);
    }

    private final boolean critical;
    public EntityDamageByEntityEvent(@NotNull final Entity damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, @NotNull final Map<DamageModifier, Double> modifiers, @NotNull final Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions, boolean critical) {
        // Paper end
        super(damagee, cause, modifiers, modifierFunctions);
        this.damager = damager;
        // Paper start - add critical damage API
        this.critical = critical;
    }

    /**
     * Shows this damage instance was critical.
     * The damage instance can be critical if the attacking player met the respective conditions.
     * Furthermore arrows may also cause a critical damage event if the arrow {@link org.bukkit.entity.AbstractArrow#isCritical()}.
     *
     * @return if the hit was critical.
     * @see <a href="https://minecraft.wiki/wiki/Damage#Critical_hit">https://minecraft.wiki/wiki/Damage#Critical_hit</a>
     */
    public boolean isCritical() {
        return this.critical;
    }
    // Paper end

    /**
     * Returns the entity that damaged the defender.
     *
     * @return Entity that damaged the defender.
     */
    @NotNull
    public Entity getDamager() {
        return damager;
    }
}
