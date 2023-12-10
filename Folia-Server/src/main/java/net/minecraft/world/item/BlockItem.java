package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockCanBuildEvent;
// CraftBukkit end

public class BlockItem extends Item {

    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    public static final String BLOCK_STATE_TAG = "BlockStateTag";
    /** @deprecated */
    @Deprecated
    private final Block block;

    public BlockItem(Block block, Item.Properties settings) {
        super(settings);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult enuminteractionresult = this.place(new BlockPlaceContext(context));

        if (!enuminteractionresult.consumesAction() && this.isEdible()) {
            InteractionResult enuminteractionresult1 = this.use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();

            return enuminteractionresult1 == InteractionResult.CONSUME ? InteractionResult.CONSUME_PARTIAL : enuminteractionresult1;
        } else {
            return enuminteractionresult;
        }
    }

    public InteractionResult place(BlockPlaceContext context) {
        if (!this.getBlock().isEnabled(context.getLevel().enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (!context.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockactioncontext1 = this.updatePlacementContext(context);

            if (blockactioncontext1 == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState iblockdata = this.getPlacementState(blockactioncontext1);
                // CraftBukkit start - special case for handling block placement with water lilies and snow buckets
                org.bukkit.block.BlockState blockstate = null;
                if (this instanceof PlaceOnWaterBlockItem || this instanceof SolidBucketItem) {
                    blockstate = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockactioncontext1.getLevel(), blockactioncontext1.getClickedPos());
                }
                final org.bukkit.block.BlockState oldBlockstate = blockstate != null ? blockstate : org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockactioncontext1.getLevel(), blockactioncontext1.getClickedPos()); // Paper
                // CraftBukkit end

                if (iblockdata == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockactioncontext1, iblockdata)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos blockposition = blockactioncontext1.getClickedPos();
                    Level world = blockactioncontext1.getLevel();
                    Player entityhuman = blockactioncontext1.getPlayer();
                    ItemStack itemstack = blockactioncontext1.getItemInHand();
                    BlockState iblockdata1 = world.getBlockState(blockposition);

                    if (iblockdata1.is(iblockdata.getBlock())) {
                        iblockdata1 = this.updateBlockStateFromTag(blockposition, world, itemstack, iblockdata1);
                        // Paper start - reset block on exception
                        try {
                        this.updateCustomBlockEntityTag(blockposition, world, entityhuman, itemstack, iblockdata1);
                        } catch (Exception e) {
                            oldBlockstate.update(true, false);
                            if (entityhuman instanceof ServerPlayer player) {
                                org.apache.logging.log4j.LogManager.getLogger().error("Player {} tried placing invalid block", player.getScoreboardName(), e);
                                player.getBukkitEntity().kickPlayer("Packet processing error");
                                return InteractionResult.FAIL;
                            }
                            throw e; // Rethrow exception if not placed by a player
                        }
                        // Paper end
                        iblockdata1.getBlock().setPlacedBy(world, blockposition, iblockdata1, entityhuman, itemstack);
                        // CraftBukkit start
                        if (blockstate != null) {
                            org.bukkit.event.block.BlockPlaceEvent placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent((ServerLevel) world, entityhuman, blockactioncontext1.getHand(), blockstate, blockposition.getX(), blockposition.getY(), blockposition.getZ());
                            if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                                blockstate.update(true, false);

                                if (true) { // Paper - if the event is called here, the inventory should be updated
                                    ((ServerPlayer) entityhuman).connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(world, blockposition.below())); // Paper - update block below
                                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory(); // SPIGOT-4541
                                }
                                return InteractionResult.FAIL;
                            }
                        }
                        // CraftBukkit end
                        if (entityhuman instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) entityhuman, blockposition, itemstack);
                        }
                    }

                    SoundType soundeffecttype = iblockdata1.getSoundType();

                    if (entityhuman == null) world.playSound(entityhuman, blockposition, this.getPlaceSound(iblockdata1), net.minecraft.sounds.SoundSource.BLOCKS, (soundeffecttype.getVolume() + 1.0F) / 2.0F, soundeffecttype.getPitch() * 0.8F); // Paper - reintroduce this for the dispenser (i.e the shulker)
                    world.gameEvent(GameEvent.BLOCK_PLACE, blockposition, GameEvent.Context.of(entityhuman, iblockdata1));
                    if ((entityhuman == null || !entityhuman.getAbilities().instabuild) && itemstack != ItemStack.EMPTY) { // CraftBukkit
                        itemstack.shrink(1);
                    }

                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            }
        }
    }

    protected SoundEvent getPlaceSound(BlockState state) {
        return state.getSoundType().getPlaceSound();
    }

    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return context;
    }

    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, @Nullable Player player, ItemStack stack, BlockState state) {
        return BlockItem.updateCustomBlockEntityTag(world, player, pos, stack);
    }

    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState iblockdata = this.getBlock().getStateForPlacement(context);

        return iblockdata != null && this.canPlace(context, iblockdata) ? iblockdata : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos pos, Level world, ItemStack stack, BlockState state) {
        BlockState iblockdata1 = state;
        CompoundTag nbttagcompound = stack.getTag();

        if (nbttagcompound != null) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("BlockStateTag");
            // CraftBukkit start
            iblockdata1 = BlockItem.getBlockState(iblockdata1, nbttagcompound1);
        }

        if (iblockdata1 != state) {
            world.setBlock(pos, iblockdata1, 2);
        }

        return iblockdata1;
    }

    public static BlockState getBlockState(BlockState iblockdata, CompoundTag nbttagcompound1) {
        BlockState iblockdata1 = iblockdata;
        {
            // CraftBukkit end
            StateDefinition<Block, BlockState> blockstatelist = iblockdata.getBlock().getStateDefinition();
            Iterator iterator = nbttagcompound1.getAllKeys().iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                Property<?> iblockstate = blockstatelist.getProperty(s);

                if (iblockstate != null) {
                    String s1 = nbttagcompound1.get(s).getAsString();

                    iblockdata1 = BlockItem.updateState(iblockdata1, iblockstate, s1);
                }
            }
        }
        return iblockdata1;
    }

    private static <T extends Comparable<T>> BlockState updateState(BlockState state, Property<T> property, String name) {
        return (BlockState) property.getValue(name).map((comparable) -> {
            return (BlockState) state.setValue(property, comparable);
        }).orElse(state);
    }

    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        Player entityhuman = context.getPlayer();
        CollisionContext voxelshapecollision = entityhuman == null ? CollisionContext.empty() : CollisionContext.of(entityhuman);
        // CraftBukkit start - store default return
        Level world = context.getLevel(); // Paper
        boolean defaultReturn = (!this.mustSurvive() || state.canSurvive(context.getLevel(), context.getClickedPos())) && world.checkEntityCollision(state, entityhuman, voxelshapecollision, context.getClickedPos(), true); // Paper
        org.bukkit.entity.Player player = (context.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) context.getPlayer().getBukkitEntity() : null;

        BlockCanBuildEvent event = new BlockCanBuildEvent(CraftBlock.at(context.getLevel(), context.getClickedPos()), player, CraftBlockData.fromData(state), defaultReturn, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand())); // Paper - expose hand
        context.getLevel().getCraftServer().getPluginManager().callEvent(event);

        return event.isBuildable();
        // CraftBukkit end
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return context.getLevel().setBlock(context.getClickedPos(), state, 11);
    }

    public static boolean updateCustomBlockEntityTag(Level world, @Nullable Player player, BlockPos pos, ItemStack stack) {
        MinecraftServer minecraftserver = world.getServer();

        if (minecraftserver == null) {
            return false;
        } else {
            CompoundTag nbttagcompound = BlockItem.getBlockEntityData(stack);

            if (nbttagcompound != null) {
                BlockEntity tileentity = world.getBlockEntity(pos);

                if (tileentity != null) {
                    if (!world.isClientSide && tileentity.onlyOpCanSetNbt() && (player == null || !(player.canUseGameMasterBlocks() || (player.getAbilities().instabuild && player.getBukkitEntity().hasPermission("minecraft.nbt.place"))))) { // Spigot - add permission
                        return false;
                    }

                    CompoundTag nbttagcompound1 = tileentity.saveWithoutMetadata();
                    CompoundTag nbttagcompound2 = nbttagcompound1.copy();

                    nbttagcompound1.merge(nbttagcompound);
                    if (!nbttagcompound1.equals(nbttagcompound2)) {
                        tileentity.load(nbttagcompound1);
                        tileentity.setChanged();
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    public String getDescriptionId() {
        return this.getBlock().getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, tooltip, context);
        this.getBlock().appendHoverText(stack, world, tooltip, context);
    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> map, Item item) {
        map.put(this.getBlock(), item);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return !(this.block instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        if (this.block instanceof ShulkerBoxBlock) {
            ItemStack itemstack = entity.getItem();
            CompoundTag nbttagcompound = BlockItem.getBlockEntityData(itemstack);

            if (nbttagcompound != null && nbttagcompound.contains("Items", 9)) {
                ListTag nbttaglist = nbttagcompound.getList("Items", 10);
                Stream<net.minecraft.nbt.Tag> stream = nbttaglist.stream(); // CraftBukkit - decompile error

                Objects.requireNonNull(CompoundTag.class);
                ItemUtils.onContainerDestroyed(entity, stream.map(CompoundTag.class::cast).map(ItemStack::of));
            }
        }

    }

    @Nullable
    public static CompoundTag getBlockEntityData(ItemStack stack) {
        return stack.getTagElement("BlockEntityTag");
    }

    public static void setBlockEntityData(ItemStack stack, BlockEntityType<?> blockEntityType, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.removeTagKey("BlockEntityTag");
        } else {
            BlockEntity.addEntityType(tag, blockEntityType);
            stack.addTagElement("BlockEntityTag", tag);
        }

    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.getBlock().requiredFeatures();
    }
}
