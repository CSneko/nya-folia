package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertPyramidStructure extends SinglePieceStructure {

    public static final Codec<DesertPyramidStructure> CODEC = simpleCodec(DesertPyramidStructure::new);

    public DesertPyramidStructure(Structure.StructureSettings config) {
        super(DesertPyramidPiece::new, 21, 21, config);
    }

    @Override
    public void afterPlace(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox box, ChunkPos chunkPos, PiecesContainer pieces) {
        Set<BlockPos> set = SortedArraySet.create(Vec3i::compareTo);
        Iterator iterator = pieces.pieces().iterator();

        while (iterator.hasNext()) {
            StructurePiece structurepiece = (StructurePiece) iterator.next();

            if (structurepiece instanceof DesertPyramidPiece) {
                DesertPyramidPiece desertpyramidpiece = (DesertPyramidPiece) structurepiece;

                set.addAll(desertpyramidpiece.getPotentialSuspiciousSandWorldPositions());
                DesertPyramidStructure.placeSuspiciousSand(box, world, desertpyramidpiece.getRandomCollapsedRoofPos());
            }
        }

        ObjectArrayList<BlockPos> objectarraylist = new ObjectArrayList(set.stream().toList());
        RandomSource randomsource1 = RandomSource.create(world.getSeed()).forkPositional().at(pieces.calculateBoundingBox().getCenter());

        Util.shuffle(objectarraylist, randomsource1);
        int i = Math.min(set.size(), randomsource1.nextInt(5, 8));
        ObjectListIterator objectlistiterator = objectarraylist.iterator();

        while (objectlistiterator.hasNext()) {
            BlockPos blockposition = (BlockPos) objectlistiterator.next();

            if (i > 0) {
                --i;
                DesertPyramidStructure.placeSuspiciousSand(box, world, blockposition);
            } else if (box.isInside(blockposition)) {
                world.setBlock(blockposition, Blocks.SAND.defaultBlockState(), 2);
            }
        }

    }

    private static void placeSuspiciousSand(BoundingBox box, WorldGenLevel world, BlockPos pos) {
        if (box.isInside(pos)) {
            // CraftBukkit start
            if (world instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
                org.bukkit.craftbukkit.block.CraftBrushableBlock brushableState = (org.bukkit.craftbukkit.block.CraftBrushableBlock) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), null);
                brushableState.setLootTable(org.bukkit.Bukkit.getLootTable(org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY)));
                brushableState.setSeed(pos.asLong());
                transformerAccess.setCraftBlock(pos, brushableState, 2);
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), 2);
            world.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK).ifPresent((brushableblockentity) -> {
                brushableblockentity.setLootTable(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, pos.asLong());
            });
        }

    }

    @Override
    public StructureType<?> type() {
        return StructureType.DESERT_PYRAMID;
    }
}
