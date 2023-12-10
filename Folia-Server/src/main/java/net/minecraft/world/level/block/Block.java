package net.minecraft.world.level.block;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public class Block extends BlockBehaviour implements ItemLike {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Holder.Reference<Block> builtInRegistryHolder;
    public static final IdMapper<BlockState> BLOCK_STATE_REGISTRY = new IdMapper<>();
    private static final LoadingCache<VoxelShape, Boolean> SHAPE_FULL_BLOCK_CACHE = CacheBuilder.newBuilder().maximumSize(512L).weakKeys().build(new CacheLoader<VoxelShape, Boolean>() {
        public Boolean load(VoxelShape voxelshape) {
            return !Shapes.joinIsNotEmpty(Shapes.block(), voxelshape, BooleanOp.NOT_SAME);
        }
    });
    public static final int UPDATE_NEIGHBORS = 1;
    public static final int UPDATE_CLIENTS = 2;
    public static final int UPDATE_INVISIBLE = 4;
    public static final int UPDATE_IMMEDIATE = 8;
    public static final int UPDATE_KNOWN_SHAPE = 16;
    public static final int UPDATE_SUPPRESS_DROPS = 32;
    public static final int UPDATE_MOVE_BY_PISTON = 64;
    public static final int UPDATE_NONE = 4;
    public static final int UPDATE_ALL = 3;
    public static final int UPDATE_ALL_IMMEDIATE = 11;
    public static final float INDESTRUCTIBLE = -1.0F;
    public static final float INSTANT = 0.0F;
    public static final int UPDATE_LIMIT = 512;
    protected final StateDefinition<Block, BlockState> stateDefinition;
    private BlockState defaultBlockState;
    // Paper start
    public final boolean isDestroyable() {
        return io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits ||
            this != Blocks.BEDROCK &&
            this != Blocks.END_PORTAL_FRAME &&
            this != Blocks.END_PORTAL &&
            this != Blocks.END_GATEWAY &&
            this != Blocks.COMMAND_BLOCK &&
            this != Blocks.REPEATING_COMMAND_BLOCK &&
            this != Blocks.CHAIN_COMMAND_BLOCK &&
            this != Blocks.BARRIER &&
            this != Blocks.STRUCTURE_BLOCK &&
            this != Blocks.JIGSAW;
    }
    public co.aikar.timings.Timing timing;
    public co.aikar.timings.Timing getTiming() {
        if (timing == null) {
            timing = co.aikar.timings.MinecraftTimings.getBlockTiming(this);
        }
        return timing;
    }
    // Paper end
    @Nullable
    private String descriptionId;
    @Nullable
    private Item item;
    private static final int CACHE_SIZE = 2048;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(2048, 0.25F) {
            protected void rehash(int i) {}
        };

        object2bytelinkedopenhashmap.defaultReturnValue((byte) 127);
        return object2bytelinkedopenhashmap;
    });

    public static int getId(@Nullable BlockState state) {
        if (state == null) {
            return 0;
        } else {
            int i = Block.BLOCK_STATE_REGISTRY.getId(state);

            return i == -1 ? 0 : i;
        }
    }

    public static BlockState stateById(int stateId) {
        BlockState iblockdata = (BlockState) Block.BLOCK_STATE_REGISTRY.byId(stateId);

        return iblockdata == null ? Blocks.AIR.defaultBlockState() : iblockdata;
    }

    public static Block byItem(@Nullable Item item) {
        return item instanceof BlockItem ? ((BlockItem) item).getBlock() : Blocks.AIR;
    }

    public static BlockState pushEntitiesUp(BlockState from, BlockState to, LevelAccessor world, BlockPos pos) {
        VoxelShape voxelshape = Shapes.joinUnoptimized(from.getCollisionShape(world, pos), to.getCollisionShape(world, pos), BooleanOp.ONLY_SECOND).move((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());

        if (voxelshape.isEmpty()) {
            return to;
        } else {
            List<Entity> list = world.getEntities((Entity) null, voxelshape.bounds());
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();
                double d0 = Shapes.collide(Direction.Axis.Y, entity.getBoundingBox().move(0.0D, 1.0D, 0.0D), List.of(voxelshape), -1.0D);

                entity.teleportRelative(0.0D, 1.0D + d0, 0.0D);
            }

            return to;
        }
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Shapes.box(minX / 16.0D, minY / 16.0D, minZ / 16.0D, maxX / 16.0D, maxY / 16.0D, maxZ / 16.0D);
    }

    public static BlockState updateFromNeighbourShapes(BlockState state, LevelAccessor world, BlockPos pos) {
        BlockState iblockdata1 = state;
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        Direction[] aenumdirection = Block.UPDATE_SHAPE_ORDER;
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            blockposition_mutableblockposition.setWithOffset(pos, enumdirection);
            iblockdata1 = iblockdata1.updateShape(enumdirection, world.getBlockState(blockposition_mutableblockposition), world, pos, blockposition_mutableblockposition);
        }

        return iblockdata1;
    }

    public static void updateOrDestroy(BlockState state, BlockState newState, LevelAccessor world, BlockPos pos, int flags) {
        Block.updateOrDestroy(state, newState, world, pos, flags, 512);
    }

    public static void updateOrDestroy(BlockState state, BlockState newState, LevelAccessor world, BlockPos pos, int flags, int maxUpdateDepth) {
        if (newState != state) {
            if (newState.isAir()) {
                if (!world.isClientSide()) {
                    world.destroyBlock(pos, (flags & 32) == 0, (Entity) null, maxUpdateDepth);
                }
            } else {
                world.setBlock(pos, newState, flags & -33, maxUpdateDepth);
            }
        }

    }

    public Block(BlockBehaviour.Properties settings) {
        super(settings);
        this.builtInRegistryHolder = BuiltInRegistries.BLOCK.createIntrusiveHolder(this);
        StateDefinition.Builder<Block, BlockState> blockstatelist_a = new StateDefinition.Builder<>(this);

        this.createBlockStateDefinition(blockstatelist_a);
        this.stateDefinition = blockstatelist_a.create(Block::defaultBlockState, BlockState::new);
        this.registerDefaultState((BlockState) this.stateDefinition.any());
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            String s = this.getClass().getSimpleName();

            if (!s.endsWith("Block")) {
                Block.LOGGER.error("Block classes should end with Block and {} doesn't.", s);
            }
        }

    }

    public static boolean isExceptionForConnection(BlockState state) {
        return state.getBlock() instanceof LeavesBlock || state.is(Blocks.BARRIER) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN) || state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(BlockTags.SHULKER_BOXES);
    }

    public boolean isRandomlyTicking(BlockState state) {
        return this.isRandomlyTicking;
    }

    public static boolean shouldRenderFace(BlockState state, BlockGetter world, BlockPos pos, Direction side, BlockPos otherPos) {
        BlockState iblockdata1 = world.getBlockState(otherPos);

        if (state.skipRendering(iblockdata1, side)) {
            return false;
        } else if (iblockdata1.canOcclude()) {
            Block.BlockStatePairKey block_a = new Block.BlockStatePairKey(state, iblockdata1, side);
            Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = (Object2ByteLinkedOpenHashMap) Block.OCCLUSION_CACHE.get();
            byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(block_a);

            if (b0 != 127) {
                return b0 != 0;
            } else {
                VoxelShape voxelshape = state.getFaceOcclusionShape(world, pos, side);

                if (voxelshape.isEmpty()) {
                    return true;
                } else {
                    VoxelShape voxelshape1 = iblockdata1.getFaceOcclusionShape(world, otherPos, side.getOpposite());
                    boolean flag = Shapes.joinIsNotEmpty(voxelshape, voxelshape1, BooleanOp.ONLY_FIRST);

                    if (object2bytelinkedopenhashmap.size() == 2048) {
                        object2bytelinkedopenhashmap.removeLastByte();
                    }

                    object2bytelinkedopenhashmap.putAndMoveToFirst(block_a, (byte) (flag ? 1 : 0));
                    return flag;
                }
            }
        } else {
            return true;
        }
    }

    public static boolean canSupportRigidBlock(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos).isFaceSturdy(world, pos, Direction.UP, SupportType.RIGID);
    }

    public static boolean canSupportCenter(LevelReader world, BlockPos pos, Direction side) {
        BlockState iblockdata = world.getBlockState(pos);

        return side == Direction.DOWN && iblockdata.is(BlockTags.UNSTABLE_BOTTOM_CENTER) ? false : iblockdata.isFaceSturdy(world, pos, side, SupportType.CENTER);
    }

    public static boolean isFaceFull(VoxelShape shape, Direction side) {
        VoxelShape voxelshape1 = shape.getFaceShape(side);

        return Block.isShapeFullBlock(voxelshape1);
    }

    public static boolean isShapeFullBlock(VoxelShape shape) {
        return shape.isFullBlock(); // Paper - optimise collisions
    }

    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return !Block.isShapeFullBlock(state.getShape(world, pos)) && state.getFluidState().isEmpty();
    }

    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {}

    public void destroy(LevelAccessor world, BlockPos pos, BlockState state) {}

    public static List<ItemStack> getDrops(BlockState state, ServerLevel world, BlockPos pos, @Nullable BlockEntity blockEntity) {
        LootParams.Builder lootparams_a = (new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);

        return state.getDrops(lootparams_a);
    }

    public static List<ItemStack> getDrops(BlockState state, ServerLevel world, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack stack) {
        LootParams.Builder lootparams_a = (new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.TOOL, stack).withOptionalParameter(LootContextParams.THIS_ENTITY, entity).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity);

        return state.getDrops(lootparams_a);
    }

    public static void dropResources(BlockState state, Level world, BlockPos pos) {
        if (world instanceof ServerLevel) {
            Block.getDrops(state, (ServerLevel) world, pos, (BlockEntity) null).forEach((itemstack) -> {
                Block.popResource(world, pos, itemstack);
            });
            state.spawnAfterBreak((ServerLevel) world, pos, ItemStack.EMPTY, true);
        }

    }

    public static void dropResources(BlockState state, LevelAccessor world, BlockPos pos, @Nullable BlockEntity blockEntity) {
        if (world instanceof ServerLevel) {
            Block.getDrops(state, (ServerLevel) world, pos, blockEntity).forEach((itemstack) -> {
                Block.popResource((ServerLevel) world, pos, itemstack);
            });
            state.spawnAfterBreak((ServerLevel) world, pos, ItemStack.EMPTY, true);
        }

    }
    // Paper start
    public static boolean dropResources(BlockState state, LevelAccessor world, BlockPos pos, @Nullable BlockEntity blockEntity, BlockPos source) {
        if (world instanceof ServerLevel) {
            List<org.bukkit.inventory.ItemStack> items = com.google.common.collect.Lists.newArrayList();
            for (net.minecraft.world.item.ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, world.getMinecraftWorld(), pos, blockEntity)) {
                items.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(drop));
            }
            io.papermc.paper.event.block.BlockBreakBlockEvent event = new io.papermc.paper.event.block.BlockBreakBlockEvent(org.bukkit.craftbukkit.block.CraftBlock.at(world, pos), org.bukkit.craftbukkit.block.CraftBlock.at(world, source), items);
            event.callEvent();
            for (var drop : event.getDrops()) {
                popResource(world.getMinecraftWorld(), pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(drop));
            }
            state.spawnAfterBreak(world.getMinecraftWorld(), pos, ItemStack.EMPTY, true);
        }
        return true;
    }
    // Paper end

    public static void dropResources(BlockState state, Level world, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack tool) {
        if (world instanceof ServerLevel) {
            Block.getDrops(state, (ServerLevel) world, pos, blockEntity, entity, tool).forEach((itemstack1) -> {
                Block.popResource(world, pos, itemstack1);
            });
            state.spawnAfterBreak((ServerLevel) world, pos, tool, true);
        }

    }

    public static void popResource(Level world, BlockPos pos, ItemStack stack) {
        double d0 = (double) EntityType.ITEM.getHeight() / 2.0D;
        double d1 = (double) pos.getX() + 0.5D + Mth.nextDouble(world.random, -0.25D, 0.25D);
        double d2 = (double) pos.getY() + 0.5D + Mth.nextDouble(world.random, -0.25D, 0.25D) - d0;
        double d3 = (double) pos.getZ() + 0.5D + Mth.nextDouble(world.random, -0.25D, 0.25D);

        Block.popResource(world, () -> {
            return new ItemEntity(world, d1, d2, d3, stack);
        }, stack);
    }

    public static void popResourceFromFace(Level world, BlockPos pos, Direction direction, ItemStack stack) {
        int i = direction.getStepX();
        int j = direction.getStepY();
        int k = direction.getStepZ();
        double d0 = (double) EntityType.ITEM.getWidth() / 2.0D;
        double d1 = (double) EntityType.ITEM.getHeight() / 2.0D;
        double d2 = (double) pos.getX() + 0.5D + (i == 0 ? Mth.nextDouble(world.random, -0.25D, 0.25D) : (double) i * (0.5D + d0));
        double d3 = (double) pos.getY() + 0.5D + (j == 0 ? Mth.nextDouble(world.random, -0.25D, 0.25D) : (double) j * (0.5D + d1)) - d1;
        double d4 = (double) pos.getZ() + 0.5D + (k == 0 ? Mth.nextDouble(world.random, -0.25D, 0.25D) : (double) k * (0.5D + d0));
        double d5 = i == 0 ? Mth.nextDouble(world.random, -0.1D, 0.1D) : (double) i * 0.1D;
        double d6 = j == 0 ? Mth.nextDouble(world.random, 0.0D, 0.1D) : (double) j * 0.1D + 0.1D;
        double d7 = k == 0 ? Mth.nextDouble(world.random, -0.1D, 0.1D) : (double) k * 0.1D;

        Block.popResource(world, () -> {
            return new ItemEntity(world, d2, d3, d4, stack, d5, d6, d7);
        }, stack);
    }

    private static void popResource(Level world, Supplier<ItemEntity> itemEntitySupplier, ItemStack stack) {
        if (!world.isClientSide && !stack.isEmpty() && world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            ItemEntity entityitem = (ItemEntity) itemEntitySupplier.get();

            entityitem.setDefaultPickUpDelay();
            // CraftBukkit start
            if (world.getCurrentWorldData().captureDrops != null) { // Folia - region threading
                world.getCurrentWorldData().captureDrops.add(entityitem); // Folia - region threading
            } else {
                world.addFreshEntity(entityitem);
            }
            // CraftBukkit end
        }
    }

    public void popExperience(ServerLevel world, BlockPos pos, int size) {
        // Paper start - add player parameter
        popExperience(world, pos, size, null);
    }
    public void popExperience(ServerLevel world, BlockPos pos, int size, net.minecraft.server.level.ServerPlayer player) {
        // Paper end - add player parameter
        if (world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            ExperienceOrb.award(world, Vec3.atCenterOf(pos), size, org.bukkit.entity.ExperienceOrb.SpawnReason.BLOCK_BREAK, player); // Paper
        }

    }

    public float getExplosionResistance() {
        return this.explosionResistance;
    }

    public void wasExploded(Level world, BlockPos pos, Explosion explosion) {}

    public void stepOn(Level world, BlockPos pos, BlockState state, Entity entity) {}

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState();
    }

    @io.papermc.paper.annotation.DoNotUse // Paper - method below allows better control of item drops
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        // Paper start
        this.playerDestroy(world, player, pos, state, blockEntity, tool, true);
    }
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool, boolean includeDrops) {
        // Paper end
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED); // CraftBukkit - EntityExhaustionEvent
        if (includeDrops) { // Paper
        Block.dropResources(state, world, pos, blockEntity, player, tool);
        } // Paper
    }

    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {}

    public boolean isPossibleToRespawnInThis(BlockState state) {
        return !state.isSolid() && !state.liquid();
    }

    public MutableComponent getName() {
        return Component.translatable(this.getDescriptionId());
    }

    public String getDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("block", BuiltInRegistries.BLOCK.getKey(this));
        }

        return this.descriptionId;
    }

    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }

    public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
    }

    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return new ItemStack(this);
    }

    public float getFriction() {
        return this.friction;
    }

    public float getSpeedFactor() {
        return this.speedFactor;
    }

    public float getJumpFactor() {
        return this.jumpFactor;
    }

    protected void spawnDestroyParticles(Level world, Player player, BlockPos pos, BlockState state) {
        world.levelEvent(player, 2001, pos, Block.getId(state));
    }

    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        this.spawnDestroyParticles(world, player, pos, state);
        if (state.is(BlockTags.GUARDED_BY_PIGLINS)) {
            PiglinAi.angerNearbyPiglins(player, false);
        }

        world.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
    }

    public void handlePrecipitation(BlockState state, Level world, BlockPos pos, Biome.Precipitation precipitation) {}

    public boolean dropFromExplosion(Explosion explosion) {
        return true;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {}

    public StateDefinition<Block, BlockState> getStateDefinition() {
        return this.stateDefinition;
    }

    protected final void registerDefaultState(BlockState state) {
        this.defaultBlockState = state;
    }

    public final BlockState defaultBlockState() {
        return this.defaultBlockState;
    }

    public final BlockState withPropertiesOf(BlockState state) {
        BlockState iblockdata1 = this.defaultBlockState();
        Iterator iterator = state.getBlock().getStateDefinition().getProperties().iterator();

        while (iterator.hasNext()) {
            Property<?> iblockstate = (Property) iterator.next();

            if (iblockdata1.hasProperty(iblockstate)) {
                iblockdata1 = Block.copyProperty(state, iblockdata1, iblockstate);
            }
        }

        return iblockdata1;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property) {
        return (BlockState) target.setValue(property, source.getValue(property));
    }

    public SoundType getSoundType(BlockState state) {
        return this.soundType;
    }

    @Override
    public Item asItem() {
        if (this.item == null) {
            this.item = Item.byBlock(this);
        }

        return this.item;
    }

    public boolean hasDynamicShape() {
        return this.dynamicShape;
    }

    public String toString() {
        return "Block{" + BuiltInRegistries.BLOCK.getKey(this) + "}";
    }

    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag options) {}

    @Override
    protected Block asBlock() {
        return this;
    }

    protected ImmutableMap<BlockState, VoxelShape> getShapeForEachState(Function<BlockState, VoxelShape> stateToShape) {
        return (ImmutableMap) this.stateDefinition.getPossibleStates().stream().collect(ImmutableMap.toImmutableMap(Function.identity(), stateToShape));
    }

    /** @deprecated */
    @Deprecated
    public Holder.Reference<Block> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    // CraftBukkit start
    protected int tryDropExperience(ServerLevel worldserver, BlockPos blockposition, ItemStack itemstack, IntProvider intprovider) {
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemstack) == 0) {
            int i = intprovider.sample(worldserver.random);

            if (i > 0) {
                // this.popExperience(worldserver, blockposition, i);
                return i;
            }
        }

        return 0;
    }

    public int getExpDrop(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, ItemStack itemstack, boolean flag) {
        return 0;
    }
    // CraftBukkit end

    // Spigot start
    public static float range(float min, float value, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
    // Spigot end

    public static final class BlockStatePairKey {

        private final BlockState first;
        private final BlockState second;
        private final Direction direction;

        public BlockStatePairKey(BlockState self, BlockState other, Direction facing) {
            this.first = self;
            this.second = other;
            this.direction = facing;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (!(object instanceof Block.BlockStatePairKey)) {
                return false;
            } else {
                Block.BlockStatePairKey block_a = (Block.BlockStatePairKey) object;

                return this.first == block_a.first && this.second == block_a.second && this.direction == block_a.direction;
            }
        }

        public int hashCode() {
            int i = this.first.hashCode();

            i = 31 * i + this.second.hashCode();
            i = 31 * i + this.direction.hashCode();
            return i;
        }
    }
}
