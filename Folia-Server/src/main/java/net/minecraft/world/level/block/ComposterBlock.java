package net.minecraft.world.level.block;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftBlockInventoryHolder;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
// CraftBukkit end

public class ComposterBlock extends Block implements WorldlyContainerHolder {

    public static final int READY = 8;
    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 7;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_COMPOSTER;
    public static final Object2FloatMap<ItemLike> COMPOSTABLES = new Object2FloatOpenHashMap();
    private static final int AABB_SIDE_THICKNESS = 2;
    private static final VoxelShape OUTER_SHAPE = Shapes.block();
    private static final VoxelShape[] SHAPES = (VoxelShape[]) Util.make(new VoxelShape[9], (avoxelshape) -> {
        for (int i = 0; i < 8; ++i) {
            avoxelshape[i] = Shapes.join(ComposterBlock.OUTER_SHAPE, Block.box(2.0D, (double) Math.max(2, 1 + i * 2), 2.0D, 14.0D, 16.0D, 14.0D), BooleanOp.ONLY_FIRST);
        }

        avoxelshape[8] = avoxelshape[7];
    });

    public static void bootStrap() {
        ComposterBlock.COMPOSTABLES.defaultReturnValue(-1.0F);
        float f = 0.3F;
        float f1 = 0.5F;
        float f2 = 0.65F;
        float f3 = 0.85F;
        float f4 = 1.0F;

        ComposterBlock.add(0.3F, Items.JUNGLE_LEAVES);
        ComposterBlock.add(0.3F, Items.OAK_LEAVES);
        ComposterBlock.add(0.3F, Items.SPRUCE_LEAVES);
        ComposterBlock.add(0.3F, Items.DARK_OAK_LEAVES);
        ComposterBlock.add(0.3F, Items.ACACIA_LEAVES);
        ComposterBlock.add(0.3F, Items.CHERRY_LEAVES);
        ComposterBlock.add(0.3F, Items.BIRCH_LEAVES);
        ComposterBlock.add(0.3F, Items.AZALEA_LEAVES);
        ComposterBlock.add(0.3F, Items.MANGROVE_LEAVES);
        ComposterBlock.add(0.3F, Items.OAK_SAPLING);
        ComposterBlock.add(0.3F, Items.SPRUCE_SAPLING);
        ComposterBlock.add(0.3F, Items.BIRCH_SAPLING);
        ComposterBlock.add(0.3F, Items.JUNGLE_SAPLING);
        ComposterBlock.add(0.3F, Items.ACACIA_SAPLING);
        ComposterBlock.add(0.3F, Items.CHERRY_SAPLING);
        ComposterBlock.add(0.3F, Items.DARK_OAK_SAPLING);
        ComposterBlock.add(0.3F, Items.MANGROVE_PROPAGULE);
        ComposterBlock.add(0.3F, Items.BEETROOT_SEEDS);
        ComposterBlock.add(0.3F, Items.DRIED_KELP);
        ComposterBlock.add(0.3F, Items.GRASS);
        ComposterBlock.add(0.3F, Items.KELP);
        ComposterBlock.add(0.3F, Items.MELON_SEEDS);
        ComposterBlock.add(0.3F, Items.PUMPKIN_SEEDS);
        ComposterBlock.add(0.3F, Items.SEAGRASS);
        ComposterBlock.add(0.3F, Items.SWEET_BERRIES);
        ComposterBlock.add(0.3F, Items.GLOW_BERRIES);
        ComposterBlock.add(0.3F, Items.WHEAT_SEEDS);
        ComposterBlock.add(0.3F, Items.MOSS_CARPET);
        ComposterBlock.add(0.3F, Items.PINK_PETALS);
        ComposterBlock.add(0.3F, Items.SMALL_DRIPLEAF);
        ComposterBlock.add(0.3F, Items.HANGING_ROOTS);
        ComposterBlock.add(0.3F, Items.MANGROVE_ROOTS);
        ComposterBlock.add(0.3F, Items.TORCHFLOWER_SEEDS);
        ComposterBlock.add(0.3F, Items.PITCHER_POD);
        ComposterBlock.add(0.5F, Items.DRIED_KELP_BLOCK);
        ComposterBlock.add(0.5F, Items.TALL_GRASS);
        ComposterBlock.add(0.5F, Items.FLOWERING_AZALEA_LEAVES);
        ComposterBlock.add(0.5F, Items.CACTUS);
        ComposterBlock.add(0.5F, Items.SUGAR_CANE);
        ComposterBlock.add(0.5F, Items.VINE);
        ComposterBlock.add(0.5F, Items.NETHER_SPROUTS);
        ComposterBlock.add(0.5F, Items.WEEPING_VINES);
        ComposterBlock.add(0.5F, Items.TWISTING_VINES);
        ComposterBlock.add(0.5F, Items.MELON_SLICE);
        ComposterBlock.add(0.5F, Items.GLOW_LICHEN);
        ComposterBlock.add(0.65F, Items.SEA_PICKLE);
        ComposterBlock.add(0.65F, Items.LILY_PAD);
        ComposterBlock.add(0.65F, Items.PUMPKIN);
        ComposterBlock.add(0.65F, Items.CARVED_PUMPKIN);
        ComposterBlock.add(0.65F, Items.MELON);
        ComposterBlock.add(0.65F, Items.APPLE);
        ComposterBlock.add(0.65F, Items.BEETROOT);
        ComposterBlock.add(0.65F, Items.CARROT);
        ComposterBlock.add(0.65F, Items.COCOA_BEANS);
        ComposterBlock.add(0.65F, Items.POTATO);
        ComposterBlock.add(0.65F, Items.WHEAT);
        ComposterBlock.add(0.65F, Items.BROWN_MUSHROOM);
        ComposterBlock.add(0.65F, Items.RED_MUSHROOM);
        ComposterBlock.add(0.65F, Items.MUSHROOM_STEM);
        ComposterBlock.add(0.65F, Items.CRIMSON_FUNGUS);
        ComposterBlock.add(0.65F, Items.WARPED_FUNGUS);
        ComposterBlock.add(0.65F, Items.NETHER_WART);
        ComposterBlock.add(0.65F, Items.CRIMSON_ROOTS);
        ComposterBlock.add(0.65F, Items.WARPED_ROOTS);
        ComposterBlock.add(0.65F, Items.SHROOMLIGHT);
        ComposterBlock.add(0.65F, Items.DANDELION);
        ComposterBlock.add(0.65F, Items.POPPY);
        ComposterBlock.add(0.65F, Items.BLUE_ORCHID);
        ComposterBlock.add(0.65F, Items.ALLIUM);
        ComposterBlock.add(0.65F, Items.AZURE_BLUET);
        ComposterBlock.add(0.65F, Items.RED_TULIP);
        ComposterBlock.add(0.65F, Items.ORANGE_TULIP);
        ComposterBlock.add(0.65F, Items.WHITE_TULIP);
        ComposterBlock.add(0.65F, Items.PINK_TULIP);
        ComposterBlock.add(0.65F, Items.OXEYE_DAISY);
        ComposterBlock.add(0.65F, Items.CORNFLOWER);
        ComposterBlock.add(0.65F, Items.LILY_OF_THE_VALLEY);
        ComposterBlock.add(0.65F, Items.WITHER_ROSE);
        ComposterBlock.add(0.65F, Items.FERN);
        ComposterBlock.add(0.65F, Items.SUNFLOWER);
        ComposterBlock.add(0.65F, Items.LILAC);
        ComposterBlock.add(0.65F, Items.ROSE_BUSH);
        ComposterBlock.add(0.65F, Items.PEONY);
        ComposterBlock.add(0.65F, Items.LARGE_FERN);
        ComposterBlock.add(0.65F, Items.SPORE_BLOSSOM);
        ComposterBlock.add(0.65F, Items.AZALEA);
        ComposterBlock.add(0.65F, Items.MOSS_BLOCK);
        ComposterBlock.add(0.65F, Items.BIG_DRIPLEAF);
        ComposterBlock.add(0.85F, Items.HAY_BLOCK);
        ComposterBlock.add(0.85F, Items.BROWN_MUSHROOM_BLOCK);
        ComposterBlock.add(0.85F, Items.RED_MUSHROOM_BLOCK);
        ComposterBlock.add(0.85F, Items.NETHER_WART_BLOCK);
        ComposterBlock.add(0.85F, Items.WARPED_WART_BLOCK);
        ComposterBlock.add(0.85F, Items.FLOWERING_AZALEA);
        ComposterBlock.add(0.85F, Items.BREAD);
        ComposterBlock.add(0.85F, Items.BAKED_POTATO);
        ComposterBlock.add(0.85F, Items.COOKIE);
        ComposterBlock.add(0.85F, Items.TORCHFLOWER);
        ComposterBlock.add(0.85F, Items.PITCHER_PLANT);
        ComposterBlock.add(1.0F, Items.CAKE);
        ComposterBlock.add(1.0F, Items.PUMPKIN_PIE);
    }

    private static void add(float levelIncreaseChance, ItemLike item) {
        ComposterBlock.COMPOSTABLES.put(item.asItem(), levelIncreaseChance);
    }

    public ComposterBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ComposterBlock.LEVEL, 0));
    }

    public static void handleFill(Level world, BlockPos pos, boolean fill) {
        BlockState iblockdata = world.getBlockState(pos);

        world.playLocalSound(pos, fill ? SoundEvents.COMPOSTER_FILL_SUCCESS : SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        double d0 = iblockdata.getShape(world, pos).max(Direction.Axis.Y, 0.5D, 0.5D) + 0.03125D;
        double d1 = 0.13124999403953552D;
        double d2 = 0.737500011920929D;
        RandomSource randomsource = world.getRandom();

        for (int i = 0; i < 10; ++i) {
            double d3 = randomsource.nextGaussian() * 0.02D;
            double d4 = randomsource.nextGaussian() * 0.02D;
            double d5 = randomsource.nextGaussian() * 0.02D;

            world.addParticle(ParticleTypes.COMPOSTER, (double) pos.getX() + 0.13124999403953552D + 0.737500011920929D * (double) randomsource.nextFloat(), (double) pos.getY() + d0 + (double) randomsource.nextFloat() * (1.0D - d0), (double) pos.getZ() + 0.13124999403953552D + 0.737500011920929D * (double) randomsource.nextFloat(), d3, d4, d5);
        }

    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return ComposterBlock.SHAPES[(Integer) state.getValue(ComposterBlock.LEVEL)];
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return ComposterBlock.OUTER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return ComposterBlock.SHAPES[0];
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if ((Integer) state.getValue(ComposterBlock.LEVEL) == 7) {
            world.scheduleTick(pos, state.getBlock(), 20);
        }

    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int i = (Integer) state.getValue(ComposterBlock.LEVEL);
        ItemStack itemstack = player.getItemInHand(hand);

        if (i < 8 && ComposterBlock.COMPOSTABLES.containsKey(itemstack.getItem())) {
            if (i < 7 && !world.isClientSide) {
                BlockState iblockdata1 = ComposterBlock.addItem(player, state, world, pos, itemstack);
                // Paper start - handle cancelled events
                if (iblockdata1 == null) {
                    return InteractionResult.PASS;
                }
                // Paper end

                world.levelEvent(1500, pos, state != iblockdata1 ? 1 : 0);
                player.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else if (i == 8) {
            ComposterBlock.extractProduce(player, state, world, pos);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static BlockState insertItem(Entity user, BlockState state, ServerLevel world, ItemStack stack, BlockPos pos) {
        int i = (Integer) state.getValue(ComposterBlock.LEVEL);

        if (i < 7 && ComposterBlock.COMPOSTABLES.containsKey(stack.getItem())) {
            // CraftBukkit start
            double rand = world.getRandom().nextDouble();
            BlockState iblockdata1 = null; // Paper
            if (false && (state == iblockdata1 || !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(user, pos, iblockdata1))) { // Paper - move event call into addItem
                return state;
            }
            iblockdata1 = ComposterBlock.addItem(user, state, world, pos, stack, rand);
            // Paper start - handle cancelled events
            if (iblockdata1 == null) {
                return state;
            }
            // Paper end
            // CraftBukkit end

            stack.shrink(1);
            return iblockdata1;
        } else {
            return state;
        }
    }

    public static BlockState extractProduce(Entity user, BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        if (user != null && !(user instanceof Player)) {
            BlockState iblockdata1 = ComposterBlock.empty(user, state, DummyGeneratorAccess.INSTANCE, pos);
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(user, pos, iblockdata1)) {
                return state;
            }
        }
        // CraftBukkit end
        if (!world.isClientSide) {
            Vec3 vec3d = Vec3.atLowerCornerWithOffset(pos, 0.5D, 1.01D, 0.5D).offsetRandom(world.random, 0.7F);
            ItemEntity entityitem = new ItemEntity(world, vec3d.x(), vec3d.y(), vec3d.z(), new ItemStack(Items.BONE_MEAL));

            entityitem.setDefaultPickUpDelay();
            world.addFreshEntity(entityitem);
        }

        BlockState iblockdata1 = ComposterBlock.empty(user, state, world, pos);

        world.playSound((Player) null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return iblockdata1;
    }

    static BlockState empty(@Nullable Entity user, BlockState state, LevelAccessor world, BlockPos pos) {
        BlockState iblockdata1 = (BlockState) state.setValue(ComposterBlock.LEVEL, 0);

        world.setBlock(pos, iblockdata1, 3);
        world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(user, iblockdata1));
        return iblockdata1;
    }

    @Nullable // Paper
    static BlockState addItem(@Nullable Entity user, BlockState state, LevelAccessor world, BlockPos pos, ItemStack stack) {
        // CraftBukkit start
        return ComposterBlock.addItem(user, state, world, pos, stack, world.getRandom().nextDouble());
    }

    @Nullable // Paper - make it nullable
    static BlockState addItem(@Nullable Entity entity, BlockState iblockdata, LevelAccessor generatoraccess, BlockPos blockposition, ItemStack itemstack, double rand) {
        // CraftBukkit end
        int i = (Integer) iblockdata.getValue(ComposterBlock.LEVEL);
        float f = ComposterBlock.COMPOSTABLES.getFloat(itemstack.getItem());

        // Paper start
        boolean willRaiseLevel = !((i != 0 || f <= 0.0F) && rand >= (double) f);
        final io.papermc.paper.event.block.CompostItemEvent event;
        if (entity == null) {
            event = new io.papermc.paper.event.block.CompostItemEvent(org.bukkit.craftbukkit.block.CraftBlock.at(generatoraccess, blockposition), itemstack.getBukkitStack(), willRaiseLevel);
        } else {
            event = new io.papermc.paper.event.entity.EntityCompostItemEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(generatoraccess, blockposition), itemstack.getBukkitStack(), willRaiseLevel);
        }
        if (!event.callEvent()) { // check for cancellation of entity event (non entity event can't be cancelled cause of hoppers)
            return null;
        }
        willRaiseLevel = event.willRaiseLevel();

        if (!willRaiseLevel) {
            // Paper end
            return iblockdata;
        } else {
            int j = i + 1;
            BlockState iblockdata1 = (BlockState) iblockdata.setValue(ComposterBlock.LEVEL, j);
            // Paper start - move the EntityChangeBlockEvent here to avoid conflict later for the compost events
            if (entity != null && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, blockposition, iblockdata1)) {
                return null;
            }
            // Paper end

            generatoraccess.setBlock(blockposition, iblockdata1, 3);
            generatoraccess.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entity, iblockdata1));
            if (j == 7) {
                generatoraccess.scheduleTick(blockposition, iblockdata.getBlock(), 20);
            }

            return iblockdata1;
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(ComposterBlock.LEVEL) == 7) {
            world.setBlock(pos, (BlockState) state.cycle(ComposterBlock.LEVEL), 3);
            world.playSound((Player) null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return (Integer) state.getValue(ComposterBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ComposterBlock.LEVEL);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor world, BlockPos pos) {
        int i = (Integer) state.getValue(ComposterBlock.LEVEL);

        // CraftBukkit - empty generatoraccess, blockposition
        return (WorldlyContainer) (i == 8 ? new ComposterBlock.OutputContainer(state, world, pos, new ItemStack(Items.BONE_MEAL)) : (i < 7 ? new ComposterBlock.InputContainer(state, world, pos) : new ComposterBlock.EmptyContainer(world, pos)));
    }

    public static class OutputContainer extends SimpleContainer implements WorldlyContainer {

        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public OutputContainer(BlockState state, LevelAccessor world, BlockPos pos, ItemStack outputItem) {
            super(outputItem);
            this.state = state;
            this.level = world;
            this.pos = pos;
            this.bukkitOwner = new CraftBlockInventoryHolder(world, pos, this); // CraftBukkit
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.DOWN ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
            return !this.changed && dir == Direction.DOWN && stack.is(Items.BONE_MEAL);
        }

        @Override
        public void setChanged() {
            // CraftBukkit start - allow putting items back (eg cancelled InventoryMoveItemEvent)
            if (this.isEmpty()) {
            ComposterBlock.empty((Entity) null, this.state, this.level, this.pos);
            this.changed = true;
            } else {
                this.level.setBlock(this.pos, this.state, 3);
                this.changed = false;
            }
            // CraftBukkit end
        }
    }

    public static class InputContainer extends SimpleContainer implements WorldlyContainer {

        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public InputContainer(BlockState state, LevelAccessor world, BlockPos pos) {
            super(1);
            this.bukkitOwner = new CraftBlockInventoryHolder(world, pos, this); // CraftBukkit
            this.state = state;
            this.level = world;
            this.pos = pos;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
            return !this.changed && dir == Direction.UP && ComposterBlock.COMPOSTABLES.containsKey(stack.getItem());
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
            return false;
        }

        @Override
        public void setChanged() {
            ItemStack itemstack = this.getItem(0);

            if (!itemstack.isEmpty()) {
                this.changed = true;
                BlockState iblockdata = ComposterBlock.addItem((Entity) null, this.state, this.level, this.pos, itemstack);

                // Paper start
                if (iblockdata == null) {
                    return;
                }
                // Paper end
                this.level.levelEvent(1500, this.pos, iblockdata != this.state ? 1 : 0);
                this.removeItemNoUpdate(0);
            }

        }
    }

    public static class EmptyContainer extends SimpleContainer implements WorldlyContainer {

        public EmptyContainer(LevelAccessor generatoraccess, BlockPos blockposition) { // CraftBukkit
            super(0);
            this.bukkitOwner = new CraftBlockInventoryHolder(generatoraccess, blockposition, this); // CraftBukkit
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
            return false;
        }
    }
}
