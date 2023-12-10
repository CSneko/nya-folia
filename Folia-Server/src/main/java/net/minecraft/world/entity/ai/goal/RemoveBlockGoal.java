package net.minecraft.world.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class RemoveBlockGoal extends MoveToBlockGoal {

    private final Block blockToRemove;
    private final Mob removerMob;
    private int ticksSinceReachedGoal;
    private static final int WAIT_AFTER_BLOCK_FOUND = 20;

    public RemoveBlockGoal(Block targetBlock, PathfinderMob mob, double speed, int maxYDifference) {
        super(mob, speed, 24, maxYDifference);
        this.blockToRemove = targetBlock;
        this.removerMob = mob;
    }

    @Override
    public boolean canUse() {
        if (!this.removerMob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        } else if (this.nextStartTick > 0) {
            --this.nextStartTick;
            return false;
        } else if (this.findNearestBlock()) {
            this.nextStartTick = reducedTickDelay(20);
            return true;
        } else {
            this.nextStartTick = this.nextStartTick(this.mob);
            return false;
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.removerMob.fallDistance = 1.0F;
    }

    @Override
    public void start() {
        super.start();
        this.ticksSinceReachedGoal = 0;
    }

    public void playDestroyProgressSound(LevelAccessor world, BlockPos pos) {}

    public void playBreakSound(Level world, BlockPos pos) {}

    @Override
    public void tick() {
        super.tick();
        Level world = this.removerMob.level();
        BlockPos blockposition = this.removerMob.blockPosition();
        BlockPos blockposition1 = this.getPosWithBlock(blockposition, world);
        RandomSource randomsource = this.removerMob.getRandom();

        if (this.isReachedTarget() && blockposition1 != null) {
            Vec3 vec3d;
            double d0;

            if (this.ticksSinceReachedGoal > 0) {
                vec3d = this.removerMob.getDeltaMovement();
                this.removerMob.setDeltaMovement(vec3d.x, 0.3D, vec3d.z);
                if (!world.isClientSide) {
                    d0 = 0.08D;
                    ((ServerLevel) world).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EGG)), (double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.7D, (double) blockposition1.getZ() + 0.5D, 3, ((double) randomsource.nextFloat() - 0.5D) * 0.08D, ((double) randomsource.nextFloat() - 0.5D) * 0.08D, ((double) randomsource.nextFloat() - 0.5D) * 0.08D, 0.15000000596046448D);
                }
            }

            if (this.ticksSinceReachedGoal % 2 == 0) {
                vec3d = this.removerMob.getDeltaMovement();
                this.removerMob.setDeltaMovement(vec3d.x, -0.3D, vec3d.z);
                if (this.ticksSinceReachedGoal % 6 == 0) {
                    this.playDestroyProgressSound(world, this.blockPos);
                }
            }

            if (this.ticksSinceReachedGoal > 60) {
                // CraftBukkit start - Step on eggs
                if (!CraftEventFactory.callEntityInteractEvent(this.removerMob, CraftBlock.at(world, blockposition1))) {
                    return;
                }
                // CraftBukkit end
                world.removeBlock(blockposition1, false);
                if (!world.isClientSide) {
                    for (int i = 0; i < 20; ++i) {
                        d0 = randomsource.nextGaussian() * 0.02D;
                        double d1 = randomsource.nextGaussian() * 0.02D;
                        double d2 = randomsource.nextGaussian() * 0.02D;

                        ((ServerLevel) world).sendParticles(ParticleTypes.POOF, (double) blockposition1.getX() + 0.5D, (double) blockposition1.getY(), (double) blockposition1.getZ() + 0.5D, 1, d0, d1, d2, 0.15000000596046448D);
                    }

                    this.playBreakSound(world, blockposition1);
                }
            }

            ++this.ticksSinceReachedGoal;
        }

    }

    @Nullable
    private BlockPos getPosWithBlock(BlockPos pos, BlockGetter world) {
        net.minecraft.world.level.block.state.BlockState block = world.getBlockStateIfLoaded(pos); // Paper
        if (block == null) return null; // Paper
        if (block.is(this.blockToRemove)) { // Paper
            return pos;
        } else {
            BlockPos[] ablockposition = new BlockPos[]{pos.below(), pos.west(), pos.east(), pos.north(), pos.south(), pos.below().below()};
            BlockPos[] ablockposition1 = ablockposition;
            int i = ablockposition.length;

            for (int j = 0; j < i; ++j) {
                BlockPos blockposition1 = ablockposition1[j];

                net.minecraft.world.level.block.state.BlockState block2 = world.getBlockStateIfLoaded(blockposition1); // Paper
                if (block2 != null && block2.is(this.blockToRemove)) { // Paper
                    return blockposition1;
                }
            }

            return null;
        }
    }

    @Override
    protected boolean isValidTarget(LevelReader world, BlockPos pos) {
        ChunkAccess ichunkaccess = world.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4); // Paper

        return ichunkaccess == null ? false : ichunkaccess.getBlockState(pos).is(this.blockToRemove) && ichunkaccess.getBlockState(pos.above()).isAir() && ichunkaccess.getBlockState(pos.above(2)).isAir();
    }
}
