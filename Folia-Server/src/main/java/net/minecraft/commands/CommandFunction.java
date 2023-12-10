package net.minecraft.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;

public class CommandFunction {
    private final CommandFunction.Entry[] entries;
    final ResourceLocation id;
    // Paper start
    public co.aikar.timings.Timing timing;
    public co.aikar.timings.Timing getTiming() {
        if (timing == null) {
            timing = co.aikar.timings.MinecraftTimings.getCommandFunctionTiming(this);
        }
        return timing;
    }
    // Paper end

    public CommandFunction(ResourceLocation id, CommandFunction.Entry[] elements) {
        this.id = id;
        this.entries = elements;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public CommandFunction.Entry[] getEntries() {
        return this.entries;
    }

    public CommandFunction instantiate(@Nullable CompoundTag arguments, CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) throws FunctionInstantiationException {
        return this;
    }

    private static boolean shouldConcatenateNextLine(CharSequence string) {
        int i = string.length();
        return i > 0 && string.charAt(i - 1) == '\\';
    }

    public static CommandFunction fromLines(ResourceLocation id, CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, List<String> lines) {
        List<CommandFunction.Entry> list = new ArrayList<>(lines.size());
        Set<String> set = new ObjectArraySet<>();

        for(int i = 0; i < lines.size(); ++i) {
            int j = i + 1;
            String string = lines.get(i).trim();
            String string3;
            if (shouldConcatenateNextLine(string)) {
                StringBuilder stringBuilder = new StringBuilder(string);

                do {
                    ++i;
                    if (i == lines.size()) {
                        throw new IllegalArgumentException("Line continuation at end of file");
                    }

                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    String string2 = lines.get(i).trim();
                    stringBuilder.append(string2);
                } while(shouldConcatenateNextLine(stringBuilder));

                string3 = stringBuilder.toString();
            } else {
                string3 = string;
            }

            StringReader stringReader = new StringReader(string3);
            if (stringReader.canRead() && stringReader.peek() != '#') {
                if (stringReader.peek() == '/') {
                    stringReader.skip();
                    if (stringReader.peek() == '/') {
                        throw new IllegalArgumentException("Unknown or invalid command '" + string3 + "' on line " + j + " (if you intended to make a comment, use '#' not '//')");
                    }

                    String string5 = stringReader.readUnquotedString();
                    throw new IllegalArgumentException("Unknown or invalid command '" + string3 + "' on line " + j + " (did you mean '" + string5 + "'? Do not use a preceding forwards slash.)");
                }

                if (stringReader.peek() == '$') {
                    CommandFunction.MacroEntry macroEntry = decomposeMacro(string3.substring(1), j);
                    list.add(macroEntry);
                    set.addAll(macroEntry.parameters());
                } else {
                    try {
                        ParseResults<CommandSourceStack> parseResults = dispatcher.parse(stringReader, source);
                        if (parseResults.getReader().canRead()) {
                            throw Commands.getParseException(parseResults);
                        }

                        list.add(new CommandFunction.CommandEntry(parseResults));
                    } catch (CommandSyntaxException var12) {
                        throw new IllegalArgumentException("Whilst parsing command on line " + j + ": " + var12.getMessage());
                    }
                }
            }
        }

        return (CommandFunction)(set.isEmpty() ? new CommandFunction(id, list.toArray((ix) -> {
            return new CommandFunction.Entry[ix];
        })) : new CommandFunction.CommandMacro(id, list.toArray((ix) -> {
            return new CommandFunction.Entry[ix];
        }), List.copyOf(set)));
    }

    @VisibleForTesting
    public static CommandFunction.MacroEntry decomposeMacro(String macro, int line) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        ImmutableList.Builder<String> builder2 = ImmutableList.builder();
        int i = macro.length();
        int j = 0;
        int k = macro.indexOf(36);

        while(k != -1) {
            if (k != i - 1 && macro.charAt(k + 1) == '(') {
                builder.add(macro.substring(j, k));
                int l = macro.indexOf(41, k + 1);
                if (l == -1) {
                    throw new IllegalArgumentException("Unterminated macro variable in macro '" + macro + "' on line " + line);
                }

                String string = macro.substring(k + 2, l);
                if (!isValidVariableName(string)) {
                    throw new IllegalArgumentException("Invalid macro variable name '" + string + "' on line " + line);
                }

                builder2.add(string);
                j = l + 1;
                k = macro.indexOf(36, j);
            } else {
                k = macro.indexOf(36, k + 1);
            }
        }

        if (j == 0) {
            throw new IllegalArgumentException("Macro without variables on line " + line);
        } else {
            if (j != i) {
                builder.add(macro.substring(j));
            }

            return new CommandFunction.MacroEntry(builder.build(), builder2.build());
        }
    }

    private static boolean isValidVariableName(String name) {
        for(int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }

        return true;
    }

    public static class CacheableFunction {
        public static final CommandFunction.CacheableFunction NONE = new CommandFunction.CacheableFunction((ResourceLocation)null);
        @Nullable
        private final ResourceLocation id;
        private boolean resolved;
        private Optional<CommandFunction> function = Optional.empty();

        public CacheableFunction(@Nullable ResourceLocation id) {
            this.id = id;
        }

        public CacheableFunction(CommandFunction function) {
            this.resolved = true;
            this.id = null;
            this.function = Optional.of(function);
        }

        public Optional<CommandFunction> get(ServerFunctionManager manager) {
            if (!this.resolved) {
                if (this.id != null) {
                    this.function = manager.get(this.id);
                }

                this.resolved = true;
            }

            return this.function;
        }

        @Nullable
        public ResourceLocation getId() {
            return this.function.map((f) -> {
                return f.id;
            }).orElse(this.id);
        }
    }

    public static class CommandEntry implements CommandFunction.Entry {
        private final ParseResults<CommandSourceStack> parse;

        public CommandEntry(ParseResults<CommandSourceStack> parsed) {
            this.parse = parsed;
        }

        @Override
        public void execute(ServerFunctionManager manager, CommandSourceStack source, Deque<ServerFunctionManager.QueuedCommand> entries, int maxChainLength, int depth, @Nullable ServerFunctionManager.TraceCallbacks tracer) throws CommandSyntaxException {
            if (tracer != null) {
                String string = this.parse.getReader().getString();
                tracer.onCommand(depth, string);
                int i = this.execute(manager, source);
                tracer.onReturn(depth, string, i);
            } else {
                this.execute(manager, source);
            }

        }

        private int execute(ServerFunctionManager manager, CommandSourceStack source) throws CommandSyntaxException {
            return manager.getDispatcher().execute(Commands.mapSource(this.parse, (currentSource) -> {
                return source;
            }));
        }

        @Override
        public String toString() {
            return this.parse.getReader().getString();
        }
    }

    static class CommandMacro extends CommandFunction {
        private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#");
        private final List<String> parameters;
        private static final int MAX_CACHE_ENTRIES = 8;
        private final Object2ObjectLinkedOpenHashMap<List<String>, CommandFunction> cache = new Object2ObjectLinkedOpenHashMap<>(8, 0.25F);

        public CommandMacro(ResourceLocation id, CommandFunction.Entry[] elements, List<String> variables) {
            super(id, elements);
            this.parameters = variables;
        }

        @Override
        public CommandFunction instantiate(@Nullable CompoundTag arguments, CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) throws FunctionInstantiationException {
            if (arguments == null) {
                throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_arguments", this.getId()));
            } else {
                List<String> list = new ArrayList<>(this.parameters.size());

                for(String string : this.parameters) {
                    if (!arguments.contains(string)) {
                        throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_argument", this.getId(), string));
                    }

                    list.add(stringify(arguments.get(string)));
                }

                CommandFunction commandFunction = this.cache.getAndMoveToLast(list);
                if (commandFunction != null) {
                    return commandFunction;
                } else {
                    if (this.cache.size() >= 8) {
                        this.cache.removeFirst();
                    }

                    CommandFunction commandFunction2 = this.substituteAndParse(list, dispatcher, source);
                    if (commandFunction2 != null) {
                        this.cache.put(list, commandFunction2);
                    }

                    return commandFunction2;
                }
            }
        }

        private static String stringify(Tag nbt) {
            if (nbt instanceof FloatTag floatTag) {
                return DECIMAL_FORMAT.format((double)floatTag.getAsFloat());
            } else if (nbt instanceof DoubleTag doubleTag) {
                return DECIMAL_FORMAT.format(doubleTag.getAsDouble());
            } else if (nbt instanceof ByteTag byteTag) {
                return String.valueOf((int)byteTag.getAsByte());
            } else if (nbt instanceof ShortTag shortTag) {
                return String.valueOf((int)shortTag.getAsShort());
            } else if (nbt instanceof LongTag longTag) {
                return String.valueOf(longTag.getAsLong());
            } else {
                return nbt.getAsString();
            }
        }

        private CommandFunction substituteAndParse(List<String> arguments, CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) throws FunctionInstantiationException {
            CommandFunction.Entry[] entrys = this.getEntries();
            CommandFunction.Entry[] entrys2 = new CommandFunction.Entry[entrys.length];

            for(int i = 0; i < entrys.length; ++i) {
                CommandFunction.Entry entry = entrys[i];
                if (!(entry instanceof CommandFunction.MacroEntry macroEntry)) {
                    entrys2[i] = entry;
                } else {
                    List<String> list = macroEntry.parameters();
                    List<String> list2 = new ArrayList<>(list.size());

                    for(String string : list) {
                        list2.add(arguments.get(this.parameters.indexOf(string)));
                    }

                    String string2 = macroEntry.substitute(list2);

                    try {
                        ParseResults<CommandSourceStack> parseResults = dispatcher.parse(string2, source);
                        if (parseResults.getReader().canRead()) {
                            throw Commands.getParseException(parseResults);
                        }

                        entrys2[i] = new CommandFunction.CommandEntry(parseResults);
                    } catch (CommandSyntaxException var13) {
                        throw new FunctionInstantiationException(Component.translatable("commands.function.error.parse", this.getId(), string2, var13.getMessage()));
                    }
                }
            }

            ResourceLocation resourceLocation = this.getId();
            return new CommandFunction(new ResourceLocation(resourceLocation.getNamespace(), resourceLocation.getPath() + "/" + arguments.hashCode()), entrys2);
        }

        static {
            DECIMAL_FORMAT.setMaximumFractionDigits(15);
            DECIMAL_FORMAT.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        }
    }

    @FunctionalInterface
    public interface Entry {
        void execute(ServerFunctionManager manager, CommandSourceStack source, Deque<ServerFunctionManager.QueuedCommand> entries, int maxChainLength, int depth, @Nullable ServerFunctionManager.TraceCallbacks tracer) throws CommandSyntaxException;
    }

    public static class FunctionEntry implements CommandFunction.Entry {
        private final CommandFunction.CacheableFunction function;

        public FunctionEntry(CommandFunction function) {
            this.function = new CommandFunction.CacheableFunction(function);
        }

        @Override
        public void execute(ServerFunctionManager manager, CommandSourceStack source, Deque<ServerFunctionManager.QueuedCommand> entries, int maxChainLength, int depth, @Nullable ServerFunctionManager.TraceCallbacks tracer) {
            Util.ifElse(this.function.get(manager), (f) -> {
                CommandFunction.Entry[] entrys = f.getEntries();
                if (tracer != null) {
                    tracer.onCall(depth, f.getId(), entrys.length);
                }

                int k = maxChainLength - entries.size();
                int l = Math.min(entrys.length, k);

                for(int m = l - 1; m >= 0; --m) {
                    entries.addFirst(new ServerFunctionManager.QueuedCommand(source, depth + 1, entrys[m]));
                }

            }, () -> {
                if (tracer != null) {
                    tracer.onCall(depth, this.function.getId(), -1);
                }

            });
        }

        @Override
        public String toString() {
            return "function " + this.function.getId();
        }
    }

    public static class MacroEntry implements CommandFunction.Entry {
        private final List<String> segments;
        private final List<String> parameters;

        public MacroEntry(List<String> parts, List<String> variables) {
            this.segments = parts;
            this.parameters = variables;
        }

        public List<String> parameters() {
            return this.parameters;
        }

        public String substitute(List<String> arguments) {
            StringBuilder stringBuilder = new StringBuilder();

            for(int i = 0; i < this.parameters.size(); ++i) {
                stringBuilder.append(this.segments.get(i)).append(arguments.get(i));
            }

            if (this.segments.size() > this.parameters.size()) {
                stringBuilder.append(this.segments.get(this.segments.size() - 1));
            }

            return stringBuilder.toString();
        }

        @Override
        public void execute(ServerFunctionManager manager, CommandSourceStack source, Deque<ServerFunctionManager.QueuedCommand> entries, int maxChainLength, int depth, @Nullable ServerFunctionManager.TraceCallbacks tracer) throws CommandSyntaxException {
            throw new IllegalStateException("Tried to execute an uninstantiated macro");
        }
    }
}
