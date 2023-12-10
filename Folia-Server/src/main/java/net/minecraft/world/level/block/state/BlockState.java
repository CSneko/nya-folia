package net.minecraft.world.level.block.state;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState extends BlockBehaviour.BlockStateBase {
    public static final Codec<BlockState> CODEC = codec(BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState).stable();

    // Paper start - optimise getType calls
    org.bukkit.Material cachedMaterial;

    public final org.bukkit.Material getBukkitMaterial() {
        if (this.cachedMaterial == null) {
            this.cachedMaterial = org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(this.getBlock());
        }

        return this.cachedMaterial;
    }
    // Paper end - optimise getType calls
    public BlockState(Block block, ImmutableMap<Property<?>, Comparable<?>> propertyMap, MapCodec<BlockState> codec) {
        super(block, propertyMap, codec);
    }

    @Override
    protected BlockState asState() {
        return this;
    }
}
