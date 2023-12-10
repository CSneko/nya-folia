package org.bukkit.inventory.meta;

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.tags.CustomItemTagContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This type represents the storage mechanism for auxiliary item data.
 * <p>
 * An implementation will handle the creation and application for ItemMeta.
 * This class should not be implemented by a plugin in a live environment.
 */
public interface ItemMeta extends Cloneable, ConfigurationSerializable, PersistentDataHolder {

    /**
     * Checks for existence of a display name.
     *
     * @return true if this has a display name
     */
    boolean hasDisplayName();

    // Paper start
    /**
     * Gets the display name.
     *
     * <p>Plugins should check that {@link #hasDisplayName()} returns <code>true</code> before calling this method.</p>
     *
     * @return the display name
     */
    net.kyori.adventure.text.@Nullable Component displayName();

    /**
     * Sets the display name.
     *
     * @param displayName the display name to set
     */
    void displayName(final net.kyori.adventure.text.@Nullable Component displayName);
    // Paper end

    /**
     * Gets the display name that is set.
     * <p>
     * Plugins should check that hasDisplayName() returns <code>true</code>
     * before calling this method.
     *
     * @return the display name that is set
     * @deprecated in favour of {@link #displayName()}
     */
    @Deprecated // Paper
    @NotNull
    String getDisplayName();

    // Paper start
    /**
     * Gets the display name that is set.
     * <p>
     * Plugins should check that hasDisplayName() returns <code>true</code>
     * before calling this method.
     *
     * @return the display name that is set
     * @deprecated use {@link #displayName()}
     */
    @NotNull
    @Deprecated
    net.md_5.bungee.api.chat.BaseComponent[] getDisplayNameComponent();
    // Paper end
    /**
     * Sets the display name.
     *
     * @param name the name to set
     * @deprecated in favour of {@link #displayName(net.kyori.adventure.text.Component)}
     */
    @Deprecated // Paper
    void setDisplayName(@Nullable String name);

    // Paper start
    /**
     * Sets the display name.
     *
     * @param component the name component to set
     * @deprecated use {@link #displayName(Component)}
     */
    @Deprecated
    void setDisplayNameComponent(@Nullable net.md_5.bungee.api.chat.BaseComponent[] component);
    // Paper end
    /**
     * Checks for existence of a localized name.
     *
     * @deprecated Use {@link ItemMeta#displayName()} and check if it is instanceof a {@link net.kyori.adventure.text.TranslatableComponent}.
     * @return true if this has a localized name
     */
    @Deprecated // Paper - Deprecate old localized API
    boolean hasLocalizedName();

    /**
     * Gets the localized display name that is set.
     * <p>
     * Plugins should check that hasLocalizedName() returns <code>true</code>
     * before calling this method.
     *
     * @deprecated Use {@link ItemMeta#displayName()} and cast it to a {@link net.kyori.adventure.text.TranslatableComponent}. No longer used by the client.
     * @return the localized name that is set
     */
    @Deprecated // Paper - Deprecate old localized API
    @NotNull
    String getLocalizedName();

    /**
     * Sets the localized name.
     *
     * @deprecated Use {@link ItemMeta#displayName(Component)} with a {@link net.kyori.adventure.text.TranslatableComponent}. No longer used by the client.
     * @param name the name to set
     */
    @Deprecated // Paper - Deprecate old localized API
    void setLocalizedName(@Nullable String name);

    /**
     * Checks for existence of lore.
     *
     * @return true if this has lore
     */
    boolean hasLore();

    // Paper start
    /**
     * Gets the lore.
     *
     * <p>Plugins should check that {@link #hasLore()} returns <code>true</code> before calling this method.</p>
     *
     * @return the lore
     */
    @Nullable List<net.kyori.adventure.text.Component> lore();

    /**
     * Sets the lore.
     *
     * @param lore the lore to set
     */
    void lore(final @Nullable List<? extends net.kyori.adventure.text.Component> lore);
    // Paper end

    /**
     * Gets the lore that is set.
     * <p>
     * Plugins should check if hasLore() returns <code>true</code> before
     * calling this method.
     *
     * @return a list of lore that is set
     * @deprecated in favour of {@link #lore()}
     */
    @Deprecated // Paper
    @Nullable
    List<String> getLore();

    /**
     * Gets the lore that is set.
     * <p>
     * Plugins should check if hasLore() returns <code>true</code> before
     * calling this method.
     *
     * @return a list of lore that is set
     * @deprecated use {@link #lore()}
     */
    @Nullable
    @Deprecated
    List<net.md_5.bungee.api.chat.BaseComponent[]> getLoreComponents();

    /**
     * Sets the lore for this item.
     * Removes lore when given null.
     *
     * @param lore the lore that will be set
     * @deprecated in favour of {@link #lore(List)}
     */
    @Deprecated // Paper
    void setLore(@Nullable List<String> lore);

    /**
     * Sets the lore for this item.
     * Removes lore when given null.
     *
     * @param lore the lore that will be set
     * @deprecated use {@link #lore(List)}
     */
    @Deprecated
    void setLoreComponents(@Nullable List<net.md_5.bungee.api.chat.BaseComponent[]> lore);

    /**
     * Checks for existence of custom model data.
     * <p>
     * CustomModelData is an integer that may be associated client side with a
     * custom item model.
     *
     * @return true if this has custom model data
     */
    boolean hasCustomModelData();

    /**
     * Gets the custom model data that is set.
     * <p>
     * CustomModelData is an integer that may be associated client side with a
     * custom item model.
     * <p>
     * Plugins should check that hasCustomModelData() returns <code>true</code>
     * before calling this method.
     *
     * @return the custom model data that is set
     */
    int getCustomModelData();

    /**
     * Sets the custom model data.
     * <p>
     * CustomModelData is an integer that may be associated client side with a
     * custom item model.
     *
     * @param data the data to set, or null to clear
     */
    void setCustomModelData(@Nullable Integer data);

    /**
     * Checks for the existence of any enchantments.
     *
     * @return true if an enchantment exists on this meta
     */
    boolean hasEnchants();

    /**
     * Checks for existence of the specified enchantment.
     *
     * @param ench enchantment to check
     * @return true if this enchantment exists for this meta
     */
    boolean hasEnchant(@NotNull Enchantment ench);

    /**
     * Checks for the level of the specified enchantment.
     *
     * @param ench enchantment to check
     * @return The level that the specified enchantment has, or 0 if none
     */
    int getEnchantLevel(@NotNull Enchantment ench);

    /**
     * Returns a copy the enchantments in this ItemMeta. <br>
     * Returns an empty map if none.
     *
     * @return An immutable copy of the enchantments
     */
    @NotNull
    Map<Enchantment, Integer> getEnchants();

    /**
     * Adds the specified enchantment to this item meta.
     *
     * @param ench Enchantment to add
     * @param level Level for the enchantment
     * @param ignoreLevelRestriction this indicates the enchantment should be
     *     applied, ignoring the level limit
     * @return true if the item meta changed as a result of this call, false
     *     otherwise
     */
    boolean addEnchant(@NotNull Enchantment ench, int level, boolean ignoreLevelRestriction);

    /**
     * Removes the specified enchantment from this item meta.
     *
     * @param ench Enchantment to remove
     * @return true if the item meta changed as a result of this call, false
     *     otherwise
     */
    boolean removeEnchant(@NotNull Enchantment ench);

    /**
     * Checks if the specified enchantment conflicts with any enchantments in
     * this ItemMeta.
     *
     * @param ench enchantment to test
     * @return true if the enchantment conflicts, false otherwise
     */
    boolean hasConflictingEnchant(@NotNull Enchantment ench);

    /**
     * Set itemflags which should be ignored when rendering a ItemStack in the Client. This Method does silently ignore double set itemFlags.
     *
     * @param itemFlags The hideflags which shouldn't be rendered
     */
    void addItemFlags(@NotNull ItemFlag... itemFlags);

    /**
     * Remove specific set of itemFlags. This tells the Client it should render it again. This Method does silently ignore double removed itemFlags.
     *
     * @param itemFlags Hideflags which should be removed
     */
    void removeItemFlags(@NotNull ItemFlag... itemFlags);

    /**
     * Get current set itemFlags. The collection returned is unmodifiable.
     *
     * @return A set of all itemFlags set
     */
    @NotNull
    Set<ItemFlag> getItemFlags();

    /**
     * Check if the specified flag is present on this item.
     *
     * @param flag the flag to check
     * @return if it is present
     */
    boolean hasItemFlag(@NotNull ItemFlag flag);

    /**
     * Return if the unbreakable tag is true. An unbreakable item will not lose
     * durability.
     *
     * @return true if the unbreakable tag is true
     */
    boolean isUnbreakable();

    /**
     * Sets the unbreakable tag. An unbreakable item will not lose durability.
     *
     * @param unbreakable true if set unbreakable
     */
    void setUnbreakable(boolean unbreakable);

    /**
     * Checks for the existence of any AttributeModifiers.
     *
     * @return true if any AttributeModifiers exist
     */
    boolean hasAttributeModifiers();

    /**
     * Return an immutable copy of all Attributes and
     * their modifiers in this ItemMeta.<br>
     * Returns null if none exist.
     *
     * @return an immutable {@link Multimap} of Attributes
     *         and their AttributeModifiers, or null if none exist
     */
    @Nullable
    Multimap<Attribute, AttributeModifier> getAttributeModifiers();

    /**
     * Return an immutable copy of all {@link Attribute}s and their
     * {@link AttributeModifier}s for a given {@link EquipmentSlot}.<br>
     * Any {@link AttributeModifier} that does have a given
     * {@link EquipmentSlot} will be returned. This is because
     * AttributeModifiers without a slot are active in any slot.<br>
     * If there are no attributes set for the given slot, an empty map
     * will be returned.
     *
     * @param slot the {@link EquipmentSlot} to check
     * @return the immutable {@link Multimap} with the
     *         respective Attributes and modifiers, or an empty map
     *         if no attributes are set.
     */
    @NotNull
    Multimap<Attribute, AttributeModifier> getAttributeModifiers(@NotNull EquipmentSlot slot);

    /**
     * Return an immutable copy of all {@link AttributeModifier}s
     * for a given {@link Attribute}
     *
     * @param attribute the {@link Attribute}
     * @return an immutable collection of {@link AttributeModifier}s
     *          or null if no AttributeModifiers exist for the Attribute.
     * @throws NullPointerException if Attribute is null
     */
    @Nullable
    Collection<AttributeModifier> getAttributeModifiers(@NotNull Attribute attribute);

    /**
     * Add an Attribute and it's Modifier.
     * AttributeModifiers can now support {@link EquipmentSlot}s.
     * If not set, the {@link AttributeModifier} will be active in ALL slots.
     * <br>
     * Two {@link AttributeModifier}s that have the same {@link java.util.UUID}
     * cannot exist on the same Attribute.
     *
     * @param attribute the {@link Attribute} to modify
     * @param modifier the {@link AttributeModifier} specifying the modification
     * @return true if the Attribute and AttributeModifier were
     *         successfully added
     * @throws NullPointerException if Attribute is null
     * @throws NullPointerException if AttributeModifier is null
     * @throws IllegalArgumentException if AttributeModifier already exists
     */
    boolean addAttributeModifier(@NotNull Attribute attribute, @NotNull AttributeModifier modifier);

    /**
     * Set all {@link Attribute}s and their {@link AttributeModifier}s.
     * To clear all currently set Attributes and AttributeModifiers use
     * null or an empty Multimap.
     * If not null nor empty, this will filter all entries that are not-null
     * and add them to the ItemStack.
     *
     * @param attributeModifiers the new Multimap containing the Attributes
     *                           and their AttributeModifiers
     */
    void setAttributeModifiers(@Nullable Multimap<Attribute, AttributeModifier> attributeModifiers);

    /**
     * Remove all {@link AttributeModifier}s associated with the given
     * {@link Attribute}.
     * This will return false if nothing was removed.
     *
     * @param attribute attribute to remove
     * @return  true if all modifiers were removed from a given
     *                  Attribute. Returns false if no attributes were
     *                  removed.
     * @throws NullPointerException if Attribute is null
     */
    boolean removeAttributeModifier(@NotNull Attribute attribute);

    /**
     * Remove all {@link Attribute}s and {@link AttributeModifier}s for a
     * given {@link EquipmentSlot}.<br>
     * If the given {@link EquipmentSlot} is null, this will remove all
     * {@link AttributeModifier}s that do not have an EquipmentSlot set.
     *
     * @param slot the {@link EquipmentSlot} to clear all Attributes and
     *             their modifiers for
     * @return true if all modifiers were removed that match the given
     *         EquipmentSlot.
     */
    boolean removeAttributeModifier(@NotNull EquipmentSlot slot);

    /**
     * Remove a specific {@link Attribute} and {@link AttributeModifier}.
     * AttributeModifiers are matched according to their {@link java.util.UUID}.
     *
     * @param attribute the {@link Attribute} to remove
     * @param modifier the {@link AttributeModifier} to remove
     * @return if any attribute modifiers were remove
     *
     * @throws NullPointerException if the Attribute is null
     * @throws NullPointerException if the AttributeModifier is null
     *
     * @see AttributeModifier#getUniqueId()
     */
    boolean removeAttributeModifier(@NotNull Attribute attribute, @NotNull AttributeModifier modifier);

    /**
     * Get this ItemMeta as an NBT string.
     * <p>
     * This string should not be relied upon as a serializable value. If
     * serialization is desired, the {@link ConfigurationSerializable} API
     * should be used instead.
     *
     * @return the NBT string
     */
    @NotNull
    String getAsString();

    /**
     * Returns a public custom tag container capable of storing tags on the
     * item.
     *
     * Those tags will be sent to the client with all of their content, so the
     * client is capable of reading them. This will result in the player seeing
     * a NBT Tag notification on the item.
     *
     * These tags can also be modified by the client once in creative mode
     *
     * @return the custom tag container
     * @deprecated this API part has been replaced by the {@link PersistentDataHolder} API.
     * Please use {@link PersistentDataHolder#getPersistentDataContainer()} instead of this.
     */
    @NotNull
    @Deprecated
    CustomItemTagContainer getCustomTagContainer();

    /**
     * Internal use only! Do not use under any circumstances!
     *
     * @param version version
     * @apiNote  internal use only
     */
    @ApiStatus.Internal
    void setVersion(int version);

    @SuppressWarnings("javadoc")
    @NotNull
    ItemMeta clone();

    // Paper start - Add an API for CanPlaceOn and CanDestroy NBT values
    /**
     * Gets set of materials what given item can destroy in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @return Set of materials
     * @deprecated Minecraft does not limit this to the material enum, Use {@link #getDestroyableKeys()} as a replacement
     */
    @Deprecated
    Set<org.bukkit.Material> getCanDestroy();

    /**
     * Sets set of materials what given item can destroy in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @param canDestroy Set of materials
     * @deprecated Minecraft does not limit this to the material enum, Use {@link #setDestroyableKeys(Collection)} as a replacement
     */
    @Deprecated
    void setCanDestroy(Set<org.bukkit.Material> canDestroy);

    /**
     * Gets set of materials where given item can be placed on in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @return Set of materials
     * @deprecated Minecraft does not limit this to the material enum, Use {@link #getPlaceableKeys()} as a replacement
     */
    @Deprecated
    Set<org.bukkit.Material> getCanPlaceOn();

    /**
     * Sets set of materials where given item can be placed on in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @param canPlaceOn Set of materials
     * @deprecated Minecraft does not limit this to the material enum, Use {@link #setPlaceableKeys(Collection)} as a replacement
     */
    @Deprecated
    void setCanPlaceOn(Set<org.bukkit.Material> canPlaceOn);

    /**
     * Gets the collection of namespaced keys that the item can destroy in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @return Set of {@link com.destroystokyo.paper.Namespaced}
     */
    @NotNull
    Set<com.destroystokyo.paper.Namespaced> getDestroyableKeys();

    /**
     * Sets the collection of namespaced keys that the item can destroy in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @param canDestroy Collection of {@link com.destroystokyo.paper.Namespaced}
     */
    void setDestroyableKeys(@NotNull Collection<com.destroystokyo.paper.Namespaced> canDestroy);

    /**
     * Gets the collection of namespaced keys that the item can be placed on in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @return Set of {@link com.destroystokyo.paper.Namespaced}
     */
    @NotNull
    Set<com.destroystokyo.paper.Namespaced> getPlaceableKeys();

    /**
     * Sets the set of namespaced keys that the item can be placed on in {@link org.bukkit.GameMode#ADVENTURE}
     *
     * @param canPlaceOn Collection of {@link com.destroystokyo.paper.Namespaced}
     */
    @NotNull
    void setPlaceableKeys(@NotNull Collection<com.destroystokyo.paper.Namespaced> canPlaceOn);

    /**
     * Checks for the existence of any keys that the item can be placed on
     *
     * @return true if this item has placeable keys
     */
    boolean hasPlaceableKeys();

    /**
     * Checks for the existence of any keys that the item can destroy
     *
     * @return true if this item has destroyable keys
     */
    boolean hasDestroyableKeys();
    // Paper end
}
