package com.destroystokyo.paper.event.player;

import com.google.common.base.Preconditions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * This event is fired during a player handshake.
 *
 * <p>If there are no listeners listening to this event, the logic default
 * to your server platform will be ran.</p>
 *
 * <p>WARNING: TAMPERING WITH THIS EVENT CAN BE DANGEROUS</p>
 */
public class PlayerHandshakeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    @NotNull private final String originalHandshake;
    @NotNull private final String originalSocketAddressHostname;
    private boolean cancelled;
    @Nullable private String serverHostname;
    @Nullable private String socketAddressHostname;
    @Nullable private UUID uniqueId;
    @Nullable private String propertiesJson;
    private boolean failed;
    private Component failMessage = Component.text("If you wish to use IP forwarding, please enable it in your BungeeCord config as well!", NamedTextColor.YELLOW);

    /**
     * Creates a new {@link PlayerHandshakeEvent}.
     *
     * @param originalHandshake the original handshake string
     * @param cancelled if this event is enabled
     *
     * @deprecated in favour of {@link PlayerHandshakeEvent(String, String, boolean)}
     */
    @Deprecated
    public PlayerHandshakeEvent(@NotNull String originalHandshake, boolean cancelled) {
        this(originalHandshake, "127.0.0.1", cancelled);
    }

    /**
     * Creates a new {@link PlayerHandshakeEvent}.
     *
     * @param originalHandshake the original handshake string
     * @param originalSocketAddressHostname the original socket address hostname
     * @param cancelled if this event is enabled
     */
    public PlayerHandshakeEvent(@NotNull String originalHandshake, @NotNull String originalSocketAddressHostname, boolean cancelled) {
        super(true);
        this.originalHandshake = originalHandshake;
        this.originalSocketAddressHostname = originalSocketAddressHostname;
        this.cancelled = cancelled;
    }

    /**
     * Determines if this event is cancelled.
     *
     * <p>When this event is cancelled, custom handshake logic will not
     * be processed.</p>
     *
     * @return {@code true} if this event is cancelled, {@code false} otherwise
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Sets if this event is cancelled.
     *
     * <p>When this event is cancelled, custom handshake logic will not
     * be processed.</p>
     *
     * @param cancelled {@code true} if this event is cancelled, {@code false} otherwise
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Gets the original handshake string.
     *
     * @return the original handshake string
     */
    @NotNull
    public String getOriginalHandshake() {
        return this.originalHandshake;
    }

    /**
     * Gets the original socket address hostname.
     *
     * <p>This does not include the port.</p>
     * <p>In cases where this event is manually fired and the plugin wasn't updated yet, the default is {@code "127.0.0.1"}.</p>
     *
     * @return the original socket address hostname
     */
    @NotNull
    public String getOriginalSocketAddressHostname() {
        return this.originalSocketAddressHostname;
    }

    /**
     * Gets the server hostname string.
     *
     * <p>This should not include the port.</p>
     *
     * @return the server hostname string
     */
    @Nullable
    public String getServerHostname() {
        return this.serverHostname;
    }

    /**
     * Sets the server hostname string.
     *
     * <p>This should not include the port.</p>
     *
     * @param serverHostname the server hostname string
     */
    public void setServerHostname(@NotNull String serverHostname) {
        this.serverHostname = serverHostname;
    }

    /**
     * Gets the socket address hostname string.
     *
     * <p>This should not include the port.</p>
     *
     * @return the socket address hostname string
     */
    @Nullable
    public String getSocketAddressHostname() {
        return this.socketAddressHostname;
    }

    /**
     * Sets the socket address hostname string.
     *
     * <p>This should not include the port.</p>
     *
     * @param socketAddressHostname the socket address hostname string
     */
    public void setSocketAddressHostname(@NotNull String socketAddressHostname) {
        this.socketAddressHostname = socketAddressHostname;
    }

    /**
     * Gets the unique id.
     *
     * @return the unique id
     */
    @Nullable
    public UUID getUniqueId() {
        return this.uniqueId;
    }

    /**
     * Sets the unique id.
     *
     * @param uniqueId the unique id
     */
    public void setUniqueId(@NotNull UUID uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Gets the profile properties.
     *
     * <p>This should be a valid JSON string.</p>
     *
     * @return the profile properties, as JSON
     */
    @Nullable
    public String getPropertiesJson() {
        return this.propertiesJson;
    }

    /**
     * Determines if authentication failed.
     *
     * <p>When {@code true}, the client connecting will be disconnected
     * with the {@link #getFailMessage() fail message}.</p>
     *
     * @return {@code true} if authentication failed, {@code false} otherwise
     */
    public boolean isFailed() {
        return this.failed;
    }

    /**
     * Sets if authentication failed and the client should be disconnected.
     *
     * <p>When {@code true}, the client connecting will be disconnected
     * with the {@link #getFailMessage() fail message}.</p>
     *
     * @param failed {@code true} if authentication failed, {@code false} otherwise
     */
    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    /**
     * Sets the profile properties.
     *
     * <p>This should be a valid JSON string.</p>
     *
     * @param propertiesJson the profile properties, as JSON
     */
    public void setPropertiesJson(@NotNull String propertiesJson) {
        this.propertiesJson = propertiesJson;
    }

    /**
     * Gets the message to display to the client when authentication fails.
     *
     * @return the message to display to the client
     */
    @NotNull
    public Component failMessage() {
        return this.failMessage;
    }

    /**
     * Sets the message to display to the client when authentication fails.
     *
     * @param failMessage the message to display to the client
     */
    public void failMessage(@NotNull Component failMessage) {
        this.failMessage = failMessage;
    }

    /**
     * Gets the message to display to the client when authentication fails.
     *
     * @return the message to display to the client
     * @deprecated use {@link #failMessage()}
     */
    @NotNull
    @Deprecated
    public String getFailMessage() {
        return LegacyComponentSerializer.legacySection().serialize(this.failMessage());
    }

    /**
     * Sets the message to display to the client when authentication fails.
     *
     * @param failMessage the message to display to the client
     * @deprecated use {@link #failMessage(Component)}
     */
    @Deprecated
    public void setFailMessage(@NotNull String failMessage) {
        Preconditions.checkArgument(failMessage != null && !failMessage.isEmpty(), "fail message cannot be null or empty");
        this.failMessage(LegacyComponentSerializer.legacySection().deserialize(failMessage));
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
