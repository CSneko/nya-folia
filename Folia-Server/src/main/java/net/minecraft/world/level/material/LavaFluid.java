package net.minecraft.world.level.material;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public abstract class LavaFluid extends FlowingFluid {

    public static final float MIN_LEVEL_CUTOFF = 0.44444445F;

    public LavaFluid() {}

    @Override
    public Fluid getFlowing() {
        return Fluids.FLOWING_LAVA;
    }

    @Override
    public Fluid getSource() {
        return Fluids.LAVA;
    }

    @Override
    public Item getBucket() {
        return Items.LAVA_BUCKET;
    }

    @Override
    public void animateTick(Level world, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos blockposition1 = pos.above();

        if (world.getBlockState(blockposition1).isAir() && !world.getBlockState(blockposition1).isSolidRender(world, blockposition1)) {
            if (random.nextInt(100) == 0) {
                double d0 = (double) pos.getX() + random.nextDouble();
                double d1 = (double) pos.getY() + 1.0D;
                double d2 = (double) pos.getZ() + random.nextDouble();

                world.addParticle(ParticleTypes.LAVA, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                world.playLocalSound(d0, d1, d2, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }

            if (random.nextInt(200) == 0) {
                world.playLocalSound((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }

    }

    @Override
    public void randomTick(Level world, BlockPos pos, FluidState state, RandomSource random) {
        if (world.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            int i = random.nextInt(3);

            if (i > 0) {
                BlockPos blockposition1 = pos;

                for (int j = 0; j < i; ++j) {
                    blockposition1 = blockposition1.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                    if (!world.isLoaded(blockposition1)) {
                        return;
                    }

                    BlockState iblockdata = world.getBlockState(blockposition1);

                    if (iblockdata.isAir()) {
                        if (this.hasFlammableNeighbours(world, blockposition1)) {
                            // CraftBukkit start - Prevent lava putting something on fire
                            if (world.getBlockState(blockposition1).getBlock() != Blocks.FIRE) {
                                if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition1, pos).isCancelled()) {
                                    continue;
                                }
                            }
                            // CraftBukkit end
                            world.setBlockAndUpdate(blockposition1, BaseFireBlock.getState(world, blockposition1));
                            return;
                        }
                    } else if (iblockdata.blocksMotion()) {
                        return;
                    }
                }
            } else {
                for (int k = 0; k < 3; ++k) {
                    BlockPos blockposition2 = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);

                    if (!world.isLoaded(blockposition2)) {
                        return;
                    }

                    if (world.isEmptyBlock(blockposition2.above()) && this.isFlammable(world, blockposition2)) {
                        // CraftBukkit start - Prevent lava putting something on fire
                        BlockPos up = blockposition2.above();
                        if (world.getBlockState(up).getBlock() != Blocks.FIRE) {
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, up, pos).isCancelled()) {
                                continue;
                            }
                        }
                        // CraftBukkit end
                        world.setBlockAndUpdate(blockposition2.above(), BaseFireBlock.getState(world, blockposition2));
                    }
                }
            }

        }
    }

    private boolean hasFlammableNeighbours(LevelReader world, BlockPos pos) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (this.isFlammable(world, pos.relative(enumdirection))) {
                return true;
            }
        }

        return false;
    }

    private boolean isFlammable(LevelReader world, BlockPos pos) {
        return pos.getY() >= world.getMinBuildHeight() && pos.getY() < world.getMaxBuildHeight() && !world.hasChunkAt(pos) ? false : world.getBlockState(pos).ignitedByLava();
    }

    @Nullable
    @Override
    public ParticleOptions getDripParticle() {
        return ParticleTypes.DRIPPING_LAVA;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor world, BlockPos pos, BlockState state) {
        this.fizz(world, pos);
    }

    @Override
    public int getSlopeFindDistance(LevelReader world) {
        return world.dimensionType().ultraWarm() ? 4 : 2;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return (BlockState) Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    @Override
    public int getDropOff(LevelReader world) {
        return world.dimensionType().ultraWarm() ? 1 : 2;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter world, BlockPos pos, Fluid fluid, Direction direction) {
        return state.getHeight(world, pos) >= 0.44444445F && fluid.is(FluidTags.WATER);
    }

    @Override
    public int getTickDelay(LevelReader world) {
        return world.dimensionType().ultraWarm() ? 10 : 30;
    }

    @Override
    public int getSpreadDelay(Level world, BlockPos pos, FluidState oldState, FluidState newState) {
        int i = this.getTickDelay(world);

        if (!oldState.isEmpty() && !newState.isEmpty() && !(Boolean) oldState.getValue(LavaFluid.FALLING) && !(Boolean) newState.getValue(LavaFluid.FALLING) && newState.getHeight(world, pos) > oldState.getHeight(world, pos) && world.getRandom().nextInt(4) != 0) {
            i *= 4;
        }

        return i;
    }

    private void fizz(LevelAccessor world, BlockPos pos) {
        world.levelEvent(1501, pos, 0);
    }

    @Override
    protected boolean canConvertToSource(Level world) {
        return world.getGameRules().getBoolean(GameRules.RULE_LAVA_SOURCE_CONVERSION);
    }

    @Override
    protected void spreadTo(LevelAccessor world, BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        if (direction == Direction.DOWN) {
            FluidState fluid1 = world.getFluidState(pos);

            if (this.is(FluidTags.LAVA) && fluid1.is(FluidTags.WATER)) {
                if (state.getBlock() instanceof LiquidBlock) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world.getMinecraftWorld(), pos, Blocks.STONE.defaultBlockState(), 3)) {
                        return;
                    }
                    // CraftBukkit end
                }

                this.fizz(world, pos);
                return;
            }
        }

        super.spreadTo(world, pos, state, direction, fluidState);
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected float getExplosionResistance() {
        return Blocks.LAVA.getExplosionResistance(); // Paper
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    public static class Flowing extends LavaFluid {

        public Flowing() {}

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LavaFluid.Flowing.LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return (Integer) state.getValue(LavaFluid.Flowing.LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends LavaFluid {

        public Source() {}

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
