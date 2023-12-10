package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
// CraftBukkit end

public class StructureTemplate {

    public static final String PALETTE_TAG = "palette";
    public static final String PALETTE_LIST_TAG = "palettes";
    public static final String ENTITIES_TAG = "entities";
    public static final String BLOCKS_TAG = "blocks";
    public static final String BLOCK_TAG_POS = "pos";
    public static final String BLOCK_TAG_STATE = "state";
    public static final String BLOCK_TAG_NBT = "nbt";
    public static final String ENTITY_TAG_POS = "pos";
    public static final String ENTITY_TAG_BLOCKPOS = "blockPos";
    public static final String ENTITY_TAG_NBT = "nbt";
    public static final String SIZE_TAG = "size";
    public final List<StructureTemplate.Palette> palettes = Lists.newArrayList();
    public final List<StructureTemplate.StructureEntityInfo> entityInfoList = Lists.newArrayList();
    private Vec3i size;
    private String author;

    // CraftBukkit start - data containers
    private static final CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new CraftPersistentDataTypeRegistry();
    public CraftPersistentDataContainer persistentDataContainer = new CraftPersistentDataContainer(StructureTemplate.DATA_TYPE_REGISTRY);
    // CraftBukkit end

    public StructureTemplate() {
        this.size = Vec3i.ZERO;
        this.author = "?";
    }

    public Vec3i getSize() {
        return this.size;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthor() {
        return this.author;
    }

    public void fillFromWorld(Level world, BlockPos start, Vec3i dimensions, boolean includeEntities, @Nullable Block ignoredBlock) {
        if (dimensions.getX() >= 1 && dimensions.getY() >= 1 && dimensions.getZ() >= 1) {
            BlockPos blockposition1 = start.offset(dimensions).offset(-1, -1, -1);
            List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
            BlockPos blockposition2 = new BlockPos(Math.min(start.getX(), blockposition1.getX()), Math.min(start.getY(), blockposition1.getY()), Math.min(start.getZ(), blockposition1.getZ()));
            BlockPos blockposition3 = new BlockPos(Math.max(start.getX(), blockposition1.getX()), Math.max(start.getY(), blockposition1.getY()), Math.max(start.getZ(), blockposition1.getZ()));

            this.size = dimensions;
            Iterator iterator = BlockPos.betweenClosed(blockposition2, blockposition3).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition4 = (BlockPos) iterator.next();
                BlockPos blockposition5 = blockposition4.subtract(blockposition2);
                BlockState iblockdata = world.getBlockState(blockposition4);

                if (ignoredBlock == null || !iblockdata.is(ignoredBlock)) {
                    BlockEntity tileentity = world.getBlockEntity(blockposition4);
                    StructureTemplate.StructureBlockInfo definedstructure_blockinfo;

                    if (tileentity != null) {
                        definedstructure_blockinfo = new StructureTemplate.StructureBlockInfo(blockposition5, iblockdata, tileentity.saveWithId());
                    } else {
                        definedstructure_blockinfo = new StructureTemplate.StructureBlockInfo(blockposition5, iblockdata, (CompoundTag) null);
                    }

                    StructureTemplate.addToLists(definedstructure_blockinfo, list, list1, list2);
                }
            }

            List<StructureTemplate.StructureBlockInfo> list3 = StructureTemplate.buildInfoList(list, list1, list2);

            this.palettes.clear();
            this.palettes.add(new StructureTemplate.Palette(list3));
            if (includeEntities) {
                this.fillEntityList(world, blockposition2, blockposition3.offset(1, 1, 1));
            } else {
                this.entityInfoList.clear();
            }

        }
    }

    private static void addToLists(StructureTemplate.StructureBlockInfo blockInfo, List<StructureTemplate.StructureBlockInfo> fullBlocks, List<StructureTemplate.StructureBlockInfo> blocksWithNbt, List<StructureTemplate.StructureBlockInfo> otherBlocks) {
        if (blockInfo.nbt != null) {
            blocksWithNbt.add(blockInfo);
        } else if (!blockInfo.state.getBlock().hasDynamicShape() && blockInfo.state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            fullBlocks.add(blockInfo);
        } else {
            otherBlocks.add(blockInfo);
        }

    }

    private static List<StructureTemplate.StructureBlockInfo> buildInfoList(List<StructureTemplate.StructureBlockInfo> fullBlocks, List<StructureTemplate.StructureBlockInfo> blocksWithNbt, List<StructureTemplate.StructureBlockInfo> otherBlocks) {
        Comparator<StructureTemplate.StructureBlockInfo> comparator = Comparator.<StructureTemplate.StructureBlockInfo>comparingInt((definedstructure_blockinfo) -> { // CraftBukkit - decompile error
            return definedstructure_blockinfo.pos.getY();
        }).thenComparingInt((definedstructure_blockinfo) -> {
            return definedstructure_blockinfo.pos.getX();
        }).thenComparingInt((definedstructure_blockinfo) -> {
            return definedstructure_blockinfo.pos.getZ();
        });

        fullBlocks.sort(comparator);
        otherBlocks.sort(comparator);
        blocksWithNbt.sort(comparator);
        List<StructureTemplate.StructureBlockInfo> list3 = Lists.newArrayList();

        list3.addAll(fullBlocks);
        list3.addAll(otherBlocks);
        list3.addAll(blocksWithNbt);
        return list3;
    }

    private void fillEntityList(Level world, BlockPos firstCorner, BlockPos secondCorner) {
        List<Entity> list = world.getEntitiesOfClass(Entity.class, new AABB(firstCorner, secondCorner), (entity) -> {
            return !(entity instanceof Player);
        });

        this.entityInfoList.clear();

        Vec3 vec3d;
        CompoundTag nbttagcompound;
        BlockPos blockposition2;

        for (Iterator iterator = list.iterator(); iterator.hasNext(); this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3d, blockposition2, nbttagcompound.copy()))) {
            Entity entity = (Entity) iterator.next();

            vec3d = new Vec3(entity.getX() - (double) firstCorner.getX(), entity.getY() - (double) firstCorner.getY(), entity.getZ() - (double) firstCorner.getZ());
            nbttagcompound = new CompoundTag();
            entity.save(nbttagcompound);
            if (entity instanceof Painting) {
                blockposition2 = ((Painting) entity).getPos().subtract(firstCorner);
            } else {
                blockposition2 = BlockPos.containing(vec3d);
            }
        }

    }

    public List<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos pos, StructurePlaceSettings placementData, Block block) {
        return this.filterBlocks(pos, placementData, block, true);
    }

    public ObjectArrayList<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos pos, StructurePlaceSettings placementData, Block block, boolean transformed) {
        ObjectArrayList<StructureTemplate.StructureBlockInfo> objectarraylist = new ObjectArrayList();
        BoundingBox structureboundingbox = placementData.getBoundingBox();

        if (this.palettes.isEmpty()) {
            return objectarraylist;
        } else {
            Iterator iterator = placementData.getRandomPalette(this.palettes, pos).blocks(block).iterator();

            while (iterator.hasNext()) {
                StructureTemplate.StructureBlockInfo definedstructure_blockinfo = (StructureTemplate.StructureBlockInfo) iterator.next();
                BlockPos blockposition1 = transformed ? StructureTemplate.calculateRelativePosition(placementData, definedstructure_blockinfo.pos).offset(pos) : definedstructure_blockinfo.pos;

                if (structureboundingbox == null || structureboundingbox.isInside(blockposition1)) {
                    objectarraylist.add(new StructureTemplate.StructureBlockInfo(blockposition1, definedstructure_blockinfo.state.rotate(placementData.getRotation()), definedstructure_blockinfo.nbt));
                }
            }

            return objectarraylist;
        }
    }

    public BlockPos calculateConnectedPosition(StructurePlaceSettings placementData1, BlockPos pos1, StructurePlaceSettings placementData2, BlockPos pos2) {
        BlockPos blockposition2 = StructureTemplate.calculateRelativePosition(placementData1, pos1);
        BlockPos blockposition3 = StructureTemplate.calculateRelativePosition(placementData2, pos2);

        return blockposition2.subtract(blockposition3);
    }

    public static BlockPos calculateRelativePosition(StructurePlaceSettings placementData, BlockPos pos) {
        return StructureTemplate.transform(pos, placementData.getMirror(), placementData.getRotation(), placementData.getRotationPivot());
    }

    public boolean placeInWorld(ServerLevelAccessor world, BlockPos pos, BlockPos pivot, StructurePlaceSettings placementData, RandomSource random, int flags) {
        if (this.palettes.isEmpty()) {
            return false;
        } else {
            // CraftBukkit start
            // We only want the TransformerGeneratorAccess at certain locations because in here are many "block update" calls that shouldn't be transformed
            ServerLevelAccessor wrappedAccess = world;
            org.bukkit.craftbukkit.util.CraftStructureTransformer structureTransformer = null;
            if (wrappedAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
                world = transformerAccess.getHandle();
                structureTransformer = transformerAccess.getStructureTransformer();
                // The structureTransformer is not needed if we can not transform blocks therefore we can save a little bit of performance doing this
                if (structureTransformer != null && !structureTransformer.canTransformBlocks()) {
                    structureTransformer = null;
                }
            }
            // CraftBukkit end
            List<StructureTemplate.StructureBlockInfo> list = placementData.getRandomPalette(this.palettes, pos).blocks();

            if ((!list.isEmpty() || !placementData.isIgnoreEntities() && !this.entityInfoList.isEmpty()) && this.size.getX() >= 1 && this.size.getY() >= 1 && this.size.getZ() >= 1) {
                BoundingBox structureboundingbox = placementData.getBoundingBox();
                List<BlockPos> list1 = Lists.newArrayListWithCapacity(placementData.shouldKeepLiquids() ? list.size() : 0);
                List<BlockPos> list2 = Lists.newArrayListWithCapacity(placementData.shouldKeepLiquids() ? list.size() : 0);
                List<Pair<BlockPos, CompoundTag>> list3 = Lists.newArrayListWithCapacity(list.size());
                int j = Integer.MAX_VALUE;
                int k = Integer.MAX_VALUE;
                int l = Integer.MAX_VALUE;
                int i1 = Integer.MIN_VALUE;
                int j1 = Integer.MIN_VALUE;
                int k1 = Integer.MIN_VALUE;
                List<StructureTemplate.StructureBlockInfo> list4 = StructureTemplate.processBlockInfos(world, pos, pivot, placementData, list);
                Iterator iterator = list4.iterator();

                BlockEntity tileentity;

                while (iterator.hasNext()) {
                    StructureTemplate.StructureBlockInfo definedstructure_blockinfo = (StructureTemplate.StructureBlockInfo) iterator.next();
                    BlockPos blockposition2 = definedstructure_blockinfo.pos;

                    if (structureboundingbox == null || structureboundingbox.isInside(blockposition2)) {
                        FluidState fluid = placementData.shouldKeepLiquids() ? world.getFluidState(blockposition2) : null;
                        BlockState iblockdata = definedstructure_blockinfo.state.mirror(placementData.getMirror()).rotate(placementData.getRotation());

                        if (definedstructure_blockinfo.nbt != null) {
                            tileentity = world.getBlockEntity(blockposition2);
                            // Paper start - Fix NBT pieces overriding a block entity during worldgen deadlock
                            if (!(world instanceof net.minecraft.world.level.WorldGenLevel)) {
                                Clearable.tryClear(tileentity);
                            }
                            // Paper end
                            world.setBlock(blockposition2, Blocks.BARRIER.defaultBlockState(), 20);
                        }
                        // CraftBukkit start
                        if (structureTransformer != null) {
                            org.bukkit.craftbukkit.block.CraftBlockState craftBlockState = (org.bukkit.craftbukkit.block.CraftBlockState) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockposition2, iblockdata, null);
                            if (definedstructure_blockinfo.nbt != null && craftBlockState instanceof org.bukkit.craftbukkit.block.CraftBlockEntityState<?> entityState) {
                                entityState.loadData(definedstructure_blockinfo.nbt);
                                if (craftBlockState instanceof org.bukkit.craftbukkit.block.CraftLootable<?> craftLootable) {
                                    craftLootable.setSeed(random.nextLong());
                                }
                            }
                            craftBlockState = structureTransformer.transformCraftState(craftBlockState);
                            iblockdata = craftBlockState.getHandle();
                            definedstructure_blockinfo = new StructureTemplate.StructureBlockInfo(blockposition2, iblockdata, (craftBlockState instanceof org.bukkit.craftbukkit.block.CraftBlockEntityState<?> craftBlockEntityState ? craftBlockEntityState.getSnapshotNBT() : null));
                        }
                        // CraftBukkit end

                        if (world.setBlock(blockposition2, iblockdata, flags)) {
                            j = Math.min(j, blockposition2.getX());
                            k = Math.min(k, blockposition2.getY());
                            l = Math.min(l, blockposition2.getZ());
                            i1 = Math.max(i1, blockposition2.getX());
                            j1 = Math.max(j1, blockposition2.getY());
                            k1 = Math.max(k1, blockposition2.getZ());
                            list3.add(Pair.of(blockposition2, definedstructure_blockinfo.nbt));
                            if (definedstructure_blockinfo.nbt != null) {
                                tileentity = world.getBlockEntity(blockposition2);
                                if (tileentity != null) {
                                    if (wrappedAccess == world && tileentity instanceof RandomizableContainerBlockEntity) { // CraftBukkit - only process if don't have a transformer access (originalAccess == worldaccess)
                                        definedstructure_blockinfo.nbt.putLong("LootTableSeed", random.nextLong());
                                    }

                                    tileentity.load(definedstructure_blockinfo.nbt);
                                }
                            }

                            if (fluid != null) {
                                if (iblockdata.getFluidState().isSource()) {
                                    list2.add(blockposition2);
                                } else if (iblockdata.getBlock() instanceof LiquidBlockContainer) {
                                    ((LiquidBlockContainer) iblockdata.getBlock()).placeLiquid(world, blockposition2, iblockdata, fluid);
                                    if (!fluid.isSource()) {
                                        list1.add(blockposition2);
                                    }
                                }
                            }
                        }
                    }
                }

                boolean flag = true;
                Direction[] aenumdirection = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

                Iterator iterator1;
                int l1;
                BlockState iblockdata1;

                while (flag && !list1.isEmpty()) {
                    flag = false;
                    iterator1 = list1.iterator();

                    while (iterator1.hasNext()) {
                        BlockPos blockposition3 = (BlockPos) iterator1.next();
                        FluidState fluid1 = world.getFluidState(blockposition3);

                        for (l1 = 0; l1 < aenumdirection.length && !fluid1.isSource(); ++l1) {
                            BlockPos blockposition4 = blockposition3.relative(aenumdirection[l1]);
                            FluidState fluid2 = world.getFluidState(blockposition4);

                            if (fluid2.isSource() && !list2.contains(blockposition4)) {
                                fluid1 = fluid2;
                            }
                        }

                        if (fluid1.isSource()) {
                            iblockdata1 = world.getBlockState(blockposition3);
                            Block block = iblockdata1.getBlock();

                            if (block instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer) block).placeLiquid(world, blockposition3, iblockdata1, fluid1);
                                flag = true;
                                iterator1.remove();
                            }
                        }
                    }
                }

                if (j <= i1) {
                    if (!placementData.getKnownShape()) {
                        BitSetDiscreteVoxelShape voxelshapebitset = new BitSetDiscreteVoxelShape(i1 - j + 1, j1 - k + 1, k1 - l + 1);
                        int i2 = j;
                        int j2 = k;

                        l1 = l;
                        Iterator iterator2 = list3.iterator();

                        while (iterator2.hasNext()) {
                            Pair<BlockPos, CompoundTag> pair = (Pair) iterator2.next();
                            BlockPos blockposition5 = (BlockPos) pair.getFirst();

                            voxelshapebitset.fill(blockposition5.getX() - i2, blockposition5.getY() - j2, blockposition5.getZ() - l1);
                        }

                        StructureTemplate.updateShapeAtEdge(world, flags, voxelshapebitset, i2, j2, l1);
                    }

                    iterator1 = list3.iterator();

                    while (iterator1.hasNext()) {
                        Pair<BlockPos, CompoundTag> pair1 = (Pair) iterator1.next();
                        BlockPos blockposition6 = (BlockPos) pair1.getFirst();

                        if (!placementData.getKnownShape()) {
                            iblockdata1 = world.getBlockState(blockposition6);
                            BlockState iblockdata2 = Block.updateFromNeighbourShapes(iblockdata1, world, blockposition6);

                            if (iblockdata1 != iblockdata2) {
                                world.setBlock(blockposition6, iblockdata2, flags & -2 | 16);
                            }

                            world.blockUpdated(blockposition6, iblockdata2.getBlock());
                        }

                        if (pair1.getSecond() != null) {
                            tileentity = world.getBlockEntity(blockposition6);
                            if (tileentity != null) {
                                // Paper start - Fix NBT pieces overriding a block entity during worldgen deadlock
                                if (!(world instanceof net.minecraft.world.level.WorldGenLevel)) {
                                    tileentity.setChanged();
                                }
                                // Paper end
                            }
                        }
                    }
                }

                if (!placementData.isIgnoreEntities()) {
                    this.placeEntities(wrappedAccess, pos, placementData.getMirror(), placementData.getRotation(), placementData.getRotationPivot(), structureboundingbox, placementData.shouldFinalizeEntities()); // CraftBukkit
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static void updateShapeAtEdge(LevelAccessor world, int flags, DiscreteVoxelShape set, int startX, int startY, int startZ) {
        set.forAllFaces((enumdirection, i1, j1, k1) -> {
            BlockPos blockposition = new BlockPos(startX + i1, startY + j1, startZ + k1);
            BlockPos blockposition1 = blockposition.relative(enumdirection);
            BlockState iblockdata = world.getBlockState(blockposition);
            BlockState iblockdata1 = world.getBlockState(blockposition1);
            BlockState iblockdata2 = iblockdata.updateShape(enumdirection, iblockdata1, world, blockposition, blockposition1);

            if (iblockdata != iblockdata2) {
                world.setBlock(blockposition, iblockdata2, flags & -2);
            }

            BlockState iblockdata3 = iblockdata1.updateShape(enumdirection.getOpposite(), iblockdata2, world, blockposition1, blockposition);

            if (iblockdata1 != iblockdata3) {
                world.setBlock(blockposition1, iblockdata3, flags & -2);
            }

        });
    }

    public static List<StructureTemplate.StructureBlockInfo> processBlockInfos(ServerLevelAccessor world, BlockPos pos, BlockPos pivot, StructurePlaceSettings placementData, List<StructureTemplate.StructureBlockInfo> infos) {
        List<StructureTemplate.StructureBlockInfo> list1 = new ArrayList();
        List<StructureTemplate.StructureBlockInfo> list2 = new ArrayList();
        Iterator iterator = infos.iterator();

        while (iterator.hasNext()) {
            StructureTemplate.StructureBlockInfo definedstructure_blockinfo = (StructureTemplate.StructureBlockInfo) iterator.next();
            BlockPos blockposition2 = StructureTemplate.calculateRelativePosition(placementData, definedstructure_blockinfo.pos).offset(pos);
            StructureTemplate.StructureBlockInfo definedstructure_blockinfo1 = new StructureTemplate.StructureBlockInfo(blockposition2, definedstructure_blockinfo.state, definedstructure_blockinfo.nbt != null ? definedstructure_blockinfo.nbt.copy() : null);

            for (Iterator iterator1 = placementData.getProcessors().iterator(); definedstructure_blockinfo1 != null && iterator1.hasNext(); definedstructure_blockinfo1 = ((StructureProcessor) iterator1.next()).processBlock(world, pos, pivot, definedstructure_blockinfo, definedstructure_blockinfo1, placementData)) {
                ;
            }

            if (definedstructure_blockinfo1 != null) {
                ((List) list2).add(definedstructure_blockinfo1);
                list1.add(definedstructure_blockinfo);
            }
        }

        StructureProcessor definedstructureprocessor;

        for (iterator = placementData.getProcessors().iterator(); iterator.hasNext(); list2 = definedstructureprocessor.finalizeProcessing(world, pos, pivot, list1, (List) list2, placementData)) {
            definedstructureprocessor = (StructureProcessor) iterator.next();
        }

        return (List) list2;
    }

    private void placeEntities(ServerLevelAccessor world, BlockPos pos, Mirror mirror, Rotation rotation, BlockPos pivot, @Nullable BoundingBox area, boolean initializeMobs) {
        Iterator iterator = this.entityInfoList.iterator();

        while (iterator.hasNext()) {
            StructureTemplate.StructureEntityInfo definedstructure_entityinfo = (StructureTemplate.StructureEntityInfo) iterator.next();
            BlockPos blockposition2 = StructureTemplate.transform(definedstructure_entityinfo.blockPos, mirror, rotation, pivot).offset(pos);

            if (area == null || area.isInside(blockposition2)) {
                CompoundTag nbttagcompound = definedstructure_entityinfo.nbt.copy();
                Vec3 vec3d = StructureTemplate.transform(definedstructure_entityinfo.pos, mirror, rotation, pivot);
                Vec3 vec3d1 = vec3d.add((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
                ListTag nbttaglist = new ListTag();

                nbttaglist.add(DoubleTag.valueOf(vec3d1.x));
                nbttaglist.add(DoubleTag.valueOf(vec3d1.y));
                nbttaglist.add(DoubleTag.valueOf(vec3d1.z));
                nbttagcompound.put("Pos", nbttaglist);
                nbttagcompound.remove("UUID");
                StructureTemplate.createEntityIgnoreException(world, nbttagcompound).ifPresent((entity) -> {
                    float f = entity.rotate(rotation);

                    f += entity.mirror(mirror) - entity.getYRot();
                    entity.moveTo(vec3d1.x, vec3d1.y, vec3d1.z, f, entity.getXRot());
                    if (initializeMobs && entity instanceof Mob) {
                        ((Mob) entity).finalizeSpawn(world, world.getCurrentDifficultyAt(BlockPos.containing(vec3d1)), MobSpawnType.STRUCTURE, (SpawnGroupData) null, nbttagcompound);
                    }

                    world.addFreshEntityWithPassengers(entity);
                });
            }
        }

    }

    private static Optional<Entity> createEntityIgnoreException(ServerLevelAccessor world, CompoundTag nbt) {
        // CraftBukkit start
        // try {
            return EntityType.create(nbt, world.getLevel());
        // } catch (Exception exception) {
            // return Optional.empty();
        // }
        // CraftBukkit end
    }

    public Vec3i getSize(Rotation rotation) {
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
            default:
                return this.size;
        }
    }

    public static BlockPos transform(BlockPos pos, Mirror mirror, Rotation rotation, BlockPos pivot) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        boolean flag = true;

        switch (mirror) {
            case LEFT_RIGHT:
                k = -k;
                break;
            case FRONT_BACK:
                i = -i;
                break;
            default:
                flag = false;
        }

        int l = pivot.getX();
        int i1 = pivot.getZ();

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPos(l - i1 + k, j, l + i1 - i);
            case CLOCKWISE_90:
                return new BlockPos(l + i1 - k, j, i1 - l + i);
            case CLOCKWISE_180:
                return new BlockPos(l + l - i, j, i1 + i1 - k);
            default:
                return flag ? new BlockPos(i, j, k) : pos;
        }
    }

    public static Vec3 transform(Vec3 point, Mirror mirror, Rotation rotation, BlockPos pivot) {
        double d0 = point.x;
        double d1 = point.y;
        double d2 = point.z;
        boolean flag = true;

        switch (mirror) {
            case LEFT_RIGHT:
                d2 = 1.0D - d2;
                break;
            case FRONT_BACK:
                d0 = 1.0D - d0;
                break;
            default:
                flag = false;
        }

        int i = pivot.getX();
        int j = pivot.getZ();

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3((double) (i - j) + d2, d1, (double) (i + j + 1) - d0);
            case CLOCKWISE_90:
                return new Vec3((double) (i + j + 1) - d2, d1, (double) (j - i) + d0);
            case CLOCKWISE_180:
                return new Vec3((double) (i + i + 1) - d0, d1, (double) (j + j + 1) - d2);
            default:
                return flag ? new Vec3(d0, d1, d2) : point;
        }
    }

    public BlockPos getZeroPositionWithTransform(BlockPos pos, Mirror mirror, Rotation rotation) {
        return StructureTemplate.getZeroPositionWithTransform(pos, mirror, rotation, this.getSize().getX(), this.getSize().getZ());
    }

    public static BlockPos getZeroPositionWithTransform(BlockPos pos, Mirror mirror, Rotation rotation, int offsetX, int offsetZ) {
        --offsetX;
        --offsetZ;
        int k = mirror == Mirror.FRONT_BACK ? offsetX : 0;
        int l = mirror == Mirror.LEFT_RIGHT ? offsetZ : 0;
        BlockPos blockposition1 = pos;

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                blockposition1 = pos.offset(l, 0, offsetX - k);
                break;
            case CLOCKWISE_90:
                blockposition1 = pos.offset(offsetZ - l, 0, k);
                break;
            case CLOCKWISE_180:
                blockposition1 = pos.offset(offsetX - k, 0, offsetZ - l);
                break;
            case NONE:
                blockposition1 = pos.offset(k, 0, l);
        }

        return blockposition1;
    }

    public BoundingBox getBoundingBox(StructurePlaceSettings placementData, BlockPos pos) {
        return this.getBoundingBox(pos, placementData.getRotation(), placementData.getRotationPivot(), placementData.getMirror());
    }

    public BoundingBox getBoundingBox(BlockPos pos, Rotation rotation, BlockPos pivot, Mirror mirror) {
        return StructureTemplate.getBoundingBox(pos, rotation, pivot, mirror, this.size);
    }

    @VisibleForTesting
    protected static BoundingBox getBoundingBox(BlockPos pos, Rotation rotation, BlockPos pivot, Mirror mirror, Vec3i dimensions) {
        Vec3i baseblockposition1 = dimensions.offset(-1, -1, -1);
        BlockPos blockposition2 = StructureTemplate.transform(BlockPos.ZERO, mirror, rotation, pivot);
        BlockPos blockposition3 = StructureTemplate.transform(BlockPos.ZERO.offset(baseblockposition1), mirror, rotation, pivot);

        return BoundingBox.fromCorners(blockposition2, blockposition3).move(pos);
    }

    public CompoundTag save(CompoundTag nbt) {
        if (this.palettes.isEmpty()) {
            nbt.put("blocks", new ListTag());
            nbt.put("palette", new ListTag());
        } else {
            List<StructureTemplate.SimplePalette> list = Lists.newArrayList();
            StructureTemplate.SimplePalette definedstructure_b = new StructureTemplate.SimplePalette();

            list.add(definedstructure_b);

            for (int i = 1; i < this.palettes.size(); ++i) {
                list.add(new StructureTemplate.SimplePalette());
            }

            ListTag nbttaglist = new ListTag();
            List<StructureTemplate.StructureBlockInfo> list1 = ((StructureTemplate.Palette) this.palettes.get(0)).blocks();

            for (int j = 0; j < list1.size(); ++j) {
                StructureTemplate.StructureBlockInfo definedstructure_blockinfo = (StructureTemplate.StructureBlockInfo) list1.get(j);
                CompoundTag nbttagcompound1 = new CompoundTag();

                nbttagcompound1.put("pos", this.newIntegerList(definedstructure_blockinfo.pos.getX(), definedstructure_blockinfo.pos.getY(), definedstructure_blockinfo.pos.getZ()));
                int k = definedstructure_b.idFor(definedstructure_blockinfo.state);

                nbttagcompound1.putInt("state", k);
                if (definedstructure_blockinfo.nbt != null) {
                    nbttagcompound1.put("nbt", definedstructure_blockinfo.nbt);
                }

                nbttaglist.add(nbttagcompound1);

                for (int l = 1; l < this.palettes.size(); ++l) {
                    StructureTemplate.SimplePalette definedstructure_b1 = (StructureTemplate.SimplePalette) list.get(l);

                    definedstructure_b1.addMapping(((StructureTemplate.StructureBlockInfo) ((StructureTemplate.Palette) this.palettes.get(l)).blocks().get(j)).state, k);
                }
            }

            nbt.put("blocks", nbttaglist);
            ListTag nbttaglist1;
            Iterator iterator;

            if (list.size() == 1) {
                nbttaglist1 = new ListTag();
                iterator = definedstructure_b.iterator();

                while (iterator.hasNext()) {
                    BlockState iblockdata = (BlockState) iterator.next();

                    nbttaglist1.add(NbtUtils.writeBlockState(iblockdata));
                }

                nbt.put("palette", nbttaglist1);
            } else {
                nbttaglist1 = new ListTag();
                iterator = list.iterator();

                while (iterator.hasNext()) {
                    StructureTemplate.SimplePalette definedstructure_b2 = (StructureTemplate.SimplePalette) iterator.next();
                    ListTag nbttaglist2 = new ListTag();
                    Iterator iterator1 = definedstructure_b2.iterator();

                    while (iterator1.hasNext()) {
                        BlockState iblockdata1 = (BlockState) iterator1.next();

                        nbttaglist2.add(NbtUtils.writeBlockState(iblockdata1));
                    }

                    nbttaglist1.add(nbttaglist2);
                }

                nbt.put("palettes", nbttaglist1);
            }
        }

        ListTag nbttaglist3 = new ListTag();

        CompoundTag nbttagcompound2;

        for (Iterator iterator2 = this.entityInfoList.iterator(); iterator2.hasNext(); nbttaglist3.add(nbttagcompound2)) {
            StructureTemplate.StructureEntityInfo definedstructure_entityinfo = (StructureTemplate.StructureEntityInfo) iterator2.next();

            nbttagcompound2 = new CompoundTag();
            nbttagcompound2.put("pos", this.newDoubleList(definedstructure_entityinfo.pos.x, definedstructure_entityinfo.pos.y, definedstructure_entityinfo.pos.z));
            nbttagcompound2.put("blockPos", this.newIntegerList(definedstructure_entityinfo.blockPos.getX(), definedstructure_entityinfo.blockPos.getY(), definedstructure_entityinfo.blockPos.getZ()));
            if (definedstructure_entityinfo.nbt != null) {
                nbttagcompound2.put("nbt", definedstructure_entityinfo.nbt);
            }
        }

        nbt.put("entities", nbttaglist3);
        nbt.put("size", this.newIntegerList(this.size.getX(), this.size.getY(), this.size.getZ()));
        // CraftBukkit start - PDC
        if (!this.persistentDataContainer.isEmpty()) {
            nbt.put("BukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return NbtUtils.addCurrentDataVersion(nbt);
    }

    public void load(HolderGetter<Block> blockLookup, CompoundTag nbt) {
        this.palettes.clear();
        this.entityInfoList.clear();
        ListTag nbttaglist = nbt.getList("size", 3);

        this.size = new Vec3i(nbttaglist.getInt(0), nbttaglist.getInt(1), nbttaglist.getInt(2));
        ListTag nbttaglist1 = nbt.getList("blocks", 10);
        ListTag nbttaglist2;
        int i;

        if (nbt.contains("palettes", 9)) {
            nbttaglist2 = nbt.getList("palettes", 9);

            for (i = 0; i < nbttaglist2.size(); ++i) {
                this.loadPalette(blockLookup, nbttaglist2.getList(i), nbttaglist1);
            }
        } else {
            this.loadPalette(blockLookup, nbt.getList("palette", 10), nbttaglist1);
        }

        nbttaglist2 = nbt.getList("entities", 10);

        for (i = 0; i < nbttaglist2.size(); ++i) {
            CompoundTag nbttagcompound1 = nbttaglist2.getCompound(i);
            ListTag nbttaglist3 = nbttagcompound1.getList("pos", 6);
            Vec3 vec3d = new Vec3(nbttaglist3.getDouble(0), nbttaglist3.getDouble(1), nbttaglist3.getDouble(2));
            ListTag nbttaglist4 = nbttagcompound1.getList("blockPos", 3);
            BlockPos blockposition = new BlockPos(nbttaglist4.getInt(0), nbttaglist4.getInt(1), nbttaglist4.getInt(2));

            if (nbttagcompound1.contains("nbt")) {
                CompoundTag nbttagcompound2 = nbttagcompound1.getCompound("nbt");

                this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3d, blockposition, nbttagcompound2));
            }
        }

        // CraftBukkit start - PDC
        Tag base = nbt.get("BukkitValues");
        if (base instanceof CompoundTag) {
            this.persistentDataContainer.putAll((CompoundTag) base);
        }
        // CraftBukkit end
    }

    private void loadPalette(HolderGetter<Block> blockLookup, ListTag palette, ListTag blocks) {
        StructureTemplate.SimplePalette definedstructure_b = new StructureTemplate.SimplePalette();

        for (int i = 0; i < palette.size(); ++i) {
            definedstructure_b.addMapping(NbtUtils.readBlockState(blockLookup, palette.getCompound(i)), i);
        }

        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();

        for (int j = 0; j < blocks.size(); ++j) {
            CompoundTag nbttagcompound = blocks.getCompound(j);
            ListTag nbttaglist2 = nbttagcompound.getList("pos", 3);
            BlockPos blockposition = new BlockPos(nbttaglist2.getInt(0), nbttaglist2.getInt(1), nbttaglist2.getInt(2));
            BlockState iblockdata = definedstructure_b.stateFor(nbttagcompound.getInt("state"));
            CompoundTag nbttagcompound1;

            if (nbttagcompound.contains("nbt")) {
                nbttagcompound1 = nbttagcompound.getCompound("nbt");
            } else {
                nbttagcompound1 = null;
            }

            StructureTemplate.StructureBlockInfo definedstructure_blockinfo = new StructureTemplate.StructureBlockInfo(blockposition, iblockdata, nbttagcompound1);

            StructureTemplate.addToLists(definedstructure_blockinfo, list, list1, list2);
        }

        List<StructureTemplate.StructureBlockInfo> list3 = StructureTemplate.buildInfoList(list, list1, list2);

        this.palettes.add(new StructureTemplate.Palette(list3));
    }

    private ListTag newIntegerList(int... ints) {
        ListTag nbttaglist = new ListTag();
        int[] aint1 = ints;
        int i = ints.length;

        for (int j = 0; j < i; ++j) {
            int k = aint1[j];

            nbttaglist.add(IntTag.valueOf(k));
        }

        return nbttaglist;
    }

    private ListTag newDoubleList(double... doubles) {
        ListTag nbttaglist = new ListTag();
        double[] adouble1 = doubles;
        int i = doubles.length;

        for (int j = 0; j < i; ++j) {
            double d0 = adouble1[j];

            nbttaglist.add(DoubleTag.valueOf(d0));
        }

        return nbttaglist;
    }

    public static record StructureBlockInfo(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {

        public String toString() {
            return String.format(Locale.ROOT, "<StructureBlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
        }
    }

    public static final class Palette {

        private final List<StructureTemplate.StructureBlockInfo> blocks;
        private final Map<Block, List<StructureTemplate.StructureBlockInfo>> cache = Maps.newConcurrentMap(); // Paper

        Palette(List<StructureTemplate.StructureBlockInfo> infos) {
            this.blocks = infos;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks() {
            return this.blocks;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks(Block block) {
            return (List) this.cache.computeIfAbsent(block, (block1) -> {
                return (List) this.blocks.stream().filter((definedstructure_blockinfo) -> {
                    return definedstructure_blockinfo.state.is(block1);
                }).collect(Collectors.toList());
            });
        }
    }

    public static class StructureEntityInfo {

        public final Vec3 pos;
        public final BlockPos blockPos;
        public final CompoundTag nbt;

        public StructureEntityInfo(Vec3 pos, BlockPos blockPos, CompoundTag nbt) {
            this.pos = pos;
            this.blockPos = blockPos;
            this.nbt = nbt;
        }
    }

    private static class SimplePalette implements Iterable<BlockState> {

        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final IdMapper<BlockState> ids = new IdMapper<>(16);
        private int lastId;

        SimplePalette() {}

        public int idFor(BlockState state) {
            int i = this.ids.getId(state);

            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(state, i);
            }

            return i;
        }

        @Nullable
        public BlockState stateFor(int id) {
            BlockState iblockdata = (BlockState) this.ids.byId(id);

            return iblockdata == null ? SimplePalette.DEFAULT_BLOCK_STATE : iblockdata; // CraftBukkit - decompile error
        }

        public Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState state, int id) {
            this.ids.addMapping(state, id);
        }
    }
}
