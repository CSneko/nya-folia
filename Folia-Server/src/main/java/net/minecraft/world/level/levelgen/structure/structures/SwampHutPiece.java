package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class SwampHutPiece extends ScatteredFeaturePiece {

    private boolean spawnedWitch;
    private boolean spawnedCat;

    public SwampHutPiece(RandomSource random, int x, int z) {
        super(StructurePieceType.SWAMPLAND_HUT, x, 64, z, 7, 7, 9, getRandomHorizontalDirection(random));
    }

    public SwampHutPiece(CompoundTag nbt) {
        super(StructurePieceType.SWAMPLAND_HUT, nbt);
        this.spawnedWitch = nbt.getBoolean("Witch");
        this.spawnedCat = nbt.getBoolean("Cat");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
        super.addAdditionalSaveData(context, nbt);
        nbt.putBoolean("Witch", this.spawnedWitch);
        nbt.putBoolean("Cat", this.spawnedCat);
    }

    @Override
    public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        if (this.updateAverageGroundHeight(world, chunkBox, 0)) {
            this.generateBox(world, chunkBox, 1, 1, 1, 5, 1, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 4, 2, 5, 4, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 1, 0, 4, 1, 0, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 2, 2, 3, 3, 2, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 2, 3, 1, 3, 6, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 2, 3, 5, 3, 6, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 2, 2, 7, 4, 3, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 0, 2, 1, 3, 2, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 0, 2, 5, 3, 2, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 1, 0, 7, 1, 3, 7, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.generateBox(world, chunkBox, 5, 0, 7, 5, 3, 7, Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), false);
            this.placeBlock(world, Blocks.OAK_FENCE.defaultBlockState(), 2, 3, 2, chunkBox);
            this.placeBlock(world, Blocks.OAK_FENCE.defaultBlockState(), 3, 3, 7, chunkBox);
            this.placeBlock(world, Blocks.AIR.defaultBlockState(), 1, 3, 4, chunkBox);
            this.placeBlock(world, Blocks.AIR.defaultBlockState(), 5, 3, 4, chunkBox);
            this.placeBlock(world, Blocks.AIR.defaultBlockState(), 5, 3, 5, chunkBox);
            this.placeBlock(world, Blocks.POTTED_RED_MUSHROOM.defaultBlockState(), 1, 3, 5, chunkBox);
            this.placeBlock(world, Blocks.CRAFTING_TABLE.defaultBlockState(), 3, 2, 6, chunkBox);
            this.placeBlock(world, Blocks.CAULDRON.defaultBlockState(), 4, 2, 6, chunkBox);
            this.placeBlock(world, Blocks.OAK_FENCE.defaultBlockState(), 1, 2, 1, chunkBox);
            this.placeBlock(world, Blocks.OAK_FENCE.defaultBlockState(), 5, 2, 1, chunkBox);
            BlockState iblockdata = (BlockState) Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            BlockState iblockdata1 = (BlockState) Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            BlockState iblockdata2 = (BlockState) Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
            BlockState iblockdata3 = (BlockState) Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);

            this.generateBox(world, chunkBox, 0, 4, 1, 6, 4, 1, iblockdata, iblockdata, false);
            this.generateBox(world, chunkBox, 0, 4, 2, 0, 4, 7, iblockdata1, iblockdata1, false);
            this.generateBox(world, chunkBox, 6, 4, 2, 6, 4, 7, iblockdata2, iblockdata2, false);
            this.generateBox(world, chunkBox, 0, 4, 8, 6, 4, 8, iblockdata3, iblockdata3, false);
            this.placeBlock(world, (BlockState) iblockdata.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT), 0, 4, 1, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), 6, 4, 1, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata3.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), 0, 4, 8, chunkBox);
            this.placeBlock(world, (BlockState) iblockdata3.setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT), 6, 4, 8, chunkBox);

            for (int i = 2; i <= 7; i += 5) {
                for (int j = 1; j <= 5; j += 4) {
                    this.fillColumnDown(world, Blocks.OAK_LOG.defaultBlockState(), j, -1, i, chunkBox);
                }
            }

            if (!this.spawnedWitch) {
                BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(2, 2, 5);

                if (chunkBox.isInside(blockposition_mutableblockposition)) {
                    this.spawnedWitch = true;
                    Witch entitywitch = (Witch) EntityType.WITCH.create(world.getLevel());

                    if (entitywitch != null) {
                        entitywitch.setPersistenceRequired();
                        entitywitch.moveTo((double) blockposition_mutableblockposition.getX() + 0.5D, (double) blockposition_mutableblockposition.getY(), (double) blockposition_mutableblockposition.getZ() + 0.5D, 0.0F, 0.0F);
                        entitywitch.finalizeSpawn(world, world.getCurrentDifficultyAt(blockposition_mutableblockposition), MobSpawnType.STRUCTURE, (SpawnGroupData) null, (CompoundTag) null);
                        world.addFreshEntityWithPassengers(entitywitch, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN); // CraftBukkit - add SpawnReason
                    }
                }
            }

            this.spawnCat(world, chunkBox);
        }
    }

    private void spawnCat(ServerLevelAccessor world, BoundingBox box) {
        if (!this.spawnedCat) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = this.getWorldPos(2, 2, 5);

            if (box.isInside(blockposition_mutableblockposition)) {
                this.spawnedCat = true;
                Cat entitycat = (Cat) EntityType.CAT.create(world.getLevel());

                if (entitycat != null) {
                    entitycat.setPersistenceRequired();
                    entitycat.moveTo((double) blockposition_mutableblockposition.getX() + 0.5D, (double) blockposition_mutableblockposition.getY(), (double) blockposition_mutableblockposition.getZ() + 0.5D, 0.0F, 0.0F);
                    entitycat.finalizeSpawn(world, world.getCurrentDifficultyAt(blockposition_mutableblockposition), MobSpawnType.STRUCTURE, (SpawnGroupData) null, (CompoundTag) null);
                    world.addFreshEntityWithPassengers(entitycat, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN); // CraftBukkit - add SpawnReason
                }
            }
        }

    }
}
