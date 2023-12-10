package net.minecraft.data.structures;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public class StructureUpdater implements SnbtToNbt.Filter {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public CompoundTag apply(String name, CompoundTag nbt) {
        return name.startsWith("data/minecraft/structures/") ? update(name, nbt) : nbt;
    }

    public static CompoundTag update(String name, CompoundTag nbt) {
        StructureTemplate structureTemplate = new StructureTemplate();
        int i = NbtUtils.getDataVersion(nbt, 500);
        int j = 3563;
        if (i < 3563) {
            LOGGER.warn("SNBT Too old, do not forget to update: {} < {}: {}", i, 3563, name);
        }

        CompoundTag compoundTag = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.STRUCTURE, nbt, i, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()); // Paper
        structureTemplate.load(BuiltInRegistries.BLOCK.asLookup(), compoundTag);
        return structureTemplate.save(new CompoundTag());
    }
}
