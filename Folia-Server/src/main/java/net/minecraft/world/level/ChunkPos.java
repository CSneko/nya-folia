package net.minecraft.world.level;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class ChunkPos {
    private static final int SAFETY_MARGIN = 1056;
    public static final long INVALID_CHUNK_POS = asLong(1875066, 1875066);
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    private static final long COORD_BITS = 32L;
    private static final long COORD_MASK = 4294967295L;
    private static final int REGION_BITS = 5;
    public static final int REGION_SIZE = 32;
    private static final int REGION_MASK = 31;
    public static final int REGION_MAX_INDEX = 31;
    public final int x;
    public final int z;
    public final long longKey; // Paper
    private static final int HASH_A = 1664525;
    private static final int HASH_C = 1013904223;
    private static final int HASH_Z_XOR = -559038737;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
        this.longKey = asLong(this.x, this.z); // Paper
    }

    public ChunkPos(BlockPos pos) {
        this.x = SectionPos.blockToSectionCoord(pos.getX());
        this.z = SectionPos.blockToSectionCoord(pos.getZ());
        this.longKey = asLong(this.x, this.z); // Paper
    }

    public ChunkPos(long pos) {
        this.x = (int)pos;
        this.z = (int)(pos >> 32);
        this.longKey = asLong(this.x, this.z); // Paper
    }

    public static ChunkPos minFromRegion(int x, int z) {
        return new ChunkPos(x << 5, z << 5);
    }

    public static ChunkPos maxFromRegion(int x, int z) {
        return new ChunkPos((x << 5) + 31, (z << 5) + 31);
    }

    public long toLong() {
        return longKey; // Paper
    }

        public static long asLong(int chunkX, int chunkZ) {
        return (long)chunkX & 4294967295L | ((long)chunkZ & 4294967295L) << 32;
    }

    public static long asLong(BlockPos pos) {
        return asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static int getX(long pos) {
        return (int)(pos & 4294967295L);
    }

    public static int getZ(long pos) {
        return (int)(pos >>> 32 & 4294967295L);
    }

    @Override
    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(int x, int z) {
        int i = 1664525 * x + 1013904223;
        int j = 1664525 * (z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (!(object instanceof ChunkPos)) {
            return false;
        } else {
            ChunkPos chunkPos = (ChunkPos)object;
            return this.x == chunkPos.x && this.z == chunkPos.z;
        }
    }

    public int getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public int getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.x);
    }

    public int getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.z);
    }

    public int getMaxBlockX() {
        return this.getBlockX(15);
    }

    public int getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public int getRegionX() {
        return this.x >> 5;
    }

    public int getRegionZ() {
        return this.z >> 5;
    }

    public int getRegionLocalX() {
        return this.x & 31;
    }

    public int getRegionLocalZ() {
        return this.z & 31;
    }

    public BlockPos getBlockAt(int offsetX, int y, int offsetZ) {
        return new BlockPos(this.getBlockX(offsetX), y, this.getBlockZ(offsetZ));
    }

    public int getBlockX(int offset) {
        return SectionPos.sectionToBlockCoord(this.x, offset);
    }

    public int getBlockZ(int offset) {
        return SectionPos.sectionToBlockCoord(this.z, offset);
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public int getChessboardDistance(ChunkPos pos) {
        return Math.max(Math.abs(this.x - pos.x), Math.abs(this.z - pos.z));
    }

    public int distanceSquared(ChunkPos pos) {
        return this.distanceSquared(pos.x, pos.z);
    }

    public int distanceSquared(long pos) {
        return this.distanceSquared(getX(pos), getZ(pos));
    }

    private int distanceSquared(int x, int z) {
        int i = x - this.x;
        int j = z - this.z;
        return i * i + j * j;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius) {
        return rangeClosed(new ChunkPos(center.x - radius, center.z - radius), new ChunkPos(center.x + radius, center.z + radius));
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos pos1, final ChunkPos pos2) {
        int i = Math.abs(pos1.x - pos2.x) + 1;
        int j = Math.abs(pos1.z - pos2.z) + 1;
        final int k = pos1.x < pos2.x ? 1 : -1;
        final int l = pos1.z < pos2.z ? 1 : -1;
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>((long)(i * j), 64) {
            @Nullable
            private ChunkPos pos;

            @Override
            public boolean tryAdvance(Consumer<? super ChunkPos> consumer) {
                if (this.pos == null) {
                    this.pos = pos1;
                } else {
                    int i = this.pos.x;
                    int j = this.pos.z;
                    if (i == pos2.x) {
                        if (j == pos2.z) {
                            return false;
                        }

                        this.pos = new ChunkPos(pos1.x, j + l);
                    } else {
                        this.pos = new ChunkPos(i + k, j);
                    }
                }

                consumer.accept(this.pos);
                return true;
            }
        }, false);
    }
}
