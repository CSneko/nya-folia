package net.minecraft.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import co.aikar.timings.Timings;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.loot.LootDataManager;
import org.slf4j.Logger;

// CraftBukkit start
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Random;
// import jline.console.ConsoleReader; // Paper
import joptsimple.OptionSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.Main;
import org.bukkit.event.server.ServerLoadEvent;
// CraftBukkit end

import co.aikar.timings.MinecraftTimings; // Paper

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, CommandSource, AutoCloseable {

    private static MinecraftServer SERVER; // Paper
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final net.kyori.adventure.text.logger.slf4j.ComponentLogger COMPONENT_LOGGER = net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(LOGGER.getName()); // Paper
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    public static final int MS_PER_TICK = 50;
    private static final int OVERLOADED_THRESHOLD = 2000;
    private static final int OVERLOADED_WARNING_INTERVAL = 15000;
    private static final long STATUS_EXPIRE_TIME_NS = 5000000000L;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    public static final int START_CHUNK_RADIUS = 11;
    private static final int START_TICKING_CHUNK_COUNT = 441;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings("Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
    private static final long DELAYED_TASKS_TICK_EXTENSION = 50L;
    public static final GameProfile ANONYMOUS_PLAYER_PROFILE = new GameProfile(Util.NIL_UUID, "Anonymous Player");
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder;
    private ProfilerFiller profiler;
    private Consumer<ProfileResults> onMetricsRecordingStopped;
    private Consumer<Path> onMetricsRecordingFinished;
    private boolean willStartRecordingMetrics;
    @Nullable
    private MinecraftServer.TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private ServerConnectionListener connection;
    public final ChunkProgressListenerFactory progressListenerFactory;
    @Nullable
    private ServerStatus status;
    @Nullable
    private ServerStatus.Favicon statusIcon;
    private final RandomSource random;
    public final DataFixer fixerUpper;
    private String localIp;
    private int port;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private Map<ResourceKey<Level>, ServerLevel> levels;
    private PlayerList playerList;
    private volatile boolean running;
    private volatile boolean isRestarting = false; // Paper - flag to signify we're attempting to restart
    private boolean stopped;
    // Folia - region threading
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    private net.kyori.adventure.text.Component motd; // Paper - Adventure
    private int playerIdleTimeout;
    public final long[] tickTimes;
    // Paper start
    public final TickTimes tickTimes5s = new TickTimes(100);
    public final TickTimes tickTimes10s = new TickTimes(200);
    public final TickTimes tickTimes60s = new TickTimes(1200);
    // Paper end
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarning;
    protected final Services services;
    private long lastServerStatus;
    public final Thread serverThread;
    private long nextTickTime;
    private long delayedTasksMaxNextTickTime;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard;
    @Nullable
    private CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents;
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private float averageTickTime;
    public final Executor executor;
    @Nullable
    private String serverId;
    public MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    protected WorldData worldData;
    private volatile boolean isSaving;

    // CraftBukkit start
    public final WorldLoader.DataLoadContext worldLoader;
    public org.bukkit.craftbukkit.CraftServer server;
    public OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    //public ConsoleReader reader; // Paper
    //public static int currentTick = 0; // Paper - Further improve tick loop // Folia - region threading
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    public Commands vanillaCommandDispatcher;
    public boolean forceTicks; // Paper
    // CraftBukkit end
    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / MinecraftServer.TPS;
    private static final int SAMPLE_INTERVAL = 20; // Paper
    public final double[] recentTps = new double[ 3 ];
    // Spigot end
    public final io.papermc.paper.configuration.PaperConfigurations paperConfigurations;
    //public static long currentTickLong = 0L; // Paper // Folia - threaded regions

    public volatile Thread shutdownThread; // Paper
    public volatile boolean abnormalExit = false; // Paper
    public boolean isIteratingOverLevels = false; // Paper
    // Paper start - lag compensation
    public static final long SERVER_INIT = System.nanoTime();
    // Paper end - lag compensation

    // Folia start - regionised ticking
    public final io.papermc.paper.threadedregions.RegionizedServer regionizedServer = new io.papermc.paper.threadedregions.RegionizedServer();

    @Override
    public void execute(Runnable runnable) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        super.execute(runnable);
    }

    @Override
    public void executeBlocking(Runnable runnable) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        super.executeBlocking(runnable);
    }

    @Override
    public void tell(TickTask runnable) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        super.tell(runnable);
    }
    // Folia end - regionised ticking

    public static <S extends MinecraftServer> S spin(Function<Thread, S> serverFactory) {
        AtomicReference<S> atomicreference = new AtomicReference();
        Thread thread = new io.papermc.paper.util.TickThread(() -> { // Paper - rewrite chunk system
            ((MinecraftServer) atomicreference.get()).runServer();
        }, "Server thread");

        thread.setUncaughtExceptionHandler((thread1, throwable) -> {
            MinecraftServer.LOGGER.error("Uncaught exception in server thread", throwable);
        });
        thread.setPriority(Thread.NORM_PRIORITY+2); // Paper - boost priority
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S s0 = serverFactory.apply(thread); // CraftBukkit - decompile error

        atomicreference.set(s0);
        thread.start();
        return s0;
    }

    public MinecraftServer(OptionSet options, WorldLoader.DataLoadContext worldLoader, Thread thread, LevelStorageSource.LevelStorageAccess convertable_conversionsession, PackRepository resourcepackrepository, WorldStem worldstem, Proxy proxy, DataFixer datafixer, Services services, ChunkProgressListenerFactory worldloadlistenerfactory) {
        super("Server");
        SERVER = this; // Paper - better singleton
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
        this.profiler = this.metricsRecorder.getProfiler();
        this.onMetricsRecordingStopped = (methodprofilerresults) -> {
            this.stopRecordingMetrics();
        };
        this.onMetricsRecordingFinished = (path) -> {
        };
        this.random = RandomSource.create();
        this.port = -1;
        this.levels = Maps.newLinkedHashMap();
        this.running = true;
        this.tickTimes = new long[100];
        this.nextTickTime = Util.getMillis();
        this.scoreboard = new ServerScoreboard(this);
        this.customBossEvents = new CustomBossEvents();
        this.registries = worldstem.registries();
        this.worldData = worldstem.worldData();
        if (false && !this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) { // CraftBukkit - initialised later
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.proxy = proxy;
            this.packRepository = resourcepackrepository;
            this.resources = new MinecraftServer.ReloadableResources(worldstem.resourceManager(), worldstem.dataPackResources());
            this.services = services;
            if (services.profileCache() != null) {
                services.profileCache().setExecutor(this);
            }

            // this.connection = new ServerConnection(this); // Spigot
            this.progressListenerFactory = worldloadlistenerfactory;
            this.storageSource = convertable_conversionsession;
            this.playerDataStorage = convertable_conversionsession.createPlayerStorage();
            this.fixerUpper = datafixer;
            this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holdergetter = this.registries.compositeAccess().registryOrThrow(Registries.BLOCK).asLookup().filterFeatures(this.worldData.enabledFeatures());

            this.structureTemplateManager = new StructureTemplateManager(worldstem.resourceManager(), convertable_conversionsession, datafixer, holdergetter);
            this.serverThread = thread;
            this.executor = Util.backgroundExecutor();
        }
        // CraftBukkit start
        this.options = options;
        this.worldLoader = worldLoader;
        this.vanillaCommandDispatcher = worldstem.dataPackResources().commands; // CraftBukkit
        // Paper start - Handled by TerminalConsoleAppender
        // Try to see if we're actually running in a terminal, disable jline if not
        /*
        if (System.console() == null && System.getProperty("jline.terminal") == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            Main.useJline = false;
        }

        try {
            this.reader = new ConsoleReader(System.in, System.out);
            this.reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                Main.useJline = false;
                this.reader = new ConsoleReader(System.in, System.out);
                this.reader.setExpandEvents(false);
            } catch (IOException ex) {
                MinecraftServer.LOGGER.warn((String) null, ex);
            }
        }
        */
        // Paper end
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));
        // CraftBukkit end
        this.paperConfigurations = services.paperConfigurations(); // Paper
    }

    private void readScoreboard(DimensionDataStorage persistentStateManager) {
        persistentStateManager.computeIfAbsent(this.getScoreboard().dataFactory(), "scoreboard");
    }

    protected abstract boolean initServer() throws IOException;

    protected void loadLevel(String s) { // CraftBukkit
        if (!JvmProfiler.INSTANCE.isRunning()) {
            ;
        }

        boolean flag = false;
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onWorldLoadedStarted();

        this.loadWorld0(s); // CraftBukkit

        if (profiledduration != null) {
            profiledduration.finish();
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to stop JFR profiling", throwable);
            }
        }

    }

    protected void forceDifficulty() {}

    // CraftBukkit start
    private void loadWorld0(String s) {
        LevelStorageSource.LevelStorageAccess worldSession = this.storageSource;

        Registry<LevelStem> dimensions = this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        for (LevelStem worldDimension : dimensions) {
            ResourceKey<LevelStem> dimensionKey = dimensions.getResourceKey(worldDimension).get();

            ServerLevel world;
            int dimension = 0;

            if (dimensionKey == LevelStem.NETHER) {
                if (this.isNetherEnabled()) {
                    dimension = -1;
                } else {
                    continue;
                }
            } else if (dimensionKey == LevelStem.END) {
                if (this.server.getAllowEnd()) {
                    dimension = 1;
                } else {
                    continue;
                }
            } else if (dimensionKey != LevelStem.OVERWORLD) {
                dimension = -999;
            }

            String worldType = (dimension == -999) ? dimensionKey.location().getNamespace() + "_" + dimensionKey.location().getPath() : org.bukkit.World.Environment.getEnvironment(dimension).toString().toLowerCase();
            String name = (dimensionKey == LevelStem.OVERWORLD) ? s : s + "_" + worldType;
            if (dimension != 0) {
                File newWorld = LevelStorageSource.getStorageFolder(new File(name).toPath(), dimensionKey).toFile();
                File oldWorld = LevelStorageSource.getStorageFolder(new File(s).toPath(), dimensionKey).toFile();
                File oldLevelDat = new File(new File(s), "level.dat"); // The data folders exist on first run as they are created in the PersistentCollection constructor above, but the level.dat won't

                if (!newWorld.isDirectory() && oldWorld.isDirectory() && oldLevelDat.isFile()) {
                    MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder required ----");
                    MinecraftServer.LOGGER.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    MinecraftServer.LOGGER.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    MinecraftServer.LOGGER.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        MinecraftServer.LOGGER.warn("A file or folder already exists at " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            MinecraftServer.LOGGER.info("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);
                            // Migrate world data too.
                            try {
                                com.google.common.io.Files.copy(oldLevelDat, new File(new File(name), "level.dat"));
                                org.apache.commons.io.FileUtils.copyDirectory(new File(new File(s), "data"), new File(new File(name), "data"));
                            } catch (IOException exception) {
                                MinecraftServer.LOGGER.warn("Unable to migrate world data.");
                            }
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            MinecraftServer.LOGGER.warn("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        MinecraftServer.LOGGER.warn("Could not create path for " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                try {
                    worldSession = LevelStorageSource.createDefault(this.server.getWorldContainer().toPath()).validateAndCreateAccess(name, dimensionKey);
                } catch (IOException | ContentValidationException ex) {
                    throw new RuntimeException(ex);
                }
            }

            org.bukkit.generator.ChunkGenerator gen = this.server.getGenerator(name);
            org.bukkit.generator.BiomeProvider biomeProvider = this.server.getBiomeProvider(name);

            PrimaryLevelData worlddata;
            WorldLoader.DataLoadContext worldloader_a = this.worldLoader;
            Registry<LevelStem> iregistry = worldloader_a.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
            DynamicOps<Tag> dynamicops = RegistryOps.create(NbtOps.INSTANCE, (HolderLookup.Provider) worldloader_a.datapackWorldgen());
            Pair<WorldData, WorldDimensions.Complete> pair = worldSession.getDataTag(dynamicops, worldloader_a.dataConfiguration(), iregistry, worldloader_a.datapackWorldgen().allRegistriesLifecycle());

            if (pair != null) {
                worlddata = (PrimaryLevelData) pair.getFirst();
            } else {
                LevelSettings worldsettings;
                WorldOptions worldoptions;
                WorldDimensions worlddimensions;

                if (this.isDemo()) {
                    worldsettings = MinecraftServer.DEMO_SETTINGS;
                    worldoptions = WorldOptions.DEMO_OPTIONS;
                    worlddimensions = WorldPresets.createNormalWorldDimensions(worldloader_a.datapackWorldgen());
                } else {
                    DedicatedServerProperties dedicatedserverproperties = ((DedicatedServer) this).getProperties();

                    worldsettings = new LevelSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty, false, new GameRules(), worldloader_a.dataConfiguration());
                    worldoptions = this.options.has("bonusChest") ? dedicatedserverproperties.worldOptions.withBonusChest(true) : dedicatedserverproperties.worldOptions;
                    worlddimensions = dedicatedserverproperties.createDimensions(worldloader_a.datapackWorldgen());
                }

                WorldDimensions.Complete worlddimensions_b = worlddimensions.bake(iregistry);
                Lifecycle lifecycle = worlddimensions_b.lifecycle().add(worldloader_a.datapackWorldgen().allRegistriesLifecycle());

                worlddata = new PrimaryLevelData(worldsettings, worldoptions, worlddimensions_b.specialWorldProperty(), lifecycle);
            }
            worlddata.checkName(name); // CraftBukkit - Migration did not rewrite the level.dat; This forces 1.8 to take the last loaded world as respawn (in this case the end)
            // Paper - move down

            PrimaryLevelData iworlddataserver = worlddata;
            boolean flag = worlddata.isDebugWorld();
            WorldOptions worldoptions = worlddata.worldGenOptions();
            long i = worldoptions.seed();
            long j = BiomeManager.obfuscateSeed(i);
            List<CustomSpawner> list = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(iworlddataserver));
            LevelStem worlddimension = (LevelStem) dimensions.get(dimensionKey);

            org.bukkit.generator.WorldInfo worldInfo = new org.bukkit.craftbukkit.generator.CraftWorldInfo(iworlddataserver, worldSession, org.bukkit.World.Environment.getEnvironment(dimension), worlddimension.type().value(), worlddimension.generator(), this.registryAccess()); // Paper
            if (biomeProvider == null && gen != null) {
                biomeProvider = gen.getDefaultBiomeProvider(worldInfo);
            }

            // Paper start - fix and optimise world upgrading
            if (options.has("forceUpgrade")) {
                net.minecraft.server.Main.convertWorldButItWorks(
                    dimensionKey, worldSession, DataFixers.getDataFixer(), worlddimension.generator().getTypeNameForDataFixer(), options.has("eraseCache")
                );
            }
            // Paper end - fix and optimise world upgrading
            ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, dimensionKey.location());

            if (dimensionKey == LevelStem.OVERWORLD) {
                this.worldData = worlddata;
                this.worldData.setGameType(((DedicatedServer) this).getProperties().gamemode); // From DedicatedServer.init

                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(11);

                world = new ServerLevel(this, this.executor, worldSession, iworlddataserver, worldKey, worlddimension, worldloadlistener, flag, j, list, true, (RandomSequences) null, org.bukkit.World.Environment.getEnvironment(dimension), gen, biomeProvider);
                DimensionDataStorage worldpersistentdata = world.getDataStorage();
                this.readScoreboard(worldpersistentdata);
                this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, world.getScoreboard());
                this.commandStorage = new CommandStorage(worldpersistentdata);
            } else {
                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(11);
                // Paper start - option to use the dimension_type to check if spawners should be added. I imagine mojang will add some datapack-y way of managing this in the future.
                final List<CustomSpawner> spawners;
                if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.useDimensionTypeForCustomSpawners && this.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getResourceKey(worlddimension.type().value()).orElseThrow() == net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD) {
                    spawners = list;
                } else {
                    spawners = Collections.emptyList();
                }
                world = new ServerLevel(this, this.executor, worldSession, iworlddataserver, worldKey, worlddimension, worldloadlistener, flag, j, spawners, true, this.overworld().getRandomSequences(), org.bukkit.World.Environment.getEnvironment(dimension), gen, biomeProvider);
                // Paper end
            }

            worlddata.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
            this.addLevel(world); // Paper - move up
            // Folia start - region threading
            // the spawn should be within ~32 blocks, so we force add ticket levels to ensure the first thread
            // to init spawn will not run into any ownership issues
            // move init to start of tickServer
            int loadRegionRadius = ((32) >> 4);
            world.randomSpawnSelection = new ChunkPos(world.getChunkSource().randomState().sampler().findSpawnPosition());
            for (int currX = -loadRegionRadius; currX <= loadRegionRadius; ++currX) {
                for (int currZ = -loadRegionRadius; currZ <= loadRegionRadius; ++currZ) {
                    ChunkPos pos = new ChunkPos(currX, currZ);
                    world.chunkSource.addTicketAtLevel(
                        TicketType.UNKNOWN, pos, io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL, pos
                    );
                }
            }
            // Folia end - region threading

            // Paper - move up
            this.getPlayerList().addWorldborderListener(world);

            if (worlddata.getCustomBossEvents() != null) {
                this.getCustomBossEvents().load(worlddata.getCustomBossEvents());
            }
        }
        this.forceDifficulty();
        for (ServerLevel worldserver : this.getAllLevels()) {
            this.prepareLevels(worldserver.getChunkSource().chunkMap.progressListener, worldserver);
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addWorld(worldserver); // Folia - region threading
            //worldserver.entityManager.tick(); // SPIGOT-6526: Load pending entities so they are available to the API // Paper - rewrite chunk system, not required to "tick" anything
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(worldserver.getWorld()));
        }

        // Paper start - Handle collideRule team for player collision toggle
        final ServerScoreboard scoreboard = this.getScoreboard();
        final java.util.Collection<String> toRemove = scoreboard.getPlayerTeams().stream().filter(team -> team.getName().startsWith("collideRule_")).map(net.minecraft.world.scores.PlayerTeam::getName).collect(java.util.stream.Collectors.toList());
        for (String teamName : toRemove) {
            scoreboard.removePlayerTeam(scoreboard.getPlayerTeam(teamName)); // Clean up after ourselves
        }

        if (!io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions) {
            this.getPlayerList().collideRuleTeamName = org.apache.commons.lang3.StringUtils.left("collideRule_" + java.util.concurrent.ThreadLocalRandom.current().nextInt(), 16);
            net.minecraft.world.scores.PlayerTeam collideTeam = scoreboard.addPlayerTeam(this.getPlayerList().collideRuleTeamName);
            collideTeam.setSeeFriendlyInvisibles(false); // Because we want to mimic them not being on a team at all
        }
        // Paper end

        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        this.server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
    }

    public void initWorld(ServerLevel worldserver, ServerLevelData iworlddataserver, WorldData saveData, WorldOptions worldoptions) {
        boolean flag = saveData.isDebugWorld();
        // CraftBukkit start
        if (worldserver.generator != null) {
            worldserver.getWorld().getPopulators().addAll(worldserver.generator.getDefaultPopulators(worldserver.getWorld()));
        }
        WorldBorder worldborder = worldserver.getWorldBorder();
        worldborder.applySettings(iworlddataserver.getWorldBorder()); // CraftBukkit - move up so that WorldBorder is set during WorldInitEvent
        this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(worldserver.getWorld())); // CraftBukkit - SPIGOT-5569: Call WorldInitEvent before any chunks are generated

        if (!iworlddataserver.isInitialized()) {
            try {
                MinecraftServer.setInitialSpawn(worldserver, iworlddataserver, worldoptions.generateBonusChest(), flag);
                iworlddataserver.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception initializing level");

                try {
                    worldserver.fillReportDetails(crashreport);
                } catch (Throwable throwable1) {
                    ;
                }

                throw new ReportedException(crashreport);
            }

            iworlddataserver.setInitialized(true);
        }

    }
    // CraftBukkit end

    private static void setInitialSpawn(ServerLevel world, ServerLevelData worldProperties, boolean bonusChest, boolean debugWorld) {
        if (debugWorld) {
            worldProperties.setSpawn(BlockPos.ZERO.above(80), 0.0F);
        } else {
            ServerChunkCache chunkproviderserver = world.getChunkSource();
            ChunkPos chunkcoordintpair = world.randomSpawnSelection; // Folia - region threading
            // CraftBukkit start
            if (world.generator != null) {
                Random rand = new Random(world.getSeed());
                org.bukkit.Location spawn = world.generator.getFixedSpawnLocation(world.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != world.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + worldProperties.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        worldProperties.setSpawn(new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()), spawn.getYaw());
                        return;
                    }
                }
            }
            // CraftBukkit end
            int i = chunkproviderserver.getGenerator().getSpawnHeight(world);

            if (i < world.getMinBuildHeight()) {
                BlockPos blockposition = chunkcoordintpair.getWorldPosition();

                world.getChunk(blockposition.offset(8, 0, 8)); // Folia - region threading - sync load first
                i = world.getHeight(Heightmap.Types.WORLD_SURFACE, blockposition.getX() + 8, blockposition.getZ() + 8);
            }

            worldProperties.setSpawn(chunkcoordintpair.getWorldPosition().offset(8, i, 8), 0.0F);
            int j = 0;
            int k = 0;
            int l = 0;
            int i1 = -1;
            boolean flag2 = true;

            for (int j1 = 0; j1 < Mth.square(11); ++j1) {
                if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
                    BlockPos blockposition1 = PlayerRespawnLogic.getSpawnPosInChunk(world, new ChunkPos(chunkcoordintpair.x + j, chunkcoordintpair.z + k));

                    if (blockposition1 != null) {
                        worldProperties.setSpawn(blockposition1, 0.0F);
                        break;
                    }
                }

                if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
                    int k1 = l;

                    l = -i1;
                    i1 = k1;
                }

                j += l;
                k += i1;
            }

            if (bonusChest) {
                world.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((iregistry) -> {
                    return iregistry.getHolder(MiscOverworldFeatures.BONUS_CHEST);
                }).ifPresent((holder_c) -> {
                    ((ConfiguredFeature) holder_c.value()).place(world, chunkproviderserver.getGenerator(), world.random, new BlockPos(worldProperties.getXSpawn(), worldProperties.getYSpawn(), worldProperties.getZSpawn()));
                });
            }

        }
    }

    private void setupDebugLevel(WorldData properties) {
        properties.setDifficulty(Difficulty.PEACEFUL);
        properties.setDifficultyLocked(true);
        ServerLevelData iworlddataserver = properties.overworldData();

        iworlddataserver.setRaining(false);
        iworlddataserver.setThundering(false);
        iworlddataserver.setClearWeatherTime(1000000000);
        iworlddataserver.setDayTime(6000L);
        iworlddataserver.setGameType(GameType.SPECTATOR);
    }

    // CraftBukkit start
    public void prepareLevels(ChunkProgressListener worldloadlistener, ServerLevel worldserver) {
        ServerChunkCache chunkproviderserver = worldserver.getChunkSource(); // Paper
        // WorldServer worldserver = this.overworld();
        this.forceTicks = true;
        // CraftBukkit end
        if (worldserver.getWorld().getKeepSpawnInMemory()) { // Paper

        MinecraftServer.LOGGER.info("Preparing start region for dimension {}", worldserver.dimension().location());
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        worldloadlistener.updateSpawnPos(new ChunkPos(blockposition));
        //ChunkProviderServer chunkproviderserver = worldserver.getChunkProvider(); // Paper - move up

        this.nextTickTime = Util.getMillis();
        // Paper start - configurable spawn reason
        int radiusBlocks = worldserver.paperConfig().spawn.keepSpawnLoadedRange * 16;
        int radiusChunks = radiusBlocks / 16 + ((radiusBlocks & 15) != 0 ? 1 : 0);
        int totalChunks = ((radiusChunks) * 2 + 1);
        totalChunks *= totalChunks;
        worldloadlistener.setChunkRadius(radiusBlocks / 16);

        worldserver.addTicketsForSpawn(radiusBlocks, blockposition);
        // Paper end

        // this.nextTickTime = SystemUtils.getMillis() + 10L;
        //this.executeModerately(); // Folia - region threading
        // Iterator iterator = this.levels.values().iterator();
        }

        if (true) {
            ServerLevel worldserver1 = worldserver;
            // CraftBukkit end
            ForcedChunksSavedData forcedchunk = (ForcedChunksSavedData) worldserver1.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");

            if (forcedchunk != null) {
                LongIterator longiterator = forcedchunk.getChunks().iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    ChunkPos chunkcoordintpair = new ChunkPos(i);

                    worldserver1.getChunkSource().updateChunkForced(chunkcoordintpair, true);
                }
            }
        }

        // CraftBukkit start
        // this.nextTickTime = SystemUtils.getMillis() + 10L;
        //this.executeModerately(); // Folia - region threading
        // CraftBukkit end
        if (worldserver.getWorld().getKeepSpawnInMemory()) worldloadlistener.stop(); // Paper
        // CraftBukkit start
        // this.updateMobSpawningFlags();
        worldserver.setSpawnSettings(worldserver.serverLevelData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))

        this.forceTicks = false;
        // CraftBukkit end
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean suppressLogs, boolean flush, boolean force) {
        // Paper start - rewrite chunk system - add close param
        // This allows us to avoid double saving chunks by closing instead of saving then closing
        return this.saveAllChunks(suppressLogs, flush, force, false);
    }
    public boolean saveAllChunks(boolean suppressLogs, boolean flush, boolean force, boolean close) {
        // Paper end - rewrite chunk system - add close param
        boolean flag3 = false;

        for (Iterator iterator = this.getAllLevels().iterator(); iterator.hasNext(); flag3 = true) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (!suppressLogs) {
                MinecraftServer.LOGGER.info("Saving chunks for level '{}'/{}", worldserver, worldserver.dimension().location());
            }
            // Paper start - rewrite chunk system
            worldserver.save((ProgressListener) null, flush, worldserver.noSave && !force, close);
            if (flush) {
                MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", worldserver.getChunkSource().chunkMap.getStorageName());
            }
            // Paper end - rewrite chunk system
        }

        // CraftBukkit start - moved to WorldServer.save
        /*
        WorldServer worldserver1 = this.overworld();
        IWorldDataServer iworlddataserver = this.worldData.overworldData();

        iworlddataserver.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save());
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        */
        // CraftBukkit end
        if (flush) {
            Iterator iterator1 = this.getAllLevels().iterator();

            while (iterator1.hasNext()) {
                ServerLevel worldserver2 = (ServerLevel) iterator1.next();

                //MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", worldserver2.getChunkSource().chunkMap.getStorageName()); // Paper - move up
            }

            MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }

        return flag3;
    }

    public boolean saveEverything(boolean suppressLogs, boolean flush, boolean force) {
        boolean flag3;

        try {
            this.isSaving = true;
            this.getPlayerList().saveAll(); // Diff on change
            flag3 = this.saveAllChunks(suppressLogs, flush, force);
        } finally {
            this.isSaving = false;
        }

        return flag3;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // CraftBukkit start
    private boolean hasStopped = false;
    public volatile boolean hasFullyShutdown = false; // Paper
    private boolean hasLoggedStop = false; // Paper
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (this.stopLock) {
            return this.hasStopped;
        }
    }
    // CraftBukkit end

    // Folia start - region threading
    private final java.util.concurrent.atomic.AtomicBoolean hasStartedShutdownThread = new java.util.concurrent.atomic.AtomicBoolean();

    private void haltServerRegionThreading() {
        if (this.hasStartedShutdownThread.getAndSet(true)) {
            // already started shutdown
            return;
        }
        new io.papermc.paper.threadedregions.RegionShutdownThread("Region shutdown thread").start();
    }

    public void haltCurrentRegion() {
        if (!io.papermc.paper.util.TickThread.isShutdownThread()) {
            throw new IllegalStateException();
        }
    }
    // Folia end - region threading

    public void stopServer() {
        // Folia start - region threading
        // halt scheduler
        // don't wait, we may be on a scheduler thread
        io.papermc.paper.threadedregions.TickRegions.getScheduler().halt(false, 0L);
        // cannot run shutdown logic on this thread, as it may be a scheduler
        if (true) {
            if (!io.papermc.paper.util.TickThread.isShutdownThread()) {
                this.haltServerRegionThreading();
                return;
            } // else: fall through to regular stop logic
        }
        // Folia end - region threading
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(this.stopLock) {
            if (this.hasStopped) return;
            this.hasStopped = true;
        }
        if (!hasLoggedStop && isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper
        // Paper start - kill main thread, and kill it hard
        shutdownThread = Thread.currentThread();
        org.spigotmc.WatchdogThread.doStop(); // Paper
        if (false && !isSameThread()) { // Folia - region threading
            MinecraftServer.LOGGER.info("Stopping main thread (Ignore any thread death message you see! - DO NOT REPORT THREAD DEATH TO PAPER)");
            while (this.getRunningThread().isAlive()) {
                this.getRunningThread().stop();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {}
            }
        }
        // Paper end
        // CraftBukkit end
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        MinecraftServer.LOGGER.info("Stopping server");
        Commands.COMMAND_SENDING_POOL.shutdownNow(); // Paper - Shutdown and don't bother finishing
        MinecraftTimings.stopServer(); // Paper
        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
            this.server.waitForAsyncTasksShutdown(); // Paper
        }
        // CraftBukkit end
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            //MinecraftServer.LOGGER.info("Saving players"); // Folia - move to shutdown thread logic
            //this.playerList.saveAll(); // Folia - move to shutdown thread logic
            this.playerList.removeAll(this.isRestarting); // Paper
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        // Folia start - region threading
        if (true) {
            // the rest till part 2 is handled by the region shutdown thread
            return;
        }
        // Folia end - region threading

        MinecraftServer.LOGGER.info("Saving worlds");
        Iterator iterator = this.getAllLevels().iterator();

        ServerLevel worldserver;

        while (iterator.hasNext()) {
            worldserver = (ServerLevel) iterator.next();
            if (worldserver != null) {
                worldserver.noSave = false;
            }
        }

        this.saveAllChunks(false, true, false, true); // Paper - rewrite chunk system - move closing into here

        this.isSaving = false;
        // Folia start - region threading
        this.stopPart2();
    }
    public void stopPart2() {
        // Folia end - region threading
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException ioexception1) {
            MinecraftServer.LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), ioexception1);
        }
        // Spigot start
        io.papermc.paper.util.MCUtil.asyncExecutor.shutdown(); // Paper
        try { io.papermc.paper.util.MCUtil.asyncExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); // Paper
        } catch (java.lang.InterruptedException ignored) {} // Paper
        if (org.spigotmc.SpigotConfig.saveUserCacheOnStopOnly) {
            MinecraftServer.LOGGER.info("Saving usercache.json");
            this.getProfileCache().save(false); // Paper
        }
        // Spigot end
        // Paper start - move final shutdown items here
        LOGGER.info("Flushing Chunk IO");
        // Paper end - move final shutdown items here
        io.papermc.paper.chunk.system.io.RegionFileIOThread.close(true); // Paper // Paper - rewrite chunk system
        // Paper start - move final shutdown items here
        LOGGER.info("Closing Thread Pool");
        Util.shutdownExecutors(); // Paper
        LOGGER.info("Closing Server");
        try {
            net.minecrell.terminalconsole.TerminalConsoleAppender.close(); // Paper - Use TerminalConsoleAppender
        } catch (Exception e) {
        }
        io.papermc.paper.log.CustomLogManager.forceReset(); // Paper - Reset loggers after shutdown
        this.onServerExit();
        // Paper end - move final shutdown items here
    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String serverIp) {
        this.localIp = serverIp;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean waitForShutdown) {
        // Paper start - allow passing of the intent to restart
        this.safeShutdown(waitForShutdown, false);
    }
    public void safeShutdown(boolean waitForShutdown, boolean isRestarting) {
        this.isRestarting = isRestarting;
        this.hasLoggedStop = true; // Paper
        if (isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper
        // Paper end
        this.running = false;
        this.stopServer(); // Folia - region threading
        if (waitForShutdown) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedexception) {
                MinecraftServer.LOGGER.error("Error while shutting down", interruptedexception);
            }
        }

    }

    // Spigot Start
    private static double calcTps(double avg, double exp, double tps)
    {
        return ( avg * exp ) + ( tps * ( 1 - exp ) );
    }

    // Paper start - Further improve server tick loop
    private static final long SEC_IN_NANO = 1000000000;
    private static final long MAX_CATCHUP_BUFFER = TICK_TIME * TPS * 60L;
    private long lastTick = 0;
    private long catchupTime = 0;
    public final RollingAverage tps1 = new RollingAverage(60);
    public final RollingAverage tps5 = new RollingAverage(60 * 5);
    public final RollingAverage tps15 = new RollingAverage(60 * 15);

    public static class RollingAverage {
        private final int size;
        private long time;
        private java.math.BigDecimal total;
        private int index = 0;
        private final java.math.BigDecimal[] samples;
        private final long[] times;

        RollingAverage(int size) {
            this.size = size;
            this.time = size * SEC_IN_NANO;
            this.total = dec(TPS).multiply(dec(SEC_IN_NANO)).multiply(dec(size));
            this.samples = new java.math.BigDecimal[size];
            this.times = new long[size];
            for (int i = 0; i < size; i++) {
                this.samples[i] = dec(TPS);
                this.times[i] = SEC_IN_NANO;
            }
        }

        private static java.math.BigDecimal dec(long t) {
            return new java.math.BigDecimal(t);
        }
        public void add(java.math.BigDecimal x, long t) {
            time -= times[index];
            total = total.subtract(samples[index].multiply(dec(times[index])));
            samples[index] = x;
            times[index] = t;
            time += t;
            total = total.add(x.multiply(dec(t)));
            if (++index == size) {
                index = 0;
            }
        }

        public double getAverage() {
            return total.divide(dec(time), 30, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
    private static final java.math.BigDecimal TPS_BASE = new java.math.BigDecimal(1E9).multiply(new java.math.BigDecimal(SAMPLE_INTERVAL));
    // Paper End
    // Spigot End

    public static volatile RuntimeException chunkSystemCrash; // Paper - rewrite chunk system

    protected void runServer() {
        try {
            long serverStartTime = Util.getNanos(); // Paper
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTime = Util.getMillis();
            this.statusIcon = (ServerStatus.Favicon) this.loadStatusIcon().orElse(null); // CraftBukkit - decompile error
            this.status = this.buildServerStatus();

            // Folia start - region threading
            if (true) {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().init(); // Folia - region threading - only after loading worlds
                String doneTime = String.format(java.util.Locale.ROOT, "%.3fs", (double) (Util.getNanos() - serverStartTime) / 1.0E9D);
                LOGGER.info("Done ({})! For help, type \"help\"", doneTime);
                for (;;) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (final InterruptedException ex) {}
                }
            }
            // Folia end - region threading

            // Spigot start
            // Paper start - move done tracking
            LOGGER.info("Running delayed init tasks");
            //this.server.getScheduler().mainThreadHeartbeat(this.tickCount); // run all 1 tick delay tasks during init, // Folia - region threading
            // this is going to be the first thing the tick process does anyways, so move done and run it after
            // everything is init before watchdog tick.
            // anything at 3+ won't be caught here but also will trip watchdog....
            // tasks are default scheduled at -1 + delay, and first tick will tick at 1
            String doneTime = String.format(java.util.Locale.ROOT, "%.3fs", (double) (Util.getNanos() - serverStartTime) / 1.0E9D);
            LOGGER.info("Done ({})! For help, type \"help\"", doneTime);
            // Paper end

            org.spigotmc.WatchdogThread.tick(); // Paper
            org.spigotmc.WatchdogThread.hasStarted = true; // Paper
            Arrays.fill( this.recentTps, 20 );
            long start = System.nanoTime(), curTime, tickSection = start; // Paper - Further improve server tick loop
            lastTick = start - TICK_TIME; // Paper
            while (this.running) {
                // Paper start - rewrite chunk system
                // guarantee that nothing can stop the server from halting if it can at least still tick
                if (this.chunkSystemCrash != null) {
                    throw this.chunkSystemCrash;
                }
                // Paper end - rewrite chunk system
                long i = ((curTime = System.nanoTime()) / (1000L * 1000L)) - this.nextTickTime; // Paper

                if (i > 5000L && this.nextTickTime - this.lastOverloadWarning >= 30000L) { // CraftBukkit
                    long j = i / 50L;

                    if (this.server.getWarnOnOverload()) // CraftBukkit
                    MinecraftServer.LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", i, j);
                    this.nextTickTime += j * 50L;
                    this.lastOverloadWarning = this.nextTickTime;
                }

                //++MinecraftServer.currentTickLong; // Paper // Folia - threaded regions
                if ( false ) // Folia - region threading
                {
                    final long diff = curTime - tickSection;
                    java.math.BigDecimal currentTps = TPS_BASE.divide(new java.math.BigDecimal(diff), 30, java.math.RoundingMode.HALF_UP);
                    tps1.add(currentTps, diff);
                    tps5.add(currentTps, diff);
                    tps15.add(currentTps, diff);
                    // Backwards compat with bad plugins
                    this.recentTps[0] = tps1.getAverage();
                    this.recentTps[1] = tps5.getAverage();
                    this.recentTps[2] = tps15.getAverage();
                    // Paper end
                    tickSection = curTime;
                }
                // Spigot end

                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    //this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount); // Folia - region threading
                }

                //MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit // Paper - don't overwrite current tick time
                lastTick = curTime;
                this.nextTickTime += 50L;
                this.startMetricsRecordingTick();
                this.profiler.push("tick");
                this.tickServer(curTime, this.nextTickTime, 0L, null); // Folia - region threading
                this.profiler.popPush("nextTickWait");
                this.mayHaveDelayedTasks = true;
                this.delayedTasksMaxNextTickTime = Math.max(Util.getMillis() + 50L, this.nextTickTime);
                this.waitUntilNextTick();
                this.profiler.pop();
                this.endMetricsRecordingTick();
                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.averageTickTime);
            }
        } catch (Throwable throwable) {
            // Paper start
            if (throwable instanceof ThreadDeath) {
                MinecraftServer.LOGGER.error("Main thread terminated by WatchDog due to hard crash", throwable);
                return;
            }
            // Paper end
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
            // Spigot Start
            if ( throwable.getCause() != null )
            {
                MinecraftServer.LOGGER.error( "\tCause of unexpected exception was", throwable.getCause() );
            }
            // Spigot End
            CrashReport crashreport = MinecraftServer.constructOrExtractCrashReport(throwable);

            this.fillSystemReport(crashreport.getSystemReport());
            File file = new File(new File(this.getServerDirectory(), "crash-reports"), "crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

            if (crashreport.saveToFile(file)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", file.getAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable1) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable1);
            } finally {
                if (this.services.profileCache() != null) {
                    this.services.profileCache().clearExecutor();
                }

                //org.spigotmc.WatchdogThread.doStop(); // Spigot // Paper - move into stop
                // CraftBukkit start - Restore terminal to original settings
                try {
                    //net.minecrell.terminalconsole.TerminalConsoleAppender.close(); // Paper - Move into stop
                } catch (Exception ignored) {
                }
                // CraftBukkit end
                //this.onServerExit(); // Paper - moved into stop
            }

        }

    }

    private static CrashReport constructOrExtractCrashReport(Throwable throwable) {
        ReportedException reportedexception = null;

        for (Throwable throwable1 = throwable; throwable1 != null; throwable1 = throwable1.getCause()) {
            if (throwable1 instanceof ReportedException) {
                ReportedException reportedexception1 = (ReportedException) throwable1;

                reportedexception = reportedexception1;
            }
        }

        CrashReport crashreport;

        if (reportedexception != null) {
            crashreport = reportedexception.getReport();
            if (reportedexception != throwable) {
                crashreport.addCategory("Wrapped in").setDetailError("Wrapping exception", throwable);
            }
        } else {
            crashreport = new CrashReport("Exception in server tick loop", throwable);
        }

        return crashreport;
    }

    private boolean haveTime() {
        // Paper start
        if (this.forceTicks) {
            return true;
        }
        // Paper end
        // CraftBukkit start
        if (isOversleep) return canOversleep();// Paper - because of our changes, this logic is broken
        return this.forceTicks || this.runningTask() || Util.getMillis() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTime : this.nextTickTime);
    }

    // Paper start
    boolean isOversleep = false;
    private boolean canOversleep() {
        return this.mayHaveDelayedTasks && Util.getMillis() < this.delayedTasksMaxNextTickTime;
    }

    private boolean canSleepForTickNoOversleep() {
        return this.forceTicks || this.runningTask() || Util.getMillis() < this.nextTickTime;
    }
    // Paper end

    private void executeModerately() {
        this.runAllTasks();
        java.util.concurrent.locks.LockSupport.parkNanos("executing tasks", 1000L);
        // CraftBukkit end
    }

    protected void waitUntilNextTick() {
        //this.executeAll(); // Paper - move this into the tick method for timings
        this.managedBlock(() -> {
            return !this.canSleepForTickNoOversleep(); // Paper - move oversleep into full server tick
        });
    }

    @Override
    public TickTask wrapRunnable(Runnable runnable) {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    protected boolean shouldRun(TickTask ticktask) {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    public boolean pollTask() {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        boolean flag = this.pollTaskInternal();

        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        if (super.pollTask()) {
            this.executeMidTickTasks(); // Paper - execute chunk tasks mid tick
            return true;
        } else {
            boolean ret = false; // Paper - force execution of all worlds, do not just bias the first
            if (this.haveTime()) {
                Iterator iterator = this.getAllLevels().iterator();

                while (iterator.hasNext()) {
                    ServerLevel worldserver = (ServerLevel) iterator.next();

                    if (worldserver.getChunkSource().pollTask()) {
                        ret = true; // Paper - force execution of all worlds, do not just bias the first
                    }
                }
            }

            return ret; // Paper - force execution of all worlds, do not just bias the first
        }
    }

    public void doRunTask(TickTask ticktask) { // CraftBukkit - decompile error
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        this.getProfiler().incrementCounter("runTask");
        super.doRunTask(ticktask);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png").toPath()).filter((path) -> {
            return Files.isRegularFile(path, new LinkOption[0]);
        }).or(() -> {
            return this.storageSource.getIconFile().filter((path) -> {
                return Files.isRegularFile(path, new LinkOption[0]);
            });
        });

        return optional.flatMap((path) -> {
            try {
                BufferedImage bufferedimage = ImageIO.read(path.toFile());

                Preconditions.checkState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                Preconditions.checkState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();

                ImageIO.write(bufferedimage, "PNG", bytearrayoutputstream);
                return Optional.of(new ServerStatus.Favicon(bytearrayoutputstream.toByteArray()));
            } catch (Exception exception) {
                MinecraftServer.LOGGER.error("Couldn't load server icon", exception);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public File getServerDirectory() {
        return new File(".");
    }

    public void onServerCrash(CrashReport report) {}

    public void onServerExit() {}

    // Folia start - region threading
    public void tickServer(long startTime, long scheduledEnd, long targetBuffer,
                           io.papermc.paper.threadedregions.TickRegions.TickRegionData region) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        if (region != null) {
            region.world.getCurrentWorldData().updateTickData();
            if (region.world.checkInitialised.get() != ServerLevel.WORLD_INIT_CHECKED) {
                synchronized (region.world.checkInitialised) {
                    if (region.world.checkInitialised.compareAndSet(ServerLevel.WORLD_INIT_NOT_CHECKED, ServerLevel.WORLD_INIT_CHECKING)) {
                        LOGGER.info("Initialising world '" + region.world.getWorld().getName() + "' before it can be ticked...");
                        this.initWorld(region.world, region.world.serverLevelData, worldData, region.world.serverLevelData.worldGenOptions()); // Folia - delayed until first tick of world
                        region.world.checkInitialised.set(ServerLevel.WORLD_INIT_CHECKED);
                        LOGGER.info("Initialised world '" + region.world.getWorld().getName() + "'");
                    } // else: must be checked
                }
            }
        }
        BooleanSupplier shouldKeepTicking = () -> {
            return scheduledEnd - System.nanoTime() > targetBuffer;
        };
        new com.destroystokyo.paper.event.server.ServerTickStartEvent((int)region.getCurrentTick()).callEvent(); // Paper
        // Folia end - region threading
        co.aikar.timings.TimingsManager.FULL_SERVER_TICK.startTiming(); // Paper
        long i = startTime; // Folia - region threading

        // Paper start - move oversleep into full server tick
        if (region == null) { // Folia - region threading
        isOversleep = true;MinecraftTimings.serverOversleep.startTiming();
        this.managedBlock(() -> {
            return !this.canOversleep();
        });
        isOversleep = false;MinecraftTimings.serverOversleep.stopTiming();
        } // Folia - region threading
        // Paper end
        // Folia - region threading - move up

        // Folia start - region threading
        if (region != null) {
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.INTERNAL_TICK_TASKS); try { // Folia - profiler
            region.getTaskQueueData().drainTasks();
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.INTERNAL_TICK_TASKS); } // Folia - profiler
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLUGIN_TICK_TASKS); try { // Folia - profiler
            ((io.papermc.paper.threadedregions.scheduler.FoliaRegionScheduler)Bukkit.getRegionScheduler()).tick();
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLUGIN_TICK_TASKS); } // Folia - profiler
            // now run all the entity schedulers
            // TODO there has got to be a more efficient variant of this crap
            long tickedEntitySchedulers = 0L; // Folia - profiler
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_SCHEDULER_TICK); try { // Folia - profiler
            for (Entity entity : region.world.getCurrentWorldData().getLocalEntitiesCopy()) {
                if (!io.papermc.paper.util.TickThread.isTickThreadFor(entity) || entity.isRemoved()) {
                    continue;
                }
                org.bukkit.craftbukkit.entity.CraftEntity bukkit = entity.getBukkitEntityRaw();
                if (bukkit != null) {
                    bukkit.taskScheduler.executeTick();
                    ++tickedEntitySchedulers; // Folia - profiler
                }
            }
            profiler.addCounter(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_SCHEDULERS_TICKED, tickedEntitySchedulers); // Folia - profiler
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_SCHEDULER_TICK); } // Folia - profiler
            // now tick connections
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CONNECTION_TICK); try { // Folia - profiler
            region.world.getCurrentWorldData().tickConnections(); // Folia - region threading
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CONNECTION_TICK); } // Folia - profiler
        }
        // Folia end - region threading

        // Folia - region threading
        this.tickChildren(shouldKeepTicking, region); // Folia - region threading
        if (region == null && i - this.lastServerStatus >= 5000000000L) { // Folia - region threading - moved to global tick
            this.lastServerStatus = i;
            this.status = this.buildServerStatus();
        }

        // Paper start - incremental chunk and player saving
        int playerSaveInterval = io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.rate;
        if (playerSaveInterval < 0) {
            playerSaveInterval = autosavePeriod;
        }
        this.profiler.push("save");
        final boolean fullSave = autosavePeriod > 0 && io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() % autosavePeriod == 0; // Folia - region threading
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.AUTOSAVE); try { // Folia - profiler
        try {
            this.isSaving = true;
            if (playerSaveInterval > 0) {
                this.playerList.saveAll(playerSaveInterval);
            }
            for (ServerLevel level : (region == null ? this.getAllLevels() : Arrays.asList(region.world))) { // Folia - region threading
                if (level.paperConfig().chunks.autoSaveInterval.value() > 0) {
                    level.saveIncrementally(region == null && fullSave); // Folia - region threading - don't save level.dat
                }
            }
        } finally {
            this.isSaving = false;
        }
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.AUTOSAVE); } // Folia - profiler
        this.profiler.pop();
        // Paper end
        io.papermc.paper.util.CachedLists.reset(); // Paper
        // Paper start - move executeAll() into full server tick timing
        if (region == null) try (co.aikar.timings.Timing ignored = MinecraftTimings.processTasksTimer.startTiming()) { // Folia - region threading
            this.runAllTasks();
        }
        // Paper end
        // Paper start
        long endTime = System.nanoTime();
        long remaining = scheduledEnd - endTime; // Folia - region ticking
        new com.destroystokyo.paper.event.server.ServerTickEndEvent((int)io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick(), ((double)(endTime - startTime) / 1000000D), remaining).callEvent(); // Folia - region ticking
        // Paper end
        this.profiler.push("tallying");
        // Folia - region threading
        this.profiler.pop();
        org.spigotmc.WatchdogThread.tick(); // Spigot
        co.aikar.timings.TimingsManager.FULL_SERVER_TICK.stopTiming(); // Paper
    }

    protected void logTickTime(long nanos) {}

    // Folia start - region threading
    public void rebuildServerStatus() {
        this.status = this.buildServerStatus();
    }
    // Folia end - region threading

    private ServerStatus buildServerStatus() {
        ServerStatus.Players serverping_serverpingplayersample = this.buildPlayerStatus();

        return new ServerStatus(io.papermc.paper.adventure.PaperAdventure.asVanilla(this.motd), Optional.of(serverping_serverpingplayersample), Optional.of(ServerStatus.Version.current()), Optional.ofNullable(this.statusIcon), this.enforceSecureProfile()); // Paper - Adventure
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> list = new java.util.ArrayList<>(this.playerList.getPlayers()); // Folia - region threading
        int i = this.getMaxPlayers();

        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(i, list.size(), List.of());
        } else {
            int j = Math.min(list.size(), org.spigotmc.SpigotConfig.playerSample); // Paper
            ObjectArrayList<GameProfile> objectarraylist = new ObjectArrayList(j);
            int k = Mth.nextInt(this.random, 0, list.size() - j);

            for (int l = 0; l < j; ++l) {
                ServerPlayer entityplayer = (ServerPlayer) list.get(k + l);

                objectarraylist.add(entityplayer.allowsListing() ? entityplayer.getGameProfile() : MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
            }

            Util.shuffle(objectarraylist, this.random);
            return new ServerStatus.Players(i, list.size(), objectarraylist);
        }
    }

    public void tickChildren(BooleanSupplier shouldKeepTicking, io.papermc.paper.threadedregions.TickRegions.TickRegionData region) { // Folia - region threading
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - regionised ticking
        if (region == null) this.getPlayerList().getPlayers().forEach((entityplayer) -> { // Folia - region threading
            entityplayer.connection.suspendFlushing();
        });
        MinecraftTimings.bukkitSchedulerTimer.startTiming(); // Spigot // Paper
       // Folia - region threading
        MinecraftTimings.bukkitSchedulerTimer.stopTiming(); // Spigot // Paper
        // Folia - region threading - moved to global tick - and moved entity scheduler to tickRegion
        this.profiler.push("commandFunctions");
        MinecraftTimings.commandFunctionsTimer.startTiming(); // Spigot // Paper
        if (region == null) this.getFunctions().tick(); // Folia - region threading - TODO Purge functions
        MinecraftTimings.commandFunctionsTimer.stopTiming(); // Spigot // Paper
        this.profiler.popPush("levels");
        //Iterator iterator = this.getAllLevels().iterator(); // Paper - moved down

        // CraftBukkit start
        // Run tasks that are waiting on processing
        MinecraftTimings.processQueueTimer.startTiming(); // Spigot
        if (region == null) while (!this.processQueue.isEmpty()) { // Folia - region threading
            this.processQueue.remove().run();
        }
        MinecraftTimings.processQueueTimer.stopTiming(); // Spigot

        MinecraftTimings.timeUpdateTimer.startTiming(); // Spigot // Paper
        // Send time updates to everyone, it will get the right time from the world the player is in.
        // Paper start - optimize time updates
        for (final ServerLevel level : (region == null ? this.getAllLevels() : Arrays.asList(region.world))) { // Folia - region threading
            final boolean doDaylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            final long dayTime = level.getDayTime();
            long worldTime = level.getGameTime();
            final ClientboundSetTimePacket worldPacket = new ClientboundSetTimePacket(worldTime, dayTime, doDaylight);
            for (Player entityhuman : level.getLocalPlayers()) { // Folia - region threading
                if (!(entityhuman instanceof ServerPlayer) || (io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + entityhuman.getId()) % 20 != 0) { // Folia - region threading
                    continue;
                }
                ServerPlayer entityplayer = (ServerPlayer) entityhuman;
                long playerTime = entityplayer.getPlayerTime();
                ClientboundSetTimePacket packet = (playerTime == dayTime) ? worldPacket :
                    new ClientboundSetTimePacket(worldTime, playerTime, doDaylight);
                entityplayer.connection.send(packet); // Add support for per player time
            }
        }
        // Paper end
        MinecraftTimings.timeUpdateTimer.stopTiming(); // Spigot // Paper

        if (region == null) this.isIteratingOverLevels = true; // Paper // Folia - region threading
        Iterator iterator = region == null ? this.getAllLevels().iterator() : Arrays.asList(region.world).iterator(); // Paper - move down // Folia - region threading
        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();
            // Folia - region threading

            this.profiler.push(() -> {
                return worldserver + " " + worldserver.dimension().location();
            });
            /* Drop global time updates
            if (this.tickCount % 20 == 0) {
                this.profiler.push("timeSync");
                this.synchronizeTime(worldserver);
                this.profiler.pop();
            }
            // CraftBukkit end */

            this.profiler.push("tick");

            try {
                worldserver.timings.doTick.startTiming(); // Spigot
                profiler.startTimer(worldserver.tickTimerId); try { // Folia - profiler
                worldserver.tick(shouldKeepTicking, region); // Folia - region threading
                // Paper start
                for (final io.papermc.paper.chunk.SingleThreadChunkRegionManager regionManager : worldserver.getChunkSource().chunkMap.regionManagers) {
                    regionManager.recalculateRegions();
                }
                // Paper end
                } finally { profiler.stopTimer(worldserver.tickTimerId); } // Folia - profiler
                worldserver.timings.doTick.stopTiming(); // Spigot
            } catch (Throwable throwable) {
                // Spigot Start
                CrashReport crashreport;
                try {
                    crashreport = CrashReport.forThrowable(throwable, "Exception ticking world");
                } catch (Throwable t) {
                    if (throwable instanceof ThreadDeath) { throw (ThreadDeath)throwable; } // Paper
                    throw new RuntimeException("Error generating crash report", t);
                }
                // Spigot End

                worldserver.fillReportDetails(crashreport);
                throw new ReportedException(crashreport);
            }

            this.profiler.pop();
            this.profiler.pop();
            regionizedWorldData.explosionDensityCache.clear(); // Paper - Optimize explosions // Folia - region threading
        }
        if (region == null) this.isIteratingOverLevels = false; // Paper // Folia - region threading

        this.profiler.popPush("connection");
        MinecraftTimings.connectionTimer.startTiming(); // Spigot
        if (region == null) this.getConnection().tick(); // Folia - region threading - moved into post entity scheduler tick
        MinecraftTimings.connectionTimer.stopTiming(); // Spigot
        this.profiler.popPush("players");
        MinecraftTimings.playerListTimer.startTiming(); // Spigot // Paper
        if (false) this.playerList.tick(); // Folia - region threading
        MinecraftTimings.playerListTimer.stopTiming(); // Spigot // Paper
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            GameTestTicker.SINGLETON.tick();
        }

        this.profiler.popPush("server gui refresh");

        MinecraftTimings.tickablesTimer.startTiming(); // Spigot // Paper
        if (region == null) for (int i = 0; i < this.tickables.size(); ++i) {  // Folia - region threading - TODO WTF is this?
            ((Runnable) this.tickables.get(i)).run();
        }
        MinecraftTimings.tickablesTimer.stopTiming(); // Spigot // Paper

        this.profiler.popPush("send chunks");
        iterator = this.playerList.getPlayers().iterator();

        if (region == null) while (iterator.hasNext()) { // Folia - region threading
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.chunkSender.sendNextChunks(entityplayer);
            entityplayer.connection.resumeFlushing();
        }

        this.profiler.pop();
    }

    private void synchronizeTime(ServerLevel world) {
        this.playerList.broadcastAll(new ClientboundSetTimePacket(world.getGameTime(), world.getDayTime(), world.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)), world.dimension());
    }

    public void forceTimeSynchronization() {
        this.profiler.push("timeSync");
        Iterator iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            this.synchronizeTime(worldserver);
        }

        this.profiler.pop();
    }

    public boolean isNetherEnabled() {
        return true;
    }

    public void addTickable(Runnable tickable) {
        this.tickables.add(tickable);
    }

    protected void setId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public File getFile(String path) {
        return new File(this.getServerDirectory(), path);
    }

    public final ServerLevel overworld() {
        return (ServerLevel) this.levels.get(Level.OVERWORLD);
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> key) {
        return (ServerLevel) this.levels.get(key);
    }

    // CraftBukkit start
    public void addLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.put(level.dimension(), level);
        this.levels = Collections.unmodifiableMap(newLevels);
    }

    public void removeLevel(ServerLevel level) {
        Map<ResourceKey<Level>, ServerLevel> oldLevels = this.levels;
        Map<ResourceKey<Level>, ServerLevel> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.remove(level.dimension());
        this.levels = Collections.unmodifiableMap(newLevels);
    }
    // CraftBukkit end

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return "Folia"; // Folia - Folia > // Paper - Paper > // Spigot - Spigot > // CraftBukkit - cb > vanilla!
    }

    public SystemReport fillSystemReport(SystemReport details) {
        details.setDetail("Server Running", () -> {
            return Boolean.toString(this.running);
        });
        if (this.playerList != null) {
            details.setDetail("Player Count", () -> {
                int i = this.playerList.getPlayerCount();

                return i + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers();
            });
        }

        details.setDetail("Data Packs", () -> {
            return (String) this.packRepository.getSelectedPacks().stream().map((resourcepackloader) -> {
                String s = resourcepackloader.getId();

                return s + (resourcepackloader.getCompatibility().isCompatible() ? "" : " (incompatible)");
            }).collect(Collectors.joining(", "));
        });
        details.setDetail("Enabled Feature Flags", () -> {
            return (String) FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
        });
        details.setDetail("World Generation", () -> {
            return this.worldData.worldGenSettingsLifecycle().toString();
        });
        if (this.serverId != null) {
            details.setDetail("Server Id", () -> {
                return this.serverId;
            });
        }

        return this.fillServerSystemReport(details);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport details);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component message) {
        MinecraftServer.LOGGER.info(io.papermc.paper.adventure.PaperAdventure.ANSI_SERIALIZER.serialize(io.papermc.paper.adventure.PaperAdventure.asAdventure(message))); // Paper - Log message with colors
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int serverPort) {
        this.port = serverPort;
    }

    @Nullable
    public GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile hostProfile) {
        this.singleplayerProfile = hostProfile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        MinecraftServer.LOGGER.info("Generating keypair");

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException cryptographyexception) {
            throw new IllegalStateException("Failed to generate key pair", cryptographyexception);
        }
    }

    // Paper start - remember per level difficulty
    public void setDifficulty(ServerLevel level, Difficulty difficulty, boolean forceUpdate) {
        PrimaryLevelData worldData = level.serverLevelData;
        if (forceUpdate || !worldData.isDifficultyLocked()) {
            worldData.setDifficulty(worldData.isHardcore() ? Difficulty.HARD : difficulty);
            level.setSpawnSettings(worldData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals());
            // this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
            // Paper end
        }
    }

    public int getScaledTrackingDistance(int initialDistance) {
        return initialDistance;
    }

    private void updateMobSpawningFlags() {
        Iterator iterator = this.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            worldserver.setSpawnSettings(worldserver.serverLevelData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))
        }

    }

    public void setDifficultyLocked(boolean locked) {
        this.worldData.setDifficultyLocked(locked);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer player) {
        LevelData worlddata = player.level().getLevelData();

        player.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean demo) {
        this.isDemo = demo;
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean preventProxyConnections) {
        this.preventProxyConnections = preventProxyConnections;
    }

    public boolean isSpawningAnimals() {
        return true;
    }

    public boolean areNpcsEnabled() {
        return true;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean pvpEnabled) {
        this.pvp = pvpEnabled;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean flightEnabled) {
        this.allowFlight = flightEnabled;
    }

    public abstract boolean isCommandBlockEnabled();

    @Override
    public String getMotd() {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(this.motd); // Paper - Adventure
    }

    public void setMotd(String motd) {
        // Paper start - Adventure
        this.motd = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserializeOr(motd, net.kyori.adventure.text.Component.empty());
    }

    public net.kyori.adventure.text.Component motd() {
        return this.motd;
    }

    public void motd(net.kyori.adventure.text.Component motd) {
        // Paper end - Adventure
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList playerManager) {
        this.playerList = playerManager;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType gameMode) {
        this.worldData.setGameType(gameMode);
    }

    public ServerConnectionListener getConnection() {
        return this.connection == null ? this.connection = new ServerConnectionListener(this) : this.connection; // Spigot
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean hasGui() {
        return false;
    }

    public boolean publishServer(@Nullable GameType gameMode, boolean cheatsAllowed, int port) {
        return false;
    }

    public int getTickCount() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(ServerLevel world, BlockPos pos, Player player) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int playerIdleTimeout) {
        this.playerIdleTimeout = playerIdleTimeout;
    }

    public MinecraftSessionService getSessionService() {
        return this.services.sessionService();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return this.services.profileKeySignatureValidator();
    }

    public GameProfileRepository getProfileRepository() {
        return this.services.profileRepository();
    }

    @Nullable
    public GameProfileCache getProfileCache() {
        return this.services.profileCache();
    }

    @Nullable
    public ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        // Folia start - region threading
        if (true) {
            // we don't need this to notify the global tick region
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTaskWithoutNotify(() -> {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().invalidateStatus();
            });
            return;
        }
        // Folia end - region threading
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable runnable) {
        if (true) throw new UnsupportedOperationException(); // Folia - region threading
        if (this.isStopped()) {
            throw new RejectedExecutionException("Server already shutting down");
        } else {
            super.executeIfPossible(runnable);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTime;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable ServerLevel world) {
        return world != null ? world.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    // Paper start - add cause
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public CompletableFuture<Void> reloadResources(Collection<String> dataPacks) {
        return this.reloadResources(dataPacks, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN);
    }
    public CompletableFuture<Void> reloadResources(Collection<String> dataPacks, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
        // Paper end
        RegistryAccess.Frozen iregistrycustom_dimension = this.registries.getAccessForLoading(RegistryLayer.RELOADABLE);
        CompletableFuture<Void> completablefuture = CompletableFuture.supplyAsync(() -> {
            Stream<String> stream = dataPacks.stream(); // CraftBukkit - decompile error
            PackRepository resourcepackrepository = this.packRepository;

            Objects.requireNonNull(this.packRepository);
            return stream.<Pack>map(resourcepackrepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()); // CraftBukkit - decompile error // Paper - decompile error // todo: is this needed anymore?
        }, this).thenCompose((immutablelist) -> {
            MultiPackResourceManager resourcemanager = new MultiPackResourceManager(PackType.SERVER_DATA, immutablelist);

            return ReloadableServerResources.loadResources(resourcemanager, iregistrycustom_dimension, this.worldData.enabledFeatures(), this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED, this.getFunctionCompilationLevel(), this.executor, this).whenComplete((datapackresources, throwable) -> {
                if (throwable != null) {
                    resourcemanager.close();
                }

            }).thenApply((datapackresources) -> {
                return new MinecraftServer.ReloadableResources(resourcemanager, datapackresources);
            });
        }).thenAcceptAsync((minecraftserver_reloadableresources) -> {
            this.resources.close();
            this.resources = minecraftserver_reloadableresources;
            this.server.syncCommands(); // SPIGOT-5884: Lost on reload
            this.packRepository.setSelected(dataPacks);
            WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(MinecraftServer.getSelectedPacks(this.packRepository), this.worldData.enabledFeatures());

            this.worldData.setDataConfiguration(worlddataconfiguration);
            this.resources.managers.updateRegistryTags(this.registryAccess());
            net.minecraft.world.item.alchemy.PotionBrewing.reload(); // Paper
            // Paper start
            if (Thread.currentThread() != this.serverThread) {
                return;
            }
            // this.getPlayerList().saveAll(); // Paper - we don't need to save everything, just advancements
            for (ServerPlayer player : this.getPlayerList().getPlayers()) {
                player.getAdvancements().save();
            }
            // Paper end
            this.getPlayerList().reloadResources();
            this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
            this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
            org.bukkit.craftbukkit.block.data.CraftBlockData.reloadCache(); // Paper - cache block data strings, they can be defined by datapacks so refresh it here
            new io.papermc.paper.event.server.ServerResourcesReloadedEvent(cause).callEvent(); // Paper - fire after everything has been reloaded
        }, this);

        if (this.isSameThread()) {
            Objects.requireNonNull(completablefuture);
            this.managedBlock(completablefuture::isDone);
        }

        return completablefuture;
    }

    public static WorldDataConfiguration configurePackRepository(PackRepository resourcePackManager, DataPackConfig dataPackSettings, boolean safeMode, FeatureFlagSet enabledFeatures) {
        resourcePackManager.reload();
        if (safeMode) {
            resourcePackManager.setSelected(Collections.singleton("vanilla"));
            return WorldDataConfiguration.DEFAULT;
        } else {
            Set<String> set = Sets.newLinkedHashSet();
            Iterator iterator = dataPackSettings.getEnabled().iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                if (resourcePackManager.isAvailable(s)) {
                    set.add(s);
                } else {
                    MinecraftServer.LOGGER.warn("Missing data pack {}", s);
                }
            }

            iterator = resourcePackManager.getAvailablePacks().iterator();

            while (iterator.hasNext()) {
                Pack resourcepackloader = (Pack) iterator.next();
                String s1 = resourcepackloader.getId();

                if (!dataPackSettings.getDisabled().contains(s1)) {
                    FeatureFlagSet featureflagset1 = resourcepackloader.getRequestedFeatures();
                    boolean flag1 = set.contains(s1);

                    if (!flag1 && resourcepackloader.getPackSource().shouldAddAutomatically()) {
                        if (featureflagset1.isSubsetOf(enabledFeatures)) {
                            MinecraftServer.LOGGER.info("Found new data pack {}, loading it automatically", s1);
                            set.add(s1);
                        } else {
                            MinecraftServer.LOGGER.info("Found new data pack {}, but can't load it due to missing features {}", s1, FeatureFlags.printMissingFlags(enabledFeatures, featureflagset1));
                        }
                    }

                    if (flag1 && !featureflagset1.isSubsetOf(enabledFeatures)) {
                        MinecraftServer.LOGGER.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.", s1, FeatureFlags.printMissingFlags(enabledFeatures, featureflagset1));
                        set.remove(s1);
                    }
                }
            }

            if (set.isEmpty()) {
                MinecraftServer.LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            resourcePackManager.setSelected(set);
            DataPackConfig datapackconfiguration1 = MinecraftServer.getSelectedPacks(resourcePackManager);
            FeatureFlagSet featureflagset2 = resourcePackManager.getRequestedFeatureFlags();

            return new WorldDataConfiguration(datapackconfiguration1, featureflagset2);
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository dataPackManager) {
        Collection<String> collection = dataPackManager.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list1 = (List) dataPackManager.getAvailableIds().stream().filter((s) -> {
            return !collection.contains(s);
        }).collect(ImmutableList.toImmutableList());

        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers(CommandSourceStack source) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerlist = source.getServer().getPlayerList();
            UserWhiteList whitelist = playerlist.getWhiteList();
            if (!((DedicatedServer)getServer()).getProperties().whiteList.get()) return; // Paper - white list not enabled
            List<ServerPlayer> list = Lists.newArrayList(playerlist.getPlayers());
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (!whitelist.isWhiteListed(entityplayer.getGameProfile()) && !this.getPlayerList().isOp(entityplayer.getGameProfile())) { // Paper - Fix kicking ops when whitelist is reloaded (MC-171420)
                    entityplayer.connection.disconnect(org.spigotmc.SpigotConfig.whitelistMessage, org.bukkit.event.player.PlayerKickEvent.Cause.WHITELIST); // Paper - use configurable message
                }
            }

        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel worldserver = this.overworld();

        return new CommandSourceStack(this, worldserver == null ? Vec3.ZERO : Vec3.atLowerCornerOf(worldserver.getSharedSpawnPos()), Vec2.ZERO, worldserver, 4, "Server", Component.literal("Server"), this, (Entity) null);
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public LootDataManager getLootData() {
        return this.resources.managers.getLootData();
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean enforceWhitelist) {
        this.enforceWhitelist = enforceWhitelist;
    }

    public float getAverageTickTime() {
        return this.averageTickTime;
    }

    public int getProfilePermissions(GameProfile profile) {
        if (this.getPlayerList().isOp(profile)) {
            ServerOpListEntry oplistentry = (ServerOpListEntry) this.getPlayerList().getOps().get(profile);

            return oplistentry != null ? oplistentry.getLevel() : (this.isSingleplayerOwner(profile) ? 4 : (this.isSingleplayer() ? (this.getPlayerList().isAllowCheatsForAllPlayers() ? 4 : 0) : this.getOperatorUserPermissionLevel()));
        } else {
            return 0;
        }
    }

    public ProfilerFiller getProfiler() {
        return this.profiler;
    }

    public abstract boolean isSingleplayerOwner(GameProfile profile);

    public void dumpServerProperties(Path file) throws IOException {}

    private void saveDebugReport(Path path) {
        Path path1 = path.resolve("levels");

        try {
            Iterator iterator = this.levels.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<ResourceKey<Level>, ServerLevel> entry = (Entry) iterator.next();
                ResourceLocation minecraftkey = ((ResourceKey) entry.getKey()).location();
                Path path2 = path1.resolve(minecraftkey.getNamespace()).resolve(minecraftkey.getPath());

                Files.createDirectories(path2);
                ((ServerLevel) entry.getValue()).saveDebugReport(path2);
            }

            this.dumpGameRules(path.resolve("gamerules.txt"));
            this.dumpClasspath(path.resolve("classpath.txt"));
            this.dumpMiscStats(path.resolve("stats.txt"));
            this.dumpThreads(path.resolve("threads.txt"));
            this.dumpServerProperties(path.resolve("server.properties.txt"));
            this.dumpNativeModules(path.resolve("modules.txt"));
        } catch (IOException ioexception) {
            MinecraftServer.LOGGER.warn("Failed to save debug report", ioexception);
        }

    }

    private void dumpMiscStats(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            bufferedwriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            bufferedwriter.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getAverageTickTime()));
            bufferedwriter.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimes)));
            bufferedwriter.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
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

    }

    private void dumpGameRules(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            final List<String> list = Lists.newArrayList();
            final GameRules gamerules = this.getGameRules();

            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", key.getId(), gamerules.getRule(key)));
                }
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();

                bufferedwriter.write(s);
            }
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

    }

    private void dumpClasspath(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            String s = System.getProperty("java.class.path");
            String s1 = System.getProperty("path.separator");
            Iterator iterator = Splitter.on(s1).split(s).iterator();

            while (iterator.hasNext()) {
                String s2 = (String) iterator.next();

                bufferedwriter.write(s2);
                bufferedwriter.write("\n");
            }
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

    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);

        Arrays.sort(athreadinfo, Comparator.comparing(ThreadInfo::getThreadName));
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        try {
            ThreadInfo[] athreadinfo1 = athreadinfo;
            int i = athreadinfo.length;

            for (int j = 0; j < i; ++j) {
                ThreadInfo threadinfo = athreadinfo1[j];

                bufferedwriter.write(threadinfo.toString());
                bufferedwriter.write(10);
            }
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

    }

    private void dumpNativeModules(Path path) throws IOException {
        BufferedWriter bufferedwriter = Files.newBufferedWriter(path);

        label50:
        {
            try {
                label51:
                {
                    ArrayList<NativeModuleLister.NativeModuleInfo> arraylist; // CraftBukkit - decompile error

                    try {
                        arraylist = Lists.newArrayList(NativeModuleLister.listModules());
                    } catch (Throwable throwable) {
                        MinecraftServer.LOGGER.warn("Failed to list native modules", throwable);
                        break label51;
                    }

                    arraylist.sort(Comparator.comparing((nativemodulelister_a) -> {
                        return nativemodulelister_a.name;
                    }));
                    Iterator iterator = arraylist.iterator();

                    while (true) {
                        if (!iterator.hasNext()) {
                            break label50;
                        }

                        NativeModuleLister.NativeModuleInfo nativemodulelister_a = (NativeModuleLister.NativeModuleInfo) iterator.next();

                        bufferedwriter.write(nativemodulelister_a.toString());
                        bufferedwriter.write(10);
                    }
                }
            } catch (Throwable throwable1) {
                if (bufferedwriter != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable2) {
                        throwable1.addSuppressed(throwable2);
                    }
                }

                throw throwable1;
            }

            if (bufferedwriter != null) {
                bufferedwriter.close();
            }

            return;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    // CraftBukkit start
    @Override
    public boolean isSameThread() {
        return io.papermc.paper.util.TickThread.isTickThread(); // Paper - rewrite chunk system
    }

    public boolean isDebugging() {
        return false;
    }

    public static MinecraftServer getServer() {
        return SERVER; // Paper
    }
    // CraftBukkit end

    private void startMetricsRecordingTick() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()), Util.timeSource, Util.ioPool(), new MetricsPersister("server"), this.onMetricsRecordingStopped, (path) -> {
                this.executeBlocking(() -> {
                    this.saveDebugReport(path.resolve("server"));
                });
                this.onMetricsRecordingFinished.accept(path);
            });
            this.willStartRecordingMetrics = false;
        }

        this.profiler = SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
        this.metricsRecorder.startTick();
        this.profiler.startTick();
    }

    private void endMetricsRecordingTick() {
        this.profiler.endTick();
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> resultConsumer, Consumer<Path> dumpConsumer) {
        this.onMetricsRecordingStopped = (methodprofilerresults) -> {
            this.stopRecordingMetrics();
            resultConsumer.accept(methodprofilerresults);
        };
        this.onMetricsRecordingFinished = dumpConsumer;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
        this.profiler = this.metricsRecorder.getProfiler();
    }

    public Path getWorldPath(LevelResource worldSavePath) {
        return this.storageSource.getLevelPath(worldSavePath);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer player) {
        return (ServerPlayerGameMode) (this.isDemo() ? new DemoMode(player) : new ServerPlayerGameMode(player));
    }

    @Nullable
    public GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component message, ChatType.Bound params, @Nullable String prefix) {
        // Paper start
        net.kyori.adventure.text.Component s1 = io.papermc.paper.adventure.PaperAdventure.asAdventure(params.decorate(message));

        if (prefix != null) {
            MinecraftServer.COMPONENT_LOGGER.info("[{}] {}", prefix, s1);
        } else {
            MinecraftServer.COMPONENT_LOGGER.info("{}", s1);
            // Paper end
        }

    }

    public final java.util.concurrent.ExecutorService chatExecutor = java.util.concurrent.Executors.newCachedThreadPool(
            new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Chat Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER)).build()); // Paper

    public ChatDecorator getChatDecorator() {
        // Paper start - moved to ChatPreviewProcessor
        return ChatDecorator.create((sender, commandSourceStack, message) -> {
            final io.papermc.paper.adventure.ChatDecorationProcessor processor = new io.papermc.paper.adventure.ChatDecorationProcessor(this, sender, commandSourceStack, message);
            return processor.process();
        });
        // Paper end
    }

    public boolean logIPs() {
        return true;
    }

    public static record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {

        public void close() {
            this.resourceManager.close();
        }
    }

    private static class TimeProfiler {

        final long startNanos;
        final int startTick;

        TimeProfiler(long time, int tick) {
            this.startNanos = time;
            this.startTick = tick;
        }

        ProfileResults stop(final long endTime, final int endTick) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String parentPath) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path path) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return endTime;
                }

                @Override
                public int getEndTimeTicks() {
                    return endTick;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }

    public static record ServerResourcePackInfo(String url, String hash, boolean isRequired, @Nullable Component prompt) {

    }

    // Paper start
    public static class TickTimes {
        private final long[] times;

        public TickTimes(int length) {
            times = new long[length];
        }

        void add(int index, long time) {
            times[index % times.length] = time;
        }

        public long[] getTimes() {
            return times.clone();
        }

        public double getAverage() {
            long total = 0L;
            for (long value : times) {
                total += value;
            }
            return ((double) total / (double) times.length) * 1.0E-6D;
        }
    }
    // Paper end

    // Paper start - execute chunk tasks mid tick
    static final long CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME = 25L * 1000L; // 25us
    static final long MAX_CHUNK_EXEC_TIME = 1000L; // 1us

    static final long TASK_EXECUTION_FAILURE_BACKOFF = 5L * 1000L; // 5us

    // Folia - region threading

    private boolean tickMidTickTasks(io.papermc.paper.threadedregions.RegionizedWorldData worldData) { // Folia - region threading
        // Folia - region threading - only execute for one world
        return worldData.world.getChunkSource().pollTask();
    }

    public final void executeMidTickTasks() {
        if (true) return; // Folia - disable mid-tick task execution
        org.spigotmc.AsyncCatcher.catchOp("mid tick chunk task execution");
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        long startTime = System.nanoTime();
        if ((startTime - worldData.lastMidTickExecute) <= CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME || (startTime - worldData.lastMidTickExecuteFailure) <= TASK_EXECUTION_FAILURE_BACKOFF) { // Folia - region threading
            // it's shown to be bad to constantly hit the queue (chunk loads slow to a crawl), even if no tasks are executed.
            // so, backoff to prevent this
            return;
        }

        co.aikar.timings.MinecraftTimings.midTickChunkTasks.startTiming();
        try {
            for (;;) {
                boolean moreTasks = this.tickMidTickTasks(worldData); // Folia - region threading
                long currTime = System.nanoTime();
                long diff = currTime - startTime;

                if (!moreTasks || diff >= MAX_CHUNK_EXEC_TIME) {
                    if (!moreTasks) {
                        worldData.lastMidTickExecuteFailure = currTime; // Folia - region threading
                    }

                    // note: negative values reduce the time
                    long overuse = diff - MAX_CHUNK_EXEC_TIME;
                    if (overuse >= (10L * 1000L * 1000L)) { // 10ms
                        // make sure something like a GC or dumb plugin doesn't screw us over...
                        overuse = 10L * 1000L * 1000L; // 10ms
                    }

                    double overuseCount = (double)overuse/(double)MAX_CHUNK_EXEC_TIME;
                    long extraSleep = (long)Math.round(overuseCount*CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME);

                    worldData.lastMidTickExecute = currTime + extraSleep; // Folia - region threading
                    return;
                }
            }
        } finally {
            co.aikar.timings.MinecraftTimings.midTickChunkTasks.stopTiming();
        }
    }
    // Paper end - execute chunk tasks mid tick
}
