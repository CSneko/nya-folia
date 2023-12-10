package net.minecraft.world.item.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;

public class Enchantments {

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final Enchantment ALL_DAMAGE_PROTECTION = Enchantments.register("protection", new ProtectionEnchantment(Enchantment.Rarity.COMMON, ProtectionEnchantment.Type.ALL, Enchantments.ARMOR_SLOTS));
    public static final Enchantment FIRE_PROTECTION = Enchantments.register("fire_protection", new ProtectionEnchantment(Enchantment.Rarity.UNCOMMON, ProtectionEnchantment.Type.FIRE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment FALL_PROTECTION = Enchantments.register("feather_falling", new ProtectionEnchantment(Enchantment.Rarity.UNCOMMON, ProtectionEnchantment.Type.FALL, Enchantments.ARMOR_SLOTS));
    public static final Enchantment BLAST_PROTECTION = Enchantments.register("blast_protection", new ProtectionEnchantment(Enchantment.Rarity.RARE, ProtectionEnchantment.Type.EXPLOSION, Enchantments.ARMOR_SLOTS));
    public static final Enchantment PROJECTILE_PROTECTION = Enchantments.register("projectile_protection", new ProtectionEnchantment(Enchantment.Rarity.UNCOMMON, ProtectionEnchantment.Type.PROJECTILE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment RESPIRATION = Enchantments.register("respiration", new OxygenEnchantment(Enchantment.Rarity.RARE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment AQUA_AFFINITY = Enchantments.register("aqua_affinity", new WaterWorkerEnchantment(Enchantment.Rarity.RARE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment THORNS = Enchantments.register("thorns", new ThornsEnchantment(Enchantment.Rarity.VERY_RARE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment DEPTH_STRIDER = Enchantments.register("depth_strider", new WaterWalkerEnchantment(Enchantment.Rarity.RARE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment FROST_WALKER = Enchantments.register("frost_walker", new FrostWalkerEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.FEET}));
    public static final Enchantment BINDING_CURSE = Enchantments.register("binding_curse", new BindingCurseEnchantment(Enchantment.Rarity.VERY_RARE, Enchantments.ARMOR_SLOTS));
    public static final Enchantment SOUL_SPEED = Enchantments.register("soul_speed", new SoulSpeedEnchantment(Enchantment.Rarity.VERY_RARE, new EquipmentSlot[]{EquipmentSlot.FEET}));
    public static final Enchantment SWIFT_SNEAK = Enchantments.register("swift_sneak", new SwiftSneakEnchantment(Enchantment.Rarity.VERY_RARE, new EquipmentSlot[]{EquipmentSlot.LEGS}));
    public static final Enchantment SHARPNESS = Enchantments.register("sharpness", new DamageEnchantment(Enchantment.Rarity.COMMON, 0, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment SMITE = Enchantments.register("smite", new DamageEnchantment(Enchantment.Rarity.UNCOMMON, 1, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment BANE_OF_ARTHROPODS = Enchantments.register("bane_of_arthropods", new DamageEnchantment(Enchantment.Rarity.UNCOMMON, 2, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment KNOCKBACK = Enchantments.register("knockback", new KnockbackEnchantment(Enchantment.Rarity.UNCOMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment FIRE_ASPECT = Enchantments.register("fire_aspect", new FireAspectEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment MOB_LOOTING = Enchantments.register("looting", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment SWEEPING_EDGE = Enchantments.register("sweeping", new SweepingEdgeEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment BLOCK_EFFICIENCY = Enchantments.register("efficiency", new DiggingEnchantment(Enchantment.Rarity.COMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment SILK_TOUCH = Enchantments.register("silk_touch", new UntouchingEnchantment(Enchantment.Rarity.VERY_RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment UNBREAKING = Enchantments.register("unbreaking", new DigDurabilityEnchantment(Enchantment.Rarity.UNCOMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment BLOCK_FORTUNE = Enchantments.register("fortune", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment POWER_ARROWS = Enchantments.register("power", new ArrowDamageEnchantment(Enchantment.Rarity.COMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment PUNCH_ARROWS = Enchantments.register("punch", new ArrowKnockbackEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment FLAMING_ARROWS = Enchantments.register("flame", new ArrowFireEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment INFINITY_ARROWS = Enchantments.register("infinity", new ArrowInfiniteEnchantment(Enchantment.Rarity.VERY_RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment FISHING_LUCK = Enchantments.register("luck_of_the_sea", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.FISHING_ROD, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment FISHING_SPEED = Enchantments.register("lure", new FishingSpeedEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.FISHING_ROD, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment LOYALTY = Enchantments.register("loyalty", new TridentLoyaltyEnchantment(Enchantment.Rarity.UNCOMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment IMPALING = Enchantments.register("impaling", new TridentImpalerEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment RIPTIDE = Enchantments.register("riptide", new TridentRiptideEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment CHANNELING = Enchantments.register("channeling", new TridentChannelingEnchantment(Enchantment.Rarity.VERY_RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment MULTISHOT = Enchantments.register("multishot", new MultiShotEnchantment(Enchantment.Rarity.RARE, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment QUICK_CHARGE = Enchantments.register("quick_charge", new QuickChargeEnchantment(Enchantment.Rarity.UNCOMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment PIERCING = Enchantments.register("piercing", new ArrowPiercingEnchantment(Enchantment.Rarity.COMMON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}));
    public static final Enchantment MENDING = Enchantments.register("mending", new MendingEnchantment(Enchantment.Rarity.RARE, EquipmentSlot.values()));
    public static final Enchantment VANISHING_CURSE = Enchantments.register("vanishing_curse", new VanishingCurseEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlot.values()));

    public Enchantments() {}

    private static Enchantment register(String name, Enchantment enchantment) {
        // CraftBukkit start
        enchantment = (Enchantment) Registry.register(BuiltInRegistries.ENCHANTMENT, name, enchantment);
        org.bukkit.enchantments.Enchantment.registerEnchantment(new org.bukkit.craftbukkit.enchantments.CraftEnchantment(enchantment));
        return enchantment;
        // CraftBukkit end
    }
}
