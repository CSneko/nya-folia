package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.event.block.BlockFormEvent;
// CraftBukkit end

public class ConcretePowderBlock extends FallingBlock {

    private final BlockState concrete;

    public ConcretePowderBlock(Block hardened, BlockBehaviour.Properties settings) {
        super(settings);
        this.concrete = hardened.defaultBlockState();
    }

    @Override
    public void onLand(Level world, BlockPos pos, BlockState fallingBlockState, BlockState currentStateInPos, FallingBlockEntity fallingBlockEntity) {
        if (ConcretePowderBlock.shouldSolidify(world, pos, currentStateInPos)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, pos, this.concrete, 3); // CraftBukkit
        }

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        // CraftBukkit start
        if (!ConcretePowderBlock.shouldSolidify(world, blockposition, iblockdata)) {
            return super.getStateForPlacement(ctx);
        }

        // TODO: An event factory call for methods like this
        CraftBlockState blockState = CraftBlockStates.getBlockState(world, blockposition);
        blockState.setData(this.concrete);

        BlockFormEvent event = new BlockFormEvent(blockState.getBlock(), blockState);
        world.getServer().server.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            return blockState.getHandle();
        }

        return super.getStateForPlacement(ctx);
        // CraftBukkit end
    }

    private static boolean shouldSolidify(BlockGetter world, BlockPos pos, BlockState state) {
        return ConcretePowderBlock.canSolidify(state) || ConcretePowderBlock.touchesLiquid(world, pos);
    }

    private static boolean touchesLiquid(BlockGetter world, BlockPos pos) {
        boolean flag = false;
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];
            BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition);

            if (enumdirection != Direction.DOWN || ConcretePowderBlock.canSolidify(iblockdata)) {
                blockposition_mutableblockposition.setWithOffset(pos, enumdirection);
                iblockdata = world.getBlockState(blockposition_mutableblockposition);
                if (ConcretePowderBlock.canSolidify(iblockdata) && !iblockdata.isFaceSturdy(world, pos, enumdirection.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static boolean canSolidify(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        // CraftBukkit start
        if (ConcretePowderBlock.touchesLiquid(world, pos)) {
            // Suppress during worldgen
            if (!(world instanceof Level)) {
                return this.concrete;
            }
            CraftBlockState blockState = CraftBlockStates.getBlockState(world, pos);
            blockState.setData(this.concrete);

            BlockFormEvent event = new BlockFormEvent(blockState.getBlock(), blockState);
            ((Level) world).getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        // CraftBukkit end
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter world, BlockPos pos) {
        return state.getMapColor(world, pos).col;
    }
}
