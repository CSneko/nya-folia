// mc-dev import
package net.minecraft.stats;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class ServerStatsCounter extends StatsCounter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final File file;
    private final Set<Stat<?>> dirty = Sets.newHashSet();

    public ServerStatsCounter(MinecraftServer server, File file) {
        this.server = server;
        this.file = file;
        if (file.isFile()) {
            try {
                this.parseLocal(server.getFixerUpper(), FileUtils.readFileToString(file));
            } catch (IOException ioexception) {
                ServerStatsCounter.LOGGER.error("Couldn't read statistics file {}", file, ioexception);
            } catch (JsonParseException jsonparseexception) {
                ServerStatsCounter.LOGGER.error("Couldn't parse statistics file {}", file, jsonparseexception);
            }
        }

        // Spigot start // Paper start - moved after stat fetching for player state file.
        for ( Map.Entry<ResourceLocation, Integer> entry : org.spigotmc.SpigotConfig.forcedStats.entrySet() )
        {
            Stat<ResourceLocation> wrapper = Stats.CUSTOM.get(java.util.Objects.requireNonNull(BuiltInRegistries.CUSTOM_STAT.get(entry.getKey()))); // Paper - ensured by SpigotConfig#stats
            this.stats.put( wrapper, entry.getValue().intValue() );
        }
        // Spigot end // Paper end - moved after stat fetching for player state file.
    }

    public void save() {
        if ( org.spigotmc.SpigotConfig.disableStatSaving ) return; // Spigot
        try {
            FileUtils.writeStringToFile(this.file, this.toJson());
        } catch (IOException ioexception) {
            ServerStatsCounter.LOGGER.error("Couldn't save stats", ioexception);
        }

    }

    @Override
    public void setValue(Player player, Stat<?> stat, int value) {
        if ( org.spigotmc.SpigotConfig.disableStatSaving ) return; // Spigot
        if (stat.getType() == Stats.CUSTOM && stat.getValue() instanceof final ResourceLocation resourceLocation && org.spigotmc.SpigotConfig.forcedStats.get(resourceLocation) != null) return; // Paper - disable saving forced stats
        super.setValue(player, stat, value);
        this.dirty.add(stat);
    }

    private Set<Stat<?>> getDirty() {
        Set<Stat<?>> set = Sets.newHashSet(this.dirty);

        this.dirty.clear();
        return set;
    }

    public void parseLocal(DataFixer dataFixer, String json) {
        try {
            JsonReader jsonreader = new JsonReader(new StringReader(json));

            label48:
            {
                try {
                    jsonreader.setLenient(false);
                    JsonElement jsonelement = Streams.parse(jsonreader);

                    if (!jsonelement.isJsonNull()) {
                        CompoundTag nbttagcompound = ServerStatsCounter.fromJson(jsonelement.getAsJsonObject());

                        nbttagcompound = DataFixTypes.STATS.updateToCurrentVersion(dataFixer, nbttagcompound, NbtUtils.getDataVersion(nbttagcompound, 1343));
                        if (!nbttagcompound.contains("stats", 10)) {
                            break label48;
                        }

                        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("stats");
                        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

                        while (true) {
                            if (!iterator.hasNext()) {
                                break label48;
                            }

                            String s1 = (String) iterator.next();

                            if (nbttagcompound1.contains(s1, 10)) {
                                Util.ifElse(BuiltInRegistries.STAT_TYPE.getOptional(new ResourceLocation(s1)), (statisticwrapper) -> {
                                    CompoundTag nbttagcompound2 = nbttagcompound1.getCompound(s1);
                                    Iterator iterator1 = nbttagcompound2.getAllKeys().iterator();

                                    while (iterator1.hasNext()) {
                                        String s2 = (String) iterator1.next();

                                        if (nbttagcompound2.contains(s2, 99)) {
                                            Util.ifElse(this.getStat(statisticwrapper, s2), (statistic) -> {
                                                this.stats.put(statistic, nbttagcompound2.getInt(s2));
                                            }, () -> {
                                                ServerStatsCounter.LOGGER.warn("Invalid statistic in {}: Don't know what {} is", this.file, s2);
                                            });
                                        } else {
                                            ServerStatsCounter.LOGGER.warn("Invalid statistic value in {}: Don't know what {} is for key {}", new Object[]{this.file, nbttagcompound2.get(s2), s2});
                                        }
                                    }

                                }, () -> {
                                    ServerStatsCounter.LOGGER.warn("Invalid statistic type in {}: Don't know what {} is", this.file, s1);
                                });
                            }
                        }
                    }

                    ServerStatsCounter.LOGGER.error("Unable to parse Stat data from {}", this.file);
                } catch (Throwable throwable) {
                    try {
                        jsonreader.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                jsonreader.close();
                return;
            }

            jsonreader.close();
        } catch (IOException | JsonParseException jsonparseexception) {
            ServerStatsCounter.LOGGER.error("Unable to parse Stat data from {}", this.file, jsonparseexception);
        }

    }

    private <T> Optional<Stat<T>> getStat(StatType<T> type, String id) {
        // CraftBukkit - decompile error start
        Optional<ResourceLocation> optional = Optional.ofNullable(ResourceLocation.tryParse(id));
        Registry<T> iregistry = type.getRegistry();

        return optional.flatMap(iregistry::getOptional).map(type::get);
        // CraftBukkit - decompile error end
    }

    private static CompoundTag fromJson(JsonObject json) {
        CompoundTag nbttagcompound = new CompoundTag();
        Iterator iterator = json.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, JsonElement> entry = (Entry) iterator.next();
            JsonElement jsonelement = (JsonElement) entry.getValue();

            if (jsonelement.isJsonObject()) {
                nbttagcompound.put((String) entry.getKey(), ServerStatsCounter.fromJson(jsonelement.getAsJsonObject()));
            } else if (jsonelement.isJsonPrimitive()) {
                JsonPrimitive jsonprimitive = jsonelement.getAsJsonPrimitive();

                if (jsonprimitive.isNumber()) {
                    nbttagcompound.putInt((String) entry.getKey(), jsonprimitive.getAsInt());
                }
            }
        }

        return nbttagcompound;
    }

    protected String toJson() {
        Map<StatType<?>, JsonObject> map = Maps.newHashMap();
        ObjectIterator objectiterator = this.stats.object2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Stat<?>> it_unimi_dsi_fastutil_objects_object2intmap_entry = (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry) objectiterator.next();
            Stat<?> statistic = (Stat) it_unimi_dsi_fastutil_objects_object2intmap_entry.getKey();

            ((JsonObject) map.computeIfAbsent(statistic.getType(), (statisticwrapper) -> {
                return new JsonObject();
            })).addProperty(ServerStatsCounter.getKey(statistic).toString(), it_unimi_dsi_fastutil_objects_object2intmap_entry.getIntValue());
        }

        JsonObject jsonobject = new JsonObject();
        Iterator iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<StatType<?>, JsonObject> entry = (Entry) iterator.next();

            jsonobject.add(BuiltInRegistries.STAT_TYPE.getKey((StatType) entry.getKey()).toString(), (JsonElement) entry.getValue());
        }

        JsonObject jsonobject1 = new JsonObject();

        jsonobject1.add("stats", jsonobject);
        jsonobject1.addProperty("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return jsonobject1.toString();
    }

    private static <T> ResourceLocation getKey(Stat<T> stat) {
        return stat.getType().getRegistry().getKey(stat.getValue());
    }

    public void markAllDirty() {
        this.dirty.addAll(this.stats.keySet());
    }

    public void sendStats(ServerPlayer player) {
        Object2IntMap<Stat<?>> object2intmap = new Object2IntOpenHashMap();
        Iterator iterator = this.getDirty().iterator();

        while (iterator.hasNext()) {
            Stat<?> statistic = (Stat) iterator.next();

            object2intmap.put(statistic, this.getValue(statistic));
        }

        player.connection.send(new ClientboundAwardStatsPacket(object2intmap));
    }
}
