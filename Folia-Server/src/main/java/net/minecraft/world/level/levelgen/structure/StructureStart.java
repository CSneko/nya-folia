package net.minecraft.world.level.levelgen.structure;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.slf4j.Logger;

public final class StructureStart {

    public static final String INVALID_START_ID = "INVALID";
    public static final StructureStart INVALID_START = new StructureStart((Structure) null, new ChunkPos(0, 0), 0, new PiecesContainer(List.of()));
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Structure structure;
    private final PiecesContainer pieceContainer;
    private final ChunkPos chunkPos;
    private final java.util.concurrent.atomic.AtomicInteger references; // Folia - region threading
    @Nullable
    private volatile BoundingBox cachedBoundingBox;
    public org.bukkit.event.world.AsyncStructureGenerateEvent.Cause generationEventCause = org.bukkit.event.world.AsyncStructureGenerateEvent.Cause.WORLD_GENERATION; // CraftBukkit

    public StructureStart(Structure structure, ChunkPos pos, int references, PiecesContainer children) {
        this.structure = structure;
        this.chunkPos = pos;
        this.references = new java.util.concurrent.atomic.AtomicInteger(references); // Folia - region threading
        this.pieceContainer = children;
    }

    @Nullable
    public static StructureStart loadStaticStart(StructurePieceSerializationContext context, CompoundTag nbt, long seed) {
        String s = nbt.getString("id");

        if ("INVALID".equals(s)) {
            return StructureStart.INVALID_START;
        } else {
            Registry<Structure> iregistry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure structure = (Structure) iregistry.get(new ResourceLocation(s));

            if (structure == null) {
                StructureStart.LOGGER.error("Unknown stucture id: {}", s);
                return null;
            } else {
                ChunkPos chunkcoordintpair = new ChunkPos(nbt.getInt("ChunkX"), nbt.getInt("ChunkZ"));
                int j = nbt.getInt("references");
                ListTag nbttaglist = nbt.getList("Children", 10);

                try {
                    PiecesContainer piecescontainer = PiecesContainer.load(nbttaglist, context);

                    if (structure instanceof OceanMonumentStructure) {
                        piecescontainer = OceanMonumentStructure.regeneratePiecesAfterLoad(chunkcoordintpair, seed, piecescontainer);
                    }

                    return new StructureStart(structure, chunkcoordintpair, j, piecescontainer);
                } catch (Exception exception) {
                    StructureStart.LOGGER.error("Failed Start with id {}", s, exception);
                    return null;
                }
            }
        }
    }

    public BoundingBox getBoundingBox() {
        BoundingBox structureboundingbox = this.cachedBoundingBox;

        if (structureboundingbox == null) {
            structureboundingbox = this.structure.adjustBoundingBox(this.pieceContainer.calculateBoundingBox());
            this.cachedBoundingBox = structureboundingbox;
        }

        return structureboundingbox;
    }

    public void placeInChunk(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos) {
        List<StructurePiece> list = this.pieceContainer.pieces();

        if (!list.isEmpty()) {
            BoundingBox structureboundingbox1 = ((StructurePiece) list.get(0)).boundingBox;
            BlockPos blockposition = structureboundingbox1.getCenter();
            BlockPos blockposition1 = new BlockPos(blockposition.getX(), structureboundingbox1.minY(), blockposition.getZ());
            // CraftBukkit start
            /*
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                StructurePiece structurepiece = (StructurePiece) iterator.next();

                if (structurepiece.getBoundingBox().intersects(structureboundingbox)) {
                    structurepiece.postProcess(generatoraccessseed, structuremanager, chunkgenerator, randomsource, structureboundingbox, chunkcoordintpair, blockposition1);
                }
            }
            */
            List<StructurePiece> pieces = list.stream().filter(piece -> piece.getBoundingBox().intersects(chunkBox)).toList();
            if (!pieces.isEmpty()) {
                org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess = new org.bukkit.craftbukkit.util.TransformerGeneratorAccess();
                transformerAccess.setHandle(world);
                transformerAccess.setStructureTransformer(new org.bukkit.craftbukkit.util.CraftStructureTransformer(this.generationEventCause, world, structureAccessor, this.structure, chunkBox, chunkPos));
                for (StructurePiece piece : pieces) {
                    piece.postProcess(transformerAccess, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, blockposition1);
                }
                transformerAccess.getStructureTransformer().discard();
            }
            // CraftBukkit end

            this.structure.afterPlace(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, this.pieceContainer);
        }
    }

    public CompoundTag createTag(StructurePieceSerializationContext context, ChunkPos chunkPos) {
        CompoundTag nbttagcompound = new CompoundTag();

        if (this.isValid()) {
            nbttagcompound.putString("id", context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(this.structure).toString());
            nbttagcompound.putInt("ChunkX", chunkPos.x);
            nbttagcompound.putInt("ChunkZ", chunkPos.z);
            nbttagcompound.putInt("references", this.references.get()); // Folia - region threading
            nbttagcompound.put("Children", this.pieceContainer.save(context));
            return nbttagcompound;
        } else {
            nbttagcompound.putString("id", "INVALID");
            return nbttagcompound;
        }
    }

    public boolean isValid() {
        return !this.pieceContainer.isEmpty();
    }

    public ChunkPos getChunkPos() {
        return this.chunkPos;
    }

    public boolean canBeReferenced() {
        throw new UnsupportedOperationException("Use tryReference()"); // Folia - region threading
    }

    // Folia start - region threading
    public boolean tryReference() {
        for (int curr = this.references.get();;) {
            if (curr >= this.getMaxReferences()) {
                return false;
            }

            if (curr == (curr = this.references.compareAndExchange(curr, curr + 1))) {
                return true;
            } // else: try again
        }
    }
    // Folia end - region threading

    public void addReference() {
       throw new UnsupportedOperationException("Use tryReference()"); // Folia - region threading
    }

    public int getReferences() {
        return this.references.get(); // Folia - region threading
    }

    protected int getMaxReferences() {
        return 1;
    }

    public Structure getStructure() {
        return this.structure;
    }

    public List<StructurePiece> getPieces() {
        return this.pieceContainer.pieces();
    }
}
