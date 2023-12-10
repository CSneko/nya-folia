package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {

    private static final int SECONDS_ON_FIRE = 8;
    private final float fireDamage;
    protected static final float AABB_OFFSET = 1.0F;
    protected static final VoxelShape DOWN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public BaseFireBlock(BlockBehaviour.Properties settings, float damage) {
        super(settings);
        this.fireDamage = damage;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return BaseFireBlock.getState(ctx.getLevel(), ctx.getClickedPos());
    }

    public static BlockState getState(BlockGetter world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();
        BlockState iblockdata = world.getBlockState(blockposition1);

        return SoulFireBlock.canSurviveOnBlock(iblockdata) ? Blocks.SOUL_FIRE.defaultBlockState() : ((FireBlock) Blocks.FIRE).getStateForPlacement(world, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return BaseFireBlock.DOWN_AABB;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0) {
            world.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
        }

        BlockPos blockposition1 = pos.below();
        BlockState iblockdata1 = world.getBlockState(blockposition1);
        double d0;
        double d1;
        double d2;
        int i;

        if (!this.canBurn(iblockdata1) && !iblockdata1.isFaceSturdy(world, blockposition1, Direction.UP)) {
            if (this.canBurn(world.getBlockState(pos.west()))) {
                for (i = 0; i < 2; ++i) {
                    d0 = (double) pos.getX() + random.nextDouble() * 0.10000000149011612D;
                    d1 = (double) pos.getY() + random.nextDouble();
                    d2 = (double) pos.getZ() + random.nextDouble();
                    world.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(pos.east()))) {
                for (i = 0; i < 2; ++i) {
                    d0 = (double) (pos.getX() + 1) - random.nextDouble() * 0.10000000149011612D;
                    d1 = (double) pos.getY() + random.nextDouble();
                    d2 = (double) pos.getZ() + random.nextDouble();
                    world.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(pos.north()))) {
                for (i = 0; i < 2; ++i) {
                    d0 = (double) pos.getX() + random.nextDouble();
                    d1 = (double) pos.getY() + random.nextDouble();
                    d2 = (double) pos.getZ() + random.nextDouble() * 0.10000000149011612D;
                    world.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(pos.south()))) {
                for (i = 0; i < 2; ++i) {
                    d0 = (double) pos.getX() + random.nextDouble();
                    d1 = (double) pos.getY() + random.nextDouble();
                    d2 = (double) (pos.getZ() + 1) - random.nextDouble() * 0.10000000149011612D;
                    world.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(world.getBlockState(pos.above()))) {
                for (i = 0; i < 2; ++i) {
                    d0 = (double) pos.getX() + random.nextDouble();
                    d1 = (double) (pos.getY() + 1) - random.nextDouble() * 0.10000000149011612D;
                    d2 = (double) pos.getZ() + random.nextDouble();
                    world.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }
        } else {
            for (i = 0; i < 3; ++i) {
                d0 = (double) pos.getX() + random.nextDouble();
                d1 = (double) pos.getY() + random.nextDouble() * 0.5D + 0.5D;
                d2 = (double) pos.getZ() + random.nextDouble();
                world.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            }
        }

    }

    protected abstract boolean canBurn(BlockState state);

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!entity.fireImmune()) {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            if (entity.getRemainingFireTicks() == 0) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityCombustEvent event = new org.bukkit.event.entity.EntityCombustByBlockEvent(org.bukkit.craftbukkit.block.CraftBlock.at(world, pos), entity.getBukkitEntity(), 8);
                world.getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.setSecondsOnFire(event.getDuration(), false);
                    // Paper start - fix EntityCombustEvent cancellation.
                } else {
                    entity.setRemainingFireTicks(entity.getRemainingFireTicks() - 1);
                    // Paper end
                }
                // CraftBukkit end
            }
        }

        entity.hurt(world.damageSources().inFire(), this.fireDamage);
        super.entityInside(state, world, pos, entity);
    }

    // Paper start - ItemActionContext param
    @Override public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) { this.onPlace(state, world, pos, oldState, notify, null); }
    @Override
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag, net.minecraft.world.item.context.UseOnContext itemActionContext) {
        // Paper end
        if (!iblockdata1.is(iblockdata.getBlock())) {
            if (BaseFireBlock.inPortalDimension(world)) {
                Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(world, blockposition, Direction.Axis.X);

                if (optional.isPresent()) {
                    ((PortalShape) optional.get()).createPortalBlocks(itemActionContext); // Paper - pass ItemActionContext param
                    return;
                }
            }

            if (!iblockdata.canSurvive(world, blockposition)) {
                fireExtinguished(world, blockposition); // CraftBukkit - fuel block broke
            }

        }
    }

    private static boolean inPortalDimension(Level world) {
        return world.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.OVERWORLD || world.getTypeKey() == net.minecraft.world.level.dimension.LevelStem.NETHER; // CraftBukkit - getTypeKey()
    }

    @Override
    protected void spawnDestroyParticles(Level world, Player player, BlockPos pos, BlockState state) {}

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide()) {
            world.levelEvent((Player) null, 1009, pos, 0);
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    public static boolean canBePlacedAt(Level world, BlockPos pos, Direction direction) {
        BlockState iblockdata = world.getBlockState(pos);

        return !iblockdata.isAir() ? false : BaseFireBlock.getState(world, pos).canSurvive(world, pos) || BaseFireBlock.isPortal(world, pos, direction);
    }

    private static boolean isPortal(Level world, BlockPos pos, Direction direction) {
        if (!BaseFireBlock.inPortalDimension(world)) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();
            boolean flag = false;
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection1 = aenumdirection[j];

                if (world.getBlockState(blockposition_mutableblockposition.set(pos).move(enumdirection1)).is(Blocks.OBSIDIAN)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                Direction.Axis enumdirection_enumaxis = direction.getAxis().isHorizontal() ? direction.getCounterClockWise().getAxis() : Direction.Plane.HORIZONTAL.getRandomAxis(world.random);

                return PortalShape.findEmptyPortalShape(world, pos, enumdirection_enumaxis).isPresent();
            }
        }
    }

    // CraftBukkit start
    protected void fireExtinguished(net.minecraft.world.level.LevelAccessor world, BlockPos position) {
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, position, Blocks.AIR.defaultBlockState()).isCancelled()) {
            world.removeBlock(position, false);
        }
    }
    // CraftBukkit end
}
