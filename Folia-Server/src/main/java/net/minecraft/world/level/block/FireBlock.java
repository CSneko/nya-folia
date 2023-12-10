package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
// CraftBukkit end

public class FireBlock extends BaseFireBlock {

    public static final int MAX_AGE = 15;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != Direction.DOWN;
    }).collect(Util.toMap());
    private static final VoxelShape UP_AABB = Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape WEST_AABB = Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_AABB = Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);
    private final Map<BlockState, VoxelShape> shapesCache;
    private static final int IGNITE_INSTANT = 60;
    private static final int IGNITE_EASY = 30;
    private static final int IGNITE_MEDIUM = 15;
    private static final int IGNITE_HARD = 5;
    private static final int BURN_INSTANT = 100;
    private static final int BURN_EASY = 60;
    private static final int BURN_MEDIUM = 20;
    private static final int BURN_HARD = 5;
    public final Object2IntMap<Block> igniteOdds = new Object2IntOpenHashMap();
    private final Object2IntMap<Block> burnOdds = new Object2IntOpenHashMap();

    public FireBlock(BlockBehaviour.Properties settings) {
        super(settings, 1.0F);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(FireBlock.AGE, 0)).setValue(FireBlock.NORTH, false)).setValue(FireBlock.EAST, false)).setValue(FireBlock.SOUTH, false)).setValue(FireBlock.WEST, false)).setValue(FireBlock.UP, false));
        this.shapesCache = ImmutableMap.copyOf((Map) this.stateDefinition.getPossibleStates().stream().filter((iblockdata) -> {
            return (Integer) iblockdata.getValue(FireBlock.AGE) == 0;
        }).collect(Collectors.toMap(Function.identity(), FireBlock::calculateShape)));
    }

    private static VoxelShape calculateShape(BlockState state) {
        VoxelShape voxelshape = Shapes.empty();

        if ((Boolean) state.getValue(FireBlock.UP)) {
            voxelshape = FireBlock.UP_AABB;
        }

        if ((Boolean) state.getValue(FireBlock.NORTH)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.NORTH_AABB);
        }

        if ((Boolean) state.getValue(FireBlock.SOUTH)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.SOUTH_AABB);
        }

        if ((Boolean) state.getValue(FireBlock.EAST)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.EAST_AABB);
        }

        if ((Boolean) state.getValue(FireBlock.WEST)) {
            voxelshape = Shapes.or(voxelshape, FireBlock.WEST_AABB);
        }

        return voxelshape.isEmpty() ? FireBlock.DOWN_AABB : voxelshape;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // CraftBukkit start
        if (!(world instanceof ServerLevel)) return this.canSurvive(state, world, pos) ? (BlockState) this.getStateWithAge(world, pos, (Integer) state.getValue(FireBlock.AGE)) : Blocks.AIR.defaultBlockState(); // Paper - don't fire events in world generation
        if (!this.canSurvive(state, world, pos)) {
            // Suppress during worldgen
            if (!(world instanceof Level)) {
                return Blocks.AIR.defaultBlockState();
            }
            CraftBlockState blockState = CraftBlockStates.getBlockState(world, pos);
            blockState.setData(Blocks.AIR.defaultBlockState());

            BlockFadeEvent event = new BlockFadeEvent(blockState.getBlock(), blockState);
            ((Level) world).getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }
        return this.getStateWithAge(world, pos, (Integer) state.getValue(FireBlock.AGE)); // Paper - diff on change, see "don't fire events in world generation"
        // CraftBukkit end
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapesCache.get(state.setValue(FireBlock.AGE, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.getStateForPlacement(ctx.getLevel(), ctx.getClickedPos());
    }

    protected BlockState getStateForPlacement(BlockGetter world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();
        BlockState iblockdata = world.getBlockState(blockposition1);

        if (!this.canBurn(iblockdata) && !iblockdata.isFaceSturdy(world, blockposition1, Direction.UP)) {
            BlockState iblockdata1 = this.defaultBlockState();
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];
                BooleanProperty blockstateboolean = (BooleanProperty) FireBlock.PROPERTY_BY_DIRECTION.get(enumdirection);

                if (blockstateboolean != null) {
                    iblockdata1 = (BlockState) iblockdata1.setValue(blockstateboolean, this.canBurn(world.getBlockState(pos.relative(enumdirection))));
                }
            }

            return iblockdata1;
        } else {
            return this.defaultBlockState();
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();

        return world.getBlockState(blockposition1).isFaceSturdy(world, blockposition1, Direction.UP) || this.isValidFireLocation(world, pos);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        world.scheduleTick(pos, (Block) this, FireBlock.getFireTickDelay(world)); // Paper
        if (world.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            if (!state.canSurvive(world, pos)) {
                this.fireExtinguished(world, pos); // CraftBukkit - invalid place location
            }

            BlockState iblockdata1 = world.getBlockState(pos.below());
            boolean flag = iblockdata1.is(world.dimensionType().infiniburn());
            int i = (Integer) state.getValue(FireBlock.AGE);

            if (!flag && world.isRaining() && this.isNearRain(world, pos) && random.nextFloat() < 0.2F + (float) i * 0.03F) {
                this.fireExtinguished(world, pos); // CraftBukkit - extinguished by rain
            } else {
                int j = Math.min(15, i + random.nextInt(3) / 2);

                if (i != j) {
                    state = (BlockState) state.setValue(FireBlock.AGE, j);
                    world.setBlock(pos, state, 4);
                }

                if (!flag) {
                    if (!this.isValidFireLocation(world, pos)) {
                        BlockPos blockposition1 = pos.below();

                        if (!world.getBlockState(blockposition1).isFaceSturdy(world, blockposition1, Direction.UP) || i > 3) {
                            this.fireExtinguished(world, pos); // CraftBukkit
                        }

                        return;
                    }

                    if (i == 15 && random.nextInt(4) == 0 && !this.canBurn(world.getBlockState(pos.below()))) {
                        this.fireExtinguished(world, pos); // CraftBukkit
                        return;
                    }
                }

                boolean flag1 = world.getBiome(pos).is(BiomeTags.INCREASED_FIRE_BURNOUT);
                int k = flag1 ? -50 : 0;

                // CraftBukkit start - add source blockposition to burn calls
                this.trySpread(world, pos.east(), 300 + k, random, i, pos);
                this.trySpread(world, pos.west(), 300 + k, random, i, pos);
                this.trySpread(world, pos.below(), 250 + k, random, i, pos);
                this.trySpread(world, pos.above(), 250 + k, random, i, pos);
                this.trySpread(world, pos.north(), 300 + k, random, i, pos);
                this.trySpread(world, pos.south(), 300 + k, random, i, pos);
                // CraftBukkit end
                BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

                for (int l = -1; l <= 1; ++l) {
                    for (int i1 = -1; i1 <= 1; ++i1) {
                        for (int j1 = -1; j1 <= 4; ++j1) {
                            if (l != 0 || j1 != 0 || i1 != 0) {
                                int k1 = 100;

                                if (j1 > 1) {
                                    k1 += (j1 - 1) * 100;
                                }

                                blockposition_mutableblockposition.setWithOffset(pos, l, j1, i1);
                                int l1 = this.getIgniteOdds(world, blockposition_mutableblockposition);

                                if (l1 > 0) {
                                    int i2 = (l1 + 40 + world.getDifficulty().getId() * 7) / (i + 30);

                                    if (flag1) {
                                        i2 /= 2;
                                    }

                                    if (i2 > 0 && random.nextInt(k1) <= i2 && (!world.isRaining() || !this.isNearRain(world, blockposition_mutableblockposition))) {
                                        int j2 = Math.min(15, i + random.nextInt(5) / 4);

                                        // CraftBukkit start - Call to stop spread of fire
                                        if (world.getBlockState(blockposition_mutableblockposition).getBlock() != Blocks.FIRE) {
                                            if (CraftEventFactory.callBlockIgniteEvent(world, blockposition_mutableblockposition, pos).isCancelled()) {
                                                continue;
                                            }

                                            CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition_mutableblockposition, this.getStateWithAge(world, blockposition_mutableblockposition, j2), 3); // CraftBukkit
                                        }
                                        // CraftBukkit end
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    protected boolean isNearRain(Level world, BlockPos pos) {
        return world.isRainingAt(pos) || world.isRainingAt(pos.west()) || world.isRainingAt(pos.east()) || world.isRainingAt(pos.north()) || world.isRainingAt(pos.south());
    }

    private int getBurnOdds(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) state.getValue(BlockStateProperties.WATERLOGGED) ? 0 : this.burnOdds.getInt(state.getBlock());
    }

    private int getIgniteOdds(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean) state.getValue(BlockStateProperties.WATERLOGGED) ? 0 : this.igniteOdds.getInt(state.getBlock());
    }

    private void trySpread(Level world, BlockPos blockposition, int i, RandomSource randomsource, int j, BlockPos sourceposition) { // CraftBukkit add sourceposition
        int k = this.getBurnOdds(world.getBlockState(blockposition));

        if (randomsource.nextInt(i) < k) {
            BlockState iblockdata = world.getBlockState(blockposition);

            // CraftBukkit start
            org.bukkit.block.Block theBlock = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            org.bukkit.block.Block sourceBlock = world.getWorld().getBlockAt(sourceposition.getX(), sourceposition.getY(), sourceposition.getZ());

            BlockBurnEvent event = new BlockBurnEvent(theBlock, sourceBlock);
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            if (iblockdata.getBlock() instanceof TntBlock && !CraftEventFactory.callTNTPrimeEvent(world, blockposition, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.FIRE, null, sourceposition)) {
                return;
            }
            // CraftBukkit end

            if (randomsource.nextInt(j + 10) < 5 && !world.isRainingAt(blockposition)) {
                int l = Math.min(j + randomsource.nextInt(5) / 4, 15);

                world.setBlock(blockposition, this.getStateWithAge(world, blockposition, l), 3);
            } else {
                if(iblockdata.getBlock() != Blocks.TNT) world.removeBlock(blockposition, false); // Paper - TNTPrimeEvent - We might be cancelling it below, move the setAir down
            }

            Block block = iblockdata.getBlock();

            if (block instanceof TntBlock) {
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = io.papermc.paper.util.MCUtil.toBukkitBlock(world, blockposition);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.FIRE, null).callEvent()) {
                    return;
                }
                world.removeBlock(blockposition, false);
                // Paper end
                TntBlock.explode(world, blockposition);
            }
        }

    }

    private BlockState getStateWithAge(LevelAccessor world, BlockPos pos, int age) {
        BlockState iblockdata = getState(world, pos);

        return iblockdata.is(Blocks.FIRE) ? (BlockState) iblockdata.setValue(FireBlock.AGE, age) : iblockdata;
    }

    private boolean isValidFireLocation(BlockGetter world, BlockPos pos) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (this.canBurn(world.getBlockState(pos.relative(enumdirection)))) {
                return true;
            }
        }

        return false;
    }

    private int getIgniteOdds(LevelReader world, BlockPos pos) {
        if (!world.isEmptyBlock(pos)) {
            return 0;
        } else {
            int i = 0;
            Direction[] aenumdirection = Direction.values();
            int j = aenumdirection.length;

            for (int k = 0; k < j; ++k) {
                Direction enumdirection = aenumdirection[k];
                BlockState iblockdata = world.getBlockState(pos.relative(enumdirection));

                i = Math.max(this.getIgniteOdds(iblockdata), i);
            }

            return i;
        }
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return this.getIgniteOdds(state) > 0;
    }

    @Override
    // Paper start - ItemActionContext param
    public void onPlace(BlockState iblockdata, Level world, BlockPos blockposition, BlockState iblockdata1, boolean flag, UseOnContext itemActionContext) {
        super.onPlace(iblockdata, world, blockposition, iblockdata1, flag, itemActionContext);
        // Paper end
        world.scheduleTick(blockposition, this, getFireTickDelay(world)); // Paper
    }

    // Paper start - customisable fire tick delay
    private static int getFireTickDelay(Level world) {
        return world.paperConfig().environment.fireTickDelay + world.random.nextInt(10);
    // Paper end
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FireBlock.AGE, FireBlock.NORTH, FireBlock.EAST, FireBlock.SOUTH, FireBlock.WEST, FireBlock.UP);
    }

    private void setFlammable(Block block, int burnChance, int spreadChance) {
        this.igniteOdds.put(block, burnChance);
        this.burnOdds.put(block, spreadChance);
    }

    public static void bootStrap() {
        FireBlock blockfire = (FireBlock) Blocks.FIRE;

        blockfire.setFlammable(Blocks.OAK_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.CHERRY_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.MANGROVE_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_PLANKS, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_MOSAIC, 5, 20);
        blockfire.setFlammable(Blocks.OAK_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.CHERRY_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.MANGROVE_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_MOSAIC_SLAB, 5, 20);
        blockfire.setFlammable(Blocks.OAK_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.CHERRY_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.MANGROVE_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_FENCE_GATE, 5, 20);
        blockfire.setFlammable(Blocks.OAK_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.CHERRY_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.MANGROVE_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_FENCE, 5, 20);
        blockfire.setFlammable(Blocks.OAK_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.BIRCH_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.SPRUCE_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.JUNGLE_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.ACACIA_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.CHERRY_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.DARK_OAK_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.MANGROVE_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.BAMBOO_MOSAIC_STAIRS, 5, 20);
        blockfire.setFlammable(Blocks.OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.SPRUCE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.BIRCH_LOG, 5, 5);
        blockfire.setFlammable(Blocks.JUNGLE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.ACACIA_LOG, 5, 5);
        blockfire.setFlammable(Blocks.CHERRY_LOG, 5, 5);
        blockfire.setFlammable(Blocks.DARK_OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.MANGROVE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.BAMBOO_BLOCK, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_SPRUCE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_BIRCH_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_JUNGLE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_ACACIA_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_CHERRY_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_DARK_OAK_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_MANGROVE_LOG, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_BAMBOO_BLOCK, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_SPRUCE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_BIRCH_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_JUNGLE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_ACACIA_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_CHERRY_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_DARK_OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.STRIPPED_MANGROVE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.SPRUCE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.BIRCH_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.JUNGLE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.ACACIA_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.CHERRY_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.DARK_OAK_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.MANGROVE_WOOD, 5, 5);
        blockfire.setFlammable(Blocks.MANGROVE_ROOTS, 5, 20);
        blockfire.setFlammable(Blocks.OAK_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.SPRUCE_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.BIRCH_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.JUNGLE_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.ACACIA_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.CHERRY_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.DARK_OAK_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.MANGROVE_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.BOOKSHELF, 30, 20);
        blockfire.setFlammable(Blocks.TNT, 15, 100);
        blockfire.setFlammable(Blocks.GRASS, 60, 100);
        blockfire.setFlammable(Blocks.FERN, 60, 100);
        blockfire.setFlammable(Blocks.DEAD_BUSH, 60, 100);
        blockfire.setFlammable(Blocks.SUNFLOWER, 60, 100);
        blockfire.setFlammable(Blocks.LILAC, 60, 100);
        blockfire.setFlammable(Blocks.ROSE_BUSH, 60, 100);
        blockfire.setFlammable(Blocks.PEONY, 60, 100);
        blockfire.setFlammable(Blocks.TALL_GRASS, 60, 100);
        blockfire.setFlammable(Blocks.LARGE_FERN, 60, 100);
        blockfire.setFlammable(Blocks.DANDELION, 60, 100);
        blockfire.setFlammable(Blocks.POPPY, 60, 100);
        blockfire.setFlammable(Blocks.BLUE_ORCHID, 60, 100);
        blockfire.setFlammable(Blocks.ALLIUM, 60, 100);
        blockfire.setFlammable(Blocks.AZURE_BLUET, 60, 100);
        blockfire.setFlammable(Blocks.RED_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.ORANGE_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.WHITE_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.PINK_TULIP, 60, 100);
        blockfire.setFlammable(Blocks.OXEYE_DAISY, 60, 100);
        blockfire.setFlammable(Blocks.CORNFLOWER, 60, 100);
        blockfire.setFlammable(Blocks.LILY_OF_THE_VALLEY, 60, 100);
        blockfire.setFlammable(Blocks.TORCHFLOWER, 60, 100);
        blockfire.setFlammable(Blocks.PITCHER_PLANT, 60, 100);
        blockfire.setFlammable(Blocks.WITHER_ROSE, 60, 100);
        blockfire.setFlammable(Blocks.PINK_PETALS, 60, 100);
        blockfire.setFlammable(Blocks.WHITE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.ORANGE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.MAGENTA_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.LIGHT_BLUE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.YELLOW_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.LIME_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.PINK_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.GRAY_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.LIGHT_GRAY_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.CYAN_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.PURPLE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.BLUE_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.BROWN_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.GREEN_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.RED_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.BLACK_WOOL, 30, 60);
        blockfire.setFlammable(Blocks.VINE, 15, 100);
        blockfire.setFlammable(Blocks.COAL_BLOCK, 5, 5);
        blockfire.setFlammable(Blocks.HAY_BLOCK, 60, 20);
        blockfire.setFlammable(Blocks.TARGET, 15, 20);
        blockfire.setFlammable(Blocks.WHITE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.ORANGE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.MAGENTA_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.LIGHT_BLUE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.YELLOW_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.LIME_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.PINK_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.GRAY_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.LIGHT_GRAY_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.CYAN_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.PURPLE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.BLUE_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.BROWN_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.GREEN_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.RED_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.BLACK_CARPET, 60, 20);
        blockfire.setFlammable(Blocks.DRIED_KELP_BLOCK, 30, 60);
        blockfire.setFlammable(Blocks.BAMBOO, 60, 60);
        blockfire.setFlammable(Blocks.SCAFFOLDING, 60, 60);
        blockfire.setFlammable(Blocks.LECTERN, 30, 20);
        blockfire.setFlammable(Blocks.COMPOSTER, 5, 20);
        blockfire.setFlammable(Blocks.SWEET_BERRY_BUSH, 60, 100);
        blockfire.setFlammable(Blocks.BEEHIVE, 5, 20);
        blockfire.setFlammable(Blocks.BEE_NEST, 30, 20);
        blockfire.setFlammable(Blocks.AZALEA_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.FLOWERING_AZALEA_LEAVES, 30, 60);
        blockfire.setFlammable(Blocks.CAVE_VINES, 15, 60);
        blockfire.setFlammable(Blocks.CAVE_VINES_PLANT, 15, 60);
        blockfire.setFlammable(Blocks.SPORE_BLOSSOM, 60, 100);
        blockfire.setFlammable(Blocks.AZALEA, 30, 60);
        blockfire.setFlammable(Blocks.FLOWERING_AZALEA, 30, 60);
        blockfire.setFlammable(Blocks.BIG_DRIPLEAF, 60, 100);
        blockfire.setFlammable(Blocks.BIG_DRIPLEAF_STEM, 60, 100);
        blockfire.setFlammable(Blocks.SMALL_DRIPLEAF, 60, 100);
        blockfire.setFlammable(Blocks.HANGING_ROOTS, 30, 60);
        blockfire.setFlammable(Blocks.GLOW_LICHEN, 15, 100);
    }
}
