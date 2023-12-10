// mc-dev import
package net.minecraft.world.item;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class DebugStickItem extends Item {

    public DebugStickItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player miner) {
        if (!world.isClientSide) {
            this.handleInteraction(miner, state, world, pos, false, miner.getItemInHand(InteractionHand.MAIN_HAND));
        }

        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player entityhuman = context.getPlayer();
        Level world = context.getLevel();

        if (!world.isClientSide && entityhuman != null) {
            BlockPos blockposition = context.getClickedPos();

            if (!this.handleInteraction(entityhuman, world.getBlockState(blockposition), world, blockposition, true, context.getItemInHand())) {
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    public boolean handleInteraction(Player player, BlockState state, LevelAccessor world, BlockPos pos, boolean update, ItemStack stack) {
        if (!player.canUseGameMasterBlocks() && !(player.getAbilities().instabuild && player.getBukkitEntity().hasPermission("minecraft.debugstick")) && !player.getBukkitEntity().hasPermission("minecraft.debugstick.always")) { // Spigot
            return false;
        } else {
            Block block = state.getBlock();
            StateDefinition<Block, BlockState> blockstatelist = block.getStateDefinition();
            Collection<Property<?>> collection = blockstatelist.getProperties();
            String s = BuiltInRegistries.BLOCK.getKey(block).toString();

            if (collection.isEmpty()) {
                DebugStickItem.message(player, Component.translatable(this.getDescriptionId() + ".empty", s));
                return false;
            } else {
                CompoundTag nbttagcompound = stack.getOrCreateTagElement("DebugProperty");
                String s1 = nbttagcompound.getString(s);
                Property<?> iblockstate = blockstatelist.getProperty(s1);

                if (update) {
                    if (iblockstate == null) {
                        iblockstate = (Property) collection.iterator().next();
                    }

                    BlockState iblockdata1 = DebugStickItem.cycleState(state, iblockstate, player.isSecondaryUseActive());

                    world.setBlock(pos, iblockdata1, 18);
                    DebugStickItem.message(player, Component.translatable(this.getDescriptionId() + ".update", iblockstate.getName(), DebugStickItem.getNameHelper(iblockdata1, iblockstate)));
                } else {
                    iblockstate = (Property) DebugStickItem.getRelative(collection, iblockstate, player.isSecondaryUseActive());
                    String s2 = iblockstate.getName();

                    nbttagcompound.putString(s, s2);
                    DebugStickItem.message(player, Component.translatable(this.getDescriptionId() + ".select", s2, DebugStickItem.getNameHelper(state, iblockstate)));
                }

                return true;
            }
        }
    }

    private static <T extends Comparable<T>> BlockState cycleState(BlockState state, Property<T> property, boolean inverse) {
        return (BlockState) state.setValue(property, DebugStickItem.getRelative(property.getPossibleValues(), state.getValue(property), inverse)); // CraftBukkit - decompile error
    }

    private static <T> T getRelative(Iterable<T> elements, @Nullable T current, boolean inverse) {
        return inverse ? Util.findPreviousInIterable(elements, current) : Util.findNextInIterable(elements, current);
    }

    private static void message(Player player, Component message) {
        ((ServerPlayer) player).sendSystemMessage(message, true);
    }

    private static <T extends Comparable<T>> String getNameHelper(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
