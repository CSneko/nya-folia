package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.slf4j.Logger;

public class ChunkSerializer {
    // Paper start
    // TODO: Check on update
    public static long getLastWorldSaveTime(CompoundTag chunkData) {
        final int dataVersion = ChunkStorage.getVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompound("Level");
            return levelData.getLong("LastUpdate");
        } else {
            return chunkData.getLong("LastUpdate");
        }
    }
    // Paper end

    public static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState(), null); // Paper - Anti-Xray - Add preset block states
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    // Paper start - replace light engine impl
    private static final int STARLIGHT_LIGHT_VERSION = 9;

    private static final String BLOCKLIGHT_STATE_TAG = "starlight.blocklight_state";
    private static final String SKYLIGHT_STATE_TAG = "starlight.skylight_state";
    private static final String STARLIGHT_VERSION_TAG = "starlight.light_version";
    // Paper end - replace light engine impl

    public ChunkSerializer() {}

    // Paper start - guard against serializing mismatching coordinates
    // TODO Note: This needs to be re-checked each update
    public static ChunkPos getChunkCoordinate(CompoundTag chunkData) {
        final int dataVersion = ChunkStorage.getVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompound("Level");
            return new ChunkPos(levelData.getInt("xPos"), levelData.getInt("zPos"));
        } else {
            return new ChunkPos(chunkData.getInt("xPos"), chunkData.getInt("zPos"));
        }
    }
    // Paper end
    // Paper start
    public static final class InProgressChunkHolder {

        public final ProtoChunk protoChunk;

        public CompoundTag poiData;

        public InProgressChunkHolder(final ProtoChunk protoChunk) {
            this.protoChunk = protoChunk;
        }
    }
    // Paper end

    public static ProtoChunk read(ServerLevel world, PoiManager poiStorage, ChunkPos chunkPos, CompoundTag nbt) {
        // Paper start - add variant for async calls
        InProgressChunkHolder holder = loadChunk(world, poiStorage, chunkPos, nbt, true);
        return holder.protoChunk;
    }
    // Paper start
    private static final int CURRENT_DATA_VERSION = net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    private static final boolean JUST_CORRUPT_IT = Boolean.getBoolean("Paper.ignoreWorldDataVersion");
    // Paper end

    public static InProgressChunkHolder loadChunk(ServerLevel world, PoiManager poiStorage, ChunkPos chunkPos, CompoundTag nbt, boolean distinguish) {
        // Paper start - Do NOT attempt to load chunks saved with newer versions
        if (nbt.contains("DataVersion", 99)) {
            int dataVersion = nbt.getInt("DataVersion");
            if (!JUST_CORRUPT_IT && dataVersion > CURRENT_DATA_VERSION) {
                new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! " + dataVersion + " > " + CURRENT_DATA_VERSION).printStackTrace();
                System.exit(1);
            }
        }
        // Paper end
        ChunkPos chunkcoordintpair1 = new ChunkPos(nbt.getInt("xPos"), nbt.getInt("zPos")); // Paper - diff on change, see ChunkSerializer#getChunkCoordinate

        if (!Objects.equals(chunkPos, chunkcoordintpair1)) {
            ChunkSerializer.LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", new Object[]{chunkPos, chunkPos, chunkcoordintpair1});
        }

        UpgradeData chunkconverter = nbt.contains("UpgradeData", 10) ? new UpgradeData(nbt.getCompound("UpgradeData"), world) : UpgradeData.EMPTY;
        boolean flag = getStatus(nbt) != null && getStatus(nbt).isOrAfter(ChunkStatus.LIGHT) && nbt.get("isLightOn") != null && nbt.getInt(STARLIGHT_VERSION_TAG) == STARLIGHT_LIGHT_VERSION; // Paper
        ListTag nbttaglist = nbt.getList("sections", 10);
        int i = world.getSectionsCount();
        LevelChunkSection[] achunksection = new LevelChunkSection[i];
        boolean flag1 = world.dimensionType().hasSkyLight();
        ServerChunkCache chunkproviderserver = world.getChunkSource();
        LevelLightEngine levellightengine = chunkproviderserver.getLightEngine();
        // Paper start
        ca.spottedleaf.starlight.common.light.SWMRNibbleArray[] blockNibbles = ca.spottedleaf.starlight.common.light.StarLightEngine.getFilledEmptyLight(world);
        ca.spottedleaf.starlight.common.light.SWMRNibbleArray[] skyNibbles = ca.spottedleaf.starlight.common.light.StarLightEngine.getFilledEmptyLight(world);
        final int minSection = io.papermc.paper.util.WorldUtil.getMinLightSection(world);
        final int maxSection = io.papermc.paper.util.WorldUtil.getMaxLightSection(world);
        boolean canReadSky = world.dimensionType().hasSkyLight();
        // Paper end
        Registry<Biome> iregistry = world.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainer<Holder<Biome>>> codec = ChunkSerializer.makeBiomeCodecRW(iregistry); // CraftBukkit - read/write
        boolean flag2 = false;

        DataResult dataresult;

        for (int j = 0; j < nbttaglist.size(); ++j) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompound(j); CompoundTag sectionData = nbttagcompound1; // Paper
            byte b0 = nbttagcompound1.getByte("Y");
            int k = world.getSectionIndexFromSectionY(b0);

            if (k >= 0 && k < achunksection.length) {
                Logger logger;
                PalettedContainer datapaletteblock;
                // Paper start - Anti-Xray - Add preset block states
                BlockState[] presetBlockStates = world.chunkPacketBlockController.getPresetBlockStates(world, chunkPos, b0);

                if (nbttagcompound1.contains("block_states", 10)) {
                    Codec<PalettedContainer<BlockState>> blockStateCodec = presetBlockStates == null ? ChunkSerializer.BLOCK_STATE_CODEC : PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState(), presetBlockStates);
                    dataresult = blockStateCodec.parse(NbtOps.INSTANCE, nbttagcompound1.getCompound("block_states")).promotePartial((s) -> {
                        ChunkSerializer.logErrors(chunkPos, b0, s);
                    });
                    logger = ChunkSerializer.LOGGER;
                    Objects.requireNonNull(logger);
                    datapaletteblock = (PalettedContainer) ((DataResult<PalettedContainer<BlockState>>) dataresult).getOrThrow(false, logger::error); // CraftBukkit - decompile error
                } else {
                    datapaletteblock = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES, presetBlockStates);
                    // Paper end
                }

                PalettedContainer object; // CraftBukkit - read/write

                if (nbttagcompound1.contains("biomes", 10)) {
                    dataresult = codec.parse(NbtOps.INSTANCE, nbttagcompound1.getCompound("biomes")).promotePartial((s) -> {
                        ChunkSerializer.logErrors(chunkPos, b0, s);
                    });
                    logger = ChunkSerializer.LOGGER;
                    Objects.requireNonNull(logger);
                    object = ((DataResult<PalettedContainer<Holder<Biome>>>) dataresult).getOrThrow(false, logger::error); // CraftBukkit - decompile error
                } else {
                    object = new PalettedContainer<>(iregistry.asHolderIdMap(), iregistry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES, null); // Paper - Anti-Xray - Add preset biomes
                }

                LevelChunkSection chunksection = new LevelChunkSection(datapaletteblock, (PalettedContainer) object); // CraftBukkit - read/write

                achunksection[k] = chunksection;
                SectionPos sectionposition = SectionPos.of(chunkPos, b0);

                // Paper - rewrite chunk system - moved to final load stage
            }

            boolean flag3 = nbttagcompound1.contains("BlockLight", 7);
            boolean flag4 = flag1 && nbttagcompound1.contains("SkyLight", 7);

            // Paper start - rewrite the light engine
            if (flag) {
                try {
                int y = sectionData.getByte("Y");
                // Paper end - rewrite the light engine
                if (flag3) {
                    // Paper start - rewrite the light engine
                    // this is where our diff is
                    blockNibbles[y - minSection] = new ca.spottedleaf.starlight.common.light.SWMRNibbleArray(sectionData.getByteArray("BlockLight").clone(), sectionData.getInt(BLOCKLIGHT_STATE_TAG)); // clone for data safety
                } else {
                    blockNibbles[y - minSection] = new ca.spottedleaf.starlight.common.light.SWMRNibbleArray(null, sectionData.getInt(BLOCKLIGHT_STATE_TAG));
                    // Paper end - rewrite the light engine
                }

                if (flag4) {
                    // Paper start - rewrite the light engine
                    // we store under the same key so mod programs editing nbt
                    // can still read the data, hopefully.
                    // however, for compatibility we store chunks as unlit so vanilla
                    // is forced to re-light them if it encounters our data. It's too much of a burden
                    // to try and maintain compatibility with a broken and inferior skylight management system.
                    skyNibbles[y - minSection] = new ca.spottedleaf.starlight.common.light.SWMRNibbleArray(sectionData.getByteArray("SkyLight").clone(), sectionData.getInt(SKYLIGHT_STATE_TAG)); // clone for data safety
                } else if (flag1) {
                    skyNibbles[y - minSection] = new ca.spottedleaf.starlight.common.light.SWMRNibbleArray(null, sectionData.getInt(SKYLIGHT_STATE_TAG));
                    // Paper end - rewrite the light engine
                }

                // Paper start - rewrite the light engine
                } catch (Exception ex) {
                    LOGGER.warn("Failed to load light data for chunk " + chunkPos + " in world '" + world.getWorld().getName() + "', light will be regenerated", ex);
                    flag = false;
                }
                // Paper end - rewrite light engine
            }
        }

        long l = nbt.getLong("InhabitedTime");
        ChunkStatus.ChunkType chunkstatus_type = ChunkSerializer.getChunkTypeFromTag(nbt);
        Logger logger1;
        BlendingData blendingdata;

        if (nbt.contains("blending_data", 10)) {
            dataresult = BlendingData.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.getCompound("blending_data")));
            logger1 = ChunkSerializer.LOGGER;
            Objects.requireNonNull(logger1);
            blendingdata = (BlendingData) ((DataResult<BlendingData>) dataresult).resultOrPartial(logger1::error).orElse(null); // CraftBukkit - decompile error
        } else {
            blendingdata = null;
        }

        Object object1;

        if (chunkstatus_type == ChunkStatus.ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelchunkticks = LevelChunkTicks.load(nbt.getList("block_ticks", 10), (s) -> {
                return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);
            LevelChunkTicks<Fluid> levelchunkticks1 = LevelChunkTicks.load(nbt.getList("fluid_ticks", 10), (s) -> {
                return BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);

            object1 = new LevelChunk(world.getLevel(), chunkPos, chunkconverter, levelchunkticks, levelchunkticks1, l, achunksection, ChunkSerializer.postLoadChunk(world, nbt), blendingdata);
            ((LevelChunk)object1).setBlockNibbles(blockNibbles); // Paper - replace light impl
            ((LevelChunk)object1).setSkyNibbles(skyNibbles); // Paper - replace light impl
        } else {
            ProtoChunkTicks<Block> protochunkticklist = ProtoChunkTicks.load(nbt.getList("block_ticks", 10), (s) -> {
                return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);
            ProtoChunkTicks<Fluid> protochunkticklist1 = ProtoChunkTicks.load(nbt.getList("fluid_ticks", 10), (s) -> {
                return BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);
            ProtoChunk protochunk = new ProtoChunk(chunkPos, chunkconverter, achunksection, protochunkticklist, protochunkticklist1, world, iregistry, blendingdata);
            protochunk.setBlockNibbles(blockNibbles); // Paper - replace light impl
            protochunk.setSkyNibbles(skyNibbles); // Paper - replace light impl

            object1 = protochunk;
            protochunk.setInhabitedTime(l);
            if (nbt.contains("below_zero_retrogen", 10)) {
                dataresult = BelowZeroRetrogen.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.getCompound("below_zero_retrogen")));
                logger1 = ChunkSerializer.LOGGER;
                Objects.requireNonNull(logger1);
                Optional<BelowZeroRetrogen> optional = ((DataResult<BelowZeroRetrogen>) dataresult).resultOrPartial(logger1::error); // CraftBukkit - decompile error

                Objects.requireNonNull(protochunk);
                optional.ifPresent(protochunk::setBelowZeroRetrogen);
            }

            ChunkStatus chunkstatus = ChunkStatus.byName(nbt.getString("Status"));

            protochunk.setStatus(chunkstatus);
            if (chunkstatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protochunk.setLightEngine(levellightengine);
            }
        }

        // CraftBukkit start - load chunk persistent data from nbt - SPIGOT-6814: Already load PDC here to account for 1.17 to 1.18 chunk upgrading.
        net.minecraft.nbt.Tag persistentBase = nbt.get("ChunkBukkitValues");
        if (persistentBase instanceof CompoundTag) {
            ((ChunkAccess) object1).persistentDataContainer.putAll((CompoundTag) persistentBase);
        }
        // CraftBukkit end

        ((ChunkAccess) object1).setLightCorrect(flag);
        CompoundTag nbttagcompound2 = nbt.getCompound("Heightmaps");
        EnumSet<Heightmap.Types> enumset = EnumSet.noneOf(Heightmap.Types.class);
        Iterator iterator = ((ChunkAccess) object1).getStatus().heightmapsAfter().iterator();

        while (iterator.hasNext()) {
            Heightmap.Types heightmap_type = (Heightmap.Types) iterator.next();
            String s = heightmap_type.getSerializationKey();

            if (nbttagcompound2.contains(s, 12)) {
                ((ChunkAccess) object1).setHeightmap(heightmap_type, nbttagcompound2.getLongArray(s));
            } else {
                enumset.add(heightmap_type);
            }
        }

        Heightmap.primeHeightmaps((ChunkAccess) object1, enumset);
        CompoundTag nbttagcompound3 = nbt.getCompound("structures");

        ((ChunkAccess) object1).setAllStarts(ChunkSerializer.unpackStructureStart(StructurePieceSerializationContext.fromLevel(world), nbttagcompound3, world.getSeed()));
        ((ChunkAccess) object1).setAllReferences(ChunkSerializer.unpackStructureReferences(world.registryAccess(), chunkPos, nbttagcompound3));
        if (nbt.getBoolean("shouldSave")) {
            ((ChunkAccess) object1).setUnsaved(true);
        }

        ListTag nbttaglist1 = nbt.getList("PostProcessing", 9);

        ListTag nbttaglist2;
        int i1;

        for (int j1 = 0; j1 < nbttaglist1.size(); ++j1) {
            nbttaglist2 = nbttaglist1.getList(j1);

            for (i1 = 0; i1 < nbttaglist2.size(); ++i1) {
                ((ChunkAccess) object1).addPackedPostProcess(nbttaglist2.getShort(i1), j1);
            }
        }

        if (chunkstatus_type == ChunkStatus.ChunkType.LEVELCHUNK) {
            return new InProgressChunkHolder(new ImposterProtoChunk((LevelChunk) object1, false)); // Paper - Async chunk loading
        } else {
            ProtoChunk protochunk1 = (ProtoChunk) object1;

            nbttaglist2 = nbt.getList("entities", 10);

            for (i1 = 0; i1 < nbttaglist2.size(); ++i1) {
                protochunk1.addEntity(nbttaglist2.getCompound(i1));
            }

            ListTag nbttaglist3 = nbt.getList("block_entities", 10);

            for (int k1 = 0; k1 < nbttaglist3.size(); ++k1) {
                CompoundTag nbttagcompound4 = nbttaglist3.getCompound(k1);

                // Paper start - do not read tile entities positioned outside the chunk
                BlockPos blockposition = BlockEntity.getPosFromTag(nbttagcompound4);
                if ((blockposition.getX() >> 4) != chunkPos.x || (blockposition.getZ() >> 4) != chunkPos.z) {
                    LOGGER.warn("Tile entity serialized in chunk " + chunkPos + " in world '" + world.getWorld().getName() + "' positioned at " + blockposition + " is located outside of the chunk");
                    continue;
                }
                // Paper end - do not read tile entities positioned outside the chunk
                ((ChunkAccess) object1).setBlockEntityNbt(nbttagcompound4);
            }

            CompoundTag nbttagcompound5 = nbt.getCompound("CarvingMasks");
            Iterator iterator1 = nbttagcompound5.getAllKeys().iterator();

            while (iterator1.hasNext()) {
                String s1 = (String) iterator1.next();
                GenerationStep.Carving worldgenstage_features = GenerationStep.Carving.valueOf(s1);

                protochunk1.setCarvingMask(worldgenstage_features, new CarvingMask(nbttagcompound5.getLongArray(s1), ((ChunkAccess) object1).getMinBuildHeight()));
            }

            return new InProgressChunkHolder(protochunk1); // Paper - Async chunk loading
        }
    }

    // Paper start - async chunk save for unload
    public record AsyncSaveData(
        Tag blockTickList, // non-null if we had to go to the server's tick list
        Tag fluidTickList, // non-null if we had to go to the server's tick list
        ListTag blockEntities,
        long worldTime
    ) {}

    // must be called sync
    public static AsyncSaveData getAsyncSaveData(ServerLevel world, ChunkAccess chunk) {
        org.spigotmc.AsyncCatcher.catchOp("preparation of chunk data for async save");

        final CompoundTag tickLists = new CompoundTag();
        ChunkSerializer.saveTicks(world, tickLists, chunk.getTicksForSerialization());

        ListTag blockEntitiesSerialized = new ListTag();
        for (final BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            final CompoundTag blockEntityNbt = chunk.getBlockEntityNbtForSaving(blockPos);
            if (blockEntityNbt != null) {
                blockEntitiesSerialized.add(blockEntityNbt);
            }
        }

        return new AsyncSaveData(
            tickLists.get(BLOCK_TICKS_TAG),
            tickLists.get(FLUID_TICKS_TAG),
            blockEntitiesSerialized,
            world.getGameTime()
        );
    }
    // Paper end

    private static void logErrors(ChunkPos chunkPos, int y, String message) {
        ChunkSerializer.LOGGER.error("Recoverable errors when loading section [" + chunkPos.x + ", " + y + ", " + chunkPos.z + "]: " + message);
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getHolderOrThrow(Biomes.PLAINS));
    }

    // CraftBukkit start - read/write
    private static Codec<PalettedContainer<Holder<Biome>>> makeBiomeCodecRW(Registry<Biome> iregistry) {
        return PalettedContainer.codecRW(iregistry.asHolderIdMap(), iregistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, iregistry.getHolderOrThrow(Biomes.PLAINS), null); // Paper - Anti-Xray - Add preset biomes
    }
    // CraftBukkit end

    public static CompoundTag write(ServerLevel world, ChunkAccess chunk) {
        // Paper start
        return saveChunk(world, chunk, null);
    }
    public static CompoundTag saveChunk(ServerLevel world, ChunkAccess chunk, @org.checkerframework.checker.nullness.qual.Nullable AsyncSaveData asyncsavedata) {
        // Paper end
        // Paper start - rewrite light impl
        final int minSection = io.papermc.paper.util.WorldUtil.getMinLightSection(world);
        final int maxSection = io.papermc.paper.util.WorldUtil.getMaxLightSection(world);
        ca.spottedleaf.starlight.common.light.SWMRNibbleArray[] blockNibbles = chunk.getBlockNibbles();
        ca.spottedleaf.starlight.common.light.SWMRNibbleArray[] skyNibbles = chunk.getSkyNibbles();
        // Paper end - rewrite light impl
        ChunkPos chunkcoordintpair = chunk.getPos();
        CompoundTag nbttagcompound = NbtUtils.addCurrentDataVersion(new CompoundTag());

        nbttagcompound.putInt("xPos", chunkcoordintpair.x);
        nbttagcompound.putInt("yPos", chunk.getMinSection());
        nbttagcompound.putInt("zPos", chunkcoordintpair.z);
        nbttagcompound.putLong("LastUpdate", asyncsavedata != null ? asyncsavedata.worldTime : world.getGameTime()); // Paper - async chunk unloading // Paper - diff on change
        nbttagcompound.putLong("InhabitedTime", chunk.getInhabitedTime());
        nbttagcompound.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getStatus()).toString());
        BlendingData blendingdata = chunk.getBlendingData();
        DataResult<Tag> dataresult; // CraftBukkit - decompile error
        Logger logger;

        if (blendingdata != null) {
            dataresult = BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingdata);
            logger = ChunkSerializer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbttagcompound.put("blending_data", nbtbase);
            });
        }

        BelowZeroRetrogen belowzeroretrogen = chunk.getBelowZeroRetrogen();

        if (belowzeroretrogen != null) {
            dataresult = BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowzeroretrogen);
            logger = ChunkSerializer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbttagcompound.put("below_zero_retrogen", nbtbase);
            });
        }

        UpgradeData chunkconverter = chunk.getUpgradeData();

        if (!chunkconverter.isEmpty()) {
            nbttagcompound.put("UpgradeData", chunkconverter.write());
        }

        LevelChunkSection[] achunksection = chunk.getSections();
        ListTag nbttaglist = new ListTag();
        ThreadedLevelLightEngine lightenginethreaded = world.getChunkSource().getLightEngine();
        Registry<Biome> iregistry = world.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = ChunkSerializer.makeBiomeCodec(iregistry);
        boolean flag = chunk.isLightCorrect();

        for (int i = lightenginethreaded.getMinLightSection(); i < lightenginethreaded.getMaxLightSection(); ++i) {
            int j = chunk.getSectionIndexFromSectionY(i);
            boolean flag1 = j >= 0 && j < achunksection.length;
            // Paper - replace light engine

            // Paper start - replace light engine
            ca.spottedleaf.starlight.common.light.SWMRNibbleArray.SaveState blockNibble = blockNibbles[i - minSection].getSaveState();
            ca.spottedleaf.starlight.common.light.SWMRNibbleArray.SaveState skyNibble = skyNibbles[i - minSection].getSaveState();
            if (flag1 || blockNibble != null || skyNibble != null) {
                // Paper end - replace light engine
                CompoundTag nbttagcompound1 = new CompoundTag(); CompoundTag section = nbttagcompound1; // Paper

                if (flag1) {
                    LevelChunkSection chunksection = achunksection[j];
                    DataResult<Tag> dataresult1 = ChunkSerializer.BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, chunksection.getStates()); // CraftBukkit - decompile error
                    Logger logger1 = ChunkSerializer.LOGGER;

                    Objects.requireNonNull(logger1);
                    nbttagcompound1.put("block_states", (Tag) dataresult1.getOrThrow(false, logger1::error));
                    dataresult1 = codec.encodeStart(NbtOps.INSTANCE, chunksection.getBiomes());
                    logger1 = ChunkSerializer.LOGGER;
                    Objects.requireNonNull(logger1);
                    nbttagcompound1.put("biomes", (Tag) dataresult1.getOrThrow(false, logger1::error));
                }

                // Paper start
                // we store under the same key so mod programs editing nbt
                // can still read the data, hopefully.
                // however, for compatibility we store chunks as unlit so vanilla
                // is forced to re-light them if it encounters our data. It's too much of a burden
                // to try and maintain compatibility with a broken and inferior skylight management system.

                if (blockNibble != null) {
                    if (blockNibble.data != null) {
                        section.putByteArray("BlockLight", blockNibble.data);
                    }
                    section.putInt(BLOCKLIGHT_STATE_TAG, blockNibble.state);
                }

                if (skyNibble != null) {
                    if (skyNibble.data != null) {
                        section.putByteArray("SkyLight", skyNibble.data);
                    }
                    section.putInt(SKYLIGHT_STATE_TAG, skyNibble.state);
                }
                // Paper end

                if (!nbttagcompound1.isEmpty()) {
                    nbttagcompound1.putByte("Y", (byte) i);
                    nbttaglist.add(nbttagcompound1);
                }
            }
        }

        nbttagcompound.put("sections", nbttaglist);
        if (flag) {
            nbttagcompound.putInt(STARLIGHT_VERSION_TAG, STARLIGHT_LIGHT_VERSION); // Paper
            nbttagcompound.putBoolean("isLightOn", false); // Paper - set to false but still store, this allows us to detect --eraseCache (as eraseCache _removes_)
        }

        // Paper start
        ListTag nbttaglist1;
        Iterator<BlockPos> iterator;
        if (asyncsavedata != null) {
            nbttaglist1 = asyncsavedata.blockEntities;
            iterator = java.util.Collections.emptyIterator();
        } else {
            nbttaglist1 = new ListTag();
            iterator = chunk.getBlockEntitiesPos().iterator();
        }
        // Paper end

        CompoundTag nbttagcompound2;

        while (iterator.hasNext()) {
            BlockPos blockposition = (BlockPos) iterator.next();

            nbttagcompound2 = chunk.getBlockEntityNbtForSaving(blockposition);
            if (nbttagcompound2 != null) {
                nbttaglist1.add(nbttagcompound2);
            }
        }

        nbttagcompound.put("block_entities", nbttaglist1);
        if (chunk.getStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK) {
            ProtoChunk protochunk = (ProtoChunk) chunk;
            ListTag nbttaglist2 = new ListTag();

            nbttaglist2.addAll(protochunk.getEntities());
            nbttagcompound.put("entities", nbttaglist2);
            nbttagcompound2 = new CompoundTag();
            GenerationStep.Carving[] aworldgenstage_features = GenerationStep.Carving.values();
            int k = aworldgenstage_features.length;

            for (int l = 0; l < k; ++l) {
                GenerationStep.Carving worldgenstage_features = aworldgenstage_features[l];
                CarvingMask carvingmask = protochunk.getCarvingMask(worldgenstage_features);

                if (carvingmask != null) {
                    nbttagcompound2.putLongArray(worldgenstage_features.toString(), carvingmask.toArray());
                }
            }

            nbttagcompound.put("CarvingMasks", nbttagcompound2);
        }

        // Paper start
        if (asyncsavedata != null) {
            nbttagcompound.put(BLOCK_TICKS_TAG, asyncsavedata.blockTickList);
            nbttagcompound.put(FLUID_TICKS_TAG, asyncsavedata.fluidTickList);
        } else {
        ChunkSerializer.saveTicks(world, nbttagcompound, chunk.getTicksForSerialization());
        }
        // Paper end
        nbttagcompound.put("PostProcessing", ChunkSerializer.packOffsets(chunk.getPostProcessing()));
        CompoundTag nbttagcompound3 = new CompoundTag();
        Iterator iterator1 = chunk.getHeightmaps().iterator();

        while (iterator1.hasNext()) {
            Entry<Heightmap.Types, Heightmap> entry = (Entry) iterator1.next();

            if (chunk.getStatus().heightmapsAfter().contains(entry.getKey())) {
                nbttagcompound3.put(((Heightmap.Types) entry.getKey()).getSerializationKey(), new LongArrayTag(((Heightmap) entry.getValue()).getRawData()));
            }
        }

        nbttagcompound.put("Heightmaps", nbttagcompound3);
        nbttagcompound.put("structures", ChunkSerializer.packStructureData(StructurePieceSerializationContext.fromLevel(world), chunkcoordintpair, chunk.getAllStarts(), chunk.getAllReferences()));
        // CraftBukkit start - store chunk persistent data in nbt
        if (!chunk.persistentDataContainer.isEmpty()) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
            nbttagcompound.put("ChunkBukkitValues", chunk.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return nbttagcompound;
    }

    private static void saveTicks(ServerLevel world, CompoundTag nbt, ChunkAccess.TicksToSave tickSchedulers) {
        long i = world.getRedstoneGameTime(); // Folia - region threading

        nbt.put("block_ticks", tickSchedulers.blocks().save(i, (block) -> {
            return BuiltInRegistries.BLOCK.getKey(block).toString();
        }));
        nbt.put("fluid_ticks", tickSchedulers.fluids().save(i, (fluidtype) -> {
            return BuiltInRegistries.FLUID.getKey(fluidtype).toString();
        }));
    }

    // Paper start
    public static @Nullable ChunkStatus getStatus(@Nullable CompoundTag compound) {
        if (compound == null) {
            return null;
        }

        // Note: Copied from below
        return ChunkStatus.getStatus(compound.getString("Status"));
    }
    // Paper end

    public static ChunkStatus.ChunkType getChunkTypeFromTag(@Nullable CompoundTag nbt) {
        return nbt != null ? ChunkStatus.byName(nbt.getString("Status")).getChunkType() : ChunkStatus.ChunkType.PROTOCHUNK;
    }

    @Nullable
    private static LevelChunk.PostLoadProcessor postLoadChunk(ServerLevel world, CompoundTag nbt) {
        ListTag nbttaglist = ChunkSerializer.getListOfCompoundsOrNull(nbt, "entities");
        ListTag nbttaglist1 = ChunkSerializer.getListOfCompoundsOrNull(nbt, "block_entities");

        return nbttaglist == null && nbttaglist1 == null ? null : (chunk) -> {
            if (nbttaglist != null) {
                world.addLegacyChunkEntities(EntityType.loadEntitiesRecursive(nbttaglist, world), chunk.getPos()); // Paper - rewrite chunk system
            }

            if (nbttaglist1 != null) {
                for (int i = 0; i < nbttaglist1.size(); ++i) {
                    CompoundTag nbttagcompound1 = nbttaglist1.getCompound(i);
                    boolean flag = nbttagcompound1.getBoolean("keepPacked");

                    // Paper start - do not read tile entities positioned outside the chunk
                    BlockPos blockposition = BlockEntity.getPosFromTag(nbttagcompound1); //  moved up
                    ChunkPos chunkPos = chunk.getPos();
                    if ((blockposition.getX() >> 4) != chunkPos.x || (blockposition.getZ() >> 4) != chunkPos.z) {
                        LOGGER.warn("Tile entity serialized in chunk " + chunkPos + " in world '" + world.getWorld().getName() + "' positioned at " + blockposition + " is located outside of the chunk");
                        continue;
                    }
                    // Paper end - do not read tile entities positioned outside the chunk

                    if (flag) {
                        chunk.setBlockEntityNbt(nbttagcompound1);
                    } else {
                        // Paper - do not read tile entities positioned outside the chunk - move up
                        BlockEntity tileentity = BlockEntity.loadStatic(blockposition, chunk.getBlockState(blockposition), nbttagcompound1);

                        if (tileentity != null) {
                            chunk.setBlockEntity(tileentity);
                        }
                    }
                }
            }

        };
    }

    @Nullable
    private static ListTag getListOfCompoundsOrNull(CompoundTag nbt, String key) {
        ListTag nbttaglist = nbt.getList(key, 10);

        return nbttaglist.isEmpty() ? null : nbttaglist;
    }

    private static CompoundTag packStructureData(StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> starts, Map<Structure, LongSet> references) {
        CompoundTag nbttagcompound = new CompoundTag();
        CompoundTag nbttagcompound1 = new CompoundTag();
        Registry<Structure> iregistry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Iterator iterator = starts.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Structure, StructureStart> entry = (Entry) iterator.next();
            ResourceLocation minecraftkey = iregistry.getKey((Structure) entry.getKey());

            nbttagcompound1.put(minecraftkey.toString(), ((StructureStart) entry.getValue()).createTag(context, pos));
        }

        nbttagcompound.put("starts", nbttagcompound1);
        CompoundTag nbttagcompound2 = new CompoundTag();
        Iterator iterator1 = references.entrySet().iterator();

        while (iterator1.hasNext()) {
            Entry<Structure, LongSet> entry1 = (Entry) iterator1.next();

            if (!((LongSet) entry1.getValue()).isEmpty()) {
                ResourceLocation minecraftkey1 = iregistry.getKey((Structure) entry1.getKey());

                nbttagcompound2.put(minecraftkey1.toString(), new LongArrayTag((LongSet) entry1.getValue()));
            }
        }

        nbttagcompound.put("References", nbttagcompound2);
        return nbttagcompound;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext context, CompoundTag nbt, long worldSeed) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> iregistry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        CompoundTag nbttagcompound1 = nbt.getCompound("starts");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            ResourceLocation minecraftkey = ResourceLocation.tryParse(s);
            Structure structure = (Structure) iregistry.get(minecraftkey);

            if (structure == null) {
                ChunkSerializer.LOGGER.error("Unknown structure start: {}", minecraftkey);
            } else {
                StructureStart structurestart = StructureStart.loadStaticStart(context, nbttagcompound1.getCompound(s), worldSeed);

                if (structurestart != null) {
                    map.put(structure, structurestart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess registryManager, ChunkPos pos, CompoundTag nbt) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> iregistry = registryManager.registryOrThrow(Registries.STRUCTURE);
        CompoundTag nbttagcompound1 = nbt.getCompound("References");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            ResourceLocation minecraftkey = ResourceLocation.tryParse(s);
            Structure structure = (Structure) iregistry.get(minecraftkey);

            if (structure == null) {
                ChunkSerializer.LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", minecraftkey, pos);
            } else {
                long[] along = nbttagcompound1.getLongArray(s);

                if (along.length != 0) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream(along).filter((i) -> {
                        ChunkPos chunkcoordintpair1 = new ChunkPos(i);

                        if (chunkcoordintpair1.getChessboardDistance(pos) > 8) {
                            ChunkSerializer.LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", new Object[]{minecraftkey, chunkcoordintpair1, pos});
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        }

        return map;
    }

    public static ListTag packOffsets(ShortList[] lists) {
        ListTag nbttaglist = new ListTag();
        ShortList[] ashortlist1 = lists;
        int i = lists.length;

        for (int j = 0; j < i; ++j) {
            ShortList shortlist = ashortlist1[j];
            ListTag nbttaglist1 = new ListTag();

            if (shortlist != null) {
                ShortListIterator shortlistiterator = shortlist.iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();

                    nbttaglist1.add(ShortTag.valueOf(oshort));
                }
            }

            nbttaglist.add(nbttaglist1);
        }

        return nbttaglist;
    }
}
