// mc-dev import
package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType; // Paper
import java.lang.reflect.Type; // Paper
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final File file;
    // Paper - replace HashMap is ConcurrentHashMap
    private final Map<String, V> map = Maps.newConcurrentMap();
    private boolean e = true;
    private static final ParameterizedType f = new ParameterizedType() {
        public Type[] getActualTypeArguments() {
            return new Type[]{StoredUserEntry.class};
        }

        public Type getRawType() {
            return List.class;
        }

        public Type getOwnerType() {
            return null;
        }
    };

    public StoredUserList(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public void add(V entry) {
        this.map.put(this.getKeyForUser(entry.getUser()), entry);

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after adding a user.", ioexception);
        }

    }

    @Nullable
    public V get(K key) {
        // Paper start
        // this.g();
        // return (V) this.d.get(this.a(k0)); // CraftBukkit - fix decompile error
        return (V) this.map.computeIfPresent(this.getKeyForUser(key), (k, v) -> {
            return v.hasExpired() ? null : v;
        });
        // Paper end
    }

    public void remove(K key) {
        this.map.remove(this.getKeyForUser(key));

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after removing a user.", ioexception);
        }

    }

    public void remove(StoredUserEntry<K> entry) {
        this.remove(entry.getUser());
    }

    public String[] getUserList() {
        return (String[]) this.map.keySet().toArray(new String[0]);
    }

    public boolean isEmpty() {
        // return this.d.size() < 1; // Paper
        return this.map.isEmpty(); // Paper - readability is the goal. As an aside, isEmpty() uses only sumCount() and a comparison. size() uses sumCount(), casts, and boolean logic
    }

    protected String getKeyForUser(K profile) {
        return profile.toString();
    }

    protected boolean contains(K k0) {
        return this.map.containsKey(this.getKeyForUser(k0));
    }

    private void removeExpired() {
        /*List<K> list = Lists.newArrayList();
        Iterator iterator = this.d.values().iterator();

        while (iterator.hasNext()) {
            V v0 = (V) iterator.next(); // CraftBukkit - decompile error

            if (v0.hasExpired()) {
                list.add(v0.getKey());
            }
        }

        iterator = list.iterator();

        while (iterator.hasNext()) {
            K k0 = (K) iterator.next(); // CraftBukkit - decompile error

            this.d.remove(this.a(k0));
        }*/

        this.map.values().removeIf(StoredUserEntry::hasExpired);
        // Paper end
    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject json);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        synchronized (this) { // Folia - region threading
        this.removeExpired(); // Paper - remove expired values before saving
        JsonArray jsonarray = new JsonArray();
        Stream<JsonObject> stream = this.map.values().stream().map((jsonlistentry) -> { // CraftBukkit - decompile error
            JsonObject jsonobject = new JsonObject();

            Objects.requireNonNull(jsonlistentry);
            return (JsonObject) Util.make(jsonobject, jsonlistentry::serialize);
        });

        Objects.requireNonNull(jsonarray);
        stream.forEach(jsonarray::add);
        BufferedWriter bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8);

        try {
            StoredUserList.GSON.toJson(jsonarray, bufferedwriter);
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }
        } // Folia - region threading

    }

    public void load() throws IOException {
        synchronized (this) { // Folia - region threading
        if (this.file.exists()) {
            BufferedReader bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8);

            label54:
            {
                try {
                    this.map.clear();
                    JsonArray jsonarray = (JsonArray) StoredUserList.GSON.fromJson(bufferedreader, JsonArray.class);

                    if (jsonarray == null) {
                        break label54;
                    }

                    Iterator iterator = jsonarray.iterator();

                    while (iterator.hasNext()) {
                        JsonElement jsonelement = (JsonElement) iterator.next();
                        JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "entry");
                        StoredUserEntry<K> jsonlistentry = this.createEntry(jsonobject);

                        if (jsonlistentry.getUser() != null) {
                            this.map.put(this.getKeyForUser(jsonlistentry.getUser()), (V) jsonlistentry); // CraftBukkit - decompile error
                        }
                    }
                // Spigot Start
                } catch ( com.google.gson.JsonParseException | NullPointerException ex )
                {
                    org.bukkit.Bukkit.getLogger().log( java.util.logging.Level.WARNING, "Unable to read file " + this.file + ", backing it up to {0}.backup and creating new copy.", ex );
                    File backup = new File( this.file + ".backup" );
                    this.file.renameTo( backup );
                    this.file.delete();
                // Spigot End
                } catch (Throwable throwable) {
                    if (bufferedreader != null) {
                        try {
                            bufferedreader.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }
                    }

                    throw throwable;
                }

                if (bufferedreader != null) {
                    bufferedreader.close();
                }

                return;
            }

            if (bufferedreader != null) {
                bufferedreader.close();
            }

        }
        } // Folia - region threading
    }
}
