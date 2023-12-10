package org.bukkit.craftbukkit.inventory;

import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.util.CraftLegacy;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.material.MaterialData;

@DelegateDeserialization(CraftMetaItem.SerializableMeta.class)
public class CraftMetaSpawnEgg extends CraftMetaItem implements SpawnEggMeta {

    private static final Set<Material> SPAWN_EGG_MATERIALS = Sets.newHashSet(
            Material.ALLAY_SPAWN_EGG,
            Material.AXOLOTL_SPAWN_EGG,
            Material.BAT_SPAWN_EGG,
            Material.BEE_SPAWN_EGG,
            Material.BLAZE_SPAWN_EGG,
            Material.CAT_SPAWN_EGG,
            Material.CAMEL_SPAWN_EGG,
            Material.CAVE_SPIDER_SPAWN_EGG,
            Material.CHICKEN_SPAWN_EGG,
            Material.COD_SPAWN_EGG,
            Material.COW_SPAWN_EGG,
            Material.CREEPER_SPAWN_EGG,
            Material.DOLPHIN_SPAWN_EGG,
            Material.DONKEY_SPAWN_EGG,
            Material.DROWNED_SPAWN_EGG,
            Material.ELDER_GUARDIAN_SPAWN_EGG,
            Material.ENDER_DRAGON_SPAWN_EGG,
            Material.ENDERMAN_SPAWN_EGG,
            Material.ENDERMITE_SPAWN_EGG,
            Material.EVOKER_SPAWN_EGG,
            Material.FOX_SPAWN_EGG,
            Material.FROG_SPAWN_EGG,
            Material.GHAST_SPAWN_EGG,
            Material.GLOW_SQUID_SPAWN_EGG,
            Material.GOAT_SPAWN_EGG,
            Material.GUARDIAN_SPAWN_EGG,
            Material.HOGLIN_SPAWN_EGG,
            Material.HORSE_SPAWN_EGG,
            Material.HUSK_SPAWN_EGG,
            Material.IRON_GOLEM_SPAWN_EGG,
            Material.LLAMA_SPAWN_EGG,
            Material.MAGMA_CUBE_SPAWN_EGG,
            Material.MOOSHROOM_SPAWN_EGG,
            Material.MULE_SPAWN_EGG,
            Material.OCELOT_SPAWN_EGG,
            Material.PANDA_SPAWN_EGG,
            Material.PARROT_SPAWN_EGG,
            Material.PHANTOM_SPAWN_EGG,
            Material.PIGLIN_BRUTE_SPAWN_EGG,
            Material.PIGLIN_SPAWN_EGG,
            Material.PIG_SPAWN_EGG,
            Material.PILLAGER_SPAWN_EGG,
            Material.POLAR_BEAR_SPAWN_EGG,
            Material.PUFFERFISH_SPAWN_EGG,
            Material.RABBIT_SPAWN_EGG,
            Material.RAVAGER_SPAWN_EGG,
            Material.SALMON_SPAWN_EGG,
            Material.SHEEP_SPAWN_EGG,
            Material.SHULKER_SPAWN_EGG,
            Material.SILVERFISH_SPAWN_EGG,
            Material.SKELETON_HORSE_SPAWN_EGG,
            Material.SKELETON_SPAWN_EGG,
            Material.SLIME_SPAWN_EGG,
            Material.SNIFFER_SPAWN_EGG,
            Material.SNOW_GOLEM_SPAWN_EGG,
            Material.SPIDER_SPAWN_EGG,
            Material.SQUID_SPAWN_EGG,
            Material.STRAY_SPAWN_EGG,
            Material.STRIDER_SPAWN_EGG,
            Material.TADPOLE_SPAWN_EGG,
            Material.TRADER_LLAMA_SPAWN_EGG,
            Material.TROPICAL_FISH_SPAWN_EGG,
            Material.TURTLE_SPAWN_EGG,
            Material.VEX_SPAWN_EGG,
            Material.VILLAGER_SPAWN_EGG,
            Material.VINDICATOR_SPAWN_EGG,
            Material.WARDEN_SPAWN_EGG,
            Material.WANDERING_TRADER_SPAWN_EGG,
            Material.WITCH_SPAWN_EGG,
            Material.WITHER_SPAWN_EGG,
            Material.WITHER_SKELETON_SPAWN_EGG,
            Material.WOLF_SPAWN_EGG,
            Material.ZOGLIN_SPAWN_EGG,
            Material.ZOMBIE_HORSE_SPAWN_EGG,
            Material.ZOMBIE_SPAWN_EGG,
            Material.ZOMBIE_VILLAGER_SPAWN_EGG,
            Material.ZOMBIFIED_PIGLIN_SPAWN_EGG
    );

    static final ItemMetaKey ENTITY_TAG = new ItemMetaKey("EntityTag", "entity-tag");
    @ItemMetaKey.Specific(ItemMetaKey.Specific.To.NBT)
    static final ItemMetaKey ENTITY_ID = new ItemMetaKey("id");

    private EntityType spawnedType;
    private CompoundTag entityTag;

    CraftMetaSpawnEgg(CraftMetaItem meta) {
        super(meta);

        if (!(meta instanceof CraftMetaSpawnEgg egg)) {
            return;
        }

        this.spawnedType = egg.spawnedType;

        this.updateMaterial(null); // Trigger type population
    }

    CraftMetaSpawnEgg(CompoundTag tag) {
        super(tag);

        if (tag.contains(CraftMetaSpawnEgg.ENTITY_TAG.NBT)) {
            this.entityTag = tag.getCompound(CraftMetaSpawnEgg.ENTITY_TAG.NBT).copy();
        }
    }

    CraftMetaSpawnEgg(Map<String, Object> map) {
        super(map);

        String entityType = SerializableMeta.getString(map, CraftMetaSpawnEgg.ENTITY_ID.BUKKIT, true);
        if (entityType != null) {
            this.spawnedType = EntityType.fromName(entityType);
        }
    }

    @Override
    void deserializeInternal(CompoundTag tag, Object context) {
        super.deserializeInternal(tag, context);

        if (tag.contains(CraftMetaSpawnEgg.ENTITY_TAG.NBT)) {
            this.entityTag = tag.getCompound(CraftMetaSpawnEgg.ENTITY_TAG.NBT);

            if (context instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) context;

                // Duplicated from constructor
                String entityType = SerializableMeta.getString(map, CraftMetaSpawnEgg.ENTITY_ID.BUKKIT, true);
                if (entityType != null) {
                    this.spawnedType = EntityType.fromName(entityType);
                }
            }

            if (this.spawnedType != null) {
                // We have a valid spawn type, just remove the ID now
                this.entityTag.remove(CraftMetaSpawnEgg.ENTITY_ID.NBT);
            }

            // Tag still has some other data, lets try our luck with a conversion
            if (!this.entityTag.isEmpty()) {
                // SPIGOT-4128: This is hopeless until we start versioning stacks. RIP data.
                // entityTag = (NBTTagCompound) MinecraftServer.getServer().dataConverterManager.update(DataConverterTypes.ENTITY, new Dynamic(DynamicOpsNBT.a, entityTag), -1, CraftMagicNumbers.DATA_VERSION).getValue();
            }

            // See if we can read a converted ID tag
            if (this.entityTag.contains(CraftMetaSpawnEgg.ENTITY_ID.NBT)) {
                this.spawnedType = EntityType.fromName(new ResourceLocation(this.entityTag.getString(CraftMetaSpawnEgg.ENTITY_ID.NBT)).getPath());
            }
        }
    }

    @Override
    void serializeInternal(Map<String, Tag> internalTags) {
        if (this.entityTag != null && !this.entityTag.isEmpty()) {
            internalTags.put(CraftMetaSpawnEgg.ENTITY_TAG.NBT, this.entityTag);
        }
    }

    @Override
    void applyToItem(CompoundTag tag) {
        super.applyToItem(tag);

        if (!this.isSpawnEggEmpty() && this.entityTag == null) {
            this.entityTag = new CompoundTag();
        }

        if (this.entityTag != null) {
            tag.put(CraftMetaSpawnEgg.ENTITY_TAG.NBT, this.entityTag);
        }
    }

    @Override
    boolean applicableTo(Material type) {
        return CraftMetaSpawnEgg.SPAWN_EGG_MATERIALS.contains(type);
    }

    @Override
    boolean isEmpty() {
        return super.isEmpty() && this.isSpawnEggEmpty();
    }

    boolean isSpawnEggEmpty() {
        return !(this.hasSpawnedType() || this.entityTag != null);
    }

    boolean hasSpawnedType() {
        return this.spawnedType != null;
    }

    @Override
    public EntityType getSpawnedType() {
        throw new UnsupportedOperationException("Must check item type to get spawned type");
    }

    @Override
    public void setSpawnedType(EntityType type) {
        throw new UnsupportedOperationException("Must change item type to set spawned type");
    }

    // Paper start
    @Override
    public EntityType getCustomSpawnedType() {
        return java.util.Optional.ofNullable(this.entityTag)
            .map(tag -> tag.getString(ENTITY_ID.NBT))
            .flatMap(net.minecraft.world.entity.EntityType::byString)
            .map(org.bukkit.craftbukkit.util.CraftMagicNumbers::getEntityType)
            .orElse(null);
    }

    @Override
    public void setCustomSpawnedType(final EntityType type) {
        if (type == null) {
            if (this.entityTag != null) {
                this.entityTag.remove(ENTITY_ID.NBT);
            }
        } else {
            if (this.entityTag == null) {
                this.entityTag = new CompoundTag();
            }
            this.entityTag.putString(ENTITY_ID.NBT, type.key().toString());
        }
    }
    // Paper end

    @Override
    boolean equalsCommon(CraftMetaItem meta) {
        if (!super.equalsCommon(meta)) {
            return false;
        }
        if (meta instanceof CraftMetaSpawnEgg) {
            CraftMetaSpawnEgg that = (CraftMetaSpawnEgg) meta;

            return this.hasSpawnedType() ? that.hasSpawnedType() && this.spawnedType.equals(that.spawnedType) : !that.hasSpawnedType()
                    && this.entityTag != null ? that.entityTag != null && this.entityTag.equals(that.entityTag) : this.entityTag == null;
        }
        return true;
    }

    @Override
    boolean notUncommon(CraftMetaItem meta) {
        return super.notUncommon(meta) && (meta instanceof CraftMetaSpawnEgg || this.isSpawnEggEmpty());
    }

    @Override
    int applyHash() {
        final int original;
        int hash = original = super.applyHash();

        if (this.hasSpawnedType()) {
            hash = 73 * hash + this.spawnedType.hashCode();
        }
        if (this.entityTag != null) {
            hash = 73 * hash + this.entityTag.hashCode();
        }

        return original != hash ? CraftMetaSpawnEgg.class.hashCode() ^ hash : hash;
    }

    @Override
    Builder<String, Object> serialize(Builder<String, Object> builder) {
        super.serialize(builder);

        return builder;
    }

    @Override
    public CraftMetaSpawnEgg clone() {
        CraftMetaSpawnEgg clone = (CraftMetaSpawnEgg) super.clone();

        clone.spawnedType = this.spawnedType;
        if (this.entityTag != null) {
            clone.entityTag = this.entityTag.copy();
        }

        return clone;
    }

    @Override
    final Material updateMaterial(Material material) {
        if (this.spawnedType == null) {
            this.spawnedType = EntityType.fromId(this.getDamage());
            this.setDamage(0);
        }

        if (this.spawnedType != null) {
            if (this.entityTag != null) {
                // Remove ID tag as it is now in the material
                this.entityTag.remove(CraftMetaSpawnEgg.ENTITY_ID.NBT);
            }

            return CraftLegacy.fromLegacy(new MaterialData(Material.LEGACY_MONSTER_EGG, (byte) this.spawnedType.getTypeId()));
        }

        return super.updateMaterial(material);
    }
}
