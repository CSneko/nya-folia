package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class StrongholdPieces {

    private static final int SMALL_DOOR_WIDTH = 3;
    private static final int SMALL_DOOR_HEIGHT = 3;
    private static final int MAX_DEPTH = 50;
    private static final int LOWEST_Y_POSITION = 10;
    private static final boolean CHECK_AIR = true;
    public static final int MAGIC_START_Y = 64;
    private static final StrongholdPieces.PieceWeight[] STRONGHOLD_PIECE_WEIGHTS = new StrongholdPieces.PieceWeight[]{new StrongholdPieces.PieceWeight(StrongholdPieces.Straight.class, 40, 0), new StrongholdPieces.PieceWeight(StrongholdPieces.PrisonHall.class, 5, 5), new StrongholdPieces.PieceWeight(StrongholdPieces.LeftTurn.class, 20, 0), new StrongholdPieces.PieceWeight(StrongholdPieces.RightTurn.class, 20, 0), new StrongholdPieces.PieceWeight(StrongholdPieces.RoomCrossing.class, 10, 6), new StrongholdPieces.PieceWeight(StrongholdPieces.StraightStairsDown.class, 5, 5), new StrongholdPieces.PieceWeight(StrongholdPieces.StairsDown.class, 5, 5), new StrongholdPieces.PieceWeight(StrongholdPieces.FiveCrossing.class, 5, 4), new StrongholdPieces.PieceWeight(StrongholdPieces.ChestCorridor.class, 5, 4), new StrongholdPieces.PieceWeight(StrongholdPieces.Library.class, 10, 2) {
                @Override
                public boolean doPlace(int chainLength) {
                    return super.doPlace(chainLength) && chainLength > 4;
                }
            }, new StrongholdPieces.PieceWeight(StrongholdPieces.PortalRoom.class, 20, 1) {
                @Override
                public boolean doPlace(int chainLength) {
                    return super.doPlace(chainLength) && chainLength > 5;
                }
            } }; // CraftBukkit - fix decompile styling
    private static List<StrongholdPieces.PieceWeight> currentPieces;
    static Class<? extends StrongholdPieces.StrongholdPiece> imposedPiece;
    private static int totalWeight;
    static final StrongholdPieces.SmoothStoneSelector SMOOTH_STONE_SELECTOR = new StrongholdPieces.SmoothStoneSelector();

    public StrongholdPieces() {}

    public static void resetPieces() {
        StrongholdPieces.currentPieces = Lists.newArrayList();
        StrongholdPieces.PieceWeight[] astrongholdpieces_f = StrongholdPieces.STRONGHOLD_PIECE_WEIGHTS;
        int i = astrongholdpieces_f.length;

        for (int j = 0; j < i; ++j) {
            StrongholdPieces.PieceWeight strongholdpieces_f = astrongholdpieces_f[j];

            strongholdpieces_f.placeCount = 0;
            StrongholdPieces.currentPieces.add(strongholdpieces_f);
        }

        StrongholdPieces.imposedPiece = null;
    }

    private static boolean updatePieceWeight() {
        boolean flag = false;

        StrongholdPieces.totalWeight = 0;

        StrongholdPieces.PieceWeight strongholdpieces_f;

        for (Iterator iterator = StrongholdPieces.currentPieces.iterator(); iterator.hasNext(); StrongholdPieces.totalWeight += strongholdpieces_f.weight) {
            strongholdpieces_f = (StrongholdPieces.PieceWeight) iterator.next();
            if (strongholdpieces_f.maxPlaceCount > 0 && strongholdpieces_f.placeCount < strongholdpieces_f.maxPlaceCount) {
                flag = true;
            }
        }

        return flag;
    }

    private static StrongholdPieces.StrongholdPiece findAndCreatePieceFactory(Class<? extends StrongholdPieces.StrongholdPiece> pieceType, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, @Nullable Direction orientation, int chainLength) {
        Object object = null;

        if (pieceType == StrongholdPieces.Straight.class) {
            object = StrongholdPieces.Straight.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.PrisonHall.class) {
            object = StrongholdPieces.PrisonHall.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.LeftTurn.class) {
            object = StrongholdPieces.LeftTurn.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.RightTurn.class) {
            object = StrongholdPieces.RightTurn.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.RoomCrossing.class) {
            object = StrongholdPieces.RoomCrossing.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.StraightStairsDown.class) {
            object = StrongholdPieces.StraightStairsDown.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.StairsDown.class) {
            object = StrongholdPieces.StairsDown.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.FiveCrossing.class) {
            object = StrongholdPieces.FiveCrossing.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.ChestCorridor.class) {
            object = StrongholdPieces.ChestCorridor.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.Library.class) {
            object = StrongholdPieces.Library.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (pieceType == StrongholdPieces.PortalRoom.class) {
            object = StrongholdPieces.PortalRoom.createPiece(holder, x, y, z, orientation, chainLength);
        }

        return (StrongholdPieces.StrongholdPiece) object;
    }

    private static StrongholdPieces.StrongholdPiece generatePieceFromSmallDoor(StrongholdPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
        if (!StrongholdPieces.updatePieceWeight()) {
            return null;
        } else {
            if (StrongholdPieces.imposedPiece != null) {
                StrongholdPieces.StrongholdPiece strongholdpieces_p = StrongholdPieces.findAndCreatePieceFactory(StrongholdPieces.imposedPiece, holder, random, x, y, z, orientation, chainLength);

                StrongholdPieces.imposedPiece = null;
                if (strongholdpieces_p != null) {
                    return strongholdpieces_p;
                }
            }

            int i1 = 0;

            while (i1 < 5) {
                ++i1;
                int j1 = random.nextInt(StrongholdPieces.totalWeight);
                Iterator iterator = StrongholdPieces.currentPieces.iterator();

                while (iterator.hasNext()) {
                    StrongholdPieces.PieceWeight strongholdpieces_f = (StrongholdPieces.PieceWeight) iterator.next();

                    j1 -= strongholdpieces_f.weight;
                    if (j1 < 0) {
                        if (!strongholdpieces_f.doPlace(chainLength) || strongholdpieces_f == start.previousPiece) {
                            break;
                        }

                        StrongholdPieces.StrongholdPiece strongholdpieces_p1 = StrongholdPieces.findAndCreatePieceFactory(strongholdpieces_f.pieceClass, holder, random, x, y, z, orientation, chainLength);

                        if (strongholdpieces_p1 != null) {
                            ++strongholdpieces_f.placeCount;
                            start.previousPiece = strongholdpieces_f;
                            if (!strongholdpieces_f.isValid()) {
                                StrongholdPieces.currentPieces.remove(strongholdpieces_f);
                            }

                            return strongholdpieces_p1;
                        }
                    }
                }
            }

            BoundingBox structureboundingbox = StrongholdPieces.FillerCorridor.findPieceBox(holder, random, x, y, z, orientation);

            if (structureboundingbox != null && structureboundingbox.minY() > 1) {
                return new StrongholdPieces.FillerCorridor(chainLength, structureboundingbox, orientation);
            } else {
                return null;
            }
        }
    }

    static StructurePiece generateAndAddPiece(StrongholdPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, @Nullable Direction orientation, int chainLength) {
        if (chainLength > 50) {
            return null;
        } else if (Math.abs(x - start.getBoundingBox().minX()) <= 112 && Math.abs(z - start.getBoundingBox().minZ()) <= 112) {
            StrongholdPieces.StrongholdPiece strongholdpieces_p = StrongholdPieces.generatePieceFromSmallDoor(start, holder, random, x, y, z, orientation, chainLength + 1);

            if (strongholdpieces_p != null) {
                holder.addPiece(strongholdpieces_p);
                start.pendingChildren.add(strongholdpieces_p);
            }

            return strongholdpieces_p;
        } else {
            return null;
        }
    }

    private static class PieceWeight {

        public final Class<? extends StrongholdPieces.StrongholdPiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;

        public PieceWeight(Class<? extends StrongholdPieces.StrongholdPiece> pieceType, int weight, int limit) {
            this.pieceClass = pieceType;
            this.weight = weight;
            this.maxPlaceCount = limit;
        }

        public boolean doPlace(int chainLength) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    public static class Straight extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 5;
        private static final int DEPTH = 7;
        private final boolean leftChild;
        private final boolean rightChild;

        public Straight(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
            this.leftChild = random.nextInt(2) == 0;
            this.rightChild = random.nextInt(2) == 0;
        }

        public Straight(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT, nbt);
            this.leftChild = nbt.getBoolean("Left");
            this.rightChild = nbt.getBoolean("Right");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Left", this.leftChild);
            nbt.putBoolean("Right", this.rightChild);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
            if (this.leftChild) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) start, holder, random, 1, 2);
            }

            if (this.rightChild) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) start, holder, random, 1, 2);
            }

        }

        public static StrongholdPieces.Straight createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, 7, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.Straight(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 4, 6, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 1, 0);
            this.generateSmallDoor(world, random, chunkBox, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 6);
            BlockState iblockdata = (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST);
            BlockState iblockdata1 = (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST);

            this.maybeGenerateBlock(world, chunkBox, random, 0.1F, 1, 2, 1, iblockdata);
            this.maybeGenerateBlock(world, chunkBox, random, 0.1F, 3, 2, 1, iblockdata1);
            this.maybeGenerateBlock(world, chunkBox, random, 0.1F, 1, 2, 5, iblockdata);
            this.maybeGenerateBlock(world, chunkBox, random, 0.1F, 3, 2, 5, iblockdata1);
            if (this.leftChild) {
                this.generateBox(world, chunkBox, 0, 1, 2, 0, 3, 4, StrongholdPieces.Straight.CAVE_AIR, StrongholdPieces.Straight.CAVE_AIR, false);
            }

            if (this.rightChild) {
                this.generateBox(world, chunkBox, 4, 1, 2, 4, 3, 4, StrongholdPieces.Straight.CAVE_AIR, StrongholdPieces.Straight.CAVE_AIR, false);
            }

        }
    }

    public static class PrisonHall extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 9;
        protected static final int HEIGHT = 5;
        protected static final int DEPTH = 11;

        public PrisonHall(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_PRISON_HALL, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public PrisonHall(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_PRISON_HALL, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
        }

        public static StrongholdPieces.PrisonHall createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 9, 5, 11, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.PrisonHall(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 8, 4, 10, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 1, 0);
            this.generateBox(world, chunkBox, 1, 1, 10, 3, 3, 10, StrongholdPieces.PrisonHall.CAVE_AIR, StrongholdPieces.PrisonHall.CAVE_AIR, false);
            this.generateBox(world, chunkBox, 4, 1, 1, 4, 3, 1, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 1, 3, 4, 3, 3, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 1, 7, 4, 3, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 1, 9, 4, 3, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);

            for (int i = 1; i <= 3; ++i) {
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, i, 4, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true)).setValue(IronBarsBlock.EAST, true), 4, i, 5, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, i, 6, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true), 5, i, 5, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true), 6, i, 5, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true), 7, i, 5, chunkBox);
            }

            this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, 3, 2, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true), 4, 3, 8, chunkBox);
            BlockState iblockdata = (BlockState) Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST)).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

            this.placeBlock(world, iblockdata, 4, 1, 2, chunkBox);
            this.placeBlock(world, iblockdata1, 4, 2, 2, chunkBox);
            this.placeBlock(world, iblockdata, 4, 1, 8, chunkBox);
            this.placeBlock(world, iblockdata1, 4, 2, 8, chunkBox);
        }
    }

    public static class LeftTurn extends StrongholdPieces.Turn {

        public LeftTurn(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_LEFT_TURN, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public LeftTurn(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_LEFT_TURN, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != Direction.NORTH && enumdirection != Direction.EAST) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
            } else {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
            }

        }

        public static StrongholdPieces.LeftTurn createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, 5, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.LeftTurn(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 4, 4, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 1, 0);
            Direction enumdirection = this.getOrientation();

            if (enumdirection != Direction.NORTH && enumdirection != Direction.EAST) {
                this.generateBox(world, chunkBox, 4, 1, 1, 4, 3, 3, StrongholdPieces.LeftTurn.CAVE_AIR, StrongholdPieces.LeftTurn.CAVE_AIR, false);
            } else {
                this.generateBox(world, chunkBox, 0, 1, 1, 0, 3, 3, StrongholdPieces.LeftTurn.CAVE_AIR, StrongholdPieces.LeftTurn.CAVE_AIR, false);
            }

        }
    }

    public static class RightTurn extends StrongholdPieces.Turn {

        public RightTurn(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_RIGHT_TURN, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public RightTurn(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_RIGHT_TURN, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != Direction.NORTH && enumdirection != Direction.EAST) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
            } else {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
            }

        }

        public static StrongholdPieces.RightTurn createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, 5, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.RightTurn(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 4, 4, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 1, 0);
            Direction enumdirection = this.getOrientation();

            if (enumdirection != Direction.NORTH && enumdirection != Direction.EAST) {
                this.generateBox(world, chunkBox, 0, 1, 1, 0, 3, 3, StrongholdPieces.RightTurn.CAVE_AIR, StrongholdPieces.RightTurn.CAVE_AIR, false);
            } else {
                this.generateBox(world, chunkBox, 4, 1, 1, 4, 3, 3, StrongholdPieces.RightTurn.CAVE_AIR, StrongholdPieces.RightTurn.CAVE_AIR, false);
            }

        }
    }

    public static class RoomCrossing extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 11;
        protected static final int HEIGHT = 7;
        protected static final int DEPTH = 11;
        protected final int type;

        public RoomCrossing(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_ROOM_CROSSING, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
            this.type = random.nextInt(5);
        }

        public RoomCrossing(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_ROOM_CROSSING, nbt);
            this.type = nbt.getInt("Type");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putInt("Type", this.type);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 4, 1);
            this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) start, holder, random, 1, 4);
            this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) start, holder, random, 1, 4);
        }

        public static StrongholdPieces.RoomCrossing createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -4, -1, 0, 11, 7, 11, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.RoomCrossing(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 10, 6, 10, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 4, 1, 0);
            this.generateBox(world, chunkBox, 4, 1, 10, 6, 3, 10, StrongholdPieces.RoomCrossing.CAVE_AIR, StrongholdPieces.RoomCrossing.CAVE_AIR, false);
            this.generateBox(world, chunkBox, 0, 1, 4, 0, 3, 6, StrongholdPieces.RoomCrossing.CAVE_AIR, StrongholdPieces.RoomCrossing.CAVE_AIR, false);
            this.generateBox(world, chunkBox, 10, 1, 4, 10, 3, 6, StrongholdPieces.RoomCrossing.CAVE_AIR, StrongholdPieces.RoomCrossing.CAVE_AIR, false);
            int i;

            switch (this.type) {
                case 0:
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 5, 1, 5, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 5, 2, 5, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 5, 3, 5, chunkBox);
                    this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST), 4, 3, 5, chunkBox);
                    this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), 6, 3, 5, chunkBox);
                    this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 5, 3, 4, chunkBox);
                    this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH), 5, 3, 6, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 4, 1, 4, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 4, 1, 5, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 4, 1, 6, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 6, 1, 4, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 6, 1, 5, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 6, 1, 6, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 5, 1, 4, chunkBox);
                    this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 5, 1, 6, chunkBox);
                    break;
                case 1:
                    for (i = 0; i < 5; ++i) {
                        this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 1, 3 + i, chunkBox);
                        this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 7, 1, 3 + i, chunkBox);
                        this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3 + i, 1, 3, chunkBox);
                        this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3 + i, 1, 7, chunkBox);
                    }

                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 5, 1, 5, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 5, 2, 5, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 5, 3, 5, chunkBox);
                    this.placeBlock(world, Blocks.WATER.defaultBlockState(), 5, 4, 5, chunkBox);
                    break;
                case 2:
                    for (i = 1; i <= 9; ++i) {
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 1, 3, i, chunkBox);
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 9, 3, i, chunkBox);
                    }

                    for (i = 1; i <= 9; ++i) {
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), i, 3, 1, chunkBox);
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), i, 3, 9, chunkBox);
                    }

                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 5, 1, 4, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 5, 1, 6, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 5, 3, 4, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 5, 3, 6, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 4, 1, 5, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 6, 1, 5, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 4, 3, 5, chunkBox);
                    this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 6, 3, 5, chunkBox);

                    for (i = 1; i <= 3; ++i) {
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 4, i, 4, chunkBox);
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 6, i, 4, chunkBox);
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 4, i, 6, chunkBox);
                        this.placeBlock(world, Blocks.COBBLESTONE.defaultBlockState(), 6, i, 6, chunkBox);
                    }

                    this.placeBlock(world, Blocks.WALL_TORCH.defaultBlockState(), 5, 3, 5, chunkBox);

                    for (i = 2; i <= 8; ++i) {
                        this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 2, 3, i, chunkBox);
                        this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 3, 3, i, chunkBox);
                        if (i <= 3 || i >= 7) {
                            this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 4, 3, i, chunkBox);
                            this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 5, 3, i, chunkBox);
                            this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 6, 3, i, chunkBox);
                        }

                        this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 7, 3, i, chunkBox);
                        this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 8, 3, i, chunkBox);
                    }

                    BlockState iblockdata = (BlockState) Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);

                    this.placeBlock(world, iblockdata, 9, 1, 3, chunkBox);
                    this.placeBlock(world, iblockdata, 9, 2, 3, chunkBox);
                    this.placeBlock(world, iblockdata, 9, 3, 3, chunkBox);
                    this.createChest(world, chunkBox, random, 3, 4, 8, BuiltInLootTables.STRONGHOLD_CROSSING);
            }

        }
    }

    public static class StraightStairsDown extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 8;

        public StraightStairsDown(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT_STAIRS_DOWN, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public StraightStairsDown(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_STRAIGHT_STAIRS_DOWN, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
        }

        public static StrongholdPieces.StraightStairsDown createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -7, 0, 5, 11, 8, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.StraightStairsDown(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 10, 7, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 7, 0);
            this.generateSmallDoor(world, random, chunkBox, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 7);
            BlockState iblockdata = (BlockState) Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);

            for (int i = 0; i < 6; ++i) {
                this.placeBlock(world, iblockdata, 1, 6 - i, 1 + i, chunkBox);
                this.placeBlock(world, iblockdata, 2, 6 - i, 1 + i, chunkBox);
                this.placeBlock(world, iblockdata, 3, 6 - i, 1 + i, chunkBox);
                if (i < 5) {
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 5 - i, 1 + i, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 2, 5 - i, 1 + i, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 5 - i, 1 + i, chunkBox);
                }
            }

        }
    }

    public static class StairsDown extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 5;
        private final boolean isSource;

        public StairsDown(StructurePieceType structurePieceType, int chainLength, int x, int z, Direction orientation) {
            super(structurePieceType, chainLength, makeBoundingBox(x, 64, z, orientation, 5, 11, 5));
            this.isSource = true;
            this.setOrientation(orientation);
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
        }

        public StairsDown(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_STAIRS_DOWN, chainLength, boundingBox);
            this.isSource = false;
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public StairsDown(StructurePieceType type, CompoundTag nbt) {
            super(type, nbt);
            this.isSource = nbt.getBoolean("Source");
        }

        public StairsDown(CompoundTag nbt) {
            this(StructurePieceType.STRONGHOLD_STAIRS_DOWN, nbt);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Source", this.isSource);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            if (this.isSource) {
                StrongholdPieces.imposedPiece = StrongholdPieces.FiveCrossing.class;
            }

            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
        }

        public static StrongholdPieces.StairsDown createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -7, 0, 5, 11, 5, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.StairsDown(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 10, 4, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 7, 0);
            this.generateSmallDoor(world, random, chunkBox, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 4);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 2, 6, 1, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 5, 1, chunkBox);
            this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 6, 1, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 5, 2, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 4, 3, chunkBox);
            this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 5, 3, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 2, 4, 3, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 3, 3, chunkBox);
            this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3, 4, 3, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 3, 2, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 2, 1, chunkBox);
            this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3, 3, 1, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 2, 2, 1, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 1, 1, chunkBox);
            this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 2, 1, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 1, 2, chunkBox);
            this.placeBlock(world, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 1, 1, 3, chunkBox);
        }
    }

    public static class FiveCrossing extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 10;
        protected static final int HEIGHT = 9;
        protected static final int DEPTH = 11;
        private final boolean leftLow;
        private final boolean leftHigh;
        private final boolean rightLow;
        private final boolean rightHigh;

        public FiveCrossing(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_FIVE_CROSSING, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
            this.leftLow = random.nextBoolean();
            this.leftHigh = random.nextBoolean();
            this.rightLow = random.nextBoolean();
            this.rightHigh = random.nextInt(3) > 0;
        }

        public FiveCrossing(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_FIVE_CROSSING, nbt);
            this.leftLow = nbt.getBoolean("leftLow");
            this.leftHigh = nbt.getBoolean("leftHigh");
            this.rightLow = nbt.getBoolean("rightLow");
            this.rightHigh = nbt.getBoolean("rightHigh");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("leftLow", this.leftLow);
            nbt.putBoolean("leftHigh", this.leftHigh);
            nbt.putBoolean("rightLow", this.rightLow);
            nbt.putBoolean("rightHigh", this.rightHigh);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            int i = 3;
            int j = 5;
            Direction enumdirection = this.getOrientation();

            if (enumdirection == Direction.WEST || enumdirection == Direction.NORTH) {
                i = 8 - i;
                j = 8 - j;
            }

            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 5, 1);
            if (this.leftLow) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) start, holder, random, i, 1);
            }

            if (this.leftHigh) {
                this.generateSmallDoorChildLeft((StrongholdPieces.StartPiece) start, holder, random, j, 7);
            }

            if (this.rightLow) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) start, holder, random, i, 1);
            }

            if (this.rightHigh) {
                this.generateSmallDoorChildRight((StrongholdPieces.StartPiece) start, holder, random, j, 7);
            }

        }

        public static StrongholdPieces.FiveCrossing createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -4, -3, 0, 10, 9, 11, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.FiveCrossing(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 9, 8, 10, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 4, 3, 0);
            if (this.leftLow) {
                this.generateBox(world, chunkBox, 0, 3, 1, 0, 5, 3, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            if (this.rightLow) {
                this.generateBox(world, chunkBox, 9, 3, 1, 9, 5, 3, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            if (this.leftHigh) {
                this.generateBox(world, chunkBox, 0, 5, 7, 0, 7, 9, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            if (this.rightHigh) {
                this.generateBox(world, chunkBox, 9, 5, 7, 9, 7, 9, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            }

            this.generateBox(world, chunkBox, 5, 1, 10, 7, 3, 10, StrongholdPieces.FiveCrossing.CAVE_AIR, StrongholdPieces.FiveCrossing.CAVE_AIR, false);
            this.generateBox(world, chunkBox, 1, 2, 1, 8, 2, 6, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 1, 5, 4, 4, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 8, 1, 5, 8, 4, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 1, 4, 7, 3, 4, 9, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 1, 3, 5, 3, 3, 6, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 1, 3, 4, 3, 3, 4, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 4, 6, 3, 4, 6, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 1, 7, 7, 1, 8, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 5, 1, 9, 7, 1, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 2, 7, 7, 2, 7, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 5, 7, 4, 5, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 7, 8, 5, 9, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 5, 7, 7, 5, 9, (BlockState) Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), (BlockState) Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), false);
            this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), 6, 5, 6, chunkBox);
        }
    }

    public static class ChestCorridor extends StrongholdPieces.StrongholdPiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 5;
        private static final int DEPTH = 7;
        private boolean hasPlacedChest;

        public ChestCorridor(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_CHEST_CORRIDOR, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
        }

        public ChestCorridor(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_CHEST_CORRIDOR, nbt);
            this.hasPlacedChest = nbt.getBoolean("Chest");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Chest", this.hasPlacedChest);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateSmallDoorChildForward((StrongholdPieces.StartPiece) start, holder, random, 1, 1);
        }

        public static StrongholdPieces.ChestCorridor createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainlength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, 7, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.ChestCorridor(chainlength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 4, 6, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 1, 1, 0);
            this.generateSmallDoor(world, random, chunkBox, StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING, 1, 1, 6);
            this.generateBox(world, chunkBox, 3, 1, 2, 3, 1, 4, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), false);
            this.placeBlock(world, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 1, 1, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 1, 5, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 2, 2, chunkBox);
            this.placeBlock(world, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3, 2, 4, chunkBox);

            for (int i = 2; i <= 4; ++i) {
                this.placeBlock(world, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2, 1, i, chunkBox);
            }

            if (!this.hasPlacedChest && chunkBox.isInside(this.getWorldPos(3, 2, 3))) {
                this.hasPlacedChest = true;
                this.createChest(world, chunkBox, random, 3, 2, 3, BuiltInLootTables.STRONGHOLD_CORRIDOR);
            }

        }
    }

    public static class Library extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 14;
        protected static final int HEIGHT = 6;
        protected static final int TALL_HEIGHT = 11;
        protected static final int DEPTH = 15;
        private final boolean isTall;

        public Library(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_LIBRARY, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.entryDoor = this.randomSmallDoor(random);
            this.isTall = boundingBox.getYSpan() > 6;
        }

        public Library(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_LIBRARY, nbt);
            this.isTall = nbt.getBoolean("Tall");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Tall", this.isTall);
        }

        public static StrongholdPieces.Library createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -4, -1, 0, 14, 11, 15, orientation);

            if (!StrongholdPiece.isOkBox(structureboundingbox) || holder.findCollisionPiece(structureboundingbox) != null) {
                structureboundingbox = BoundingBox.orientBox(x, y, z, -4, -1, 0, 14, 6, 15, orientation);
                if (!StrongholdPiece.isOkBox(structureboundingbox) || holder.findCollisionPiece(structureboundingbox) != null) {
                    return null;
                }
            }

            return new StrongholdPieces.Library(chainLength, random, structureboundingbox, orientation);
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            byte b0 = 11;

            if (!this.isTall) {
                b0 = 6;
            }

            this.generateBox(world, chunkBox, 0, 0, 0, 13, b0 - 1, 14, true, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, this.entryDoor, 4, 1, 0);
            this.generateMaybeBox(world, chunkBox, random, 0.07F, 2, 1, 1, 11, 4, 13, Blocks.COBWEB.defaultBlockState(), Blocks.COBWEB.defaultBlockState(), false, false);
            boolean flag = true;
            boolean flag1 = true;

            int i;

            for (i = 1; i <= 13; ++i) {
                if ((i - 1) % 4 == 0) {
                    this.generateBox(world, chunkBox, 1, 1, i, 1, 4, i, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                    this.generateBox(world, chunkBox, 12, 1, i, 12, 4, i, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                    this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), 2, 3, i, chunkBox);
                    this.placeBlock(world, (BlockState) Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST), 11, 3, i, chunkBox);
                    if (this.isTall) {
                        this.generateBox(world, chunkBox, 1, 6, i, 1, 9, i, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                        this.generateBox(world, chunkBox, 12, 6, i, 12, 9, i, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                    }
                } else {
                    this.generateBox(world, chunkBox, 1, 1, i, 1, 4, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                    this.generateBox(world, chunkBox, 12, 1, i, 12, 4, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                    if (this.isTall) {
                        this.generateBox(world, chunkBox, 1, 6, i, 1, 9, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                        this.generateBox(world, chunkBox, 12, 6, i, 12, 9, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                    }
                }
            }

            for (i = 3; i < 12; i += 2) {
                this.generateBox(world, chunkBox, 3, 1, i, 4, 3, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 6, 1, i, 7, 3, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 9, 1, i, 10, 3, i, Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), false);
            }

            if (this.isTall) {
                this.generateBox(world, chunkBox, 1, 5, 1, 3, 5, 13, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 10, 5, 1, 12, 5, 13, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 4, 5, 1, 9, 5, 2, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 4, 5, 12, 9, 5, 13, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), false);
                this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 9, 5, 11, chunkBox);
                this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 8, 5, 11, chunkBox);
                this.placeBlock(world, Blocks.OAK_PLANKS.defaultBlockState(), 9, 5, 10, chunkBox);
                BlockState iblockdata = (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
                BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

                this.generateBox(world, chunkBox, 3, 6, 3, 3, 6, 11, iblockdata1, iblockdata1, false);
                this.generateBox(world, chunkBox, 10, 6, 3, 10, 6, 9, iblockdata1, iblockdata1, false);
                this.generateBox(world, chunkBox, 4, 6, 2, 9, 6, 2, iblockdata, iblockdata, false);
                this.generateBox(world, chunkBox, 4, 6, 12, 7, 6, 12, iblockdata, iblockdata, false);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 3, 6, 2, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.EAST, true), 3, 6, 12, chunkBox);
                this.placeBlock(world, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.WEST, true), 10, 6, 2, chunkBox);

                for (int j = 0; j <= 2; ++j) {
                    this.placeBlock(world, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.WEST, true), 8 + j, 6, 12 - j, chunkBox);
                    if (j != 2) {
                        this.placeBlock(world, (BlockState) ((BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 8 + j, 6, 11 - j, chunkBox);
                    }
                }

                BlockState iblockdata2 = (BlockState) Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);

                this.placeBlock(world, iblockdata2, 10, 1, 13, chunkBox);
                this.placeBlock(world, iblockdata2, 10, 2, 13, chunkBox);
                this.placeBlock(world, iblockdata2, 10, 3, 13, chunkBox);
                this.placeBlock(world, iblockdata2, 10, 4, 13, chunkBox);
                this.placeBlock(world, iblockdata2, 10, 5, 13, chunkBox);
                this.placeBlock(world, iblockdata2, 10, 6, 13, chunkBox);
                this.placeBlock(world, iblockdata2, 10, 7, 13, chunkBox);
                boolean flag2 = true;
                boolean flag3 = true;
                BlockState iblockdata3 = (BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true);

                this.placeBlock(world, iblockdata3, 6, 9, 7, chunkBox);
                BlockState iblockdata4 = (BlockState) Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true);

                this.placeBlock(world, iblockdata4, 7, 9, 7, chunkBox);
                this.placeBlock(world, iblockdata3, 6, 8, 7, chunkBox);
                this.placeBlock(world, iblockdata4, 7, 8, 7, chunkBox);
                BlockState iblockdata5 = (BlockState) ((BlockState) iblockdata1.setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);

                this.placeBlock(world, iblockdata5, 6, 7, 7, chunkBox);
                this.placeBlock(world, iblockdata5, 7, 7, 7, chunkBox);
                this.placeBlock(world, iblockdata3, 5, 7, 7, chunkBox);
                this.placeBlock(world, iblockdata4, 8, 7, 7, chunkBox);
                this.placeBlock(world, (BlockState) iblockdata3.setValue(FenceBlock.NORTH, true), 6, 7, 6, chunkBox);
                this.placeBlock(world, (BlockState) iblockdata3.setValue(FenceBlock.SOUTH, true), 6, 7, 8, chunkBox);
                this.placeBlock(world, (BlockState) iblockdata4.setValue(FenceBlock.NORTH, true), 7, 7, 6, chunkBox);
                this.placeBlock(world, (BlockState) iblockdata4.setValue(FenceBlock.SOUTH, true), 7, 7, 8, chunkBox);
                BlockState iblockdata6 = Blocks.TORCH.defaultBlockState();

                this.placeBlock(world, iblockdata6, 5, 8, 7, chunkBox);
                this.placeBlock(world, iblockdata6, 8, 8, 7, chunkBox);
                this.placeBlock(world, iblockdata6, 6, 8, 6, chunkBox);
                this.placeBlock(world, iblockdata6, 6, 8, 8, chunkBox);
                this.placeBlock(world, iblockdata6, 7, 8, 6, chunkBox);
                this.placeBlock(world, iblockdata6, 7, 8, 8, chunkBox);
            }

            this.createChest(world, chunkBox, random, 3, 3, 5, BuiltInLootTables.STRONGHOLD_LIBRARY);
            if (this.isTall) {
                this.placeBlock(world, StrongholdPieces.Library.CAVE_AIR, 12, 9, 1, chunkBox);
                this.createChest(world, chunkBox, random, 12, 8, 1, BuiltInLootTables.STRONGHOLD_LIBRARY);
            }

        }
    }

    public static class PortalRoom extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 11;
        protected static final int HEIGHT = 8;
        protected static final int DEPTH = 16;
        private boolean hasPlacedSpawner;

        public PortalRoom(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_PORTAL_ROOM, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public PortalRoom(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_PORTAL_ROOM, nbt);
            this.hasPlacedSpawner = nbt.getBoolean("Mob");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Mob", this.hasPlacedSpawner);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            if (start != null) {
                ((StrongholdPieces.StartPiece) start).portalRoomPiece = this;
            }

        }

        public static StrongholdPieces.PortalRoom createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -4, -1, 0, 11, 8, 16, orientation);

            return StrongholdPiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new StrongholdPieces.PortalRoom(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 10, 7, 15, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(world, random, chunkBox, StrongholdPieces.StrongholdPiece.SmallDoorType.GRATES, 4, 1, 0);
            boolean flag = true;

            this.generateBox(world, chunkBox, 1, 6, 1, 1, 6, 14, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 9, 6, 1, 9, 6, 14, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 2, 6, 1, 8, 6, 2, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 2, 6, 14, 8, 6, 14, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 1, 1, 1, 2, 1, 4, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 8, 1, 1, 9, 1, 4, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 1, 1, 1, 1, 1, 3, Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 9, 1, 1, 9, 1, 3, Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 3, 1, 8, 7, 1, 12, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 1, 9, 6, 1, 11, Blocks.LAVA.defaultBlockState(), Blocks.LAVA.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)).setValue(IronBarsBlock.SOUTH, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true)).setValue(IronBarsBlock.EAST, true);

            int i;

            for (i = 3; i < 14; i += 2) {
                this.generateBox(world, chunkBox, 0, 3, i, 0, 4, i, iblockdata, iblockdata, false);
                this.generateBox(world, chunkBox, 10, 3, i, 10, 4, i, iblockdata, iblockdata, false);
            }

            for (i = 2; i < 9; i += 2) {
                this.generateBox(world, chunkBox, i, 3, 15, i, 4, 15, iblockdata1, iblockdata1, false);
            }

            BlockState iblockdata2 = (BlockState) Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);

            this.generateBox(world, chunkBox, 4, 1, 5, 6, 1, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 2, 6, 6, 2, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);
            this.generateBox(world, chunkBox, 4, 3, 7, 6, 3, 7, false, random, StrongholdPieces.SMOOTH_STONE_SELECTOR);

            for (int j = 4; j <= 6; ++j) {
                this.placeBlock(world, iblockdata2, j, 1, 4, chunkBox);
                this.placeBlock(world, iblockdata2, j, 2, 5, chunkBox);
                this.placeBlock(world, iblockdata2, j, 3, 6, chunkBox);
            }

            BlockState iblockdata3 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.NORTH);
            BlockState iblockdata4 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.SOUTH);
            BlockState iblockdata5 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.EAST);
            BlockState iblockdata6 = (BlockState) Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, Direction.WEST);
            boolean flag1 = true;
            boolean[] aboolean = new boolean[12];

            for (int k = 0; k < aboolean.length; ++k) {
                aboolean[k] = random.nextFloat() > 0.9F;
                flag1 &= aboolean[k];
            }

            this.placeBlock(world, (BlockState) iblockdata3.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[0]), 4, 3, 8, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata3.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[1]), 5, 3, 8, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata3.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[2]), 6, 3, 8, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata4.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[3]), 4, 3, 12, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata4.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[4]), 5, 3, 12, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata4.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[5]), 6, 3, 12, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata5.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[6]), 3, 3, 9, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata5.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[7]), 3, 3, 10, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata5.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[8]), 3, 3, 11, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata6.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[9]), 7, 3, 9, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata6.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[10]), 7, 3, 10, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata6.setValue(EndPortalFrameBlock.HAS_EYE, aboolean[11]), 7, 3, 11, chunkBox);
            if (flag1) {
                BlockState iblockdata7 = Blocks.END_PORTAL.defaultBlockState();

                this.placeBlock(world, iblockdata7, 4, 3, 9, chunkBox);
                this.placeBlock(world, iblockdata7, 5, 3, 9, chunkBox);
                this.placeBlock(world, iblockdata7, 6, 3, 9, chunkBox);
                this.placeBlock(world, iblockdata7, 4, 3, 10, chunkBox);
                this.placeBlock(world, iblockdata7, 5, 3, 10, chunkBox);
                this.placeBlock(world, iblockdata7, 6, 3, 10, chunkBox);
                this.placeBlock(world, iblockdata7, 4, 3, 11, chunkBox);
                this.placeBlock(world, iblockdata7, 5, 3, 11, chunkBox);
                this.placeBlock(world, iblockdata7, 6, 3, 11, chunkBox);
            }

            if (!this.hasPlacedSpawner) {
                BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(5, 3, 6);

                if (chunkBox.isInside(blockposition_mutableblockposition)) {
                    this.hasPlacedSpawner = true;
                    // CraftBukkit start
                    /*
                    generatoraccessseed.setBlock(blockposition_mutableblockposition, Blocks.SPAWNER.defaultBlockState(), 2);
                    TileEntity tileentity = generatoraccessseed.getBlockEntity(blockposition_mutableblockposition);

                    if (tileentity instanceof TileEntityMobSpawner) {
                        TileEntityMobSpawner tileentitymobspawner = (TileEntityMobSpawner) tileentity;

                        tileentitymobspawner.setEntityId(EntityTypes.SILVERFISH, randomsource);
                    }
                    */
                    this.placeCraftSpawner(world, blockposition_mutableblockposition, org.bukkit.entity.EntityType.SILVERFISH, 2);
                    // CraftBukkit end
                }
            }

        }
    }

    private abstract static class StrongholdPiece extends StructurePiece {

        protected StrongholdPieces.StrongholdPiece.SmallDoorType entryDoor;

        protected StrongholdPiece(StructurePieceType type, int length, BoundingBox boundingBox) {
            super(type, length, boundingBox);
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
        }

        public StrongholdPiece(StructurePieceType type, CompoundTag nbt) {
            super(type, nbt);
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
            this.entryDoor = StrongholdPieces.StrongholdPiece.SmallDoorType.valueOf(nbt.getString("EntryDoor"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            nbt.putString("EntryDoor", this.entryDoor.name());
        }

        protected void generateSmallDoor(WorldGenLevel world, RandomSource random, BoundingBox boundingBox, StrongholdPieces.StrongholdPiece.SmallDoorType type, int x, int y, int z) {
            switch (type) {
                case OPENING:
                    this.generateBox(world, boundingBox, x, y, z, x + 3 - 1, y + 3 - 1, z, StrongholdPieces.StrongholdPiece.CAVE_AIR, StrongholdPieces.StrongholdPiece.CAVE_AIR, false);
                    break;
                case WOOD_DOOR:
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x, y, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x, y + 1, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x, y + 2, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 1, y + 2, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 2, y + 2, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 2, y + 1, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 2, y, z, boundingBox);
                    this.placeBlock(world, Blocks.OAK_DOOR.defaultBlockState(), x + 1, y, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x + 1, y + 1, z, boundingBox);
                    break;
                case GRATES:
                    this.placeBlock(world, Blocks.CAVE_AIR.defaultBlockState(), x + 1, y, z, boundingBox);
                    this.placeBlock(world, Blocks.CAVE_AIR.defaultBlockState(), x + 1, y + 1, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true), x, y, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true), x, y + 1, z, boundingBox);
                    this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true), x, y + 2, z, boundingBox);
                    this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true), x + 1, y + 2, z, boundingBox);
                    this.placeBlock(world, (BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true), x + 2, y + 2, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true), x + 2, y + 1, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true), x + 2, y, z, boundingBox);
                    break;
                case IRON_DOOR:
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x, y, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x, y + 1, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x, y + 2, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 1, y + 2, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 2, y + 2, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 2, y + 1, z, boundingBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), x + 2, y, z, boundingBox);
                    this.placeBlock(world, Blocks.IRON_DOOR.defaultBlockState(), x + 1, y, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), x + 1, y + 1, z, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH), x + 2, y + 1, z + 1, boundingBox);
                    this.placeBlock(world, (BlockState) Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH), x + 2, y + 1, z - 1, boundingBox);
            }

        }

        protected StrongholdPieces.StrongholdPiece.SmallDoorType randomSmallDoor(RandomSource random) {
            int i = random.nextInt(5);

            switch (i) {
                case 0:
                case 1:
                default:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.OPENING;
                case 2:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.WOOD_DOOR;
                case 3:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.GRATES;
                case 4:
                    return StrongholdPieces.StrongholdPiece.SmallDoorType.IRON_DOOR;
            }
        }

        @Nullable
        protected StructurePiece generateSmallDoorChildForward(StrongholdPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int leftRightOffset, int heightOffset) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() - 1, enumdirection, this.getGenDepth());
                    case SOUTH:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.maxZ() + 1, enumdirection, this.getGenDepth());
                    case WEST:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, enumdirection, this.getGenDepth());
                    case EAST:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, enumdirection, this.getGenDepth());
                }
            }

            return null;
        }

        @Nullable
        protected StructurePiece generateSmallDoorChildLeft(StrongholdPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int heightOffset, int leftRightOffset) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.WEST, this.getGenDepth());
                    case SOUTH:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.WEST, this.getGenDepth());
                    case WEST:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth());
                    case EAST:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth());
                }
            }

            return null;
        }

        @Nullable
        protected StructurePiece generateSmallDoorChildRight(StrongholdPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int heightOffset, int leftRightOffset) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.EAST, this.getGenDepth());
                    case SOUTH:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.EAST, this.getGenDepth());
                    case WEST:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth());
                    case EAST:
                        return StrongholdPieces.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth());
                }
            }

            return null;
        }

        protected static boolean isOkBox(BoundingBox boundingBox) {
            return boundingBox != null && boundingBox.minY() > 10;
        }

        protected static enum SmallDoorType {

            OPENING, WOOD_DOOR, GRATES, IRON_DOOR;

            private SmallDoorType() {}
        }
    }

    public static class StartPiece extends StrongholdPieces.StairsDown {

        public StrongholdPieces.PieceWeight previousPiece;
        @Nullable
        public StrongholdPieces.PortalRoom portalRoomPiece;
        public final List<StructurePiece> pendingChildren = Lists.newArrayList();

        public StartPiece(RandomSource random, int i, int j) {
            super(StructurePieceType.STRONGHOLD_START, 0, i, j, getRandomHorizontalDirection(random));
        }

        public StartPiece(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_START, nbt);
        }

        @Override
        public BlockPos getLocatorPosition() {
            return this.portalRoomPiece != null ? this.portalRoomPiece.getLocatorPosition() : super.getLocatorPosition();
        }
    }

    public static class FillerCorridor extends StrongholdPieces.StrongholdPiece {

        private final int steps;

        public FillerCorridor(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.STRONGHOLD_FILLER_CORRIDOR, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.steps = orientation != Direction.NORTH && orientation != Direction.SOUTH ? boundingBox.getXSpan() : boundingBox.getZSpan();
        }

        public FillerCorridor(CompoundTag nbt) {
            super(StructurePieceType.STRONGHOLD_FILLER_CORRIDOR, nbt);
            this.steps = nbt.getInt("Steps");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putInt("Steps", this.steps);
        }

        public static BoundingBox findPieceBox(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation) {
            boolean flag = true;
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, 4, orientation);
            StructurePiece structurepiece = holder.findCollisionPiece(structureboundingbox);

            if (structurepiece == null) {
                return null;
            } else {
                if (structurepiece.getBoundingBox().minY() == structureboundingbox.minY()) {
                    for (int l = 2; l >= 1; --l) {
                        structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, l, orientation);
                        if (!structurepiece.getBoundingBox().intersects(structureboundingbox)) {
                            return BoundingBox.orientBox(x, y, z, -1, -1, 0, 5, 5, l + 1, orientation);
                        }
                    }
                }

                return null;
            }
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            for (int i = 0; i < this.steps; ++i) {
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 0, 0, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 0, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 2, 0, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 0, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 4, 0, i, chunkBox);

                for (int j = 1; j <= 3; ++j) {
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 0, j, i, chunkBox);
                    this.placeBlock(world, Blocks.CAVE_AIR.defaultBlockState(), 1, j, i, chunkBox);
                    this.placeBlock(world, Blocks.CAVE_AIR.defaultBlockState(), 2, j, i, chunkBox);
                    this.placeBlock(world, Blocks.CAVE_AIR.defaultBlockState(), 3, j, i, chunkBox);
                    this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 4, j, i, chunkBox);
                }

                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 0, 4, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 1, 4, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 2, 4, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 3, 4, i, chunkBox);
                this.placeBlock(world, Blocks.STONE_BRICKS.defaultBlockState(), 4, 4, i, chunkBox);
            }

        }
    }

    private static class SmoothStoneSelector extends StructurePiece.BlockSelector {

        SmoothStoneSelector() {}

        @Override
        public void next(RandomSource random, int x, int y, int z, boolean placeBlock) {
            if (placeBlock) {
                float f = random.nextFloat();

                if (f < 0.2F) {
                    this.next = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                } else if (f < 0.5F) {
                    this.next = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                } else if (f < 0.55F) {
                    this.next = Blocks.INFESTED_STONE_BRICKS.defaultBlockState();
                } else {
                    this.next = Blocks.STONE_BRICKS.defaultBlockState();
                }
            } else {
                this.next = Blocks.CAVE_AIR.defaultBlockState();
            }

        }
    }

    public abstract static class Turn extends StrongholdPieces.StrongholdPiece {

        protected static final int WIDTH = 5;
        protected static final int HEIGHT = 5;
        protected static final int DEPTH = 5;

        protected Turn(StructurePieceType type, int length, BoundingBox boundingBox) {
            super(type, length, boundingBox);
        }

        public Turn(StructurePieceType type, CompoundTag nbt) {
            super(type, nbt);
        }
    }
}
