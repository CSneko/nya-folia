package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

public class ExplorationMapFunction extends LootItemConditionalFunction {
    public static final TagKey<Structure> DEFAULT_DESTINATION = StructureTags.ON_TREASURE_MAPS;
    public static final MapDecoration.Type DEFAULT_DECORATION = MapDecoration.Type.MANSION;
    public static final byte DEFAULT_ZOOM = 2;
    public static final int DEFAULT_SEARCH_RADIUS = 50;
    public static final boolean DEFAULT_SKIP_EXISTING = true;
    public static final Codec<ExplorationMapFunction> CODEC = RecordCodecBuilder.create((instance) -> {
        return commonFields(instance).and(instance.group(ExtraCodecs.strictOptionalField(TagKey.codec(Registries.STRUCTURE), "destination", DEFAULT_DESTINATION).forGetter((explorationMapFunction) -> {
            return explorationMapFunction.destination;
        }), MapDecoration.Type.CODEC.optionalFieldOf("decoration", DEFAULT_DECORATION).forGetter((explorationMapFunction) -> {
            return explorationMapFunction.mapDecoration;
        }), ExtraCodecs.strictOptionalField(Codec.BYTE, "zoom", (byte)2).forGetter((explorationMapFunction) -> {
            return explorationMapFunction.zoom;
        }), ExtraCodecs.strictOptionalField(Codec.INT, "search_radius", 50).forGetter((explorationMapFunction) -> {
            return explorationMapFunction.searchRadius;
        }), ExtraCodecs.strictOptionalField(Codec.BOOL, "skip_existing_chunks", true).forGetter((explorationMapFunction) -> {
            return explorationMapFunction.skipKnownStructures;
        }))).apply(instance, ExplorationMapFunction::new);
    });
    private final TagKey<Structure> destination;
    private final MapDecoration.Type mapDecoration;
    private final byte zoom;
    private final int searchRadius;
    private final boolean skipKnownStructures;

    ExplorationMapFunction(List<LootItemCondition> conditions, TagKey<Structure> destination, MapDecoration.Type decoration, byte zoom, int searchRadius, boolean skipExistingChunks) {
        super(conditions);
        this.destination = destination;
        this.mapDecoration = decoration;
        this.zoom = zoom;
        this.searchRadius = searchRadius;
        this.skipKnownStructures = skipExistingChunks;
    }

    @Override
    public LootItemFunctionType getType() {
        return LootItemFunctions.EXPLORATION_MAP;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.ORIGIN);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(Items.MAP)) {
            return stack;
        } else {
            Vec3 vec3 = context.getParamOrNull(LootContextParams.ORIGIN);
            if (vec3 != null) {
                ServerLevel serverLevel = context.getLevel();
                // Paper start
                if (!serverLevel.paperConfig().environment.treasureMaps.enabled) {
                    /*
                     * NOTE: I fear users will just get a plain map as their "treasure"
                     * This is preferable to disrespecting the config.
                     */
                    return stack;
                }
                // Paper end
                BlockPos blockPos = serverLevel.findNearestMapStructure(this.destination, BlockPos.containing(vec3), this.searchRadius, !serverLevel.paperConfig().environment.treasureMaps.findAlreadyDiscoveredLootTable.or(!this.skipKnownStructures)); // Paper
                if (blockPos != null) {
                    ItemStack itemStack = MapItem.create(serverLevel, blockPos.getX(), blockPos.getZ(), this.zoom, true, true);
                    MapItem.renderBiomePreviewMap(serverLevel, itemStack);
                    MapItemSavedData.addTargetDecoration(itemStack, blockPos, "+", this.mapDecoration);
                    return itemStack;
                }
            }

            return stack;
        }
    }

    public static ExplorationMapFunction.Builder makeExplorationMap() {
        return new ExplorationMapFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<ExplorationMapFunction.Builder> {
        private TagKey<Structure> destination = ExplorationMapFunction.DEFAULT_DESTINATION;
        private MapDecoration.Type mapDecoration = ExplorationMapFunction.DEFAULT_DECORATION;
        private byte zoom = 2;
        private int searchRadius = 50;
        private boolean skipKnownStructures = true;

        @Override
        protected ExplorationMapFunction.Builder getThis() {
            return this;
        }

        public ExplorationMapFunction.Builder setDestination(TagKey<Structure> destination) {
            this.destination = destination;
            return this;
        }

        public ExplorationMapFunction.Builder setMapDecoration(MapDecoration.Type decoration) {
            this.mapDecoration = decoration;
            return this;
        }

        public ExplorationMapFunction.Builder setZoom(byte zoom) {
            this.zoom = zoom;
            return this;
        }

        public ExplorationMapFunction.Builder setSearchRadius(int searchRadius) {
            this.searchRadius = searchRadius;
            return this;
        }

        public ExplorationMapFunction.Builder setSkipKnownStructures(boolean skipExistingChunks) {
            this.skipKnownStructures = skipExistingChunks;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new ExplorationMapFunction(this.getConditions(), this.destination, this.mapDecoration, this.zoom, this.searchRadius, this.skipKnownStructures);
        }
    }
}
