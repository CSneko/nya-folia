package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)); // Folia - region threading - SDF is not thread-safe
    public static final String EXPIRES_NEVER = "forever";
    protected final Date created;
    protected final String source;
    @Nullable
    protected final Date expires;
    protected final String reason;

    public BanListEntry(@Nullable T key, @Nullable Date creationDate, @Nullable String source, @Nullable Date expiryDate, @Nullable String reason) {
        super(key);
        this.created = creationDate == null ? new Date() : creationDate;
        this.source = source == null ? "(Unknown)" : source;
        this.expires = expiryDate;
        this.reason = reason == null ? "Banned by an operator." : reason;
    }

    protected BanListEntry(@Nullable T key, JsonObject json) {
        super(BanListEntry.checkExpiry(key, json)); // CraftBukkit

        Date date;

        try {
            date = json.has("created") ? BanListEntry.DATE_FORMAT.get().parse(json.get("created").getAsString()) : new Date(); // Folia - region threading - SDF is not thread-safe
        } catch (ParseException parseexception) {
            date = new Date();
        }

        this.created = date;
        this.source = json.has("source") ? json.get("source").getAsString() : "(Unknown)";

        Date date1;

        try {
            date1 = json.has("expires") ? BanListEntry.DATE_FORMAT.get().parse(json.get("expires").getAsString()) : null; // Folia - region threading - SDF is not thread-safe
        } catch (ParseException parseexception1) {
            date1 = null;
        }

        this.expires = date1;
        this.reason = json.has("reason") ? json.get("reason").getAsString() : "Banned by an operator.";
    }

    public Date getCreated() {
        return this.created;
    }

    public String getSource() {
        return this.source;
    }

    @Nullable
    public Date getExpires() {
        return this.expires;
    }

    public String getReason() {
        return this.reason;
    }

    public abstract Component getDisplayName();

    @Override
    boolean hasExpired() {
        return this.expires == null ? false : this.expires.before(new Date());
    }

    @Override
    protected void serialize(JsonObject json) {
        json.addProperty("created", BanListEntry.DATE_FORMAT.get().format(this.created)); // Folia - region threading - SDF is not thread-safe
        json.addProperty("source", this.source);
        json.addProperty("expires", this.expires == null ? "forever" : BanListEntry.DATE_FORMAT.get().format(this.expires)); // Folia - region threading - SDF is not thread-safe
        json.addProperty("reason", this.reason);
    }

    // CraftBukkit start
    private static <T> T checkExpiry(T object, JsonObject jsonobject) {
        Date expires = null;

        try {
            expires = jsonobject.has("expires") ? BanListEntry.DATE_FORMAT.get().parse(jsonobject.get("expires").getAsString()) : null; // Folia - region threading - SDF is not thread-safe
        } catch (ParseException ex) {
            // Guess we don't have a date
        }

        if (expires == null || expires.after(new Date())) {
            return object;
        } else {
            return null;
        }
    }
    // CraftBukkit end
}
