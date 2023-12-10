package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class Beardifier implements DensityFunctions.BeardifierOrMarker {
    public static final int BEARD_KERNEL_RADIUS = 12;
    private static final int BEARD_KERNEL_SIZE = 24;
    private static final float[] BEARD_KERNEL = Util.make(new float[13824], (array) -> {
        for(int i = 0; i < 24; ++i) {
            for(int j = 0; j < 24; ++j) {
                for(int k = 0; k < 24; ++k) {
                    array[i * 24 * 24 + j * 24 + k] = (float)computeBeardContribution(j - 12, k - 12, i - 12);
                }
            }
        }

    });
    private final ObjectListIterator<Beardifier.Rigid> pieceIterator;
    private final ObjectListIterator<JigsawJunction> junctionIterator;

    public static Beardifier forStructuresInChunk(StructureManager world, ChunkPos pos) {
        int i = pos.getMinBlockX();
        int j = pos.getMinBlockZ();
        ObjectList<Beardifier.Rigid> objectList = new ObjectArrayList<>(10);
        ObjectList<JigsawJunction> objectList2 = new ObjectArrayList<>(32);
        // Paper start - replace for each
        for (net.minecraft.world.level.levelgen.structure.StructureStart start : world.startsForStructure(pos, (structure) -> {
            return structure.terrainAdaptation() != TerrainAdjustment.NONE;
        })) { // Paper end
            TerrainAdjustment terrainAdjustment = start.getStructure().terrainAdaptation();

            for(StructurePiece structurePiece : start.getPieces()) {
                if (structurePiece.isCloseToChunk(pos, 12)) {
                    if (structurePiece instanceof PoolElementStructurePiece) {
                        PoolElementStructurePiece poolElementStructurePiece = (PoolElementStructurePiece)structurePiece;
                        StructureTemplatePool.Projection projection = poolElementStructurePiece.getElement().getProjection();
                        if (projection == StructureTemplatePool.Projection.RIGID) {
                            objectList.add(new Beardifier.Rigid(poolElementStructurePiece.getBoundingBox(), terrainAdjustment, poolElementStructurePiece.getGroundLevelDelta()));
                        }

                        for(JigsawJunction jigsawJunction : poolElementStructurePiece.getJunctions()) {
                            // Paper start - decompile fix
                            int i2 = jigsawJunction.getSourceX();
                            int j2 = jigsawJunction.getSourceZ();
                            if (i2 > i - 12 && j2 > j - 12 && i2 < i + 15 + 12 && j2 < j + 15 + 12) {
                                // Paper end
                                objectList2.add(jigsawJunction);
                            }
                        }
                    } else {
                        objectList.add(new Beardifier.Rigid(structurePiece.getBoundingBox(), terrainAdjustment, 0));
                    }
                }
            }

        } // Paper
        return new Beardifier(objectList.iterator(), objectList2.iterator());
    }

    @VisibleForTesting
    public Beardifier(ObjectListIterator<Beardifier.Rigid> pieceIterator, ObjectListIterator<JigsawJunction> junctionIterator) {
        this.pieceIterator = pieceIterator;
        this.junctionIterator = junctionIterator;
    }

    @Override
    public double compute(DensityFunction.FunctionContext pos) {
        int i = pos.blockX();
        int j = pos.blockY();
        int k = pos.blockZ();

        double d;
        double var10001;
        for(d = 0.0D; this.pieceIterator.hasNext(); d += var10001) {
            Beardifier.Rigid rigid = this.pieceIterator.next();
            BoundingBox boundingBox = rigid.box();
            int l = rigid.groundLevelDelta();
            int m = Math.max(0, Math.max(boundingBox.minX() - i, i - boundingBox.maxX()));
            int n = Math.max(0, Math.max(boundingBox.minZ() - k, k - boundingBox.maxZ()));
            int o = boundingBox.minY() + l;
            int p = j - o;
            int var10000;
            switch (rigid.terrainAdjustment()) {
                case NONE:
                    var10000 = 0;
                    break;
                case BURY:
                case BEARD_THIN:
                    var10000 = p;
                    break;
                case BEARD_BOX:
                    var10000 = Math.max(0, Math.max(o - j, j - boundingBox.maxY()));
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            int q = var10000;
            switch (rigid.terrainAdjustment()) {
                case NONE:
                    var10001 = 0.0D;
                    break;
                case BURY:
                    var10001 = getBuryContribution(m, q, n);
                    break;
                case BEARD_THIN:
                case BEARD_BOX:
                    var10001 = getBeardContribution(m, q, n, p) * 0.8D;
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }
        }

        this.pieceIterator.back(Integer.MAX_VALUE);

        while(this.junctionIterator.hasNext()) {
            JigsawJunction jigsawJunction = this.junctionIterator.next();
            int r = i - jigsawJunction.getSourceX();
            int s = j - jigsawJunction.getSourceGroundY();
            int t = k - jigsawJunction.getSourceZ();
            d += getBeardContribution(r, s, t, s) * 0.4D;
        }

        this.junctionIterator.back(Integer.MAX_VALUE);
        return d;
    }

    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    private static double getBuryContribution(int x, int y, int z) {
        double d = Mth.length((double)x, (double)y / 2.0D, (double)z);
        return Mth.clampedMap(d, 0.0D, 6.0D, 1.0D, 0.0D);
    }

    private static double getBeardContribution(int x, int y, int z, int yy) {
        int i = x + 12;
        int j = y + 12;
        int k = z + 12;
        if (isInKernelRange(i) && isInKernelRange(j) && isInKernelRange(k)) {
            double d = (double)yy + 0.5D;
            double e = Mth.lengthSquared((double)x, d, (double)z);
            double f = -d * Mth.fastInvSqrt(e / 2.0D) / 2.0D;
            return f * (double)BEARD_KERNEL[k * 24 * 24 + i * 24 + j];
        } else {
            return 0.0D;
        }
    }

    private static boolean isInKernelRange(int i) {
        return i >= 0 && i < 24;
    }

    private static double computeBeardContribution(int x, int y, int z) {
        return computeBeardContribution(x, (double)y + 0.5D, z);
    }

    private static double computeBeardContribution(int x, double y, int z) {
        double d = Mth.lengthSquared((double)x, y, (double)z);
        return Math.pow(Math.E, -d / 16.0D);
    }

    @VisibleForTesting
    public static record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {
    }
}
