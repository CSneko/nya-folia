/*
 * Copyright (c) 2017 - Daniel Ennis (Aikar) - MIT License
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

package com.destroystokyo.paper.event.profile;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fires when the server needs to verify if a player is whitelisted.
 *
 * Plugins may override/control the servers whitelist with this event,
 * and dynamically change the kick message.
 */
public class ProfileWhitelistVerifyEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    @NotNull private final PlayerProfile profile;
    private final boolean whitelistEnabled;
    private boolean whitelisted;
    private final boolean isOp;
    @Nullable private Component kickMessage;

    @Deprecated
    public ProfileWhitelistVerifyEvent(@NotNull final PlayerProfile profile, boolean whitelistEnabled, boolean whitelisted, boolean isOp, @Nullable String kickMessage) {
        this(profile, whitelistEnabled, whitelisted, isOp, kickMessage == null ? null : LegacyComponentSerializer.legacySection().deserialize(kickMessage));
    }

    public ProfileWhitelistVerifyEvent(@NotNull final PlayerProfile profile, boolean whitelistEnabled, boolean whitelisted, boolean isOp, @Nullable Component kickMessage) {
        this.profile = profile;
        this.whitelistEnabled = whitelistEnabled;
        this.whitelisted = whitelisted;
        this.isOp = isOp;
        this.kickMessage = kickMessage;
    }

    /**
     * @return the currently planned message to send to the user if they are not whitelisted
     * @deprecated use {@link #kickMessage()}
     */
    @Deprecated
    @Nullable
    public String getKickMessage() {
        return this.kickMessage == null ? null : LegacyComponentSerializer.legacySection().serialize(kickMessage);
    }

    /**
     * @param kickMessage The message to send to the player on kick if not whitelisted. May set to null to use the server configured default
     * @deprecated Use {@link #kickMessage(Component)}
     */
    @Deprecated
    public void setKickMessage(@Nullable String kickMessage) {
        this.kickMessage(kickMessage == null ? null : LegacyComponentSerializer.legacySection().deserialize(kickMessage));
    }

    /**
     * @return the currently planned message to send to the user if they are not whitelisted
     */
    @Nullable
    public Component kickMessage() {
        return this.kickMessage;
    }

    /**
     * @param kickMessage The message to send to the player on kick if not whitelisted. May set to null to use the server configured default
     */
    public void kickMessage(@Nullable Component kickMessage) {
        this.kickMessage = kickMessage;
    }

    /**
     * @return The profile of the player trying to connect
     */
    @NotNull
    public PlayerProfile getPlayerProfile() {
        return profile;
    }

    /**
     * @return Whether the player is whitelisted to play on this server (whitelist may be off is why its true)
     */
    public boolean isWhitelisted() {
        return whitelisted;
    }

    /**
     * Changes the players whitelisted state. false will deny the login
     * @param whitelisted The new whitelisted state
     */
    public void setWhitelisted(boolean whitelisted) {
        this.whitelisted = whitelisted;
    }

    /**
     * @return if the player obtained whitelist status by having op
     */
    public boolean isOp() {
        return isOp;
    }

    /**
     * @return if the server even has whitelist on
     */
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
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
