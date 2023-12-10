package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.Objects;

public class UserWhiteList extends StoredUserList<GameProfile, UserWhiteListEntry> {
    public UserWhiteList(File file) {
        super(file);
    }

    @Override
    protected StoredUserEntry<GameProfile> createEntry(JsonObject json) {
        return new UserWhiteListEntry(json);
    }

    public boolean isWhiteListed(GameProfile profile) {
        return this.contains(profile);
    }

    @Override
    public String[] getUserList() {
        return this.getEntries().stream().map(StoredUserEntry::getUser).filter(Objects::nonNull).map(GameProfile::getName).toArray((i) -> {
            return new String[i];
        });
    }

    @Override
    protected String getKeyForUser(GameProfile gameProfile) {
        return gameProfile.getId().toString();
    }
    // Paper start - Add whitelist events
    @Override
    public void add(UserWhiteListEntry entry) {
        if (!new io.papermc.paper.event.server.WhitelistStateUpdateEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitCopy(entry.getUser()), io.papermc.paper.event.server.WhitelistStateUpdateEvent.WhitelistStatus.ADDED).callEvent()) {
            return;
        }

        super.add(entry);
    }

    @Override
    public void remove(GameProfile profile) {
        if (!new io.papermc.paper.event.server.WhitelistStateUpdateEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitCopy(profile), io.papermc.paper.event.server.WhitelistStateUpdateEvent.WhitelistStatus.REMOVED).callEvent()) {
            return;
        }

        super.remove(profile);
    }
    // Paper end
}
