package net.minecraft.world.level.block;

import java.util.Collection;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class SculkVeinBlock extends MultifaceBlock implements SculkBehaviour, SimpleWaterloggedBlock {

    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final MultifaceSpreader veinSpreader;
    private final MultifaceSpreader sameSpaceSpreader;

    public SculkVeinBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.veinSpreader = new MultifaceSpreader(new SculkVeinBlock.SculkVeinSpreaderConfig(MultifaceSpreader.DEFAULT_SPREAD_ORDER));
        this.sameSpaceSpreader = new MultifaceSpreader(new SculkVeinBlock.SculkVeinSpreaderConfig(new MultifaceSpreader.SpreadType[]{MultifaceSpreader.SpreadType.SAME_POSITION}));
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(SculkVeinBlock.WATERLOGGED, false));
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.veinSpreader;
    }

    public MultifaceSpreader getSameSpaceSpreader() {
        return this.sameSpaceSpreader;
    }

    public static boolean regrow(LevelAccessor world, BlockPos pos, BlockState state, Collection<Direction> directions) {
        boolean flag = false;
        BlockState iblockdata1 = Blocks.SCULK_VEIN.defaultBlockState();
        Iterator iterator = directions.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection);

            if (canAttachTo(world, enumdirection, blockposition1, world.getBlockState(blockposition1))) {
                iblockdata1 = (BlockState) iblockdata1.setValue(getFaceProperty(enumdirection), true);
                flag = true;
            }
        }

        if (!flag) {
            return false;
        } else {
            if (!state.getFluidState().isEmpty()) {
                iblockdata1 = (BlockState) iblockdata1.setValue(SculkVeinBlock.WATERLOGGED, true);
            }

            world.setBlock(pos, iblockdata1, 3);
            return true;
        }
    }

    @Override
    public void onDischarged(LevelAccessor world, BlockState state, BlockPos pos, RandomSource random) {
        if (state.is((Block) this)) {
            Direction[] aenumdirection = SculkVeinBlock.DIRECTIONS;
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];
                BooleanProperty blockstateboolean = getFaceProperty(enumdirection);

                if ((Boolean) state.getValue(blockstateboolean) && world.getBlockState(pos.relative(enumdirection)).is(Blocks.SCULK)) {
                    state = (BlockState) state.setValue(blockstateboolean, false);
                }
            }

            if (!hasAnyFace(state)) {
                FluidState fluid = world.getFluidState(pos);

                state = (fluid.isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState();
            }

            world.setBlock(pos, state, 3);
            SculkBehaviour.super.onDischarged(world, state, pos, random);
        }
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor world, BlockPos catalystPos, RandomSource random, SculkSpreader spreadManager, boolean shouldConvertToBlock) {
        // CraftBukkit - add source block
        return shouldConvertToBlock && this.attemptPlaceSculk(spreadManager, world, cursor.getPos(), random, catalystPos) ? cursor.getCharge() - 1 : (random.nextInt(spreadManager.chargeDecayRate()) == 0 ? Mth.floor((float) cursor.getCharge() * 0.5F) : cursor.getCharge());
    }

    private boolean attemptPlaceSculk(SculkSpreader sculkspreader, LevelAccessor generatoraccess, BlockPos blockposition, RandomSource randomsource, BlockPos sourceBlock) { // CraftBukkit
        BlockState iblockdata = generatoraccess.getBlockState(blockposition);
        TagKey<Block> tagkey = sculkspreader.replaceableBlocks();
        Iterator iterator = Direction.allShuffled(randomsource).iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();

            if (hasFace(iblockdata, enumdirection)) {
                BlockPos blockposition1 = blockposition.relative(enumdirection);
                BlockState iblockdata1 = generatoraccess.getBlockState(blockposition1);

                if (iblockdata1.is(tagkey)) {
                    BlockState iblockdata2 = Blocks.SCULK.defaultBlockState();

                    // CraftBukkit start - Call BlockSpreadEvent
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(generatoraccess, sourceBlock, blockposition1, iblockdata2, 3)) {
                        return false;
                    }
                    // CraftBukkit end
                    Block.pushEntitiesUp(iblockdata1, iblockdata2, generatoraccess, blockposition1);
                    generatoraccess.playSound((Player) null, blockposition1, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    this.veinSpreader.spreadAll(iblockdata2, generatoraccess, blockposition1, sculkspreader.isWorldGeneration());
                    Direction enumdirection1 = enumdirection.getOpposite();
                    Direction[] aenumdirection = SculkVeinBlock.DIRECTIONS;
                    int i = aenumdirection.length;

                    for (int j = 0; j < i; ++j) {
                        Direction enumdirection2 = aenumdirection[j];

                        if (enumdirection2 != enumdirection1) {
                            BlockPos blockposition2 = blockposition1.relative(enumdirection2);
                            BlockState iblockdata3 = generatoraccess.getBlockState(blockposition2);

                            if (iblockdata3.is((Block) this)) {
                                this.onDischarged(generatoraccess, iblockdata3, blockposition2, randomsource);
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasSubstrateAccess(LevelAccessor world, BlockState state, BlockPos pos) {
        if (!state.is(Blocks.SCULK_VEIN)) {
            return false;
        } else {
            Direction[] aenumdirection = SculkVeinBlock.DIRECTIONS;
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                if (hasFace(state, enumdirection) && world.getBlockState(pos.relative(enumdirection)).is(BlockTags.SCULK_REPLACEABLE)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if ((Boolean) state.getValue(SculkVeinBlock.WATERLOGGED)) {
            world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SculkVeinBlock.WATERLOGGED);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.getItemInHand().is(Items.SCULK_VEIN) || super.canBeReplaced(state, context);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SculkVeinBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private class SculkVeinSpreaderConfig extends MultifaceSpreader.DefaultSpreaderConfig {

        private final MultifaceSpreader.SpreadType[] spreadTypes;

        public SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType... amultifacespreader_e) {
            super(SculkVeinBlock.this);
            this.spreadTypes = amultifacespreader_e;
        }

        @Override
        public boolean stateCanBeReplaced(BlockGetter world, BlockPos pos, BlockPos growPos, Direction direction, BlockState state) {
            BlockState iblockdata1 = world.getBlockState(growPos.relative(direction));

            if (!iblockdata1.is(Blocks.SCULK) && !iblockdata1.is(Blocks.SCULK_CATALYST) && !iblockdata1.is(Blocks.MOVING_PISTON)) {
                if (pos.distManhattan(growPos) == 2) {
                    BlockPos blockposition2 = pos.relative(direction.getOpposite());

                    if (world.getBlockState(blockposition2).isFaceSturdy(world, blockposition2, direction)) {
                        return false;
                    }
                }

                FluidState fluid = state.getFluidState();

                return !fluid.isEmpty() && !fluid.is((Fluid) Fluids.WATER) ? false : (state.is(BlockTags.FIRE) ? false : state.canBeReplaced() || super.stateCanBeReplaced(world, pos, growPos, direction, state));
            } else {
                return false;
            }
        }

        @Override
        public MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return this.spreadTypes;
        }

        @Override
        public boolean isOtherBlockValidAsSource(BlockState state) {
            return !state.is(Blocks.SCULK_VEIN);
        }
    }
}
