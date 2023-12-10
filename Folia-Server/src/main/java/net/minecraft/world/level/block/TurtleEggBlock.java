package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class TurtleEggBlock extends Block {

    public static final int MAX_HATCH_LEVEL = 2;
    public static final int MIN_EGGS = 1;
    public static final int MAX_EGGS = 4;
    private static final VoxelShape ONE_EGG_AABB = Block.box(3.0D, 0.0D, 3.0D, 12.0D, 7.0D, 12.0D);
    private static final VoxelShape MULTIPLE_EGGS_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    public static final IntegerProperty EGGS = BlockStateProperties.EGGS;

    public TurtleEggBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TurtleEggBlock.HATCH, 0)).setValue(TurtleEggBlock.EGGS, 1));
    }

    @Override
    public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            this.destroyEgg(world, state, pos, entity, 100);
        }

        super.stepOn(world, pos, state, entity);
    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!(entity instanceof Zombie)) {
            this.destroyEgg(world, state, pos, entity, 3);
        }

        super.fallOn(world, state, pos, entity, fallDistance);
    }

    private void destroyEgg(Level world, BlockState state, BlockPos pos, Entity entity, int inverseChance) {
        if (this.canDestroyEgg(world, entity)) {
            if (!world.isClientSide && world.random.nextInt(inverseChance) == 0 && state.is(Blocks.TURTLE_EGG)) {
                // CraftBukkit start - Step on eggs
                org.bukkit.event.Cancellable cancellable;
                if (entity instanceof Player) {
                    cancellable = CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                } else {
                    cancellable = new EntityInteractEvent(entity.getBukkitEntity(), CraftBlock.at(world, pos));
                    world.getCraftServer().getPluginManager().callEvent((EntityInteractEvent) cancellable);
                }

                if (cancellable.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.decreaseEggs(world, pos, state);
            }

        }
    }

    public void decreaseEggs(Level world, BlockPos pos, BlockState state) {
        world.playSound((Player) null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + world.random.nextFloat() * 0.2F);
        int i = (Integer) state.getValue(TurtleEggBlock.EGGS);

        if (i <= 1) {
            world.destroyBlock(pos, false);
        } else {
            world.setBlock(pos, (BlockState) state.setValue(TurtleEggBlock.EGGS, i - 1), 2);
            world.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));
            world.levelEvent(2001, pos, Block.getId(state));
        }

    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (this.shouldUpdateHatchLevel(world) && TurtleEggBlock.onSand(world, pos)) {
            int i = (Integer) state.getValue(TurtleEggBlock.HATCH);

            if (i < 2) {
                // CraftBukkit start - Call BlockGrowEvent
                if (!CraftEventFactory.handleBlockGrowEvent(world, pos, state.setValue(TurtleEggBlock.HATCH, i + 1), 2)) {
                    return;
                }
                // CraftBukkit end
                world.playSound((Player) null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                // worldserver.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockTurtleEgg.HATCH, i + 1), 2); // CraftBukkit - handled above
                world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            } else {
                // CraftBukkit start - Call BlockFadeEvent
                if (CraftEventFactory.callBlockFadeEvent(world, pos, Blocks.AIR.defaultBlockState()).isCancelled()) {
                    return;
                }
                // CraftBukkit end
                world.playSound((Player) null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
                world.removeBlock(pos, false);
                world.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(state));

                for (int j = 0; j < (Integer) state.getValue(TurtleEggBlock.EGGS); ++j) {
                    world.levelEvent(2001, pos, Block.getId(state));
                    Turtle entityturtle = (Turtle) EntityType.TURTLE.create(world);

                    if (entityturtle != null) {
                        entityturtle.setAge(-24000);
                        entityturtle.setHomePos(pos);
                        entityturtle.moveTo((double) pos.getX() + 0.3D + (double) j * 0.2D, (double) pos.getY(), (double) pos.getZ() + 0.3D, 0.0F, 0.0F);
                        world.addFreshEntity(entityturtle, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG); // CraftBukkit
                    }
                }
            }
        }

    }

    public static boolean onSand(BlockGetter world, BlockPos pos) {
        return TurtleEggBlock.isSand(world, pos.below());
    }

    public static boolean isSand(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos).is(BlockTags.SAND);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (TurtleEggBlock.onSand(world, pos) && !world.isClientSide) {
            world.levelEvent(2005, pos, 0);
        }

    }

    private boolean shouldUpdateHatchLevel(Level world) {
        float f = world.getTimeOfDay(1.0F);

        return (double) f < 0.69D && (double) f > 0.65D ? true : world.random.nextInt(500) == 0;
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool, boolean includeDrops) { // Paper
        super.playerDestroy(world, player, pos, state, blockEntity, tool, includeDrops); // Paper
        this.decreaseEggs(world, pos, state);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && (Integer) state.getValue(TurtleEggBlock.EGGS) < 4 ? true : super.canBeReplaced(state, context);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState iblockdata = ctx.getLevel().getBlockState(ctx.getClickedPos());

        return iblockdata.is((Block) this) ? (BlockState) iblockdata.setValue(TurtleEggBlock.EGGS, Math.min(4, (Integer) iblockdata.getValue(TurtleEggBlock.EGGS) + 1)) : super.getStateForPlacement(ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (Integer) state.getValue(TurtleEggBlock.EGGS) > 1 ? TurtleEggBlock.MULTIPLE_EGGS_AABB : TurtleEggBlock.ONE_EGG_AABB;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TurtleEggBlock.HATCH, TurtleEggBlock.EGGS);
    }

    private boolean canDestroyEgg(Level world, Entity entity) {
        return !(entity instanceof Turtle) && !(entity instanceof Bat) ? (!(entity instanceof LivingEntity) ? false : entity instanceof Player || world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) : false;
    }
}
