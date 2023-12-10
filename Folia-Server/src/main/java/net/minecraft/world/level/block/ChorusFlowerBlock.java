package net.minecraft.world.level.block;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class ChorusFlowerBlock extends Block {

    public static final int DEAD_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    protected static final VoxelShape BLOCK_SUPPORT_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);
    private final ChorusPlantBlock plant;

    protected ChorusFlowerBlock(ChorusPlantBlock plantBlock, BlockBehaviour.Properties settings) {
        super(settings);
        this.plant = plantBlock;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(ChorusFlowerBlock.AGE, 0));
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(world, pos)) {
            world.destroyBlock(pos, true);
        }

    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(ChorusFlowerBlock.AGE) < 5;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return ChorusFlowerBlock.BLOCK_SUPPORT_SHAPE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockPos blockposition1 = pos.above();

        if (world.isEmptyBlock(blockposition1) && blockposition1.getY() < world.getMaxBuildHeight()) {
            int i = (Integer) state.getValue(ChorusFlowerBlock.AGE);

            if (i < 5) {
                boolean flag = false;
                boolean flag1 = false;
                BlockState iblockdata1 = world.getBlockState(pos.below());
                int j;

                if (iblockdata1.is(Blocks.END_STONE)) {
                    flag = true;
                } else if (iblockdata1.is((Block) this.plant)) {
                    j = 1;

                    for (int k = 0; k < 4; ++k) {
                        BlockState iblockdata2 = world.getBlockState(pos.below(j + 1));

                        if (!iblockdata2.is((Block) this.plant)) {
                            if (iblockdata2.is(Blocks.END_STONE)) {
                                flag1 = true;
                            }
                            break;
                        }

                        ++j;
                    }

                    if (j < 2 || j <= random.nextInt(flag1 ? 5 : 4)) {
                        flag = true;
                    }
                } else if (iblockdata1.isAir()) {
                    flag = true;
                }

                if (flag && ChorusFlowerBlock.allNeighborsEmpty(world, blockposition1, (Direction) null) && world.isEmptyBlock(pos.above(2))) {
                    // CraftBukkit start - add event
                    if (CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition1, this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(i)), 2)) {
                        world.setBlock(pos, this.plant.getStateForPlacement(world, pos), 2);
                        this.placeGrownFlower(world, blockposition1, i);
                    }
                    // CraftBukkit end
                } else if (i < 4) {
                    j = random.nextInt(4);
                    if (flag1) {
                        ++j;
                    }

                    boolean flag2 = false;

                    for (int l = 0; l < j; ++l) {
                        Direction enumdirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos blockposition2 = pos.relative(enumdirection);

                        if (world.isEmptyBlock(blockposition2) && world.isEmptyBlock(blockposition2.below()) && ChorusFlowerBlock.allNeighborsEmpty(world, blockposition2, enumdirection.getOpposite())) {
                            // CraftBukkit start - add event
                            if (CraftEventFactory.handleBlockSpreadEvent(world, pos, blockposition2, this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(i + 1)), 2)) {
                                this.placeGrownFlower(world, blockposition2, i + 1);
                                flag2 = true;
                            }
                            // CraftBukkit end
                        }
                    }

                    if (flag2) {
                        world.setBlock(pos, this.plant.getStateForPlacement(world, pos), 2);
                    } else {
                        // CraftBukkit start - add event
                        if (CraftEventFactory.handleBlockGrowEvent(world, pos, this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(5)), 2)) {
                            this.placeDeadFlower(world, pos);
                        }
                        // CraftBukkit end
                    }
                } else {
                    // CraftBukkit start - add event
                    if (CraftEventFactory.handleBlockGrowEvent(world, pos, this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, Integer.valueOf(5)), 2)) {
                        this.placeDeadFlower(world, pos);
                    }
                    // CraftBukkit end
                }

            }
        }
    }

    private void placeGrownFlower(Level world, BlockPos pos, int age) {
        world.setBlock(pos, (BlockState) this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, age), 2);
        world.levelEvent(1033, pos, 0);
    }

    private void placeDeadFlower(Level world, BlockPos pos) {
        world.setBlock(pos, (BlockState) this.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5), 2);
        world.levelEvent(1034, pos, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader world, BlockPos pos, @Nullable Direction exceptDirection) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        Direction enumdirection1;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            enumdirection1 = (Direction) iterator.next();
        } while (enumdirection1 == exceptDirection || world.isEmptyBlock(pos.relative(enumdirection1)));

        return false;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction != Direction.UP && !state.canSurvive(world, pos)) {
            world.scheduleTick(pos, (Block) this, 1);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockState iblockdata1 = world.getBlockState(pos.below());

        if (!iblockdata1.is((Block) this.plant) && !iblockdata1.is(Blocks.END_STONE)) {
            if (!iblockdata1.isAir()) {
                return false;
            } else {
                boolean flag = false;
                Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                while (iterator.hasNext()) {
                    Direction enumdirection = (Direction) iterator.next();
                    BlockState iblockdata2 = world.getBlockState(pos.relative(enumdirection));

                    if (iblockdata2.is((Block) this.plant)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (!iblockdata2.isAir()) {
                        return false;
                    }
                }

                return flag;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ChorusFlowerBlock.AGE);
    }

    public static void generatePlant(LevelAccessor world, BlockPos pos, RandomSource random, int size) {
        world.setBlock(pos, ((ChorusPlantBlock) Blocks.CHORUS_PLANT).getStateForPlacement(world, pos), 2);
        ChorusFlowerBlock.growTreeRecursive(world, pos, random, pos, size, 0);
    }

    private static void growTreeRecursive(LevelAccessor world, BlockPos pos, RandomSource random, BlockPos rootPos, int size, int layer) {
        ChorusPlantBlock blockchorusfruit = (ChorusPlantBlock) Blocks.CHORUS_PLANT;
        int k = random.nextInt(4) + 1;

        if (layer == 0) {
            ++k;
        }

        for (int l = 0; l < k; ++l) {
            BlockPos blockposition2 = pos.above(l + 1);

            if (!ChorusFlowerBlock.allNeighborsEmpty(world, blockposition2, (Direction) null)) {
                return;
            }

            world.setBlock(blockposition2, blockchorusfruit.getStateForPlacement(world, blockposition2), 2);
            world.setBlock(blockposition2.below(), blockchorusfruit.getStateForPlacement(world, blockposition2.below()), 2);
        }

        boolean flag = false;

        if (layer < 4) {
            int i1 = random.nextInt(4);

            if (layer == 0) {
                ++i1;
            }

            for (int j1 = 0; j1 < i1; ++j1) {
                Direction enumdirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockposition3 = pos.above(k).relative(enumdirection);

                if (Math.abs(blockposition3.getX() - rootPos.getX()) < size && Math.abs(blockposition3.getZ() - rootPos.getZ()) < size && world.isEmptyBlock(blockposition3) && world.isEmptyBlock(blockposition3.below()) && ChorusFlowerBlock.allNeighborsEmpty(world, blockposition3, enumdirection.getOpposite())) {
                    flag = true;
                    world.setBlock(blockposition3, blockchorusfruit.getStateForPlacement(world, blockposition3), 2);
                    world.setBlock(blockposition3.relative(enumdirection.getOpposite()), blockchorusfruit.getStateForPlacement(world, blockposition3.relative(enumdirection.getOpposite())), 2);
                    ChorusFlowerBlock.growTreeRecursive(world, blockposition3, random, rootPos, size, layer + 1);
                }
            }
        }

        if (!flag) {
            world.setBlock(pos.above(k), (BlockState) Blocks.CHORUS_FLOWER.defaultBlockState().setValue(ChorusFlowerBlock.AGE, 5), 2);
        }

    }

    @Override
    public void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockposition = hit.getBlockPos();

        if (!world.isClientSide && projectile.mayInteract(world, blockposition) && projectile.getType().is(EntityTypeTags.IMPACT_PROJECTILES)) {
            // CraftBukkit
            if (!CraftEventFactory.callEntityChangeBlockEvent(projectile, blockposition, state.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                return;
            }
            // CraftBukkit end
            world.destroyBlock(blockposition, true, projectile);
        }

    }
}
