package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FrogspawnBlock extends Block {
    private static final int MIN_TADPOLES_SPAWN = 2;
    private static final int MAX_TADPOLES_SPAWN = 5;
    private static final int DEFAULT_MIN_HATCH_TICK_DELAY = 3600;
    private static final int DEFAULT_MAX_HATCH_TICK_DELAY = 12000;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.5D, 16.0D);
    private static int minHatchTickDelay = 3600;
    private static int maxHatchTickDelay = 12000;

    public FrogspawnBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return mayPlaceOn(world, pos.below());
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleTick(pos, this, getFrogspawnHatchDelay(world.getRandom()));
    }

    private static int getFrogspawnHatchDelay(RandomSource random) {
        return random.nextInt(minHatchTickDelay, maxHatchTickDelay);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return !this.canSurvive(state, world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!this.canSurvive(state, world, pos)) {
            this.destroyBlock(world, pos);
        } else {
            this.hatchFrogspawn(world, pos, random);
        }
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (entity.getType().equals(EntityType.FALLING_BLOCK)) {
            this.destroyBlock(world, pos);
        }

    }

    private static boolean mayPlaceOn(BlockGetter world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        FluidState fluidState2 = world.getFluidState(pos.above());
        return fluidState.getType() == Fluids.WATER && fluidState2.getType() == Fluids.EMPTY;
    }

    private void hatchFrogspawn(ServerLevel world, BlockPos pos, RandomSource random) {
        // Paper start - Call BlockFadeEvent
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, Blocks.AIR.defaultBlockState()).isCancelled()) {
            return;
        }
        // Paper end
        this.destroyBlock(world, pos);
        world.playSound((Player)null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.spawnTadpoles(world, pos, random);
    }

    private void destroyBlock(Level world, BlockPos pos) {
        world.destroyBlock(pos, false);
    }

    private void spawnTadpoles(ServerLevel world, BlockPos pos, RandomSource random) {
        int i = random.nextInt(2, 6);

        for(int j = 1; j <= i; ++j) {
            Tadpole tadpole = EntityType.TADPOLE.create(world);
            if (tadpole != null) {
                double d = (double)pos.getX() + this.getRandomTadpolePositionOffset(random);
                double e = (double)pos.getZ() + this.getRandomTadpolePositionOffset(random);
                int k = random.nextInt(1, 361);
                tadpole.moveTo(d, (double)pos.getY() - 0.5D, e, (float)k, 0.0F);
                tadpole.setPersistenceRequired();
                world.addFreshEntity(tadpole, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // Paper
            }
        }

    }

    private double getRandomTadpolePositionOffset(RandomSource random) {
        double d = (double)(Tadpole.HITBOX_WIDTH / 2.0F);
        return Mth.clamp(random.nextDouble(), d, 1.0D - d);
    }

    @VisibleForTesting
    public static void setHatchDelay(int min, int max) {
        minHatchTickDelay = min;
        maxHatchTickDelay = max;
    }

    @VisibleForTesting
    public static void setDefaultHatchDelay() {
        minHatchTickDelay = 3600;
        maxHatchTickDelay = 12000;
    }
}
