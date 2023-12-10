package io.papermc.paper.event.player;

import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Called when a {@link Player} clicks a side on a sign that causes a command to run.
 * <p>
 * This command is run with elevated permissions which allows players to access commands on signs they wouldn't
 * normally be able to run.
 */
public class PlayerSignCommandPreprocessEvent extends PlayerCommandPreprocessEvent {

    private final Sign sign;
    private final Side side;

    @ApiStatus.Internal
    public PlayerSignCommandPreprocessEvent(@NotNull Player player, @NotNull String message, @NotNull Set<Player> recipients, @NotNull Sign sign, @NotNull Side side) {
        super(player, message, recipients);
        this.sign = sign;
        this.side = side;
    }

    /**
     * Gets the sign that the command originated from.
     *
     * @return the sign
     */
    public @NotNull Sign getSign() {
        return this.sign;
    }

    /**
     * Gets the side of the sign that the command originated from.
     *
     * @return the sign side
     */
    public @NotNull Side getSide() {
        return this.side;
    }
}
