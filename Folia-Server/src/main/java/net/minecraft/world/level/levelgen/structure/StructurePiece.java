package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

public abstract class StructurePiece {

    private static final Logger LOGGER = LogUtils.getLogger();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected BoundingBox boundingBox;
    @Nullable
    private Direction orientation;
    private Mirror mirror;
    private Rotation rotation;
    protected int genDepth;
    private final StructurePieceType type;
    public static final Set<Block> SHAPE_CHECK_BLOCKS = ImmutableSet.<Block>builder().add(Blocks.NETHER_BRICK_FENCE).add(Blocks.TORCH).add(Blocks.WALL_TORCH).add(Blocks.OAK_FENCE).add(Blocks.SPRUCE_FENCE).add(Blocks.DARK_OAK_FENCE).add(Blocks.ACACIA_FENCE).add(Blocks.BIRCH_FENCE).add(Blocks.JUNGLE_FENCE).add(Blocks.LADDER).add(Blocks.IRON_BARS).build();  // CraftBukkit - decompile error / PAIL private -> public

    protected StructurePiece(StructurePieceType type, int length, BoundingBox boundingBox) {
        this.type = type;
        this.genDepth = length;
        this.boundingBox = boundingBox;
    }

    public StructurePiece(StructurePieceType type, CompoundTag nbt) {
        // CraftBukkit start - decompile error
        this(type, nbt.getInt("GD"), (BoundingBox) BoundingBox.CODEC.parse(NbtOps.INSTANCE, nbt.get("BB")).resultOrPartial(Objects.requireNonNull(StructurePiece.LOGGER)::error).orElseThrow(() -> {
            return new IllegalArgumentException("Invalid boundingbox");
        }));
        // CraftBukkit end
        int j = nbt.getInt("O");

        this.setOrientation(j == -1 ? null : Direction.from2DDataValue(j));
    }

    protected static BoundingBox makeBoundingBox(int x, int y, int z, Direction orientation, int width, int height, int depth) {
        return orientation.getAxis() == Direction.Axis.Z ? new BoundingBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1) : new BoundingBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
    }

    protected static Direction getRandomHorizontalDirection(RandomSource random) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(random);
    }

    public final CompoundTag createTag(StructurePieceSerializationContext context) {
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.putString("id", BuiltInRegistries.STRUCTURE_PIECE.getKey(this.getType()).toString());
        // CraftBukkit start - decompile error
        BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, this.boundingBox).resultOrPartial(Objects.requireNonNull(StructurePiece.LOGGER)::error).ifPresent((nbtbase) -> {
             nbttagcompound.put("BB", nbtbase);
        });
        // CraftBukkit end
        Direction enumdirection = this.getOrientation();

        nbttagcompound.putInt("O", enumdirection == null ? -1 : enumdirection.get2DDataValue());
        nbttagcompound.putInt("GD", this.genDepth);
        this.addAdditionalSaveData(context, nbttagcompound);
        return nbttagcompound;
    }

    protected abstract void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt);

    public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {}

    public abstract void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot);

    public BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public int getGenDepth() {
        return this.genDepth;
    }

    public void setGenDepth(int chainLength) {
        this.genDepth = chainLength;
    }

    public boolean isCloseToChunk(ChunkPos pos, int offset) {
        int j = pos.getMinBlockX();
        int k = pos.getMinBlockZ();

        return this.boundingBox.intersects(j - offset, k - offset, j + 15 + offset, k + 15 + offset);
    }

    public BlockPos getLocatorPosition() {
        return new BlockPos(this.boundingBox.getCenter());
    }

    protected BlockPos.MutableBlockPos getWorldPos(int x, int y, int z) {
        return new BlockPos.MutableBlockPos(this.getWorldX(x, z), this.getWorldY(y), this.getWorldZ(x, z));
    }

    protected int getWorldX(int x, int z) {
        Direction enumdirection = this.getOrientation();

        if (enumdirection == null) {
            return x;
        } else {
            switch (enumdirection) {
                case NORTH:
                case SOUTH:
                    return this.boundingBox.minX() + x;
                case WEST:
                    return this.boundingBox.maxX() - z;
                case EAST:
                    return this.boundingBox.minX() + z;
                default:
                    return x;
            }
        }
    }

    protected int getWorldY(int y) {
        return this.getOrientation() == null ? y : y + this.boundingBox.minY();
    }

    protected int getWorldZ(int x, int z) {
        Direction enumdirection = this.getOrientation();

        if (enumdirection == null) {
            return z;
        } else {
            switch (enumdirection) {
                case NORTH:
                    return this.boundingBox.maxZ() - z;
                case SOUTH:
                    return this.boundingBox.minZ() + z;
                case WEST:
                case EAST:
                    return this.boundingBox.minZ() + x;
                default:
                    return z;
            }
        }
    }

    protected void placeBlock(WorldGenLevel world, BlockState block, int x, int y, int z, BoundingBox box) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

        if (box.isInside(blockposition_mutableblockposition)) {
            if (this.canBeReplaced(world, x, y, z, box)) {
                if (this.mirror != Mirror.NONE) {
                    block = block.mirror(this.mirror);
                }

                if (this.rotation != Rotation.NONE) {
                    block = block.rotate(this.rotation);
                }

                world.setBlock(blockposition_mutableblockposition, block, 2);
                // CraftBukkit start - fluid handling is already done if we have a transformer generator access
                if (world instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess) {
                    return;
                }
                // CraftBukkit end
                FluidState fluid = world.getFluidState(blockposition_mutableblockposition);

                if (!fluid.isEmpty()) {
                    world.scheduleTick(blockposition_mutableblockposition, fluid.getType(), 0);
                }

                if (StructurePiece.SHAPE_CHECK_BLOCKS.contains(block.getBlock())) {
                    world.getChunk(blockposition_mutableblockposition).markPosForPostprocessing(blockposition_mutableblockposition);
                }

            }
        }
    }

    // CraftBukkit start
    protected boolean placeCraftBlockEntity(ServerLevelAccessor worldAccess, BlockPos position, org.bukkit.craftbukkit.block.CraftBlockEntityState<?> craftBlockEntityState, int i) {
        if (worldAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
            return transformerAccess.setCraftBlock(position, craftBlockEntityState, i);
        }
        boolean result = worldAccess.setBlock(position, craftBlockEntityState.getHandle(), i);
        BlockEntity tileEntity = worldAccess.getBlockEntity(position);
        if (tileEntity != null) {
            tileEntity.load(craftBlockEntityState.getSnapshotNBT());
        }
        return result;
    }

    protected void placeCraftSpawner(ServerLevelAccessor worldAccess, BlockPos position, org.bukkit.entity.EntityType entityType, int i) {
        // This method is used in structures that are generated by code and place spawners as they set the entity after the block was placed making it impossible for plugins to access that information
        org.bukkit.craftbukkit.block.CraftCreatureSpawner spawner = (org.bukkit.craftbukkit.block.CraftCreatureSpawner) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(position, Blocks.SPAWNER.defaultBlockState(), null);
        spawner.setSpawnedType(entityType);
        this.placeCraftBlockEntity(worldAccess, position, spawner, i);
    }

    protected void setCraftLootTable(ServerLevelAccessor worldAccess, BlockPos position, RandomSource randomSource, net.minecraft.resources.ResourceLocation loottableKey) {
        // This method is used in structures that use data markers to a loot table to loot containers as otherwise plugins won't have access to that information.
        net.minecraft.world.level.block.entity.BlockEntity tileEntity = worldAccess.getBlockEntity(position);
        if (tileEntity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity tileEntityLootable) {
            tileEntityLootable.setLootTable(loottableKey, randomSource.nextLong());
            if (worldAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
                transformerAccess.setCraftBlock(position, (org.bukkit.craftbukkit.block.CraftBlockState) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(position, tileEntity.getBlockState(), tileEntityLootable.saveWithFullMetadata()), 3);
            }
        }
    }
    // CraftBukkit end

    protected boolean canBeReplaced(LevelReader world, int x, int y, int z, BoundingBox box) {
        return true;
    }

    protected BlockState getBlock(BlockGetter world, int x, int y, int z, BoundingBox box) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

        return !box.isInside(blockposition_mutableblockposition) ? Blocks.AIR.defaultBlockState() : world.getBlockState(blockposition_mutableblockposition);
    }

    protected boolean isInterior(LevelReader world, int x, int z, int y, BoundingBox box) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, z + 1, y);

        return !box.isInside(blockposition_mutableblockposition) ? false : blockposition_mutableblockposition.getY() < world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, blockposition_mutableblockposition.getX(), blockposition_mutableblockposition.getZ());
    }

    protected void generateAirBox(WorldGenLevel world, BoundingBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int k1 = minY; k1 <= maxY; ++k1) {
            for (int l1 = minX; l1 <= maxX; ++l1) {
                for (int i2 = minZ; i2 <= maxZ; ++i2) {
                    this.placeBlock(world, Blocks.AIR.defaultBlockState(), l1, k1, i2, bounds);
                }
            }
        }

    }

    protected void generateBox(WorldGenLevel world, BoundingBox box, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState outline, BlockState inside, boolean cantReplaceAir) {
        for (int k1 = minY; k1 <= maxY; ++k1) {
            for (int l1 = minX; l1 <= maxX; ++l1) {
                for (int i2 = minZ; i2 <= maxZ; ++i2) {
                    if (!cantReplaceAir || !this.getBlock(world, l1, k1, i2, box).isAir()) {
                        if (k1 != minY && k1 != maxY && l1 != minX && l1 != maxX && i2 != minZ && i2 != maxZ) {
                            this.placeBlock(world, inside, l1, k1, i2, box);
                        } else {
                            this.placeBlock(world, outline, l1, k1, i2, box);
                        }
                    }
                }
            }
        }

    }

    protected void generateBox(WorldGenLevel world, BoundingBox box, BoundingBox fillBox, BlockState outline, BlockState inside, boolean cantReplaceAir) {
        this.generateBox(world, box, fillBox.minX(), fillBox.minY(), fillBox.minZ(), fillBox.maxX(), fillBox.maxY(), fillBox.maxZ(), outline, inside, cantReplaceAir);
    }

    protected void generateBox(WorldGenLevel world, BoundingBox box, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean cantReplaceAir, RandomSource random, StructurePiece.BlockSelector randomizer) {
        for (int k1 = minY; k1 <= maxY; ++k1) {
            for (int l1 = minX; l1 <= maxX; ++l1) {
                for (int i2 = minZ; i2 <= maxZ; ++i2) {
                    if (!cantReplaceAir || !this.getBlock(world, l1, k1, i2, box).isAir()) {
                        randomizer.next(random, l1, k1, i2, k1 == minY || k1 == maxY || l1 == minX || l1 == maxX || i2 == minZ || i2 == maxZ);
                        this.placeBlock(world, randomizer.getNext(), l1, k1, i2, box);
                    }
                }
            }
        }

    }

    protected void generateBox(WorldGenLevel world, BoundingBox box, BoundingBox fillBox, boolean cantReplaceAir, RandomSource random, StructurePiece.BlockSelector randomizer) {
        this.generateBox(world, box, fillBox.minX(), fillBox.minY(), fillBox.minZ(), fillBox.maxX(), fillBox.maxY(), fillBox.maxZ(), cantReplaceAir, random, randomizer);
    }

    protected void generateMaybeBox(WorldGenLevel world, BoundingBox box, RandomSource random, float blockChance, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState outline, BlockState inside, boolean cantReplaceAir, boolean stayBelowSeaLevel) {
        for (int k1 = minY; k1 <= maxY; ++k1) {
            for (int l1 = minX; l1 <= maxX; ++l1) {
                for (int i2 = minZ; i2 <= maxZ; ++i2) {
                    if (random.nextFloat() <= blockChance && (!cantReplaceAir || !this.getBlock(world, l1, k1, i2, box).isAir()) && (!stayBelowSeaLevel || this.isInterior(world, l1, k1, i2, box))) {
                        if (k1 != minY && k1 != maxY && l1 != minX && l1 != maxX && i2 != minZ && i2 != maxZ) {
                            this.placeBlock(world, inside, l1, k1, i2, box);
                        } else {
                            this.placeBlock(world, outline, l1, k1, i2, box);
                        }
                    }
                }
            }
        }

    }

    protected void maybeGenerateBlock(WorldGenLevel world, BoundingBox bounds, RandomSource random, float threshold, int x, int y, int z, BlockState state) {
        if (random.nextFloat() < threshold) {
            this.placeBlock(world, state, x, y, z, bounds);
        }

    }

    protected void generateUpperHalfSphere(WorldGenLevel world, BoundingBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState block, boolean cantReplaceAir) {
        float f = (float) (maxX - minX + 1);
        float f1 = (float) (maxY - minY + 1);
        float f2 = (float) (maxZ - minZ + 1);
        float f3 = (float) minX + f / 2.0F;
        float f4 = (float) minZ + f2 / 2.0F;

        for (int k1 = minY; k1 <= maxY; ++k1) {
            float f5 = (float) (k1 - minY) / f1;

            for (int l1 = minX; l1 <= maxX; ++l1) {
                float f6 = ((float) l1 - f3) / (f * 0.5F);

                for (int i2 = minZ; i2 <= maxZ; ++i2) {
                    float f7 = ((float) i2 - f4) / (f2 * 0.5F);

                    if (!cantReplaceAir || !this.getBlock(world, l1, k1, i2, bounds).isAir()) {
                        float f8 = f6 * f6 + f5 * f5 + f7 * f7;

                        if (f8 <= 1.05F) {
                            this.placeBlock(world, block, l1, k1, i2, bounds);
                        }
                    }
                }
            }
        }

    }

    protected void fillColumnDown(WorldGenLevel world, BlockState state, int x, int y, int z, BoundingBox box) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

        if (box.isInside(blockposition_mutableblockposition)) {
            while (this.isReplaceableByStructures(world.getBlockState(blockposition_mutableblockposition)) && blockposition_mutableblockposition.getY() > world.getMinBuildHeight() + 1) {
                world.setBlock(blockposition_mutableblockposition, state, 2);
                blockposition_mutableblockposition.move(Direction.DOWN);
            }

        }
    }

    protected boolean isReplaceableByStructures(BlockState state) {
        return state.isAir() || state.liquid() || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    protected boolean createChest(WorldGenLevel world, BoundingBox boundingBox, RandomSource random, int x, int y, int z, ResourceLocation lootTableId) {
        return this.createChest(world, boundingBox, random, this.getWorldPos(x, y, z), lootTableId, (BlockState) null);
    }

    public static BlockState reorient(BlockGetter world, BlockPos pos, BlockState state) {
        Direction enumdirection = null;
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection1 = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection1);
            BlockState iblockdata1 = world.getBlockState(blockposition1);

            if (iblockdata1.is(Blocks.CHEST)) {
                return state;
            }

            if (iblockdata1.isSolidRender(world, blockposition1)) {
                if (enumdirection != null) {
                    enumdirection = null;
                    break;
                }

                enumdirection = enumdirection1;
            }
        }

        if (enumdirection != null) {
            return (BlockState) state.setValue(HorizontalDirectionalBlock.FACING, enumdirection.getOpposite());
        } else {
            Direction enumdirection2 = (Direction) state.getValue(HorizontalDirectionalBlock.FACING);
            BlockPos blockposition2 = pos.relative(enumdirection2);

            if (world.getBlockState(blockposition2).isSolidRender(world, blockposition2)) {
                enumdirection2 = enumdirection2.getOpposite();
                blockposition2 = pos.relative(enumdirection2);
            }

            if (world.getBlockState(blockposition2).isSolidRender(world, blockposition2)) {
                enumdirection2 = enumdirection2.getClockWise();
                blockposition2 = pos.relative(enumdirection2);
            }

            if (world.getBlockState(blockposition2).isSolidRender(world, blockposition2)) {
                enumdirection2 = enumdirection2.getOpposite();
                pos.relative(enumdirection2);
            }

            return (BlockState) state.setValue(HorizontalDirectionalBlock.FACING, enumdirection2);
        }
    }

    protected boolean createChest(ServerLevelAccessor world, BoundingBox boundingBox, RandomSource random, BlockPos pos, ResourceLocation lootTableId, @Nullable BlockState block) {
        if (boundingBox.isInside(pos) && !world.getBlockState(pos).is(Blocks.CHEST)) {
            if (block == null) {
                block = StructurePiece.reorient(world, pos, Blocks.CHEST.defaultBlockState());
            }

            // CraftBukkit start
            /*
            worldaccess.setBlock(blockposition, iblockdata, 2);
            TileEntity tileentity = worldaccess.getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityChest) {
                ((TileEntityChest) tileentity).setLootTable(minecraftkey, randomsource.nextLong());
            }
            */
            org.bukkit.craftbukkit.block.CraftChest chestState = (org.bukkit.craftbukkit.block.CraftChest) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(pos, block, null);
            chestState.setLootTable(org.bukkit.Bukkit.getLootTable(org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(lootTableId)));
            chestState.setSeed(random.nextLong());
            this.placeCraftBlockEntity(world, pos, chestState, 2);
            // CraftBukkit end

            return true;
        } else {
            return false;
        }
    }

    protected boolean createDispenser(WorldGenLevel world, BoundingBox boundingBox, RandomSource random, int x, int y, int z, Direction facing, ResourceLocation lootTableId) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

        if (boundingBox.isInside(blockposition_mutableblockposition) && !world.getBlockState(blockposition_mutableblockposition).is(Blocks.DISPENSER)) {
            // CraftBukkit start
            /*
            this.placeBlock(generatoraccessseed, (IBlockData) Blocks.DISPENSER.defaultBlockState().setValue(BlockDispenser.FACING, enumdirection), i, j, k, structureboundingbox);
            TileEntity tileentity = generatoraccessseed.getBlockEntity(blockposition_mutableblockposition);

            if (tileentity instanceof TileEntityDispenser) {
                ((TileEntityDispenser) tileentity).setLootTable(minecraftkey, randomsource.nextLong());
            }
            */
            if (!this.canBeReplaced(world, x, y, z, boundingBox)) {
                return true;
            }
            BlockState iblockdata = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing);
            if (this.mirror != Mirror.NONE) {
                iblockdata = iblockdata.mirror(this.mirror);
            }
            if (this.rotation != Rotation.NONE) {
                iblockdata = iblockdata.rotate(this.rotation);
            }

            org.bukkit.craftbukkit.block.CraftDispenser dispenserState = (org.bukkit.craftbukkit.block.CraftDispenser) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockposition_mutableblockposition, iblockdata, null);
            dispenserState.setLootTable(org.bukkit.Bukkit.getLootTable(org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(lootTableId)));
            dispenserState.setSeed(random.nextLong());
            this.placeCraftBlockEntity(world, blockposition_mutableblockposition, dispenserState, 2);
            // CraftBukkit end

            return true;
        } else {
            return false;
        }
    }

    public void move(int x, int y, int z) {
        this.boundingBox.move(x, y, z);
    }

    public static BoundingBox createBoundingBox(Stream<StructurePiece> pieces) {
        Stream<BoundingBox> stream1 = pieces.map(StructurePiece::getBoundingBox); // CraftBukkit - decompile error

        Objects.requireNonNull(stream1);
        return (BoundingBox) BoundingBox.encapsulatingBoxes(stream1::iterator).orElseThrow(() -> {
            return new IllegalStateException("Unable to calculate boundingbox without pieces");
        });
    }

    @Nullable
    public static StructurePiece findCollisionPiece(List<StructurePiece> pieces, BoundingBox box) {
        Iterator iterator = pieces.iterator();

        StructurePiece structurepiece;

        do {
            if (!iterator.hasNext()) {
                return null;
            }

            structurepiece = (StructurePiece) iterator.next();
        } while (!structurepiece.getBoundingBox().intersects(box));

        return structurepiece;
    }

    @Nullable
    public Direction getOrientation() {
        return this.orientation;
    }

    public void setOrientation(@Nullable Direction orientation) {
        this.orientation = orientation;
        if (orientation == null) {
            this.rotation = Rotation.NONE;
            this.mirror = Mirror.NONE;
        } else {
            switch (orientation) {
                case SOUTH:
                    this.mirror = Mirror.LEFT_RIGHT;
                    this.rotation = Rotation.NONE;
                    break;
                case WEST:
                    this.mirror = Mirror.LEFT_RIGHT;
                    this.rotation = Rotation.CLOCKWISE_90;
                    break;
                case EAST:
                    this.mirror = Mirror.NONE;
                    this.rotation = Rotation.CLOCKWISE_90;
                    break;
                default:
                    this.mirror = Mirror.NONE;
                    this.rotation = Rotation.NONE;
            }
        }

    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public StructurePieceType getType() {
        return this.type;
    }

    public abstract static class BlockSelector {

        protected BlockState next;

        public BlockSelector() {
            this.next = Blocks.AIR.defaultBlockState();
        }

        public abstract void next(RandomSource random, int x, int y, int z, boolean placeBlock);

        public BlockState getNext() {
            return this.next;
        }
    }
}
