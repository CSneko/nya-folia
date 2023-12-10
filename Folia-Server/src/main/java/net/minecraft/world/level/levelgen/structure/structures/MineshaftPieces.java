package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.slf4j.Logger;

public class MineshaftPieces {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_SHAFT_WIDTH = 3;
    private static final int DEFAULT_SHAFT_HEIGHT = 3;
    private static final int DEFAULT_SHAFT_LENGTH = 5;
    private static final int MAX_PILLAR_HEIGHT = 20;
    private static final int MAX_CHAIN_HEIGHT = 50;
    private static final int MAX_DEPTH = 8;
    public static final int MAGIC_START_Y = 50;

    public MineshaftPieces() {}

    private static MineshaftPieces.MineShaftPiece createRandomShaftPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, @Nullable Direction orientation, int chainLength, MineshaftStructure.Type type) {
        int i1 = random.nextInt(100);
        BoundingBox structureboundingbox;

        if (i1 >= 80) {
            structureboundingbox = MineshaftPieces.MineShaftCrossing.findCrossing(holder, random, x, y, z, orientation);
            if (structureboundingbox != null) {
                return new MineshaftPieces.MineShaftCrossing(chainLength, structureboundingbox, orientation, type);
            }
        } else if (i1 >= 70) {
            structureboundingbox = MineshaftPieces.MineShaftStairs.findStairs(holder, random, x, y, z, orientation);
            if (structureboundingbox != null) {
                return new MineshaftPieces.MineShaftStairs(chainLength, structureboundingbox, orientation, type);
            }
        } else {
            structureboundingbox = MineshaftPieces.MineShaftCorridor.findCorridorSize(holder, random, x, y, z, orientation);
            if (structureboundingbox != null) {
                return new MineshaftPieces.MineShaftCorridor(chainLength, random, structureboundingbox, orientation, type);
            }
        }

        return null;
    }

    static MineshaftPieces.MineShaftPiece generateAndAddPiece(StructurePiece start, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
        if (chainLength > 8) {
            return null;
        } else if (Math.abs(x - start.getBoundingBox().minX()) <= 80 && Math.abs(z - start.getBoundingBox().minZ()) <= 80) {
            MineshaftStructure.Type mineshaftstructure_a = ((MineshaftPieces.MineShaftPiece) start).type;
            MineshaftPieces.MineShaftPiece mineshaftpieces_c = MineshaftPieces.createRandomShaftPiece(holder, random, x, y, z, orientation, chainLength + 1, mineshaftstructure_a);

            if (mineshaftpieces_c != null) {
                holder.addPiece(mineshaftpieces_c);
                mineshaftpieces_c.addChildren(start, holder, random);
            }

            return mineshaftpieces_c;
        } else {
            return null;
        }
    }

    public static class MineShaftCrossing extends MineshaftPieces.MineShaftPiece {

        private final Direction direction;
        private final boolean isTwoFloored;

        public MineShaftCrossing(CompoundTag nbt) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, nbt);
            this.isTwoFloored = nbt.getBoolean("tf");
            this.direction = Direction.from2DDataValue(nbt.getInt("D"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("tf", this.isTwoFloored);
            nbt.putInt("D", this.direction.get2DDataValue());
        }

        public MineShaftCrossing(int chainLength, BoundingBox boundingBox, @Nullable Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, chainLength, type, boundingBox);
            this.direction = orientation;
            this.isTwoFloored = boundingBox.getYSpan() > 3;
        }

        @Nullable
        public static BoundingBox findCrossing(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation) {
            byte b0;

            if (random.nextInt(4) == 0) {
                b0 = 6;
            } else {
                b0 = 2;
            }

            BoundingBox structureboundingbox;

            switch (orientation) {
                case NORTH:
                default:
                    structureboundingbox = new BoundingBox(-1, 0, -4, 3, b0, 0);
                    break;
                case SOUTH:
                    structureboundingbox = new BoundingBox(-1, 0, 0, 3, b0, 4);
                    break;
                case WEST:
                    structureboundingbox = new BoundingBox(-4, 0, -1, 0, b0, 3);
                    break;
                case EAST:
                    structureboundingbox = new BoundingBox(0, 0, -1, 4, b0, 3);
            }

            structureboundingbox.move(x, y, z);
            return holder.findCollisionPiece(structureboundingbox) != null ? null : structureboundingbox;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            int i = this.getGenDepth();

            switch (this.direction) {
                case NORTH:
                default:
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i);
                    break;
                case SOUTH:
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i);
                    break;
                case WEST:
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i);
                    break;
                case EAST:
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i);
            }

            if (this.isTwoFloored) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() - 1, Direction.NORTH, i);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1, Direction.WEST, i);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1, Direction.EAST, i);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!this.isInInvalidLocation(world, chunkBox)) {
                BlockState iblockdata = this.type.getPlanksState();

                if (this.isTwoFloored) {
                    this.generateBox(world, chunkBox, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), this.boundingBox.maxX() - 1, this.boundingBox.minY() + 3 - 1, this.boundingBox.maxZ(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(world, chunkBox, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxX(), this.boundingBox.minY() + 3 - 1, this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(world, chunkBox, this.boundingBox.minX() + 1, this.boundingBox.maxY() - 2, this.boundingBox.minZ(), this.boundingBox.maxX() - 1, this.boundingBox.maxY(), this.boundingBox.maxZ(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(world, chunkBox, this.boundingBox.minX(), this.boundingBox.maxY() - 2, this.boundingBox.minZ() + 1, this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(world, chunkBox, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3, this.boundingBox.minZ() + 1, this.boundingBox.maxX() - 1, this.boundingBox.minY() + 3, this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                } else {
                    this.generateBox(world, chunkBox, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), this.boundingBox.maxX() - 1, this.boundingBox.maxY(), this.boundingBox.maxZ(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                    this.generateBox(world, chunkBox, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ() - 1, MineshaftPieces.MineShaftCrossing.CAVE_AIR, MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
                }

                this.placeSupportPillar(world, chunkBox, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(world, chunkBox, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                this.placeSupportPillar(world, chunkBox, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(world, chunkBox, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                int i = this.boundingBox.minY() - 1;

                for (int j = this.boundingBox.minX(); j <= this.boundingBox.maxX(); ++j) {
                    for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); ++k) {
                        this.setPlanksBlock(world, chunkBox, iblockdata, j, i, k);
                    }
                }

            }
        }

        private void placeSupportPillar(WorldGenLevel world, BoundingBox boundingBox, int x, int minY, int z, int maxY) {
            if (!this.getBlock(world, x, maxY + 1, z, boundingBox).isAir()) {
                this.generateBox(world, boundingBox, x, minY, z, x, maxY, z, this.type.getPlanksState(), MineshaftPieces.MineShaftCrossing.CAVE_AIR, false);
            }

        }
    }

    public static class MineShaftStairs extends MineshaftPieces.MineShaftPiece {

        public MineShaftStairs(int chainLength, BoundingBox boundingBox, Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, chainLength, type, boundingBox);
            this.setOrientation(orientation);
        }

        public MineShaftStairs(CompoundTag nbt) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, nbt);
        }

        @Nullable
        public static BoundingBox findStairs(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation) {
            BoundingBox structureboundingbox;

            switch (orientation) {
                case NORTH:
                default:
                    structureboundingbox = new BoundingBox(0, -5, -8, 2, 2, 0);
                    break;
                case SOUTH:
                    structureboundingbox = new BoundingBox(0, -5, 0, 2, 2, 8);
                    break;
                case WEST:
                    structureboundingbox = new BoundingBox(-8, -5, 0, 0, 2, 2);
                    break;
                case EAST:
                    structureboundingbox = new BoundingBox(0, -5, 0, 8, 2, 2);
            }

            structureboundingbox.move(x, y, z);
            return holder.findCollisionPiece(structureboundingbox) != null ? null : structureboundingbox;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            int i = this.getGenDepth();
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                    default:
                        MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                        break;
                    case SOUTH:
                        MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                        break;
                    case WEST:
                        MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.WEST, i);
                        break;
                    case EAST:
                        MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.EAST, i);
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!this.isInInvalidLocation(world, chunkBox)) {
                this.generateBox(world, chunkBox, 0, 5, 0, 2, 7, 1, MineshaftPieces.MineShaftStairs.CAVE_AIR, MineshaftPieces.MineShaftStairs.CAVE_AIR, false);
                this.generateBox(world, chunkBox, 0, 0, 7, 2, 2, 8, MineshaftPieces.MineShaftStairs.CAVE_AIR, MineshaftPieces.MineShaftStairs.CAVE_AIR, false);

                for (int i = 0; i < 5; ++i) {
                    this.generateBox(world, chunkBox, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, MineshaftPieces.MineShaftStairs.CAVE_AIR, MineshaftPieces.MineShaftStairs.CAVE_AIR, false);
                }

            }
        }
    }

    public static class MineShaftCorridor extends MineshaftPieces.MineShaftPiece {

        private final boolean hasRails;
        private final boolean spiderCorridor;
        private boolean hasPlacedSpider;
        private final int numSections;

        public MineShaftCorridor(CompoundTag nbt) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, nbt);
            this.hasRails = nbt.getBoolean("hr");
            this.spiderCorridor = nbt.getBoolean("sc");
            this.hasPlacedSpider = nbt.getBoolean("hps");
            this.numSections = nbt.getInt("Num");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("hr", this.hasRails);
            nbt.putBoolean("sc", this.spiderCorridor);
            nbt.putBoolean("hps", this.hasPlacedSpider);
            nbt.putInt("Num", this.numSections);
        }

        public MineShaftCorridor(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, chainLength, type, boundingBox);
            this.setOrientation(orientation);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !this.hasRails && random.nextInt(23) == 0;
            if (this.getOrientation().getAxis() == Direction.Axis.Z) {
                this.numSections = boundingBox.getZSpan() / 5;
            } else {
                this.numSections = boundingBox.getXSpan() / 5;
            }

        }

        @Nullable
        public static BoundingBox findCorridorSize(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation) {
            for (int l = random.nextInt(3) + 2; l > 0; --l) {
                int i1 = l * 5;
                BoundingBox structureboundingbox;

                switch (orientation) {
                    case NORTH:
                    default:
                        structureboundingbox = new BoundingBox(0, 0, -(i1 - 1), 2, 2, 0);
                        break;
                    case SOUTH:
                        structureboundingbox = new BoundingBox(0, 0, 0, 2, 2, i1 - 1);
                        break;
                    case WEST:
                        structureboundingbox = new BoundingBox(-(i1 - 1), 0, 0, 0, 2, 2);
                        break;
                    case EAST:
                        structureboundingbox = new BoundingBox(0, 0, 0, i1 - 1, 2, 2);
                }

                structureboundingbox.move(x, y, z);
                if (holder.findCollisionPiece(structureboundingbox) == null) {
                    return structureboundingbox;
                }
            }

            return null;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            int i = this.getGenDepth();
            int j = random.nextInt(4);
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                    default:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ() - 1, enumdirection, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), Direction.WEST, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), Direction.EAST, i);
                        }
                        break;
                    case SOUTH:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() + 1, enumdirection, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() - 3, Direction.WEST, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() - 3, Direction.EAST, i);
                        }
                        break;
                    case WEST:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), enumdirection, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                        }
                        break;
                    case EAST:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ(), enumdirection, i);
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() - 3, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.minZ() - 1, Direction.NORTH, i);
                        } else {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() - 3, this.boundingBox.minY() - 1 + random.nextInt(3), this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                        }
                }
            }

            if (i < 8) {
                int k;
                int l;

                if (enumdirection != Direction.NORTH && enumdirection != Direction.SOUTH) {
                    for (l = this.boundingBox.minX() + 3; l + 3 <= this.boundingBox.maxX(); l += 5) {
                        k = random.nextInt(5);
                        if (k == 0) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, l, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i + 1);
                        } else if (k == 1) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, l, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i + 1);
                        }
                    }
                } else {
                    for (l = this.boundingBox.minZ() + 3; l + 3 <= this.boundingBox.maxZ(); l += 5) {
                        k = random.nextInt(5);
                        if (k == 0) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), l, Direction.WEST, i + 1);
                        } else if (k == 1) {
                            MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), l, Direction.EAST, i + 1);
                        }
                    }
                }
            }

        }

        @Override
        protected boolean createChest(WorldGenLevel world, BoundingBox boundingBox, RandomSource random, int x, int y, int z, ResourceLocation lootTableId) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

            if (boundingBox.isInside(blockposition_mutableblockposition) && world.getBlockState(blockposition_mutableblockposition).isAir() && !world.getBlockState(blockposition_mutableblockposition.below()).isAir()) {
                BlockState iblockdata = (BlockState) Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, random.nextBoolean() ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);

                this.placeBlock(world, iblockdata, x, y, z, boundingBox);
                MinecartChest entityminecartchest = new MinecartChest(world.getLevel(), (double) blockposition_mutableblockposition.getX() + 0.5D, (double) blockposition_mutableblockposition.getY() + 0.5D, (double) blockposition_mutableblockposition.getZ() + 0.5D);

                entityminecartchest.setLootTable(lootTableId, random.nextLong());
                world.addFreshEntity(entityminecartchest);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!this.isInInvalidLocation(world, chunkBox)) {
                boolean flag = false;
                boolean flag1 = true;
                boolean flag2 = false;
                boolean flag3 = true;
                int i = this.numSections * 5 - 1;
                BlockState iblockdata = this.type.getPlanksState();

                this.generateBox(world, chunkBox, 0, 0, 0, 2, 1, i, MineshaftPieces.MineShaftCorridor.CAVE_AIR, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                this.generateMaybeBox(world, chunkBox, random, 0.8F, 0, 2, 0, 2, 2, i, MineshaftPieces.MineShaftCorridor.CAVE_AIR, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false, false);
                if (this.spiderCorridor) {
                    this.generateMaybeBox(world, chunkBox, random, 0.6F, 0, 0, 0, 2, 1, i, Blocks.COBWEB.defaultBlockState(), MineshaftPieces.MineShaftCorridor.CAVE_AIR, false, true);
                }

                int j;
                int k;

                for (k = 0; k < this.numSections; ++k) {
                    j = 2 + k * 5;
                    this.placeSupport(world, chunkBox, 0, 0, j, 2, 2, random);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.1F, 0, 2, j - 1);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.1F, 2, 2, j - 1);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.1F, 0, 2, j + 1);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.1F, 2, 2, j + 1);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.05F, 0, 2, j - 2);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.05F, 2, 2, j - 2);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.05F, 0, 2, j + 2);
                    this.maybePlaceCobWeb(world, chunkBox, random, 0.05F, 2, 2, j + 2);
                    if (random.nextInt(100) == 0) {
                        this.createChest(world, chunkBox, random, 2, 0, j - 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (random.nextInt(100) == 0) {
                        this.createChest(world, chunkBox, random, 0, 0, j + 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (this.spiderCorridor && !this.hasPlacedSpider) {
                        boolean flag4 = true;
                        int l = j - 1 + random.nextInt(3);
                        BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(1, 0, l);

                        if (chunkBox.isInside(blockposition_mutableblockposition) && this.isInterior(world, 1, 0, l, chunkBox)) {
                            this.hasPlacedSpider = true;
                            // CraftBukkit start
                            /*
                            generatoraccessseed.setBlock(blockposition_mutableblockposition, Blocks.SPAWNER.defaultBlockState(), 2);
                            TileEntity tileentity = generatoraccessseed.getBlockEntity(blockposition_mutableblockposition);

                            if (tileentity instanceof TileEntityMobSpawner) {
                                TileEntityMobSpawner tileentitymobspawner = (TileEntityMobSpawner) tileentity;

                                tileentitymobspawner.setEntityId(EntityTypes.CAVE_SPIDER, randomsource);
                            }
                            */
                            this.placeCraftSpawner(world, blockposition_mutableblockposition, org.bukkit.entity.EntityType.CAVE_SPIDER, 2);
                            // CraftBukkit end
                        }
                    }
                }

                for (k = 0; k <= 2; ++k) {
                    for (j = 0; j <= i; ++j) {
                        this.setPlanksBlock(world, chunkBox, iblockdata, k, -1, j);
                    }
                }

                boolean flag5 = true;

                this.placeDoubleLowerOrUpperSupport(world, chunkBox, 0, -1, 2);
                if (this.numSections > 1) {
                    j = i - 2;
                    this.placeDoubleLowerOrUpperSupport(world, chunkBox, 0, -1, j);
                }

                if (this.hasRails) {
                    BlockState iblockdata1 = (BlockState) Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH);

                    for (int i1 = 0; i1 <= i; ++i1) {
                        BlockState iblockdata2 = this.getBlock(world, 1, -1, i1, chunkBox);

                        if (!iblockdata2.isAir() && iblockdata2.isSolidRender(world, this.getWorldPos(1, -1, i1))) {
                            float f = this.isInterior(world, 1, 0, i1, chunkBox) ? 0.7F : 0.9F;

                            this.maybeGenerateBlock(world, chunkBox, random, f, 1, 0, i1, iblockdata1);
                        }
                    }
                }

            }
        }

        private void placeDoubleLowerOrUpperSupport(WorldGenLevel world, BoundingBox box, int x, int y, int z) {
            BlockState iblockdata = this.type.getWoodState();
            BlockState iblockdata1 = this.type.getPlanksState();

            if (this.getBlock(world, x, y, z, box).is(iblockdata1.getBlock())) {
                this.fillPillarDownOrChainUp(world, iblockdata, x, y, z, box);
            }

            if (this.getBlock(world, x + 2, y, z, box).is(iblockdata1.getBlock())) {
                this.fillPillarDownOrChainUp(world, iblockdata, x + 2, y, z, box);
            }

        }

        @Override
        protected void fillColumnDown(WorldGenLevel world, BlockState state, int x, int y, int z, BoundingBox box) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

            if (box.isInside(blockposition_mutableblockposition)) {
                int l = blockposition_mutableblockposition.getY();

                while (this.isReplaceableByStructures(world.getBlockState(blockposition_mutableblockposition)) && blockposition_mutableblockposition.getY() > world.getMinBuildHeight() + 1) {
                    blockposition_mutableblockposition.move(Direction.DOWN);
                }

                if (this.canPlaceColumnOnTopOf(world, blockposition_mutableblockposition, world.getBlockState(blockposition_mutableblockposition))) {
                    while (blockposition_mutableblockposition.getY() < l) {
                        blockposition_mutableblockposition.move(Direction.UP);
                        world.setBlock(blockposition_mutableblockposition, state, 2);
                    }

                }
            }
        }

        protected void fillPillarDownOrChainUp(WorldGenLevel world, BlockState state, int x, int y, int z, BoundingBox box) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);

            if (box.isInside(blockposition_mutableblockposition)) {
                int l = blockposition_mutableblockposition.getY();
                int i1 = 1;
                boolean flag = true;

                for (boolean flag1 = true; flag || flag1; ++i1) {
                    BlockState iblockdata1;
                    boolean flag2;

                    if (flag) {
                        blockposition_mutableblockposition.setY(l - i1);
                        iblockdata1 = world.getBlockState(blockposition_mutableblockposition);
                        flag2 = this.isReplaceableByStructures(iblockdata1) && !iblockdata1.is(Blocks.LAVA);
                        if (!flag2 && this.canPlaceColumnOnTopOf(world, blockposition_mutableblockposition, iblockdata1)) {
                            MineShaftCorridor.fillColumnBetween(world, state, blockposition_mutableblockposition, l - i1 + 1, l);
                            return;
                        }

                        flag = i1 <= 20 && flag2 && blockposition_mutableblockposition.getY() > world.getMinBuildHeight() + 1;
                    }

                    if (flag1) {
                        blockposition_mutableblockposition.setY(l + i1);
                        iblockdata1 = world.getBlockState(blockposition_mutableblockposition);
                        flag2 = this.isReplaceableByStructures(iblockdata1);
                        if (!flag2 && this.canHangChainBelow(world, blockposition_mutableblockposition, iblockdata1)) {
                            world.setBlock(blockposition_mutableblockposition.setY(l + 1), this.type.getFenceState(), 2);
                            MineShaftCorridor.fillColumnBetween(world, Blocks.CHAIN.defaultBlockState(), blockposition_mutableblockposition, l + 2, l + i1);
                            return;
                        }

                        flag1 = i1 <= 50 && flag2 && blockposition_mutableblockposition.getY() < world.getMaxBuildHeight() - 1;
                    }
                }

            }
        }

        private static void fillColumnBetween(WorldGenLevel world, BlockState state, BlockPos.MutableBlockPos pos, int startY, int endY) {
            for (int k = startY; k < endY; ++k) {
                world.setBlock(pos.setY(k), state, 2);
            }

        }

        private boolean canPlaceColumnOnTopOf(LevelReader world, BlockPos pos, BlockState state) {
            return state.isFaceSturdy(world, pos, Direction.UP);
        }

        private boolean canHangChainBelow(LevelReader world, BlockPos pos, BlockState state) {
            return Block.canSupportCenter(world, pos, Direction.DOWN) && !(state.getBlock() instanceof FallingBlock);
        }

        private void placeSupport(WorldGenLevel world, BoundingBox boundingBox, int minX, int minY, int z, int maxY, int maxX, RandomSource random) {
            if (this.isSupportingBox(world, boundingBox, minX, maxX, maxY, z)) {
                BlockState iblockdata = this.type.getPlanksState();
                BlockState iblockdata1 = this.type.getFenceState();

                this.generateBox(world, boundingBox, minX, minY, z, minX, maxY - 1, z, (BlockState) iblockdata1.setValue(FenceBlock.WEST, true), MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                this.generateBox(world, boundingBox, maxX, minY, z, maxX, maxY - 1, z, (BlockState) iblockdata1.setValue(FenceBlock.EAST, true), MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                if (random.nextInt(4) == 0) {
                    this.generateBox(world, boundingBox, minX, maxY, z, minX, maxY, z, iblockdata, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                    this.generateBox(world, boundingBox, maxX, maxY, z, maxX, maxY, z, iblockdata, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                } else {
                    this.generateBox(world, boundingBox, minX, maxY, z, maxX, maxY, z, iblockdata, MineshaftPieces.MineShaftCorridor.CAVE_AIR, false);
                    this.maybeGenerateBlock(world, boundingBox, random, 0.05F, minX + 1, maxY, z - 1, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
                    this.maybeGenerateBlock(world, boundingBox, random, 0.05F, minX + 1, maxY, z + 1, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
                }

            }
        }

        private void maybePlaceCobWeb(WorldGenLevel world, BoundingBox box, RandomSource random, float threshold, int x, int y, int z) {
            if (this.isInterior(world, x, y, z, box) && random.nextFloat() < threshold && this.hasSturdyNeighbours(world, box, x, y, z, 2)) {
                this.placeBlock(world, Blocks.COBWEB.defaultBlockState(), x, y, z, box);
            }

        }

        private boolean hasSturdyNeighbours(WorldGenLevel world, BoundingBox box, int x, int y, int z, int count) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);
            int i1 = 0;
            Direction[] aenumdirection = Direction.values();
            int j1 = aenumdirection.length;

            for (int k1 = 0; k1 < j1; ++k1) {
                Direction enumdirection = aenumdirection[k1];

                blockposition_mutableblockposition.move(enumdirection);
                if (box.isInside(blockposition_mutableblockposition) && world.getBlockState(blockposition_mutableblockposition).isFaceSturdy(world, blockposition_mutableblockposition, enumdirection.getOpposite())) {
                    ++i1;
                    if (i1 >= count) {
                        return true;
                    }
                }

                blockposition_mutableblockposition.move(enumdirection.getOpposite());
            }

            return false;
        }
    }

    private abstract static class MineShaftPiece extends StructurePiece {

        protected MineshaftStructure.Type type;

        public MineShaftPiece(StructurePieceType structurePieceType, int chainLength, MineshaftStructure.Type type, BoundingBox box) {
            super(structurePieceType, chainLength, box);
            this.type = type;
        }

        public MineShaftPiece(StructurePieceType type, CompoundTag nbt) {
            super(type, nbt);
            this.type = MineshaftStructure.Type.byId(nbt.getInt("MST"));
        }

        @Override
        protected boolean canBeReplaced(LevelReader world, int x, int y, int z, BoundingBox box) {
            BlockState iblockdata = this.getBlock(world, x, y, z, box);

            return !iblockdata.is(this.type.getPlanksState().getBlock()) && !iblockdata.is(this.type.getWoodState().getBlock()) && !iblockdata.is(this.type.getFenceState().getBlock()) && !iblockdata.is(Blocks.CHAIN);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            nbt.putInt("MST", this.type.ordinal());
        }

        protected boolean isSupportingBox(BlockGetter world, BoundingBox boundingBox, int minX, int maxX, int y, int z) {
            for (int i1 = minX; i1 <= maxX; ++i1) {
                if (this.getBlock(world, i1, y + 1, z, boundingBox).isAir()) {
                    return false;
                }
            }

            return true;
        }

        protected boolean isInInvalidLocation(LevelAccessor world, BoundingBox box) {
            int i = Math.max(this.boundingBox.minX() - 1, box.minX());
            int j = Math.max(this.boundingBox.minY() - 1, box.minY());
            int k = Math.max(this.boundingBox.minZ() - 1, box.minZ());
            int l = Math.min(this.boundingBox.maxX() + 1, box.maxX());
            int i1 = Math.min(this.boundingBox.maxY() + 1, box.maxY());
            int j1 = Math.min(this.boundingBox.maxZ() + 1, box.maxZ());
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos((i + l) / 2, (j + i1) / 2, (k + j1) / 2);

            if (world.getBiome(blockposition_mutableblockposition).is(BiomeTags.MINESHAFT_BLOCKING)) {
                return true;
            } else {
                int k1;
                int l1;

                for (k1 = i; k1 <= l; ++k1) {
                    for (l1 = k; l1 <= j1; ++l1) {
                        if (world.getBlockState(blockposition_mutableblockposition.set(k1, j, l1)).liquid()) {
                            return true;
                        }

                        if (world.getBlockState(blockposition_mutableblockposition.set(k1, i1, l1)).liquid()) {
                            return true;
                        }
                    }
                }

                for (k1 = i; k1 <= l; ++k1) {
                    for (l1 = j; l1 <= i1; ++l1) {
                        if (world.getBlockState(blockposition_mutableblockposition.set(k1, l1, k)).liquid()) {
                            return true;
                        }

                        if (world.getBlockState(blockposition_mutableblockposition.set(k1, l1, j1)).liquid()) {
                            return true;
                        }
                    }
                }

                for (k1 = k; k1 <= j1; ++k1) {
                    for (l1 = j; l1 <= i1; ++l1) {
                        if (world.getBlockState(blockposition_mutableblockposition.set(i, l1, k1)).liquid()) {
                            return true;
                        }

                        if (world.getBlockState(blockposition_mutableblockposition.set(l, l1, k1)).liquid()) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        protected void setPlanksBlock(WorldGenLevel world, BoundingBox box, BlockState state, int x, int y, int z) {
            if (this.isInterior(world, x, y, z, box)) {
                BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(x, y, z);
                BlockState iblockdata1 = world.getBlockState(blockposition_mutableblockposition);

                if (!iblockdata1.isFaceSturdy(world, blockposition_mutableblockposition, Direction.UP)) {
                    world.setBlock(blockposition_mutableblockposition, state, 2);
                }

            }
        }
    }

    public static class MineShaftRoom extends MineshaftPieces.MineShaftPiece {

        private final List<BoundingBox> childEntranceBoxes = Lists.newLinkedList();

        public MineShaftRoom(int chainLength, RandomSource random, int x, int z, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_ROOM, chainLength, type, new BoundingBox(x, 50, z, x + 7 + random.nextInt(6), 54 + random.nextInt(6), z + 7 + random.nextInt(6)));
            this.type = type;
        }

        public MineShaftRoom(CompoundTag nbt) {
            super(StructurePieceType.MINE_SHAFT_ROOM, nbt);
            DataResult<List<BoundingBox>> dataresult = BoundingBox.CODEC.listOf().parse(NbtOps.INSTANCE, nbt.getList("Entrances", 11)); // CraftBukkit - decompile error
            Logger logger = MineshaftPieces.LOGGER;

            Objects.requireNonNull(logger);
            Optional<List<BoundingBox>> optional = dataresult.resultOrPartial(logger::error); // CraftBukkit - decompile error
            List list = this.childEntranceBoxes;

            Objects.requireNonNull(this.childEntranceBoxes);
            optional.ifPresent(list::addAll);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            int i = this.getGenDepth();
            int j = this.boundingBox.getYSpan() - 3 - 1;

            if (j <= 0) {
                j = 1;
            }

            int k;
            MineshaftPieces.MineShaftPiece mineshaftpieces_c;
            BoundingBox structureboundingbox;

            for (k = 0; k < this.boundingBox.getXSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getXSpan());
                if (k + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                mineshaftpieces_c = MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + k, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.minZ() - 1, Direction.NORTH, i);
                if (mineshaftpieces_c != null) {
                    structureboundingbox = mineshaftpieces_c.getBoundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(structureboundingbox.minX(), structureboundingbox.minY(), this.boundingBox.minZ(), structureboundingbox.maxX(), structureboundingbox.maxY(), this.boundingBox.minZ() + 1));
                }
            }

            for (k = 0; k < this.boundingBox.getXSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getXSpan());
                if (k + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                mineshaftpieces_c = MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + k, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.maxZ() + 1, Direction.SOUTH, i);
                if (mineshaftpieces_c != null) {
                    structureboundingbox = mineshaftpieces_c.getBoundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(structureboundingbox.minX(), structureboundingbox.minY(), this.boundingBox.maxZ() - 1, structureboundingbox.maxX(), structureboundingbox.maxY(), this.boundingBox.maxZ()));
                }
            }

            for (k = 0; k < this.boundingBox.getZSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getZSpan());
                if (k + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                mineshaftpieces_c = MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.minZ() + k, Direction.WEST, i);
                if (mineshaftpieces_c != null) {
                    structureboundingbox = mineshaftpieces_c.getBoundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.minX(), structureboundingbox.minY(), structureboundingbox.minZ(), this.boundingBox.minX() + 1, structureboundingbox.maxY(), structureboundingbox.maxZ()));
                }
            }

            for (k = 0; k < this.boundingBox.getZSpan(); k += 4) {
                k += random.nextInt(this.boundingBox.getZSpan());
                if (k + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                mineshaftpieces_c = MineshaftPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + random.nextInt(j) + 1, this.boundingBox.minZ() + k, Direction.EAST, i);
                if (mineshaftpieces_c != null) {
                    structureboundingbox = mineshaftpieces_c.getBoundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.maxX() - 1, structureboundingbox.minY(), structureboundingbox.minZ(), this.boundingBox.maxX(), structureboundingbox.maxY(), structureboundingbox.maxZ()));
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!this.isInInvalidLocation(world, chunkBox)) {
                this.generateBox(world, chunkBox, this.boundingBox.minX(), this.boundingBox.minY() + 1, this.boundingBox.minZ(), this.boundingBox.maxX(), Math.min(this.boundingBox.minY() + 3, this.boundingBox.maxY()), this.boundingBox.maxZ(), MineshaftPieces.MineShaftRoom.CAVE_AIR, MineshaftPieces.MineShaftRoom.CAVE_AIR, false);
                Iterator iterator = this.childEntranceBoxes.iterator();

                while (iterator.hasNext()) {
                    BoundingBox structureboundingbox1 = (BoundingBox) iterator.next();

                    this.generateBox(world, chunkBox, structureboundingbox1.minX(), structureboundingbox1.maxY() - 2, structureboundingbox1.minZ(), structureboundingbox1.maxX(), structureboundingbox1.maxY(), structureboundingbox1.maxZ(), MineshaftPieces.MineShaftRoom.CAVE_AIR, MineshaftPieces.MineShaftRoom.CAVE_AIR, false);
                }

                this.generateUpperHalfSphere(world, chunkBox, this.boundingBox.minX(), this.boundingBox.minY() + 4, this.boundingBox.minZ(), this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ(), MineshaftPieces.MineShaftRoom.CAVE_AIR, false);
            }
        }

        @Override
        public void move(int x, int y, int z) {
            super.move(x, y, z);
            Iterator iterator = this.childEntranceBoxes.iterator();

            while (iterator.hasNext()) {
                BoundingBox structureboundingbox = (BoundingBox) iterator.next();

                structureboundingbox.move(x, y, z);
            }

        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            DataResult<Tag> dataresult = BoundingBox.CODEC.listOf().encodeStart(NbtOps.INSTANCE, this.childEntranceBoxes); // CraftBukkit - decompile error
            Logger logger = MineshaftPieces.LOGGER;

            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbt.put("Entrances", nbtbase);
            });
        }
    }
}
