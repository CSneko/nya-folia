package net.minecraft.commands.arguments.blocks;

import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockStateParser {

    public static final SimpleCommandExceptionType ERROR_NO_TAGS_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.block.tag.disallowed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_BLOCK = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("argument.block.id.invalid", object);
    });
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_PROPERTY = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatable("argument.block.property.unknown", object, object1);
    });
    public static final Dynamic2CommandExceptionType ERROR_DUPLICATE_PROPERTY = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatable("argument.block.property.duplicate", object1, object);
    });
    public static final Dynamic3CommandExceptionType ERROR_INVALID_VALUE = new Dynamic3CommandExceptionType((object, object1, object2) -> {
        return Component.translatable("argument.block.property.invalid", object, object2, object1);
    });
    public static final Dynamic2CommandExceptionType ERROR_EXPECTED_VALUE = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatable("argument.block.property.novalue", object, object1);
    });
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_PROPERTIES = new SimpleCommandExceptionType(Component.translatable("argument.block.property.unclosed"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("arguments.block.tag.unknown", object);
    });
    private static final char SYNTAX_START_PROPERTIES = '[';
    private static final char SYNTAX_START_NBT = '{';
    private static final char SYNTAX_END_PROPERTIES = ']';
    private static final char SYNTAX_EQUALS = '=';
    private static final char SYNTAX_PROPERTY_SEPARATOR = ',';
    private static final char SYNTAX_TAG = '#';
    private static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    private final HolderLookup<Block> blocks;
    private final StringReader reader;
    private final boolean forTesting;
    private final boolean allowNbt;
    private final Map<Property<?>, Comparable<?>> properties = Maps.newLinkedHashMap(); // CraftBukkit - stable
    private final Map<String, String> vagueProperties = Maps.newHashMap();
    private ResourceLocation id = new ResourceLocation("");
    @Nullable
    private StateDefinition<Block, BlockState> definition;
    @Nullable
    private BlockState state;
    @Nullable
    private CompoundTag nbt;
    @Nullable
    private HolderSet<Block> tag;
    private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions;

    private BlockStateParser(HolderLookup<Block> registryWrapper, StringReader reader, boolean allowTag, boolean allowSnbt) {
        this.suggestions = BlockStateParser.SUGGEST_NOTHING;
        this.blocks = registryWrapper;
        this.reader = reader;
        this.forTesting = allowTag;
        this.allowNbt = allowSnbt;
    }

    public static BlockStateParser.BlockResult parseForBlock(HolderLookup<Block> registryWrapper, String string, boolean allowSnbt) throws CommandSyntaxException {
        return BlockStateParser.parseForBlock(registryWrapper, new StringReader(string), allowSnbt);
    }

    public static BlockStateParser.BlockResult parseForBlock(HolderLookup<Block> registryWrapper, StringReader reader, boolean allowSnbt) throws CommandSyntaxException {
        int i = reader.getCursor();

        try {
            BlockStateParser argumentblock = new BlockStateParser(registryWrapper, reader, false, allowSnbt);

            argumentblock.parse();
            return new BlockStateParser.BlockResult(argumentblock.state, argumentblock.properties, argumentblock.nbt);
        } catch (CommandSyntaxException commandsyntaxexception) {
            reader.setCursor(i);
            throw commandsyntaxexception;
        }
    }

    public static Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parseForTesting(HolderLookup<Block> registryWrapper, String string, boolean allowSnbt) throws CommandSyntaxException {
        return BlockStateParser.parseForTesting(registryWrapper, new StringReader(string), allowSnbt);
    }

    public static Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parseForTesting(HolderLookup<Block> registryWrapper, StringReader reader, boolean allowSnbt) throws CommandSyntaxException {
        int i = reader.getCursor();

        try {
            BlockStateParser argumentblock = new BlockStateParser(registryWrapper, reader, true, allowSnbt);

            argumentblock.parse();
            return argumentblock.tag != null ? Either.right(new BlockStateParser.TagResult(argumentblock.tag, argumentblock.vagueProperties, argumentblock.nbt)) : Either.left(new BlockStateParser.BlockResult(argumentblock.state, argumentblock.properties, argumentblock.nbt));
        } catch (CommandSyntaxException commandsyntaxexception) {
            reader.setCursor(i);
            throw commandsyntaxexception;
        }
    }

    public static CompletableFuture<Suggestions> fillSuggestions(HolderLookup<Block> registryWrapper, SuggestionsBuilder builder, boolean allowTag, boolean allowSnbt) {
        StringReader stringreader = new StringReader(builder.getInput());

        stringreader.setCursor(builder.getStart());
        BlockStateParser argumentblock = new BlockStateParser(registryWrapper, stringreader, allowTag, allowSnbt);

        try {
            argumentblock.parse();
        } catch (CommandSyntaxException commandsyntaxexception) {
            ;
        }

        return (CompletableFuture) argumentblock.suggestions.apply(builder.createOffset(stringreader.getCursor()));
    }

    private void parse() throws CommandSyntaxException {
        if (this.forTesting) {
            this.suggestions = this::suggestBlockIdOrTag;
        } else {
            this.suggestions = this::suggestItem;
        }

        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.readTag();
            this.suggestions = this::suggestOpenVaguePropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readVagueProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        } else {
            this.readBlock();
            this.suggestions = this::suggestOpenPropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        }

        if (this.allowNbt && this.reader.canRead() && this.reader.peek() == '{') {
            this.suggestions = BlockStateParser.SUGGEST_NOTHING;
            this.readNbt();
        }

    }

    private CompletableFuture<Suggestions> suggestPropertyNameOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        return this.suggestPropertyName(builder);
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyNameOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        return this.suggestVaguePropertyName(builder);
    }

    private CompletableFuture<Suggestions> suggestPropertyName(SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);
        Iterator iterator = this.state.getProperties().iterator();

        while (iterator.hasNext()) {
            Property<?> iblockstate = (Property) iterator.next();

            if (!this.properties.containsKey(iblockstate) && iblockstate.getName().startsWith(s)) {
                builder.suggest(iblockstate.getName() + "=");
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyName(SuggestionsBuilder builder) {
        String s = builder.getRemaining().toLowerCase(Locale.ROOT);

        if (this.tag != null) {
            Iterator iterator = this.tag.iterator();

            while (iterator.hasNext()) {
                Holder<Block> holder = (Holder) iterator.next();
                Iterator iterator1 = ((Block) holder.value()).getStateDefinition().getProperties().iterator();

                while (iterator1.hasNext()) {
                    Property<?> iblockstate = (Property) iterator1.next();

                    if (!this.vagueProperties.containsKey(iblockstate.getName()) && iblockstate.getName().startsWith(s)) {
                        builder.suggest(iblockstate.getName() + "=");
                    }
                }
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty() && this.hasBlockEntity()) {
            builder.suggest(String.valueOf('{'));
        }

        return builder.buildFuture();
    }

    private boolean hasBlockEntity() {
        if (this.state != null) {
            return this.state.hasBlockEntity();
        } else {
            if (this.tag != null) {
                Iterator iterator = this.tag.iterator();

                while (iterator.hasNext()) {
                    Holder<Block> holder = (Holder) iterator.next();

                    if (((Block) holder.value()).defaultBlockState().hasBlockEntity()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf('='));
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestNextPropertyOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        if (builder.getRemaining().isEmpty() && this.properties.size() < this.state.getProperties().size()) {
            builder.suggest(String.valueOf(','));
        }

        return builder.buildFuture();
    }

    private static <T extends Comparable<T>> SuggestionsBuilder addSuggestions(SuggestionsBuilder builder, Property<T> property) {
        Iterator iterator = property.getPossibleValues().iterator();

        while (iterator.hasNext()) {
            T t0 = (T) iterator.next(); // CraftBukkit - decompile error

            if (t0 instanceof Integer) {
                Integer integer = (Integer) t0;

                builder.suggest(integer);
            } else {
                builder.suggest(property.getName(t0));
            }
        }

        return builder;
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyValue(SuggestionsBuilder builder, String name) {
        boolean flag = false;

        if (this.tag != null) {
            Iterator iterator = this.tag.iterator();

            while (iterator.hasNext()) {
                Holder<Block> holder = (Holder) iterator.next();
                Block block = (Block) holder.value();
                Property<?> iblockstate = block.getStateDefinition().getProperty(name);

                if (iblockstate != null) {
                    BlockStateParser.addSuggestions(builder, iblockstate);
                }

                if (!flag) {
                    Iterator iterator1 = block.getStateDefinition().getProperties().iterator();

                    while (iterator1.hasNext()) {
                        Property<?> iblockstate1 = (Property) iterator1.next();

                        if (!this.vagueProperties.containsKey(iblockstate1.getName())) {
                            flag = true;
                            break;
                        }
                    }
                }
            }
        }

        if (flag) {
            builder.suggest(String.valueOf(','));
        }

        builder.suggest(String.valueOf(']'));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenVaguePropertiesOrNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty() && this.tag != null) {
            boolean flag = false;
            boolean flag1 = false;
            Iterator iterator = this.tag.iterator();

            while (iterator.hasNext()) {
                Holder<Block> holder = (Holder) iterator.next();
                Block block = (Block) holder.value();

                flag |= !block.getStateDefinition().getProperties().isEmpty();
                flag1 |= block.defaultBlockState().hasBlockEntity();
                if (flag && flag1) {
                    break;
                }
            }

            if (flag) {
                builder.suggest(String.valueOf('['));
            }

            if (flag1) {
                builder.suggest(String.valueOf('{'));
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenPropertiesOrNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            if (!this.definition.getProperties().isEmpty()) {
                builder.suggest(String.valueOf('['));
            }

            if (this.state.hasBlockEntity()) {
                builder.suggest(String.valueOf('{'));
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTag(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.blocks.listTagIds().map(TagKey::location), builder, String.valueOf('#'));
    }

    private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.blocks.listElementIds().map(ResourceKey::location), builder);
    }

    private CompletableFuture<Suggestions> suggestBlockIdOrTag(SuggestionsBuilder builder) {
        this.suggestTag(builder);
        this.suggestItem(builder);
        return builder.buildFuture();
    }

    private void readBlock() throws CommandSyntaxException {
        int i = this.reader.getCursor();

        this.id = ResourceLocation.read(this.reader);
        Block block = (Block) ((Holder.Reference) this.blocks.get(ResourceKey.create(Registries.BLOCK, this.id)).orElseThrow(() -> {
            this.reader.setCursor(i);
            return BlockStateParser.ERROR_UNKNOWN_BLOCK.createWithContext(this.reader, this.id.toString());
        })).value();

        this.definition = block.getStateDefinition();
        this.state = block.defaultBlockState();
    }

    private void readTag() throws CommandSyntaxException {
        if (!this.forTesting) {
            throw BlockStateParser.ERROR_NO_TAGS_ALLOWED.createWithContext(this.reader);
        } else {
            int i = this.reader.getCursor();

            this.reader.expect('#');
            this.suggestions = this::suggestTag;
            ResourceLocation minecraftkey = ResourceLocation.read(this.reader);

            this.tag = (HolderSet) this.blocks.get(TagKey.create(Registries.BLOCK, minecraftkey)).orElseThrow(() -> {
                this.reader.setCursor(i);
                return BlockStateParser.ERROR_UNKNOWN_TAG.createWithContext(this.reader, minecraftkey.toString());
            });
        }
    }

    private void readProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestPropertyNameOrEnd;
        this.reader.skipWhitespace();

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                int i = this.reader.getCursor();
                String s = this.reader.readString();
                Property<?> iblockstate = this.definition.getProperty(s);

                if (iblockstate == null) {
                    this.reader.setCursor(i);
                    throw BlockStateParser.ERROR_UNKNOWN_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
                }

                if (this.properties.containsKey(iblockstate)) {
                    this.reader.setCursor(i);
                    throw BlockStateParser.ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skipWhitespace();
                this.suggestions = this::suggestEquals;
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    throw BlockStateParser.ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.suggestions = (suggestionsbuilder) -> {
                    return BlockStateParser.addSuggestions(suggestionsbuilder, iblockstate).buildFuture();
                };
                int j = this.reader.getCursor();

                this.setValue(iblockstate, this.reader.readString(), j);
                this.suggestions = this::suggestNextPropertyOrEnd;
                this.reader.skipWhitespace();
                if (!this.reader.canRead()) {
                    continue;
                }

                if (this.reader.peek() == ',') {
                    this.reader.skip();
                    this.suggestions = this::suggestPropertyName;
                    continue;
                }

                if (this.reader.peek() != ']') {
                    throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip();
                return;
            }

            throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    private void readVagueProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestVaguePropertyNameOrEnd;
        int i = -1;

        this.reader.skipWhitespace();

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                int j = this.reader.getCursor();
                String s = this.reader.readString();

                if (this.vagueProperties.containsKey(s)) {
                    this.reader.setCursor(j);
                    throw BlockStateParser.ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skipWhitespace();
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    this.reader.setCursor(j);
                    throw BlockStateParser.ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), s);
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.suggestions = (suggestionsbuilder) -> {
                    return this.suggestVaguePropertyValue(suggestionsbuilder, s);
                };
                i = this.reader.getCursor();
                String s1 = this.reader.readString();

                this.vagueProperties.put(s, s1);
                this.reader.skipWhitespace();
                if (!this.reader.canRead()) {
                    continue;
                }

                i = -1;
                if (this.reader.peek() == ',') {
                    this.reader.skip();
                    this.suggestions = this::suggestVaguePropertyName;
                    continue;
                }

                if (this.reader.peek() != ']') {
                    throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip();
                return;
            }

            if (i >= 0) {
                this.reader.setCursor(i);
            }

            throw BlockStateParser.ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    private void readNbt() throws CommandSyntaxException {
        this.nbt = (new TagParser(this.reader)).readStruct();
    }

    private <T extends Comparable<T>> void setValue(Property<T> property, String value, int cursor) throws CommandSyntaxException {
        Optional<T> optional = property.getValue(value);

        if (optional.isPresent()) {
            this.state = (BlockState) this.state.setValue(property, (T) optional.get()); // CraftBukkit - decompile error
            this.properties.put(property, (Comparable) optional.get());
        } else {
            this.reader.setCursor(cursor);
            throw BlockStateParser.ERROR_INVALID_VALUE.createWithContext(this.reader, this.id.toString(), property.getName(), value);
        }
    }

    public static String serialize(BlockState state) {
        StringBuilder stringbuilder = new StringBuilder((String) state.getBlockHolder().unwrapKey().map((resourcekey) -> {
            return resourcekey.location().toString();
        }).orElse("air"));

        if (!state.getProperties().isEmpty()) {
            stringbuilder.append('[');
            boolean flag = false;

            for (UnmodifiableIterator unmodifiableiterator = state.getValues().entrySet().iterator(); unmodifiableiterator.hasNext(); flag = true) {
                Entry<Property<?>, Comparable<?>> entry = (Entry) unmodifiableiterator.next();

                if (flag) {
                    stringbuilder.append(',');
                }

                BlockStateParser.appendProperty(stringbuilder, (Property) entry.getKey(), (Comparable) entry.getValue());
            }

            stringbuilder.append(']');
        }

        return stringbuilder.toString();
    }

    private static <T extends Comparable<T>> void appendProperty(StringBuilder builder, Property<T> property, Comparable<?> value) {
        builder.append(property.getName());
        builder.append('=');
        builder.append(property.getName((T) value)); // CraftBukkit - decompile error
    }

    public static record BlockResult(BlockState blockState, Map<Property<?>, Comparable<?>> properties, @Nullable CompoundTag nbt) {

    }

    public static record TagResult(HolderSet<Block> tag, Map<String, String> vagueProperties, @Nullable CompoundTag nbt) {

    }
}
