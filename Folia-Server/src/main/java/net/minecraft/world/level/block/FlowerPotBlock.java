package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlowerPotBlock extends Block {
    private static final Map<Block, Block> POTTED_BY_CONTENT = Maps.newHashMap();
    public static final float AABB_SIZE = 3.0F;
    protected static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);
    private final Block content;

    public FlowerPotBlock(Block content, BlockBehaviour.Properties settings) {
        super(settings);
        this.content = content;
        POTTED_BY_CONTENT.put(content, this);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemStack = player.getItemInHand(hand);
        Item item = itemStack.getItem();
        BlockState blockState = (item instanceof BlockItem ? POTTED_BY_CONTENT.getOrDefault(((BlockItem)item).getBlock(), Blocks.AIR) : Blocks.AIR).defaultBlockState();
        boolean bl = blockState.is(Blocks.AIR);
        boolean bl2 = this.isEmpty();
        if (bl != bl2) {
            // Paper start
            org.bukkit.entity.Player player1 = (org.bukkit.entity.Player) player.getBukkitEntity();
            boolean placing = bl2;
            org.bukkit.block.Block bukkitblock = org.bukkit.craftbukkit.block.CraftBlock.at(world, pos);
            org.bukkit.inventory.ItemStack bukkititemstack = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemStack);
            org.bukkit.Material mat = org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(content);
            org.bukkit.inventory.ItemStack bukkititemstack1 = new org.bukkit.inventory.ItemStack(mat, 1);
            org.bukkit.inventory.ItemStack whichitem = placing ? bukkititemstack : bukkititemstack1;

            io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent event = new io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent(player1, bukkitblock, whichitem, placing);
            player1.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                // Update client
                player1.sendBlockChange(bukkitblock.getLocation(), bukkitblock.getBlockData());
                player1.updateInventory();

                return InteractionResult.PASS;
            }
            // Paper end
            if (bl2) {
                world.setBlock(pos, blockState, 3);
                player.awardStat(Stats.POT_FLOWER);
                if (!player.getAbilities().instabuild) {
                    itemStack.shrink(1);
                }
            } else {
                ItemStack itemStack2 = new ItemStack(this.content);
                if (itemStack.isEmpty()) {
                    player.setItemInHand(hand, itemStack2);
                } else if (!player.addItem(itemStack2)) {
                    player.drop(itemStack2, false);
                }

                world.setBlock(pos, Blocks.FLOWER_POT.defaultBlockState(), 3);
            }

            world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return this.isEmpty() ? super.getCloneItemStack(world, pos, state) : new ItemStack(this.content);
    }

    private boolean isEmpty() {
        return this.content == Blocks.AIR;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    public Block getContent() {
        return this.content;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }
}
