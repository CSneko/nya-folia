package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

@Immutable
public class BlockPos extends Vec3i {
    public static final Codec<BlockPos> CODEC = Codec.INT_STREAM.comapFlatMap((stream) -> {
        return Util.fixedSize(stream, 3).map((values) -> {
            return new BlockPos(values[0], values[1], values[2]);
        });
    }, (pos) -> {
        return IntStream.of(pos.getX(), pos.getY(), pos.getZ());
    }).stable();
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    // Paper start - static constants
    private static final int PACKED_X_LENGTH = 26;
    private static final int PACKED_Z_LENGTH = 26;
    public static final int PACKED_Y_LENGTH = 12;
    private static final long PACKED_X_MASK = 67108863;
    private static final long PACKED_Y_MASK = 4095;
    private static final long PACKED_Z_MASK = 67108863;
    private static final int Z_OFFSET = 12;
    private static final int X_OFFSET = 38;
    // Paper end

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(Vec3i pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public static long getAdjacent(int baseX, int baseY, int baseZ, Direction enumdirection) { return asLong(baseX + enumdirection.getStepX(), baseY + enumdirection.getStepY(), baseZ + enumdirection.getStepZ()); } // Paper
    public static long offset(long value, Direction direction) {
        return offset(value, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static long offset(long value, int x, int y, int z) {
        return asLong((int) (value >> 38) + x, (int) ((value << 52) >> 52) + y, (int) ((value << 26) >> 38) + z); // Paper - simplify/inline
    }

    public static int getX(long packedPos) {
        return (int) (packedPos >> 38); // Paper - simplify/inline
    }

    public static int getY(long packedPos) {
        return (int) ((packedPos << 52) >> 52); // Paper - simplify/inline
    }

    public static int getZ(long packedPos) {
        return (int) ((packedPos << 26) >> 38);  // Paper - simplify/inline
    }

    public static BlockPos of(long packedPos) {
        return new BlockPos((int) (packedPos >> 38), (int) ((packedPos << 52) >> 52), (int) ((packedPos << 26) >> 38)); // Paper - simplify/inline
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    public static BlockPos containing(Position pos) {
        return containing(pos.x(), pos.y(), pos.z());
    }

    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }

    public static long asLong(int x, int y, int z) {
        return (((long) x & (long) 67108863) << 38) | (((long) y & (long) 4095)) | (((long) z & (long) 67108863) << 12); // Paper - inline constants and simplify
    }

    public static long getFlatIndex(long y) {
        return y & -16L;
    }

    @Override
    public BlockPos offset(int i, int j, int k) {
        return i == 0 && j == 0 && k == 0 ? this : new BlockPos(this.getX() + i, this.getY() + j, this.getZ() + k);
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    @Override
    public BlockPos offset(Vec3i vec3i) {
        return this.offset(vec3i.getX(), vec3i.getY(), vec3i.getZ());
    }

    @Override
    public BlockPos subtract(Vec3i vec3i) {
        return this.offset(-vec3i.getX(), -vec3i.getY(), -vec3i.getZ());
    }

    @Override
    public BlockPos multiply(int i) {
        if (i == 1) {
            return this;
        } else {
            return i == 0 ? ZERO : new BlockPos(this.getX() * i, this.getY() * i, this.getZ() * i);
        }
    }

    @Override
    public BlockPos above() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos above(int distance) {
        return distance == 0 ? this : new BlockPos(this.getX(), this.getY() + distance, this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos below() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos below(int i) {
        return i == 0 ? this : new BlockPos(this.getX(), this.getY() - i, this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos north() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos north(int distance) {
        return distance == 0 ? this : new BlockPos(this.getX(), this.getY(), this.getZ() - distance); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos south() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos south(int distance) {
        return distance == 0 ? this : new BlockPos(this.getX(), this.getY(), this.getZ() + distance); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos west() {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos west(int distance) {
        return distance == 0 ? this : new BlockPos(this.getX() - distance, this.getY(), this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos east() {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos east(int distance) {
        return distance == 0 ? this : new BlockPos(this.getX() + distance, this.getY(), this.getZ()); // Paper - Optimize BlockPosition
    }

    @Override
    public BlockPos relative(Direction direction) {
        // Paper Start - Optimize BlockPosition
        switch(direction) {
            case UP:
                return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
            case DOWN:
                return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
            case NORTH:
                return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
            case SOUTH:
                return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
            case WEST:
                return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
            case EAST:
                return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
            default:
        return new BlockPos(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
        }
        // Paper End
    }

    @Override
    public BlockPos relative(Direction direction, int i) {
        return i == 0 ? this : new BlockPos(this.getX() + direction.getStepX() * i, this.getY() + direction.getStepY() * i, this.getZ() + direction.getStepZ() * i);
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int i) {
        if (i == 0) {
            return this;
        } else {
            int j = axis == Direction.Axis.X ? i : 0;
            int k = axis == Direction.Axis.Y ? i : 0;
            int l = axis == Direction.Axis.Z ? i : 0;
            return new BlockPos(this.getX() + j, this.getY() + k, this.getZ() + l);
        }
    }

    public BlockPos rotate(Rotation rotation) {
        switch (rotation) {
            case NONE:
            default:
                return this;
            case CLOCKWISE_90:
                return new BlockPos(-this.getZ(), this.getY(), this.getX());
            case CLOCKWISE_180:
                return new BlockPos(-this.getX(), this.getY(), -this.getZ());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(this.getZ(), this.getY(), -this.getX());
        }
    }

    @Override
    public BlockPos cross(Vec3i pos) {
        return new BlockPos(this.getY() * pos.getZ() - this.getZ() * pos.getY(), this.getZ() * pos.getX() - this.getX() * pos.getZ(), this.getX() * pos.getY() - this.getY() * pos.getX());
    }

    public BlockPos atY(int y) {
        return new BlockPos(this.getX(), y, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public static Iterable<BlockPos> randomInCube(RandomSource random, int count, BlockPos around, int range) {
        return randomBetweenClosed(random, count, around.getX() - range, around.getY() - range, around.getZ() - range, around.getX() + range, around.getY() + range, around.getZ() + range);
    }

    /** @deprecated */
    @Deprecated
    public static Stream<BlockPos> squareOutSouthEast(BlockPos pos) {
        return Stream.of(pos, pos.south(), pos.east(), pos.south().east());
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource random, int count, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int i = maxX - minX + 1;
        int j = maxY - minY + 1;
        int k = maxZ - minZ + 1;
        return () -> {
            return new AbstractIterator<BlockPos>() {
                final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
                int counter = count;

                @Override
                protected BlockPos computeNext() {
                    if (this.counter <= 0) {
                        return this.endOfData();
                    } else {
                        BlockPos blockPos = this.nextPos.set(minX + random.nextInt(i), minY + random.nextInt(j), minZ + random.nextInt(k));
                        --this.counter;
                        return blockPos;
                    }
                }
            };
        };
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos center, int rangeX, int rangeY, int rangeZ) {
        int i = rangeX + rangeY + rangeZ;
        // Paper start - rename variables to fix conflict with anonymous class (remap fix)
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();
        // Paper end
        return () -> {
            return new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int currentDepth;
                private int maxX;
                private int maxY;
                private int x;
                private int y;
                private boolean zMirror;

                @Override
                protected BlockPos computeNext() {
                    if (this.zMirror) {
                        this.zMirror = false;
                        this.cursor.setZ(centerZ - (this.cursor.getZ() - centerZ)); // Paper - remap fix
                        return this.cursor;
                    } else {
                        BlockPos blockPos;
                        for(blockPos = null; blockPos == null; ++this.y) {
                            if (this.y > this.maxY) {
                                ++this.x;
                                if (this.x > this.maxX) {
                                    ++this.currentDepth;
                                    if (this.currentDepth > i) {
                                        return this.endOfData();
                                    }

                                    this.maxX = Math.min(rangeX, this.currentDepth);
                                    this.x = -this.maxX;
                                }

                                this.maxY = Math.min(rangeY, this.currentDepth - Math.abs(this.x));
                                this.y = -this.maxY;
                            }

                            int i = this.x;
                            int j = this.y;
                            int k = this.currentDepth - Math.abs(i) - Math.abs(j);
                            if (k <= rangeZ) {
                                this.zMirror = k != 0;
                                blockPos = this.cursor.set(centerX + i, centerY + j, centerZ + k); // Paper - remap fix
                            }
                        }

                        return blockPos;
                    }
                }
            };
        };
    }

    public static Optional<BlockPos> findClosestMatch(BlockPos pos, int horizontalRange, int verticalRange, Predicate<BlockPos> condition) {
        for(BlockPos blockPos : withinManhattan(pos, horizontalRange, verticalRange, horizontalRange)) {
            if (condition.test(blockPos)) {
                return Optional.of(blockPos);
            }
        }

        return Optional.empty();
    }

    public static Stream<BlockPos> withinManhattanStream(BlockPos center, int maxX, int maxY, int maxZ) {
        return StreamSupport.stream(withinManhattan(center, maxX, maxY, maxZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos start, BlockPos end) {
        return betweenClosed(Math.min(start.getX(), end.getX()), Math.min(start.getY(), end.getY()), Math.min(start.getZ(), end.getZ()), Math.max(start.getX(), end.getX()), Math.max(start.getY(), end.getY()), Math.max(start.getZ(), end.getZ()));
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos start, BlockPos end) {
        return StreamSupport.stream(betweenClosed(start, end).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BoundingBox box) {
        return betweenClosedStream(Math.min(box.minX(), box.maxX()), Math.min(box.minY(), box.maxY()), Math.min(box.minZ(), box.maxZ()), Math.max(box.minX(), box.maxX()), Math.max(box.minY(), box.maxY()), Math.max(box.minZ(), box.maxZ()));
    }

    public static Stream<BlockPos> betweenClosedStream(AABB box) {
        return betweenClosedStream(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ));
    }

    public static Stream<BlockPos> betweenClosedStream(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        return StreamSupport.stream(betweenClosed(startX, startY, startZ, endX, endY, endZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        int i = endX - startX + 1;
        int j = endY - startY + 1;
        int k = endZ - startZ + 1;
        int l = i * j * k;
        return () -> {
            return new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int index;

                @Override
                protected BlockPos computeNext() {
                    if (this.index == l) {
                        return this.endOfData();
                    } else {
                        int offsetX = this.index % i; // Paper - decomp fix
                        int u = this.index / i; // Paper - decomp fix
                        int offsetY = u % j; // Paper - decomp fix
                        int offsetZ = u / j; // Paper - decomp fix
                        ++this.index;
                        return this.cursor.set(startX + offsetX, startY + offsetY, startZ + offsetZ); // Paper - decomp fix
                    }
                }
            };
        };
    }

    public static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos center, int radius, Direction firstDirection, Direction secondDirection) {
        Validate.validState(firstDirection.getAxis() != secondDirection.getAxis(), "The two directions cannot be on the same axis");
        return () -> {
            return new AbstractIterator<BlockPos.MutableBlockPos>() {
                private final Direction[] directions = new Direction[]{firstDirection, secondDirection, firstDirection.getOpposite(), secondDirection.getOpposite()};
                private final BlockPos.MutableBlockPos cursor = center.mutable().move(secondDirection);
                private final int legs = 4 * radius;
                private int leg = -1;
                private int legSize;
                private int legIndex;
                private int lastX = this.cursor.getX();
                private int lastY = this.cursor.getY();
                private int lastZ = this.cursor.getZ();

                @Override
                protected BlockPos.MutableBlockPos computeNext() {
                    this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
                    this.lastX = this.cursor.getX();
                    this.lastY = this.cursor.getY();
                    this.lastZ = this.cursor.getZ();
                    if (this.legIndex >= this.legSize) {
                        if (this.leg >= this.legs) {
                            return this.endOfData();
                        }

                        ++this.leg;
                        this.legIndex = 0;
                        this.legSize = this.leg / 2 + 1;
                    }

                    ++this.legIndex;
                    return this.cursor;
                }
            };
        };
    }

    public static int breadthFirstTraversal(BlockPos pos, int maxDepth, int maxIterations, BiConsumer<BlockPos, Consumer<BlockPos>> nextQueuer, Predicate<BlockPos> callback) {
        Queue<Pair<BlockPos, Integer>> queue = new ArrayDeque<>();
        LongSet longSet = new LongOpenHashSet();
        queue.add(Pair.of(pos, 0));
        int i = 0;

        while(!queue.isEmpty()) {
            Pair<BlockPos, Integer> pair = queue.poll();
            BlockPos blockPos = pair.getLeft();
            int j = pair.getRight();
            long l = blockPos.asLong();
            if (longSet.add(l) && callback.test(blockPos)) {
                ++i;
                if (i >= maxIterations) {
                    return i;
                }

                if (j < maxDepth) {
                    nextQueuer.accept(blockPos, (queuedPos) -> {
                        queue.add(Pair.of(queuedPos, j + 1));
                    });
                }
            }
        }

        return i;
    }

    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos(double x, double y, double z) {
            this(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        @Override
        public BlockPos offset(int i, int j, int k) {
            return super.offset(i, j, k).immutable();
        }

        @Override
        public BlockPos multiply(int i) {
            return super.multiply(i).immutable();
        }

        @Override
        public BlockPos relative(Direction direction, int i) {
            return super.relative(direction, i).immutable();
        }

        @Override
        public BlockPos relative(Direction.Axis axis, int i) {
            return super.relative(axis, i).immutable();
        }

        @Override
        public BlockPos rotate(Rotation rotation) {
            return super.rotate(rotation).immutable();
        }

        public BlockPos.MutableBlockPos set(int x, int y, int z) {
            this.x = x; // Paper - force inline
            this.y = y; // Paper - force inline
            this.z = z; // Paper - force inline
            return this;
        }

        public BlockPos.MutableBlockPos set(double x, double y, double z) {
            return this.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        public BlockPos.MutableBlockPos set(Vec3i pos) {
            return this.set(pos.getX(), pos.getY(), pos.getZ());
        }

        public BlockPos.MutableBlockPos set(long pos) {
            return this.set(getX(pos), getY(pos), getZ(pos));
        }

        public BlockPos.MutableBlockPos set(AxisCycle axis, int x, int y, int z) {
            return this.set(axis.cycle(x, y, z, Direction.Axis.X), axis.cycle(x, y, z, Direction.Axis.Y), axis.cycle(x, y, z, Direction.Axis.Z));
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Direction direction) {
            return this.set(pos.getX() + direction.getStepX(), pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ());
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, int x, int y, int z) {
            return this.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i vec1, Vec3i vec2) {
            return this.set(vec1.getX() + vec2.getX(), vec1.getY() + vec2.getY(), vec1.getZ() + vec2.getZ());
        }

        public BlockPos.MutableBlockPos move(Direction direction) {
            return this.move(direction, 1);
        }

        public BlockPos.MutableBlockPos move(Direction direction, int distance) {
            return this.set(this.getX() + direction.getStepX() * distance, this.getY() + direction.getStepY() * distance, this.getZ() + direction.getStepZ() * distance);
        }

        public BlockPos.MutableBlockPos move(int dx, int dy, int dz) {
            return this.set(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
        }

        public BlockPos.MutableBlockPos move(Vec3i vec) {
            return this.set(this.getX() + vec.getX(), this.getY() + vec.getY(), this.getZ() + vec.getZ());
        }

        public BlockPos.MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
            switch (axis) {
                case X:
                    return this.set(Mth.clamp(this.getX(), min, max), this.getY(), this.getZ());
                case Y:
                    return this.set(this.getX(), Mth.clamp(this.getY(), min, max), this.getZ());
                case Z:
                    return this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), min, max));
                default:
                    throw new IllegalStateException("Unable to clamp axis " + axis);
            }
        }

        // Paper start - comment out useless overrides @Override - TODO figure out why this is suddenly important to keep
        @Override
        public BlockPos.MutableBlockPos setX(int i) {
            this.x = i; // Paper
            return this;
        }

        @Override
        public BlockPos.MutableBlockPos setY(int i) {
            this.y = i; // Paper
            return this;
        }

        @Override
        public BlockPos.MutableBlockPos setZ(int i) {
            this.z = i; // Paper
            return this;
        }
        // Paper end

        @Override
        public BlockPos immutable() {
            return new BlockPos(this);
        }
    }
}
