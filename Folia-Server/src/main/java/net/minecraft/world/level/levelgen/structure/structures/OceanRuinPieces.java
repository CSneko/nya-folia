package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendLoot;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class OceanRuinPieces {

    static final StructureProcessor WARM_SUSPICIOUS_BLOCK_PROCESSOR = OceanRuinPieces.archyRuleProcessor(Blocks.SAND, Blocks.SUSPICIOUS_SAND, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY);
    static final StructureProcessor COLD_SUSPICIOUS_BLOCK_PROCESSOR = OceanRuinPieces.archyRuleProcessor(Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY);
    private static final ResourceLocation[] WARM_RUINS = new ResourceLocation[]{new ResourceLocation("underwater_ruin/warm_1"), new ResourceLocation("underwater_ruin/warm_2"), new ResourceLocation("underwater_ruin/warm_3"), new ResourceLocation("underwater_ruin/warm_4"), new ResourceLocation("underwater_ruin/warm_5"), new ResourceLocation("underwater_ruin/warm_6"), new ResourceLocation("underwater_ruin/warm_7"), new ResourceLocation("underwater_ruin/warm_8")};
    private static final ResourceLocation[] RUINS_BRICK = new ResourceLocation[]{new ResourceLocation("underwater_ruin/brick_1"), new ResourceLocation("underwater_ruin/brick_2"), new ResourceLocation("underwater_ruin/brick_3"), new ResourceLocation("underwater_ruin/brick_4"), new ResourceLocation("underwater_ruin/brick_5"), new ResourceLocation("underwater_ruin/brick_6"), new ResourceLocation("underwater_ruin/brick_7"), new ResourceLocation("underwater_ruin/brick_8")};
    private static final ResourceLocation[] RUINS_CRACKED = new ResourceLocation[]{new ResourceLocation("underwater_ruin/cracked_1"), new ResourceLocation("underwater_ruin/cracked_2"), new ResourceLocation("underwater_ruin/cracked_3"), new ResourceLocation("underwater_ruin/cracked_4"), new ResourceLocation("underwater_ruin/cracked_5"), new ResourceLocation("underwater_ruin/cracked_6"), new ResourceLocation("underwater_ruin/cracked_7"), new ResourceLocation("underwater_ruin/cracked_8")};
    private static final ResourceLocation[] RUINS_MOSSY = new ResourceLocation[]{new ResourceLocation("underwater_ruin/mossy_1"), new ResourceLocation("underwater_ruin/mossy_2"), new ResourceLocation("underwater_ruin/mossy_3"), new ResourceLocation("underwater_ruin/mossy_4"), new ResourceLocation("underwater_ruin/mossy_5"), new ResourceLocation("underwater_ruin/mossy_6"), new ResourceLocation("underwater_ruin/mossy_7"), new ResourceLocation("underwater_ruin/mossy_8")};
    private static final ResourceLocation[] BIG_RUINS_BRICK = new ResourceLocation[]{new ResourceLocation("underwater_ruin/big_brick_1"), new ResourceLocation("underwater_ruin/big_brick_2"), new ResourceLocation("underwater_ruin/big_brick_3"), new ResourceLocation("underwater_ruin/big_brick_8")};
    private static final ResourceLocation[] BIG_RUINS_MOSSY = new ResourceLocation[]{new ResourceLocation("underwater_ruin/big_mossy_1"), new ResourceLocation("underwater_ruin/big_mossy_2"), new ResourceLocation("underwater_ruin/big_mossy_3"), new ResourceLocation("underwater_ruin/big_mossy_8")};
    private static final ResourceLocation[] BIG_RUINS_CRACKED = new ResourceLocation[]{new ResourceLocation("underwater_ruin/big_cracked_1"), new ResourceLocation("underwater_ruin/big_cracked_2"), new ResourceLocation("underwater_ruin/big_cracked_3"), new ResourceLocation("underwater_ruin/big_cracked_8")};
    private static final ResourceLocation[] BIG_WARM_RUINS = new ResourceLocation[]{new ResourceLocation("underwater_ruin/big_warm_4"), new ResourceLocation("underwater_ruin/big_warm_5"), new ResourceLocation("underwater_ruin/big_warm_6"), new ResourceLocation("underwater_ruin/big_warm_7")};

    public OceanRuinPieces() {}

    private static StructureProcessor archyRuleProcessor(Block baseBlock, Block suspiciousBlock, ResourceLocation lootTableId) {
        return new CappedProcessor(new RuleProcessor(List.of(new ProcessorRule(new BlockMatchTest(baseBlock), AlwaysTrueTest.INSTANCE, PosAlwaysTrueTest.INSTANCE, suspiciousBlock.defaultBlockState(), new AppendLoot(lootTableId)))), ConstantInt.of(5));
    }

    private static ResourceLocation getSmallWarmRuin(RandomSource random) {
        return (ResourceLocation) Util.getRandom((Object[]) OceanRuinPieces.WARM_RUINS, random);
    }

    private static ResourceLocation getBigWarmRuin(RandomSource random) {
        return (ResourceLocation) Util.getRandom((Object[]) OceanRuinPieces.BIG_WARM_RUINS, random);
    }

    public static void addPieces(StructureTemplateManager manager, BlockPos pos, Rotation rotation, StructurePieceAccessor holder, RandomSource random, OceanRuinStructure structure) {
        boolean flag = random.nextFloat() <= structure.largeProbability;
        float f = flag ? 0.9F : 0.8F;

        OceanRuinPieces.addPiece(manager, pos, rotation, holder, random, structure, flag, f);
        if (flag && random.nextFloat() <= structure.clusterProbability) {
            OceanRuinPieces.addClusterRuins(manager, random, rotation, pos, structure, holder);
        }

    }

    private static void addClusterRuins(StructureTemplateManager manager, RandomSource random, Rotation rotation, BlockPos pos, OceanRuinStructure structure, StructurePieceAccessor pieces) {
        BlockPos blockposition1 = new BlockPos(pos.getX(), 90, pos.getZ());
        BlockPos blockposition2 = StructureTemplate.transform(new BlockPos(15, 0, 15), Mirror.NONE, rotation, BlockPos.ZERO).offset(blockposition1);
        BoundingBox structureboundingbox = BoundingBox.fromCorners(blockposition1, blockposition2);
        BlockPos blockposition3 = new BlockPos(Math.min(blockposition1.getX(), blockposition2.getX()), blockposition1.getY(), Math.min(blockposition1.getZ(), blockposition2.getZ()));
        List<BlockPos> list = OceanRuinPieces.allPositions(random, blockposition3);
        int i = Mth.nextInt(random, 4, 8);

        for (int j = 0; j < i; ++j) {
            if (!list.isEmpty()) {
                int k = random.nextInt(list.size());
                BlockPos blockposition4 = (BlockPos) list.remove(k);
                Rotation enumblockrotation1 = Rotation.getRandom(random);
                BlockPos blockposition5 = StructureTemplate.transform(new BlockPos(5, 0, 6), Mirror.NONE, enumblockrotation1, BlockPos.ZERO).offset(blockposition4);
                BoundingBox structureboundingbox1 = BoundingBox.fromCorners(blockposition4, blockposition5);

                if (!structureboundingbox1.intersects(structureboundingbox)) {
                    OceanRuinPieces.addPiece(manager, blockposition4, enumblockrotation1, pieces, random, structure, false, 0.8F);
                }
            }
        }

    }

    private static List<BlockPos> allPositions(RandomSource random, BlockPos pos) {
        List<BlockPos> list = Lists.newArrayList();

        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, -16 + Mth.nextInt(random, 4, 8)));
        list.add(pos.offset(Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 6)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 3, 8)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 8)));
        return list;
    }

    private static void addPiece(StructureTemplateManager manager, BlockPos pos, Rotation rotation, StructurePieceAccessor holder, RandomSource random, OceanRuinStructure structure, boolean large, float integrity) {
        switch (structure.biomeTemp) {
            case WARM:
            default:
                ResourceLocation minecraftkey = large ? OceanRuinPieces.getBigWarmRuin(random) : OceanRuinPieces.getSmallWarmRuin(random);

                holder.addPiece(new OceanRuinPieces.OceanRuinPiece(manager, minecraftkey, pos, rotation, integrity, structure.biomeTemp, large));
                break;
            case COLD:
                ResourceLocation[] aminecraftkey = large ? OceanRuinPieces.BIG_RUINS_BRICK : OceanRuinPieces.RUINS_BRICK;
                ResourceLocation[] aminecraftkey1 = large ? OceanRuinPieces.BIG_RUINS_CRACKED : OceanRuinPieces.RUINS_CRACKED;
                ResourceLocation[] aminecraftkey2 = large ? OceanRuinPieces.BIG_RUINS_MOSSY : OceanRuinPieces.RUINS_MOSSY;
                int i = random.nextInt(aminecraftkey.length);

                holder.addPiece(new OceanRuinPieces.OceanRuinPiece(manager, aminecraftkey[i], pos, rotation, integrity, structure.biomeTemp, large));
                holder.addPiece(new OceanRuinPieces.OceanRuinPiece(manager, aminecraftkey1[i], pos, rotation, 0.7F, structure.biomeTemp, large));
                holder.addPiece(new OceanRuinPieces.OceanRuinPiece(manager, aminecraftkey2[i], pos, rotation, 0.5F, structure.biomeTemp, large));
        }

    }

    public static class OceanRuinPiece extends TemplateStructurePiece {

        private final OceanRuinStructure.Type biomeType;
        private final float integrity;
        private final boolean isLarge;

        public OceanRuinPiece(StructureTemplateManager structureTemplateManager, ResourceLocation template, BlockPos pos, Rotation rotation, float integrity, OceanRuinStructure.Type biomeType, boolean large) {
            super(StructurePieceType.OCEAN_RUIN, 0, structureTemplateManager, template, template.toString(), OceanRuinPiece.makeSettings(rotation, integrity, biomeType), pos);
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = large;
        }

        private OceanRuinPiece(StructureTemplateManager holder, CompoundTag nbt, Rotation rotation, float integrity, OceanRuinStructure.Type biomeType, boolean large) {
            super(StructurePieceType.OCEAN_RUIN, nbt, holder, (minecraftkey) -> {
                return OceanRuinPiece.makeSettings(rotation, integrity, biomeType);
            });
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = large;
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, float integrity, OceanRuinStructure.Type temperature) {
            StructureProcessor definedstructureprocessor = temperature == OceanRuinStructure.Type.COLD ? OceanRuinPieces.COLD_SUSPICIOUS_BLOCK_PROCESSOR : OceanRuinPieces.WARM_SUSPICIOUS_BLOCK_PROCESSOR;

            return (new StructurePlaceSettings()).setRotation(rotation).setMirror(Mirror.NONE).addProcessor(new BlockRotProcessor(integrity)).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR).addProcessor(definedstructureprocessor);
        }

        public static OceanRuinPieces.OceanRuinPiece create(StructureTemplateManager structureTemplateManager, CompoundTag nbt) {
            Rotation enumblockrotation = Rotation.valueOf(nbt.getString("Rot"));
            float f = nbt.getFloat("Integrity");
            OceanRuinStructure.Type oceanruinstructure_a = OceanRuinStructure.Type.valueOf(nbt.getString("BiomeType"));
            boolean flag = nbt.getBoolean("IsLarge");

            return new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, nbt, enumblockrotation, f, oceanruinstructure_a, flag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putString("Rot", this.placeSettings.getRotation().name());
            nbt.putFloat("Integrity", this.integrity);
            nbt.putString("BiomeType", this.biomeType.toString());
            nbt.putBoolean("IsLarge", this.isLarge);
        }

        @Override
        protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox) {
            if ("chest".equals(metadata)) {
                // CraftBukkit start - transform block to ensure loot table is accessible
                /*
                worldaccess.setBlock(blockposition, (IBlockData) Blocks.CHEST.defaultBlockState().setValue(BlockChest.WATERLOGGED, worldaccess.getFluidState(blockposition).is(TagsFluid.WATER)), 2);
                TileEntity tileentity = worldaccess.getBlockEntity(blockposition);

                if (tileentity instanceof TileEntityChest) {
                    ((TileEntityChest) tileentity).setLootTable(this.isLarge ? LootTables.UNDERWATER_RUIN_BIG : LootTables.UNDERWATER_RUIN_SMALL, randomsource.nextLong());
                }
                */
                org.bukkit.craftbukkit.block.CraftChest craftChest = (org.bukkit.craftbukkit.block.CraftChest) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, world.getFluidState(pos).is(FluidTags.WATER)), null);
                craftChest.setSeed(random.nextLong());
                craftChest.setLootTable(org.bukkit.Bukkit.getLootTable(org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(this.isLarge ? BuiltInLootTables.UNDERWATER_RUIN_BIG : BuiltInLootTables.UNDERWATER_RUIN_SMALL)));
                this.placeCraftBlockEntity(world, pos, craftChest, 2);
                // CraftBukkit end
            } else if ("drowned".equals(metadata)) {
                Drowned entitydrowned = (Drowned) EntityType.DROWNED.create(world.getLevel());

                if (entitydrowned != null) {
                    entitydrowned.setPersistenceRequired();
                    entitydrowned.moveTo(pos, 0.0F, 0.0F);
                    entitydrowned.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, (SpawnGroupData) null, (CompoundTag) null);
                    world.addFreshEntityWithPassengers(entitydrowned);
                    if (pos.getY() > world.getSeaLevel()) {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    } else {
                        world.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                    }
                }
            }

        }

        @Override
        public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int i = world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.templatePosition.getX(), this.templatePosition.getZ());

            this.templatePosition = new BlockPos(this.templatePosition.getX(), i, this.templatePosition.getZ());
            BlockPos blockposition1 = StructureTemplate.transform(new BlockPos(this.template.getSize().getX() - 1, 0, this.template.getSize().getZ() - 1), Mirror.NONE, this.placeSettings.getRotation(), BlockPos.ZERO).offset(this.templatePosition);

            this.templatePosition = new BlockPos(this.templatePosition.getX(), this.getHeight(this.templatePosition, world, blockposition1), this.templatePosition.getZ());
            super.postProcess(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
        }

        private int getHeight(BlockPos start, BlockGetter world, BlockPos end) {
            int i = start.getY();
            int j = 512;
            int k = i - 1;
            int l = 0;
            Iterator iterator = BlockPos.betweenClosed(start, end).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition2 = (BlockPos) iterator.next();
                int i1 = blockposition2.getX();
                int j1 = blockposition2.getZ();
                int k1 = start.getY() - 1;
                BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos(i1, k1, j1);
                BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition);

                for (FluidState fluid = world.getFluidState(blockposition_mutableblockposition); (iblockdata.isAir() || fluid.is(FluidTags.WATER) || iblockdata.is(BlockTags.ICE)) && k1 > world.getMinBuildHeight() + 1; fluid = world.getFluidState(blockposition_mutableblockposition)) {
                    --k1;
                    blockposition_mutableblockposition.set(i1, k1, j1);
                    iblockdata = world.getBlockState(blockposition_mutableblockposition);
                }

                j = Math.min(j, k1);
                if (k1 < k - 2) {
                    ++l;
                }
            }

            int l1 = Math.abs(start.getX() - end.getX());

            if (k - j > 2 && l > l1 - 2) {
                i = j + 1;
            }

            return i;
        }
    }
}
