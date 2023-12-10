package net.minecraft.world.level.block;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
// CraftBukkit end

public class CampfireBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty SIGNAL_FIRE = BlockStateProperties.SIGNAL_FIRE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape VIRTUAL_FENCE_POST = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final int SMOKE_DISTANCE = 5;
    private final boolean spawnParticles;
    private final int fireDamage;

    public CampfireBlock(boolean emitsParticles, int fireDamage, BlockBehaviour.Properties settings) {
        super(settings);
        this.spawnParticles = emitsParticles;
        this.fireDamage = fireDamage;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CampfireBlock.LIT, true)).setValue(CampfireBlock.SIGNAL_FIRE, false)).setValue(CampfireBlock.WATERLOGGED, false)).setValue(CampfireBlock.FACING, Direction.NORTH));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CampfireBlockEntity) {
            CampfireBlockEntity tileentitycampfire = (CampfireBlockEntity) tileentity;
            ItemStack itemstack = player.getItemInHand(hand);
            Optional<RecipeHolder<CampfireCookingRecipe>> optional = tileentitycampfire.getCookableRecipe(itemstack);

            if (optional.isPresent()) {
                if (!world.isClientSide && tileentitycampfire.placeFood(player, player.getAbilities().instabuild ? itemstack.copy() : itemstack, ((CampfireCookingRecipe) ((RecipeHolder) optional.get()).value()).getCookingTime())) {
                    player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                    return InteractionResult.SUCCESS;
                }

                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if ((Boolean) state.getValue(CampfireBlock.LIT) && entity instanceof LivingEntity && !EnchantmentHelper.hasFrostWalker((LivingEntity) entity)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.blockDamageRT.set(CraftBlock.at(world, pos)); // CraftBukkit // Folia - region threading
            entity.hurt(world.damageSources().inFire(), (float) this.fireDamage);
            org.bukkit.craftbukkit.event.CraftEventFactory.blockDamageRT.set(null); // CraftBukkit // Folia - region threading
        }

        super.entityInside(state, world, pos, entity);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof CampfireBlockEntity) {
                Containers.dropContents(world, pos, ((CampfireBlockEntity) tileentity).getItems());
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        boolean flag = world.getFluidState(blockposition).getType() == Fluids.WATER;

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(CampfireBlock.WATERLOGGED, flag)).setValue(CampfireBlock.SIGNAL_FIRE, this.isSmokeSource(world.getBlockState(blockposition.below())))).setValue(CampfireBlock.LIT, !flag)).setValue(CampfireBlock.FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(CampfireBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return direction == Direction.DOWN ? (BlockState) state.setValue(CampfireBlock.SIGNAL_FIRE, this.isSmokeSource(neighborState)) : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    private boolean isSmokeSource(BlockState state) {
        return state.is(Blocks.HAY_BLOCK);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CampfireBlock.SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(CampfireBlock.LIT)) {
            if (random.nextInt(10) == 0) {
                world.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
            }

            if (this.spawnParticles && random.nextInt(5) == 0) {
                for (int i = 0; i < random.nextInt(1) + 1; ++i) {
                    world.addParticle(ParticleTypes.LAVA, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, (double) (random.nextFloat() / 2.0F), 5.0E-5D, (double) (random.nextFloat() / 2.0F));
                }
            }

        }
    }

    public static void dowse(@Nullable Entity entity, LevelAccessor world, BlockPos pos, BlockState state) {
        if (world.isClientSide()) {
            for (int i = 0; i < 20; ++i) {
                CampfireBlock.makeParticles((Level) world, pos, (Boolean) state.getValue(CampfireBlock.SIGNAL_FIRE), true);
            }
        }

        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CampfireBlockEntity) {
            ((CampfireBlockEntity) tileentity).dowse();
        }

        world.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
    }

    @Override
    public boolean placeLiquid(LevelAccessor world, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!(Boolean) state.getValue(BlockStateProperties.WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            boolean flag = (Boolean) state.getValue(CampfireBlock.LIT);

            if (flag) {
                if (!world.isClientSide()) {
                    world.playSound((Player) null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                CampfireBlock.dowse((Entity) null, world, pos, state);
            }

            world.setBlock(pos, (BlockState) ((BlockState) state.setValue(CampfireBlock.WATERLOGGED, true)).setValue(CampfireBlock.LIT, false), 3);
            world.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(world));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockposition = hit.getBlockPos();

        if (!world.isClientSide && projectile.isOnFire() && projectile.mayInteract(world, blockposition) && !(Boolean) state.getValue(CampfireBlock.LIT) && !(Boolean) state.getValue(CampfireBlock.WATERLOGGED)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(world, blockposition, projectile).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlock(blockposition, (BlockState) state.setValue(BlockStateProperties.LIT, true), 11);
        }

    }

    public static void makeParticles(Level world, BlockPos pos, boolean isSignal, boolean lotsOfSmoke) {
        RandomSource randomsource = world.getRandom();
        SimpleParticleType particletype = isSignal ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;

        world.addAlwaysVisibleParticle(particletype, true, (double) pos.getX() + 0.5D + randomsource.nextDouble() / 3.0D * (double) (randomsource.nextBoolean() ? 1 : -1), (double) pos.getY() + randomsource.nextDouble() + randomsource.nextDouble(), (double) pos.getZ() + 0.5D + randomsource.nextDouble() / 3.0D * (double) (randomsource.nextBoolean() ? 1 : -1), 0.0D, 0.07D, 0.0D);
        if (lotsOfSmoke) {
            world.addParticle(ParticleTypes.SMOKE, (double) pos.getX() + 0.5D + randomsource.nextDouble() / 4.0D * (double) (randomsource.nextBoolean() ? 1 : -1), (double) pos.getY() + 0.4D, (double) pos.getZ() + 0.5D + randomsource.nextDouble() / 4.0D * (double) (randomsource.nextBoolean() ? 1 : -1), 0.0D, 0.005D, 0.0D);
        }

    }

    public static boolean isSmokeyPos(Level world, BlockPos pos) {
        for (int i = 1; i <= 5; ++i) {
            BlockPos blockposition1 = pos.below(i);
            BlockState iblockdata = world.getBlockState(blockposition1);

            if (CampfireBlock.isLitCampfire(iblockdata)) {
                return true;
            }

            boolean flag = Shapes.joinIsNotEmpty(CampfireBlock.VIRTUAL_FENCE_POST, iblockdata.getCollisionShape(world, pos, CollisionContext.empty()), BooleanOp.AND);

            if (flag) {
                BlockState iblockdata1 = world.getBlockState(blockposition1.below());

                return CampfireBlock.isLitCampfire(iblockdata1);
            }
        }

        return false;
    }

    public static boolean isLitCampfire(BlockState state) {
        return state.hasProperty(CampfireBlock.LIT) && state.is(BlockTags.CAMPFIRES) && (Boolean) state.getValue(CampfireBlock.LIT);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(CampfireBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CampfireBlock.FACING, rotation.rotate((Direction) state.getValue(CampfireBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(CampfireBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CampfireBlock.LIT, CampfireBlock.SIGNAL_FIRE, CampfireBlock.WATERLOGGED, CampfireBlock.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CampfireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? ((Boolean) state.getValue(CampfireBlock.LIT) ? createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::particleTick) : null) : ((Boolean) state.getValue(CampfireBlock.LIT) ? createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::cookTick) : createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::cooldownTick));
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CAMPFIRES, (blockbase_blockdata) -> {
            return blockbase_blockdata.hasProperty(CampfireBlock.WATERLOGGED) && blockbase_blockdata.hasProperty(CampfireBlock.LIT);
        }) && !(Boolean) state.getValue(CampfireBlock.WATERLOGGED) && !(Boolean) state.getValue(CampfireBlock.LIT);
    }
}
