package org.bukkit.event.entity;

import com.google.common.base.Function;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when an entity is damaged by a block
 * <p>
 * For explosions, the Block returned by {@link #getDamager()} has
 * already been cleared. See {@link #getDamagerBlockState()} for a snapshot
 * of the block if it has already been changed.
 */
public class EntityDamageByBlockEvent extends EntityDamageEvent {
    private final Block damager;
    private final org.bukkit.block.BlockState damagerBlockState; // Paper

    public EntityDamageByBlockEvent(@Nullable final Block damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, final double damage) {
        // Paper start
        this(damager, damagee, cause, damage, null);
    }
    @org.jetbrains.annotations.ApiStatus.Internal
    public EntityDamageByBlockEvent(@Nullable final Block damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, final double damage, final @Nullable org.bukkit.block.BlockState damagerBlockState) {
        // Paper end
        super(damagee, cause, damage);
        this.damager = damager;
        this.damagerBlockState = damagerBlockState; // Paper
    }

    public EntityDamageByBlockEvent(@Nullable final Block damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, @NotNull final Map<DamageModifier, Double> modifiers, @NotNull final Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions) {
        // Paper start
        this(damager, damagee, cause, modifiers, modifierFunctions, null);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public EntityDamageByBlockEvent(@Nullable final Block damager, @NotNull final Entity damagee, @NotNull final DamageCause cause, @NotNull final Map<DamageModifier, Double> modifiers, @NotNull final Map<DamageModifier, ? extends Function<? super Double, Double>> modifierFunctions, final @Nullable org.bukkit.block.BlockState damagerBlockState) {
        // Paper end
        super(damagee, cause, modifiers, modifierFunctions);
        this.damager = damager;
        this.damagerBlockState = damagerBlockState; // Paper
    }

    /**
     * Returns the block that damaged the player.
     *
     * @return Block that damaged the player
     */
    @Nullable
    public Block getDamager() {
        return damager;
    }

    // Paper start
    /**
    * Get a capture of the block that directly caused
    * the damage, like a bed or respawn anchor. This
    * block state is not placed so {@link org.bukkit.block.BlockState#isPlaced}
    * will be false.
    * <p>
    * Can be null if the block wasn't changed before the event
    *
    * @return the damager block state or null if not applicable
    */
    public @Nullable org.bukkit.block.BlockState getDamagerBlockState() {
        return this.damagerBlockState;
    }
    // Paper end
}
