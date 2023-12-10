package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// CraftBukkit start
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityInteractEvent;
// CraftBukkit end

public class ButtonBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int PRESSED_DEPTH = 1;
    private static final int UNPRESSED_DEPTH = 2;
    protected static final int HALF_AABB_HEIGHT = 2;
    protected static final int HALF_AABB_WIDTH = 3;
    protected static final VoxelShape CEILING_AABB_X = Block.box(6.0D, 14.0D, 5.0D, 10.0D, 16.0D, 11.0D);
    protected static final VoxelShape CEILING_AABB_Z = Block.box(5.0D, 14.0D, 6.0D, 11.0D, 16.0D, 10.0D);
    protected static final VoxelShape FLOOR_AABB_X = Block.box(6.0D, 0.0D, 5.0D, 10.0D, 2.0D, 11.0D);
    protected static final VoxelShape FLOOR_AABB_Z = Block.box(5.0D, 0.0D, 6.0D, 11.0D, 2.0D, 10.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(5.0D, 6.0D, 14.0D, 11.0D, 10.0D, 16.0D);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0D, 6.0D, 0.0D, 11.0D, 10.0D, 2.0D);
    protected static final VoxelShape WEST_AABB = Block.box(14.0D, 6.0D, 5.0D, 16.0D, 10.0D, 11.0D);
    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 6.0D, 5.0D, 2.0D, 10.0D, 11.0D);
    protected static final VoxelShape PRESSED_CEILING_AABB_X = Block.box(6.0D, 15.0D, 5.0D, 10.0D, 16.0D, 11.0D);
    protected static final VoxelShape PRESSED_CEILING_AABB_Z = Block.box(5.0D, 15.0D, 6.0D, 11.0D, 16.0D, 10.0D);
    protected static final VoxelShape PRESSED_FLOOR_AABB_X = Block.box(6.0D, 0.0D, 5.0D, 10.0D, 1.0D, 11.0D);
    protected static final VoxelShape PRESSED_FLOOR_AABB_Z = Block.box(5.0D, 0.0D, 6.0D, 11.0D, 1.0D, 10.0D);
    protected static final VoxelShape PRESSED_NORTH_AABB = Block.box(5.0D, 6.0D, 15.0D, 11.0D, 10.0D, 16.0D);
    protected static final VoxelShape PRESSED_SOUTH_AABB = Block.box(5.0D, 6.0D, 0.0D, 11.0D, 10.0D, 1.0D);
    protected static final VoxelShape PRESSED_WEST_AABB = Block.box(15.0D, 6.0D, 5.0D, 16.0D, 10.0D, 11.0D);
    protected static final VoxelShape PRESSED_EAST_AABB = Block.box(0.0D, 6.0D, 5.0D, 1.0D, 10.0D, 11.0D);
    private final BlockSetType type;
    private final int ticksToStayPressed;
    private final boolean arrowsCanPress;

    protected ButtonBlock(BlockBehaviour.Properties settings, BlockSetType blockSetType, int pressTicks, boolean wooden) {
        super(settings.sound(blockSetType.soundType()));
        this.type = blockSetType;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ButtonBlock.FACING, Direction.NORTH)).setValue(ButtonBlock.POWERED, false)).setValue(ButtonBlock.FACE, AttachFace.WALL));
        this.ticksToStayPressed = pressTicks;
        this.arrowsCanPress = wooden;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction enumdirection = (Direction) state.getValue(ButtonBlock.FACING);
        boolean flag = (Boolean) state.getValue(ButtonBlock.POWERED);

        switch ((AttachFace) state.getValue(ButtonBlock.FACE)) {
            case FLOOR:
                if (enumdirection.getAxis() == Direction.Axis.X) {
                    return flag ? ButtonBlock.PRESSED_FLOOR_AABB_X : ButtonBlock.FLOOR_AABB_X;
                }

                return flag ? ButtonBlock.PRESSED_FLOOR_AABB_Z : ButtonBlock.FLOOR_AABB_Z;
            case WALL:
                VoxelShape voxelshape;

                switch (enumdirection) {
                    case EAST:
                        voxelshape = flag ? ButtonBlock.PRESSED_EAST_AABB : ButtonBlock.EAST_AABB;
                        break;
                    case WEST:
                        voxelshape = flag ? ButtonBlock.PRESSED_WEST_AABB : ButtonBlock.WEST_AABB;
                        break;
                    case SOUTH:
                        voxelshape = flag ? ButtonBlock.PRESSED_SOUTH_AABB : ButtonBlock.SOUTH_AABB;
                        break;
                    case NORTH:
                    case UP:
                    case DOWN:
                        voxelshape = flag ? ButtonBlock.PRESSED_NORTH_AABB : ButtonBlock.NORTH_AABB;
                        break;
                    default:
                        throw new IncompatibleClassChangeError();
                }

                return voxelshape;
            case CEILING:
            default:
                return enumdirection.getAxis() == Direction.Axis.X ? (flag ? ButtonBlock.PRESSED_CEILING_AABB_X : ButtonBlock.CEILING_AABB_X) : (flag ? ButtonBlock.PRESSED_CEILING_AABB_Z : ButtonBlock.CEILING_AABB_Z);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if ((Boolean) state.getValue(ButtonBlock.POWERED)) {
            return InteractionResult.CONSUME;
        } else {
            // CraftBukkit start
            boolean powered = ((Boolean) state.getValue(ButtonBlock.POWERED));
            org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getCraftServer().getPluginManager().callEvent(eventRedstone);

            if ((eventRedstone.getNewCurrent() > 0) != (!powered)) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end
            this.press(state, world, pos);
            this.playSound(player, world, pos, true);
            world.gameEvent((Entity) player, GameEvent.BLOCK_ACTIVATE, pos);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    public void press(BlockState state, Level world, BlockPos pos) {
        world.setBlock(pos, (BlockState) state.setValue(ButtonBlock.POWERED, true), 3);
        this.updateNeighbours(state, world, pos);
        world.scheduleTick(pos, (Block) this, this.ticksToStayPressed);
    }

    protected void playSound(@Nullable Player player, LevelAccessor world, BlockPos pos, boolean powered) {
        world.playSound(powered ? player : null, pos, this.getSound(powered), SoundSource.BLOCKS);
    }

    protected SoundEvent getSound(boolean powered) {
        return powered ? this.type.buttonClickOn() : this.type.buttonClickOff();
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock())) {
            if ((Boolean) state.getValue(ButtonBlock.POWERED)) {
                this.updateNeighbours(state, world, pos);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(ButtonBlock.POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(ButtonBlock.POWERED) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(ButtonBlock.POWERED)) {
            this.checkPressed(state, world, pos);
        }
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!world.isClientSide && this.arrowsCanPress && !(Boolean) state.getValue(ButtonBlock.POWERED)) {
            this.checkPressed(state, world, pos);
        }
    }

    protected void checkPressed(BlockState state, Level world, BlockPos pos) {
        AbstractArrow entityarrow = this.arrowsCanPress ? (AbstractArrow) world.getEntitiesOfClass(AbstractArrow.class, state.getShape(world, pos).bounds().move(pos)).stream().findFirst().orElse(null) : null; // CraftBukkit - decompile error
        boolean flag = entityarrow != null;
        boolean flag1 = (Boolean) state.getValue(ButtonBlock.POWERED);

        // CraftBukkit start - Call interact event when arrows turn on wooden buttons
        if (flag1 != flag && flag) {
            org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            EntityInteractEvent event = new EntityInteractEvent(entityarrow.getBukkitEntity(), block);
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end

        if (flag != flag1) {
            // CraftBukkit start
            boolean powered = flag1;
            org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getCraftServer().getPluginManager().callEvent(eventRedstone);

            if ((flag && eventRedstone.getNewCurrent() <= 0) || (!flag && eventRedstone.getNewCurrent() > 0)) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.setValue(ButtonBlock.POWERED, flag), 3);
            this.updateNeighbours(state, world, pos);
            this.playSound((Player) null, world, pos, flag);
            world.gameEvent((Entity) entityarrow, flag ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        }

        if (flag) {
            world.scheduleTick(new BlockPos(pos), (Block) this, this.ticksToStayPressed);
        }

    }

    private void updateNeighbours(BlockState state, Level world, BlockPos pos) {
        world.updateNeighborsAt(pos, this);
        world.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ButtonBlock.FACING, ButtonBlock.POWERED, ButtonBlock.FACE);
    }
}
