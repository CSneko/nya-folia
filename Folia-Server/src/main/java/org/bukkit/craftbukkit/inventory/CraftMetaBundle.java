package org.bukkit.craftbukkit.inventory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

@DelegateDeserialization(CraftMetaItem.SerializableMeta.class)
public class CraftMetaBundle extends CraftMetaItem implements BundleMeta {

    static final ItemMetaKey ITEMS = new ItemMetaKey("Items", "items");
    //
    private List<ItemStack> items;

    CraftMetaBundle(CraftMetaItem meta) {
        super(meta);

        if (!(meta instanceof CraftMetaBundle)) {
            return;
        }

        CraftMetaBundle bundle = (CraftMetaBundle) meta;

        if (bundle.hasItems()) {
            this.items = new ArrayList<>(bundle.items);
        }
    }

    CraftMetaBundle(CompoundTag tag) {
        super(tag);

        if (tag.contains(CraftMetaBundle.ITEMS.NBT, CraftMagicNumbers.NBT.TAG_LIST)) {
            ListTag list = tag.getList(CraftMetaBundle.ITEMS.NBT, CraftMagicNumbers.NBT.TAG_COMPOUND);

            if (list != null && !list.isEmpty()) {
                this.items = new ArrayList<>();

                for (int i = 0; i < list.size(); i++) {
                    CompoundTag nbttagcompound1 = list.getCompound(i);

                    ItemStack itemStack = CraftItemStack.asCraftMirror(net.minecraft.world.item.ItemStack.of(nbttagcompound1));
                    if (!itemStack.getType().isAir()) { // SPIGOT-7174 - Avoid adding air
                        this.addItem(itemStack);
                    }
                }
            }
        }
    }

    CraftMetaBundle(Map<String, Object> map) {
        super(map);

        Iterable<?> items = SerializableMeta.getObject(Iterable.class, map, CraftMetaBundle.ITEMS.BUKKIT, true);
        if (items != null) {
            for (Object stack : items) {
                if (stack instanceof ItemStack itemStack && !itemStack.getType().isAir()) { // SPIGOT-7174 - Avoid adding air
                    this.addItem(itemStack);
                }
            }
        }
    }

    @Override
    void applyToItem(CompoundTag tag) {
        super.applyToItem(tag);

        if (this.hasItems()) {
            ListTag list = new ListTag();

            for (ItemStack item : this.items) {
                CompoundTag saved = new CompoundTag();
                CraftItemStack.asNMSCopy(item).save(saved);
                list.add(saved);
            }

            tag.put(CraftMetaBundle.ITEMS.NBT, list);
        }
    }

    @Override
    boolean applicableTo(Material type) {
        return type == Material.BUNDLE;
    }

    @Override
    boolean isEmpty() {
        return super.isEmpty() && this.isBundleEmpty();
    }

    boolean isBundleEmpty() {
        return !(this.hasItems());
    }

    @Override
    public boolean hasItems() {
        return this.items != null && !this.items.isEmpty();
    }

    @Override
    public List<ItemStack> getItems() {
        return (this.items == null) ? ImmutableList.of() : ImmutableList.copyOf(this.items);
    }

    @Override
    public void setItems(List<ItemStack> items) {
        this.items = null;

        if (items == null) {
            return;
        }

        for (ItemStack i : items) {
            this.addItem(i);
        }
    }

    @Override
    public void addItem(ItemStack item) {
        Preconditions.checkArgument(item != null && !item.getType().isAir(), "item is null or air");

        if (this.items == null) {
            this.items = new ArrayList<>();
        }

        this.items.add(item);
    }

    @Override
    boolean equalsCommon(CraftMetaItem meta) {
        if (!super.equalsCommon(meta)) {
            return false;
        }
        if (meta instanceof CraftMetaBundle) {
            CraftMetaBundle that = (CraftMetaBundle) meta;

            return (this.hasItems() ? that.hasItems() && this.items.equals(that.items) : !that.hasItems());
        }
        return true;
    }

    @Override
    boolean notUncommon(CraftMetaItem meta) {
        return super.notUncommon(meta) && (meta instanceof CraftMetaBundle || this.isBundleEmpty());
    }

    @Override
    int applyHash() {
        final int original;
        int hash = original = super.applyHash();

        if (this.hasItems()) {
            hash = 61 * hash + this.items.hashCode();
        }

        return original != hash ? CraftMetaBundle.class.hashCode() ^ hash : hash;
    }

    @Override
    public CraftMetaBundle clone() {
        return (CraftMetaBundle) super.clone();
    }

    @Override
    ImmutableMap.Builder<String, Object> serialize(ImmutableMap.Builder<String, Object> builder) {
        super.serialize(builder);

        if (this.hasItems()) {
            builder.put(CraftMetaBundle.ITEMS.BUKKIT, this.items);
        }

        return builder;
    }
}
