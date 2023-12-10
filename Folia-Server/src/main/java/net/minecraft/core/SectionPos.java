package net.minecraft.core;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;

public class SectionPos extends Vec3i {
    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_MASK = 15;
    public static final int SECTION_HALF_SIZE = 8;
    public static final int SECTION_MAX_INDEX = 15;
    private static final int PACKED_X_LENGTH = 22;
    private static final int PACKED_Y_LENGTH = 20;
    private static final int PACKED_Z_LENGTH = 22;
    private static final long PACKED_X_MASK = 4194303L;
    private static final long PACKED_Y_MASK = 1048575L;
    private static final long PACKED_Z_MASK = 4194303L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = 20;
    private static final int X_OFFSET = 42;
    private static final int RELATIVE_X_SHIFT = 8;
    private static final int RELATIVE_Y_SHIFT = 0;
    private static final int RELATIVE_Z_SHIFT = 4;

    SectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static SectionPos of(int x, int y, int z) {
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(BlockPos pos) {
        return new SectionPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4); // Paper
    }

    public static SectionPos of(ChunkPos chunkPos, int y) {
        return new SectionPos(chunkPos.x, y, chunkPos.z);
    }

    public static SectionPos of(EntityAccess entity) {
        return of(entity.blockPosition());
    }

    public static SectionPos of(Position pos) {
        return new SectionPos(blockToSectionCoord(pos.x()), blockToSectionCoord(pos.y()), blockToSectionCoord(pos.z()));
    }

    public static SectionPos of(long packed) {
        return new SectionPos((int) (packed >> 42), (int) (packed << 44 >> 44), (int) (packed << 22 >> 42)); // Paper
    }

    public static SectionPos bottomOf(ChunkAccess chunk) {
        return of(chunk.getPos(), chunk.getMinSection());
    }

    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    // Paper start
    public static long getAdjacentFromBlockPos(int x, int y, int z, Direction enumdirection) {
        return (((long) ((x >> 4) + enumdirection.getStepX()) & 4194303L) << 42) | (((long) ((y >> 4) + enumdirection.getStepY()) & 1048575L)) | (((long) ((z >> 4) + enumdirection.getStepZ()) & 4194303L) << 20);
    }
    public static long getAdjacentFromSectionPos(int x, int y, int z, Direction enumdirection) {
        return (((long) (x + enumdirection.getStepX()) & 4194303L) << 42) | (((long) ((y) + enumdirection.getStepY()) & 1048575L)) | (((long) (z + enumdirection.getStepZ()) & 4194303L) << 20);
    }
    // Paper end
    public static long offset(long packed, int x, int y, int z) {
        return (((long) ((int) (packed >> 42) + x) & 4194303L) << 42) | (((long) ((int) (packed << 44 >> 44) + y) & 1048575L)) | (((long) ((int) (packed << 22 >> 42) + z) & 4194303L) << 20); // Simplify to reduce instruction count
    }

    public static int posToSectionCoord(double coord) {
        return blockToSectionCoord(Mth.floor(coord));
    }

    public static int blockToSectionCoord(int coord) {
        return coord >> 4;
    }

    public static int blockToSectionCoord(double coord) {
        return Mth.floor(coord) >> 4;
    }

    public static int sectionRelative(int coord) {
        return coord & 15;
    }

    public static short sectionRelativePos(BlockPos pos) {
        return (short) ((pos.getX() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getY() & 15); // Paper - simplify/inline
    }

    public static int sectionRelativeX(short packedLocalPos) {
        return packedLocalPos >>> 8 & 15;
    }

    public static int sectionRelativeY(short packedLocalPos) {
        return packedLocalPos >>> 0 & 15;
    }

    public static int sectionRelativeZ(short packedLocalPos) {
        return packedLocalPos >>> 4 & 15;
    }

    public int relativeToBlockX(short packedLocalPos) {
        return this.minBlockX() + sectionRelativeX(packedLocalPos);
    }

    public int relativeToBlockY(short packedLocalPos) {
        return this.minBlockY() + sectionRelativeY(packedLocalPos);
    }

    public int relativeToBlockZ(short packedLocalPos) {
        return this.minBlockZ() + sectionRelativeZ(packedLocalPos);
    }

    public BlockPos relativeToBlockPos(short packedLocalPos) {
        return new BlockPos(this.relativeToBlockX(packedLocalPos), this.relativeToBlockY(packedLocalPos), this.relativeToBlockZ(packedLocalPos));
    }

    public static int sectionToBlockCoord(int sectionCoord) {
        return sectionCoord << 4;
    }

    public static int sectionToBlockCoord(int chunkCoord, int offset) {
        return sectionToBlockCoord(chunkCoord) + offset;
    }

    public static int x(long packed) {
        return (int)(packed << 0 >> 42);
    }

    public static int y(long packed) {
        return (int)(packed << 44 >> 44);
    }

    public static int z(long packed) {
        return (int)(packed << 22 >> 42);
    }

    public int x() {
        return this.getX();
    }

    public int y() {
        return this.getY();
    }

    public int z() {
        return this.getZ();
    }

    public final int minBlockX() { // Paper - make final
        return this.getX() << 4; // Paper - inline
    }

    public final int minBlockY() { // Paper - make final
        return this.getY() << 4; // Paper - inline
    }

    public int minBlockZ() { // Paper - make final
        return this.getZ() << 4; // Paper - inline
    }

    public int maxBlockX() {
        return sectionToBlockCoord(this.x(), 15);
    }

    public int maxBlockY() {
        return sectionToBlockCoord(this.y(), 15);
    }

    public int maxBlockZ() {
        return sectionToBlockCoord(this.z(), 15);
    }

    public static long blockToSection(long blockPos) {
        // b(a(BlockPosition.b(i)), a(BlockPosition.c(i)), a(BlockPosition.d(i)));
        return (((long) (int) (blockPos >> 42) & 4194303L) << 42) | (((long) (int) ((blockPos << 52) >> 56) & 1048575L)) | (((long) (int) ((blockPos << 26) >> 42) & 4194303L) << 20); // Simplify to reduce instruction count
    }

    public static long getZeroNode(int x, int z) {
        return getZeroNode(asLong(x, 0, z));
    }

    public static long getZeroNode(long pos) {
        return pos & -1048576L;
    }

    public BlockPos origin() {
        return new BlockPos(sectionToBlockCoord(this.x()), sectionToBlockCoord(this.y()), sectionToBlockCoord(this.z()));
    }

    public BlockPos center() {
        int i = 8;
        return this.origin().offset(8, 8, 8);
    }

    public ChunkPos chunk() {
        return new ChunkPos(this.x(), this.z());
    }

    public static long asLong(BlockPos pos) {
        return asLong(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getY()), blockToSectionCoord(pos.getZ()));
    }

    // Paper start
    public static long blockPosAsSectionLong(int i, int j, int k) {
        return (((long) (i >> 4) & 4194303L) << 42) | (((long) (j >> 4) & 1048575L)) | (((long) (k >> 4) & 4194303L) << 20);
    }
    // Paper end

    public static long asLong(int x, int y, int z) {
        return (((long) x & 4194303L) << 42) | (((long) y & 1048575L)) | (((long) z & 4194303L) << 20); // Paper - Simplify to reduce instruction count
    }

    public long asLong() {
        return (((long) getX() & 4194303L) << 42) | (((long) getY() & 1048575L)) | (((long) getZ() & 4194303L) << 20); // Paper - Simplify to reduce instruction count
    }

    @Override
    public SectionPos offset(int i, int j, int k) {
        return i == 0 && j == 0 && k == 0 ? this : new SectionPos(this.x() + i, this.y() + j, this.z() + k);
    }

    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
    }

    public static Stream<SectionPos> cube(SectionPos center, int radius) {
        return betweenClosedStream(center.getX() - radius, center.getY() - radius, center.getZ() - radius, center.getX() + radius, center.getY() + radius, center.getZ() + radius); // Paper - simplify/inline
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos center, int radius, int minY, int maxY) {
        return betweenClosedStream(center.x - radius, 0, center.z - radius, center.x + radius, 15, center.z + radius); // Paper - simplify/inline
    }

    public static Stream<SectionPos> betweenClosedStream(final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<SectionPos>((long)((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)), 64) {
            final Cursor3D cursor = new Cursor3D(minX, minY, minZ, maxX, maxY, maxZ);

            @Override
            public boolean tryAdvance(Consumer<? super SectionPos> consumer) {
                if (this.cursor.advance()) {
                    consumer.accept(new SectionPos(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }

    public static void aroundAndAtBlockPos(BlockPos pos, LongConsumer consumer) {
        aroundAndAtBlockPos(pos.getX(), pos.getY(), pos.getZ(), consumer);
    }

    public static void aroundAndAtBlockPos(long pos, LongConsumer consumer) {
        aroundAndAtBlockPos(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos), consumer);
    }

    public static void aroundAndAtBlockPos(int x, int y, int z, LongConsumer consumer) {
        int i = blockToSectionCoord(x - 1);
        int j = blockToSectionCoord(x + 1);
        int k = blockToSectionCoord(y - 1);
        int l = blockToSectionCoord(y + 1);
        int m = blockToSectionCoord(z - 1);
        int n = blockToSectionCoord(z + 1);
        if (i == j && k == l && m == n) {
            consumer.accept(asLong(i, k, m));
        } else {
            for(int o = i; o <= j; ++o) {
                for(int p = k; p <= l; ++p) {
                    for(int q = m; q <= n; ++q) {
                        consumer.accept(asLong(o, p, q));
                    }
                }
            }
        }

    }
}
