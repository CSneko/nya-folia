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
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class NetherFortressPieces {

    private static final int MAX_DEPTH = 30;
    private static final int LOWEST_Y_POSITION = 10;
    public static final int MAGIC_START_Y = 64;
    static final NetherFortressPieces.PieceWeight[] BRIDGE_PIECE_WEIGHTS = new NetherFortressPieces.PieceWeight[]{new NetherFortressPieces.PieceWeight(NetherFortressPieces.BridgeStraight.class, 30, 0, true), new NetherFortressPieces.PieceWeight(NetherFortressPieces.BridgeCrossing.class, 10, 4), new NetherFortressPieces.PieceWeight(NetherFortressPieces.RoomCrossing.class, 10, 4), new NetherFortressPieces.PieceWeight(NetherFortressPieces.StairsRoom.class, 10, 3), new NetherFortressPieces.PieceWeight(NetherFortressPieces.MonsterThrone.class, 5, 2), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleEntrance.class, 5, 1)};
    static final NetherFortressPieces.PieceWeight[] CASTLE_PIECE_WEIGHTS = new NetherFortressPieces.PieceWeight[]{new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorPiece.class, 25, 0, true), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorCrossingPiece.class, 15, 5), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorRightTurnPiece.class, 5, 10), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.class, 5, 10), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleCorridorStairsPiece.class, 10, 3, true), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleCorridorTBalconyPiece.class, 7, 2), new NetherFortressPieces.PieceWeight(NetherFortressPieces.CastleStalkRoom.class, 5, 2)};

    public NetherFortressPieces() {}

    static NetherFortressPieces.NetherBridgePiece findAndCreateBridgePieceFactory(NetherFortressPieces.PieceWeight pieceData, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
        Class<? extends NetherFortressPieces.NetherBridgePiece> oclass = pieceData.pieceClass;
        Object object = null;

        if (oclass == NetherFortressPieces.BridgeStraight.class) {
            object = NetherFortressPieces.BridgeStraight.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.BridgeCrossing.class) {
            object = NetherFortressPieces.BridgeCrossing.createPiece(holder, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.RoomCrossing.class) {
            object = NetherFortressPieces.RoomCrossing.createPiece(holder, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.StairsRoom.class) {
            object = NetherFortressPieces.StairsRoom.createPiece(holder, x, y, z, chainLength, orientation);
        } else if (oclass == NetherFortressPieces.MonsterThrone.class) {
            object = NetherFortressPieces.MonsterThrone.createPiece(holder, x, y, z, chainLength, orientation);
        } else if (oclass == NetherFortressPieces.CastleEntrance.class) {
            object = NetherFortressPieces.CastleEntrance.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorPiece.class) {
            object = NetherFortressPieces.CastleSmallCorridorPiece.createPiece(holder, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorRightTurnPiece.class) {
            object = NetherFortressPieces.CastleSmallCorridorRightTurnPiece.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.class) {
            object = NetherFortressPieces.CastleSmallCorridorLeftTurnPiece.createPiece(holder, random, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleCorridorStairsPiece.class) {
            object = NetherFortressPieces.CastleCorridorStairsPiece.createPiece(holder, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleCorridorTBalconyPiece.class) {
            object = NetherFortressPieces.CastleCorridorTBalconyPiece.createPiece(holder, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleSmallCorridorCrossingPiece.class) {
            object = NetherFortressPieces.CastleSmallCorridorCrossingPiece.createPiece(holder, x, y, z, orientation, chainLength);
        } else if (oclass == NetherFortressPieces.CastleStalkRoom.class) {
            object = NetherFortressPieces.CastleStalkRoom.createPiece(holder, x, y, z, orientation, chainLength);
        }

        return (NetherFortressPieces.NetherBridgePiece) object;
    }

    private static class PieceWeight {

        public final Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;
        public final boolean allowInRow;

        public PieceWeight(Class<? extends NetherFortressPieces.NetherBridgePiece> pieceType, int weight, int limit, boolean repeatable) {
            this.pieceClass = pieceType;
            this.weight = weight;
            this.maxPlaceCount = limit;
            this.allowInRow = repeatable;
        }

        public PieceWeight(Class<? extends NetherFortressPieces.NetherBridgePiece> pieceType, int weight, int limit) {
            this(pieceType, weight, limit, false);
        }

        public boolean doPlace(int chainLength) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    public static class BridgeStraight extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 19;

        public BridgeStraight(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public BridgeStraight(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 1, 3, false);
        }

        public static NetherFortressPieces.BridgeStraight createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -3, 0, 5, 10, 19, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.BridgeStraight(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 3, 0, 4, 4, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 5, 0, 3, 7, 18, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 0, 0, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 5, 0, 4, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 4, 2, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 13, 4, 2, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 0, 15, 4, 1, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 18 - j, chunkBox);
                }
            }

            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);
            BlockState iblockdata1 = (BlockState) iblockdata.setValue(FenceBlock.EAST, true);
            BlockState iblockdata2 = (BlockState) iblockdata.setValue(FenceBlock.WEST, true);

            this.generateBox(world, chunkBox, 0, 1, 1, 0, 4, 1, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 0, 3, 4, 0, 4, 4, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 0, 3, 14, 0, 4, 14, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 0, 1, 17, 0, 4, 17, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 4, 1, 1, 4, 4, 1, iblockdata2, iblockdata2, false);
            this.generateBox(world, chunkBox, 4, 3, 4, 4, 4, 4, iblockdata2, iblockdata2, false);
            this.generateBox(world, chunkBox, 4, 3, 14, 4, 4, 14, iblockdata2, iblockdata2, false);
            this.generateBox(world, chunkBox, 4, 1, 17, 4, 4, 17, iblockdata2, iblockdata2, false);
        }
    }

    public static class BridgeCrossing extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 19;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 19;

        public BridgeCrossing(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        protected BridgeCrossing(int x, int z, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, 0, StructurePiece.makeBoundingBox(x, 64, z, orientation, 19, 10, 19));
            this.setOrientation(orientation);
        }

        protected BridgeCrossing(StructurePieceType type, CompoundTag nbt) {
            super(type, nbt);
        }

        public BridgeCrossing(CompoundTag nbt) {
            this(StructurePieceType.NETHER_FORTRESS_BRIDGE_CROSSING, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 8, 3, false);
            this.generateChildLeft((NetherFortressPieces.StartPiece) start, holder, random, 3, 8, false);
            this.generateChildRight((NetherFortressPieces.StartPiece) start, holder, random, 3, 8, false);
        }

        public static NetherFortressPieces.BridgeCrossing createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -8, -3, 0, 19, 10, 19, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.BridgeCrossing(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 7, 3, 0, 11, 4, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 3, 7, 18, 4, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 0, 10, 7, 18, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 8, 18, 7, 10, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 7, 5, 0, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 7, 5, 11, 7, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 11, 5, 0, 11, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 11, 5, 11, 11, 5, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 7, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 11, 5, 7, 18, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 11, 7, 5, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 11, 5, 11, 18, 5, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 7, 2, 0, 11, 2, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 7, 2, 13, 11, 2, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 7, 0, 0, 11, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 7, 0, 15, 11, 1, 18, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            int i;
            int j;

            for (i = 7; i <= 11; ++i) {
                for (j = 0; j <= 2; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 18 - j, chunkBox);
                }
            }

            this.generateBox(world, chunkBox, 0, 2, 7, 5, 2, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 13, 2, 7, 18, 2, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 0, 7, 3, 1, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 15, 0, 7, 18, 1, 11, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (i = 0; i <= 2; ++i) {
                for (j = 7; j <= 11; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), 18 - i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class RoomCrossing extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 7;
        private static final int HEIGHT = 9;
        private static final int DEPTH = 7;

        public RoomCrossing(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_ROOM_CROSSING, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public RoomCrossing(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_ROOM_CROSSING, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 2, 0, false);
            this.generateChildLeft((NetherFortressPieces.StartPiece) start, holder, random, 0, 2, false);
            this.generateChildRight((NetherFortressPieces.StartPiece) start, holder, random, 0, 2, false);
        }

        public static NetherFortressPieces.RoomCrossing createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 9, 7, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.RoomCrossing(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 6, 7, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 1, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 6, 1, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 2, 0, 6, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 2, 6, 6, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 0, 6, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 5, 0, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 2, 0, 6, 6, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 2, 5, 6, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(world, chunkBox, 2, 6, 0, 4, 6, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 0, 4, 5, 0, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 2, 6, 6, 4, 6, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 6, 4, 5, 6, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 0, 6, 2, 0, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 2, 0, 5, 4, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 6, 6, 2, 6, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 5, 2, 6, 5, 4, iblockdata1, iblockdata1, false);

            for (int i = 0; i <= 6; ++i) {
                for (int j = 0; j <= 6; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class StairsRoom extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 7;
        private static final int HEIGHT = 11;
        private static final int DEPTH = 7;

        public StairsRoom(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_STAIRS_ROOM, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public StairsRoom(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_STAIRS_ROOM, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildRight((NetherFortressPieces.StartPiece) start, holder, random, 6, 2, false);
        }

        public static NetherFortressPieces.StairsRoom createPiece(StructurePieceAccessor holder, int x, int y, int z, int chainlength, Direction orientation) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 11, 7, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.StairsRoom(chainlength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 6, 10, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 1, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 2, 0, 6, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 1, 0, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 2, 1, 6, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 2, 6, 5, 8, 6, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(world, chunkBox, 0, 3, 2, 0, 5, 4, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 6, 3, 2, 6, 5, 2, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 6, 3, 4, 6, 5, 4, iblockdata1, iblockdata1, false);
            this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), 5, 2, 5, chunkBox);
            this.generateBox(world, chunkBox, 4, 2, 5, 4, 3, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 3, 2, 5, 3, 4, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 2, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 2, 5, 1, 6, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 7, 1, 5, 7, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 8, 2, 6, 8, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 6, 0, 4, 8, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 0, 4, 5, 0, iblockdata, iblockdata, false);

            for (int i = 0; i <= 6; ++i) {
                for (int j = 0; j <= 6; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class MonsterThrone extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 7;
        private static final int HEIGHT = 8;
        private static final int DEPTH = 9;
        private boolean hasPlacedSpawner;

        public MonsterThrone(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_MONSTER_THRONE, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public MonsterThrone(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_MONSTER_THRONE, nbt);
            this.hasPlacedSpawner = nbt.getBoolean("Mob");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Mob", this.hasPlacedSpawner);
        }

        public static NetherFortressPieces.MonsterThrone createPiece(StructurePieceAccessor holder, int x, int y, int z, int chainLength, Direction orientation) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -2, 0, 0, 7, 8, 9, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.MonsterThrone(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 2, 0, 6, 7, 7, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 0, 0, 5, 1, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 2, 1, 5, 2, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 3, 2, 5, 3, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 4, 3, 5, 4, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 2, 0, 1, 4, 2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 2, 0, 5, 4, 2, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 5, 2, 1, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 5, 2, 5, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 3, 0, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 5, 3, 6, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 5, 8, 5, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.placeBlock(world, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 1, 6, 3, chunkBox);
            this.placeBlock(world, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 5, 6, 3, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)).setValue(FenceBlock.NORTH, true), 0, 6, 3, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.NORTH, true), 6, 6, 3, chunkBox);
            this.generateBox(world, chunkBox, 0, 6, 4, 0, 6, 7, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 6, 6, 4, 6, 6, 7, iblockdata1, iblockdata1, false);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)).setValue(FenceBlock.SOUTH, true), 0, 6, 8, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.SOUTH, true), 6, 6, 8, chunkBox);
            this.generateBox(world, chunkBox, 1, 6, 8, 5, 6, 8, iblockdata, iblockdata, false);
            this.placeBlock(world, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 1, 7, 8, chunkBox);
            this.generateBox(world, chunkBox, 2, 7, 8, 4, 7, 8, iblockdata, iblockdata, false);
            this.placeBlock(world, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 5, 7, 8, chunkBox);
            this.placeBlock(world, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true), 2, 8, 8, chunkBox);
            this.placeBlock(world, iblockdata, 3, 8, 8, chunkBox);
            this.placeBlock(world, (BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true), 4, 8, 8, chunkBox);
            if (!this.hasPlacedSpawner) {
                BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(3, 5, 5);

                if (chunkBox.isInside(blockposition_mutableblockposition)) {
                    this.hasPlacedSpawner = true;
                    // CraftBukkit start
                    /*
                    generatoraccessseed.setBlock(blockposition_mutableblockposition, Blocks.SPAWNER.defaultBlockState(), 2);
                    TileEntity tileentity = generatoraccessseed.getBlockEntity(blockposition_mutableblockposition);

                    if (tileentity instanceof TileEntityMobSpawner) {
                        TileEntityMobSpawner tileentitymobspawner = (TileEntityMobSpawner) tileentity;

                        tileentitymobspawner.setEntityId(EntityTypes.BLAZE, randomsource);
                    }
                    */
                    this.placeCraftSpawner(world, blockposition_mutableblockposition, org.bukkit.entity.EntityType.BLAZE, 2);
                    // CraftBukkit end
                }
            }

            for (int i = 0; i <= 6; ++i) {
                for (int j = 0; j <= 6; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class CastleEntrance extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 13;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 13;

        public CastleEntrance(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_ENTRANCE, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public CastleEntrance(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_ENTRANCE, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 5, 3, true);
        }

        public static NetherFortressPieces.CastleEntrance createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -5, -3, 0, 13, 14, 13, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleEntrance(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 8, 0, 7, 8, 0, Blocks.NETHER_BRICK_FENCE.defaultBlockState(), Blocks.NETHER_BRICK_FENCE.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            int i;

            for (i = 1; i <= 11; i += 2) {
                this.generateBox(world, chunkBox, i, 10, 0, i, 11, 0, iblockdata, iblockdata, false);
                this.generateBox(world, chunkBox, i, 10, 12, i, 11, 12, iblockdata, iblockdata, false);
                this.generateBox(world, chunkBox, 0, 10, i, 0, 11, i, iblockdata1, iblockdata1, false);
                this.generateBox(world, chunkBox, 12, 10, i, 12, 11, i, iblockdata1, iblockdata1, false);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 0, chunkBox);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 12, chunkBox);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), 0, 13, i, chunkBox);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), 12, 13, i, chunkBox);
                if (i != 11) {
                    this.placeBlock(world, iblockdata, i + 1, 13, 0, chunkBox);
                    this.placeBlock(world, iblockdata, i + 1, 13, 12, chunkBox);
                    this.placeBlock(world, iblockdata1, 0, 13, i + 1, chunkBox);
                    this.placeBlock(world, iblockdata1, 12, 13, i + 1, chunkBox);
                }
            }

            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 0, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 12, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 12, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 0, chunkBox);

            for (i = 3; i <= 9; i += 2) {
                this.generateBox(world, chunkBox, 1, 7, i, 1, 8, i, (BlockState) iblockdata1.setValue(FenceBlock.WEST, true), (BlockState) iblockdata1.setValue(FenceBlock.WEST, true), false);
                this.generateBox(world, chunkBox, 11, 7, i, 11, 8, i, (BlockState) iblockdata1.setValue(FenceBlock.EAST, true), (BlockState) iblockdata1.setValue(FenceBlock.EAST, true), false);
            }

            this.generateBox(world, chunkBox, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            int j;

            for (i = 4; i <= 8; ++i) {
                for (j = 0; j <= 2; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, 12 - j, chunkBox);
                }
            }

            for (i = 0; i <= 2; ++i) {
                for (j = 4; j <= 8; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), 12 - i, -1, j, chunkBox);
                }
            }

            this.generateBox(world, chunkBox, 5, 5, 5, 7, 5, 7, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 1, 6, 6, 4, 6, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), 6, 0, 6, chunkBox);
            this.placeBlock(world, Blocks.LAVA.defaultBlockState(), 6, 5, 6, chunkBox);
            BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(6, 5, 6);

            if (chunkBox.isInside(blockposition_mutableblockposition)) {
                world.scheduleTick(blockposition_mutableblockposition, (Fluid) Fluids.LAVA, 0);
            }

        }
    }

    public static class CastleSmallCorridorPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;

        public CastleSmallCorridorPiece(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public CastleSmallCorridorPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 1, 0, true);
        }

        public static NetherFortressPieces.CastleSmallCorridorPiece createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorPiece(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(world, chunkBox, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 3, 1, 0, 4, 1, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 0, 3, 3, 0, 4, 3, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 4, 3, 1, 4, 4, 1, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 4, 3, 3, 4, 4, 3, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class CastleSmallCorridorRightTurnPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;
        private boolean isNeedingChest;

        public CastleSmallCorridorRightTurnPiece(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_RIGHT_TURN, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        public CastleSmallCorridorRightTurnPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_RIGHT_TURN, nbt);
            this.isNeedingChest = nbt.getBoolean("Chest");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Chest", this.isNeedingChest);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildRight((NetherFortressPieces.StartPiece) start, holder, random, 0, 1, true);
        }

        public static NetherFortressPieces.CastleSmallCorridorRightTurnPiece createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorRightTurnPiece(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(world, chunkBox, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 3, 1, 0, 4, 1, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 0, 3, 3, 0, 4, 3, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 3, 4, 1, 4, 4, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 3, 3, 4, 3, 4, 4, iblockdata, iblockdata, false);
            if (this.isNeedingChest && chunkBox.isInside(this.getWorldPos(1, 2, 3))) {
                this.isNeedingChest = false;
                this.createChest(world, chunkBox, random, 1, 2, 3, BuiltInLootTables.NETHER_BRIDGE);
            }

            this.generateBox(world, chunkBox, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class CastleSmallCorridorLeftTurnPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;
        private boolean isNeedingChest;

        public CastleSmallCorridorLeftTurnPiece(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_LEFT_TURN, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        public CastleSmallCorridorLeftTurnPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_LEFT_TURN, nbt);
            this.isNeedingChest = nbt.getBoolean("Chest");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putBoolean("Chest", this.isNeedingChest);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildLeft((NetherFortressPieces.StartPiece) start, holder, random, 0, 1, true);
        }

        public static NetherFortressPieces.CastleSmallCorridorLeftTurnPiece createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorLeftTurnPiece(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            this.generateBox(world, chunkBox, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 3, 1, 4, 4, 1, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 4, 3, 3, 4, 4, 3, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 4, 3, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 3, 4, 1, 4, 4, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 3, 3, 4, 3, 4, 4, iblockdata, iblockdata, false);
            if (this.isNeedingChest && chunkBox.isInside(this.getWorldPos(3, 2, 3))) {
                this.isNeedingChest = false;
                this.createChest(world, chunkBox, random, 3, 2, 3, BuiltInLootTables.NETHER_BRIDGE);
            }

            this.generateBox(world, chunkBox, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class CastleCorridorStairsPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 10;

        public CastleCorridorStairsPiece(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_STAIRS, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public CastleCorridorStairsPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_STAIRS, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 1, 0, true);
        }

        public static NetherFortressPieces.CastleCorridorStairsPiece createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -7, 0, 5, 14, 10, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleCorridorStairsPiece(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockState iblockdata = (BlockState) Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);

            for (int i = 0; i <= 9; ++i) {
                int j = Math.max(1, 7 - i);
                int k = Math.min(Math.max(j + 5, 14 - i), 13);
                int l = i;

                this.generateBox(world, chunkBox, 0, 0, i, 4, j, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 1, j + 1, i, 3, k - 1, i, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
                if (i <= 6) {
                    this.placeBlock(world, iblockdata, 1, j + 1, i, chunkBox);
                    this.placeBlock(world, iblockdata, 2, j + 1, i, chunkBox);
                    this.placeBlock(world, iblockdata, 3, j + 1, i, chunkBox);
                }

                this.generateBox(world, chunkBox, 0, k, i, 4, k, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 0, j + 1, i, 0, k - 1, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                this.generateBox(world, chunkBox, 4, j + 1, i, 4, k - 1, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                if ((i & 1) == 0) {
                    this.generateBox(world, chunkBox, 0, j + 2, i, 0, j + 3, i, iblockdata1, iblockdata1, false);
                    this.generateBox(world, chunkBox, 4, j + 2, i, 4, j + 3, i, iblockdata1, iblockdata1, false);
                }

                for (int i1 = 0; i1 <= 4; ++i1) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i1, -1, l, chunkBox);
                }
            }

        }
    }

    public static class CastleCorridorTBalconyPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 9;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 9;

        public CastleCorridorTBalconyPiece(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_T_BALCONY, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public CastleCorridorTBalconyPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_CORRIDOR_T_BALCONY, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            byte b0 = 1;
            Direction enumdirection = this.getOrientation();

            if (enumdirection == Direction.WEST || enumdirection == Direction.NORTH) {
                b0 = 5;
            }

            this.generateChildLeft((NetherFortressPieces.StartPiece) start, holder, random, 0, b0, random.nextInt(8) > 0);
            this.generateChildRight((NetherFortressPieces.StartPiece) start, holder, random, 0, b0, random.nextInt(8) > 0);
        }

        public static NetherFortressPieces.CastleCorridorTBalconyPiece createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -3, 0, 0, 9, 7, 9, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleCorridorTBalconyPiece(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);

            this.generateBox(world, chunkBox, 0, 0, 0, 8, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 8, 5, 8, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 6, 0, 8, 6, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 2, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 2, 0, 8, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 3, 0, 1, 4, 0, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 7, 3, 0, 7, 4, 0, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 0, 2, 4, 8, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 1, 4, 2, 2, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 1, 4, 7, 2, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 3, 8, 7, 3, 8, iblockdata1, iblockdata1, false);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)).setValue(FenceBlock.SOUTH, true), 0, 3, 8, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.SOUTH, true), 8, 3, 8, chunkBox);
            this.generateBox(world, chunkBox, 0, 3, 6, 0, 3, 7, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 8, 3, 6, 8, 3, 7, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 0, 3, 4, 0, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 3, 4, 8, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 3, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 6, 3, 5, 7, 5, 5, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 4, 5, 1, 5, 5, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 7, 4, 5, 7, 5, 5, iblockdata1, iblockdata1, false);

            for (int i = 0; i <= 5; ++i) {
                for (int j = 0; j <= 8; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), j, -1, i, chunkBox);
                }
            }

        }
    }

    public static class CastleSmallCorridorCrossingPiece extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 7;
        private static final int DEPTH = 5;

        public CastleSmallCorridorCrossingPiece(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_CROSSING, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public CastleSmallCorridorCrossingPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_SMALL_CORRIDOR_CROSSING, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 1, 0, true);
            this.generateChildLeft((NetherFortressPieces.StartPiece) start, holder, random, 0, 1, true);
            this.generateChildRight((NetherFortressPieces.StartPiece) start, holder, random, 0, 1, true);
        }

        public static NetherFortressPieces.CastleSmallCorridorCrossingPiece createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, 0, 0, 5, 7, 5, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleSmallCorridorCrossingPiece(chainLength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 4, 0, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (int i = 0; i <= 4; ++i) {
                for (int j = 0; j <= 4; ++j) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, -1, j, chunkBox);
                }
            }

        }
    }

    public static class CastleStalkRoom extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 13;
        private static final int HEIGHT = 14;
        private static final int DEPTH = 13;

        public CastleStalkRoom(int chainLength, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_STALK_ROOM, chainLength, boundingBox);
            this.setOrientation(orientation);
        }

        public CastleStalkRoom(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_CASTLE_STALK_ROOM, nbt);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor holder, RandomSource random) {
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 5, 3, true);
            this.generateChildForward((NetherFortressPieces.StartPiece) start, holder, random, 5, 11, true);
        }

        public static NetherFortressPieces.CastleStalkRoom createPiece(StructurePieceAccessor holder, int x, int y, int z, Direction orientation, int chainlength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -5, -3, 0, 13, 14, 13, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.CastleStalkRoom(chainlength, structureboundingbox, orientation) : null;
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            this.generateBox(world, chunkBox, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState iblockdata = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.WEST, true)).setValue(FenceBlock.EAST, true);
            BlockState iblockdata1 = (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.SOUTH, true);
            BlockState iblockdata2 = (BlockState) iblockdata1.setValue(FenceBlock.WEST, true);
            BlockState iblockdata3 = (BlockState) iblockdata1.setValue(FenceBlock.EAST, true);

            int i;

            for (i = 1; i <= 11; i += 2) {
                this.generateBox(world, chunkBox, i, 10, 0, i, 11, 0, iblockdata, iblockdata, false);
                this.generateBox(world, chunkBox, i, 10, 12, i, 11, 12, iblockdata, iblockdata, false);
                this.generateBox(world, chunkBox, 0, 10, i, 0, 11, i, iblockdata1, iblockdata1, false);
                this.generateBox(world, chunkBox, 12, 10, i, 12, 11, i, iblockdata1, iblockdata1, false);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 0, chunkBox);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), i, 13, 12, chunkBox);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), 0, 13, i, chunkBox);
                this.placeBlock(world, Blocks.NETHER_BRICKS.defaultBlockState(), 12, 13, i, chunkBox);
                if (i != 11) {
                    this.placeBlock(world, iblockdata, i + 1, 13, 0, chunkBox);
                    this.placeBlock(world, iblockdata, i + 1, 13, 12, chunkBox);
                    this.placeBlock(world, iblockdata1, 0, 13, i + 1, chunkBox);
                    this.placeBlock(world, iblockdata1, 12, 13, i + 1, chunkBox);
                }
            }

            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 0, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.EAST, true), 0, 13, 12, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.SOUTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 12, chunkBox);
            this.placeBlock(world, (BlockState) ((BlockState) Blocks.NETHER_BRICK_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true)).setValue(FenceBlock.WEST, true), 12, 13, 0, chunkBox);

            for (i = 3; i <= 9; i += 2) {
                this.generateBox(world, chunkBox, 1, 7, i, 1, 8, i, iblockdata2, iblockdata2, false);
                this.generateBox(world, chunkBox, 11, 7, i, 11, 8, i, iblockdata3, iblockdata3, false);
            }

            BlockState iblockdata4 = (BlockState) Blocks.NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);

            int j;
            int k;

            for (j = 0; j <= 6; ++j) {
                int l = j + 4;

                for (k = 5; k <= 7; ++k) {
                    this.placeBlock(world, iblockdata4, k, 5 + j, l, chunkBox);
                }

                if (l >= 5 && l <= 8) {
                    this.generateBox(world, chunkBox, 5, 5, l, 7, j + 4, l, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                } else if (l >= 9 && l <= 10) {
                    this.generateBox(world, chunkBox, 5, 8, l, 7, j + 4, l, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                }

                if (j >= 1) {
                    this.generateBox(world, chunkBox, 5, 6 + j, l, 7, 9 + j, l, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
                }
            }

            for (j = 5; j <= 7; ++j) {
                this.placeBlock(world, iblockdata4, j, 12, 11, chunkBox);
            }

            this.generateBox(world, chunkBox, 5, 6, 7, 5, 7, 7, iblockdata3, iblockdata3, false);
            this.generateBox(world, chunkBox, 7, 6, 7, 7, 7, 7, iblockdata2, iblockdata2, false);
            this.generateBox(world, chunkBox, 5, 13, 12, 7, 13, 12, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 2, 3, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 9, 3, 5, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 5, 4, 2, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 9, 5, 2, 10, 5, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 9, 5, 9, 10, 5, 10, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 10, 5, 4, 10, 5, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            BlockState iblockdata5 = (BlockState) iblockdata4.setValue(StairBlock.FACING, Direction.EAST);
            BlockState iblockdata6 = (BlockState) iblockdata4.setValue(StairBlock.FACING, Direction.WEST);

            this.placeBlock(world, iblockdata6, 4, 5, 2, chunkBox);
            this.placeBlock(world, iblockdata6, 4, 5, 3, chunkBox);
            this.placeBlock(world, iblockdata6, 4, 5, 9, chunkBox);
            this.placeBlock(world, iblockdata6, 4, 5, 10, chunkBox);
            this.placeBlock(world, iblockdata5, 8, 5, 2, chunkBox);
            this.placeBlock(world, iblockdata5, 8, 5, 3, chunkBox);
            this.placeBlock(world, iblockdata5, 8, 5, 9, chunkBox);
            this.placeBlock(world, iblockdata5, 8, 5, 10, chunkBox);
            this.generateBox(world, chunkBox, 3, 4, 4, 4, 4, 8, Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 4, 4, 9, 4, 8, Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 3, 5, 4, 4, 5, 8, Blocks.NETHER_WART.defaultBlockState(), Blocks.NETHER_WART.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 8, 5, 4, 9, 5, 8, Blocks.NETHER_WART.defaultBlockState(), Blocks.NETHER_WART.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            int i1;

            for (k = 4; k <= 8; ++k) {
                for (i1 = 0; i1 <= 2; ++i1) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), k, -1, i1, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), k, -1, 12 - i1, chunkBox);
                }
            }

            for (k = 0; k <= 2; ++k) {
                for (i1 = 4; i1 <= 8; ++i1) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), k, -1, i1, chunkBox);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultBlockState(), 12 - k, -1, i1, chunkBox);
                }
            }

        }
    }

    public static class BridgeEndFiller extends NetherFortressPieces.NetherBridgePiece {

        private static final int WIDTH = 5;
        private static final int HEIGHT = 10;
        private static final int DEPTH = 8;
        private final int selfSeed;

        public BridgeEndFiller(int chainLength, RandomSource random, BoundingBox boundingBox, Direction orientation) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_END_FILLER, chainLength, boundingBox);
            this.setOrientation(orientation);
            this.selfSeed = random.nextInt();
        }

        public BridgeEndFiller(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_END_FILLER, nbt);
            this.selfSeed = nbt.getInt("Seed");
        }

        public static NetherFortressPieces.BridgeEndFiller createPiece(StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            BoundingBox structureboundingbox = BoundingBox.orientBox(x, y, z, -1, -3, 0, 5, 10, 8, orientation);

            return NetherBridgePiece.isOkBox(structureboundingbox) && holder.findCollisionPiece(structureboundingbox) == null ? new NetherFortressPieces.BridgeEndFiller(chainLength, random, structureboundingbox, orientation) : null;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putInt("Seed", this.selfSeed);
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            RandomSource randomsource1 = RandomSource.create((long) this.selfSeed);

            int i;
            int j;
            int k;

            for (j = 0; j <= 4; ++j) {
                for (k = 3; k <= 4; ++k) {
                    i = randomsource1.nextInt(8);
                    this.generateBox(world, chunkBox, j, k, 0, j, k, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                }
            }

            j = randomsource1.nextInt(8);
            this.generateBox(world, chunkBox, 0, 5, 0, 0, 5, j, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            j = randomsource1.nextInt(8);
            this.generateBox(world, chunkBox, 4, 5, 0, 4, 5, j, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);

            for (j = 0; j <= 4; ++j) {
                k = randomsource1.nextInt(5);
                this.generateBox(world, chunkBox, j, 2, 0, j, 2, k, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
            }

            for (j = 0; j <= 4; ++j) {
                for (k = 0; k <= 1; ++k) {
                    i = randomsource1.nextInt(3);
                    this.generateBox(world, chunkBox, j, k, 0, j, k, i, Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(), false);
                }
            }

        }
    }

    public static class StartPiece extends NetherFortressPieces.BridgeCrossing {

        public NetherFortressPieces.PieceWeight previousPiece;
        public List<NetherFortressPieces.PieceWeight> availableBridgePieces;
        public List<NetherFortressPieces.PieceWeight> availableCastlePieces;
        public final List<StructurePiece> pendingChildren = Lists.newArrayList();

        public StartPiece(RandomSource random, int x, int z) {
            super(x, z, getRandomHorizontalDirection(random));
            this.availableBridgePieces = Lists.newArrayList();
            NetherFortressPieces.PieceWeight[] anetherfortresspieces_n = NetherFortressPieces.BRIDGE_PIECE_WEIGHTS;
            int k = anetherfortresspieces_n.length;

            NetherFortressPieces.PieceWeight netherfortresspieces_n;
            int l;

            for (l = 0; l < k; ++l) {
                netherfortresspieces_n = anetherfortresspieces_n[l];
                netherfortresspieces_n.placeCount = 0;
                this.availableBridgePieces.add(netherfortresspieces_n);
            }

            this.availableCastlePieces = Lists.newArrayList();
            anetherfortresspieces_n = NetherFortressPieces.CASTLE_PIECE_WEIGHTS;
            k = anetherfortresspieces_n.length;

            for (l = 0; l < k; ++l) {
                netherfortresspieces_n = anetherfortresspieces_n[l];
                netherfortresspieces_n.placeCount = 0;
                this.availableCastlePieces.add(netherfortresspieces_n);
            }

        }

        public StartPiece(CompoundTag nbt) {
            super(StructurePieceType.NETHER_FORTRESS_START, nbt);
        }
    }

    private abstract static class NetherBridgePiece extends StructurePiece {

        protected NetherBridgePiece(StructurePieceType type, int length, BoundingBox boundingBox) {
            super(type, length, boundingBox);
        }

        public NetherBridgePiece(StructurePieceType type, CompoundTag nbt) {
            super(type, nbt);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {}

        private int updatePieceWeight(List<NetherFortressPieces.PieceWeight> possiblePieces) {
            boolean flag = false;
            int i = 0;

            NetherFortressPieces.PieceWeight netherfortresspieces_n;

            for (Iterator iterator = possiblePieces.iterator(); iterator.hasNext(); i += netherfortresspieces_n.weight) {
                netherfortresspieces_n = (NetherFortressPieces.PieceWeight) iterator.next();
                if (netherfortresspieces_n.maxPlaceCount > 0 && netherfortresspieces_n.placeCount < netherfortresspieces_n.maxPlaceCount) {
                    flag = true;
                }
            }

            return flag ? i : -1;
        }

        private NetherFortressPieces.NetherBridgePiece generatePiece(NetherFortressPieces.StartPiece start, List<NetherFortressPieces.PieceWeight> possiblePieces, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, Direction orientation, int chainLength) {
            int i1 = this.updatePieceWeight(possiblePieces);
            boolean flag = i1 > 0 && chainLength <= 30;
            int j1 = 0;

            while (j1 < 5 && flag) {
                ++j1;
                int k1 = random.nextInt(i1);
                Iterator iterator = possiblePieces.iterator();

                while (iterator.hasNext()) {
                    NetherFortressPieces.PieceWeight netherfortresspieces_n = (NetherFortressPieces.PieceWeight) iterator.next();

                    k1 -= netherfortresspieces_n.weight;
                    if (k1 < 0) {
                        if (!netherfortresspieces_n.doPlace(chainLength) || netherfortresspieces_n == start.previousPiece && !netherfortresspieces_n.allowInRow) {
                            break;
                        }

                        NetherFortressPieces.NetherBridgePiece netherfortresspieces_m = NetherFortressPieces.findAndCreateBridgePieceFactory(netherfortresspieces_n, holder, random, x, y, z, orientation, chainLength);

                        if (netherfortresspieces_m != null) {
                            ++netherfortresspieces_n.placeCount;
                            start.previousPiece = netherfortresspieces_n;
                            if (!netherfortresspieces_n.isValid()) {
                                possiblePieces.remove(netherfortresspieces_n);
                            }

                            return netherfortresspieces_m;
                        }
                    }
                }
            }

            return NetherFortressPieces.BridgeEndFiller.createPiece(holder, random, x, y, z, orientation, chainLength);
        }

        private StructurePiece generateAndAddPiece(NetherFortressPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int x, int y, int z, @Nullable Direction orientation, int chainLength, boolean inside) {
            if (Math.abs(x - start.getBoundingBox().minX()) <= 112 && Math.abs(z - start.getBoundingBox().minZ()) <= 112) {
                List<NetherFortressPieces.PieceWeight> list = start.availableBridgePieces;

                if (inside) {
                    list = start.availableCastlePieces;
                }

                NetherFortressPieces.NetherBridgePiece netherfortresspieces_m = this.generatePiece(start, list, holder, random, x, y, z, orientation, chainLength + 1);

                if (netherfortresspieces_m != null) {
                    holder.addPiece(netherfortresspieces_m);
                    start.pendingChildren.add(netherfortresspieces_m);
                }

                return netherfortresspieces_m;
            } else {
                return NetherFortressPieces.BridgeEndFiller.createPiece(holder, random, x, y, z, orientation, chainLength);
            }
        }

        @Nullable
        protected StructurePiece generateChildForward(NetherFortressPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int leftRightOffset, int heightOffset, boolean inside) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() - 1, enumdirection, this.getGenDepth(), inside);
                    case SOUTH:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.maxZ() + 1, enumdirection, this.getGenDepth(), inside);
                    case WEST:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, enumdirection, this.getGenDepth(), inside);
                    case EAST:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, enumdirection, this.getGenDepth(), inside);
                }
            }

            return null;
        }

        @Nullable
        protected StructurePiece generateChildLeft(NetherFortressPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int heightOffset, int leftRightOffset, boolean inside) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.WEST, this.getGenDepth(), inside);
                    case SOUTH:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.WEST, this.getGenDepth(), inside);
                    case WEST:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth(), inside);
                    case EAST:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() - 1, Direction.NORTH, this.getGenDepth(), inside);
                }
            }

            return null;
        }

        @Nullable
        protected StructurePiece generateChildRight(NetherFortressPieces.StartPiece start, StructurePieceAccessor holder, RandomSource random, int heightOffset, int leftRightOffset, boolean inside) {
            Direction enumdirection = this.getOrientation();

            if (enumdirection != null) {
                switch (enumdirection) {
                    case NORTH:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.EAST, this.getGenDepth(), inside);
                    case SOUTH:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + heightOffset, this.boundingBox.minZ() + leftRightOffset, Direction.EAST, this.getGenDepth(), inside);
                    case WEST:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth(), inside);
                    case EAST:
                        return this.generateAndAddPiece(start, holder, random, this.boundingBox.minX() + leftRightOffset, this.boundingBox.minY() + heightOffset, this.boundingBox.maxZ() + 1, Direction.SOUTH, this.getGenDepth(), inside);
                }
            }

            return null;
        }

        protected static boolean isOkBox(BoundingBox boundingBox) {
            return boundingBox != null && boundingBox.minY() > 10;
        }
    }
}
