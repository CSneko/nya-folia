package io.papermc.paper.event.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a world's gamerule is changed, either by command or by api.
 */
public class WorldGameRuleChangeEvent extends WorldEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final CommandSender commandSender;
    private final GameRule<?> gameRule;
    private String value;
    private boolean cancelled;

    public WorldGameRuleChangeEvent(@NotNull World world, @Nullable CommandSender commandSender, @NotNull GameRule<?> gameRule, @NotNull String value) {
        super(world);
        this.commandSender = commandSender;
        this.gameRule = gameRule;
        this.value = value;
    }

    /**
     * Gets the command sender associated with this event.
     *
     * @return {@code null} if the gamerule was changed via api, otherwise the {@link CommandSender}.
     */
    @Nullable
    public CommandSender getCommandSender() {
        return commandSender;
    }

    /**
     * Gets the game rule associated with this event.
     *
     * @return the gamerule being changed.
     */
    @NotNull
    public GameRule<?> getGameRule() {
        return gameRule;
    }

    /**
     * Gets the new value of the gamerule.
     *
     * @return the new value of the gamerule.
     */
    @NotNull
    public String getValue() {
        return value;
    }

    /**
     * Sets the new value of this gamerule.
     *
     * @param value the new value of the gamerule.
     */
    public void setValue(@NotNull String value) {
        this.value = value;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
