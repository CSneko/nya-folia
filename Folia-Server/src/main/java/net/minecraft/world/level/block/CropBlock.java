package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class CropBlock extends BushBlock implements BonemealableBlock {

    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D), Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)};

    protected CropBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(this.getAgeProperty(), 0));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CropBlock.SHAPE_BY_AGE[this.getAge(state)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(Blocks.FARMLAND);
    }

    protected IntegerProperty getAgeProperty() {
        return CropBlock.AGE;
    }

    public int getMaxAge() {
        return 7;
    }

    public int getAge(BlockState state) {
        return (Integer) state.getValue(this.getAgeProperty());
    }

    public BlockState getStateForAge(int age) {
        return (BlockState) this.defaultBlockState().setValue(this.getAgeProperty(), age);
    }

    public final boolean isMaxAge(BlockState iblockdata) {
        return this.getAge(iblockdata) >= this.getMaxAge();
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return !this.isMaxAge(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getRawBrightness(pos, 0) >= 9) {
            int i = this.getAge(state);

            if (i < this.getMaxAge()) {
                float f = CropBlock.getGrowthSpeed(this, world, pos);

                // Spigot start
                int modifier;
                if (this == Blocks.BEETROOTS) {
                    modifier = world.spigotConfig.beetrootModifier;
                } else if (this == Blocks.CARROTS) {
                    modifier = world.spigotConfig.carrotModifier;
                } else if (this == Blocks.POTATOES) {
                    modifier = world.spigotConfig.potatoModifier;
                // Paper start
                } else if (this == Blocks.TORCHFLOWER_CROP) {
                    modifier = world.spigotConfig.torchFlowerModifier;
                // Paper end
                } else {
                    modifier = world.spigotConfig.wheatModifier;
                }

                if (random.nextFloat() < (modifier / (100.0f * (Math.floor((25.0F / f) + 1))))) { // Spigot - SPIGOT-7159: Better modifier resolution
                    // Spigot end
                    CraftEventFactory.handleBlockGrowEvent(world, pos, this.getStateForAge(i + 1), 2); // CraftBukkit
                }
            }
        }

    }

    public void growCrops(Level world, BlockPos pos, BlockState state) {
        int i = this.getAge(state) + this.getBonemealAgeIncrease(world);
        int j = this.getMaxAge();

        if (i > j) {
            i = j;
        }

        CraftEventFactory.handleBlockGrowEvent(world, pos, this.getStateForAge(i), 2); // CraftBukkit
    }

    protected int getBonemealAgeIncrease(Level world) {
        return Mth.nextInt(world.random, 2, 5);
    }

    protected static float getGrowthSpeed(Block block, BlockGetter world, BlockPos pos) {
        float f = 1.0F;
        BlockPos blockposition1 = pos.below();

        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                float f1 = 0.0F;
                BlockState iblockdata = world.getBlockState(blockposition1.offset(i, 0, j));

                if (iblockdata.is(Blocks.FARMLAND)) {
                    f1 = 1.0F;
                    if ((Integer) iblockdata.getValue(FarmBlock.MOISTURE) > 0) {
                        f1 = 3.0F;
                    }
                }

                if (i != 0 || j != 0) {
                    f1 /= 4.0F;
                }

                f += f1;
            }
        }

        BlockPos blockposition2 = pos.north();
        BlockPos blockposition3 = pos.south();
        BlockPos blockposition4 = pos.west();
        BlockPos blockposition5 = pos.east();
        boolean flag = world.getBlockState(blockposition4).is(block) || world.getBlockState(blockposition5).is(block);
        boolean flag1 = world.getBlockState(blockposition2).is(block) || world.getBlockState(blockposition3).is(block);

        if (flag && flag1) {
            f /= 2.0F;
        } else {
            boolean flag2 = world.getBlockState(blockposition4.north()).is(block) || world.getBlockState(blockposition5.north()).is(block) || world.getBlockState(blockposition5.south()).is(block) || world.getBlockState(blockposition4.south()).is(block);

            if (flag2) {
                f /= 2.0F;
            }
        }

        return f;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return CropBlock.hasSufficientLight(world, pos) && super.canSurvive(state, world, pos);
    }

    protected static boolean hasSufficientLight(LevelReader world, BlockPos pos) {
        return world.getRawBrightness(pos, 0) >= 8;
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (entity instanceof Ravager && CraftEventFactory.callEntityChangeBlockEvent(entity, pos, Blocks.AIR.defaultBlockState(), !world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) { // CraftBukkit
            world.destroyBlock(pos, true, entity);
        }

        super.entityInside(state, world, pos, entity);
    }

    protected ItemLike getBaseSeedId() {
        return Items.WHEAT_SEEDS;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter world, BlockPos pos, BlockState state) {
        return new ItemStack(this.getBaseSeedId());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return !this.isMaxAge(state);
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        this.growCrops(world, pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CropBlock.AGE);
    }
}
