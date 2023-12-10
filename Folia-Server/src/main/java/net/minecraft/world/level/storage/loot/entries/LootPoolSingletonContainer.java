package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootPoolSingletonContainer extends LootPoolEntryContainer {
    public static final int DEFAULT_WEIGHT = 1;
    public static final int DEFAULT_QUALITY = 0;
    protected final int weight;
    protected final int quality;
    protected final List<LootItemFunction> functions;
    final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final LootPoolEntry entry = new LootPoolSingletonContainer.EntryBase() {
        @Override
        public void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context) {
            LootPoolSingletonContainer.this.createItemStack(LootItemFunction.decorate(LootPoolSingletonContainer.this.compositeFunction, lootConsumer, context), context);
        }
    };

    protected LootPoolSingletonContainer(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(conditions);
        this.weight = weight;
        this.quality = quality;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    protected static <T extends LootPoolSingletonContainer> Products.P4<RecordCodecBuilder.Mu<T>, Integer, Integer, List<LootItemCondition>, List<LootItemFunction>> singletonFields(RecordCodecBuilder.Instance<T> instance) {
        return instance.group(ExtraCodecs.strictOptionalField(Codec.INT, "weight", 1).forGetter((lootPoolSingletonContainer) -> {
            return lootPoolSingletonContainer.weight;
        }), ExtraCodecs.strictOptionalField(Codec.INT, "quality", 0).forGetter((lootPoolSingletonContainer) -> {
            return lootPoolSingletonContainer.quality;
        })).and(commonFields(instance).t1()).and(ExtraCodecs.strictOptionalField(LootItemFunctions.CODEC.listOf(), "functions", List.of()).forGetter((lootPoolSingletonContainer) -> {
            return lootPoolSingletonContainer.functions;
        }));
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);

        for(int i = 0; i < this.functions.size(); ++i) {
            this.functions.get(i).validate(reporter.forChild(".functions[" + i + "]"));
        }

    }

    protected abstract void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context);

    @Override
    public boolean expand(LootContext context, Consumer<LootPoolEntry> choiceConsumer) {
        if (this.canRun(context)) {
            choiceConsumer.accept(this.entry);
            return true;
        } else {
            return false;
        }
    }

    public static LootPoolSingletonContainer.Builder<?> simpleBuilder(LootPoolSingletonContainer.EntryConstructor factory) {
        return new LootPoolSingletonContainer.DummyBuilder(factory);
    }

    public abstract static class Builder<T extends LootPoolSingletonContainer.Builder<T>> extends LootPoolEntryContainer.Builder<T> implements FunctionUserBuilder<T> {
        protected int weight = 1;
        protected int quality = 0;
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();

        @Override
        public T apply(LootItemFunction.Builder builder) {
            this.functions.add(builder.build());
            return this.getThis();
        }

        protected List<LootItemFunction> getFunctions() {
            return this.functions.build();
        }

        public T setWeight(int weight) {
            this.weight = weight;
            return this.getThis();
        }

        public T setQuality(int quality) {
            this.quality = quality;
            return this.getThis();
        }
    }

    static class DummyBuilder extends LootPoolSingletonContainer.Builder<LootPoolSingletonContainer.DummyBuilder> {
        private final LootPoolSingletonContainer.EntryConstructor constructor;

        public DummyBuilder(LootPoolSingletonContainer.EntryConstructor factory) {
            this.constructor = factory;
        }

        @Override
        protected LootPoolSingletonContainer.DummyBuilder getThis() {
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return this.constructor.build(this.weight, this.quality, this.getConditions(), this.getFunctions());
        }
    }

    protected abstract class EntryBase implements LootPoolEntry {
        @Override
        public int getWeight(float luck) {
            // Paper start - Offer an alternative loot formula to refactor how luck bonus applies
            // SEE: https://luckformula.emc.gs for details and data
            if (LootPoolSingletonContainer.this.lastLuck != null && LootPoolSingletonContainer.this.lastLuck == luck) {
                return lastWeight;
            }
            // This is vanilla
            float qualityModifer = (float) LootPoolSingletonContainer.this.quality * luck;
            double baseWeight = (LootPoolSingletonContainer.this.weight + qualityModifer);
            if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.useAlternativeLuckFormula) {
                // Random boost to avoid losing precision in the final int cast on return
                final int weightBoost = 100;
                baseWeight *= weightBoost;
                // If we have vanilla 1, bump that down to 0 so nothing is is impacted
                // vanilla 3 = 300, 200 basis = impact 2%
                // =($B2*(($B2-100)/100/100))
                double impacted = baseWeight * ((baseWeight - weightBoost) / weightBoost / 100);
                // =($B$7/100)
                float luckModifier = Math.min(100, luck * 10) / 100;
                // =B2 - (C2 *($B$7/100))
                baseWeight = Math.ceil(baseWeight - (impacted * luckModifier));
            }
            LootPoolSingletonContainer.this.lastLuck = luck;
            LootPoolSingletonContainer.this.lastWeight = (int) Math.max(Math.floor(baseWeight), 0);
            return lastWeight;
        }
    }
    private Float lastLuck = null;
    private int lastWeight = 0;
    // Paper end

    @FunctionalInterface
    protected interface EntryConstructor {
        LootPoolSingletonContainer build(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions);
    }
}
