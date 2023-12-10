package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractCandleBlock extends Block {

    public static final int LIGHT_PER_CANDLE = 3;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    protected AbstractCandleBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    protected abstract Iterable<Vec3> getParticleOffsets(BlockState state);

    public static boolean isLit(BlockState state) {
        return state.hasProperty(AbstractCandleBlock.LIT) && (state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) && (Boolean) state.getValue(AbstractCandleBlock.LIT);
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!world.isClientSide && projectile.isOnFire() && this.canBeLit(state)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, hit.getBlockPos(), projectile).isCancelled()) {
                return;
            }
            // CraftBukkit end
            AbstractCandleBlock.setLit(world, state, hit.getBlockPos(), true);
        }

    }

    protected boolean canBeLit(BlockState state) {
        return !(Boolean) state.getValue(AbstractCandleBlock.LIT);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(AbstractCandleBlock.LIT)) {
            this.getParticleOffsets(state).forEach((vec3d) -> {
                AbstractCandleBlock.addParticlesAndSound(world, vec3d.add((double) pos.getX(), (double) pos.getY(), (double) pos.getZ()), random);
            });
        }
    }

    private static void addParticlesAndSound(Level world, Vec3 vec3d, RandomSource random) {
        float f = random.nextFloat();

        if (f < 0.3F) {
            world.addParticle(ParticleTypes.SMOKE, vec3d.x, vec3d.y, vec3d.z, 0.0D, 0.0D, 0.0D);
            if (f < 0.17F) {
                world.playLocalSound(vec3d.x + 0.5D, vec3d.y + 0.5D, vec3d.z + 0.5D, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
            }
        }

        world.addParticle(ParticleTypes.SMALL_FLAME, vec3d.x, vec3d.y, vec3d.z, 0.0D, 0.0D, 0.0D);
    }

    public static void extinguish(@Nullable Player player, BlockState state, LevelAccessor world, BlockPos pos) {
        AbstractCandleBlock.setLit(world, state, pos, false);
        if (state.getBlock() instanceof AbstractCandleBlock) {
            ((AbstractCandleBlock) state.getBlock()).getParticleOffsets(state).forEach((vec3d) -> {
                world.addParticle(ParticleTypes.SMOKE, (double) pos.getX() + vec3d.x(), (double) pos.getY() + vec3d.y(), (double) pos.getZ() + vec3d.z(), 0.0D, 0.10000000149011612D, 0.0D);
            });
        }

        world.playSound((Player) null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        world.gameEvent((Entity) player, GameEvent.BLOCK_CHANGE, pos);
    }

    private static void setLit(LevelAccessor world, BlockState state, BlockPos pos, boolean lit) {
        world.setBlock(pos, (BlockState) state.setValue(AbstractCandleBlock.LIT, lit), 11);
    }
}
