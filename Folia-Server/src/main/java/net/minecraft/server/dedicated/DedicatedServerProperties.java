package net.minecraft.server.dedicated;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

// CraftBukkit start
import joptsimple.OptionSet;
// CraftBukkit end

public class DedicatedServerProperties extends Settings<DedicatedServerProperties> {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();
    public final boolean debug = this.get("debug", false); // CraftBukkit
    public final boolean onlineMode = this.get("online-mode", true);
    public final boolean preventProxyConnections = this.get("prevent-proxy-connections", false);
    public final String serverIp = this.get("server-ip", "");
    public final boolean spawnAnimals = this.get("spawn-animals", true);
    public final boolean spawnNpcs = this.get("spawn-npcs", true);
    public final boolean pvp = this.get("pvp", true);
    public final boolean allowFlight = this.get("allow-flight", false);
    public final String motd = this.get("motd", "A Minecraft Server");
    public final boolean forceGameMode = this.get("force-gamemode", false);
    public final boolean enforceWhitelist = this.get("enforce-whitelist", false);
    public final Difficulty difficulty;
    public final GameType gamemode;
    public final String levelName;
    public final int serverPort;
    @Nullable
    public final Boolean announcePlayerAchievements;
    public final boolean enableQuery;
    public final int queryPort;
    public final boolean enableRcon;
    public final int rconPort;
    public final String rconPassword;
    public final boolean hardcore;
    public final boolean allowNether;
    public final boolean spawnMonsters;
    public final boolean useNativeTransport;
    public final boolean enableCommandBlock;
    public final int spawnProtection;
    public final int opPermissionLevel;
    public final int functionPermissionLevel;
    public final long maxTickTime;
    public final int maxChainedNeighborUpdates;
    public final int rateLimitPacketsPerSecond;
    public final int viewDistance;
    public final int simulationDistance;
    public final int maxPlayers;
    public final int networkCompressionThreshold;
    public final boolean broadcastRconToOps;
    public final boolean broadcastConsoleToOps;
    public final int maxWorldSize;
    public final boolean syncChunkWrites;
    public final boolean enableJmxMonitoring;
    public final boolean enableStatus;
    public final boolean hideOnlinePlayers;
    public final int entityBroadcastRangePercentage;
    public final String textFilteringConfig;
    public final Optional<MinecraftServer.ServerResourcePackInfo> serverResourcePackInfo;
    public final DataPackConfig initialDataPackConfiguration;
    public final Settings<DedicatedServerProperties>.MutableValue<Integer> playerIdleTimeout;
    public final Settings<DedicatedServerProperties>.MutableValue<Boolean> whiteList;
    public final boolean enforceSecureProfile;
    public final boolean logIPs;
    private final DedicatedServerProperties.WorldDimensionData worldDimensionData;
    public final WorldOptions worldOptions;

    public final String rconIp; // Paper - Add rcon ip

    // CraftBukkit start
    public DedicatedServerProperties(Properties properties, OptionSet optionset) {
        super(properties, optionset);
        // CraftBukkit end
        this.difficulty = (Difficulty) this.get("difficulty", dispatchNumberOrString(Difficulty::byId, Difficulty::byName), Difficulty::getKey, Difficulty.EASY);
        this.gamemode = (GameType) this.get("gamemode", dispatchNumberOrString(GameType::byId, GameType::byName), GameType::getName, GameType.SURVIVAL);
        this.levelName = this.get("level-name", "world");
        this.serverPort = this.get("server-port", 25565);
        this.announcePlayerAchievements = this.getLegacyBoolean("announce-player-achievements");
        this.enableQuery = this.get("enable-query", false);
        this.queryPort = this.get("query.port", 25565);
        this.enableRcon = this.get("enable-rcon", false);
        this.rconPort = this.get("rcon.port", 25575);
        this.rconPassword = this.get("rcon.password", "");
        this.hardcore = this.get("hardcore", false);
        this.allowNether = this.get("allow-nether", true);
        this.spawnMonsters = this.get("spawn-monsters", true);
        this.useNativeTransport = this.get("use-native-transport", true);
        this.enableCommandBlock = this.get("enable-command-block", false);
        this.spawnProtection = this.get("spawn-protection", 16);
        this.opPermissionLevel = this.get("op-permission-level", 4);
        this.functionPermissionLevel = this.get("function-permission-level", 2);
        this.maxTickTime = this.get("max-tick-time", TimeUnit.MINUTES.toMillis(1L));
        this.maxChainedNeighborUpdates = this.get("max-chained-neighbor-updates", 1000000);
        this.rateLimitPacketsPerSecond = this.get("rate-limit", 0);
        this.viewDistance = this.get("view-distance", 10);
        this.simulationDistance = this.get("simulation-distance", 10);
        this.maxPlayers = this.get("max-players", 20);
        this.networkCompressionThreshold = this.get("network-compression-threshold", 256);
        this.broadcastRconToOps = this.get("broadcast-rcon-to-ops", true);
        this.broadcastConsoleToOps = this.get("broadcast-console-to-ops", true);
        this.maxWorldSize = this.get("max-world-size", (integer) -> {
            return Mth.clamp(integer, 1, 29999984);
        }, 29999984);
        this.syncChunkWrites = this.get("sync-chunk-writes", true) && Boolean.getBoolean("Paper.enable-sync-chunk-writes"); // Paper - hide behind flag
        this.enableJmxMonitoring = this.get("enable-jmx-monitoring", false);
        this.enableStatus = this.get("enable-status", true);
        this.hideOnlinePlayers = this.get("hide-online-players", false);
        this.entityBroadcastRangePercentage = this.get("entity-broadcast-range-percentage", (integer) -> {
            return Mth.clamp(integer, 10, 1000);
        }, 100);
        this.textFilteringConfig = this.get("text-filtering-config", "");
        this.playerIdleTimeout = this.getMutable("player-idle-timeout", 0);
        this.whiteList = this.getMutable("white-list", false);
        this.enforceSecureProfile = this.get("enforce-secure-profile", true);
        this.logIPs = this.get("log-ips", true);
        String s = this.get("level-seed", "");
        boolean flag = this.get("generate-structures", true);
        long i = WorldOptions.parseSeed(s).orElse(WorldOptions.randomSeed());

        this.worldOptions = new WorldOptions(i, flag, false);
        this.worldDimensionData = new DedicatedServerProperties.WorldDimensionData((JsonObject) this.get("generator-settings", (s1) -> {
            return GsonHelper.parse(!s1.isEmpty() ? s1 : "{}");
        }, new JsonObject()), (String) this.get("level-type", (s1) -> {
            return s1.toLowerCase(Locale.ROOT);
        }, WorldPresets.NORMAL.location().toString()));
        this.serverResourcePackInfo = DedicatedServerProperties.getServerPackInfo(this.get("resource-pack", ""), this.get("resource-pack-sha1", ""), this.getLegacyString("resource-pack-hash"), this.get("require-resource-pack", false), this.get("resource-pack-prompt", ""));
        this.initialDataPackConfiguration = DedicatedServerProperties.getDatapackConfig(this.get("initial-enabled-packs", String.join(",", WorldDataConfiguration.DEFAULT.dataPacks().getEnabled())), this.get("initial-disabled-packs", String.join(",", WorldDataConfiguration.DEFAULT.dataPacks().getDisabled())));
        // Paper start - Configurable rcon ip
        final String rconIp = this.getStringRaw("rcon.ip");
        this.rconIp = rconIp == null ? this.serverIp : rconIp;
        // Paper end
    }

    // CraftBukkit start
    public static DedicatedServerProperties fromFile(Path path, OptionSet optionset) {
        return new DedicatedServerProperties(loadFromFile(path), optionset);
    }

    @Override
    public DedicatedServerProperties reload(RegistryAccess iregistrycustom, Properties properties, OptionSet optionset) {
        return new DedicatedServerProperties(properties, optionset);
        // CraftBukkit end
    }

    @Nullable
    private static Component parseResourcePackPrompt(String prompt) {
        if (!Strings.isNullOrEmpty(prompt)) {
            try {
                return Component.Serializer.fromJson(prompt);
            } catch (Exception exception) {
                DedicatedServerProperties.LOGGER.warn("Failed to parse resource pack prompt '{}'", prompt, exception);
            }
        }

        return null;
    }

    private static Optional<MinecraftServer.ServerResourcePackInfo> getServerPackInfo(String url, String sha1, @Nullable String hash, boolean required, String prompt) {
        if (url.isEmpty()) {
            return Optional.empty();
        } else {
            String s4;

            if (!sha1.isEmpty()) {
                s4 = sha1;
                if (!Strings.isNullOrEmpty(hash)) {
                    DedicatedServerProperties.LOGGER.warn("resource-pack-hash is deprecated and found along side resource-pack-sha1. resource-pack-hash will be ignored.");
                }
            } else if (!Strings.isNullOrEmpty(hash)) {
                DedicatedServerProperties.LOGGER.warn("resource-pack-hash is deprecated. Please use resource-pack-sha1 instead.");
                s4 = hash;
            } else {
                s4 = "";
            }

            if (s4.isEmpty()) {
                DedicatedServerProperties.LOGGER.warn("You specified a resource pack without providing a sha1 hash. Pack will be updated on the client only if you change the name of the pack.");
            } else if (!DedicatedServerProperties.SHA1.matcher(s4).matches()) {
                DedicatedServerProperties.LOGGER.warn("Invalid sha1 for resource-pack-sha1");
            }

            Component ichatbasecomponent = DedicatedServerProperties.parseResourcePackPrompt(prompt);

            return Optional.of(new MinecraftServer.ServerResourcePackInfo(url, s4, required, ichatbasecomponent));
        }
    }

    private static DataPackConfig getDatapackConfig(String enabled, String disabled) {
        List<String> list = DedicatedServerProperties.COMMA_SPLITTER.splitToList(enabled);
        List<String> list1 = DedicatedServerProperties.COMMA_SPLITTER.splitToList(disabled);

        return new DataPackConfig(list, list1);
    }

    private static FeatureFlagSet getFeatures(String featureFlags) {
        return FeatureFlags.REGISTRY.fromNames((Iterable) DedicatedServerProperties.COMMA_SPLITTER.splitToStream(featureFlags).mapMulti((s1, consumer) -> {
            ResourceLocation minecraftkey = ResourceLocation.tryParse(s1);

            if (minecraftkey == null) {
                DedicatedServerProperties.LOGGER.warn("Invalid resource location {}, ignoring", s1);
            } else {
                consumer.accept(minecraftkey);
            }

        }).collect(Collectors.toList()));
    }

    public WorldDimensions createDimensions(RegistryAccess dynamicRegistry) {
        return this.worldDimensionData.create(dynamicRegistry);
    }

    public static record WorldDimensionData(JsonObject generatorSettings, String levelType) {

        private static final Map<String, ResourceKey<WorldPreset>> LEGACY_PRESET_NAMES = Map.of("default", WorldPresets.NORMAL, "largebiomes", WorldPresets.LARGE_BIOMES);

        public WorldDimensions create(RegistryAccess dynamicRegistryManager) {
            Registry<WorldPreset> iregistry = dynamicRegistryManager.registryOrThrow(Registries.WORLD_PRESET);
            Holder.Reference<WorldPreset> holder_c = (Holder.Reference) iregistry.getHolder(WorldPresets.NORMAL).or(() -> {
                return iregistry.holders().findAny();
            }).orElseThrow(() -> {
                return new IllegalStateException("Invalid datapack contents: can't find default preset");
            });
            Optional<ResourceKey<WorldPreset>> optional = Optional.ofNullable(ResourceLocation.tryParse(this.levelType)).map((minecraftkey) -> { // CraftBukkit - decompile error
                return ResourceKey.create(Registries.WORLD_PRESET, minecraftkey);
            }).or(() -> {
                return Optional.ofNullable(DedicatedServerProperties.WorldDimensionData.LEGACY_PRESET_NAMES.get(this.levelType)); // CraftBukkit - decompile error
            });

            Objects.requireNonNull(iregistry);
            Holder<WorldPreset> holder = (Holder) optional.flatMap(iregistry::getHolder).orElseGet(() -> {
                DedicatedServerProperties.LOGGER.warn("Failed to parse level-type {}, defaulting to {}", this.levelType, holder_c.key().location());
                return holder_c;
            });
            WorldDimensions worlddimensions = ((WorldPreset) holder.value()).createWorldDimensions();

            if (holder.is(WorldPresets.FLAT)) {
                RegistryOps<JsonElement> registryops = RegistryOps.create(JsonOps.INSTANCE, (HolderLookup.Provider) dynamicRegistryManager);
                DataResult<FlatLevelGeneratorSettings> dataresult = FlatLevelGeneratorSettings.CODEC.parse(new Dynamic(registryops, this.generatorSettings())); // CraftBukkit - decompile error
                Logger logger = DedicatedServerProperties.LOGGER;

                Objects.requireNonNull(logger);
                Optional<FlatLevelGeneratorSettings> optional1 = dataresult.resultOrPartial(logger::error);

                if (optional1.isPresent()) {
                    return worlddimensions.replaceOverworldGenerator(dynamicRegistryManager, new FlatLevelSource((FlatLevelGeneratorSettings) optional1.get()));
                }
            }

            return worlddimensions;
        }
    }
}
