package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

public class UpgradeData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final UpgradeData EMPTY = new UpgradeData(EmptyBlockGetter.INSTANCE);
    private static final String TAG_INDICES = "Indices";
    private static final Direction8[] DIRECTIONS = Direction8.values();
    private final EnumSet<Direction8> sides = EnumSet.noneOf(Direction8.class);
    private final List<SavedTick<Block>> neighborBlockTicks = Lists.newArrayList();
    private final List<SavedTick<Fluid>> neighborFluidTicks = Lists.newArrayList();
    private final int[][] index;
    static final Map<Block, UpgradeData.BlockFixer> MAP = new IdentityHashMap<>();
    static final Set<UpgradeData.BlockFixer> CHUNKY_FIXERS = Sets.newHashSet();

    private UpgradeData(LevelHeightAccessor world) {
        this.index = new int[world.getSectionsCount()][];
    }

    public UpgradeData(CompoundTag nbt, LevelHeightAccessor world) {
        this(world);
        if (nbt.contains("Indices", 10)) {
            CompoundTag compoundTag = nbt.getCompound("Indices");

            for(int i = 0; i < this.index.length; ++i) {
                String string = String.valueOf(i);
                if (compoundTag.contains(string, 11)) {
                    this.index[i] = compoundTag.getIntArray(string);
                }
            }
        }

        int j = nbt.getInt("Sides");

        for(Direction8 direction8 : Direction8.values()) {
            if ((j & 1 << direction8.ordinal()) != 0) {
                this.sides.add(direction8);
            }
        }

        loadTicks(nbt, "neighbor_block_ticks", (id) -> {
            return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(id)).or(() -> {
                return Optional.of(Blocks.AIR);
            });
        }, this.neighborBlockTicks);
        loadTicks(nbt, "neighbor_fluid_ticks", (id) -> {
            return BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(id)).or(() -> {
                return Optional.of(Fluids.EMPTY);
            });
        }, this.neighborFluidTicks);
    }

    private static <T> void loadTicks(CompoundTag nbt, String key, Function<String, Optional<T>> nameToType, List<SavedTick<T>> ticks) {
        if (nbt.contains(key, 9)) {
            for(Tag tag : nbt.getList(key, 10)) {
                SavedTick.loadTick((CompoundTag)tag, nameToType).ifPresent(ticks::add);
            }
        }

    }

    // Paper start - filter out relocated neighbour ticks
    private static <T> void filterTickList(int chunkX, int chunkZ, List<SavedTick<T>> ticks) {
        for (java.util.Iterator<SavedTick<T>> iterator = ticks.iterator(); iterator.hasNext();) {
            SavedTick<T> tick = iterator.next();
            BlockPos tickPos = tick.pos();
            int tickCX = tickPos.getX() >> 4;
            int tickCZ = tickPos.getZ() >> 4;

            int dist = Math.max(Math.abs(chunkX - tickCX), Math.abs(chunkZ - tickCZ));

            if (dist != 1) {
                LOGGER.warn("Neighbour tick '" + tick + "' serialized in chunk (" + chunkX + "," + chunkZ + ") is too far (" + tickCX + "," + tickCZ + ")");
                iterator.remove();
            }
        }
    }
    // Paper end - filter out relocated neighbour ticks

    public void upgrade(LevelChunk chunk) {
        this.upgradeInside(chunk);

        for(Direction8 direction8 : DIRECTIONS) {
            upgradeSides(chunk, direction8);
        }

        // Paper start - filter out relocated neighbour ticks
        filterTickList(chunk.locX, chunk.locZ, this.neighborBlockTicks);
        filterTickList(chunk.locX, chunk.locZ, this.neighborFluidTicks);
        // Paper end - filter out relocated neighbour ticks

        Level level = chunk.getLevel();
        this.neighborBlockTicks.forEach((tick) -> {
            Block block = tick.type() == Blocks.AIR ? level.getBlockState(tick.pos()).getBlock() : tick.type();
            level.scheduleTick(tick.pos(), block, tick.delay(), tick.priority());
        });
        this.neighborFluidTicks.forEach((tick) -> {
            Fluid fluid = tick.type() == Fluids.EMPTY ? level.getFluidState(tick.pos()).getType() : tick.type();
            level.scheduleTick(tick.pos(), fluid, tick.delay(), tick.priority());
        });
        UpgradeData.BlockFixers.values(); // Paper - force the class init so that we don't access CHUNKY_FIXERS before all BlockFixers are initialised
        CHUNKY_FIXERS.forEach((logic) -> {
            logic.processChunk(level);
        });
    }

    private static void upgradeSides(LevelChunk chunk, Direction8 side) {
        Level level = chunk.getLevel();
        if (chunk.getUpgradeData().sides.remove(side)) {
            Set<Direction> set = side.getDirections();
            int i = 0;
            int j = 15;
            boolean bl = set.contains(Direction.EAST);
            boolean bl2 = set.contains(Direction.WEST);
            boolean bl3 = set.contains(Direction.SOUTH);
            boolean bl4 = set.contains(Direction.NORTH);
            boolean bl5 = set.size() == 1;
            ChunkPos chunkPos = chunk.getPos();
            int k = chunkPos.getMinBlockX() + (!bl5 || !bl4 && !bl3 ? (bl2 ? 0 : 15) : 1);
            int l = chunkPos.getMinBlockX() + (!bl5 || !bl4 && !bl3 ? (bl2 ? 0 : 15) : 14);
            int m = chunkPos.getMinBlockZ() + (!bl5 || !bl && !bl2 ? (bl4 ? 0 : 15) : 1);
            int n = chunkPos.getMinBlockZ() + (!bl5 || !bl && !bl2 ? (bl4 ? 0 : 15) : 14);
            Direction[] directions = Direction.values();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for(BlockPos blockPos : BlockPos.betweenClosed(k, level.getMinBuildHeight(), m, l, level.getMaxBuildHeight() - 1, n)) {
                BlockState blockState = level.getBlockState(blockPos);
                BlockState blockState2 = blockState;

                for(Direction direction : directions) {
                    mutableBlockPos.setWithOffset(blockPos, direction);
                    blockState2 = updateState(blockState2, direction, level, blockPos, mutableBlockPos);
                }

                Block.updateOrDestroy(blockState, blockState2, level, blockPos, 18);
            }

        }
    }

    private static BlockState updateState(BlockState oldState, Direction dir, LevelAccessor world, BlockPos currentPos, BlockPos otherPos) {
        return MAP.getOrDefault(oldState.getBlock(), UpgradeData.BlockFixers.DEFAULT).updateShape(oldState, dir, world.getBlockState(otherPos), world, currentPos, otherPos);
    }

    private void upgradeInside(LevelChunk chunk) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();
        LevelAccessor levelAccessor = chunk.getLevel();

        for(int i = 0; i < this.index.length; ++i) {
            LevelChunkSection levelChunkSection = chunk.getSection(i);
            int[] is = this.index[i];
            this.index[i] = null;
            if (is != null && is.length > 0) {
                Direction[] directions = Direction.values();
                PalettedContainer<BlockState> palettedContainer = levelChunkSection.getStates();
                int j = chunk.getSectionYFromSectionIndex(i);
                int k = SectionPos.sectionToBlockCoord(j);

                for(int l : is) {
                    int m = l & 15;
                    int n = l >> 8 & 15;
                    int o = l >> 4 & 15;
                    mutableBlockPos.set(chunkPos.getMinBlockX() + m, k + n, chunkPos.getMinBlockZ() + o);
                    BlockState blockState = palettedContainer.get(l);
                    BlockState blockState2 = blockState;

                    for(Direction direction : directions) {
                        mutableBlockPos2.setWithOffset(mutableBlockPos, direction);
                        if (SectionPos.blockToSectionCoord(mutableBlockPos.getX()) == chunkPos.x && SectionPos.blockToSectionCoord(mutableBlockPos.getZ()) == chunkPos.z) {
                            blockState2 = updateState(blockState2, direction, levelAccessor, mutableBlockPos, mutableBlockPos2);
                        }
                    }

                    Block.updateOrDestroy(blockState, blockState2, levelAccessor, mutableBlockPos, 18);
                }
            }
        }

        for(int p = 0; p < this.index.length; ++p) {
            if (this.index[p] != null) {
                LOGGER.warn("Discarding update data for section {} for chunk ({} {})", levelAccessor.getSectionYFromSectionIndex(p), chunkPos.x, chunkPos.z);
            }

            this.index[p] = null;
        }

    }

    public boolean isEmpty() {
        for(int[] is : this.index) {
            if (is != null) {
                return false;
            }
        }

        return this.sides.isEmpty();
    }

    public CompoundTag write() {
        CompoundTag compoundTag = new CompoundTag();
        CompoundTag compoundTag2 = new CompoundTag();

        for(int i = 0; i < this.index.length; ++i) {
            String string = String.valueOf(i);
            if (this.index[i] != null && this.index[i].length != 0) {
                compoundTag2.putIntArray(string, this.index[i]);
            }
        }

        if (!compoundTag2.isEmpty()) {
            compoundTag.put("Indices", compoundTag2);
        }

        int j = 0;

        for(Direction8 direction8 : this.sides) {
            j |= 1 << direction8.ordinal();
        }

        compoundTag.putByte("Sides", (byte)j);
        if (!this.neighborBlockTicks.isEmpty()) {
            ListTag listTag = new ListTag();
            this.neighborBlockTicks.forEach((blockTick) -> {
                listTag.add(blockTick.save((block) -> {
                    return BuiltInRegistries.BLOCK.getKey(block).toString();
                }));
            });
            compoundTag.put("neighbor_block_ticks", listTag);
        }

        if (!this.neighborFluidTicks.isEmpty()) {
            ListTag listTag2 = new ListTag();
            this.neighborFluidTicks.forEach((fluidTick) -> {
                listTag2.add(fluidTick.save((fluid) -> {
                    return BuiltInRegistries.FLUID.getKey(fluid).toString();
                }));
            });
            compoundTag.put("neighbor_fluid_ticks", listTag2);
        }

        return compoundTag;
    }

    public interface BlockFixer {
        BlockState updateShape(BlockState oldState, Direction direction, BlockState otherState, LevelAccessor world, BlockPos currentPos, BlockPos otherPos);

        default void processChunk(LevelAccessor world) {
        }
    }

    static enum BlockFixers implements UpgradeData.BlockFixer {
        BLACKLIST(Blocks.OBSERVER, Blocks.NETHER_PORTAL, Blocks.WHITE_CONCRETE_POWDER, Blocks.ORANGE_CONCRETE_POWDER, Blocks.MAGENTA_CONCRETE_POWDER, Blocks.LIGHT_BLUE_CONCRETE_POWDER, Blocks.YELLOW_CONCRETE_POWDER, Blocks.LIME_CONCRETE_POWDER, Blocks.PINK_CONCRETE_POWDER, Blocks.GRAY_CONCRETE_POWDER, Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.CYAN_CONCRETE_POWDER, Blocks.PURPLE_CONCRETE_POWDER, Blocks.BLUE_CONCRETE_POWDER, Blocks.BROWN_CONCRETE_POWDER, Blocks.GREEN_CONCRETE_POWDER, Blocks.RED_CONCRETE_POWDER, Blocks.BLACK_CONCRETE_POWDER, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL, Blocks.DRAGON_EGG, Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND, Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.ACACIA_SIGN, Blocks.CHERRY_SIGN, Blocks.JUNGLE_SIGN, Blocks.DARK_OAK_SIGN, Blocks.OAK_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN, Blocks.BIRCH_WALL_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN, Blocks.ACACIA_HANGING_SIGN, Blocks.JUNGLE_HANGING_SIGN, Blocks.DARK_OAK_HANGING_SIGN, Blocks.OAK_WALL_HANGING_SIGN, Blocks.SPRUCE_WALL_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN, Blocks.JUNGLE_WALL_HANGING_SIGN, Blocks.DARK_OAK_WALL_HANGING_SIGN) {
            @Override
            public BlockState updateShape(BlockState oldState, Direction direction, BlockState otherState, LevelAccessor world, BlockPos currentPos, BlockPos otherPos) {
                return oldState;
            }
        },
        DEFAULT {
            @Override
            public BlockState updateShape(BlockState oldState, Direction direction, BlockState otherState, LevelAccessor world, BlockPos currentPos, BlockPos otherPos) {
                return oldState.updateShape(direction, world.getBlockState(otherPos), world, currentPos, otherPos);
            }
        },
        CHEST(Blocks.CHEST, Blocks.TRAPPED_CHEST) {
            @Override
            public BlockState updateShape(BlockState oldState, Direction direction, BlockState otherState, LevelAccessor world, BlockPos currentPos, BlockPos otherPos) {
                if (otherState.is(oldState.getBlock()) && direction.getAxis().isHorizontal() && oldState.getValue(ChestBlock.TYPE) == ChestType.SINGLE && otherState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                    Direction direction2 = oldState.getValue(ChestBlock.FACING);
                    if (direction.getAxis() != direction2.getAxis() && direction2 == otherState.getValue(ChestBlock.FACING)) {
                        ChestType chestType = direction == direction2.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
                        world.setBlock(otherPos, otherState.setValue(ChestBlock.TYPE, chestType.getOpposite()), 18);
                        if (direction2 == Direction.NORTH || direction2 == Direction.EAST) {
                            BlockEntity blockEntity = world.getBlockEntity(currentPos);
                            BlockEntity blockEntity2 = world.getBlockEntity(otherPos);
                            if (blockEntity instanceof ChestBlockEntity && blockEntity2 instanceof ChestBlockEntity) {
                                ChestBlockEntity.swapContents((ChestBlockEntity)blockEntity, (ChestBlockEntity)blockEntity2);
                            }
                        }

                        return oldState.setValue(ChestBlock.TYPE, chestType);
                    }
                }

                return oldState;
            }
        },
        LEAVES(true, Blocks.ACACIA_LEAVES, Blocks.CHERRY_LEAVES, Blocks.BIRCH_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES) {
            private final ThreadLocal<List<ObjectSet<BlockPos>>> queue = ThreadLocal.withInitial(() -> {
                return Lists.newArrayListWithCapacity(7);
            });

            @Override
            public BlockState updateShape(BlockState oldState, Direction direction, BlockState otherState, LevelAccessor world, BlockPos currentPos, BlockPos otherPos) {
                BlockState blockState = oldState.updateShape(direction, world.getBlockState(otherPos), world, currentPos, otherPos);
                if (oldState != blockState) {
                    int i = blockState.getValue(BlockStateProperties.DISTANCE);
                    List<ObjectSet<BlockPos>> list = this.queue.get();
                    if (list.isEmpty()) {
                        for(int j = 0; j < 7; ++j) {
                            list.add(new ObjectOpenHashSet<>());
                        }
                    }

                    list.get(i).add(currentPos.immutable());
                }

                return oldState;
            }

            @Override
            public void processChunk(LevelAccessor world) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                List<ObjectSet<BlockPos>> list = this.queue.get();

                for(int i = 2; i < list.size(); ++i) {
                    int j = i - 1;
                    ObjectSet<BlockPos> objectSet = list.get(j);
                    ObjectSet<BlockPos> objectSet2 = list.get(i);

                    for(BlockPos blockPos : objectSet) {
                        BlockState blockState = world.getBlockState(blockPos);
                        if (blockState.getValue(BlockStateProperties.DISTANCE) >= j) {
                            world.setBlock(blockPos, blockState.setValue(BlockStateProperties.DISTANCE, Integer.valueOf(j)), 18);
                            if (i != 7) {
                                for(Direction direction : DIRECTIONS) {
                                    mutableBlockPos.setWithOffset(blockPos, direction);
                                    BlockState blockState2 = world.getBlockState(mutableBlockPos);
                                    if (blockState2.hasProperty(BlockStateProperties.DISTANCE) && blockState.getValue(BlockStateProperties.DISTANCE) > i) {
                                        objectSet2.add(mutableBlockPos.immutable());
                                    }
                                }
                            }
                        }
                    }
                }

                list.clear();
            }
        },
        STEM_BLOCK(Blocks.MELON_STEM, Blocks.PUMPKIN_STEM) {
            @Override
            public BlockState updateShape(BlockState oldState, Direction direction, BlockState otherState, LevelAccessor world, BlockPos currentPos, BlockPos otherPos) {
                if (oldState.getValue(StemBlock.AGE) == 7) {
                    StemGrownBlock stemGrownBlock = ((StemBlock)oldState.getBlock()).getFruit();
                    if (otherState.is(stemGrownBlock)) {
                        return stemGrownBlock.getAttachedStem().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
                    }
                }

                return oldState;
            }
        };

        public static final Direction[] DIRECTIONS = Direction.values();

        BlockFixers(Block... blocks) {
            this(false, blocks);
        }

        BlockFixers(boolean addCallback, Block... blocks) {
            for(Block block : blocks) {
                UpgradeData.MAP.put(block, this);
            }

            if (addCallback) {
                UpgradeData.CHUNKY_FIXERS.add(this);
            }

        }
    }
}
