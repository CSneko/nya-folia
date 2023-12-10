package net.minecraft.world.level.portal;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
// CraftBukkit end

public class PortalShape {

    private static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;
    private static final BlockBehaviour.StatePredicate FRAME = (iblockdata, iblockaccess, blockposition) -> {
        return iblockdata.is(Blocks.OBSIDIAN);
    };
    private static final float SAFE_TRAVEL_MAX_ENTITY_XY = 4.0F;
    private static final double SAFE_TRAVEL_MAX_VERTICAL_DELTA = 1.0D;
    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private int numPortalBlocks;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private final int width;
    org.bukkit.craftbukkit.util.BlockStateListPopulator blocks; // CraftBukkit - add field

    public static Optional<PortalShape> findEmptyPortalShape(LevelAccessor world, BlockPos pos, Direction.Axis axis) {
        return PortalShape.findPortalShape(world, pos, (blockportalshape) -> {
            return blockportalshape.isValid() && blockportalshape.numPortalBlocks == 0;
        }, axis);
    }

    public static Optional<PortalShape> findPortalShape(LevelAccessor world, BlockPos pos, Predicate<PortalShape> validator, Direction.Axis axis) {
        Optional<PortalShape> optional = Optional.of(new PortalShape(world, pos, axis)).filter(validator);

        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis enumdirection_enumaxis1 = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;

            return Optional.of(new PortalShape(world, pos, enumdirection_enumaxis1)).filter(validator);
        }
    }

    public PortalShape(LevelAccessor world, BlockPos pos, Direction.Axis axis) {
        this.blocks = new org.bukkit.craftbukkit.util.BlockStateListPopulator(world.getMinecraftWorld()); // CraftBukkit
        this.level = world;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = this.calculateBottomLeft(pos);
        if (this.bottomLeft == null) {
            this.bottomLeft = pos;
            this.width = 1;
            this.height = 1;
        } else {
            this.width = this.calculateWidth();
            if (this.width > 0) {
                this.height = this.calculateHeight();
            }
        }

    }

    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos) {
        for (int i = Math.max(this.level.getMinBuildHeight(), pos.getY() - 21); pos.getY() > i && PortalShape.isEmpty(this.level.getBlockState(pos.below())); pos = pos.below()) {
            ;
        }

        Direction enumdirection = this.rightDir.getOpposite();
        int j = this.getDistanceUntilEdgeAboveFrame(pos, enumdirection) - 1;

        return j < 0 ? null : pos.relative(enumdirection, j);
    }

    private int calculateWidth() {
        int i = this.getDistanceUntilEdgeAboveFrame(this.bottomLeft, this.rightDir);

        return i >= 2 && i <= 21 ? i : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= 21; ++i) {
            blockposition_mutableblockposition.set(pos).move(direction, i);
            BlockState iblockdata = this.level.getBlockState(blockposition_mutableblockposition);

            if (!PortalShape.isEmpty(iblockdata)) {
                if (PortalShape.FRAME.test(iblockdata, this.level, blockposition_mutableblockposition)) {
                    this.blocks.setBlock(blockposition_mutableblockposition, iblockdata, 18); // CraftBukkit - lower left / right
                    return i;
                }
                break;
            }

            BlockState iblockdata1 = this.level.getBlockState(blockposition_mutableblockposition.move(Direction.DOWN));

            if (!PortalShape.FRAME.test(iblockdata1, this.level, blockposition_mutableblockposition)) {
                break;
            }
            this.blocks.setBlock(blockposition_mutableblockposition, iblockdata1, 18); // CraftBukkit - bottom row
        }

        return 0;
    }

    private int calculateHeight() {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        int i = this.getDistanceUntilTop(blockposition_mutableblockposition);

        return i >= 3 && i <= 21 && this.hasTopFrame(blockposition_mutableblockposition, i) ? i : 0;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos pos, int height) {
        for (int j = 0; j < this.width; ++j) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition1 = pos.set(this.bottomLeft).move(Direction.UP, height).move(this.rightDir, j);

            if (!PortalShape.FRAME.test(this.level.getBlockState(blockposition_mutableblockposition1), this.level, blockposition_mutableblockposition1)) {
                return false;
            }
            this.blocks.setBlock(blockposition_mutableblockposition1, this.level.getBlockState(blockposition_mutableblockposition1), 18); // CraftBukkit - upper row
        }

        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos pos) {
        for (int i = 0; i < 21; ++i) {
            pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, -1);
            if (!PortalShape.FRAME.test(this.level.getBlockState(pos), this.level, pos)) {
                return i;
            }

            pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, this.width);
            if (!PortalShape.FRAME.test(this.level.getBlockState(pos), this.level, pos)) {
                return i;
            }

            for (int j = 0; j < this.width; ++j) {
                pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, j);
                BlockState iblockdata = this.level.getBlockState(pos);

                if (!PortalShape.isEmpty(iblockdata)) {
                    return i;
                }

                if (iblockdata.is(Blocks.NETHER_PORTAL)) {
                    ++this.numPortalBlocks;
                }
            }
            // CraftBukkit start - left and right
            this.blocks.setBlock(pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, -1), this.level.getBlockState(pos), 18);
            this.blocks.setBlock(pos.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, this.width), this.level.getBlockState(pos), 18);
            // CraftBukkit end
        }

        return 21;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    public boolean isValid() {
        return this.bottomLeft != null && this.width >= 2 && this.width <= 21 && this.height >= 3 && this.height <= 21;
    }

    // CraftBukkit start - return boolean
    // Paper start - ItemActionContext param
    @Deprecated public boolean createPortalBlocks() { return this.createPortalBlocks(null); }
    public boolean createPortalBlocks(UseOnContext itemActionContext) {
        // Paper end
        org.bukkit.World bworld = this.level.getMinecraftWorld().getWorld();

        // Copy below for loop
        BlockState iblockdata = (BlockState) Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);

        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1)).forEach((blockposition) -> {
            this.blocks.setBlock(blockposition, iblockdata, 18);
        });

        PortalCreateEvent event = new PortalCreateEvent((java.util.List<org.bukkit.block.BlockState>) (java.util.List) blocks.getList(), bworld, itemActionContext == null || itemActionContext.getPlayer() == null ? null : itemActionContext.getPlayer().getBukkitEntity(), PortalCreateEvent.CreateReason.FIRE); // Paper - pass entity param
        this.level.getMinecraftWorld().getServer().server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }
        // CraftBukkit end
        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1)).forEach((blockposition) -> {
            this.level.setBlock(blockposition, iblockdata, 18);
        });
        return true; // CraftBukkit
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    public static Vec3 getRelativePosition(BlockUtil.FoundRectangle portalRect, Direction.Axis portalAxis, Vec3 entityPos, EntityDimensions entityDimensions) {
        double d0 = (double) portalRect.axis1Size - (double) entityDimensions.width;
        double d1 = (double) portalRect.axis2Size - (double) entityDimensions.height;
        BlockPos blockposition = portalRect.minCorner;
        double d2;

        if (d0 > 0.0D) {
            float f = (float) blockposition.get(portalAxis) + entityDimensions.width / 2.0F;

            d2 = Mth.clamp(Mth.inverseLerp(entityPos.get(portalAxis) - (double) f, 0.0D, d0), 0.0D, 1.0D);
        } else {
            d2 = 0.5D;
        }

        Direction.Axis enumdirection_enumaxis1;
        double d3;

        if (d1 > 0.0D) {
            enumdirection_enumaxis1 = Direction.Axis.Y;
            d3 = Mth.clamp(Mth.inverseLerp(entityPos.get(enumdirection_enumaxis1) - (double) blockposition.get(enumdirection_enumaxis1), 0.0D, d1), 0.0D, 1.0D);
        } else {
            d3 = 0.0D;
        }

        enumdirection_enumaxis1 = portalAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        double d4 = entityPos.get(enumdirection_enumaxis1) - ((double) blockposition.get(enumdirection_enumaxis1) + 0.5D);

        return new Vec3(d2, d3, d4);
    }

    public static PortalInfo createPortalInfo(ServerLevel worldserver, BlockUtil.FoundRectangle blockutil_rectangle, Direction.Axis enumdirection_enumaxis, Vec3 vec3d, Entity entity, Vec3 vec3d1, float f, float f1, CraftPortalEvent portalEventInfo) { // CraftBukkit
        BlockPos blockposition = blockutil_rectangle.minCorner;
        BlockState iblockdata = worldserver.getBlockState(blockposition);
        Direction.Axis enumdirection_enumaxis1 = (Direction.Axis) iblockdata.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        double d0 = (double) blockutil_rectangle.axis1Size;
        double d1 = (double) blockutil_rectangle.axis2Size;
        EntityDimensions entitysize = entity.getDimensions(entity.getPose());
        int i = enumdirection_enumaxis == enumdirection_enumaxis1 ? 0 : 90;
        Vec3 vec3d2 = enumdirection_enumaxis == enumdirection_enumaxis1 ? vec3d1 : new Vec3(vec3d1.z, vec3d1.y, -vec3d1.x);
        double d2 = (double) entitysize.width / 2.0D + (d0 - (double) entitysize.width) * vec3d.x();
        double d3 = (d1 - (double) entitysize.height) * vec3d.y();
        double d4 = 0.5D + vec3d.z();
        boolean flag = enumdirection_enumaxis1 == Direction.Axis.X;
        Vec3 vec3d3 = new Vec3((double) blockposition.getX() + (flag ? d2 : d4), (double) blockposition.getY() + d3, (double) blockposition.getZ() + (flag ? d4 : d2));
        Vec3 vec3d4 = PortalShape.findCollisionFreePosition(vec3d3, worldserver, entity, entitysize);

        return new PortalInfo(vec3d4, vec3d2, f + (float) i, f1, worldserver, portalEventInfo); // CraftBukkit
    }

    private static Vec3 findCollisionFreePosition(Vec3 fallback, ServerLevel world, Entity entity, EntityDimensions dimensions) {
        if (dimensions.width <= 4.0F && dimensions.height <= 4.0F) {
            double d0 = (double) dimensions.height / 2.0D;
            Vec3 vec3d1 = fallback.add(0.0D, d0, 0.0D);
            VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec3d1, (double) dimensions.width, 0.0D, (double) dimensions.width).expandTowards(0.0D, 1.0D, 0.0D).inflate(1.0E-6D));
            Optional<Vec3> optional = world.findFreePosition(entity, voxelshape, vec3d1, (double) dimensions.width, (double) dimensions.height, (double) dimensions.width);
            Optional<Vec3> optional1 = optional.map((vec3d2) -> {
                return vec3d2.subtract(0.0D, d0, 0.0D);
            });

            return (Vec3) optional1.orElse(fallback);
        } else {
            return fallback;
        }
    }
}
