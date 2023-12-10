package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AttributeMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Attribute, AttributeInstance> attributes = Maps.newHashMap();
    private final Set<AttributeInstance> dirtyAttributes = Sets.newHashSet();
    private final AttributeSupplier supplier;

    public AttributeMap(AttributeSupplier defaultAttributes) {
        this.supplier = defaultAttributes;
    }

    private void onAttributeModified(AttributeInstance instance) {
        if (instance.getAttribute().isClientSyncable()) {
            this.dirtyAttributes.add(instance);
        }

    }

    public Set<AttributeInstance> getDirtyAttributes() {
        return this.dirtyAttributes;
    }

    public Collection<AttributeInstance> getSyncableAttributes() {
        return this.attributes.values().stream().filter((attribute) -> {
            return attribute.getAttribute().isClientSyncable();
        }).collect(Collectors.toList());
    }

    @Nullable
    public AttributeInstance getInstance(Attribute attribute) {
        return this.attributes.computeIfAbsent(attribute, (attributex) -> {
            return this.supplier.createInstance(this::onAttributeModified, attributex);
        });
    }

    @Nullable
    public AttributeInstance getInstance(Holder<Attribute> attribute) {
        return this.getInstance(attribute.value());
    }

    public boolean hasAttribute(Attribute attribute) {
        return this.attributes.get(attribute) != null || this.supplier.hasAttribute(attribute);
    }

    public boolean hasAttribute(Holder<Attribute> attribute) {
        return this.hasAttribute(attribute.value());
    }

    public boolean hasModifier(Attribute attribute, UUID uuid) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(uuid) != null : this.supplier.hasModifier(attribute, uuid);
    }

    public boolean hasModifier(Holder<Attribute> attribute, UUID uuid) {
        return this.hasModifier(attribute.value(), uuid);
    }

    public double getValue(Attribute attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getValue() : this.supplier.getValue(attribute);
    }

    public double getBaseValue(Attribute attribute) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getBaseValue() : this.supplier.getBaseValue(attribute);
    }

    public double getModifierValue(Attribute attribute, UUID uuid) {
        AttributeInstance attributeInstance = this.attributes.get(attribute);
        return attributeInstance != null ? attributeInstance.getModifier(uuid).getAmount() : this.supplier.getModifierValue(attribute, uuid);
    }

    public double getModifierValue(Holder<Attribute> attribute, UUID uuid) {
        return this.getModifierValue(attribute.value(), uuid);
    }

    public void removeAttributeModifiers(Multimap<Attribute, AttributeModifier> attributeModifiers) {
        attributeModifiers.asMap().forEach((attribute, modifiers) -> {
            AttributeInstance attributeInstance = this.attributes.get(attribute);
            if (attributeInstance != null) {
                modifiers.forEach((modifier) -> {
                    attributeInstance.removeModifier(modifier.getId());
                });
            }

        });
    }

    public void addTransientAttributeModifiers(Multimap<Attribute, AttributeModifier> attributeModifiers) {
        attributeModifiers.forEach((attribute, attributeModifier) -> {
            AttributeInstance attributeInstance = this.getInstance(attribute);
            if (attributeInstance != null) {
                attributeInstance.removeModifier(attributeModifier.getId());
                attributeInstance.addTransientModifier(attributeModifier);
            }

        });
    }

    public void assignValues(AttributeMap other) {
        other.attributes.values().forEach((attributeInstance) -> {
            AttributeInstance attributeInstance2 = this.getInstance(attributeInstance.getAttribute());
            if (attributeInstance2 != null) {
                attributeInstance2.replaceFrom(attributeInstance);
            }

        });
    }

    public ListTag save() {
        ListTag listTag = new ListTag();

        for(AttributeInstance attributeInstance : this.attributes.values()) {
            listTag.add(attributeInstance.save());
        }

        return listTag;
    }

    public void load(ListTag nbt) {
        for(int i = 0; i < nbt.size(); ++i) {
            CompoundTag compoundTag = nbt.getCompound(i);
            String string = compoundTag.getString("Name");
            Util.ifElse(BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.tryParse(string)), (attribute) -> {
                AttributeInstance attributeInstance = this.getInstance(attribute);
                if (attributeInstance != null) {
                    attributeInstance.load(compoundTag);
                }

            }, () -> {
                LOGGER.warn("Ignoring unknown attribute '{}'", (Object)string);
            });
        }

    }

    // Paper - start
    public void registerAttribute(Attribute attributeBase) {
        AttributeInstance attributeModifiable = new AttributeInstance(attributeBase, AttributeInstance::getAttribute);
        attributes.put(attributeBase, attributeModifiable);
    }
    // Paper - end

}
