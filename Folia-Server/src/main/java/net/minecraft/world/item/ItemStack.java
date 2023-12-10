package net.minecraft.world.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.DigDurabilityEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

// CraftBukkit start
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public final class ItemStack {

    public static final Codec<ItemStack> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemStack::getItem), Codec.INT.fieldOf("Count").forGetter(ItemStack::getCount), CompoundTag.CODEC.optionalFieldOf("tag").forGetter((itemstack) -> {
            return Optional.ofNullable(itemstack.getTag());
        })).apply(instance, ItemStack::new);
    });
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Void) null);
    public static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = (DecimalFormat) Util.make(new DecimalFormat("#.##"), (decimalformat) -> {
        decimalformat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final String TAG_ENCH = "Enchantments";
    public static final String TAG_DISPLAY = "display";
    public static final String TAG_DISPLAY_NAME = "Name";
    public static final String TAG_LORE = "Lore";
    public static final String TAG_DAMAGE = "Damage";
    public static final String TAG_COLOR = "color";
    private static final String TAG_UNBREAKABLE = "Unbreakable";
    private static final String TAG_REPAIR_COST = "RepairCost";
    private static final String TAG_CAN_DESTROY_BLOCK_LIST = "CanDestroy";
    private static final String TAG_CAN_PLACE_ON_BLOCK_LIST = "CanPlaceOn";
    private static final String TAG_HIDE_FLAGS = "HideFlags";
    private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    private static final int DONT_HIDE_TOOLTIP = 0;
    private static final Style LORE_STYLE = Style.EMPTY.withColor(ChatFormatting.DARK_PURPLE).withItalic(true);
    private int count;
    private int popTime;
    /** @deprecated */
    @Deprecated
    @Nullable
    private Item item;
    @Nullable
    private CompoundTag tag;
    @Nullable
    private Entity entityRepresentation;
    @Nullable
    private AdventureModeCheck adventureBreakCheck;
    @Nullable
    private AdventureModeCheck adventurePlaceCheck;

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    // Paper start
    private static final java.util.Comparator<? super CompoundTag> enchantSorter = java.util.Comparator.comparing(o -> o.getString("id"));
    private void processEnchantOrder(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Enchantments", 9)) {
            return;
        }
        ListTag list = tag.getList("Enchantments", 10);
        if (list.size() < 2) {
            return;
        }
        try {
            //noinspection unchecked
            list.sort((java.util.Comparator<? super net.minecraft.nbt.Tag>) enchantSorter); // Paper
        } catch (Exception ignored) {}
    }

    private void processText() {
        CompoundTag display = getTagElement("display");
        if (display != null) {
            if (display.contains("Name", 8)) {
                String json = display.getString("Name");
                if (json != null && json.contains("\u00A7")) {
                    try {
                        display.put("Name", convert(json));
                    } catch (com.google.gson.JsonParseException jsonparseexception) {
                        display.remove("Name");
                    }
                }
            }
            if (display.contains("Lore", 9)) {
                ListTag list = display.getList("Lore", 8);
                for (int index = 0; index < list.size(); index++) {
                    String json = list.getString(index);
                    if (json != null && json.contains("\u00A7")) { // Only try if it has legacy in the unparsed json
                        try {
                            list.set(index, convert(json));
                        } catch (com.google.gson.JsonParseException e) {
                            list.set(index, net.minecraft.nbt.StringTag.valueOf(org.bukkit.craftbukkit.util.CraftChatMessage.toJSON(net.minecraft.network.chat.Component.literal(""))));
                        }
                    }
                }
            }
        }
    }

    private net.minecraft.nbt.StringTag convert(String json) {
        Component component = Component.Serializer.fromJson(json);
        if (component.getContents() instanceof net.minecraft.network.chat.contents.LiteralContents literalContents && literalContents.text().contains("\u00A7") && component.getSiblings().isEmpty()) {
            // Only convert if the root component is a single comp with legacy in it, don't convert already normal components
            component = org.bukkit.craftbukkit.util.CraftChatMessage.fromString(literalContents.text())[0];
        }
        return net.minecraft.nbt.StringTag.valueOf(org.bukkit.craftbukkit.util.CraftChatMessage.toJSON(component));
    }
    // Paper end

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(Holder<Item> entry) {
        this((ItemLike) entry.value(), 1);
    }

    private ItemStack(ItemLike item, int count, Optional<CompoundTag> nbt) {
        this(item, count);
        nbt.ifPresent(this::setTag);
    }

    public ItemStack(Holder<Item> itemEntry, int count) {
        this((ItemLike) itemEntry.value(), count);
    }

    public ItemStack(ItemLike item, int count) {
        this.item = item.asItem();
        this.count = count;
        if (this.item.canBeDepleted()) {
            this.setDamageValue(this.getDamageValue());
        }

    }

    private ItemStack(@Nullable Void ovoid) {
        this.item = null;
    }

    // Called to run this stack through the data converter to handle older storage methods and serialized items
    public void convertStack(int version) {
        if (0 < version && version < CraftMagicNumbers.INSTANCE.getDataVersion() && MinecraftServer.getServer() != null) { // Paper - skip conversion if the server doesn't exist (for tests)
            CompoundTag savedStack = new CompoundTag();
            this.save(savedStack);
            savedStack = (CompoundTag) MinecraftServer.getServer().fixerUpper.update(References.ITEM_STACK, new Dynamic(NbtOps.INSTANCE, savedStack), version, CraftMagicNumbers.INSTANCE.getDataVersion()).getValue();
            this.load(savedStack);
        }
    }

    // CraftBukkit - break into own method
    private void load(CompoundTag nbttagcompound) {
        this.item = (Item) BuiltInRegistries.ITEM.get(new ResourceLocation(nbttagcompound.getString("id")));
        this.count = nbttagcompound.getByte("Count");
        if (nbttagcompound.contains("tag", 10)) {
            // CraftBukkit start - make defensive copy as this data may be coming from the save thread
            this.tag = nbttagcompound.getCompound("tag").copy();
            // CraftBukkit end
            this.processEnchantOrder(this.tag); // Paper
            this.processText(); // Paper
            this.getItem().verifyTagAfterLoad(this.tag);
        }

        if (this.getItem().canBeDepleted()) {
            this.setDamageValue(this.getDamageValue());
        }

    }

    private ItemStack(CompoundTag nbt) {
        this.load(nbt);
        // CraftBukkit end
    }

    public static ItemStack of(CompoundTag nbt) {
        try {
            return new ItemStack(nbt);
        } catch (RuntimeException runtimeexception) {
            ItemStack.LOGGER.debug("Tried to load invalid item: {}", nbt, runtimeexception);
            return ItemStack.EMPTY;
        }
    }

    public boolean isEmpty() {
        return this == ItemStack.EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet enabledFeatures) {
        return this.isEmpty() || this.getItem().isEnabled(enabledFeatures);
    }

    public ItemStack split(int amount) {
        int j = Math.min(amount, this.getCount());
        ItemStack itemstack = this.copyWithCount(j);

        this.shrink(j);
        return itemstack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            this.setCount(0);
            return itemstack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tag) {
        return this.getItem().builtInRegistryHolder().is(tag);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> predicate) {
        return predicate.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> itemEntry) {
        return this.getItem().builtInRegistryHolder() == itemEntry;
    }

    public boolean is(HolderSet<Item> registryEntryList) {
        return registryEntryList.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext context) {
        net.minecraft.world.entity.player.Player entityhuman = context.getPlayer();
        BlockPos blockposition = context.getClickedPos();
        BlockInWorld shapedetectorblock = new BlockInWorld(context.getLevel(), blockposition, false);

        if (entityhuman != null && !entityhuman.getAbilities().mayBuild && !this.hasAdventureModePlaceTagForBlock(context.getLevel().registryAccess().registryOrThrow(Registries.BLOCK), shapedetectorblock)) {
            return InteractionResult.PASS;
        } else {
            Item item = this.getItem();
            // CraftBukkit start - handle all block place event logic here
            CompoundTag oldData = this.getTagClone();
            int oldCount = this.getCount();
            ServerLevel world = (ServerLevel) context.getLevel();
            io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading

            if (!(item instanceof BucketItem/* || item instanceof SolidBucketItem*/)) { // if not bucket // Paper - capture block states for snow buckets
                worldData.captureBlockStates = true; // Folia - region threading
                // special case bonemeal
                if (item == Items.BONE_MEAL) {
                    worldData.captureTreeGeneration = true; // Folia - region threading
                }
            }
            InteractionResult enuminteractionresult;
            try {
                enuminteractionresult = item.useOn(context);
            } finally {
                worldData.captureBlockStates = false; // Folia - region threading
            }
            CompoundTag newData = this.getTagClone();
            int newCount = this.getCount();
            this.setCount(oldCount);
            this.setTagClone(oldData);
            if (enuminteractionresult.consumesAction() && worldData.captureTreeGeneration && worldData.capturedBlockStates.size() > 0) { // Folia - region threading
                world.getCurrentWorldData().captureTreeGeneration = false; // Folia - region threading
                Location location = CraftLocation.toBukkit(blockposition, world.getWorld());
                TreeType treeType = SaplingBlock.treeTypeRT.get(); // Folia - region threading
                SaplingBlock.treeTypeRT.set(null); // Folia - region threading
                List<CraftBlockState> blocks = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                worldData.capturedBlockStates.clear(); // Folia - region threading
                StructureGrowEvent structureEvent = null;
                if (treeType != null) {
                    boolean isBonemeal = this.getItem() == Items.BONE_MEAL;
                    structureEvent = new StructureGrowEvent(location, treeType, isBonemeal, (Player) entityhuman.getBukkitEntity(), (List< BlockState>) (List<? extends BlockState>) blocks);
                    org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                }

                BlockFertilizeEvent fertilizeEvent = new BlockFertilizeEvent(CraftBlock.at(world, blockposition), (Player) entityhuman.getBukkitEntity(), (List< BlockState>) (List<? extends BlockState>) blocks);
                fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                if (!fertilizeEvent.isCancelled()) {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.tag, oldData)) {
                        this.setTag(newData);
                        this.setCount(newCount);
                    }
                    for (CraftBlockState blockstate : blocks) {
                        world.setBlock(blockstate.getPosition(),blockstate.getHandle(), blockstate.getFlag()); // SPIGOT-7248 - manual update to avoid physics where appropriate
                        world.checkCapturedTreeStateForObserverNotify(blockposition, blockstate); // Paper - notify observers even if grow failed
                        if (blockstate instanceof org.bukkit.craftbukkit.block.CapturedBlockState capturedBlockState) capturedBlockState.checkTreeBlockHack(); // Paper
                    }
                    entityhuman.awardStat(Stats.ITEM_USED.get(item)); // SPIGOT-7236 - award stat
                }

                SignItem.openSign = null; // SPIGOT-6758 - Reset on early return
                return enuminteractionresult;
            }
            worldData.captureTreeGeneration = false; // Folia - region threading

            if (entityhuman != null && enuminteractionresult.shouldAwardStats()) {
                InteractionHand enumhand = context.getHand();
                org.bukkit.event.block.BlockPlaceEvent placeEvent = null;
                List<BlockState> blocks = new java.util.ArrayList<>(worldData.capturedBlockStates.values()); // Folia - region threading
                worldData.capturedBlockStates.clear(); // Folia - region threading
                if (blocks.size() > 1) {
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockMultiPlaceEvent(world, entityhuman, enumhand, blocks, blockposition.getX(), blockposition.getY(), blockposition.getZ());
                } else if (blocks.size() == 1 && item != Items.POWDER_SNOW_BUCKET) { // Paper - don't call event twice for snow buckets
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(world, entityhuman, enumhand, blocks.get(0), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                }

                if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                    enuminteractionresult = InteractionResult.FAIL; // cancel placement
                    // PAIL: Remove this when MC-99075 fixed
                    placeEvent.getPlayer().updateInventory();
                    worldData.capturedTileEntities.clear(); // Paper - clear out tile entities as chests and such will pop loot // Folia - region threading
                    // revert back all captured blocks
                    worldData.preventPoiUpdated = true; // CraftBukkit - SPIGOT-5710 // Folia - region threading
                    for (BlockState blockstate : blocks) {
                        blockstate.update(true, false);
                    }
                    worldData.preventPoiUpdated = false; // Folia - region threading

                    // Brute force all possible updates
                    BlockPos placedPos = ((CraftBlock) placeEvent.getBlock()).getPosition();
                    for (Direction dir : Direction.values()) {
                        ((ServerPlayer) entityhuman).connection.send(new ClientboundBlockUpdatePacket(world, placedPos.relative(dir)));
                    }
                    SignItem.openSign = null; // SPIGOT-6758 - Reset on early return
                } else {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.tag, oldData)) {
                        this.setTag(newData);
                        this.setCount(newCount);
                    }

                    for (Map.Entry<BlockPos, BlockEntity> e : worldData.capturedTileEntities.entrySet()) { // Folia - region threading
                        world.setBlockEntity(e.getValue());
                    }

                    for (BlockState blockstate : blocks) {
                        int updateFlag = ((CraftBlockState) blockstate).getFlag();
                        net.minecraft.world.level.block.state.BlockState oldBlock = ((CraftBlockState) blockstate).getHandle();
                        BlockPos newblockposition = ((CraftBlockState) blockstate).getPosition();
                        net.minecraft.world.level.block.state.BlockState block = world.getBlockState(newblockposition);

                        if (!(block.getBlock() instanceof BaseEntityBlock)) { // Containers get placed automatically
                            block.getBlock().onPlace(block, world, newblockposition, oldBlock, true, context); // Paper - pass context
                        }

                        world.notifyAndUpdatePhysics(newblockposition, null, oldBlock, block, world.getBlockState(newblockposition), updateFlag, 512); // send null chunk as chunk.k() returns false by this point
                    }

                    // Special case juke boxes as they update their tile entity. Copied from ItemRecord.
                    // PAIL: checkme on updates.
                    if (this.item instanceof RecordItem) {
                        BlockEntity tileentity = world.getBlockEntity(blockposition);

                        if (tileentity instanceof JukeboxBlockEntity) {
                            JukeboxBlockEntity tileentityjukebox = (JukeboxBlockEntity) tileentity;

                            tileentityjukebox.setFirstItem(this.copy()); // Paper - sync this with record item, jukebox has now an inventory
                            world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entityhuman, world.getBlockState(blockposition)));
                        }

                        this.shrink(1);
                        entityhuman.awardStat(Stats.PLAY_RECORD);
                    }

                    if (this.item == Items.WITHER_SKELETON_SKULL) { // Special case skulls to allow wither spawns to be cancelled
                        BlockPos bp = blockposition;
                        if (!world.getBlockState(blockposition).canBeReplaced()) {
                            if (!world.getBlockState(blockposition).isSolid()) {
                                bp = null;
                            } else {
                                bp = bp.relative(context.getClickedFace());
                            }
                        }
                        if (bp != null) {
                            BlockEntity te = world.getBlockEntity(bp);
                            if (te instanceof SkullBlockEntity) {
                                WitherSkullBlock.checkSpawn(world, bp, (SkullBlockEntity) te);
                            }
                        }
                    }

                    // SPIGOT-4678
                    if (this.item instanceof SignItem && SignItem.openSign != null) {
                        try {
                            if (world.getBlockEntity(SignItem.openSign) instanceof SignBlockEntity tileentitysign) {
                                if (world.getBlockState(SignItem.openSign).getBlock() instanceof SignBlock blocksign) {
                                    blocksign.openTextEdit(entityhuman, tileentitysign, true, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.PLACE); // Paper
                                }
                            }
                        } finally {
                            SignItem.openSign = null;
                        }
                    }

                    // SPIGOT-7315: Moved from BlockBed#setPlacedBy
                    if (placeEvent != null && this.item instanceof BedItem) {
                        BlockPos position = ((CraftBlock) placeEvent.getBlock()).getPosition();
                        net.minecraft.world.level.block.state.BlockState blockData =  world.getBlockState(position);

                        if (blockData.getBlock() instanceof BedBlock) {
                            world.blockUpdated(position, Blocks.AIR);
                            blockData.updateNeighbourShapes(world, position, 3);
                        }
                    }

                    // SPIGOT-1288 - play sound stripped from ItemBlock
                    if (this.item instanceof BlockItem) {
                        // Paper start
                        BlockPos position = new net.minecraft.world.item.context.BlockPlaceContext(context).getClickedPos();
                        net.minecraft.world.level.block.state.BlockState blockData = world.getBlockState(position);
                        SoundType soundeffecttype = blockData.getSoundType();
                        // Paper end
                        world.playSound(entityhuman, blockposition, soundeffecttype.getPlaceSound(), SoundSource.BLOCKS, (soundeffecttype.getVolume() + 1.0F) / 2.0F, soundeffecttype.getPitch() * 0.8F);
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(item));
                }
            }
            worldData.capturedTileEntities.clear(); // Folia - region threading
            worldData.capturedBlockStates.clear(); // Folia - region threading
            // CraftBukkit end

            return enuminteractionresult;
        }
    }

    public float getDestroySpeed(net.minecraft.world.level.block.state.BlockState state) {
        return this.getItem().getDestroySpeed(this, state);
    }

    public InteractionResultHolder<ItemStack> use(Level world, net.minecraft.world.entity.player.Player user, InteractionHand hand) {
        return this.getItem().use(world, user, hand);
    }

    public ItemStack finishUsingItem(Level world, LivingEntity user) {
        return this.getItem().finishUsingItem(this, world, user);
    }

    public CompoundTag save(CompoundTag nbt) {
        ResourceLocation minecraftkey = BuiltInRegistries.ITEM.getKey(this.getItem());

        nbt.putString("id", minecraftkey == null ? "minecraft:air" : minecraftkey.toString());
        nbt.putByte("Count", (byte) this.count);
        if (this.tag != null) {
            nbt.put("tag", this.tag.copy());
        }

        return nbt;
    }

    public int getMaxStackSize() {
        return this.getItem().getMaxStackSize();
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        if (!this.isEmpty() && this.getItem().getMaxDamage() > 0) {
            CompoundTag nbttagcompound = this.getTag();

            return nbttagcompound == null || !nbttagcompound.getBoolean("Unbreakable");
        } else {
            return false;
        }
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return this.tag == null ? 0 : this.tag.getInt("Damage");
    }

    public void setDamageValue(int damage) {
        this.getOrCreateTag().putInt("Damage", Math.max(0, damage));
    }

    public int getMaxDamage() {
        return this.getItem().getMaxDamage();
    }

    public boolean hurt(int amount, RandomSource random, @Nullable LivingEntity player) { // Paper - allow any living entity instead of only ServerPlayers
        if (!this.isDamageableItem()) {
            return false;
        } else {
            int j;

            if (amount > 0) {
                j = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, this);
                int k = 0;

                for (int l = 0; j > 0 && l < amount; ++l) {
                    if (DigDurabilityEnchantment.shouldIgnoreDurabilityDrop(this, j, random)) {
                        ++k;
                    }
                }

                int originalDamage = amount; // Paper
                amount -= k;
                // CraftBukkit start
                if (player instanceof ServerPlayer serverPlayer) { // Paper
                    PlayerItemDamageEvent event = new PlayerItemDamageEvent(serverPlayer.getBukkitEntity(), CraftItemStack.asCraftMirror(this), amount, originalDamage); // Paper
                    event.getPlayer().getServer().getPluginManager().callEvent(event);

                    if (amount != event.getDamage() || event.isCancelled()) {
                        event.getPlayer().updateInventory();
                    }
                    if (event.isCancelled()) {
                        return false;
                    }

                    amount = event.getDamage();
                    // Paper start - EntityDamageItemEvent
                } else if (player != null) {
                    io.papermc.paper.event.entity.EntityDamageItemEvent event = new io.papermc.paper.event.entity.EntityDamageItemEvent(player.getBukkitLivingEntity(), CraftItemStack.asCraftMirror(this), amount);
                    if (!event.callEvent()) {
                        return false;
                    }
                    amount = event.getDamage();
                    // Paper end
                }
                // CraftBukkit end
                if (amount <= 0) {
                    return false;
                }
            }

            if (player instanceof ServerPlayer serverPlayer && amount != 0) { // Paper
                CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, this, this.getDamageValue() + amount); // Paper
            }

            j = this.getDamageValue() + amount;
            this.setDamageValue(j);
            return j >= this.getMaxDamage();
        }
    }

    public <T extends LivingEntity> void hurtAndBreak(int amount, T entity, Consumer<T> breakCallback) {
        if (!entity.level().isClientSide && (!(entity instanceof net.minecraft.world.entity.player.Player) || !((net.minecraft.world.entity.player.Player) entity).getAbilities().instabuild)) {
            if (this.isDamageableItem()) {
                if (this.hurt(amount, entity.getRandom(), entity /*instanceof ServerPlayer ? (ServerPlayer) entity : null*/)) { // Paper - pass LivingEntity for EntityItemDamageEvent
                    breakCallback.accept(entity);
                    Item item = this.getItem();
                    // CraftBukkit start - Check for item breaking
                    if (this.count == 1 && entity instanceof net.minecraft.world.entity.player.Player) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerItemBreakEvent((net.minecraft.world.entity.player.Player) entity, this);
                    }
                    // CraftBukkit end

                    this.shrink(1);
                    if (entity instanceof net.minecraft.world.entity.player.Player) {
                        ((net.minecraft.world.entity.player.Player) entity).awardStat(Stats.ITEM_BROKEN.get(item));
                    }

                    this.setDamageValue(0);
                }

            }
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction clickType, net.minecraft.world.entity.player.Player player) {
        return this.getItem().overrideStackedOnOther(this, slot, clickType, player);
    }

    public boolean overrideOtherStackedOnMe(ItemStack stack, Slot slot, ClickAction clickType, net.minecraft.world.entity.player.Player player, SlotAccess cursorStackReference) {
        return this.getItem().overrideOtherStackedOnMe(this, stack, slot, clickType, player, cursorStackReference);
    }

    public void hurtEnemy(LivingEntity target, net.minecraft.world.entity.player.Player attacker) {
        Item item = this.getItem();

        if (item.hurtEnemy(this, target, attacker)) {
            attacker.awardStat(Stats.ITEM_USED.get(item));
        }

    }

    public void mineBlock(Level world, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, net.minecraft.world.entity.player.Player miner) {
        Item item = this.getItem();

        if (item.mineBlock(this, world, state, pos, miner)) {
            miner.awardStat(Stats.ITEM_USED.get(item));
        }

    }

    public boolean isCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState state) {
        return this.getItem().isCorrectToolForDrops(state);
    }

    public InteractionResult interactLivingEntity(net.minecraft.world.entity.player.Player user, LivingEntity entity, InteractionHand hand) {
        return this.getItem().interactLivingEntity(this, user, entity, hand);
    }

    public ItemStack copy() {
        // Paper start
        return this.copy(false);
    }

    public ItemStack copy(boolean originalItem) {
        if (!originalItem && this.isEmpty()) {
            // Paper end
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(originalItem ? this.item : this.getItem(), this.count); // Paper

            itemstack.setPopTime(this.getPopTime());
            if (this.tag != null) {
                itemstack.tag = this.tag.copy();
            }

            return itemstack;
        }
    }

    public ItemStack copyWithCount(int count) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            itemstack.setCount(count);
            return itemstack;
        }
    }

    public static boolean matches(ItemStack left, ItemStack right) {
        return left == right ? true : (left.getCount() != right.getCount() ? false : ItemStack.isSameItemSameTags(left, right));
    }

    public static boolean isSameItem(ItemStack left, ItemStack right) {
        return left.is(right.getItem());
    }

    public static boolean isSameItemSameTags(ItemStack stack, ItemStack otherStack) {
        return !stack.is(otherStack.getItem()) ? false : (stack.isEmpty() && otherStack.isEmpty() ? true : Objects.equals(stack.tag, otherStack.tag));
    }

    public String getDescriptionId() {
        return this.getItem().getDescriptionId(this);
    }

    public String toString() {
        int i = this.getCount();

        return i + " " + this.getItem();
    }

    public void inventoryTick(Level world, Entity entity, int slot, boolean selected) {
        if (this.popTime > 0) {
            --this.popTime;
        }

        if (this.getItem() != null) {
            this.getItem().inventoryTick(this, world, entity, slot, selected);
        }

    }

    public void onCraftedBy(Level world, net.minecraft.world.entity.player.Player player, int amount) {
        player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), amount);
        this.getItem().onCraftedBy(this, world, player);
    }

    public int getUseDuration() {
        return this.getItem().getUseDuration(this);
    }

    public UseAnim getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(Level world, LivingEntity user, int remainingUseTicks) {
        this.getItem().releaseUsing(this, world, user, remainingUseTicks);
    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    public boolean hasTag() {
        return !this.isEmpty() && this.tag != null && !this.tag.isEmpty();
    }

    @Nullable
    public CompoundTag getTag() {
        return this.tag;
    }

    // CraftBukkit start
    @Nullable
    private CompoundTag getTagClone() {
        return this.tag == null ? null : this.tag.copy();
    }

    private void setTagClone(@Nullable CompoundTag nbtttagcompound) {
        this.setTag(nbtttagcompound == null ? null : nbtttagcompound.copy());
    }
    // CraftBukkit end

    public CompoundTag getOrCreateTag() {
        if (this.tag == null) {
            this.setTag(new CompoundTag());
        }

        return this.tag;
    }

    public CompoundTag getOrCreateTagElement(String key) {
        if (this.tag != null && this.tag.contains(key, 10)) {
            return this.tag.getCompound(key);
        } else {
            CompoundTag nbttagcompound = new CompoundTag();

            this.addTagElement(key, nbttagcompound);
            return nbttagcompound;
        }
    }

    @Nullable
    public CompoundTag getTagElement(String key) {
        return this.tag != null && this.tag.contains(key, 10) ? this.tag.getCompound(key) : null;
    }

    public void removeTagKey(String key) {
        if (this.tag != null && this.tag.contains(key)) {
            this.tag.remove(key);
            if (this.tag.isEmpty()) {
                this.tag = null;
            }
        }

    }

    public ListTag getEnchantmentTags() {
        return this.tag != null ? this.tag.getList("Enchantments", 10) : new ListTag();
    }

    // Paper start - (this is just a good no conflict location)
    public org.bukkit.inventory.ItemStack asBukkitMirror() {
        return CraftItemStack.asCraftMirror(this);
    }
    public org.bukkit.inventory.ItemStack asBukkitCopy() {
        return CraftItemStack.asCraftMirror(this.copy());
    }
    public static ItemStack fromBukkitCopy(org.bukkit.inventory.ItemStack itemstack) {
        return CraftItemStack.asNMSCopy(itemstack);
    }
    private org.bukkit.craftbukkit.inventory.CraftItemStack bukkitStack;
    public org.bukkit.inventory.ItemStack getBukkitStack() {
        if (bukkitStack == null || bukkitStack.handle != this) {
            bukkitStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this);
        }
        return bukkitStack;
    }
    // Paper end

    public void setTag(@Nullable CompoundTag nbt) {
        this.tag = nbt;
        this.processEnchantOrder(this.tag); // Paper
        if (this.getItem().canBeDepleted()) {
            this.setDamageValue(this.getDamageValue());
        }

        if (nbt != null) {
            this.getItem().verifyTagAfterLoad(nbt);
        }

    }

    public Component getHoverName() {
        CompoundTag nbttagcompound = this.getTagElement("display");

        if (nbttagcompound != null && nbttagcompound.contains("Name", 8)) {
            try {
                MutableComponent ichatmutablecomponent = Component.Serializer.fromJson(nbttagcompound.getString("Name"));

                if (ichatmutablecomponent != null) {
                    return ichatmutablecomponent;
                }

                nbttagcompound.remove("Name");
            } catch (Exception exception) {
                nbttagcompound.remove("Name");
            }
        }

        return this.getItem().getName(this);
    }

    public ItemStack setHoverName(@Nullable Component name) {
        CompoundTag nbttagcompound = this.getOrCreateTagElement("display");

        if (name != null) {
            nbttagcompound.putString("Name", Component.Serializer.toJson(name));
        } else {
            nbttagcompound.remove("Name");
        }

        return this;
    }

    public void resetHoverName() {
        CompoundTag nbttagcompound = this.getTagElement("display");

        if (nbttagcompound != null) {
            nbttagcompound.remove("Name");
            if (nbttagcompound.isEmpty()) {
                this.removeTagKey("display");
            }
        }

        if (this.tag != null && this.tag.isEmpty()) {
            this.tag = null;
        }

    }

    public boolean hasCustomHoverName() {
        CompoundTag nbttagcompound = this.getTagElement("display");

        return nbttagcompound != null && nbttagcompound.contains("Name", 8);
    }

    public List<Component> getTooltipLines(@Nullable net.minecraft.world.entity.player.Player player, TooltipFlag context) {
        List<Component> list = Lists.newArrayList();
        MutableComponent ichatmutablecomponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().color);

        if (this.hasCustomHoverName()) {
            ichatmutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        list.add(ichatmutablecomponent);
        if (!context.isAdvanced() && !this.hasCustomHoverName() && this.is(Items.FILLED_MAP)) {
            Integer integer = MapItem.getMapId(this);

            if (integer != null) {
                list.add(MapItem.getTooltipForId(this));
            }
        }

        int i = this.getHideFlags();

        if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.ADDITIONAL)) {
            this.getItem().appendHoverText(this, player == null ? null : player.level(), list, context);
        }

        int j;

        if (this.hasTag()) {
            if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.UPGRADES) && player != null) {
                ArmorTrim.appendUpgradeHoverText(this, player.level().registryAccess(), list);
            }

            if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.ENCHANTMENTS)) {
                ItemStack.appendEnchantmentNames(list, this.getEnchantmentTags());
            }

            if (this.tag.contains("display", 10)) {
                CompoundTag nbttagcompound = this.tag.getCompound("display");

                if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.DYE) && nbttagcompound.contains("color", 99)) {
                    if (context.isAdvanced()) {
                        list.add(Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", nbttagcompound.getInt("color"))).withStyle(ChatFormatting.GRAY));
                    } else {
                        list.add(Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    }
                }

                if (nbttagcompound.getTagType("Lore") == 9) {
                    ListTag nbttaglist = nbttagcompound.getList("Lore", 8);

                    for (j = 0; j < nbttaglist.size(); ++j) {
                        String s = nbttaglist.getString(j);

                        try {
                            MutableComponent ichatmutablecomponent1 = Component.Serializer.fromJson(s);

                            if (ichatmutablecomponent1 != null) {
                                list.add(ComponentUtils.mergeStyles(ichatmutablecomponent1, ItemStack.LORE_STYLE));
                            }
                        } catch (Exception exception) {
                            nbttagcompound.remove("Lore");
                        }
                    }
                }
            }
        }

        int k;

        if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.MODIFIERS)) {
            EquipmentSlot[] aenumitemslot = EquipmentSlot.values();

            k = aenumitemslot.length;

            for (j = 0; j < k; ++j) {
                EquipmentSlot enumitemslot = aenumitemslot[j];
                Multimap<Attribute, AttributeModifier> multimap = this.getAttributeModifiers(enumitemslot);

                if (!multimap.isEmpty()) {
                    list.add(CommonComponents.EMPTY);
                    list.add(Component.translatable("item.modifiers." + enumitemslot.getName()).withStyle(ChatFormatting.GRAY));
                    Iterator iterator = multimap.entries().iterator();

                    while (iterator.hasNext()) {
                        Entry<Attribute, AttributeModifier> entry = (Entry) iterator.next();
                        AttributeModifier attributemodifier = (AttributeModifier) entry.getValue();
                        double d0 = attributemodifier.getAmount();
                        boolean flag = false;

                        if (player != null) {
                            if (attributemodifier.getId() == Item.BASE_ATTACK_DAMAGE_UUID) {
                                d0 += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                                d0 += (double) EnchantmentHelper.getDamageBonus(this, MobType.UNDEFINED);
                                flag = true;
                            } else if (attributemodifier.getId() == Item.BASE_ATTACK_SPEED_UUID) {
                                d0 += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                                flag = true;
                            }
                        }

                        double d1;

                        if (attributemodifier.getOperation() != AttributeModifier.Operation.MULTIPLY_BASE && attributemodifier.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL) {
                            if (((Attribute) entry.getKey()).equals(Attributes.KNOCKBACK_RESISTANCE)) {
                                d1 = d0 * 10.0D;
                            } else {
                                d1 = d0;
                            }
                        } else {
                            d1 = d0 * 100.0D;
                        }

                        if (flag) {
                            list.add(CommonComponents.space().append((Component) Component.translatable("attribute.modifier.equals." + attributemodifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) entry.getKey()).getDescriptionId()))).withStyle(ChatFormatting.DARK_GREEN));
                        } else if (d0 > 0.0D) {
                            list.add(Component.translatable("attribute.modifier.plus." + attributemodifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) entry.getKey()).getDescriptionId())).withStyle(ChatFormatting.BLUE));
                        } else if (d0 < 0.0D) {
                            d1 *= -1.0D;
                            list.add(Component.translatable("attribute.modifier.take." + attributemodifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) entry.getKey()).getDescriptionId())).withStyle(ChatFormatting.RED));
                        }
                    }
                }
            }
        }

        if (this.hasTag()) {
            if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.UNBREAKABLE) && this.tag.getBoolean("Unbreakable")) {
                list.add(Component.translatable("item.unbreakable").withStyle(ChatFormatting.BLUE));
            }

            ListTag nbttaglist1;

            if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.CAN_DESTROY) && this.tag.contains("CanDestroy", 9)) {
                nbttaglist1 = this.tag.getList("CanDestroy", 8);
                if (!nbttaglist1.isEmpty()) {
                    list.add(CommonComponents.EMPTY);
                    list.add(Component.translatable("item.canBreak").withStyle(ChatFormatting.GRAY));

                    for (k = 0; k < nbttaglist1.size(); ++k) {
                        list.addAll(ItemStack.expandBlockState(nbttaglist1.getString(k)));
                    }
                }
            }

            if (ItemStack.shouldShowInTooltip(i, ItemStack.TooltipPart.CAN_PLACE) && this.tag.contains("CanPlaceOn", 9)) {
                nbttaglist1 = this.tag.getList("CanPlaceOn", 8);
                if (!nbttaglist1.isEmpty()) {
                    list.add(CommonComponents.EMPTY);
                    list.add(Component.translatable("item.canPlace").withStyle(ChatFormatting.GRAY));

                    for (k = 0; k < nbttaglist1.size(); ++k) {
                        list.addAll(ItemStack.expandBlockState(nbttaglist1.getString(k)));
                    }
                }
            }
        }

        if (context.isAdvanced()) {
            if (this.isDamaged()) {
                list.add(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
            }

            list.add(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            if (this.hasTag()) {
                list.add(Component.translatable("item.nbt_tags", this.tag.getAllKeys().size()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
            list.add(ItemStack.DISABLED_ITEM_TOOLTIP);
        }

        return list;
    }

    private static boolean shouldShowInTooltip(int flags, ItemStack.TooltipPart tooltipSection) {
        return (flags & tooltipSection.getMask()) == 0;
    }

    private int getHideFlags() {
        return this.hasTag() && this.tag.contains("HideFlags", 99) ? this.tag.getInt("HideFlags") : 0;
    }

    public void hideTooltipPart(ItemStack.TooltipPart tooltipSection) {
        CompoundTag nbttagcompound = this.getOrCreateTag();

        nbttagcompound.putInt("HideFlags", nbttagcompound.getInt("HideFlags") | tooltipSection.getMask());
    }

    public static void appendEnchantmentNames(List<Component> tooltip, ListTag enchantments) {
        for (int i = 0; i < enchantments.size(); ++i) {
            CompoundTag nbttagcompound = enchantments.getCompound(i);

            BuiltInRegistries.ENCHANTMENT.getOptional(EnchantmentHelper.getEnchantmentId(nbttagcompound)).ifPresent((enchantment) -> {
                tooltip.add(enchantment.getFullname(EnchantmentHelper.getEnchantmentLevel(nbttagcompound)));
            });
        }

    }

    private static Collection<Component> expandBlockState(String tag) {
        try {
            return (Collection) BlockStateParser.parseForTesting(BuiltInRegistries.BLOCK.asLookup(), tag, true).map((argumentblock_a) -> {
                return Lists.newArrayList(new Component[]{argumentblock_a.blockState().getBlock().getName().withStyle(ChatFormatting.DARK_GRAY)});
            }, (argumentblock_b) -> {
                return (List) argumentblock_b.tag().stream().map((holder) -> {
                    return ((Block) holder.value()).getName().withStyle(ChatFormatting.DARK_GRAY);
                }).collect(Collectors.toList());
            });
        } catch (CommandSyntaxException commandsyntaxexception) {
            return Lists.newArrayList(new Component[]{Component.literal("missingno").withStyle(ChatFormatting.DARK_GRAY)});
        }
    }

    public boolean hasFoil() {
        return this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        return this.getItem().getRarity(this);
    }

    public boolean isEnchantable() {
        return !this.getItem().isEnchantable(this) ? false : !this.isEnchanted();
    }

    public void enchant(Enchantment enchantment, int level) {
        this.getOrCreateTag();
        if (!this.tag.contains("Enchantments", 9)) {
            this.tag.put("Enchantments", new ListTag());
        }

        ListTag nbttaglist = this.tag.getList("Enchantments", 10);

        nbttaglist.add(EnchantmentHelper.storeEnchantment(EnchantmentHelper.getEnchantmentId(enchantment), (byte) level));
        processEnchantOrder(this.tag); // Paper
    }

    public boolean isEnchanted() {
        return this.tag != null && this.tag.contains("Enchantments", 9) ? !this.tag.getList("Enchantments", 10).isEmpty() : false;
    }

    public void addTagElement(String key, Tag element) {
        this.getOrCreateTag().put(key, element);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity holder) {
        this.entityRepresentation = holder;
    }

    @Nullable
    public ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame) this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public int getBaseRepairCost() {
        return this.hasTag() && this.tag.contains("RepairCost", 3) ? this.tag.getInt("RepairCost") : 0;
    }

    public void setRepairCost(int repairCost) {
        if (repairCost > 0) {
            this.getOrCreateTag().putInt("RepairCost", repairCost);
        } else {
            this.removeTagKey("RepairCost");
        }

    }

    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        Object object;

        if (this.hasTag() && this.tag.contains("AttributeModifiers", 9)) {
            object = HashMultimap.create();
            ListTag nbttaglist = this.tag.getList("AttributeModifiers", 10);

            for (int i = 0; i < nbttaglist.size(); ++i) {
                CompoundTag nbttagcompound = nbttaglist.getCompound(i);

                if (!nbttagcompound.contains("Slot", 8) || nbttagcompound.getString("Slot").equals(slot.getName())) {
                    Optional<Attribute> optional = BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.tryParse(nbttagcompound.getString("AttributeName")));

                    if (!optional.isEmpty()) {
                        AttributeModifier attributemodifier = AttributeModifier.load(nbttagcompound);

                        if (attributemodifier != null && attributemodifier.getId().getLeastSignificantBits() != 0L && attributemodifier.getId().getMostSignificantBits() != 0L) {
                            ((Multimap) object).put((Attribute) optional.get(), attributemodifier);
                        }
                    }
                }
            }
        } else {
            object = this.getItem().getDefaultAttributeModifiers(slot);
        }

        return (Multimap) object;
    }

    public void addAttributeModifier(Attribute attribute, AttributeModifier modifier, @Nullable EquipmentSlot slot) {
        this.getOrCreateTag();
        if (!this.tag.contains("AttributeModifiers", 9)) {
            this.tag.put("AttributeModifiers", new ListTag());
        }

        ListTag nbttaglist = this.tag.getList("AttributeModifiers", 10);
        CompoundTag nbttagcompound = modifier.save();

        nbttagcompound.putString("AttributeName", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
        if (slot != null) {
            nbttagcompound.putString("Slot", slot.getName());
        }

        nbttaglist.add(nbttagcompound);
    }

    // CraftBukkit start
    @Deprecated
    public void setItem(Item item) {
        this.bukkitStack = null; // Paper
        this.item = item;
    }
    // CraftBukkit end

    public Component getDisplayName() {
        MutableComponent ichatmutablecomponent = Component.empty().append(this.getHoverName());

        if (this.hasCustomHoverName()) {
            ichatmutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent ichatmutablecomponent1 = ComponentUtils.wrapInSquareBrackets(ichatmutablecomponent);

        if (!this.isEmpty()) {
            ichatmutablecomponent1.withStyle(this.getRarity().color).withStyle((chatmodifier) -> {
                return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(this)));
            });
        }

        return ichatmutablecomponent1;
    }

    public boolean hasAdventureModePlaceTagForBlock(Registry<Block> blockRegistry, BlockInWorld pos) {
        if (this.adventurePlaceCheck == null) {
            this.adventurePlaceCheck = new AdventureModeCheck("CanPlaceOn");
        }

        return this.adventurePlaceCheck.test(this, blockRegistry, pos);
    }

    public boolean hasAdventureModeBreakTagForBlock(Registry<Block> blockRegistry, BlockInWorld pos) {
        if (this.adventureBreakCheck == null) {
            this.adventureBreakCheck = new AdventureModeCheck("CanDestroy");
        }

        return this.adventureBreakCheck.test(this, blockRegistry, pos);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int bobbingAnimationTime) {
        this.popTime = bobbingAnimationTime;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void grow(int amount) {
        this.setCount(this.getCount() + amount);
    }

    public void shrink(int amount) {
        this.grow(-amount);
    }

    public void onUseTick(Level world, LivingEntity user, int remainingUseTicks) {
        this.getItem().onUseTick(world, user, this, remainingUseTicks);
    }

    public void onDestroyed(ItemEntity entity) {
        this.getItem().onDestroyed(entity);
    }

    public boolean isEdible() {
        return this.getItem().isEdible();
    }

    public SoundEvent getDrinkingSound() {
        return this.getItem().getDrinkingSound();
    }

    public SoundEvent getEatingSound() {
        return this.getItem().getEatingSound();
    }

    public static enum TooltipPart {

        ENCHANTMENTS, MODIFIERS, UNBREAKABLE, CAN_DESTROY, CAN_PLACE, ADDITIONAL, DYE, UPGRADES;

        private final int mask = 1 << this.ordinal();

        private TooltipPart() {}

        public int getMask() {
            return this.mask;
        }
    }
}
