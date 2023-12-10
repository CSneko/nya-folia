package net.minecraft.world.level.material;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FluidState extends StateHolder<Fluid, FluidState> {
    public static final Codec<FluidState> CODEC = codec(BuiltInRegistries.FLUID.byNameCodec(), Fluid::defaultFluidState).stable();
    public static final int AMOUNT_MAX = 9;
    public static final int AMOUNT_FULL = 8;

    // Paper start
    protected final boolean isEmpty;
    // Paper end
    public FluidState(Fluid fluid, ImmutableMap<Property<?>, Comparable<?>> propertiesMap, MapCodec<FluidState> codec) {
        super(fluid, propertiesMap, codec);
        this.isEmpty = fluid.isEmpty(); // Paper - moved from isEmpty()
    }

    public Fluid getType() {
        return this.owner;
    }

    public boolean isSource() {
        return this.getType().isSource(this);
    }

    public boolean isSourceOfType(Fluid fluid) {
        return this.owner == fluid && this.owner.isSource(this);
    }

    public boolean isEmpty() {
        return this.isEmpty; // Paper - moved into constructor
    }

    public float getHeight(BlockGetter world, BlockPos pos) {
        return this.getType().getHeight(this, world, pos);
    }

    public float getOwnHeight() {
        return this.getType().getOwnHeight(this);
    }

    public int getAmount() {
        return this.getType().getAmount(this);
    }

    public boolean shouldRenderBackwardUpFace(BlockGetter world, BlockPos pos) {
        for(int i = -1; i <= 1; ++i) {
            for(int j = -1; j <= 1; ++j) {
                BlockPos blockPos = pos.offset(i, 0, j);
                FluidState fluidState = world.getFluidState(blockPos);
                if (!fluidState.getType().isSame(this.getType()) && !world.getBlockState(blockPos).isSolidRender(world, blockPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void tick(Level world, BlockPos pos) {
        this.getType().tick(world, pos, this);
    }

    public void animateTick(Level world, BlockPos pos, RandomSource random) {
        this.getType().animateTick(world, pos, this, random);
    }

    public boolean isRandomlyTicking() {
        return this.getType().isRandomlyTicking();
    }

    public void randomTick(Level world, BlockPos pos, RandomSource random) {
        this.getType().randomTick(world, pos, this, random);
    }

    public Vec3 getFlow(BlockGetter world, BlockPos pos) {
        return this.getType().getFlow(world, pos, this);
    }

    public BlockState createLegacyBlock() {
        return this.getType().createLegacyBlock(this);
    }

    @Nullable
    public ParticleOptions getDripParticle() {
        return this.getType().getDripParticle();
    }

    public boolean is(TagKey<Fluid> tag) {
        return this.getType().builtInRegistryHolder().is(tag);
    }

    public boolean is(HolderSet<Fluid> fluids) {
        return fluids.contains(this.getType().builtInRegistryHolder());
    }

    public boolean is(Fluid fluid) {
        return this.getType() == fluid;
    }

    public float getExplosionResistance() {
        return this.getType().getExplosionResistance();
    }

    public boolean canBeReplacedWith(BlockGetter world, BlockPos pos, Fluid fluid, Direction direction) {
        return this.getType().canBeReplacedWith(this, world, pos, fluid, direction);
    }

    public VoxelShape getShape(BlockGetter world, BlockPos pos) {
        return this.getType().getShape(this, world, pos);
    }

    public Holder<Fluid> holder() {
        return this.owner.builtInRegistryHolder();
    }

    public Stream<TagKey<Fluid>> getTags() {
        return this.owner.builtInRegistryHolder().tags();
    }
}
