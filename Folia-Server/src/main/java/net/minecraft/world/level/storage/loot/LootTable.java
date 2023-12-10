package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.world.LootGenerateEvent;
// CraftBukkit end

public class LootTable {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, Optional.empty(), List.of(), List.of());
    public static final LootContextParamSet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    public static final Codec<LootTable> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(LootContextParamSets.CODEC.optionalFieldOf("type", LootTable.DEFAULT_PARAM_SET).forGetter((loottable) -> {
            return loottable.paramSet;
        }), ExtraCodecs.strictOptionalField(ResourceLocation.CODEC, "random_sequence").forGetter((loottable) -> {
            return loottable.randomSequence;
        }), ExtraCodecs.strictOptionalField(LootPool.CODEC.listOf(), "pools", List.of()).forGetter((loottable) -> {
            return loottable.pools;
        }), ExtraCodecs.strictOptionalField(LootItemFunctions.CODEC.listOf(), "functions", List.of()).forGetter((loottable) -> {
            return loottable.functions;
        })).apply(instance, LootTable::new);
    });
    private final LootContextParamSet paramSet;
    private final Optional<ResourceLocation> randomSequence;
    private final List<LootPool> pools;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    public CraftLootTable craftLootTable; // CraftBukkit

    LootTable(LootContextParamSet type, Optional<ResourceLocation> randomSequenceId, List<LootPool> pools, List<LootItemFunction> functions) {
        this.paramSet = type;
        this.randomSequence = randomSequenceId;
        this.pools = pools;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    public static Consumer<ItemStack> createStackSplitter(ServerLevel world, Consumer<ItemStack> consumer) {
        boolean skipSplitter = world != null && !world.paperConfig().fixes.splitOverstackedLoot; // Paper - preserve overstacked items
        return (itemstack) -> {
            if (itemstack.isItemEnabled(world.enabledFeatures())) {
                if (skipSplitter || itemstack.getCount() < itemstack.getMaxStackSize()) { // Paper - preserve overstacked items
                    consumer.accept(itemstack);
                } else {
                    int i = itemstack.getCount();

                    while (i > 0) {
                        ItemStack itemstack1 = itemstack.copyWithCount(Math.min(itemstack.getMaxStackSize(), i));

                        i -= itemstack1.getCount();
                        consumer.accept(itemstack1);
                    }
                }

            }
        };
    }

    public void getRandomItemsRaw(LootParams parameters, Consumer<ItemStack> lootConsumer) {
        this.getRandomItemsRaw((new LootContext.Builder(parameters)).create(this.randomSequence), lootConsumer);
    }

    public void getRandomItemsRaw(LootContext context, Consumer<ItemStack> lootConsumer) {
        LootContext.VisitedEntry<?> loottableinfo_c = LootContext.createVisitedEntry(this);

        if (context.pushVisitedElement(loottableinfo_c)) {
            Consumer<ItemStack> consumer1 = LootItemFunction.decorate(this.compositeFunction, lootConsumer, context);
            Iterator iterator = this.pools.iterator();

            while (iterator.hasNext()) {
                LootPool lootselector = (LootPool) iterator.next();

                lootselector.addRandomItems(consumer1, context);
            }

            context.popVisitedElement(loottableinfo_c);
        } else {
            LootTable.LOGGER.warn("Detected infinite loop in loot tables");
        }

    }

    public void getRandomItems(LootParams parameters, long seed, Consumer<ItemStack> lootConsumer) {
        this.getRandomItemsRaw((new LootContext.Builder(parameters)).withOptionalRandomSeed(seed).create(this.randomSequence), LootTable.createStackSplitter(parameters.getLevel(), lootConsumer));
    }

    public void getRandomItems(LootParams parameters, Consumer<ItemStack> lootConsumer) {
        this.getRandomItemsRaw(parameters, LootTable.createStackSplitter(parameters.getLevel(), lootConsumer));
    }

    public void getRandomItems(LootContext context, Consumer<ItemStack> lootConsumer) {
        this.getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), lootConsumer));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams parameters, long seed) {
        return this.getRandomItems((new LootContext.Builder(parameters)).withOptionalRandomSeed(seed).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams parameters) {
        return this.getRandomItems((new LootContext.Builder(parameters)).create(this.randomSequence));
    }

    private ObjectArrayList<ItemStack> getRandomItems(LootContext context) {
        ObjectArrayList<ItemStack> objectarraylist = new ObjectArrayList();

        Objects.requireNonNull(objectarraylist);
        this.getRandomItems(context, objectarraylist::add);
        return objectarraylist;
    }

    public LootContextParamSet getParamSet() {
        return this.paramSet;
    }

    public void validate(ValidationContext reporter) {
        int i;

        for (i = 0; i < this.pools.size(); ++i) {
            ((LootPool) this.pools.get(i)).validate(reporter.forChild(".pools[" + i + "]"));
        }

        for (i = 0; i < this.functions.size(); ++i) {
            ((LootItemFunction) this.functions.get(i)).validate(reporter.forChild(".functions[" + i + "]"));
        }

    }

    public void fill(Container inventory, LootParams parameters, long seed) {
        // CraftBukkit start
        this.fillInventory(inventory, parameters, seed, false);
    }

    public void fillInventory(Container iinventory, LootParams lootparams, long i, boolean plugin) {
        // CraftBukkit end
        LootContext loottableinfo = (new LootContext.Builder(lootparams)).withOptionalRandomSeed(i).create(this.randomSequence);
        ObjectArrayList<ItemStack> objectarraylist = this.getRandomItems(loottableinfo);
        RandomSource randomsource = loottableinfo.getRandom();
        // CraftBukkit start
        LootGenerateEvent event = CraftEventFactory.callLootGenerateEvent(iinventory, this, loottableinfo, objectarraylist, plugin);
        if (event.isCancelled()) {
            return;
        }
        objectarraylist = event.getLoot().stream().map(CraftItemStack::asNMSCopy).collect(ObjectArrayList.toList());
        // CraftBukkit end
        List<Integer> list = this.getAvailableSlots(iinventory, randomsource);

        this.shuffleAndSplitItems(objectarraylist, list.size(), randomsource);
        ObjectListIterator objectlistiterator = objectarraylist.iterator();

        while (objectlistiterator.hasNext()) {
            ItemStack itemstack = (ItemStack) objectlistiterator.next();

            if (list.isEmpty()) {
                LootTable.LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemstack.isEmpty()) {
                iinventory.setItem((Integer) list.remove(list.size() - 1), ItemStack.EMPTY);
            } else {
                iinventory.setItem((Integer) list.remove(list.size() - 1), itemstack);
            }
        }

    }

    private void shuffleAndSplitItems(ObjectArrayList<ItemStack> drops, int freeSlots, RandomSource random) {
        List<ItemStack> list = Lists.newArrayList();
        ObjectListIterator objectlistiterator = drops.iterator();

        while (objectlistiterator.hasNext()) {
            ItemStack itemstack = (ItemStack) objectlistiterator.next();

            if (itemstack.isEmpty()) {
                objectlistiterator.remove();
            } else if (itemstack.getCount() > 1) {
                list.add(itemstack);
                objectlistiterator.remove();
            }
        }

        while (freeSlots - drops.size() - list.size() > 0 && !list.isEmpty()) {
            ItemStack itemstack1 = (ItemStack) list.remove(Mth.nextInt(random, 0, list.size() - 1));
            int j = Mth.nextInt(random, 1, itemstack1.getCount() / 2);
            ItemStack itemstack2 = itemstack1.split(j);

            if (itemstack1.getCount() > 1 && random.nextBoolean()) {
                list.add(itemstack1);
            } else {
                drops.add(itemstack1);
            }

            if (itemstack2.getCount() > 1 && random.nextBoolean()) {
                list.add(itemstack2);
            } else {
                drops.add(itemstack2);
            }
        }

        drops.addAll(list);
        Util.shuffle(drops, random);
    }

    private List<Integer> getAvailableSlots(Container inventory, RandomSource random) {
        ObjectArrayList<Integer> objectarraylist = new ObjectArrayList();

        for (int i = 0; i < inventory.getContainerSize(); ++i) {
            if (inventory.getItem(i).isEmpty()) {
                objectarraylist.add(i);
            }
        }

        Util.shuffle(objectarraylist, random);
        return objectarraylist;
    }

    public static LootTable.Builder lootTable() {
        return new LootTable.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootTable.Builder> {

        private final ImmutableList.Builder<LootPool> pools = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private LootContextParamSet paramSet;
        private Optional<ResourceLocation> randomSequence;

        public Builder() {
            this.paramSet = LootTable.DEFAULT_PARAM_SET;
            this.randomSequence = Optional.empty();
        }

        public LootTable.Builder withPool(LootPool.Builder poolBuilder) {
            this.pools.add(poolBuilder.build());
            return this;
        }

        public LootTable.Builder setParamSet(LootContextParamSet type) {
            this.paramSet = type;
            return this;
        }

        public LootTable.Builder setRandomSequence(ResourceLocation randomSequenceId) {
            this.randomSequence = Optional.of(randomSequenceId);
            return this;
        }

        @Override
        public LootTable.Builder apply(LootItemFunction.Builder function) {
            this.functions.add(function.build());
            return this;
        }

        @Override
        public LootTable.Builder unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, this.randomSequence, this.pools.build(), this.functions.build());
        }
    }
}
