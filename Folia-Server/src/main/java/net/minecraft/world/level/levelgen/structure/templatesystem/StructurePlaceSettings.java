package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class StructurePlaceSettings {

    private Mirror mirror;
    private Rotation rotation;
    private BlockPos rotationPivot;
    private boolean ignoreEntities;
    @Nullable
    private BoundingBox boundingBox;
    private boolean keepLiquids;
    @Nullable
    private RandomSource random;
    public int palette = -1; // CraftBukkit - Set initial value so we know if the palette has been set forcefully
    private final List<StructureProcessor> processors;
    private boolean knownShape;
    private boolean finalizeEntities;

    public StructurePlaceSettings() {
        this.mirror = Mirror.NONE;
        this.rotation = Rotation.NONE;
        this.rotationPivot = BlockPos.ZERO;
        this.keepLiquids = true;
        this.processors = Lists.newArrayList();
    }

    public StructurePlaceSettings copy() {
        StructurePlaceSettings definedstructureinfo = new StructurePlaceSettings();

        definedstructureinfo.mirror = this.mirror;
        definedstructureinfo.rotation = this.rotation;
        definedstructureinfo.rotationPivot = this.rotationPivot;
        definedstructureinfo.ignoreEntities = this.ignoreEntities;
        definedstructureinfo.boundingBox = this.boundingBox;
        definedstructureinfo.keepLiquids = this.keepLiquids;
        definedstructureinfo.random = this.random;
        definedstructureinfo.palette = this.palette;
        definedstructureinfo.processors.addAll(this.processors);
        definedstructureinfo.knownShape = this.knownShape;
        definedstructureinfo.finalizeEntities = this.finalizeEntities;
        return definedstructureinfo;
    }

    public StructurePlaceSettings setMirror(Mirror mirror) {
        this.mirror = mirror;
        return this;
    }

    public StructurePlaceSettings setRotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public StructurePlaceSettings setRotationPivot(BlockPos position) {
        this.rotationPivot = position;
        return this;
    }

    public StructurePlaceSettings setIgnoreEntities(boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
        return this;
    }

    public StructurePlaceSettings setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public StructurePlaceSettings setRandom(@Nullable RandomSource random) {
        this.random = random;
        return this;
    }

    public StructurePlaceSettings setKeepLiquids(boolean placeFluids) {
        this.keepLiquids = placeFluids;
        return this;
    }

    public StructurePlaceSettings setKnownShape(boolean updateNeighbors) {
        this.knownShape = updateNeighbors;
        return this;
    }

    public StructurePlaceSettings clearProcessors() {
        this.processors.clear();
        return this;
    }

    public StructurePlaceSettings addProcessor(StructureProcessor processor) {
        this.processors.add(processor);
        return this;
    }

    public StructurePlaceSettings popProcessor(StructureProcessor processor) {
        this.processors.remove(processor);
        return this;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public BlockPos getRotationPivot() {
        return this.rotationPivot;
    }

    public RandomSource getRandom(@Nullable BlockPos pos) {
        return this.random != null ? this.random : (pos == null ? RandomSource.create(Util.getMillis()) : RandomSource.create(Mth.getSeed(pos)));
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    @Nullable
    public BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public boolean getKnownShape() {
        return this.knownShape;
    }

    public List<StructureProcessor> getProcessors() {
        return this.processors;
    }

    public boolean shouldKeepLiquids() {
        return this.keepLiquids;
    }

    public StructureTemplate.Palette getRandomPalette(List<StructureTemplate.Palette> infoLists, @Nullable BlockPos pos) {
        int i = infoLists.size();

        if (i == 0) {
            throw new IllegalStateException("No palettes");
        // CraftBukkit start
        } else if (this.palette > 0) {
            if (this.palette >= i) {
                throw new IllegalArgumentException("Palette index out of bounds. Got " + this.palette + " where there are only " + i + " palettes available.");
            }
            return infoLists.get(this.palette);
        // CraftBukkit end
        } else {
            return (StructureTemplate.Palette) infoLists.get(this.getRandom(pos).nextInt(i));
        }
    }

    public StructurePlaceSettings setFinalizeEntities(boolean initializeMobs) {
        this.finalizeEntities = initializeMobs;
        return this;
    }

    public boolean shouldFinalizeEntities() {
        return this.finalizeEntities;
    }
}
