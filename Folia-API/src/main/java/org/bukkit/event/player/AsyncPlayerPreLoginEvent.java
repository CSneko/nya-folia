package org.bukkit.event.player;

import java.net.InetAddress;
import java.util.UUID;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Stores details for players attempting to log in.
 * <p>
 * This event is asynchronous, and not run using main thread.
 */
public class AsyncPlayerPreLoginEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private Result result;
    private net.kyori.adventure.text.Component message; // Paper
    //private String name; // Paper - Not used anymore
    private final InetAddress ipAddress;
    private final InetAddress rawAddress; // Paper
    //private UUID uniqueId; // Paper - Not used anymore
    private final String hostname; // Paper

    @Deprecated
    public AsyncPlayerPreLoginEvent(@NotNull final String name, @NotNull final InetAddress ipAddress) {
        this(name, ipAddress, null);
    }

    public AsyncPlayerPreLoginEvent(@NotNull final String name, @NotNull final InetAddress ipAddress, @NotNull final UUID uniqueId) {
        // Paper start
        this(name, ipAddress, uniqueId, Bukkit.createProfile(uniqueId, name));
    }
    private PlayerProfile profile;

    /**
     * Gets the PlayerProfile of the player logging in
     * @return The Profile
     */
    @NotNull
    public PlayerProfile getPlayerProfile() {
        return profile;
    }

    /**
     * Changes the PlayerProfile the player will login as
     * @param profile The profile to use
     */
    public void setPlayerProfile(@NotNull PlayerProfile profile) {
        this.profile = profile;
    }

    // Paper Start
    /**
     * Gets the raw address of the player logging in
     * @return The address
     */
    @NotNull
    public InetAddress getRawAddress() {
        return rawAddress;
    }
    // Paper end

    @Deprecated
    public AsyncPlayerPreLoginEvent(@NotNull final String name, @NotNull final InetAddress ipAddress, @NotNull final UUID uniqueId, @NotNull PlayerProfile profile) {
        this(name, ipAddress, ipAddress, uniqueId, profile);
    }

    @Deprecated // Paper - Add hostname
    public AsyncPlayerPreLoginEvent(@NotNull final String name, @NotNull final InetAddress ipAddress, @NotNull final InetAddress rawAddress, @NotNull final UUID uniqueId, @NotNull PlayerProfile profile) {
        // Paper start - Add hostname
        this(name, ipAddress, rawAddress, uniqueId, profile, "");
    }

    public AsyncPlayerPreLoginEvent(@NotNull final String name, @NotNull final InetAddress ipAddress, @NotNull final InetAddress rawAddress, @NotNull final UUID uniqueId, @NotNull PlayerProfile profile, @NotNull String hostname) {
        // Paper end - Add hostname
        super(true);
        this.profile = profile;
        // Paper end
        this.result = Result.ALLOWED;
        this.message = net.kyori.adventure.text.Component.empty(); // Paper
        //this.name = name; // Paper - Not used anymore
        this.ipAddress = ipAddress;
        this.rawAddress = rawAddress; // Paper
        //this.uniqueId = uniqueId; // Paper - Not used anymore
        this.hostname = hostname; // Paper - Add hostname
    }

    /**
     * Gets the current result of the login, as an enum
     *
     * @return Current Result of the login
     */
    @NotNull
    public Result getLoginResult() {
        return result;
    }

    /**
     * Gets the current result of the login, as an enum
     *
     * @return Current Result of the login
     * @see #getLoginResult()
     * @deprecated This method uses a deprecated enum from {@link
     *     PlayerPreLoginEvent}
     */
    @Deprecated
    @NotNull
    public PlayerPreLoginEvent.Result getResult() {
        return result == null ? null : result.old();
    }

    /**
     * Sets the new result of the login, as an enum
     *
     * @param result New result to set
     */
    public void setLoginResult(@NotNull final Result result) {
        this.result = result;
    }

    /**
     * Sets the new result of the login, as an enum
     *
     * @param result New result to set
     * @see #setLoginResult(Result)
     * @deprecated This method uses a deprecated enum from {@link
     *     PlayerPreLoginEvent}
     */
    @Deprecated
    public void setResult(@NotNull final PlayerPreLoginEvent.Result result) {
        this.result = result == null ? null : Result.valueOf(result.name());
    }

    // Paper start
    /**
     * Gets the current kick message that will be used if getResult() !=
     * Result.ALLOWED
     *
     * @return Current kick message
     */
    @NotNull
    public net.kyori.adventure.text.Component kickMessage() {
        return message;
    }

    /**
     * Sets the kick message to display if getResult() != Result.ALLOWED
     *
     * @param message New kick message
     */
    public void kickMessage(@NotNull final net.kyori.adventure.text.Component message) {
        this.message = message;
    }

    /**
     * Disallows the player from logging in, with the given reason
     *
     * @param result New result for disallowing the player
     * @param message Kick message to display to the user
     */
    public void disallow(@NotNull final Result result, @NotNull final net.kyori.adventure.text.Component message) {
        this.result = result;
        this.message = message;
    }

    /**
     * Disallows the player from logging in, with the given reason
     *
     * @param result New result for disallowing the player
     * @param message Kick message to display to the user
     * @deprecated This method uses a deprecated enum from {@link
     *     PlayerPreLoginEvent}
     * @see #disallow(Result, String)
     */
    @Deprecated
    public void disallow(@NotNull final PlayerPreLoginEvent.Result result, @NotNull final net.kyori.adventure.text.Component message) {
        this.result = result == null ? null : Result.valueOf(result.name());
        this.message = message;
    }
    // Paper end
    /**
     * Gets the current kick message that will be used if getResult() !=
     * Result.ALLOWED
     *
     * @return Current kick message
     * @deprecated in favour of {@link #kickMessage()}
     */
    @NotNull
    @Deprecated // Paper
    public String getKickMessage() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(this.message); // Paper
    }

    /**
     * Sets the kick message to display if getResult() != Result.ALLOWED
     *
     * @param message New kick message
     * @deprecated in favour of {@link #kickMessage(net.kyori.adventure.text.Component)}
     */
    @Deprecated // Paper
    public void setKickMessage(@NotNull final String message) {
        this.message = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message); // Paper
    }

    /**
     * Allows the player to log in
     */
    public void allow() {
        result = Result.ALLOWED;
        message = net.kyori.adventure.text.Component.empty(); // Paper
    }

    /**
     * Disallows the player from logging in, with the given reason
     *
     * @param result New result for disallowing the player
     * @param message Kick message to display to the user
     * @deprecated in favour of {@link #disallow(org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result, net.kyori.adventure.text.Component)}
     */
    @Deprecated // Paper
    public void disallow(@NotNull final Result result, @NotNull final String message) {
        this.result = result;
        this.message = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message); // Paper
    }

    /**
     * Disallows the player from logging in, with the given reason
     *
     * @param result New result for disallowing the player
     * @param message Kick message to display to the user
     * @see #disallow(Result, String)
     * @deprecated This method uses a deprecated enum from {@link
     *     PlayerPreLoginEvent}
     */
    @Deprecated
    public void disallow(@NotNull final PlayerPreLoginEvent.Result result, @NotNull final String message) {
        this.result = result == null ? null : Result.valueOf(result.name());
        this.message = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message); // Paper
    }

    /**
     * Gets the player's name.
     *
     * @return the player's name
     */
    @NotNull
    public String getName() {
        return profile.getName(); // Paper
    }

    /**
     * Gets the player IP address.
     *
     * @return The IP address
     */
    @NotNull
    public InetAddress getAddress() {
        return ipAddress;
    }

    /**
     * Gets the player's unique ID.
     *
     * @return The unique ID
     */
    @NotNull
    public UUID getUniqueId() {
        return profile.getId(); // Paper
    }

    // Paper start
    /**
     * Gets the hostname that the player used to connect to the server, or
     * blank if unknown
     *
     * @return The hostname
     */
    @NotNull
    public String getHostname() {
        return hostname;
    }
    // Paper end

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Basic kick reasons for communicating to plugins
     */
    public enum Result {

        /**
         * The player is allowed to log in
         */
        ALLOWED,
        /**
         * The player is not allowed to log in, due to the server being full
         */
        KICK_FULL,
        /**
         * The player is not allowed to log in, due to them being banned
         */
        KICK_BANNED,
        /**
         * The player is not allowed to log in, due to them not being on the
         * white list
         */
        KICK_WHITELIST,
        /**
         * The player is not allowed to log in, for reasons undefined
         */
        KICK_OTHER;

        @Deprecated
        @NotNull
        private PlayerPreLoginEvent.Result old() {
            return PlayerPreLoginEvent.Result.valueOf(name());
        }
    }
}
