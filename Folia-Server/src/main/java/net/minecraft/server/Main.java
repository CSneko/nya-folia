package net.minecraft.server;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import io.papermc.paper.world.ThreadedWorldUpgrader;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import net.minecraft.CrashReport;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

// CraftBukkit start
import com.google.common.base.Charsets;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import org.bukkit.configuration.file.YamlConfiguration;
// CraftBukkit end

public class Main {

    private static final Logger LOGGER = LogUtils.getLogger();

    public Main() {}

    @DontObfuscate
    public static void main(final OptionSet optionset) { // CraftBukkit - replaces main(String[] astring)
        SharedConstants.tryDetectVersion();
        /* CraftBukkit start - Replace everything
        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("nogui");
        OptionSpec<Void> optionspec1 = optionparser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpec<Void> optionspec2 = optionparser.accepts("demo");
        OptionSpec<Void> optionspec3 = optionparser.accepts("bonusChest");
        OptionSpec<Void> optionspec4 = optionparser.accepts("forceUpgrade");
        OptionSpec<Void> optionspec5 = optionparser.accepts("eraseCache");
        OptionSpec<Void> optionspec6 = optionparser.accepts("safeMode", "Loads level with vanilla datapack only");
        OptionSpec<Void> optionspec7 = optionparser.accepts("help").forHelp();
        OptionSpec<String> optionspec8 = optionparser.accepts("universe").withRequiredArg().defaultsTo(".", new String[0]);
        OptionSpec<String> optionspec9 = optionparser.accepts("world").withRequiredArg();
        OptionSpec<Integer> optionspec10 = optionparser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1, new Integer[0]);
        OptionSpec<String> optionspec11 = optionparser.accepts("serverId").withRequiredArg();
        OptionSpec<Void> optionspec12 = optionparser.accepts("jfrProfile");
        OptionSpec<Path> optionspec13 = optionparser.accepts("pidFile").withRequiredArg().withValuesConvertedBy(new PathConverter(new PathProperties[0]));
        NonOptionArgumentSpec nonoptionargumentspec = optionparser.nonOptions();

        try {
            OptionSet optionset = optionparser.parse(astring);

            if (optionset.has(optionspec7)) {
                optionparser.printHelpOn(System.err);
                return;
            }
            */ // CraftBukkit end

        try {
            // Paper start
            if (Boolean.getBoolean("Paper.isRunDev")) {
                net.minecraft.server.packs.VanillaPackResourcesBuilder.developmentConfig = builder -> {
                    try {
                        builder.pushAssetPath(net.minecraft.server.packs.PackType.SERVER_DATA, net.minecraft.server.packs.VanillaPackResourcesBuilder.safeGetPath(java.util.Objects.requireNonNull(Main.class.getResource("/data/.paperassetsroot"), "Missing required .paperassetsroot file").toURI()).getParent());
                    } catch (java.net.URISyntaxException | IOException ex) {
                        throw new RuntimeException(ex);
                    }
                };
            }
            // Paper end

            Path path = (Path) optionset.valueOf("pidFile"); // CraftBukkit

            if (path != null) {
                Main.writePidFile(path);
            }

            CrashReport.preload();
            if (optionset.has("jfrProfile")) { // CraftBukkit
                JvmProfiler.INSTANCE.start(Environment.SERVER);
            }

            io.papermc.paper.plugin.PluginInitializerManager.load(optionset); // Paper
            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            Path path1 = Paths.get("server.properties");
            DedicatedServerSettings dedicatedserversettings = new DedicatedServerSettings(optionset); // CraftBukkit - CLI argument support

            dedicatedserversettings.forceSave();
            Path path2 = Paths.get("eula.txt");
            Eula eula = new Eula(path2);
            // Paper start - load config files for access below if needed
            org.bukkit.configuration.file.YamlConfiguration bukkitConfiguration = io.papermc.paper.configuration.PaperConfigurations.loadLegacyConfigFile((File) optionset.valueOf("bukkit-settings"));
            org.bukkit.configuration.file.YamlConfiguration spigotConfiguration = io.papermc.paper.configuration.PaperConfigurations.loadLegacyConfigFile((File) optionset.valueOf("spigot-settings"));
            // Paper end

            if (optionset.has("initSettings")) { // CraftBukkit
                // CraftBukkit start - SPIGOT-5761: Create bukkit.yml and commands.yml if not present
                File configFile = (File) optionset.valueOf("bukkit-settings");
                YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
                configuration.options().copyDefaults(true);
                configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(Main.class.getClassLoader().getResourceAsStream("configurations/bukkit.yml"), Charsets.UTF_8)));
                configuration.save(configFile);

                File commandFile = (File) optionset.valueOf("commands-settings");
                YamlConfiguration commandsConfiguration = YamlConfiguration.loadConfiguration(commandFile);
                commandsConfiguration.options().copyDefaults(true);
                commandsConfiguration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(Main.class.getClassLoader().getResourceAsStream("configurations/commands.yml"), Charsets.UTF_8)));
                commandsConfiguration.save(commandFile);
                // CraftBukkit end
                Main.LOGGER.info("Initialized '{}' and '{}'", path1.toAbsolutePath(), path2.toAbsolutePath());
                return;
            }

            // Spigot Start
            boolean eulaAgreed = Boolean.getBoolean( "com.mojang.eula.agree" );
            if ( eulaAgreed )
            {
                System.err.println( "You have used the Spigot command line EULA agreement flag." );
                System.err.println( "By using this setting you are indicating your agreement to Mojang's EULA (https://account.mojang.com/documents/minecraft_eula)." );
                System.err.println( "If you do not agree to the above EULA please stop your server and remove this flag immediately." );
            }
            // Spigot End
            if (!eula.hasAgreedToEULA() && !eulaAgreed) { // Spigot
                Main.LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            // Paper start - Warn on headless
            String awtException = io.papermc.paper.util.ServerEnvironment.awtDependencyCheck();
            if (awtException != null) {
                Main.LOGGER.error("You are using a headless JRE distribution.");
                Main.LOGGER.error("This distribution is missing certain graphic libraries that the Minecraft server needs to function.");
                Main.LOGGER.error("For instructions on how to install the non-headless JRE, see https://docs.papermc.io/misc/java-install");
                Main.LOGGER.error("");
                Main.LOGGER.error(awtException);
                return;
            }
            // Paper end

            org.spigotmc.SpigotConfig.disabledAdvancements = spigotConfiguration.getStringList("advancements.disabled"); // Paper - fix SPIGOT-5885, must be set early in init
            // Paper start - fix SPIGOT-5824
            File file;
            File userCacheFile = new File(Services.USERID_CACHE_FILE);
            if (optionset.has("universe")) {
                file = (File) optionset.valueOf("universe"); // CraftBukkit
                userCacheFile = new File(file, Services.USERID_CACHE_FILE);
            } else {
                file = new File(bukkitConfiguration.getString("settings.world-container", "."));
            }
            // Paper end - fix SPIGOT-5824
            Services services = Services.create(new com.destroystokyo.paper.profile.PaperAuthenticationService(Proxy.NO_PROXY), file, userCacheFile, optionset); // Paper
            // CraftBukkit start
            String s = (String) Optional.ofNullable((String) optionset.valueOf("world")).orElse(dedicatedserversettings.getProperties().levelName);
            LevelStorageSource convertable = LevelStorageSource.createDefault(file.toPath());
            LevelStorageSource.LevelStorageAccess convertable_conversionsession = convertable.validateAndCreateAccess(s, LevelStem.OVERWORLD);
            LevelSummary worldinfo = convertable_conversionsession.getSummary();

            if (worldinfo != null) {
                if (worldinfo.requiresManualConversion()) {
                    Main.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                    return;
                }

                if (!worldinfo.isCompatible()) {
                    Main.LOGGER.info("This world was created by an incompatible version.");
                    return;
                }
            }

            boolean flag = optionset.has("safeMode");

            if (flag) {
                Main.LOGGER.warn("Safe mode active, only vanilla datapack will be loaded");
            }

            PackRepository resourcepackrepository = ServerPacksSource.createPackRepository(convertable_conversionsession);
            // CraftBukkit start
            File bukkitDataPackFolder = new File(convertable_conversionsession.getLevelPath(LevelResource.DATAPACK_DIR).toFile(), "bukkit");
            if (!bukkitDataPackFolder.exists()) {
                bukkitDataPackFolder.mkdirs();
            }
            File mcMeta = new File(bukkitDataPackFolder, "pack.mcmeta");
            try {
                com.google.common.io.Files.write("{\n"
                        + "    \"pack\": {\n"
                        + "        \"description\": \"Data pack for resources provided by Bukkit plugins\",\n"
                        + "        \"pack_format\": " + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA) + "\n"
                        + "    }\n"
                        + "}\n", mcMeta, com.google.common.base.Charsets.UTF_8);
            } catch (java.io.IOException ex) {
                throw new RuntimeException("Could not initialize Bukkit datapack", ex);
            }
            AtomicReference<WorldLoader.DataLoadContext> worldLoader = new AtomicReference<>();
            // CraftBukkit end

            WorldStem worldstem;

            try {
                WorldLoader.InitConfig worldloader_c = Main.loadOrCreateConfig(dedicatedserversettings.getProperties(), convertable_conversionsession, flag, resourcepackrepository);

                worldstem = (WorldStem) Util.blockUntilDone((executor) -> {
                    return WorldLoader.load(worldloader_c, (worldloader_a) -> {
                        worldLoader.set(worldloader_a); // CraftBukkit
                        Registry<LevelStem> iregistry = worldloader_a.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
                        DynamicOps<Tag> dynamicops = RegistryOps.create(NbtOps.INSTANCE, (HolderLookup.Provider) worldloader_a.datapackWorldgen());
                        Pair<WorldData, WorldDimensions.Complete> pair = convertable_conversionsession.getDataTag(dynamicops, worldloader_a.dataConfiguration(), iregistry, worldloader_a.datapackWorldgen().allRegistriesLifecycle());

                        if (pair != null) {
                            return new WorldLoader.DataLoadOutput<>((WorldData) pair.getFirst(), ((WorldDimensions.Complete) pair.getSecond()).dimensionsRegistryAccess());
                        } else {
                            LevelSettings worldsettings;
                            WorldOptions worldoptions;
                            WorldDimensions worlddimensions;

                            if (optionset.has("demo")) { // CraftBukkit
                                worldsettings = MinecraftServer.DEMO_SETTINGS;
                                worldoptions = WorldOptions.DEMO_OPTIONS;
                                worlddimensions = WorldPresets.createNormalWorldDimensions(worldloader_a.datapackWorldgen());
                            } else {
                                DedicatedServerProperties dedicatedserverproperties = dedicatedserversettings.getProperties();

                                worldsettings = new LevelSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty, false, new GameRules(), worldloader_a.dataConfiguration());
                                worldoptions = optionset.has("bonusChest") ? dedicatedserverproperties.worldOptions.withBonusChest(true) : dedicatedserverproperties.worldOptions; // CraftBukkit
                                worlddimensions = dedicatedserverproperties.createDimensions(worldloader_a.datapackWorldgen());
                            }

                            WorldDimensions.Complete worlddimensions_b = worlddimensions.bake(iregistry);
                            Lifecycle lifecycle = worlddimensions_b.lifecycle().add(worldloader_a.datapackWorldgen().allRegistriesLifecycle());

                            return new WorldLoader.DataLoadOutput<>(new PrimaryLevelData(worldsettings, worldoptions, worlddimensions_b.specialWorldProperty(), lifecycle), worlddimensions_b.dimensionsRegistryAccess());
                        }
                    }, WorldStem::new, Util.backgroundExecutor(), executor);
                }).get();
            } catch (Exception exception) {
                Main.LOGGER.warn("Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", exception);
                return;
            }

            /*
            IRegistryCustom.Dimension iregistrycustom_dimension = worldstem.registries().compositeAccess();

            if (optionset.has(optionspec4)) {
                forceUpgrade(convertable_conversionsession, DataConverterRegistry.getDataFixer(), optionset.has(optionspec5), () -> {
                    return true;
                }, iregistrycustom_dimension.registryOrThrow(Registries.LEVEL_STEM));
            }

            SaveData savedata = worldstem.worldData();

            convertable_conversionsession.saveDataTag(iregistrycustom_dimension, savedata);
            */
            Class.forName(net.minecraft.world.entity.npc.VillagerTrades.class.getName());// Paper - load this sync so it won't fail later async
            final DedicatedServer dedicatedserver = (DedicatedServer) MinecraftServer.spin((thread) -> {
                DedicatedServer dedicatedserver1 = new DedicatedServer(optionset, worldLoader.get(), thread, convertable_conversionsession, resourcepackrepository, worldstem, dedicatedserversettings, DataFixers.getDataFixer(), services, LoggerChunkProgressListener::new);

                /*
                dedicatedserver1.setPort((Integer) optionset.valueOf(optionspec10));
                */
                dedicatedserver1.setDemo(optionset.has("demo")); // Paper - Restore setting the demo mode
                /*
                dedicatedserver1.setId((String) optionset.valueOf(optionspec11));
                */
                boolean flag1 = !optionset.has("nogui") && !optionset.nonOptionArguments().contains("nogui");

                if(!Boolean.parseBoolean(System.getenv().getOrDefault("PAPER_DISABLE_SERVER_GUI", String.valueOf(false)))) // Paper
                if (flag1 && !GraphicsEnvironment.isHeadless()) {
                    dedicatedserver1.showGui();
                }

                if (optionset.has("port")) {
                    int port = (Integer) optionset.valueOf("port");
                    if (port > 0) {
                        dedicatedserver1.setPort(port);
                    }
                }

                return dedicatedserver1;
            });
            /* CraftBukkit start
            Thread thread = new Thread("Server Shutdown Thread") {
                public void run() {
                    dedicatedserver.halt(true);
                }
            };

            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(Main.LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
            */ // CraftBukkit end
        } catch (Exception exception1) {
            Main.LOGGER.error(LogUtils.FATAL_MARKER, "Failed to start the minecraft server", exception1);
        }

    }

    private static void writePidFile(Path path) {
        try {
            long i = ProcessHandle.current().pid();

            Files.writeString(path, Long.toString(i));
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    private static WorldLoader.InitConfig loadOrCreateConfig(DedicatedServerProperties serverPropertiesHandler, LevelStorageSource.LevelStorageAccess session, boolean safeMode, PackRepository dataPackManager) {
        WorldDataConfiguration worlddataconfiguration = session.getDataConfiguration();
        boolean flag1;
        WorldDataConfiguration worlddataconfiguration1;

        if (worlddataconfiguration != null) {
            flag1 = false;
            worlddataconfiguration1 = worlddataconfiguration;
        } else {
            flag1 = true;
            worlddataconfiguration1 = new WorldDataConfiguration(serverPropertiesHandler.initialDataPackConfiguration, FeatureFlags.DEFAULT_FLAGS);
        }

        WorldLoader.PackConfig worldloader_d = new WorldLoader.PackConfig(dataPackManager, worlddataconfiguration1, safeMode, flag1);

        return new WorldLoader.InitConfig(worldloader_d, Commands.CommandSelection.DEDICATED, serverPropertiesHandler.functionPermissionLevel);
    }

    // Paper start - fix and optimise world upgrading
    public static void convertWorldButItWorks(net.minecraft.resources.ResourceKey<net.minecraft.world.level.dimension.LevelStem> dimensionType, net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess worldSession,
                                              DataFixer dataFixer, Optional<net.minecraft.resources.ResourceKey<com.mojang.serialization.Codec<? extends net.minecraft.world.level.chunk.ChunkGenerator>>> generatorKey, boolean removeCaches) {
        int threads = Runtime.getRuntime().availableProcessors() * 3 / 8;
        final ThreadedWorldUpgrader worldUpgrader = new ThreadedWorldUpgrader(dimensionType, worldSession.getLevelId(), worldSession.levelDirectory.path().toFile(), threads, dataFixer, generatorKey, removeCaches);
        worldUpgrader.convert();
    }
    // Paper end - fix and optimise world upgrading

    public static void forceUpgrade(LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, boolean eraseCache, BooleanSupplier continueCheck, Registry<LevelStem> dimensionOptionsRegistry) {
        Main.LOGGER.info("Forcing world upgrade! {}", session.getLevelId()); // CraftBukkit
        WorldUpgrader worldupgrader = new WorldUpgrader(session, dataFixer, dimensionOptionsRegistry, eraseCache);
        Component ichatbasecomponent = null;

        while (!worldupgrader.isFinished()) {
            Component ichatbasecomponent1 = worldupgrader.getStatus();

            if (ichatbasecomponent != ichatbasecomponent1) {
                ichatbasecomponent = ichatbasecomponent1;
                Main.LOGGER.info(worldupgrader.getStatus().getString());
            }

            int i = worldupgrader.getTotalChunks();

            if (i > 0) {
                int j = worldupgrader.getConverted() + worldupgrader.getSkipped();

                Main.LOGGER.info("{}% completed ({} / {} chunks)...", new Object[]{Mth.floor((float) j / (float) i * 100.0F), j, i});
            }

            if (!continueCheck.getAsBoolean()) {
                worldupgrader.cancel();
            } else {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interruptedexception) {
                    ;
                }
            }
        }

    }
}
