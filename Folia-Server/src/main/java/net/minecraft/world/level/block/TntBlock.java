package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.TNTPrimeEvent.PrimeCause;
// CraftBukkit end

public class TntBlock extends Block {

    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;

    public TntBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(TntBlock.UNSTABLE, false));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            if (world.hasNeighborSignal(pos) && CraftEventFactory.callTNTPrimeEvent(world, pos, PrimeCause.REDSTONE, null, null)) { // CraftBukkit - TNTPrimeEvent
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = io.papermc.paper.util.MCUtil.toBukkitBlock(world, pos);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE, null).callEvent()) {
                    return;
                }
                // Paper end
                TntBlock.explode(world, pos);
                world.removeBlock(pos, false);
            }

        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.hasNeighborSignal(pos) && CraftEventFactory.callTNTPrimeEvent(world, pos, PrimeCause.REDSTONE, null, sourcePos)) { // CraftBukkit - TNTPrimeEvent
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = io.papermc.paper.util.MCUtil.toBukkitBlock(world, pos);
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE, null).callEvent()) {
                return;
            }
            // Paper end
            TntBlock.explode(world, pos);
            world.removeBlock(pos, false);
        }

    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide() && !player.isCreative() && (Boolean) state.getValue(TntBlock.UNSTABLE) && CraftEventFactory.callTNTPrimeEvent(world, pos, PrimeCause.BLOCK_BREAK, player, null)) { // CraftBukkit - TNTPrimeEvent
            TntBlock.explode(world, pos);
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void wasExploded(Level world, BlockPos pos, Explosion explosion) {
        if (!world.isClientSide) {
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = io.papermc.paper.util.MCUtil.toBukkitBlock(world, pos);
            org.bukkit.entity.Entity source = explosion.source != null ? explosion.source.getBukkitEntity() : null;
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.EXPLOSION, source).callEvent()) {
                return;
            }
            // Paper end
            PrimedTnt entitytntprimed = new PrimedTnt(world, (double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, explosion.getIndirectSourceEntity());
            int i = entitytntprimed.getFuse();

            entitytntprimed.setFuse((short) (world.random.nextInt(i / 4) + i / 8));
            world.addFreshEntity(entitytntprimed);
        }
    }

    public static void explode(Level world, BlockPos pos) {
        TntBlock.explode(world, pos, (LivingEntity) null);
    }

    private static void explode(Level world, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!world.isClientSide) {
            PrimedTnt entitytntprimed = new PrimedTnt(world, (double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, igniter);

            world.addFreshEntity(entitytntprimed);
            world.playSound((Player) null, entitytntprimed.getX(), entitytntprimed.getY(), entitytntprimed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            world.gameEvent((Entity) igniter, GameEvent.PRIME_FUSE, pos);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!itemstack.is(Items.FLINT_AND_STEEL) && !itemstack.is(Items.FIRE_CHARGE)) {
            return super.use(state, world, pos, player, hand, hit);
        } else {
            // CraftBukkit start - TNTPrimeEvent
            if (!CraftEventFactory.callTNTPrimeEvent(world, pos, PrimeCause.PLAYER, player, null)) {
                return InteractionResult.CONSUME;
            }
            // CraftBukkit end
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = io.papermc.paper.util.MCUtil.toBukkitBlock(world, pos);
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.ITEM, player.getBukkitEntity()).callEvent()) {
                return InteractionResult.FAIL;
            }
            // Paper end
            TntBlock.explode(world, pos, player);
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            Item item = itemstack.getItem();

            if (!player.isCreative()) {
                if (itemstack.is(Items.FLINT_AND_STEEL)) {
                    itemstack.hurtAndBreak(1, player, (entityhuman1) -> {
                        entityhuman1.broadcastBreakEvent(hand);
                    });
                } else {
                    itemstack.shrink(1);
                }
            }

            player.awardStat(Stats.ITEM_USED.get(item));
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!world.isClientSide) {
            BlockPos blockposition = hit.getBlockPos();
            Entity entity = projectile.getOwner();

            if (projectile.isOnFire() && projectile.mayInteract(world, blockposition)) {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(projectile, blockposition, state.getFluidState().createLegacyBlock()) || !CraftEventFactory.callTNTPrimeEvent(world, blockposition, PrimeCause.PROJECTILE, projectile, null)) { // Paper - fix wrong block state
                    return;
                }
                // CraftBukkit end
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = io.papermc.paper.util.MCUtil.toBukkitBlock(world, blockposition);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.PROJECTILE, projectile.getBukkitEntity()).callEvent()) {
                    return;
                }
                // Paper end
                TntBlock.explode(world, blockposition, entity instanceof LivingEntity ? (LivingEntity) entity : null);
                world.removeBlock(blockposition, false);
            }
        }

    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TntBlock.UNSTABLE);
    }
}
