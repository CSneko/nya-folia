package io.papermc.paper.event.entity;

import com.google.common.base.Preconditions;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when two entities mate and the mating process results in a fertilization.
 * Fertilization differs from normal breeding, as represented by the {@link org.bukkit.event.entity.EntityBreedEvent}, as
 * it does not result in the immediate creation of the child entity in the world.
 * <p>
 * An example of this would be:
 * <ul>
 * <li>A frog being marked as "is_pregnant" and laying {@link org.bukkit.Material#FROGSPAWN} later.</li>
 * <li>Sniffers producing the {@link org.bukkit.Material#SNIFFER_EGG} item, which needs to be placed before it can begin to hatch.</li>
 * <li>A turtle being marked with "HasEgg" and laying a {@link org.bukkit.Material#TURTLE_EGG} later.</li>
 * </ul>
 *
 * The event hence only exposes the two parent entities in the fertilization process and cannot provide the child entity, as it will only exist at a later point in time.
 */
public class EntityFertilizeEggEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final LivingEntity mother;
    private final LivingEntity father;
    private final Player breeder;
    private final ItemStack bredWith;
    private int experience;

    private boolean cancel;

    public EntityFertilizeEggEvent(@NotNull LivingEntity mother, @NotNull LivingEntity father, @Nullable Player breeder, @Nullable ItemStack bredWith, int experience) {
        super(mother);
        Preconditions.checkArgument(mother != null, "Cannot have null mother");
        Preconditions.checkArgument(father != null, "Cannot have null father");

        // Breeder can be null in the case of spontaneous conception
        this.mother = mother;
        this.father = father;
        this.breeder = breeder;
        this.bredWith = bredWith;
        this.experience = experience;
    }

    @NotNull
    @Override
    public LivingEntity getEntity() {
        return (LivingEntity) entity;
    }

    /**
     * Provides the entity in the fertilization process that will eventually be responsible for "creating" offspring,
     * may that be by setting a block that later hatches or dropping an egg that has to be placed.
     *
     * @return The "mother" entity.
     */
    @NotNull
    public LivingEntity getMother() {
        return mother;
    }

    /**
     * Provides the "father" entity in the fertilization process that is not responsible for initiating the offspring
     * creation.
     *
     * @return the other parent
     */
    @NotNull
    public LivingEntity getFather() {
        return father;
    }

    /**
     * Gets the Entity responsible for fertilization. Breeder is null for spontaneous
     * conception.
     *
     * @return The Entity who initiated breeding.
     */
    @Nullable
    public Player getBreeder() {
        return breeder;
    }

    /**
     * The ItemStack that was used to initiate fertilization, if present.
     *
     * @return ItemStack used to initiate breeding.
     */
    @Nullable
    public ItemStack getBredWith() {
        return bredWith;
    }

    /**
     * Get the amount of experience granted by fertilization.
     *
     * @return experience amount
     */
    public int getExperience() {
        return experience;
    }

    /**
     * Set the amount of experience granted by fertilization.
     * If the amount is negative or zero, no experience will be dropped.
     *
     * @param experience experience amount
     */
    public void setExperience(int experience) {
        this.experience = experience;
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
