package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class RedStoneOreBlock extends Block {

    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    public RedStoneOreBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(RedStoneOreBlock.LIT, false));
    }

    @Override
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {
        RedStoneOreBlock.interact(state, world, pos, player); // CraftBukkit - add entityhuman
        super.attack(state, world, pos, player);
    }

    @Override
    public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            // CraftBukkit start
            if (entity instanceof Player) {
                org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) entity, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                if (!event.isCancelled()) {
                    RedStoneOreBlock.interact(world.getBlockState(pos), world, pos, entity); // add entity
                }
            } else {
                EntityInteractEvent event = new EntityInteractEvent(entity.getBukkitEntity(), world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()));
                world.getCraftServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    RedStoneOreBlock.interact(world.getBlockState(pos), world, pos, entity); // add entity
                }
            }
            // CraftBukkit end
        }

        super.stepOn(world, pos, state, entity);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            RedStoneOreBlock.spawnParticles(world, pos);
        } else {
            RedStoneOreBlock.interact(state, world, pos, player); // CraftBukkit - add entityhuman
        }

        ItemStack itemstack = player.getItemInHand(hand);

        return itemstack.getItem() instanceof BlockItem && (new BlockPlaceContext(player, hand, itemstack, hit)).canPlace() ? InteractionResult.PASS : InteractionResult.SUCCESS;
    }

    private static void interact(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity) { // CraftBukkit - add Entity
        RedStoneOreBlock.spawnParticles(world, blockposition);
        if (!(Boolean) iblockdata.getValue(RedStoneOreBlock.LIT)) {
            // CraftBukkit start
            if (!CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, iblockdata.setValue(RedStoneOreBlock.LIT, true))) {
                return;
            }
            // CraftBukkit end
            world.setBlock(blockposition, (BlockState) iblockdata.setValue(RedStoneOreBlock.LIT, true), 3);
        }

    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return (Boolean) state.getValue(RedStoneOreBlock.LIT);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedStoneOreBlock.LIT)) {
            // CraftBukkit start
            if (CraftEventFactory.callBlockFadeEvent(world, pos, state.setValue(RedStoneOreBlock.LIT, false)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.setValue(RedStoneOreBlock.LIT, false), 3);
        }

    }

    @Override
    public void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, world, pos, tool, dropExperience);
        // CraftBukkit start - Delegated to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, ItemStack itemstack, boolean flag) {
        if (flag && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            int i = 1 + worldserver.random.nextInt(5);

            // this.popExperience(worldserver, blockposition, i);
            return i;
        }

        return 0;
        // CraftBukkit end
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedStoneOreBlock.LIT)) {
            RedStoneOreBlock.spawnParticles(world, pos);
        }

    }

    private static void spawnParticles(Level world, BlockPos pos) {
        double d0 = 0.5625D;
        RandomSource randomsource = world.random;
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];
            BlockPos blockposition1 = pos.relative(enumdirection);

            if (!world.getBlockState(blockposition1).isSolidRender(world, blockposition1)) {
                Direction.Axis enumdirection_enumaxis = enumdirection.getAxis();
                double d1 = enumdirection_enumaxis == Direction.Axis.X ? 0.5D + 0.5625D * (double) enumdirection.getStepX() : (double) randomsource.nextFloat();
                double d2 = enumdirection_enumaxis == Direction.Axis.Y ? 0.5D + 0.5625D * (double) enumdirection.getStepY() : (double) randomsource.nextFloat();
                double d3 = enumdirection_enumaxis == Direction.Axis.Z ? 0.5D + 0.5625D * (double) enumdirection.getStepZ() : (double) randomsource.nextFloat();

                world.addParticle(DustParticleOptions.REDSTONE, (double) pos.getX() + d1, (double) pos.getY() + d2, (double) pos.getZ() + d3, 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedStoneOreBlock.LIT);
    }
}
