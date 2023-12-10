package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class BoneMealItem extends Item {

    public static final int GRASS_SPREAD_WIDTH = 3;
    public static final int GRASS_SPREAD_HEIGHT = 1;
    public static final int GRASS_COUNT_MULTIPLIER = 3;

    public BoneMealItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // CraftBukkit start - extract bonemeal application logic to separate, static method
        return BoneMealItem.applyBonemeal(context);
    }

    public static InteractionResult applyBonemeal(UseOnContext itemactioncontext) {
        // CraftBukkit end
        Level world = itemactioncontext.getLevel();
        BlockPos blockposition = itemactioncontext.getClickedPos();
        BlockPos blockposition1 = blockposition.relative(itemactioncontext.getClickedFace());

        if (BoneMealItem.growCrop(itemactioncontext.getItemInHand(), world, blockposition)) {
            if (!world.isClientSide) {
                itemactioncontext.getPlayer().gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                world.levelEvent(1505, blockposition, 0);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            BlockState iblockdata = world.getBlockState(blockposition);
            boolean flag = iblockdata.isFaceSturdy(world, blockposition, itemactioncontext.getClickedFace());

            if (flag && BoneMealItem.growWaterPlant(itemactioncontext.getItemInHand(), world, blockposition1, itemactioncontext.getClickedFace())) {
                if (!world.isClientSide) {
                    itemactioncontext.getPlayer().gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                    world.levelEvent(1505, blockposition1, 0);
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public static boolean growCrop(ItemStack stack, Level world, BlockPos pos) {
        BlockState iblockdata = world.getBlockState(pos);
        Block block = iblockdata.getBlock();

        if (block instanceof BonemealableBlock) {
            BonemealableBlock iblockfragileplantelement = (BonemealableBlock) block;

            if (iblockfragileplantelement.isValidBonemealTarget(world, pos, iblockdata)) {
                if (world instanceof ServerLevel) {
                    if (iblockfragileplantelement.isBonemealSuccess(world, world.random, pos, iblockdata)) {
                        iblockfragileplantelement.performBonemeal((ServerLevel) world, world.random, pos, iblockdata);
                    }

                    stack.shrink(1);
                }

                return true;
            }
        }

        return false;
    }

    public static boolean growWaterPlant(ItemStack stack, Level world, BlockPos blockPos, @Nullable Direction facing) {
        if (world.getBlockState(blockPos).is(Blocks.WATER) && world.getFluidState(blockPos).getAmount() == 8) {
            if (!(world instanceof ServerLevel)) {
                return true;
            } else {
                RandomSource randomsource = world.getRandom();
                int i = 0;

                while (i < 128) {
                    BlockPos blockposition1 = blockPos;
                    BlockState iblockdata = Blocks.SEAGRASS.defaultBlockState();
                    int j = 0;

                    while (true) {
                        if (j < i / 16) {
                            blockposition1 = blockposition1.offset(randomsource.nextInt(3) - 1, (randomsource.nextInt(3) - 1) * randomsource.nextInt(3) / 2, randomsource.nextInt(3) - 1);
                            if (!world.getBlockState(blockposition1).isCollisionShapeFullBlock(world, blockposition1)) {
                                ++j;
                                continue;
                            }
                        } else {
                            Holder<Biome> holder = world.getBiome(blockposition1);

                            if (holder.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
                                if (i == 0 && facing != null && facing.getAxis().isHorizontal()) {
                                    iblockdata = (BlockState) BuiltInRegistries.BLOCK.getTag(BlockTags.WALL_CORALS).flatMap((holderset_named) -> {
                                        return holderset_named.getRandomElement(world.random);
                                    }).map((holder1) -> {
                                        return ((Block) holder1.value()).defaultBlockState();
                                    }).orElse(iblockdata);
                                    if (iblockdata.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                        iblockdata = (BlockState) iblockdata.setValue(BaseCoralWallFanBlock.FACING, facing);
                                    }
                                } else if (randomsource.nextInt(4) == 0) {
                                    iblockdata = (BlockState) BuiltInRegistries.BLOCK.getTag(BlockTags.UNDERWATER_BONEMEALS).flatMap((holderset_named) -> {
                                        return holderset_named.getRandomElement(world.random);
                                    }).map((holder1) -> {
                                        return ((Block) holder1.value()).defaultBlockState();
                                    }).orElse(iblockdata);
                                }
                            }

                            if (iblockdata.is(BlockTags.WALL_CORALS, (blockbase_blockdata) -> {
                                return blockbase_blockdata.hasProperty(BaseCoralWallFanBlock.FACING);
                            })) {
                                for (int k = 0; !iblockdata.canSurvive(world, blockposition1) && k < 4; ++k) {
                                    iblockdata = (BlockState) iblockdata.setValue(BaseCoralWallFanBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(randomsource));
                                }
                            }

                            if (iblockdata.canSurvive(world, blockposition1)) {
                                BlockState iblockdata1 = world.getBlockState(blockposition1);

                                if (iblockdata1.is(Blocks.WATER) && world.getFluidState(blockposition1).getAmount() == 8) {
                                    world.setBlock(blockposition1, iblockdata, 3);
                                } else if (iblockdata1.is(Blocks.SEAGRASS) && randomsource.nextInt(10) == 0) {
                                    ((BonemealableBlock) Blocks.SEAGRASS).performBonemeal((ServerLevel) world, randomsource, blockposition1, iblockdata1);
                                }
                            }
                        }

                        ++i;
                        break;
                    }
                }

                stack.shrink(1);
                return true;
            }
        } else {
            return false;
        }
    }

    public static void addGrowthParticles(LevelAccessor world, BlockPos pos, int count) {
        if (count == 0) {
            count = 15;
        }

        BlockState iblockdata = world.getBlockState(pos);

        if (!iblockdata.isAir()) {
            double d0 = 0.5D;
            double d1;

            if (iblockdata.is(Blocks.WATER)) {
                count *= 3;
                d1 = 1.0D;
                d0 = 3.0D;
            } else if (iblockdata.isSolidRender(world, pos)) {
                pos = pos.above();
                count *= 3;
                d0 = 3.0D;
                d1 = 1.0D;
            } else {
                d1 = iblockdata.getShape(world, pos).max(Direction.Axis.Y);
            }

            world.addParticle(ParticleTypes.HAPPY_VILLAGER, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
            RandomSource randomsource = world.getRandom();

            for (int j = 0; j < count; ++j) {
                double d2 = randomsource.nextGaussian() * 0.02D;
                double d3 = randomsource.nextGaussian() * 0.02D;
                double d4 = randomsource.nextGaussian() * 0.02D;
                double d5 = 0.5D - d0;
                double d6 = (double) pos.getX() + d5 + randomsource.nextDouble() * d0 * 2.0D;
                double d7 = (double) pos.getY() + randomsource.nextDouble() * d1;
                double d8 = (double) pos.getZ() + d5 + randomsource.nextDouble() * d0 * 2.0D;

                if (!world.getBlockState(BlockPos.containing(d6, d7, d8).below()).isAir()) {
                    world.addParticle(ParticleTypes.HAPPY_VILLAGER, d6, d7, d8, d2, d3, d4);
                }
            }

        }
    }
}
