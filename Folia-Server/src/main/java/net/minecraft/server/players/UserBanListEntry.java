// mc-dev import
package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.Date;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public class UserBanListEntry extends BanListEntry<GameProfile> {

    public UserBanListEntry(@Nullable GameProfile profile) {
        this(profile, (Date) null, (String) null, (Date) null, (String) null);
    }

    public UserBanListEntry(@Nullable GameProfile profile, @Nullable Date created, @Nullable String source, @Nullable Date expiry, @Nullable String reason) {
        super(profile, created, source, expiry, reason);
    }

    public UserBanListEntry(JsonObject json) {
        super(UserBanListEntry.createGameProfile(json), json);
    }

    @Override
    protected void serialize(JsonObject json) {
        if (this.getUser() != null) {
            json.addProperty("uuid", ((GameProfile) this.getUser()).getId().toString());
            json.addProperty("name", ((GameProfile) this.getUser()).getName());
            super.serialize(json);
        }
    }

    @Override
    public Component getDisplayName() {
        GameProfile gameprofile = (GameProfile) this.getUser();

        return gameprofile != null ? Component.literal(gameprofile.getName()) : Component.translatable("commands.banlist.entry.unknown");
    }

    @Nullable
    private static GameProfile createGameProfile(JsonObject json) {
        // Spigot start
        // this whole method has to be reworked to account for the fact Bukkit only accepts UUID bans and gives no way for usernames to be stored!
        UUID uuid = null;
        String name = null;
        if (json.has("uuid")) {
            String s = json.get("uuid").getAsString();

            try {
                uuid = UUID.fromString(s);
            } catch (Throwable throwable) {
            }

        }
        if ( json.has("name"))
        {
            name = json.get("name").getAsString();
        }
        if ( uuid != null || name != null )
        {
            return new GameProfile( uuid, name );
        } else {
            return null;
        }
        // Spigot End
    }
}
