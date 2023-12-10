package net.minecraft.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;

public class ServerFunctionManager {

    private static final Component NO_RECURSIVE_TRACES = Component.translatable("commands.debug.function.noRecursion");
    private static final ResourceLocation TICK_FUNCTION_TAG = new ResourceLocation("tick");
    private static final ResourceLocation LOAD_FUNCTION_TAG = new ResourceLocation("load");
    final MinecraftServer server;
    @Nullable
    private ServerFunctionManager.ExecutionContext context;
    private List<CommandFunction> ticking = ImmutableList.of();
    private boolean postReload;
    private ServerFunctionLibrary library;

    public ServerFunctionManager(MinecraftServer server, ServerFunctionLibrary loader) {
        this.server = server;
        this.library = loader;
        this.postReload(loader);
    }

    public int getCommandLimit() {
        return this.server.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.server.vanillaCommandDispatcher.getDispatcher(); // CraftBukkit
    }

    public void tick() {
        if (this.postReload) {
            this.postReload = false;
            Collection<CommandFunction> collection = this.library.getTag(ServerFunctionManager.LOAD_FUNCTION_TAG);

            this.executeTagFunctions(collection, ServerFunctionManager.LOAD_FUNCTION_TAG);
        }

        this.executeTagFunctions(this.ticking, ServerFunctionManager.TICK_FUNCTION_TAG);
    }

    private void executeTagFunctions(Collection<CommandFunction> functions, ResourceLocation label) {
        ProfilerFiller gameprofilerfiller = this.server.getProfiler();

        Objects.requireNonNull(label);
        gameprofilerfiller.push(label::toString);
        Iterator iterator = functions.iterator();

        while (iterator.hasNext()) {
            CommandFunction customfunction = (CommandFunction) iterator.next();

            this.execute(customfunction, this.getGameLoopSender());
        }

        this.server.getProfiler().pop();
    }

    public int execute(CommandFunction function, CommandSourceStack source) {
        try {
            return this.execute(function, source, (ServerFunctionManager.TraceCallbacks) null, (CompoundTag) null);
        } catch (FunctionInstantiationException functioninstantiationexception) {
            return 0;
        }
    }

    public int execute(CommandFunction function, CommandSourceStack source, @Nullable ServerFunctionManager.TraceCallbacks tracer, @Nullable CompoundTag arguments) throws FunctionInstantiationException {
        CommandFunction customfunction1 = function.instantiate(arguments, this.getDispatcher(), source);

        if (this.context != null) {
            if (tracer != null) {
                this.context.reportError(ServerFunctionManager.NO_RECURSIVE_TRACES.getString());
                return 0;
            } else {
                this.context.delayFunctionCall(customfunction1, source);
                return 0;
            }
        } else {
            int i;

            try (co.aikar.timings.Timing timing = function.getTiming().startTiming()) { // Paper
                this.context = new ServerFunctionManager.ExecutionContext(tracer);
                i = this.context.runTopCommand(customfunction1, source);
            } finally {
                this.context = null;
            }

            return i;
        }
    }

    public void replaceLibrary(ServerFunctionLibrary loader) {
        this.library = loader;
        this.postReload(loader);
    }

    private void postReload(ServerFunctionLibrary loader) {
        this.ticking = ImmutableList.copyOf(loader.getTag(ServerFunctionManager.TICK_FUNCTION_TAG));
        this.postReload = true;
    }

    public CommandSourceStack getGameLoopSender() {
        return this.server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }

    public Optional<CommandFunction> get(ResourceLocation id) {
        return this.library.getFunction(id);
    }

    public Collection<CommandFunction> getTag(ResourceLocation id) {
        return this.library.getTag(id);
    }

    public Iterable<ResourceLocation> getFunctionNames() {
        return this.library.getFunctions().keySet();
    }

    public Iterable<ResourceLocation> getTagNames() {
        return this.library.getAvailableTags();
    }

    public interface TraceCallbacks {

        void onCommand(int depth, String command);

        void onReturn(int depth, String command, int result);

        void onError(int depth, String message);

        void onCall(int depth, ResourceLocation function, int size);
    }

    private class ExecutionContext {

        private int depth;
        @Nullable
        private final ServerFunctionManager.TraceCallbacks tracer;
        private final Deque<ServerFunctionManager.QueuedCommand> commandQueue = Queues.newArrayDeque();
        private final List<ServerFunctionManager.QueuedCommand> nestedCalls = Lists.newArrayList();
        boolean abortCurrentDepth = false;

        ExecutionContext(@Nullable ServerFunctionManager.TraceCallbacks customfunctiondata_tracecallbacks) {
            this.tracer = customfunctiondata_tracecallbacks;
        }

        void delayFunctionCall(CommandFunction function, CommandSourceStack source) {
            int i = ServerFunctionManager.this.getCommandLimit();
            CommandSourceStack commandlistenerwrapper1 = this.wrapSender(source);

            if (this.commandQueue.size() + this.nestedCalls.size() < i) {
                this.nestedCalls.add(new ServerFunctionManager.QueuedCommand(commandlistenerwrapper1, this.depth, new CommandFunction.FunctionEntry(function)));
            }

        }

        private CommandSourceStack wrapSender(CommandSourceStack source) {
            IntConsumer intconsumer = source.getReturnValueConsumer();

            return intconsumer instanceof ServerFunctionManager.ExecutionContext.AbortingReturnValueConsumer ? source : source.withReturnValueConsumer(new ServerFunctionManager.ExecutionContext.AbortingReturnValueConsumer(intconsumer));
        }

        int runTopCommand(CommandFunction function, CommandSourceStack source) {
            int i = ServerFunctionManager.this.getCommandLimit();
            CommandSourceStack commandlistenerwrapper1 = this.wrapSender(source);
            int j = 0;
            CommandFunction.Entry[] acustomfunction_d = function.getEntries();

            for (int k = acustomfunction_d.length - 1; k >= 0; --k) {
                this.commandQueue.push(new ServerFunctionManager.QueuedCommand(commandlistenerwrapper1, 0, acustomfunction_d[k]));
            }

            do {
                if (this.commandQueue.isEmpty()) {
                    return j;
                }

                try {
                    ServerFunctionManager.QueuedCommand customfunctiondata_queuedcommand = (ServerFunctionManager.QueuedCommand) this.commandQueue.removeFirst();
                    ProfilerFiller gameprofilerfiller = ServerFunctionManager.this.server.getProfiler();

                    Objects.requireNonNull(customfunctiondata_queuedcommand);
                    gameprofilerfiller.push(customfunctiondata_queuedcommand::toString);
                    this.depth = customfunctiondata_queuedcommand.depth;
                    customfunctiondata_queuedcommand.execute(ServerFunctionManager.this, this.commandQueue, i, this.tracer);
                    if (this.abortCurrentDepth) {
                        while (!this.commandQueue.isEmpty() && ((ServerFunctionManager.QueuedCommand) this.commandQueue.peek()).depth >= this.depth) {
                            this.commandQueue.removeFirst();
                        }

                        this.abortCurrentDepth = false;
                    } else if (!this.nestedCalls.isEmpty()) {
                        List list = Lists.reverse(this.nestedCalls);
                        Deque deque = this.commandQueue;

                        Objects.requireNonNull(this.commandQueue);
                        list.forEach(deque::addFirst);
                    }

                    this.nestedCalls.clear();
                } finally {
                    ServerFunctionManager.this.server.getProfiler().pop();
                }

                ++j;
            } while (j < i);

            return j;
        }

        public void reportError(String message) {
            if (this.tracer != null) {
                this.tracer.onError(this.depth, message);
            }

        }

        private class AbortingReturnValueConsumer implements IntConsumer {

            private final IntConsumer wrapped;

            AbortingReturnValueConsumer(IntConsumer intconsumer) {
                this.wrapped = intconsumer;
            }

            public void accept(int i) {
                this.wrapped.accept(i);
                ExecutionContext.this.abortCurrentDepth = true;
            }
        }
    }

    public static class QueuedCommand {

        private final CommandSourceStack sender;
        final int depth;
        private final CommandFunction.Entry entry;

        public QueuedCommand(CommandSourceStack source, int depth, CommandFunction.Entry element) {
            this.sender = source;
            this.depth = depth;
            this.entry = element;
        }

        public void execute(ServerFunctionManager manager, Deque<ServerFunctionManager.QueuedCommand> entries, int maxChainLength, @Nullable ServerFunctionManager.TraceCallbacks tracer) {
            try {
                this.entry.execute(manager, this.sender, entries, maxChainLength, this.depth, tracer);
            } catch (CommandSyntaxException commandsyntaxexception) {
                if (tracer != null) {
                    tracer.onError(this.depth, commandsyntaxexception.getRawMessage().getString());
                }
            } catch (Exception exception) {
                if (tracer != null) {
                    tracer.onError(this.depth, exception.getMessage());
                }
            }

        }

        public String toString() {
            return this.entry.toString();
        }
    }
}
