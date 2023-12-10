package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class ExplosionCondition implements LootItemCondition {

    private static final ExplosionCondition INSTANCE = new ExplosionCondition();
    public static final Codec<ExplosionCondition> CODEC = Codec.unit(ExplosionCondition.INSTANCE);

    private ExplosionCondition() {}

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.SURVIVES_EXPLOSION;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.EXPLOSION_RADIUS);
    }

    public boolean test(LootContext loottableinfo) {
        Float ofloat = (Float) loottableinfo.getParamOrNull(LootContextParams.EXPLOSION_RADIUS);

        if (ofloat != null) {
            RandomSource randomsource = loottableinfo.getRandom();
            float f = 1.0F / ofloat;

            // CraftBukkit - <= to < to allow for plugins to completely disable block drops from explosions
            return randomsource.nextFloat() < f;
        } else {
            return true;
        }
    }

    public static LootItemCondition.Builder survivesExplosion() {
        return () -> {
            return ExplosionCondition.INSTANCE;
        };
    }
}
