// mc-dev import
package net.minecraft.world.level.block.state;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BlockBehaviour implements FeatureElement {

    protected static final Direction[] UPDATE_SHAPE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};
    public final boolean hasCollision;
    protected final float explosionResistance;
    protected final boolean isRandomlyTicking;
    protected final SoundType soundType;
    protected final float friction;
    protected final float speedFactor;
    protected final float jumpFactor;
    protected final boolean dynamicShape;
    protected final FeatureFlagSet requiredFeatures;
    protected final BlockBehaviour.Properties properties;
    @Nullable
    protected ResourceLocation drops;

    public BlockBehaviour(BlockBehaviour.Properties settings) {
        this.hasCollision = settings.hasCollision;
        this.drops = settings.drops;
        this.explosionResistance = settings.explosionResistance;
        this.isRandomlyTicking = settings.isRandomlyTicking;
        this.soundType = settings.soundType;
        this.friction = settings.friction;
        this.speedFactor = settings.speedFactor;
        this.jumpFactor = settings.jumpFactor;
        this.dynamicShape = settings.dynamicShape;
        this.requiredFeatures = settings.requiredFeatures;
        this.properties = settings;
    }

    /** @deprecated */
    @Deprecated
    public void updateIndirectNeighbourShapes(BlockState state, LevelAccessor world, BlockPos pos, int flags, int maxUpdateDepth) {}

    /** @deprecated */
    @Deprecated
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        switch (type) {
            case LAND:
                return !state.isCollisionShapeFullBlock(world, pos);
            case WATER:
                return world.getFluidState(pos).is(FluidTags.WATER);
            case AIR:
                return !state.isCollisionShapeFullBlock(world, pos);
            default:
                return false;
        }
    }

    /** @deprecated */
    @Deprecated
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    /** @deprecated */
    @Deprecated
    public boolean skipRendering(BlockState state, BlockState stateFrom, Direction direction) {
        return false;
    }

    /** @deprecated */
    @Deprecated
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        DebugPackets.sendNeighborsUpdatePacket(world, pos);
    }

    // Paper start - add ItemActionContext param
    @Deprecated
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag, UseOnContext itemActionContext) {
        this.onPlace(iblockdata, world, blockposition, iblockdata1, flag);
    }
    // Paper end
    /** @deprecated */
    @Deprecated
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        org.spigotmc.AsyncCatcher.catchOp("block onPlace"); // Spigot
    }

    /** @deprecated */
    @Deprecated
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        org.spigotmc.AsyncCatcher.catchOp("block remove"); // Spigot
        if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
            world.removeBlockEntity(pos);
        }

    }

    /** @deprecated */
    @Deprecated
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** @deprecated */
    @Deprecated
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        return false;
    }

    /** @deprecated */
    @Deprecated
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** @deprecated */
    @Deprecated
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    /** @deprecated */
    @Deprecated
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    /** @deprecated */
    @Deprecated
    public FluidState getFluidState(BlockState state) {
        return Fluids.EMPTY.defaultFluidState();
    }

    /** @deprecated */
    @Deprecated
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    public float getMaxHorizontalOffset() {
        return 0.25F;
    }

    public float getMaxVerticalOffset() {
        return 0.2F;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    /** @deprecated */
    @Deprecated
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state;
    }

    /** @deprecated */
    @Deprecated
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    /** @deprecated */
    @Deprecated
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return state.canBeReplaced() && (context.getItemInHand().isEmpty() || !context.getItemInHand().is(this.asItem())) && (state.isDestroyable() || (context.getPlayer() != null && context.getPlayer().getAbilities().instabuild)); // Paper;
    }

    /** @deprecated */
    @Deprecated
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return state.canBeReplaced() || !state.isSolid();
    }

    /** @deprecated */
    @Deprecated
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ResourceLocation minecraftkey = this.getLootTable();

        if (minecraftkey == BuiltInLootTables.EMPTY) {
            return Collections.emptyList();
        } else {
            LootParams lootparams = builder.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
            ServerLevel worldserver = lootparams.getLevel();
            LootTable loottable = worldserver.getServer().getLootData().getLootTable(minecraftkey);

            return loottable.getRandomItems(lootparams);
        }
    }

    /** @deprecated */
    @Deprecated
    public long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos);
    }

    /** @deprecated */
    @Deprecated
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return state.getShape(world, pos);
    }

    /** @deprecated */
    @Deprecated
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return this.getCollisionShape(state, world, pos, CollisionContext.empty());
    }

    /** @deprecated */
    @Deprecated
    public VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    /** @deprecated */
    @Deprecated
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return state.isSolidRender(world, pos) ? world.getMaxLightLevel() : (state.propagatesSkylightDown(world, pos) ? 0 : 1);
    }

    /** @deprecated */
    @Nullable
    @Deprecated
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        return null;
    }

    /** @deprecated */
    @Deprecated
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    /** @deprecated */
    @Deprecated
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return state.isCollisionShapeFullBlock(world, pos) ? 0.2F : 1.0F;
    }

    /** @deprecated */
    @Deprecated
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return 0;
    }

    /** @deprecated */
    @Deprecated
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** @deprecated */
    @Deprecated
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.hasCollision ? state.getShape(world, pos) : Shapes.empty();
    }

    /** @deprecated */
    @Deprecated
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return Block.isShapeFullBlock(state.getCollisionShape(world, pos));
    }

    /** @deprecated */
    @Deprecated
    public boolean isOcclusionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return Block.isShapeFullBlock(state.getOcclusionShape(world, pos));
    }

    /** @deprecated */
    @Deprecated
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.getCollisionShape(state, world, pos, context);
    }

    /** @deprecated */
    @Deprecated
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.tick(state, world, pos, random);
    }

    /** @deprecated */
    @Deprecated
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {}

    /** @deprecated */
    @Deprecated
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        float f = state.getDestroySpeed(world, pos);

        if (f == -1.0F) {
            return 0.0F;
        } else {
            int i = player.hasCorrectToolForDrops(state) ? 30 : 100;

            return player.getDestroySpeed(state) / f / (float) i;
        }
    }

    /** @deprecated */
    @Deprecated
    public void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {}

    /** @deprecated */
    @Deprecated
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {}

    /** @deprecated */
    @Deprecated
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return 0;
    }

    /** @deprecated */
    @Deprecated
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {}

    /** @deprecated */
    @Deprecated
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return 0;
    }

    public final ResourceLocation getLootTable() {
        if (this.drops == null) {
            ResourceLocation minecraftkey = BuiltInRegistries.BLOCK.getKey(this.asBlock());

            this.drops = minecraftkey.withPrefix("blocks/");
        }

        return this.drops;
    }

    /** @deprecated */
    @Deprecated
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {}

    public abstract Item asItem();

    protected abstract Block asBlock();

    public MapColor defaultMapColor() {
        return (MapColor) this.properties.mapColor.apply(this.asBlock().defaultBlockState());
    }

    public float defaultDestroyTime() {
        return this.properties.destroyTime;
    }

    public static class Properties {

        Function<BlockState, MapColor> mapColor = (iblockdata) -> {
            return MapColor.NONE;
        };
        boolean hasCollision = true;
        SoundType soundType;
        ToIntFunction<BlockState> lightEmission;
        float explosionResistance;
        float destroyTime;
        boolean requiresCorrectToolForDrops;
        boolean isRandomlyTicking;
        float friction;
        float speedFactor;
        float jumpFactor;
        ResourceLocation drops;
        boolean canOcclude;
        boolean isAir;
        boolean ignitedByLava;
        /** @deprecated */
        @Deprecated
        boolean liquid;
        /** @deprecated */
        @Deprecated
        boolean forceSolidOff;
        boolean forceSolidOn;
        PushReaction pushReaction;
        boolean spawnTerrainParticles;
        NoteBlockInstrument instrument;
        boolean replaceable;
        BlockBehaviour.StateArgumentPredicate<EntityType<?>> isValidSpawn;
        BlockBehaviour.StatePredicate isRedstoneConductor;
        BlockBehaviour.StatePredicate isSuffocating;
        BlockBehaviour.StatePredicate isViewBlocking;
        BlockBehaviour.StatePredicate hasPostProcess;
        BlockBehaviour.StatePredicate emissiveRendering;
        boolean dynamicShape;
        FeatureFlagSet requiredFeatures;
        Optional<BlockBehaviour.OffsetFunction> offsetFunction;

        private Properties() {
            this.soundType = SoundType.STONE;
            this.lightEmission = (iblockdata) -> {
                return 0;
            };
            this.friction = 0.6F;
            this.speedFactor = 1.0F;
            this.jumpFactor = 1.0F;
            this.canOcclude = true;
            this.pushReaction = PushReaction.NORMAL;
            this.spawnTerrainParticles = true;
            this.instrument = NoteBlockInstrument.HARP;
            this.isValidSpawn = (iblockdata, iblockaccess, blockposition, entitytypes) -> {
                return iblockdata.isFaceSturdy(iblockaccess, blockposition, Direction.UP) && iblockdata.getLightEmission() < 14;
            };
            this.isRedstoneConductor = (iblockdata, iblockaccess, blockposition) -> {
                return iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition);
            };
            this.isSuffocating = (iblockdata, iblockaccess, blockposition) -> {
                return iblockdata.blocksMotion() && iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition);
            };
            this.isViewBlocking = this.isSuffocating;
            this.hasPostProcess = (iblockdata, iblockaccess, blockposition) -> {
                return false;
            };
            this.emissiveRendering = (iblockdata, iblockaccess, blockposition) -> {
                return false;
            };
            this.requiredFeatures = FeatureFlags.VANILLA_SET;
            this.offsetFunction = Optional.empty();
        }

        public static BlockBehaviour.Properties of() {
            return new BlockBehaviour.Properties();
        }

        public static BlockBehaviour.Properties copy(BlockBehaviour block) {
            BlockBehaviour.Properties blockbase_info = new BlockBehaviour.Properties();

            blockbase_info.destroyTime = block.properties.destroyTime;
            blockbase_info.explosionResistance = block.properties.explosionResistance;
            blockbase_info.hasCollision = block.properties.hasCollision;
            blockbase_info.isRandomlyTicking = block.properties.isRandomlyTicking;
            blockbase_info.lightEmission = block.properties.lightEmission;
            blockbase_info.mapColor = block.properties.mapColor;
            blockbase_info.soundType = block.properties.soundType;
            blockbase_info.friction = block.properties.friction;
            blockbase_info.speedFactor = block.properties.speedFactor;
            blockbase_info.dynamicShape = block.properties.dynamicShape;
            blockbase_info.canOcclude = block.properties.canOcclude;
            blockbase_info.isAir = block.properties.isAir;
            blockbase_info.ignitedByLava = block.properties.ignitedByLava;
            blockbase_info.liquid = block.properties.liquid;
            blockbase_info.forceSolidOff = block.properties.forceSolidOff;
            blockbase_info.forceSolidOn = block.properties.forceSolidOn;
            blockbase_info.pushReaction = block.properties.pushReaction;
            blockbase_info.requiresCorrectToolForDrops = block.properties.requiresCorrectToolForDrops;
            blockbase_info.offsetFunction = block.properties.offsetFunction;
            blockbase_info.spawnTerrainParticles = block.properties.spawnTerrainParticles;
            blockbase_info.requiredFeatures = block.properties.requiredFeatures;
            blockbase_info.emissiveRendering = block.properties.emissiveRendering;
            blockbase_info.instrument = block.properties.instrument;
            blockbase_info.replaceable = block.properties.replaceable;
            return blockbase_info;
        }

        public BlockBehaviour.Properties mapColor(DyeColor color) {
            this.mapColor = (iblockdata) -> {
                return color.getMapColor();
            };
            return this;
        }

        public BlockBehaviour.Properties mapColor(MapColor color) {
            this.mapColor = (iblockdata) -> {
                return color;
            };
            return this;
        }

        public BlockBehaviour.Properties mapColor(Function<BlockState, MapColor> mapColorProvider) {
            this.mapColor = mapColorProvider;
            return this;
        }

        public BlockBehaviour.Properties noCollission() {
            this.hasCollision = false;
            this.canOcclude = false;
            return this;
        }

        public BlockBehaviour.Properties noOcclusion() {
            this.canOcclude = false;
            return this;
        }

        public BlockBehaviour.Properties friction(float slipperiness) {
            this.friction = slipperiness;
            return this;
        }

        public BlockBehaviour.Properties speedFactor(float velocityMultiplier) {
            this.speedFactor = velocityMultiplier;
            return this;
        }

        public BlockBehaviour.Properties jumpFactor(float jumpVelocityMultiplier) {
            this.jumpFactor = jumpVelocityMultiplier;
            return this;
        }

        public BlockBehaviour.Properties sound(SoundType soundGroup) {
            this.soundType = soundGroup;
            return this;
        }

        public BlockBehaviour.Properties lightLevel(ToIntFunction<BlockState> luminance) {
            this.lightEmission = luminance;
            return this;
        }

        public BlockBehaviour.Properties strength(float hardness, float resistance) {
            return this.destroyTime(hardness).explosionResistance(resistance);
        }

        public BlockBehaviour.Properties instabreak() {
            return this.strength(0.0F);
        }

        public BlockBehaviour.Properties strength(float strength) {
            this.strength(strength, strength);
            return this;
        }

        public BlockBehaviour.Properties randomTicks() {
            this.isRandomlyTicking = true;
            return this;
        }

        public BlockBehaviour.Properties dynamicShape() {
            this.dynamicShape = true;
            return this;
        }

        public BlockBehaviour.Properties noLootTable() {
            this.drops = BuiltInLootTables.EMPTY;
            return this;
        }

        public BlockBehaviour.Properties dropsLike(Block source) {
            this.drops = source.getLootTable();
            return this;
        }

        public BlockBehaviour.Properties ignitedByLava() {
            this.ignitedByLava = true;
            return this;
        }

        public BlockBehaviour.Properties liquid() {
            this.liquid = true;
            return this;
        }

        public BlockBehaviour.Properties forceSolidOn() {
            this.forceSolidOn = true;
            return this;
        }

        /** @deprecated */
        @Deprecated
        public BlockBehaviour.Properties forceSolidOff() {
            this.forceSolidOff = true;
            return this;
        }

        public BlockBehaviour.Properties pushReaction(PushReaction pistonBehavior) {
            this.pushReaction = pistonBehavior;
            return this;
        }

        public BlockBehaviour.Properties air() {
            this.isAir = true;
            return this;
        }

        public BlockBehaviour.Properties isValidSpawn(BlockBehaviour.StateArgumentPredicate<EntityType<?>> predicate) {
            this.isValidSpawn = predicate;
            return this;
        }

        public BlockBehaviour.Properties isRedstoneConductor(BlockBehaviour.StatePredicate predicate) {
            this.isRedstoneConductor = predicate;
            return this;
        }

        public BlockBehaviour.Properties isSuffocating(BlockBehaviour.StatePredicate predicate) {
            this.isSuffocating = predicate;
            return this;
        }

        public BlockBehaviour.Properties isViewBlocking(BlockBehaviour.StatePredicate predicate) {
            this.isViewBlocking = predicate;
            return this;
        }

        public BlockBehaviour.Properties hasPostProcess(BlockBehaviour.StatePredicate predicate) {
            this.hasPostProcess = predicate;
            return this;
        }

        public BlockBehaviour.Properties emissiveRendering(BlockBehaviour.StatePredicate predicate) {
            this.emissiveRendering = predicate;
            return this;
        }

        public BlockBehaviour.Properties requiresCorrectToolForDrops() {
            this.requiresCorrectToolForDrops = true;
            return this;
        }

        public BlockBehaviour.Properties destroyTime(float hardness) {
            this.destroyTime = hardness;
            return this;
        }

        public BlockBehaviour.Properties explosionResistance(float resistance) {
            this.explosionResistance = Math.max(0.0F, resistance);
            return this;
        }

        public BlockBehaviour.Properties offsetType(BlockBehaviour.OffsetType offsetType) {
            switch (offsetType) {
                case XYZ:
                    this.offsetFunction = Optional.of((iblockdata, iblockaccess, blockposition) -> {
                        Block block = iblockdata.getBlock();
                        long i = Mth.getSeed(blockposition.getX(), 0, blockposition.getZ());
                        double d0 = ((double) ((float) (i >> 4 & 15L) / 15.0F) - 1.0D) * (double) block.getMaxVerticalOffset();
                        float f = block.getMaxHorizontalOffset();
                        double d1 = Mth.clamp(((double) ((float) (i & 15L) / 15.0F) - 0.5D) * 0.5D, (double) (-f), (double) f);
                        double d2 = Mth.clamp(((double) ((float) (i >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D, (double) (-f), (double) f);

                        return new Vec3(d1, d0, d2);
                    });
                    break;
                case XZ:
                    this.offsetFunction = Optional.of((iblockdata, iblockaccess, blockposition) -> {
                        Block block = iblockdata.getBlock();
                        long i = Mth.getSeed(blockposition.getX(), 0, blockposition.getZ());
                        float f = block.getMaxHorizontalOffset();
                        double d0 = Mth.clamp(((double) ((float) (i & 15L) / 15.0F) - 0.5D) * 0.5D, (double) (-f), (double) f);
                        double d1 = Mth.clamp(((double) ((float) (i >> 8 & 15L) / 15.0F) - 0.5D) * 0.5D, (double) (-f), (double) f);

                        return new Vec3(d0, 0.0D, d1);
                    });
                    break;
                default:
                    this.offsetFunction = Optional.empty();
            }

            return this;
        }

        public BlockBehaviour.Properties noTerrainParticles() {
            this.spawnTerrainParticles = false;
            return this;
        }

        public BlockBehaviour.Properties requiredFeatures(FeatureFlag... features) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(features);
            return this;
        }

        public BlockBehaviour.Properties instrument(NoteBlockInstrument instrument) {
            this.instrument = instrument;
            return this;
        }

        public BlockBehaviour.Properties replaceable() {
            this.replaceable = true;
            return this;
        }
    }

    public interface StateArgumentPredicate<A> {

        boolean test(BlockState state, BlockGetter world, BlockPos pos, A type);
    }

    public interface OffsetFunction {

        Vec3 evaluate(BlockState state, BlockGetter world, BlockPos pos);
    }

    public interface StatePredicate {

        boolean test(BlockState state, BlockGetter world, BlockPos pos);
    }

    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> {

        private final int lightEmission; public final int getEmittedLight() { return this.lightEmission; } // Paper - OBFHELPER
        private final boolean useShapeForLightOcclusion; public final boolean isTransparentOnSomeFaces() { return this.useShapeForLightOcclusion; } // Paper - OBFHELPER
        private final boolean isAir;
        private final boolean ignitedByLava;
        /** @deprecated */
        @Deprecated
        private final boolean liquid;
        /** @deprecated */
        @Deprecated
        private boolean legacySolid;
        private final PushReaction pushReaction;
        private final MapColor mapColor;
        public final float destroySpeed;
        private final boolean requiresCorrectToolForDrops;
        private final boolean canOcclude; public final boolean isOpaque() { return this.canOcclude; } // Paper - OBFHELPER
        private final BlockBehaviour.StatePredicate isRedstoneConductor;
        private final BlockBehaviour.StatePredicate isSuffocating;
        private final BlockBehaviour.StatePredicate isViewBlocking;
        private final BlockBehaviour.StatePredicate hasPostProcess;
        private final BlockBehaviour.StatePredicate emissiveRendering;
        private final Optional<BlockBehaviour.OffsetFunction> offsetFunction;
        private final boolean spawnTerrainParticles;
        private final NoteBlockInstrument instrument;
        private final boolean replaceable;
        @Nullable
        protected BlockBehaviour.BlockStateBase.Cache cache;
        private FluidState fluidState;
        private boolean isRandomlyTicking;

        protected BlockStateBase(Block block, ImmutableMap<Property<?>, Comparable<?>> propertyMap, MapCodec<BlockState> codec) {
            super(block, propertyMap, codec);
            this.fluidState = Fluids.EMPTY.defaultFluidState();
            BlockBehaviour.Properties blockbase_info = block.properties;

            this.lightEmission = blockbase_info.lightEmission.applyAsInt(this.asState());
            this.useShapeForLightOcclusion = block.useShapeForLightOcclusion(this.asState());
            this.isAir = blockbase_info.isAir;
            this.ignitedByLava = blockbase_info.ignitedByLava;
            this.liquid = blockbase_info.liquid;
            this.pushReaction = blockbase_info.pushReaction;
            this.mapColor = (MapColor) blockbase_info.mapColor.apply(this.asState());
            this.destroySpeed = blockbase_info.destroyTime;
            this.requiresCorrectToolForDrops = blockbase_info.requiresCorrectToolForDrops;
            this.canOcclude = blockbase_info.canOcclude;
            this.isRedstoneConductor = blockbase_info.isRedstoneConductor;
            this.isSuffocating = blockbase_info.isSuffocating;
            this.isViewBlocking = blockbase_info.isViewBlocking;
            this.hasPostProcess = blockbase_info.hasPostProcess;
            this.emissiveRendering = blockbase_info.emissiveRendering;
            this.offsetFunction = blockbase_info.offsetFunction;
            this.spawnTerrainParticles = blockbase_info.spawnTerrainParticles;
            this.instrument = blockbase_info.instrument;
            this.replaceable = blockbase_info.replaceable;
            this.conditionallyFullOpaque = this.isOpaque() & this.isTransparentOnSomeFaces(); // Paper
            // Paper start - optimise collisions
            this.id1 = it.unimi.dsi.fastutil.HashCommon.murmurHash3(it.unimi.dsi.fastutil.HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
            this.id2 = it.unimi.dsi.fastutil.HashCommon.murmurHash3(it.unimi.dsi.fastutil.HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
            // Paper end - optimise collisions
        }
        // Paper start - impl cached craft block data, lazy load to fix issue with loading at the wrong time
        private org.bukkit.craftbukkit.block.data.CraftBlockData cachedCraftBlockData;

        public org.bukkit.craftbukkit.block.data.CraftBlockData createCraftBlockData() {
            if (cachedCraftBlockData == null) cachedCraftBlockData = org.bukkit.craftbukkit.block.data.CraftBlockData.createData(asState());
            return (org.bukkit.craftbukkit.block.data.CraftBlockData) cachedCraftBlockData.clone();
        }
        // Paper end

        private boolean calculateSolid() {
            if (((Block) this.owner).properties.forceSolidOn) {
                return true;
            } else if (((Block) this.owner).properties.forceSolidOff) {
                return false;
            } else if (this.cache == null) {
                return false;
            } else {
                VoxelShape voxelshape = this.cache.collisionShape;

                if (voxelshape.isEmpty()) {
                    return false;
                } else {
                    AABB axisalignedbb = voxelshape.bounds();

                    return axisalignedbb.getSize() >= 0.7291666666666666D ? true : axisalignedbb.getYsize() >= 1.0D;
                }
            }
        }

        // Paper start
        protected boolean shapeExceedsCube = true;
        public final boolean shapeExceedsCube() {
            return this.shapeExceedsCube;
        }
        // Paper end
        // Paper start - starlight
        protected int opacityIfCached = -1;
        // ret -1 if opacity is dynamic, or -1 if the block is conditionally full opaque, else return opacity in [0, 15]
        public final int getOpacityIfCached() {
            return this.opacityIfCached;
        }

        protected final boolean conditionallyFullOpaque;
        public final boolean isConditionallyFullOpaque() {
            return this.conditionallyFullOpaque;
        }
        // Paper end - starlight
        // Paper start - optimise collisions
        private static final int RANDOM_OFFSET = 704237939;
        private static final Direction[] DIRECTIONS_CACHED = Direction.values();
        private static final java.util.concurrent.atomic.AtomicInteger ID_GENERATOR = new java.util.concurrent.atomic.AtomicInteger();
        private final int id1, id2;
        private boolean occludesFullBlock;
        private boolean emptyCollisionShape;
        private VoxelShape constantCollisionShape;
        private AABB constantAABBCollision;
        private static void initCaches(final VoxelShape shape) {
            shape.isFullBlock();
            shape.occludesFullBlock();
            shape.toAabbs();
            if (!shape.isEmpty()) {
                shape.bounds();
            }
        }

        public final boolean hasCache() {
            return this.cache != null;
        }

        public final boolean occludesFullBlock() {
            return this.occludesFullBlock;
        }

        public final boolean emptyCollisionShape() {
            return this.emptyCollisionShape;
        }

        public final int uniqueId1() {
            return this.id1;
        }

        public final int uniqueId2() {
            return this.id2;
        }

        public final VoxelShape getConstantCollisionShape() {
            return this.constantCollisionShape;
        }

        public final AABB getConstantCollisionAABB() {
            return this.constantAABBCollision;
        }
        // Paper end - optimise collisions

        public void initCache() {
            this.fluidState = ((Block) this.owner).getFluidState(this.asState());
            this.isRandomlyTicking = ((Block) this.owner).isRandomlyTicking(this.asState());
            if (!this.getBlock().hasDynamicShape()) {
                this.cache = new BlockBehaviour.BlockStateBase.Cache(this.asState());
            }
            this.shapeExceedsCube = this.cache == null || this.cache.largeCollisionShape; // Paper - moved from actual method to here
            this.opacityIfCached = this.cache == null || this.isConditionallyFullOpaque() ? -1 : this.cache.lightBlock; // Paper - starlight - cache opacity for light

            this.legacySolid = this.calculateSolid();
            // Paper start - optimise collisions
            if (this.cache != null) {
                final VoxelShape collisionShape = this.cache.collisionShape;
                try {
                    this.constantCollisionShape = this.getCollisionShape(null, null, null);
                    this.constantAABBCollision = this.constantCollisionShape == null ? null : this.constantCollisionShape.getSingleAABBRepresentation();
                } catch (final Throwable throwable) {
                    this.constantCollisionShape = null;
                    this.constantAABBCollision = null;
                }
                this.occludesFullBlock = collisionShape.occludesFullBlock();
                this.emptyCollisionShape = collisionShape.isEmpty();
                // init caches
                initCaches(collisionShape);
                if (collisionShape != Shapes.empty() && collisionShape != Shapes.block()) {
                    for (final Direction direction : DIRECTIONS_CACHED) {
                        // initialise the directional face shape cache as well
                        final VoxelShape shape = Shapes.getFaceShape(collisionShape, direction);
                        initCaches(shape);
                    }
                }
                if (this.cache.occlusionShapes != null) {
                    for (final VoxelShape shape : this.cache.occlusionShapes) {
                        initCaches(shape);
                    }
                }
            } else {
                this.occludesFullBlock = false;
                this.emptyCollisionShape = false;
                this.constantCollisionShape = null;
                this.constantAABBCollision = null;
            }
            // Paper end - optimise collisions
        }

        public Block getBlock() {
            return (Block) this.owner;
        }

        public Holder<Block> getBlockHolder() {
            return ((Block) this.owner).builtInRegistryHolder();
        }

        /** @deprecated */
        @Deprecated
        public boolean blocksMotion() {
            Block block = this.getBlock();

            return block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING && this.isSolid();
        }

        /** @deprecated */
        @Deprecated
        public boolean isSolid() {
            return this.legacySolid;
        }

        // Paper start
        public final boolean isDestroyable() {
            return getBlock().isDestroyable();
        }
        // Paper end

        public boolean isValidSpawn(BlockGetter world, BlockPos pos, EntityType<?> type) {
            return this.getBlock().properties.isValidSpawn.test(this.asState(), world, pos, type);
        }

        public boolean propagatesSkylightDown(BlockGetter world, BlockPos pos) {
            return this.cache != null ? this.cache.propagatesSkylightDown : this.getBlock().propagatesSkylightDown(this.asState(), world, pos);
        }

        public int getLightBlock(BlockGetter world, BlockPos pos) {
            return this.cache != null ? this.cache.lightBlock : this.getBlock().getLightBlock(this.asState(), world, pos);
        }

        public VoxelShape getFaceOcclusionShape(BlockGetter world, BlockPos pos, Direction direction) {
            return this.cache != null && this.cache.occlusionShapes != null ? this.cache.occlusionShapes[direction.ordinal()] : Shapes.getFaceShape(this.getOcclusionShape(world, pos), direction);
        }

        public VoxelShape getOcclusionShape(BlockGetter world, BlockPos pos) {
            return this.getBlock().getOcclusionShape(this.asState(), world, pos);
        }

        public final boolean hasLargeCollisionShape() { // Paper
            return this.shapeExceedsCube; // Paper - moved into shape cache init
        }

        public final boolean useShapeForLightOcclusion() { // Paper
            return this.useShapeForLightOcclusion;
        }

        public final int getLightEmission() { // Paper
            return this.lightEmission;
        }

        public final boolean isAir() { // Paper
            return this.isAir;
        }

        public boolean ignitedByLava() {
            return this.ignitedByLava;
        }

        /** @deprecated */
        @Deprecated
        public boolean liquid() {
            return this.liquid;
        }

        public MapColor getMapColor(BlockGetter world, BlockPos pos) {
            return this.mapColor;
        }

        public BlockState rotate(Rotation rotation) {
            return this.getBlock().rotate(this.asState(), rotation);
        }

        public BlockState mirror(Mirror mirror) {
            return this.getBlock().mirror(this.asState(), mirror);
        }

        public RenderShape getRenderShape() {
            return this.getBlock().getRenderShape(this.asState());
        }

        public boolean emissiveRendering(BlockGetter world, BlockPos pos) {
            return this.emissiveRendering.test(this.asState(), world, pos);
        }

        public float getShadeBrightness(BlockGetter world, BlockPos pos) {
            return this.getBlock().getShadeBrightness(this.asState(), world, pos);
        }

        public boolean isRedstoneConductor(BlockGetter world, BlockPos pos) {
            return this.isRedstoneConductor.test(this.asState(), world, pos);
        }

        public boolean isSignalSource() {
            return this.getBlock().isSignalSource(this.asState());
        }

        public int getSignal(BlockGetter world, BlockPos pos, Direction direction) {
            return this.getBlock().getSignal(this.asState(), world, pos, direction);
        }

        public boolean hasAnalogOutputSignal() {
            return this.getBlock().hasAnalogOutputSignal(this.asState());
        }

        public int getAnalogOutputSignal(Level world, BlockPos pos) {
            return this.getBlock().getAnalogOutputSignal(this.asState(), world, pos);
        }

        public float getDestroySpeed(BlockGetter world, BlockPos pos) {
            return this.destroySpeed;
        }

        public float getDestroyProgress(Player player, BlockGetter world, BlockPos pos) {
            return this.getBlock().getDestroyProgress(this.asState(), player, world, pos);
        }

        public int getDirectSignal(BlockGetter world, BlockPos pos, Direction direction) {
            return this.getBlock().getDirectSignal(this.asState(), world, pos, direction);
        }

        public PushReaction getPistonPushReaction() {
            return !this.isDestroyable() ? PushReaction.BLOCK : this.pushReaction; // Paper
        }

        public boolean isSolidRender(BlockGetter world, BlockPos pos) {
            if (this.cache != null) {
                return this.cache.solidRender;
            } else {
                BlockState iblockdata = this.asState();

                return iblockdata.canOcclude() ? Block.isShapeFullBlock(iblockdata.getOcclusionShape(world, pos)) : false;
            }
        }

        public final boolean canOcclude() { // Paper
            return this.canOcclude;
        }

        public boolean skipRendering(BlockState state, Direction direction) {
            return this.getBlock().skipRendering(this.asState(), state, direction);
        }

        public VoxelShape getShape(BlockGetter world, BlockPos pos) {
            return this.getShape(world, pos, CollisionContext.empty());
        }

        public VoxelShape getShape(BlockGetter world, BlockPos pos, CollisionContext context) {
            return this.getBlock().getShape(this.asState(), world, pos, context);
        }

        public VoxelShape getCollisionShape(BlockGetter world, BlockPos pos) {
            return this.cache != null ? this.cache.collisionShape : this.getCollisionShape(world, pos, CollisionContext.empty());
        }

        public VoxelShape getCollisionShape(BlockGetter world, BlockPos pos, CollisionContext context) {
            return this.getBlock().getCollisionShape(this.asState(), world, pos, context);
        }

        public VoxelShape getBlockSupportShape(BlockGetter world, BlockPos pos) {
            return this.getBlock().getBlockSupportShape(this.asState(), world, pos);
        }

        public VoxelShape getVisualShape(BlockGetter world, BlockPos pos, CollisionContext context) {
            return this.getBlock().getVisualShape(this.asState(), world, pos, context);
        }

        public VoxelShape getInteractionShape(BlockGetter world, BlockPos pos) {
            return this.getBlock().getInteractionShape(this.asState(), world, pos);
        }

        public final boolean entityCanStandOn(BlockGetter world, BlockPos pos, Entity entity) {
            return this.entityCanStandOnFace(world, pos, entity, Direction.UP);
        }

        public final boolean entityCanStandOnFace(BlockGetter world, BlockPos pos, Entity entity, Direction direction) {
            return Block.isFaceFull(this.getCollisionShape(world, pos, CollisionContext.of(entity)), direction);
        }

        public Vec3 getOffset(BlockGetter world, BlockPos pos) {
            return (Vec3) this.offsetFunction.map((blockbase_b) -> {
                return blockbase_b.evaluate(this.asState(), world, pos);
            }).orElse(Vec3.ZERO);
        }

        public boolean hasOffsetFunction() {
            return this.offsetFunction.isPresent();
        }

        public boolean triggerEvent(Level world, BlockPos pos, int type, int data) {
            return this.getBlock().triggerEvent(this.asState(), world, pos, type, data);
        }

        /** @deprecated */
        @Deprecated
        public void neighborChanged(Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
            this.getBlock().neighborChanged(this.asState(), world, pos, sourceBlock, sourcePos, notify);
        }

        public final void updateNeighbourShapes(LevelAccessor world, BlockPos pos, int flags) {
            this.updateNeighbourShapes(world, pos, flags, 512);
        }

        public final void updateNeighbourShapes(LevelAccessor world, BlockPos pos, int flags, int maxUpdateDepth) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
            Direction[] aenumdirection = BlockBehaviour.UPDATE_SHAPE_ORDER;
            int k = aenumdirection.length;

            for (int l = 0; l < k; ++l) {
                Direction enumdirection = aenumdirection[l];

                blockposition_mutableblockposition.setWithOffset(pos, enumdirection);
                world.neighborShapeChanged(enumdirection.getOpposite(), this.asState(), blockposition_mutableblockposition, pos, flags, maxUpdateDepth);
            }

        }

        public final void updateIndirectNeighbourShapes(LevelAccessor world, BlockPos pos, int flags) {
            this.updateIndirectNeighbourShapes(world, pos, flags, 512);
        }

        public void updateIndirectNeighbourShapes(LevelAccessor world, BlockPos pos, int flags, int maxUpdateDepth) {
            this.getBlock().updateIndirectNeighbourShapes(this.asState(), world, pos, flags, maxUpdateDepth);
        }

        public void onPlace(Level world, BlockPos pos, BlockState state, boolean notify) {
            this.getBlock().onPlace(this.asState(), world, pos, state, notify);
        }

        public void onRemove(Level world, BlockPos pos, BlockState state, boolean moved) {
            this.getBlock().onRemove(this.asState(), world, pos, state, moved);
        }

        public void tick(ServerLevel world, BlockPos pos, RandomSource random) {
            this.getBlock().tick(this.asState(), world, pos, random);
        }

        public void randomTick(ServerLevel world, BlockPos pos, RandomSource random) {
            this.getBlock().randomTick(this.asState(), world, pos, random);
        }

        public void entityInside(Level world, BlockPos pos, Entity entity) {
            this.getBlock().entityInside(this.asState(), world, pos, entity);
        }

        public void spawnAfterBreak(ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
            this.getBlock().spawnAfterBreak(this.asState(), world, pos, tool, dropExperience);
        }

        public List<ItemStack> getDrops(LootParams.Builder builder) {
            return this.getBlock().getDrops(this.asState(), builder);
        }

        public InteractionResult use(Level world, Player player, InteractionHand hand, BlockHitResult hit) {
            return this.getBlock().use(this.asState(), world, hit.getBlockPos(), player, hand, hit);
        }

        public void attack(Level world, BlockPos pos, Player player) {
            this.getBlock().attack(this.asState(), world, pos, player);
        }

        public boolean isSuffocating(BlockGetter world, BlockPos pos) {
            return this.isSuffocating.test(this.asState(), world, pos);
        }

        public boolean isViewBlocking(BlockGetter world, BlockPos pos) {
            return this.isViewBlocking.test(this.asState(), world, pos);
        }

        public BlockState updateShape(Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
            return this.getBlock().updateShape(this.asState(), direction, neighborState, world, pos, neighborPos);
        }

        public boolean isPathfindable(BlockGetter world, BlockPos pos, PathComputationType type) {
            return this.getBlock().isPathfindable(this.asState(), world, pos, type);
        }

        public boolean canBeReplaced(BlockPlaceContext context) {
            return this.getBlock().canBeReplaced(this.asState(), context);
        }

        public boolean canBeReplaced(Fluid fluid) {
            return this.getBlock().canBeReplaced(this.asState(), fluid);
        }

        public boolean canBeReplaced() {
            return this.replaceable;
        }

        public boolean canSurvive(LevelReader world, BlockPos pos) {
            return this.getBlock().canSurvive(this.asState(), world, pos);
        }

        public boolean hasPostProcess(BlockGetter world, BlockPos pos) {
            return this.hasPostProcess.test(this.asState(), world, pos);
        }

        @Nullable
        public MenuProvider getMenuProvider(Level world, BlockPos pos) {
            return this.getBlock().getMenuProvider(this.asState(), world, pos);
        }

        public boolean is(TagKey<Block> tag) {
            return this.getBlock().builtInRegistryHolder().is(tag);
        }

        public boolean is(TagKey<Block> tag, Predicate<BlockBehaviour.BlockStateBase> predicate) {
            return this.is(tag) && predicate.test(this);
        }

        public boolean is(HolderSet<Block> blocks) {
            return blocks.contains(this.getBlock().builtInRegistryHolder());
        }

        public boolean is(Holder<Block> blockEntry) {
            return this.is((Block) blockEntry.value());
        }

        public Stream<TagKey<Block>> getTags() {
            return this.getBlock().builtInRegistryHolder().tags();
        }

        public boolean hasBlockEntity() {
            return this.getBlock() instanceof EntityBlock;
        }

        @Nullable
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockEntityType<T> blockEntityType) {
            return this.getBlock() instanceof EntityBlock ? ((EntityBlock) this.getBlock()).getTicker(world, this.asState(), blockEntityType) : null;
        }

        public boolean is(Block block) {
            return this.getBlock() == block;
        }

        public final FluidState getFluidState() { // Paper
            return this.fluidState;
        }

        public final boolean isRandomlyTicking() { // Paper
            return this.isRandomlyTicking;
        }

        public long getSeed(BlockPos pos) {
            return this.getBlock().getSeed(this.asState(), pos);
        }

        public SoundType getSoundType() {
            return this.getBlock().getSoundType(this.asState());
        }

        public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
            this.getBlock().onProjectileHit(world, state, hit, projectile);
        }

        public boolean isFaceSturdy(BlockGetter world, BlockPos pos, Direction direction) {
            return this.isFaceSturdy(world, pos, direction, SupportType.FULL);
        }

        public boolean isFaceSturdy(BlockGetter world, BlockPos pos, Direction direction, SupportType shapeType) {
            return this.cache != null ? this.cache.isFaceSturdy(direction, shapeType) : shapeType.isSupporting(this.asState(), world, pos, direction);
        }

        public boolean isCollisionShapeFullBlock(BlockGetter world, BlockPos pos) {
            return this.cache != null ? this.cache.isCollisionShapeFullBlock : this.getBlock().isCollisionShapeFullBlock(this.asState(), world, pos);
        }

        protected abstract BlockState asState();

        public boolean requiresCorrectToolForDrops() {
            return this.requiresCorrectToolForDrops;
        }

        public boolean shouldSpawnTerrainParticles() {
            return this.spawnTerrainParticles;
        }

        public NoteBlockInstrument instrument() {
            return this.instrument;
        }

        private static final class Cache {

            private static final Direction[] DIRECTIONS = Direction.values();
            private static final int SUPPORT_TYPE_COUNT = SupportType.values().length;
            protected final boolean solidRender;
            final boolean propagatesSkylightDown;
            final int lightBlock;
            @Nullable
            final VoxelShape[] occlusionShapes;
            protected final VoxelShape collisionShape;
            protected final boolean largeCollisionShape;
            private final boolean[] faceSturdy;
            protected final boolean isCollisionShapeFullBlock;

            Cache(BlockState state) {
                Block block = state.getBlock();

                this.solidRender = state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                this.propagatesSkylightDown = block.propagatesSkylightDown(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                this.lightBlock = block.getLightBlock(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                int i;

                if (!state.canOcclude()) {
                    this.occlusionShapes = null;
                } else {
                    this.occlusionShapes = new VoxelShape[BlockBehaviour.BlockStateBase.Cache.DIRECTIONS.length];
                    VoxelShape voxelshape = block.getOcclusionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    Direction[] aenumdirection = BlockBehaviour.BlockStateBase.Cache.DIRECTIONS;

                    i = aenumdirection.length;

                    for (int j = 0; j < i; ++j) {
                        Direction enumdirection = aenumdirection[j];

                        this.occlusionShapes[enumdirection.ordinal()] = Shapes.getFaceShape(voxelshape, enumdirection);
                    }
                }

                this.collisionShape = block.getCollisionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
                if (!this.collisionShape.isEmpty() && state.hasOffsetFunction()) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "%s has a collision shape and an offset type, but is not marked as dynamicShape in its properties.", BuiltInRegistries.BLOCK.getKey(block)));
                } else {
                    this.largeCollisionShape = Arrays.stream(Direction.Axis.values()).anyMatch((enumdirection_enumaxis) -> {
                        return this.collisionShape.min(enumdirection_enumaxis) < 0.0D || this.collisionShape.max(enumdirection_enumaxis) > 1.0D;
                    });
                    this.faceSturdy = new boolean[BlockBehaviour.BlockStateBase.Cache.DIRECTIONS.length * BlockBehaviour.BlockStateBase.Cache.SUPPORT_TYPE_COUNT];
                    Direction[] aenumdirection1 = BlockBehaviour.BlockStateBase.Cache.DIRECTIONS;
                    int k = aenumdirection1.length;

                    for (i = 0; i < k; ++i) {
                        Direction enumdirection1 = aenumdirection1[i];
                        SupportType[] aenumblocksupport = SupportType.values();
                        int l = aenumblocksupport.length;

                        for (int i1 = 0; i1 < l; ++i1) {
                            SupportType enumblocksupport = aenumblocksupport[i1];

                            this.faceSturdy[Cache.getFaceSupportIndex(enumdirection1, enumblocksupport)] = enumblocksupport.isSupporting(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, enumdirection1);
                        }
                    }

                    this.isCollisionShapeFullBlock = Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                }
            }

            public boolean isFaceSturdy(Direction direction, SupportType shapeType) {
                return this.faceSturdy[Cache.getFaceSupportIndex(direction, shapeType)];
            }

            private static int getFaceSupportIndex(Direction direction, SupportType shapeType) {
                return direction.ordinal() * BlockBehaviour.BlockStateBase.Cache.SUPPORT_TYPE_COUNT + shapeType.ordinal();
            }
        }
    }

    public static enum OffsetType {

        NONE, XZ, XYZ;

        private OffsetType() {}
    }
}
