package net.minecraft.world.level.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DaylightDetectorBlock extends BaseEntityBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    public DaylightDetectorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(DaylightDetectorBlock.POWER, 0)).setValue(DaylightDetectorBlock.INVERTED, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return DaylightDetectorBlock.SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Integer) state.getValue(DaylightDetectorBlock.POWER);
    }

    private static void updateSignalStrength(BlockState state, Level world, BlockPos pos) {
        int i = world.getBrightness(LightLayer.SKY, pos) - world.getSkyDarken();
        float f = world.getSunAngle(1.0F);
        boolean flag = (Boolean) state.getValue(DaylightDetectorBlock.INVERTED);

        if (flag) {
            i = 15 - i;
        } else if (i > 0) {
            float f1 = f < 3.1415927F ? 0.0F : 6.2831855F;

            f += (f1 - f) * 0.2F;
            i = Math.round((float) i * Mth.cos(f));
        }

        i = Mth.clamp(i, 0, 15);
        if ((Integer) state.getValue(DaylightDetectorBlock.POWER) != i) {
            i = org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, pos, ((Integer) state.getValue(DaylightDetectorBlock.POWER)), i).getNewCurrent(); // CraftBukkit - Call BlockRedstoneEvent
            world.setBlock(pos, (BlockState) state.setValue(DaylightDetectorBlock.POWER, i), 3);
        }

    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.mayBuild()) {
            if (world.isClientSide) {
                return InteractionResult.SUCCESS;
            } else {
                BlockState iblockdata1 = (BlockState) state.cycle(DaylightDetectorBlock.INVERTED);

                world.setBlock(pos, iblockdata1, 2);
                world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, iblockdata1));
                DaylightDetectorBlock.updateSignalStrength(iblockdata1, world, pos);
                return InteractionResult.CONSUME;
            }
        } else {
            return super.use(state, world, pos, player, hand, hit);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DaylightDetectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return !world.isClientSide && world.dimensionType().hasSkyLight() ? createTickerHelper(type, BlockEntityType.DAYLIGHT_DETECTOR, DaylightDetectorBlock::tickEntity) : null;
    }

    private static void tickEntity(Level world, BlockPos pos, BlockState state, DaylightDetectorBlockEntity blockEntity) {
        if (world.getRedstoneGameTime() % 20L == 0L) { // Folia - region threading
            DaylightDetectorBlock.updateSignalStrength(state, world, pos);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DaylightDetectorBlock.POWER, DaylightDetectorBlock.INVERTED);
    }
}
