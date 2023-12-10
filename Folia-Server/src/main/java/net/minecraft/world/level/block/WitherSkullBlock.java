package net.minecraft.world.level.block;

import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;

// CraftBukkit start
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
// CraftBukkit end

public class WitherSkullBlock extends SkullBlock {

    @Nullable
    private static BlockPattern witherPatternFull;
    @Nullable
    private static BlockPattern witherPatternBase;

    protected WitherSkullBlock(BlockBehaviour.Properties settings) {
        super(SkullBlock.Types.WITHER_SKELETON, settings);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof SkullBlockEntity) {
            WitherSkullBlock.checkSpawn(world, pos, (SkullBlockEntity) tileentity);
        }

    }

    public static void checkSpawn(Level world, BlockPos pos, SkullBlockEntity blockEntity) {
        if (world.getCurrentWorldData().captureBlockStates) return; // CraftBukkit // Folia - region threading
        if (!world.isClientSide) {
            BlockState iblockdata = blockEntity.getBlockState();
            boolean flag = iblockdata.is(Blocks.WITHER_SKELETON_SKULL) || iblockdata.is(Blocks.WITHER_SKELETON_WALL_SKULL);

            if (flag && pos.getY() >= world.getMinBuildHeight() && world.getDifficulty() != Difficulty.PEACEFUL) {
                BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = WitherSkullBlock.getOrCreateWitherFull().find(world, pos);

                if (shapedetector_shapedetectorcollection != null) {
                    WitherBoss entitywither = (WitherBoss) EntityType.WITHER.create(world);

                    if (entitywither != null) {
                        // BlockPumpkinCarved.clearPatternBlocks(world, shapedetector_shapedetectorcollection); // CraftBukkit - move down
                        BlockPos blockposition1 = shapedetector_shapedetectorcollection.getBlock(1, 2, 0).getPos();

                        entitywither.moveTo((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.55D, (double) blockposition1.getZ() + 0.5D, shapedetector_shapedetectorcollection.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F, 0.0F);
                        entitywither.yBodyRot = shapedetector_shapedetectorcollection.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
                        entitywither.makeInvulnerable();
                        // CraftBukkit start
                        if (!world.addFreshEntity(entitywither, SpawnReason.BUILD_WITHER)) {
                            return;
                        }
                        CarvedPumpkinBlock.clearPatternBlocks(world, shapedetector_shapedetectorcollection); // CraftBukkit - from above
                        // CraftBukkit end
                        Iterator iterator = world.getEntitiesOfClass(ServerPlayer.class, entitywither.getBoundingBox().inflate(50.0D)).iterator();

                        while (iterator.hasNext()) {
                            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                            CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayer, (Entity) entitywither);
                        }

                        // world.addFreshEntity(entitywither); // CraftBukkit - moved up
                        CarvedPumpkinBlock.updatePatternBlocks(world, shapedetector_shapedetectorcollection);
                    }

                }
            }
        }
    }

    public static boolean canSpawnMob(Level world, BlockPos pos, ItemStack stack) {
        return stack.is(Items.WITHER_SKELETON_SKULL) && pos.getY() >= world.getMinBuildHeight() + 2 && world.getDifficulty() != Difficulty.PEACEFUL && !world.isClientSide ? WitherSkullBlock.getOrCreateWitherBase().find(world, pos) != null : false;
    }

    private static BlockPattern getOrCreateWitherFull() {
        if (WitherSkullBlock.witherPatternFull == null) {
            WitherSkullBlock.witherPatternFull = BlockPatternBuilder.start().aisle("^^^", "###", "~#~").where('#', (shapedetectorblock) -> {
                return shapedetectorblock.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS);
            }).where('^', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_SKULL).or(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_WALL_SKULL)))).where('~', (shapedetectorblock) -> {
                return shapedetectorblock.getState().isAir();
            }).build();
        }

        return WitherSkullBlock.witherPatternFull;
    }

    private static BlockPattern getOrCreateWitherBase() {
        if (WitherSkullBlock.witherPatternBase == null) {
            WitherSkullBlock.witherPatternBase = BlockPatternBuilder.start().aisle("   ", "###", "~#~").where('#', (shapedetectorblock) -> {
                return shapedetectorblock.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS);
            }).where('~', (shapedetectorblock) -> {
                return shapedetectorblock.getState().isAir();
            }).build();
        }

        return WitherSkullBlock.witherPatternBase;
    }
}
