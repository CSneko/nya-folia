/*
 * Copyright (c) 2017 Daniel Ennis (Aikar) MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.destroystokyo.paper.event.server;

import com.google.common.base.Preconditions;
import io.papermc.paper.util.TransformingRandomAccessList;
import net.kyori.adventure.text.Component;
import net.kyori.examination.Examinable;
import net.kyori.examination.ExaminableProperty;
import net.kyori.examination.string.StringExaminer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows plugins to compute tab completion results asynchronously. If this event provides completions, then the standard synchronous process will not be fired to populate the results. However, the synchronous TabCompleteEvent will fire with the Async results.
 *
 * Only 1 process will be allowed to provide completions, the Async Event, or the standard process.
 */
public class AsyncTabCompleteEvent extends Event implements Cancellable {
    @NotNull private final CommandSender sender;
    @NotNull private final String buffer;
    private final boolean isCommand;
    @Nullable
    private final Location loc;
    private final List<Completion> completions = new ArrayList<>();
    private final List<String> stringCompletions = new TransformingRandomAccessList<>(
        this.completions,
        Completion::suggestion,
        Completion::completion
    );
    private boolean cancelled;
    private boolean handled = false;
    private boolean fireSyncHandler = true;

    public AsyncTabCompleteEvent(@NotNull CommandSender sender, @NotNull String buffer, boolean isCommand, @Nullable Location loc) {
        super(true);
        this.sender = sender;
        this.buffer = buffer;
        this.isCommand = isCommand;
        this.loc = loc;
    }

    @Deprecated
    public AsyncTabCompleteEvent(@NotNull CommandSender sender, @NotNull List<String> completions, @NotNull String buffer, boolean isCommand, @Nullable Location loc) {
        super(true);
        this.sender = sender;
        this.completions.addAll(fromStrings(completions));
        this.buffer = buffer;
        this.isCommand = isCommand;
        this.loc = loc;
    }

    /**
     * Get the sender completing this command.
     *
     * @return the {@link CommandSender} instance
     */
    @NotNull
    public CommandSender getSender() {
        return sender;
    }

    /**
     * The list of completions which will be offered to the sender, in order.
     * This list is mutable and reflects what will be offered.
     *
     * If this collection is not empty after the event is fired, then
     * the standard process of calling {@link Command#tabComplete(CommandSender, String, String[])}
     * or current player names will not be called.
     *
     * @return a list of offered completions
     */
    @NotNull
    public List<String> getCompletions() {
        return this.stringCompletions;
    }

    /**
     * Set the completions offered, overriding any already set.
     * If this collection is not empty after the event is fired, then
     * the standard process of calling {@link Command#tabComplete(CommandSender, String, String[])}
     * or current player names will not be called.
     *
     * The passed collection will be cloned to a new List. You must call {{@link #getCompletions()}} to mutate from here
     *
     * @param completions the new completions
     */
    public void setCompletions(@NotNull List<String> completions) {
        if (completions == this.stringCompletions) {
            return;
        }
        Preconditions.checkNotNull(completions);
        this.completions.clear();
        this.completions.addAll(fromStrings(completions));
    }

    /**
     * The list of {@link Completion completions} which will be offered to the sender, in order.
     * This list is mutable and reflects what will be offered.
     * <p>
     * If this collection is not empty after the event is fired, then
     * the standard process of calling {@link Command#tabComplete(CommandSender, String, String[])}
     * or current player names will not be called.
     *
     * @return a list of offered completions
     */
    public @NotNull List<Completion> completions() {
        return this.completions;
    }

    /**
     * Set the {@link Completion completions} offered, overriding any already set.
     * If this collection is not empty after the event is fired, then
     * the standard process of calling {@link Command#tabComplete(CommandSender, String, String[])}
     * or current player names will not be called.
     * <p>
     * The passed collection will be cloned to a new List. You must call {{@link #completions()}} to mutate from here
     *
     * @param newCompletions the new completions
     */
    public void completions(final @NotNull List<Completion> newCompletions) {
        Preconditions.checkNotNull(newCompletions, "new completions");
        this.completions.clear();
        this.completions.addAll(newCompletions);
    }

    /**
     * Return the entire buffer which formed the basis of this completion.
     *
     * @return command buffer, as entered
     */
    @NotNull
    public String getBuffer() {
        return buffer;
    }

    /**
     * @return True if it is a command being tab completed, false if it is a chat message.
     */
    public boolean isCommand() {
        return isCommand;
    }

    /**
     * @return The position looked at by the sender, or null if none
     */
    @Nullable
    public Location getLocation() {
        return loc;
    }

    /**
     * If true, the standard process of calling {@link Command#tabComplete(CommandSender, String, String[])}
     * or current player names will not be called.
     *
     * @return Is completions considered handled. Always true if completions is not empty.
     */
    public boolean isHandled() {
        return !completions.isEmpty() || handled;
    }

    /**
     * Sets whether or not to consider the completion request handled.
     * If true, the standard process of calling {@link Command#tabComplete(CommandSender, String, String[])}
     * or current player names will not be called.
     *
     * @param handled if this completion should be marked as being handled
     */
    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    private static final HandlerList handlers = new HandlerList();


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Will provide no completions, and will not fire the synchronous process
     * @param cancelled true if you wish to cancel this event
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    private static @NotNull List<Completion> fromStrings(final @NotNull List<String> strings) {
        final List<Completion> list = new ArrayList<>();
        for (final String it : strings) {
            list.add(new CompletionImpl(it, null));
        }
        return list;
    }

    /**
     * A rich tab completion, consisting of a string suggestion, and a nullable {@link Component} tooltip.
     */
    public interface Completion extends Examinable {
        /**
         * Get the suggestion string for this {@link Completion}.
         *
         * @return suggestion string
         */
        @NotNull String suggestion();

        /**
         * Get the suggestion tooltip for this {@link Completion}.
         *
         * @return tooltip component
         */
        @Nullable Component tooltip();

        @Override
        default @NotNull Stream<? extends ExaminableProperty> examinableProperties() {
            return Stream.of(ExaminableProperty.of("suggestion", this.suggestion()), ExaminableProperty.of("tooltip", this.tooltip()));
        }

        /**
         * Create a new {@link Completion} from a suggestion string.
         *
         * @param suggestion suggestion string
         * @return new completion instance
         */
        static @NotNull Completion completion(final @NotNull String suggestion) {
            return new CompletionImpl(suggestion, null);
        }

        /**
         * Create a new {@link Completion} from a suggestion string and a tooltip {@link Component}.
         *
         * <p>If the provided component is null, the suggestion will not have a tooltip.</p>
         *
         * @param suggestion suggestion string
         * @param tooltip    tooltip component, or null
         * @return new completion instance
         */
        static @NotNull Completion completion(final @NotNull String suggestion, final @Nullable Component tooltip) {
            return new CompletionImpl(suggestion, tooltip);
        }
    }

    static final class CompletionImpl implements Completion {
        private final String suggestion;
        private final Component tooltip;

        CompletionImpl(final @NotNull String suggestion, final @Nullable Component tooltip) {
            this.suggestion = suggestion;
            this.tooltip = tooltip;
        }

        @Override
        public @NotNull String suggestion() {
            return this.suggestion;
        }

        @Override
        public @Nullable Component tooltip() {
            return this.tooltip;
        }

        @Override
        public boolean equals(final @Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            final CompletionImpl that = (CompletionImpl) o;
            return this.suggestion.equals(that.suggestion)
                && java.util.Objects.equals(this.tooltip, that.tooltip);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.suggestion, this.tooltip);
        }

        @Override
        public @NotNull String toString() {
            return StringExaminer.simpleEscaping().examine(this);
        }
    }
}
