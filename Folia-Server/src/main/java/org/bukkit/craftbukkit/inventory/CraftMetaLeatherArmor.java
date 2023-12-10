package org.bukkit.craftbukkit.inventory;

import static org.bukkit.craftbukkit.inventory.CraftItemFactory.*;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.CraftMetaItem.SerializableMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

@DelegateDeserialization(SerializableMeta.class)
class CraftMetaLeatherArmor extends CraftMetaItem implements LeatherArmorMeta {

    private static final Set<Material> LEATHER_ARMOR_MATERIALS = Sets.newHashSet(
            Material.LEATHER_HELMET,
            Material.LEATHER_HORSE_ARMOR,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS
    );

    static final ItemMetaKey COLOR = new ItemMetaKey("color");

    private Color color = DEFAULT_LEATHER_COLOR;

    CraftMetaLeatherArmor(CraftMetaItem meta) {
        super(meta);
        CraftMetaLeatherArmor.readColor(this, meta);
    }

    CraftMetaLeatherArmor(CompoundTag tag) {
        super(tag);
        CraftMetaLeatherArmor.readColor(this, tag);
    }

    CraftMetaLeatherArmor(Map<String, Object> map) {
        super(map);
        CraftMetaLeatherArmor.readColor(this, map);
    }

    @Override
    void applyToItem(CompoundTag itemTag) {
        super.applyToItem(itemTag);
        CraftMetaLeatherArmor.applyColor(this, itemTag);
    }

    @Override
    boolean isEmpty() {
        return super.isEmpty() && this.isLeatherArmorEmpty();
    }

    boolean isLeatherArmorEmpty() {
        return !(this.hasColor());
    }

    @Override
    boolean applicableTo(Material type) {
        return CraftMetaLeatherArmor.LEATHER_ARMOR_MATERIALS.contains(type);
    }

    @Override
    public CraftMetaLeatherArmor clone() {
        return (CraftMetaLeatherArmor) super.clone();
    }

    @Override
    public Color getColor() {
        return this.color;
    }

    @Override
    public void setColor(Color color) {
        this.color = color == null ? DEFAULT_LEATHER_COLOR : color;
    }

    boolean hasColor() {
        return CraftMetaLeatherArmor.hasColor(this);
    }

    @Override
    Builder<String, Object> serialize(Builder<String, Object> builder) {
        super.serialize(builder);

        CraftMetaLeatherArmor.serialize(this, builder);

        return builder;
    }

    @Override
    boolean equalsCommon(CraftMetaItem meta) {
        if (!super.equalsCommon(meta)) {
            return false;
        }
        if (meta instanceof CraftMetaLeatherArmor) {
            CraftMetaLeatherArmor that = (CraftMetaLeatherArmor) meta;

            return this.color.equals(that.color);
        }
        return true;
    }

    @Override
    boolean notUncommon(CraftMetaItem meta) {
        return super.notUncommon(meta) && (meta instanceof CraftMetaLeatherArmor || this.isLeatherArmorEmpty());
    }

    @Override
    int applyHash() {
        final int original;
        int hash = original = super.applyHash();
        if (this.hasColor()) {
            hash ^= this.color.hashCode();
        }
        return original != hash ? CraftMetaLeatherArmor.class.hashCode() ^ hash : hash;
    }

    static void readColor(LeatherArmorMeta meta, CraftMetaItem other) {
        if (!(other instanceof CraftMetaLeatherArmor armorMeta)) {
            return;
        }

        meta.setColor(armorMeta.color);
    }

    static void readColor(LeatherArmorMeta meta, CompoundTag tag) {
        if (tag.contains(DISPLAY.NBT)) {
            CompoundTag display = tag.getCompound(DISPLAY.NBT);
            if (display.contains(CraftMetaLeatherArmor.COLOR.NBT)) {
                try {
                    meta.setColor(Color.fromRGB(display.getInt(CraftMetaLeatherArmor.COLOR.NBT)));
                } catch (IllegalArgumentException ex) {
                    // Invalid colour
                }
            }
        }
    }

    static void readColor(LeatherArmorMeta meta, Map<String, Object> map) {
        meta.setColor(SerializableMeta.getObject(Color.class, map, CraftMetaLeatherArmor.COLOR.BUKKIT, true));
    }

    static boolean hasColor(LeatherArmorMeta meta) {
        return !DEFAULT_LEATHER_COLOR.equals(meta.getColor());
    }

    static void applyColor(LeatherArmorMeta meta, CompoundTag tag) {
        if (CraftMetaLeatherArmor.hasColor(meta)) {
            ((CraftMetaItem) meta).setDisplayTag(tag, CraftMetaLeatherArmor.COLOR.NBT, IntTag.valueOf(meta.getColor().asRGB()));
        }
    }

    static void serialize(LeatherArmorMeta meta, Builder<String, Object> builder) {
        if (CraftMetaLeatherArmor.hasColor(meta)) {
            builder.put(CraftMetaLeatherArmor.COLOR.BUKKIT, meta.getColor());
        }
    }
}
