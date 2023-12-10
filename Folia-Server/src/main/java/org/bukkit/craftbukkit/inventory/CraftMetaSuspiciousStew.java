package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@DelegateDeserialization(CraftMetaItem.SerializableMeta.class)
public class CraftMetaSuspiciousStew extends CraftMetaItem implements SuspiciousStewMeta {

    static final ItemMetaKey DURATION = new ItemMetaKey("duration", "duration"); // Paper
    static final ItemMetaKey EFFECTS = new ItemMetaKey(net.minecraft.world.item.SuspiciousStewItem.EFFECTS_TAG, "effects"); // Paper
    static final ItemMetaKey ID = new ItemMetaKey("id", "id");

    private List<PotionEffect> customEffects;

    CraftMetaSuspiciousStew(CraftMetaItem meta) {
        super(meta);
        if (!(meta instanceof CraftMetaSuspiciousStew stewMeta)) {
            return;
        }
        if (stewMeta.hasCustomEffects()) {
            this.customEffects = new ArrayList<>(stewMeta.customEffects);
        }
    }

    CraftMetaSuspiciousStew(CompoundTag tag) {
        super(tag);
        if (tag.contains(CraftMetaSuspiciousStew.EFFECTS.NBT)) {
            ListTag list = tag.getList(CraftMetaSuspiciousStew.EFFECTS.NBT, CraftMagicNumbers.NBT.TAG_COMPOUND);
            int length = list.size();
            this.customEffects = new ArrayList<>(length);

            for (int i = 0; i < length; i++) {
                CompoundTag effect = list.getCompound(i);
                PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.fromString(effect.getString(CraftMetaSuspiciousStew.ID.NBT)));
                if (type == null) {
                    continue;
                }
                int duration = effect.getInt(CraftMetaSuspiciousStew.DURATION.NBT);
                this.customEffects.add(new PotionEffect(type, duration, 0));
            }
        }
    }

    CraftMetaSuspiciousStew(Map<String, Object> map) {
        super(map);

        Iterable<?> rawEffectList = SerializableMeta.getObject(Iterable.class, map, CraftMetaSuspiciousStew.EFFECTS.BUKKIT, true);
        if (rawEffectList == null) {
            return;
        }

        for (Object obj : rawEffectList) {
            Preconditions.checkArgument(obj instanceof PotionEffect, "Object (%s) in effect list is not valid", obj.getClass());
            this.addCustomEffect((PotionEffect) obj, true);
        }
    }

    @Override
    void applyToItem(CompoundTag tag) {
        super.applyToItem(tag);

        if (this.customEffects != null) {
            ListTag effectList = new ListTag();
            tag.put(CraftMetaSuspiciousStew.EFFECTS.NBT, effectList);

            for (PotionEffect effect : this.customEffects) {
                CompoundTag effectData = new CompoundTag();
                effectData.putString(CraftMetaSuspiciousStew.ID.NBT, effect.getType().getKey().toString());
                effectData.putInt(CraftMetaSuspiciousStew.DURATION.NBT, effect.getDuration());
                effectList.add(effectData);
            }
        }
    }

    @Override
    boolean isEmpty() {
        return super.isEmpty() && this.isStewEmpty();
    }

    boolean isStewEmpty() {
        return !this.hasCustomEffects();
    }

    @Override
    boolean applicableTo(Material type) {
        return type == Material.SUSPICIOUS_STEW;
    }

    @Override
    public CraftMetaSuspiciousStew clone() {
        CraftMetaSuspiciousStew clone = ((CraftMetaSuspiciousStew) super.clone());
        if (this.customEffects != null) {
            clone.customEffects = new ArrayList<>(this.customEffects);
        }
        return clone;
    }

    @Override
    public boolean hasCustomEffects() {
        return this.customEffects != null;
    }

    @Override
    public List<PotionEffect> getCustomEffects() {
        if (this.hasCustomEffects()) {
            return ImmutableList.copyOf(this.customEffects);
        }
        return ImmutableList.of();
    }

    @Override
    public boolean addCustomEffect(PotionEffect effect, boolean overwrite) {
        Preconditions.checkArgument(effect != null, "Potion effect cannot be null");

        int index = this.indexOfEffect(effect.getType());
        if (index != -1) {
            if (overwrite) {
                PotionEffect old = this.customEffects.get(index);
                if (old.getDuration() == effect.getDuration()) {
                    return false;
                }
                this.customEffects.set(index, effect);
                return true;
            } else {
                return false;
            }
        } else {
            if (this.customEffects == null) {
                this.customEffects = new ArrayList<>();
            }
            this.customEffects.add(effect);
            return true;
        }
    }

    @Override
    public boolean removeCustomEffect(PotionEffectType type) {
        Preconditions.checkArgument(type != null, "Potion effect type cannot be null");

        if (!this.hasCustomEffects()) {
            return false;
        }

        boolean changed = false;
        Iterator<PotionEffect> iterator = this.customEffects.iterator();
        while (iterator.hasNext()) {
            PotionEffect effect = iterator.next();
            if (type.equals(effect.getType())) {
                iterator.remove();
                changed = true;
            }
        }
        if (this.customEffects.isEmpty()) {
            this.customEffects = null;
        }
        return changed;
    }

    @Override
    public boolean hasCustomEffect(PotionEffectType type) {
        Preconditions.checkArgument(type != null, "Potion effect type cannot be null");
        return this.indexOfEffect(type) != -1;
    }

    private int indexOfEffect(PotionEffectType type) {
        if (!this.hasCustomEffects()) {
            return -1;
        }

        for (int i = 0; i < this.customEffects.size(); i++) {
            if (this.customEffects.get(i).getType().equals(type)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean clearCustomEffects() {
        boolean changed = this.hasCustomEffects();
        this.customEffects = null;
        return changed;
    }

    @Override
    int applyHash() {
        final int original;
        int hash = original = super.applyHash();
        if (this.hasCustomEffects()) {
            hash = 73 * hash + this.customEffects.hashCode();
        }
        return original != hash ? CraftMetaSuspiciousStew.class.hashCode() ^ hash : hash;
    }

    @Override
    boolean equalsCommon(CraftMetaItem meta) {
        if (!super.equalsCommon(meta)) {
            return false;
        }
        if (meta instanceof CraftMetaSuspiciousStew that) {
            return (this.hasCustomEffects() ? that.hasCustomEffects() && this.customEffects.equals(that.customEffects) : !that.hasCustomEffects());
        }
        return true;
    }

    @Override
    boolean notUncommon(CraftMetaItem meta) {
        return super.notUncommon(meta) && (meta instanceof CraftMetaSuspiciousStew || this.isStewEmpty());
    }

    @Override
    Builder<String, Object> serialize(Builder<String, Object> builder) {
        super.serialize(builder);

        if (this.hasCustomEffects()) {
            builder.put(CraftMetaSuspiciousStew.EFFECTS.BUKKIT, ImmutableList.copyOf(this.customEffects));
        }

        return builder;
    }
}
