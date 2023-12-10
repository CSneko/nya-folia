package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

public final class NbtUtils {
    private static final Comparator<ListTag> YXZ_LISTTAG_INT_COMPARATOR = Comparator.<ListTag>comparingInt((nbt) -> {
        return nbt.getInt(1);
    }).thenComparingInt((nbt) -> {
        return nbt.getInt(0);
    }).thenComparingInt((nbt) -> {
        return nbt.getInt(2);
    });
    private static final Comparator<ListTag> YXZ_LISTTAG_DOUBLE_COMPARATOR = Comparator.<ListTag>comparingDouble((nbt) -> {
        return nbt.getDouble(1);
    }).thenComparingDouble((nbt) -> {
        return nbt.getDouble(0);
    }).thenComparingDouble((nbt) -> {
        return nbt.getDouble(2);
    });
    public static final String SNBT_DATA_TAG = "data";
    private static final char PROPERTIES_START = '{';
    private static final char PROPERTIES_END = '}';
    private static final String ELEMENT_SEPARATOR = ",";
    private static final char KEY_VALUE_SEPARATOR = ':';
    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INDENT = 2;
    private static final int NOT_FOUND = -1;

    private NbtUtils() {
    }

    @Nullable
    public static GameProfile readGameProfile(CompoundTag nbt) {
        UUID uUID = nbt.hasUUID("Id") ? nbt.getUUID("Id") : Util.NIL_UUID;
        // Paper start - support string UUID's
        if (nbt.contains("Id", Tag.TAG_STRING)) {
            uUID = UUID.fromString(nbt.getString("Id"));
        }
        // Paper end
        String string = nbt.getString("Name");

        try {
            GameProfile gameProfile = new GameProfile(uUID, string);
            if (nbt.contains("Properties", 10)) {
                CompoundTag compoundTag = nbt.getCompound("Properties");

                for(String string2 : compoundTag.getAllKeys()) {
                    ListTag listTag = compoundTag.getList(string2, 10);

                    for(int i = 0; i < listTag.size(); ++i) {
                        CompoundTag compoundTag2 = listTag.getCompound(i);
                        String string3 = compoundTag2.getString("Value");
                        if (compoundTag2.contains("Signature", 8)) {
                            gameProfile.getProperties().put(string2, new com.mojang.authlib.properties.Property(string2, string3, compoundTag2.getString("Signature")));
                        } else {
                            gameProfile.getProperties().put(string2, new com.mojang.authlib.properties.Property(string2, string3));
                        }
                    }
                }
            }

            return gameProfile;
        } catch (Throwable var11) {
            return null;
        }
    }

    public static CompoundTag writeGameProfile(CompoundTag nbt, GameProfile profile) {
        if (!profile.getName().isEmpty()) {
            nbt.putString("Name", profile.getName());
        }

        if (!profile.getId().equals(Util.NIL_UUID)) {
            nbt.putUUID("Id", profile.getId());
        }

        if (!profile.getProperties().isEmpty()) {
            CompoundTag compoundTag = new CompoundTag();

            for(String string : profile.getProperties().keySet()) {
                ListTag listTag = new ListTag();

                for(com.mojang.authlib.properties.Property property : profile.getProperties().get(string)) {
                    CompoundTag compoundTag2 = new CompoundTag();
                    compoundTag2.putString("Value", property.value());
                    String string2 = property.signature();
                    if (string2 != null) {
                        compoundTag2.putString("Signature", string2);
                    }

                    listTag.add(compoundTag2);
                }

                compoundTag.put(string, listTag);
            }

            nbt.put("Properties", compoundTag);
        }

        return nbt;
    }

    @VisibleForTesting
    public static boolean compareNbt(@Nullable Tag standard, @Nullable Tag subject, boolean ignoreListOrder) {
        if (standard == subject) {
            return true;
        } else if (standard == null) {
            return true;
        } else if (subject == null) {
            return false;
        } else if (!standard.getClass().equals(subject.getClass())) {
            return false;
        } else if (standard instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag)standard;
            CompoundTag compoundTag2 = (CompoundTag)subject;

            for(String string : compoundTag.getAllKeys()) {
                Tag tag = compoundTag.get(string);
                if (!compareNbt(tag, compoundTag2.get(string), ignoreListOrder)) {
                    return false;
                }
            }

            return true;
        } else {
            if (standard instanceof ListTag) {
                ListTag listTag = (ListTag)standard;
                if (ignoreListOrder) {
                    ListTag listTag2 = (ListTag)subject;
                    if (listTag.isEmpty()) {
                        return listTag2.isEmpty();
                    }

                    for(Tag tag2 : listTag) {
                        boolean bl = false;

                        for(Tag tag3 : listTag2) {
                            if (compareNbt(tag2, tag3, ignoreListOrder)) {
                                bl = true;
                                break;
                            }
                        }

                        if (!bl) {
                            return false;
                        }
                    }

                    return true;
                }
            }

            return standard.equals(subject);
        }
    }

    public static IntArrayTag createUUID(UUID uuid) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(uuid));
    }

    public static UUID loadUUID(Tag element) {
        if (element.getType() != IntArrayTag.TYPE) {
            throw new IllegalArgumentException("Expected UUID-Tag to be of type " + IntArrayTag.TYPE.getName() + ", but found " + element.getType().getName() + ".");
        } else {
            int[] is = ((IntArrayTag)element).getAsIntArray();
            if (is.length != 4) {
                throw new IllegalArgumentException("Expected UUID-Array to be of length 4, but found " + is.length + ".");
            } else {
                return UUIDUtil.uuidFromIntArray(is);
            }
        }
    }

    public static BlockPos readBlockPos(CompoundTag nbt) {
        return new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
    }

    public static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putInt("X", pos.getX());
        compoundTag.putInt("Y", pos.getY());
        compoundTag.putInt("Z", pos.getZ());
        return compoundTag;
    }

    public static BlockState readBlockState(HolderGetter<Block> blockLookup, CompoundTag nbt) {
        if (!nbt.contains("Name", 8)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            ResourceLocation resourceLocation = new ResourceLocation(nbt.getString("Name"));
            Optional<? extends Holder<Block>> optional = blockLookup.get(ResourceKey.create(Registries.BLOCK, resourceLocation));
            if (optional.isEmpty()) {
                return Blocks.AIR.defaultBlockState();
            } else {
                Block block = optional.get().value();
                BlockState blockState = block.defaultBlockState();
                if (nbt.contains("Properties", 10)) {
                    CompoundTag compoundTag = nbt.getCompound("Properties");
                    StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();

                    for(String string : compoundTag.getAllKeys()) {
                        Property<?> property = stateDefinition.getProperty(string);
                        if (property != null) {
                            blockState = setValueHelper(blockState, property, string, compoundTag, nbt);
                        }
                    }
                }

                return blockState;
            }
        }
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(S state, Property<T> property, String key, CompoundTag properties, CompoundTag root) {
        Optional<T> optional = property.getValue(properties.getString(key));
        if (optional.isPresent()) {
            return state.setValue(property, optional.get());
        } else {
            LOGGER.warn("Unable to read property: {} with value: {} for blockstate: {}", key, properties.getString(key), root);
            return state;
        }
    }

    public static CompoundTag writeBlockState(BlockState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        ImmutableMap<Property<?>, Comparable<?>> immutableMap = state.getValues();
        if (!immutableMap.isEmpty()) {
            CompoundTag compoundTag2 = new CompoundTag();

            for(Map.Entry<Property<?>, Comparable<?>> entry : immutableMap.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag2.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag2);
        }

        return compoundTag;
    }

    public static CompoundTag writeFluidState(FluidState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.FLUID.getKey(state.getType()).toString());
        ImmutableMap<Property<?>, Comparable<?>> immutableMap = state.getValues();
        if (!immutableMap.isEmpty()) {
            CompoundTag compoundTag2 = new CompoundTag();

            for(Map.Entry<Property<?>, Comparable<?>> entry : immutableMap.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag2.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag2);
        }

        return compoundTag;
    }

    private static <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }

    public static String prettyPrint(Tag nbt) {
        return prettyPrint(nbt, false);
    }

    public static String prettyPrint(Tag nbt, boolean withArrayContents) {
        return prettyPrint(new StringBuilder(), nbt, 0, withArrayContents).toString();
    }

    public static StringBuilder prettyPrint(StringBuilder stringBuilder, Tag nbt, int depth, boolean withArrayContents) {
        switch (nbt.getId()) {
            case 0:
                break;
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 8:
                stringBuilder.append((Object)nbt);
                break;
            case 7:
                ByteArrayTag byteArrayTag = (ByteArrayTag)nbt;
                byte[] bs = byteArrayTag.getAsByteArray();
                int i = bs.length;
                indent(depth, stringBuilder).append("byte[").append(i).append("] {\n");
                if (!withArrayContents) {
                    indent(depth + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                } else {
                    indent(depth + 1, stringBuilder);

                    for(int j = 0; j < bs.length; ++j) {
                        if (j != 0) {
                            stringBuilder.append(',');
                        }

                        if (j % 16 == 0 && j / 16 > 0) {
                            stringBuilder.append('\n');
                            if (j < bs.length) {
                                indent(depth + 1, stringBuilder);
                            }
                        } else if (j != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%02X", bs[j] & 255));
                    }
                }

                stringBuilder.append('\n');
                indent(depth, stringBuilder).append('}');
                break;
            case 9:
                ListTag listTag = (ListTag)nbt;
                int k = listTag.size();
                int l = listTag.getElementType();
                String string = l == 0 ? "undefined" : TagTypes.getType(l).getPrettyName();
                indent(depth, stringBuilder).append("list<").append(string).append(">[").append(k).append("] [");
                if (k != 0) {
                    stringBuilder.append('\n');
                }

                for(int m = 0; m < k; ++m) {
                    if (m != 0) {
                        stringBuilder.append(",\n");
                    }

                    indent(depth + 1, stringBuilder);
                    prettyPrint(stringBuilder, listTag.get(m), depth + 1, withArrayContents);
                }

                if (k != 0) {
                    stringBuilder.append('\n');
                }

                indent(depth, stringBuilder).append(']');
                break;
            case 10:
                CompoundTag compoundTag = (CompoundTag)nbt;
                List<String> list = Lists.newArrayList(compoundTag.getAllKeys());
                Collections.sort(list);
                indent(depth, stringBuilder).append('{');
                if (stringBuilder.length() - stringBuilder.lastIndexOf("\n") > 2 * (depth + 1)) {
                    stringBuilder.append('\n');
                    indent(depth + 1, stringBuilder);
                }

                int r = list.stream().mapToInt(String::length).max().orElse(0);
                String string2 = Strings.repeat(" ", r);

                for(int s = 0; s < list.size(); ++s) {
                    if (s != 0) {
                        stringBuilder.append(",\n");
                    }

                    String string3 = list.get(s);
                    indent(depth + 1, stringBuilder).append('"').append(string3).append('"').append((CharSequence)string2, 0, string2.length() - string3.length()).append(": ");
                    prettyPrint(stringBuilder, compoundTag.get(string3), depth + 1, withArrayContents);
                }

                if (!list.isEmpty()) {
                    stringBuilder.append('\n');
                }

                indent(depth, stringBuilder).append('}');
                break;
            case 11:
                IntArrayTag intArrayTag = (IntArrayTag)nbt;
                int[] is = intArrayTag.getAsIntArray();
                int n = 0;

                for(int o : is) {
                    n = Math.max(n, String.format(Locale.ROOT, "%X", o).length());
                }

                int p = is.length;
                indent(depth, stringBuilder).append("int[").append(p).append("] {\n");
                if (!withArrayContents) {
                    indent(depth + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                } else {
                    indent(depth + 1, stringBuilder);

                    for(int q = 0; q < is.length; ++q) {
                        if (q != 0) {
                            stringBuilder.append(',');
                        }

                        if (q % 16 == 0 && q / 16 > 0) {
                            stringBuilder.append('\n');
                            if (q < is.length) {
                                indent(depth + 1, stringBuilder);
                            }
                        } else if (q != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + n + "X", is[q]));
                    }
                }

                stringBuilder.append('\n');
                indent(depth, stringBuilder).append('}');
                break;
            case 12:
                LongArrayTag longArrayTag = (LongArrayTag)nbt;
                long[] ls = longArrayTag.getAsLongArray();
                long t = 0L;

                for(long u : ls) {
                    t = Math.max(t, (long)String.format(Locale.ROOT, "%X", u).length());
                }

                long v = (long)ls.length;
                indent(depth, stringBuilder).append("long[").append(v).append("] {\n");
                if (!withArrayContents) {
                    indent(depth + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                } else {
                    indent(depth + 1, stringBuilder);

                    for(int w = 0; w < ls.length; ++w) {
                        if (w != 0) {
                            stringBuilder.append(',');
                        }

                        if (w % 16 == 0 && w / 16 > 0) {
                            stringBuilder.append('\n');
                            if (w < ls.length) {
                                indent(depth + 1, stringBuilder);
                            }
                        } else if (w != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + t + "X", ls[w]));
                    }
                }

                stringBuilder.append('\n');
                indent(depth, stringBuilder).append('}');
                break;
            default:
                stringBuilder.append("<UNKNOWN :(>");
        }

        return stringBuilder;
    }

    private static StringBuilder indent(int depth, StringBuilder stringBuilder) {
        int i = stringBuilder.lastIndexOf("\n") + 1;
        int j = stringBuilder.length() - i;

        for(int k = 0; k < 2 * depth - j; ++k) {
            stringBuilder.append(' ');
        }

        return stringBuilder;
    }

    public static Component toPrettyComponent(Tag element) {
        return (new TextComponentTagVisitor("", 0)).visit(element);
    }

    public static String structureToSnbt(CompoundTag compound) {
        return (new SnbtPrinterTagVisitor()).visit(packStructureTemplate(compound));
    }

    public static CompoundTag snbtToStructure(String string) throws CommandSyntaxException {
        return unpackStructureTemplate(TagParser.parseTag(string));
    }

    @VisibleForTesting
    static CompoundTag packStructureTemplate(CompoundTag compound) {
        boolean bl = compound.contains("palettes", 9);
        ListTag listTag;
        if (bl) {
            listTag = compound.getList("palettes", 9).getList(0);
        } else {
            listTag = compound.getList("palette", 10);
        }

        ListTag listTag3 = listTag.stream().map(CompoundTag.class::cast).map(NbtUtils::packBlockState).map(StringTag::valueOf).collect(Collectors.toCollection(ListTag::new));
        compound.put("palette", listTag3);
        if (bl) {
            ListTag listTag4 = new ListTag();
            ListTag listTag5 = compound.getList("palettes", 9);
            listTag5.stream().map(ListTag.class::cast).forEach((nbt) -> {
                CompoundTag compoundTag = new CompoundTag();

                for(int i = 0; i < nbt.size(); ++i) {
                    compoundTag.putString(listTag3.getString(i), packBlockState(nbt.getCompound(i)));
                }

                listTag4.add(compoundTag);
            });
            compound.put("palettes", listTag4);
        }

        if (compound.contains("entities", 9)) {
            ListTag listTag6 = compound.getList("entities", 10);
            ListTag listTag7 = listTag6.stream().map(CompoundTag.class::cast).sorted(Comparator.comparing((nbt) -> {
                return nbt.getList("pos", 6);
            }, YXZ_LISTTAG_DOUBLE_COMPARATOR)).collect(Collectors.toCollection(ListTag::new));
            compound.put("entities", listTag7);
        }

        ListTag listTag8 = compound.getList("blocks", 10).stream().map(CompoundTag.class::cast).sorted(Comparator.comparing((nbt) -> {
            return nbt.getList("pos", 3);
        }, YXZ_LISTTAG_INT_COMPARATOR)).peek((nbt) -> {
            nbt.putString("state", listTag3.getString(nbt.getInt("state")));
        }).collect(Collectors.toCollection(ListTag::new));
        compound.put("data", listTag8);
        compound.remove("blocks");
        return compound;
    }

    @VisibleForTesting
    static CompoundTag unpackStructureTemplate(CompoundTag compound) {
        ListTag listTag = compound.getList("palette", 8);
        Map<String, Tag> map = listTag.stream().map(StringTag.class::cast).map(StringTag::getAsString).collect(ImmutableMap.toImmutableMap(Function.identity(), NbtUtils::unpackBlockState));
        if (compound.contains("palettes", 9)) {
            compound.put("palettes", compound.getList("palettes", 10).stream().map(CompoundTag.class::cast).map((nbt) -> {
                return map.keySet().stream().map(nbt::getString).map(NbtUtils::unpackBlockState).collect(Collectors.toCollection(ListTag::new));
            }).collect(Collectors.toCollection(ListTag::new)));
            compound.remove("palette");
        } else {
            compound.put("palette", map.values().stream().collect(Collectors.toCollection(ListTag::new)));
        }

        if (compound.contains("data", 9)) {
            Object2IntMap<String> object2IntMap = new Object2IntOpenHashMap<>();
            object2IntMap.defaultReturnValue(-1);

            for(int i = 0; i < listTag.size(); ++i) {
                object2IntMap.put(listTag.getString(i), i);
            }

            ListTag listTag2 = compound.getList("data", 10);

            for(int j = 0; j < listTag2.size(); ++j) {
                CompoundTag compoundTag = listTag2.getCompound(j);
                String string = compoundTag.getString("state");
                int k = object2IntMap.getInt(string);
                if (k == -1) {
                    throw new IllegalStateException("Entry " + string + " missing from palette");
                }

                compoundTag.putInt("state", k);
            }

            compound.put("blocks", listTag2);
            compound.remove("data");
        }

        return compound;
    }

    @VisibleForTesting
    static String packBlockState(CompoundTag compound) {
        StringBuilder stringBuilder = new StringBuilder(compound.getString("Name"));
        if (compound.contains("Properties", 10)) {
            CompoundTag compoundTag = compound.getCompound("Properties");
            String string = compoundTag.getAllKeys().stream().sorted().map((key) -> {
                return key + ":" + compoundTag.get(key).getAsString();
            }).collect(Collectors.joining(","));
            stringBuilder.append('{').append(string).append('}');
        }

        return stringBuilder.toString();
    }

    @VisibleForTesting
    static CompoundTag unpackBlockState(String string) {
        CompoundTag compoundTag = new CompoundTag();
        int i = string.indexOf(123);
        String string2;
        if (i >= 0) {
            string2 = string.substring(0, i);
            CompoundTag compoundTag2 = new CompoundTag();
            if (i + 2 <= string.length()) {
                String string3 = string.substring(i + 1, string.indexOf(125, i));
                COMMA_SPLITTER.split(string3).forEach((property) -> {
                    List<String> list = COLON_SPLITTER.splitToList(property);
                    if (list.size() == 2) {
                        compoundTag2.putString(list.get(0), list.get(1));
                    } else {
                        LOGGER.error("Something went wrong parsing: '{}' -- incorrect gamedata!", (Object)string);
                    }

                });
                compoundTag.put("Properties", compoundTag2);
            }
        } else {
            string2 = string;
        }

        compoundTag.putString("Name", string2);
        return compoundTag;
    }

    public static CompoundTag addCurrentDataVersion(CompoundTag nbt) {
        int i = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        return addDataVersion(nbt, i);
    }

    public static CompoundTag addDataVersion(CompoundTag nbt, int dataVersion) {
        nbt.putInt("DataVersion", dataVersion);
        return nbt;
    }

    public static int getDataVersion(CompoundTag nbt, int fallback) {
        return nbt.contains("DataVersion", 99) ? nbt.getInt("DataVersion") : fallback;
    }
}
