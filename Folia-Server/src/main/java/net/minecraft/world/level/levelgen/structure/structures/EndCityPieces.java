package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class EndCityPieces {

    private static final int MAX_GEN_DEPTH = 8;
    static final EndCityPieces.SectionGenerator HOUSE_TOWER_GENERATOR = new EndCityPieces.SectionGenerator() {
        @Override
        public void init() {}

        @Override
        public boolean generate(StructureTemplateManager manager, int depth, EndCityPieces.EndCityPiece root, BlockPos pos, List<StructurePiece> pieces, RandomSource random) {
            if (depth > 8) {
                return false;
            } else {
                Rotation enumblockrotation = root.placeSettings().getRotation();
                EndCityPieces.EndCityPiece endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, root, pos, "base_floor", enumblockrotation, true));
                int j = random.nextInt(3);

                if (j == 0) {
                    EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 4, -1), "base_roof", enumblockrotation, true));
                } else if (j == 1) {
                    endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 0, -1), "second_floor_2", enumblockrotation, false));
                    endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 8, -1), "second_roof", enumblockrotation, false));
                    EndCityPieces.recursiveChildren(manager, EndCityPieces.TOWER_GENERATOR, depth + 1, endcitypieces_a1, (BlockPos) null, pieces, random);
                } else if (j == 2) {
                    endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 0, -1), "second_floor_2", enumblockrotation, false));
                    endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 4, -1), "third_floor_2", enumblockrotation, false));
                    endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 8, -1), "third_roof", enumblockrotation, true));
                    EndCityPieces.recursiveChildren(manager, EndCityPieces.TOWER_GENERATOR, depth + 1, endcitypieces_a1, (BlockPos) null, pieces, random);
                }

                return true;
            }
        }
    };
    static final List<Tuple<Rotation, BlockPos>> TOWER_BRIDGES = Lists.newArrayList(new Tuple[]{new Tuple<>(Rotation.NONE, new BlockPos(1, -1, 0)), new Tuple<>(Rotation.CLOCKWISE_90, new BlockPos(6, -1, 1)), new Tuple<>(Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, -1, 5)), new Tuple<>(Rotation.CLOCKWISE_180, new BlockPos(5, -1, 6))});
    static final EndCityPieces.SectionGenerator TOWER_GENERATOR = new EndCityPieces.SectionGenerator() {
        @Override
        public void init() {}

        @Override
        public boolean generate(StructureTemplateManager manager, int depth, EndCityPieces.EndCityPiece root, BlockPos pos, List<StructurePiece> pieces, RandomSource random) {
            Rotation enumblockrotation = root.placeSettings().getRotation();
            EndCityPieces.EndCityPiece endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, root, new BlockPos(3 + random.nextInt(2), -3, 3 + random.nextInt(2)), "tower_base", enumblockrotation, true));

            endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, 7, 0), "tower_piece", enumblockrotation, true));
            EndCityPieces.EndCityPiece endcitypieces_a2 = random.nextInt(3) == 0 ? endcitypieces_a1 : null;
            int j = 1 + random.nextInt(3);

            for (int k = 0; k < j; ++k) {
                endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, 4, 0), "tower_piece", enumblockrotation, true));
                if (k < j - 1 && random.nextBoolean()) {
                    endcitypieces_a2 = endcitypieces_a1;
                }
            }

            if (endcitypieces_a2 != null) {
                Iterator iterator = EndCityPieces.TOWER_BRIDGES.iterator();

                while (iterator.hasNext()) {
                    Tuple<Rotation, BlockPos> tuple = (Tuple) iterator.next();

                    if (random.nextBoolean()) {
                        EndCityPieces.EndCityPiece endcitypieces_a3 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a2, (BlockPos) tuple.getB(), "bridge_end", enumblockrotation.getRotated((Rotation) tuple.getA()), true));

                        EndCityPieces.recursiveChildren(manager, EndCityPieces.TOWER_BRIDGE_GENERATOR, depth + 1, endcitypieces_a3, (BlockPos) null, pieces, random);
                    }
                }

                EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 4, -1), "tower_top", enumblockrotation, true));
            } else {
                if (depth != 7) {
                    return EndCityPieces.recursiveChildren(manager, EndCityPieces.FAT_TOWER_GENERATOR, depth + 1, endcitypieces_a1, (BlockPos) null, pieces, random);
                }

                EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-1, 4, -1), "tower_top", enumblockrotation, true));
            }

            return true;
        }
    };
    static final EndCityPieces.SectionGenerator TOWER_BRIDGE_GENERATOR = new EndCityPieces.SectionGenerator() {
        public boolean shipCreated;

        @Override
        public void init() {
            this.shipCreated = false;
        }

        @Override
        public boolean generate(StructureTemplateManager manager, int depth, EndCityPieces.EndCityPiece root, BlockPos pos, List<StructurePiece> pieces, RandomSource random) {
            Rotation enumblockrotation = root.placeSettings().getRotation();
            int j = random.nextInt(4) + 1;
            EndCityPieces.EndCityPiece endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, root, new BlockPos(0, 0, -4), "bridge_piece", enumblockrotation, true));

            endcitypieces_a1.setGenDepth(-1);
            byte b0 = 0;

            for (int k = 0; k < j; ++k) {
                if (random.nextBoolean()) {
                    endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, b0, -4), "bridge_piece", enumblockrotation, true));
                    b0 = 0;
                } else {
                    if (random.nextBoolean()) {
                        endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, b0, -4), "bridge_steep_stairs", enumblockrotation, true));
                    } else {
                        endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, b0, -8), "bridge_gentle_stairs", enumblockrotation, true));
                    }

                    b0 = 4;
                }
            }

            if (!this.shipCreated && random.nextInt(10 - depth) == 0) {
                EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-8 + random.nextInt(8), b0, -70 + random.nextInt(10)), "ship", enumblockrotation, true));
                this.shipCreated = true;
            } else if (!EndCityPieces.recursiveChildren(manager, EndCityPieces.HOUSE_TOWER_GENERATOR, depth + 1, endcitypieces_a1, new BlockPos(-3, b0 + 1, -11), pieces, random)) {
                return false;
            }

            endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(4, b0, 0), "bridge_end", enumblockrotation.getRotated(Rotation.CLOCKWISE_180), true));
            endcitypieces_a1.setGenDepth(-1);
            return true;
        }
    };
    static final List<Tuple<Rotation, BlockPos>> FAT_TOWER_BRIDGES = Lists.newArrayList(new Tuple[]{new Tuple<>(Rotation.NONE, new BlockPos(4, -1, 0)), new Tuple<>(Rotation.CLOCKWISE_90, new BlockPos(12, -1, 4)), new Tuple<>(Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, -1, 8)), new Tuple<>(Rotation.CLOCKWISE_180, new BlockPos(8, -1, 12))});
    static final EndCityPieces.SectionGenerator FAT_TOWER_GENERATOR = new EndCityPieces.SectionGenerator() {
        @Override
        public void init() {}

        @Override
        public boolean generate(StructureTemplateManager manager, int depth, EndCityPieces.EndCityPiece root, BlockPos pos, List<StructurePiece> pieces, RandomSource random) {
            Rotation enumblockrotation = root.placeSettings().getRotation();
            EndCityPieces.EndCityPiece endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, root, new BlockPos(-3, 4, -3), "fat_tower_base", enumblockrotation, true));

            endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, 4, 0), "fat_tower_middle", enumblockrotation, true));

            for (int j = 0; j < 2 && random.nextInt(3) != 0; ++j) {
                endcitypieces_a1 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(0, 8, 0), "fat_tower_middle", enumblockrotation, true));
                Iterator iterator = EndCityPieces.FAT_TOWER_BRIDGES.iterator();

                while (iterator.hasNext()) {
                    Tuple<Rotation, BlockPos> tuple = (Tuple) iterator.next();

                    if (random.nextBoolean()) {
                        EndCityPieces.EndCityPiece endcitypieces_a2 = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, (BlockPos) tuple.getB(), "bridge_end", enumblockrotation.getRotated((Rotation) tuple.getA()), true));

                        EndCityPieces.recursiveChildren(manager, EndCityPieces.TOWER_BRIDGE_GENERATOR, depth + 1, endcitypieces_a2, (BlockPos) null, pieces, random);
                    }
                }
            }

            EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(manager, endcitypieces_a1, new BlockPos(-2, 8, -2), "fat_tower_top", enumblockrotation, true));
            return true;
        }
    };

    public EndCityPieces() {}

    static EndCityPieces.EndCityPiece addPiece(StructureTemplateManager structureTemplateManager, EndCityPieces.EndCityPiece lastPiece, BlockPos relativePosition, String template, Rotation rotation, boolean ignoreAir) {
        EndCityPieces.EndCityPiece endcitypieces_a1 = new EndCityPieces.EndCityPiece(structureTemplateManager, template, lastPiece.templatePosition(), rotation, ignoreAir);
        BlockPos blockposition1 = lastPiece.template().calculateConnectedPosition(lastPiece.placeSettings(), relativePosition, endcitypieces_a1.placeSettings(), BlockPos.ZERO);

        endcitypieces_a1.move(blockposition1.getX(), blockposition1.getY(), blockposition1.getZ());
        return endcitypieces_a1;
    }

    public static void startHouseTower(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, List<StructurePiece> pieces, RandomSource random) {
        EndCityPieces.FAT_TOWER_GENERATOR.init();
        EndCityPieces.HOUSE_TOWER_GENERATOR.init();
        EndCityPieces.TOWER_BRIDGE_GENERATOR.init();
        EndCityPieces.TOWER_GENERATOR.init();
        EndCityPieces.EndCityPiece endcitypieces_a = EndCityPieces.addHelper(pieces, new EndCityPieces.EndCityPiece(structureTemplateManager, "base_floor", pos, rotation, true));

        endcitypieces_a = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_a, new BlockPos(-1, 0, -1), "second_floor_1", rotation, false));
        endcitypieces_a = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_a, new BlockPos(-1, 4, -1), "third_floor_1", rotation, false));
        endcitypieces_a = EndCityPieces.addHelper(pieces, EndCityPieces.addPiece(structureTemplateManager, endcitypieces_a, new BlockPos(-1, 8, -1), "third_roof", rotation, true));
        EndCityPieces.recursiveChildren(structureTemplateManager, EndCityPieces.TOWER_GENERATOR, 1, endcitypieces_a, (BlockPos) null, pieces, random);
    }

    static EndCityPieces.EndCityPiece addHelper(List<StructurePiece> pieces, EndCityPieces.EndCityPiece piece) {
        pieces.add(piece);
        return piece;
    }

    static boolean recursiveChildren(StructureTemplateManager manager, EndCityPieces.SectionGenerator piece, int depth, EndCityPieces.EndCityPiece parent, BlockPos pos, List<StructurePiece> pieces, RandomSource random) {
        if (depth > 8) {
            return false;
        } else {
            List<StructurePiece> list1 = Lists.newArrayList();

            if (piece.generate(manager, depth, parent, pos, list1, random)) {
                boolean flag = false;
                int j = random.nextInt();
                Iterator iterator = list1.iterator();

                while (iterator.hasNext()) {
                    StructurePiece structurepiece = (StructurePiece) iterator.next();

                    structurepiece.setGenDepth(j);
                    StructurePiece structurepiece1 = StructurePiece.findCollisionPiece(pieces, structurepiece.getBoundingBox());

                    if (structurepiece1 != null && structurepiece1.getGenDepth() != parent.getGenDepth()) {
                        flag = true;
                        break;
                    }
                }

                if (!flag) {
                    pieces.addAll(list1);
                    return true;
                }
            }

            return false;
        }
    }

    public static class EndCityPiece extends TemplateStructurePiece {

        public EndCityPiece(StructureTemplateManager manager, String template, BlockPos pos, Rotation rotation, boolean includeAir) {
            super(StructurePieceType.END_CITY_PIECE, 0, manager, EndCityPiece.makeResourceLocation(template), template, EndCityPiece.makeSettings(includeAir, rotation), pos);
        }

        public EndCityPiece(StructureTemplateManager manager, CompoundTag nbt) {
            super(StructurePieceType.END_CITY_PIECE, nbt, manager, (minecraftkey) -> {
                return EndCityPiece.makeSettings(nbt.getBoolean("OW"), Rotation.valueOf(nbt.getString("Rot")));
            });
        }

        private static StructurePlaceSettings makeSettings(boolean includeAir, Rotation rotation) {
            BlockIgnoreProcessor definedstructureprocessorblockignore = includeAir ? BlockIgnoreProcessor.STRUCTURE_BLOCK : BlockIgnoreProcessor.STRUCTURE_AND_AIR;

            return (new StructurePlaceSettings()).setIgnoreEntities(true).addProcessor(definedstructureprocessorblockignore).setRotation(rotation);
        }

        @Override
        protected ResourceLocation makeTemplateLocation() {
            return EndCityPiece.makeResourceLocation(this.templateName);
        }

        private static ResourceLocation makeResourceLocation(String template) {
            return new ResourceLocation("end_city/" + template);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
            super.addAdditionalSaveData(context, nbt);
            nbt.putString("Rot", this.placeSettings.getRotation().name());
            nbt.putBoolean("OW", this.placeSettings.getProcessors().get(0) == BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void handleDataMarker(String metadata, BlockPos pos, ServerLevelAccessor world, RandomSource random, BoundingBox boundingBox) {
            if (metadata.startsWith("Chest")) {
                BlockPos blockposition1 = pos.below();

                if (boundingBox.isInside(blockposition1)) {
                    // CraftBukkit start - ensure block transformation
                    /*
                    TileEntityLootable.setLootTable(worldaccess, randomsource, blockposition1, LootTables.END_CITY_TREASURE);
                    */
                    this.setCraftLootTable(world, blockposition1, random, BuiltInLootTables.END_CITY_TREASURE);
                    // CraftBukkit end
                }
            } else if (boundingBox.isInside(pos) && Level.isInSpawnableBounds(pos)) {
                if (metadata.startsWith("Sentry")) {
                    Shulker entityshulker = (Shulker) EntityType.SHULKER.create(world.getLevel());

                    if (entityshulker != null) {
                        entityshulker.setPos((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D);
                        world.addFreshEntity(entityshulker);
                    }
                } else if (metadata.startsWith("Elytra")) {
                    ItemFrame entityitemframe = new ItemFrame(world.getLevel(), pos, this.placeSettings.getRotation().rotate(Direction.SOUTH));

                    entityitemframe.setItem(new ItemStack(Items.ELYTRA), false);
                    world.addFreshEntity(entityitemframe);
                }
            }

        }
    }

    private interface SectionGenerator {

        void init();

        boolean generate(StructureTemplateManager manager, int depth, EndCityPieces.EndCityPiece root, BlockPos pos, List<StructurePiece> pieces, RandomSource random);
    }
}
