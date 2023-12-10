package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
// CraftBukkit start
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    @Nullable
    private BlockPattern snowGolemBase;
    @Nullable
    private BlockPattern snowGolemFull;
    @Nullable
    private BlockPattern ironGolemBase;
    @Nullable
    private BlockPattern ironGolemFull;
    private static final Predicate<BlockState> PUMPKINS_PREDICATE = (iblockdata) -> {
        return iblockdata != null && (iblockdata.is(Blocks.CARVED_PUMPKIN) || iblockdata.is(Blocks.JACK_O_LANTERN));
    };

    protected CarvedPumpkinBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CarvedPumpkinBlock.FACING, Direction.NORTH));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            this.trySpawnGolem(world, pos);
        }
    }

    public boolean canSpawnGolem(LevelReader world, BlockPos pos) {
        return this.getOrCreateSnowGolemBase().find(world, pos) != null || this.getOrCreateIronGolemBase().find(world, pos) != null;
    }

    private void trySpawnGolem(Level world, BlockPos pos) {
        BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = this.getOrCreateSnowGolemFull().find(world, pos);

        if (shapedetector_shapedetectorcollection != null) {
            SnowGolem entitysnowman = (SnowGolem) EntityType.SNOW_GOLEM.create(world);

            if (entitysnowman != null) {
                CarvedPumpkinBlock.spawnGolemInWorld(world, shapedetector_shapedetectorcollection, entitysnowman, shapedetector_shapedetectorcollection.getBlock(0, 2, 0).getPos());
            }
        } else {
            BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection1 = this.getOrCreateIronGolemFull().find(world, pos);

            if (shapedetector_shapedetectorcollection1 != null) {
                IronGolem entityirongolem = (IronGolem) EntityType.IRON_GOLEM.create(world);

                if (entityirongolem != null) {
                    entityirongolem.setPlayerCreated(true);
                    CarvedPumpkinBlock.spawnGolemInWorld(world, shapedetector_shapedetectorcollection1, entityirongolem, shapedetector_shapedetectorcollection1.getBlock(1, 2, 0).getPos());
                }
            }
        }

    }

    private static void spawnGolemInWorld(Level world, BlockPattern.BlockPatternMatch patternResult, Entity entity, BlockPos pos) {
        // clearPatternBlocks(world, shapedetector_shapedetectorcollection); // CraftBukkit - moved down
        entity.moveTo((double) pos.getX() + 0.5D, (double) pos.getY() + 0.05D, (double) pos.getZ() + 0.5D, 0.0F, 0.0F);
        // CraftBukkit start
        // Paper start - correct spawn reason
        final SpawnReason spawnReason;
        if (entity.getType() == EntityType.SNOW_GOLEM) {
            spawnReason = SpawnReason.BUILD_SNOWMAN;
        } else if (entity.getType() == EntityType.IRON_GOLEM) {
            spawnReason = SpawnReason.BUILD_IRONGOLEM;
        } else {
            spawnReason = SpawnReason.DEFAULT;
        }
        if (!world.addFreshEntity(entity, spawnReason)) {
        // Paper end
            return;
        }
        CarvedPumpkinBlock.clearPatternBlocks(world, patternResult); // CraftBukkit - from above
        // CraftBukkit end
        Iterator iterator = world.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(5.0D)).iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayer, entity);
        }

        CarvedPumpkinBlock.updatePatternBlocks(world, patternResult);
    }

    public static void clearPatternBlocks(Level world, BlockPattern.BlockPatternMatch patternResult) {
        for (int i = 0; i < patternResult.getWidth(); ++i) {
            for (int j = 0; j < patternResult.getHeight(); ++j) {
                BlockInWorld shapedetectorblock = patternResult.getBlock(i, j, 0);

                world.setBlock(shapedetectorblock.getPos(), Blocks.AIR.defaultBlockState(), 2);
                world.levelEvent(2001, shapedetectorblock.getPos(), Block.getId(shapedetectorblock.getState()));
            }
        }

    }

    public static void updatePatternBlocks(Level world, BlockPattern.BlockPatternMatch patternResult) {
        for (int i = 0; i < patternResult.getWidth(); ++i) {
            for (int j = 0; j < patternResult.getHeight(); ++j) {
                BlockInWorld shapedetectorblock = patternResult.getBlock(i, j, 0);

                world.blockUpdated(shapedetectorblock.getPos(), Blocks.AIR);
            }
        }

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CarvedPumpkinBlock.FACING);
    }

    private BlockPattern getOrCreateSnowGolemBase() {
        if (this.snowGolemBase == null) {
            this.snowGolemBase = BlockPatternBuilder.start().aisle(" ", "#", "#").where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK))).build();
        }

        return this.snowGolemBase;
    }

    private BlockPattern getOrCreateSnowGolemFull() {
        if (this.snowGolemFull == null) {
            this.snowGolemFull = BlockPatternBuilder.start().aisle("^", "#", "#").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.SNOW_BLOCK))).build();
        }

        return this.snowGolemFull;
    }

    private BlockPattern getOrCreateIronGolemBase() {
        if (this.ironGolemBase == null) {
            this.ironGolemBase = BlockPatternBuilder.start().aisle("~ ~", "###", "~#~").where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK))).where('~', (shapedetectorblock) -> {
                return shapedetectorblock.getState().isAir();
            }).build();
        }

        return this.ironGolemBase;
    }

    private BlockPattern getOrCreateIronGolemFull() {
        if (this.ironGolemFull == null) {
            this.ironGolemFull = BlockPatternBuilder.start().aisle("~^~", "###", "~#~").where('^', BlockInWorld.hasState(CarvedPumpkinBlock.PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.IRON_BLOCK))).where('~', (shapedetectorblock) -> {
                return shapedetectorblock.getState().isAir();
            }).build();
        }

        return this.ironGolemFull;
    }
}
