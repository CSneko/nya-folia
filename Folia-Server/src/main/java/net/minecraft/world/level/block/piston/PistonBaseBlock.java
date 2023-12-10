package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import com.google.common.collect.ImmutableList;
import java.util.AbstractList;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
// CraftBukkit end

public class PistonBaseBlock extends DirectionalBlock {

    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    public static final int TRIGGER_EXTEND = 0;
    public static final int TRIGGER_CONTRACT = 1;
    public static final int TRIGGER_DROP = 2;
    public static final float PLATFORM_THICKNESS = 4.0F;
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 0.0D, 12.0D, 16.0D, 16.0D);
    protected static final VoxelShape WEST_AABB = Block.box(4.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 12.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 4.0D, 16.0D, 16.0D, 16.0D);
    protected static final VoxelShape UP_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);
    protected static final VoxelShape DOWN_AABB = Block.box(0.0D, 4.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final boolean isSticky;

    public PistonBaseBlock(boolean sticky, BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(PistonBaseBlock.FACING, Direction.NORTH)).setValue(PistonBaseBlock.EXTENDED, false));
        this.isSticky = sticky;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if ((Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
            switch ((Direction) state.getValue(PistonBaseBlock.FACING)) {
                case DOWN:
                    return PistonBaseBlock.DOWN_AABB;
                case UP:
                default:
                    return PistonBaseBlock.UP_AABB;
                case NORTH:
                    return PistonBaseBlock.NORTH_AABB;
                case SOUTH:
                    return PistonBaseBlock.SOUTH_AABB;
                case WEST:
                    return PistonBaseBlock.WEST_AABB;
                case EAST:
                    return PistonBaseBlock.EAST_AABB;
            }
        } else {
            return Shapes.block();
        }
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (!world.isClientSide) {
            this.checkIfExtend(world, pos, state);
        }

    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide) {
            this.checkIfExtend(world, pos, state);
        }

    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            if (!world.isClientSide && world.getBlockEntity(pos) == null) {
                this.checkIfExtend(world, pos, state);
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) ((BlockState) this.defaultBlockState().setValue(PistonBaseBlock.FACING, ctx.getNearestLookingDirection().getOpposite())).setValue(PistonBaseBlock.EXTENDED, false);
    }

    private void checkIfExtend(Level world, BlockPos pos, BlockState state) {
        Direction enumdirection = (Direction) state.getValue(PistonBaseBlock.FACING);
        boolean flag = this.getNeighborSignal(world, pos, enumdirection);

        if (flag && !(Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
            if ((new PistonStructureResolver(world, pos, enumdirection, true)).resolve()) {
                world.blockEvent(pos, this, 0, enumdirection.get3DDataValue());
            }
        } else if (!flag && (Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
            BlockPos blockposition1 = pos.relative(enumdirection, 2);
            BlockState iblockdata1 = world.getBlockState(blockposition1);
            byte b0 = 1;

            if (iblockdata1.is(Blocks.MOVING_PISTON) && iblockdata1.getValue(PistonBaseBlock.FACING) == enumdirection) {
                BlockEntity tileentity = world.getBlockEntity(blockposition1);

                if (tileentity instanceof PistonMovingBlockEntity) {
                    PistonMovingBlockEntity tileentitypiston = (PistonMovingBlockEntity) tileentity;

                    if (tileentitypiston.isExtending() && (tileentitypiston.getProgress(0.0F) < 0.5F || world.getGameTime() == tileentitypiston.getLastTicked() || ((ServerLevel) world).isHandlingTick())) {
                        b0 = 2;
                    }
                }
            }

            // CraftBukkit start
            // if (!this.isSticky) { // Paper - Move further down
            //     org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            //     BlockPistonRetractEvent event = new BlockPistonRetractEvent(block, ImmutableList.<org.bukkit.block.Block>of(), CraftBlock.notchToBlockFace(enumdirection));
            //     world.getCraftServer().getPluginManager().callEvent(event);
            //
            //     if (event.isCancelled()) {
            //         return;
            //     }
            // }
            // PAIL: checkME - what happened to setTypeAndData?
            // CraftBukkit end
            world.blockEvent(pos, this, b0, enumdirection.get3DDataValue());
        }

    }

    private boolean getNeighborSignal(SignalGetter world, BlockPos pos, Direction pistonFace) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        int j;

        for (j = 0; j < i; ++j) {
            Direction enumdirection1 = aenumdirection[j];

            if (enumdirection1 != pistonFace && world.hasSignal(pos.relative(enumdirection1), enumdirection1)) {
                return true;
            }
        }

        if (world.hasSignal(pos, Direction.DOWN)) {
            return true;
        } else {
            BlockPos blockposition1 = pos.above();
            Direction[] aenumdirection1 = Direction.values();

            j = aenumdirection1.length;

            for (int k = 0; k < j; ++k) {
                Direction enumdirection2 = aenumdirection1[k];

                if (enumdirection2 != Direction.DOWN && world.hasSignal(blockposition1.relative(enumdirection2), enumdirection2)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        Direction enumdirection = (Direction) state.getValue(PistonBaseBlock.FACING);
        // Paper start - prevent retracting when we're facing the wrong way (we were replaced before retraction could occur)
        Direction directionQueuedAs = Direction.from3DDataValue(data & 7); // Paper - copied from below
        if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits && enumdirection != directionQueuedAs) {
            return false;
        }
        // Paper end - prevent retracting when we're facing the wrong way
        BlockState iblockdata1 = (BlockState) state.setValue(PistonBaseBlock.EXTENDED, true);

        if (!world.isClientSide) {
            boolean flag = this.getNeighborSignal(world, pos, enumdirection);

            if (flag && (type == 1 || type == 2)) {
                world.setBlock(pos, iblockdata1, 2);
                return false;
            }

            if (!flag && type == 0) {
                return false;
            }
        }

        if (type == 0) {
            if (!this.moveBlocks(world, pos, enumdirection, true)) {
                return false;
            }

            world.setBlock(pos, iblockdata1, 67);
            world.playSound((Player) null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, world.random.nextFloat() * 0.25F + 0.6F);
            world.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(iblockdata1));
        } else if (type == 1 || type == 2) {
            BlockEntity tileentity = world.getBlockEntity(pos.relative(enumdirection));

            if (tileentity instanceof PistonMovingBlockEntity) {
                ((PistonMovingBlockEntity) tileentity).finalTick();
            }

            BlockState iblockdata2 = (BlockState) ((BlockState) Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, enumdirection)).setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);

            // Paper start - Move empty piston retract call to fix multiple event fires
            if (!this.isSticky) {
                if (!new BlockPistonRetractEvent(CraftBlock.at(world, pos), java.util.Collections.emptyList(), CraftBlock.notchToBlockFace(enumdirection)).callEvent()) {
                    return false;
                }
            }
            // Paper end
            world.setBlock(pos, iblockdata2, 20);
            world.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(pos, iblockdata2, (BlockState) this.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.from3DDataValue(data & 7)), enumdirection, false, true)); // Paper - diff on change
            world.blockUpdated(pos, iblockdata2.getBlock());
            iblockdata2.updateNeighbourShapes(world, pos, 2);
            if (this.isSticky) {
                BlockPos blockposition1 = pos.offset(enumdirection.getStepX() * 2, enumdirection.getStepY() * 2, enumdirection.getStepZ() * 2);
                BlockState iblockdata3 = world.getBlockState(blockposition1);
                boolean flag1 = false;

                if (iblockdata3.is(Blocks.MOVING_PISTON)) {
                    BlockEntity tileentity1 = world.getBlockEntity(blockposition1);

                    if (tileentity1 instanceof PistonMovingBlockEntity) {
                        PistonMovingBlockEntity tileentitypiston = (PistonMovingBlockEntity) tileentity1;

                        if (tileentitypiston.getDirection() == enumdirection && tileentitypiston.isExtending()) {
                            tileentitypiston.finalTick();
                            flag1 = true;
                        }
                    }
                }

                if (!flag1) {
                    if (type == 1 && !iblockdata3.isAir() && PistonBaseBlock.isPushable(iblockdata3, world, blockposition1, enumdirection.getOpposite(), false, enumdirection) && (iblockdata3.getPistonPushReaction() == PushReaction.NORMAL || iblockdata3.is(Blocks.PISTON) || iblockdata3.is(Blocks.STICKY_PISTON))) {
                        this.moveBlocks(world, pos, enumdirection, false);
                    } else {
                        // Paper start - fire BlockPistonRetractEvent for sticky pistons retracting nothing (air)
                        if (type == TRIGGER_CONTRACT && iblockdata2.isAir()) {
                            if (!new BlockPistonRetractEvent(CraftBlock.at(world, pos), java.util.Collections.emptyList(), CraftBlock.notchToBlockFace(enumdirection)).callEvent()) {
                                return false;
                            }
                        }
                        // Paper end
                        world.removeBlock(pos.relative(enumdirection), false);
                    }
                }
            } else {
                // Paper start - fix headless pistons breaking blocks
                BlockPos headPos = pos.relative(enumdirection);
                if (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits || world.getBlockState(headPos) == Blocks.PISTON_HEAD.defaultBlockState().setValue(FACING, enumdirection)) { // double check to make sure we're not a headless piston.
                    world.removeBlock(headPos, false);
                } else {
                    ((ServerLevel)world).getChunkSource().blockChanged(headPos); // ... fix client desync
                }
                // Paper end - fix headless pistons breaking blocks
            }

            world.playSound((Player) null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, world.random.nextFloat() * 0.15F + 0.6F);
            world.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(iblockdata2));
        }

        return true;
    }

    public static boolean isPushable(BlockState state, Level world, BlockPos pos, Direction direction, boolean canBreak, Direction pistonDir) {
        if (pos.getY() >= world.getMinBuildHeight() && pos.getY() <= world.getMaxBuildHeight() - 1 && world.getWorldBorder().isWithinBounds(pos)) {
            if (state.isAir()) {
                return true;
            } else if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.CRYING_OBSIDIAN) && !state.is(Blocks.RESPAWN_ANCHOR) && !state.is(Blocks.REINFORCED_DEEPSLATE)) {
                if (direction == Direction.DOWN && pos.getY() == world.getMinBuildHeight()) {
                    return false;
                } else if (direction == Direction.UP && pos.getY() == world.getMaxBuildHeight() - 1) {
                    return false;
                } else {
                    if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON)) {
                        if (state.getDestroySpeed(world, pos) == -1.0F) {
                            return false;
                        }

                        switch (state.getPistonPushReaction()) {
                            case BLOCK:
                                return false;
                            case DESTROY:
                                return canBreak;
                            case PUSH_ONLY:
                                return direction == pistonDir;
                        }
                    } else if ((Boolean) state.getValue(PistonBaseBlock.EXTENDED)) {
                        return false;
                    }

                    return !state.hasBlockEntity();
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean moveBlocks(Level world, BlockPos pos, Direction dir, boolean retract) {
        BlockPos blockposition1 = pos.relative(dir);

        if (!retract && world.getBlockState(blockposition1).is(Blocks.PISTON_HEAD)) {
            world.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 20);
        }

        PistonStructureResolver pistonextendschecker = new PistonStructureResolver(world, pos, dir, retract);

        if (!pistonextendschecker.resolve()) {
            return false;
        } else {
            Map<BlockPos, BlockState> map = Maps.newHashMap();
            List<BlockPos> list = pistonextendschecker.getToPush();
            List<BlockState> list1 = Lists.newArrayList();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition2 = (BlockPos) iterator.next();
                BlockState iblockdata = world.getBlockState(blockposition2);

                list1.add(iblockdata);
                map.put(blockposition2, iblockdata);
            }

            List<BlockPos> list2 = pistonextendschecker.getToDestroy();
            BlockState[] aiblockdata = new BlockState[list.size() + list2.size()];
            Direction enumdirection1 = retract ? dir : dir.getOpposite();
            int i = 0;
            // CraftBukkit start
            final org.bukkit.block.Block bblock = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());

            final List<BlockPos> moved = pistonextendschecker.getToPush();
            final List<BlockPos> broken = pistonextendschecker.getToDestroy();

            List<org.bukkit.block.Block> blocks = new AbstractList<org.bukkit.block.Block>() {

                @Override
                public int size() {
                    return moved.size() + broken.size();
                }

                @Override
                public org.bukkit.block.Block get(int index) {
                    if (index >= this.size() || index < 0) {
                        throw new ArrayIndexOutOfBoundsException(index);
                    }
                    BlockPos pos = (BlockPos) (index < moved.size() ? moved.get(index) : broken.get(index - moved.size()));
                    return bblock.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                }
            };
            org.bukkit.event.block.BlockPistonEvent event;
            if (retract) {
                event = new BlockPistonExtendEvent(bblock, blocks, CraftBlock.notchToBlockFace(enumdirection1));
            } else {
                event = new BlockPistonRetractEvent(bblock, blocks, CraftBlock.notchToBlockFace(enumdirection1));
            }
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                for (BlockPos b : broken) {
                    world.sendBlockUpdated(b, Blocks.AIR.defaultBlockState(), world.getBlockState(b), 3);
                }
                for (BlockPos b : moved) {
                    world.sendBlockUpdated(b, Blocks.AIR.defaultBlockState(), world.getBlockState(b), 3);
                    b = b.relative(enumdirection1);
                    world.sendBlockUpdated(b, Blocks.AIR.defaultBlockState(), world.getBlockState(b), 3);
                }
                return false;
            }
            // CraftBukkit end

            BlockPos blockposition3;
            int j;
            BlockState iblockdata1;

            for (j = list2.size() - 1; j >= 0; --j) {
                blockposition3 = (BlockPos) list2.get(j);
                iblockdata1 = world.getBlockState(blockposition3);
                BlockEntity tileentity = iblockdata1.hasBlockEntity() ? world.getBlockEntity(blockposition3) : null;

                dropResources(iblockdata1, world, blockposition3, tileentity, pos); // Paper
                world.setBlock(blockposition3, Blocks.AIR.defaultBlockState(), 18);
                world.gameEvent(GameEvent.BLOCK_DESTROY, blockposition3, GameEvent.Context.of(iblockdata1));
                if (!iblockdata1.is(BlockTags.FIRE)) {
                    world.addDestroyBlockEffect(blockposition3, iblockdata1);
                }

                aiblockdata[i++] = iblockdata1;
            }

            for (j = list.size() - 1; j >= 0; --j) {
                // Paper start - fix a variety of piston desync dupes
                boolean allowDesync = io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPistonDuplication;
                BlockPos oldPos = blockposition3 = (BlockPos) list.get(j);
                iblockdata1 = allowDesync ? world.getBlockState(oldPos) : null;
                // Paper end - fix a variety of piston desync dupes
                blockposition3 = blockposition3.relative(enumdirection1);
                map.remove(blockposition3);
                BlockState iblockdata2 = (BlockState) Blocks.MOVING_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, dir);

                world.setBlock(blockposition3, iblockdata2, 68);
                // Paper start - fix a variety of piston desync dupes
                if (!allowDesync) {
                    iblockdata1 = world.getBlockState(oldPos);
                    map.replace(oldPos, iblockdata1);
                }
                world.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockposition3, iblockdata2, allowDesync ? list1.get(j) : iblockdata1, dir, retract, false));
                if (!allowDesync) {
                    world.setBlock(oldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON | 1024); // set air to prevent later physics updates from seeing this block
                }
                // Paper end - fix a variety of piston desync dupes
                aiblockdata[i++] = iblockdata1;
            }

            if (retract) {
                PistonType blockpropertypistontype = this.isSticky ? PistonType.STICKY : PistonType.DEFAULT;
                BlockState iblockdata3 = (BlockState) ((BlockState) Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, dir)).setValue(PistonHeadBlock.TYPE, blockpropertypistontype);

                iblockdata1 = (BlockState) ((BlockState) Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, dir)).setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
                map.remove(blockposition1);
                world.setBlock(blockposition1, iblockdata1, 68);
                world.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockposition1, iblockdata1, iblockdata3, dir, true, true));
            }

            BlockState iblockdata4 = Blocks.AIR.defaultBlockState();
            Iterator iterator1 = map.keySet().iterator();

            while (iterator1.hasNext()) {
                BlockPos blockposition4 = (BlockPos) iterator1.next();

                world.setBlock(blockposition4, iblockdata4, 82);
            }

            iterator1 = map.entrySet().iterator();

            BlockPos blockposition5;

            while (iterator1.hasNext()) {
                Entry<BlockPos, BlockState> entry = (Entry) iterator1.next();

                blockposition5 = (BlockPos) entry.getKey();
                BlockState iblockdata5 = (BlockState) entry.getValue();

                iblockdata5.updateIndirectNeighbourShapes(world, blockposition5, 2);
                iblockdata4.updateNeighbourShapes(world, blockposition5, 2);
                iblockdata4.updateIndirectNeighbourShapes(world, blockposition5, 2);
            }

            i = 0;

            int k;

            for (k = list2.size() - 1; k >= 0; --k) {
                iblockdata1 = aiblockdata[i++];
                blockposition5 = (BlockPos) list2.get(k);
                iblockdata1.updateIndirectNeighbourShapes(world, blockposition5, 2);
                world.updateNeighborsAt(blockposition5, iblockdata1.getBlock());
            }

            for (k = list.size() - 1; k >= 0; --k) {
                world.updateNeighborsAt((BlockPos) list.get(k), aiblockdata[i++].getBlock());
            }

            if (retract) {
                world.updateNeighborsAt(blockposition1, Blocks.PISTON_HEAD);
            }

            return true;
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(PistonBaseBlock.FACING, rotation.rotate((Direction) state.getValue(PistonBaseBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(PistonBaseBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PistonBaseBlock.FACING, PistonBaseBlock.EXTENDED);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return (Boolean) state.getValue(PistonBaseBlock.EXTENDED);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }
}
