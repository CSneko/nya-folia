package net.minecraft.world.level.block;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ComparatorBlock extends DiodeBlock implements EntityBlock {

    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    public ComparatorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ComparatorBlock.FACING, Direction.NORTH)).setValue(ComparatorBlock.POWERED, false)).setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE));
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !this.canSurviveOn(world, neighborPos, neighborState) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected int getOutputSignal(BlockGetter world, BlockPos pos, BlockState state) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        return tileentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity) tileentity).getOutputSignal() : 0;
    }

    private int calculateOutputSignal(Level world, BlockPos pos, BlockState state) {
        int i = this.getInputSignal(world, pos, state);

        if (i == 0) {
            return 0;
        } else {
            int j = this.getAlternateSignal(world, pos, state);

            return j > i ? 0 : (state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? i - j : i);
        }
    }

    @Override
    protected boolean shouldTurnOn(Level world, BlockPos pos, BlockState state) {
        int i = this.getInputSignal(world, pos, state);

        if (i == 0) {
            return false;
        } else {
            int j = this.getAlternateSignal(world, pos, state);

            return i > j ? true : i == j && state.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE;
        }
    }

    @Override
    protected int getInputSignal(Level world, BlockPos pos, BlockState state) {
        int i = super.getInputSignal(world, pos, state);
        Direction enumdirection = (Direction) state.getValue(ComparatorBlock.FACING);
        BlockPos blockposition1 = pos.relative(enumdirection);
        BlockState iblockdata1 = world.getBlockState(blockposition1);

        if (iblockdata1.hasAnalogOutputSignal()) {
            i = iblockdata1.getAnalogOutputSignal(world, blockposition1);
        } else if (i < 15 && iblockdata1.isRedstoneConductor(world, blockposition1)) {
            blockposition1 = blockposition1.relative(enumdirection);
            iblockdata1 = world.getBlockState(blockposition1);
            ItemFrame entityitemframe = this.getItemFrame(world, enumdirection, blockposition1);
            int j = Math.max(entityitemframe == null ? Integer.MIN_VALUE : entityitemframe.getAnalogOutput(), iblockdata1.hasAnalogOutputSignal() ? iblockdata1.getAnalogOutputSignal(world, blockposition1) : Integer.MIN_VALUE);

            if (j != Integer.MIN_VALUE) {
                i = j;
            }
        }

        return i;
    }

    @Nullable
    private ItemFrame getItemFrame(Level world, Direction facing, BlockPos pos) {
        // CraftBukkit - decompile error
        List<ItemFrame> list = world.getEntitiesOfClass(ItemFrame.class, new AABB((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 1), (double) (pos.getZ() + 1)), (java.util.function.Predicate<ItemFrame>) (entityitemframe) -> {
            return entityitemframe != null && entityitemframe.getDirection() == facing;
        });

        return list.size() == 1 ? (ItemFrame) list.get(0) : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            state = (BlockState) state.cycle(ComparatorBlock.MODE);
            float f = state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;

            world.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, f);
            world.setBlock(pos, state, 2);
            this.refreshOutputState(world, pos, state);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    @Override
    protected void checkTickOnNeighbor(Level world, BlockPos pos, BlockState state) {
        if (!world.getBlockTicks().willTickThisTick(pos, this)) {
            int i = this.calculateOutputSignal(world, pos, state);
            BlockEntity tileentity = world.getBlockEntity(pos);
            int j = tileentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity) tileentity).getOutputSignal() : 0;

            if (i != j || (Boolean) state.getValue(ComparatorBlock.POWERED) != this.shouldTurnOn(world, pos, state)) {
                TickPriority ticklistpriority = this.shouldPrioritize(world, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;

                world.scheduleTick(pos, (Block) this, 2, ticklistpriority);
            }

        }
    }

    private void refreshOutputState(Level world, BlockPos pos, BlockState state) {
        int i = this.calculateOutputSignal(world, pos, state);
        BlockEntity tileentity = world.getBlockEntity(pos);
        int j = 0;

        if (tileentity instanceof ComparatorBlockEntity) {
            ComparatorBlockEntity tileentitycomparator = (ComparatorBlockEntity) tileentity;

            j = tileentitycomparator.getOutputSignal();
            tileentitycomparator.setOutputSignal(i);
        }

        if (j != i || state.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE) {
            boolean flag = this.shouldTurnOn(world, pos, state);
            boolean flag1 = (Boolean) state.getValue(ComparatorBlock.POWERED);

            if (flag1 && !flag) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(world, pos, 15, 0).getNewCurrent() != 0) {
                    return;
                }
                // CraftBukkit end
                world.setBlock(pos, (BlockState) state.setValue(ComparatorBlock.POWERED, false), 2);
            } else if (!flag1 && flag) {
                // CraftBukkit start
                if (CraftEventFactory.callRedstoneChange(world, pos, 0, 15).getNewCurrent() != 15) {
                    return;
                }
                // CraftBukkit end
                world.setBlock(pos, (BlockState) state.setValue(ComparatorBlock.POWERED, true), 2);
            }

            this.updateNeighborsInFront(world, pos, state);
        }

    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.refreshOutputState(world, pos, state);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        super.triggerEvent(state, world, pos, type, data);
        BlockEntity tileentity = world.getBlockEntity(pos);

        return tileentity != null && tileentity.triggerEvent(type, data);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComparatorBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ComparatorBlock.FACING, ComparatorBlock.MODE, ComparatorBlock.POWERED);
    }
}
