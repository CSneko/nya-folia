package net.minecraft.world.level;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ClipContext {

    private final Vec3 from;
    private final Vec3 to;
    public final ClipContext.Block block; // Paper - optimise collisions - public
    public final ClipContext.Fluid fluid; // Paper - optimise collisions - public
    private final CollisionContext collisionContext;

    public ClipContext(Vec3 start, Vec3 end, ClipContext.Block shapeType, ClipContext.Fluid fluidHandling, Entity entity) {
        this.from = start;
        this.to = end;
        this.block = shapeType;
        this.fluid = fluidHandling;
        this.collisionContext = (entity == null) ? CollisionContext.empty() : CollisionContext.of(entity); // CraftBukkit
    }

    public Vec3 getTo() {
        return this.to;
    }

    public Vec3 getFrom() {
        return this.from;
    }

    public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
        return this.block.get(state, world, pos, this.collisionContext);
    }

    public VoxelShape getFluidShape(FluidState state, BlockGetter world, BlockPos pos) {
        return this.fluid.canPick(state) ? state.getShape(world, pos) : Shapes.empty();
    }

    public static enum Block implements ClipContext.ShapeGetter {

        COLLIDER(BlockBehaviour.BlockStateBase::getCollisionShape), OUTLINE(BlockBehaviour.BlockStateBase::getShape), VISUAL(BlockBehaviour.BlockStateBase::getVisualShape), FALLDAMAGE_RESETTING((iblockdata, iblockaccess, blockposition, voxelshapecollision) -> {
            return iblockdata.is(BlockTags.FALL_DAMAGE_RESETTING) ? Shapes.block() : Shapes.empty();
        });

        private final ClipContext.ShapeGetter shapeGetter;

        private Block(ClipContext.ShapeGetter raytrace_c) {
            this.shapeGetter = raytrace_c;
        }

        @Override
        public VoxelShape get(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return this.shapeGetter.get(state, world, pos, context);
        }
    }

    public static enum Fluid {

        NONE((fluid) -> {
            return false;
        }), SOURCE_ONLY(FluidState::isSource), ANY((fluid) -> {
            return !fluid.isEmpty();
        }), WATER((fluid) -> {
            return fluid.is(FluidTags.WATER);
        });

        private final Predicate<FluidState> canPick;

        private Fluid(Predicate<FluidState> predicate) { // CraftBukkit - decompile error
            this.canPick = predicate;
        }

        public boolean canPick(FluidState state) {
            return this.canPick.test(state);
        }
    }

    public interface ShapeGetter {

        VoxelShape get(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context);
    }
}
