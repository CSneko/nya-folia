package org.bukkit.event.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a player executes a command that is not defined
 */
public class UnknownCommandEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    @NotNull private CommandSender sender;
    @NotNull private String commandLine;
    @Nullable private Component message;

    public UnknownCommandEvent(@NotNull final CommandSender sender, @NotNull final String commandLine, @Nullable final Component message) {
        super(false);
        this.sender = sender;
        this.commandLine = commandLine;
        this.message = message;
    }

    /**
     * Gets the CommandSender or ConsoleCommandSender
     * <p>
     *
     * @return Sender of the command
     */
    @NotNull
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Gets the command that was send
     * <p>
     *
     * @return Command sent
     */
    @NotNull
    public String getCommandLine() {
        return commandLine;
    }

    /**
     * Gets message that will be returned
     * <p>
     *
     * @return Unknown command message
     * @deprecated use {@link #message()}
     */
    @Nullable
    @Deprecated
    public String getMessage() {
        return this.message == null ? null : LegacyComponentSerializer.legacySection().serialize(this.message);
    }

    /**
     * Sets message that will be returned
     * <p>
     * Set to null to avoid any message being sent
     *
     * @param message the message to be returned, or null
     * @deprecated use {@link #message(Component)}
     */
    @Deprecated
    public void setMessage(@Nullable String message) {
        this.message(message == null ? null : LegacyComponentSerializer.legacySection().deserialize(message));
    }

    /**
     * Gets message that will be returned
     * <p>
     *
     * @return Unknown command message
     */
    @Nullable
    @Contract(pure = true)
    public Component message() {
        return this.message;
    }

    /**
     * Sets message that will be returned
     * <p>
     * Set to null to avoid any message being sent
     *
     * @param message the message to be returned, or null
     */
    public void message(@Nullable Component message) {
        this.message = message;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}

