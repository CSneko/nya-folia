package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record LootItemRandomChanceWithLootingCondition(float percent, float lootingMultiplier) implements LootItemCondition {

    public static final Codec<LootItemRandomChanceWithLootingCondition> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("chance").forGetter(LootItemRandomChanceWithLootingCondition::percent), Codec.FLOAT.fieldOf("looting_multiplier").forGetter(LootItemRandomChanceWithLootingCondition::lootingMultiplier)).apply(instance, LootItemRandomChanceWithLootingCondition::new);
    });

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.RANDOM_CHANCE_WITH_LOOTING;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.KILLER_ENTITY);
    }

    public boolean test(LootContext loottableinfo) {
        Entity entity = (Entity) loottableinfo.getParamOrNull(LootContextParams.KILLER_ENTITY);
        int i = 0;

        if (entity instanceof LivingEntity) {
            i = EnchantmentHelper.getMobLooting((LivingEntity) entity);
        }
        // CraftBukkit start - only use lootingModifier if set by Bukkit
        if (loottableinfo.hasParam(LootContextParams.LOOTING_MOD)) {
            i = loottableinfo.getParamOrNull(LootContextParams.LOOTING_MOD);
        }
        // CraftBukkit end

        return loottableinfo.getRandom().nextFloat() < this.percent + (float) i * this.lootingMultiplier;
    }

    public static LootItemCondition.Builder randomChanceAndLootingBoost(float chance, float lootingMultiplier) {
        return () -> {
            return new LootItemRandomChanceWithLootingCondition(chance, lootingMultiplier);
        };
    }
}
