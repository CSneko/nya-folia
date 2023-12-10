package net.minecraft.world.level.redstone;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockPhysicsEvent;
// CraftBukkit end

public interface NeighborUpdater {

    Direction[] UPDATE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

    void shapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth);

    void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos);

    void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify);

    default void updateNeighborsAtExceptFromFacing(BlockPos pos, Block sourceBlock, @Nullable Direction except) {
        Direction[] aenumdirection = NeighborUpdater.UPDATE_ORDER;
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection1 = aenumdirection[j];

            if (enumdirection1 != except) {
                this.neighborChanged(pos.relative(enumdirection1), sourceBlock, pos);
            }
        }

    }

    static void executeShapeUpdate(LevelAccessor world, Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        BlockState iblockdata1 = world.getBlockState(pos);
        BlockState iblockdata2 = iblockdata1.updateShape(direction, neighborState, world, pos, neighborPos);

        Block.updateOrDestroy(iblockdata1, iblockdata2, world, pos, flags, maxUpdateDepth);
    }

    static void executeUpdate(Level world, BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        try {
            // CraftBukkit start
            CraftWorld cworld = ((ServerLevel) world).getWorld();
            if (cworld != null) {
                BlockPhysicsEvent event = new BlockPhysicsEvent(CraftBlock.at(world, pos), CraftBlockData.fromData(state), CraftBlock.at(world, sourcePos));
                ((ServerLevel) world).getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // CraftBukkit end
            state.neighborChanged(world, pos, sourceBlock, sourcePos, notify);
            // Spigot Start
        } catch (StackOverflowError ex) {
            world.lastPhysicsProblem = new BlockPos(pos);
            // Spigot End
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while updating neighbours");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being updated");

            crashreportsystemdetails.setDetail("Source block type", () -> {
                try {
                    return String.format(Locale.ROOT, "ID #%s (%s // %s)", BuiltInRegistries.BLOCK.getKey(sourceBlock), sourceBlock.getDescriptionId(), sourceBlock.getClass().getCanonicalName());
                } catch (Throwable throwable1) {
                    return "ID #" + BuiltInRegistries.BLOCK.getKey(sourceBlock);
                }
            });
            CrashReportCategory.populateBlockDetails(crashreportsystemdetails, world, pos, state);
            throw new ReportedException(crashreport);
        }
    }
}
