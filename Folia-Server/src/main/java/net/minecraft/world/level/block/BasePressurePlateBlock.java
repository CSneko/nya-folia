package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public abstract class BasePressurePlateBlock extends Block {

    protected static final VoxelShape PRESSED_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 0.5D, 15.0D);
    protected static final VoxelShape AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 1.0D, 15.0D);
    protected static final AABB TOUCH_AABB = new AABB(0.0625D, 0.0D, 0.0625D, 0.9375D, 0.25D, 0.9375D);
    private final BlockSetType type;

    protected BasePressurePlateBlock(BlockBehaviour.Properties settings, BlockSetType blockSetType) {
        super(settings.sound(blockSetType.soundType()));
        this.type = blockSetType;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.getSignalForState(state) > 0 ? BasePressurePlateBlock.PRESSED_AABB : BasePressurePlateBlock.AABB;
    }

    protected int getPressedTime() {
        return 20;
    }

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockPos blockposition1 = pos.below();

        return canSupportRigidBlock(world, blockposition1) || canSupportCenter(world, blockposition1, Direction.UP);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        int i = this.getSignalForState(state);

        if (i > 0) {
            this.checkPressed((Entity) null, world, pos, state, i);
        }

    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!world.isClientSide) {
            int i = this.getSignalForState(state);

            if (i == 0) {
                this.checkPressed(entity, world, pos, state, i);
            }

        }
    }

    private void checkPressed(@Nullable Entity entity, Level world, BlockPos pos, BlockState state, int output) {
        int j = this.getSignalStrength(world, pos);
        boolean flag = output > 0;
        boolean flag1 = j > 0;

        // CraftBukkit start - Interact Pressure Plate
        org.bukkit.World bworld = world.getWorld();
        org.bukkit.plugin.PluginManager manager = world.getCraftServer().getPluginManager();

        if (flag != flag1) {
            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bworld.getBlockAt(pos.getX(), pos.getY(), pos.getZ()), output, j);
            manager.callEvent(eventRedstone);

            flag1 = eventRedstone.getNewCurrent() > 0;
            j = eventRedstone.getNewCurrent();
        }
        // CraftBukkit end

        if (output != j) {
            BlockState iblockdata1 = this.setSignalForState(state, j);

            world.setBlock(pos, iblockdata1, 2);
            this.updateNeighbours(world, pos);
            world.setBlocksDirty(pos, state, iblockdata1);
        }

        if (!flag1 && flag) {
            world.playSound((Player) null, pos, this.type.pressurePlateClickOff(), SoundSource.BLOCKS);
            world.gameEvent(entity, GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (flag1 && !flag) {
            world.playSound((Player) null, pos, this.type.pressurePlateClickOn(), SoundSource.BLOCKS);
            world.gameEvent(entity, GameEvent.BLOCK_ACTIVATE, pos);
        }

        if (flag1) {
            world.scheduleTick(new BlockPos(pos), (Block) this, this.getPressedTime());
        }

    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved && !state.is(newState.getBlock())) {
            if (this.getSignalForState(state) > 0) {
                this.updateNeighbours(world, pos);
            }

            super.onRemove(state, world, pos, newState, moved);
        }
    }

    protected void updateNeighbours(Level world, BlockPos pos) {
        world.updateNeighborsAt(pos, this);
        world.updateNeighborsAt(pos.below(), this);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return this.getSignalForState(state);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? this.getSignalForState(state) : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    protected static int getEntityCount(Level world, AABB box, Class<? extends Entity> entityClass) {
        // CraftBukkit start
        return BasePressurePlateBlock.getEntities(world, box, entityClass).size();
    }

    protected static <T extends Entity> java.util.List<T> getEntities(Level world, AABB axisalignedbb, Class<T> oclass) {
        // CraftBukkit end
        return world.getEntitiesOfClass(oclass, axisalignedbb, EntitySelector.NO_SPECTATORS.and((entity) -> {
            return !entity.isIgnoringBlockTriggers();
        })); // CraftBukkit
    }

    protected abstract int getSignalStrength(Level world, BlockPos pos);

    protected abstract int getSignalForState(BlockState state);

    protected abstract BlockState setSignalForState(BlockState state, int rsOut);
}
