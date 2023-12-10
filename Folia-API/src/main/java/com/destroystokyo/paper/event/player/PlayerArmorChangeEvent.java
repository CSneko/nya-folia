package com.destroystokyo.paper.event.player;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.bukkit.Material.*;

/**
 * Called when the player themselves change their armor items
 * <p>
 * Not currently called for environmental factors though it <strong>MAY BE IN THE FUTURE</strong>
 */
public class PlayerArmorChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    @NotNull private final SlotType slotType;
    @NotNull private final ItemStack oldItem;
    @NotNull private final ItemStack newItem;

    @ApiStatus.Internal
    public PlayerArmorChangeEvent(@NotNull Player player, @NotNull SlotType slotType, @NotNull ItemStack oldItem, @NotNull ItemStack newItem) {
        super(player);
        this.slotType = slotType;
        this.oldItem = oldItem;
        this.newItem = newItem;
    }

    /**
     * Gets the type of slot being altered.
     *
     * @return type of slot being altered
     */
    @NotNull
    public SlotType getSlotType() {
        return this.slotType;
    }

    /**
     * Gets the existing item that's being replaced
     *
     * @return old item
     */
    @NotNull
    public ItemStack getOldItem() {
        return this.oldItem;
    }

    /**
     * Gets the new item that's replacing the old
     *
     * @return new item
     */
    @NotNull
    public ItemStack getNewItem() {
        return this.newItem;
    }

    @Override
    public String toString() {
        return "ArmorChangeEvent{" + "player=" + player + ", slotType=" + slotType + ", oldItem=" + oldItem + ", newItem=" + newItem + '}';
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum SlotType {
        HEAD(NETHERITE_HELMET, DIAMOND_HELMET, GOLDEN_HELMET, IRON_HELMET, CHAINMAIL_HELMET, LEATHER_HELMET, CARVED_PUMPKIN, PLAYER_HEAD, SKELETON_SKULL, ZOMBIE_HEAD, CREEPER_HEAD, WITHER_SKELETON_SKULL, TURTLE_HELMET, DRAGON_HEAD, PIGLIN_HEAD),
        CHEST(NETHERITE_CHESTPLATE, DIAMOND_CHESTPLATE, GOLDEN_CHESTPLATE, IRON_CHESTPLATE, CHAINMAIL_CHESTPLATE, LEATHER_CHESTPLATE, ELYTRA),
        LEGS(NETHERITE_LEGGINGS, DIAMOND_LEGGINGS, GOLDEN_LEGGINGS, IRON_LEGGINGS, CHAINMAIL_LEGGINGS, LEATHER_LEGGINGS),
        FEET(NETHERITE_BOOTS, DIAMOND_BOOTS, GOLDEN_BOOTS, IRON_BOOTS, CHAINMAIL_BOOTS, LEATHER_BOOTS);

        private final Set<Material> mutableTypes = new HashSet<>();
        private Set<Material> immutableTypes;

        SlotType(Material... types) {
            this.mutableTypes.addAll(Arrays.asList(types));
        }

        /**
         * Gets an immutable set of all allowed material types that can be placed in an
         * armor slot.
         *
         * @return immutable set of material types
         */
        @NotNull
        public Set<Material> getTypes() {
            if (immutableTypes == null) {
                immutableTypes = Collections.unmodifiableSet(mutableTypes);
            }

            return immutableTypes;
        }

        /**
         * Gets the type of slot via the specified material
         *
         * @param material material to get slot by
         * @return slot type the material will go in, or null if it won't
         */
        @Nullable
        public static SlotType getByMaterial(@NotNull Material material) {
            for (SlotType slotType : values()) {
                if (slotType.getTypes().contains(material)) {
                    return slotType;
                }
            }
            return null;
        }

        /**
         * Gets whether this material can be equipped to a slot
         *
         * @param material material to check
         * @return whether this material can be equipped
         */
        public static boolean isEquipable(@NotNull Material material) {
            return getByMaterial(material) != null;
        }
    }
}
