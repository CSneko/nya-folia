package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockCollisions<T> extends AbstractIterator<T> {
    private final AABB box;
    private final CollisionContext context;
    private final Cursor3D cursor;
    private final BlockPos.MutableBlockPos pos;
    private final VoxelShape entityShape;
    private final CollisionGetter collisionGetter;
    private final boolean onlySuffocatingBlocks;
    @Nullable
    private BlockGetter cachedBlockGetter;
    private long cachedBlockGetterPos;
    private final BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider;

    public BlockCollisions(CollisionGetter world, @Nullable Entity entity, AABB box, boolean forEntity, BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultFunction) {
        this.context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
        this.pos = new BlockPos.MutableBlockPos();
        this.entityShape = Shapes.create(box);
        this.collisionGetter = world;
        this.box = box;
        this.onlySuffocatingBlocks = forEntity;
        this.resultProvider = resultFunction;
        int i = Mth.floor(box.minX - 1.0E-7D) - 1;
        int j = Mth.floor(box.maxX + 1.0E-7D) + 1;
        int k = Mth.floor(box.minY - 1.0E-7D) - 1;
        int l = Mth.floor(box.maxY + 1.0E-7D) + 1;
        int m = Mth.floor(box.minZ - 1.0E-7D) - 1;
        int n = Mth.floor(box.maxZ + 1.0E-7D) + 1;
        this.cursor = new Cursor3D(i, k, m, j, l, n);
    }

    @Nullable
    private BlockGetter getChunk(int x, int z) {
        int i = SectionPos.blockToSectionCoord(x);
        int j = SectionPos.blockToSectionCoord(z);
        long l = ChunkPos.asLong(i, j);
        if (this.cachedBlockGetter != null && this.cachedBlockGetterPos == l) {
            return this.cachedBlockGetter;
        } else {
            BlockGetter blockGetter = this.collisionGetter.getChunkForCollisions(i, j);
            this.cachedBlockGetter = blockGetter;
            this.cachedBlockGetterPos = l;
            return blockGetter;
        }
    }

    @Override
    protected T computeNext() {
        while(true) {
            if (this.cursor.advance()) {
                int i = this.cursor.nextX(); final int x = i; // Paper
                int j = this.cursor.nextY(); final int y = j; // Paper
                int k = this.cursor.nextZ(); final int z = k; // Paper
                int l = this.cursor.getNextType();
                if (l == 3) {
                    continue;
                }
                // Paper start - ensure we don't load chunks
                final @Nullable Entity source = this.context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext ? entityContext.getEntity() : null;
                boolean far = source != null && io.papermc.paper.util.MCUtil.distanceSq(source.getX(), y, source.getZ(), x, y, z) > 14;
                this.pos.set(x, y, z);

                BlockState blockState;
                if (this.collisionGetter instanceof net.minecraft.server.level.WorldGenRegion) {
                    BlockGetter blockGetter = this.getChunk(x, z);
                    if (blockGetter == null) {
                       continue;
                    }
                    blockState = blockGetter.getBlockState(this.pos);
                } else if ((!far && source instanceof net.minecraft.server.level.ServerPlayer) || (source != null && source.collisionLoadChunks)) {
                    blockState = this.collisionGetter.getBlockState(this.pos);
                } else {
                    blockState = this.collisionGetter.getBlockStateIfLoaded(this.pos);
                }

                if (blockState == null) {
                    if (!(source instanceof net.minecraft.server.level.ServerPlayer) || source.level().paperConfig().chunks.preventMovingIntoUnloadedChunks) {
                        return this.resultProvider.apply(new BlockPos.MutableBlockPos(x, y, z), Shapes.create(far ? source.getBoundingBox() : new AABB(new BlockPos(x, y, z))));
                    }
                    // Paper end
                    continue;
                }

                // Paper - moved up
                if (/*this.onlySuffocatingBlocks && (!blockState.isSuffocating(blockGetter, this.pos)) ||*/ l == 1 && !blockState.hasLargeCollisionShape() || l == 2 && !blockState.is(Blocks.MOVING_PISTON)) { // Paper - onlySuffocatingBlocks is only true on the client, so we don't care about it here
                    continue;
                }

                VoxelShape voxelShape = blockState.getCollisionShape(this.collisionGetter, this.pos, this.context);
                if (voxelShape == Shapes.block()) {
                    if (!io.papermc.paper.util.CollisionUtil.voxelShapeIntersect(this.box, (double)i, (double)j, (double)k, (double)i + 1.0D, (double)j + 1.0D, (double)k + 1.0D)) { // Paper - keep vanilla behavior for voxelshape intersection - See comment in CollisionUtil
                        continue;
                    }

                    return this.resultProvider.apply(this.pos, voxelShape.move((double)i, (double)j, (double)k));
                }

                VoxelShape voxelShape2 = voxelShape.move((double)i, (double)j, (double)k);
                if (voxelShape2.isEmpty() || !Shapes.joinIsNotEmpty(voxelShape2, this.entityShape, BooleanOp.AND)) {
                    continue;
                }

                return this.resultProvider.apply(this.pos, voxelShape2);
            }

            return this.endOfData();
        }
    }
}
