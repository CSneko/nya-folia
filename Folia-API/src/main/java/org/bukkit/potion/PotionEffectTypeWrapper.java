package org.bukkit.potion;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

public class PotionEffectTypeWrapper extends PotionEffectType {
    protected PotionEffectTypeWrapper(int id, @NotNull String name) {
        super(id, NamespacedKey.minecraft(name));
    }

    @Override
    public double getDurationModifier() {
        return getType().getDurationModifier();
    }

    @NotNull
    @Override
    public String getName() {
        return getType().getName();
    }

    /**
     * Get the potion type bound to this wrapper.
     *
     * @return The potion effect type
     */
    @NotNull
    public PotionEffectType getType() {
        return PotionEffectType.getByKey(super.getKey()); // Paper
    }

    @Override
    public boolean isInstant() {
        return getType().isInstant();
    }

    @NotNull
    @Override
    public Color getColor() {
        return getType().getColor();
    }
    // Paper start
    @Override
    public @NotNull org.bukkit.NamespacedKey getKey() {
        return this.getType().getKey();
    }

    @Override
    public @NotNull java.util.Map<org.bukkit.attribute.Attribute, org.bukkit.attribute.AttributeModifier> getEffectAttributes() {
        return this.getType().getEffectAttributes();
    }

    @Override
    public double getAttributeModifierAmount(@NotNull org.bukkit.attribute.Attribute attribute, int effectAmplifier) {
        return this.getType().getAttributeModifierAmount(attribute, effectAmplifier);
    }

    @Override
    public @NotNull PotionEffectType.Category getEffectCategory() {
        return this.getType().getEffectCategory();
    }

    @Override
    public @NotNull String translationKey() {
        return this.getType().translationKey();
    }
    // Paper end
}
