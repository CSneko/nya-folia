package net.minecraft.advancements.critereon;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.Structure;

public record LocationPredicate(Optional<LocationPredicate.PositionPredicate> position, Optional<ResourceKey<Biome>> biome, Optional<ResourceKey<Structure>> structure, Optional<ResourceKey<Level>> dimension, Optional<Boolean> smokey, Optional<LightPredicate> light, Optional<BlockPredicate> block, Optional<FluidPredicate> fluid) {
    public static final Codec<LocationPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.strictOptionalField(LocationPredicate.PositionPredicate.CODEC, "position").forGetter(LocationPredicate::position), ExtraCodecs.strictOptionalField(ResourceKey.codec(Registries.BIOME), "biome").forGetter(LocationPredicate::biome), ExtraCodecs.strictOptionalField(ResourceKey.codec(Registries.STRUCTURE), "structure").forGetter(LocationPredicate::structure), ExtraCodecs.strictOptionalField(ResourceKey.codec(Registries.DIMENSION), "dimension").forGetter(LocationPredicate::dimension), ExtraCodecs.strictOptionalField(Codec.BOOL, "smokey").forGetter(LocationPredicate::smokey), ExtraCodecs.strictOptionalField(LightPredicate.CODEC, "light").forGetter(LocationPredicate::light), ExtraCodecs.strictOptionalField(BlockPredicate.CODEC, "block").forGetter(LocationPredicate::block), ExtraCodecs.strictOptionalField(FluidPredicate.CODEC, "fluid").forGetter(LocationPredicate::fluid)).apply(instance, LocationPredicate::new);
    });

    private static Optional<LocationPredicate> of(Optional<LocationPredicate.PositionPredicate> position, Optional<ResourceKey<Biome>> biome, Optional<ResourceKey<Structure>> structure, Optional<ResourceKey<Level>> dimension, Optional<Boolean> smokey, Optional<LightPredicate> light, Optional<BlockPredicate> block, Optional<FluidPredicate> fluid) {
        return position.isEmpty() && biome.isEmpty() && structure.isEmpty() && dimension.isEmpty() && smokey.isEmpty() && light.isEmpty() && block.isEmpty() && fluid.isEmpty() ? Optional.empty() : Optional.of(new LocationPredicate(position, biome, structure, dimension, smokey, light, block, fluid));
    }

    public boolean matches(ServerLevel world, double x, double y, double z) {
        if (this.position.isPresent() && !this.position.get().matches(x, y, z)) {
            return false;
        } else if (this.dimension.isPresent() && this.dimension.get() != (io.papermc.paper.configuration.GlobalConfiguration.get().misc.strictAdvancementDimensionCheck ? world.dimension() : org.bukkit.craftbukkit.util.CraftDimensionUtil.getMainDimensionKey(world))) { // Paper
            return false;
        } else {
            BlockPos blockPos = BlockPos.containing(x, y, z);
            boolean bl = world.isLoaded(blockPos);
            if (!this.biome.isPresent() || bl && world.getBiome(blockPos).is(this.biome.get())) {
                if (!this.structure.isPresent() || bl && world.structureManager().getStructureWithPieceAt(blockPos, this.structure.get()).isValid()) {
                    if (!this.smokey.isPresent() || bl && this.smokey.get() == CampfireBlock.isSmokeyPos(world, blockPos)) {
                        if (this.light.isPresent() && !this.light.get().matches(world, blockPos)) {
                            return false;
                        } else if (this.block.isPresent() && !this.block.get().matches(world, blockPos)) {
                            return false;
                        } else {
                            return !this.fluid.isPresent() || this.fluid.get().matches(world, blockPos);
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    public JsonElement serializeToJson() {
        return Util.getOrThrow(CODEC.encodeStart(JsonOps.INSTANCE, this), IllegalStateException::new);
    }

    public static Optional<LocationPredicate> fromJson(@Nullable JsonElement json) {
        return json != null && !json.isJsonNull() ? Optional.of(Util.getOrThrow(CODEC.parse(JsonOps.INSTANCE, json), JsonParseException::new)) : Optional.empty();
    }

    public static class Builder {
        private MinMaxBounds.Doubles x = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles y = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles z = MinMaxBounds.Doubles.ANY;
        private Optional<ResourceKey<Biome>> biome = Optional.empty();
        private Optional<ResourceKey<Structure>> structure = Optional.empty();
        private Optional<ResourceKey<Level>> dimension = Optional.empty();
        private Optional<Boolean> smokey = Optional.empty();
        private Optional<LightPredicate> light = Optional.empty();
        private Optional<BlockPredicate> block = Optional.empty();
        private Optional<FluidPredicate> fluid = Optional.empty();

        public static LocationPredicate.Builder location() {
            return new LocationPredicate.Builder();
        }

        public static LocationPredicate.Builder inBiome(ResourceKey<Biome> biome) {
            return location().setBiome(biome);
        }

        public static LocationPredicate.Builder inDimension(ResourceKey<Level> dimension) {
            return location().setDimension(dimension);
        }

        public static LocationPredicate.Builder inStructure(ResourceKey<Structure> structure) {
            return location().setStructure(structure);
        }

        public static LocationPredicate.Builder atYLocation(MinMaxBounds.Doubles y) {
            return location().setY(y);
        }

        public LocationPredicate.Builder setX(MinMaxBounds.Doubles x) {
            this.x = x;
            return this;
        }

        public LocationPredicate.Builder setY(MinMaxBounds.Doubles y) {
            this.y = y;
            return this;
        }

        public LocationPredicate.Builder setZ(MinMaxBounds.Doubles z) {
            this.z = z;
            return this;
        }

        public LocationPredicate.Builder setBiome(ResourceKey<Biome> biome) {
            this.biome = Optional.of(biome);
            return this;
        }

        public LocationPredicate.Builder setStructure(ResourceKey<Structure> structure) {
            this.structure = Optional.of(structure);
            return this;
        }

        public LocationPredicate.Builder setDimension(ResourceKey<Level> dimension) {
            this.dimension = Optional.of(dimension);
            return this;
        }

        public LocationPredicate.Builder setLight(LightPredicate.Builder light) {
            this.light = Optional.of(light.build());
            return this;
        }

        public LocationPredicate.Builder setBlock(BlockPredicate.Builder block) {
            this.block = Optional.of(block.build());
            return this;
        }

        public LocationPredicate.Builder setFluid(FluidPredicate.Builder fluid) {
            this.fluid = Optional.of(fluid.build());
            return this;
        }

        public LocationPredicate.Builder setSmokey(boolean smokey) {
            this.smokey = Optional.of(smokey);
            return this;
        }

        public LocationPredicate build() {
            Optional<LocationPredicate.PositionPredicate> optional = LocationPredicate.PositionPredicate.of(this.x, this.y, this.z);
            return new LocationPredicate(optional, this.biome, this.structure, this.dimension, this.smokey, this.light, this.block, this.fluid);
        }
    }

    static record PositionPredicate(MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z) {
        public static final Codec<LocationPredicate.PositionPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ExtraCodecs.strictOptionalField(MinMaxBounds.Doubles.CODEC, "x", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::x), ExtraCodecs.strictOptionalField(MinMaxBounds.Doubles.CODEC, "y", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::y), ExtraCodecs.strictOptionalField(MinMaxBounds.Doubles.CODEC, "z", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::z)).apply(instance, LocationPredicate.PositionPredicate::new);
        });

        static Optional<LocationPredicate.PositionPredicate> of(MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z) {
            return x.isAny() && y.isAny() && z.isAny() ? Optional.empty() : Optional.of(new LocationPredicate.PositionPredicate(x, y, z));
        }

        public boolean matches(double x, double y, double z) {
            return this.x.matches(x) && this.y.matches(y) && this.z.matches(z);
        }
    }
}
