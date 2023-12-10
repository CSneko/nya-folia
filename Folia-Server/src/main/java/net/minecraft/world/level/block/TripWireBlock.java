package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit

public class TripWireBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    public static final BooleanProperty DISARMED = BlockStateProperties.DISARMED;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = CrossCollisionBlock.PROPERTY_BY_DIRECTION;
    protected static final VoxelShape AABB = Block.box(0.0D, 1.0D, 0.0D, 16.0D, 2.5D, 16.0D);
    protected static final VoxelShape NOT_ATTACHED_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    private static final int RECHECK_PERIOD = 10;
    private final TripWireHookBlock hook;

    public TripWireBlock(TripWireHookBlock hookBlock, BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(TripWireBlock.POWERED, false)).setValue(TripWireBlock.ATTACHED, false)).setValue(TripWireBlock.DISARMED, false)).setValue(TripWireBlock.NORTH, false)).setValue(TripWireBlock.EAST, false)).setValue(TripWireBlock.SOUTH, false)).setValue(TripWireBlock.WEST, false));
        this.hook = hookBlock;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(TripWireBlock.ATTACHED) ? TripWireBlock.AABB : TripWireBlock.NOT_ATTACHED_AABB;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return this.defaultBlockState(); // Paper - place tripwire without updating
        Level world = ctx.getLevel();
        BlockPos blockposition = ctx.getClickedPos();

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(TripWireBlock.NORTH, this.shouldConnectTo(world.getBlockState(blockposition.north()), Direction.NORTH))).setValue(TripWireBlock.EAST, this.shouldConnectTo(world.getBlockState(blockposition.east()), Direction.EAST))).setValue(TripWireBlock.SOUTH, this.shouldConnectTo(world.getBlockState(blockposition.south()), Direction.SOUTH))).setValue(TripWireBlock.WEST, this.shouldConnectTo(world.getBlockState(blockposition.west()), Direction.WEST));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return state; // Paper - prevent tripwire from updating
        return direction.getAxis().isHorizontal() ? (BlockState) state.setValue((Property) TripWireBlock.PROPERTY_BY_DIRECTION.get(direction), this.shouldConnectTo(neighborState, direction)) : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent adjacent tripwires from updating
        if (!oldState.is(state.getBlock())) {
            this.updateSource(world, pos, state);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent adjacent tripwires from updating
        if (!moved && !state.is(newState.getBlock())) {
            this.updateSource(world, pos, (BlockState) state.setValue(TripWireBlock.POWERED, true), true); // Paper - fix state inconsistency
        }
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent disarming tripwires
        if (!world.isClientSide && !player.getMainHandItem().isEmpty() && player.getMainHandItem().is(Items.SHEARS)) {
            world.setBlock(pos, (BlockState) state.setValue(TripWireBlock.DISARMED, true), 4);
            world.gameEvent((Entity) player, GameEvent.SHEAR, pos);
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    private void updateSource(Level world, BlockPos pos, BlockState state) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent adjacent tripwires from updating
        // Paper start - fix state inconsistency
        this.updateSource(world, pos, state, false);
    }

    private void updateSource(Level world, BlockPos pos, BlockState state, boolean beingRemoved) {
        // Paper end
        Direction[] aenumdirection = new Direction[]{Direction.SOUTH, Direction.WEST};
        int i = aenumdirection.length;
        int j = 0;

        while (j < i) {
            Direction enumdirection = aenumdirection[j];
            int k = 1;

            while (true) {
                if (k < 42) {
                    BlockPos blockposition1 = pos.relative(enumdirection, k);
                    BlockState iblockdata1 = world.getBlockState(blockposition1);

                    if (iblockdata1.is((Block) this.hook)) {
                        if (iblockdata1.getValue(TripWireHookBlock.FACING) == enumdirection.getOpposite()) {
                            this.hook.calculateState(world, blockposition1, iblockdata1, false, true, k, state, beingRemoved); // Paper - fix state inconsistency
                        }
                    } else if (iblockdata1.is((Block) this)) {
                        ++k;
                        continue;
                    }
                }

                ++j;
                break;
            }
        }

    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent tripwires from detecting collision
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!world.isClientSide) {
            if (!(Boolean) state.getValue(TripWireBlock.POWERED)) {
                this.checkPressed(world, pos);
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableTripwireUpdates) return; // Paper - prevent tripwire pressed check
        if ((Boolean) world.getBlockState(pos).getValue(TripWireBlock.POWERED)) {
            this.checkPressed(world, pos);
        }
    }

    private void checkPressed(Level world, BlockPos pos) {
        BlockState iblockdata = world.getBlockState(pos);
        boolean flag = (Boolean) iblockdata.getValue(TripWireBlock.POWERED);
        boolean flag1 = false;
        List<? extends Entity> list = world.getEntities((Entity) null, iblockdata.getShape(world, pos).bounds().move(pos));

        if (!list.isEmpty()) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (!entity.isIgnoringBlockTriggers()) {
                    flag1 = true;
                    break;
                }
            }
        }

        // CraftBukkit start - Call interact even when triggering connected tripwire
        if (flag != flag1 && flag1 && (Boolean)iblockdata.getValue(TripWireBlock.ATTACHED)) {
            org.bukkit.World bworld = world.getWorld();
            org.bukkit.plugin.PluginManager manager = world.getCraftServer().getPluginManager();
            org.bukkit.block.Block block = bworld.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            boolean allowed = false;

            // If all of the events are cancelled block the tripwire trigger, else allow
            for (Object object : list) {
                if (object != null) {
                    org.bukkit.event.Cancellable cancellable;

                    if (object instanceof Player) {
                        cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((Player) object, org.bukkit.event.block.Action.PHYSICAL, pos, null, null, null);
                    } else if (object instanceof Entity) {
                        cancellable = new EntityInteractEvent(((Entity) object).getBukkitEntity(), block);
                        manager.callEvent((EntityInteractEvent) cancellable);
                    } else {
                        continue;
                    }

                    if (!cancellable.isCancelled()) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) {
                return;
            }
        }
        // CraftBukkit end

        if (flag1 != flag) {
            iblockdata = (BlockState) iblockdata.setValue(TripWireBlock.POWERED, flag1);
            world.setBlock(pos, iblockdata, 3);
            this.updateSource(world, pos, iblockdata);
        }

        if (flag1) {
            world.scheduleTick(new BlockPos(pos), (Block) this, 10);
        }

    }

    public boolean shouldConnectTo(BlockState state, Direction facing) {
        return state.is((Block) this.hook) ? state.getValue(TripWireHookBlock.FACING) == facing.getOpposite() : state.is((Block) this);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.NORTH))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.EAST))).setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.NORTH))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.EAST))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TripWireBlock.POWERED, TripWireBlock.ATTACHED, TripWireBlock.DISARMED, TripWireBlock.NORTH, TripWireBlock.EAST, TripWireBlock.WEST, TripWireBlock.SOUTH);
    }
}
