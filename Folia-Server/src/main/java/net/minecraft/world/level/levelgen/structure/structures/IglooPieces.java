package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class IglooPieces {

    public static final int GENERATION_HEIGHT = 90;
    static final ResourceLocation STRUCTURE_LOCATION_IGLOO = new ResourceLocation("igloo/top");
    private static final ResourceLocation STRUCTURE_LOCATION_LADDER = new ResourceLocation("igloo/middle");
    private static final ResourceLocation STRUCTURE_LOCATION_LABORATORY = new ResourceLocation("igloo/bottom");
    static final Map<ResourceLocation, BlockPos> PIVOTS = ImmutableMap.of(IglooPieces.STRUCTURE_LOCATION_IGLOO, new BlockPos(3, 5, 5), IglooPieces.STRUCTURE_LOCATION_LADDER, new BlockPos(1, 3, 1), IglooPieces.STRUCTURE_LOCATION_LABORATORY, new BlockPos(3, 6, 7));
    static final Map<ResourceLocation, BlockPos> OFFSETS = ImmutableMap.of(IglooPieces.STRUCTURE_LOCATION_IGLOO, BlockPos.ZERO, IglooPieces.STRUCTURE_LOCATION_LADDER, new BlockPos(2, -3, 4), IglooPieces.STRUCTURE_LOCATION_LABORATORY, new BlockPos(0, -3, -2));

    public IglooPieces() {}

    public static void addPieces(StructureTemplateManager manager, BlockPos pos, Rotation rotation, StructurePieceAccessor holder, RandomSource random) {
        if (random.nextDouble() < 0.5D) {
            int i = random.nextInt(8) + 4;

            holder.addPiece(new IglooPieces.IglooPiece(manager, IglooPieces.STRUCTURE_LOCATION_LABORATORY, pos, rotation, i * 3));

            for (int j = 0; j < i - 1; ++j) {
                holder.addPiece(new IglooPieces.IglooPiece(manager, IglooPieces.STRUCTURE_LOCATION_LADDER, pos, rotation, j * 3));
            }
        }

        holder.addPiece(new IglooPieces.IglooPiece(manager, IglooPieces.STRUCTURE_LOCATION_IGLOO, pos, rotation, 0));
    }

    public static class IglooPiece extends TemplateStructurePiece {

        public IglooPiece(StructureTemplateManager manager, ResourceLocation identifier, BlockPos pos, Rotation rotation, int yOffset) {
            super(StructurePieceType.IGLOO, 0, manager, identifier, identifier.toString(), IglooPiece.makeSettings(rotation, identifier), IglooPiece.makePosition(identifier, pos, yOffset));
        }

        public IglooPiece(StructureTemplateManager manager, CompoundTag nbt) {
            super(StructurePieceType.IGLOO, nbt, manager, (minecraftkey) -> {
                return IglooPiece.makeSettings(Rotation.valueOf(nbt.getString("Rot")), minecraftkey);
            });
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, ResourceLocation identifier) {
            return (new StructurePlaceSettings()).setRotation(rotation).setMirror(Mirror.NONE).setRotationPivot((BlockPos) IglooPieces.PIVOTS.get(identifier)).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        private static BlockPos makePosition(ResourceLocation identifier, BlockPos pos, int yOffset) {
            return pos.offset((Vec3i) IglooPieces.OFFSETS.get(identifier)).below(yOffset);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putString("Rot", this.placeSettings.getRotation().name());
        }

        @Override
        protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox) {
            if ("chest".equals(metadata)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                // CraftBukkit start - ensure block transformation
                /*
                TileEntity tileentity = worldaccess.getBlockEntity(blockposition.below());

                if (tileentity instanceof TileEntityChest) {
                    ((TileEntityChest) tileentity).setLootTable(LootTables.IGLOO_CHEST, randomsource.nextLong());
                }
                */
                this.setCraftLootTable(world, pos.below(), random, BuiltInLootTables.IGLOO_CHEST);
                // CraftBukkit end

            }
        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            ResourceLocation minecraftkey = new ResourceLocation(this.templateName);
            StructurePlaceSettings definedstructureinfo = IglooPiece.makeSettings(this.placeSettings.getRotation(), minecraftkey);
            BlockPos blockposition1 = (BlockPos) IglooPieces.OFFSETS.get(minecraftkey);
            BlockPos blockposition2 = this.templatePosition.offset(StructureTemplate.calculateRelativePosition(definedstructureinfo, new BlockPos(3 - blockposition1.getX(), 0, -blockposition1.getZ())));
            int i = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockposition2.getX(), blockposition2.getZ());
            BlockPos blockposition3 = this.templatePosition;

            this.templatePosition = this.templatePosition.offset(0, i - 90 - 1, 0);
            super.postProcess(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
            if (minecraftkey.equals(IglooPieces.STRUCTURE_LOCATION_IGLOO)) {
                BlockPos blockposition4 = this.templatePosition.offset(StructureTemplate.calculateRelativePosition(definedstructureinfo, new BlockPos(3, 0, 5)));
                BlockState iblockdata = world.getBlockState(blockposition4.below());

                if (!iblockdata.isAir() && !iblockdata.is(Blocks.LADDER)) {
                    world.setBlock(blockposition4, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                }
            }

            this.templatePosition = blockposition3;
        }
    }
}
