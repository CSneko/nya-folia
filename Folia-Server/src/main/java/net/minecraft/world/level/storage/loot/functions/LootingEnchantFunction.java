package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class LootingEnchantFunction extends LootItemConditionalFunction {

    public static final int NO_LIMIT = 0;
    public static final Codec<LootingEnchantFunction> CODEC = RecordCodecBuilder.create((instance) -> {
        return commonFields(instance).and(instance.group(NumberProviders.CODEC.fieldOf("count").forGetter((lootenchantfunction) -> {
            return lootenchantfunction.value;
        }), ExtraCodecs.strictOptionalField(Codec.INT, "limit", 0).forGetter((lootenchantfunction) -> {
            return lootenchantfunction.limit;
        }))).apply(instance, LootingEnchantFunction::new);
    });
    private final NumberProvider value;
    private final int limit;

    LootingEnchantFunction(List<LootItemCondition> conditions, NumberProvider countRange, int limit) {
        super(conditions);
        this.value = countRange;
        this.limit = limit;
    }

    @Override
    public LootItemFunctionType getType() {
        return LootItemFunctions.LOOTING_ENCHANT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Sets.union(ImmutableSet.of(LootContextParams.KILLER_ENTITY), this.value.getReferencedContextParams());
    }

    private boolean hasLimit() {
        return this.limit > 0;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Entity entity = (Entity) context.getParamOrNull(LootContextParams.KILLER_ENTITY);

        if (entity instanceof LivingEntity) {
            int i = EnchantmentHelper.getMobLooting((LivingEntity) entity);
            // CraftBukkit start - use lootingModifier if set by plugin
            if (context.hasParam(LootContextParams.LOOTING_MOD)) {
                i = context.getParamOrNull(LootContextParams.LOOTING_MOD);
            }
            // CraftBukkit end

            if (i <= 0) { // CraftBukkit - account for possible negative looting values from Bukkit
                return stack;
            }

            float f = (float) i * this.value.getFloat(context);

            stack.grow(Math.round(f));
            if (this.hasLimit() && stack.getCount() > this.limit) {
                stack.setCount(this.limit);
            }
        }

        return stack;
    }

    public static LootingEnchantFunction.Builder lootingMultiplier(NumberProvider countRange) {
        return new LootingEnchantFunction.Builder(countRange);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<LootingEnchantFunction.Builder> {

        private final NumberProvider count;
        private int limit = 0;

        public Builder(NumberProvider countRange) {
            this.count = countRange;
        }

        @Override
        protected LootingEnchantFunction.Builder getThis() {
            return this;
        }

        public LootingEnchantFunction.Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new LootingEnchantFunction(this.getConditions(), this.count, this.limit);
        }
    }
}
