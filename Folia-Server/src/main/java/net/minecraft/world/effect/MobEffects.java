package net.minecraft.world.effect;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class MobEffects {

    private static final int DARKNESS_EFFECT_FACTOR_PADDING_DURATION_TICKS = 22;
    public static final MobEffect MOVEMENT_SPEED = MobEffects.register("speed", (new MobEffect(MobEffectCategory.BENEFICIAL, 3402751)).addAttributeModifier(Attributes.MOVEMENT_SPEED, "91AEAA56-376B-4498-935B-2F7F68070635", 0.20000000298023224D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final MobEffect MOVEMENT_SLOWDOWN = MobEffects.register("slowness", (new MobEffect(MobEffectCategory.HARMFUL, 9154528)).addAttributeModifier(Attributes.MOVEMENT_SPEED, "7107DE5E-7CE8-4030-940E-514C1F160890", -0.15000000596046448D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final MobEffect DIG_SPEED = MobEffects.register("haste", (new MobEffect(MobEffectCategory.BENEFICIAL, 14270531)).addAttributeModifier(Attributes.ATTACK_SPEED, "AF8B6E3F-3328-4C0A-AA36-5BA2BB9DBEF3", 0.10000000149011612D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final MobEffect DIG_SLOWDOWN = MobEffects.register("mining_fatigue", (new MobEffect(MobEffectCategory.HARMFUL, 4866583)).addAttributeModifier(Attributes.ATTACK_SPEED, "55FCED67-E92A-486E-9800-B47F202C4386", -0.10000000149011612D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final MobEffect DAMAGE_BOOST = MobEffects.register("strength", (new MobEffect(MobEffectCategory.BENEFICIAL, 16762624)).addAttributeModifier(Attributes.ATTACK_DAMAGE, "648D7064-6A60-4F59-8ABE-C2C23A6DD7A9", 3.0D, AttributeModifier.Operation.ADDITION));
    public static final MobEffect HEAL = MobEffects.register("instant_health", new HealOrHarmMobEffect(MobEffectCategory.BENEFICIAL, 16262179, false));
    public static final MobEffect HARM = MobEffects.register("instant_damage", new HealOrHarmMobEffect(MobEffectCategory.HARMFUL, 11101546, true));
    public static final MobEffect JUMP = MobEffects.register("jump_boost", new MobEffect(MobEffectCategory.BENEFICIAL, 16646020));
    public static final MobEffect CONFUSION = MobEffects.register("nausea", new MobEffect(MobEffectCategory.HARMFUL, 5578058));
    public static final MobEffect REGENERATION = MobEffects.register("regeneration", new RegenerationMobEffect(MobEffectCategory.BENEFICIAL, 13458603));
    public static final MobEffect DAMAGE_RESISTANCE = MobEffects.register("resistance", new MobEffect(MobEffectCategory.BENEFICIAL, 9520880));
    public static final MobEffect FIRE_RESISTANCE = MobEffects.register("fire_resistance", new MobEffect(MobEffectCategory.BENEFICIAL, 16750848));
    public static final MobEffect WATER_BREATHING = MobEffects.register("water_breathing", new MobEffect(MobEffectCategory.BENEFICIAL, 10017472));
    public static final MobEffect INVISIBILITY = MobEffects.register("invisibility", new MobEffect(MobEffectCategory.BENEFICIAL, 16185078));
    public static final MobEffect BLINDNESS = MobEffects.register("blindness", new MobEffect(MobEffectCategory.HARMFUL, 2039587));
    public static final MobEffect NIGHT_VISION = MobEffects.register("night_vision", new MobEffect(MobEffectCategory.BENEFICIAL, 12779366));
    public static final MobEffect HUNGER = MobEffects.register("hunger", new HungerMobEffect(MobEffectCategory.HARMFUL, 5797459));
    public static final MobEffect WEAKNESS = MobEffects.register("weakness", (new MobEffect(MobEffectCategory.HARMFUL, 4738376)).addAttributeModifier(Attributes.ATTACK_DAMAGE, "22653B89-116E-49DC-9B6B-9971489B5BE5", -4.0D, AttributeModifier.Operation.ADDITION));
    public static final MobEffect POISON = MobEffects.register("poison", new PoisonMobEffect(MobEffectCategory.HARMFUL, 8889187));
    public static final MobEffect WITHER = MobEffects.register("wither", new WitherMobEffect(MobEffectCategory.HARMFUL, 7561558));
    public static final MobEffect HEALTH_BOOST = MobEffects.register("health_boost", (new MobEffect(MobEffectCategory.BENEFICIAL, 16284963)).addAttributeModifier(Attributes.MAX_HEALTH, "5D6F0BA2-1186-46AC-B896-C61C5CEE99CC", 4.0D, AttributeModifier.Operation.ADDITION));
    public static final MobEffect ABSORPTION = MobEffects.register("absorption", (new AbsorptionMobEffect(MobEffectCategory.BENEFICIAL, 2445989)).addAttributeModifier(Attributes.MAX_ABSORPTION, "EAE29CF0-701E-4ED6-883A-96F798F3DAB5", 4.0D, AttributeModifier.Operation.ADDITION));
    public static final MobEffect SATURATION = MobEffects.register("saturation", new SaturationMobEffect(MobEffectCategory.BENEFICIAL, 16262179));
    public static final MobEffect GLOWING = MobEffects.register("glowing", new MobEffect(MobEffectCategory.NEUTRAL, 9740385));
    public static final MobEffect LEVITATION = MobEffects.register("levitation", new MobEffect(MobEffectCategory.HARMFUL, 13565951));
    public static final MobEffect LUCK = MobEffects.register("luck", (new MobEffect(MobEffectCategory.BENEFICIAL, 5882118)).addAttributeModifier(Attributes.LUCK, "03C3C89D-7037-4B42-869F-B146BCB64D2E", 1.0D, AttributeModifier.Operation.ADDITION));
    public static final MobEffect UNLUCK = MobEffects.register("unluck", (new MobEffect(MobEffectCategory.HARMFUL, 12624973)).addAttributeModifier(Attributes.LUCK, "CC5AF142-2BD2-4215-B636-2605AED11727", -1.0D, AttributeModifier.Operation.ADDITION));
    public static final MobEffect SLOW_FALLING = MobEffects.register("slow_falling", new MobEffect(MobEffectCategory.BENEFICIAL, 15978425));
    public static final MobEffect CONDUIT_POWER = MobEffects.register("conduit_power", new MobEffect(MobEffectCategory.BENEFICIAL, 1950417));
    public static final MobEffect DOLPHINS_GRACE = MobEffects.register("dolphins_grace", new MobEffect(MobEffectCategory.BENEFICIAL, 8954814));
    public static final MobEffect BAD_OMEN = MobEffects.register("bad_omen", new BadOmenMobEffect(MobEffectCategory.NEUTRAL, 745784));
    public static final MobEffect HERO_OF_THE_VILLAGE = MobEffects.register("hero_of_the_village", new MobEffect(MobEffectCategory.BENEFICIAL, 4521796));
    public static final MobEffect DARKNESS = MobEffects.register("darkness", (new MobEffect(MobEffectCategory.HARMFUL, 2696993)).setFactorDataFactory(() -> {
        return new MobEffectInstance.FactorData(22);
    }));

    public MobEffects() {}

    private static MobEffect register(String id, MobEffect statusEffect) {
        // CraftBukkit start
        statusEffect = (MobEffect) Registry.register(BuiltInRegistries.MOB_EFFECT, id, statusEffect);
        org.bukkit.potion.PotionEffectType.registerPotionEffectType(new org.bukkit.craftbukkit.potion.CraftPotionEffectType(statusEffect));
        return statusEffect;
        // CraftBukkit end
    }
}
