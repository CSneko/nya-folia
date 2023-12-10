package net.minecraft.world.level.block.piston;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonMovingBlockEntity extends BlockEntity {
    private static final int TICKS_TO_EXTEND = 2;
    private static final double PUSH_OFFSET = 0.01D;
    public static final double TICK_MOVEMENT = 0.51D;
    private BlockState movedState = Blocks.AIR.defaultBlockState();
    private Direction direction;
    private boolean extending;
    private boolean isSourcePiston;
    private static final ThreadLocal<Direction> NOCLIP = ThreadLocal.withInitial(() -> {
        return null;
    });
    private float progress;
    private float progressO;
    private long lastTicked;
    private int deathTicks;

    public PistonMovingBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.PISTON, pos, state);
    }

    public PistonMovingBlockEntity(BlockPos pos, BlockState state, BlockState pushedBlock, Direction facing, boolean extending, boolean source) {
        this(pos, state);
        this.movedState = pushedBlock;
        this.direction = facing;
        this.extending = extending;
        this.isSourcePiston = source;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public boolean isExtending() {
        return this.extending;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public boolean isSourcePiston() {
        return this.isSourcePiston;
    }

    public float getProgress(float tickDelta) {
        if (tickDelta > 1.0F) {
            tickDelta = 1.0F;
        }

        return Mth.lerp(tickDelta, this.progressO, this.progress);
    }

    public float getXOff(float tickDelta) {
        return (float)this.direction.getStepX() * this.getExtendedProgress(this.getProgress(tickDelta));
    }

    public float getYOff(float tickDelta) {
        return (float)this.direction.getStepY() * this.getExtendedProgress(this.getProgress(tickDelta));
    }

    public float getZOff(float tickDelta) {
        return (float)this.direction.getStepZ() * this.getExtendedProgress(this.getProgress(tickDelta));
    }

    private float getExtendedProgress(float progress) {
        return this.extending ? progress - 1.0F : 1.0F - progress;
    }

    private BlockState getCollisionRelatedBlockState() {
        return !this.isExtending() && this.isSourcePiston() && this.movedState.getBlock() instanceof PistonBaseBlock ? Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.SHORT, Boolean.valueOf(this.progress > 0.25F)).setValue(PistonHeadBlock.TYPE, this.movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT).setValue(PistonHeadBlock.FACING, this.movedState.getValue(PistonBaseBlock.FACING)) : this.movedState;
    }

    private static void moveCollidedEntities(Level world, BlockPos pos, float f, PistonMovingBlockEntity blockEntity) {
        Direction direction = blockEntity.getMovementDirection();
        double d = (double)(f - blockEntity.progress);
        VoxelShape voxelShape = blockEntity.getCollisionRelatedBlockState().getCollisionShape(world, pos);
        if (!voxelShape.isEmpty()) {
            AABB aABB = moveByPositionAndProgress(pos, voxelShape.bounds(), blockEntity);
            List<Entity> list = world.getEntities((Entity)null, PistonMath.getMovementArea(aABB, direction, d).minmax(aABB));
            if (!list.isEmpty()) {
                List<AABB> list2 = voxelShape.toAabbs();
                boolean bl = blockEntity.movedState.is(Blocks.SLIME_BLOCK);
                Iterator var12 = list.iterator();

                while(true) {
                    Entity entity;
                    while(true) {
                        if (!var12.hasNext()) {
                            return;
                        }

                        entity = (Entity)var12.next();
                        if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
                            if (!bl) {
                                break;
                            }

                            if (!(entity instanceof ServerPlayer)) {
                                Vec3 vec3 = entity.getDeltaMovement();
                                double e = vec3.x;
                                double g = vec3.y;
                                double h = vec3.z;
                                switch (direction.getAxis()) {
                                    case X:
                                        e = (double)direction.getStepX();
                                        break;
                                    case Y:
                                        g = (double)direction.getStepY();
                                        break;
                                    case Z:
                                        h = (double)direction.getStepZ();
                                }

                                entity.setDeltaMovement(e, g, h);
                                // Paper - EAR items stuck in in slime pushed by a piston
                                entity.activatedTick = Math.max(entity.activatedTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 10); // Folia - region threading
                                entity.activatedImmunityTick = Math.max(entity.activatedImmunityTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 10); // Folia - region threading
                                // Paper end
                                break;
                            }
                        }
                    }

                    double i = 0.0D;

                    for(AABB aABB2 : list2) {
                        AABB aABB3 = PistonMath.getMovementArea(moveByPositionAndProgress(pos, aABB2, blockEntity), direction, d);
                        AABB aABB4 = entity.getBoundingBox();
                        if (aABB3.intersects(aABB4)) {
                            i = Math.max(i, getMovement(aABB3, direction, aABB4));
                            if (i >= d) {
                                break;
                            }
                        }
                    }

                    if (!(i <= 0.0D)) {
                        i = Math.min(i, d) + 0.01D;
                        moveEntityByPiston(direction, entity, i, direction);
                        if (!blockEntity.extending && blockEntity.isSourcePiston) {
                            fixEntityWithinPistonBase(pos, entity, direction, d);
                        }
                    }
                }
            }
        }
    }

    private static void moveEntityByPiston(Direction direction, Entity entity, double distance, Direction movementDirection) {
        NOCLIP.set(direction);
        entity.move(MoverType.PISTON, new Vec3(distance * (double)movementDirection.getStepX(), distance * (double)movementDirection.getStepY(), distance * (double)movementDirection.getStepZ()));
        NOCLIP.set((Direction)null);
    }

    private static void moveStuckEntities(Level world, BlockPos pos, float f, PistonMovingBlockEntity blockEntity) {
        if (blockEntity.isStickyForEntities()) {
            Direction direction = blockEntity.getMovementDirection();
            if (direction.getAxis().isHorizontal()) {
                double d = blockEntity.movedState.getCollisionShape(world, pos).max(Direction.Axis.Y);
                AABB aABB = moveByPositionAndProgress(pos, new AABB(0.0D, d, 0.0D, 1.0D, 1.5000010000000001D, 1.0D), blockEntity);
                double e = (double)(f - blockEntity.progress);

                for(Entity entity : world.getEntities((Entity)null, aABB, (entityx) -> {
                    return matchesStickyCritera(aABB, entityx, pos);
                })) {
                    moveEntityByPiston(direction, entity, e, direction);
                }

            }
        }
    }

    private static boolean matchesStickyCritera(AABB box, Entity entity, BlockPos pos) {
        return entity.getPistonPushReaction() == PushReaction.NORMAL && entity.onGround() && (entity.isSupportedBy(pos) || entity.getX() >= box.minX && entity.getX() <= box.maxX && entity.getZ() >= box.minZ && entity.getZ() <= box.maxZ);
    }

    private boolean isStickyForEntities() {
        return this.movedState.is(Blocks.HONEY_BLOCK);
    }

    public Direction getMovementDirection() {
        return this.extending ? this.direction : this.direction.getOpposite();
    }

    private static double getMovement(AABB aABB, Direction direction, AABB aABB2) {
        switch (direction) {
            case EAST:
                return aABB.maxX - aABB2.minX;
            case WEST:
                return aABB2.maxX - aABB.minX;
            case UP:
            default:
                return aABB.maxY - aABB2.minY;
            case DOWN:
                return aABB2.maxY - aABB.minY;
            case SOUTH:
                return aABB.maxZ - aABB2.minZ;
            case NORTH:
                return aABB2.maxZ - aABB.minZ;
        }
    }

    private static AABB moveByPositionAndProgress(BlockPos pos, AABB box, PistonMovingBlockEntity blockEntity) {
        double d = (double)blockEntity.getExtendedProgress(blockEntity.progress);
        return box.move((double)pos.getX() + d * (double)blockEntity.direction.getStepX(), (double)pos.getY() + d * (double)blockEntity.direction.getStepY(), (double)pos.getZ() + d * (double)blockEntity.direction.getStepZ());
    }

    private static void fixEntityWithinPistonBase(BlockPos pos, Entity entity, Direction direction, double amount) {
        AABB aABB = entity.getBoundingBox();
        AABB aABB2 = Shapes.block().bounds().move(pos);
        if (aABB.intersects(aABB2)) {
            Direction direction2 = direction.getOpposite();
            double d = getMovement(aABB2, direction2, aABB) + 0.01D;
            double e = getMovement(aABB2, direction2, aABB.intersect(aABB2)) + 0.01D;
            if (Math.abs(d - e) < 0.01D) {
                d = Math.min(d, amount) + 0.01D;
                moveEntityByPiston(direction, entity, d, direction2);
            }
        }

    }

    public BlockState getMovedState() {
        return this.movedState;
    }

    public void finalTick() {
        if (this.level != null && (this.progressO < 1.0F || this.level.isClientSide)) {
            this.progress = 1.0F;
            this.progressO = this.progress;
            this.level.removeBlockEntity(this.worldPosition);
            this.setRemoved();
            if (this.level.getBlockState(this.worldPosition).is(Blocks.MOVING_PISTON)) {
                BlockState blockState;
                if (this.isSourcePiston) {
                    blockState = Blocks.AIR.defaultBlockState();
                } else {
                    blockState = Block.updateFromNeighbourShapes(this.movedState, this.level, this.worldPosition);
                }

                this.level.setBlock(this.worldPosition, blockState, 3);
                this.level.neighborChanged(this.worldPosition, blockState.getBlock(), this.worldPosition);
            }
        }

    }

    public static void tick(Level world, BlockPos pos, BlockState state, PistonMovingBlockEntity blockEntity) {
        blockEntity.lastTicked = world.getGameTime();
        blockEntity.progressO = blockEntity.progress;
        if (blockEntity.progressO >= 1.0F) {
            if (world.isClientSide && blockEntity.deathTicks < 5) {
                ++blockEntity.deathTicks;
            } else {
                world.removeBlockEntity(pos);
                blockEntity.setRemoved();
                if (world.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
                    BlockState blockState = Block.updateFromNeighbourShapes(blockEntity.movedState, world, pos);
                    if (blockState.isAir()) {
                        world.setBlock(pos, blockEntity.movedState, io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPistonDuplication ? 84 : (84 | Block.UPDATE_CLIENTS)); // Paper - force notify (flag 2), it's possible the set type by the piston block (which doesn't notify) set this block to air
                        Block.updateOrDestroy(blockEntity.movedState, blockState, world, pos, 3);
                    } else {
                        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
                            blockState = blockState.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(false));
                        }

                        world.setBlock(pos, blockState, 67);
                        world.neighborChanged(pos, blockState.getBlock(), pos);
                    }
                }

            }
        } else {
            float f = blockEntity.progress + 0.5F;
            moveCollidedEntities(world, pos, f, blockEntity);
            moveStuckEntities(world, pos, f, blockEntity);
            blockEntity.progress = f;
            if (blockEntity.progress >= 1.0F) {
                blockEntity.progress = 1.0F;
            }

        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        HolderGetter<Block> holderGetter = (HolderGetter<Block>)(this.level != null ? this.level.holderLookup(Registries.BLOCK) : BuiltInRegistries.BLOCK.asLookup());
        this.movedState = NbtUtils.readBlockState(holderGetter, nbt.getCompound("blockState"));
        this.direction = Direction.from3DDataValue(nbt.getInt("facing"));
        this.progress = nbt.getFloat("progress");
        this.progressO = this.progress;
        this.extending = nbt.getBoolean("extending");
        this.isSourcePiston = nbt.getBoolean("source");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("blockState", NbtUtils.writeBlockState(this.movedState));
        nbt.putInt("facing", this.direction.get3DDataValue());
        nbt.putFloat("progress", this.progressO);
        nbt.putBoolean("extending", this.extending);
        nbt.putBoolean("source", this.isSourcePiston);
    }

    public VoxelShape getCollisionShape(BlockGetter world, BlockPos pos) {
        VoxelShape voxelShape;
        if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
            voxelShape = this.movedState.setValue(PistonBaseBlock.EXTENDED, Boolean.valueOf(true)).getCollisionShape(world, pos);
        } else {
            voxelShape = Shapes.empty();
        }

        Direction direction = NOCLIP.get();
        if ((double)this.progress < 1.0D && direction == this.getMovementDirection()) {
            return voxelShape;
        } else {
            BlockState blockState;
            if (this.isSourcePiston()) {
                blockState = Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, this.direction).setValue(PistonHeadBlock.SHORT, Boolean.valueOf(this.extending != 1.0F - this.progress < 0.25F));
            } else {
                blockState = this.movedState;
            }

            float f = this.getExtendedProgress(this.progress);
            double d = (double)((float)this.direction.getStepX() * f);
            double e = (double)((float)this.direction.getStepY() * f);
            double g = (double)((float)this.direction.getStepZ() * f);
            return Shapes.or(voxelShape, blockState.getCollisionShape(world, pos).move(d, e, g));
        }
    }

    public long getLastTicked() {
        return this.lastTicked;
    }

    @Override
    public void setLevel(Level world) {
        super.setLevel(world);
        if (world.holderLookup(Registries.BLOCK).get(this.movedState.getBlock().builtInRegistryHolder().key()).isEmpty()) {
            this.movedState = Blocks.AIR.defaultBlockState();
        }

    }
}
